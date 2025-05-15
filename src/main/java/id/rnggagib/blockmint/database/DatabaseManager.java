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
        this.dbFile = new File(plugin.getDataFolder(), "blockmint.db").getAbsolutePath();
    }
    
    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            createConnection();
            setupTables();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC library not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database!", e);
        }
    }
    
    private void createConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
    }
    
    private void setupTables() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS generators (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "owner TEXT NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "level INTEGER DEFAULT 1, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
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
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database connection!", e);
        }
    }
    
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return getConnection().prepareStatement(sql);
    }
    
    public ResultSet executeQuery(String sql) throws SQLException {
        return getConnection().createStatement().executeQuery(sql);
    }
    
    public int executeUpdate(String sql) throws SQLException {
        return getConnection().createStatement().executeUpdate(sql);
    }
}