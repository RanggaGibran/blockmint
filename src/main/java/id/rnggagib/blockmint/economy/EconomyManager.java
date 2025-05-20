package id.rnggagib.blockmint.economy;

import id.rnggagib.BlockMint;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

public class EconomyManager {
    
    private final BlockMint plugin;
    private final AtomicReference<Double> economyMultiplier = new AtomicReference<>(1.0);
    private final Map<String, Double> generatorTypeMultipliers = new HashMap<>();
    
    // Economic metrics
    private double totalGeneratorCount = 0;
    private double totalValueGenerated = 0;
    private double lastHourValueGenerated = 0;
    private double serverBalanceTotal = 0;
    private double lastBalanceCheck = 0;
    private long lastMetricsUpdate = 0;
    
    private BukkitTask economyUpdateTask;
    private static final long UPDATE_INTERVAL = 5 * 60 * 20; // 5 minutes in ticks
    
    public EconomyManager(BlockMint plugin) {
        this.plugin = plugin;
        initialize();
    }
    
    private void initialize() {
        loadConfig();
        startUpdateTask();
    }
    
    private void loadConfig() {
        economyMultiplier.set(plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.base-multiplier", 1.0));
        
        if (plugin.getConfigManager().getConfig().getBoolean("economy.smart-generation.enabled", true)) {
            plugin.getLogger().info("Smart Resource Generation is enabled");
        } else {
            plugin.getLogger().info("Smart Resource Generation is disabled");
        }
    }
    
    private void startUpdateTask() {
        if (economyUpdateTask != null) {
            economyUpdateTask.cancel();
        }
        
        economyUpdateTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, 
            this::updateEconomyMetrics, 100L, UPDATE_INTERVAL);
    }
    
    public void shutdown() {
        if (economyUpdateTask != null) {
            economyUpdateTask.cancel();
            economyUpdateTask = null;
        }
    }
    
    public double getGeneratorValueMultiplier(String generatorType) {
        if (!plugin.getConfigManager().getConfig().getBoolean("economy.smart-generation.enabled", true)) {
            return 1.0;
        }
        
        Double typeMultiplier = generatorTypeMultipliers.getOrDefault(generatorType, 1.0);
        return economyMultiplier.get() * typeMultiplier;
    }
    
    private void updateEconomyMetrics() {
        updateGeneratorMetrics();
        updateServerBalanceMetrics();
        calculateMultipliers();
    }
    
