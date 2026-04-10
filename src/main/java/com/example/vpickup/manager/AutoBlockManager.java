package com.example.vpickup.manager;

import com.example.vpickup.VPickup;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Sikiştirma tariflerini compress.yml'den yukler.
 *
 * plugins/vPickup/compress.yml dosyasini duzenleyin.
 * Dosya yoksa JAR icindeki varsayilan kopyalanir.
 */
public final class AutoBlockManager {

    private record Recipe(Material output, int inputCount) {}

    // EnumMap: O(1) arama, boxing yok
    private final Map<Material, Recipe> recipes = new EnumMap<>(Material.class);
    private final VPickup plugin;

    public AutoBlockManager(VPickup plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * compress.yml'i diskten yukler.
     * Format:
     *   GIRDI_MATERYALI:
     *     output: CIKTI_MATERYALI
     *     count: MIKTAR
     */
    public void load() {
        recipes.clear();
        FileConfiguration cfg = loadYaml("compress.yml");
        if (cfg == null) return;

        int loaded = 0, skipped = 0;
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section == null) {
                plugin.getLogger().warning("[AutoBlock] " + key + " icin gecersiz format (section bekleniyor).");
                skipped++;
                continue;
            }

            String outputName = section.getString("output");
            int count = section.getInt("count", 0);

            if (outputName == null || outputName.isBlank()) {
                plugin.getLogger().warning("[AutoBlock] " + key + " icin 'output' eksik.");
                skipped++;
                continue;
            }
            if (count <= 1) {
                plugin.getLogger().warning("[AutoBlock] " + key + " icin 'count' en az 2 olmali.");
                skipped++;
                continue;
            }

            Material input  = parseMaterial(key);
            Material output = parseMaterial(outputName);

            if (input == null) {
                plugin.getLogger().warning("[AutoBlock] Bilinmeyen girdi materyali: " + key);
                skipped++;
                continue;
            }
            if (output == null) {
                plugin.getLogger().warning("[AutoBlock] Bilinmeyen cikti materyali: " + outputName);
                skipped++;
                continue;
            }

            recipes.put(input, new Recipe(output, count));
            loaded++;
        }

        plugin.getLogger().info("[AutoBlock] " + loaded + " tarif yuklendi" +
                (skipped > 0 ? ", " + skipped + " atlandi." : "."));
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Oyuncunun envanterindeki uygun materyalleri sikistirir.
     * Kaskad: 9 nugget → ingot, 9 ingot → blok gibi zincirleme desteklenir.
     */
    public void compress(Player player) {
        Inventory inv = player.getInventory();
        boolean changed = true;

        // Kaskad: 9 nugget → ingot, 9 ingot → blok gibi zincirleme
        while (changed) {
            changed = false;
            for (Map.Entry<Material, Recipe> entry : recipes.entrySet()) {
                if (compressOnce(inv, entry.getKey(), entry.getValue())) {
                    changed = true;
                }
            }
        }
    }

    // ── Yardimci metodlar ──────────────────────────────────────────────────────

    private static boolean compressOnce(Inventory inv, Material ingredient, Recipe recipe) {
        int total = countMaterial(inv, ingredient);
        int sets  = total / recipe.inputCount();
        if (sets == 0) return false;

        removeMaterial(inv, ingredient, sets * recipe.inputCount());
        inv.addItem(new ItemStack(recipe.output(), sets));
        return true;
    }

    private static int countMaterial(Inventory inv, Material mat) {
        int count = 0;
        for (ItemStack item : inv.getStorageContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    private static void removeMaterial(Inventory inv, Material mat, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != mat) continue;
            if (item.getAmount() <= amount) {
                amount -= item.getAmount();
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - amount);
                amount = 0;
            }
        }
        inv.setStorageContents(contents);
    }

    private FileConfiguration loadYaml(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in  = plugin.getResource(fileName);
                 OutputStream out = new FileOutputStream(file)) {
                if (in == null) {
                    plugin.getLogger().severe("[AutoBlock] JAR icinde " + fileName + " bulunamadi!");
                    return null;
                }
                in.transferTo(out);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[AutoBlock] " + fileName + " kopyalanamadi!", e);
                return null;
            }
        }

        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[AutoBlock] " + fileName + " okunamadi!", e);
            return null;
        }
    }

    private static Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
