package id.rnggagib.blockmint.tasks;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import org.bukkit.Chunk;

public class GeneratorTask extends BukkitRunnable {

    private final BlockMint plugin;
    private final boolean autoCollect;
    private final boolean showParticles;
    private final boolean playSound;
    private final double collectionRange;
    private final Map<Location, Long> lastParticleEffect = new HashMap<>();
    private final long particleInterval = 2000;
    
    // Performance optimization fields
    private Map<String, List<Location>> regionMap = new ConcurrentHashMap<>();
    private Iterator<String> regionIterator;
    private int batchSize = 5;
    private int processingTick = 0;
    private final int SERVER_TPS_CHECK_INTERVAL = 20; // Check TPS every 20 ticks
    private long lastPerformanceAdjust = 0;
    private static final long PERFORMANCE_ADJUST_INTERVAL = 60000; // 1 minute

    public GeneratorTask(BlockMint plugin) {
        this.plugin = plugin;
        this.autoCollect = plugin.getConfigManager().getConfig().getBoolean("settings.auto-collect", false);
        this.showParticles = plugin.getConfigManager().getConfig().getBoolean("settings.visual-effects.show-particles", true);
        this.playSound = plugin.getConfigManager().getConfig().getBoolean("settings.visual-effects.play-sounds", true);
        this.collectionRange = plugin.getConfigManager().getConfig().getDouble("settings.auto-collect-range", 10.0);
        
        initializeRegionMap();
    }
    
    private void initializeRegionMap() {
        plugin.getLogger().info("Initializing generator regions for optimized processing...");
        Map<Location, Generator> generators = plugin.getGeneratorManager().getActiveGenerators();
        
        for (Map.Entry<Location, Generator> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            String regionKey = getRegionKey(loc);
            
            regionMap.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(loc);
        }
        
        plugin.getLogger().info("Initialized " + regionMap.size() + " generator regions");
        regionIterator = regionMap.keySet().iterator();
    }
    
    private String getRegionKey(Location location) {
        return location.getWorld().getName() + ":" + (location.getBlockX() >> 4) / 3 + ":" + (location.getBlockZ() >> 4) / 3;
    }
    
    public void addGeneratorToRegion(Location location) {
        String regionKey = getRegionKey(location);
        regionMap.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(location);
    }
    
    public void removeGeneratorFromRegion(Location location) {
        String regionKey = getRegionKey(location);
        List<Location> region = regionMap.get(regionKey);
        if (region != null) {
            region.remove(location);
            if (region.isEmpty()) {
                regionMap.remove(regionKey);
                regionIterator = regionMap.keySet().iterator(); // Reset iterator
            }
        }
    }

    @Override
    public void run() {
        processingTick++;
        
        if (processingTick % SERVER_TPS_CHECK_INTERVAL == 0) {
            adjustBatchSizeBasedOnTPS();
        }
        
        if (System.currentTimeMillis() - lastPerformanceAdjust > PERFORMANCE_ADJUST_INTERVAL) {
            redistributeRegions();
            lastPerformanceAdjust = System.currentTimeMillis();
        }
        
        processGeneratorBatch();
        
        // Process network auto-collection every 5 ticks (approximately 0.25s)
        if (processingTick % 5 == 0) {
            plugin.getNetworkManager().processNetworkAutoCollection();
        }
    }
    
    private void processGeneratorBatch() {
        if (!regionIterator.hasNext()) {
            regionIterator = regionMap.keySet().iterator();
            if (!regionIterator.hasNext()) return; // No regions
        }
        
        int processedRegions = 0;
        while (regionIterator.hasNext() && processedRegions < batchSize) {
            String regionKey = regionIterator.next();
            List<Location> locations = regionMap.get(regionKey);
            
            if (locations == null || locations.isEmpty()) continue;
            
            boolean shouldProcess = false;
            Location checkLoc = locations.get(0);
            
            if (checkLoc.getWorld() != null && 
                checkLoc.getWorld().isChunkLoaded(checkLoc.getBlockX() >> 4, checkLoc.getBlockZ() >> 4)) {
                shouldProcess = true;
                
                // Check if any players are nearby to prioritize processing
                for (Player player : checkLoc.getWorld().getPlayers()) {
                    if (player.getLocation().distance(checkLoc) <= 100) {
                        shouldProcess = true;
                        break;
                    }
                }
            }
            
            if (shouldProcess) {
                for (Location location : new ArrayList<>(locations)) {
                    processGenerator(location);
                }
            }
            
            processedRegions++;
        }
    }
    
