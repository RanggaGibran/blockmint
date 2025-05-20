package id.rnggagib.blockmint.database;

import id.rnggagib.BlockMint;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final BlockMint plugin;
    private Connection connection;
    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    private final ConcurrentHashMap<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, List<BatchOperation>> batchOperations = new ConcurrentHashMap<>();
    private final long batchTimeThreshold = 2000; // 2 seconds
    private final int batchSizeThreshold = 20; // 20 operations
    
    public DatabaseManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String dbType = config.getString("database.type", "sqlite");
        
        if (dbType.equalsIgnoreCase("mysql")) {
            initializeMySQL();
        } else {
            if (!plugin.getDependencyManager().ensureSQLiteDriverLoaded()) {
                plugin.getLogger().severe("Failed to load SQLite driver! Database functionality will not work.");
                return;
            }
            initializeSQLite();
        }
        
        setupTables();
        verifyTableStructure();
        
        startBatchProcessor();
    }
    
    private void initializeSQLite() {
        String fileName = plugin.getConfigManager().getConfig().getString("database.file", "blockmint.db");
        File dataFolder = plugin.getDataFolder();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        
        String jdbcUrl = "jdbc:sqlite:" + dataFolder + File.separator + fileName;
        
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            connection.setAutoCommit(true);
            plugin.getLogger().info("Successfully connected to SQLite database!");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database!", e);
        }
    }
    
    private void initializeMySQL() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String host = config.getString("database.mysql.host", "localhost");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "blockmint");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "");
        boolean useSSL = config.getBoolean("database.mysql.ssl", false);
        
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&allowPublicKeyRetrieval=true";
        
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            plugin.getLogger().info("Successfully connected to MySQL database!");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL database!", e);
        }
    }
    
    private void setupTables() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS generators (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "owner TEXT NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "level INTEGER DEFAULT 1, " +
                    "last_generation BIGINT DEFAULT 0" +
                    ")");
            
            statement.execute("CREATE TABLE IF NOT EXISTS player_stats (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "player_name TEXT NOT NULL, " +
                    "generators_owned INTEGER DEFAULT 0, " +
                    "total_earnings REAL DEFAULT 0.0" +
                    ")");
            
            plugin.getLogger().info("Database tables initialized successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up database tables!", e);
        }
    }
    
    private void verifyTableStructure() {
        try {
            boolean playerStatsNeedsMigration = false;
            boolean hasGeneratorsPlacedColumn = false;
            boolean hasGeneratorsOwnedColumn = false;
            boolean hasPlayerNameColumn = false;
            
            ResultSet playerStatsColumns = connection.getMetaData().getColumns(null, null, "player_stats", null);
            while (playerStatsColumns.next()) {
                String columnName = playerStatsColumns.getString("COLUMN_NAME");
                if ("generators_placed".equals(columnName)) {
                    hasGeneratorsPlacedColumn = true;
                }
                if ("generators_owned".equals(columnName)) {
                    hasGeneratorsOwnedColumn = true;
                }
                if ("player_name".equals(columnName)) {
                    hasPlayerNameColumn = true;
                }
            }
            
            if (hasGeneratorsPlacedColumn && !hasGeneratorsOwnedColumn) {
                plugin.getLogger().info("Migrating player_stats table from generators_placed to generators_owned...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE player_stats RENAME COLUMN generators_placed TO generators_owned");
                }
                plugin.getLogger().info("Migration complete.");
            }
            
            if (!hasPlayerNameColumn) {
                plugin.getLogger().info("Adding player_name column to player_stats table...");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("ALTER TABLE player_stats ADD COLUMN player_name TEXT DEFAULT 'Unknown'");
                }
                plugin.getLogger().info("Column added successfully.");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error verifying table structure: " + e.getMessage());
            plugin.getLogger().log(Level.INFO, "This is not critical if using SQLite with older versions.");
        }
    }
    
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        if (connection == null || connection.isClosed()) {
            plugin.getLogger().warning("Database connection lost, reconnecting...");
            initialize();
            
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Could not reconnect to the database!");
            }
        }
        
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    public ResultSet executeQuery(String sql) throws SQLException {
        try (PreparedStatement stmt = prepareStatement(sql)) {
            return stmt.executeQuery();
        }
    }
    
    public int executeUpdate(String sql) throws SQLException {
        try (PreparedStatement stmt = prepareStatement(sql)) {
            return stmt.executeUpdate();
        }
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Database connection closed successfully.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error closing database connection!", e);
            }
        }
    }
    
    public void executeAsync(String sql, Consumer<ResultSet> resultHandler) {
        executeAsync(sql, resultHandler, null);
    }
    
    public void executeAsync(String sql, Consumer<ResultSet> resultHandler, Consumer<SQLException> errorHandler) {
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ResultSet resultSet = executeQuery(sql);
                
                if (resultHandler != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            resultHandler.accept(resultSet);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Error in query result handler", e);
                        } finally {
                            try {
                                resultSet.close();
                            } catch (SQLException e) {
                                plugin.getLogger().log(Level.WARNING, "Error closing result set", e);
                            }
                        }
                    });
                }
            } catch (SQLException e) {
                if (errorHandler != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> errorHandler.accept(e));
                } else {
                    plugin.getLogger().log(Level.SEVERE, "Error executing async query: " + sql, e);
                }
            } finally {
                pendingQueries.decrementAndGet();
                activeTasks.remove(taskId);
            }
        });
        
        activeTasks.put(taskId, task);
    }
    
    public void updateAsync(String sql, Consumer<Integer> resultHandler, Consumer<SQLException> errorHandler) {
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int result = executeUpdate(sql);
                
                if (resultHandler != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> resultHandler.accept(result));
                }
            } catch (SQLException e) {
                if (errorHandler != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> errorHandler.accept(e));
                } else {
                    plugin.getLogger().log(Level.SEVERE, "Error executing async update: " + sql, e);
                }
            } finally {
                pendingQueries.decrementAndGet();
                activeTasks.remove(taskId);
            }
        });
        
        activeTasks.put(taskId, task);
    }
    
    public void updateAsync(String sql) {
        updateAsync(sql, null, null);
    }
    
    public CompletableFuture<ResultSet> queryAsync(String sql) {
        CompletableFuture<ResultSet> future = new CompletableFuture<>();
        
        executeAsync(sql, 
            resultSet -> future.complete(resultSet), 
            error -> future.completeExceptionally(error)
        );
        
        return future;
    }
    
    public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = prepareStatement(sql);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                int result = stmt.executeUpdate();
                future.complete(result);
            } catch (SQLException e) {
                future.completeExceptionally(e);
                plugin.getLogger().log(Level.SEVERE, "Error executing async update with parameters", e);
            } finally {
                pendingQueries.decrementAndGet();
                activeTasks.remove(taskId);
            }
        });
        
        activeTasks.put(taskId, task);
        return future;
    }
    
    public <T> CompletableFuture<T> queryWithMapperAsync(String sql, ResultSetMapper<T> mapper) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                T result = mapper.map(rs);
                future.complete(result);
            } catch (SQLException e) {
                future.completeExceptionally(e);
                plugin.getLogger().log(Level.SEVERE, "Error executing async query with mapper", e);
            } finally {
                pendingQueries.decrementAndGet();
                activeTasks.remove(taskId);
            }
        });
        
        activeTasks.put(taskId, task);
        return future;
    }
    
    public void addBatchOperation(String sql, Object[] params) {
        UUID playerUUID = UUID.randomUUID(); // Generic batch for non-player operations
        addBatchOperation(playerUUID, sql, params);
    }
    
    public void addBatchOperation(UUID playerUUID, String sql, Object[] params) {
        BatchOperation operation = new BatchOperation(sql, params, System.currentTimeMillis());
        
        batchOperations.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(operation);
        
        List<BatchOperation> playerBatch = batchOperations.get(playerUUID);
        if (playerBatch.size() >= batchSizeThreshold) {
            executeBatch(playerUUID);
        }
    }
    
    private void startBatchProcessor() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::processBatchOperations, 100L, 100L);
    }
    
    private void processBatchOperations() {
        long now = System.currentTimeMillis();
        
        for (UUID playerUUID : new ArrayList<>(batchOperations.keySet())) {
            List<BatchOperation> operations = batchOperations.get(playerUUID);
            if (operations == null || operations.isEmpty()) continue;
            
            BatchOperation oldest = operations.get(0);
            if (now - oldest.timestamp > batchTimeThreshold) {
                executeBatch(playerUUID);
            }
        }
    }
    
    private void executeBatch(UUID playerUUID) {
        List<BatchOperation> operations = batchOperations.remove(playerUUID);
        if (operations == null || operations.isEmpty()) return;
        
        // Group operations by SQL query
        Map<String, List<Object[]>> groupedOperations = new HashMap<>();
        for (BatchOperation op : operations) {
            groupedOperations.computeIfAbsent(op.sql, k -> new ArrayList<>()).add(op.params);
        }
        
        // Execute each group as a batch
        for (Map.Entry<String, List<Object[]>> entry : groupedOperations.entrySet()) {
            String sql = entry.getKey();
            List<Object[]> paramSets = entry.getValue();
            
            pendingQueries.incrementAndGet();
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try (PreparedStatement stmt = prepareStatement(sql)) {
                    for (Object[] params : paramSets) {
                        for (int i = 0; i < params.length; i++) {
                            stmt.setObject(i + 1, params[i]);
                        }
                        stmt.addBatch();
                    }
                    
                    stmt.executeBatch();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing batch operation", e);
                } finally {
                    pendingQueries.decrementAndGet();
                }
            });
        }
    }
    
    public void waitForPendingOperations() {
        while (pendingQueries.get() > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    public int getPendingQueriesCount() {
        return pendingQueries.get();
    }
    
    public void cancelAllTasks() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }
    
    private static class BatchOperation {
        final String sql;
        final Object[] params;
        final long timestamp;
        
        BatchOperation(String sql, Object[] params, long timestamp) {
            this.sql = sql;
            this.params = params;
            this.timestamp = timestamp;
        }
    }
    
    public interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}