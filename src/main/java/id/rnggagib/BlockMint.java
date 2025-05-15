package id.rnggagib;

import id.rnggagib.blockmint.commands.CommandManager;
import id.rnggagib.blockmint.config.ConfigManager;
import id.rnggagib.blockmint.database.DatabaseManager;
import id.rnggagib.blockmint.generators.GeneratorManager;
import id.rnggagib.blockmint.gui.GUIManager;
import id.rnggagib.blockmint.listeners.BlockListeners;
import id.rnggagib.blockmint.listeners.ChunkListeners;
import id.rnggagib.blockmint.listeners.GUIListener;
import id.rnggagib.blockmint.listeners.PlayerListeners;
import id.rnggagib.blockmint.placeholders.BlockMintExpansion;
import id.rnggagib.blockmint.tasks.GeneratorTask;
import id.rnggagib.blockmint.utils.DisplayManager;
import id.rnggagib.blockmint.utils.MessageManager;
import id.rnggagib.blockmint.utils.PluginUtils;
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
    private GUIManager guiManager;
    private Economy economy;
    private GeneratorTask generatorTask;
    private int taskId = -1;
    private PluginUtils utils;
    
    @Override
    public void onEnable() {
        instance = this;
        
        if (!setupEconomy()) {
            getLogger().severe("Vault or an Economy plugin not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        loadManagers();
        registerCommands();
        registerListeners();
        startTasks();
        
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BlockMintExpansion(this).register();
            getLogger().info("Registered PlaceholderAPI expansion!");
        }
        
        getLogger().info("BlockMint has been enabled!");
    }
    
    @Override
    public void onDisable() {
        stopTasks();
        DisplayManager.removeAllHolograms();
        
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
        
        guiManager = new GUIManager(this);
        
        utils = new PluginUtils(this);
    }
    
    private void registerCommands() {
        commandManager = new CommandManager(this);
        commandManager.registerCommands();
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new BlockListeners(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListeners(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListeners(this), this);
    }
    
    private void startTasks() {
        int interval = getConfigManager().getConfig().getInt("settings.generator-check-interval", 100);
        generatorTask = new GeneratorTask(this);
        taskId = generatorTask.runTaskTimer(this, interval, interval).getTaskId();
    }
    
    private void stopTasks() {
        if (taskId != -1) {
            getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
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
        stopTasks();
        configManager.reloadConfigs();
        messageManager.reload();
        generatorManager.reloadGenerators();
        startTasks();
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
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }
    
    public GUIManager getGUIManager() {
        return guiManager;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    public PluginUtils getUtils() {
        return utils;
    }
}