    private void updateGeneratorMetrics() {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT COUNT(*) as count FROM generators"
            );
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                totalGeneratorCount = rs.getDouble("count");
            }
            
            stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT SUM(total_earnings) as total FROM player_stats"
            );
            rs = stmt.executeQuery();
            if (rs.next()) {
                totalValueGenerated = rs.getDouble("total");
            }
            
            long oneHourAgo = System.currentTimeMillis() - (3600 * 1000);
            stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT SUM(total_earnings) as last_hour FROM economic_transactions WHERE timestamp > ?"
            );
            stmt.setLong(1, oneHourAgo);
            rs = stmt.executeQuery();
            if (rs.next()) {
                lastHourValueGenerated = rs.getDouble("last_hour");
                if (rs.wasNull()) lastHourValueGenerated = 0;
            }
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Error updating economy metrics: " + e.getMessage());
        }
    }
    
    private void updateServerBalanceMetrics() {
        double currentBalance = 0;
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT SUM(balance) as total FROM economy_balances"
            );
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                currentBalance = rs.getDouble("total");
                if (rs.wasNull()) currentBalance = 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error getting server balance: " + e.getMessage());
            return;
        }
        
        // Store the previous balance and update with current
        lastBalanceCheck = serverBalanceTotal;
        serverBalanceTotal = currentBalance;
        
        lastMetricsUpdate = System.currentTimeMillis();
    }
    
    private void calculateMultipliers() {
        if (!plugin.getConfigManager().getConfig().getBoolean("economy.smart-generation.enabled", true)) {
            economyMultiplier.set(1.0);
            return;
        }
        
        double baseMultiplier = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.base-multiplier", 1.0);
        double inflationFactor = calculateInflationFactor();
        double activityFactor = calculateActivityFactor();
        double saturationFactor = calculateSaturationFactor();
        
        double newMultiplier = baseMultiplier * inflationFactor * activityFactor * saturationFactor;
        
        // Clamp the multiplier within reasonable bounds
        double minMultiplier = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.min-multiplier", 0.5);
        double maxMultiplier = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.max-multiplier", 2.0);
        newMultiplier = Math.max(minMultiplier, Math.min(maxMultiplier, newMultiplier));
        
        economyMultiplier.set(newMultiplier);
        
        // Calculate type-specific multipliers based on rarity and usage
        calculateTypeMultipliers();
        
        plugin.getLogger().info("Updated economy multiplier: " + newMultiplier);
    }
    
    private double calculateInflationFactor() {
        if (lastBalanceCheck == 0) return 1.0;
        
        // If money supply is growing too quickly, reduce generation rate
        double moneyGrowth = (serverBalanceTotal - lastBalanceCheck) / lastBalanceCheck;
        double inflationThreshold = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.inflation-threshold", 0.05);
        
        // Calculate inverse factor: higher inflation = lower multiplier
        if (moneyGrowth > inflationThreshold) {
            double inflationControl = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.inflation-control", 0.8);
            return Math.max(0.5, 1 - ((moneyGrowth - inflationThreshold) * inflationControl));
        } else if (moneyGrowth < 0) {
            // Deflation - slightly increase production
            return 1.05;
        }
        
        return 1.0;
    }
    
    private double calculateActivityFactor() {
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        int expectedPlayers = plugin.getConfigManager().getConfig().getInt("economy.smart-generation.expected-players", 20);
        
        if (expectedPlayers <= 0) return 1.0;
        
        // Scale generation based on server activity - more players = slightly less per generator
        double activityRatio = (double)onlinePlayers / expectedPlayers;
        double activityControl = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.activity-impact", 0.1);
        
        return Math.max(0.7, 1.0 - (activityRatio * activityControl));
    }
    
    private double calculateSaturationFactor() {
        int totalGenerators = plugin.getGeneratorManager().getDatabaseGeneratorCount();
        int expectedGenerators = plugin.getConfigManager().getConfig().getInt("economy.smart-generation.expected-generators", 1000);
        
        if (expectedGenerators <= 0 || totalGenerators <= 0) return 1.0;
        
        // If there are too many generators, slightly reduce output per generator
        double saturationRatio = (double)totalGenerators / expectedGenerators;
        double saturationControl = plugin.getConfigManager().getConfig().getDouble("economy.smart-generation.saturation-control", 0.2);
        
        if (saturationRatio > 1.0) {
            return Math.max(0.6, 1.0 - ((saturationRatio - 1.0) * saturationControl));
        } 
        
        // If below expected count, slightly boost generation
        return Math.min(1.3, 1.0 + ((1.0 - saturationRatio) * 0.1));
    }
    
    private void calculateTypeMultipliers() {
        generatorTypeMultipliers.clear();
        
        Map<String, Integer> generatorTypeCounts = new HashMap<>();
        int totalGenerators = 0;
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT type, COUNT(*) as count FROM generators GROUP BY type"
            );
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String type = rs.getString("type");
                int count = rs.getInt("count");
                generatorTypeCounts.put(type, count);
                totalGenerators += count;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error counting generator types: " + e.getMessage());
            return;
        }
        
        if (totalGenerators == 0) return;
        
        // Calculate rarity-based multipliers
        for (String type : plugin.getGeneratorManager().getGeneratorTypes().keySet()) {
            int count = generatorTypeCounts.getOrDefault(type, 0);
            double percentage = (double)count / totalGenerators;
            
            // Rarer generators get a higher multiplier
            double rarityBonus = 0;
            if (percentage < 0.05) rarityBonus = 0.2; // Very rare
            else if (percentage < 0.10) rarityBonus = 0.1; // Rare
            else if (percentage > 0.30) rarityBonus = -0.1; // Common
            
            generatorTypeMultipliers.put(type, 1.0 + rarityBonus);
        }
    }
    
    public void logTransaction(UUID playerUUID, double amount, String source) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                        "INSERT INTO economic_transactions (player_uuid, amount, source, timestamp) VALUES (?, ?, ?, ?)"
                );
                stmt.setString(1, playerUUID.toString());
                stmt.setDouble(2, amount);
                stmt.setString(3, source);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error logging economic transaction: " + e.getMessage());
            }
        });
    }
    
    public void reload() {
        loadConfig();
    }
}