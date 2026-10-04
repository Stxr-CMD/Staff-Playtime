package com.staffplaytime.gui;

import com.staffplaytime.StaffPlaytimePlugin;
import com.staffplaytime.StaffRank;
import com.staffplaytime.storage.StaffEntry;
import com.staffplaytime.util.TimeFormat;
import com.staffplaytime.util.WeekUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The leaderboard GUI (6 rows).
 *
 *  row 0:  border, info item in the middle
 *  rows 1-4: 28 player heads, best playtime first
 *  row 5:  prev page | this week | last week | close | all time | refresh | next page
 *
 * Want to move a button? Change the SLOT_ numbers below.
 */
public class LeaderboardGui implements InventoryHolder {

    public enum View {
        THIS_WEEK("This Week"),
        LAST_WEEK("Last Week"),
        ALL_TIME("All Time");

        public final String label;

        View(String label) {
            this.label = label;
        }
    }

    // where the heads go (7 columns x 4 rows)
    private static final int[] HEAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    // button slots
    private static final int SLOT_INFO = 4;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_THIS_WEEK = 47;
    private static final int SLOT_LAST_WEEK = 48;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_ALL_TIME = 50;
    private static final int SLOT_REFRESH = 51;
    private static final int SLOT_NEXT = 53;

    private final StaffPlaytimePlugin plugin;
    private final Player viewer;
    private final List<StaffEntry> entries;
    private final View view;
    private int page;
    private Inventory inventory;

    private LeaderboardGui(StaffPlaytimePlugin plugin, Player viewer, List<StaffEntry> entries, View view, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.entries = entries;
        this.view = view;
        this.page = page;
    }

    // ------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------

