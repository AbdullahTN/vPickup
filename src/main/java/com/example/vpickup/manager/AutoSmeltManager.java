package com.example.vpickup.manager;

import com.example.vpickup.VPickup;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Eritme tariflerini smelt.yml'den yükler.
 *
 * plugins/vPickup/smelt.yml dosyasını düzenleyin.
 * Dosya yoksa JAR içindeki varsayılan kopyalanır.
 * reload() çağrısıyla veya sunucu yeniden başlatmayla değişiklikler uygulanır.
 */
public final class AutoSmeltManager {

    // EnumMap: O(1) arama, boxing yok — her blok kırma olayında okunur
    private final Map<Material, Material> smeltMap = new EnumMap<>(Material.class);
    private final VPickup plugin;

    public AutoSmeltManager(VPickup plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * smelt.yml'i diskten yükler ve haritayı doldurur.
     * Dosya yoksa JAR'dan kopyalanır.
     */
    public void load() {
        smeltMap.clear();
        FileConfiguration cfg = loadYaml("smelt.yml");
        if (cfg == null) return;

        int loaded = 0, skipped = 0;
        for (String key : cfg.getKeys(false)) {
            String outputName = cfg.getString(key);
            if (outputName == null) continue;

            Material input  = parseMaterial(key);
            Material output = parseMaterial(outputName);

            if (input == null) {
                plugin.getLogger().warning("[AutoSmelt] Bilinmeyen girdi materyali: " + key);
                skipped++;
                continue;
            }
            if (output == null) {
                plugin.getLogger().warning("[AutoSmelt] Bilinmeyen cikti materyali: " + outputName);
                skipped++;
                continue;
            }

            smeltMap.put(input, output);
            loaded++;
        }

        plugin.getLogger().info("[AutoSmelt] " + loaded + " tarif yuklendi" +
                (skipped > 0 ? ", " + skipped + " atlandi." : "."));
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Verilen materyal icin eritilmis ItemStack dondurur. Tarif yoksa null. */
    public ItemStack smelt(Material input, int amount) {
        Material result = smeltMap.get(input);
        if (result == null) return null;
        return new ItemStack(result, amount);
    }

    /** Bu materyal eritelenebilir mi? */
    public boolean isSmeltable(Material mat) {
        return smeltMap.containsKey(mat);
    }

    // ── Yardimci metodlar ──────────────────────────────────────────────────────

    private FileConfiguration loadYaml(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in  = plugin.getResource(fileName);
                 OutputStream out = new FileOutputStream(file)) {
                if (in == null) {
                    plugin.getLogger().severe("[AutoSmelt] JAR icinde " + fileName + " bulunamadi!");
                    return null;
                }
                in.transferTo(out);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "[AutoSmelt] " + fileName + " kopyalanamadi!", e);
                return null;
            }
        }

        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[AutoSmelt] " + fileName + " okunamadi!", e);
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
