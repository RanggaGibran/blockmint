package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockListeners implements Listener {
    
    private final BlockMint plugin;
    private final Map<UUID, Long> lastPickupAttempt = new HashMap<>();
    private final long CONFIRMATION_DELAY_MS = 3000;
    
    public BlockListeners(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        String material = block.getType().name();
        
        Map<String, GeneratorType> generatorTypes = plugin.getGeneratorManager().getGeneratorTypes();
        GeneratorType generatorType = null;
        
        for (GeneratorType type : generatorTypes.values()) {
            if (type.getMaterial().equals(material)) {
                generatorType = type;
                break;
            }
        }
        
        if (generatorType == null) {
            return;
        }
        
        if (!player.hasPermission("blockmint.use")) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }
        
        int maxGenerators = plugin.getConfigManager().getConfig().getInt("settings.max-generators-per-player", 10);
        if (maxGenerators > 0) {
            int playerGenerators = countPlayerGenerators(player.getUniqueId());
            if (playerGenerators >= maxGenerators && !player.hasPermission("blockmint.bypass.limit")) {
                event.setCancelled(true);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("limit", String.valueOf(maxGenerators));
                plugin.getMessageManager().send(player, "general.generator-limit-reached", placeholders);
                return;
            }
        }
        
        boolean success = plugin.getGeneratorManager().placeGenerator(
                player.getUniqueId(), 
                block.getLocation(), 
                generatorType.getId()
        );
        
        if (success) {
            plugin.getMessageManager().send(player, "general.generator-placed");
            if (plugin.getConfigManager().getConfig().getBoolean("settings.use-holograms", true)) {
                DisplayManager.createHologram(plugin, block.getLocation(), generatorType, 1);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Location location = block.getLocation();
        
        Generator generator = plugin.getGeneratorManager().getGenerator(location);
        if (generator == null) {
            return;
        }
        
        event.setCancelled(true);
        
        if (event.getAction().name().contains("LEFT_CLICK") && player.isSneaking()) {
            attemptPickupGenerator(player, generator, location, block);
            return;
        }
        
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK:
                if (player.isSneaking() && player.hasPermission("blockmint.upgrade")) {
                    upgradeGenerator(player, generator);
                } else {
                    collectGenerator(player, generator);
                }
                break;
            case LEFT_CLICK_BLOCK:
                showGeneratorInfo(player, generator);
                break;
            default:
                break;
        }
    }
    
    private void attemptPickupGenerator(Player player, Generator generator, Location location, Block block) {
        if (!generator.getOwner().equals(player.getUniqueId()) && 
            !player.hasPermission("blockmint.admin.remove")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        if (lastPickupAttempt.containsKey(playerUUID) && 
            now - lastPickupAttempt.get(playerUUID) < CONFIRMATION_DELAY_MS) {
            
            if (plugin.getGeneratorManager().removeGenerator(location)) {
                ItemStack generatorItem = plugin.getUtils().getGeneratorItemManager().createGeneratorItem(generator.getType());
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(generatorItem);
                
                if (leftover.isEmpty()) {
                    plugin.getMessageManager().send(player, "general.generator-collected");
                } else {
                    location.getWorld().dropItemNaturally(
                        location.clone().add(0.5, 0.5, 0.5), 
                        generatorItem
                    );
                    plugin.getMessageManager().send(player, "general.generator-collected-inventory-full");
                }
                
                DisplayManager.removeHologram(location);
                block.setType(Material.AIR);
            } else {
                plugin.getMessageManager().send(player, "general.generator-removed-failed");
            }
            
            lastPickupAttempt.remove(playerUUID);
        } else {
            lastPickupAttempt.put(playerUUID, now);
            plugin.getMessageManager().send(player, "general.generator-collect-confirm");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        
        Generator generator = plugin.getGeneratorManager().getGenerator(location);
        if (generator == null) {
            return;
        }
        
        event.setCancelled(true);
    }
    
    private int countPlayerGenerators(UUID playerUUID) {
        int count = 0;
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getOwner().equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }
    
    private void collectGenerator(Player player, Generator generator) {
        if (!generator.canGenerate()) {
            long remaining = generator.getType().getGenerationTime() * 1000 - 
                           (System.currentTimeMillis() - generator.getLastGeneration());
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", formatTime(remaining / 1000));
            plugin.getMessageManager().send(player, "general.not-ready", placeholders);
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
    
    private void upgradeGenerator(Player player, Generator generator) {
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
    
    private void showGeneratorInfo(Player player, Generator generator) {
        GeneratorType type = generator.getType();
        UUID ownerUUID = generator.getOwner();
        String ownerName = plugin.getServer().getOfflinePlayer(ownerUUID).getName();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", type.getName());
        placeholders.put("level", String.valueOf(generator.getLevel()));
        placeholders.put("max-level", String.valueOf(type.getMaxLevel()));
        placeholders.put("value", String.format("%.2f", generator.getValue()));
        placeholders.put("owner", ownerName);
        
        plugin.getMessageManager().send(player, "generator.info-header", placeholders);
        plugin.getMessageManager().send(player, "generator.info-level", placeholders);
        plugin.getMessageManager().send(player, "generator.info-value", placeholders);
        plugin.getMessageManager().send(player, "generator.info-owner", placeholders);
        
        if (generator.getLevel() < type.getMaxLevel()) {
            placeholders.put("upgrade-cost", String.format("%.2f", type.getUpgradeCost(generator.getLevel())));
            plugin.getMessageManager().send(player, "generator.info-upgrade", placeholders);
        }
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