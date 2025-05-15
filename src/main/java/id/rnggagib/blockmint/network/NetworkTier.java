package id.rnggagib.blockmint.network;

public enum NetworkTier {
    
    BASIC(0.05, 0.01, 0.25, "Basic", "REDSTONE_BLOCK"),
    ADVANCED(0.1, 0.015, 0.4, "Advanced", "LAPIS_BLOCK"),
    ELITE(0.15, 0.02, 0.6, "Elite", "GOLD_BLOCK"),
    ULTIMATE(0.2, 0.025, 0.8, "Ultimate", "DIAMOND_BLOCK"),
    CELESTIAL(0.3, 0.03, 1.0, "Celestial", "NETHERITE_BLOCK");
    
    private final double baseBonus;
    private final double perGeneratorBonus;
    private final double maxBonus;
    private final String displayName;
    private final String material;
    
    NetworkTier(double baseBonus, double perGeneratorBonus, double maxBonus, String displayName, String material) {
        this.baseBonus = baseBonus;
        this.perGeneratorBonus = perGeneratorBonus;
        this.maxBonus = maxBonus;
        this.displayName = displayName;
        this.material = material;
    }
    
    public double getBaseBonus() {
        return baseBonus;
    }
    
    public double getPerGeneratorBonus() {
        return perGeneratorBonus;
    }
    
    public double getMaxBonus() {
        return maxBonus;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getMaterial() {
        return material;
    }
    
    public NetworkTier getNextTier() {
        int ordinal = this.ordinal();
        if (ordinal < NetworkTier.values().length - 1) {
            return NetworkTier.values()[ordinal + 1];
        }
        return this;
    }
    
    public boolean isMaxTier() {
        return this == CELESTIAL;
    }
    
    public static NetworkTier fromName(String name) {
        for (NetworkTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return BASIC;
    }
}