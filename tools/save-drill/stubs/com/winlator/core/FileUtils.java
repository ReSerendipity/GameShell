package com.winlator.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * save-drill 演练专用 FileUtils：签名与 ci/stubs/com/winlator/core/FileUtils.java 一致，
 * 但 {@link #delete(File)} 做真实递归删除——CI 桩的 delete 是空操作（返回 false），
 * 无法驱动自动备份滚动清理（pruneAutoBackups）的演练。
 * 其余方法保持与 CI 桩相同的最小实现（演练路径用不到）。
 */
public class FileUtils {
    public static boolean copy(File srcFile, File dstFile) { return false; }
    public static void copy(android.content.Context context, String assetFile, File dstFile) {}
    public static void chmod(File file, int mode) {}
    public static boolean isSymlink(File file) { return false; }
    public static void symlink(File linkTarget, File linkFile) {}
    public static void symlink(String linkTarget, String linkFile) {}

    /** 真实递归删除（演练专用；先删子项再删目录本身，任一步失败返回 false）。 */
    public static boolean delete(File targetFile) {
        if (targetFile == null || !targetFile.exists()) return false;
        Path root = targetFile.toPath();
        // 自底向上删，目录空了才删得掉
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return !targetFile.exists();
    }

    public static String readString(File file) { return null; }
}
