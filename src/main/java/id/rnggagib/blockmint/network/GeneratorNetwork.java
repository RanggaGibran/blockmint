package id.rnggagib.blockmint.network;

import id.rnggagib.blockmint.generators.Generator;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GeneratorNetwork {
    
    private int networkId;
    private UUID owner;
    private String name;
    private Set<Integer> generatorIds;
    private NetworkTier tier;
    private long creationTime;
    private double efficiencyBonus;
    
    public GeneratorNetwork(int networkId, UUID owner, String name, NetworkTier tier) {
        this.networkId = networkId;
        this.owner = owner;
        this.name = name;
        this.generatorIds = new HashSet<>();
        this.tier = tier;
        this.creationTime = System.currentTimeMillis();
        calculateEfficiencyBonus();
    }
    
    public void addGenerator(int generatorId) {
        generatorIds.add(generatorId);
        calculateEfficiencyBonus();
    }
    
    public void removeGenerator(int generatorId) {
        generatorIds.remove(generatorId);
        calculateEfficiencyBonus();
    }
    
    public boolean containsGenerator(int generatorId) {
        return generatorIds.contains(generatorId);
    }
    
    private void calculateEfficiencyBonus() {
        int size = generatorIds.size();
        this.efficiencyBonus = tier.getBaseBonus() + (size * tier.getPerGeneratorBonus());
        
        if (this.efficiencyBonus > tier.getMaxBonus()) {
            this.efficiencyBonus = tier.getMaxBonus();
        }
    }
    
    public int getNetworkId() {
        return networkId;
    }
    
    public UUID getOwner() {
        return owner;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Set<Integer> getGeneratorIds() {
        return new HashSet<>(generatorIds);
    }
    
    public int getSize() {
        return generatorIds.size();
    }
    
    public NetworkTier getTier() {
        return tier;
    }
    
    public void upgradeTier(NetworkTier newTier) {
        this.tier = newTier;
        calculateEfficiencyBonus();
    }
    
    public double getEfficiencyBonus() {
        return efficiencyBonus;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
}