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
   // STATIC ATTRIBUTES
   // thread management
   /** for growing accounts with compound interest */
   private static Timer timerAccountInterestApplier = null;
   /** for stopping thread gracefully */
   private static AccountInterestApplier timertaskAccountInterestApplier = null;
   /** used to monitor a timer interval for configuration changes */
   private static int oldAccountPeriodicInterestFrequency = 0;

   // INSTANCE ATTRIBUTES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;

   // STATIC METHODS
   /**
    * Spawns and handles a thread for handling automatically rebalancing wares' quantities for sale within the marketplace.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfig() {
      // if necessary, start, reload, or stop applying account interest
      if (Config.accountPeriodicInterestEnabled  && Config.accountPeriodicInterestFrequency > 0) {
         // start applying account interest
         if (timerAccountInterestApplier == null) {
            // initialize timer objects
            timerAccountInterestApplier     = new Timer(true);
            timertaskAccountInterestApplier = new AccountInterestApplier();

            // initialize periodically growing accounts with interest
            timerAccountInterestApplier.scheduleAtFixedRate(timertaskAccountInterestApplier, 0L, Config.accountPeriodicInterestFrequency);
         }

         // reload applying account interest
         else if (oldAccountPeriodicInterestFrequency != Config.accountPeriodicInterestFrequency) {
            // There's no way to change a task's period.
            // Therefore, it is necessary to stop the current task
            // and schedule a new one.
            timertaskAccountInterestApplier.stop = true;
            timertaskAccountInterestApplier.cancel();

            // initialize timertask object
            timertaskAccountInterestApplier = new AccountInterestApplier();

            // initialize periodically growing accounts with interest
            timerAccountInterestApplier.scheduleAtFixedRate(timertaskAccountInterestApplier, 0L, Config.accountPeriodicInterestFrequency);
         }
      }

      // stop applying account interest
      else if (timerAccountInterestApplier != null && (!Config.accountPeriodicInterestEnabled || Config.accountPeriodicInterestFrequency <= 0))
         end();

      // record timer interval to monitor for changes
      oldAccountPeriodicInterestFrequency = Config.accountPeriodicInterestFrequency;
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

   /**
    * Loops through each account and applies compound interest.
    * <p>
    * Complexity: O(n), where n is the number of accounts usable within the market
    */
   private static void applyAccountInterest() {
      // wait for permission to adjust ware's properties
      Account.acquireMutex();

      // apply compound interest to every account
      for (Account account : Account.getAllAccounts()) {
         // apply compound interest
         account.setMoney(account.getMoney() * Config.accountPeriodicInterestPercent);
      }

      // allow other threads to adjust wares' properties
      Account.releaseMutex();
      return;
   }

   // INSTANCE METHODS
   /**
    * Constructor: Initializes the periodic event.
    */
   public AccountInterestApplier() { }

   /**
    * Calls on the appropriate function for
    * applying interest to all accounts.
    */
   public void run() {
      if (stop)
         return;

      try {
         applyAccountInterest();
      } catch (Exception e) {
         Account.releaseMutex();
         System.err.println("fatal error while applying account interest: " + e);
         e.printStackTrace();
      }
   }
}