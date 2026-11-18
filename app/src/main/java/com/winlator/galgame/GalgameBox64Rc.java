package com.winlator.galgame;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * P0-1：把 box64 调参下沉到「可执行名段」。
 *
 * <p>背景：{@code assets/box64/default.box64rc} 是上游 PC 游戏名单（steam.exe、farcry3.exe…），
 * galgame 一条都用不上；而 {@code GuestProgramLauncherComponent.copyDefaultBox64RCFile()}
 * 每次启动都把整份文件覆盖写成 {@code /etc/config.box64rc}，没有任何按游戏追加的入口。
 *
 * <p>做法：**先拷上游、再追加**。启动时从容器根目录的 {@code galgame_overlay.json} 读
 * engine 与 exe，按 {@code assets/box64/galgame_sections.conf} 里对应引擎段的键值，
 * 生成一段 {@code [<exe 名>]} 追加到已拷贝好的 rcfile 末尾。上游段原样保留（Steam/CEF
 * 修复不丢），段名精确到本次启动的 exe，不会误伤其它游戏。
 *
 * <p>为什么不是全局 {@code [*]}：{@code [*]} 会改所有经此 rootfs 启动的程序，包括其它
 * 容器；报告要求的「兜底」在这里改成「未知引擎也按本游戏 exe 名落段」，作用域相同但
 * 副作用只限这一款游戏。
 *
 * <p>失败策略：解析/写入任何异常都静默跳过——rcfile 追加失败最多让本游戏少一组调参，
 * 不能因此阻断启动。
 */
public final class GalgameBox64Rc {

    /** 引擎段模板（纯文本，新增引擎只加一段，不需要改 Java）。 */
    static final String SECTIONS_ASSET = "box64/galgame_sections.conf";

    /** 从 gameDir 向上找 overlay 的层数上限：常见 `<root>/.wine/drive_c/Games/<id>` 为 5 层。 */
    private static final int MAX_WALK_UP = 8;

    private GalgameBox64Rc() {}

    /**
     * 生成并追加本游戏的 rc 段；无 overlay / 引擎无模板 / 任何异常时什么都不做。
     *
     * @param rcFile 已拷贝好上游内容的 {@code /etc/config.box64rc}
     * @param gameDir 启动 exe 所在目录（GuestProgramLauncherComponent 的 workingDirOverride）
     */
    public static void appendFor(Context context, File rcFile, File gameDir) {
        try {
            String section = build(context, gameDir);
            if (section.isEmpty() || rcFile == null || !rcFile.isFile()) return;
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(rcFile, true), StandardCharsets.UTF_8)) {
                writer.write(section);
            }
        }
        catch (Throwable ignored) {
            // 追加失败不阻断启动：本游戏退回上游默认调参
        }
    }

    /** 生成待追加文本；不需要追加时返回空串。 */
    static String build(Context context, File gameDir) {
        if (context == null || gameDir == null) return "";
        File containerRoot = findContainerRoot(gameDir);
        if (containerRoot == null) return "";

        File overlay = new File(containerRoot, "galgame_overlay.json");
        if (!overlay.isFile()) return "";

        try {
            String json = readText(overlay);
            if (json == null) return "";
            JSONObject o = new JSONObject(json);
            String engine = o.optString("engine", "");
            String exePath = o.optString("exe", "");
            String exeName = exePath.isEmpty() ? "" : new File(exePath).getName();
            if (!exeName.isEmpty() && !isAscii(exeName)) {
                // 非 ASCII 名启动走 gs_launch.exe 别名（见 GalgameLaunchHelper.ensureEngineRuntime），
                // 段名必须跟实际被 box64 执行的 exe 名一致，否则该段永远匹配不到。
                File alias = new File(new File(exePath).getParentFile(), GalgameLaunchHelper.launchAliasName());
                if (alias.isFile()) exeName = alias.getName();
            }
            String conf = readAsset(context, SECTIONS_ASSET);
            return buildSection(conf, engine, exeName);
        }
        catch (Throwable t) {
            return "";
        }
    }

    /**
     * 纯函数：从模板文本里取 engine 段，生成 {@code [exe]} 段文本。
     *
     * <p>只接受 {@code BOX64_} 前缀键；exe 名含 {@code [ ]}、换行等会破坏 rcfile 语法的字符时
     * 直接放弃（宁可少调参，不可写出无法解析的 rcfile）。
     */
    static String buildSection(String conf, String engine, String exeName) {
        if (conf == null || engine == null || exeName == null) return "";
        String exe = exeName.trim();
        if (exe.isEmpty() || !isSafeExeName(exe)) return "";

        String body = parseEngineSection(conf, engine);
        if (body.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(body.length() + 96);
        sb.append('\n');
        sb.append("# GameShell: galgame per-exe section, appended after upstream default.box64rc\n");
        sb.append("# engine=").append(engine.trim()).append(" exe=").append(exe).append('\n');
        sb.append('[').append(exe).append("]\n");
        sb.append(body);
        return sb.toString();
    }

    /** 纯函数：解析模板里指定引擎段，返回若干 {@code KEY=VALUE\n}。 */
    static String parseEngineSection(String conf, String engine) {
        String want = engine.trim().toLowerCase();
        if (want.isEmpty()) return "";

        boolean inSection = false;
        StringBuilder out = new StringBuilder();
        for (String rawLine : conf.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                String name = line.substring(1, line.length() - 1).trim().toLowerCase();
                inSection = name.equals(want);
                continue;
            }
            if (!inSection) continue;

            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) continue;
            if (!key.startsWith("BOX64_")) continue;
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) continue;
            out.append(key).append('=').append(value).append('\n');
        }
        return out.toString();
    }

    /**
     * 从游戏目录向上找带 {@code galgame_overlay.json} 的容器根目录；找不到返回 null。
     * 只按「overlay 存在」判定，不依赖目录名，避免容器被改名后失配。
     */
    static File findContainerRoot(File start) {
        File dir = start;
        for (int i = 0; i < MAX_WALK_UP && dir != null; i++) {
            if (new File(dir, "galgame_overlay.json").isFile()) return dir;
            dir = dir.getParentFile();
        }
        return null;
    }

    private static boolean isSafeExeName(String exe) {
        for (int i = 0; i < exe.length(); i++) {
            char c = exe.charAt(i);
            if (c == '[' || c == ']' || c == '\n' || c == '\r' || c == '#' || c == ';') return false;
        }
        return true;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7f) return false;
        }
        return true;
    }

    private static String readAsset(Context context, String name) {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
        catch (Throwable t) {
            return "";
        }
    }

    private static String readText(File f) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            return new String(data, StandardCharsets.UTF_8);
        }
        catch (Throwable t) {
            return null;
        }
    }
}
