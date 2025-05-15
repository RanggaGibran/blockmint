package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import id.rnggagib.blockmint.generators.GeneratorType;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

public class ListCommand implements SubCommand {

    private final BlockMint plugin;
    
    public ListCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "list";
    }
    
    @Override
    public String getDescription() {
        return "Lists all available generator types";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint list";
    }
    
    @Override
    public String getPermission() {
        return "blockmint.use";
    }
    
    @Override
    public boolean requiresPlayer() {
        return false;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Map<String, GeneratorType> generatorTypes = plugin.getGeneratorManager().getGeneratorTypes();
        
        if (generatorTypes.isEmpty()) {
            plugin.getMessageManager().send(sender, "commands.no-generators");
            return;
        }
        
        plugin.getMessageManager().send(sender, "commands.list-header");
        
        for (GeneratorType type : generatorTypes.values()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", type.getName());
            placeholders.put("value", String.format("%.2f", type.getBaseValue()));
            placeholders.put("material", type.getMaterial());
            placeholders.put("max-level", String.valueOf(type.getMaxLevel()));
            placeholders.put("time", formatTime(type.getGenerationTime()));
            
            plugin.getMessageManager().send(sender, "commands.list-item", placeholders);
        }
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        
        return hours + "h " + minutes + "m " + seconds + "s";
    }
}