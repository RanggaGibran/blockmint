package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.gui.BaseGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {
    
    private final BlockMint plugin;
    
    public GUIListener(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // Check if it's a BaseGUI
        BaseGUI gui = plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            event.setCancelled(true);
            gui.handleClick(event);
            return;
        }
        
        // Check if it's a NetworkGUI
        if (plugin.getNetworkGUIManager().isNetworkInventory(title)) {
            event.setCancelled(true);
            plugin.getNetworkGUIManager().handleInventoryClick(player, event.getRawSlot(), event.getInventory());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        BaseGUI gui = plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
        
        if (gui != null && event.getInventory().equals(gui.getInventory())) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        InventoryHolder holder = event.getDestination().getHolder();
        if (holder instanceof Player) {
            Player player = (Player) holder;
            BaseGUI gui = plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
            
            if (gui != null && (
                event.getDestination().equals(gui.getInventory()) || 
                event.getSource().equals(gui.getInventory()))) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
        plugin.getNetworkGUIManager().playerLogout(player.getUniqueId());
    }
}