package ink.magma.huskhomessign.commands;

import ink.magma.huskhomessign.HuskHomesSign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;

public class ReloadCommand implements TabExecutor {
    private final HuskHomesSign instance;

    public ReloadCommand(HuskHomesSign instance) {
        this.instance = instance;
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (Objects.requireNonNullElse(args[0], "").equals("reload")) {
            instance.saveDefaultConfig();
            instance.reloadConfig();
            instance.readConfig();
            sender.sendMessage("[HuskHomesSign] Reloaded.");
            return true;
        }
        return false;
    }

    @Override
    @ParametersAreNonnullByDefault
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length <= 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
