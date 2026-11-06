package com.winlator.galgame;

import java.io.File;
import java.util.Locale;

/**
 * 游戏语言检测（判据以「书写系统」为主，2026-11-06 重写）。
 *
 * <p>历史问题（TD-23）：旧实现把「名称里出现任意汉字」当成中文证据，而日文标题几乎必含
 * 汉字（`彼女`/`恋`/`学園`），于是日文 galgame 被判成 `zh_CN`，导致 LCID 936 + Shift-JIS
 * 文本乱码；{@code GalgameLocaleInjector.reapply} 又按同一判据去「纠正」本来正确的值。
 *
 * <p>新判据（只认正证据，认不出就交给调用方决定）：
 * <ol>
 *   <li><b>显式中文标记</b>：路径/名称/exe 里出现 {@code chs}/{@code cht}/{@code cn}（须成词）、
 *       {@code 简体}/{@code 简中}/{@code 漢化} 等 → 中文；</li>
 *   <li><b>假名</b>（平假名/片假名/半角片假名）→ 日语（决定性信号，中文汉化标题极少带假名）；</li>
 *   <li><b>谚文</b>（韩文）→ 无证据（本仓只有日中两套 locale，不硬塞中文）；</li>
 *   <li><b>简体专有字</b>（{@code 们}/{@code 这}/{@code 说}/{@code 时} 这类日文不使用的简化字形）→ 中文；</li>
 *   <li>其余（纯汉字但字形与日文共用、无假名、无标记）→ <b>无证据</b>。</li>
 * </ol>
 *
 * <p>与旧版的差别就在第 5 条：以前这里返回中文，现在返回 {@code null}。导入侧
 * {@link #detectLocale} 把 {@code null} 落回日语默认（与改动前一致）；重注入侧改用
 * {@link #detectLocaleOrNull}，遇到 {@code null} 保留容器已有值，不再瞎纠正。
 */
public final class GalgameLanguageDetector {

    private GalgameLanguageDetector() {}

    public static final String ZH_LOCALE = "zh_CN.UTF-8";
    public static final String JA_LOCALE = "ja_JP.UTF-8";

    /** 显式中文标记（中文/拉丁词），按子串匹配（中文词无词边界）。 */
    private static final String[] ZH_SUBSTRING_HINTS = {
            "简体", "简中", "繁體", "繁体", "漢化", "汉化", "中文",
            "chinese", "zhongwen", "hanhua", "zh-cn", "zh_cn", "zhcn", "zh-tw", "zh_tw"
    };

    /** 显式中文标记（ASCII 短标记），必须成「词」出现，避免 `scan`、`chen` 之类误命中。 */
    private static final String[] ZH_TOKEN_HINTS = {
            "chs", "cht", "cn"
    };

    /**
     * 简体专有字形：日文（常用/常用外汉字）**不使用**的简化字。
     * 只收字形互斥、日文完全不用的字；宁缺勿滥 —— 收错一个字就会把日文标题判成中文。
     * （刻意排除 `与`、`也`、`乱`、`重`、`点`、`想`、`承`、`装`、`誉`、`随`、`径` 这类
     * 日文同样在用的字形。）
     */
    private static final String SIMPLIFIED_ONLY =
            "们这说请让给时间电话语读书听写习认识记忆战队员见现场长门问题东车马鸟鱼龙"
            + "专个临为举义乡买军华汉极动势结带帮应该试验觉张迟坏轻钟银钱铁红绿蓝风岁"
            + "压庆历广归录彻态总恶过还进运远违连适选逻际陈险难页顶项顺须领频颜额类粮"
            + "紧纲纳纵纸线练组织终经绍绝统继维绵综缓编码缩网罗罢联职肃胜胁荣药获莱莲"
            + "营萧虽蛮补观规视览计订讨训议讯讲论许设访证评诉诊词译诗诚诞询详误诵课谁"
            + "调谈谊谋谓谜谢";

    /**
     * 有正证据时返回建议 locale；**无证据返回 {@code null}**（调用方自行决定保留默认还是保留原值）。
     *
     * @param sourceFolder 游戏源文件夹（通常就是游戏标题名，判据主要看它）
     * @param exeName      可选 exe 文件名
     */
    public static String detectLocaleOrNull(File sourceFolder, String exeName) {
        String folderName = sourceFolder != null ? sourceFolder.getName() : null;
        String path = sourceFolder != null ? sourceFolder.getAbsolutePath() : "";

        // ① 显式中文标记（路径 / 文件夹名 / exe 名）
        if (hasExplicitChineseHint(path) || hasExplicitChineseHint(folderName)
                || hasExplicitChineseHint(exeName)) {
            return ZH_LOCALE;
        }

        String sample = (folderName != null ? folderName : "") + " " + nvl(stripExtension(exeName));

        // ② 假名 → 日语（决定性）
        if (containsKana(sample)) return JA_LOCALE;

        // ③ 谚文 → 不主张中文（本仓无韩文 locale 档位，返回无证据）
        if (containsHangul(sample)) return null;

        // ④ 简体专有字 → 中文
        if (containsSimplifiedOnlyHan(sample)) return ZH_LOCALE;

        // ⑤ 纯汉字、字形与日文共用 → 无证据（旧版在这里误判成中文）
        return null;
    }

    /**
     * 兼容旧调用：无证据时落回日语默认（与改动前行为一致）。
     * 需要区分「无证据」的调用方请用 {@link #detectLocaleOrNull}。
     */
    public static String detectLocale(File sourceFolder, String exeName) {
        String detected = detectLocaleOrNull(sourceFolder, exeName);
        return detected != null ? detected : JA_LOCALE;
    }

    // ---- 判据实现 ----

    static boolean hasExplicitChineseHint(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String h : ZH_SUBSTRING_HINTS) {
            if (lower.contains(h)) return true;
        }
        for (String token : ZH_TOKEN_HINTS) {
            if (containsToken(lower, token)) return true;
        }
        return false;
    }

    /** token 必须以「非字母数字」为边界出现（`chs` 命中 `[chs]`、`_chs_`、`-chs`，不命中 `chen`）。 */
    private static boolean containsToken(String haystack, String token) {
        int from = 0;
        while (true) {
            int at = haystack.indexOf(token, from);
            if (at < 0) return false;
            boolean leftOk = at == 0 || !isAsciiAlnum(haystack.charAt(at - 1));
            int end = at + token.length();
            boolean rightOk = end >= haystack.length() || !isAsciiAlnum(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            from = at + 1;
        }
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /** 平假名 / 片假名 / 半角片假名 / 长音符。 */
    static boolean containsKana(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x3041 && c <= 0x309F)      // 平假名
                    || (c >= 0x30A1 && c <= 0x30FF)   // 片假名
                    || (c >= 0xFF66 && c <= 0xFF9D)   // 半角片假名
                    || c == 0x30FC || c == 0xFF70) {  // 长音符
                return true;
            }
        }
        return false;
    }

    /** 谚文音节 / 字母。 */
    static boolean containsHangul(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0xAC00 && c <= 0xD7A3)
                    || (c >= 0x1100 && c <= 0x11FF)
                    || (c >= 0x3130 && c <= 0x318F)) {
                return true;
            }
        }
        return false;
    }

    /** 是否含「日文不使用的简体专有字」（逐字查表）。 */
    static boolean containsSimplifiedOnlyHan(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (SIMPLIFIED_ONLY.indexOf(s.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private static String stripExtension(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
