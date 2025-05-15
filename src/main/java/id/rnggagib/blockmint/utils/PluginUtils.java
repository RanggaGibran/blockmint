package id.rnggagib.blockmint.utils;

import id.rnggagib.BlockMint;

public class PluginUtils {
    
    private final BlockMint plugin;
    
    public PluginUtils(BlockMint plugin) {
        this.plugin = plugin;
    }
    
    public GeneratorItemManager getGeneratorItemManager() {
        return new GeneratorItemManager();
    }
    
    public MessageManager getMessageManager() {
        return plugin.getMessageManager();
    }
}