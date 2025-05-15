package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListeners implements Listener {
    
    private final BlockMint plugin;
    
    public ChunkListeners(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.isFullyEnabled()) {
            return;
        }
        
        Chunk chunk = event.getChunk();
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getGeneratorManager().handleChunkLoad(chunk);
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.isFullyEnabled()) {
            return;
        }
        
        plugin.getGeneratorManager().handleChunkUnload(event.getChunk());
    }
}