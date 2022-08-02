package commandeconomy;

import java.util.TimerTask;

/**
 * Periodically saves wares and accounts.
 * <p>
 * This class is only used by the terminal interface.
 * Autosaving in Minecraft is done through an
 * Event Handler alerted upon saving the world.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-12-12
 */
public class Autosaver extends TimerTask  {
   // INSTANCE ATTRIBUTES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;

   // INSTANCE METHODS
   /**
    * Calls on the appropriate function for
    * periodically saving the marketplace.
    */
   public void run() {
      if (stop)
         return;

      try {
         // call corresponding functions
         Marketplace.saveWares();
         Account.saveAccounts();
      } catch (Exception e) {
         System.err.println("fatal error while automatically saving the marketplace: " + e.getMessage());
      }
   }
}