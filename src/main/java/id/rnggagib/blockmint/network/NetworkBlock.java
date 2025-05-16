package id.rnggagib.blockmint.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;

import id.rnggagib.BlockMint;

public class NetworkBlock {
    
    private int networkId;
    private Location location;
    private UUID owner;
    private String name;
    private NetworkTier tier;
    private Set<Integer> connectedGenerators;
    private long creationTime;
    private double range;
    
    public NetworkBlock(int networkId, Location location, UUID owner, String name, NetworkTier tier) {
        this.networkId = networkId;
        this.location = location;
        this.owner = owner;
        this.name = name;
        this.tier = tier;
        this.connectedGenerators = new HashSet<>();
        this.creationTime = System.currentTimeMillis();
        this.range = calculateRange();
    }
    
    public int getNetworkId() {
        return networkId;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public UUID getOwner() {
        return owner;
    }
    
    public void setOwner(UUID owner) {
        this.owner = owner;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public NetworkTier getTier() {
        return tier;
    }
    
    public void upgradeTier(NetworkTier newTier) {
        if (this.tier.ordinal() < newTier.ordinal()) {
            this.tier = newTier;
            this.range = calculateRange();
            updateBlockAppearance();
        }
    }
    
    public void addGenerator(int generatorId) {
        connectedGenerators.add(generatorId);
    }
    
    public void removeGenerator(int generatorId) {
        connectedGenerators.remove(generatorId);
    }
    
    public Set<Integer> getConnectedGenerators() {
        return new HashSet<>(connectedGenerators);
    }
    
    public int getConnectedGeneratorCount() {
        return connectedGenerators.size();
    }
    
    public double getRange() {
        return range;
    }
    
    public boolean isInRange(Location generatorLocation) {
        if (!location.getWorld().equals(generatorLocation.getWorld())) {
            return false;
        }
        
        return location.distance(generatorLocation) <= range;
    }
    
    public double getEfficiencyBonus() {
        double baseBonus = tier.getBaseBonus();
        double perGeneratorBonus = tier.getPerGeneratorBonus() * connectedGenerators.size();
        double totalBonus = baseBonus + perGeneratorBonus;
        
        return Math.min(totalBonus, tier.getMaxBonus());
    }
    
    public boolean isAtLocation(Location otherLocation) {
        return location.getWorld().equals(otherLocation.getWorld()) &&
               location.getBlockX() == otherLocation.getBlockX() &&
               location.getBlockY() == otherLocation.getBlockY() &&
               location.getBlockZ() == otherLocation.getBlockZ();
    }
    
    private double calculateRange() {
        return 10 + (tier.ordinal() * 5);
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }
    
    public void updateBlockAppearance() {
        if (location == null || location.getWorld() == null || !location.getChunk().isLoaded()) {
            return;
        }
        
        Material blockMaterial;
        
        switch (tier) {
            case BASIC:
                blockMaterial = Material.LODESTONE;
                break;
            case ADVANCED:
                blockMaterial = Material.RESPAWN_ANCHOR;
                break;
            case ELITE:
                blockMaterial = Material.BEACON;
                break;
            case ULTIMATE:
                blockMaterial = Material.CONDUIT;
                break;
            case CELESTIAL:
                blockMaterial = Material.END_GATEWAY;
                break;
            default:
                blockMaterial = Material.LODESTONE;
        }
        
        if (location.getBlock().getType() != blockMaterial) {
            location.getBlock().setType(blockMaterial);
        }
    }
    
    public boolean isMaxCapacity() {
        int maxGenerators = getMaxGenerators();
        return connectedGenerators.size() >= maxGenerators;
    }
    
    public int getMaxGenerators() {
        return 5 + (tier.ordinal() * 5);
    }
    
    public static ItemStack createNetworkBlockItem(NetworkTier tier, String name) {
        Material material = Material.valueOf(tier.getMaterial());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§b§l" + name + " Network");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Place to create a network hub");
        lore.add("§7Tier: §e" + tier.getDisplayName());
        lore.add("§7Range: §e" + (10 + (tier.ordinal() * 5)) + " blocks");
        lore.add("§7Max Generators: §e" + (5 + (tier.ordinal() * 5)));
        lore.add("§7Base Efficiency: §e+" + (int)(tier.getBaseBonus() * 100) + "%");
        lore.add("§7Per Generator: §e+" + (int)(tier.getPerGeneratorBonus() * 100) + "%");
        lore.add("§7Max Bonus: §e+" + (int)(tier.getMaxBonus() * 100) + "%");
        lore.add("");
        lore.add("§b§lNetwork Controller");
        
        meta.setLore(lore);
        
        NamespacedKey key = new NamespacedKey(BlockMint.getInstance(), "network_tier");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, tier.name());
        
        NamespacedKey nameKey = new NamespacedKey(BlockMint.getInstance(), "network_name");
        meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
        
        item.setItemMeta(meta);
        return item;
    }
    
    public static NetworkTier getTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(BlockMint.getInstance(), "network_tier");
        
        if (container.has(key, PersistentDataType.STRING)) {
            String tierName = container.get(key, PersistentDataType.STRING);
            return NetworkTier.valueOf(tierName);
        }
        
        return null;
    }
    
    public static String getNameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(BlockMint.getInstance(), "network_name");
        
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }
        
        return null;
    }
}