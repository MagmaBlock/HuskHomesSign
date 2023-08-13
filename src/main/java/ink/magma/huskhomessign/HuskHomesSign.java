package ink.magma.huskhomessign;

import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.teleport.TeleportationException;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.ParametersAreNonnullByDefault;

public final class HuskHomesSign extends JavaPlugin implements Listener, CommandExecutor {
    public HuskHomesAPI huskHomesAPI;
    public static JavaPlugin instance;

    String createKey;
    int createKeyLine;
    String useKey;
    int useKeyLine;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        readConfig();

        if (Bukkit.getPluginManager().getPlugin("HuskHomes") != null) {
            this.huskHomesAPI = HuskHomesAPI.getInstance();
            Bukkit.getPluginManager().registerEvents(this, this);
        } else {
            getLogger().warning("Can't find HuskHomes install on server, this addon will not work...");
        }
    }

    private void readConfig() {
        createKey = getConfig().getString("sign.create.key-word", "[warp]");
        createKeyLine = getConfig().getInt("sign.create.key-word-line", 1) - 1;
        useKey = ChatColor.translateAlternateColorCodes('&', getConfig().getString("sign.use.key-word", "ConfigError"));
        useKeyLine = getConfig().getInt("sign.use.key-word-line", 2) - 1;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equals("huskhomessign") && args[0] != null && args[0].equals("reload")) {
            saveDefaultConfig();
            reloadConfig();
            readConfig();
            sender.sendMessage("[HuskHomesSign] Reloaded.");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onSignPlace(SignChangeEvent event) {
        // if sign has a key word
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

            // test this warp is valid, if not will send a message to creator
            huskHomesAPI.getWarp(warpName.trim())
                    .thenAccept((result) -> {
                        if (result.isEmpty()) {
                            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    getConfig().getString("message.invalid-warp", "")
                            ));
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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerClickSign(PlayerInteractEvent event) {
        // if player right-click on a sign, and check permission
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getBlockData() instanceof org.bukkit.block.data.type.Sign) && !(event.getClickedBlock().getBlockData() instanceof WallSign)) {
            return;
        }

        Sign sign = (Sign) event.getClickedBlock().getState();
        if (sign.getLine(useKeyLine).equals(useKey)) {

            if (!event.getPlayer().hasPermission("huskhomessign.use")) {
                event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("message.no-use-permission", "")));
                return;
            }

            if (sign.getLine(useKeyLine + 1).isEmpty()) return;

            // avoid some plugin (or 1.20+) can let player edit sign by right click
            event.setCancelled(true);

            huskHomesAPI.getWarp(sign.getLine(useKeyLine + 1))
                    .thenAccept((result) -> {
                        if (result.isPresent()) {
                            OnlineUser user = huskHomesAPI.adaptUser(event.getPlayer());
                            try {
                                huskHomesAPI.teleportBuilder(user)
                                        .target(result.get())
                                        .toTimedTeleport()
                                        .execute();
                            } catch (TeleportationException e) {
                                getLogger().warning(e.getMessage());
                                getLogger().warning("Error happened while executing warp teleportation.");
                                event.getPlayer().sendMessage(getConfig().getString("message.teleport-error", ""));
                            }
                        } else {
                            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    getConfig().getString("message.invalid-warp", "")
                            ));
                        }
                    });

        }


    }


}
