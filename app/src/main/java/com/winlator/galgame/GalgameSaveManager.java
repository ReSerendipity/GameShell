package com.winlator.galgame;

import android.util.Log;

import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.core.WineRegistryEditor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * B4 / P4 存档持久化（Plan Part V）。
 * 全新模块，**绝不改 Container.DEFAULT_DRIVES**（零改铁律 §VIII.4）。
 *
 * <ul>
 *   <li><b>S: 常驻存档盘</b> → {@code INTERNAL_STORAGE/galgame-saves/<game>}
 *       = {@code /data/data/com.winlator/storage/galgame-saves/<game>}
 *       容器外 → 容器重置 / 重装存活（AC-12 / AC-13）。</li>
 *   <li><b>Shell-Folder 类</b>（Documents / AppData / Saved Games）：写 Wine
 *       {@code User Shell Folders} 注册表 → S:（P4）。</li>
 *   <li><b>便携存档类</b>：游戏目录存档子目录「先搬后链」→ S:（每次导入重建，P4；
 *       RK-09 对硬编码绝对路径引擎的兜底）。</li>
 *   <li><b>导出</b>（卸载/换机安全）：目录复制 S: → {@code Downloads/galgame-backup/<game>-<ts>}
 *       （AC-14）。</li>
 * </ul>
 *
 * 所有注册表/符号链接操作仅用官方公开 API（{@link WineRegistryEditor} / {@link FileUtils}）。
 */
public final class GalgameSaveManager {

    private static final String TAG = "GameShell: saves";

    /** 容器外存档根（对应 S: 盘）。 */
    public static final String SAVE_ROOT =
            "/data/data/com.winlator/storage/galgame-saves";

    /** 常见引擎的「便携存档」子目录名（大小写/命名不一，逐一匹配）。 */
    private static final String[] PORTABLE_SAVE_DIRS = {
            "savedata", "save", "saves", "Savedata", "sav", "SaveData",
            "ysp_save", "save data", "savedata_", "Save", "SAVE"
    };

    private static final String SHELL_FOLDERS_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders";

    private final String gameId;
    private final File gameSaveDir;
    private boolean autoBackup = true;

    public GalgameSaveManager(String gameId) {
        this(gameId, new File(SAVE_ROOT, gameId));
    }

    /**
     * 测试缝隙（tools/save-drill 存档恢复演练用，M10）：S: 盘根可注入，
     * 让纯 JVM 演练不触碰真实存档路径（真实构造器永远用 {@link #SAVE_ROOT}）。
     */
    GalgameSaveManager(String gameId, File saveDir) {
        if (gameId == null || gameId.trim().isEmpty()) {
            // 空 id 会让 S: 盘目录退化成 SAVE_ROOT 根：所有游戏共用一份存档，
            // 导出/恢复互相串档（2026-10-26 评估 TD-24）。宁可当场报错。
            throw new IllegalArgumentException(
                    "gameId 不能为空：空的 gameId 会让存档目录退化成 S: 盘根，导致跨游戏串档");
        }
        this.gameId = gameId;
        this.gameSaveDir = saveDir;
    }

    // ---- 基础 ----

    /** 确保 S: 盘目录存在（导入流调用）。 */
    public boolean ensureSaveDir() {
        return gameSaveDir.exists() || gameSaveDir.mkdirs();
    }

    /** 取得该游戏的 S: 盘路径（供导入流拼 drives 字段用）。 */
    public String savePath() {
        return gameSaveDir.getAbsolutePath();
    }

    /** S: 盘驱动器描述串（"S:<绝对路径>"），拼进 Container.drives。 */
    public String saveDriveSpec() {
        return "S:" + savePath();
    }

