package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetworkNotifyCommand implements SubCommand {

    private final BlockMint plugin;
    private final Map<UUID, Boolean> notificationSettings = new HashMap<>();
    
    public NetworkNotifyCommand(BlockMint plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "notify";
    }

    @Override
    public String getDescription() {
        return "Toggle network auto-collect notifications";
    }

    @Override
    public String getSyntax() {
        return "/blockmint network notify";
    }

    @Override
    public String getPermission() {
        return "blockmint.network.notify";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "commands.player-only");
            return;
        }
        
        UUID playerUuid = player.getUniqueId();
        
        plugin.getDatabaseManager().executeAsync(
            "SELECT network_notifications FROM player_stats WHERE uuid = '" + playerUuid.toString() + "'",
            resultSet -> {
                try {
                    boolean currentSetting = false;
                    if (resultSet.next()) {
                        currentSetting = resultSet.getBoolean("network_notifications");
                    }
                    
                    boolean newSetting = !currentSetting;
                    
                    plugin.getDatabaseManager().updateAsync(
                        "UPDATE player_stats SET network_notifications = " + (newSetting ? "1" : "0") + 
                        " WHERE uuid = '" + playerUuid.toString() + "'",
                        result -> {
                            notificationSettings.put(playerUuid, newSetting);
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (newSetting) {
                                    plugin.getMessageManager().send(player, "network.notifications-enabled");
                                } else {
                                    plugin.getMessageManager().send(player, "network.notifications-disabled");
                                }
                            });
                        },
                        error -> plugin.getLogger().severe("Error updating network notification setting: " + error.getMessage())
                    );
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error processing notification settings: " + e.getMessage());
                }
            },
            error -> plugin.getLogger().severe("Error querying network notification setting: " + error.getMessage())
        );
    }
    
    public boolean shouldNotify(UUID playerUuid) {
        return notificationSettings.getOrDefault(playerUuid, false);
    }
    
    public void loadPlayerSetting(UUID playerUuid) {
        plugin.getDatabaseManager().executeAsync(
            "SELECT network_notifications FROM player_stats WHERE uuid = '" + playerUuid.toString() + "'",
            resultSet -> {
                try {
                    if (resultSet.next()) {
                        notificationSettings.put(playerUuid, resultSet.getBoolean("network_notifications"));
                    } else {
                        notificationSettings.put(playerUuid, false);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error loading player notification setting: " + e.getMessage());
                }
            },
            error -> plugin.getLogger().severe("Error querying player notification setting: " + error.getMessage())
        );
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}