package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.network.NetworkBlock;
import id.rnggagib.blockmint.network.permissions.NetworkMember;
import id.rnggagib.blockmint.network.permissions.NetworkPermission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetworkPermissionsGUI extends BaseGUI {
    
    private final NetworkBlock network;
    private final Map<Integer, NetworkMember> memberSlots = new HashMap<>();
    private final Map<Integer, NetworkPermission> permissionSlots = new HashMap<>();
    private NetworkMember selectedMember;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    public NetworkPermissionsGUI(BlockMint plugin, Player player, NetworkBlock network) {
        super(plugin, player);
        this.network = network;
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Network Permissions: " + ChatColor.AQUA + network.getName());
        
        showMembersList();
        
        player.openInventory(inventory);
    }
    
    private void showMembersList() {
        clearInventory();
        
        memberSlots.clear();
        
        ItemStack infoItem = createInfoItem();
        inventory.setItem(4, infoItem);
        
        List<NetworkMember> members = plugin.getNetworkManager().getPermissionManager().getNetworkMembers(network.getNetworkId());
        
        int slot = 9;
        for (NetworkMember member : members) {
            inventory.setItem(slot, createMemberItem(member));
            memberSlots.put(slot, member);
            slot++;
            
            if (slot > 44) {
                break;
            }
        }
        
        // Show add member button if there's room
        if (slot <= 44) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to add a new member");
            lore.add(ChatColor.GRAY + "to your network");
            
            inventory.setItem(slot, GUIManager.createItem(Material.LIME_DYE, ChatColor.GREEN + "Add Member", lore));
        }
        
        // Back button
        inventory.setItem(45, GUIManager.createItem(Material.ARROW, ChatColor.YELLOW + "Back to Network Menu", null));
    }
    
    private void showMemberPermissions(NetworkMember member) {
        clearInventory();
        selectedMember = member;
        permissionSlots.clear();
        
        // Member info at top
        inventory.setItem(4, createDetailedMemberItem(member));
        
        // Available permissions
        List<NetworkPermission> permissions = new ArrayList<>();
        permissions.add(NetworkPermission.VIEW);
        permissions.add(NetworkPermission.USE);
        permissions.add(NetworkPermission.MANAGE);
        permissions.add(NetworkPermission.ADMIN);
        
        int slot = 20;
        for (NetworkPermission permission : permissions) {
            inventory.setItem(slot, createPermissionItem(permission, member.getPermission() == permission));
            permissionSlots.put(slot, permission);
            slot += 2;
        }
        
        // Remove member button
        List<String> removeLore = new ArrayList<>();
        removeLore.add(ChatColor.GRAY + "Click to remove this member");
        removeLore.add(ChatColor.GRAY + "from your network");
        removeLore.add("");
        removeLore.add(ChatColor.RED + "This action cannot be undone!");
        
        inventory.setItem(40, GUIManager.createItem(Material.BARRIER, ChatColor.RED + "Remove Member", removeLore));
        
        if (!member.getPlayerUuid().equals(player.getUniqueId())) {
            // Transfer ownership button
            List<String> transferLore = new ArrayList<>();
            transferLore.add(ChatColor.GRAY + "Transfer network ownership");
            transferLore.add(ChatColor.GRAY + "to this player");
            transferLore.add("");
            transferLore.add(ChatColor.RED + "This will make them the owner!");
            transferLore.add(ChatColor.RED + "You'll become an admin instead.");
            
            inventory.setItem(42, GUIManager.createItem(Material.GOLDEN_HELMET, ChatColor.GOLD + "Transfer Ownership", transferLore));
        }
        
        // Back button to members list
        inventory.setItem(45, GUIManager.createItem(Material.ARROW, ChatColor.YELLOW + "Back to Members List", null));
    }
    
    private ItemStack createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Network ID: " + ChatColor.WHITE + network.getNetworkId());
        lore.add(ChatColor.GRAY + "Owner: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(network.getOwner()).getName());
        lore.add("");
        
        int memberCount = plugin.getNetworkManager().getPermissionManager().getMemberCount(network.getNetworkId());
        lore.add(ChatColor.GRAY + "Members: " + ChatColor.WHITE + memberCount);
        
        return GUIManager.createItem(Material.WRITABLE_BOOK, ChatColor.AQUA + "Network Permissions", lore);
    }
    
    private ItemStack createMemberItem(NetworkMember member) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Role: " + getColorForPermission(member.getPermission()) + member.getPermission().getDisplayName());
        lore.add("");
        lore.add(ChatColor.GRAY + "Joined: " + ChatColor.WHITE + dateFormat.format(new Date(member.getJoinedTime())));
        lore.add(ChatColor.GRAY + "Last access: " + ChatColor.WHITE + dateFormat.format(new Date(member.getLastAccess())));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to manage permissions");
        
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + member.getPlayerName());
        meta.setLore(lore);
        
        Player targetPlayer = Bukkit.getPlayer(member.getPlayerUuid());
        if (targetPlayer != null) {
            meta.setOwningPlayer(targetPlayer);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createDetailedMemberItem(NetworkMember member) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Current Role: " + getColorForPermission(member.getPermission()) + member.getPermission().getDisplayName());
        lore.add("");
        lore.add(ChatColor.GRAY + "Permissions:");
        lore.add(getPermissionStatus(member, NetworkPermission.VIEW));
        lore.add(getPermissionStatus(member, NetworkPermission.USE));
        lore.add(getPermissionStatus(member, NetworkPermission.MANAGE));
        lore.add(getPermissionStatus(member, NetworkPermission.ADMIN));
        lore.add("");
        lore.add(ChatColor.GRAY + "Joined: " + ChatColor.WHITE + dateFormat.format(new Date(member.getJoinedTime())));
        lore.add(ChatColor.GRAY + "Last access: " + ChatColor.WHITE + dateFormat.format(new Date(member.getLastAccess())));
        
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + member.getPlayerName());
        meta.setLore(lore);
        
        Player targetPlayer = Bukkit.getPlayer(member.getPlayerUuid());
        if (targetPlayer != null) {
            meta.setOwningPlayer(targetPlayer);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    private String getPermissionStatus(NetworkMember member, NetworkPermission permission) {
        boolean hasPermission = member.hasPermission(permission);
        String status = hasPermission ? "✓" : "✗";
        String color = hasPermission ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
        return color + status + " " + ChatColor.GRAY + permission.getDisplayName() + ": " + permission.getDescription();
    }
    
    private ItemStack createPermissionItem(NetworkPermission permission, boolean isSelected) {
        Material material;
        if (isSelected) {
            material = Material.LIME_WOOL;
        } else {
            material = Material.WHITE_WOOL;
        }
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + permission.getDescription());
        lore.add("");
        if (isSelected) {
            lore.add(ChatColor.GREEN + "Currently selected");
        } else {
            lore.add(ChatColor.YELLOW + "Click to select");
        }
        
        return GUIManager.createItem(material, getColorForPermission(permission) + permission.getDisplayName(), lore);
    }
    
    private ChatColor getColorForPermission(NetworkPermission permission) {
        switch (permission) {
            case NONE:
                return ChatColor.GRAY;
            case VIEW:
                return ChatColor.GREEN;
            case USE:
                return ChatColor.BLUE;
            case MANAGE:
                return ChatColor.GOLD;
            case ADMIN:
                return ChatColor.RED;
            case OWNER:
                return ChatColor.DARK_RED;
            default:
                return ChatColor.WHITE;
        }
    }
    
    private void clearInventory() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }
        
        // Set background
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, GUIManager.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
            }
        }
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        
        // Handle back button
        if (slot == 45) {
            if (selectedMember != null) {
                selectedMember = null;
                showMembersList();
            } else {
                openNetworkMainMenu();
            }
            return;
        }
        
        // If we're showing the members list
        if (selectedMember == null) {
            if (memberSlots.containsKey(slot)) {
                NetworkMember member = memberSlots.get(slot);
                showMemberPermissions(member);
            } else if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.LIME_DYE) {
                // Add member button
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Type the name of the player you want to add:");
                
                // Register a conversation listener to get the player's response
                // This would be better with a proper conversation API, but for simplicity:
                plugin.getServer().getPluginManager().registerEvents(new PlayerChatListener(plugin, player, input -> {
                    Player targetPlayer = Bukkit.getPlayer(input);
                    if (targetPlayer == null) {
                        player.sendMessage(ChatColor.RED + "Player not found. Please try again.");
                        return;
                    }
                    
                    UUID targetUuid = targetPlayer.getUniqueId();
                    
                    // Don't add the owner again
                    if (network.getOwner().equals(targetUuid)) {
                        player.sendMessage(ChatColor.RED + "That player is already the owner of this network.");
                        return;
                    }
                    
                    // Check if player is already a member
                    NetworkPermission currentPermission = plugin.getNetworkManager().getPermissionManager()
                        .getPlayerPermission(network.getNetworkId(), targetUuid);
                    
                    if (currentPermission != NetworkPermission.NONE) {
                        player.sendMessage(ChatColor.RED + "That player is already a member of this network.");
                        return;
                    }
                    
                    boolean added = plugin.getNetworkManager().getPermissionManager()
                        .addMember(network.getNetworkId(), targetUuid, targetPlayer.getName(), NetworkPermission.VIEW);
                    
                    if (added) {
                        player.sendMessage(ChatColor.GREEN + "Added " + targetPlayer.getName() + " to your network with VIEW permission.");
                        
                        // Notify the added player
                        targetPlayer.sendMessage(ChatColor.GREEN + "You have been added to " + player.getName() + "'s network: " + 
                            ChatColor.AQUA + network.getName() + ChatColor.GREEN + " with VIEW permission.");
                        
                        // Reopen the GUI
                        NetworkPermissionsGUI gui = new NetworkPermissionsGUI(plugin, player, network);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            gui.open();
                            plugin.getGUIManager().registerActiveGUI(player.getUniqueId().toString(), gui);
                        });
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to add player to the network.");
                    }
                }), plugin);
            }
        } else {
            // We're showing a specific member's permissions
            if (permissionSlots.containsKey(slot)) {
                NetworkPermission selectedPermission = permissionSlots.get(slot);
                
                if (selectedMember.getPermission() != selectedPermission) {
                    boolean updated = plugin.getNetworkManager().getPermissionManager()
                        .updateMemberPermission(network.getNetworkId(), selectedMember.getPlayerUuid(), selectedPermission);
                    
                    if (updated) {
                        player.sendMessage(ChatColor.GREEN + "Updated " + selectedMember.getPlayerName() + "'s permission to " + 
                            selectedPermission.getDisplayName() + ".");
                        
                        // Notify the affected player if they're online
                        Player targetPlayer = Bukkit.getPlayer(selectedMember.getPlayerUuid());
                        if (targetPlayer != null) {
                            targetPlayer.sendMessage(ChatColor.YELLOW + "Your permission for network " + ChatColor.AQUA + 
                                network.getName() + ChatColor.YELLOW + " has been updated to " + ChatColor.GREEN + 
                                selectedPermission.getDisplayName() + ChatColor.YELLOW + ".");
                        }
                        
                        // Update the member object
                        selectedMember.setPermission(selectedPermission);
                        
                        // Refresh the GUI
                        showMemberPermissions(selectedMember);
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to update permission.");
                    }
                }
            } else if (slot == 40) {
                // Remove member button
                UUID memberUuid = selectedMember.getPlayerUuid();
                String memberName = selectedMember.getPlayerName();
                
                boolean removed = plugin.getNetworkManager().getPermissionManager()
                    .removeMember(network.getNetworkId(), memberUuid);
                
                if (removed) {
                    player.sendMessage(ChatColor.GREEN + "Removed " + memberName + " from your network.");
                    
                    // Notify the removed player if they're online
                    Player targetPlayer = Bukkit.getPlayer(memberUuid);
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(ChatColor.RED + "You have been removed from the network: " + 
                            ChatColor.AQUA + network.getName() + ChatColor.RED + ".");
                    }
                    
                    // Go back to members list
                    selectedMember = null;
                    showMembersList();
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to remove member from the network.");
                }
            } else if (slot == 42 && !selectedMember.getPlayerUuid().equals(player.getUniqueId())) {
                // Transfer ownership button
                UUID newOwnerUuid = selectedMember.getPlayerUuid();
                String newOwnerName = selectedMember.getPlayerName();
                
                boolean transferred = plugin.getNetworkManager().getPermissionManager()
                    .transferOwnership(network.getNetworkId(), newOwnerUuid, newOwnerName);
                
                if (transferred) {
                    player.sendMessage(ChatColor.GREEN + "Transferred ownership of network " + 
                        network.getName() + " to " + newOwnerName + ".");
                    
                    // Notify the new owner if they're online
                    Player newOwner = Bukkit.getPlayer(newOwnerUuid);
                    if (newOwner != null) {
                        newOwner.sendMessage(ChatColor.GREEN + "You are now the owner of network: " + 
                            ChatColor.AQUA + network.getName() + ChatColor.GREEN + "!");
                    }
                    
                    // Go back to members list
                    selectedMember = null;
                    showMembersList();
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to transfer ownership.");
                }
            }
        }
    }
    
    private void openNetworkMainMenu() {
        player.closeInventory();
        plugin.getNetworkGUIManager().openNetworkManagementGUI(player, network);
    }
}