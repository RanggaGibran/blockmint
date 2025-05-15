package id.rnggagib;

import id.rnggagib.blockmint.commands.CommandManager;
import id.rnggagib.blockmint.config.ConfigManager;
import id.rnggagib.blockmint.database.DatabaseManager;
import id.rnggagib.blockmint.generators.GeneratorManager;
import id.rnggagib.blockmint.listeners.BlockListeners;
import id.rnggagib.blockmint.listeners.PlayerListeners;
import id.rnggagib.blockmint.utils.MessageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class BlockMint extends JavaPlugin {
    
    private static BlockMint instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private CommandManager commandManager;
    private DatabaseManager databaseManager;
    private GeneratorManager generatorManager;
    private Economy economy;
    
    @Override
    public void onEnable() {
        instance = this;
        
        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        loadManagers();
        registerCommands();
        registerListeners();
        
        getLogger().info("BlockMint has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("BlockMint has been disabled!");
    }
    
    private void loadManagers() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        
        messageManager = new MessageManager(this);
        
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        
        generatorManager = new GeneratorManager(this);
        generatorManager.loadGenerators();
    }
    
    private void registerCommands() {
        commandManager = new CommandManager(this);
        commandManager.registerCommands();
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new BlockListeners(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListeners(this), this);
    }
    
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    public void reload() {
        configManager.reloadConfigs();
        messageManager.reload();
        generatorManager.reloadGenerators();
    }
    
    public static BlockMint getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }
    
    public Economy getEconomy() {
        return economy;
    }
}
