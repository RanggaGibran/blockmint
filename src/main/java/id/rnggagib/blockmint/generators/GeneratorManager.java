package id.rnggagib.blockmint.generators;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GeneratorManager {
    
    private final BlockMint plugin;
    private final Map<String, GeneratorType> generatorTypes = new HashMap<>();
    private final Map<Location, Generator> activeGenerators = new ConcurrentHashMap<>();
    
    public GeneratorManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void loadGenerators() {
        loadGeneratorTypes();
        loadGeneratorsFromDatabase();
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
            
            plugin.getLogger().info("Processing generator type: " + key);
            
            String name = typeSection.getString("name", key);
            String material = typeSection.getString("material", "DIAMOND_BLOCK");
            double baseValue = typeSection.getDouble("base-value", 10.0);
            int generationTime = typeSection.getInt("generation-time", 300);
            double valueMultiplier = typeSection.getDouble("value-multiplier", 1.5);
            int maxLevel = typeSection.getInt("max-level", 10);
            double upgradeCostBase = typeSection.getDouble("upgrade-cost-base", 100.0);
            double upgradeCostMultiplier = typeSection.getDouble("upgrade-cost-multiplier", 1.8);
            String textureValue = typeSection.getString("texture-value", "");
            
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
                    textureValue
            );
            
            generatorTypes.put(key, type);
            plugin.getLogger().info("Successfully loaded generator type: " + key);
        }
        
        plugin.getLogger().info("Loaded " + generatorTypes.size() + " generator types!");
    }
    
    public void loadGeneratorsFromDatabase() {
        activeGenerators.clear();
        DisplayManager.removeAllHolograms();
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT * FROM generators"
            );
            
            ResultSet rs = stmt.executeQuery();
            
            int count = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                UUID owner = UUID.fromString(rs.getString("owner"));
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                String type = rs.getString("type");
                int level = rs.getInt("level");
                long lastGeneration = rs.getLong("last_generation");
                
                if (!generatorTypes.containsKey(type)) {
                    plugin.getLogger().warning("Unknown generator type: " + type + " for generator " + id);
                    continue;
                }
                
                GeneratorType generatorType = generatorTypes.get(type);
                
                if (plugin.getServer().getWorld(world) == null) {
                    plugin.getLogger().warning("World " + world + " not found for generator " + id);
                    continue;
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x, y, z);
                
                Generator generator = new Generator(id, owner, location, generatorType, level);
                generator.setLastGeneration(lastGeneration);
                
                activeGenerators.put(location, generator);
                
                if (location.getWorld() != null && location.getChunk().isLoaded()) {
                    if (location.getBlock().getType() != Material.valueOf(generatorType.getMaterial())) {
                        location.getBlock().setType(Material.valueOf(generatorType.getMaterial()));
                    }
                    
                    if (plugin.getConfigManager().getConfig().getBoolean("settings.use-holograms", true)) {
                        DisplayManager.createHologram(plugin, location, generatorType, level);
                    }
                }
                
                count++;
            }
            
            plugin.getLogger().info("Loaded " + count + " generators from database.");
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading generators from database!", e);
        }
    }
    
    public boolean placeGenerator(UUID owner, Location location, String type) {
        if (!generatorTypes.containsKey(type)) {
            return false;
        }
        
        if (activeGenerators.containsKey(location)) {
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
                
                updatePlayerStats(owner);
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error adding generator to database!", e);
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
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing generator from database!", e);
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
            plugin.getLogger().log(Level.SEVERE, "Error updating player stats!", e);
        }
    }
    
    public void reloadGenerators() {
        loadGenerators();
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
    
    public void recreateHolograms() {
        DisplayManager.removeAllHolograms();
        
        for (Map.Entry<Location, Generator> entry : activeGenerators.entrySet()) {
            Location location = entry.getKey();
            Generator generator = entry.getValue();
            
            if (location.getWorld() != null && location.getChunk().isLoaded()) {
                if (location.getBlock().getType() != Material.valueOf(generator.getType().getMaterial())) {
                    location.getBlock().setType(Material.valueOf(generator.getType().getMaterial()));
                }
                
                if (plugin.getConfigManager().getConfig().getBoolean("settings.use-holograms", true)) {
                    DisplayManager.createHologram(plugin, location, generator.getType(), generator.getLevel());
                }
            }
        }
    }
}