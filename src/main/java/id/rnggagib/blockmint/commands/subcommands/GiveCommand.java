package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.utils.GeneratorItemManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GiveCommand implements SubCommand {

    private final BlockMint plugin;
    
    public GiveCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "give";
    }
    
    @Override
    public String getDescription() {
        return "Gives a generator to a player";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint give <player> <type> [amount]";
    }
    
    @Override
    public String getPermission() {
        return "blockmint.admin.give";
    }
    
    @Override
    public boolean requiresPlayer() {
        return false;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "commands.give-usage");
            return;
        }
        
        String playerName = args[1];
        String type = args[2];
        int amount = 1;
        
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    plugin.getMessageManager().send(sender, "commands.invalid-amount");
                    return;
                }
            } catch (NumberFormatException e) {
                plugin.getMessageManager().send(sender, "commands.invalid-amount");
                return;
            }
        }
        
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            plugin.getMessageManager().send(sender, "commands.invalid-player");
            return;
        }
        
        GeneratorType generatorType = plugin.getGeneratorManager().getGeneratorTypes().get(type);
        if (generatorType == null) {
            plugin.getMessageManager().send(sender, "commands.invalid-type");
            return;
        }
        
        ItemStack item = GeneratorItemManager.createGeneratorItem(generatorType);
        item.setAmount(amount);
        
        HashMap<Integer, ItemStack> failedItems = target.getInventory().addItem(item);
        int given = amount - failedItems.values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
        
        if (!failedItems.isEmpty()) {
            for (ItemStack leftover : failedItems.values()) {
                target.getWorld().dropItem(target.getLocation(), leftover);
            }
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        placeholders.put("amount", String.valueOf(given));
        placeholders.put("type", generatorType.getName());
        
        plugin.getMessageManager().send(sender, "commands.give-success", placeholders);
        
        if (sender != target) {
            plugin.getMessageManager().send(target, "commands.give-received", placeholders);
        }
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 3) {
            return plugin.getGeneratorManager().getGeneratorTypes().keySet().stream()
                    .filter(type -> type.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 4) {
            return List.of("1", "5", "10", "32", "64");
        }
        return new ArrayList<>();
    }
}