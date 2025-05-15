package id.rnggagib.blockmint.utils;

import id.rnggagib.blockmint.generators.GeneratorType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GeneratorItemManager {
    
    private static final NamespacedKey GENERATOR_TYPE_KEY = new NamespacedKey("blockmint", "generator_type");
    
    public static ItemStack createGeneratorItem(GeneratorType type) {
        Material material = Material.valueOf(type.getMaterial());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6§l" + type.getName());
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Place this to create a generator");
        lore.add("§7Base Value: §e$" + String.format("%.2f", type.getBaseValue()));
        lore.add("§7Max Level: §e" + type.getMaxLevel());
        lore.add("§7Generation Time: §e" + formatTime(type.getGenerationTime()));
        lore.add("");
        lore.add("§7Value Multiplier: §e" + String.format("%.1fx", type.getValueMultiplier()));
        lore.add("");
        lore.add("§e§lBlockMint Generator");
        
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(GENERATOR_TYPE_KEY, PersistentDataType.STRING, type.getId());
        
        item.setItemMeta(meta);
        return item;
    }
    
    public static String getGeneratorType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(GENERATOR_TYPE_KEY, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(GENERATOR_TYPE_KEY, PersistentDataType.STRING);
        }
        
        return null;
    }
    
    private static String formatTime(long seconds) {
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