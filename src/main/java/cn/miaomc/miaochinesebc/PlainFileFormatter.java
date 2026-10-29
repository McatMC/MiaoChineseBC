package cn.miaomc.miaochinesebc;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/** 包裹日志文件的格式化器，去掉 § 颜色代码，让 proxy.log 保持纯文本；控制台仍显示颜色。 */
final class PlainFileFormatter extends Formatter {
    private static final Pattern COLOR = Pattern.compile("§[0-9a-fk-orxA-FK-ORX]");
    final Formatter wrapped;

    PlainFileFormatter(Formatter wrapped) {
        this.wrapped = wrapped;
    }

    public String format(LogRecord record) {
        return COLOR.matcher(wrapped.format(record)).replaceAll("");
    }

    public String getHead(java.util.logging.Handler h) {
        return wrapped.getHead(h);
    }

    public String getTail(java.util.logging.Handler h) {
        return wrapped.getTail(h);
    }
}
