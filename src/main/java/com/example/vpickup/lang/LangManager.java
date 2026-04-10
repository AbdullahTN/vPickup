package com.example.vpickup.lang;

import com.example.vpickup.VPickup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Manages localisation for vPickup.
 *
 * ── Supported languages ──
 *   • en  (English  — default fallback)
 *   • tr  (Türkçe)
 *
 * ── How it works ──
 *   1. On startup the selected lang file is copied from the JAR to
 *      plugins/vPickup/lang/<code>.yml if it does not already exist,
 *      so server admins can customise messages without editing the JAR.
 *   2. The file is loaded into a {@link FileConfiguration} in memory.
 *   3. Every missing key falls back to the bundled en.yml so the plugin
 *      never throws a NullPointerException on an incomplete translation.
 *
 * ── Thread safety ──
 *   LangManager is initialised once on the main thread during onEnable
 *   and only read (never written) afterwards — no synchronisation needed.
 */
public final class LangManager {

    private static final MiniMessage MM       = MiniMessage.miniMessage();
    private static final String      FALLBACK = "en";

    private final VPickup            plugin;
    private       FileConfiguration  lang;      // active language config
    private       FileConfiguration  fallback;  // always en.yml, used as safety net

    public LangManager(VPickup plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Loads the language file specified in config.yml (key: {@code language}).
     * Falls back to English if the requested locale is unknown or malformed.
     */
    public void load() {
        String code = plugin.getConfig()
                            .getString("language", FALLBACK)
                            .toLowerCase(Locale.ROOT)
                            .trim();

        // Always ensure the fallback (en) is available in memory
        fallback = loadLangFile(FALLBACK);

        if (code.equals(FALLBACK)) {
            lang = fallback;
        } else {
            FileConfiguration requested = loadLangFile(code);
            lang = (requested != null) ? requested : fallback;
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns the raw MiniMessage string for a given key.
     * Falls back to the English string if the key is absent in the active locale.
     *
     * @param key config key, e.g. {@code "pickup-enabled"}
     * @return MiniMessage-formatted string, never null
     */
    public String getRaw(String key) {
        String value = lang != null ? lang.getString(key) : null;
        if (value == null && fallback != null) {
            value = fallback.getString(key);
        }
        return value != null ? value : "<gray>" + key + "</gray>";
    }

    /**
     * Returns a parsed {@link Component} for the given key.
     * Convenience wrapper for {@link #getRaw(String)}.
     */
    public Component get(String key) {
        return MM.deserialize(getRaw(key));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Copies the bundled {@code lang/<code>.yml} to the plugin data folder
     * (if absent) and loads it into a {@link FileConfiguration}.
     *
     * @param code ISO language code, e.g. "en" or "tr"
     * @return loaded config, or {@code null} if the resource does not exist
     */
    private FileConfiguration loadLangFile(String code) {
        String resourcePath = "lang/" + code + ".yml";
        File   dataFile     = new File(plugin.getDataFolder(), resourcePath);

        // Copy bundled resource to disk on first run so admins can edit it
        if (!dataFile.exists()) {
            InputStream resource = plugin.getResource(resourcePath);
            if (resource == null) {
                plugin.getLogger().warning(
                        "Language file not found in JAR: " + resourcePath +
                        ". Falling back to English.");
                return null;
            }
            dataFile.getParentFile().mkdirs();
            try (InputStream in = resource;
                 OutputStream out = new FileOutputStream(dataFile)) {
                in.transferTo(out);
                plugin.getLogger().info("Extracted language file: " + resourcePath);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not write language file: " + resourcePath, e);
                // Fall through — we can still load from the JAR stream directly
            }
        }

        // Prefer reading from disk so admin edits take effect after /reload
        if (dataFile.exists()) {
            try (InputStreamReader reader =
                         new InputStreamReader(new FileInputStream(dataFile),
                                               StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to read language file from disk: " + dataFile, e);
            }
        }

        // Last resort: load directly from JAR (read-only, no disk write)
        InputStream fallbackStream = plugin.getResource(resourcePath);
        if (fallbackStream != null) {
            try (InputStreamReader reader =
                         new InputStreamReader(fallbackStream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not load language file from JAR: " + resourcePath, e);
            }
        }

        return null;
    }
}
