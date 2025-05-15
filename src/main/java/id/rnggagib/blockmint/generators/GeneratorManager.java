package id.rnggagib.blockmint.generators;

import id.rnggagib.BlockMint;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

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
        loadActiveGenerators();
    }
    
    public void reloadGenerators() {
        generatorTypes.clear();
        loadGeneratorTypes();
    }
    
    private void loadGeneratorTypes() {
        ConfigurationSection section = plugin.getConfigManager().getGeneratorsConfig().getConfigurationSection("generators");
        if (section == null) {
            plugin.getLogger().warning("No generators defined in generators.yml!");
            return;
        }
        
        for (String key : section.getKeys(false)) {
            ConfigurationSection genSection = section.getConfigurationSection(key);
            if (genSection == null) continue;
            
            GeneratorType generatorType = new GeneratorType(
                    key,
                    genSection.getString("name", key),
                    genSection.getString("material"),
                    genSection.getDouble("base-value", 10.0),
                    genSection.getDouble("value-multiplier", 1.5),
                    genSection.getInt("max-level", 10),
                    genSection.getLong("generation-time", 60),
                    genSection.getString("texture-value", "")
            );
            
            generatorTypes.put(key, generatorType);
            plugin.getLogger().info("Loaded generator type: " + key);
        }
    }
    
    private void loadActiveGenerators() {
        activeGenerators.clear();
        
        try {
            ResultSet rs = plugin.getDatabaseManager().executeQuery("SELECT * FROM generators");
            while (rs.next()) {
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String type = rs.getString("type");
                String owner = rs.getString("owner");
                int level = rs.getInt("level");
                
                if (!generatorTypes.containsKey(type)) {
                    plugin.getLogger().warning("Unknown generator type: " + type);
                    continue;
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x, y, z);
                Generator generator = new Generator(
                        rs.getInt("id"),
                        UUID.fromString(owner),
                        location,
                        generatorTypes.get(type),
                        level
                );
                
                activeGenerators.put(location, generator);
            }
            
            plugin.getLogger().info("Loaded " + activeGenerators.size() + " active generators");
            
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
        
        GeneratorType generatorType = generatorTypes.get(type);
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "INSERT INTO generators (owner, world, x, y, z, type, level) VALUES (?, ?, ?, ?, ?, ?, 1)"
            );
            stmt.setString(1, owner.toString());
            stmt.setString(2, location.getWorld().getName());
            stmt.setInt(3, location.getBlockX());
            stmt.setInt(4, location.getBlockY());
            stmt.setInt(5, location.getBlockZ());
            stmt.setString(6, type);
            stmt.executeUpdate();
            
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
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
            stmt.executeUpdate();
            
            activeGenerators.remove(location);
            updatePlayerStats(generator.getOwner());
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing generator from database!", e);
        }
        
        return false;
    }
    
    public Generator getGenerator(Location location) {
        return activeGenerators.get(location);
    }
    
    public Map<String, GeneratorType> getGeneratorTypes() {
        return generatorTypes;
    }
    
    public Map<Location, Generator> getActiveGenerators() {
        return activeGenerators;
    }
    
    private void updatePlayerStats(UUID uuid) {
        try {
            int count = 0;
            for (Generator generator : activeGenerators.values()) {
                if (generator.getOwner().equals(uuid)) {
                    count++;
                }
            }
            
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "INSERT OR REPLACE INTO player_stats (uuid, player_name, generators_owned) " +
                            "VALUES (?, ?, ?)"
            );
            stmt.setString(1, uuid.toString());
            stmt.setString(2, plugin.getServer().getOfflinePlayer(uuid).getName());
            stmt.setInt(3, count);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating player stats!", e);
        }
    }
}