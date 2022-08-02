package commandeconomy;

import java.util.Timer;               // for handling features relying on timers
import java.util.TimerTask;           // for disabling features relying on timers mid-execution

/**
 * Periodically moves wares' quantities for sale closer to equilibrium.
 * <p>
 * Variables and functions for performing this task are
 * kept in Config and Marketplace, so the same code
 * may work for multiple interfaces.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-09-02
 */
public class MarketRebalancer extends TimerTask  {
   // STATIC ATTRIBUTES
   // thread management
   /** for periodically moving wares' quantities for sale toward equilibrium */
   private static Timer timerMarketRebalancer = null;
   /** for stopping the thread gracefully */
   private static MarketRebalancer timertaskMarketRebalancer = null;
   /** used to monitor a timer interval for configuration changes */
   private static int oldAutomaticStockRebalancingFrequency = 0;
   /** amounts each hierarchy level should periodically adjust by when rebalancing the market */
   private static int[] autoRebalanceAdjustQuantities = null;

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
      // if necessary, start, reload, or stop automatic market rebalancing
      if (Config.automaticStockRebalancing && Config.automaticStockRebalancingFrequency > 0) {
         // start automatic market rebalancing
         if (timerMarketRebalancer == null ) {
            // calculate how much to adjust wares' quantities for sale
            calcQuantityChanges();

            // initialize timer objects
            timerMarketRebalancer     = new Timer(true);
            timertaskMarketRebalancer = new MarketRebalancer();

            // initialize periodically rebalancing the marketplace
            timerMarketRebalancer.scheduleAtFixedRate(timertaskMarketRebalancer, (long) 0, (long) Config.automaticStockRebalancingFrequency * 60000); // 60000 milliseconds per minute
         }

         // reload automatic market rebalancing
         else if (oldAutomaticStockRebalancingFrequency != Config.automaticStockRebalancingFrequency) {
            // There's no way to change a task's period.
            // Therefore, it is necessary to stop the current task
            // and schedule a new one.
            timertaskMarketRebalancer.stop = true;
            timertaskMarketRebalancer.cancel();

            // initialize timertask object
            timertaskMarketRebalancer = new MarketRebalancer();

            // initialize periodically rebalancing the marketplace
            timerMarketRebalancer.scheduleAtFixedRate(timertaskMarketRebalancer, (long) 0, (long) Config.automaticStockRebalancingFrequency * 60000); // 60000 milliseconds per minute
         }
      }

      // stop automatic market rebalancing
      else if (timerMarketRebalancer != null &&
               (!Config.automaticStockRebalancing || Config.automaticStockRebalancingFrequency <= 0))
         end();

      // record timer interval to monitor for changes
      oldAutomaticStockRebalancingFrequency = Config.automaticStockRebalancingFrequency;
   }

   /**
    * Closes the thread handling moving wares' quantities for sale toward equilibrium.
    * <p>
    * Complexity: O(1)
    */
   public static void end() {
      // if necessary, stop automatic marketplace rebalancing
      if (timerMarketRebalancer != null) {
         timertaskMarketRebalancer.stop = true;
         timertaskMarketRebalancer = null;
         timerMarketRebalancer.cancel();
         timerMarketRebalancer = null;
         autoRebalanceAdjustQuantities = null;

         // deallocate memory
         autoRebalanceAdjustQuantities = null;
      }
   }

   /**
    * Calculates how much to adjust wares' quantities for sale
    * according to configuration settings.
    * <p>
    * Complexity: O(1)
    */
   public static void calcQuantityChanges() {
      // generate automatic market rebalancing incremental values
      autoRebalanceAdjustQuantities = new int[]{(int) (Config.automaticStockRebalancingPercent * Config.quanMid[0]),
                                                (int) (Config.automaticStockRebalancingPercent * Config.quanMid[1]),
                                                (int) (Config.automaticStockRebalancingPercent * Config.quanMid[2]),
                                                (int) (Config.automaticStockRebalancingPercent * Config.quanMid[3]),
                                                (int) (Config.automaticStockRebalancingPercent * Config.quanMid[4]),
                                                (int) (Config.automaticStockRebalancingPercent * Config.quanMid[5])};
   }

   /**
    * Loops through each ware and moves its
    * quantity available for sale closer to equilibrium.
    * <p>
    * Complexity: O(n), where n is the number of wares in the market
    */
   private static void incrementallyRebalanceMarket() {
      // if config for this feature hasn't been set up,
      // this feature is probably disabled or something's wrong
      if (autoRebalanceAdjustQuantities == null)
         return;

      // set up variables
      int quanEquilibrium;
      int quanOnMarket;
      int adjustment;

      // prevent other threads from adjusting wares' properties
      Marketplace.acquireMutex();

      // change every applicable ware's quantity for sale
      for (Ware ware : Marketplace.getAllWares()) {
         // if feature is suddenly disabled,
         // stop executing immediately
         // prevents null pointer exceptions
         // after reloading
         if (autoRebalanceAdjustQuantities == null) {
            // allow other threads to adjust wares' properties
            Marketplace.releaseMutex();
            return;
         }

         // make sure ware's quantity should be adjusted
         if (ware instanceof WareUntradeable || ware instanceof WareLinked)
            continue;

         quanEquilibrium = Config.quanMid[ware.getLevel()];
         quanOnMarket    = ware.getQuantity();
         adjustment      = autoRebalanceAdjustQuantities[ware.getLevel()];

         // if the ware is overstocked
         if (quanOnMarket - adjustment > quanEquilibrium) {
            ware.subtractQuantity(adjustment);
         }

         // if ware is understocked
         else if (quanOnMarket + adjustment < quanEquilibrium) {
            ware.addQuantity(adjustment);
         }

         // if the ware is near equilibrium
         else {
            ware.setQuantity(quanEquilibrium);
         }
      }

      // allow other threads to adjust wares' properties
      Marketplace.releaseMutex();
      return;
   }

   // INSTANCE METHODS
   /**
    * Constructor: Initializes the periodic event.
    */
   public MarketRebalancer() { }

   /**
    * Calls on the appropriate function for
    * periodically rebalancing the marketplace.
    */
   public void run() {
      if (stop)
         return;

      try {
         incrementallyRebalanceMarket();
      } catch (Exception e) {
         Marketplace.releaseMutex();
         System.err.println("fatal error while automatically rebalancing the marketplace: " + e);
         e.printStackTrace();
      }
   }
}