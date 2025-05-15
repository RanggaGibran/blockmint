package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Map;

public class ChunkListeners implements Listener {

    private final BlockMint plugin;
    
    public ChunkListeners(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<Location, Generator> entry : plugin.getGeneratorManager().getActiveGenerators().entrySet()) {
                Location location = entry.getKey();
                
                if (location.getWorld().equals(chunk.getWorld()) && 
                    location.getBlockX() >> 4 == chunk.getX() && 
                    location.getBlockZ() >> 4 == chunk.getZ()) {
                    
                    Generator generator = entry.getValue();
                    DisplayManager.createHologram(plugin, location, generator.getType(), generator.getLevel());
                }
            }
        }, 20L);
    }
}