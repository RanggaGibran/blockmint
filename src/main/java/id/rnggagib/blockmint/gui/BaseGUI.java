package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public abstract class BaseGUI {
    
    protected final BlockMint plugin;
    protected final Player player;
    protected Inventory inventory;
    
    public BaseGUI(BlockMint plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }
    
    public abstract void open();
    
    public abstract void handleClick(InventoryClickEvent event);
    
    public Inventory getInventory() {
        return inventory;
    }
    
    public Player getPlayer() {
        return player;
    }
}