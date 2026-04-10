package com.example.vpickup.command;

import com.example.vpickup.VPickup;
import com.example.vpickup.data.PlayerData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.function.*;

/**
 * Reusable command that toggles one boolean flag on the player's PlayerData.
 *
 * All three commands (/ap, /ab, /as) share identical logic; only the flag
 * getter/setter and the message keys differ.
 */
public final class ToggleCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final VPickup                       plugin;
    private final Function<PlayerData, Boolean> toggle;    // mutates the flag, returns new state
    private final String                        enabledKey;
    private final String                        disabledKey;

    /**
     * @param plugin       main plugin instance
     * @param toggle       lambda that toggles the relevant flag and returns the new value
     * @param enabledKey   config key for the "enabled" message
     * @param disabledKey  config key for the "disabled" message
     */
    public ToggleCommand(VPickup plugin,
                         Function<PlayerData, Boolean> toggle,
                         String enabledKey,
                         String disabledKey) {
        this.plugin       = plugin;
        this.toggle       = toggle;
        this.enabledKey   = enabledKey;
        this.disabledKey  = disabledKey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("vpickup.use")) {
            player.sendMessage(plugin.getLangManager().get("no-permission"));
            return true;
        }

        PlayerData data    = plugin.getDatabaseManager().get(player.getUniqueId());
        boolean    enabled = toggle.apply(data);   // apply toggle, get new state

        // Persist asynchronously — no main-thread blocking
        plugin.getDatabaseManager().saveAsync(data);

        String msgKey = enabled ? enabledKey : disabledKey;
        player.sendMessage(plugin.getLangManager().get(msgKey));
        return true;
    }
}
