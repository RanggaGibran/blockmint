package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Map;

public class ChunkListeners implements Listener {
    
    private final BlockMint plugin;
    
    public ChunkListeners(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Map<Location, Generator> activeGenerators = plugin.getGeneratorManager().getActiveGenerators();
            
            for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
                Location location = entry.getKey();
                
                if (isLocationInChunk(location, chunk)) {
                    Generator generator = entry.getValue();
                    
                    // Ensure the block is correctly placed
                    if (location.getBlock().getType() != Material.valueOf(generator.getType().getMaterial())) {
                        location.getBlock().setType(Material.valueOf(generator.getType().getMaterial()));
                    }
                    
                    // Create or refresh hologram
                    DisplayManager.removeHologram(location);
                    if (plugin.getConfigManager().getConfig().getBoolean("settings.use-holograms", true)) {
                        DisplayManager.createHologram(plugin, location, generator.getType(), generator.getLevel());
                    }
                }
            }
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        
        Map<Location, Generator> activeGenerators = plugin.getGeneratorManager().getActiveGenerators();
        
        for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
            Location location = entry.getKey();
            
            if (isLocationInChunk(location, chunk)) {
                DisplayManager.removeHologram(location);
            }
        }
    }
    
    private boolean isLocationInChunk(Location location, Chunk chunk) {
        return location.getWorld().equals(chunk.getWorld()) 
                && location.getBlockX() >> 4 == chunk.getX() 
                && location.getBlockZ() >> 4 == chunk.getZ();
    }
}