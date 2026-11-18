package com.winlator.galgame;

import android.content.Context;

import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.GPUHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断向导（Plan Part I §I.6 / §I.7 闪退排查）。
 *
 * 按「命中率」排序的检查项，全部为**只读检查 + 修复建议**，不擅自改配置：
 *   1) GPU 驱动（头号闪退原因：Adreno 应走 Turnip，Mali/麒麟应走 Vortek/VirGL）
 *   2) 日文区域 / 字体（乱码、豆腐块）
 *   3) 视频解码（OP/ED 黑屏）
 *   4) 32/64 位（读 PE 头 Machine 字段）
 *   5) 音频后端
 *   6) 加密包（A5：仅提示，不破解）
 *   7) 启动里程碑（P0-4：X 连接 / wine 存活 / 首帧，来自 galgame_launch_session.json）
 *   8) 运行时依赖（P0-2：导入期 evidence 与我方组件包是否齐备，只提示不安装）
 *   9) 每游戏修复（P0-3：已落地的字段级修复，可回滚）
 *
 * 读取 P1/P2 写入的 galgame_overlay.json + Container 现配置，产出有序检查表。
 */
public final class GalgameDiagnostics {

    private GalgameDiagnostics() {}

    public enum Severity { OK, INFO, WARN, ERROR }

    /** 单条检查结果。 */
    public static final class Check {
        public final String id;
        public final Severity severity;
        public final String title;
        public final String detail;
        public final String fix;

        Check(String id, Severity severity, String title, String detail, String fix) {
            this.id = id;
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.fix = fix;
        }
    }

    /** 检查报告。 */
    public static final class Report {
        public final List<Check> checks = new ArrayList<>();

        public int count(Severity s) {
            int c = 0;
            for (Check ch : checks) if (ch.severity == s) c++;
            return c;
        }

        public boolean hasProblems() {
            return count(Severity.WARN) + count(Severity.ERROR) > 0;
        }

        public String summary() {
            return "OK=" + count(Severity.OK) + " INFO=" + count(Severity.INFO)
                    + " WARN=" + count(Severity.WARN) + " ERROR=" + count(Severity.ERROR);
        }
    }

    /** 执行全部检查。 */
    public static Report run(Context context, Container container) {
        Report report = new Report();
        JSONObject overlay = readOverlay(container);

        checkGpu(context, container, report);
        checkLocaleFont(container, overlay, report);
        checkVideo(container, report);
        checkBitness(overlay, report);
        checkAudio(container, report);
        checkEncryption(overlay, report);
        // P0-4 / P0-2 / P0-3：启动里程碑、运行时依赖建议、已落地修复
        checkLaunchStages(container, report);
        checkRedist(container, overlay, report);
        checkFixes(overlay, report);

        return report;
    }

    // ---- 1) GPU 驱动 ----

    private static void checkGpu(Context context, Container container, Report r) {
        String configured = container != null ? container.getGraphicsDriver() : null;

        String soc;
        String recommended;
        try {
            short adreno = GPUHelper.getAdrenoModelId(context);
            String renderer = GPUHelper.glGetRenderer(context);
            if (adreno > 0) soc = "Adreno " + adreno;
            else if (renderer != null && !renderer.isEmpty()) soc = renderer;
            else soc = "未知 GPU";
            recommended = GraphicsDrivers.getDefaultDriver(context);
        }
        catch (Throwable t) {
            soc = "未知 GPU";
            recommended = GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO;
        }

        if (configured == null || configured.isEmpty()) {
            r.checks.add(new Check("gpu_driver", Severity.OK, "GPU 驱动",
                    "GPU=" + soc + "；走官方自动选择（建议 " + recommended + "）",
                    ""));
            return;
        }

        String[] ids = GraphicsDrivers.parseIdentifiers(configured);
        String vk = ids.length > 0 ? GraphicsDrivers.getName(ids[0]) : "?";
        String gl = ids.length > 1 ? GraphicsDrivers.getName(ids[1]) : "?";

        r.checks.add(new Check("gpu_driver", Severity.INFO, "GPU 驱动",
                "GPU=" + soc + "；当前 Vulkan=" + vk + ", OpenGL=" + gl + "；建议=" + recommended,
                "若启动即黑屏/秒退，优先尝试把 Vulkan 切到 " + GraphicsDrivers.getName(GraphicsDrivers.TURNIP)
                        + "（Adreno）或 " + GraphicsDrivers.getName(GraphicsDrivers.VORTEK) + "（Mali/麒麟）"));
    }

