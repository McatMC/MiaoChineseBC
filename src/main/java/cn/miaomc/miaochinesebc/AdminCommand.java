package cn.miaomc.miaochinesebc;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /miaochinesebc reload|status。 */
final class AdminCommand extends Command implements TabExecutor {
    private final MiaoChineseBC plugin;

    AdminCommand(MiaoChineseBC plugin) {
        super("miaochinesebc", "miaochinesebc.admin", "mcbc");
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        Settings s = plugin.settings();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String prefix = s.finish(s.raw("command.prefix", ""));
        if (sub.equals("reload")) {
            try {
                plugin.reload();
                Settings now = plugin.settings();
                send(sender, now.finish(now.raw("command.prefix", "")) + now.render("command.reloaded"));
            } catch (Exception e) {
                send(sender, prefix + s.render("command.reload-failed", "error", e.getMessage()));
                plugin.getLogger().warning("重载配置失败：" + e.getMessage());
            }
        } else if (sub.equals("status")) {
            Folder f = plugin.folder();
            send(
                    sender,
                    prefix
                            + s.render(
                                    "command.status",
                                    "seconds", s.windowMillis / 1000,
                                    "keys", f.keys(),
                                    "pending", f.pending(),
                                    "total", f.total()));
        } else {
            for (String line : s.lines("command.help")) send(sender, line);
        }
    }

    private static void send(CommandSender to, String text) {
        to.sendMessage(TextComponent.fromLegacyText(text));
    }

    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1)
            for (String s : new String[] {"reload", "status"})
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        return out;
    }
}
