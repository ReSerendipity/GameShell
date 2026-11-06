package com.winlator.galgame;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 原生引擎游戏的「收纳库」（2026-10-25 第七轮，健壮性决策）。
 *
 * <p>背景：B 路由（Kirikiroid2 / Ren'Py 原生引擎）此前直接<b>引用</b>用户选中的源目录，
 * 用户在库外的任何改动（改名/移动/部分删除/被相册类工具清理）都会让游戏损坏且无从恢复。
 * 现改为导入时把源目录完整复制进应用私有目录，游戏本体与容器路线一样由本程序全权管理。
 *
 * <p>存放位置：{@code getFilesDir()/galgame-native/<gameId>}——应用私有空间，
 * 卸载即随应用清除，其他 App 与用户文件管理器均不可见不可写。
 *
 * <p>同时收敛了此前的一个健壮性隐患：KR2 的 {@code getWritablePath()} 会跟随
 * {@code .preference} 配置在<b>游戏目录</b>里创建 {@code files/}、{@code .preference/}
 * 运行时目录（第五轮已证它们会被再次导入误判成游戏）。收纳库同样持有这两个目录的
 * 创建权，彻底杜绝运行时产物写进用户可见空间。
 */
public final class GalgameNativeStore {

    private static final String STORE_DIR = "galgame-native";

    /**
     * 收纳完成标记（TD-19）。
     *
     * <p>此前 `store()` 的判断是「目标目录非空即已收纳、直接返回」——磁盘满、进程被杀、
     * 权限中途被收留下的<b>半拷贝</b>于是被当成成品，库里卡片能点、进去缺文件。
     * 现在拷贝成功后写这个标记（文件数 + 字节数），再次调用时把标记与实际树对比，
     * 对不上就整棵删掉重拷。
     *
     * <p>为什么整棵重拷而不是续拷：续拷要逐文件比对内容/长度才能保证一致，成本与复杂度
     * 都高于「删掉再来一次」，而重复导入同一游戏本来就是低频路径。
     */
    private static final String COMPLETE_MARKER = ".galgame-store.complete";

    private GalgameNativeStore() {}

    /** 收纳库根（应用私有）。 */
    public static File root(Context context) {
        return new File(context.getFilesDir(), STORE_DIR);
    }

    /** 某游戏的收纳目录。 */
    public static File gameDir(Context context, String gameId) {
        return new File(root(context), gameId);
    }

    /**
     * 收纳（复制）一个游戏目录。目标已存在时视为已收纳，直接返回现有目录
     * （幂等；重复导入同一游戏不会重复占空间——调用方应先查 {@link #isStored}）。
     *
     * @return 收纳后的游戏目录
     * @throws IOException 复制失败（磁盘满/源不可读）；已复制的部分保留，便于重试续拷
     */
    public static File store(Context context, String gameId, File sourceDir,
                             ImportFlow.ProgressListener progress) throws IOException {
        File target = gameDir(context, gameId);
        if (isComplete(target)) {
            return target;
        }
        if (target.isDirectory() && target.list() != null && target.list().length > 0) {
            // 半拷贝（或无标记的旧收纳）：删掉重来，不能拿它当成品
            com.winlator.core.FileUtils.delete(target);
        }
        if (sourceDir == null || !sourceDir.isDirectory()) {
            throw new IOException("源目录不存在: " + sourceDir);
        }
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("无法创建 " + target);
        }
        long total = countBytes(sourceDir);
        long[] state = {0, System.currentTimeMillis()};
        copyDir(sourceDir, target, total, state, progress);
        // TD-19：拷完先自证完整性（源/目标的文件数与字节数必须一致），再写完成标记
        ImportFlow.verifyCopyComplete(sourceDir, target, "收纳");
        writeMarker(target);
        if (progress != null) progress.onProgress(total, total);
        return target;
    }

    /**
     * 收纳目录是否「完整可用」：目录存在 + 有完成标记 + 标记与实际树一致。
     *
     * <p>没有标记的旧收纳一律视为不完整：宁可重拷一次，也不把可能缺文件的老目录继续用。
     */
    public static boolean isComplete(File target) {
        if (target == null || !target.isDirectory()) return false;
        File marker = new File(target, COMPLETE_MARKER);
        if (!marker.isFile()) return false;
        String text;
        try {
            text = com.winlator.core.FileUtils.readString(marker);   // 单行 "文件数 字节数"
        } catch (Exception e) {
            return false;
        }
        if (text == null) return false;
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) return false;
        long[] actual = ImportFlow.countTree(target);
        if (actual[0] < 0) return false;
        // 标记文件本身要计入实际树，比对时排除
        actual = new long[] {actual[0] - 1, actual[1] - marker.length()};
        try {
            return Long.parseLong(parts[0]) == actual[0] && Long.parseLong(parts[1]) == actual[1];
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void writeMarker(File target) throws IOException {
        long[] stats = ImportFlow.countTree(target);
        if (stats[0] < 0) throw new IOException("收纳目录不可读: " + target);
        File marker = new File(target, COMPLETE_MARKER);
        try (FileOutputStream out = new FileOutputStream(marker)) {
            out.write((stats[0] + " " + stats[1] + "\n").getBytes("UTF-8"));
        }
    }

    public static boolean isStored(Context context, String gameId) {
        File dir = gameDir(context, gameId);
        return dir.isDirectory() && dir.list() != null && dir.list().length > 0;
    }

    /** 递归删除收纳目录（彻底删除用；应用私有空间内，无需权限）。 */
    public static boolean remove(Context context, String gameId) {
        return com.winlator.core.FileUtils.delete(gameDir(context, gameId));
    }

    private static long countBytes(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long sum = 0;
        for (File f : files) {
            if (f.isDirectory()) sum += countBytes(f);
            else sum += Math.max(0, f.length());
        }
        return sum;
    }

    private static void copyDir(File src, File dst, long total, long[] state,
                                ImportFlow.ProgressListener progress) throws IOException {
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) {
                if (!t.exists() && !t.mkdirs()) throw new IOException("无法创建 " + t);
                copyDir(f, t, total, state, progress);
            }
            else copyFile(f, t, total, state, progress);
        }
    }

    private static void copyFile(File src, File dst, long total, long[] state,
                                 ImportFlow.ProgressListener progress) throws IOException {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (progress != null && total > 0) {
                    state[0] += n;
                    long now = System.currentTimeMillis();
                    if (now - state[1] >= 200) {
                        state[1] = now;
                        progress.onProgress(state[0], total);
                    }
                }
            }
        }
    }
}
