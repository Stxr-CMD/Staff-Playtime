package com.staffplaytime;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Reads config.yml once and keeps the values in plain fields.
 * Want a new option? Add it to config.yml, then add a field here.
 */
public class Settings {

    // tracking
    public final int idleSeconds;
    public final double minMoveDistance;
    public final boolean countHeadRotation;
    public final int flushIntervalSeconds;
    public final Set<String> ignoredWorlds;

    // week
    public final DayOfWeek weekStart;
    public final ZoneId zone;

    // storage
    public final String storageType;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlDatabase;
    public final String mysqlUser;
    public final String mysqlPassword;

    // gui
    public final String guiTitle;
    public final String borderMaterial;
    public final String fillerMaterial;

    // ranks (sorted, highest weight first)
    private final List<StaffRank> ranks = new ArrayList<>();

    public Settings(FileConfiguration cfg, Logger log) {
        idleSeconds = Math.max(1, cfg.getInt("tracking.idle-seconds", 30));
        minMoveDistance = Math.max(0, cfg.getDouble("tracking.min-move-distance", 0.1));
        countHeadRotation = cfg.getBoolean("tracking.count-head-rotation", false);
        flushIntervalSeconds = Math.max(10, cfg.getInt("tracking.flush-interval-seconds", 60));
        ignoredWorlds = new HashSet<>(cfg.getStringList("tracking.ignored-worlds"));

        DayOfWeek day = DayOfWeek.MONDAY;
        try {
            day = DayOfWeek.valueOf(cfg.getString("week.start-day", "MONDAY").toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warning("week.start-day is not a valid day, using MONDAY");
        }
        weekStart = day;

        ZoneId zoneId = ZoneId.of("UTC");
        try {
            zoneId = ZoneId.of(cfg.getString("week.timezone", "UTC"));
        } catch (Exception e) {
            log.warning("week.timezone is not valid, using UTC");
        }
        zone = zoneId;

        storageType = cfg.getString("storage.type", "sqlite").toLowerCase();
        mysqlHost = cfg.getString("storage.mysql.host", "localhost");
        mysqlPort = cfg.getInt("storage.mysql.port", 3306);
        mysqlDatabase = cfg.getString("storage.mysql.database", "minecraft");
        mysqlUser = cfg.getString("storage.mysql.username", "root");
        mysqlPassword = cfg.getString("storage.mysql.password", "");

        guiTitle = cfg.getString("gui.title", "<dark_red><bold>STAFF PLAYTIME</bold></dark_red> <dark_gray>»</dark_gray> <gray><view>");
        borderMaterial = cfg.getString("gui.border-material", "BLACK_STAINED_GLASS_PANE");
        fillerMaterial = cfg.getString("gui.filler-material", "GRAY_STAINED_GLASS_PANE");

        loadRanks(cfg, log);
    }

    private void loadRanks(FileConfiguration cfg, Logger log) {
        ConfigurationSection section = cfg.getConfigurationSection("ranks");
        if (section == null) {
            log.warning("No ranks found in config.yml, nobody will be tracked!");
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null) continue;
            String permission = r.getString("permission");
            if (permission == null || permission.isBlank()) {
                log.warning("Rank '" + id + "' has no permission set, skipping it");
                continue;
            }
            ranks.add(new StaffRank(
                    id.toLowerCase(),
                    r.getString("display", id),
                    r.getString("color", "<white>"),
                    permission,
                    r.getInt("weight", 0)
            ));
        }
        ranks.sort(Comparator.comparingInt(StaffRank::weight).reversed());
    }

    /** Highest staff rank this player has, or null if they are not staff. */
    public StaffRank findRank(Player player) {
        for (StaffRank rank : ranks) {
            if (player.hasPermission(rank.permission())) {
                return rank;
            }
        }
        return null;
    }

    /** Look a rank up by the id we saved in the database. */
    public StaffRank getRank(String id) {
        for (StaffRank rank : ranks) {
            if (rank.id().equalsIgnoreCase(id)) {
                return rank;
            }
        }
        return null;
    }

    public List<StaffRank> getRanks() {
        return ranks;
    }
}
