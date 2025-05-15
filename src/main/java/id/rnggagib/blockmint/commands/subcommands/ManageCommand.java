package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ManageCommand implements SubCommand {

    private final BlockMint plugin;
    
    public ManageCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "manage";
    }
    
    @Override
    public String getDescription() {
        return "Open the generator management interface";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint manage";
    }
    
    @Override
    public String getPermission() {
        return "blockmint.manage";
    }
    
    @Override
    public boolean requiresPlayer() {
        return true;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "commands.player-only");
            return;
        }
        
        Player player = (Player) sender;
        plugin.getGUIManager().openGeneratorManagement(player);
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}