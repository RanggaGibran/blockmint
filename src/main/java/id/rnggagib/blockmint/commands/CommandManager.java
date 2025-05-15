package id.rnggagib.blockmint.commands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.subcommands.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {
    
    private final BlockMint plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    
    public CommandManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void registerCommands() {
        registerSubCommands();
        
        plugin.getCommand("blockmint").setExecutor(this);
        plugin.getCommand("blockmint").setTabCompleter(this);
    }
    
    private void registerSubCommands() {
        registerSubCommand(new HelpCommand(plugin));
        registerSubCommand(new ReloadCommand(plugin));
        registerSubCommand(new GiveCommand(plugin));
        registerSubCommand(new ListCommand(plugin));
        registerSubCommand(new StatsCommand(plugin));
    }
    
    private void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            SubCommand helpCommand = subCommands.get("help");
            if (helpCommand != null) {
                helpCommand.execute(sender, args);
            }
            return true;
        }
        
        SubCommand subCommand = subCommands.get(args[0].toLowerCase());
        
        if (subCommand == null) {
            plugin.getMessageManager().send(sender, "commands.unknown");
            return true;
        }
        
        if (subCommand.requiresPlayer() && !(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "commands.player-only");
            return true;
        }
        
        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            plugin.getMessageManager().send(sender, "commands.no-permission");
            return true;
        }
        
        subCommand.execute(sender, args);
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.values().stream()
                    .filter(subCommand -> subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission()))
                    .map(SubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length > 1) {
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                return subCommand.getTabCompletions(sender, args);
            }
        }
        
        return new ArrayList<>();
    }
    
    public Map<String, SubCommand> getSubCommands() {
        return subCommands;
    }
}