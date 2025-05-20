package id.rnggagib.blockmint.commands.subcommands;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.commands.SubCommand;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvolveCommand implements SubCommand {

    private final BlockMint plugin;

    public EvolveCommand(BlockMint plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "evolve";
    }

    @Override
    public String getDescription() {
        return "Evolve a generator to the next tier";
    }

    @Override
    public String getSyntax() {
        return "/blockmint evolve <generator_id>";
    }

    @Override
    public String getPermission() {
        return "blockmint.evolve";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, "commands.player-only");
            return;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            plugin.getMessageManager().send(player, "command.evolve.invalid-arguments");
            return;
        }

        int generatorId;
        try {
            generatorId = Integer.parseInt(args[1]); // Changed from args[0] to args[1] because the command itself is at args[0]
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(player, "command.evolve.invalid-id");
            return;
        }

        Generator generator = findGenerator(generatorId);
        if (generator == null) {
            plugin.getMessageManager().send(player, "command.evolve.not-found");
            return;
        }

        if (!generator.getOwner().equals(player.getUniqueId()) && 
            !player.hasPermission("blockmint.admin.evolve")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        if (!generator.isEvolutionReady()) {
            plugin.getMessageManager().send(player, "command.evolve.not-ready");
            return;
        }

        GeneratorType nextType = generator.getEvolutionTarget();
        if (nextType == null) {
            plugin.getMessageManager().send(player, "command.evolve.no-evolution-path");
            return;
        }

        double evolutionCost = generator.getType().getEvolutionCost();
        if (evolutionCost > 0 && plugin.getConfigManager().getConfig().getBoolean("economy.charge-for-evolution", true)) {
            if (!plugin.getEconomy().has(player, evolutionCost)) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("cost", String.format("%.2f", evolutionCost));
                plugin.getMessageManager().send(player, "general.not-enough-money", placeholders);
                return;
            }
            
            plugin.getEconomy().withdrawPlayer(player, evolutionCost);
        }

        evolveGenerator(generator, nextType, player);
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        Player player = (Player) sender;
        
        if (args.length == 2) {
            List<String> generatorIds = new ArrayList<>();
            for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
                if (generator.getOwner().equals(player.getUniqueId()) && generator.isEvolutionReady()) {
                    generatorIds.add(String.valueOf(generator.getId()));
                }
            }
            return generatorIds;
        }
        
        return new ArrayList<>();
    }
    
    private Generator findGenerator(int generatorId) {
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getId() == generatorId) {
                return generator;
            }
        }
        return null;
    }
    
    private void evolveGenerator(Generator generator, GeneratorType nextType, Player player) {
        Location location = generator.getLocation();
        
        // Store old type name before changing it
        String oldTypeName = generator.getType().getName();
        
        // Update generator properties
        generator.setType(nextType);
        
        // Reset evolution tracking but keep level
        updateGeneratorTypeAsync(generator.getId(), nextType.getId());
        
        // Update the block type
        if (location.getChunk().isLoaded()) {
            location.getBlock().setType(Material.valueOf(nextType.getMaterial()));
        }
        
        // Display visual effects
        playEvolutionEffects(location);
        
        // Update the hologram
        DisplayManager.removeHologram(location);
        DisplayManager.createHologram(plugin, location, nextType, generator.getLevel());
        
        // Notify the player
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("old_type", oldTypeName);
        placeholders.put("new_type", nextType.getName());
        plugin.getMessageManager().send(player, "command.evolve.success", placeholders);
    }
    
    private void updateGeneratorTypeAsync(final int generatorId, final String newType) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                        "UPDATE generators SET type = ?, usage_count = 0, resources_generated = 0 WHERE id = ?"
                );
                stmt.setString(1, newType);
                stmt.setInt(2, generatorId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not update generator type in database: " + e.getMessage());
            }
        });
    }
    
    private void playEvolutionEffects(Location location) {
        Location effectLoc = location.clone().add(0.5, 1.2, 0.5);
        
        // Create a spiral of particles
        for (int i = 0; i < 5; i++) {
            final int index = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double radius = 0.8;
                for (double y = 0; y < 2.0; y += 0.1) {
                    double x = radius * Math.cos(y * Math.PI * 2 + index);
                    double z = radius * Math.sin(y * Math.PI * 2 + index);
                    effectLoc.getWorld().spawnParticle(
                        Particle.SPELL_WITCH, 
                        effectLoc.getX() + x, 
                        effectLoc.getY() + y, 
                        effectLoc.getZ() + z, 
                        1, 0, 0, 0, 0
                    );
                }
                
                if (index == 0) {
                    effectLoc.getWorld().playSound(effectLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
                } else if (index == 4) {
                    effectLoc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, effectLoc, 1);
                    effectLoc.getWorld().playSound(effectLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                    
                    // Final particle burst
                    for (int j = 0; j < 50; j++) {
                        double x = (Math.random() - 0.5) * 2;
                        double y = Math.random() * 2;
                        double z = (Math.random() - 0.5) * 2;
                        effectLoc.getWorld().spawnParticle(
                            Particle.END_ROD, 
                            effectLoc.getX(), 
                            effectLoc.getY(), 
                            effectLoc.getZ(), 
                            0, x, y, z, 0.1
                        );
                    }
                }
            }, i * 20L); // 1 second apart
        }
    }
}