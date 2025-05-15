package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.network.NetworkBlock;
import id.rnggagib.blockmint.network.NetworkTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetworkGUIManager {

    private final BlockMint plugin;
    private final Map<UUID, NetworkBlock> activeNetworkGUI = new HashMap<>();
    private final Map<UUID, Map<Integer, Generator>> generatorSlots = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    
    public NetworkGUIManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void openNetworkManagementGUI(Player player, NetworkBlock network) {
        activeNetworkGUI.put(player.getUniqueId(), network);
        playerPages.put(player.getUniqueId(), 0);
        openNetworkMainMenu(player, network);
    }
    
    private void openNetworkMainMenu(Player player, NetworkBlock network) {
        Inventory inventory = Bukkit.createInventory(null, 36, 
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Network: " + ChatColor.AQUA + network.getName());
        
        NetworkTier tier = network.getTier();
        
        // Network info
        ItemStack infoItem = createItem(
                Material.BEACON, 
                ChatColor.AQUA + "Network Information", 
                List.of(
                    ChatColor.GRAY + "ID: " + ChatColor.WHITE + network.getNetworkId(),
                    ChatColor.GRAY + "Tier: " + ChatColor.WHITE + tier.getDisplayName(),
                    ChatColor.GRAY + "Range: " + ChatColor.WHITE + network.getRange() + " blocks",
                    ChatColor.GRAY + "Generators: " + ChatColor.WHITE + network.getConnectedGeneratorCount() + "/" + network.getMaxGenerators(),
                    ChatColor.GRAY + "Efficiency Bonus: " + ChatColor.WHITE + String.format("%.1f%%", network.getEfficiencyBonus() * 100)
                )
        );
        inventory.setItem(4, infoItem);
        
        // Advanced statistics dashboard button
        ItemStack statsItem = createItem(
                Material.SPYGLASS,
                ChatColor.GOLD + "Advanced Statistics Dashboard",
                List.of(
                    ChatColor.GRAY + "View detailed statistics and analytics",
                    ChatColor.GRAY + "for this generator network",
                    "",
                    ChatColor.YELLOW + "Click to open the dashboard"
                )
        );
        inventory.setItem(8, statsItem);
        
        // Network range visualization
        ItemStack visualizeItem = createItem(
                Material.ENDER_EYE,
                ChatColor.GREEN + "Toggle Network Visualization",
                List.of(ChatColor.GRAY + "Click to toggle network range and connections visualization")
        );
        inventory.setItem(11, visualizeItem);
        
        // Connected generators
        ItemStack generatorsItem = createItem(
                Material.CRAFTING_TABLE,
                ChatColor.YELLOW + "View Connected Generators",
                List.of(
                    ChatColor.GRAY + "Connected: " + network.getConnectedGeneratorCount() + "/" + network.getMaxGenerators(),
                    ChatColor.GRAY + "Click to manage connected generators"
                )
        );
        inventory.setItem(13, generatorsItem);
        
        // Upgrade network
        List<String> upgradeLore = new ArrayList<>();
        if (!tier.isMaxTier()) {
            NetworkTier nextTier = tier.getNextTier();
            double cost = calculateUpgradeCost(tier, nextTier);
            upgradeLore.add(ChatColor.GRAY + "Upgrade to: " + ChatColor.GOLD + nextTier.getDisplayName());
            upgradeLore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "$" + String.format("%,.2f", cost));
            upgradeLore.add("");
            upgradeLore.add(ChatColor.GRAY + "New Range: " + ChatColor.WHITE + (10 + (nextTier.ordinal() * 5)) + " blocks");
            upgradeLore.add(ChatColor.GRAY + "New Max Generators: " + ChatColor.WHITE + (5 + (nextTier.ordinal() * 5)));
            upgradeLore.add(ChatColor.GRAY + "New Base Bonus: " + ChatColor.WHITE + String.format("%.1f%%", nextTier.getBaseBonus() * 100));
        } else {
            upgradeLore.add(ChatColor.RED + "Maximum tier reached");
            upgradeLore.add(ChatColor.GRAY + "This network is already at the highest tier");
        }
        
        Material upgradeItemType = tier.isMaxTier() ? Material.BARRIER : Material.EXPERIENCE_BOTTLE;
        ItemStack upgradeItem = createItem(upgradeItemType, ChatColor.GOLD + "Upgrade Network", upgradeLore);
        inventory.setItem(15, upgradeItem);
        
        // Pickup network
        ItemStack pickupItem = createItem(
                Material.CHEST_MINECART,
                ChatColor.RED + "Dismantle Network",
                List.of(
                    ChatColor.GRAY + "Removes this network and returns",
                    ChatColor.GRAY + "the network block to your inventory.",
                    ChatColor.GRAY + "All connections will be lost.",
                    "",
                    ChatColor.RED + "This action cannot be undone!"
                )
        );
        inventory.setItem(31, pickupItem);
        
        // Fill empty slots with glass panes
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
        }
        
        player.openInventory(inventory);
    }
    
    public void openConnectedGeneratorsGUI(Player player) {
        NetworkBlock network = activeNetworkGUI.get(player.getUniqueId());
        if (network == null) return;
        
        int page = playerPages.getOrDefault(player.getUniqueId(), 0);
        
        Inventory inventory = Bukkit.createInventory(null, 54, 
                ChatColor.DARK_AQUA + "Network Generators - Page " + (page + 1));
        
        // Get connected generator objects
        List<Generator> connectedGenerators = new ArrayList<>();
        for (int generatorId : network.getConnectedGenerators()) {
            for (Generator g : plugin.getGeneratorManager().getActiveGenerators().values()) {
                if (g.getId() == generatorId) {
                    connectedGenerators.add(g);
                    break;
                }
            }
        }
        
        // Get nearby unconnected generators
        List<Generator> nearbyGenerators = new ArrayList<>();
        for (Generator g : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (g.getOwner().equals(player.getUniqueId()) && 
                !network.getConnectedGenerators().contains(g.getId()) &&
                network.isInRange(g.getLocation())) {
                nearbyGenerators.add(g);
            }
        }
        
        // Show connected generators
        int slot = 0;
        Map<Integer, Generator> slotMap = new HashMap<>();
        
        int generatorsPerPage = 18;
        int maxConnectedPages = (connectedGenerators.size() - 1) / generatorsPerPage + 1;
        int maxNearbyPages = (nearbyGenerators.size() - 1) / generatorsPerPage + 1;
        int maxTotalPages = Math.max(maxConnectedPages, maxNearbyPages);
        
        if (page >= maxTotalPages && maxTotalPages > 0) {
            page = 0;
            playerPages.put(player.getUniqueId(), 0);
        }
        
        int startConnectedIndex = page * generatorsPerPage;
        int endConnectedIndex = Math.min(startConnectedIndex + generatorsPerPage, connectedGenerators.size());
        
        for (int i = startConnectedIndex; i < endConnectedIndex; i++) {
            Generator generator = connectedGenerators.get(i);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + generator.getId());
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + generator.getType().getName());
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + generator.getLevel() + "/" + generator.getType().getMaxLevel());
            lore.add(ChatColor.GRAY + "Value: " + ChatColor.WHITE + "$" + String.format("%.2f", generator.getValue()));
            lore.add("");
            lore.add(ChatColor.GRAY + "X: " + ChatColor.WHITE + generator.getLocation().getBlockX());
            lore.add(ChatColor.GRAY + "Y: " + ChatColor.WHITE + generator.getLocation().getBlockY());
            lore.add(ChatColor.GRAY + "Z: " + ChatColor.WHITE + generator.getLocation().getBlockZ());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to remove from network");
            
            ItemStack item = createItem(
                Material.valueOf(generator.getType().getMaterial()),
                ChatColor.GREEN + "Connected: " + generator.getType().getName() + " Generator",
                lore
            );
            
            inventory.setItem(slot, item);
            slotMap.put(slot, generator);
            slot++;
        }
        
        // Divider
        for (int i = 18; i < 27; i++) {
            inventory.setItem(i, createItem(Material.WHITE_STAINED_GLASS_PANE, " ", null));
        }
        
        // Title for nearby generators
        inventory.setItem(22, createItem(
            Material.COMPASS,
            ChatColor.GOLD + "Nearby Available Generators",
            List.of(ChatColor.GRAY + "Click on a generator below to connect it to this network")
        ));
        
        // Show nearby generators
        slot = 27;
        int startNearbyIndex = page * generatorsPerPage;
        int endNearbyIndex = Math.min(startNearbyIndex + generatorsPerPage, nearbyGenerators.size());
        
        for (int i = startNearbyIndex; i < endNearbyIndex; i++) {
            Generator generator = nearbyGenerators.get(i);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + generator.getId());
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + generator.getType().getName());
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + generator.getLevel() + "/" + generator.getType().getMaxLevel());
            lore.add(ChatColor.GRAY + "Value: " + ChatColor.WHITE + "$" + String.format("%.2f", generator.getValue()));
            lore.add("");
            lore.add(ChatColor.GRAY + "X: " + ChatColor.WHITE + generator.getLocation().getBlockX());
            lore.add(ChatColor.GRAY + "Y: " + ChatColor.WHITE + generator.getLocation().getBlockY());
            lore.add(ChatColor.GRAY + "Z: " + ChatColor.WHITE + generator.getLocation().getBlockZ());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to add to network");
            
            ItemStack item = createItem(
                Material.valueOf(generator.getType().getMaterial()),
                ChatColor.AQUA + "Available: " + generator.getType().getName() + " Generator",
                lore
            );
            
            inventory.setItem(slot, item);
            slotMap.put(slot, generator);
            slot++;
        }
        
        // Navigation bar
        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "Previous Page", null));
        }
        
        if (page < maxTotalPages - 1) {
            inventory.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "Next Page", null));
        }
        
        inventory.setItem(49, createItem(Material.BARRIER, ChatColor.RED + "Back to Network Menu", null));
        
        // Store generator slot map
        generatorSlots.put(player.getUniqueId(), slotMap);
        
        player.openInventory(inventory);
    }
    
    public void handleInventoryClick(Player player, int slot, Inventory inventory) {
        NetworkBlock network = activeNetworkGUI.get(player.getUniqueId());
        if (network == null) return;
        
        String title = player.getOpenInventory().getTitle();
        
        if (title.startsWith(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Network: ")) {
            // Main network menu
            switch (slot) {
                case 8:  // Statistics Dashboard
                    openNetworkStatisticsDashboard(player, network);
                    break;
                    
                case 11: // Visualize
                    plugin.getNetworkManager().toggleNetworkVisualization(player);
                    player.closeInventory();
                    break;
                    
                case 13: // View generators
                    openConnectedGeneratorsGUI(player);
                    break;
                    
                case 15: // Upgrade
                    if (!network.getTier().isMaxTier()) {
                        handleNetworkUpgrade(player, network);
                    } else {
                        player.sendMessage(ChatColor.RED + "This network is already at the maximum tier.");
                    }
                    break;
                    
                case 31: // Dismantle
                    handleNetworkDismantling(player, network);
                    break;
            }
        } else if (title.startsWith(ChatColor.DARK_AQUA + "Network Generators")) {
            // Generators menu
            Map<Integer, Generator> slotMap = generatorSlots.getOrDefault(player.getUniqueId(), new HashMap<>());
            
            if (slot == 45 && playerPages.getOrDefault(player.getUniqueId(), 0) > 0) {
                // Previous page
                playerPages.put(player.getUniqueId(), playerPages.get(player.getUniqueId()) - 1);
                openConnectedGeneratorsGUI(player);
                return;
            }
            
            if (slot == 53) {
                // Next page
                playerPages.put(player.getUniqueId(), playerPages.get(player.getUniqueId()) + 1);
                openConnectedGeneratorsGUI(player);
                return;
            }
            
            if (slot == 49) {
                // Back to main menu
                openNetworkMainMenu(player, network);
                return;
            }
            
            Generator generator = slotMap.get(slot);
            if (generator != null) {
                if (slot < 18) {
                    // Remove from network
                    boolean success = plugin.getNetworkManager().removeGeneratorFromNetwork(generator.getId());
                    if (success) {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("name", network.getName());
                        plugin.getMessageManager().send(player, "network.remove-success", placeholders);
                        openConnectedGeneratorsGUI(player);
                    } else {
                        plugin.getMessageManager().send(player, "network.remove-failed");
                    }
                } else if (slot >= 27) {
                    // Add to network
                    if (network.isMaxCapacity()) {
                        plugin.getMessageManager().send(player, "network.network-full");
                        return;
                    }
                    
                    boolean success = plugin.getNetworkManager().addGeneratorToNetwork(network.getNetworkId(), generator.getId());
                    if (success) {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("name", network.getName());
                        plugin.getMessageManager().send(player, "network.add-success", placeholders);
                        openConnectedGeneratorsGUI(player);
                    } else {
                        plugin.getMessageManager().send(player, "network.add-failed");
                    }
                }
            }
        }
    }
    
    private void handleNetworkUpgrade(Player player, NetworkBlock network) {
        NetworkTier currentTier = network.getTier();
        NetworkTier nextTier = currentTier.getNextTier();
        double cost = calculateUpgradeCost(currentTier, nextTier);
        
        if (!plugin.getEconomy().has(player, cost)) {
            player.sendMessage(ChatColor.RED + "You need $" + String.format("%,.2f", cost) + " to upgrade this network.");
            return;
        }
        
        boolean success = plugin.getNetworkManager().upgradeNetwork(network.getNetworkId(), nextTier);
        
        if (success) {
            plugin.getEconomy().withdrawPlayer(player, cost);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("tier", nextTier.getDisplayName());
            plugin.getMessageManager().send(player, "network.upgrade-success", placeholders);
            
            // Update the GUI
            openNetworkMainMenu(player, network);
        } else {
            plugin.getMessageManager().send(player, "network.upgrade-failed");
        }
    }
    
    private void handleNetworkDismantling(Player player, NetworkBlock network) {
        boolean success = plugin.getNetworkManager().deleteNetwork(network.getNetworkId());
        
        if (success) {
            // Create network block item
            ItemStack networkItem = NetworkBlock.createNetworkBlockItem(network.getTier(), network.getName());
            player.getInventory().addItem(networkItem);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", network.getName());
            plugin.getMessageManager().send(player, "network.removed", placeholders);
            
            player.closeInventory();
        } else {
            plugin.getMessageManager().send(player, "network.remove-failed");
        }
    }
    
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (name != null) {
            meta.setDisplayName(name);
        }
        
        if (lore != null) {
            meta.setLore(lore);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private double calculateUpgradeCost(NetworkTier currentTier, NetworkTier nextTier) {
        double baseCost = getNetworkTierCost(nextTier) - getNetworkTierCost(currentTier);
        return baseCost * 0.8;
    }
    
    private double getNetworkTierCost(NetworkTier tier) {
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
    
    public void playerLogout(UUID playerId) {
        activeNetworkGUI.remove(playerId);
        generatorSlots.remove(playerId);
        playerPages.remove(playerId);
    }
    
    public boolean isNetworkInventory(String title) {
        return title.startsWith(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Network: ") || 
               title.startsWith(ChatColor.DARK_AQUA + "Network Generators") ||
               title.startsWith(ChatColor.DARK_AQUA + "Network Statistics:");
    }
    
    private void openNetworkStatisticsDashboard(Player player, NetworkBlock network) {
        NetworkStatsDashboardGUI dashboardGUI = new NetworkStatsDashboardGUI(plugin, player, network);
        dashboardGUI.open();
        
        // Register the dashboard as an active GUI to handle interactions
        plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
        plugin.getGUIManager().registerActiveGUI(player.getUniqueId().toString(), dashboardGUI);
    }
}