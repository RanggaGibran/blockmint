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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public class DisplayManager {
    
    private static final Map<Location, List<Entity>> holograms = new HashMap<>();
    private static final Map<Location, Item> displayItems = new HashMap<>();
    private static final Map<Location, BukkitTask> anchorTasks = new HashMap<>();
    private static final Map<Location, UUID> hologramIds = new HashMap<>();
    private static final Map<UUID, HologramTemplate> hologramTemplates = new HashMap<>();
    private static final Map<Location, HologramAnimation> hologramAnimations = new HashMap<>();
    private static final Map<UUID, Set<UUID>> playerVisibilityOverrides = new ConcurrentHashMap<>();
    private static final int CLEANUP_INTERVAL = 6000;
    private static final int VERIFICATION_INTERVAL = 1200;
    private static BlockMint plugin;
    private static NamespacedKey hologramKey;
    
    public static class HologramTemplate {
        private final UUID templateId;
        private final String name;
        private final List<String> lines;
        private final Material displayMaterial;
        private final boolean animated;
        private final boolean rotateItem;
        private final double heightOffset;
        private final Function<Location, List<String>> dynamicTextProvider;
        
        public HologramTemplate(String name, List<String> lines, Material displayMaterial, 
                              boolean animated, boolean rotateItem, double heightOffset,
                              Function<Location, List<String>> dynamicTextProvider) {
            this.templateId = UUID.randomUUID();
            this.name = name;
            this.lines = lines;
            this.displayMaterial = displayMaterial;
            this.animated = animated;
            this.rotateItem = rotateItem;
            this.heightOffset = heightOffset;
            this.dynamicTextProvider = dynamicTextProvider;
        }
        
        public UUID getTemplateId() {
            return templateId;
        }
        
        public String getName() {
            return name;
        }
        
        public List<String> getLines() {
            return lines;
        }
        
        public Material getDisplayMaterial() {
            return displayMaterial;
        }
        
        public boolean isAnimated() {
            return animated;
        }
        
        public boolean shouldRotateItem() {
            return rotateItem;
        }
        
        public double getHeightOffset() {
            return heightOffset;
        }
        
        public Function<Location, List<String>> getDynamicTextProvider() {
            return dynamicTextProvider;
        }
    }
    
    public static class HologramAnimation {
        private final List<String> frames;
        private final long frameDelay;
        private int currentFrame;
        private BukkitTask animationTask;
        
        public HologramAnimation(List<String> frames, long frameDelay) {
            this.frames = frames;
            this.frameDelay = frameDelay;
            this.currentFrame = 0;
        }
        
        public String getCurrentFrame() {
            return frames.get(currentFrame);
        }
        
        public void nextFrame() {
            currentFrame = (currentFrame + 1) % frames.size();
        }
        
        public void startAnimation(BlockMint plugin, Location location, ArmorStand stand) {
            stopAnimation();
            
            animationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (stand != null && !stand.isDead()) {
                    stand.setCustomName(frames.get(currentFrame));
                    nextFrame();
                } else {
                    stopAnimation();
                }
            }, frameDelay, frameDelay);
        }
        
        public void stopAnimation() {
            if (animationTask != null) {
                animationTask.cancel();
                animationTask = null;
            }
        }
    }
    
    public static void initialize(BlockMint pluginInstance) {
        plugin = pluginInstance;
        hologramKey = new NamespacedKey(plugin, "blockmint_hologram");
        
        plugin.getLogger().info("Initializing Advanced Hologram Management System...");
        
        cleanupOrphanedHolograms();
        loadDefaultTemplates();
        
        plugin.getServer().getScheduler().runTaskTimer(plugin, 
            DisplayManager::verifyHologramIntegrity, VERIFICATION_INTERVAL, VERIFICATION_INTERVAL);
        
        plugin.getServer().getScheduler().runTaskTimer(plugin, 
            DisplayManager::cleanupOrphanedHolograms, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }
    
    private static void loadDefaultTemplates() {
        List<String> generatorLines = new ArrayList<>();
        generatorLines.add("{name} Generator");
        generatorLines.add("Level {level}/{maxLevel}");
        generatorLines.add("Value: {value}");
        generatorLines.add("Owner: {owner}");
        generatorLines.add("{evolution_status}");
        
        Function<Location, List<String>> generatorTextProvider = (location) -> {
            Generator generator = plugin.getGeneratorManager().getGenerator(location);
            if (generator == null) return generatorLines;
            
            List<String> result = new ArrayList<>();
            result.add(generator.getType().getName() + " Generator");
            result.add("Level " + generator.getLevel() + "/" + generator.getType().getMaxLevel());
            
            // Include economy multiplier indicator with color
            double ecoMultiplier = generator.getEconomyMultiplier();
            String multiplierDisplay = "";
            if (ecoMultiplier > 1.1) {
                multiplierDisplay = " §a(+" + String.format("%.1f", (ecoMultiplier - 1.0) * 100) + "%)";
            } else if (ecoMultiplier < 0.9) {
                multiplierDisplay = " §c(" + String.format("%.1f", (ecoMultiplier - 1.0) * 100) + "%)";
            }
            
            result.add("Value: $" + String.format("%.2f", generator.getValue()) + multiplierDisplay);
            
            String ownerName = plugin.getServer().getOfflinePlayer(generator.getOwner()).getName();
            if (ownerName == null) ownerName = "Unknown";
            result.add("Owner: " + ownerName);
            
            // Add evolution progress if applicable
            if (generator.getType().hasEvolution()) {
                int progress = generator.getEvolutionProgressPercent();
                if (progress < 100) {
                    GeneratorType nextType = generator.getEvolutionTarget();
                    if (nextType != null) {
                        result.add("§dEvolution: " + progress + "% → " + nextType.getName());
                    }
                } else {
                    result.add("§aReady to evolve!");
                }
            }
            
            return result;
        };
        
        HologramTemplate generatorTemplate = new HologramTemplate(
            "generator_default", 
            generatorLines,
            Material.DIAMOND_BLOCK,
            false,
            true,
            1.5,
            generatorTextProvider
        );
        
        registerTemplate(generatorTemplate);
        
        // Network template
        List<String> networkLines = new ArrayList<>();
        networkLines.add("{tier} Network Controller");
        networkLines.add("Owner: {owner}");
        networkLines.add("Generators: {count}/{max}");
        
        Function<Location, List<String>> networkTextProvider = (location) -> {
            NetworkBlock network = plugin.getNetworkManager().getNetworkAt(location);
            if (network == null) return networkLines;
            
            List<String> result = new ArrayList<>();
            result.add(network.getTier().getDisplayName() + " Network Controller");
            
            String ownerName = plugin.getServer().getOfflinePlayer(network.getOwner()).getName();
            if (ownerName == null) ownerName = "Unknown";
            result.add("Owner: " + ownerName);
            
            result.add("Generators: " + network.getConnectedGeneratorCount() + "/" + network.getMaxGenerators());
            
            return result;
        };
        
        HologramTemplate networkTemplate = new HologramTemplate(
            "network_default", 
            networkLines,
            Material.BEACON,
            false,
            true,
            1.8,
            networkTextProvider
        );
        
        registerTemplate(networkTemplate);
        
        // Animated template example
        List<String> animatedLines = new ArrayList<>();
        animatedLines.add("⬛⬛⬛⬛⬛");
        animatedLines.add("⬛⬛⬜⬛⬛");
        animatedLines.add("⬛⬜⬜⬜⬛");
        animatedLines.add("⬛⬛⬜⬛⬛");
        animatedLines.add("⬛⬛⬛⬛⬛");
        
        HologramTemplate animatedTemplate = new HologramTemplate(
            "loading_animation", 
            animatedLines,
            Material.ENDER_PEARL,
            true,
            true,
            1.5,
            null
        );
        
        registerTemplate(animatedTemplate);
    }
    
    private static void cleanupOrphanedHolograms() {
        int removed = 0;
        
        for (World world : Bukkit.getWorlds()) {
            Collection<Entity> entities = world.getEntitiesByClasses(ArmorStand.class, Item.class);
            
            for (Entity entity : entities) {
                if (entity instanceof ArmorStand) {
                    ArmorStand stand = (ArmorStand) entity;
                    if (stand.isMarker() && !stand.isVisible() && !stand.hasBasePlate()) {
                        // Check if it's our hologram by checking persistent data
                        PersistentDataContainer container = stand.getPersistentDataContainer();
                        if (container.has(hologramKey, PersistentDataType.STRING)) {
                            // Only remove if it's orphaned (not in our tracking map)
                            boolean isOrphaned = true;
                            for (List<Entity> trackedEntities : holograms.values()) {
                                if (trackedEntities.contains(stand)) {
                                    isOrphaned = false;
                                    break;
                                }
                            }
                            
                            if (isOrphaned) {
                                entity.remove();
                                removed++;
                            }
                        }
                    }
                } else if (entity instanceof Item) {
                    Item item = (Item) entity;
                    if (item.getPickupDelay() == Integer.MAX_VALUE && !item.hasGravity()) {
                        // Check if it's our display item and not tracked
                        PersistentDataContainer container = item.getPersistentDataContainer();
                        if (container.has(hologramKey, PersistentDataType.STRING) && 
                            !displayItems.containsValue(item)) {
                            entity.remove();
                            removed++;
                        }
                    }
                }
            }
        }
        
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " orphaned hologram entities");
        }
    }
    
    private static void verifyHologramIntegrity() {
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
                    createGeneratorHologram(location, generator.getType(), generator.getLevel());
                    fixed++;
                } else {
                    // Check if it's a network block
                    NetworkBlock network = plugin.getNetworkManager().getNetworkAt(location);
                    if (network != null) {
                        removeHologram(location);
                        createNetworkHologram(location, network);
                        fixed++;
                    } else {
                        removeHologram(location);
                    }
                }
            }
        }
        
        if (fixed > 0) {
            plugin.getLogger().info("Fixed " + fixed + " broken holograms");
        }
    }
    
    public static void registerTemplate(HologramTemplate template) {
        hologramTemplates.put(template.getTemplateId(), template);
        plugin.getLogger().info("Registered hologram template: " + template.getName());
    }
    
    public static void unregisterTemplate(UUID templateId) {
        if (hologramTemplates.remove(templateId) != null) {
            plugin.getLogger().info("Unregistered hologram template: " + templateId);
        }
    }
    
    public static HologramTemplate getTemplate(UUID templateId) {
        return hologramTemplates.get(templateId);
    }
    
    public static HologramTemplate getTemplateByName(String name) {
        for (HologramTemplate template : hologramTemplates.values()) {
            if (template.getName().equals(name)) {
                return template;
            }
        }
        return null;
    }
    
    public static void createGeneratorHologram(Location location, GeneratorType type, int level) {
        HologramTemplate template = getTemplateByName("generator_default");
        if (template == null) {
            // Fall back to original method if template doesn't exist
            createHologram(plugin, location, type, level);
            return;
        }
        
        createTemplatedHologram(location, template, createGeneratorReplacements(location, type, level));
    }
    
    public static void createNetworkHologram(Location location, NetworkBlock network) {
        // Check if location already has a hologram for better restart handling
        if (hasHologram(location)) {
            removeHologram(location);
        }
        
        HologramTemplate template = getTemplateByName("network_default");
        if (template == null) {
            return;
        }
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{tier}", network.getTier().getDisplayName());
        
        String ownerName = Bukkit.getOfflinePlayer(network.getOwner()).getName();
        if (ownerName == null) ownerName = "Unknown";
        replacements.put("{owner}", ownerName);
        
        replacements.put("{count}", String.valueOf(network.getConnectedGeneratorCount()));
        replacements.put("{max}", String.valueOf(network.getMaxGenerators()));
        
        createTemplatedHologram(location, template, replacements);
    }
    
    private static Map<String, String> createGeneratorReplacements(Location location, GeneratorType type, int level) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{name}", type.getName());
        replacements.put("{level}", String.valueOf(level));
        replacements.put("{maxLevel}", String.valueOf(type.getMaxLevel()));
        
        Generator generator = plugin.getGeneratorManager().getGenerator(location);
        if (generator != null) {
            String ownerName = plugin.getServer().getOfflinePlayer(generator.getOwner()).getName();
            if (ownerName == null) ownerName = "Unknown";
            replacements.put("{owner}", ownerName);
        } else {
            replacements.put("{owner}", "Unknown");
        }
        
        return replacements;
    }
    
    public static void createTemplatedHologram(Location location, HologramTemplate template, Map<String, String> replacements) {
        removeNearbyHolograms(location, 1.0);
        removeHologram(location);
        
        Location baseLocation = location.clone();
        Location hologramLoc = baseLocation.clone().add(0.5, template.getHeightOffset(), 0.5);
        
        List<Entity> hologramEntities = new ArrayList<>();
        UUID hologramUUID = UUID.randomUUID();
        
        try {
            List<String> lines;
            if (template.getDynamicTextProvider() != null) {
                lines = template.getDynamicTextProvider().apply(location);
            } else {
                lines = new ArrayList<>(template.getLines());
                
                // Apply replacements
                if (replacements != null) {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                            line = line.replace(replacement.getKey(), replacement.getValue());
                        }
                        lines.set(i, line);
                    }
                }
            }
            
            // Create ArmorStands for text lines
            double yOffset = 0.25 * (lines.size() - 1);
            for (String line : lines) {
                ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(
                        hologramLoc.clone().add(0, yOffset, 0), 
                        EntityType.ARMOR_STAND);
                setupArmorStand(stand);
                
                // Mark as our hologram
                stand.getPersistentDataContainer().set(
                    hologramKey, 
                    PersistentDataType.STRING, 
                    hologramUUID.toString()
                );
                
                stand.setCustomName(line);
                stand.setCustomNameVisible(true);
                hologramEntities.add(stand);
                
                yOffset -= 0.25;
            }
            
            // Create display item
            Material material = template.getDisplayMaterial();
            if (material == null) {
                if (location.getBlock().getType() != Material.AIR) {
                    material = location.getBlock().getType();
                } else {
                    material = Material.STONE;
                }
            }
            
            Item displayItem = createDisplayItem(location, material, hologramLoc);
            displayItem.getPersistentDataContainer().set(
                hologramKey, 
                PersistentDataType.STRING, 
                hologramUUID.toString()
            );
            
            hologramEntities.add(displayItem);
            displayItems.put(location, displayItem);
            
            // Set up animation if needed
            if (template.isAnimated() && !lines.isEmpty()) {
                HologramAnimation animation = new HologramAnimation(lines, 10L);
                hologramAnimations.put(location, animation);
                
                // Start animation on first line
                ArmorStand firstStand = (ArmorStand) hologramEntities.get(0);
                animation.startAnimation(plugin, location, firstStand);
            }
            
            holograms.put(location, hologramEntities);
            hologramIds.put(location, hologramUUID);
            
            scheduleAnchorTask(location, template.shouldRotateItem());
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating templated hologram at " + 
                    location.getWorld().getName() + " " + 
                    location.getX() + "," + location.getY() + "," + location.getZ(), e);
            
            for (Entity entity : hologramEntities) {
                entity.remove();
            }
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
            
            titleStand.getPersistentDataContainer().set(
                hologramKey, 
                PersistentDataType.STRING, 
                hologramUUID.toString()
            );
            
            String titleText = plugin.getMessageManager().getMessage("generator.hologram-title")
                    .replace("{name}", type.getName());
            titleStand.setCustomName(plugin.getMessageManager().stripMiniMessage(titleText));
            titleStand.setCustomNameVisible(true);
            hologramEntities.add(titleStand);
            
            ArmorStand levelStand = (ArmorStand) location.getWorld().spawnEntity(
                    hologramLoc.clone().add(0, 0.25, 0), 
                    EntityType.ARMOR_STAND);
            setupArmorStand(levelStand);
            
            levelStand.getPersistentDataContainer().set(
                hologramKey, 
                PersistentDataType.STRING, 
                hologramUUID.toString()
            );
            
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
            
            ownerStand.getPersistentDataContainer().set(
                hologramKey, 
                PersistentDataType.STRING, 
                hologramUUID.toString()
            );
            
            String ownerText = plugin.getMessageManager().getMessage("generator.hologram-owner")
                    .replace("{owner}", ownerName);
            ownerStand.setCustomName(plugin.getMessageManager().stripMiniMessage(ownerText));
            ownerStand.setCustomNameVisible(true);
            hologramEntities.add(ownerStand);
            
            Item displayItem = createDisplayItem(location, Material.valueOf(type.getMaterial()), hologramLoc);
            
            displayItem.getPersistentDataContainer().set(
                hologramKey, 
                PersistentDataType.STRING, 
                hologramUUID.toString()
            );
            
            hologramEntities.add(displayItem);
            displayItems.put(location, displayItem);
            
            holograms.put(location, hologramEntities);
            hologramIds.put(location, hologramUUID);
            
            scheduleAnchorTask(location, true);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating hologram at " + 
                    location.getWorld().getName() + " " + 
                    location.getX() + "," + location.getY() + "," + location.getZ(), e);
            
            for (Entity entity : hologramEntities) {
                entity.remove();
            }
        }
    }
    
    private static Item createDisplayItem(Location location, Material material, Location hologramLoc) {
        ItemStack itemStack = new ItemStack(material, 1);
        Item displayItem = location.getWorld().dropItem(hologramLoc.clone(), itemStack);
        
        displayItem.setPickupDelay(Integer.MAX_VALUE);
        displayItem.setVelocity(new Vector(0, 0, 0));
        displayItem.setGravity(false);
        displayItem.setGlowing(true);
        displayItem.setCustomNameVisible(false);
        displayItem.setInvulnerable(true);
        
        return displayItem;
    }
    
    private static void scheduleAnchorTask(Location location, boolean shouldRotate) {
        if (anchorTasks.containsKey(location) && anchorTasks.get(location) != null) {
            anchorTasks.get(location).cancel();
        }
        
        int anchorFrequency = plugin.getConfigManager().getConfig().getInt("settings.display-item.anchor-frequency", 20);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (holograms.containsKey(location)) {
                anchorItemExactly(location, shouldRotate);
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
                
                if (hologramAnimations.containsKey(location)) {
                    HologramAnimation animation = hologramAnimations.get(location);
                    animation.stopAnimation();
                    hologramAnimations.remove(location);
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
            createGeneratorHologram(location, generator.getType(), generator.getLevel());
            return;
        }
        
        try {
            List<Entity> entities = holograms.get(location);
            if (entities.get(0) instanceof ArmorStand && entities.get(1) instanceof ArmorStand) {
                ArmorStand nameStand = (ArmorStand) entities.get(0);
                ArmorStand infoStand = (ArmorStand) entities.get(1);
                
                if (nameStand.isDead() || infoStand.isDead()) {
                    createGeneratorHologram(location, generator.getType(), generator.getLevel());
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
                createGeneratorHologram(location, generator.getType(), generator.getLevel());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error updating hologram at " + 
                    location.getWorld().getName() + " " + 
                    location.getX() + "," + location.getY() + "," + location.getZ(), e);
            createGeneratorHologram(location, generator.getType(), generator.getLevel());
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
    
    private static void anchorItemExactly(Location blockLocation, boolean shouldRotate) {
        if (!displayItems.containsKey(blockLocation) || 
            displayItems.get(blockLocation) == null || 
            displayItems.get(blockLocation).isDead()) {
            
            Generator generator = plugin.getGeneratorManager().getGenerator(blockLocation);
            if (generator != null) {
                createGeneratorHologram(blockLocation, generator.getType(), generator.getLevel());
                return;
            }
            
            NetworkBlock network = plugin.getNetworkManager().getNetworkAt(blockLocation);
            if (network != null) {
                createNetworkHologram(blockLocation, network);
                return;
            }
            
            return;
        }
        
        Item item = displayItems.get(blockLocation);
        
        double heightOffset = plugin.getConfigManager().getConfig().getDouble("settings.display-item.height-offset", 1.2);
        Location exactLoc = blockLocation.clone().add(0.5, heightOffset, 0.5);
        
        item.teleport(exactLoc);
        item.setVelocity(new Vector(0, 0, 0));
        
        if (shouldRotate) {
            int rotationSpeed = plugin.getConfigManager().getConfig().getInt("settings.display-item.rotation-speed", 2);
            item.setRotation(item.getLocation().getYaw() + rotationSpeed, 0);
        }
    }
    
    public static void setPlayerHologramVisibility(UUID playerUuid, UUID hologramId, boolean visible) {
        if (visible) {
            playerVisibilityOverrides.computeIfAbsent(playerUuid, k -> new HashSet<>()).remove(hologramId);
        } else {
            playerVisibilityOverrides.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(hologramId);
        }
    }
    
    public static boolean isHologramVisibleToPlayer(UUID playerUuid, UUID hologramId) {
        Set<UUID> hiddenHolograms = playerVisibilityOverrides.get(playerUuid);
        return hiddenHolograms == null || !hiddenHolograms.contains(hologramId);
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
        
        if (hologramAnimations.containsKey(location)) {
            HologramAnimation animation = hologramAnimations.get(location);
            animation.stopAnimation();
            hologramAnimations.remove(location);
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
        
        for (HologramAnimation animation : hologramAnimations.values()) {
            animation.stopAnimation();
        }
        
        holograms.clear();
        displayItems.clear();
        anchorTasks.clear();
        hologramIds.clear();
        hologramAnimations.clear();
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
    
    public static void forEachHologram(Consumer<Location> action) {
        for (Location location : new ArrayList<>(holograms.keySet())) {
            action.accept(location);
        }
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
    
    public static void togglePlayerHologramVisibility(Player player) {
        UUID playerUuid = player.getUniqueId();
        boolean hasOverrides = playerVisibilityOverrides.containsKey(playerUuid) && 
                              !playerVisibilityOverrides.get(playerUuid).isEmpty();
        
        if (hasOverrides) {
            playerVisibilityOverrides.remove(playerUuid);
            player.sendMessage("§aHologram visibility restored.");
        } else {
            Set<UUID> allHolograms = new HashSet<>();
            for (UUID hologramId : hologramIds.values()) {
                allHolograms.add(hologramId);
            }
            playerVisibilityOverrides.put(playerUuid, allHolograms);
            player.sendMessage("§7Holograms are now hidden.");
        }
    }
    
    public static void restoreAllHolograms(BlockMint plugin) {
        plugin.getLogger().info("Restoring all holograms...");
        
        // Restore generator holograms
        int generatorCount = 0;
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            createGeneratorHologram(generator.getLocation(), generator.getType(), generator.getLevel());
            generatorCount++;
        }
        
        // Restore network holograms
        int networkCount = 0;
        for (NetworkBlock network : plugin.getNetworkManager().getNetworks().values()) {
            createNetworkHologram(network.getLocation(), network);
            networkCount++;
        }
        
        plugin.getLogger().info("Restored " + generatorCount + " generator holograms and " + 
                               networkCount + " network holograms");
    }
}