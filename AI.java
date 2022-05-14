package commandeconomy;

import java.util.concurrent.ThreadLocalRandom; // for randomizing trade frequency and decisions
import java.util.HashMap;                      // for holding AI trade preferences and decisions
import java.util.Map;                          // for iterating through hashmaps and priority queues
import java.util.Iterator;
import java.util.HashSet;                      // for validating AI trade preferences
import java.util.PriorityQueue;                // for storing multiple trade decisions
import java.util.Comparator;                   // for evaluating trade decisions when multiple should be made
import java.lang.StringBuilder;                // for faster error message generation
import java.util.Arrays;                       // for sorting trade decisions by desirability

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
   private static int[] tradeQuantities = null;

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

   // STRUCTS
   /**
    * A potential purchase or sale an AI could make.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2021-04-28
    */
   private class TradeDecision
   {
      /** ware to be traded */
      public Ware    ware;
      /** how good the deal seems */
      public float   desirability;
      /** whether the trade is to buy the ware */
      public boolean isPurchase;

      /**
       * Fills fields for a potential purchase or sale an AI could make.
       * <p>
       * Complexity: O(1)
       * @param ware         ware to be traded
       * @param desirability how good the deal seems
       * @param isPurchase   whether the trade is to buy the ware
       */
      public TradeDecision (Ware ware, float desirability, boolean isPurchase) {
         this.ware         = ware;
         this.desirability = desirability;
         this.isPurchase   = isPurchase;
      }
   };

   /**
    * Evaluates the worth of two trades an AI could make.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2021-04-28
    */
   private class comparatorTradeDecision implements Comparator<TradeDecision>
   {
   /**
    * Compares two trade decisions for order in a min heap.
    * Returns a negative integer, zero, or a positive integer
    * as the trade decision is less desirable, equally desirable, or more desirable than the second.
    * <p>
    * Complexity: O(1)
    * @param lhs the first trade offer to be compared
    * @param rhs the second trade offer to be compared
    * @return a negative integer, zero, or a positive integer as the trade decision is less desirable, equally desirable, or more desirable than the second
    */
      public int compare(TradeDecision lhs, TradeDecision rhs) {
         // handle null trade decisions
         if (lhs == null)
            return -1; // lhs is less desirable
         else if (rhs == null)
            return 1;  // lhs is more desirable

         // sort as a min heap
         float result = lhs.desirability - rhs.desirability;
         if (result > 0.0f)
            return 1;  // lhs is more desirable
         else if (result < 0.0f)
            return -1; // lhs is less desirable
         else
            return 0;  // lhs is equally desirable
      }
   };

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

   /**
    * Recalculates how much to buy or sell for each trade
    * according to configuration settings.
    * <p>
    * Complexity: O(n), where n is the number of wares to be traded
    * @param tradesPending initialized map of ware references and changes to their quantities for sale
    */
   public static void finalizeTrades(HashMap<Ware, Integer> tradesPending) {
      if (tradesPending == null || tradesPending.size() <= 0)
         return;

      // prepare to trade
      Ware    ware;
      Integer tradeQuantityObject;
      int     tradeQuantity;

      // prevent other threads from adjusting wares' properties
      Marketplace.acquireMutex();

      // perform each trade
      for (Map.Entry<Ware, Integer> entry : tradesPending.entrySet()) {
         ware                = entry.getKey();
         tradeQuantityObject = entry.getValue();

         // for paranoia's sake
         if (ware == null || tradeQuantityObject == null)
            continue;

         // grab adjustment for ware's quantity available for sale
         tradeQuantity = tradeQuantityObject.intValue();
         if (tradeQuantity == 0)
            continue;

         // buy a ware
         if (tradeQuantity < 0) {
            // only purchase what is available
            if (ware.getQuantity() >= -tradeQuantity)
               ware.addQuantity(tradeQuantity);
            else // buyout the ware
               ware.setQuantity(0);
         }

         // sell a ware
         else
            ware.addQuantity(tradeQuantity);
      }

      // allow other threads to adjust wares' properties
      Marketplace.releaseMutex();

      // clear pending trades
      tradesPending.clear();
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
    * @param profession      name of the AI's profession; used for printing error messages
    * @param purchasablesIDs IDs for wares the AI may buy
    * @param sellablesIDs    IDs for wares the AI may sell
    * @param preferences     biases the AI may carry toward trades
    */
   public AI(String profession, String[] purchasablesIDs, String[] sellablesIDs, 
             HashMap<String, Float> preferences) {
      // set variables
      this.purchasablesIDs = purchasablesIDs;
      this.sellablesIDs    = sellablesIDs;
      this.preferences     = preferences;

      // validate the AI
      load(profession);
   }

   /**
    * Prepares and checks the AI's data, correcting errors where possible.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    * @param profession name of the AI's profession; used for printing error messages
    * @return <code>true</code> if an error was detected and loading failed
    */
   public boolean load(String profession) {
      HashSet<String> wareIDsTrading    = new HashSet<String>(); // create a set of ware IDs to compare to preferences later on
      StringBuilder   wareIDsMismatched = null;                  // prepare to print preferred ware IDs which don't match any IDs used for trading
      Ware            ware;                                      // holds ware references for checking validity
      Ware[]          compressedArray;                           // for resizing arrays to not have null references
      int             index             = 0;                     // used for accessing array containing ware references
      boolean         isValid           = true;                  // whether the AI loaded successfully

      // if purchasables' IDs are specified,
      // try to load them
      if (purchasablesIDs != null) {
         if (purchasablesIDs.length > 0) {
            // allocate space for purchasables
            purchasables = new Ware[purchasablesIDs.length];

            // add existing purchasables to the AI's list of wares to buy
            for (String wareID : purchasablesIDs) {
               // add ware ID to set for later validation
               wareIDsTrading.add(wareID);

               // grab the ware to be used
               ware = Marketplace.translateAndGrab(wareID);
               // if the ware ID isn't valid, skip it
               if (ware == null) {
                  // print a warning
                  if (Config.aiReportInvalidWares)
                     Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID_WARE_PRO + profession +
                                                            CommandEconomy.WARN_AI_INVALID_WARE_IDS + wareID);

                  // skip the ware
                  continue;
               }

               // add the ware to the array
               purchasables[index] = ware;
               index++;
            }

            // if no wares were valid, don't worry about buying
            if (index == 0)
               purchasables = null;

            // if necessary, resize the purchasables array
            else if (purchasablesIDs.length > index) {
               // create new array without null references
               compressedArray = new Ware[index];

               // copy ware references to appropriately-sized array
               System.arraycopy(purchasables, 0, compressedArray, 0, index);

               // replace old array with new
               purchasables = compressedArray;
            }
         }

         // if the array has zero elements,
         // ensure it is set to null
         else
            purchasables = null;
      }

      // if sellables' IDs are specified,
      // try to load them
      if (sellablesIDs != null) {
         if (sellablesIDs.length > 0) {
            index = 0;

            // allocate space for sellables
            sellables = new Ware[sellablesIDs.length];

            // add existing sellables to the AI's list of wares to sell
            for (String wareID : sellablesIDs) {
               // add ware ID to set for later validation
               wareIDsTrading.add(wareID);

               // grab the ware to be used
               ware = Marketplace.translateAndGrab(wareID);
               // if the ware ID isn't valid, skip it
               if (ware == null) {
                  // print a warning
                  if (Config.aiReportInvalidWares)
                     Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID_WARE_PRO + profession +
                                                            CommandEconomy.WARN_AI_INVALID_WARE_IDS + wareID);

                  // skip the ware
                  continue;
               }

               // add the ware to the array
               sellables[index] = ware;
               index++;
            }

            // if no wares were valid, don't worry about buying
            if (index == 0)
               sellables = null;

            // if necessary, resize the sellables array
            else if (sellablesIDs.length > index) {
               // create new array without null references
               compressedArray = new Ware[index];

               // copy ware references to appropriately-sized array
               System.arraycopy(sellables, 0, compressedArray, 0, index);

               // replace old array with new
               sellables = compressedArray;
            }
         }

         // if the array has zero elements,
         // ensure it is set to null
         else
            sellables = null;
      }

      // if preferences exist, ensure their ware IDs and aliases
      // correspond to purchasable and sellable IDs
      if (preferences != null) {
         if (preferences.size() > 0) {
            Iterator<Map.Entry<String, Float>> iterator = preferences.entrySet().iterator(); // get hashmap iterator
            Map.Entry<String, Float> entry;  // contains preference currently being processed
            String                   wareID; // identifier for ware AI has an opinion of
            float                    bias;   // how much AI favors or dislikes the ware
            wareIDsMismatched = new StringBuilder(); // prepare to print preferred ware IDs which don't match any IDs used for trading

            // check every preference's validity
            while (iterator.hasNext()) {
               entry  = iterator.next();
               wareID = entry.getKey();
               bias   = entry.getValue();

               // if a preference is invalid, remove it
               if (wareID == null || Float.isNaN(bias)) {
                  if (Config.aiReportInvalidWares)
                     Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID_PREF_PRO + profession +
                                                            CommandEconomy.WARN_AI_INVALID_PREF_IDS + wareID);

                  iterator.remove();
                  continue;
               }

               // if any preferences do not match a ware, flag an error
               if (!wareIDsTrading.contains(wareID)) {
                  isValid = false;

                  wareIDsMismatched.append(wareID).append(", "); // record mismatched ware ID
               }
            }
         }

         // if the map has zero elements,
         // ensure it is set to null
         else
            preferences = null;
      }

      // if ware IDs are mismatched,
      // report the matching ones
      if (!isValid && wareIDsMismatched != null)
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_AI_PREFS_MISMATCH_PRO + profession +
                                                CommandEconomy.ERROR_AI_PREFS_MISMATCH_IDS + wareIDsMismatched.substring(0, wareIDsMismatched.length() - 2)); // -2 characters removes ending comma and space

      return !isValid; // true == error detected
   }

   /**
    * Relinks affected wares with the marketplace's wares.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    * @param profession name of the AI's profession; used for printing error messages
    */
   public void reload(String profession) {
      Ware    ware;            // holds ware references for checking validity
      Ware[]  compressedArray; // for resizing arrays to not have null references
      int     index   = 0;     // used for accessing array containing ware references

      // if purchasable IDs are specified,
      // try to load them
      if (purchasablesIDs != null) {
         if (purchasablesIDs.length > 0) {
            // allocate space for purchasables
            purchasables = new Ware[purchasablesIDs.length];

            // add existing purchasables to the AI's list of wares to buy
            for (String wareID : purchasablesIDs) {
               // grab the ware to be used
               ware = Marketplace.translateAndGrab(wareID);
               // if the ware ID isn't valid, skip it
               if (ware == null) {
                  // print a warning
                  if (Config.aiReportInvalidWares)
                     Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID_WARE_PRO + profession +
                                                            CommandEconomy.WARN_AI_INVALID_WARE_IDS + wareID);

                  // skip the ware
                  continue;
               }

               // add the ware to the array
               purchasables[index] = ware;
               index++;
            }

            // if no wares were valid, don't worry about buying
            if (index == 0)
               purchasables = null;

            // if necessary, resize the purchasables array
            else if (purchasablesIDs.length > index) {
               // create new array without null references
               compressedArray = new Ware[index];

               // copy ware references to appropriately-sized array
               System.arraycopy(purchasables, 0, compressedArray, 0, index);

               // replace old array with new
               purchasables = compressedArray;
            }
         }

         // if the array has zero elements,
         // ensure it is set to null
         else
            purchasables = null;
      }

      // if sellable IDs are specified,
      // try to load them
      if (sellablesIDs != null) {
         if (sellablesIDs.length > 0) {
            index = 0;

            // allocate space for sellables
            sellables = new Ware[sellablesIDs.length];

            // add existing sellables to the AI's list of wares to sell
            for (String wareID : sellablesIDs) {
               // grab the ware to be used
               ware = Marketplace.translateAndGrab(wareID);
               // if the ware ID isn't valid, skip it
               if (ware == null) {
                  // print a warning
                  if (Config.aiReportInvalidWares)
                     Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID_WARE_PRO + profession +
                                                            CommandEconomy.WARN_AI_INVALID_WARE_IDS + wareID);

                  // skip the ware
                  continue;
               }

               // add the ware to the array
               sellables[index] = ware;
               index++;
            }

            // if no wares were valid, don't worry about buying
            if (index == 0)
               sellables = null;

            // if necessary, resize the sellables array
            else if (sellablesIDs.length > index) {
               // create new array without null references
               compressedArray = new Ware[index];

               // copy ware references to appropriately-sized array
               System.arraycopy(sellables, 0, compressedArray, 0, index);

               // replace old array with new
               sellables = compressedArray;
            }
         }

         // if the array has zero elements,
         // ensure it is set to null
         else
            sellables = null;
      }
   }

   /**
    * Sets how many trades the AI makes per trade event.
    * <p>
    * Complexity: O(1)
    */
   public void incrementDecisionsPerTradeEvent() {
      decisionsPerTradeEvent += 1;
   }

   /**
    * Sets how many trades the AI makes per trade event.
    * <p>
    * Complexity: O(1)
    */
   public void resetDecisionsPerTradeEvent() {
      decisionsPerTradeEvent = 0;
   }

   /**
    * Tells the AI to make a trading decision, if possible.
    * <p>
    * Complexity: O(n), where n is the number of wares affected by the AI
    * @param tradesPending initialized map of ware references and changes to their quantities for sale
    */
   public void trade(HashMap<Ware, Integer> tradesPending) {
      // if not allowed to trade,
      // unaware of how much volume to trade at once,
      // or have nowhere to schedule trades,
      // don't trade
      if (decisionsPerTradeEvent <= 0 ||
          tradeQuantities == null ||
          tradesPending == null)
         return;

      PriorityQueue<TradeDecision> tradeDecisions = null;         // if multiple trade decisions should be used, track the best potential decisions
      Ware    wareBest              = null;                       // the best offer seen so far
      float   desirabilityBest      = Float.NEGATIVE_INFINITY;    // willingness to make the best offer seen so far
      float   randomness;                                         // pseudorandomness influencing decisions
      float   priceCurrent;                                       // ware's current unit price
      float   priceEquilibrium;                                   // ware's unit price when supply and demand are balanced
      float   desirabilityTrade;                                  // AI's willingness to make the trade; lower than 1.0 == undesirable, higher == more desirable
      float   aiPreference;                                       // AI's personal bias towards a ware
      boolean makeMultipleDecisions = decisionsPerTradeEvent > 1; // whether the AI should make multiple trades at once
      boolean isPurchaseBest        = true;                       // whether the best offer seen so far is a purchasing decision

      // if multiple trade decisions should be made,
      // prepare a min heap for holding all trade decisions
      if (makeMultipleDecisions)
         tradeDecisions = new PriorityQueue<TradeDecision>(decisionsPerTradeEvent, new comparatorTradeDecision());

      // if necessary, consider wares able to be bought
      if (purchasables != null) {
         // evaluate buying desirabilities
         for (Ware ware : purchasables) {
            // if nothing is in stock, skip the ware
            if (ware.getQuantity() <= 0)
               continue;

            // get ware's prices
            priceEquilibrium = Marketplace.getEquilibriumPrice(ware, true);
            priceCurrent     = Marketplace.getPrice(null, ware.getWareID(), 1, true);

            // By enforcing a current price percentage floor, pseudorandomness' influence
            // is protected from excessive effects of supply and demand.
            // Additionally, the floor limits how high buying's desirability may be at extremes,
            // making it more possible for selling to be chosen despite prices being excessively low.
            // Having a floor greater than zero protects from division by zero and
            // complications from increasingly negative prices.
            if (priceEquilibrium <= 0.01f)
               priceEquilibrium = 0.01f;
            if (priceCurrent <= 0.01f)
               priceCurrent = 0.01f;

            // if necessary, determine randomness
            if (Config.aiRandomness > 0.0f)
               randomness = (float) ThreadLocalRandom.current().nextDouble(Config.aiRandomness); // from 0.0 to randomness max
            else
               randomness = 0.0f;

            // check for personal bias
            if (preferences != null)
               aiPreference = preferences.getOrDefault(ware.getWareID(), 1.0f);
            else
               aiPreference = 1.0f; // lower than 1.0 == undesirable, higher == more desirable

            // determine willingness to perform trade
            // buying desirability = ((ware's equilibrium price / current price) + pseudorandom percentage) * AI preference
            desirabilityTrade = ((priceEquilibrium / priceCurrent) + randomness) * aiPreference;

            // handle recording decisions differently based on
            // whether multiple trade decisions should be used
            if (makeMultipleDecisions) {
               // if multiple trade decisions should be made,
               // record it in the min heap
               // if the heap is full, compare the current trade decision to the least desired one
               if (tradeDecisions.size() >= decisionsPerTradeEvent &&
                   tradeDecisions.peek().desirability < desirabilityTrade) {
                  tradeDecisions.poll(); // removes least desirable offer
                  tradeDecisions.add(new TradeDecision(ware, desirabilityTrade, true));
               }
               // if the heap has room, add the trade decision
               else
                  tradeDecisions.add(new TradeDecision(ware, desirabilityTrade, true));
            }

            else {
               // if this the most desirable ware to be bought, record it
               if (desirabilityBest < desirabilityTrade) {
                  wareBest         = ware;
                  desirabilityBest = desirabilityTrade;
               }
            }
         }
      }

      // if necessary, consider wares able to be sold
      if (sellables != null) {
         // evaluate selling desirabilities
         for (Ware ware : sellables) {
            // get ware's prices
            priceEquilibrium = Marketplace.getEquilibriumPrice(ware, false);
            priceCurrent     = Marketplace.getPrice(null, ware.getWareID(), 1, false);

            // prevent division by zero errors
            if (priceEquilibrium <= 0.01f)
               priceEquilibrium = 0.01f;
            if (priceCurrent <= 0.01f)
               priceCurrent = 0.01f;

            // if necessary, determine randomness
            if (Config.aiRandomness > 0.0f)
               randomness = (float) ThreadLocalRandom.current().nextDouble(Config.aiRandomness); // from 0.0 to randomness max
            else
               randomness = 0.0f;

            // check for personal bias
            if (preferences != null)
               aiPreference = preferences.getOrDefault(ware.getWareID(), 1.0f);
            else
               aiPreference = 1.0f; // lower than 1.0 == undesirable, higher == more desirable

            // determine willingness to perform trade
            // selling desirability = ((current price / ware's equilibrium price) + pseudorandom percentage) * AI preference
            desirabilityTrade = ((priceCurrent / priceEquilibrium) + randomness) * aiPreference;

            // handle recording decisions differently based on
            // whether multiple trade decisions should be used
            if (makeMultipleDecisions) {
               // if multiple trade decisions should be made,
               // record it in the min heap
               // if the heap is full, compare the current trade decision to the least desired one
               if (tradeDecisions.size() >= decisionsPerTradeEvent &&
                   tradeDecisions.peek().desirability < desirabilityTrade) {
                  tradeDecisions.poll(); // removes least desirable offer
                  tradeDecisions.add(new TradeDecision(ware, desirabilityTrade, false));
               }
               // if the heap has room, add the trade decision
               else
                  tradeDecisions.add(new TradeDecision(ware, desirabilityTrade, false));
            }

            else {
               // if this the most desirable ware to be sold, record it
               if (desirabilityBest < desirabilityTrade) {
                  wareBest         = ware;
                  desirabilityBest = desirabilityTrade;
                  isPurchaseBest   = false;
               }
            }
         }
      }

      // if no ware was found for whatever reason, stop
      if (wareBest == null &&
          (tradeDecisions == null || tradeDecisions.size() <= 0))
         return;

      // prepare to combine trade amounts with pending trades' amounts
      Integer intObject;
      int     intValue;

      // make decisions according to the number of trade decisions to be made
      if (makeMultipleDecisions) {
         // If multiple trade decisions should be made,
         // loop through the most desirable trade decisions
         // until every trade decision to be made is made.
         TradeDecision[] decisions     = tradeDecisions.toArray(new TradeDecision[tradeDecisions.size()]);
         TradeDecision   decision;          // current trade offer to be made
         int             index         = 0; // for iterating through trade decisions
         int             decisionsMade = 0; // track how many decisions should be made
                                            // in case there are more decisions to make than wares to trade

         // sort trade decisions by desirability
         Arrays.sort(decisions, tradeDecisions.comparator());

         // keep trading as long as there are decisions to be made
         while (decisionsMade < decisionsPerTradeEvent) {
            // if more decisions should be made,
            // but all offers have been parsed,
            // cycle back to the first decision
            if (index >= decisions.length)
               index = 0;

            // grab the next offer to be made
            decision = decisions[index];
            index++;

            // if the ware is null, don't trade
            if (decision == null)
               continue;

            // make the trade
            intObject = tradesPending.get(decision.ware);
            if (intObject == null)
               intValue = 0;
            else
               intValue = intObject.intValue();

            // buy a ware
            if (decision.isPurchase)
               tradesPending.put(decision.ware, intValue - tradeQuantities[decision.ware.getLevel()]);

            // sell a ware
            else
               tradesPending.put(decision.ware, intValue + tradeQuantities[decision.ware.getLevel()]);
            decisionsMade++;
         }
      }
      else {
         intObject = tradesPending.get(wareBest);
         if (intObject == null)
            intValue = 0;
         else
            intValue = intObject.intValue();

         // buy a ware
         if (isPurchaseBest)
            tradesPending.put(wareBest, intValue - tradeQuantities[wareBest.getLevel()]);
         // sell a ware
         else
            tradesPending.put(wareBest, intValue + tradeQuantities[wareBest.getLevel()]);
      }

      return;
   }
};