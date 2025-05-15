package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReloadCommand implements SubCommand {
    
    private final BlockMint plugin;
    
    public ReloadCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "reload";
    }
    
    @Override
    public String getDescription() {
        return "Reloads the plugin configuration";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint reload";
    }
    
    @Override
    public String getPermission() {
        return "blockmint.admin.reload";
    }
    
    @Override
    public boolean requiresPlayer() {
        return false;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.reload();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("version", plugin.getDescription().getVersion());
        
        plugin.getMessageManager().send(sender, "commands.reload-success", placeholders);
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return List.of();
    }
}