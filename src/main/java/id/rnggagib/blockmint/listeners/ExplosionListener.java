package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.List;

public class ExplosionListener implements Listener {
    
    private final BlockMint plugin;
    
    public ExplosionListener(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectGenerators(event.blockList());
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectGenerators(event.blockList());
    }
    
    private void protectGenerators(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        int protectedCount = 0;
        
        while (iterator.hasNext()) {
            Block block = iterator.next();
            Location location = block.getLocation();
            
            Generator generator = plugin.getGeneratorManager().getGenerator(location);
            if (generator != null) {
                iterator.remove();
                protectedCount++;
            }
        }
        
        if (protectedCount > 0 && plugin.getConfigManager().getConfig().getBoolean("settings.debug.protection", false)) {
            plugin.getLogger().info("Protected " + protectedCount + " generators from explosion");
        }
    }
}