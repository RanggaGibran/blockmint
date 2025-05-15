package id.rnggagib.blockmint.database;

import id.rnggagib.BlockMint;

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
    private final String dbFile;
    
    public DatabaseManager(BlockMint plugin) {
        this.plugin = plugin;
        this.dbFile = "blockmint.db";
    }
    
    public void initialize() {
        try {
            createConnection();
            setupTables();
            plugin.getLogger().info("Database connection established!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }
    
    private void createConnection() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        String url = "jdbc:sqlite:" + plugin.getDataFolder() + File.separator + dbFile;
        connection = DriverManager.getConnection(url);
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
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up database tables!", e);
        }
    }
    
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            createConnection();
        }
        return connection;
    }
    
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }
    
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return getConnection().prepareStatement(sql);
    }
    
    public PreparedStatement prepareStatement(String sql, boolean returnGeneratedKeys) throws SQLException {
        return getConnection().prepareStatement(sql, returnGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
    }
    
    public ResultSet executeQuery(String sql) throws SQLException {
        return getConnection().createStatement().executeQuery(sql);
    }
    
    public int executeUpdate(String sql) throws SQLException {
        return getConnection().createStatement().executeUpdate(sql);
    }
}