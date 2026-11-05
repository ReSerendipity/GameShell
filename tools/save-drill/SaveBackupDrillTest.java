package com.winlator.galgame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * M10 存档备份与恢复演练（纯 JVM，无需 Android 设备/模拟器）。
 *
 * <p>演练内容（对平判据见各步输出）：
 * <ol>
 *   <li><b>导出</b>：S: 盘样本存档 → exportBackup(dest, auto=true)；
 *       判据 = 快照内文件集合与 S: 盘一致（含 .autobackup 标记）且逐文件字节相等；</li>
 *   <li><b>清空/损坏</b>：删 1 个存档文件、改写 1 个、混入 1 个陌生文件；</li>
 *   <li><b>恢复</b>：restoreBackup(快照)；
 *       判据 = 被删/被改文件与快照逐文件字节相等（覆盖式合并：陌生文件按语义保留）；</li>
 *   <li><b>滚动清理</b>：8 份自动快照 → pruneAutoBackups 保留最新 5 份；
 *       判据 = 恰剪 3 份、无标记的手动导出永不被剪。</li>
 * </ol>
 *
 * <p>同包声明（com.winlator.galgame）：演练需要触达包内可见的
 * {@code exportBackup(File, boolean)} / {@code pruneAutoBackups}。
 */
public class SaveBackupDrillTest {

    static int pass = 0, fail = 0;
    static final List<String> failures = new ArrayList<>();

    static final String GAME_ID = "gs_drill_game";

