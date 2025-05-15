package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GeneratorListGUI extends BaseGUI {
    
    private final int page;
    private final Map<Integer, Generator> generatorSlots = new HashMap<>();
    private final int GENERATORS_PER_PAGE = 36;
    
    public GeneratorListGUI(BlockMint plugin, Player player, int page) {
        super(plugin, player);
        this.page = page;
    }
    
    @Override
    public void open() {
        inventory = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Your Generators");
        
        List<Generator> playerGenerators = getPlayerGenerators();
        
        int maxPage = (playerGenerators.size() - 1) / GENERATORS_PER_PAGE;
        if (page > maxPage) {
            player.sendMessage(ChatColor.RED + "This page doesn't exist!");
            return;
        }
        
        int startIndex = page * GENERATORS_PER_PAGE;
        int endIndex = Math.min(startIndex + GENERATORS_PER_PAGE, playerGenerators.size());
        
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            Generator generator = playerGenerators.get(i);
            boolean isReady = generator.canGenerate();
            
            inventory.setItem(slot, GUIManager.createGeneratorIcon(generator, isReady));
            generatorSlots.put(slot, generator);
            slot++;
        }
        
        setBottomBar(maxPage);
        
        player.openInventory(inventory);
    }
    
    private void setBottomBar(int maxPage) {
        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, GUIManager.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }
        
        List<String> collectAllLore = new ArrayList<>();
        collectAllLore.add("&7Collect from all your");
        collectAllLore.add("&7ready generators.");
        inventory.setItem(49, GUIManager.createItem(Material.HOPPER, "&aCollect All", collectAllLore));
        
        if (page > 0) {
            inventory.setItem(45, GUIManager.createItem(Material.ARROW, "&aPrevious Page", null));
        }
        
        if (page < maxPage) {
            inventory.setItem(53, GUIManager.createItem(Material.ARROW, "&aNext Page", null));
        }
        
        List<String> pageLore = new ArrayList<>();
        pageLore.add("&7Current: &e" + (page + 1));
        pageLore.add("&7Total: &e" + (maxPage + 1));
        inventory.setItem(46, GUIManager.createItem(Material.PAPER, "&6Page Info", pageLore));
        
        List<String> statsLore = new ArrayList<>();
        statsLore.add("&7Generators: &e" + countPlayerGenerators());
        statsLore.add("&7Total Value: &e$" + String.format("%.2f", getTotalGeneratorValue()));
        inventory.setItem(52, GUIManager.createItem(Material.BOOK, "&6Generator Stats", statsLore));
    }
    
    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot >= inventory.getSize() || slot < 0) {
            return;
        }
        
        if (slot < 36 && generatorSlots.containsKey(slot)) {
            Generator generator = generatorSlots.get(slot);
            
            if (event.isShiftClick()) {
                teleportToGenerator(generator);
            } else {
                openGeneratorDetailsGUI(generator);
            }
            return;
        }
        
        switch (slot) {
            case 45:
                if (page > 0) {
                    GeneratorListGUI previousPage = new GeneratorListGUI(plugin, player, page - 1);
                    previousPage.open();
                    plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
                    plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
                }
                break;
            case 49:
                collectAllReadyGenerators();
                break;
            case 53:
                int maxPage = (getPlayerGenerators().size() - 1) / GENERATORS_PER_PAGE;
                if (page < maxPage) {
                    GeneratorListGUI nextPage = new GeneratorListGUI(plugin, player, page + 1);
                    nextPage.open();
                    plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
                    plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
                }
                break;
        }
    }
    
    private void openGeneratorDetailsGUI(Generator generator) {
        GeneratorDetailsGUI detailsGUI = new GeneratorDetailsGUI(plugin, player, generator, page);
        detailsGUI.open();
        plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
        plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
    }
    
    private void teleportToGenerator(Generator generator) {
        Location location = generator.getLocation().clone().add(0.5, 1, 0.5);
        player.teleport(location);
        player.sendMessage(ChatColor.GREEN + "Teleported to your " + generator.getType().getName() + " generator!");
        player.closeInventory();
    }
    
    private void collectAllReadyGenerators() {
        List<Generator> allGenerators = getPlayerGenerators();
        int collected = 0;
        double totalValue = 0;
        
        for (Generator generator : allGenerators) {
            if (generator.canGenerate()) {
                double value = generator.getValue();
                plugin.getEconomy().depositPlayer(player, value);
                generator.setLastGeneration(System.currentTimeMillis());
                
                totalValue += value;
                collected++;
                
                updatePlayerEarnings(player.getUniqueId(), value);
            }
        }
        
        if (collected > 0) {
            player.sendMessage(ChatColor.GREEN + "Collected $" + String.format("%.2f", totalValue) + 
                    " from " + collected + " generators!");
            
            GeneratorListGUI refreshedGUI = new GeneratorListGUI(plugin, player, page);
            refreshedGUI.open();
            plugin.getGUIManager().removeActiveGUI(player.getUniqueId().toString());
            plugin.getGUIManager().getActiveGUI(player.getUniqueId().toString());
        } else {
            player.sendMessage(ChatColor.YELLOW + "You don't have any generators ready to collect!");
        }
    }
    
    private List<Generator> getPlayerGenerators() {
        List<Generator> playerGenerators = new ArrayList<>();
        UUID playerUUID = player.getUniqueId();
        
        for (Generator generator : plugin.getGeneratorManager().getActiveGenerators().values()) {
            if (generator.getOwner().equals(playerUUID)) {
                playerGenerators.add(generator);
            }
        }
        
        return playerGenerators;
    }
    
    private int countPlayerGenerators() {
        return getPlayerGenerators().size();
    }
    
    private double getTotalGeneratorValue() {
        double total = 0;
        for (Generator generator : getPlayerGenerators()) {
            total += generator.getValue();
        }
        return total;
    }
    
    private void updatePlayerEarnings(UUID playerUUID, double amount) {
        try {
            PreparedStatement stmt = plugin.getDatabaseManager().prepareStatement(
                    "UPDATE player_stats SET total_earnings = total_earnings + ? WHERE uuid = ?"
            );
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not update player earnings in database: " + e.getMessage());
        }
    }
}