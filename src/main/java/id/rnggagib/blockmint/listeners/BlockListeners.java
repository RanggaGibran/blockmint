package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.utils.DisplayManager;
import id.rnggagib.blockmint.utils.GeneratorItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BlockListeners implements Listener {
    
    private final BlockMint plugin;
    private final ConcurrentHashMap<UUID, Long> lastPickupAttempt = new ConcurrentHashMap<>(16, 0.75f, 4);
    private final long CONFIRMATION_DELAY_MS = 3000;
    private final ConcurrentHashMap<UUID, Integer> generatorCountCache = new ConcurrentHashMap<>(32, 0.75f, 8);
    private final ConcurrentHashMap<UUID, Long> generatorCountCacheExpiry = new ConcurrentHashMap<>(32, 0.75f, 8);
    private final long COUNT_CACHE_DURATION_MS = 60000;
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private final AtomicInteger pendingAsyncOperations = new AtomicInteger(0);
    
    public BlockListeners(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String generatorType = GeneratorItemManager.getGeneratorType(item);
        
        if (generatorType == null) {
            return;
        }

        // Jika event dibatalkan oleh plugin lain, hormati pembatalan tersebut
        if (event.isCancelled()) {
            plugin.getMessageManager().send(event.getPlayer(), "general.cannot-place-here");
            return;
        }
        
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        
        // Biarkan event berjalan dan tunggu hingga post-processing
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Cek apakah blok benar-benar terpasang
            Material expectedMaterial = Material.valueOf(plugin.getGeneratorManager()
                                               .getGeneratorTypes().get(generatorType).getMaterial());
            
            if (location.getBlock().getType() == expectedMaterial) {
                // Blok terpasang dengan sukses, sekarang daftarkan generator
                boolean canPlace = plugin.getGeneratorManager().placeGenerator(player.getUniqueId(), location, generatorType);
                
                if (canPlace) {
                    // Generator berhasil didaftarkan
                    plugin.getMessageManager().send(player, "general.generator-placed");
                } else {
                    // Ada masalah dengan pendaftaran generator, kembalikan blok
                    location.getBlock().setType(Material.AIR);
                    player.getInventory().addItem(item);
                    plugin.getMessageManager().send(player, "general.generator-place-failed");
                }
            }
            // Jika blok tidak terpasang, berarti event dibatalkan oleh plugin proteksi
            // Tidak perlu melakukan apa-apa karena item tetap di inventory player
        });
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
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
        
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking() && player.hasPermission("blockmint.upgrade")) {
                upgradeGenerator(player, generator);
            } else {
                collectGenerator(player, generator);
            }
        } else if (action == Action.LEFT_CLICK_BLOCK) {
            showGeneratorInfo(player, generator);
        }
    }
    
    private void attemptPickupGenerator(Player player, Generator generator, Location location, Block block) {
        UUID playerUUID = player.getUniqueId();
        
        if (!generator.getOwner().equals(playerUUID) && 
            !player.hasPermission("blockmint.admin.remove")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }
        
        long now = System.currentTimeMillis();
        Long lastAttempt = lastPickupAttempt.get(playerUUID);
        
        if (lastAttempt != null && now - lastAttempt < CONFIRMATION_DELAY_MS) {
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
                invalidateGeneratorCountCache(playerUUID);
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
    
    private int getPlayerGeneratorCount(UUID playerUUID) {
        long now = System.currentTimeMillis();
        
        cacheLock.readLock().lock();
        try {
            if (generatorCountCache.containsKey(playerUUID)) {
                Long expiry = generatorCountCacheExpiry.get(playerUUID);
                if (expiry != null && now < expiry) {
                    return generatorCountCache.get(playerUUID);
                }
            }
        } finally {
            cacheLock.readLock().unlock();
        }
        
        int count = countPlayerGenerators(playerUUID);
        
        cacheLock.writeLock().lock();
        try {
            generatorCountCache.put(playerUUID, count);
            generatorCountCacheExpiry.put(playerUUID, now + COUNT_CACHE_DURATION_MS);
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        return count;
    }
    
    private void invalidateGeneratorCountCache(UUID playerUUID) {
        cacheLock.writeLock().lock();
        try {
            generatorCountCache.remove(playerUUID);
            generatorCountCacheExpiry.remove(playerUUID);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private int countPlayerGenerators(UUID playerUUID) {
        return (int) plugin.getGeneratorManager().getActiveGenerators().values().stream()
                .filter(generator -> generator.getOwner().equals(playerUUID))
                .count();
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
        
        // Track generator usage for evolution
        generator.incrementUsage(value);
        updateGeneratorUsageAsync(generator.getId(), generator.getUsageCount(), generator.getResourcesGenerated());
        
        // Check if generator is ready for evolution
        if (generator.isEvolutionReady()) {
            notifyEvolutionReady(player, generator);
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.format("%.2f", value));
        plugin.getMessageManager().send(player, "general.collect-success", placeholders);
        
        updatePlayerEarningsAsync(player.getUniqueId(), value);
        DisplayManager.updateHologram(plugin, generator);
    }
    
    private void updateGeneratorUsageAsync(final int generatorId, final int usageCount, final double resourcesGenerated) {
        pendingAsyncOperations.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                        "UPDATE generators SET usage_count = ?, resources_generated = ? WHERE id = ?"
                );
                stmt.setInt(1, usageCount);
                stmt.setDouble(2, resourcesGenerated);
                stmt.setInt(3, generatorId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not update generator usage in database: " + e.getMessage());
            } finally {
                pendingAsyncOperations.decrementAndGet();
            }
        });
    }
    
    private void notifyEvolutionReady(Player player, Generator generator) {
        GeneratorType nextType = generator.getEvolutionTarget();
        if (nextType == null) return;
        
        TextComponent message = Component.text("Your " + generator.getType().getName() + " Generator is ready to evolve into a " + 
                nextType.getName() + " Generator! ", NamedTextColor.GREEN)
                .append(Component.text("[Evolve Now]", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/blockmint evolve " + generator.getId()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to evolve your generator"))));
        
        plugin.getAdventure().player(player).sendMessage(message);
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
        
        updateGeneratorLevelAsync(generator.getId(), newLevel, player, generator);
    }
    
    private void updateGeneratorLevelAsync(final int generatorId, final int newLevel, final Player player, final Generator generator) {
        pendingAsyncOperations.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                        "UPDATE generators SET level = ? WHERE id = ?"
                );
                stmt.setInt(1, newLevel);
                stmt.setInt(2, generatorId);
                stmt.executeUpdate();
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("level", String.valueOf(newLevel));
                    plugin.getMessageManager().send(player, "general.upgrade-success", placeholders);
                    
                    DisplayManager.updateHologram(plugin, generator);
                    pendingAsyncOperations.decrementAndGet();
                });
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not update generator level in database: " + e.getMessage());
                pendingAsyncOperations.decrementAndGet();
            }
        });
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
    
    private void updatePlayerEarningsAsync(final UUID playerUUID, final double amount) {
        pendingAsyncOperations.incrementAndGet();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                        "UPDATE player_stats SET total_earnings = total_earnings + ? WHERE uuid = ?"
                );
                stmt.setDouble(1, amount);
                stmt.setString(2, playerUUID.toString());
                stmt.executeUpdate();
                
                // Log the transaction for economic analysis
                plugin.getEconomyManager().logTransaction(playerUUID, amount, "generator_collect");
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not update player earnings in database: " + e.getMessage());
            } finally {
                pendingAsyncOperations.decrementAndGet();
            }
        });
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
    
    public int getPendingAsyncOperations() {
        return pendingAsyncOperations.get();
    }
    
    public void clearCaches() {
        cacheLock.writeLock().lock();
        try {
            generatorCountCache.clear();
            generatorCountCacheExpiry.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
        
        lastPickupAttempt.clear();
    }
}