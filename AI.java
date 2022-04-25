package commandeconomy;

import java.util.concurrent.ThreadLocalRandom; // for randomizing trade frequency and decisions
import java.util.HashMap;                      // for holding AI trade preferences

/**
 * An artificial intelligence capable of buying and selling wares
 * according to their profession.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-4-23
 */
public class AI {
   // STATIC ATTRIBUTES
   /** how much to buy or sell for each trade */
   private static int[] tradeQuantities  = null;

   // INSTANCE ATTRIBUTES
   /** IDs for wares the AI may buy */
   String[] purchasablesIDs      = null;
   /** IDs for wares the AI may sell */
   String[] sellablesIDs         = null;
   /** wares the AI may buy */
   transient Ware[] purchasables = null;
   /** wares the AI may sell */
   transient Ware[] sellables    = null;
   /** biases the AI carries when making trading decisions */
   HashMap<String, Float> preferences  = null;
   /** how many trade decisions the AI should make during a single trade event */
   transient int decisionsPerTradeEvent = 0;

   // STATIC METHODS
   /**
    * Recalculates how much to buy or sell for each trade
    * according to configuration settings.
    * <p>
    * Complexity: O(1)
    */
   public static void calcTradeQuantities() {
      // if necessary, initialize array
      if (tradeQuantities == null)
         tradeQuantities = new int[6];

      // calculate change amounts
      for (int i = 0; i < 6; i++)
         tradeQuantities[i]  = (int) (Config.aiTradeQuantityPercent * Config.quanMid[i]);
   }

   // INSTANCE METHODS
   /**
    * Creates an AI for GSON.
    * <p>
    * Complexity: O(1)
    */
   public AI() { }

   /**
    * Creates an AI with a given profession.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    */
   public AI(String[] purchasablesIDs, String[] sellablesIDs, 
             HashMap<String, Float> preferences) {}

   /**
    * Prepares and checks the AI's data, correcting errors where possible.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    * @return <code>true</code> if an error was detected and loading failed
    */
   public boolean load() {
      return true;
   }

   /**
    * Relinks affected wares with the marketplace's wares.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    * @return <code>true</code> if an error was detected and loading failed
    */
   public boolean reload() {
      return true;
   }

   /**
    * Sets how many trades the AI makes per trade event.
    * <p>
    * Complexity: O(1)
    * @param numDecisions number of trades to make when its time to trade
    */
   public void setDecisionsPerTradeEvent(int numDecisions) {
      if (numDecisions < 0)
         decisionsPerTradeEvent = 0;
      else
         decisionsPerTradeEvent = numDecisions;
      return;
   }

   /**
    * Tells the AI to make a trading decision, if possible.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    */
   public void trade() {
      return;
   }
};