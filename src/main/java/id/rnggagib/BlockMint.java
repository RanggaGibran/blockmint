package id.rnggagib;

import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/*
 * blockmint java plugin
 */
public class BlockMint extends JavaPlugin
{
  private static final Logger LOGGER=Logger.getLogger("blockmint");

  public void onEnable()
  {
    LOGGER.info("blockmint enabled");
  }

  public void onDisable()
  {
    LOGGER.info("blockmint disabled");
  }
}
