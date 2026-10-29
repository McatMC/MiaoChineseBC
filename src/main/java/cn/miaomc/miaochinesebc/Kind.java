package cn.miaomc.miaochinesebc;

/** 可识别的日志类型，id 与 config.yml 的 types、messages.yml 的 formats/labels 键名一致。 */
enum Kind {
    PING("ping", true),
    LOGIN("login", true),
    SERVER_CONNECT("server-connect", false),
    SERVER_DISCONNECT("server-disconnect", false),
    PROXY_DISCONNECT("proxy-disconnect", false),
    KICK("kick", false),
    COMMAND("command", false),
    READ_TIMEOUT("read-timeout", true),
    WRITE_TIMEOUT("write-timeout", true),
    CORRUPTED_FRAME("corrupted-frame", true),
    BAD_PACKET("bad-packet", true),
    OVERFLOW("overflow", true),
    NETWORK_ERROR("network-error", true),
    EXCEPTION("exception", true),
    PACKET_LIMIT("packet-limit", true),
    NO_CLIENT("no-client", false),
    OTHER_CONNECT("other-connect", false),
    OTHER_DISCONNECT("other-disconnect", false),
    SYSTEM("system", false);

    final String id;
    final boolean foldByDefault;

    Kind(String id, boolean foldByDefault) {
        this.id = id;
        this.foldByDefault = foldByDefault;
    }
}
