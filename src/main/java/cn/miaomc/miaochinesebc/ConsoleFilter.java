package cn.miaomc.miaochinesebc;

import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** 挂在 BungeeCord 主日志记录器上的过滤器：先交给原有过滤器，再由插件折叠、改写为中文。 */
final class ConsoleFilter implements Filter {
    /** 插件自己输出的汇总日志，直接放行。 */
    static final class Own extends LogRecord {
        Own(Level level, String message, String loggerName) {
            super(level, message);
            setLoggerName(loggerName);
        }
    }

    private final MiaoChineseBC plugin;
    final Filter previous;
    volatile boolean active = true;

    ConsoleFilter(MiaoChineseBC plugin, Filter previous) {
        this.plugin = plugin;
        this.previous = previous;
    }

    public boolean isLoggable(LogRecord record) {
        if (previous != null && !previous.isLoggable(record)) return false;
        if (!active || record instanceof Own) return true;
        try {
            return plugin.handle(record);
        } catch (Throwable e) {
            // 任何意外都不能影响日志输出，保留原始日志
            return true;
        }
    }
}
