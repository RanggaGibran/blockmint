package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.network.NetworkBlock;
import id.rnggagib.blockmint.network.NetworkTier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NetworkStatsDashboardGUI extends BaseGUI {
    
    private final NetworkBlock network;
    private final Map<String, Integer> generatorTypeCount = new HashMap<>();
    private final Map<Integer, Double> generatorLevelValues = new TreeMap<>();
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    
    public NetworkStatsDashboardGUI(BlockMint plugin, Player player, NetworkBlock network) {
        super(plugin, player);
        this.network = network;
        calculateNetworkStatistics();
    }
    
    private void calculateNetworkStatistics() {
        generatorTypeCount.clear();
        generatorLevelValues.clear();
        
        double totalNetworkValue = 0;
        
        for (int generatorId : network.getConnectedGenerators()) {
            Generator generator = findGenerator(generatorId);
            if (generator == null) continue;
            
            String typeName = generator.getType().getName();
            int level = generator.getLevel();
            double value = generator.getValue();
            
            generatorTypeCount.put(typeName, generatorTypeCount.getOrDefault(typeName, 0) + 1);
            generatorLevelValues.put(level, generatorLevelValues.getOrDefault(level, 0.0) + value);
            
            totalNetworkValue += value;
        }
    }
    
    private Generator findGenerator(int generatorId) {
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getId() == generatorId) {
                return generator;
            }
        }
        return null;
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Network Statistics: " + ChatColor.AQUA + network.getName());
        
        // Network overview section
        ItemStack networkInfoItem = createNetworkOverviewItem();
        inventory.setItem(4, networkInfoItem);
        
        // Generator type distribution section
        createTypeDistributionItems();
        
        // Generator level distribution
        createLevelDistributionItems();
        
        // Efficiency analysis
        ItemStack efficiencyItem = createEfficiencyAnalysisItem();
        inventory.setItem(22, efficiencyItem);
        
        // Production forecast
        ItemStack forecastItem = createProductionForecastItem();
        inventory.setItem(31, forecastItem);
        
        // Income potential by time
        createIncomeByTimeItems();
        
        // Network growth potential
        ItemStack growthItem = createGrowthPotentialItem();
        inventory.setItem(40, growthItem);
        
        // Back button
        inventory.setItem(45, GUIManager.createItem(Material.ARROW, ChatColor.YELLOW + "Back to Network Menu", null));
        
        // Fill empty slots with glass panes
        for (int i = 0; i < 54; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, GUIManager.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
        }
        
        player.openInventory(inventory);
    }
    
    private ItemStack createNetworkOverviewItem() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + network.getNetworkId());
        lore.add(ChatColor.GRAY + "Tier: " + ChatColor.WHITE + network.getTier().getDisplayName());
        lore.add(ChatColor.GRAY + "Owner: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(network.getOwner()).getName());
        lore.add(ChatColor.GRAY + "Range: " + ChatColor.WHITE + network.getRange() + " blocks");
        lore.add("");
        lore.add(ChatColor.GRAY + "Connected Generators: " + ChatColor.WHITE + network.getConnectedGeneratorCount() + "/" + network.getMaxGenerators());
        lore.add(ChatColor.GRAY + "Efficiency Bonus: " + ChatColor.GREEN + "+" + df.format(network.getEfficiencyBonus() * 100) + "%");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Network Usage: " + getUsagePercentBar(network.getConnectedGeneratorCount(), network.getMaxGenerators()));
        
        return GUIManager.createItem(Material.BEACON, ChatColor.AQUA + "Network Overview", lore);
    }
    
    private void createTypeDistributionItems() {
        ItemStack chartItem = GUIManager.createItem(Material.FILLED_MAP, ChatColor.GREEN + "Generator Type Distribution", 
            List.of(ChatColor.GRAY + "Types of generators in your network"));
        inventory.setItem(19, chartItem);
        
        int slot = 28;
        for (Map.Entry<String, Integer> entry : generatorTypeCount.entrySet()) {
            if (slot >= 36) break;
            
            String typeName = entry.getKey();
            int count = entry.getValue();
            double percentage = (double) count / network.getConnectedGeneratorCount() * 100;
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Count: " + ChatColor.WHITE + count);
            lore.add(ChatColor.GRAY + "Percentage: " + ChatColor.WHITE + df.format(percentage) + "%");
            
            Material material = Material.PAPER;
            for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
                if (generator.getType().getName().equals(typeName)) {
                    try {
                        material = Material.valueOf(generator.getType().getMaterial());
                        break;
                    } catch (Exception e) {
                        material = Material.PAPER;
                    }
                }
            }
            
            inventory.setItem(slot, GUIManager.createItem(material, ChatColor.YELLOW + typeName, lore));
            slot++;
        }
    }
    
    private void createLevelDistributionItems() {
        ItemStack chartItem = GUIManager.createItem(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "Generator Level Distribution", 
            List.of(ChatColor.GRAY + "Level distribution in your network"));
        inventory.setItem(25, chartItem);
        
        int slot = 34;
        double totalValue = generatorLevelValues.values().stream().mapToDouble(Double::doubleValue).sum();
        
        for (Map.Entry<Integer, Double> entry : generatorLevelValues.entrySet()) {
            if (slot >= 36) break;
            
            int level = entry.getKey();
            double value = entry.getValue();
            double percentage = value / totalValue * 100;
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Value: " + ChatColor.GOLD + "$" + df.format(value));
            lore.add(ChatColor.GRAY + "Percentage: " + ChatColor.WHITE + df.format(percentage) + "%");
            
            inventory.setItem(slot, GUIManager.createItem(getGlassByLevel(level), ChatColor.YELLOW + "Level " + level, lore));
            slot++;
        }
    }
    
    private ItemStack createEfficiencyAnalysisItem() {
        double baseEfficiency = network.getTier().getBaseBonus() * 100;
        double perGeneratorBonus = network.getTier().getPerGeneratorBonus() * 100;
        double maxBonus = network.getTier().getMaxBonus() * 100;
        double currentBonus = network.getEfficiencyBonus() * 100;
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Base efficiency: " + ChatColor.GREEN + "+" + df.format(baseEfficiency) + "%");
        lore.add(ChatColor.GRAY + "Per generator: " + ChatColor.GREEN + "+" + df.format(perGeneratorBonus) + "%");
        lore.add(ChatColor.GRAY + "Current bonus: " + ChatColor.GREEN + "+" + df.format(currentBonus) + "%");
        lore.add(ChatColor.GRAY + "Maximum possible: " + ChatColor.GREEN + "+" + df.format(maxBonus) + "%");
        lore.add("");
        
        if (currentBonus < maxBonus) {
            double remaining = maxBonus - currentBonus;
            int generatorsNeeded = (int) Math.ceil(remaining / perGeneratorBonus);
            lore.add(ChatColor.YELLOW + "Add " + generatorsNeeded + " more generators to reach max efficiency");
        } else {
            lore.add(ChatColor.GREEN + "Maximum efficiency achieved!");
        }
        
        return GUIManager.createItem(Material.COMPARATOR, ChatColor.GOLD + "Efficiency Analysis", lore);
    }
    
    private ItemStack createProductionForecastItem() {
        double hourlyProduction = calculateTotalHourlyProduction();
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Hourly income: " + ChatColor.GOLD + "$" + df.format(hourlyProduction));
        lore.add(ChatColor.GRAY + "Daily income: " + ChatColor.GOLD + "$" + df.format(hourlyProduction * 24));
        lore.add(ChatColor.GRAY + "Weekly income: " + ChatColor.GOLD + "$" + df.format(hourlyProduction * 24 * 7));
        lore.add("");
        lore.add(ChatColor.GRAY + "Based on current network setup");
        lore.add(ChatColor.GRAY + "and generator efficiency");
        
        return GUIManager.createItem(Material.CLOCK, ChatColor.AQUA + "Production Forecast", lore);
    }
    
    private void createIncomeByTimeItems() {
        ItemStack chartItem = GUIManager.createItem(Material.GOLD_INGOT, ChatColor.GREEN + "Income Breakdown by Time", 
            List.of(ChatColor.GRAY + "Revenue generation timeline"));
        inventory.setItem(37, chartItem);
        
        int[] timeIntervals = {1, 8, 24};
        String[] timeLabels = {"Hour", "Work Day", "Day"};
        Material[] materials = {Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT};
        
        double hourlyProduction = calculateTotalHourlyProduction();
        
        for (int i = 0; i < timeIntervals.length; i++) {
            double income = hourlyProduction * timeIntervals[i];
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Income: " + ChatColor.GOLD + "$" + df.format(income));
            lore.add(ChatColor.GRAY + "Based on " + timeIntervals[i] + " hour" + (timeIntervals[i] > 1 ? "s" : ""));
            lore.add("");
            lore.add(ChatColor.YELLOW + "Click to see detailed breakdown");
            
            inventory.setItem(46 + i, GUIManager.createItem(materials[i], ChatColor.YELLOW + "Income per " + timeLabels[i], lore));
        }
    }
    
    private ItemStack createGrowthPotentialItem() {
        double currentEfficiency = network.getEfficiencyBonus() * 100;
        double maxEfficiency = network.getTier().getMaxBonus() * 100;
        double growthPotential = (maxEfficiency / currentEfficiency - 1) * 100;
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current network capacity: " + network.getConnectedGeneratorCount() + "/" + network.getMaxGenerators());
        lore.add(ChatColor.GRAY + "Remaining slots: " + (network.getMaxGenerators() - network.getConnectedGeneratorCount()));
        lore.add("");
        lore.add(ChatColor.GRAY + "Efficiency growth potential: " + ChatColor.GREEN + "+" + df.format(growthPotential) + "%");
        
        if (network.getTier().isMaxTier()) {
            lore.add("");
            lore.add(ChatColor.GOLD + "Maximum tier reached");
        } else {
            NetworkTier nextTier = network.getTier().getNextTier();
            lore.add("");
            lore.add(ChatColor.YELLOW + "Next tier: " + ChatColor.WHITE + nextTier.getDisplayName());
            lore.add(ChatColor.YELLOW + "Bonus increase: " + ChatColor.GREEN + "+" + 
                df.format((nextTier.getMaxBonus() - network.getTier().getMaxBonus()) * 100) + "%");
        }
        
        return GUIManager.createItem(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, ChatColor.LIGHT_PURPLE + "Network Growth Potential", lore);
    }
    
    private double calculateTotalHourlyProduction() {
        double totalHourlyProduction = 0;
        for (int generatorId : network.getConnectedGenerators()) {
            Generator generator = findGenerator(generatorId);
            if (generator == null) continue;
            
            double value = generator.getValue();
            long generationTimeSeconds = generator.getType().getGenerationTime();
            double hourlyGeneration = value * (3600.0 / generationTimeSeconds);
            
            totalHourlyProduction += hourlyGeneration;
        }
        
        return totalHourlyProduction;
    }
    
    private String getUsagePercentBar(int current, int max) {
        int barLength = 20;
        int filledBars = Math.min(barLength, Math.round((float) current / max * barLength));
        
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("■");
            } else {
                bar.append(ChatColor.GRAY).append("■");
            }
        }
        
        bar.append(" ").append(ChatColor.YELLOW).append(current).append("/").append(max)
           .append(" (").append(Math.round((float) current / max * 100)).append("%)");
        
        return bar.toString();
    }
    
    private Material getGlassByLevel(int level) {
        switch (level % 16) {
            case 0: return Material.WHITE_STAINED_GLASS;
            case 1: return Material.ORANGE_STAINED_GLASS;
            case 2: return Material.MAGENTA_STAINED_GLASS;
            case 3: return Material.LIGHT_BLUE_STAINED_GLASS;
            case 4: return Material.YELLOW_STAINED_GLASS;
            case 5: return Material.LIME_STAINED_GLASS;
            case 6: return Material.PINK_STAINED_GLASS;
            case 7: return Material.GRAY_STAINED_GLASS;
            case 8: return Material.LIGHT_GRAY_STAINED_GLASS;
            case 9: return Material.CYAN_STAINED_GLASS;
            case 10: return Material.PURPLE_STAINED_GLASS;
            case 11: return Material.BLUE_STAINED_GLASS;
            case 12: return Material.BROWN_STAINED_GLASS;
            case 13: return Material.GREEN_STAINED_GLASS;
            case 14: return Material.RED_STAINED_GLASS;
            default: return Material.BLACK_STAINED_GLASS;
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        
        if (slot == 45) {
            // Back button
            openNetworkMainMenu();
            return;
        }
    }
    
    private void openNetworkMainMenu() {
        player.closeInventory();
        plugin.getNetworkGUIManager().openNetworkManagementGUI(player, network);
    }
}