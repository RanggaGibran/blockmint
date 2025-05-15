package id.rnggagib.blockmint.utils;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DisplayManager {
    
    private static final Map<Location, List<Entity>> holograms = new HashMap<>();
    private static final Map<UUID, Long> itemAnchorTimers = new HashMap<>();
    private static final long REANCHOR_TIME_MS = 5000;
    
    public static void createHologram(BlockMint plugin, Location location, GeneratorType type, int level) {
        removeHologram(location);
        
        Location hologramLoc = location.clone().add(0.5, 
                plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 0.8), 
                0.5);
        
        String ownerName = plugin.getServer().getOfflinePlayer(
                plugin.getGeneratorManager().getGenerator(location).getOwner()).getName();
        
        ArmorStand titleStand = (ArmorStand) location.getWorld().spawnEntity(
                hologramLoc.clone().add(0, 0.5, 0), 
                EntityType.ARMOR_STAND);
        setupArmorStand(titleStand);
        
        String titleText = plugin.getMessageManager().getMessage("generator.hologram-title")
                .replace("{name}", type.getName());
        titleStand.setCustomName(plugin.getMessageManager().stripMiniMessage(titleText));
        titleStand.setCustomNameVisible(true);
        
        ArmorStand levelStand = (ArmorStand) location.getWorld().spawnEntity(
                hologramLoc.clone().add(0, 0.25, 0), 
                EntityType.ARMOR_STAND);
        setupArmorStand(levelStand);
        
        String levelText = plugin.getMessageManager().getMessage("generator.hologram-level")
                .replace("{level}", String.valueOf(level))
                .replace("{max-level}", String.valueOf(type.getMaxLevel()));
        levelStand.setCustomName(plugin.getMessageManager().stripMiniMessage(levelText));
        levelStand.setCustomNameVisible(true);
        
        ArmorStand ownerStand = (ArmorStand) location.getWorld().spawnEntity(
                hologramLoc.clone().add(0, 0, 0), 
                EntityType.ARMOR_STAND);
        setupArmorStand(ownerStand);
        
        String ownerText = plugin.getMessageManager().getMessage("generator.hologram-owner")
                .replace("{owner}", ownerName);
        ownerStand.setCustomName(plugin.getMessageManager().stripMiniMessage(ownerText));
        ownerStand.setCustomNameVisible(true);
        
        Item displayItem = location.getWorld().dropItem(
                hologramLoc.clone().add(0, -0.3, 0), 
                new ItemStack(Material.valueOf(type.getMaterial())));
        displayItem.setPickupDelay(Integer.MAX_VALUE);
        displayItem.setVelocity(new Vector(0, 0, 0));
        displayItem.setGravity(false);
        displayItem.setGlowing(true);
        displayItem.setCustomNameVisible(false);
        
        if (displayItem.getItemStack().getMaxStackSize() > 1) {
            ItemStack itemStack = displayItem.getItemStack();
            itemStack.setAmount(1);
            displayItem.setItemStack(itemStack);
        }
        
        itemAnchorTimers.put(displayItem.getUniqueId(), System.currentTimeMillis());
        List<Entity> hologramEntities = new ArrayList<>();
        hologramEntities.add(titleStand);
        hologramEntities.add(levelStand);
        hologramEntities.add(ownerStand);
        hologramEntities.add(displayItem);
        
        holograms.put(location, hologramEntities);
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (holograms.containsKey(location)) {
                resetItemPosition(plugin, location);
            }
        }, 20L);
    }
    
    public static void updateHologram(BlockMint plugin, Generator generator) {
        Location location = generator.getLocation();
        if (!holograms.containsKey(location)) {
            createHologram(plugin, location, generator.getType(), generator.getLevel());
            return;
        }
        
        List<Entity> entities = holograms.get(location);
        if (entities.size() < 3) {
            removeHologram(location);
            createHologram(plugin, location, generator.getType(), generator.getLevel());
            return;
        }
        
        ArmorStand levelStand = (ArmorStand) entities.get(1);
        String levelText = plugin.getMessageManager().getMessage("generator.hologram-level")
                .replace("{level}", String.valueOf(generator.getLevel()))
                .replace("{max-level}", String.valueOf(generator.getType().getMaxLevel()));
        levelStand.setCustomName(plugin.getMessageManager().stripMiniMessage(levelText));
        
        if (generator.canGenerate()) {
            ArmorStand ownerStand = (ArmorStand) entities.get(2);
            String readyText = plugin.getMessageManager().getMessage("generator.hologram-ready");
            ownerStand.setCustomName(plugin.getMessageManager().stripMiniMessage(readyText));
        } else {
            ArmorStand ownerStand = (ArmorStand) entities.get(2);
            long elapsed = System.currentTimeMillis() - generator.getLastGeneration();
            long total = generator.getType().getGenerationTime() * 1000;
            int percent = (int) ((elapsed * 100) / total);
            
            String progressText = plugin.getMessageManager().getMessage("generator.hologram-progress")
                    .replace("{percent}", String.valueOf(percent));
            ownerStand.setCustomName(plugin.getMessageManager().stripMiniMessage(progressText));
        }
        
        if (entities.size() >= 4 && entities.get(3) instanceof Item) {
            Item displayItem = (Item) entities.get(3);
            UUID itemUUID = displayItem.getUniqueId();
            
            if (!itemAnchorTimers.containsKey(itemUUID) || 
                System.currentTimeMillis() - itemAnchorTimers.get(itemUUID) > REANCHOR_TIME_MS) {
                resetItemPosition(plugin, location);
                itemAnchorTimers.put(itemUUID, System.currentTimeMillis());
            }
        }
    }
    
    private static void resetItemPosition(BlockMint plugin, Location location) {
        if (!holograms.containsKey(location)) return;
        
        List<Entity> entities = holograms.get(location);
        if (entities.size() < 4 || !(entities.get(3) instanceof Item)) return;
        
        Item displayItem = (Item) entities.get(3);
        Location idealItemLoc = location.clone().add(0.5, 
                plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 0.8) - 0.3, 
                0.5);
        
        if (displayItem.getLocation().distanceSquared(idealItemLoc) > 0.1) {
            displayItem.teleport(idealItemLoc);
            displayItem.setVelocity(new Vector(0, 0, 0));
        }
    }
    
    public static void removeHologram(Location location) {
        if (!holograms.containsKey(location)) {
            return;
        }
        
        List<Entity> entities = holograms.get(location);
        for (Entity entity : entities) {
            if (entity instanceof Item) {
                itemAnchorTimers.remove(entity.getUniqueId());
            }
            entity.remove();
        }
        
        holograms.remove(location);
    }
    
    public static void removeAllHolograms() {
        for (List<Entity> entities : holograms.values()) {
            for (Entity entity : entities) {
                if (entity instanceof Item) {
                    itemAnchorTimers.remove(entity.getUniqueId());
                }
                entity.remove();
            }
        }
        
        holograms.clear();
        itemAnchorTimers.clear();
    }
    
    private static void setupArmorStand(ArmorStand stand) {
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
    }
}