package commandeconomy;

import java.util.Timer;               // for handling features relying on timers
import java.util.TimerTask;           // for disabling features relying on timers mid-execution

/**
 * Periodically applies compound interest to account funds.
 * <p>
 * Variables and functions for performing this task are
 * kept in Config and Marketplace, so the same code
 * may work for multiple interfaces.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2022-06-29
 */
public class AccountInterestApplier extends TimerTask  {
   /** whether the task should continue running */
   public volatile boolean stop = false;

   // account interest
   /** for growing accounts with compound interest */
   private static Timer timerAccountInterestApplier = null;
   /** for stopping the account interest-applying thread gracefully */
   private static AccountInterestApplier timertaskAccountInterestApplier = null;
   /** used to monitor a timer interval for configuration changes */
   private static int oldAccountPeriodicInterestInterval = 0;

   /**
    * Constructor: Initializes the periodic event.
    */
   public AccountInterestApplier() { }

   /**
    * Calls on the appropriate function for
    * periodically rebalancing the marketplace.
    */
   public void run() {
      if (stop)
         return;

      try {
         Account.applyAccountInterest();
      } catch (Exception e) {
         Account.releaseMutex();
         System.err.println("fatal error while applying account interest: " + e);
         e.printStackTrace();
      }
   }

   /**
    * Spawns and handles a thread for handling automatically rebalancing wares' quantities for sale within the marketplace.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfig() {
      // if necessary, start, reload, or stop applying account interest
      if (Config.accountPeriodicInterestEnabled  && Config.accountPeriodicInterestInterval > 0) {
         // start applying account interest
         if (timerAccountInterestApplier == null) {
            // initialize timer objects
            timerAccountInterestApplier     = new Timer(true);
            timertaskAccountInterestApplier = new AccountInterestApplier();

            // initialize periodically growing accounts with interest
            timerAccountInterestApplier.scheduleAtFixedRate(timertaskAccountInterestApplier, (long) 0, (long) Config.accountPeriodicInterestInterval);
         }

         // reload applying account interest
         else if (oldAccountPeriodicInterestInterval != Config.accountPeriodicInterestInterval) {
            // There's no way to change a task's period.
            // Therefore, it is necessary to stop the current task
            // and schedule a new one.
            timertaskAccountInterestApplier.stop = true;
            timertaskAccountInterestApplier.cancel();

            // initialize timertask object
            timertaskAccountInterestApplier = new AccountInterestApplier();

            // initialize periodically growing accounts with interest
            timerAccountInterestApplier.scheduleAtFixedRate(timertaskAccountInterestApplier, (long) 0, (long) Config.accountPeriodicInterestInterval);
         }
      }

      // stop applying account interest
      else if (timerAccountInterestApplier != null && (!Config.accountPeriodicInterestEnabled || Config.accountPeriodicInterestInterval <= 0)) {
         timertaskAccountInterestApplier.stop = true;
         timertaskAccountInterestApplier = null;
         timerAccountInterestApplier.cancel();
         timerAccountInterestApplier = null;
      }

      // record timer interval to monitor for changes
      oldAccountPeriodicInterestInterval = Config.accountPeriodicInterestInterval;
   }

   /**
    * Closes the thread handling applying account interest.
    * <p>
    * Complexity: O(1)
    */
   public static void end() {
      // if necessary, stop applying account interest
      if (timerAccountInterestApplier != null) {
         timertaskAccountInterestApplier.stop = true;
         timertaskAccountInterestApplier = null;
         timerAccountInterestApplier.cancel();
         timerAccountInterestApplier = null;
      }
   }
}