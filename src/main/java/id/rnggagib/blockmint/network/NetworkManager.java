package id.rnggagib.blockmint.network;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.utils.DisplayManager;
import id.rnggagib.blockmint.network.permissions.NetworkPermission;
import id.rnggagib.blockmint.network.permissions.NetworkPermissionManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NetworkManager {
    
    private final BlockMint plugin;
    private final Map<Integer, NetworkBlock> networks = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> generatorNetworkMap = new ConcurrentHashMap<>();
    private BukkitTask networkVisualizerTask;
    private final Set<UUID> playersViewingNetworks = new HashSet<>();
    private NetworkPermissionManager permissionManager;
    
    public NetworkManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        createNetworkTables();
        loadNetworksFromDatabase();
        
        // Initialize permission manager after networks are loaded
        permissionManager = new NetworkPermissionManager(plugin);
        permissionManager.initialize();
        
        startNetworkVisualizerTask();
        
        plugin.getLogger().info("Network manager initialized with " + networks.size() + " networks");
    }
    
    private void createNetworkTables() {
        try {
            plugin.getDatabaseManager().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS networks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "owner TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "tier TEXT NOT NULL, " +
                    "creation_time BIGINT NOT NULL" +
                    ")"
            );
            
            plugin.getDatabaseManager().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS network_generators (" +
                    "network_id INTEGER NOT NULL, " +
                    "generator_id INTEGER NOT NULL, " +
                    "PRIMARY KEY (network_id, generator_id), " +
                    "FOREIGN KEY (network_id) REFERENCES networks(id) ON DELETE CASCADE" +
                    ")"
            );
            
            plugin.getLogger().info("Network tables initialized");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create network tables", e);
        }
    }
    
    private void loadNetworksFromDatabase() {
        networks.clear();
        generatorNetworkMap.clear();
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT * FROM networks"
            );
            
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            
            while (rs.next()) {
                count++;
                int networkId = rs.getInt("id");
                UUID owner = UUID.fromString(rs.getString("owner"));
                String name = rs.getString("name");
                
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                
                NetworkTier tier = NetworkTier.fromName(rs.getString("tier"));
                long creationTime = rs.getLong("creation_time");
                
                if (plugin.getServer().getWorld(world) == null) {
                    plugin.getLogger().warning("World " + world + " not found for network " + networkId + ", skipping");
                    continue;
                }
                
                Location location = new Location(plugin.getServer().getWorld(world), x, y, z);
                
                NetworkBlock network = new NetworkBlock(networkId, location, owner, name, tier);
                network.setCreationTime(creationTime);
                networks.put(networkId, network);
                
                loadNetworkGenerators(network);
                loadNetworkAutoCollectSettings(network); // Load auto-collect settings
            }
            
            plugin.getLogger().info("Loaded " + networks.size() + " network blocks from " + count + " database records");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load networks from database", e);
        }
    }
    
    private void loadNetworkGenerators(NetworkBlock network) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "SELECT generator_id FROM network_generators WHERE network_id = ?"
            );
            stmt.setInt(1, network.getNetworkId());
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                int generatorId = rs.getInt("generator_id");
                network.addGenerator(generatorId);
                generatorNetworkMap.put(generatorId, network.getNetworkId());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load network generators", e);
        }
    }
    
    public NetworkBlock createNetwork(UUID owner, String name, NetworkTier tier, Location location) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "INSERT INTO networks (owner, name, world, x, y, z, tier, creation_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );
            
            stmt.setString(1, owner.toString());
            stmt.setString(2, name);
            stmt.setString(3, location.getWorld().getName());
            stmt.setInt(4, location.getBlockX());
            stmt.setInt(5, location.getBlockY());
            stmt.setInt(6, location.getBlockZ());
            stmt.setString(7, tier.name());
            stmt.setLong(8, System.currentTimeMillis());
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                int networkId = -1;
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    networkId = rs.getInt(1);
                }
                
                NetworkBlock network = new NetworkBlock(networkId, location, owner, name, tier);
                networks.put(networkId, network);
                
                // Add the owner as a member with owner permissions in the database for consistency
                permissionManager.addMember(networkId, owner, plugin.getServer().getOfflinePlayer(owner).getName(), NetworkPermission.OWNER);
                
                plugin.getLogger().fine("Created network " + networkId + " for player " + owner);
                return network;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create network", e);
        }
        
        return null;
    }
    
    public boolean addGeneratorToNetwork(int networkId, int generatorId) {
        NetworkBlock network = networks.get(networkId);
        if (network == null) return false;
        
        Generator generator = findGeneratorById(generatorId);
        if (generator == null) return false;
        
        if (!network.isInRange(generator.getLocation())) {
            return false;
        }
        
        if (network.isMaxCapacity()) {
            return false;
        }
        
        if (!checkPermission(networkId, generator.getOwner(), NetworkPermission.USE)) {
            return false;
        }
        
        if (generatorNetworkMap.containsKey(generatorId)) {
            removeGeneratorFromNetwork(generatorId);
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "INSERT INTO network_generators (network_id, generator_id) VALUES (?, ?)"
            );
            
            stmt.setInt(1, networkId);
            stmt.setInt(2, generatorId);
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                network.addGenerator(generatorId);
                generatorNetworkMap.put(generatorId, networkId);
                
                DisplayManager.updateHologram(plugin, generator);
                
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add generator to network", e);
        }
        
        return false;
    }
    
    public boolean removeGeneratorFromNetwork(int generatorId) {
        Integer networkId = generatorNetworkMap.get(generatorId);
        if (networkId == null) return false;
        
        NetworkBlock network = networks.get(networkId);
        if (network == null) return false;
        
        Generator generator = findGeneratorById(generatorId);
        if (generator == null) return false;
        
        UUID playerUuid = generator.getOwner();
        NetworkPermission requiredPermission = NetworkPermission.USE;
        
        if (!network.getOwner().equals(playerUuid)) {
            requiredPermission = NetworkPermission.MANAGE;
        }
        
        if (!checkPermission(networkId, playerUuid, requiredPermission)) {
            return false;
        }
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "DELETE FROM network_generators WHERE network_id = ? AND generator_id = ?"
            );
            
            stmt.setInt(1, networkId);
            stmt.setInt(2, generatorId);
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                network.removeGenerator(generatorId);
                generatorNetworkMap.remove(generatorId);
                
                if (generator != null) {
                    DisplayManager.updateHologram(plugin, generator);
                }
                
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove generator from network", e);
        }
        
        return false;
    }
    
    public boolean deleteNetwork(int networkId) {
        NetworkBlock network = networks.get(networkId);
        if (network == null) return false;
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "DELETE FROM networks WHERE id = ?"
            );
            
            stmt.setInt(1, networkId);
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                for (int generatorId : network.getConnectedGenerators()) {
                    generatorNetworkMap.remove(generatorId);
                    
                    Generator generator = findGeneratorById(generatorId);
                    if (generator != null) {
                        DisplayManager.updateHologram(plugin, generator);
                    }
                }
                
                permissionManager.handleNetworkDeletion(networkId);
                networks.remove(networkId);
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete network", e);
        }
        
        return false;
    }
    
    public boolean upgradeNetwork(int networkId, NetworkTier newTier) {
        NetworkBlock network = networks.get(networkId);
        if (network == null) return false;
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "UPDATE networks SET tier = ? WHERE id = ?"
            );
            
            stmt.setString(1, newTier.name());
            stmt.setInt(2, networkId);
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                network.upgradeTier(newTier);
                
                for (int generatorId : network.getConnectedGenerators()) {
                    Generator generator = findGeneratorById(generatorId);
                    if (generator != null) {
                        DisplayManager.updateHologram(plugin, generator);
                    }
                }
                
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to upgrade network", e);
        }
        
        return false;
    }
    
    public NetworkBlock getGeneratorNetwork(int generatorId) {
        Integer networkId = generatorNetworkMap.get(generatorId);
        if (networkId == null) return null;
        
        return networks.get(networkId);
    }
    
    public NetworkBlock getNetworkAt(Location location) {
        for (NetworkBlock network : networks.values()) {
            if (network.isAtLocation(location)) {
                return network;
            }
        }
        return null;
    }
    
    public List<NetworkBlock> getPlayerNetworks(UUID playerUuid) {
        List<NetworkBlock> playerNetworks = new ArrayList<>();
        
        for (NetworkBlock network : networks.values()) {
            if (network.getOwner().equals(playerUuid)) {
                playerNetworks.add(network);
            }
        }
        
        return playerNetworks;
    }
    
    public double getGeneratorEfficiencyBonus(int generatorId) {
        NetworkBlock network = getGeneratorNetwork(generatorId);
        if (network == null) return 0.0;
        
        return network.getEfficiencyBonus();
    }
    
    public void toggleNetworkVisualization(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        if (playersViewingNetworks.contains(playerUuid)) {
            playersViewingNetworks.remove(playerUuid);
            player.sendMessage("§aNetwork visualization disabled");
        } else {
            playersViewingNetworks.add(playerUuid);
            player.sendMessage("§aNetwork visualization enabled");
        }
    }
    
    private void startNetworkVisualizerTask() {
        if (networkVisualizerTask != null) {
            networkVisualizerTask.cancel();
        }
        
        networkVisualizerTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::visualizeNetworks, 20L, 10L
        );
    }
    
    private void visualizeNetworks() {
        if (playersViewingNetworks.isEmpty()) return;
        
        Map<UUID, List<Location>> playerNetworkLocations = new HashMap<>();
        Map<UUID, Map<Integer, List<Generator>>> playerGeneratorsByNetwork = new HashMap<>();
        
        for (UUID playerUuid : playersViewingNetworks) {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                playersViewingNetworks.remove(playerUuid);
                continue;
            }
            
            List<Location> networkLocations = new ArrayList<>();
            Map<Integer, List<Generator>> generatorsByNetwork = new HashMap<>();
            
            for (NetworkBlock network : networks.values()) {
                if (network.getOwner().equals(playerUuid)) {
                    if (network.getLocation().getWorld().equals(player.getWorld())) {
                        networkLocations.add(network.getLocation());
                        
                        List<Generator> networkGenerators = new ArrayList<>();
                        for (int generatorId : network.getConnectedGenerators()) {
                            Generator generator = findGeneratorById(generatorId);
                            if (generator != null && generator.getLocation().getWorld().equals(player.getWorld())) {
                                networkGenerators.add(generator);
                            }
                        }
                        
                        if (!networkGenerators.isEmpty()) {
                            generatorsByNetwork.put(network.getNetworkId(), networkGenerators);
                        }
                    }
                }
            }
            
            playerNetworkLocations.put(playerUuid, networkLocations);
            playerGeneratorsByNetwork.put(playerUuid, generatorsByNetwork);
        }
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (UUID playerUuid : playersViewingNetworks) {
                List<Location> networkLocations = playerNetworkLocations.get(playerUuid);
                Map<Integer, List<Generator>> generatorsByNetwork = playerGeneratorsByNetwork.get(playerUuid);
                
                if (networkLocations == null || generatorsByNetwork == null) continue;
                
                Player player = plugin.getServer().getPlayer(playerUuid);
                if (player == null || !player.isOnline()) continue;
                
                for (Map.Entry<Integer, List<Generator>> entry : generatorsByNetwork.entrySet()) {
                    int networkId = entry.getKey();
                    List<Generator> generators = entry.getValue();
                    NetworkBlock network = networks.get(networkId);
                    
                    if (network == null || generators.isEmpty()) continue;
                    
                    Location networkLocation = network.getLocation().clone().add(0.5, 0.5, 0.5);
                    Particle.DustOptions dustOptions = getNetworkParticleColor(network.getTier());
                    
                    // Draw range indicator
                    if (player.getLocation().distance(networkLocation) < 50) {
                        drawRangeCircle(player, networkLocation, network.getRange(), dustOptions);
                    }
                    
                    // Draw connections
                    for (Generator generator : generators) {
                        if (player.getLocation().distance(generator.getLocation()) < 50) {
                            drawParticleLine(networkLocation, generator.getLocation().clone().add(0.5, 0.5, 0.5), dustOptions);
                        }
                    }
                }
            }
        });
    }
    
    private void drawRangeCircle(Player player, Location center, double radius, Particle.DustOptions dustOptions) {
        int points = 36;
        double angleIncrement = (2 * Math.PI) / points;
        
        for (int i = 0; i < points; i++) {
            double angle = i * angleIncrement;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            
            Location particleLocation = new Location(center.getWorld(), x, center.getY(), z);
            player.spawnParticle(Particle.REDSTONE, particleLocation, 1, 0, 0, 0, 0, dustOptions);
        }
    }
    
    private void drawParticleLine(Location from, Location to, Particle.DustOptions dustOptions) {
        if (!from.getWorld().equals(to.getWorld())) return;
        
        double distance = from.distance(to);
        if (distance > 100) return;
        
        double particlesPerBlock = 2;
        int particleCount = (int) (distance * particlesPerBlock);
        
        double xDiff = (to.getX() - from.getX()) / particleCount;
        double yDiff = (to.getY() - from.getY()) / particleCount;
        double zDiff = (to.getZ() - from.getZ()) / particleCount;
        
        for (int i = 0; i < particleCount; i++) {
            double x = from.getX() + xDiff * i;
            double y = from.getY() + yDiff * i;
            double z = from.getZ() + zDiff * i;
            
            Location particleLocation = new Location(from.getWorld(), x, y, z);
            from.getWorld().spawnParticle(
                    Particle.REDSTONE, 
                    particleLocation, 
                    1, 0, 0, 0, 0, 
                    dustOptions);
        }
    }
    
    private Particle.DustOptions getNetworkParticleColor(NetworkTier tier) {
        switch (tier) {
            case BASIC:
                return new Particle.DustOptions(org.bukkit.Color.RED, 1.0f);
            case ADVANCED:
                return new Particle.DustOptions(org.bukkit.Color.BLUE, 1.0f);
            case ELITE:
                return new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.0f);
            case ULTIMATE:
                return new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 255, 255), 1.0f);
            case CELESTIAL:
                return new Particle.DustOptions(org.bukkit.Color.PURPLE, 1.0f);
            default:
                return new Particle.DustOptions(org.bukkit.Color.WHITE, 1.0f);
        }
    }
    
    private Generator findGeneratorById(int generatorId) {
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getId() == generatorId) {
                return generator;
            }
        }
        return null;
    }
    
    private boolean checkPermission(int networkId, UUID playerUuid, NetworkPermission requiredPermission) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null && player.hasPermission("blockmint.admin.network.bypass")) {
            return true;
        }
        
        return permissionManager.checkPermission(networkId, playerUuid, requiredPermission);
    }
    
    public void shutdown() {
        if (networkVisualizerTask != null) {
            networkVisualizerTask.cancel();
            networkVisualizerTask = null;
        }
        
        if (permissionManager != null) {
            permissionManager.shutdown();
        }
        
        networks.clear();
        generatorNetworkMap.clear();
        playersViewingNetworks.clear();
    }
    
    public Map<Integer, NetworkBlock> getNetworks() {
        return networks;
    }
    
    public void restoreNetworkBlocks() {
        int restored = 0;
        
        for (NetworkBlock network : networks.values()) {
            Location location = network.getLocation();
            
            if (location.getWorld() != null && location.getChunk().isLoaded()) {
                network.updateBlockAppearance();
                restored++;
            }
        }
        
        plugin.getLogger().info("Restored " + restored + " network blocks");
    }
    
    private void restoreNetworkHolograms() {
        plugin.getLogger().info("Restoring network holograms for " + networks.size() + " networks");
        
        for (NetworkBlock network : networks.values()) {
            Location location = network.getLocation();
            DisplayManager.createNetworkHologram(location, network);
        }
    }
    
    public NetworkPermissionManager getPermissionManager() {
        return permissionManager;
    }

    public void processNetworkAutoCollection() {
        for (NetworkBlock network : networks.values()) {
            if (!network.isAutoCollectEnabled()) continue;
            
            World world = network.getLocation().getWorld();
            if (world == null || !world.isChunkLoaded(network.getLocation().getBlockX() >> 4, network.getLocation().getBlockZ() >> 4)) {
                continue;
            }
            
            UUID ownerUuid = network.getOwner();
            Player owner = plugin.getServer().getPlayer(ownerUuid);
            if (owner == null || !owner.isOnline()) continue;
            
            double totalCollected = 0;
            int generatorsCollected = 0;
            
            for (Integer generatorId : network.getConnectedGenerators()) {
                Generator generator = plugin.getGeneratorManager().findGeneratorById(generatorId);
                if (generator == null) continue;
                
                if (generator.canGenerate()) {
                    double value = generator.getValue() * network.getAutoCollectEfficiency();
                    plugin.getEconomy().depositPlayer(owner, value);
                    generator.setLastGeneration(System.currentTimeMillis());
                    generator.incrementUsageCount();
                    generator.addResourcesGenerated(value);
                    
                    totalCollected += value;
                    generatorsCollected++;
                    
                    Location genLocation = generator.getLocation();
                    if (genLocation.getWorld() != null && genLocation.getWorld().isChunkLoaded(genLocation.getBlockX() >> 4, genLocation.getBlockZ() >> 4)) {
                        spawnNetworkCollectionEffect(genLocation, network.getLocation());
                        DisplayManager.updateHologram(plugin, generator);
                    }
                }
            }
            
            if (totalCollected > 0) {
                network.setLastAutoCollectTime(System.currentTimeMillis());
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("amount", String.format("%.2f", totalCollected));
                placeholders.put("count", String.valueOf(generatorsCollected));
                placeholders.put("network", network.getTier().getDisplayName());
                
                plugin.getMessageManager().send(owner, "network.auto-collect-success", placeholders);
                
                plugin.getDatabaseManager().addBatchOperation(
                    ownerUuid,
                    "UPDATE player_stats SET total_earnings = total_earnings + ? WHERE uuid = ?",
                    new Object[]{totalCollected, ownerUuid.toString()}
                );
                
                plugin.getEconomyManager().logTransaction(ownerUuid, totalCollected, "network_auto_collect");
                
                Location networkLocation = network.getLocation().clone().add(0.5, 1.0, 0.5);
                world.playSound(networkLocation, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
                
                DisplayManager.updateHologram(plugin, network);
            }
        }
    }

    private void spawnNetworkCollectionEffect(Location generatorLocation, Location networkLocation) {
        if (generatorLocation.getWorld() != networkLocation.getWorld()) return;
        
        org.bukkit.World world = generatorLocation.getWorld();
        Location genCenter = generatorLocation.clone().add(0.5, 1.0, 0.5);
        Location netCenter = networkLocation.clone().add(0.5, 1.0, 0.5);
        
        Vector direction = netCenter.toVector().subtract(genCenter.toVector()).normalize().multiply(0.3);
        Location particleLoc = genCenter.clone();
        
        Particle.DustOptions dustOptions = new Particle.DustOptions(org.bukkit.Color.fromRGB(30, 144, 255), 0.7f);
        
        for (int i = 0; i < 10; i++) {
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0, dustOptions);
            particleLoc.add(direction);
        }
    }

    public void saveNetwork(NetworkBlock network) {
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(
                "UPDATE networks SET owner = ?, tier = ?, auto_collect_enabled = ?, last_auto_collect_time = ? WHERE id = ?")) {
            
            stmt.setString(1, network.getOwner().toString());
            stmt.setString(2, network.getTier().name());
            stmt.setInt(3, network.isAutoCollectEnabled() ? 1 : 0);
            stmt.setLong(4, network.getLastAutoCollectTime());
            stmt.setInt(5, network.getNetworkId());
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving network data", e);
        }
    }

    // Modify the loadNetworks method to load auto-collect settings
    private void loadNetworkAutoCollectSettings(NetworkBlock network) {
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT auto_collect_enabled, last_auto_collect_time FROM networks WHERE id = ?")) {
                
            stmt.setInt(1, network.getNetworkId());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                network.setAutoCollectEnabled(rs.getInt("auto_collect_enabled") == 1);
                network.setLastAutoCollectTime(rs.getLong("last_auto_collect_time"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading network auto-collect settings", e);
        }
    }
}