    public static void main(String[] args) throws Exception {
        File workspace = Files.createTempDirectory("save-drill-").toFile();
        File saveRoot = new File(workspace, "galgame-saves");
        File backupRoot = new File(workspace, "backup");

        System.out.println("[save-drill] timestamp=" +
                ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        System.out.println("[save-drill] workspace=" + workspace.getAbsolutePath());
        System.out.println("[save-drill] game_id=" + GAME_ID);

        GalgameSaveManager m = new GalgameSaveManager(GAME_ID, new File(saveRoot, GAME_ID));

        // ---- 造样本存档（S: 盘布局：便携存档 + Shell Folder 目录 + 嵌套子目录）----
        File saveDir = m.gameSaveDir();
        if (!saveDir.isDirectory() && !saveDir.mkdirs()) throw new IOException("建 S: 盘目录失败");
        byte[] saveDat = pattern(4096, 7, 251);
        byte[] globalDat = pattern(1024, 13, 253);
        byte[] entryBin = pattern(512, 29, 241);
        byte[] progressSav = pattern(2048, 11, 247);
        byte[] configIni = ("engine=siglus\nsave_slot=3\nnotes=演练样本\n").getBytes("UTF-8");
        writeFile(new File(saveDir, "Save/save.dat"), saveDat);
        writeFile(new File(saveDir, "Save/global.dat"), globalDat);
        writeFile(new File(saveDir, "Save/deep/level2/entry.bin"), entryBin);
        writeFile(new File(saveDir, "Documents/config.ini"), configIni);
        writeFile(new File(saveDir, "SavedGames/progress.sav"), progressSav);
        int sampleFiles = countFiles(saveDir);
        long sampleBytes = totalBytes(saveDir);
        System.out.println("[save-drill] samples=" + sampleFiles + " files / " + sampleBytes + " bytes");
        check("sample-tree-5-files", sampleFiles == 5);

        // ---- 步骤 1：导出（自动备份路径，写标记）----
        boolean exported = m.exportBackup(backupRoot, true);
        check("step1-export-ok", exported);
        File snapshot = exported ? soleBackup(backupRoot) : null;
        check("step1-snapshot-dir-exists", snapshot != null);
        check("step1-autobackup-marker", snapshot != null
                && new File(snapshot, GalgameSaveManager.AUTO_BACKUP_MARKER).isFile());
        check("step1-bytes-identical", snapshot != null && sameTree(saveDir, snapshot));

        // ---- 步骤 2：清空/损坏（删 1、改写 1、混入 1）----
        if (new File(saveDir, "Save/save.dat").delete()) {
            System.out.println("[save-drill] corrupt: deleted Save/save.dat");
        }
        writeFile(new File(saveDir, "Save/global.dat"), pattern(1024, 99, 199));
        System.out.println("[save-drill] corrupt: mutated Save/global.dat");
        writeFile(new File(saveDir, "stray.tmp"), "陌生文件".getBytes("UTF-8"));
        System.out.println("[save-drill] corrupt: added stray.tmp");
        check("step2-corrupt-applied",
                !new File(saveDir, "Save/save.dat").exists()
                        && !sameBytes(new File(saveDir, "Save/global.dat"), globalDat));

        // ---- 步骤 3：恢复 ----
        boolean restored = m.restoreBackup(snapshot);
        check("step3-restore-ok", restored);
        check("step3-restored-bytes-identical",
                sameBytes(new File(saveDir, "Save/save.dat"), saveDat)
                        && sameBytes(new File(saveDir, "Save/global.dat"), globalDat)
                        && sameBytes(new File(saveDir, "Save/deep/level2/entry.bin"), entryBin)
                        && sameBytes(new File(saveDir, "Documents/config.ini"), configIni)
                        && sameBytes(new File(saveDir, "SavedGames/progress.sav"), progressSav));
        check("step3-merge-semantics-stray-kept", new File(saveDir, "stray.tmp").isFile());

        // ---- 步骤 4：滚动清理（8 份自动快照 → 保留最新 5，手动导出不动）----
        for (int i = 0; i < 7; i++) {
            File auto = new File(backupRoot, GAME_ID + "-100000" + i);
            auto.mkdirs();
            new File(auto, GalgameSaveManager.AUTO_BACKUP_MARKER).createNewFile();
            writeFile(new File(auto, "Save/save.dat"), saveDat);
        }
        File manual = new File(backupRoot, GAME_ID + "-999999"); // 手动导出样式：无标记
        manual.mkdirs();
        writeFile(new File(manual, "Save/save.dat"), saveDat);
        int pruned = GalgameSaveManager.pruneAutoBackups(backupRoot, GAME_ID);
        check("step4-pruned-count-3", pruned == 3);
        check("step4-oldest-3-auto-gone",
                !new File(backupRoot, GAME_ID + "-1000000").exists()
                        && !new File(backupRoot, GAME_ID + "-1000001").exists()
                        && !new File(backupRoot, GAME_ID + "-1000002").exists());
        check("step4-newest-auto-kept", new File(backupRoot, GAME_ID + "-1000006").isDirectory()
                && snapshot.isDirectory());
        check("step4-manual-export-untouched", manual.isDirectory());

        System.out.println("[save-drill] assertions: pass=" + pass + " fail=" + fail);
        for (String f : failures) System.out.println("[save-drill] FAILED: " + f);
        System.out.println("DRILL RESULT: " + (fail == 0 ? "PASS" : "FAIL"));
        if (fail > 0) System.exit(1);
    }

    // ---- 工具 ----

    static File soleBackup(File backupRoot) {
        File[] all = backupRoot.listFiles();
        if (all == null || all.length != 1 || !all[0].isDirectory()) return null;
        return all[0];
    }

    /** 对平判据实现：两侧相对文件集合一致且逐文件字节相等（快照侧跳过 .autobackup 标记，其由 step1 单独验证）。 */
    static boolean sameTree(File src, File dst) {
        List<File> a = new ArrayList<>(), b = new ArrayList<>();
        collect(src, a);
        collect(dst, b, GalgameSaveManager.AUTO_BACKUP_MARKER);
        if (a.size() != b.size()) return false;
        String base = src.getAbsolutePath();
        String baseB = dst.getAbsolutePath();
        for (File f : a) {
            String rel = f.getAbsolutePath().substring(base.length());
            File g = new File(baseB + rel);
            if (!sameContent(f, g)) return false;
        }
        return true;
    }

    static void collect(File dir, List<File> out) {
        collect(dir, out, null);
    }

    static void collect(File dir, List<File> out, String excludeName) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (excludeName != null && f.isFile() && f.getName().equals(excludeName)) continue;
            if (f.isDirectory()) collect(f, out, excludeName);
            else out.add(f);
        }
    }

    static boolean sameContent(File a, File b) {
        if (a == null || b == null || !a.isFile() || !b.isFile()) return false;
        try {
            return java.util.Arrays.equals(Files.readAllBytes(a.toPath()),
                    Files.readAllBytes(b.toPath()));
        } catch (IOException e) {
            return false;
        }
    }

    /** 文件内容与已知字节数组逐字节比对（对平判据用）。 */
    static boolean sameBytes(File f, byte[] expected) {
        if (f == null || !f.isFile()) return false;
        try {
            return java.util.Arrays.equals(Files.readAllBytes(f.toPath()), expected);
        } catch (IOException e) {
            return false;
        }
    }

    static byte[] pattern(int len, int mul, int mod) {
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = (byte) ((i * mul) % mod);
        return out;
    }

    static void writeFile(File f, byte[] data) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    static int countFiles(File dir) {
        List<File> out = new ArrayList<>();
        collect(dir, out);
        return out.size();
    }

    static long totalBytes(File dir) {
        long sum = 0;
        List<File> out = new ArrayList<>();
        collect(dir, out);
        for (File f : out) sum += f.length();
        return sum;
    }

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("[save-drill] " + name + " ... PASS");
        } else {
            fail++;
            failures.add(name);
            System.out.println("[save-drill] " + name + " ... FAIL");
        }
    }
}
