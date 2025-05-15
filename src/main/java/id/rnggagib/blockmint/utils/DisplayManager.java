package id.rnggagib.blockmint.utils;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.network.NetworkBlock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DisplayManager {
    
    private static final Map<Location, List<Entity>> holograms = new HashMap<>();
    private static final Map<Location, Item> displayItems = new HashMap<>();
    private static final Map<Location, BukkitTask> anchorTasks = new HashMap<>();
    private static final Map<Location, UUID> hologramIds = new HashMap<>();
    
    public static void initialize(BlockMint plugin) {
        plugin.getLogger().info("Initializing DisplayManager...");
        cleanupOrphanedHolograms(plugin);
        
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            verifyHologramIntegrity(plugin);
        }, 1200L, 1200L);
    }
    
    private static void cleanupOrphanedHolograms(BlockMint plugin) {
        plugin.getLogger().info("Cleaning up orphaned holograms...");
        int removed = 0;
        
        for (World world : Bukkit.getWorlds()) {
            Collection<Entity> entities = world.getEntitiesByClasses(ArmorStand.class, Item.class);
            
            for (Entity entity : entities) {
                if (entity instanceof ArmorStand) {
                    ArmorStand stand = (ArmorStand) entity;
                    if (stand.isMarker() && !stand.isVisible() && !stand.hasBasePlate()) {
                        entity.remove();
                        removed++;
                    }
                } else if (entity instanceof Item) {
                    Item item = (Item) entity;
                    if (item.getPickupDelay() == Integer.MAX_VALUE && !item.hasGravity()) {
                        entity.remove();
                        removed++;
                    }
                }
            }
        }
        
        plugin.getLogger().info("Removed " + removed + " orphaned hologram entities");
    }
    
    private static void verifyHologramIntegrity(BlockMint plugin) {
        Iterator<Map.Entry<Location, List<Entity>>> iterator = holograms.entrySet().iterator();
        int fixed = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<Location, List<Entity>> entry = iterator.next();
            Location location = entry.getKey();
            List<Entity> entities = entry.getValue();
            
            boolean needsRecreation = false;
            
            for (Entity entity : entities) {
                if (entity == null || entity.isDead()) {
                    needsRecreation = true;
                    break;
                }
            }
            
            if (needsRecreation) {
                Generator generator = plugin.getGeneratorManager().getGenerator(location);
                if (generator != null) {
                    removeHologram(location);
                    createHologram(plugin, location, generator.getType(), generator.getLevel());
                    fixed++;
                } else {
                    removeHologram(location);
                }
            }
        }
        
        if (fixed > 0) {
            plugin.getLogger().info("Fixed " + fixed + " broken holograms");
        }
    }
    
    public static void createHologram(BlockMint plugin, Location location, GeneratorType type, int level) {
        removeNearbyHolograms(location, 1.0);
        removeHologram(location);
        
        Location baseLocation = location.clone();
        Location hologramLoc = baseLocation.clone().add(0.5, 
                plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 1.2), 
                0.5);
        
        String ownerName;
        try {
            ownerName = plugin.getServer().getOfflinePlayer(
                plugin.getGeneratorManager().getGenerator(location).getOwner()).getName();
            if (ownerName == null) ownerName = "Unknown";
        } catch (Exception e) {
            ownerName = "Unknown";
            plugin.getLogger().log(Level.WARNING, "Failed to get owner name for hologram", e);
        }
        
        List<Entity> hologramEntities = new ArrayList<>();
        UUID hologramUUID = UUID.randomUUID();
        
        try {
            ArmorStand titleStand = (ArmorStand) location.getWorld().spawnEntity(
                    hologramLoc.clone().add(0, 0.5, 0), 
                    EntityType.ARMOR_STAND);
            setupArmorStand(titleStand);
            
            String titleText = plugin.getMessageManager().getMessage("generator.hologram-title")
                    .replace("{name}", type.getName());
            titleStand.setCustomName(plugin.getMessageManager().stripMiniMessage(titleText));
            titleStand.setCustomNameVisible(true);
            hologramEntities.add(titleStand);
            
            ArmorStand levelStand = (ArmorStand) location.getWorld().spawnEntity(
                    hologramLoc.clone().add(0, 0.25, 0), 
                    EntityType.ARMOR_STAND);
            setupArmorStand(levelStand);
            
            String levelText = plugin.getMessageManager().getMessage("generator.hologram-level")
                    .replace("{level}", String.valueOf(level))
                    .replace("{max-level}", String.valueOf(type.getMaxLevel()));
            levelStand.setCustomName(plugin.getMessageManager().stripMiniMessage(levelText));
            levelStand.setCustomNameVisible(true);
            hologramEntities.add(levelStand);
            
            ArmorStand ownerStand = (ArmorStand) location.getWorld().spawnEntity(
                    hologramLoc.clone().add(0, 0, 0), 
                    EntityType.ARMOR_STAND);
            setupArmorStand(ownerStand);
            
            String ownerText = plugin.getMessageManager().getMessage("generator.hologram-owner")
                    .replace("{owner}", ownerName);
            ownerStand.setCustomName(plugin.getMessageManager().stripMiniMessage(ownerText));
            ownerStand.setCustomNameVisible(true);
            hologramEntities.add(ownerStand);
            
            Item displayItem = createDisplayItem(plugin, location, type, hologramLoc);
            hologramEntities.add(displayItem);
            displayItems.put(location, displayItem);
            
            holograms.put(location, hologramEntities);
            hologramIds.put(location, hologramUUID);
            
            scheduleAnchorTask(plugin, location);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating hologram at " + 
                    location.getWorld().getName() + " " + 
                    location.getX() + "," + location.getY() + "," + location.getZ(), e);
            
            for (Entity entity : hologramEntities) {
                entity.remove();
            }
        }
    }
    
    private static Item createDisplayItem(BlockMint plugin, Location location, GeneratorType type, Location hologramLoc) {
        ItemStack itemStack = new ItemStack(Material.valueOf(type.getMaterial()), 1);
        Item displayItem = location.getWorld().dropItem(hologramLoc.clone(), itemStack);
        
        displayItem.setPickupDelay(Integer.MAX_VALUE);
        displayItem.setVelocity(new Vector(0, 0, 0));
        displayItem.setGravity(false);
        displayItem.setGlowing(true);
        displayItem.setCustomNameVisible(false);
        displayItem.setInvulnerable(true);
        
        return displayItem;
    }
    
    private static void scheduleAnchorTask(BlockMint plugin, Location location) {
        if (anchorTasks.containsKey(location) && anchorTasks.get(location) != null) {
            anchorTasks.get(location).cancel();
        }
        
        int anchorFrequency = plugin.getConfigManager().getConfig().getInt("settings.display-item.anchor-frequency", 20);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (holograms.containsKey(location)) {
                anchorItemExactly(plugin, location);
            } else {
                if (anchorTasks.containsKey(location)) {
                    anchorTasks.get(location).cancel();
                    anchorTasks.remove(location);
                }
            }
        }, 5L, anchorFrequency);
        
        anchorTasks.put(location, task);
    }
    
    private static void removeNearbyHolograms(Location center, double radius) {
        double radiusSquared = radius * radius;
        Iterator<Map.Entry<Location, List<Entity>>> iterator = holograms.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<Location, List<Entity>> entry = iterator.next();
            Location location = entry.getKey();
            
            if (location.getWorld().equals(center.getWorld()) && 
                location.distanceSquared(center) <= radiusSquared && 
                !location.equals(center)) {
                
                List<Entity> entities = entry.getValue();
                for (Entity entity : entities) {
                    if (entity != null && !entity.isDead()) {
                        entity.remove();
                    }
                }
                
                if (anchorTasks.containsKey(location)) {
                    BukkitTask task = anchorTasks.get(location);
                    if (task != null) {
                        task.cancel();
                    }
                    anchorTasks.remove(location);
                }
                
                displayItems.remove(location);
                hologramIds.remove(location);
                iterator.remove();
            }
        }
    }
    
    public static void updateHologram(BlockMint plugin, Generator generator) {
        Location location = generator.getLocation();
        
        if (!hasHologram(location) || holograms.get(location) == null || holograms.get(location).size() < 2) {
            createHologram(plugin, location, generator.getType(), generator.getLevel());
            return;
        }
        
        try {
            List<Entity> entities = holograms.get(location);
            if (entities.get(0) instanceof ArmorStand && entities.get(1) instanceof ArmorStand) {
                ArmorStand nameStand = (ArmorStand) entities.get(0);
                ArmorStand infoStand = (ArmorStand) entities.get(1);
                
                if (nameStand.isDead() || infoStand.isDead()) {
                    createHologram(plugin, location, generator.getType(), generator.getLevel());
                    return;
                }
                
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
            } else {
                createHologram(plugin, location, generator.getType(), generator.getLevel());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error updating hologram at " + 
                    location.getWorld().getName() + " " + 
                    location.getX() + "," + location.getY() + "," + location.getZ(), e);
            createHologram(plugin, location, generator.getType(), generator.getLevel());
        }
    }
    
    private static String formatTime(long seconds) {
        if (seconds < 0) seconds = 0;
        
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
        if (!displayItems.containsKey(blockLocation) || 
            displayItems.get(blockLocation) == null || 
            displayItems.get(blockLocation).isDead()) {
            
            Generator generator = plugin.getGeneratorManager().getGenerator(blockLocation);
            if (generator != null) {
                createHologram(plugin, blockLocation, generator.getType(), generator.getLevel());
                return;
            }
            return;
        }
        
        Item item = displayItems.get(blockLocation);
        
        double heightOffset = plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 1.2);
        Location exactLoc = blockLocation.clone().add(0.5, heightOffset, 0.5);
        
        item.teleport(exactLoc);
        item.setVelocity(new Vector(0, 0, 0));
        
        boolean shouldRotate = plugin.getConfigManager().getConfig().getBoolean("settings.display-item.rotate-display", true);
        if (shouldRotate) {
            int rotationSpeed = plugin.getConfigManager().getConfig().getInt("settings.display-item.rotation-speed", 2);
            item.setRotation(item.getLocation().getYaw() + rotationSpeed, 0);
        }
    }
    
    public static void removeHologram(Location location) {
        if (!holograms.containsKey(location)) {
            return;
        }
        
        List<Entity> entities = holograms.get(location);
        for (Entity entity : entities) {
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        
        if (anchorTasks.containsKey(location)) {
            BukkitTask task = anchorTasks.get(location);
            if (task != null) {
                task.cancel();
            }
            anchorTasks.remove(location);
        }
        
        holograms.remove(location);
        displayItems.remove(location);
        hologramIds.remove(location);
    }
    
    public static void removeAllHolograms() {
        for (List<Entity> entities : holograms.values()) {
            for (Entity entity : entities) {
                if (entity != null && !entity.isDead()) {
                    entity.remove();
                }
            }
        }
        
        for (BukkitTask task : anchorTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        
        holograms.clear();
        displayItems.clear();
        anchorTasks.clear();
        hologramIds.clear();
    }
    
    private static void setupArmorStand(ArmorStand stand) {
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setPersistent(false);
        stand.setSilent(true);
    }
    
    public static boolean hasHologram(Location location) {
        return holograms.containsKey(location);
    }
    
    public static int getActiveHologramsCount() {
        return holograms.size();
    }
    
    public static Map<Location, UUID> getHologramIds() {
        return hologramIds;
    }
}