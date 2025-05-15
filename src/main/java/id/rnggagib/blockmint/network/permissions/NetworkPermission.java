package id.rnggagib.blockmint.network.permissions;

public enum NetworkPermission {
    NONE(0, "None", "No access to the network"),
    VIEW(1, "View", "Can view network stats only"),
    USE(2, "Use", "Can view stats and connect/disconnect own generators"),
    MANAGE(3, "Manager", "Can add/remove any generators and view stats"),
    ADMIN(4, "Admin", "Can do everything except delete or transfer ownership"),
    OWNER(5, "Owner", "Full access including deletion and transfer");
    
    private final int level;
    private final String displayName;
    private final String description;
    
    NetworkPermission(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean hasPermission(NetworkPermission required) {
        return this.level >= required.level;
    }
    
    public static NetworkPermission fromLevel(int level) {
        for (NetworkPermission permission : values()) {
            if (permission.level == level) {
                return permission;
            }
        }
        return NONE;
    }
}