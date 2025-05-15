package id.rnggagib.blockmint.utils;

import id.rnggagib.BlockMint;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.logging.Level;

public class DependencyManager {
    
    private final BlockMint plugin;
    private boolean sqliteLoaded = false;
    private static final String SQLITE_VERSION = "3.43.0.0";
    private static final String SQLITE_URL = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.43.0.0/sqlite-jdbc-3.43.0.0.jar";
    
    public DependencyManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public boolean ensureSQLiteDriverLoaded() {
        if (sqliteLoaded) {
            return true;
        }
        
        try {
            // Try to use the SQLite driver already available in the system
            Class.forName("org.sqlite.JDBC");
            sqliteLoaded = true;
            plugin.getLogger().info("Using system SQLite driver");
            return true;
        } catch (ClassNotFoundException e) {
            // If not available, download and load it
            plugin.getLogger().info("SQLite driver not found in system. Attempting to download...");
            return downloadAndLoadSQLite();
        }
    }
    
    private boolean downloadAndLoadSQLite() {
        File libFolder = new File(plugin.getDataFolder(), "lib");
        if (!libFolder.exists()) {
            libFolder.mkdirs();
        }
        
        File sqliteJar = new File(libFolder, "sqlite-jdbc-" + SQLITE_VERSION + ".jar");
        
        if (!sqliteJar.exists()) {
            plugin.getLogger().info("Downloading SQLite JDBC driver...");
            
            try {
                downloadFile(SQLITE_URL, sqliteJar);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to download SQLite driver", e);
                return false;
            }
        }
        
        try {
            // Load the driver
            URLClassLoader childClassLoader = new URLClassLoader(
                new URL[] {sqliteJar.toURI().toURL()},
                this.getClass().getClassLoader()
            );
            
            Class<?> driverClass = Class.forName("org.sqlite.JDBC", true, childClassLoader);
            Driver driver = (Driver) driverClass.newInstance();
            DriverManager.registerDriver(new DriverShim(driver));
            
            sqliteLoaded = true;
            plugin.getLogger().info("Successfully loaded SQLite driver from local file");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load SQLite driver", e);
            return false;
        }
    }
    
    private void downloadFile(String url, File destination) throws IOException {
        URL downloadUrl = new URL(url);
        URLConnection connection = downloadUrl.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(destination)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
    
    private static class DriverShim implements Driver {
        private final Driver driver;
        
        DriverShim(Driver d) {
            this.driver = d;
        }
        
        public Connection connect(String url, java.util.Properties info) throws java.sql.SQLException {
            return driver.connect(url, info);
        }
        
        public boolean acceptsURL(String url) throws java.sql.SQLException {
            return driver.acceptsURL(url);
        }
        
        public int getMajorVersion() {
            return driver.getMajorVersion();
        }
        
        public int getMinorVersion() {
            return driver.getMinorVersion();
        }
        
        public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws java.sql.SQLException {
            return driver.getPropertyInfo(url, info);
        }
        
        public boolean jdbcCompliant() {
            return driver.jdbcCompliant();
        }
        
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getLogger(java.util.logging.Logger.GLOBAL_LOGGER_NAME);
        }
    }
}