    // ---- 2) 日文区域 / 字体 ----

    private static void checkLocaleFont(Container container, JSONObject overlay, Report r) {
        JSONObject loc = overlay != null ? overlay.optJSONObject("locale_injection") : null;

        String locale = container != null ? container.getExtra("galgame_locale", null) : null;
        if (locale == null && loc != null) locale = opt(loc, "locale");

        // 中文产品化后：ja_JP 与 zh_CN 均视为正确注入，其余（或无）判 WARN
        boolean okLocale = locale != null && (locale.startsWith("ja") || locale.startsWith("zh"));
        if (!okLocale) {
            r.checks.add(new Check("locale", Severity.WARN, "语言区域",
                    "未检测到 ja_JP / zh_CN 注入（当前：" + (locale == null ? "无" : locale) + "）",
                    "重新导入以注入区域；或点「重新注入语言/字体」；也可在容器 EnvVars 手加 LC_ALL/LANG"));
        }
        else {
            r.checks.add(new Check("locale", Severity.OK, "语言区域", "已注入 " + locale, ""));
        }

        String font = container != null ? container.getExtra("galgame_font", null) : null;
        if ((font == null || font.isEmpty()) && loc != null) font = opt(loc, "font_face");

        if (font == null || font.isEmpty()) {
            r.checks.add(new Check("font", Severity.WARN, "字体",
                    "未注入显示字体，可能出现豆腐块（□□□）",
                    "把 .ttf/.otf 放入 " + GalgameFonts.dropInDir().getAbsolutePath()
                            + " 后「重新注入语言/字体」"));
        }
        else {
            r.checks.add(new Check("font", Severity.OK, "字体", "已注册字体：" + font, ""));
        }
    }

    // ---- 3) 视频解码 ----

    private static void checkVideo(Container container, Report r) {
        String wc = container != null ? container.getWinComponents() : null;
        boolean ds = GalgameVideoSupport.isDirectShowEnabled(wc);
        boolean wm = GalgameVideoSupport.isWmDecoderEnabled(wc);

        if (ds && wm) {
            r.checks.add(new Check("video", Severity.OK, "视频解码",
                    "DirectShow / wmdecoder 已开启（OP/ED 可播）", ""));
        }
        else {
            r.checks.add(new Check("video", Severity.WARN, "视频解码",
                    "directshow=" + (ds ? "1" : "0") + ", wmdecoder=" + (wm ? "1" : "0") + "，OP/ED 可能黑屏",
                    "重新导入以套用 P3 预设（强制 directshow=1 / wmdecoder=1）"));
        }
    }

    // ---- 4) 32/64 位 ----

    private static void checkBitness(JSONObject overlay, Report r) {
        String exePath = overlay != null ? opt(overlay, "exe") : "";
        if (exePath == null || exePath.isEmpty()) {
            r.checks.add(new Check("bitness", Severity.INFO, "32/64 位", "未记录启动 exe，跳过位宽检测", ""));
            return;
        }

        File exe = new File(exePath);
        if (!exe.isFile()) {
            // 兼容 overlay 里记录为相对名/旧路径的情况：回退到游戏目录下同名文件。
            String gameDir = overlay != null ? opt(overlay, "game_dir") : "";
            if (gameDir != null && !gameDir.isEmpty()) exe = new File(gameDir, new File(exePath).getName());
        }
        int machine = peMachine(exe);

        String desc;
        Severity sev = Severity.OK;
        String fix = "";
        switch (machine) {
            case 0x014c: desc = "32 位 (x86)"; break;
            case 0x8664: desc = "64 位 (x64)"; break;
            case 0xaa64: desc = "ARM64"; break;
            default:
                desc = machine == -1 ? "无法读取" : "未知 (0x" + Integer.toHexString(machine) + ")";
                sev = Severity.INFO;
                fix = "无法解析 PE 头：exe 可能已被移动/删除，或不是合法 PE";
                break;
        }
        r.checks.add(new Check("bitness", sev, "32/64 位",
                "启动 exe：" + exe.getName() + " → " + desc, fix));
    }

