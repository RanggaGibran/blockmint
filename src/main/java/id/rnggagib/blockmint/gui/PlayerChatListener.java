package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

public class PlayerChatListener implements Listener {
    
    private final BlockMint plugin;
    private final Player player;
    private final Consumer<String> callback;
    
    public PlayerChatListener(BlockMint plugin, Player player, Consumer<String> callback) {
        this.plugin = plugin;
        this.player = player;
        this.callback = callback;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) {
            return;
        }
        
        event.setCancelled(true);
        String input = event.getMessage();
        
        // Unregister listener to avoid memory leaks
        HandlerList.unregisterAll(this);
        
        // Execute callback in main thread for safety
        plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(input));
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().equals(player)) {
            HandlerList.unregisterAll(this);
        }
    }
}