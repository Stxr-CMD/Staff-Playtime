package com.staffplaytime.command;

import com.staffplaytime.StaffPlaytimePlugin;
import com.staffplaytime.StaffRank;
import com.staffplaytime.gui.LeaderboardGui;
import com.staffplaytime.gui.LeaderboardGui.View;
import com.staffplaytime.storage.StaffEntry;
import com.staffplaytime.util.TimeFormat;
import com.staffplaytime.util.WeekUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /staffplaytime (aliases: /spt, /splaytime)
 *
 * Adding a new subcommand:
 *   1. add a case in onCommand
 *   2. write the method
 *   3. add it to onTabComplete and plugin.yml permissions
 */
public class StaffPlaytimeCommand implements CommandExecutor, TabCompleter {

    private static final long CONFIRM_MILLIS = 15_000;

    private final StaffPlaytimePlugin plugin;

    // who typed /spt resetall and is waiting to confirm
    private final Map<String, PendingReset> pendingResets = new HashMap<>();

    private record PendingReset(boolean history, long time) {
    }

    public StaffPlaytimeCommand(StaffPlaytimePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staffplaytime.use")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.messages().sendList(sender, "help");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check" -> check(sender, args);
            case "gui" -> openGui(sender, "staffplaytime.gui", View.THIS_WEEK);
            case "leaderboard", "lb", "top" -> openGui(sender, "staffplaytime.leaderboard", View.THIS_WEEK);
            case "lastweek" -> openGui(sender, "staffplaytime.lastweek", View.LAST_WEEK);
            case "reset" -> reset(sender, args);
            case "resetall" -> resetAll(sender, args);
            case "reload" -> reload(sender);
            default -> plugin.messages().send(sender, "unknown-command");
        }
        return true;
    }

    // ------------------------------------------------------------
    // /spt gui | leaderboard | lastweek
    // ------------------------------------------------------------

    private void openGui(CommandSender sender, String permission, View view) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission(permission)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        LeaderboardGui.open(plugin, player, view, 0);
    }

    // ------------------------------------------------------------
    // /spt check [player]
    // ------------------------------------------------------------

    private void check(CommandSender sender, String[] args) {
        UUID uuid = null;
        String name;

        if (args.length >= 2 && !isSelf(sender, args[1])) {
            if (!sender.hasPermission("staffplaytime.check.others")) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            name = args[1];
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) uuid = online.getUniqueId();
        } else {
            if (!(sender instanceof Player self)) {
                plugin.messages().send(sender, "usage-check");
                return;
            }
            if (!self.hasPermission("staffplaytime.check")) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            name = self.getName();
            uuid = self.getUniqueId();
        }

        final UUID knownUuid = uuid;
        final String lookupName = name;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID id = knownUuid != null ? knownUuid : plugin.storage().findByName(lookupName);
                StaffEntry entry = null;
                if (id != null) {
                    plugin.tracker().flush();
                    entry = plugin.storage().loadOne(id,
                            WeekUtil.currentWeekId(plugin.settings()),
                            WeekUtil.lastWeekId(plugin.settings()));
                }
                final StaffEntry result = entry;
                sync(() -> sendCheck(sender, lookupName, result));
            } catch (SQLException e) {
                plugin.getLogger().warning("check failed: " + e.getMessage());
                sync(() -> plugin.messages().send(sender, "database-error"));
            }
        });
    }

    private void sendCheck(CommandSender sender, String askedName, StaffEntry entry) {
        if (entry == null) {
            plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("player", askedName));
            return;
        }

        StaffRank rank = plugin.settings().getRank(entry.rankId);
        Component rankText = rank != null
                ? plugin.messages().parse(rank.color() + rank.display())
                : Component.text(entry.rankId);

        plugin.messages().sendList(sender, "check",
                Placeholder.unparsed("player", entry.name),
                Placeholder.component("rank", rankText),
                Placeholder.unparsed("this_week", TimeFormat.format(entry.thisWeek)),
                Placeholder.unparsed("last_week", TimeFormat.format(entry.lastWeek)),
                Placeholder.unparsed("all_time", TimeFormat.format(entry.total)));
    }

    private boolean isSelf(CommandSender sender, String name) {
        return sender instanceof Player p && p.getName().equalsIgnoreCase(name);
    }

    // ------------------------------------------------------------
    // /spt reset <player> [all]
    // ------------------------------------------------------------

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("staffplaytime.reset")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-reset");
            return;
        }

        final String name = args[1];
        final boolean wholeHistory = args.length >= 3 && args[2].equalsIgnoreCase("all");
        Player online = Bukkit.getPlayerExact(name);
        final UUID knownUuid = online != null ? online.getUniqueId() : null;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                UUID id = knownUuid != null ? knownUuid : plugin.storage().findByName(name);
                if (id == null) {
                    sync(() -> plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("player", name)));
                    return;
                }
                plugin.tracker().flush(); // save pending seconds first, then wipe
                plugin.storage().resetPlayer(id, wholeHistory ? null : WeekUtil.currentWeekId(plugin.settings()));

                sync(() -> {
                    plugin.messages().send(sender, "reset-done", Placeholder.unparsed("player", name));
                    plugin.refreshOpenGuis();
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("reset failed: " + e.getMessage());
                sync(() -> plugin.messages().send(sender, "database-error"));
            }
        });
    }

    // ------------------------------------------------------------
    // /spt resetall [history]   (asks you to run it twice)
    // ------------------------------------------------------------

    private void resetAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("staffplaytime.resetall")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }

        boolean history = args.length >= 2 && args[1].equalsIgnoreCase("history");
        if (history && !sender.hasPermission("staffplaytime.resetall.history")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }

        String scope = history ? "ALL playtime including previous weeks" : "this week's playtime";
        String key = sender.getName();
        long now = System.currentTimeMillis();
        PendingReset pending = pendingResets.get(key);

        // first time (or too late, or other mode): ask for confirmation
        if (pending == null || pending.history() != history || now - pending.time() > CONFIRM_MILLIS) {
            pendingResets.put(key, new PendingReset(history, now));
            plugin.messages().send(sender, "resetall-warning", Placeholder.unparsed("scope", scope));
            return;
        }
        pendingResets.remove(key);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.tracker().flush();
                plugin.storage().resetAll(history ? null : WeekUtil.currentWeekId(plugin.settings()));

                sync(() -> {
                    plugin.messages().send(sender, "resetall-done", Placeholder.unparsed("scope", scope));
                    plugin.refreshOpenGuis(); // everyone looking at the GUI sees the new numbers

                    // the person who did it gets the GUI opened if they weren't looking at it
                    if (sender instanceof Player p && p.hasPermission("staffplaytime.gui")
                            && !(p.getOpenInventory().getTopInventory().getHolder() instanceof LeaderboardGui)) {
                        LeaderboardGui.open(plugin, p, View.THIS_WEEK, 0);
                    }
                });
            } catch (SQLException e) {
                plugin.getLogger().warning("resetall failed: " + e.getMessage());
                sync(() -> plugin.messages().send(sender, "database-error"));
            }
        });
    }

    // ------------------------------------------------------------
    // /spt reload
    // ------------------------------------------------------------

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("staffplaytime.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.reload();
        plugin.messages().send(sender, "reloaded");
    }

    // ------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.add("help");
            if (sender.hasPermission("staffplaytime.check") || sender.hasPermission("staffplaytime.check.others")) options.add("check");
            if (sender.hasPermission("staffplaytime.gui")) options.add("gui");
            if (sender.hasPermission("staffplaytime.leaderboard")) options.add("leaderboard");
            if (sender.hasPermission("staffplaytime.lastweek")) options.add("lastweek");
            if (sender.hasPermission("staffplaytime.reset")) options.add("reset");
            if (sender.hasPermission("staffplaytime.resetall")) options.add("resetall");
            if (sender.hasPermission("staffplaytime.reload")) options.add("reload");
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            boolean wantsPlayer = (sub.equals("check") && sender.hasPermission("staffplaytime.check.others"))
                    || (sub.equals("reset") && sender.hasPermission("staffplaytime.reset"));
            if (wantsPlayer) {
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            }
            if (sub.equals("resetall") && sender.hasPermission("staffplaytime.resetall.history")) {
                options.add("history");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("staffplaytime.reset")) {
            options.add("all");
        }

        String typed = args[args.length - 1].toLowerCase();
        options.removeIf(option -> !option.toLowerCase().startsWith(typed));
        return options;
    }

    // ------------------------------------------------------------
    // helper: run something back on the main thread
    // ------------------------------------------------------------

    private void sync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
