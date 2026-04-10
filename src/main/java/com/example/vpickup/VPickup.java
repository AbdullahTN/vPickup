package com.example.vpickup;

import com.example.vpickup.command.ToggleCommand;
import com.example.vpickup.data.DatabaseManager;
import com.example.vpickup.lang.LangManager;
import com.example.vpickup.listener.BlockBreakListener;
import com.example.vpickup.listener.PlayerSessionListener;
import com.example.vpickup.manager.ActionBarManager;
import com.example.vpickup.manager.AutoBlockManager;
import com.example.vpickup.manager.AutoSmeltManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * vPickup — Main plugin class.
 *
 * ── Startup order ──
 *   1. Copy default config.yml if absent.
 *   2. Initialise SQLite database (blocking, done once during enable).
 *   3. Wire up managers and listeners.
 *   4. Register commands.
 *
 * ── Shutdown order ──
 *   1. Persist all cached PlayerData synchronously (guards against async
 *      tasks not finishing if the server stops abruptly).
 *   2. Close SQLite connection.
 */
public final class VPickup extends JavaPlugin {

    private DatabaseManager  databaseManager;
    private ActionBarManager actionBarManager;
    private AutoSmeltManager autoSmeltManager;
    private AutoBlockManager autoBlockManager;
    private LangManager      langManager;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // ── Config ───────────────────────────────────────────────────────────
        saveDefaultConfig();

        // ── Language ─────────────────────────────────────────────────────────
        langManager = new LangManager(this);
        langManager.load();

        // ── Database ─────────────────────────────────────────────────────────
        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        // ── Managers ─────────────────────────────────────────────────────────
        actionBarManager = new ActionBarManager(this);

        autoSmeltManager = new AutoSmeltManager(this);
        autoSmeltManager.load();

        autoBlockManager = new AutoBlockManager(this);
        autoBlockManager.load();

        // ── Listeners ────────────────────────────────────────────────────────
        var pm = getServer().getPluginManager();
        pm.registerEvents(new BlockBreakListener(this, actionBarManager, autoSmeltManager, autoBlockManager), this);
        pm.registerEvents(new PlayerSessionListener(this, actionBarManager), this);

        // ── Commands ─────────────────────────────────────────────────────────
        // /ap — toggle Auto-Pickup
        Objects.requireNonNull(getCommand("ap")).setExecutor(
                new ToggleCommand(this,
                        data -> { data.toggleAutoPickup(); return data.isAutoPickup(); },
                        "pickup-enabled", "pickup-disabled"));

        // /ab — toggle Auto-Block
        Objects.requireNonNull(getCommand("ab")).setExecutor(
                new ToggleCommand(this,
                        data -> { data.toggleAutoBlock(); return data.isAutoBlock(); },
                        "block-enabled", "block-disabled"));

        // /as — toggle Auto-Smelt
        Objects.requireNonNull(getCommand("as")).setExecutor(
                new ToggleCommand(this,
                        data -> { data.toggleAutoSmelt(); return data.isAutoSmelt(); },
                        "smelt-enabled", "smelt-disabled"));

        getLogger().info("vPickup v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            // Synchronous final flush — ensures no data loss on /stop
            databaseManager.saveAllSync();
            databaseManager.close();
        }
        getLogger().info("vPickup disabled. All data saved.");
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public DatabaseManager  getDatabaseManager()  { return databaseManager;  }
    public ActionBarManager getActionBarManager()  { return actionBarManager; }
    public AutoSmeltManager getAutoSmeltManager()  { return autoSmeltManager; }
    public AutoBlockManager getAutoBlockManager()  { return autoBlockManager; }
    public LangManager      getLangManager()       { return langManager;      }
}
