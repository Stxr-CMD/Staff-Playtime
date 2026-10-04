package com.staffplaytime.storage;

import com.staffplaytime.Settings;
import com.staffplaytime.StaffPlaytimePlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All database work lives here (SQLite or MySQL).
 *
 * One row = one staff member in one week:
 *   uuid | week_id | name | rank_id | seconds
 *
 * IMPORTANT: these methods block, so call them from an async task, never from the main thread.
 * We open a fresh connection for every call. It is simple and it can't time out on MySQL.
 */
public class Storage {

    private static final String TABLE = "staff_playtime";

    private final StaffPlaytimePlugin plugin;
    private final Settings settings;
    private final boolean mysql;

    public Storage(StaffPlaytimePlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.mysql = settings.storageType.equals("mysql");
    }

    /** Small holder for the seconds we want to add. */
    public record Delta(UUID uuid, String weekId, String name, String rankId, long seconds) {
    }

    // ------------------------------------------------------------
    // Connection + table
    // ------------------------------------------------------------

    private Connection connect() throws SQLException {
        try {
            if (mysql) {
                Class.forName("com.mysql.cj.jdbc.Driver");
                String url = "jdbc:mysql://" + settings.mysqlHost + ":" + settings.mysqlPort + "/"
                        + settings.mysqlDatabase + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
                return DriverManager.getConnection(url, settings.mysqlUser, settings.mysqlPassword);
            }
            Class.forName("org.sqlite.JDBC");
            File file = new File(plugin.getDataFolder(), "playtime.db");
            return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found for " + settings.storageType, e);
        }
    }

    public void init() throws SQLException {
        plugin.getDataFolder().mkdirs();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "uuid VARCHAR(36) NOT NULL, "
                    + "week_id VARCHAR(10) NOT NULL, "
                    + "name VARCHAR(16) NOT NULL, "
                    + "rank_id VARCHAR(32) NOT NULL, "
                    + "seconds BIGINT NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, week_id))");
        }
    }

    // ------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------

    /** Adds the seconds on top of what is already saved (creates the row if needed). */
    public void addSeconds(List<Delta> deltas) throws SQLException {
        if (deltas.isEmpty()) return;

        String sql;
        if (mysql) {
            sql = "INSERT INTO " + TABLE + " (uuid, week_id, name, rank_id, seconds) VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE seconds = seconds + VALUES(seconds), "
                    + "name = VALUES(name), rank_id = VALUES(rank_id)";
        } else {
            sql = "INSERT INTO " + TABLE + " (uuid, week_id, name, rank_id, seconds) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT(uuid, week_id) DO UPDATE SET seconds = seconds + excluded.seconds, "
                    + "name = excluded.name, rank_id = excluded.rank_id";
        }

        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            for (Delta d : deltas) {
                ps.setString(1, d.uuid().toString());
                ps.setString(2, d.weekId());
                ps.setString(3, d.name());
                ps.setString(4, d.rankId());
                ps.setLong(5, d.seconds());
                ps.addBatch();
            }
            ps.executeBatch();
            c.commit();
        }
    }

    /** Wipes one player's playtime. onlyWeekId = null means every week. */
    public void resetPlayer(UUID uuid, String onlyWeekId) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE uuid = ?" + (onlyWeekId != null ? " AND week_id = ?" : "");
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (onlyWeekId != null) ps.setString(2, onlyWeekId);
            ps.executeUpdate();
        }
    }

    /** Wipes everybody. onlyWeekId = null means every week (full history). */
    public void resetAll(String onlyWeekId) throws SQLException {
        String sql = "DELETE FROM " + TABLE + (onlyWeekId != null ? " WHERE week_id = ?" : "");
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (onlyWeekId != null) ps.setString(1, onlyWeekId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------

    /** Everyone who has any playtime, with this week / last week / total filled in. */
    public List<StaffEntry> loadAll(String thisWeekId, String lastWeekId) throws SQLException {
        Map<UUID, StaffEntry> map = new LinkedHashMap<>();
        // newest rows first so the name and rank we keep are the most recent ones
        String sql = "SELECT uuid, week_id, name, rank_id, seconds FROM " + TABLE + " ORDER BY week_id DESC";

        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                StaffEntry entry = map.computeIfAbsent(uuid,
                        k -> new StaffEntry(uuid, safe(rs), safeRank(rs)));
                String week = rs.getString("week_id");
                long seconds = rs.getLong("seconds");

                entry.total += seconds;
                if (week.equals(thisWeekId)) entry.thisWeek = seconds;
                if (week.equals(lastWeekId)) entry.lastWeek = seconds;
            }
        }
        return new java.util.ArrayList<>(map.values());
    }

    public StaffEntry loadOne(UUID uuid, String thisWeekId, String lastWeekId) throws SQLException {
        for (StaffEntry e : loadAll(thisWeekId, lastWeekId)) {
            if (e.uuid.equals(uuid)) return e;
        }
        return null;
    }

    /** Find a saved staff member by name (for offline players). */
    public UUID findByName(String name) throws SQLException {
        String sql = "SELECT uuid FROM " + TABLE + " WHERE LOWER(name) = LOWER(?) ORDER BY week_id DESC LIMIT 1";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString("uuid"));
            }
        }
        return null;
    }

    private String safe(ResultSet rs) {
        try {
            return rs.getString("name");
        } catch (SQLException e) {
            return "Unknown";
        }
    }

    private String safeRank(ResultSet rs) {
        try {
            return rs.getString("rank_id");
        } catch (SQLException e) {
            return "";
        }
    }
}
