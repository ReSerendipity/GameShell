package com.winlator.galgame;

import com.winlator.container.Container;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * P0-4：把启动黑盒拆成三个客观里程碑，并落到容器目录供诊断读取。
 *
 * <p>背景：TD-16（窗口映射后静止 8-40 s）、TD-13（按键到不了 Wine）都表现为「起来了，
 * 但不知道卡在哪」；上游失败时统一弹回库页，用户与日志都拿不到分段信息。termux-box 的做法
 * 是等 {@code .virgl_test} 就绪文件而不是固定 sleep——判据必须是客观事件，不是耗时。
 *
 * <p>本类只在**事件真的发生**时打点：
 * <ul>
 *   <li>{@link #X_SERVER_READY}：XConnector 已 listen（{@code XServerComponent.start} 返回）；</li>
 *   <li>{@link #WINE_STARTED}：wine 进程已 fork 出 pid（{@code ProcessHelper.exec} 返回 &gt; 0）；</li>
 *   <li>{@link #FIRST_FRAME}：X 侧第一个窗口收到内容更新（{@code onUpdateWindowContent}）；</li>
 *   <li>{@link #EXITED} / {@link #STOPPED}：进程退出（带 status）或被主动停止。</li>
 * </ul>
 *
 * <p>落点：{@code <containerRoot>/galgame_launch_session.json}。只在 galgame 容器的启动路线
 * 被 {@link GalgameLaunchHelper#injectExecPath} 激活；普通容器没有 active session，所有
 * {@code mark()} 调用立即返回，等于零行为变化。
 *
 * <p>为什么不用「固定等待 N 秒」：慢机与快机差距极大（TD-16 实测 8-40 s），任何阈值都会
 * 误杀；本类只记录「有没有发生」，阈值判断留给诊断层（那里也只是提示，不阻断）。
 */
public final class GalgameLaunchSession {

    public static final String FILE_NAME = "galgame_launch_session.json";

    public static final String EXEC_INJECTED = "EXEC_INJECTED";
    public static final String X_SERVER_READY = "X_SERVER_READY";
    public static final String WINE_STARTED = "WINE_STARTED";
    public static final String FIRST_FRAME = "FIRST_FRAME";
    public static final String EXITED = "EXITED";
    public static final String STOPPED = "STOPPED";

    /** 首帧迟迟不到的提示阈值（仅诊断提示，不阻断启动）。 */
    public static final long FIRST_FRAME_HINT_MS = 20000L;

    private static final Object LOCK = new Object();
    private static final Set<String> MARKED = new HashSet<>();
    /** 首帧门闩：wine 未起时 onUpdateWindowContent 可能每帧触发，不能每次都读文件。 */
    private static volatile boolean wineStarted;

    private static volatile File currentFile;

    private GalgameLaunchSession() {}

    /** 新一次启动开始：重建会话文件并记录注入的 exe。 */
    public static void begin(Container container, String exePath) {
        if (container == null || container.getRootDir() == null) return;

        synchronized (LOCK) {
            MARKED.clear();
            wineStarted = false;
            currentFile = new File(container.getRootDir(), FILE_NAME);
            long now = System.currentTimeMillis();

            JSONObject o = new JSONObject();
            try {
                o.put("session", now);
                o.put("exe", exePath != null ? exePath : "");
                o.put("started_at", now);
                o.put("stages", new JSONArray());
            }
            catch (Throwable ignored) {}
            write(currentFile, o);
        }
        mark(EXEC_INJECTED, exePath != null ? "exe=" + exePath : "");
    }

    /** 记录一个阶段；重复阶段只记第一次，无活动会话时零动作。 */
    public static void mark(String stage) {
        mark(stage, "");
    }

    /**
     * 首帧专用：只有 wine 已经启动后才记 FIRST_FRAME。
     *
     * <p>X 服务先于 wine 起来时，窗口管理器可能已经上报过空白根窗口的内容更新；若照收，
     * 会把「X 画了个空桌面」当成「游戏出画面」，诊断就成了假阳性。这里用 WINE_STARTED
     * 是否已存在作为门闩。
     */
    public static void markFirstFrame() {
        if (!wineStarted) return;
        synchronized (LOCK) {
            if (MARKED.contains(FIRST_FRAME)) return;
        }
        mark(FIRST_FRAME);
    }

    /** 记录一个阶段并附带一行说明（如 {@code status=0}）。 */
    public static void mark(String stage, String detail) {
        if (stage == null || stage.isEmpty()) return;

        File file = currentFile;
        if (file == null) return;

        synchronized (LOCK) {
            if (!MARKED.add(stage)) return;
            if (WINE_STARTED.equals(stage)) wineStarted = true;
            if (!file.isFile()) {
                MARKED.remove(stage);
                return;
            }

            try {
                JSONObject o = read(file);
                if (o == null) {
                    MARKED.remove(stage);
                    return;
                }
                JSONArray stages = o.optJSONArray("stages");
                if (stages == null) {
                    stages = new JSONArray();
                    o.put("stages", stages);
                }
                long now = System.currentTimeMillis();
                JSONObject s = new JSONObject();
                s.put("stage", stage);
                s.put("at", now);
                s.put("since_ms", Math.max(0L, now - o.optLong("started_at", now)));
                if (detail != null && !detail.isEmpty()) s.put("detail", detail);
                stages.put(s);
                write(file, o);
            }
            catch (Throwable t) {
                MARKED.remove(stage);
            }
        }
    }

    /** 清空活动会话（容器被删除/重置时调用，避免下个容器误读）。 */
    public static void clear() {
        synchronized (LOCK) {
            MARKED.clear();
            currentFile = null;
        }
    }

    /** 读取指定容器的会话文件；缺失或损坏返回 null。 */
    public static JSONObject readFor(Container container) {
        if (container == null || container.getRootDir() == null) return null;
        return read(new File(container.getRootDir(), FILE_NAME));
    }

    private static JSONObject read(File file) {
        if (file == null || !file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 12];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static void write(File file, JSONObject o) {
        if (file == null || o == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();

        File tmp = new File(parent, file.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(o.toString(2));
        }
        catch (Throwable t) {
            return;
        }

        // 同目录 rename 在 ext4/f2fs 上是原子的；失败则退回直接覆盖（诊断可容忍短暂半文件）
        if (!tmp.renameTo(file)) {
            try (Writer w = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                w.write(o.toString(2));
            }
            catch (Throwable ignored) {}
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }
}
