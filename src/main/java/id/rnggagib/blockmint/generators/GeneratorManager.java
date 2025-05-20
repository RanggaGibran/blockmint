package id.rnggagib.blockmint.generators;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GeneratorManager {
    
    private final BlockMint plugin;
    private final Map<String, GeneratorType> generatorTypes = new HashMap<>();
    private final Map<Location, Generator> activeGenerators = new ConcurrentHashMap<>();
    private int databaseGeneratorCount = 0;
    private int loadedGeneratorCount = 0;
    private int activeHologramCount = 0;
    private List<Generator> pendingGenerators = new ArrayList<>();
    
    public GeneratorManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void loadGeneratorTypes() {
        generatorTypes.clear();
        
        FileConfiguration config = plugin.getConfigManager().getGeneratorsConfig();
        
        plugin.getLogger().info("Loading generator types from configuration...");
        
        ConfigurationSection typesSection = config.getConfigurationSection("types");
        
        if (typesSection == null) {
            plugin.getLogger().warning("No generator types found in configuration! Section 'types' is missing");
            plugin.getLogger().info("Available sections: " + config.getKeys(false));
            return;
        }
        
        for (String key : typesSection.getKeys(false)) {
            ConfigurationSection typeSection = typesSection.getConfigurationSection(key);
            if (typeSection == null) {
                plugin.getLogger().warning("Type section for " + key + " is null, skipping");
                continue;
            }
            
            String name = typeSection.getString("name", key);
            String material = typeSection.getString("material", "DIAMOND_BLOCK");
            double baseValue = typeSection.getDouble("base-value", 10.0);
            int generationTime = typeSection.getInt("generation-time", 300);
            double valueMultiplier = typeSection.getDouble("value-multiplier", 1.5);
            int maxLevel = typeSection.getInt("max-level", 10);
            double upgradeCostBase = typeSection.getDouble("upgrade-cost-base", 100.0);
            double upgradeCostMultiplier = typeSection.getDouble("upgrade-cost-multiplier", 1.8);
            String textureValue = typeSection.getString("texture-value", "");
            
            // Load evolution path information
            String evolutionPath = "";
            int evolutionRequiredUsage = 0;
            double evolutionRequiredResources = 0;
            double evolutionCost = 0;
            
            if (typeSection.contains("evolution")) {
                ConfigurationSection evolutionSection = typeSection.getConfigurationSection("evolution");
                if (evolutionSection != null) {
                    evolutionPath = evolutionSection.getString("path", "");
                    evolutionRequiredUsage = evolutionSection.getInt("required-usage", 100);
                    evolutionRequiredResources = evolutionSection.getDouble("required-resources", 1000.0);
                    evolutionCost = evolutionSection.getDouble("evolution-cost", 500.0);
                }
            }
            
            GeneratorType type = new GeneratorType(
                    key, 
                    name, 
                    material, 
                    baseValue, 
                    generationTime, 
                    valueMultiplier, 
                    maxLevel, 
                    upgradeCostBase, 
                    upgradeCostMultiplier,
                    textureValue,
                    evolutionPath,
                    evolutionRequiredUsage,
                    evolutionRequiredResources,
                    evolutionCost
            );
            
            generatorTypes.put(key, type);
        }
        
        plugin.getLogger().info("Loaded " + generatorTypes.size() + " generator types!");
    }
    
    public void loadGeneratorsFromDatabaseAsync() {
        activeGenerators.clear();
        pendingGenerators.clear();
        databaseGeneratorCount = 0;
        loadedGeneratorCount = 0;
        
        plugin.getLogger().info("Loading generator data from database...");
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT * FROM generators"
            );
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                databaseGeneratorCount++;
                
                int id = rs.getInt("id");
                UUID owner = UUID.fromString(rs.getString("owner"));
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String type = rs.getString("type");
                int level = rs.getInt("level");
                long lastGeneration = rs.getLong("last_generation");
                
                // Load evolution data (defaults to 0 if columns don't exist)
                int usageCount = 0;
                double resourcesGenerated = 0.0;
                try {
                    usageCount = rs.getInt("usage_count");
                    resourcesGenerated = rs.getDouble("resources_generated");
                } catch (SQLException e) {
                    // Columns might not exist yet, that's fine
                }
                
                if (!generatorTypes.containsKey(type)) {
                    plugin.getLogger().warning("Unknown generator type: " + type + " for generator " + id + ", skipping");
                    continue;
                }
                
                GeneratorType generatorType = generatorTypes.get(type);
                
                if (plugin.getServer().getWorld(world) == null) {
                    plugin.getLogger().warning("World " + world + " not found for generator " + id + ", skipping");
                    continue;
                }
                
                try {
                    Location location = new Location(plugin.getServer().getWorld(world), x, y, z);
                    
                    Generator generator = new Generator(id, owner, location, generatorType, level, usageCount, resourcesGenerated);
                    generator.setLastGeneration(lastGeneration);
                    
                    activeGenerators.put(location, generator);
                    pendingGenerators.add(generator);
                    loadedGeneratorCount++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error loading generator " + id + ": " + e.getMessage());
                }
            }
            
            plugin.getLogger().info("Loaded " + loadedGeneratorCount + " out of " + databaseGeneratorCount + " generators from database.");
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading generators from database!", e);
        }
    }
    
    public void processLoadedGenerators() {
        plugin.getLogger().info("Processing " + pendingGenerators.size() + " generators on main thread...");
        
        int blocksPlaced = 0;
        
        for (Generator generator : pendingGenerators) {
            Location location = generator.getLocation();
            
            if (location.getWorld() == null) {
                continue;
            }
            
            if (!location.getChunk().isLoaded()) {
                plugin.getLogger().fine("Chunk not loaded for generator at " + formatLocation(location) + ", skipping block placement");
                continue;
            }
            
            try {
                if (location.getBlock().getType() != Material.valueOf(generator.getType().getMaterial())) {
                    location.getBlock().setType(Material.valueOf(generator.getType().getMaterial()));
                    blocksPlaced++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error setting block for generator at " + formatLocation(location) + ": " + e.getMessage());
            }
        }
        
        pendingGenerators.clear();
        plugin.getLogger().info("Generator processing complete: " + blocksPlaced + " blocks placed");
    }
    
    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + " [" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]";
    }
    
    public boolean placeGenerator(UUID owner, Location location, String type) {
        if (!generatorTypes.containsKey(type)) {
            plugin.getLogger().warning("Attempted to place unknown generator type: " + type);
            return false;
        }
        
        if (activeGenerators.containsKey(location)) {
            plugin.getLogger().warning("Attempted to place generator where one already exists at " + formatLocation(location));
            return false;
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "INSERT INTO generators (owner, world, x, y, z, type, level, last_generation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );
            stmt.setString(1, owner.toString());
            stmt.setString(2, location.getWorld().getName());
            stmt.setDouble(3, location.getX());
            stmt.setDouble(4, location.getY());
            stmt.setDouble(5, location.getZ());
            stmt.setString(6, type);
            stmt.setInt(7, 1);
            stmt.setLong(8, System.currentTimeMillis());
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                int id = -1;
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    id = rs.getInt(1);
                }
                
                GeneratorType generatorType = generatorTypes.get(type);
                Generator generator = new Generator(id, owner, location, generatorType, 1);
                activeGenerators.put(location, generator);
                loadedGeneratorCount++;
                
                updatePlayerStats(owner);
                
                plugin.getLogger().fine("Successfully placed generator ID " + id + " at " + formatLocation(location));
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error adding generator to database: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    public boolean removeGenerator(Location location) {
        Generator generator = activeGenerators.get(location);
        
        if (generator == null) {
            return false;
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "DELETE FROM generators WHERE id = ?"
            );
            stmt.setInt(1, generator.getId());
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                activeGenerators.remove(location);
                loadedGeneratorCount--;
                
                DisplayManager.removeHologram(location);
                activeHologramCount--;
                
                plugin.getLogger().fine("Successfully removed generator ID " + generator.getId() + " at " + formatLocation(location));
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing generator from database: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    private void updatePlayerStats(UUID playerUUID) {
        try {
            PreparedStatement selectStmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT COUNT(*) FROM player_stats WHERE uuid = ?"
            );
            selectStmt.setString(1, playerUUID.toString());
            ResultSet rs = selectStmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                PreparedStatement updateStmt = plugin.getDatabaseManager().prepareStatement(
                        "UPDATE player_stats SET generators_owned = generators_owned + 1 WHERE uuid = ?"
                );
                updateStmt.setString(1, playerUUID.toString());
                updateStmt.executeUpdate();
            } else {
                PreparedStatement insertStmt = plugin.getDatabaseManager().prepareStatement(
                        "INSERT INTO player_stats (uuid, player_name, generators_owned, total_earnings) VALUES (?, ?, 1, 0.0)"
                );
                insertStmt.setString(1, playerUUID.toString());
                insertStmt.setString(2, plugin.getServer().getOfflinePlayer(playerUUID).getName());
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating player stats: " + e.getMessage(), e);
        }
    }
    
    public void reloadGeneratorTypes() {
        loadGeneratorTypes();
    }
    
    public void recreateHolograms() {
        DisplayManager.removeAllHolograms();
        activeHologramCount = 0;
        
        int recreated = 0;
        for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
            Location location = entry.getKey();
            Generator generator = entry.getValue();
            
            if (location.getWorld() != null && location.getChunk().isLoaded()) {
                try {
                    if (plugin.getConfigManager().getConfig().getBoolean("settings.use-holograms", true)) {
                        DisplayManager.createHologram(plugin, location, generator.getType(), generator.getLevel());
                        activeHologramCount++;
                        recreated++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error recreating hologram at " + formatLocation(location) + ": " + e.getMessage());
                }
            }
        }
        
        plugin.getLogger().info("Hologram recreation complete: " + recreated + " holograms created");
    }
    
    public void updateBlocksIfNeeded() {
        int updated = 0;
        
        for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
            Location location = entry.getKey();
            Generator generator = entry.getValue();
            
            if (location.getWorld() != null && location.getChunk().isLoaded()) {
                if (location.getBlock().getType() != Material.valueOf(generator.getType().getMaterial())) {
                    location.getBlock().setType(Material.valueOf(generator.getType().getMaterial()));
                    updated++;
                }
            }
        }
        
        if (updated > 0) {
            plugin.getLogger().info("Updated " + updated + " generator blocks");
        }
    }
    
    public void handleChunkLoad(Chunk chunk) {
        for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
            Location location = entry.getKey();
            
            if (isLocationInChunk(location, chunk)) {
                Generator generator = entry.getValue();
                
                if (location.getBlock().getType() != Material.valueOf(generator.getType().getMaterial())) {
                    location.getBlock().setType(Material.valueOf(generator.getType().getMaterial()));
                }
                
                if (plugin.getConfigManager().getConfig().getBoolean("settings.use-holograms", true) && 
                    !DisplayManager.hasHologram(location)) {
                    DisplayManager.createHologram(plugin, location, generator.getType(), generator.getLevel());
                    activeHologramCount++;
                }
            }
        }
    }
    
    public void handleChunkUnload(Chunk chunk) {
        int removedHolograms = 0;
        
        for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
            Location location = entry.getKey();
            
            if (isLocationInChunk(location, chunk)) {
                if (DisplayManager.hasHologram(location)) {
                    DisplayManager.removeHologram(location);
                    activeHologramCount--;
                    removedHolograms++;
                }
            }
        }
        
        if (removedHolograms > 0 && plugin.getConfigManager().getConfig().getBoolean("settings.debug.chunk-events", false)) {
            plugin.getLogger().fine("Removed " + removedHolograms + " holograms due to chunk unload at " + 
                    chunk.getWorld().getName() + " [" + chunk.getX() + ", " + chunk.getZ() + "]");
        }
    }
    
    private boolean isLocationInChunk(Location location, Chunk chunk) {
        return location.getWorld().equals(chunk.getWorld()) 
                && location.getBlockX() >> 4 == chunk.getX() 
                && location.getBlockZ() >> 4 == chunk.getZ();
    }
    
    public Generator getGenerator(Location location) {
        return activeGenerators.get(location);
    }
    
    public Map<Location, Generator> getActiveGenerators() {
        return activeGenerators;
    }
    
    public Map<String, GeneratorType> getGeneratorTypes() {
        return generatorTypes;
    }
    
    public int getActiveGeneratorCount() {
        return loadedGeneratorCount;
    }
    
    public int getDatabaseGeneratorCount() {
        return databaseGeneratorCount;
    }
    
    public int getActiveHologramCount() {
        return activeHologramCount;
    }
}