package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.network.NetworkBlock;
import id.rnggagib.blockmint.network.NetworkTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class NetworkCommand implements SubCommand {

    private final BlockMint plugin;
    
    public NetworkCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    private Map<String, SubCommand> subcommands = new HashMap<>();

    public void registerSubCommand(String name, SubCommand subCommand) {
        subcommands.put(name.toLowerCase(), subCommand);
    }
    
    @Override
    public String getName() {
        return "network";
    }
    
    @Override
    public String getDescription() {
        return "Manage generator networks";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint network <create|list|info|add|remove|upgrade|give|visualize>";
    }
    
    @Override
    public String getPermission() {
        return "blockmint.network";
    }
    
    @Override
    public boolean requiresPlayer() {
        return true;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            showHelp(player);
            return;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "create":
                handleCreateNetwork(player, args);
                break;
            case "list":
                handleListNetworks(player);
                break;
            case "info":
                handleNetworkInfo(player, args);
                break;
            case "add":
                handleAddGenerator(player, args);
                break;
            case "remove":
                handleRemoveGenerator(player, args);
                break;
            case "upgrade":
                handleUpgradeNetwork(player, args);
                break;
            case "give":
                handleGiveNetworkBlock(player, args);
                break;
            case "visualize":
                handleVisualizeNetwork(player);
                break;
            default:
                showHelp(player);
                break;
        }
    }
    
    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Generator Network Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network create <name> <tier> " + ChatColor.GRAY + "- Create a new network");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network list " + ChatColor.GRAY + "- List your networks");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network info <id> " + ChatColor.GRAY + "- Show network information");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network add <network-id> <generator-id> " + ChatColor.GRAY + "- Add generator to network");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network remove <generator-id> " + ChatColor.GRAY + "- Remove generator from its network");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network upgrade <id> <tier> " + ChatColor.GRAY + "- Upgrade a network");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network give <player> <tier> " + ChatColor.GRAY + "- Give a network block");
        player.sendMessage(ChatColor.YELLOW + "/blockmint network visualize " + ChatColor.GRAY + "- Toggle network visualization");
    }
    
    private void handleCreateNetwork(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /blockmint network create <name> <tier>");
            player.sendMessage(ChatColor.RED + "Available tiers: BASIC, ADVANCED, ELITE, ULTIMATE, CELESTIAL");
            return;
        }
        
        String name = args[2];
        String tierName = args[3].toUpperCase();
        
        NetworkTier tier;
        try {
            tier = NetworkTier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid tier: " + tierName);
            player.sendMessage(ChatColor.RED + "Available tiers: BASIC, ADVANCED, ELITE, ULTIMATE, CELESTIAL");
            return;
        }
        
        ItemStack networkBlock = NetworkBlock.createNetworkBlockItem(tier, name);
        player.getInventory().addItem(networkBlock);
        
        player.sendMessage(ChatColor.GREEN + "You've received a " + tier.getDisplayName() + " Network Block named '" + name + "'");
        player.sendMessage(ChatColor.YELLOW + "Place it to create a network controller!");
    }
    
    private void handleListNetworks(Player player) {
        List<NetworkBlock> networks = plugin.getNetworkManager().getPlayerNetworks(player.getUniqueId());
        
        if (networks.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You don't have any generator networks.");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Your Generator Networks ===");
        
        for (NetworkBlock network : networks) {
            String networkInfo = ChatColor.YELLOW + "ID: " + network.getNetworkId() + 
                                ChatColor.GRAY + " | " + 
                                ChatColor.YELLOW + "Name: " + network.getName() + 
                                ChatColor.GRAY + " | " + 
                                ChatColor.YELLOW + "Tier: " + network.getTier().getDisplayName() + 
                                ChatColor.GRAY + " | " + 
                                ChatColor.YELLOW + "Generators: " + network.getConnectedGeneratorCount() + 
                                ChatColor.GRAY + " | " + 
                                ChatColor.YELLOW + "Bonus: " + String.format("%.1f", network.getEfficiencyBonus() * 100) + "%";
            
            player.sendMessage(networkInfo);
        }
    }
    
    private void handleNetworkInfo(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /blockmint network info <id>");
            return;
        }
        
        int networkId;
        try {
            networkId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid network ID: " + args[2]);
            return;
        }
        
        NetworkBlock network = plugin.getNetworkManager().getNetworks().get(networkId);
        
        if (network == null) {
            player.sendMessage(ChatColor.RED + "Network not found with ID: " + networkId);
            return;
        }
        
        if (!network.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to view this network.");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "=== Network Information ===");
        player.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + network.getNetworkId());
        player.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + network.getName());
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(network.getOwner()).getName());
        player.sendMessage(ChatColor.YELLOW + "Tier: " + ChatColor.WHITE + network.getTier().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Generators: " + ChatColor.WHITE + network.getConnectedGeneratorCount());
        player.sendMessage(ChatColor.YELLOW + "Efficiency Bonus: " + ChatColor.WHITE + String.format("%.1f", network.getEfficiencyBonus() * 100) + "%");
        
        player.sendMessage(ChatColor.GOLD + "Connected Generators:");
        
        for (int generatorId : network.getConnectedGenerators()) {
            Generator generator = null;
            for (Generator g : plugin.getGeneratorManager().getActiveGenerators().values()) {
                if (g.getId() == generatorId) {
                    generator = g;
                    break;
                }
            }
            
            if (generator != null) {
                Location loc = generator.getLocation();
                player.sendMessage(ChatColor.YELLOW + "ID: " + generatorId + 
                                  ChatColor.GRAY + " | " + 
                                  ChatColor.YELLOW + "Type: " + generator.getType().getName() + 
                                  ChatColor.GRAY + " | " + 
                                  ChatColor.YELLOW + "Level: " + generator.getLevel() + 
                                  ChatColor.GRAY + " | " + 
                                  ChatColor.YELLOW + "Location: " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            } else {
                player.sendMessage(ChatColor.YELLOW + "ID: " + generatorId + ChatColor.RED + " (not loaded)");
            }
        }
    }
    
    private void handleAddGenerator(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /blockmint network add <network-id> <generator-id>");
            return;
        }
        
        int networkId;
        int generatorId;
        
        try {
            networkId = Integer.parseInt(args[2]);
            generatorId = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid network or generator ID.");
            return;
        }
        
        NetworkBlock network = plugin.getNetworkManager().getNetworks().get(networkId);
        
        if (network == null) {
            player.sendMessage(ChatColor.RED + "Network not found with ID: " + networkId);
            return;
        }
        
        if (!network.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to modify this network.");
            return;
        }
        
        Generator generator = null;
        for (Generator g : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (g.getId() == generatorId) {
                generator = g;
                break;
            }
        }
        
        if (generator == null) {
            player.sendMessage(ChatColor.RED + "Generator not found with ID: " + generatorId);
            return;
        }
        
        if (!generator.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin")) {
            player.sendMessage(ChatColor.RED + "You don't own this generator.");
            return;
        }
        
        boolean success = plugin.getNetworkManager().addGeneratorToNetwork(networkId, generatorId);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Added generator " + generatorId + " to network " + network.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to add generator to network.");
        }
    }
    
    private void handleRemoveGenerator(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /blockmint network remove <generator-id>");
            return;
        }
        
        int generatorId;
        
        try {
            generatorId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid generator ID.");
            return;
        }
        
        Generator generator = null;
        for (Generator g : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (g.getId() == generatorId) {
                generator = g;
                break;
            }
        }
        
        if (generator == null) {
            player.sendMessage(ChatColor.RED + "Generator not found with ID: " + generatorId);
            return;
        }
        
        if (!generator.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin")) {
            player.sendMessage(ChatColor.RED + "You don't own this generator.");
            return;
        }
        
        NetworkBlock network = plugin.getNetworkManager().getGeneratorNetwork(generatorId);
        
        if (network == null) {
            player.sendMessage(ChatColor.RED + "This generator is not part of any network.");
            return;
        }
        
        boolean success = plugin.getNetworkManager().removeGeneratorFromNetwork(generatorId);
        
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Removed generator " + generatorId + " from network " + network.getName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to remove generator from network.");
        }
    }
    
    private void handleUpgradeNetwork(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /blockmint network upgrade <id> <tier>");
            player.sendMessage(ChatColor.RED + "Available tiers: BASIC, ADVANCED, ELITE, ULTIMATE, CELESTIAL");
            return;
        }
        
        int networkId;
        
        try {
            networkId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid network ID: " + args[2]);
            return;
        }
        
        String tierName = args[3].toUpperCase();
        NetworkTier newTier;
        
        try {
            newTier = NetworkTier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid tier: " + tierName);
            player.sendMessage(ChatColor.RED + "Available tiers: BASIC, ADVANCED, ELITE, ULTIMATE, CELESTIAL");
            return;
        }
        
        NetworkBlock network = plugin.getNetworkManager().getNetworks().get(networkId);
        
        if (network == null) {
            player.sendMessage(ChatColor.RED + "Network not found with ID: " + networkId);
            return;
        }
        
        if (!network.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to upgrade this network.");
            return;
        }
        
        if (network.getTier().ordinal() >= newTier.ordinal()) {
            player.sendMessage(ChatColor.RED + "The network is already at or above this tier level.");
            return;
        }
        
        double upgradeCost = calculateUpgradeCost(network.getTier(), newTier);
        
        if (!plugin.getEconomy().has(player, upgradeCost)) {
            player.sendMessage(ChatColor.RED + "You need $" + String.format("%.2f", upgradeCost) + " to upgrade this network.");
            return;
        }
        
        boolean success = plugin.getNetworkManager().upgradeNetwork(networkId, newTier);
        
        if (success) {
            plugin.getEconomy().withdrawPlayer(player, upgradeCost);
            player.sendMessage(ChatColor.GREEN + "Upgraded network " + network.getName() + " to " + newTier.getDisplayName() + " tier.");
            player.sendMessage(ChatColor.GREEN + "New Efficiency Bonus: " + String.format("%.1f", network.getEfficiencyBonus() * 100) + "%");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to upgrade network.");
        }
    }
    
    private void handleGiveNetworkBlock(Player player, String[] args) {
        if (!player.hasPermission("blockmint.admin.network.give")) {
            plugin.getMessageManager().send(player, "commands.no-permission");
            return;
        }
        
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /blockmint network give <player> <tier>");
            return;
        }
        
        String targetName = args[2];
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }
        
        String tierName = args[3].toUpperCase();
        NetworkTier tier;
        try {
            tier = NetworkTier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Invalid tier: " + tierName);
            player.sendMessage(ChatColor.RED + "Available tiers: BASIC, ADVANCED, ELITE, ULTIMATE, CELESTIAL");
            return;
        }
        
        String name = "Network " + target.getName();
        ItemStack networkBlock = NetworkBlock.createNetworkBlockItem(tier, name);
        target.getInventory().addItem(networkBlock);
        
        player.sendMessage(ChatColor.GREEN + "Gave a " + tier.getDisplayName() + " Network Block to " + target.getName());
        target.sendMessage(ChatColor.GREEN + "You received a " + tier.getDisplayName() + " Network Block");
    }
    
    private void handleVisualizeNetwork(Player player) {
        plugin.getNetworkManager().toggleNetworkVisualization(player);
    }
    
    private double calculateNetworkCost(NetworkTier tier) {
        switch (tier) {
            case BASIC:
                return 1000;
            case ADVANCED:
                return 5000;
            case ELITE:
                return 25000;
            case ULTIMATE:
                return 100000;
            case CELESTIAL:
                return 500000;
            default:
                return 1000;
        }
    }
    
    private double calculateUpgradeCost(NetworkTier currentTier, NetworkTier newTier) {
        double baseCost = calculateNetworkCost(newTier) - calculateNetworkCost(currentTier);
        return baseCost * 0.8;
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            for (Map.Entry<String, SubCommand> entry : subcommands.entrySet()) {
                SubCommand subcommand = entry.getValue();
                if (subcommand.getPermission() == null || sender.hasPermission(subcommand.getPermission())) {
                    String name = entry.getKey();
                    if (name.startsWith(args[1].toLowerCase())) {
                        completions.add(name);
                    }
                }
            }
            return completions;
        } else if (args.length > 2) {
            String subcommandName = args[1].toLowerCase();
            SubCommand subcommand = subcommands.get(subcommandName);
            if (subcommand != null) {
                return subcommand.getTabCompletions(sender, args);
            }
        }
        
        if (args.length == 2) {
            return Arrays.asList("create", "list", "info", "add", "remove", "upgrade", "give", "visualize").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 3) {
            switch (args[1].toLowerCase()) {
                case "info":
                case "upgrade":
                    return plugin.getNetworkManager().getPlayerNetworks(((Player) sender).getUniqueId()).stream()
                            .map(n -> String.valueOf(n.getNetworkId()))
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                case "add":
                    return plugin.getNetworkManager().getPlayerNetworks(((Player) sender).getUniqueId()).stream()
                            .map(n -> String.valueOf(n.getNetworkId()))
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                case "give":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                case "remove":
                    return plugin.getGeneratorManager().getActiveGenerators().values().stream()
                            .filter(g -> g.getOwner().equals(((Player) sender).getUniqueId()))
                            .filter(g -> plugin.getNetworkManager().getGeneratorNetwork(g.getId()) != null)
                            .map(g -> String.valueOf(g.getId()))
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
            }
        }
        
        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("upgrade") || args[1].equalsIgnoreCase("give")) {
                return Arrays.stream(NetworkTier.values())
                        .map(NetworkTier::name)
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[1].equalsIgnoreCase("add")) {
                return plugin.getGeneratorManager().getActiveGenerators().values().stream()
                        .filter(g -> g.getOwner().equals(((Player) sender).getUniqueId()))
                        .map(g -> String.valueOf(g.getId()))
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }

    public Map<String, SubCommand> getSubcommands() {
        return subcommands;
    }
}