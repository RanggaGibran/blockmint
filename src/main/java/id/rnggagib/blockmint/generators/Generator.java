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
    private final GeneratorType type;
    private int level;
    private long lastGeneration;
    
    public Generator(int id, UUID owner, Location location, GeneratorType type, int level) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.type = type;
        this.level = level;
        this.lastGeneration = System.currentTimeMillis();
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
    
    public boolean canGenerate() {
        return System.currentTimeMillis() - lastGeneration >= type.getGenerationTime() * 1000;
    }
    
    public double getValue() {
        double baseValue = type.getBaseValue() * Math.pow(type.getValueMultiplier(), level - 1);
        double networkBonus = getNetworkBonus();
        
        return baseValue * (1 + networkBonus);
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
}