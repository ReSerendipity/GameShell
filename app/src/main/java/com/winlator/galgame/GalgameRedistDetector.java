package com.winlator.galgame;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * P0-2：导入期「运行时依赖侦测 + 映射」，只读、零 Android 依赖、可纯 javac 单测。
 *
 * <p>范围（本批明确不做）：**不下载、不打包微软再分发包、不往 prefix 安装任何东西**。
 * 本类只回答「这个游戏看起来需要哪些运行时组件、证据是什么、我们有没有对应组件包」，
 * 结果写进容器 overlay 并由诊断页展示，把「点开报缺 DLL」提前到导入时可见。
 *
 * <p>两条独立证据链：
 * <ol>
 *   <li><b>随包安装器</b>：扫 {@code _CommonRedist / _Redist / Redistributables} 等目录与游戏根
 *       附近的 vcredist / dotNetFx / oalinst / dxsetup 等文件名；</li>
 *   <li><b>PE 导入表</b>：读主 exe 的 Import Directory，按导入的 DLL 名反推
 *       {@code msvcp140 → VC++ 2015-2022}、{@code mscoree → .NET}、{@code quartz → DirectShow} 等。</li>
 * </ol>
 *
 * <p>成本护栏沿 Bannerlator 的思路（不抄代码）：目录深度 {@link #MAX_DEPTH}、条目上限
 * {@link #MAX_ENTRIES}、导入描述符 {@link #MAX_IMPORT_DESCRIPTORS}、DLL 名长度
 * {@link #MAX_DLL_NAME}；超限置 {@code truncated} 而不是继续扫。
 *
 * <p>与 {@code assets/wincomponents/wincomponents.json} 的关系：调用方把现有组件键传进来，
 * 本类只负责标 {@code available}；「有证据但没包」（如 vcrun2015）留给诊断页报警，不擅自安装。
 */
public final class GalgameRedistDetector {

    static final int MAX_DEPTH = 5;
    static final int MAX_ENTRIES = 8000;
    static final int MAX_IMPORT_DESCRIPTORS = 512;
    static final int MAX_DLL_NAME = 260;
    static final long MAX_EXE_BYTES = 512L * 1024 * 1024;

    /** 判定为「再分发目录」的名字（小写，含子串匹配）。 */
    private static final String[] REDIST_DIR_HINTS = {
            "_commonredist", "_redist", "redistributables", "redist", "_installer", "installers"
    };

    /** PE 导入 DLL 前缀 → 组件键；顺序有意义（140 必须先于 100 等短前缀）。 */
    private static final String[][] IMPORT_RULES = {
            {"msvcp140", "vcrun2015"}, {"msvcr140", "vcrun2015"},
            {"vcruntime140", "vcrun2015"}, {"concrt140", "vcrun2015"},
            {"msvcp120", "vcrun2013"}, {"msvcr120", "vcrun2013"},
            {"msvcp110", "vcrun2012"}, {"msvcr110", "vcrun2012"},
            {"msvcp100", "vcrun2010"}, {"msvcr100", "vcrun2010"},
            {"msvcp90", "vcrun2008"}, {"msvcr90", "vcrun2008"},
            {"msvcp80", "vcrun2005"}, {"msvcr80", "vcrun2005"}, {"atl80", "vcrun2005"},
            {"mscoree", "dotnetfx"},
            {"d3dx9_", "direct3d"}, {"d3dx10", "direct3d"}, {"d3dx11", "direct3d"},
            {"d3dcompiler_", "direct3d"},
            {"quartz", "directshow"}, {"amstream", "directshow"}, {"qedit", "directshow"},
            {"wmvcore", "wmdecoder"}, {"wmadmod", "wmdecoder"}, {"wmasf", "wmdecoder"},
            {"xaudio2_", "xaudio"}, {"xapofx", "xaudio"}, {"x3daudio", "xaudio"},
            {"dsound", "directsound"},
            {"dplayx", "directplay"}, {"dpnet", "directplay"},
            {"dmime", "directmusic"}, {"dmusic", "directmusic"},
            {"dinput8", "dinput"},
            {"ddraw", "ddraw"},
            {"openal32", "openal"},
            {"xinput", "directx"},
            {"physx", "physx"},
            {"xlive", "gfwl"},
    };

    private GalgameRedistDetector() {}

    /** 单条建议。 */
    public static final class Finding {
        public final String component;
        public final String label;
        public final String evidence;
        public final String kind;      // IMPORT / BUNDLED / LOOSE
        public final boolean available;

        Finding(String component, String label, String evidence, String kind, boolean available) {
            this.component = component;
            this.label = label;
            this.evidence = evidence;
            this.kind = kind;
            this.available = available;
        }
    }

    /** 侦测结果。 */
    public static final class Result {
        public final List<Finding> findings = new ArrayList<>();
        public int filesScanned;
        public boolean truncated;

        public boolean isEmpty() {
            return findings.isEmpty();
        }
    }

    /**
     * @param gameDir            游戏根目录（stage 后的容器内副本）
     * @param exe                启动 exe；null 则只扫安装器
     * @param availableComponents 我方现有 wincomponents 键（决定 Finding.available）
     */
    public static Result detect(File gameDir, File exe, Set<String> availableComponents) {
        Result result = new Result();
        Map<String, Finding> byComponent = new LinkedHashMap<>();

        if (exe != null && exe.isFile()) {
            List<String> imports = peImports(exe);
            for (String dll : imports) {
                String component = componentForDll(dll);
                if (component == null) continue;
                String evidence = "PE 导入表: " + dll;
                putIfAbsent(byComponent, new Finding(component, labelOf(component), evidence,
                        "IMPORT", availableComponents != null && availableComponents.contains(component)));
            }
        }

        if (gameDir != null && gameDir.isDirectory()) {
            scanInstallers(gameDir, byComponent, availableComponents, result);
        }

        result.findings.addAll(byComponent.values());
        return result;
    }

    // ---- 安装器扫描 ----

    private static void scanInstallers(File dir, Map<String, Finding> out,
                                       Set<String> available, Result result) {
        List<File> queue = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        List<Boolean> insideRedist = new ArrayList<>();
        queue.add(dir);
        depths.add(0);
        insideRedist.add(false);

        int head = 0;
        while (head < queue.size()) {
            if (result.filesScanned >= MAX_ENTRIES) {
                result.truncated = true;
                return;
            }
            File cur = queue.get(head);
            int depth = depths.get(head);
            boolean inside = insideRedist.get(head);
            head++;

            File[] children = cur.listFiles();
            if (children == null) continue;

            for (File f : children) {
                if (result.filesScanned >= MAX_ENTRIES) {
                    result.truncated = true;
                    return;
                }
                result.filesScanned++;

                if (f.isDirectory()) {
                    // 根目录只下钻「再分发目录」；一旦进入再分发树，则继续下钻其子目录
                    // （常见形态 _CommonRedist/vcredist/2015/…）
                    boolean childInside = inside || isRedistDir(f.getName());
                    if (depth < MAX_DEPTH && childInside) {
                        queue.add(f);
                        depths.add(depth + 1);
                        insideRedist.add(childInside);
                    }
                    continue;
                }
                classifyInstaller(f, inside ? "BUNDLED" : "LOOSE", out, available);
            }
        }
    }

    private static boolean isRedistDir(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : REDIST_DIR_HINTS) {
            if (lower.contains(hint)) return true;
        }
        return false;
    }

    private static void classifyInstaller(File f, String kind, Map<String, Finding> out,
                                          Set<String> available) {
        String lower = f.getName().toLowerCase(Locale.ROOT);
        String component = null;

        if (lower.contains("vcredist") || lower.contains("vc_redist") || lower.contains("vcredist")) {
            // 老包常叫 vcredist_x86.exe（文件名不带年份），年份在父目录（如 .../vcredist/2010/）
            String parent = f.getParentFile() != null
                    ? f.getParentFile().getName().toLowerCase(Locale.ROOT) : "";
            String year = vcYear(lower + " " + parent);
            component = year != null ? "vcrun" + year : "vcrun_unknown";
        }
        else if (lower.contains("dotnetfx") || lower.startsWith("ndp") || lower.contains("dotnet")) {
            component = "dotnetfx";
        }
        else if (lower.contains("oalinst")) component = "openal";
        else if (lower.contains("xnafx")) component = "xna";
        else if (lower.contains("dxsetup") || lower.contains("dxwebsetup")) component = "directx";
        else if (lower.contains("wmfdist")) component = "wmdecoder";
        else if (lower.contains("physx")) component = "physx";

        if (component == null) return;

        String evidence = (kind.equals("BUNDLED") ? "随包目录: " : "游戏目录: ") + f.getName();
        putIfAbsent(out, new Finding(component, labelOf(component), evidence, kind,
                available != null && available.contains(component)));
    }

    private static String vcYear(String lower) {
        String[] years = {"2005", "2008", "2010", "2012", "2013", "2015", "2017", "2019", "2022"};
        for (String y : years) {
            if (lower.contains(y)) return y;
        }
        // 微软自 2015 起统一用 VC_redist.x86/x64.exe，文件名不再带年份
        if (lower.contains("vc_redist")) return "2015";
        return null;
    }

    private static void putIfAbsent(Map<String, Finding> out, Finding f) {
        if (!out.containsKey(f.component)) out.put(f.component, f);
    }

    static String componentForDll(String dllName) {
        if (dllName == null) return null;
        String lower = dllName.toLowerCase(Locale.ROOT);
        int slash = Math.max(lower.lastIndexOf('\\'), lower.lastIndexOf('/'));
        if (slash >= 0) lower = lower.substring(slash + 1);
        if (lower.endsWith(".dll")) lower = lower.substring(0, lower.length() - 4);

        for (String[] rule : IMPORT_RULES) {
            if (lower.startsWith(rule[0])) return rule[1];
        }
        return null;
    }

    static String labelOf(String component) {
        if (component == null) return "";
        switch (component) {
            case "vcrun2005": return "VC++ 2005";
            case "vcrun2008": return "VC++ 2008";
            case "vcrun2010": return "VC++ 2010";
            case "vcrun2012": return "VC++ 2012";
            case "vcrun2013": return "VC++ 2013";
            case "vcrun2015": return "VC++ 2015-2022";
            case "dotnetfx":  return ".NET Framework";
            case "direct3d":  return "Direct3D / D3DX";
            case "directx":   return "DirectX (xinput 等)";
            case "directshow":return "DirectShow";
            case "wmdecoder": return "WMV/WMA 解码";
            case "xaudio":    return "XAudio2";
            case "directsound":return "DirectSound";
            case "directplay":return "DirectPlay";
            case "directmusic":return "DirectMusic";
            case "dinput":    return "DirectInput";
            case "ddraw":     return "DirectDraw";
            case "openal":    return "OpenAL";
            case "physx":     return "PhysX";
            case "gfwl":      return "Games for Windows Live";
            default:          return component;
        }
    }

    // ---- PE 导入表（有界解析） ----

    /** 读取 PE 导入表里的 DLL 名；非 PE / 解析失败返回空表。 */
    static List<String> peImports(File exe) {
        List<String> names = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(exe, "r")) {
            if (raf.length() < 64 || raf.length() > MAX_EXE_BYTES) return names;

            byte[] dos = new byte[64];
            raf.readFully(dos);
            if (dos[0] != 'M' || dos[1] != 'Z') return names;

            int peOff = leInt(dos, 0x3C);
            if (peOff <= 0 || peOff + 24L >= raf.length()) return names;

            raf.seek(peOff);
            if (raf.read() != 'P' || raf.read() != 'E' || raf.read() != 0 || raf.read() != 0) {
                return names;
            }

            // COFF: Machine(2) + NumberOfSections(2) + TimeDateStamp(4) + SymPtr(4) + SymCount(4)
            //       + SizeOfOptionalHeader(2) + Characteristics(2)
            leShort(raf); // machine：本类不需要，只做结构校验
            int sections = leShort(raf);
            raf.skipBytes(12);
            int optionalSize = leShort(raf);
            if (sections <= 0 || sections > 96 || optionalSize <= 0) return names;

            long optionalStart = peOff + 24L;
            raf.seek(optionalStart);
            int magic = leShort(raf);
            int dataDirOff;
            if (magic == 0x10b) dataDirOff = 96;        // PE32
            else if (magic == 0x20b) dataDirOff = 112;  // PE32+
            else return names;

            long importDirPos = optionalStart + dataDirOff + 8; // Entry[1] = Import
            if (importDirPos + 8 > raf.length()) return names;
            raf.seek(importDirPos);
            int importRva = leInt(raf);
            if (importRva <= 0) return names;

            List<Section> secs = readSections(raf, peOff + 24L + optionalSize, sections);
            long importOff = rvaToOffset(secs, importRva);
            if (importOff < 0) return names;

            byte[] desc = new byte[20];
            long descPos = importOff;
            for (int i = 0; i < MAX_IMPORT_DESCRIPTORS; i++) {
                if (descPos + 20 > raf.length()) break;
                raf.seek(descPos);
                raf.readFully(desc);
                descPos += 20;

                int nameRva = leInt(desc, 12);
                int firstThunk = leInt(desc, 16);
                if (nameRva == 0 && firstThunk == 0) break;

                long nameOff = rvaToOffset(secs, nameRva);
                if (nameOff >= 0) {
                    String dll = readCString(raf, nameOff, MAX_DLL_NAME);
                    if (!dll.isEmpty()) names.add(dll);
                }
            }
        }
        catch (Throwable t) {
            // 解析失败 = 没有导入证据，不影响安装器证据
        }
        return names;
    }

    private static List<Section> readSections(RandomAccessFile raf, long tableStart, int count)
            throws Exception {
        List<Section> out = new ArrayList<>(count);
        raf.seek(tableStart);
        byte[] raw = new byte[40];
        for (int i = 0; i < count; i++) {
            if (raf.getFilePointer() + 40 > raf.length()) break;
            raf.readFully(raw);
            int virtualSize = leInt(raw, 8);
            int virtualAddress = leInt(raw, 12);
            int rawSize = leInt(raw, 16);
            int rawPtr = leInt(raw, 20);
            out.add(new Section(virtualAddress, Math.max(virtualSize, rawSize), rawPtr));
        }
        return out;
    }

    private static long rvaToOffset(List<Section> sections, int rva) {
        for (Section s : sections) {
            if (rva >= s.virtualAddress && rva < s.virtualAddress + s.size) {
                return s.rawPointer + (rva - s.virtualAddress);
            }
        }
        return -1L;
    }

    private static String readCString(RandomAccessFile raf, long offset, int max)
            throws Exception {
        if (offset < 0 || offset >= raf.length()) return "";
        raf.seek(offset);
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < max; i++) {
            int c = raf.read();
            if (c <= 0) break;
            sb.append((char)c);
        }
        return sb.toString();
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static int leInt(RandomAccessFile raf) throws Exception {
        int b0 = raf.read(), b1 = raf.read(), b2 = raf.read(), b3 = raf.read();
        if (b3 < 0) return -1;
        return (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
    }

    /** PE 的 16 位字段是小端；DataInput.readUnsignedShort 是大端，不能用。 */
    private static int leShort(RandomAccessFile raf) throws Exception {
        int b0 = raf.read(), b1 = raf.read();
        if (b1 < 0) return -1;
        return (b0 & 0xFF) | ((b1 & 0xFF) << 8);
    }

    private static final class Section {
        final int virtualAddress;
        final int size;
        final int rawPointer;

        Section(int virtualAddress, int size, int rawPointer) {
            this.virtualAddress = virtualAddress;
            this.size = size;
            this.rawPointer = rawPointer;
        }
    }
}
