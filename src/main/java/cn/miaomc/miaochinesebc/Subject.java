package cn.miaomc.miaochinesebc;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志中的连接来源。BungeeCord 把连接处理器对象作为日志参数：握手阶段直接读取 PendingConnection，其余处理器解析其 toString。
 * 同时兼容上游 BungeeCord 与 Waterfall 系分支的写法（后者在方括号内附带 IP，用 | 分隔）。
 */
final class Subject {
    static final String INITIAL = "initial";
    static final String PLAYER = "player";
    static final String DOWNSTREAM = "downstream";
    static final String CONNECTOR = "connector";
    static final String UNKNOWN = "unknown";

    private static final Pattern INITIAL_TEXT = Pattern.compile("^\\[(.*)\\] <-> InitialHandler$");
    private static final Pattern CONNECTOR_TEXT = Pattern.compile("^\\[(.*?)\\] <-> ServerConnector \\[(.*)\\]$");
    private static final Pattern DOWNSTREAM_TEXT =
            Pattern.compile("^\\[(.*?)\\] <-> DownstreamBridge <-> \\[(.*)\\]$");
    private static final Pattern UPSTREAM_TEXT = Pattern.compile("^\\[(.*?)\\] -> UpstreamBridge$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_.*-]{1,32}$");
    private static final Method NONE;
    private static final Map<Class<?>, Method> HANDSHAKE = new ConcurrentHashMap<Class<?>, Method>();
    private static final Map<Class<?>, Method> HOST = new ConcurrentHashMap<Class<?>, Method>();

    static {
        try {
            NONE = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    String type = UNKNOWN;
    String raw = "";
    String player;
    String ip;
    int port = -1;
    String server;
    String host;
    int protocol = -1;

    private Subject() {}

    /** 折叠使用的来源：优先 IP，其次玩家名；都没有时不折叠。 */
    String key() {
        return ip != null ? ip : player;
    }

    static Subject of(Object o, ProxyServer proxy) {
        Subject s = new Subject();
        if (o instanceof PendingConnection) {
            PendingConnection c = (PendingConnection) o;
            s.type = INITIAL;
            s.raw = String.valueOf(o);
            s.player = c.getName();
            s.address(c.getSocketAddress());
            s.protocol = c.getVersion();
            s.host = handshakeHost(o);
            return s;
        }
        if (o instanceof ProxiedPlayer) {
            s.player((ProxiedPlayer) o);
            return s;
        }
        String text = String.valueOf(o);
        s.raw = text;
        Matcher m;
        if ((m = INITIAL_TEXT.matcher(text)).matches()) {
            s.type = INITIAL;
            s.parts(m.group(1));
        } else if ((m = CONNECTOR_TEXT.matcher(text)).matches()) {
            s.type = CONNECTOR;
            s.parts(m.group(1));
            s.server = m.group(2);
        } else if ((m = DOWNSTREAM_TEXT.matcher(text)).matches()) {
            s.type = DOWNSTREAM;
            s.parts(m.group(1));
            s.server = m.group(2);
        } else if ((m = UPSTREAM_TEXT.matcher(text)).matches()) {
            s.type = PLAYER;
            s.parts(m.group(1));
        } else if (NAME.matcher(text).matches()) {
            s.type = PLAYER;
            s.player = text;
        }
        if (s.ip == null && s.player != null) {
            ProxiedPlayer online = proxy.getPlayer(s.player);
            if (online != null) s.address(online.getSocketAddress());
        }
        return s;
    }

    private void player(ProxiedPlayer p) {
        type = PLAYER;
        raw = p.getName();
        player = p.getName();
        address(p.getSocketAddress());
        protocol = p.getPendingConnection() == null ? -1 : p.getPendingConnection().getVersion();
        if (p.getServer() != null) server = p.getServer().getInfo().getName();
    }

    /** 拆分方括号内的 “名字,地址” 或 “名字|地址”（顺序不定）。 */
    private void parts(String content) {
        for (String part : content.split("[|,]")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            if (part.indexOf('/') >= 0 || part.matches(".*:\\d+$")) address(part);
            else player = part;
        }
    }

    private void address(SocketAddress a) {
        if (a instanceof InetSocketAddress) {
            InetSocketAddress inet = (InetSocketAddress) a;
            ip = inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
            port = inet.getPort();
        } else if (a != null) {
            ip = String.valueOf(a);
        }
    }

    /** 解析 InetSocketAddress.toString() 的结果，例如 /1.2.3.4:5678、host/1.2.3.4:5678、/[::1]:5678。 */
    private void address(String text) {
        String s = text.substring(text.lastIndexOf('/') + 1);
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.substring(colon + 1).matches("\\d+")) {
            port = Integer.parseInt(s.substring(colon + 1));
            s = s.substring(0, colon);
        }
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length() - 1);
        if (!s.isEmpty()) ip = s;
    }

    /**
     * 通过反射读取握手包中客户端填写的地址。不使用 PendingConnection.getVirtualHost()，因为它会新建 InetSocketAddress
     * 并触发 DNS 解析，在日志线程中执行会拖慢所有日志输出。
     */
    private static String handshakeHost(Object conn) {
        try {
            Method getHandshake = cached(HANDSHAKE, conn.getClass(), "getHandshake");
            if (getHandshake == NONE) return null;
            Object handshake = getHandshake.invoke(conn);
            if (handshake == null) return null;
            Method getHost = cached(HOST, handshake.getClass(), "getHost");
            if (getHost == NONE) return null;
            Object host = getHost.invoke(handshake);
            if (host == null) return null;
            String h = String.valueOf(host);
            int zero = h.indexOf('\0');
            if (zero >= 0) h = h.substring(0, zero);
            if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
            return h.isEmpty() ? null : h;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Method cached(Map<Class<?>, Method> cache, Class<?> type, String name) {
        Method m = cache.get(type);
        if (m == null) {
            try {
                m = type.getMethod(name);
            } catch (Throwable e) {
                m = NONE;
            }
            try {
                if (m != NONE) m.setAccessible(true);
            } catch (Throwable ignored) {
                // 公开方法无需放开访问权限也能调用
            }
            cache.put(type, m);
        }
        return m;
    }
}
