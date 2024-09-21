package ink.magma.huskhomessign;

import ink.magma.huskhomessign.commands.ReloadCommand;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.teleport.TeleportationException;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class HuskHomesSign extends JavaPlugin implements Listener {
    public HuskHomesAPI huskHomesAPI;
    public static JavaPlugin instance;

    String createKey;
    int createKeyLine;
    String useKey;
    int useKeyLine;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        readConfig();

        if (Bukkit.getPluginManager().getPlugin("HuskHomes") == null) {
            getLogger().warning("Can't find HuskHomes install on server, this addon will not work...");
            return;
        }

        this.huskHomesAPI = HuskHomesAPI.getInstance();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(Bukkit.getPluginCommand("huskhomessign")).setExecutor(new ReloadCommand(this));

    }

    // Listen player create warp sign
    @EventHandler
    public void onSignPlace(SignChangeEvent event) {
        // If sign has key word
        if (String.valueOf(event.getLine(createKeyLine)).equals(createKey)) {
            if (!event.getPlayer().hasPermission("huskhomessign.create")) return;
            String warpName = event.getLine(createKeyLine + 1);
            if (warpName == null || warpName.trim().isEmpty()) return;

            event.setLine(0, "");
            event.setLine(1, "");
            event.setLine(2, "");
            event.setLine(3, "");
            event.setLine(useKeyLine, useKey);
            event.setLine(useKeyLine + 1, warpName.trim());

            // Get the warp, if not exist, send a message to creator
            huskHomesAPI.getWarp(warpName.trim())
                    .thenAccept((result) -> {
                        if (result.isEmpty()) {
                            event.getPlayer().sendMessage(getConfigMessage("invalid-warp"));
                        }
                    });

        }

        // avoid player has no permission to create "use sign",
        // because some server player can use color codes on sign
        if (String.valueOf(event.getLine(useKeyLine)).equals(useKey)) {
            if (event.getPlayer().hasPermission("huskhomessign.create")) return;
            event.setLine(useKeyLine, "");
        }
    }

    // Protect warp sign from break by player without permission
    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        // Ignore events not Signs or WallSigns
        if (!(event.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Sign) && !(event.getBlock().getBlockData() instanceof WallSign)) {
            return;
        }
        // If player has permission, ignore.
        if (event.getPlayer().hasPermission("huskhomessign.break")) return;
        // Player don't have break permission, check if the block is warp sign.
        Sign sign = (Sign) event.getBlock().getState();

        if (sign.getLine(useKeyLine).equals(useKey)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(getConfigMessage("no-break-permission"));
        }
    }

    // Listen player click warp sign
    @EventHandler(ignoreCancelled = true)
    public void onPlayerClickSign(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getBlockData() instanceof org.bukkit.block.data.type.Sign) && !(event.getClickedBlock().getBlockData() instanceof WallSign)) {
            return;
        }
        Sign sign = (Sign) event.getClickedBlock().getState();
        if (!event.getPlayer().hasPermission("huskhomessign.use")) {
            event.getPlayer().sendMessage(getConfigMessage("no-use-permission"));
            return;
        }
        // Is the sign a warp sign
        if (!sign.getLine(useKeyLine).equals(useKey)) return;
        // Is the sign contains warp name
        if (sign.getLine(useKeyLine + 1).isEmpty()) return;

        // avoid some plugin (or 1.20+) can let player edit sign by right click
        event.setCancelled(true);

        // Do teleport
        huskHomesAPI.getWarp(sign.getLine(useKeyLine + 1)).thenAccept((result) -> {
            // If target warp point not found
            if (result.isEmpty()) {
                event.getPlayer().sendMessage(getConfigMessage("invalid-warp"));
                return;
            }

            OnlineUser user = huskHomesAPI.adaptUser(event.getPlayer());
            try {
                huskHomesAPI.teleportBuilder(user)
                        .target(result.get())
                        .toTimedTeleport()
                        .execute();
            } catch (TeleportationException e) {
                // Warming up
                if (e.getType().equals(TeleportationException.Type.ALREADY_WARMING_UP)) return;
                // Other errors
                getLogger().warning(e.getMessage());
                getLogger().warning("Error happened while executing warp teleportation.");
                event.getPlayer().sendMessage(getConfigMessage("teleport-error"));
            }
        });


    }

    public void readConfig() {
        createKey = getConfig().getString("sign.create.key-word", "[warp]");
        createKeyLine = getConfig().getInt("sign.create.key-word-line", 1) - 1;
        useKey = ChatColor.translateAlternateColorCodes('&', getConfig().getString("sign.use.key-word", "ConfigError"));
        useKeyLine = getConfig().getInt("sign.use.key-word-line", 2) - 1;
    }

    private String getConfigMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("message." + key, "")
        );
    }
}
