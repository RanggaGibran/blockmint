package id.rnggagib.blockmint.config;

import id.rnggagib.BlockMint;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    
    private final BlockMint plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration generatorsConfig;
    private FileConfiguration messagesConfig;
    
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    
    public ConfigManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfigs() {
        createDefaultConfig();
        createGeneratorsConfig();
        createMessagesConfig();
    }
    
    public void reloadConfigs() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        mainConfig = YamlConfiguration.loadConfiguration(configFile);
        configs.put("config", mainConfig);
        
        File generatorsFile = new File(plugin.getDataFolder(), "generators.yml");
        generatorsConfig = YamlConfiguration.loadConfiguration(generatorsFile);
        configs.put("generators", generatorsConfig);
        
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        configs.put("messages", messagesConfig);
        
        plugin.getLogger().info("All configurations reloaded successfully.");
    }
    
    private void createDefaultConfig() {
        plugin.saveDefaultConfig();
        mainConfig = plugin.getConfig();
        configs.put("config", mainConfig);
    }
    
    private void createGeneratorsConfig() {
        File file = new File(plugin.getDataFolder(), "generators.yml");
        
        if (!file.exists()) {
            plugin.saveResource("generators.yml", false);
        }
        
        generatorsConfig = YamlConfiguration.loadConfiguration(file);
        configs.put("generators", generatorsConfig);
    }
    
    private void createMessagesConfig() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        messagesConfig = YamlConfiguration.loadConfiguration(file);
        configs.put("messages", messagesConfig);
    }
    
    public FileConfiguration getConfig() {
        return mainConfig;
    }
    
    public FileConfiguration getGeneratorsConfig() {
        return generatorsConfig;
    }
    
    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }
    
    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }
    
    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        if (config == null) return;
        
        try {
            String fileName = name + ".yml";
            File configFile = new File(plugin.getDataFolder(), fileName);
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + name + " config: " + e.getMessage());
        }
    }
}