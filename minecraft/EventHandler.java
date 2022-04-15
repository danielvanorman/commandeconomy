package commandeconomy;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;    // for registering to be notified of Forge events
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent.Save;             // for saving economy data when the world saves
import net.minecraftforge.event.world.WorldEvent;

/**
 * This class is notified of specific events from the
 * Minecraft Forge event bus, such as when the game is saving.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-06-06
 */
@EventBusSubscriber(modid = CommandEconomy.MODID)
public class EventHandler {
   /**
    * When the world is being saving, save the economy.
    *
    * @param event information concerning Minecraft's current state
    */
   @SubscribeEvent
   public static void onEvent(WorldEvent.Save event) {
      if (!Config.disableAutoSaving) {
         Marketplace.saveWares();
         Account.saveAccounts();
      }
   }
}
