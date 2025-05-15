package id.rnggagib.blockmint.commands;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public interface SubCommand {
    
    String getName();
    
    String getDescription();
    
    String getSyntax();
    
    String getPermission();
    
    boolean requiresPlayer();
    
    void execute(CommandSender sender, String[] args);
    
    default List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}