package id.rnggagib.blockmint.listeners;

import id.rnggagib.BlockMint;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListeners implements Listener {
    
    private final BlockMint plugin;
    
    public PlayerListeners(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement checkStmt = plugin.getDatabaseManager().prepareStatement(
                        "SELECT * FROM player_stats WHERE uuid = ?"
                );
                checkStmt.setString(1, uuid.toString());
                ResultSet rs = checkStmt.executeQuery();
                
                if (!rs.next()) {
                    PreparedStatement insertStmt = plugin.getDatabaseManager().prepareStatement(
                            "INSERT INTO player_stats (uuid, player_name, generators_owned, total_earnings) VALUES (?, ?, 0, 0.0)"
                    );
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setString(2, player.getName());
                    insertStmt.executeUpdate();
                } else {
                    PreparedStatement updateStmt = plugin.getDatabaseManager().prepareStatement(
                            "UPDATE player_stats SET player_name = ? WHERE uuid = ?"
                    );
                    updateStmt.setString(1, player.getName());
                    updateStmt.setString(2, uuid.toString());
                    updateStmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error updating player stats on join!", e);
            }
        });
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Add any cleanup code here if needed
    }
}