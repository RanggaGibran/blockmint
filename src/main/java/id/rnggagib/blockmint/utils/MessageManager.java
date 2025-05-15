package id.rnggagib.blockmint.utils;

import id.rnggagib.BlockMint;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    
    private final BlockMint plugin;
    private final MiniMessage miniMessage;
    private final BukkitAudiences adventure;
    private FileConfiguration messagesConfig;
    private final Map<String, String> messageCache = new HashMap<>();
    
    public MessageManager(BlockMint plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.adventure = BukkitAudiences.create(plugin);
        reload();
    }
    
    public void reload() {
        messagesConfig = plugin.getConfigManager().getMessagesConfig();
        loadMessages();
    }
    
    private void loadMessages() {
        messageCache.clear();
        for (String key : messagesConfig.getKeys(true)) {
            if (messagesConfig.isString(key)) {
                messageCache.put(key, messagesConfig.getString(key));
            }
        }
    }
    
    public String getMessage(String path) {
        return messageCache.getOrDefault(path, path);
    }
    
    public String formatMessage(String message, Map<String, String> placeholders) {
        String formatted = message;
        
        String prefix = messageCache.getOrDefault("prefix", "<gradient:#FF5F6D:#FFC371>BlockMint</gradient> <dark_gray>»</dark_gray> ");
        formatted = formatted.replace("<prefix>", prefix);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                formatted = formatted.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
            }
        }
        return formatted;
    }
    
    public void send(CommandSender sender, String path) {
        send(sender, path, null);
    }
    
    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        if (message == null || message.isEmpty()) {
            return;
        }
        
        message = formatMessage(message, placeholders);
        
        if (sender instanceof Player player) {
            if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                message = PlaceholderAPI.setPlaceholders(player, message);
            }
            adventure.sender(sender).sendMessage(miniMessage.deserialize(message));
        } else {
            sender.sendMessage(stripMiniMessage(message));
        }
    }
    
    public Component deserialize(String message) {
        return miniMessage.deserialize(message);
    }
    
    public String stripMiniMessage(String message) {
        Pattern pattern = Pattern.compile("<[^>]*>");
        Matcher matcher = pattern.matcher(message);
        return matcher.replaceAll("");
    }
    
    public void close() {
        if (adventure != null) {
            adventure.close();
        }
    }
}