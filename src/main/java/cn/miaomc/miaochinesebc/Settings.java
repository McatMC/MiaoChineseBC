package cn.miaomc.miaochinesebc;

import net.md_5.bungee.config.Configuration;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** config.yml 与 messages.yml 的只读快照，重载时整体替换。 */
final class Settings {
    final boolean translate;
    final boolean colors;
    final boolean plainFileLog;
    final int detailMaxLength;
    final long windowMillis;
    final int maxKeys;
    final int floodThreshold;
    private final EnumSet<Kind> hidden = EnumSet.noneOf(Kind.class);
    private final EnumSet<Kind> folded = EnumSet.noneOf(Kind.class);
    private final Configuration messages;
    private final List<String[]> errors = new ArrayList<String[]>();
    private final Map<String, String> systemExact = new HashMap<String, String>();
    private final List<String[]> systemPrefix = new ArrayList<String[]>();
    private final List<Object[]> systemRegex = new ArrayList<Object[]>();

    Settings(Configuration c, Configuration messages, java.util.logging.Logger logger) {
        translate = c.getBoolean("translate", true);
        colors = c.getBoolean("colors", true);
        plainFileLog = c.getBoolean("plain-file-log", true);
        detailMaxLength = c.getInt("detail-max-length", 160);
        windowMillis = Math.max(1, c.getInt("fold.window-seconds", 10)) * 1000L;
        maxKeys = Math.max(16, c.getInt("fold.max-keys", 5000));
        floodThreshold = Math.max(0, c.getInt("fold.flood-threshold", 30));
        for (Kind k : Kind.values()) {
            if (!c.getBoolean("types." + k.id + ".show", true)) hidden.add(k);
            if (c.getBoolean("types." + k.id + ".fold", k.foldByDefault)) folded.add(k);
        }
        this.messages = messages;
        for (Object o : messages.getList("errors")) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            String from = str(m.get("from"));
            if (!from.isEmpty()) errors.add(new String[] {from, str(m.get("to"))});
        }
        for (Object o : messages.getList("system")) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            String text = str(m.get("text"));
            if (m.get("match") != null) systemExact.put(str(m.get("match")), text);
            else if (m.get("prefix") != null) systemPrefix.add(new String[] {str(m.get("prefix")), text});
            else if (m.get("regex") != null) {
                try {
                    systemRegex.add(new Object[] {Pattern.compile(str(m.get("regex"))), text});
                } catch (RuntimeException e) {
                    logger.warning("messages.yml 中的正则无效，已跳过：" + m.get("regex"));
                }
            }
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    boolean show(Kind k) {
        return !hidden.contains(k);
    }

    boolean fold(Kind k) {
        return folded.contains(k);
    }

    /** 上色后的原始模板，缺失时返回 fallback。 */
    String raw(String path, String fallback) {
        return Texts.color(messages.getString(path, fallback));
    }

    /** 读取模板、替换占位符，并按 colors 设置决定是否去掉颜色。 */
    String render(String path, Map<String, String> vars) {
        return finish(Texts.apply(raw(path, path), vars));
    }

    String render(String path, Object... values) {
        Map<String, String> vars = new HashMap<String, String>();
        for (int i = 0; i + 1 < values.length; i += 2)
            vars.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
        return render(path, vars);
    }

    String finish(String colored) {
        return colors ? colored : Texts.strip(colored);
    }

    List<String> lines(String path) {
        List<String> out = new ArrayList<String>();
        for (String s : messages.getStringList(path)) out.add(finish(Texts.color(s)));
        return out;
    }

    /** 按 errors 列表替换错误信息中的英文片段。 */
    String clean(String text) {
        String out = text;
        for (String[] e : errors) out = out.replace(e[0], e[1]);
        return out;
    }

    /** 系统日志汉化：template 为原始模板，params 为参数；未命中返回 null。 */
    String system(String template, Object[] params) {
        String exact = systemExact.get(template);
        if (exact != null) return finish(Texts.color(Texts.params(exact, params)));
        String formatted = Texts.params(template, params);
        for (String[] p : systemPrefix)
            if (formatted.startsWith(p[0]))
                return finish(Texts.color(p[1]) + formatted.substring(p[0].length()));
        for (Object[] r : systemRegex) {
            Matcher m = ((Pattern) r[0]).matcher(formatted);
            if (m.matches()) {
                try {
                    return finish(Texts.color(m.replaceFirst((String) r[1])));
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
