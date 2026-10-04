package com.staffplaytime;

import com.staffplaytime.command.StaffPlaytimeCommand;
import com.staffplaytime.gui.GuiListener;
import com.staffplaytime.gui.LeaderboardGui;
import com.staffplaytime.storage.Storage;
import com.staffplaytime.tracking.MovementListener;
import com.staffplaytime.tracking.PlaytimeTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;

public class StaffPlaytimePlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private Storage storage;
    private PlaytimeTracker tracker;

    private BukkitTask tickTask;
    private BukkitTask flushTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig(), getLogger());
        messages = new Messages(getConfig());

        // database
        storage = new Storage(this, settings);
        try {
            storage.init();
        } catch (SQLException e) {
            getLogger().severe("Could not connect to the database: " + e.getMessage());
            getLogger().severe("Check the storage section in config.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // tracking
        tracker = new PlaytimeTracker(this);
        getServer().getPluginManager().registerEvents(new MovementListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        // players already online (after /reload) count as active
        for (Player p : Bukkit.getOnlinePlayers()) {
            tracker.markMoved(p.getUniqueId());
        }

        // command
        StaffPlaytimeCommand command = new StaffPlaytimeCommand(this);
        getCommand("staffplaytime").setExecutor(command);
        getCommand("staffplaytime").setTabCompleter(command);

        startTasks();
        getLogger().info("StaffPlaytime enabled (" + settings.storageType + ", " + settings.getRanks().size() + " staff ranks)");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (flushTask != null) flushTask.cancel();

        // save whatever is still in memory
        if (tracker != null && storage != null) {
            tracker.flush();
        }
    }

    private void startTasks() {
        // every second: count playtime (main thread)
        tickTask = Bukkit.getScheduler().runTaskTimer(this, tracker::tick, 20L, 20L);

        // every X seconds: save to the database (async)
        long flushTicks = settings.flushIntervalSeconds * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, tracker::flush, flushTicks, flushTicks);
    }

    /** /staffplaytime reload. Storage settings need a full restart. */
    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        messages.reload(getConfig());

        tickTask.cancel();
        flushTask.cancel();
        startTasks();
    }

    /** Re-reads the database for everyone who has the GUI open. */
    public void refreshOpenGuis() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof LeaderboardGui gui) {
                gui.refresh();
            }
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Storage storage() {
        return storage;
    }

    public PlaytimeTracker tracker() {
        return tracker;
    }
}
