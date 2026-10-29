package cn.miaomc.miaochinesebc;

import java.util.*;

/**
 * 按“类型 + 来源”折叠日志。每个来源在窗口内只输出第一条，其余计数，窗口结束时由 tick 产出汇总；来源持续刷屏时每个窗口一条汇总。
 * 同一类型在一个窗口内出现过多新来源时，新来源也不再逐条输出，合并为一条整体汇总。日志可能在多个线程中同时写入，所有方法均加锁。
 */
final class Folder {
    /** 一条待输出的汇总。flood 为 true 时 key 为空，keys 为合并的来源数量。 */
    static final class Summary {
        final Kind kind;
        final String key;
        final int count;
        final int keys;
        final long elapsedMillis;
        final boolean flood;

        Summary(Kind kind, String key, int count, int keys, long elapsedMillis, boolean flood) {
            this.kind = kind;
            this.key = key;
            this.count = count;
            this.keys = keys;
            this.elapsedMillis = elapsedMillis;
            this.flood = flood;
        }
    }

    private static final class Bucket {
        final Kind kind;
        final String key;
        long start;
        int count;

        Bucket(Kind kind, String key, long start) {
            this.kind = kind;
            this.key = key;
            this.start = start;
        }
    }

    private static final class Flood {
        long start;
        int fresh;
        int count;
        final Set<String> keys = new HashSet<String>();
    }

    private final Map<String, Bucket> buckets = new HashMap<String, Bucket>();
    private final Map<Kind, Flood> floods = new EnumMap<Kind, Flood>(Kind.class);
    private long total;

    /** 返回 true 表示这条日志应当输出。 */
    synchronized boolean admit(Kind kind, String key, long now, Settings s) {
        String id = kind.id + '\u0000' + key;
        Bucket b = buckets.get(id);
        if (b != null) {
            if (now - b.start >= s.windowMillis && b.count == 0) {
                b.start = now;
                return true;
            }
            b.count++;
            total++;
            return false;
        }
        Flood f = floods.get(kind);
        if (f == null) floods.put(kind, f = new Flood());
        if (now - f.start >= s.windowMillis && f.count == 0) {
            f.start = now;
            f.fresh = 0;
        }
        f.fresh++;
        boolean flooding = s.floodThreshold > 0 && f.fresh > s.floodThreshold;
        if (flooding || buckets.size() >= s.maxKeys) {
            f.count++;
            if (f.keys.size() < s.maxKeys) f.keys.add(key);
            total++;
            return false;
        }
        buckets.put(id, new Bucket(kind, key, now));
        return true;
    }

    /** 产出到期的汇总；all 为 true 时（关闭插件）产出全部未汇总的计数。 */
    synchronized List<Summary> tick(long now, Settings s, boolean all) {
        List<Summary> out = new ArrayList<Summary>();
        for (Iterator<Bucket> it = buckets.values().iterator(); it.hasNext(); ) {
            Bucket b = it.next();
            if (!all && now - b.start < s.windowMillis) continue;
            if (b.count > 0) {
                out.add(new Summary(b.kind, b.key, b.count, 1, now - b.start, false));
                // 仍在刷屏的来源保持折叠，下个窗口继续只输出汇总
                b.start = now;
                b.count = 0;
            } else {
                it.remove();
            }
        }
        for (Map.Entry<Kind, Flood> e : floods.entrySet()) {
            Flood f = e.getValue();
            if (!all && now - f.start < s.windowMillis) continue;
            if (f.count > 0) out.add(new Summary(e.getKey(), null, f.count, f.keys.size(), now - f.start, true));
            f.start = now;
            f.fresh = 0;
            f.count = 0;
            f.keys.clear();
        }
        return out;
    }

    synchronized int keys() {
        return buckets.size();
    }

    synchronized int pending() {
        int n = 0;
        for (Bucket b : buckets.values()) n += b.count;
        for (Flood f : floods.values()) n += f.count;
        return n;
    }

    synchronized long total() {
        return total;
    }
}
