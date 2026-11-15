import com.winlator.galgame.GalgameLanguageDetector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GalgameLanguageDetector 的纯 JVM 回归（无需 Android/设备）。
 *
 * 覆盖点（口径 = TD-23「只认正证据」）：
 *  - 显式中文标记命中（汉化/简中/chs/cht/zh-cn/chinese/cn，忽略大小写，ASCII 标记须成词）-> zh_CN
 *  - 简体专有字（日文不用的简化字形，如「说」）-> zh_CN
 *  - 纯汉字（统一表意 / 扩展 A / 扩展 B 代理对）**不再**当证据：detectLocaleOrNull 返回 null，
 *    兼容入口 detectLocale 落回 ja_JP
 *  - 无信号/纯假名/空输入 -> 默认 ja_JP（保守策略：不误伤日语游戏）
 *
 * 用法：由 tools/run_detect_test.sh 统一编译运行（复制真实源码后编译，测的始终是当前代码）。
 */
public class LanguageDetectorTest {

    static int pass = 0, fail = 0;
    static Path root;

    static void check(String name, File folder, String exe, String expect) {
        String got = GalgameLanguageDetector.detectLocale(folder, exe);
        boolean ok = got.equals(expect);
        if (ok) { pass++; System.out.println("  PASS  " + name + " -> " + got); }
        else    { fail++; System.out.println("  FAIL  " + name + " -> got " + got + ", expect " + expect); }
    }

    /** TD-23 三态契约：无证据时 detectLocaleOrNull 必须为 null，兼容入口必须落回 ja_JP。 */
    static void checkNoEvidence(String name, File folder, String exe) {
        String tri = GalgameLanguageDetector.detectLocaleOrNull(folder, exe);
        String legacy = GalgameLanguageDetector.detectLocale(folder, exe);
        boolean ok = tri == null && GalgameLanguageDetector.JA_LOCALE.equals(legacy);
        if (ok) { pass++; System.out.println("  PASS  " + name + " -> null/" + legacy); }
        else    { fail++; System.out.println("  FAIL  " + name + " -> got " + tri + "/" + legacy
                + ", expect null/" + GalgameLanguageDetector.JA_LOCALE); }
    }

    public static void main(String[] args) throws Exception {
        root = Files.createTempDirectory("lang_detect_test");
        File base = root.toFile();
        System.out.println("temp root: " + base + "\n");

        // 1) 空输入：null 文件夹 -> 默认日语
        check("null folder", null, "game.exe", GalgameLanguageDetector.JA_LOCALE);

        // 2) 强信号：路径/文件夹名（不区分大小写）
        check("hint hanhua",  new File(base, "某游戏 汉化补丁版"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint jianzhong", new File(base, "某游戏 简中"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint chs exe", new File(base, "SomeGame"), "patch.chs.exe", GalgameLanguageDetector.ZH_LOCALE);
        check("hint cht dir", new File(base, "CHT_release"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint zh-cn",   new File(base, "zh-CN pack"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint chinese", new File(base, "chinese edition"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint cn dir",  new File(base, "cn_release"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("hint cn exe (real device case HANA9cn.EXE)", new File(base, "Simple"), "HANA9cn.EXE", GalgameLanguageDetector.ZH_LOCALE);

        // 3) 简体专有字（日文不用的简化字形）仍是正证据
        check("han simplified-only 说", new File(base, "游戏 说"), null, GalgameLanguageDetector.ZH_LOCALE);
        check("han simplified-only 们", new File(base, "我们的"), null, GalgameLanguageDetector.ZH_LOCALE);

        // 4) 纯汉字（统一表意 / 扩展 A / 扩展 B 代理对）自 TD-23 起不再当证据
        checkNoEvidence("han unified",  new File(base, "逆転裁判"), null);
        checkNoEvidence("han ext-A",    new File(base, "\u3400\u3401test"), null);
        checkNoEvidence("han ext-B surrogate", new File(base, "\uD842\uDFB7野家"), null);

        // 5) 负例：无信号 / 纯假名 -> 默认日语
        check("no signal",    new File(base, "PlainGame"), "game.exe", GalgameLanguageDetector.JA_LOCALE);
        check("kana only",    new File(base, "とりの あくど"), "game.exe", GalgameLanguageDetector.JA_LOCALE);
        check("null exe",     new File(base, "Simple"), null, GalgameLanguageDetector.JA_LOCALE);
        check("chen no false hit", new File(base, "chen game"), "chen.exe", GalgameLanguageDetector.JA_LOCALE);
        check("cnc no false hit", new File(base, "cnc pack"), null, GalgameLanguageDetector.JA_LOCALE);

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }
}
