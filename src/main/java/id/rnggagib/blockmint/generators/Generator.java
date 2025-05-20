package id.rnggagib.blockmint.generators;

import org.bukkit.Location;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.network.GeneratorNetwork;
import id.rnggagib.blockmint.network.NetworkBlock;

import java.util.UUID;

public class Generator {
    
    private final int id;
    private final UUID owner;
    private final Location location;
    private GeneratorType type;
    private int level;
    private long lastGeneration;
    
    // Evolution tracking fields
    private int usageCount;
    private double resourcesGenerated;
    private long lastEvolutionCheck;
    private boolean evolutionReady;
    
    public Generator(int id, UUID owner, Location location, GeneratorType type, int level) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.type = type;
        this.level = level;
        this.lastGeneration = System.currentTimeMillis();
        this.usageCount = 0;
        this.resourcesGenerated = 0;
        this.lastEvolutionCheck = System.currentTimeMillis();
        this.evolutionReady = false;
    }
    
    public Generator(int id, UUID owner, Location location, GeneratorType type, int level, 
                     int usageCount, double resourcesGenerated) {
        this(id, owner, location, type, level);
        this.usageCount = usageCount;
        this.resourcesGenerated = resourcesGenerated;
        checkEvolutionEligibility();
    }
    
    public int getId() {
        return id;
    }
    
    public UUID getOwner() {
        return owner;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public GeneratorType getType() {
        return type;
    }
    
    public void setType(GeneratorType type) {
        this.type = type;
    }
    
    public int getLevel() {
        return level;
    }
    
    public void setLevel(int level) {
        this.level = Math.min(level, type.getMaxLevel());
    }
    
    public long getLastGeneration() {
        return lastGeneration;
    }
    
    public void setLastGeneration(long lastGeneration) {
        this.lastGeneration = lastGeneration;
    }
    
    public int getUsageCount() {
        return usageCount;
    }
    
    public void incrementUsage(double amount) {
        this.usageCount++;
        this.resourcesGenerated += amount;
        checkEvolutionEligibility();
    }
    
    public double getResourcesGenerated() {
        return resourcesGenerated;
    }
    
    public boolean isEvolutionReady() {
        return evolutionReady;
    }
    
    public GeneratorType getEvolutionTarget() {
        if (!type.hasEvolution()) {
            return null;
        }
        String targetId = type.getEvolutionPath();
        return BlockMint.getInstance().getGeneratorManager().getGeneratorTypes().get(targetId);
    }
    
    private void checkEvolutionEligibility() {
        // Only check every 5 uses to avoid constant checks
        if (usageCount % 5 != 0) {
            return;
        }

        // No evolution path defined
        if (!type.hasEvolution()) {
            evolutionReady = false;
            return;
        }

        // Check if requirements are met
        if (usageCount >= type.getEvolutionRequiredUsage() && 
            resourcesGenerated >= type.getEvolutionRequiredResources()) {
            evolutionReady = true;
        }
        
        lastEvolutionCheck = System.currentTimeMillis();
    }
    
    public boolean canGenerate() {
        return System.currentTimeMillis() - lastGeneration >= getAdjustedGenerationTime() * 1000;
    }
    
    public double getValue() {
        double baseValue = type.getBaseValue() * Math.pow(type.getValueMultiplier(), level - 1);
        double networkBonus = getNetworkBonus();
        double economyMultiplier = getEconomyMultiplier();
        
        return baseValue * (1 + networkBonus) * economyMultiplier;
    }
    
    public long getAdjustedGenerationTime() {
        // For high demand periods, slightly reduce generation time
        double economyMultiplier = getEconomyMultiplier();
        long baseTime = type.getGenerationTime();
        
        if (economyMultiplier > 1.0) {
            // Higher multiplier = slight reduction in time (max 20%)
            return (long)(baseTime * (1.0 - Math.min(0.2, (economyMultiplier - 1.0) * 0.5)));
        } else if (economyMultiplier < 1.0) {
            // Lower multiplier = slight increase in time (max 30%)
            return (long)(baseTime * (1.0 + Math.min(0.3, (1.0 - economyMultiplier) * 0.7)));
        }
        
        return baseTime;
    }
    
    public double getEconomyMultiplier() {
        if (BlockMint.getInstance().getEconomyManager() != null) {
            return BlockMint.getInstance().getEconomyManager().getGeneratorValueMultiplier(type.getId());
        }
        return 1.0;
    }
    
    public double getNetworkBonus() {
        if (BlockMint.getInstance().getNetworkManager() != null) {
            return BlockMint.getInstance().getNetworkManager().getGeneratorEfficiencyBonus(id);
        }
        return 0;
    }
    
    public int getNetworkId() {
        if (BlockMint.getInstance().getNetworkManager() != null) {
            NetworkBlock networkBlock = BlockMint.getInstance().getNetworkManager().getGeneratorNetwork(id);
            if (networkBlock != null) {
                return networkBlock.getNetworkId();
            }
        }
        return -1;
    }
    
    public String getTimeLeftString() {
        if (canGenerate()) {
            return "Ready";
        }
        
        long elapsed = System.currentTimeMillis() - lastGeneration;
        long total = getAdjustedGenerationTime() * 1000;
        long remaining = total - elapsed;
        long seconds = remaining / 1000;
        
        if (seconds < 60) {
            return seconds + "s";
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        
        return hours + "h " + minutes + "m " + seconds + "s";
    }
    
    public int getProgressPercent() {
        if (canGenerate()) {
            return 100;
        }
        
        long elapsed = System.currentTimeMillis() - lastGeneration;
        long total = getAdjustedGenerationTime() * 1000;
        return (int) ((elapsed * 100) / total);
    }
    
    public int getEvolutionProgressPercent() {
        if (!type.hasEvolution()) {
            return 0;
        }
        
        int usageProgress = Math.min(100, (usageCount * 100) / type.getEvolutionRequiredUsage());
        int resourceProgress = Math.min(100, (int)((resourcesGenerated * 100) / type.getEvolutionRequiredResources()));
        
        // Return the minimum of the two percentages (both requirements must be met)
        return Math.min(usageProgress, resourceProgress);
    }
}