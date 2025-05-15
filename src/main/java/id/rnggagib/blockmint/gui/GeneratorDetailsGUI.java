package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GeneratorDetailsGUI extends BaseGUI {
    
    private final Generator generator;
    private final int listPage;
    
    public GeneratorDetailsGUI(BlockMint plugin, Player player, Generator generator, int listPage) {
        super(plugin, player);
        this.generator = generator;
        this.listPage = listPage;
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(player, 27, ChatColor.GOLD + generator.getType().getName() + " Generator");
        
        inventory.setItem(4, GUIManager.createGeneratorIcon(generator, generator.canGenerate()));
        
        List<String> collectLore = new ArrayList<>();
        if (generator.canGenerate()) {
            collectLore.add("&7Click to collect");
            collectLore.add("&a$" + String.format("%.2f", generator.getValue()));
            inventory.setItem(11, GUIManager.createItem(Material.CHEST, "&aCollect", collectLore));
        } else {
            long elapsed = System.currentTimeMillis() - generator.getLastGeneration();
            long total = generator.getType().getGenerationTime() * 1000;
            int percent = (int) ((elapsed * 100) / total);
            long remaining = (total - elapsed) / 1000;
            
            collectLore.add("&7Progress: &e" + percent + "%");
            collectLore.add("&7Time remaining: &e" + GUIManager.formatTime(remaining));
            inventory.setItem(11, GUIManager.createItem(Material.HOPPER, "&7Not Ready", collectLore));
        }
        
        List<String> upgradeLore = new ArrayList<>();
        if (generator.getLevel() < generator.getType().getMaxLevel()) {
            double upgradeCost = generator.getType().getUpgradeCost(generator.getLevel());
            upgradeLore.add("&7Upgrade to level " + (generator.getLevel() + 1));
            upgradeLore.add("&7Cost: &e$" + String.format("%.2f", upgradeCost));
            upgradeLore.add("");
            double newValue = generator.getType().getValueAtLevel(generator.getLevel() + 1);
            upgradeLore.add("&7New value: &e$" + String.format("%.2f", newValue));
            inventory.setItem(13, GUIManager.createItem(Material.EXPERIENCE_BOTTLE, "&6Upgrade", upgradeLore));
        } else {
            upgradeLore.add("&7This generator is already");
            upgradeLore.add("&7at maximum level!");
            inventory.setItem(13, GUIManager.createItem(Material.BARRIER, "&cMax Level", upgradeLore));
        }
        
        List<String> teleportLore = new ArrayList<>();
        teleportLore.add("&7Teleport to this generator");
        inventory.setItem(15, GUIManager.createItem(Material.ENDER_PEARL, "&bTeleport", teleportLore));
        
        inventory.setItem(18, GUIManager.createItem(Material.ARROW, "&aBack to List", null));
        
        for (int i = 0; i < 27; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, GUIManager.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
        }
        
        List<String> locationLore = new ArrayList<>();
        Location loc = generator.getLocation();
        locationLore.add("&7World: &e" + loc.getWorld().getName());
        locationLore.add("&7X: &e" + loc.getBlockX());
        locationLore.add("&7Y: &e" + loc.getBlockY());
        locationLore.add("&7Z: &e" + loc.getBlockZ());
        inventory.setItem(26, GUIManager.createItem(Material.COMPASS, "&6Location", locationLore));
        
        player.openInventory(inventory);
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= inventory.getSize() || slot < 0) {
            return;
        }
        
        switch (slot) {
            case 11:
                if (generator.canGenerate()) {
                    collectGenerator();
                    open();
                }
                break;
            case 13:
                if (generator.getLevel() < generator.getType().getMaxLevel()) {
                    upgradeGenerator();
                    open();
                }
                break;
            case 15:
                teleportToGenerator();
                break;
            case 18:
                GeneratorListGUI listGUI = new GeneratorListGUI(plugin, player, listPage);
                listGUI.open();
                plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
                plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
                break;
        }
    }
    
    private void collectGenerator() {
        if (!generator.canGenerate()) {
            player.sendMessage(ChatColor.RED + "This generator isn't ready to collect!");
            return;
        }
        
        double value = generator.getValue();
        plugin.getEconomy().depositPlayer(player, value);
        
        generator.setLastGeneration(System.currentTimeMillis());
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.format("%.2f", value));
        plugin.getMessageManager().send(player, "general.collect-success", placeholders);
        
        updatePlayerEarnings(player.getUniqueId(), value);
        DisplayManager.updateHologram(plugin, generator);
    }
    
    private void upgradeGenerator() {
        if (generator.getLevel() >= generator.getType().getMaxLevel()) {
            plugin.getMessageManager().send(player, "general.max-level-reached");
            return;
        }
        
        double upgradeCost = generator.getType().getUpgradeCost(generator.getLevel());
        if (plugin.getConfigManager().getConfig().getBoolean("economy.charge-for-upgrades", true)) {
            if (!plugin.getEconomy().has(player, upgradeCost)) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("cost", String.format("%.2f", upgradeCost));
                plugin.getMessageManager().send(player, "general.not-enough-money", placeholders);
                return;
            }
            
            plugin.getEconomy().withdrawPlayer(player, upgradeCost);
        }
        
        int newLevel = generator.getLevel() + 1;
        generator.setLevel(newLevel);
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "UPDATE generators SET level = ? WHERE id = ?"
            );
            stmt.setInt(1, newLevel);
            stmt.setInt(2, generator.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not update generator level in database: " + e.getMessage());
            return;
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("level", String.valueOf(newLevel));
        plugin.getMessageManager().send(player, "general.upgrade-success", placeholders);
        
        DisplayManager.updateHologram(plugin, generator);
    }
    
    private void teleportToGenerator() {
        Location location = generator.getLocation().clone().add(0.5, 1, 0.5);
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "Teleported to your " + generator.getType().getName() + " generator!");
        player.closeInventory();
    }
    
    private void updatePlayerEarnings(UUID playerUUID, double amount) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "UPDATE player_stats SET total_earnings = total_earnings + ? WHERE uuid = ?"
            );
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not update player earnings in database: " + e.getMessage());
        }
    }
}