package cn.miaomc.miaochinesebc;

import java.util.HashMap;
import java.util.Map;

/** 协议号与 Minecraft 版本名称的对应关系，依据 BungeeCord 26.1 的 ProtocolConstants。 */
final class Versions {
    private static final Map<Integer, String> NAMES = new HashMap<Integer, String>();

    static {
        put(47, "1.8.x");
        put(107, "1.9");
        put(108, "1.9.1");
        put(109, "1.9.2");
        put(110, "1.9.3-1.9.4");
        put(210, "1.10.x");
        put(315, "1.11");
        put(316, "1.11.1-1.11.2");
        put(335, "1.12");
        put(338, "1.12.1");
        put(340, "1.12.2");
        put(393, "1.13");
        put(401, "1.13.1");
        put(404, "1.13.2");
        put(477, "1.14");
        put(480, "1.14.1");
        put(485, "1.14.2");
        put(490, "1.14.3");
        put(498, "1.14.4");
        put(573, "1.15");
        put(575, "1.15.1");
        put(578, "1.15.2");
        put(735, "1.16");
        put(736, "1.16.1");
        put(751, "1.16.2");
        put(753, "1.16.3");
        put(754, "1.16.4-1.16.5");
        put(755, "1.17");
        put(756, "1.17.1");
        put(757, "1.18-1.18.1");
        put(758, "1.18.2");
        put(759, "1.19");
        put(760, "1.19.1-1.19.2");
        put(761, "1.19.3");
        put(762, "1.19.4");
        put(763, "1.20-1.20.1");
        put(764, "1.20.2");
        put(765, "1.20.3-1.20.4");
        put(766, "1.20.5-1.20.6");
        put(767, "1.21-1.21.1");
        put(768, "1.21.2-1.21.3");
        put(769, "1.21.4");
        put(770, "1.21.5");
        put(771, "1.21.6");
        put(772, "1.21.7-1.21.8");
        put(773, "1.21.9-1.21.10");
        put(774, "1.21.11");
        put(775, "26.1");
        put(776, "26.2");
        put(777, "26.3");
    }

    private Versions() {}

    private static void put(int protocol, String name) {
        NAMES.put(protocol, name);
    }

    /** 已知协议返回版本名，快照返回“快照 #n”，其余返回 null。 */
    static String name(int protocol) {
        String name = NAMES.get(protocol);
        if (name != null) return name;
        if (protocol >= 0x40000000) return "快照 #" + (protocol - 0x40000000);
        return null;
    }
}
