package id.rnggagib.blockmint.network.permissions;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.network.NetworkBlock;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NetworkPermissionManager {
    
    private final BlockMint plugin;
    private final Map<Integer, Map<UUID, NetworkMember>> networkMembers = new ConcurrentHashMap<>();
    
    public NetworkPermissionManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        createPermissionsTable();
        loadAllPermissions();
    }
    
    private void createPermissionsTable() {
        try {
            plugin.getDatabaseManager().executeUpdate(
                "CREATE TABLE IF NOT EXISTS network_permissions (" +
                "network_id INTEGER NOT NULL, " +
                "player_uuid TEXT NOT NULL, " +
                "player_name TEXT NOT NULL, " +
                "permission_level INTEGER NOT NULL DEFAULT 0, " +
                "joined_time BIGINT NOT NULL, " +
                "last_access BIGINT NOT NULL, " +
                "PRIMARY KEY (network_id, player_uuid), " +
                "FOREIGN KEY (network_id) REFERENCES networks(id) ON DELETE CASCADE" +
                ")"
            );
            
            plugin.getLogger().info("Network permissions table initialized");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating network permissions table", e);
        }
    }
    
    private void loadAllPermissions() {
        networkMembers.clear();
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                "SELECT network_id, player_uuid, player_name, permission_level, joined_time, last_access FROM network_permissions"
            );
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int networkId = rs.getInt("network_id");
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                String playerName = rs.getString("player_name");
                int permLevel = rs.getInt("permission_level");
                long joinedTime = rs.getLong("joined_time");
                long lastAccess = rs.getLong("last_access");
                
                NetworkPermission permission = NetworkPermission.fromLevel(permLevel);
                NetworkMember member = new NetworkMember(playerUuid, playerName, permission);
                member.setJoinedTime(joinedTime);
                member.setLastAccess(lastAccess);
                
                networkMembers.computeIfAbsent(networkId, k -> new ConcurrentHashMap<>()).put(playerUuid, member);
            }
            
            plugin.getLogger().info("Loaded network permissions for " + networkMembers.size() + " networks");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading network permissions", e);
        }
    }
    
    public boolean addMember(int networkId, UUID playerUuid, String playerName, NetworkPermission permission) {
        if (permission == NetworkPermission.OWNER) {
            plugin.getLogger().warning("Cannot add member with OWNER permission. Use transferOwnership instead.");
            return false;
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                "INSERT OR REPLACE INTO network_permissions (network_id, player_uuid, player_name, permission_level, joined_time, last_access) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
            );
            
            long currentTime = System.currentTimeMillis();
            
            stmt.setInt(1, networkId);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, playerName);
            stmt.setInt(4, permission.getLevel());
            stmt.setLong(5, currentTime);
            stmt.setLong(6, currentTime);
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                NetworkMember member = new NetworkMember(playerUuid, playerName, permission);
                networkMembers.computeIfAbsent(networkId, k -> new ConcurrentHashMap<>()).put(playerUuid, member);
                return true;
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error adding network member", e);
        }
        
        return false;
    }
    
    public boolean removeMember(int networkId, UUID playerUuid) {
        NetworkBlock network = plugin.getNetworkManager().getNetworks().get(networkId);
        if (network == null) {
            return false;
        }
        
        if (network.getOwner().equals(playerUuid)) {
            plugin.getLogger().warning("Cannot remove the network owner. Use transferOwnership instead.");
            return false;
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                "DELETE FROM network_permissions WHERE network_id = ? AND player_uuid = ?"
            );
            
            stmt.setInt(1, networkId);
            stmt.setString(2, playerUuid.toString());
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                Map<UUID, NetworkMember> members = networkMembers.get(networkId);
                if (members != null) {
                    members.remove(playerUuid);
                }
                return true;
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing network member", e);
        }
        
        return false;
    }
    
    public boolean updateMemberPermission(int networkId, UUID playerUuid, NetworkPermission permission) {
        if (permission == NetworkPermission.OWNER) {
            plugin.getLogger().warning("Cannot update to OWNER permission. Use transferOwnership instead.");
            return false;
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                "UPDATE network_permissions SET permission_level = ? WHERE network_id = ? AND player_uuid = ?"
            );
            
            stmt.setInt(1, permission.getLevel());
            stmt.setInt(2, networkId);
            stmt.setString(3, playerUuid.toString());
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                Map<UUID, NetworkMember> members = networkMembers.get(networkId);
                if (members != null && members.containsKey(playerUuid)) {
                    members.get(playerUuid).setPermission(permission);
                }
                return true;
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating member permission", e);
        }
        
        return false;
    }
    
    public boolean transferOwnership(int networkId, UUID newOwnerUuid, String newOwnerName) {
        NetworkBlock network = plugin.getNetworkManager().getNetworks().get(networkId);
        if (network == null) {
            return false;
        }
        
        UUID oldOwnerUuid = network.getOwner();
        
        try {
            plugin.getDatabaseManager().getConnection().setAutoCommit(false);
            
            // Remove new owner from permissions if they exist
            PreparedStatement removeStmt = plugin.getDatabaseManager().prepareStatement(
                "DELETE FROM network_permissions WHERE network_id = ? AND player_uuid = ?"
            );
            removeStmt.setInt(1, networkId);
            removeStmt.setString(2, newOwnerUuid.toString());
            removeStmt.executeUpdate();
            
            // Add old owner as ADMIN
            PreparedStatement addStmt = plugin.getDatabaseManager().prepareStatement(
                "INSERT OR REPLACE INTO network_permissions (network_id, player_uuid, player_name, permission_level, joined_time, last_access) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
            );
            
            long currentTime = System.currentTimeMillis();
            addStmt.setInt(1, networkId);
            addStmt.setString(2, oldOwnerUuid.toString());
            addStmt.setString(3, plugin.getServer().getOfflinePlayer(oldOwnerUuid).getName());
            addStmt.setInt(4, NetworkPermission.ADMIN.getLevel());
            addStmt.setLong(5, currentTime);
            addStmt.setLong(6, currentTime);
            addStmt.executeUpdate();
            
            // Update the network owner
            PreparedStatement ownerStmt = plugin.getDatabaseManager().prepareStatement(
                "UPDATE networks SET owner = ? WHERE id = ?"
            );
            ownerStmt.setString(1, newOwnerUuid.toString());
            ownerStmt.setInt(2, networkId);
            ownerStmt.executeUpdate();
            
            plugin.getDatabaseManager().getConnection().commit();
            plugin.getDatabaseManager().getConnection().setAutoCommit(true);
            
            // Update in-memory data
            network.setOwner(newOwnerUuid);
            
            Map<UUID, NetworkMember> members = networkMembers.computeIfAbsent(networkId, k -> new ConcurrentHashMap<>());
            members.remove(newOwnerUuid);
            
            NetworkMember oldOwnerMember = new NetworkMember(oldOwnerUuid, 
                plugin.getServer().getOfflinePlayer(oldOwnerUuid).getName(), NetworkPermission.ADMIN);
            members.put(oldOwnerUuid, oldOwnerMember);
            
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error transferring network ownership", e);
            try {
                plugin.getDatabaseManager().getConnection().rollback();
                plugin.getDatabaseManager().getConnection().setAutoCommit(true);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error rolling back transaction", ex);
            }
        }
        
        return false;
    }
    
    public List<NetworkMember> getNetworkMembers(int networkId) {
        Map<UUID, NetworkMember> members = networkMembers.get(networkId);
        return members != null ? new ArrayList<>(members.values()) : new ArrayList<>();
    }
    
    public NetworkPermission getPlayerPermission(int networkId, UUID playerUuid) {
        NetworkBlock network = plugin.getNetworkManager().getNetworks().get(networkId);
        if (network == null) {
            return NetworkPermission.NONE;
        }
        
        // The network owner always has OWNER permission
        if (network.getOwner().equals(playerUuid)) {
            return NetworkPermission.OWNER;
        }
        
        // Check permission table
        Map<UUID, NetworkMember> members = networkMembers.get(networkId);
        if (members != null && members.containsKey(playerUuid)) {
            NetworkMember member = members.get(playerUuid);
            member.updateLastAccess();
            return member.getPermission();
        }
        
        return NetworkPermission.NONE;
    }
    
    public boolean checkPermission(int networkId, UUID playerUuid, NetworkPermission requiredPermission) {
        NetworkPermission currentPermission = getPlayerPermission(networkId, playerUuid);
        return currentPermission.hasPermission(requiredPermission);
    }
    
    public void updateMemberLastAccess(int networkId, UUID playerUuid) {
        Map<UUID, NetworkMember> members = networkMembers.get(networkId);
        if (members != null && members.containsKey(playerUuid)) {
            NetworkMember member = members.get(playerUuid);
            member.updateLastAccess();
            
            // Update in database (less frequently, using a separate task would be better for performance)
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "UPDATE network_permissions SET last_access = ? WHERE network_id = ? AND player_uuid = ?"
                );
                
                stmt.setLong(1, member.getLastAccess());
                stmt.setInt(2, networkId);
                stmt.setString(3, playerUuid.toString());
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error updating member last access time", e);
            }
        }
    }
    
    public void handleNetworkDeletion(int networkId) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                "DELETE FROM network_permissions WHERE network_id = ?"
            );
            
            stmt.setInt(1, networkId);
            stmt.executeUpdate();
            
            networkMembers.remove(networkId);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing network permissions on deletion", e);
        }
    }
    
    public int getMemberCount(int networkId) {
        Map<UUID, NetworkMember> members = networkMembers.get(networkId);
        return members != null ? members.size() : 0;
    }
    
    public void shutdown() {
        networkMembers.clear();
    }
}