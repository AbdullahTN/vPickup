package com.example.vpickup.listener;

import com.example.vpickup.VPickup;
import com.example.vpickup.manager.ActionBarManager;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

/**
 * Manages the player session lifecycle:
 *   • Join  → async-load PlayerData into cache
 *   • Quit  → evict action-bar buffer, persist data
 */
public final class PlayerSessionListener implements Listener {

    private final VPickup          plugin;
    private final ActionBarManager actionBar;

    public PlayerSessionListener(VPickup plugin, ActionBarManager actionBar) {
        this.plugin    = plugin;
        this.actionBar = actionBar;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Fire an async DB load; cache is populated before any block break can occur
        plugin.getDatabaseManager().loadAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        // Flush any pending action-bar tasks to avoid orphaned scheduler entries
        actionBar.evict(uuid);

        // Async-persist final state (no data loss, zero main-thread blocking)
        plugin.getDatabaseManager().saveAsync(
                plugin.getDatabaseManager().get(uuid)
        );

        // Evict from in-memory cache
        plugin.getDatabaseManager().invalidate(uuid);
    }
}
