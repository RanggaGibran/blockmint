package id.rnggagib.blockmint.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final BlockMint plugin;
    private Connection connection;
    private HikariDataSource connectionPool;
    private final AtomicInteger pendingQueries = new AtomicInteger(0);
    private final ConcurrentHashMap<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, List<BatchOperation>> batchOperations = new ConcurrentHashMap<>();
    private final long batchTimeThreshold = 2000;
    private final int batchSizeThreshold = 20;
    
    private final Map<String, PreparedStatement> statementCache = new ConcurrentHashMap<>();
    private final Map<String, CachedQueryResult<?>> queryResultCache = new ConcurrentHashMap<>();
    private final long DEFAULT_CACHE_EXPIRY = TimeUnit.MINUTES.toMillis(5);
    private final int MAX_CACHE_SIZE = 100;
    
    public DatabaseManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String dbType = config.getString("database.type", "sqlite");
        
        if (dbType.equalsIgnoreCase("mysql")) {
            initializeMySQLPool();
        } else {
            if (!plugin.getDependencyManager().ensureSQLiteDriverLoaded()) {
                plugin.getLogger().severe("Failed to load SQLite driver! Database functionality will not work.");
                return;
            }
            initializeSQLitePool();
        }
        
        setupTables();
        verifyTableStructure();
        
        startBatchProcessor();
        startCacheCleanupTask();
    }
    
    private void initializeSQLitePool() {
        String fileName = plugin.getConfigManager().getConfig().getString("database.file", "blockmint.db");
        File dataFolder = plugin.getDataFolder();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        
        String jdbcUrl = "jdbc:sqlite:" + dataFolder + File.separator + fileName;
        
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(3);
            config.setPoolName("BlockMint-SQLite");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(60000);
            config.setMaxLifetime(1800000);
            
            connectionPool = new HikariDataSource(config);
            connection = connectionPool.getConnection();
            connection.setAutoCommit(true);
            plugin.getLogger().info("Successfully initialized SQLite connection pool!");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite connection pool!", e);
            fallbackToDirectConnection(jdbcUrl);
        }
    }
    
    private void fallbackToDirectConnection(String jdbcUrl) {
        try {
            plugin.getLogger().warning("Falling back to direct database connection...");
            connection = DriverManager.getConnection(jdbcUrl);
            connection.setAutoCommit(true);
            plugin.getLogger().info("Successfully connected to database using direct connection!");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize direct database connection!", e);
        }
    }
    
    private void initializeMySQLPool() {
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
            
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(20);
            hikariConfig.setMinimumIdle(5);
            hikariConfig.setPoolName("BlockMint-MySQL");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            
            connectionPool = new HikariDataSource(hikariConfig);
            connection = connectionPool.getConnection();
            plugin.getLogger().info("Successfully initialized MySQL connection pool!");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize MySQL connection pool!", e);
            fallbackToDirectConnection(jdbcUrl);
        }
    }
    
    private void setupTables() {
        try (Statement statement = getConnection().createStatement()) {
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
            
            // New tables for economic tracking
            statement.execute("CREATE TABLE IF NOT EXISTS economic_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid TEXT NOT NULL, " +
                    "amount REAL NOT NULL, " +
                    "source TEXT NOT NULL, " +
                    "timestamp BIGINT NOT NULL" +
                    ")");
            
            statement.execute("CREATE TABLE IF NOT EXISTS economy_balances (" +
                    "player_uuid TEXT PRIMARY KEY, " +
                    "balance REAL NOT NULL, " +
                    "last_updated BIGINT NOT NULL" +
                    ")");
                    
            statement.execute("CREATE INDEX IF NOT EXISTS idx_generators_owner ON generators(owner)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_generators_type ON generators(type)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_generators_world ON generators(world)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON economic_transactions(timestamp)");
            
            plugin.getLogger().info("Database tables and indexes initialized successfully.");
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
            
            ResultSet playerStatsColumns = getConnection().getMetaData().getColumns(null, null, "player_stats", null);
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
                try (Statement stmt = getConnection().createStatement()) {
                    stmt.execute("ALTER TABLE player_stats RENAME COLUMN generators_placed TO generators_owned");
                }
                plugin.getLogger().info("Migration complete.");
            }
            
            if (!hasPlayerNameColumn) {
                plugin.getLogger().info("Adding player_name column to player_stats table...");
                try (Statement stmt = getConnection().createStatement()) {
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
        if (connectionPool != null) {
            return getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        }
        
        if (connection == null || connection.isClosed()) {
            plugin.getLogger().warning("Database connection lost, reconnecting...");
            initialize();
            
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Could not reconnect to the database!");
            }
        }
        
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    public PreparedStatement getCachedPreparedStatement(String sql) throws SQLException {
        if (statementCache.containsKey(sql)) {
            PreparedStatement stmt = statementCache.get(sql);
            if (!stmt.isClosed()) {
                return stmt;
            } else {
                statementCache.remove(sql);
            }
        }
        
        PreparedStatement stmt = prepareStatement(sql);
        statementCache.put(sql, stmt);
        return stmt;
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
    
    public Connection getConnection() throws SQLException {
        if (connectionPool != null) {
            if (connection == null || connection.isClosed()) {
                connection = connectionPool.getConnection();
            }
            return connection;
        }
        
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is not available!");
        }
        
        return connection;
    }
    
    public void close() {
        clearStatementCache();
        
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Database connection closed successfully.");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error closing database connection!", e);
            }
        }
        
        if (connectionPool != null && !connectionPool.isClosed()) {
            connectionPool.close();
            plugin.getLogger().info("Connection pool closed successfully.");
        }
    }
    
    public void executeAsync(String sql, Consumer<ResultSet> resultHandler) {
        executeAsync(sql, resultHandler, null);
    }
    
    public void executeAsync(String sql, Consumer<ResultSet> resultHandler, Consumer<SQLException> errorHandler) {
        String cacheKey = "query:" + sql;
        CachedQueryResult<ResultSet> cachedResult = (CachedQueryResult<ResultSet>) queryResultCache.get(cacheKey);
        if (cachedResult != null && !cachedResult.isExpired()) {
            if (resultHandler != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    resultHandler.accept(cachedResult.getValue());
                });
            }
            return;
        }
        
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ResultSet resultSet = executeQuery(sql);
                
                if (resultHandler != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            resultHandler.accept(resultSet);
                            
                            if (isCacheableQuery(sql)) {
                                CachedQueryResult<ResultSet> newCache = new CachedQueryResult<>(resultSet, DEFAULT_CACHE_EXPIRY);
                                addToCache(cacheKey, newCache);
                            }
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
                
                invalidateCacheForUpdate(sql);
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
                
                invalidateCacheForUpdate(sql);
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
        return queryWithMapperAsync(sql, mapper, DEFAULT_CACHE_EXPIRY);
    }
    
    public <T> CompletableFuture<T> queryWithMapperAsync(String sql, ResultSetMapper<T> mapper, long cacheTimeMs) {
        CompletableFuture<T> future = new CompletableFuture<>();
        String cacheKey = "mapper:" + sql;
        
        CachedQueryResult<T> cachedResult = (CachedQueryResult<T>) queryResultCache.get(cacheKey);
        if (cachedResult != null && !cachedResult.isExpired()) {
            future.complete(cachedResult.getValue());
            return future;
        }
        
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement stmt = prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                T result = mapper.map(rs);
                future.complete(result);
                
                if (isCacheableQuery(sql)) {
                    CachedQueryResult<T> newCache = new CachedQueryResult<>(result, cacheTimeMs);
                    addToCache(cacheKey, newCache);
                }
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
    
    public <T> T queryWithTransaction(SqlFunction<Connection, T> function) throws SQLException {
        Connection conn = null;
        boolean originalAutoCommit = true;
        
        try {
            conn = getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            T result = function.apply(conn);
            
            conn.commit();
            return result;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.SEVERE, "Error rolling back transaction", rollbackEx);
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException resetEx) {
                    plugin.getLogger().log(Level.WARNING, "Error resetting auto-commit", resetEx);
                }
            }
        }
    }
    
    public CompletableFuture<Void> executeTransactionAsync(SqlConsumer<Connection> consumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        pendingQueries.incrementAndGet();
        UUID taskId = UUID.randomUUID();
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                queryWithTransaction(conn -> {
                    consumer.accept(conn);
                    return null;
                });
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
                plugin.getLogger().log(Level.SEVERE, "Error executing async transaction", e);
            } finally {
                pendingQueries.decrementAndGet();
                activeTasks.remove(taskId);
            }
        });
        
        activeTasks.put(taskId, task);
        return future;
    }
    
    public void addBatchOperation(String sql, Object[] params) {
        UUID playerUUID = UUID.randomUUID(); 
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
        
        Map<String, List<Object[]>> groupedOperations = new HashMap<>();
        for (BatchOperation op : operations) {
            groupedOperations.computeIfAbsent(op.sql, k -> new ArrayList<>()).add(op.params);
        }
        
        for (Map.Entry<String, List<Object[]>> entry : groupedOperations.entrySet()) {
            String sql = entry.getKey();
            List<Object[]> paramSets = entry.getValue();
            
            pendingQueries.incrementAndGet();
            
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Connection conn = getConnection();
                    boolean originalAutoCommit = conn.getAutoCommit();
                    conn.setAutoCommit(false);
                    
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        for (Object[] params : paramSets) {
                            for (int i = 0; i < params.length; i++) {
                                stmt.setObject(i + 1, params[i]);
                            }
                            stmt.addBatch();
                        }
                        
                        stmt.executeBatch();
                        conn.commit();
                        
                        invalidateCacheForUpdate(sql);
                    } catch (SQLException e) {
                        conn.rollback();
                        throw e;
                    } finally {
                        conn.setAutoCommit(originalAutoCommit);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Error executing batch operation", e);
                } finally {
                    pendingQueries.decrementAndGet();
                }
            });
        }
    }
    
    private boolean isCacheableQuery(String sql) {
        String normalizedSql = sql.trim().toLowerCase();
        return normalizedSql.startsWith("select") && !normalizedSql.contains("random()") && 
               !normalizedSql.contains("now()") && !normalizedSql.contains("current_timestamp");
    }
    
    private void invalidateCacheForUpdate(String sql) {
        String normalizedSql = sql.trim().toLowerCase();
        String tableName = extractTableName(normalizedSql);
        if (tableName != null) {
            List<String> keysToRemove = new ArrayList<>();
            for (String key : queryResultCache.keySet()) {
                if (key.contains(tableName)) {
                    keysToRemove.add(key);
                }
            }
            for (String key : keysToRemove) {
                queryResultCache.remove(key);
            }
        }
    }
    
    private String extractTableName(String sql) {
        if (sql.contains("update ")) {
            int updateIndex = sql.indexOf("update ") + 7;
            int spaceIndex = sql.indexOf(' ', updateIndex);
            if (spaceIndex > updateIndex) {
                return sql.substring(updateIndex, spaceIndex).trim();
            }
        } else if (sql.contains("insert into ")) {
            int insertIndex = sql.indexOf("insert into ") + 12;
            int spaceIndex = sql.indexOf(' ', insertIndex);
            if (spaceIndex > insertIndex) {
                return sql.substring(insertIndex, spaceIndex).trim();
            }
        } else if (sql.contains("delete from ")) {
            int deleteIndex = sql.indexOf("delete from ") + 12;
            int spaceIndex = sql.indexOf(' ', deleteIndex);
            if (spaceIndex > deleteIndex) {
                return sql.substring(deleteIndex, spaceIndex).trim();
            }
        }
        return null;
    }
    
    private <T> void addToCache(String key, CachedQueryResult<T> result) {
        if (queryResultCache.size() >= MAX_CACHE_SIZE) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            
            for (Map.Entry<String, CachedQueryResult<?>> entry : queryResultCache.entrySet()) {
                CachedQueryResult<?> value = entry.getValue();
                if (value.getCreationTime() < oldestTime) {
                    oldestTime = value.getCreationTime();
                    oldestKey = entry.getKey();
                }
            }
            
            if (oldestKey != null) {
                queryResultCache.remove(oldestKey);
            }
        }
        
        queryResultCache.put(key, result);
    }
    
    private void startCacheCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            List<String> keysToRemove = new ArrayList<>();
            
            for (Map.Entry<String, CachedQueryResult<?>> entry : queryResultCache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    keysToRemove.add(entry.getKey());
                }
            }
            
            for (String key : keysToRemove) {
                queryResultCache.remove(key);
            }
            
        }, 20 * 60, 20 * 60); // Run every minute
    }
    
    private void clearStatementCache() {
        for (PreparedStatement stmt : statementCache.values()) {
            try {
                stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing prepared statement", e);
            }
        }
        statementCache.clear();
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
    
    public void optimizeDatabase() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Statement stmt = getConnection().createStatement()) {
                if (plugin.getConfigManager().getConfig().getString("database.type", "sqlite").equalsIgnoreCase("sqlite")) {
                    stmt.execute("VACUUM");
                    stmt.execute("ANALYZE");
                    plugin.getLogger().info("SQLite database optimized");
                } else {
                    stmt.execute("OPTIMIZE TABLE generators, player_stats");
                    stmt.execute("ANALYZE TABLE generators, player_stats");
                    plugin.getLogger().info("MySQL tables optimized");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to optimize database", e);
            }
        });
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
    
    private static class CachedQueryResult<T> {
        private final T value;
        private final long expiryTime;
        private final long creationTime;
        
        CachedQueryResult(T value, long cacheTimeMs) {
            this.value = value;
            this.creationTime = System.currentTimeMillis();
            this.expiryTime = this.creationTime + cacheTimeMs;
        }
        
        public T getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
    }
    
    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }
    
    @FunctionalInterface
    public interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }
    
    public interface ResultSetMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}