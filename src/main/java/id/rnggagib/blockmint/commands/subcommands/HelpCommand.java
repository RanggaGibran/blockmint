package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.CommandManager;
import id.rnggagib.blockmint.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelpCommand implements SubCommand {

    private final BlockMint plugin;
    
    public HelpCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "help";
    }
    
    @Override
    public String getDescription() {
        return "Shows all available commands";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint help [page]";
    }
    
    @Override
    public String getPermission() {
        return null;
    }
    
    @Override
    public boolean requiresPlayer() {
        return false;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        CommandManager commandManager = plugin.getCommandManager();
        List<SubCommand> availableCommands = new ArrayList<>();
        
        for (SubCommand cmd : commandManager.getSubCommands().values()) {
            if (cmd.getPermission() == null || sender.hasPermission(cmd.getPermission())) {
                availableCommands.add(cmd);
            }
        }
        
        int pageSize = 5;
        int maxPage = (availableCommands.size() + pageSize - 1) / pageSize;
        int page = 1;
        
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) page = 1;
                if (page > maxPage) page = maxPage;
            } catch (NumberFormatException e) {
                plugin.getMessageManager().send(sender, "commands.invalid-number");
                return;
            }
        }
        
        plugin.getMessageManager().send(sender, "commands.help-header");
        
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, availableCommands.size());
        
        for (int i = start; i < end; i++) {
            SubCommand cmd = availableCommands.get(i);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("command", cmd.getSyntax());
            placeholders.put("description", cmd.getDescription());
            plugin.getMessageManager().send(sender, "commands.help-command", placeholders);
        }
        
        if (maxPage > 1) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("page", String.valueOf(page));
            placeholders.put("max_page", String.valueOf(maxPage));
            plugin.getMessageManager().send(sender, "commands.help-footer", placeholders);
        } else {
            plugin.getMessageManager().send(sender, "commands.help-footer");
        }
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            CommandManager commandManager = plugin.getCommandManager();
            List<SubCommand> availableCommands = new ArrayList<>();
            
            for (SubCommand cmd : commandManager.getSubCommands().values()) {
                if (cmd.getPermission() == null || sender.hasPermission(cmd.getPermission())) {
                    availableCommands.add(cmd);
                }
            }
            
            int maxPage = (availableCommands.size() + 4) / 5;
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= maxPage; i++) {
                pages.add(String.valueOf(i));
            }
            return pages;
        }
        return List.of();
    }
}