    /** 读 PE 头 Machine 字段：0x014c=x86, 0x8664=x64, 0xaa64=ARM64；失败返回 -1。 */
    static int peMachine(File f) {
        if (f == null || !f.isFile()) return -1;
        try (InputStream in = new FileInputStream(f)) {
            byte[] dos = new byte[64];
            if (in.read(dos) < 64) return -1;
            if (dos[0] != 'M' || dos[1] != 'Z') return -1;

            int peOff = (dos[0x3C] & 0xFF) | ((dos[0x3D] & 0xFF) << 8)
                    | ((dos[0x3E] & 0xFF) << 16) | ((dos[0x3F] & 0xFF) << 24);
            if (peOff < 0) return -1;

            long toSkip = peOff - 64L;
            while (toSkip > 0) {
                long s = in.skip(toSkip);
                if (s <= 0) break;
                toSkip -= s;
            }

            byte[] pe = new byte[6];
            if (in.read(pe) < 6) return -1;
            if (pe[0] != 'P' || pe[1] != 'E' || pe[2] != 0 || pe[3] != 0) return -1;
            return (pe[4] & 0xFF) | ((pe[5] & 0xFF) << 8);
        }
        catch (Exception e) {
            return -1;
        }
    }

    // ---- 5) 音频后端 ----

    private static void checkAudio(Container container, Report r) {
        String audio = container != null ? container.getAudioDriver() : null;
        String name = AudioDrivers.PULSEAUDIO.equals(audio) ? "PulseAudio"
                : (AudioDrivers.ALSA.equals(audio) ? "ALSA" : String.valueOf(audio));
        r.checks.add(new Check("audio", Severity.INFO, "音频后端",
                "当前：" + name,
                "若无声/爆音，尝试在容器设置里切换 ALSA ↔ PulseAudio"));
    }

    // ---- 6) 加密（A5） ----

