package com.staffplaytime.gui;

import com.staffplaytime.StaffPlaytimePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Makes sure nobody can take items out of the GUI and passes button clicks on. */
public class GuiListener implements Listener {

    public GuiListener(StaffPlaytimePlugin plugin) {
        // nothing needed, kept so the constructor matches the other listeners
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof LeaderboardGui gui)) return;

        event.setCancelled(true); // never let items move, also stops shift-click from the player inventory

        // only react to clicks inside the GUI, not in the player's own inventory
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        gui.handleClick(event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof LeaderboardGui) {
            event.setCancelled(true);
        }
    }
}
