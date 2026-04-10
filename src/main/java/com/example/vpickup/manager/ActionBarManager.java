package com.example.vpickup.manager;

import com.example.vpickup.VPickup;
import com.example.vpickup.util.TextUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aggregates item-pickup events over a configurable window (default 20 ticks)
 * and then flushes a single action-bar message per player.
 *
 * ── Why aggregate? ──
 *   Fortune can drop 2-4 items per ore. Without aggregation, the action bar
 *   would flicker and look spammy. Batching within a 1-second window merges
 *   all concurrent drops into one clean message.
 *
 * ── Thread model ──
 *   All methods are called from the main thread (BukkitEvent / BukkitTask),
 *   so no synchronisation is needed beyond the Maps.
 */
public final class ActionBarManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Per-player pending pickup totals: material → count
    private final Map<UUID, Map<Material, Integer>> pending = new HashMap<>();
    // Per-player pending XP
    private final Map<UUID, Integer> pendingXp = new HashMap<>();
    // Scheduled flush task handle
    private final Map<UUID, BukkitTask> flushTasks = new HashMap<>();

    private final VPickup plugin;
    private final int flushTicks;
    private final String gradStart;
    private final String gradEnd;

    public ActionBarManager(VPickup plugin) {
        this.plugin     = plugin;
        this.flushTicks = plugin.getConfig().getInt("actionbar-flush-ticks", 20);
        this.gradStart  = plugin.getConfig().getString("actionbar-gradient-start", "#ffaa00");
        this.gradEnd    = plugin.getConfig().getString("actionbar-gradient-end",   "#ffffff");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Records an item pickup and schedules (or resets) the flush task.
     *
     * @param player  the player who picked up the item
     * @param mat     the material picked up
     * @param amount  number of items
     * @param xp      experience to credit (0 if none)
     */
    public void record(Player player, Material mat, int amount, int xp) {
        UUID uuid = player.getUniqueId();

        // Accumulate material counts
        pending.computeIfAbsent(uuid, k -> new LinkedHashMap<>())
               .merge(mat, amount, Integer::sum);

        // Accumulate XP
        pendingXp.merge(uuid, xp, Integer::sum);

        // Cancel any existing flush task and reschedule — this resets the window
        BukkitTask existing = flushTasks.remove(uuid);
        if (existing != null) existing.cancel();

        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> flush(player), flushTicks);
        flushTasks.put(uuid, task);
    }

    /**
     * Immediately cancels and clears any pending data for a player.
     * Call on player quit to avoid orphaned tasks.
     */
    public void evict(UUID uuid) {
        BukkitTask t = flushTasks.remove(uuid);
        if (t != null) t.cancel();
        pending.remove(uuid);
        pendingXp.remove(uuid);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Builds and sends the action-bar message, then clears the buffer.
     *
     * Format examples:
     *   ʟᴀᴘɪs ʟᴀᴢᴜʟɪ x𝟼𝟺 (+𝟷𝟶 XP)    ← single material with XP
     *   ᴅɪᴀᴍᴏɴᴅ x𝟹 · ɪʀᴏɴ ɪɴɢᴏᴛ x𝟷𝟸   ← multiple materials
     */
    private void flush(Player player) {
        UUID uuid = player.getUniqueId();
        Map<Material, Integer> items = pending.remove(uuid);
        int totalXp = pendingXp.getOrDefault(uuid, 0);
        pendingXp.remove(uuid);
        flushTasks.remove(uuid);

        if (items == null || items.isEmpty()) return;
        if (!player.isOnline()) return;

        StringBuilder msg = new StringBuilder();
        msg.append("<gradient:").append(gradStart).append(":").append(gradEnd).append(">");

        boolean first = true;
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            if (!first) msg.append(" <gray>·</gray> ");
            String label  = TextUtil.materialToLabel(entry.getKey());
            String amount = TextUtil.toMonoDigits(entry.getValue());
            msg.append(label).append(" <white>x").append(amount);
            first = false;
        }

        if (totalXp > 0) {
            msg.append(" <gray>(+").append(TextUtil.toMonoDigits(totalXp)).append(" XP)</gray>");
        }

        msg.append("</gradient>");

        Component component = MM.deserialize(msg.toString());
        player.sendActionBar(component);
    }
}