    private static void checkEncryption(JSONObject overlay, Report r) {
        if (overlay == null) return;
        boolean encrypted = "true".equalsIgnoreCase(opt(overlay, "encrypted"));
        if (!encrypted) return;

        StringBuilder marks = new StringBuilder();
        org.json.JSONArray arr = overlay.optJSONArray("encryption_markers");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                if (marks.length() > 0) marks.append("; ");
                marks.append(arr.optString(i, ""));
            }
        }
        r.checks.add(new Check("encryption", Severity.WARN, "加密包（A5）",
                "检测到加密特征：" + marks,
                "本外壳仅检测提示，不提供破解（A5）；请使用正版/合法渠道"));
    }

    // ---- 7) 启动里程碑（P0-4） ----

    /**
     * 三段客观里程碑：X 连接可建立 → wine 进程存活 → 首帧到达。
     * 判据全部来自事件打点（{@link GalgameLaunchSession}），不用固定耗时猜。
     */
    private static void checkLaunchStages(Container container, Report r) {
        JSONObject session = GalgameLaunchSession.readFor(container);
        if (session == null) {
            r.checks.add(new Check("launch_stages", Severity.INFO, "启动里程碑",
                    "本容器还没有启动会话文件（启动一次后再查看）", ""));
            return;
        }

        long started = session.optLong("started_at", 0L);
        long age = started > 0 ? System.currentTimeMillis() - started : 0L;
        JSONArray stages = session.optJSONArray("stages");

        JSONObject x = findStage(stages, GalgameLaunchSession.X_SERVER_READY);
        JSONObject wine = findStage(stages, GalgameLaunchSession.WINE_STARTED);
        JSONObject frame = findStage(stages, GalgameLaunchSession.FIRST_FRAME);
        JSONObject exited = findStage(stages, GalgameLaunchSession.EXITED);
        JSONObject stopped = findStage(stages, GalgameLaunchSession.STOPPED);

        if (x == null) {
            r.checks.add(new Check("launch_stages", Severity.ERROR, "启动里程碑",
                    "会话已开始，但 X 连接层没有就绪（缺 X_SERVER_READY）——问题在图形/连接层，不在游戏",
                    "查看 logcat 中 XConnector/XServer 段；确认 rootfs 正常、未被其它实例占用"));
            return;
        }

        if (wine == null) {
            String detail = "X 已就绪（+" + since(x) + "ms），但 guest 进程还没有 fork 出 pid";
            if (stopped != null) {
                r.checks.add(new Check("launch_stages", Severity.INFO, "启动里程碑",
                        detail + "；随后被手动停止", ""));
            }
            else if (exited != null) {
                r.checks.add(new Check("launch_stages", Severity.ERROR, "启动里程碑",
                        detail + "；进程退出 " + detailOf(exited),
                        "看 logcat 与 box64 trace：多为 box64/wine 解包失败或 exe 路径不可达"));
            }
            else {
                r.checks.add(new Check("launch_stages", Severity.WARN, "启动里程碑",
                        detail + "（已等待 " + age + "ms）",
                        "若持续超过 20s：检查 box64 版本包是否解压完成、exe 是否在容器内存在"));
            }
            return;
        }

        if (frame == null) {
            if (exited != null) {
                r.checks.add(new Check("launch_stages", Severity.ERROR, "启动里程碑",
                        "wine 已启动（+" + since(wine) + "ms）但未产生首帧就退出：" + detailOf(exited),
                        "按 box64 trace / WINEDEBUG 排查；这类形态是「进程活着但没画出来」的对立面"));
            }
            else if (stopped != null) {
                r.checks.add(new Check("launch_stages", Severity.INFO, "启动里程碑",
                        "wine 已启动，但在首帧前被手动停止", ""));
            }
            else if (age < GalgameLaunchSession.FIRST_FRAME_HINT_MS) {
                r.checks.add(new Check("launch_stages", Severity.INFO, "启动里程碑",
                        "启动中：wine 已起（+" + since(wine) + "ms），等待首帧（已 " + age + "ms）", ""));
            }
            else {
                r.checks.add(new Check("launch_stages", Severity.WARN, "启动里程碑",
                        "首帧未到达：wine 已运行 " + age + "ms，X 侧仍无窗口内容更新（TD-16 形态）",
                        "开 box64 trace；检查 GPU 驱动（Adreno→Turnip / Mali→Vortek）与容器分辨率"));
            }
            return;
        }

        String detail = "X +" + since(x) + "ms / wine +" + since(wine)
                + "ms / 首帧 +" + since(frame) + "ms";
        Severity sev = Severity.OK;
        if (exited != null && !isZeroStatus(exited)) {
            sev = Severity.WARN;
            detail += "；进程已退出 " + detailOf(exited);
        }
        r.checks.add(new Check("launch_stages", sev, "启动里程碑", detail,
                sev == Severity.OK ? "" : "非 0 退出：看 logcat 崩溃段与 GalgameCrashHandler 报告"));
    }

    private static JSONObject findStage(JSONArray stages, String name) {
        if (stages == null) return null;
        for (int i = 0; i < stages.length(); i++) {
            JSONObject o = stages.optJSONObject(i);
            if (o != null && name.equals(o.optString("stage"))) return o;
        }
        return null;
    }

    private static long since(JSONObject stage) {
        return stage == null ? 0L : stage.optLong("since_ms", 0L);
    }

    private static String detailOf(JSONObject stage) {
        return stage == null ? "" : stage.optString("detail", "");
    }

    private static boolean isZeroStatus(JSONObject exited) {
        String d = detailOf(exited);
        return d.equals("status=0") || d.isEmpty();
    }

    // ---- 8) 运行时依赖建议（P0-2，只展示不安装） ----

    private static void checkRedist(Container container, JSONObject overlay, Report r) {
        JSONArray arr = overlay != null ? overlay.optJSONArray("redist_recommend") : null;
        if (arr == null || arr.length() == 0) return;   // 没有证据就不占位

        int missing = 0;
        int disabled = 0;
        StringBuilder missingNames = new StringBuilder();
        StringBuilder disabledNames = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String component = o.optString("component", "");
            String label = o.optString("label", component);
            boolean available = o.optBoolean("available", false);

            if (!available) {
                missing++;
                appendName(missingNames, label + "(" + component + ")");
            }
            else if (container != null && !winComponentEnabled(container.getWinComponents(), component)) {
                disabled++;
                appendName(disabledNames, label + "(" + component + ")");
            }
        }

        if (missing > 0) {
            r.checks.add(new Check("redist_components", Severity.WARN, "运行时依赖",
                    "检测到需要但本地没有组件包：" + missingNames
                            + (disabled > 0 ? "；另有未启用：" + disabledNames : ""),
                    "本批只侦测不打包：需先在 assets/wincomponents 补该组件包，才谈得上「免启动安装」"));
        }
        else if (disabled > 0) {
            r.checks.add(new Check("redist_components", Severity.WARN, "运行时依赖",
                    "检测到需要且已有组件包，但容器未启用：" + disabledNames,
                    "重新导入以套用组件，或在容器设置里开启对应组件"));
        }
        else {
            r.checks.add(new Check("redist_components", Severity.OK, "运行时依赖",
                    "导入期证据涉及的组件均已具备并启用", ""));
        }
    }

    private static void appendName(StringBuilder sb, String name) {
        if (sb.length() > 0) sb.append("、");
        sb.append(name);
    }

    /** 极简 wincomponents CSV 判定：key 存在且值为 1/true。 */
    private static boolean winComponentEnabled(String csv, String key) {
        if (csv == null || key == null || key.isEmpty()) return false;
        for (String part : csv.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            if (!key.equals(part.substring(0, eq).trim())) continue;
            String v = part.substring(eq + 1).trim();
            return "1".equals(v) || "true".equalsIgnoreCase(v);
        }
        return false;
    }

    // ---- 9) 已落地的每游戏修复（P0-3） ----

    private static void checkFixes(JSONObject overlay, Report r) {
        JSONArray arr = overlay != null ? overlay.optJSONArray("fixes_applied") : null;
        if (arr == null || arr.length() == 0) {
            r.checks.add(new Check("game_fixes", Severity.INFO, "每游戏修复",
                    "本游戏没有命中已启用的修复条目（数据层已就绪，当前 galgame_fixes.conf 无启用项）",
                    ""));
            return;
        }

        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            appendName(detail, o.optString("field") + ": " + o.optString("from", "") + "→"
                    + o.optString("to", "") + " [" + o.optString("fix", "") + "]");
        }
        r.checks.add(new Check("game_fixes", Severity.OK, "每游戏修复",
                "已应用 " + arr.length() + " 项字段级修复：" + detail,
                "如需还原：ImportFlow.revertGameFixes(container)"));
    }

    // ---- 工具 ----

    private static JSONObject readOverlay(Container container) {
        if (container == null) return null;
        File f = new File(container.getRootDir(), "galgame_overlay.json");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String opt(JSONObject o, String key) {
        try {
            return o != null && o.has(key) ? o.getString(key) : null;
        }
        catch (Exception e) {
            return null;
        }
    }
}