    public File gameSaveDir() {
        return gameSaveDir;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public void setAutoBackup(boolean on) {
        this.autoBackup = on;
    }

    // ---- P4-1：Shell Folder 重定向（Documents / AppData / Saved Games → S:）----

    /**
     * 把 Wine 的 User Shell Folders 重定向到 S:，使「文档类」存档落到常驻盘。
     * 同时建好 S: 上的对应子目录。返回是否成功。
     */
    public boolean redirectShellFolders(Container container) {
        if (container == null) return false;
        File userReg = new File(container.getRootDir(), ".wine/user.reg");

        String[][] folders = {
                {"Personal", "Documents"},
                {"My Documents", "Documents"},
                {"AppData", "AppData\\Roaming"},
                {"Saved Games", "SavedGames"},
        };

        try {
            if (!ensureSaveDir()) return false;
            for (String[] f : folders) {
                File sub = new File(gameSaveDir, f[1].replace("\\", "/"));
                if (!sub.isDirectory()) sub.mkdirs();
            }

            try (WineRegistryEditor reg = new WineRegistryEditor(userReg)) {
                for (String[] f : folders) {
                    reg.setStringValue(SHELL_FOLDERS_KEY, f[0], "S:\\" + f[1]);
                }
            }
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    // ---- P4-2：便携存档「先搬后链」 ----

    /**
     * 扫描游戏目录下的常见存档子目录，把内容搬到 S: 后，用符号链接指回 S:。
     * 采用「先搬后链」以避免丢档（对比直接替换为空链接）。
     *
     * @return 成功建立的符号链接数
     */
    public int symlinkPortableSaves(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory() || !ensureSaveDir()) return 0;

        int linked = 0;
        for (String name : PORTABLE_SAVE_DIRS) {
            File dir = new File(gameDir, name);
            if (!dir.isDirectory()) continue;
            if (FileUtils.isSymlink(dir)) continue;   // 已链，跳过

            File target = new File(gameSaveDir, name);
            if (!target.isDirectory() && !target.mkdirs()) continue;   // 目标建不出就别动源目录

            try {
                // 先搬：同名不同内容以游戏目录为准，旧副本改名 .bak-<ts> 保留（不直接覆盖）
                if (!mergeInto(dir, target)) {
                    // 合并不完整（拷不动/改名失败）→ 跳过链接，源目录原样留着，宁可没链上也不丢档
                    Log.w(TAG, "存档合并不完整，跳过 S: 链接: " + dir);
                    continue;
                }
                if (!FileUtils.delete(dir)) {
                    Log.w(TAG, "源存档目录删不掉，跳过 S: 链接: " + dir);
                    continue;
                }
                FileUtils.symlink(target, dir);
                if (!FileUtils.isSymlink(dir)) {
                    // 上游 symlink 吞掉 ErrnoException：链没建成就把存档拷回去，宁可多占一份
                    mergeInto(target, dir);
                    Log.w(TAG, "符号链接创建失败，已把存档拷回游戏目录: " + dir);
                    continue;
                }
                linked++;
            }
            catch (Exception e) {
                Log.w(TAG, "便携存档迁移失败: " + dir, e);
                if (!dir.exists()) mergeInto(target, dir);
            }
        }
        return linked;
    }

    // ---- 备份 / 恢复 ----

    /** 默认导出目录：Download/galgame-backup。 */
    public static File defaultBackupDir() {
        return new File(com.winlator.core.AppUtils.DIRECTORY_DOWNLOADS, "galgame-backup");
    }

    // ---- 自动备份（M10 P0 止血，2026-11-05）----

    /** 自动备份滚动保留份数（按 gameId 逐游戏清理，只清带 {@link #AUTO_BACKUP_MARKER} 的目录）。 */
    static final int AUTO_BACKUP_KEEP = 5;

    /** 自动备份目录内标记文件名（区分自动快照与用户手动导出，滚动清理只认带标记的）。 */
    static final String AUTO_BACKUP_MARKER = ".autobackup";

    /** 自动备份单线程执行器（串行，避免连续启动两局时对同一 S: 盘并发拷贝）。 */
    private static final java.util.concurrent.ExecutorService AUTO_BACKUP_IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    /**
     * 自动备份调用点（M10：此前 {@code autoBackup} 字段无任何调用点，备份只有用户
     * 手动入口）。游戏启动成功后由启动入口调用，把 S: 盘当前存档快照到
     * {@code Downloads/galgame-backup/<gameId>-<ts>/}。
     *
     * <ul>
     *   <li><b>异步串行</b>：单线程执行器，绝不阻塞/打断启动路径；</li>
     *   <li><b>恢复可见</b>：与手动导出同目录同名规范，恢复对话框无需改动即可列出自动快照；</li>
     *   <li><b>可清理</b>：快照内写 {@value #AUTO_BACKUP_MARKER} 标记，滚动保留
     *       {@value #AUTO_BACKUP_KEEP} 份，手动导出（无标记）永不被自动清理；</li>
     *   <li><b>失败静默</b>：备份失败只记日志，游戏照常玩——备份是保险，不是门槛。</li>
     * </ul>
     */
    public static void autoBackupAsync(final String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) return;
        AUTO_BACKUP_IO.execute(new Runnable() {
            @Override
            public void run() {
                GalgameSaveManager m = new GalgameSaveManager(gameId);
                if (!m.autoBackup) return;
                boolean ok = m.exportBackup(defaultBackupDir(), true);
                int pruned = ok ? pruneAutoBackups(defaultBackupDir(), gameId) : 0;
                Log.i(TAG, "自动备份 gameId=" + gameId + " ok=" + ok + " pruned=" + pruned);
            }
        });
    }

    /** 滚动清理：只清带自动备份标记的本游戏快照，保留最新 {@value #AUTO_BACKUP_KEEP} 份。 */
    static int pruneAutoBackups(File backupRoot, String gameId) {
        File[] all = backupRoot.listFiles();
        if (all == null) return 0;
        String prefix = gameId + "-";
        List<File> autos = new ArrayList<>();
        for (File f : all) {
            if (f.isDirectory() && f.getName().startsWith(prefix)
                    && new File(f, AUTO_BACKUP_MARKER).isFile()) {
                autos.add(f);
            }
        }
        // 目录名以秒级时间戳结尾，字典序 = 时间序；倒序后前 KEEP 份保留
        autos.sort((a, b) -> b.getName().compareTo(a.getName()));
        int pruned = 0;
        for (int i = AUTO_BACKUP_KEEP; i < autos.size(); i++) {
            if (FileUtils.delete(autos.get(i))) pruned++;
            else Log.w(TAG, "过期自动备份清理失败（保留现场）: " + autos.get(i));
        }
        return pruned;
    }

    /**
     * 导出存档备份（AC-14）。落地为「目录复制」到 destDir/<gameId>-<ts>/。
     * 不抛异常，失败返回 false。（tar 需设备端二进制，留作后续增强。）
     */
    public boolean exportBackup(File destDir) {
        return exportBackup(destDir, false);
    }

    /**
     * 导出实现。auto=true（自动备份路径）时在快照内写 {@value #AUTO_BACKUP_MARKER}
     * 标记，供滚动清理识别；手动导出（auto=false）不写标记、永不被自动清理。
     * 包内可见：供 {@link #autoBackupAsync} 与 save-drill 演练夹具复用。
     */
    boolean exportBackup(File destDir, boolean auto) {
        if (!ensureSaveDir() || destDir == null) return false;
        if (!destDir.isDirectory() && !destDir.mkdirs()) return false;

        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        File target = new File(destDir, gameId + "-" + ts);
        try {
            copyDirectory(gameSaveDir, target);
            if (auto && !new File(target, AUTO_BACKUP_MARKER).createNewFile()) {
                Log.w(TAG, "自动备份标记写入失败（不影响快照可用性）: " + target);
            }
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    /** 从备份目录恢复存档到 S: 盘（覆盖式合并）。 */
    public boolean restoreBackup(File backupDir) {
        if (backupDir == null || !backupDir.isDirectory()) return false;
        ensureSaveDir();
        try {
            copyDirectory(backupDir, gameSaveDir);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    // ---- 文件工具 ----

    /** 把 src 内容合并进 dst（不覆盖 dst 已存在的同名文件）。 */
    /** 把 src 合并进 dst；返回是否每个文件都已在 dst 落地（false = 调用方不该删 src）。 */
    private static boolean mergeInto(File src, File dst) {
        if (!dst.isDirectory() && !dst.mkdirs()) return false;
        File[] files = src.listFiles();
        if (files == null) return false;
        boolean ok = true;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) {
                if (!mergeInto(f, t)) ok = false;
                continue;
            }
            if (t.isFile() && t.length() == f.length()) continue;      // 已有等价副本
            if (t.exists()) {
                // 同名不同内容：源（游戏目录里的活档）为准，旧的目标副本改名保留
                File bak = new File(dst, f.getName() + ".bak-" + System.currentTimeMillis());
                if (!t.renameTo(bak)) { ok = false; continue; }
            }
            if (!copyFile(f, t)) ok = false;
        }
        return ok;
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        if (!dst.isDirectory() && !dst.mkdirs()) throw new IOException("无法创建 " + dst);
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) copyDirectory(f, t);
            else if (!copyFile(f, t)) throw new IOException("复制失败: " + f);
        }
    }

    private static boolean copyFile(File src, File dst) {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "复制存档文件失败: " + src + " -> " + dst, e);
            return false;
        }
    }

    private GalgameSaveManager() { throw new AssertionError(); }
}
