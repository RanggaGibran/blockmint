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
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.economy.EconomyManager;
import id.rnggagib.blockmint.chunk.ChunkManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;

import java.util.*;
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
    private EconomyManager economyManager;
    private boolean isFullyEnabled = false;
    private BukkitAudiences adventure;
    private ChunkManager chunkManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize Adventure early in your plugin startup
        this.adventure = BukkitAudiences.create(this);
        
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
            
            // Initialize ChunkManager before loading generators
            chunkManager = new ChunkManager(this);
            
            // After loading generators, register them with the ChunkManager
            getServer().getScheduler().runTaskLater(this, () -> {
                for (Map.Entry<Location, Generator> entry : generatorManager.getActiveGenerators().entrySet()) {
                    chunkManager.registerGenerator(entry.getKey());
                }
                
                // Update the GeneratorTask with our optimized version
                if (generatorTask != null) {
                    generatorTask.cancel();
                }
                
                generatorTask = new GeneratorTask(this);
                generatorTask.runTaskTimer(this, 20L, 20L);
                
                getLogger().info("Performance optimizations initialized");
            }, 40L); // Short delay to ensure everything is initialized
            
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
        
        economyManager = new EconomyManager(this);
        
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
            
            for (Generator generator : generatorManager.getActiveGenerators().values()) {
                blockLocations.add(generator.getLocation());
            }
            
            for (NetworkBlock network : networkManager.getNetworks().values()) {
                blockLocations.add(network.getLocation());
            }
            
            ensureChunksLoaded(blockLocations, chunkLoadRadius, () -> {
                networkManager.restoreNetworkBlocks();
                generatorManager.updateBlocksIfNeeded();
                
                DisplayManager.restoreAllHolograms(this);
                
                getLogger().info("Entity restoration completed");
            });
        });
    }
    
    public void ensureChunksLoaded(Collection<Location> locations, int radius, Runnable callback) {
        Set<ChunkLocation> chunks = new HashSet<>();
        Map<ChunkLocation, Double> prioritizedChunks = new HashMap<>();
        
        for (Location location : locations) {
            if (location.getWorld() == null) continue;
            
            int baseChunkX = location.getBlockX() >> 4;
            int baseChunkZ = location.getBlockZ() >> 4;
            
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    ChunkLocation coords = new ChunkLocation(location.getWorld(), baseChunkX + x, baseChunkZ + z);
                    chunks.add(coords);
                    
                    double distance = Math.sqrt(x * x + z * z);
                    prioritizedChunks.put(coords, distance);
                }
            }
        }
        
        if (chunks.isEmpty()) {
            callback.run();
            return;
        }
        
        int serverTPS = estimateCurrentTPS();
        int dynamicBatchSize = calculateBatchSize(serverTPS, chunks.size());
        
        List<ChunkLocation> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort(Comparator.comparingDouble(prioritizedChunks::get));
        
        AtomicInteger remaining = new AtomicInteger(sortedChunks.size());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        getServer().getScheduler().runTask(this, () -> {
            getLogger().info("Starting to load " + remaining.get() + " chunks with batch size " + dynamicBatchSize);
            processChunkBatch(sortedChunks, 0, dynamicBatchSize, remaining, successCount, failCount, startTime, callback);
        });
    }
    
    private void processChunkBatch(List<ChunkLocation> chunks, int startIndex, int batchSize, 
                                  AtomicInteger remaining, AtomicInteger successCount, AtomicInteger failCount, 
                                  long startTime, Runnable callback) {
        int endIndex = Math.min(startIndex + batchSize, chunks.size());
        boolean needsMoreProcessing = endIndex < chunks.size();
        boolean isLastBatch = !needsMoreProcessing;
        int currentServerTPS = estimateCurrentTPS();
        
        for (int i = startIndex; i < endIndex; i++) {
            ChunkLocation coords = chunks.get(i);
            try {
                if (!coords.world.isChunkLoaded(coords.x, coords.z)) {
                    coords.world.loadChunk(coords.x, coords.z, true);
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to load chunk at " + coords.world.getName() + 
                           " [" + coords.x + ", " + coords.z + "]", e);
                failCount.incrementAndGet();
            }
            
            if (remaining.decrementAndGet() <= 0) {
                long timeTaken = System.currentTimeMillis() - startTime;
                getLogger().info("Chunk loading completed: " + successCount.get() + " chunks loaded, " + 
                             failCount.get() + " failed in " + timeTaken + "ms");
                callback.run();
                return;
            }
        }
        
        if (needsMoreProcessing) {
            int adaptedBatchSize = adaptBatchSize(batchSize, currentServerTPS);
            int delay = calculateDelay(currentServerTPS, adaptedBatchSize);
            
            getServer().getScheduler().runTaskLater(this, () -> {
                processChunkBatch(chunks, endIndex, adaptedBatchSize, remaining, successCount, failCount, startTime, callback);
            }, delay);
        } else if (isLastBatch) {
            long timeTaken = System.currentTimeMillis() - startTime;
            getLogger().info("Chunk loading completed: " + successCount.get() + " chunks loaded, " + 
                         failCount.get() + " failed in " + timeTaken + "ms");
            callback.run();
        }
    }
    
    private int estimateCurrentTPS() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            double memoryUsage = (double) (totalMemory - freeMemory) / maxMemory;
            
            if (memoryUsage > 0.8) {
                return 15;
            } else if (memoryUsage > 0.6) {
                return 17;
            } else {
                return 19;
            }
        } catch (Exception e) {
            return 18;
        }
    }
    
    private int calculateBatchSize(int tps, int totalChunks) {
        if (tps >= 19) {
            return Math.min(10, totalChunks);
        } else if (tps >= 17) {
            return Math.min(5, totalChunks);
        } else if (tps >= 15) {
            return Math.min(3, totalChunks);
        } else {
            return Math.min(2, totalChunks);
        }
    }
    
    private int adaptBatchSize(int currentBatchSize, int tps) {
        if (tps >= 19.5) {
            return currentBatchSize + 1;
        } else if (tps < 18) {
            return Math.max(1, currentBatchSize - 1);
        }
        return currentBatchSize;
    }
    
    private int calculateDelay(int tps, int batchSize) {
        if (tps >= 19) {
            return 1;
        } else if (tps >= 17) {
            return 2;
        } else if (tps >= 15) {
            return 3;
        } else {
            return 4;
        }
    }
    
    @Override
    public void onDisable() {
        isFullyEnabled = false;
        
        // Close the audience to free resources
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        
        stopTasks();
        
        getLogger().info("Removing all generator holograms...");
        DisplayManager.removeAllHolograms();
        
        if (economyManager != null) {
            economyManager.shutdown();
        }
        
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
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public BukkitAudiences getAdventure() {
        return this.adventure;
    }
    
    public ChunkManager getChunkManager() {
        return chunkManager;
    }
    
    public BukkitRunnable getGeneratorTask() {
        return generatorTask;
    }
    
    public static class ChunkLocation {
        final World world;
        final int x;
        final int z;
        
        public ChunkLocation(World world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkLocation that = (ChunkLocation) o;
            return x == that.x && z == that.z && world.equals(that.world);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
    }
}
