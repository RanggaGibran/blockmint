package id.rnggagib.blockmint.chunk;

import id.rnggagib.BlockMint;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkManager {
    private final BlockMint plugin;
    private final Map<String, Set<Location>> chunkGeneratorMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> chunkLoadStatus = new ConcurrentHashMap<>();
    private final AtomicInteger activeChunks = new AtomicInteger(0);
    private final int CHUNK_LOAD_BATCH_SIZE = 5;
    private final int CHUNK_UNLOAD_DELAY = 60; // In seconds
    
    public ChunkManager(BlockMint plugin) {
        this.plugin = plugin;
        startChunkMonitorTask();
    }
    
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    private String getChunkKey(Location location) {
        return location.getWorld().getName() + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4);
    }
    
    public void registerGenerator(Location location) {
        String chunkKey = getChunkKey(location);
        chunkGeneratorMap.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(location);
    }
    
    public void unregisterGenerator(Location location) {
        String chunkKey = getChunkKey(location);
        Set<Location> generators = chunkGeneratorMap.get(chunkKey);
        if (generators != null) {
            generators.remove(location);
            if (generators.isEmpty()) {
                chunkGeneratorMap.remove(chunkKey);
            }
        }
    }
    
    public void handleChunkLoad(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        chunkLoadStatus.put(chunkKey, true);
        activeChunks.incrementAndGet();
        
        Set<Location> generators = chunkGeneratorMap.get(chunkKey);
        if (generators != null && !generators.isEmpty()) {
            plugin.getGeneratorManager().handleChunkLoad(chunk);
        }
    }
    
    public void handleChunkUnload(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        chunkLoadStatus.put(chunkKey, false);
        activeChunks.decrementAndGet();
        
        Set<Location> generators = chunkGeneratorMap.get(chunkKey);
        if (generators != null && !generators.isEmpty()) {
            plugin.getGeneratorManager().handleChunkUnload(chunk);
        }
    }
    
    public boolean isChunkLoaded(Location location) {
        String chunkKey = getChunkKey(location);
        Boolean status = chunkLoadStatus.get(chunkKey);
        if (status != null) {
            return status;
        }
        
        Chunk chunk = location.getChunk();
        boolean isLoaded = chunk.isLoaded();
        chunkLoadStatus.put(chunkKey, isLoaded);
        return isLoaded;
    }
    
    public int getActiveChunkCount() {
        return activeChunks.get();
    }
    
    public Set<Location> getGeneratorsInChunk(Chunk chunk) {
        String chunkKey = getChunkKey(chunk);
        return chunkGeneratorMap.getOrDefault(chunkKey, new HashSet<>());
    }
    
    private void startChunkMonitorTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int totalChunks = chunkGeneratorMap.size();
                int loaded = 0;
                
                for (String chunkKey : chunkGeneratorMap.keySet()) {
                    Boolean status = chunkLoadStatus.get(chunkKey);
                    if (status != null && status) {
                        loaded++;
                    }
                }
                
                plugin.getLogger().fine("Generator chunks: " + loaded + "/" + totalChunks + " loaded");
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60); // Run every minute
    }
    
    public void prioritizeChunksAroundPlayers() {
        Map<String, Integer> chunkPriority = new HashMap<>();
        
        // Prioritize chunks near players
        for (World world : plugin.getServer().getWorlds()) {
            for (org.bukkit.entity.Player player : world.getPlayers()) {
                Location loc = player.getLocation();
                
                // Prioritize the 5x5 chunk area around the player
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        int chunkX = (loc.getBlockX() >> 4) + x;
                        int chunkZ = (loc.getBlockZ() >> 4) + z;
                        String key = world.getName() + ":" + chunkX + ":" + chunkZ;
                        
                        // Calculate priority - center chunks have higher priority
                        int priority = 5 - (Math.abs(x) + Math.abs(z));
                        chunkPriority.put(key, Math.max(chunkPriority.getOrDefault(key, 0), priority));
                    }
                }
            }
        }
        
        // Load high-priority chunks with generators
        new BukkitRunnable() {
            @Override
            public void run() {
                int loaded = 0;
                
                for (Map.Entry<String, Integer> entry : chunkPriority.entrySet()) {
                    if (loaded >= CHUNK_LOAD_BATCH_SIZE) break;
                    
                    String key = entry.getKey();
                    int priority = entry.getValue();
                    
                    if (priority >= 3 && chunkGeneratorMap.containsKey(key)) {
                        Boolean status = chunkLoadStatus.get(key);
                        if (status == null || !status) {
                            String[] parts = key.split(":");
                            World world = plugin.getServer().getWorld(parts[0]);
                            if (world != null) {
                                int x = Integer.parseInt(parts[1]);
                                int z = Integer.parseInt(parts[2]);
                                
                                if (!world.isChunkLoaded(x, z)) {
                                    world.loadChunk(x, z, true);
                                    loaded++;
                                }
                            }
                        }
                    }
                }
            }
        }.runTask(plugin);
    }
}