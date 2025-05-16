package id.rnggagib;

import id.rnggagib.blockmint.commands.CommandManager;
import id.rnggagib.blockmint.config.ConfigManager;
import id.rnggagib.blockmint.database.DatabaseManager;
import id.rnggagib.blockmint.generators.GeneratorManager;
import id.rnggagib.blockmint.tasks.GeneratorTask;
import id.rnggagib.blockmint.gui.GUIManager;
import id.rnggagib.blockmint.listeners.BlockListeners;
import id.rnggagib.blockmint.listeners.ChunkListeners;
import id.rnggagib.blockmint.listeners.GUIListener;
import id.rnggagib.blockmint.listeners.PlayerListeners;
import id.rnggagib.blockmint.listeners.ExplosionListener;
import id.rnggagib.blockmint.listeners.NetworkBlockListener;
import id.rnggagib.blockmint.placeholders.BlockMintExpansion;
import id.rnggagib.blockmint.utils.DisplayManager;
import id.rnggagib.blockmint.utils.MessageManager;
import id.rnggagib.blockmint.utils.PluginUtils;
import id.rnggagib.blockmint.network.NetworkManager;
import id.rnggagib.blockmint.gui.NetworkGUIManager;
import id.rnggagib.blockmint.utils.DependencyManager;
import id.rnggagib.blockmint.network.NetworkBlock;
import id.rnggagib.blockmint.network.NetworkTier;
import id.rnggagib.blockmint.generators.Generator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
    private NetworkManager networkManager;
    private NetworkGUIManager networkGUIManager;
    private DependencyManager dependencyManager;
    private boolean isFullyEnabled = false;
    
    @Override
    public void onEnable() {
        instance = this;
        
        if (!setupEconomy()) {
            getLogger().severe("Vault not found or no economy plugin installed! Disabling BlockMint...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        try {
            loadConfig();
            
            dependencyManager = new DependencyManager(this);
            initializeDatabase();
            registerEvents();
            
            getServer().getScheduler().runTaskLater(this, () -> {
                loadManagers();
                registerCommands();
                startTasks();
                
                DisplayManager.initialize(this);
                
                getServer().getScheduler().runTaskLater(this, () -> {
                    executeDelayedStartup();
                }, 40L);
            }, 20L);
            
            getLogger().info("BlockMint has been enabled!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error enabling BlockMint", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    private void initializeDatabase() {
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
    }
    
    private void loadConfig() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        messageManager = new MessageManager(this);
    }
    
    private void loadManagers() {
        generatorManager = new GeneratorManager(this);
        generatorManager.loadGeneratorTypes();
        
        networkManager = new NetworkManager(this);
        
        guiManager = new GUIManager(this);
        networkGUIManager = new NetworkGUIManager(this);
        
        loadDataSequence();
    }
    
    private void loadDataSequence() {
        getLogger().info("Starting plugin data loading sequence...");
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            generatorManager.loadGeneratorsFromDatabaseAsync();
            
            getServer().getScheduler().runTask(this, () -> {
                generatorManager.processLoadedGenerators();
                
                getServer().getScheduler().runTaskLater(this, () -> {
                    networkManager.initialize();
                    
                    getServer().getScheduler().runTaskLater(this, () -> {
                        restoreEntities();
                    }, 20L);
                }, 20L);
            });
        });
    }
    
    private void executeDelayedStartup() {
        getLogger().info("Running delayed startup tasks...");
        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BlockMintExpansion(this).register();
            getLogger().info("Registered PlaceholderAPI expansion");
        }
        
        isFullyEnabled = true;
    }
    
    private void restoreEntities() {
        getLogger().info("Restoring all entities...");
        
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            int chunkLoadRadius = getConfigManager().getConfig().getInt("settings.chunk-load-radius", 3);
            
            Set<Location> blockLocations = new HashSet<>();
            
            // Collect generator locations
            for (Generator generator : generatorManager.getActiveGenerators().values()) {
                blockLocations.add(generator.getLocation());
            }
            
            // Collect network locations
            for (NetworkBlock network : networkManager.getNetworks().values()) {
                blockLocations.add(network.getLocation());
            }
            
            ensureChunksLoaded(blockLocations, chunkLoadRadius, () -> {
                // Restore physical blocks
                networkManager.restoreNetworkBlocks();
                generatorManager.updateBlocksIfNeeded();
                
                // Create holograms
                DisplayManager.restoreAllHolograms(this);
                
                getLogger().info("Entity restoration completed");
            });
        });
    }
    
    public void ensureChunksLoaded(Collection<Location> locations, int radius, Runnable callback) {
        Set<ChunkCoordinates> chunks = new HashSet<>();
        
        for (Location location : locations) {
            if (location.getWorld() == null) continue;
            
            int baseChunkX = location.getBlockX() >> 4;
            int baseChunkZ = location.getBlockZ() >> 4;
            
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    chunks.add(new ChunkCoordinates(location.getWorld(), baseChunkX + x, baseChunkZ + z));
                }
            }
        }
        
        if (chunks.isEmpty()) {
            callback.run();
            return;
        }
        
        AtomicInteger remaining = new AtomicInteger(chunks.size());
        List<ChunkCoordinates> chunkList = new ArrayList<>(chunks);
        int batchSize = 4;
        
        getServer().getScheduler().runTask(this, () -> {
            processChunkBatch(chunkList, 0, batchSize, remaining, callback);
        });
    }
    
    private void processChunkBatch(List<ChunkCoordinates> chunks, int startIndex, int batchSize, AtomicInteger remaining, Runnable callback) {
        int endIndex = Math.min(startIndex + batchSize, chunks.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            ChunkCoordinates coords = chunks.get(i);
            coords.world.loadChunk(coords.x, coords.z, true);
            
            if (remaining.decrementAndGet() <= 0) {
                callback.run();
                return;
            }
        }
        
        if (endIndex < chunks.size()) {
            getServer().getScheduler().runTaskLater(this, () -> {
                processChunkBatch(chunks, endIndex, batchSize, remaining, callback);
            }, 1L);
        }
    }
    
    private static class ChunkCoordinates {
        final World world;
        final int x;
        final int z;
        
        public ChunkCoordinates(World world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoordinates that = (ChunkCoordinates) o;
            return x == that.x && z == that.z && world.equals(that.world);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
    }
    
    @Override
    public void onDisable() {
        isFullyEnabled = false;
        
        stopTasks();
        
        getLogger().info("Removing all generator holograms...");
        DisplayManager.removeAllHolograms();
        
        if (networkManager != null) {
            networkManager.shutdown();
        }
        
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        if (messageManager != null) {
            messageManager.close();
        }
        
        getLogger().info("BlockMint has been disabled!");
    }
    
    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new BlockListeners(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListeners(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListeners(this), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(this), this);
        getServer().getPluginManager().registerEvents(new NetworkBlockListener(this), this);
    }
    
    private void registerCommands() {
        commandManager = new CommandManager(this);
        commandManager.registerCommands();
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
        configManager.reloadConfigs();
        generatorManager.reloadGeneratorTypes();
        generatorManager.recreateHolograms();
        getLogger().info("BlockMint configuration reloaded");
    }
    
    public boolean isFullyEnabled() {
        return isFullyEnabled;
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
        if (utils == null) {
            utils = new PluginUtils(this);
        }
        return utils;
    }
    
    public NetworkManager getNetworkManager() {
        return networkManager;
    }
    
    public NetworkGUIManager getNetworkGUIManager() {
        return networkGUIManager;
    }
    
    public DependencyManager getDependencyManager() {
        return dependencyManager;
    }
}
