package cn.miaomc.miaochinesebc;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * 在 onLoad 阶段接管 BungeeCord 主日志记录器，尽早覆盖插件加载与启动日志。 只处理 BungeeCord 核心日志；其他插件通过各自的 PluginLogger
 * 输出，不经过主日志记录器的过滤器。
 */
public final class MiaoChineseBC extends Plugin {
    private volatile Settings settings;
    private Translator translator;
    private final Folder folder = new Folder();
    private ConsoleFilter filter;
    private ScheduledTask tickTask;

    public void onLoad() {
        try {
            settings = readSettings();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "MiaoChineseBC 读取配置失败，插件未启用：" + e.getMessage(), e);
            return;
        }
        translator = new Translator(getProxy());
        Logger root = getProxy().getLogger();
        filter = new ConsoleFilter(this, root.getFilter());
        root.setFilter(filter);
        Settings s = settings;
        if (s.plainFileLog) wrapFileFormatters(root);
        log(
                Level.INFO,
                s.render(
                        "command.enabled",
                        "translate", s.raw(s.translate ? "command.state-on" : "command.state-off", ""),
                        "seconds", s.windowMillis / 1000));
    }

    public void onEnable() {
        if (filter == null) return;
        getProxy().getPluginManager().registerCommand(this, new AdminCommand(this));
        tickTask =
                getProxy()
                        .getScheduler()
                        .schedule(
                                this,
                                new Runnable() {
                                    public void run() {
                                        flush(false);
                                    }
                                },
                                1,
                                1,
                                TimeUnit.SECONDS);
    }

    public void onDisable() {
        if (filter == null) return;
        if (tickTask != null) tickTask.cancel();
        flush(true);
        if (proxyRunning()) {
            // 被插件管理器单独卸载：恢复原有过滤器；若之后又有别的过滤器包在外面，只停用自己
            Logger root = getProxy().getLogger();
            if (root.getFilter() == filter) root.setFilter(filter.previous);
            filter.active = false;
            unwrapFileFormatters(root);
        }
        // 代理正在关闭时保留过滤器，继续汉化之后的关闭日志
    }

    private static void wrapFileFormatters(Logger root) {
        for (Handler h : root.getHandlers())
            if (h instanceof FileHandler && !(h.getFormatter() instanceof PlainFileFormatter))
                h.setFormatter(new PlainFileFormatter(h.getFormatter()));
    }

    private static void unwrapFileFormatters(Logger root) {
        for (Handler h : root.getHandlers())
            if (h.getFormatter() instanceof PlainFileFormatter)
                h.setFormatter(((PlainFileFormatter) h.getFormatter()).wrapped);
    }

    private Settings readSettings() throws IOException {
        return new Settings(load("config.yml"), load("messages.yml"), getLogger());
    }

    private Configuration load(String name) throws IOException {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs())
            throw new IOException("无法创建插件目录");
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream(name)) {
                Files.copy(in, file.toPath());
            }
        }
        return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
    }

    /** 重载配置；读取失败时抛出异常并保留原配置。已折叠的计数不受影响。 */
    void reload() throws IOException {
        settings = readSettings();
        Logger root = getProxy().getLogger();
        if (settings.plainFileLog) wrapFileFormatters(root);
        else unwrapFileFormatters(root);
    }

    Settings settings() {
        return settings;
    }

    Folder folder() {
        return folder;
    }

    /** 过滤器回调：返回 false 表示丢弃这条日志。 */
    boolean handle(LogRecord record) {
        Settings s = settings;
        Translator.Match match = translator.classify(record, s);
        if (match == null) return true;
        if (!s.show(match.kind)) return false;
        String key = match.key();
        if (key != null && s.fold(match.kind) && !folder.admit(match.kind, key, System.currentTimeMillis(), s))
            return false;
        if (s.translate) {
            record.setMessage(translator.render(match, s));
            record.setParameters(null);
        }
        return true;
    }

    private void flush(boolean all) {
        Settings s = settings;
        for (Folder.Summary f : folder.tick(System.currentTimeMillis(), s, all)) {
            String seconds = String.valueOf(Math.max(1, (f.elapsedMillis + 500) / 1000));
            String label = s.raw("labels." + f.kind.id, f.kind.id);
            log(
                    Level.INFO,
                    f.flood
                            ? s.render("flood-summary", "keys", f.keys, "count", f.count, "seconds", seconds, "label", label)
                            : s.render("fold-summary", "key", f.key, "count", f.count, "seconds", seconds, "label", label));
        }
    }

    /** 通过主日志记录器输出，与 BungeeCord 自身日志格式一致。 */
    private void log(Level level, String message) {
        Logger root = getProxy().getLogger();
        root.log(new ConsoleFilter.Own(level, message, root.getName()));
    }

    /** 读取 BungeeCord.isRunning；关闭流程中为 false。无法读取时视为仍在运行。 */
    private boolean proxyRunning() {
        try {
            return getProxy().getClass().getField("isRunning").getBoolean(getProxy());
        } catch (Throwable e) {
            return true;
        }
    }
}
