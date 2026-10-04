package com.staffplaytime.tracking;

import com.staffplaytime.StaffPlaytimePlugin;
import com.staffplaytime.StaffRank;
import com.staffplaytime.Settings;
import com.staffplaytime.storage.Storage;
import com.staffplaytime.util.WeekUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The heart of the plugin.
 *
 * Every second tick() looks at all online players:
 *   - not staff?                    -> skip
 *   - standing still too long?      -> skip (this is the "no AFK time" rule)
 *   - otherwise                     -> +1 second
 *
 * The seconds are collected in memory and written to the database every
 * few seconds (flush), so we never hit the database on the main thread.
 */
public class PlaytimeTracker {

    private final StaffPlaytimePlugin plugin;

    // when each player last moved (milliseconds)
    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<>();

    // seconds waiting to be saved
    private final Map<PendingKey, Pending> pending = new ConcurrentHashMap<>();

    private record PendingKey(UUID uuid, String weekId) {
    }

    private static class Pending {
        String name;
        String rankId;
        long seconds;
    }

    public PlaytimeTracker(StaffPlaytimePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // Movement (called by MovementListener)
    // ------------------------------------------------------------

    public void markMoved(UUID uuid) {
        lastMove.put(uuid, System.currentTimeMillis());
    }

    public void forget(UUID uuid) {
        lastMove.remove(uuid);
    }

    /** True if the player moved within the idle limit. */
    public boolean isActive(UUID uuid) {
        Long moved = lastMove.get(uuid);
        if (moved == null) return false;
        long idleMs = plugin.settings().idleSeconds * 1000L;
        return System.currentTimeMillis() - moved <= idleMs;
    }

    // ------------------------------------------------------------
    // The one-second tick (runs on the main thread)
    // ------------------------------------------------------------

    public void tick() {
        Settings settings = plugin.settings();
        String weekId = WeekUtil.currentWeekId(settings);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("staffplaytime.exempt")) continue;
            if (settings.ignoredWorlds.contains(player.getWorld().getName())) continue;

            StaffRank rank = settings.findRank(player);
            if (rank == null) continue;                  // not staff

            if (!isActive(player.getUniqueId())) continue; // standing still

            pending.compute(new PendingKey(player.getUniqueId(), weekId), (key, old) -> {
                Pending p = old != null ? old : new Pending();
                p.name = player.getName();
                p.rankId = rank.id();
                p.seconds++;
                return p;
            });
        }
    }

    // ------------------------------------------------------------
    // Saving (call from an async task or on shutdown)
    // ------------------------------------------------------------

    public void flush() {
        List<Storage.Delta> batch = new ArrayList<>();

        for (PendingKey key : new ArrayList<>(pending.keySet())) {
            Pending p = pending.remove(key);
            if (p == null || p.seconds <= 0) continue;
            batch.add(new Storage.Delta(key.uuid(), key.weekId(), p.name, p.rankId, p.seconds));
        }
        if (batch.isEmpty()) return;

        try {
            plugin.storage().addSeconds(batch);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save playtime, will try again later: " + e.getMessage());
            // put the seconds back so nothing is lost
            for (Storage.Delta d : batch) {
                pending.compute(new PendingKey(d.uuid(), d.weekId()), (key, old) -> {
                    Pending p = old != null ? old : new Pending();
                    p.name = d.name();
                    p.rankId = d.rankId();
                    p.seconds += d.seconds();
                    return p;
                });
            }
        }
    }
}
