package id.rnggagib.blockmint.placeholders;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class BlockMintExpansion extends PlaceholderExpansion {

    private final BlockMint plugin;

    public BlockMintExpansion(BlockMint plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "blockmint";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player == null) {
            return "";
        }
        
        UUID uuid = player.getUniqueId();
        
        if (identifier.equals("generators_count")) {
            return String.valueOf(countPlayerGenerators(uuid));
        }
        
        if (identifier.equals("generators_limit")) {
            int maxGenerators = plugin.getConfigManager().getConfig().getInt("settings.max-generators-per-player", 10);
            return maxGenerators == 0 ? "∞" : String.valueOf(maxGenerators);
        }
        
        if (identifier.equals("total_earnings")) {
            double earnings = getTotalEarnings(uuid);
            return String.format("%.2f", earnings);
        }
        
        if (identifier.equals("total_earnings_formatted")) {
            double earnings = getTotalEarnings(uuid);
            if (earnings >= 1_000_000_000) {
                return String.format("%.2fB", earnings / 1_000_000_000);
            } else if (earnings >= 1_000_000) {
                return String.format("%.2fM", earnings / 1_000_000);
            } else if (earnings >= 1_000) {
                return String.format("%.2fK", earnings / 1_000);
            } else {
                return String.format("%.2f", earnings);
            }
        }
        
        if (identifier.equals("next_collection")) {
            if (player.isOnline()) {
                long nextCollection = getNextCollectionTime(uuid);
                if (nextCollection == -1) {
                    return "No generators";
                } else if (nextCollection == 0) {
                    return "Ready for collection";
                } else {
                    return formatTime(nextCollection / 1000);
                }
            }
        }
        
        if (identifier.startsWith("count_")) {
            String type = identifier.substring(6);
            return String.valueOf(countGeneratorType(uuid, type));
        }

        return null;
    }
    
    private int countPlayerGenerators(UUID playerUUID) {
        int count = 0;
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getOwner().equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }
    
    private double getTotalEarnings(UUID playerUUID) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT total_earnings FROM player_stats WHERE uuid = ?"
            );
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getDouble("total_earnings");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not retrieve total earnings from database: " + e.getMessage());
        }
        return 0.0;
    }
    
    private long getNextCollectionTime(UUID playerUUID) {
        long nextCollection = -1;
        
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getOwner().equals(playerUUID)) {
                if (generator.canGenerate()) {
                    return 0;
                }
                
                long generationTime = generator.getType().getGenerationTime() * 1000;
                long elapsed = System.currentTimeMillis() - generator.getLastGeneration();
                long remaining = generationTime - elapsed;
                
                if (nextCollection == -1 || remaining < nextCollection) {
                    nextCollection = remaining;
                }
            }
        }
        
        return nextCollection;
    }
    
    private int countGeneratorType(UUID playerUUID, String typeId) {
        int count = 0;
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getOwner().equals(playerUUID) && generator.getType().getId().equals(typeId)) {
                count++;
            }
        }
        return count;
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes < 60) {
            return minutes + "m " + seconds + "s";
        }
        
        long hours = minutes / 60;
        minutes = minutes % 60;
        
        return hours + "h " + minutes + "m " + seconds + "s";
    }
}