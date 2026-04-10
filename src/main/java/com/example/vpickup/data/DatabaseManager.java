package com.example.vpickup.data;

import com.example.vpickup.VPickup;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Manages all persistence via an SQLite database.
 *
 * ── Thread Safety ──
 *   • All JDBC calls happen on a dedicated async BukkitScheduler thread to
 *     keep the main thread free (zero blocking I/O = stable TPS).
 *   • The in-memory cache (ConcurrentHashMap) is always authoritative for
 *     reads during a session; the DB is the source of truth across restarts.
 */
public final class DatabaseManager {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS players (" +
            "  uuid        TEXT    PRIMARY KEY," +
            "  auto_pickup INTEGER NOT NULL DEFAULT 1," +
            "  auto_block  INTEGER NOT NULL DEFAULT 0," +
            "  auto_smelt  INTEGER NOT NULL DEFAULT 0" +
            ");";

    private static final String UPSERT =
            "INSERT INTO players (uuid, auto_pickup, auto_block, auto_smelt) VALUES (?,?,?,?)" +
            " ON CONFLICT(uuid) DO UPDATE SET" +
            "   auto_pickup = excluded.auto_pickup," +
            "   auto_block  = excluded.auto_block," +
            "   auto_smelt  = excluded.auto_smelt;";

    private static final String SELECT =
            "SELECT auto_pickup, auto_block, auto_smelt FROM players WHERE uuid = ?;";

    // ── State ──────────────────────────────────────────────────────────────────

    private final VPickup plugin;
    private Connection connection;

    /** Live in-memory cache — populated on player join, evicted on quit. */
    private final ConcurrentMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public DatabaseManager(VPickup plugin) {
        this.plugin = plugin;
    }

    /** Opens the SQLite connection and ensures the schema exists. Blocking — call once on enable. */
    public void init() {
        File dbFile = new File(plugin.getDataFolder(), "players.db");
        plugin.getDataFolder().mkdirs();
        try {
            // Force the shaded driver to register itself
            Class.forName("com.example.vpickup.libs.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");   // WAL mode → concurrent reads + better perf
                st.execute("PRAGMA synchronous=NORMAL;"); // balanced durability/speed
                st.execute(CREATE_TABLE);
            }
            plugin.getLogger().info("SQLite database initialised at " + dbFile.getPath());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise SQLite database!", e);
        }
    }

    /** Closes the connection. Call once on disable (after flushing all pending writes). */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection.", e);
        }
    }

    // ── Cache accessors (main-thread safe) ──────────────────────────────────────

    /** Returns the cached PlayerData; never null after loadAsync has fired for this player. */
    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::defaultFor);
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    // ── Async load / save ──────────────────────────────────────────────────────

    /**
     * Loads data from the DB asynchronously and populates the cache.
     * Safe to call from the main thread (player join).
     */
    public void loadAsync(UUID uuid) {
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskAsynchronously(plugin, () -> {
            PlayerData data = loadFromDb(uuid);
            // Write to cache from async thread — ConcurrentHashMap is thread-safe
            cache.put(uuid, data);
        });
    }

    /**
     * Persists a PlayerData object to the DB asynchronously.
     * Safe to call from the main thread (command execution, block break).
     */
    public void saveAsync(PlayerData data) {
        // Snapshot primitives before leaving the main thread
        final UUID    uuid        = data.getUuid();
        final boolean autoPickup  = data.isAutoPickup();
        final boolean autoBlock   = data.isAutoBlock();
        final boolean autoSmelt   = data.isAutoSmelt();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
                ps.setString (1, uuid.toString());
                ps.setInt    (2, autoPickup ? 1 : 0);
                ps.setInt    (3, autoBlock  ? 1 : 0);
                ps.setInt    (4, autoSmelt  ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to save data for " + uuid, e);
            }
        });
    }

    /** Saves ALL cached entries synchronously — used during onDisable to avoid data loss. */
    public void saveAllSync() {
        for (PlayerData data : cache.values()) {
            try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
                ps.setString(1, data.getUuid().toString());
                ps.setInt   (2, data.isAutoPickup() ? 1 : 0);
                ps.setInt   (3, data.isAutoBlock()  ? 1 : 0);
                ps.setInt   (4, data.isAutoSmelt()  ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Sync save failed for " + data.getUuid(), e);
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private PlayerData loadFromDb(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(
                            uuid,
                            rs.getInt("auto_pickup") == 1,
                            rs.getInt("auto_block")  == 1,
                            rs.getInt("auto_smelt")  == 1
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load data for " + uuid + ", using defaults.", e);
        }
        return PlayerData.defaultFor(uuid);
    }
}
