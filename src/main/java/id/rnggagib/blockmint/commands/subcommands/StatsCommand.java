package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import id.rnggagib.blockmint.generators.Generator;
import org.bukkit.Bukkit;
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
import java.util.logging.Level;
import java.util.stream.Collectors;

public class StatsCommand implements SubCommand {

    private final BlockMint plugin;
    
    public StatsCommand(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "stats";
    }
    
    @Override
    public String getDescription() {
        return "Shows generator statistics";
    }
    
    @Override
    public String getSyntax() {
        return "/blockmint stats [player]";
    }
    
    @Override
    public String getPermission() {
        return "blockmint.stats";
    }
    
    @Override
    public boolean requiresPlayer() {
        return false;
    }
    
    @Override
    public void execute(CommandSender sender, String[] args) {
        Player target;
        
        if (args.length > 1) {
            if (!sender.hasPermission("blockmint.admin.stats.others")) {
                plugin.getMessageManager().send(sender, "commands.no-permission");
                return;
            }
            
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                plugin.getMessageManager().send(sender, "commands.invalid-player");
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                plugin.getMessageManager().send(sender, "commands.player-only");
                return;
            }
            target = (Player) sender;
        }
        
        UUID uuid = target.getUniqueId();
        final int generatorCount = countPlayerGenerators(uuid);
        final Map<String, Integer> generatorCounts = countGeneratorTypes(uuid);
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                        "SELECT generators_owned, total_earnings FROM player_stats WHERE uuid = ?"
                );
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                double totalEarnings = 0;
                int maxGenerators = plugin.getConfigManager().getConfig().getInt("settings.max-generators-per-player", 10);
                
                if (rs.next()) {
                    totalEarnings = rs.getDouble("total_earnings");
                }
                
                final double earnings = totalEarnings;
                final int maxGens = maxGenerators;
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", target.getName());
                    placeholders.put("count", String.valueOf(generatorCount));
                    placeholders.put("max", maxGens == 0 ? "∞" : String.valueOf(maxGens));
                    placeholders.put("earnings", String.format("%.2f", earnings));
                    
                    plugin.getMessageManager().send(sender, "commands.stats-header", placeholders);
                    plugin.getMessageManager().send(sender, "commands.stats-count", placeholders);
                    plugin.getMessageManager().send(sender, "commands.stats-earnings", placeholders);
                    
                    if (!generatorCounts.isEmpty()) {
                        plugin.getMessageManager().send(sender, "commands.stats-generators-header");
                        for (Map.Entry<String, Integer> entry : generatorCounts.entrySet()) {
                            Map<String, String> genPlaceholders = new HashMap<>();
                            genPlaceholders.put("type", entry.getKey());
                            genPlaceholders.put("count", String.valueOf(entry.getValue()));
                            plugin.getMessageManager().send(sender, "commands.stats-generators-item", genPlaceholders);
                        }
                    }
                });
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error fetching player stats", e);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getMessageManager().send(sender, "commands.stats-error");
                });
            }
        });
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
    
    private Map<String, Integer> countGeneratorTypes(UUID playerUUID) {
        Map<String, Integer> counts = new HashMap<>();
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getOwner().equals(playerUUID)) {
                String type = generator.getType().getName();
                counts.put(type, counts.getOrDefault(type, 0) + 1);
            }
        }
        return counts;
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 2 && sender.hasPermission("blockmint.admin.stats.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}