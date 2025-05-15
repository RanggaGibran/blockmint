package id.rnggagib.blockmint.utils;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.network.GeneratorNetwork;
import id.rnggagib.blockmint.network.NetworkBlock;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DisplayManager {
    
    private static final Map<Location, List<Entity>> holograms = new HashMap<>();
    private static final Map<Location, Item> displayItems = new HashMap<>();
    private static final Map<Location, BukkitTask> anchorTasks = new HashMap<>();
    
    public static void createHologram(BlockMint plugin, Location location, GeneratorType type, int level) {
        removeHologram(location);
        
        Location baseLocation = location.clone();
        Location hologramLoc = baseLocation.clone().add(0.5, 
                plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 1.2), 
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
        
        Location itemLoc = hologramLoc.clone();
        ItemStack itemStack = new ItemStack(Material.valueOf(type.getMaterial()), 1);
        Item displayItem = location.getWorld().dropItem(itemLoc, itemStack);
        
        displayItem.setPickupDelay(Integer.MAX_VALUE);
        displayItem.setVelocity(new Vector(0, 0, 0));
        displayItem.setGravity(false);
        displayItem.setGlowing(true);
        displayItem.setCustomNameVisible(false);
        displayItem.setInvulnerable(true);
        
        List<Entity> hologramEntities = new ArrayList<>();
        hologramEntities.add(titleStand);
        hologramEntities.add(levelStand);
        hologramEntities.add(ownerStand);
        hologramEntities.add(displayItem);
        
        holograms.put(location, hologramEntities);
        displayItems.put(location, displayItem);
        
        anchorItemExactly(plugin, location);
        
        // Cancel any existing tasks for this location
        if (anchorTasks.containsKey(location) && anchorTasks.get(location) != null) {
            anchorTasks.get(location).cancel();
        }
        
        // Schedule periodic anchoring task
        int anchorFrequency = plugin.getConfigManager().getConfig().getInt("settings.display-item.anchor-frequency", 20);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (holograms.containsKey(location)) {
                anchorItemExactly(plugin, location);
            } else {
                // Auto-cleanup if the hologram no longer exists
                if (anchorTasks.containsKey(location)) {
                    anchorTasks.get(location).cancel();
                    anchorTasks.remove(location);
                }
            }
        }, 5L, anchorFrequency);
        
        anchorTasks.put(location, task);
    }
    
    public static void updateHologram(BlockMint plugin, Generator generator) {
        Location location = generator.getLocation();
        
        if (!hasHologram(location)) return;
        
        ArmorStand nameStand = (ArmorStand) holograms.get(location).get(0);
        ArmorStand infoStand = (ArmorStand) holograms.get(location).get(1);
        
        String name = generator.getType().getName() + " Generator";
        String level = "Level " + generator.getLevel() + "/" + generator.getType().getMaxLevel();
        
        NetworkBlock network = plugin.getNetworkManager().getGeneratorNetwork(generator.getId());
        String networkInfo = "";
        if (network != null) {
            networkInfo = " §7[§b⚡ " + network.getTier().name().charAt(0) + "§7]";
        }
        
        nameStand.setCustomName("§6§l" + name + networkInfo);
        
        if (generator.canGenerate()) {
            infoStand.setCustomName("§a✓ Ready to collect!");
        } else {
            long elapsed = System.currentTimeMillis() - generator.getLastGeneration();
            long total = generator.getType().getGenerationTime() * 1000;
            long remaining = total - elapsed;
            
            infoStand.setCustomName("§e" + level + " §7| §f" + formatTime(remaining / 1000));
        }
    }
    
    private static String formatTime(long seconds) {
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
        
        return hours + "h " + minutes + "m";
    }
    
    private static void anchorItemExactly(BlockMint plugin, Location blockLocation) {
        if (!displayItems.containsKey(blockLocation) || displayItems.get(blockLocation) == null || 
            displayItems.get(blockLocation).isDead()) {
            return;
        }
        
        Item item = displayItems.get(blockLocation);
        
        double heightOffset = plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 1.2);
        Location exactLoc = blockLocation.clone().add(0.5, heightOffset, 0.5);
        
        item.teleport(exactLoc);
        item.setVelocity(new Vector(0, 0, 0));
    }
    
    public static void removeHologram(Location location) {
        if (!holograms.containsKey(location)) {
            return;
        }
        
        List<Entity> entities = holograms.get(location);
        for (Entity entity : entities) {
            entity.remove();
        }
        
        holograms.remove(location);
        displayItems.remove(location);
        
        // Cancel any scheduled tasks for this location
        if (anchorTasks.containsKey(location)) {
            BukkitTask task = anchorTasks.get(location);
            if (task != null) {
                task.cancel();
            }
            anchorTasks.remove(location);
        }
    }
    
    public static void removeAllHolograms() {
        for (List<Entity> entities : holograms.values()) {
            for (Entity entity : entities) {
                entity.remove();
            }
        }
        
        // Cancel all scheduled tasks
        for (BukkitTask task : anchorTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        
        holograms.clear();
        displayItems.clear();
        anchorTasks.clear();
    }
    
    private static void setupArmorStand(ArmorStand stand) {
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setPersistent(true);
    }
    
    public static boolean hasHologram(Location location) {
        return holograms.containsKey(location);
    }
    
    public static int getActiveHologramsCount() {
        return holograms.size();
    }
}