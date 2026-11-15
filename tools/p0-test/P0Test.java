package com.winlator.galgame;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P0 纯逻辑单测（不依赖 JUnit / Android 运行时）。
 *
 * 运行方式（仓库根，pwsh）：
 *   javac -d out $(ci/stubs) $(galgame 全树) P0Test.java
 *   java -cp out com.winlator.galgame.P0Test
 *
 * 覆盖：box64 段生成、修复匹配/合并/预览、安装器扫描、PE 导入表解析。
 * 不覆盖：需要 Android Container/文件系统的 GalgameLaunchSession IO。
 */
public final class P0Test {

    private static int failed = 0;
    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        testBox64Section();
        testFixRegistry();
        testFixMerge();
        testRedistInstallers();
        testPeImports();
        testDllMapping();

        System.out.println("P0Test: passed=" + passed + " failed=" + failed);
        if (failed > 0) System.exit(1);
    }

    // ---- P0-1 ----

    private static void testBox64Section() {
        String conf = "# c\n[kirikiri]\nBOX64_DYNAREC_STRONGMEM=1\nWINEDLLOVERRIDES=x\n"
                + "[siglus]\n";
        String sec = GalgameBox64Rc.buildSection(conf, "KIRIKIRI", "game.exe");
        check("section has exe header", sec.contains("[game.exe]"));
        check("section has strongmem", sec.contains("BOX64_DYNAREC_STRONGMEM=1"));
        check("non-BOX64 key dropped", !sec.contains("WINEDLLOVERRIDES"));
        check("unknown engine empty", GalgameBox64Rc.buildSection(conf, "UNKNOWN", "game.exe").isEmpty());
        check("empty siglus section", GalgameBox64Rc.buildSection(conf, "SIGLUS", "s.exe").isEmpty());
        check("unsafe exe rejected", GalgameBox64Rc.buildSection(conf, "KIRIKIRI", "bad[1].exe").isEmpty());
    }

    // ---- P0-3 ----

    private static void testFixRegistry() {
        String conf = "[f1]\nenabled=true\nengines=KIRIKIRI\nexe_regex=krkr.*\n"
                + "set.dxwrapper=wined3d\nset.evil=1\nnote=n1\n"
                + "[f2]\nenabled=false\nengines=SIGLUS\nset.dxwrapper=dxvk\n";

        Map<String, String> cur = new LinkedHashMap<>();
        cur.put("dxwrapper", "dxvk");

        GalgameFixRegistry.Plan p = GalgameFixRegistry.resolve(conf, "KIRIKIRI", "krkr_launch.exe", "wine-10.10", cur);
        check("enabled fix matched", p.changes.size() == 1);
        check("dxwrapper target", "wined3d".equals(p.changes.get(0).to));
        check("whitelist blocks evil field", !p.preview().contains("evil"));
        check("preview has arrow", p.preview().contains("→"));
        check("fixIds recorded", p.fixIds.contains("f1"));

        check("disabled fix ignored",
                GalgameFixRegistry.resolve(conf, "SIGLUS", "SiglusEngine.exe", null, cur).isEmpty());
        check("engine mismatch ignored",
                GalgameFixRegistry.resolve(conf, "BGI", "krkr.exe", null, cur).isEmpty());
        check("exe regex mismatch ignored",
                GalgameFixRegistry.resolve(conf, "KIRIKIRI", "other.exe", null, cur).isEmpty());
        check("no change when already equal",
                GalgameFixRegistry.resolve(
                        "[f]\nenabled=true\nset.dxwrapper=dxvk\n", "KIRIKIRI", "x.exe", "", cur).isEmpty());

        String wineConf = "[f]\nenabled=true\nwine_version_contains=10.\nset.screen_size=1920x1080\n";
        check("wine mismatch ignored",
                GalgameFixRegistry.resolve(wineConf, "KIRIKIRI", "x.exe", "wine-9.22", cur).isEmpty());
        check("wine match applied",
                !GalgameFixRegistry.resolve(wineConf, "KIRIKIRI", "x.exe", "wine-10.10", cur).isEmpty());
    }

    private static void testFixMerge() {
        check("env appends missing only",
                "LC_ALL=ja_JP.UTF-8 LANG=zh_CN.UTF-8".equals(
                        GalgameFixRegistry.appendEnv("LC_ALL=ja_JP.UTF-8", "LC_ALL=zz LANG=zh_CN.UTF-8")));
        check("env append on empty", "A=1".equals(GalgameFixRegistry.appendEnv(null, "A=1")));
        check("env no-op keeps original", "A=1".equals(GalgameFixRegistry.appendEnv("A=1", "A=1")));

        check("wincomponents updates declared key",
                "direct3d=1,vcrun2010=1,vcrun2015=1".equals(
                        GalgameFixRegistry.mergeKeyValueCsv("direct3d=1,vcrun2010=0", "vcrun2010=1,vcrun2015=1")));
        check("wincomponents keeps others",
                "A=1,B=2".equals(GalgameFixRegistry.mergeKeyValueCsv("A=1", "B=2")));
    }

    // ---- P0-2 ----

    private static void testRedistInstallers() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "p0-redist-" + System.nanoTime());
        File common = new File(dir, "_CommonRedist/vcredist");
        File y2010 = new File(common, "2010");
        check("mkdir", common.mkdirs() && y2010.mkdirs());
        writeFile(new File(common, "vcredist_x64_2015.exe"), new byte[]{0});
        // 老包文件名不带年份：年份来自父目录 2010
        writeFile(new File(y2010, "vcredist_x86.exe"), new byte[]{0});
        writeFile(new File(dir, "oalinst.exe"), new byte[]{0});
        writeFile(new File(dir, "game.exe"), new byte[]{0});

        Set<String> available = new HashSet<>(Arrays.asList("direct3d", "vcrun2010"));
        GalgameRedistDetector.Result r = GalgameRedistDetector.detect(dir, null, available);

        boolean foundVc = false;
        boolean found2010 = false;
        boolean foundOal = false;
        for (GalgameRedistDetector.Finding f : r.findings) {
            if ("vcrun2015".equals(f.component)) {
                foundVc = true;
                check("vcrun2015 not available", !f.available);
                check("vcrun2015 bundled kind", "BUNDLED".equals(f.kind));
            }
            if ("vcrun2010".equals(f.component)) {
                found2010 = true;
                check("vcrun2010 available", f.available);
            }
            if ("openal".equals(f.component)) foundOal = true;
        }
        check("vcredist detected", foundVc);
        check("parent-dir year detected", found2010);
        check("oalinst detected", foundOal);
        check("scan counted files", r.filesScanned > 0);
        deleteTree(dir);
    }

    private static void testPeImports() throws Exception {
        File pe = new File(System.getProperty("java.io.tmpdir"), "p0-pe-" + System.nanoTime() + ".exe");
        writeFile(pe, minimalPe("msvcp140.dll"));
        List<String> imports = GalgameRedistDetector.peImports(pe);
        check("pe import parsed", imports.contains("msvcp140.dll"));
        check("pe import maps to vcrun2015",
                "vcrun2015".equals(GalgameRedistDetector.componentForDll(imports.get(0))));
        deleteTree(pe);
    }

    private static void testDllMapping() {
        check("msvcp140", "vcrun2015".equals(GalgameRedistDetector.componentForDll("MSVCP140.dll")));
        check("msvcr120", "vcrun2013".equals(GalgameRedistDetector.componentForDll("msvcr120.dll")));
        check("quartz", "directshow".equals(GalgameRedistDetector.componentForDll("quartz.dll")));
        check("mscoree", "dotnetfx".equals(GalgameRedistDetector.componentForDll("mscoree.dll")));
        check("unknown null", GalgameRedistDetector.componentForDll("kernel32.dll") == null);
        check("path prefix stripped",
                "vcrun2010".equals(GalgameRedistDetector.componentForDll("C:\\x\\msvcp100.dll")));
    }

    /** 构造最小 PE32：一个 .idata 段，导入表只有一条 msvcp140.dll。 */
    private static byte[] minimalPe(String dllName) throws Exception {
        byte[] b = new byte[0x400];
        b[0] = 'M';
        b[1] = 'Z';
        putLe32(b, 0x3C, 0x40);
        // PE 签名
        b[0x40] = 'P'; b[0x41] = 'E'; b[0x42] = 0; b[0x43] = 0;
        // COFF
        putLe16(b, 0x44, 0x014c);      // machine
        putLe16(b, 0x46, 1);           // sections
        putLe16(b, 0x54, 224);         // optional header size
        // Optional header
        long opt = 0x58;
        putLe16(b, (int) opt, 0x10b);  // PE32
        putLe32(b, (int) opt + 96 + 8, 0x1000); // Import dir RVA
        // Section table at opt+224
        long sec = opt + 224;
        String name = ".idata";
        for (int i = 0; i < name.length(); i++) b[(int) sec + i] = (byte) name.charAt(i);
        putLe32(b, (int) sec + 8, 0x200);   // VirtualSize
        putLe32(b, (int) sec + 12, 0x1000); // VirtualAddress
        putLe32(b, (int) sec + 16, 0x200);  // SizeOfRawData
        putLe32(b, (int) sec + 20, 0x200);  // PointerToRawData
        // Import descriptor at file 0x200
        putLe32(b, 0x200 + 12, 0x1040); // Name RVA
        putLe32(b, 0x200 + 16, 0x1050); // FirstThunk
        // DLL name at file 0x240
        byte[] n = dllName.getBytes("US-ASCII");
        System.arraycopy(n, 0, b, 0x240, n.length);
        return b;
    }

    private static void putLe16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void putLe32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    // ---- helpers ----

    private static void writeFile(File f, byte[] data) throws Exception {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void check(String what, boolean ok) {
        if (ok) passed++;
        else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }
}
