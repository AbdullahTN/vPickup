package com.example.vpickup.data;

import java.util.UUID;

/**
 * Immutable snapshot of a player's vPickup preferences.
 * All mutation goes through DatabaseManager to keep the DB authoritative.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile boolean autoPickup;
    private volatile boolean autoBlock;
    private volatile boolean autoSmelt;

    public PlayerData(UUID uuid, boolean autoPickup, boolean autoBlock, boolean autoSmelt) {
        this.uuid       = uuid;
        this.autoPickup = autoPickup;
        this.autoBlock  = autoBlock;
        this.autoSmelt  = autoSmelt;
    }

    /** Create a default record for a brand-new player. */
    public static PlayerData defaultFor(UUID uuid) {
        return new PlayerData(uuid, true, false, false);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getUuid()       { return uuid; }
    public boolean isAutoPickup() { return autoPickup; }
    public boolean isAutoBlock()  { return autoBlock;  }
    public boolean isAutoSmelt()  { return autoSmelt;  }

    // ── Toggles (called from the main thread only) ────────────────────────────

    public boolean toggleAutoPickup() { autoPickup = !autoPickup; return autoPickup; }
    public boolean toggleAutoBlock()  { autoBlock  = !autoBlock;  return autoBlock;  }
    public boolean toggleAutoSmelt()  { autoSmelt  = !autoSmelt;  return autoSmelt;  }
}
