package cn.miaomc.miaochinesebc;

import net.md_5.bungee.api.ProxyServer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 识别 BungeeCord 核心日志的类型与来源，并生成中文文本。模板与参数来自上游 BungeeCord 26.1 的 HandlerBoss、InitialHandler 等类。 */
final class Translator {
    /** 识别结果；text 在确定需要输出后才生成。 */
    static final class Match {
        final Kind kind;
        final Subject who;
        final Map<String, String> extra;
        final String systemText;

        Match(Kind kind, Subject who, Map<String, String> extra, String systemText) {
            this.kind = kind;
            this.who = who;
            this.extra = extra;
            this.systemText = systemText;
        }

        String key() {
            return who == null ? null : who.key();
        }
    }

    private static final Pattern HANDLER_SUFFIX =
            Pattern.compile("^(.*) - (could not decode packet!|encountered exception|exception processing exception)$");
    private static final Pattern CLASS_PREFIX = Pattern.compile("^(?:[\\w$]+\\.)+([\\w$]+(?:Exception|Error))");
    private static final Pattern HEX = Pattern.compile("\\b[0-9a-fA-F]{8,}\\b");
    private static final Pattern QUALIFIED =
            Pattern.compile("\\b(?:[a-z_][\\w]*\\.)+(?:[A-Z]\\w*\\$)*([A-Z]\\w*)");
    private static final int REMEMBERED = 2048;
    private final ProxyServer proxy;
    /** 玩家名到 IP 的近期记录：下线与踢出日志输出时玩家可能已不在在线列表中，用它补全 IP。 */
    private final Map<String, String> recentIps =
            new LinkedHashMap<String, String>(256, 0.75f, true) {
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > REMEMBERED;
                }
            };

    Translator(ProxyServer proxy) {
        this.proxy = proxy;
    }

    Match classify(LogRecord record, Settings s) {
        Match m = classify0(record, s);
        if (m != null && m.who != null && m.who.player != null) {
            String name = m.who.player.toLowerCase(Locale.ROOT);
            synchronized (recentIps) {
                if (m.who.ip != null) recentIps.put(name, m.who.ip);
                else m.who.ip = recentIps.get(name);
            }
        }
        return m;
    }

    private Match classify0(LogRecord record, Settings s) {
        String msg = record.getMessage();
        if (msg == null) return null;
        Object[] p = record.getParameters();
        if (p == null) p = new Object[0];
        if (p.length >= 1) {
            if (msg.equals("{0} has pinged")) return match(Kind.PING, p[0]);
            if (msg.equals("{0} has connected")) {
                Subject w = Subject.of(p[0], proxy);
                return new Match(
                        w.type.equals(Subject.INITIAL)
                                ? Kind.LOGIN
                                : w.type.equals(Subject.CONNECTOR) ? Kind.SERVER_CONNECT : Kind.OTHER_CONNECT,
                        w,
                        vars(),
                        null);
            }
            if (msg.equals("{0} has disconnected")) {
                Subject w = Subject.of(p[0], proxy);
                Kind k;
                if (w.type.equals(Subject.PLAYER)) k = Kind.PROXY_DISCONNECT;
                else if (w.type.equals(Subject.DOWNSTREAM) || w.type.equals(Subject.CONNECTOR))
                    k = Kind.SERVER_DISCONNECT;
                else k = Kind.OTHER_DISCONNECT;
                return new Match(k, w, vars(), null);
            }
            if (msg.equals("{0} - read timed out")) return match(Kind.READ_TIMEOUT, p[0]);
            if (msg.equals("{0} - write timed out")) return match(Kind.WRITE_TIMEOUT, p[0]);
            if (msg.equals("[{0}] No client connected for pending server!")) return match(Kind.NO_CLIENT, p[0]);
        }
        if (p.length >= 2) {
            if (msg.equals("[{0}] disconnected with: {1}"))
                return match(Kind.KICK, p[0], "reason", reason(p[1], s));
            if (msg.equals("{0} executed command: /{1}"))
                return match(Kind.COMMAND, p[0], "command", String.valueOf(p[1]));
            if (msg.equals("{0} - corrupted frame: {1}"))
                return match(Kind.CORRUPTED_FRAME, p[0], "detail", detail(p[1], s));
            if (msg.equals("{0} - bad packet, are mods in use!? {1}"))
                return match(Kind.BAD_PACKET, p[0], "detail", detail(p[1], s));
            if (msg.equals("{0} - overflow in packet detected! {1}"))
                return match(Kind.OVERFLOW, p[0], "detail", detail(p[1], s));
            if (msg.equals("{0} - encountered exception: {1}"))
                return match(Kind.EXCEPTION, p[0], "detail", detail(p[1], s));
        }
        if (p.length >= 3) {
            if (msg.equals("{0} - {1}: {2}"))
                return match(
                        Kind.NETWORK_ERROR,
                        p[0],
                        "error_type",
                        String.valueOf(p[1]),
                        "error",
                        Texts.oneLine(s.clean(String.valueOf(p[2])), s.detailMaxLength));
            if (msg.equals("{0} exceeded packet limit ({1} packets and {2} bytes per second)"))
                return match(Kind.PACKET_LIMIT, p[0], "packets", String.valueOf(p[1]), "bytes", String.valueOf(p[2]));
        }
        Matcher m = HANDLER_SUFFIX.matcher(msg);
        if (p.length == 0 && m.matches()) {
            String what = m.group(2);
            String key =
                    what.startsWith("could")
                            ? "details.decode"
                            : what.startsWith("encountered") ? "details.unhandled" : "details.nested";
            String detail = s.raw(key, what);
            if (record.getThrown() != null) detail += "：" + detail(record.getThrown(), s);
            return match(Kind.EXCEPTION, m.group(1), "detail", detail);
        }
        String text = s.system(msg, p);
        return text == null ? null : new Match(Kind.SYSTEM, null, null, text);
    }

    /** 生成最终输出文本。 */
    String render(Match match, Settings s) {
        if (match.systemText != null) return match.systemText;
        Map<String, String> v = subjectVars(match.who, s);
        v.putAll(match.extra);
        v.put("who", s.render("who." + whoKey(match.who), v));
        return s.render("formats." + match.kind.id, v);
    }

    private Match match(Kind kind, Object handler, String... extra) {
        return new Match(kind, Subject.of(handler, proxy), vars(extra), null);
    }

    private static Map<String, String> vars(String... values) {
        Map<String, String> out = new HashMap<String, String>();
        for (int i = 0; i + 1 < values.length; i += 2) out.put(values[i], values[i + 1]);
        return out;
    }

    private static Map<String, String> subjectVars(Subject w, Settings s) {
        String none = s.raw("unknown-value", "-");
        Map<String, String> v = new HashMap<String, String>();
        v.put("raw", w.raw);
        v.put("player", or(w.player, none));
        v.put("ip", or(w.ip, none));
        v.put("port", w.port < 0 ? none : String.valueOf(w.port));
        v.put("server", or(w.server, none));
        v.put("host", or(w.host, none));
        v.put("protocol", w.protocol < 0 ? none : String.valueOf(w.protocol));
        String version = w.protocol < 0 ? null : Versions.name(w.protocol);
        v.put("version", version != null ? version : s.raw("unknown-version", "?") + (w.protocol < 0 ? "" : " (" + w.protocol + ")"));
        v.put("stage", s.render("stage." + w.type, new HashMap<String, String>()));
        return v;
    }

    private static String whoKey(Subject w) {
        if (w.type.equals(Subject.INITIAL)) return w.player == null ? "initial" : "initial-named";
        return w.type;
    }

    private static String or(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** 断开原因：先替换英文片段（errors 中可能写有完整类名），再缩短剩余的完整类名，合并为单行。 */
    private static String reason(Object o, Settings s) {
        String text = s.clean(Texts.oneLine(String.valueOf(o), 0));
        return Texts.oneLine(QUALIFIED.matcher(text).replaceAll("$1"), s.detailMaxLength);
    }

    /** 异常详情：去掉完整类名、替换英文片段、解码十六进制数据、合并为单行并截断。 */
    private static String detail(Object o, Settings s) {
        String text;
        if (o instanceof Throwable) {
            Throwable t = (Throwable) o;
            text = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        } else {
            text = String.valueOf(o);
        }
        text = CLASS_PREFIX.matcher(text).replaceFirst("$1");
        text = s.clean(Texts.oneLine(text, 0));
        text = QUALIFIED.matcher(text).replaceAll("$1");
        Matcher m = HEX.matcher(text);
        if (m.find()) {
            String preview = preview(m.group());
            if (preview != null)
                text = text.substring(0, m.start()) + s.render("hex-preview", "text", preview) + text.substring(m.end());
        }
        return Texts.oneLine(text, s.detailMaxLength);
    }

    /** 把十六进制转储解码为可读文本，可打印字符少于六成时返回 null。 */
    private static String preview(String hex) {
        int n = Math.min(hex.length() / 2, 64);
        StringBuilder out = new StringBuilder(n);
        int printable = 0;
        for (int i = 0; i < n; i++) {
            int b = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            if (b >= 32 && b < 127) {
                out.append((char) b);
                printable++;
            } else {
                out.append('.');
            }
        }
        return n > 0 && printable * 10 >= n * 6 ? out.toString() : null;
    }
}
