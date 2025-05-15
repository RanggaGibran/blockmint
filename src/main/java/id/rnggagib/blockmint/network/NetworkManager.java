package id.rnggagib.blockmint.network;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.utils.DisplayManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
    private final Map<Integer, GeneratorNetwork> networks = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> generatorNetworkMap = new ConcurrentHashMap<>();
    private BukkitTask networkVisualizerTask;
    private final Set<UUID> playersViewingNetworks = new HashSet<>();
    
    public NetworkManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void initialize() {
        createNetworkTables();
        loadNetworksFromDatabase();
        startNetworkVisualizerTask();
    }
    
    private void createNetworkTables() {
        try {
            plugin.getDatabaseManager().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS networks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "owner TEXT NOT NULL, " +
                    "name TEXT NOT NULL, " +
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
            
            while (rs.next()) {
                int networkId = rs.getInt("id");
                UUID owner = UUID.fromString(rs.getString("owner"));
                String name = rs.getString("name");
                NetworkTier tier = NetworkTier.fromName(rs.getString("tier"));
                long creationTime = rs.getLong("creation_time");
                
                GeneratorNetwork network = new GeneratorNetwork(networkId, owner, name, tier);
                networks.put(networkId, network);
                
                loadNetworkGenerators(network);
            }
            
            plugin.getLogger().info("Loaded " + networks.size() + " generator networks");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load networks from database", e);
        }
    }
    
    private void loadNetworkGenerators(GeneratorNetwork network) {
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
    
    public GeneratorNetwork createNetwork(UUID owner, String name, NetworkTier tier) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "INSERT INTO networks (owner, name, tier, creation_time) VALUES (?, ?, ?, ?)"
            );
            
            stmt.setString(1, owner.toString());
            stmt.setString(2, name);
            stmt.setString(3, tier.name());
            stmt.setLong(4, System.currentTimeMillis());
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                int networkId = -1;
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    networkId = rs.getInt(1);
                }
                
                GeneratorNetwork network = new GeneratorNetwork(networkId, owner, name, tier);
                networks.put(networkId, network);
                
                plugin.getLogger().fine("Created network " + networkId + " for player " + owner);
                return network;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create network", e);
        }
        
        return null;
    }
    
    public boolean addGeneratorToNetwork(int networkId, int generatorId) {
        GeneratorNetwork network = networks.get(networkId);
        if (network == null) return false;
        
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
                
                Generator generator = findGeneratorById(generatorId);
                if (generator != null) {
                    DisplayManager.updateHologram(plugin, generator);
                }
                
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
        
        GeneratorNetwork network = networks.get(networkId);
        if (network == null) return false;
        
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
                
                Generator generator = findGeneratorById(generatorId);
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
        GeneratorNetwork network = networks.get(networkId);
        if (network == null) return false;
        
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "DELETE FROM networks WHERE id = ?"
            );
            
            stmt.setInt(1, networkId);
            
            int result = stmt.executeUpdate();
            
            if (result > 0) {
                for (int generatorId : network.getGeneratorIds()) {
                    generatorNetworkMap.remove(generatorId);
                    
                    Generator generator = findGeneratorById(generatorId);
                    if (generator != null) {
                        DisplayManager.updateHologram(plugin, generator);
                    }
                }
                
                networks.remove(networkId);
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete network", e);
        }
        
        return false;
    }
    
    public boolean upgradeNetwork(int networkId, NetworkTier newTier) {
        GeneratorNetwork network = networks.get(networkId);
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
                
                for (int generatorId : network.getGeneratorIds()) {
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
    
    public GeneratorNetwork getGeneratorNetwork(int generatorId) {
        Integer networkId = generatorNetworkMap.get(generatorId);
        if (networkId == null) return null;
        
        return networks.get(networkId);
    }
    
    public List<GeneratorNetwork> getPlayerNetworks(UUID playerUuid) {
        List<GeneratorNetwork> playerNetworks = new ArrayList<>();
        
        for (GeneratorNetwork network : networks.values()) {
            if (network.getOwner().equals(playerUuid)) {
                playerNetworks.add(network);
            }
        }
        
        return playerNetworks;
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
        
        Map<UUID, List<Generator>> playerGenerators = new HashMap<>();
        
        for (UUID playerUuid : playersViewingNetworks) {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                playersViewingNetworks.remove(playerUuid);
                continue;
            }
            
            List<Generator> generators = new ArrayList<>();
            for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
                if (generator.getOwner().equals(playerUuid)) {
                    generators.add(generator);
                }
            }
            
            playerGenerators.put(playerUuid, generators);
        }
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Map.Entry<UUID, List<Generator>> entry : playerGenerators.entrySet()) {
                UUID playerUuid = entry.getKey();
                List<Generator> generators = entry.getValue();
                
                Map<Integer, List<Generator>> networkGenerators = new HashMap<>();
                
                for (Generator generator : generators) {
                    Integer networkId = generatorNetworkMap.get(generator.getId());
                    if (networkId != null) {
                        networkGenerators.computeIfAbsent(networkId, k -> new ArrayList<>()).add(generator);
                    }
                }
                
                for (Map.Entry<Integer, List<Generator>> networkEntry : networkGenerators.entrySet()) {
                    int networkId = networkEntry.getKey();
                    List<Generator> networkGens = networkEntry.getValue();
                    
                    GeneratorNetwork network = networks.get(networkId);
                    if (network == null) continue;
                    
                    drawNetworkConnections(network, networkGens);
                }
            }
        });
    }
    
    private void drawNetworkConnections(GeneratorNetwork network, List<Generator> generators) {
        if (generators.size() < 2) return;
        
        Particle.DustOptions dustOptions = getNetworkParticleColor(network.getTier());
        
        for (int i = 0; i < generators.size(); i++) {
            for (int j = i + 1; j < generators.size(); j++) {
                Generator gen1 = generators.get(i);
                Generator gen2 = generators.get(j);
                
                drawParticleLine(gen1.getLocation(), gen2.getLocation(), dustOptions);
            }
        }
    }
    
    private void drawParticleLine(Location loc1, Location loc2, Particle.DustOptions dustOptions) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return;
        
        double distance = loc1.distance(loc2);
        if (distance > 50) return;
        
        double particlesPerBlock = 2;
        int particleCount = (int) (distance * particlesPerBlock);
        
        Location particleLoc1 = loc1.clone().add(0.5, 0.5, 0.5);
        Location particleLoc2 = loc2.clone().add(0.5, 0.5, 0.5);
        
        double xDiff = (particleLoc2.getX() - particleLoc1.getX()) / particleCount;
        double yDiff = (particleLoc2.getY() - particleLoc1.getY()) / particleCount;
        double zDiff = (particleLoc2.getZ() - particleLoc1.getZ()) / particleCount;
        
        for (int i = 0; i < particleCount; i++) {
            double x = particleLoc1.getX() + xDiff * i;
            double y = particleLoc1.getY() + yDiff * i;
            double z = particleLoc1.getZ() + zDiff * i;
            
            loc1.getWorld().spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, dustOptions);
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
    
    public double getGeneratorEfficiencyBonus(int generatorId) {
        GeneratorNetwork network = getGeneratorNetwork(generatorId);
        if (network == null) return 0;
        
        return network.getEfficiencyBonus();
    }
    
    public void shutdown() {
        if (networkVisualizerTask != null) {
            networkVisualizerTask.cancel();
            networkVisualizerTask = null;
        }
        
        networks.clear();
        generatorNetworkMap.clear();
        playersViewingNetworks.clear();
    }
    
    public Map<Integer, GeneratorNetwork> getNetworks() {
        return networks;
    }
}