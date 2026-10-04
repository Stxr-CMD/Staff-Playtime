package com.staffplaytime.tracking;

import com.staffplaytime.StaffPlaytimePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/**
 * Tells the tracker when a player moves.
 * Only real movement counts, not just standing there.
 */
public class MovementListener implements Listener {

    private final StaffPlaytimePlugin plugin;

    public MovementListener(StaffPlaytimePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // joining counts as activity so the timer starts right away
        plugin.tracker().markMoved(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.tracker().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        double min = plugin.settings().minMoveDistance;
        boolean moved = from.getWorld() != to.getWorld() || from.distanceSquared(to) > min * min;

        // optional: camera movement also counts
        if (!moved && plugin.settings().countHeadRotation) {
            moved = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        }

        if (moved) {
            plugin.tracker().markMoved(event.getPlayer().getUniqueId());
        }
    }

    // Riding a boat, minecart or horse doesn't fire PlayerMoveEvent, so we watch the vehicle.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        double min = plugin.settings().minMoveDistance;
        if (event.getFrom().distanceSquared(event.getTo()) <= min * min) return;

        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player) {
                plugin.tracker().markMoved(player.getUniqueId());
            }
        }
    }
}
