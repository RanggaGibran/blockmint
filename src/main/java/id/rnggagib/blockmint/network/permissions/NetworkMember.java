package id.rnggagib.blockmint.network.permissions;

import java.util.UUID;

public class NetworkMember {
    
    private final UUID playerUuid;
    private final String playerName;
    private NetworkPermission permission;
    private long joinedTime;
    private long lastAccess;
    
    public NetworkMember(UUID playerUuid, String playerName, NetworkPermission permission) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.permission = permission;
        this.joinedTime = System.currentTimeMillis();
        this.lastAccess = System.currentTimeMillis();
    }
    
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public NetworkPermission getPermission() {
        return permission;
    }
    
    public void setPermission(NetworkPermission permission) {
        this.permission = permission;
    }
    
    public long getJoinedTime() {
        return joinedTime;
    }
    
    public void setJoinedTime(long joinedTime) {
        this.joinedTime = joinedTime;
    }
    
    public long getLastAccess() {
        return lastAccess;
    }
    
    public void setLastAccess(long lastAccess) {
        this.lastAccess = lastAccess;
    }
    
    public void updateLastAccess() {
        this.lastAccess = System.currentTimeMillis();
    }
    
    public boolean hasPermission(NetworkPermission required) {
        return this.permission.hasPermission(required);
    }
}