    /** Loads fresh numbers from the database, then opens the GUI. */
    public static void open(StaffPlaytimePlugin plugin, Player viewer, View view, int page) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<StaffEntry> data = load(plugin);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (data == null) {
                    plugin.messages().send(viewer, "database-error");
                    return;
                }
                new LeaderboardGui(plugin, viewer, data, view, page).show();
            });
        });
    }

    private static List<StaffEntry> load(StaffPlaytimePlugin plugin) {
        try {
            plugin.tracker().flush(); // save pending seconds first so the numbers are current
            return plugin.storage().loadAll(
                    WeekUtil.currentWeekId(plugin.settings()),
                    WeekUtil.lastWeekId(plugin.settings()));
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load playtime: " + e.getMessage());
            return null;
        }
    }

    /** Reload from the database and show again (same tab, same page). */
    public void refresh() {
        open(plugin, viewer, view, page);
    }

    private void show() {
        Component title = plugin.messages().parse(plugin.settings().guiTitle,
                Placeholder.unparsed("view", view.label));
        inventory = Bukkit.createInventory(this, 54, title);
        render();
        viewer.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------

    private void render() {
        inventory.clear();

        ItemStack border = filler(plugin.settings().borderMaterial, Material.BLACK_STAINED_GLASS_PANE);
        ItemStack empty = filler(plugin.settings().fillerMaterial, Material.GRAY_STAINED_GLASS_PANE);

        // whole inventory gets the border, then we place everything else on top
        for (int slot = 0; slot < 54; slot++) {
            inventory.setItem(slot, border);
        }
        for (int slot : HEAD_SLOTS) {
            inventory.setItem(slot, empty);
        }

        // heads
        List<StaffEntry> sorted = sortedEntries();
        int perPage = HEAD_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil(sorted.size() / (double) perPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < sorted.size(); i++) {
            int position = start + i + 1;
            inventory.setItem(HEAD_SLOTS[i], buildHead(sorted.get(start + i), position));
        }

        // buttons
        inventory.setItem(SLOT_INFO, buildInfo(sorted, totalPages));

        if (page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.ARROW, "<yellow>« Previous Page", false,
                    "<gray>Page " + page + "/" + totalPages));
        }
        if (page < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, "<yellow>Next Page »", false,
                    "<gray>Page " + (page + 2) + "/" + totalPages));
        }

        inventory.setItem(SLOT_THIS_WEEK, button(Material.CLOCK, "<green>This Week", view == View.THIS_WEEK,
                "<gray>Playtime since " + WeekUtil.currentWeekStart(plugin.settings()),
                "", "<yellow>Click to view"));
        inventory.setItem(SLOT_LAST_WEEK, button(Material.WRITABLE_BOOK, "<gold>Last Week", view == View.LAST_WEEK,
                "<gray>Playtime of the previous week",
                "", "<yellow>Click to view"));
        inventory.setItem(SLOT_ALL_TIME, button(Material.EXPERIENCE_BOTTLE, "<aqua>All Time", view == View.ALL_TIME,
                "<gray>Everything ever recorded",
                "", "<yellow>Click to view"));
        inventory.setItem(SLOT_REFRESH, button(Material.SUNFLOWER, "<white>Refresh", false,
                "<gray>Load the latest numbers"));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "<red>Close", false));
    }

    private int totalPages() {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) HEAD_SLOTS.length));
    }

    private List<StaffEntry> sortedEntries() {
        List<StaffEntry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> {
            int byTime = Long.compare(valueOf(b), valueOf(a)); // most time first
            return byTime != 0 ? byTime : a.name.compareToIgnoreCase(b.name);
        });
        return sorted;
    }

    private long valueOf(StaffEntry e) {
        return switch (view) {
            case THIS_WEEK -> e.thisWeek;
            case LAST_WEEK -> e.lastWeek;
            case ALL_TIME -> e.total;
        };
    }

    private ItemStack buildHead(StaffEntry e, int position) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(e.uuid));

        StaffRank rank = plugin.settings().getRank(e.rankId);
        String rankColor = rank != null ? rank.color() : "<gray>";
        String rankName = rank != null ? rank.display() : e.rankId;

        // "#1 PlayerName" - name is colored like the rank
        Component name = Component.text("#" + position + " ", positionColor(position))
                .append(plugin.messages().parse(rankColor + "<name>", Placeholder.unparsed("name", e.name)));
        meta.displayName(plain(name));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("<gray>Rank: " + rankColor + rankName));
        lore.add(Component.empty());
        lore.add(statLine("This Week", e.thisWeek, View.THIS_WEEK));
        lore.add(statLine("Last Week", e.lastWeek, View.LAST_WEEK));
        lore.add(statLine("All Time", e.total, View.ALL_TIME));
        lore.add(Component.empty());
        lore.add(line("<gray>Status here: " + statusOf(e)));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /** Online + moving = green, online + standing = yellow, otherwise red. Only for this server. */
    private String statusOf(StaffEntry e) {
        Player online = Bukkit.getPlayer(e.uuid);
        if (online == null) return "<red>Offline";
        return plugin.tracker().isActive(e.uuid) ? "<green>Online" : "<yellow>AFK";
    }

    private Component statLine(String label, long seconds, View lineView) {
        String time = TimeFormat.format(seconds);
        if (lineView == view) {
            return line("<white>▸ " + label + ": <green>" + time); // the tab you are looking at
        }
        return line("<gray>  " + label + ": <white>" + time);
    }

    private ItemStack buildInfo(List<StaffEntry> sorted, int totalPages) {
        long sum = 0;
        for (StaffEntry e : sorted) sum += valueOf(e);

        return button(Material.NETHER_STAR, "<dark_red><bold>STAFF PLAYTIME", false,
                "<gray>Viewing: <white>" + view.label,
                "<gray>Week started: <white>" + WeekUtil.currentWeekStart(plugin.settings()),
                "",
                "<gray>Staff listed: <white>" + sorted.size(),
                "<gray>Combined time: <white>" + TimeFormat.format(sum),
                "<gray>Page: <white>" + (page + 1) + "/" + totalPages);
    }

    // ------------------------------------------------------------
    // Clicks (called from GuiListener)
    // ------------------------------------------------------------

    public void handleClick(int slot) {
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    page--;
                    click();
                    render();
                }
            }
            case SLOT_NEXT -> {
                if (page < totalPages() - 1) {
                    page++;
                    click();
                    render();
                }
            }
            case SLOT_THIS_WEEK -> switchView(View.THIS_WEEK, null);
            case SLOT_LAST_WEEK -> switchView(View.LAST_WEEK, "staffplaytime.lastweek");
            case SLOT_ALL_TIME -> switchView(View.ALL_TIME, "staffplaytime.alltime");
            case SLOT_REFRESH -> {
                click();
                refresh();
            }
            case SLOT_CLOSE -> viewer.closeInventory();
            default -> {
            }
        }
    }

    private void switchView(View newView, String permission) {
        if (permission != null && !viewer.hasPermission(permission)) {
            plugin.messages().send(viewer, "no-permission");
            return;
        }
        if (newView == view) return;
        click();
        // the title changes with the tab, so we open a new inventory (no database call needed)
        Bukkit.getScheduler().runTask(plugin,
                () -> new LeaderboardGui(plugin, viewer, entries, newView, 0).show());
    }

    private void click() {
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }

    // ------------------------------------------------------------
    // Little item helpers
    // ------------------------------------------------------------

    private ItemStack button(Material material, String name, boolean glow, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(name));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(line(l));
            meta.lore(lines);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler(String materialName, Material fallback) {
        Material material = Material.matchMaterial(materialName);
        ItemStack item = new ItemStack(material != null ? material : fallback);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    /** MiniMessage text without the default italic that lore and names get. */
    private Component line(String mini) {
        return plain(plugin.messages().parse(mini));
    }

    private Component plain(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private TextColor positionColor(int position) {
        return switch (position) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> TextColor.color(0xCD7F32); // bronze
            default -> NamedTextColor.DARK_GRAY;
        };
    }
}
