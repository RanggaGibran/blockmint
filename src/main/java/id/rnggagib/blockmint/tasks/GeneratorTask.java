package id.rnggagib.blockmint.tasks;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GeneratorTask extends BukkitRunnable {

    private final BlockMint plugin;
    private final boolean autoCollect;
    private final boolean showParticles;
    private final boolean playSound;
    private final double collectionRange;
    private final Map<Location, Long> lastParticleEffect = new HashMap<>();
    private final long particleInterval = 2000;

    public GeneratorTask(BlockMint plugin) {
        this.plugin = plugin;
        this.autoCollect = plugin.getConfigManager().getConfig().getBoolean("settings.auto-collect", false);
        this.showParticles = plugin.getConfigManager().getConfig().getBoolean("settings.visual-effects.show-particles", true);
        this.playSound = plugin.getConfigManager().getConfig().getBoolean("settings.visual-effects.play-sounds", true);
        this.collectionRange = plugin.getConfigManager().getConfig().getDouble("settings.auto-collect-range", 10.0);
    }

    @Override
    public void run() {
        for (Map.Entry<Location, Generator> entry : plugin.getGeneratorManager().getActiveGenerators().entrySet()) {
            Location location = entry.getKey();
            Generator generator = entry.getValue();
            
            if (location.getWorld() == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            
            if (generator.canGenerate()) {
                if (autoCollect) {
                    handleAutoCollect(location, generator);
                } else {
                    showReadyEffects(location, generator);
                }
            }
            
            DisplayManager.updateHologram(plugin, generator);
        }
    }

    private void handleAutoCollect(Location location, Generator generator) {
        UUID ownerUUID = generator.getOwner();
        Player owner = plugin.getServer().getPlayer(ownerUUID);
        
        if (owner != null && owner.isOnline() && owner.getLocation().getWorld() == location.getWorld() 
                && owner.getLocation().distance(location) <= collectionRange) {
            
            double value = generator.getValue();
            plugin.getEconomy().depositPlayer(owner, value);
            
            generator.setLastGeneration(System.currentTimeMillis());
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.format("%.2f", value));
            plugin.getMessageManager().send(owner, "general.auto-collect-success", placeholders);
            
            updatePlayerEarnings(ownerUUID, value);
            
            if (playSound) {
                owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
            
            if (showParticles) {
                spawnCollectionParticles(location);
            }
        } else {
            showReadyEffects(location, generator);
        }
    }

    private void showReadyEffects(Location location, Generator generator) {
        long now = System.currentTimeMillis();
        Long lastEffect = lastParticleEffect.get(location);
        
        if (lastEffect == null || (now - lastEffect) > particleInterval) {
            if (showParticles) {
                spawnReadyParticles(location);
            }
            
            if (playSound) {
                location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
            }
            
            lastParticleEffect.put(location, now);
        }
    }

    private void spawnReadyParticles(Location location) {
        Location particleLoc = location.clone().add(0.5, 1.2, 0.5);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0f);
        
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double x = Math.cos(angle) * 0.5;
            double z = Math.sin(angle) * 0.5;
            
            location.getWorld().spawnParticle(
                    Particle.REDSTONE, 
                    particleLoc.clone().add(x, 0, z), 
                    2, 
                    0.05, 
                    0.05, 
                    0.05, 
                    0, 
                    dustOptions);
        }
    }

    private void spawnCollectionParticles(Location location) {
        Location particleLoc = location.clone().add(0.5, 1.0, 0.5);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f);
        
        location.getWorld().spawnParticle(
                Particle.REDSTONE, 
                particleLoc, 
                15, 
                0.3, 
                0.3, 
                0.3, 
                0, 
                dustOptions);
    }

    private void updatePlayerEarnings(UUID playerUUID, double amount) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "UPDATE player_stats SET total_earnings = total_earnings + ? WHERE uuid = ?"
            );
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Could not update player earnings in database: " + e.getMessage());
        }
    }
}