    private void processGenerator(Location location) {
        if (location.getWorld() == null || 
            !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        
        Generator generator = plugin.getGeneratorManager().getActiveGenerators().get(location);
        if (generator == null) return;
        
        if (generator.canGenerate()) {
            if (autoCollect) {
                handleAutoCollect(location, generator);
            } else {
                showReadyEffects(location, generator);
            }
        }
        
        DisplayManager.updateHologram(plugin, generator);
    }
    
    private void adjustBatchSizeBasedOnTPS() {
        // Bukkit/Spigot API does not provide getTPS() directly; set to 20.0 or use a TPS utility if available
        double tps = 20.0; // Assume perfect TPS, or replace with actual TPS retrieval if available
        
        if (tps >= 19.5) {
            // Server is running smoothly, increase batch size
            batchSize = Math.min(batchSize + 1, 20);
        } else if (tps < 17) {
            // Server is struggling, reduce batch size
            batchSize = Math.max(1, batchSize - 1);
        }
    }
    
    private void redistributeRegions() {
        plugin.getLogger().fine("Redistributing generator regions for balanced processing...");
        
        // Rebuild the region map to account for newly placed or removed generators
        Map<String, List<Location>> newRegionMap = new ConcurrentHashMap<>();
        Map<Location, Generator> generators = plugin.getGeneratorManager().getActiveGenerators();
        
        for (Map.Entry<Location, Generator> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            String regionKey = getRegionKey(loc);
            
            newRegionMap.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(loc);
        }
        
        // Balance regions if they're too large
        Map<String, List<Location>> balancedMap = new ConcurrentHashMap<>();
        int maxPerRegion = 50; // Maximum generators per region
        
        for (Map.Entry<String, List<Location>> entry : newRegionMap.entrySet()) {
            String key = entry.getKey();
            List<Location> locations = entry.getValue();
            
            if (locations.size() <= maxPerRegion) {
                balancedMap.put(key, locations);
            } else {
                // Split this region into smaller ones
                int subRegionCount = (locations.size() / maxPerRegion) + 1;
                for (int i = 0; i < locations.size(); i++) {
                    String subKey = key + ":" + (i % subRegionCount);
                    balancedMap.computeIfAbsent(subKey, k -> new ArrayList<>()).add(locations.get(i));
                }
            }
        }
        
        regionMap = balancedMap;
        regionIterator = regionMap.keySet().iterator();
        plugin.getLogger().fine("Region redistribution complete: " + regionMap.size() + " regions");
    }

    private void handleAutoCollect(Location location, Generator generator) {
        UUID ownerUUID = generator.getOwner();
        Player owner = plugin.getServer().getPlayer(ownerUUID);
        
        if (owner != null && owner.isOnline() && owner.getLocation().getWorld() == location.getWorld() 
                && owner.getLocation().distance(location) <= collectionRange) {
            
            double value = generator.getValue();
            plugin.getEconomy().depositPlayer(owner, value);
            
            generator.setLastGeneration(System.currentTimeMillis());
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.format("%.2f", value));
            plugin.getMessageManager().send(owner, "general.auto-collect-success", placeholders);
            
            updatePlayerEarningsBatched(ownerUUID, value);
            
            if (playSound) {
                owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
            
            if (showParticles) {
                spawnCollectionParticles(location);
            }
        } else {
            showReadyEffects(location, generator);
        }
    }

    private void showReadyEffects(Location location, Generator generator) {
        long now = System.currentTimeMillis();
        Long lastEffect = lastParticleEffect.get(location);
        
        if (lastEffect == null || (now - lastEffect) > particleInterval) {
            if (showParticles) {
                spawnReadyParticles(location);
            }
            
            if (playSound) {
                location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
            }
            
            lastParticleEffect.put(location, now);
        }
    }

    private void spawnReadyParticles(Location location) {
        Location particleLoc = location.clone().add(0.5, 1.2, 0.5);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0f);
        
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double x = Math.cos(angle) * 0.5;
            double z = Math.sin(angle) * 0.5;
            
            location.getWorld().spawnParticle(
                    Particle.REDSTONE, 
                    particleLoc.clone().add(x, 0, z), 
                    2, 
                    0.05, 
                    0.05, 
                    0.05, 
                    0, 
                    dustOptions);
        }
    }

    private void spawnCollectionParticles(Location location) {
        Location particleLoc = location.clone().add(0.5, 1.0, 0.5);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f);
        
        location.getWorld().spawnParticle(
                Particle.REDSTONE, 
                particleLoc, 
                15, 
                0.3, 
                0.3, 
                0.3, 
                0, 
                dustOptions);
    }

    private void updatePlayerEarningsBatched(UUID playerUUID, double amount) {
        plugin.getDatabaseManager().addBatchOperation(
            playerUUID, 
            "UPDATE player_stats SET total_earnings = total_earnings + ? WHERE uuid = ?",
            new Object[]{amount, playerUUID.toString()}
        );
    }
    
    public void refreshRegions() {
        initializeRegionMap();
    }
}