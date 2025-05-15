package id.rnggagib.blockmint.gui;

import id.rnggagib.BlockMint;
import id.rnggagib.blockmint.generators.Generator;
import id.rnggagib.blockmint.generators.GeneratorType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GUIManager {
    
    private final BlockMint plugin;
    private final Map<String, BaseGUI> activeGuis = new HashMap<>();
    
    public GUIManager(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public void openGeneratorManagement(Player player) {
        GeneratorListGUI gui = new GeneratorListGUI(plugin, player, 0);
        gui.open();
        activeGuis.put(player.getUniqueId().toString(), gui);
    }
    
    public BaseGUI getActiveGUI(String uuid) {
        return activeGuis.get(uuid);
    }
    
    public void removeActiveGUI(String uuid) {
        activeGuis.remove(uuid);
    }
    
    public void registerActiveGUI(String uuid, BaseGUI gui) {
        activeGuis.put(uuid, gui);
    }
    
    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            
            if (lore != null) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            
            meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES, 
                ItemFlag.HIDE_ENCHANTS, 
                ItemFlag.HIDE_POTION_EFFECTS, 
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_UNBREAKABLE
            );
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public static ItemStack createGeneratorIcon(Generator generator, boolean isReadyToCollect) {
        Material material = Material.valueOf(generator.getType().getMaterial());
        
        String nameColor = isReadyToCollect ? "&a" : "&6";
        String name = nameColor + generator.getType().getName() + " Generator";
        
        List<String> lore = new ArrayList<>();
        lore.add("&7Level: &e" + generator.getLevel() + "&7/&e" + generator.getType().getMaxLevel());
        lore.add("&7Value: &e$" + String.format("%.2f", generator.getValue()));
        
        if (isReadyToCollect) {
            lore.add("");
            lore.add("&a✓ Ready to collect!");
        } else {
            long elapsed = System.currentTimeMillis() - generator.getLastGeneration();
            long total = generator.getType().getGenerationTime() * 1000;
            int percent = (int) ((elapsed * 100) / total);
            long remaining = (total - elapsed) / 1000;
            
            lore.add("&7Progress: &e" + percent + "%");
            lore.add("&7Time remaining: &e" + formatTime(remaining));
        }
        
        lore.add("");
        lore.add("&eClick &7to view more options");
        lore.add("&eShift-Click &7to teleport to generator");
        
        return createItem(material, name, lore);
    }
    
    public static String formatTime(long seconds) {
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