package id.rnggagib.blockmint.database;

import id.rnggagib.BlockMint;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final BlockMint plugin;
    private Connection connection;
    
    public DatabaseManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String dbType = config.getString("database.type", "sqlite");
        
        if (dbType.equalsIgnoreCase("mysql")) {
            initializeMySQL();
        } else {
            initializeSQLite();
        }
        
        setupTables();
        verifyTableStructure();
    }
    
    private void initializeSQLite() {
        String fileName = plugin.getConfigManager().getConfig().getString("database.file", "blockmint.db");
        File dataFolder = plugin.getDataFolder();
        
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        
        String jdbcUrl = "jdbc:sqlite:" + dataFolder + File.separator + fileName;
        
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(jdbcUrl);
            plugin.getLogger().info("Successfully connected to SQLite database!");
        } catch (ClassNotFoundException | SQLException e) {
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
}