package cn.miaomc.miaochinesebc;

import net.md_5.bungee.api.ChatColor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 颜色代码转换与占位符替换。 */
final class Texts {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private Texts() {}

    /** 转换 &#RRGGBB 与 & 颜色代码。 */
    static String color(String s) {
        if (s == null || s.isEmpty()) return "";
        Matcher m = HEX.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) hex.append('§').append(c);
            m.appendReplacement(out, Matcher.quoteReplacement(hex.toString()));
        }
        m.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    static String strip(String s) {
        return ChatColor.stripColor(s);
    }

    /** 单次扫描替换 {键}；替换进来的值不会再被解析，未知的 {键} 保持原样。 */
    static String apply(String template, Map<String, String> vars) {
        StringBuilder out = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf('{', i);
            int close = open < 0 ? -1 : template.indexOf('}', open + 1);
            if (close < 0) break;
            String value = vars.get(template.substring(open + 1, close));
            out.append(template, i, open);
            if (value == null) {
                out.append('{');
                i = open + 1;
            } else {
                out.append(value);
                i = close + 1;
            }
        }
        return out.append(template, i, template.length()).toString();
    }

    /** 把 BungeeCord 风格的 {0} {1} 替换为参数值。 */
    static String params(String template, Object[] params) {
        if (params == null || params.length == 0) return template;
        StringBuilder out = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            int open = template.indexOf('{', i);
            int close = open < 0 ? -1 : template.indexOf('}', open + 1);
            if (close < 0) break;
            out.append(template, i, open);
            int index = index(template.substring(open + 1, close));
            if (index >= 0 && index < params.length) {
                out.append(params[index]);
                i = close + 1;
            } else {
                out.append('{');
                i = open + 1;
            }
        }
        return out.append(template, i, template.length()).toString();
    }

    private static int index(String s) {
        if (s.isEmpty() || s.length() > 2) return -1;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return -1;
        return Integer.parseInt(s);
    }

    /** 合并为单行，过长时截断。 */
    static String oneLine(String s, int max) {
        String out = s.replace("\r", "").replace('\n', ' ').trim();
        return max > 0 && out.length() > max ? out.substring(0, max) + "…" : out;
    }
}
