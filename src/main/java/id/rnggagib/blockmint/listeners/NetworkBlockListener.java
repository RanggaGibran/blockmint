package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.network.NetworkBlock;
import id.rnggagib.blockmint.network.NetworkTier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetworkBlockListener implements Listener {
    
    private final BlockMint plugin;
    private final Map<UUID, Long> lastPickupAttempt = new HashMap<>();
    private final long CONFIRMATION_DELAY_MS = 3000;
    
    public NetworkBlockListener(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        
        NetworkTier tier = NetworkBlock.getTierFromItem(item);
        if (tier == null) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!player.hasPermission("blockmint.network.create")) {
            plugin.getMessageManager().send(player, "network.no-permission");
            event.setCancelled(true);
            return;
        }
        
        String networkName = NetworkBlock.getNameFromItem(item);
        if (networkName == null) {
            networkName = "Network " + (plugin.getNetworkManager().getPlayerNetworks(player.getUniqueId()).size() + 1);
        }
        
        NetworkBlock networkBlock = plugin.getNetworkManager().createNetwork(
            player.getUniqueId(), 
            networkName, 
            tier, 
            event.getBlock().getLocation()
        );
        
        if (networkBlock != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", networkName);
            placeholders.put("tier", tier.getDisplayName());
            plugin.getMessageManager().send(player, "network.created", placeholders);
        } else {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "network.create-failed");
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        NetworkBlock networkBlock = plugin.getNetworkManager().getNetworkAt(block.getLocation());
        
        if (networkBlock == null) {
            return;
        }
        
        Player player = event.getPlayer();
        if (!networkBlock.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin.network.break")) {
            plugin.getMessageManager().send(player, "network.no-permission");
            event.setCancelled(true);
            return;
        }
        
        event.setCancelled(true);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onNetworkBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Block block = event.getClickedBlock();
        NetworkBlock networkBlock = plugin.getNetworkManager().getNetworkAt(block.getLocation());
        
        if (networkBlock == null) {
            return;
        }
        
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        
        if (event.getAction().name().contains("LEFT_CLICK") && player.isSneaking()) {
            attemptPickupNetworkBlock(player, networkBlock, block);
            return;
        }
        
        if (!networkBlock.getOwner().equals(player.getUniqueId()) && !player.hasPermission("blockmint.admin.network.manage")) {
            plugin.getMessageManager().send(player, "network.no-permission");
            return;
        }
        
        plugin.getNetworkGUIManager().openNetworkManagementGUI(player, networkBlock);
    }
    
    private void attemptPickupNetworkBlock(Player player, NetworkBlock networkBlock, Block block) {
        if (!networkBlock.getOwner().equals(player.getUniqueId()) && 
            !player.hasPermission("blockmint.admin.network.break")) {
            plugin.getMessageManager().send(player, "network.no-permission");
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        if (lastPickupAttempt.containsKey(playerUUID) && 
            now - lastPickupAttempt.get(playerUUID) < CONFIRMATION_DELAY_MS) {
            
            boolean success = plugin.getNetworkManager().deleteNetwork(networkBlock.getNetworkId());
            
            if (success) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("name", networkBlock.getName());
                plugin.getMessageManager().send(player, "network.removed", placeholders);
                
                ItemStack networkItem = NetworkBlock.createNetworkBlockItem(networkBlock.getTier(), networkBlock.getName());
                
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(networkItem);
                if (!overflow.isEmpty()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), networkItem);
                    plugin.getMessageManager().send(player, "network.collected-inventory-full");
                } else {
                    plugin.getMessageManager().send(player, "network.collected");
                }
                
                block.setType(Material.AIR);
                lastPickupAttempt.remove(playerUUID);
            } else {
                plugin.getMessageManager().send(player, "network.remove-failed");
            }
        } else {
            lastPickupAttempt.put(playerUUID, now);
            plugin.getMessageManager().send(player, "network.collect-confirm");
        }
    }
}