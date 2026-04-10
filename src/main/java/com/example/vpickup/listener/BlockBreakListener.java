package com.example.vpickup.listener;

import com.example.vpickup.VPickup;
import com.example.vpickup.data.PlayerData;
import com.example.vpickup.manager.ActionBarManager;
import com.example.vpickup.manager.AutoBlockManager;
import com.example.vpickup.manager.AutoSmeltManager;import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Core event listener for vPickup.
 *
 * ── Flow ──
 *   1. Player breaks a block with Auto-Pickup enabled.
 *   2. Cancel item drops (setDropItems(false) / suppress event drops).
 *   3. Compute correct drops (Fortune / Silk Touch aware).
 *   4. Optionally auto-smelt drops.
 *   5. Add drops directly to player inventory.
 *      → If full, drop on the ground and show "INVENTORY FULL" action bar.
 *   6. Optionally auto-compress inventory via AutoBlockManager.
 *   7. Send aggregated action-bar notification via ActionBarManager.
 *   8. Play the appropriate sound.
 */
public final class BlockBreakListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final VPickup          plugin;
    private final ActionBarManager actionBar;
    private final AutoSmeltManager autoSmelt;
    private final AutoBlockManager autoBlock;

    // Config-derived caches (cheap lookups on hot path)
    private final Set<Material>    rareMaterials;
    private final Sound            normalSound;
    private final float            normalVolume;
    private final float            normalPitch;
    private final Sound            rareSound;
    private final float            rareVolume;
    private final float            rarePitch;
    private final String           fullMessage;

    public BlockBreakListener(VPickup plugin, ActionBarManager actionBar,
                               AutoSmeltManager autoSmelt, AutoBlockManager autoBlock) {
        this.plugin     = plugin;
        this.actionBar  = actionBar;
        this.autoSmelt  = autoSmelt;
        this.autoBlock  = autoBlock;

        // Pre-load config values once — avoids repeated YAML lookups on every block break
        rareMaterials = loadRareMaterials();
        normalSound   = parseSound("sounds.normal-pickup.sound",   Sound.ENTITY_ITEM_PICKUP);
        normalVolume  = (float) plugin.getConfig().getDouble("sounds.normal-pickup.volume", 0.3);
        normalPitch   = (float) plugin.getConfig().getDouble("sounds.normal-pickup.pitch",  1.2);
        rareSound     = parseSound("sounds.rare-pickup.sound",     Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        rareVolume    = (float) plugin.getConfig().getDouble("sounds.rare-pickup.volume", 0.5);
        rarePitch     = (float) plugin.getConfig().getDouble("sounds.rare-pickup.pitch",  1.0);
        fullMessage   = plugin.getLangManager().getRaw("inventory-full");
    }

    // ── Event handler ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDatabaseManager().get(player.getUniqueId());

        if (!data.isAutoPickup()) return;

        Block     block = event.getBlock();
        ItemStack tool  = player.getInventory().getItemInMainHand();

        // Suppress the vanilla item-drop — we handle distribution ourselves
        event.setDropItems(false);

        // ── 1. Compute drops ────────────────────────────────────────────────
        Collection<ItemStack> drops = computeDrops(block, tool);

        // ── 2. XP — grab what the event would have given ────────────────────
        int xpToDrop = event.getExpToDrop();
        // We award XP to the player directly instead of spawning an orb
        event.setExpToDrop(0);

        // ── 3. Optional Auto-Smelt ──────────────────────────────────────────
        if (data.isAutoSmelt()) {
            drops = smeltDrops(drops);
        }

        // ── 4. Add to inventory ─────────────────────────────────────────────
        boolean anyFull = false;
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;

            Map<Integer, ItemStack> leftover =
                    player.getInventory().addItem(drop.clone());

            if (!leftover.isEmpty()) {
                // Inventory full: drop remainder at block location
                anyFull = true;
                for (ItemStack excess : leftover.values()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), excess);
                }
            } else {
                // Successfully added — record for action bar
                boolean rare = rareMaterials.contains(drop.getType());
                actionBar.record(player, drop.getType(), drop.getAmount(), 0);
                playPickupSound(player, block.getLocation(), rare);
            }
        }

        // Award XP directly (spawns no orb, just adds to counter)
        if (xpToDrop > 0) {
            player.giveExp(xpToDrop);
            // Re-record XP into the action-bar aggregator by piggy-backing on a
            // synthetic record with the first non-null drop material
            drops.stream().filter(Objects::nonNull).findFirst().ifPresent(d ->
                    actionBar.record(player, d.getType(), 0, xpToDrop));
        }

        if (anyFull) {
            player.sendActionBar(MM.deserialize(fullMessage));
        }

        // ── 5. Optional Auto-Block ──────────────────────────────────────────
        if (data.isAutoBlock()) {
            autoBlock.compress(player);
        }
    }

    // ── Drop computation ──────────────────────────────────────────────────────

    /**
     * Returns the correct drops for the block + tool combination,
     * respecting Fortune and Silk Touch enchantments.
     *
     * Paper's {@code Block#getDrops(ItemStack)} already handles Fortune maths
     * internally, so we simply delegate to it.
     */
    private static Collection<ItemStack> computeDrops(Block block, ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return block.getDrops(new ItemStack(Material.AIR));
        }

        // Silk Touch check — getDrops with the actual tool honours Silk Touch
        return block.getDrops(tool);
    }

    /** Replaces smeltable items with their smelted results. */
    private Collection<ItemStack> smeltDrops(Collection<ItemStack> drops) {
        List<ItemStack> result = new ArrayList<>(drops.size());
        for (ItemStack item : drops) {
            if (item == null || item.getType().isAir()) continue;
            ItemStack smelted = autoSmelt.smelt(item.getType(), item.getAmount());
            result.add(smelted != null ? smelted : item);
        }
        return result;
    }

    // ── Sound helpers ─────────────────────────────────────────────────────────

    private void playPickupSound(Player player, Location loc, boolean rare) {
        if (rare) {
            player.playSound(loc, rareSound,  rareVolume,   rarePitch);
        } else {
            player.playSound(loc, normalSound, normalVolume, normalPitch);
        }
    }

    // ── Config helpers ────────────────────────────────────────────────────────

    private Set<Material> loadRareMaterials() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        List<String>  list = plugin.getConfig().getStringList("rare-materials");
        for (String name : list) {
            try {
                set.add(Material.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    private Sound parseSound(String path, Sound fallback) {
        String name = plugin.getConfig().getString(path);
        if (name == null) return fallback;
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
