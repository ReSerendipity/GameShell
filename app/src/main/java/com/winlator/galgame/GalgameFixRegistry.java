package com.winlator.galgame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * P0-3：每游戏修复/覆盖的**数据层**（纯 Java，可 javac 单测；不含 UI）。
 *
 * <p>依据：GameNative 用「source+id → 一个 apply」表达多数修复，Bannerlator 证明
 * 「预览与写入必须同源」才能避免静默改配置。本类只做三件事：
 * <ol>
 *   <li>{@link #resolve}：按 engine / exe 名 / Wine 版本匹配 {@code assets/galgame_fixes.conf}，
 *       算出「将会改哪些字段、从什么改成什么」；</li>
 *   <li>{@link #preview}：把 Plan 渲染成人类可读行（供诊断页/后续详情页，不写任何配置）；</li>
 *   <li>由 {@link ImportFlow#applyGameFixes} 落地：**只写 fix 声明过的键**，写入前把旧值存进
 *       容器 extraData（{@code galgame_fixes_prev}），并提供
 *       {@link ImportFlow#revertGameFixes} 回滚。</li>
 * </ol>
 *
 * <p>数据格式（{@code galgame_fixes.conf}）：
 * <pre>
 * [fix-id]
 * enabled=true
 * engines=KIRIKIRI,SIGLUS        # 空 = 任意引擎
 * exe_regex=SiglusEngine\.exe     # 空 = 任意 exe；Java 正则，忽略大小写
 * wine_version_contains=10.      # 空 = 任意 Wine
 * set.dxwrapper=wined3d
 * set.wincomponents=vcrun2010=1
 * set.env=FOO=1
 * set.windows_version=win7
 * set.screen_size=1920x1080
 * set.box64_preset=compatibility
 * note=...
 * </pre>
 *
 * <p>明确边界：不改 UI、不自动启用任何「没有真机证据」的修复。当前 conf 里的条目全部
 * {@code enabled=false}，只作为 schema 示例与后续按个案开启的落点。
 */
public final class GalgameFixRegistry {

    /** 允许写入的字段白名单；白名单之外的 set.* 一律忽略（防配置漂移）。 */
    static final String[] FIELDS = {
            "dxwrapper", "dxwrapper_config", "wincomponents", "env",
            "windows_version", "screen_size", "box64_preset"
    };

    private GalgameFixRegistry() {}

    /** 单字段变更。 */
    public static final class Change {
        public final String fixId;
        public final String field;
        public final String from;
        public final String to;
        public final String note;

        Change(String fixId, String field, String from, String to, String note) {
            this.fixId = fixId;
            this.field = field;
            this.from = from;
            this.to = to;
            this.note = note;
        }
    }

    /** 一次解析结果。 */
    public static final class Plan {
        public final List<Change> changes = new ArrayList<>();
        public final List<String> fixIds = new ArrayList<>();

        public boolean isEmpty() {
            return changes.isEmpty();
        }

        /** 人类可读预览，一行一个字段。 */
        public String preview() {
            StringBuilder sb = new StringBuilder();
            for (Change c : changes) {
                sb.append(c.field).append(": ")
                        .append(c.from == null || c.from.isEmpty() ? "(空)" : c.from)
                        .append(" → ").append(c.to)
                        .append("  [").append(c.fixId).append(']');
                if (c.note != null && !c.note.isEmpty()) sb.append(" — ").append(c.note);
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    /**
     * 纯函数：匹配并计算出变更计划。
     *
     * @param conf         galgame_fixes.conf 全文
     * @param engine       容器 overlay 的 engine（如 KIRIKIRI）；null 按不匹配处理
     * @param exeName      启动 exe 文件名；null 按空处理
     * @param wineVersion  容器当前 Wine identifier；null 按空处理
     * @param current      当前字段值（键见 {@link #FIELDS}）
     */
    public static Plan resolve(String conf, String engine, String exeName,
                               String wineVersion, Map<String, String> current) {
        Plan plan = new Plan();
        if (conf == null || conf.trim().isEmpty()) return plan;

        Map<String, String> working = new LinkedHashMap<>();
        if (current != null) working.putAll(current);

        for (Map<String, String> fix : parse(conf)) {
            if (!"true".equalsIgnoreCase(fix.get("enabled"))) continue;
            if (!matchesEngine(fix.get("engines"), engine)) continue;
            if (!matchesExe(fix.get("exe_regex"), exeName)) continue;
            if (!matchesWine(fix.get("wine_version_contains"), wineVersion)) continue;

            boolean touched = false;
            for (String field : FIELDS) {
                String want = fix.get("set." + field);
                if (want == null || want.trim().isEmpty()) continue;

                String oldValue = working.get(field);
                String newValue;
                if ("env".equals(field)) {
                    newValue = appendEnv(oldValue, want);
                }
                else if ("wincomponents".equals(field)) {
                    newValue = mergeKeyValueCsv(oldValue, want);
                }
                else {
                    newValue = want.trim();
                }
                if (newValue == null) continue;
                if (oldValue != null && oldValue.equals(newValue)) continue;

                plan.changes.add(new Change(fix.get("id"), field, oldValue, newValue, fix.get("note")));
                working.put(field, newValue);
                touched = true;
            }
            if (touched && !plan.fixIds.contains(fix.get("id"))) plan.fixIds.add(fix.get("id"));
        }
        return plan;
    }

    /** 预览（不写任何配置）。 */
    public static String preview(Plan plan) {
        return plan == null ? "" : plan.preview();
    }

    // ---- 匹配 ----

    private static boolean matchesEngine(String engines, String engine) {
        if (engines == null || engines.trim().isEmpty()) return true;
        if (engine == null) return false;
        for (String e : engines.split(",")) {
            if (e.trim().equalsIgnoreCase(engine.trim())) return true;
        }
        return false;
    }

    private static boolean matchesExe(String regex, String exeName) {
        if (regex == null || regex.trim().isEmpty()) return true;
        if (exeName == null) return false;
        try {
            return Pattern.compile(regex.trim(), Pattern.CASE_INSENSITIVE)
                    .matcher(exeName).find();
        }
        catch (Throwable t) {
            return false;   // 写错的正则不匹配，不阻断导入
        }
    }

    private static boolean matchesWine(String contains, String wineVersion) {
        if (contains == null || contains.trim().isEmpty()) return true;
        if (wineVersion == null) return false;
        return wineVersion.toLowerCase(Locale.ROOT).contains(contains.trim().toLowerCase(Locale.ROOT));
    }

    // ---- 合并 ----

    /** 把 {@code KEY=VAL KEY2=VAL2} 追加到 env 串；已存在的 KEY 不覆盖（用户/预设优先）。 */
    static String appendEnv(String current, String add) {
        String base = current == null ? "" : current.trim();
        Set<String> existing = new LinkedHashSet<>();
        for (String tok : base.split("\\s+")) {
            if (tok.isEmpty()) continue;
            int eq = tok.indexOf('=');
            existing.add(eq > 0 ? tok.substring(0, eq) : tok);
        }
        StringBuilder appended = new StringBuilder();
        for (String tok : add.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            int eq = tok.indexOf('=');
            String key = eq > 0 ? tok.substring(0, eq) : tok;
            if (existing.contains(key)) continue;
            existing.add(key);
            appended.append(appended.length() > 0 ? " " : "").append(tok);
        }
        if (appended.length() == 0) return current;
        return base.isEmpty() ? appended.toString() : base + " " + appended;
    }

    /** 把 {@code k=v,k=v} 合并进 wincomponents CSV；只覆盖声明过的键。 */
    static String mergeKeyValueCsv(String current, String add) {
        String base = current == null ? "" : current.trim();
        List<String[]> entries = new ArrayList<>();
        Set<String> index = new LinkedHashSet<>();

        for (String part : base.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String k = eq > 0 ? p.substring(0, eq).trim() : p;
            String v = eq > 0 ? p.substring(eq + 1).trim() : "";
            if (index.add(k)) entries.add(new String[]{k, v});
            else {
                for (String[] e : entries) if (e[0].equals(k)) e[1] = v;
            }
        }

        for (String part : add.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String k = p.substring(0, eq).trim();
            String v = p.substring(eq + 1).trim();
            boolean found = false;
            for (String[] e : entries) {
                if (e[0].equals(k)) { e[1] = v; found = true; break; }
            }
            if (!found) { entries.add(new String[]{k, v}); index.add(k); }
        }

        StringBuilder sb = new StringBuilder();
        for (String[] e : entries) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e[0]).append('=').append(e[1]);
        }
        return sb.toString();
    }

    // ---- 解析 ----

    /** 解析 conf：返回每个 [id] 段的键值（含 {@code id}）。坏行静默跳过。 */
    static List<Map<String, String>> parse(String conf) {
        List<Map<String, String>> out = new ArrayList<>();
        Map<String, String> cur = null;

        for (String raw : conf.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                String id = line.substring(1, line.length() - 1).trim();
                if (!id.isEmpty()) {
                    cur = new LinkedHashMap<>();
                    cur.put("id", id);
                    out.add(cur);
                }
                else cur = null;
                continue;
            }
            if (cur == null) continue;

            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            cur.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return out;
    }
}
