package commandeconomy;

import com.google.gson.Gson;          // for saving and loading
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;             // for iterating through components when manufacturing
import java.util.Map;                 // for iterating through hashmaps
import java.util.UUID;                // for more securely tracking users internally

/**
 * Holds values for a ware used within the market.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-02-04
 */
@SuppressWarnings("deprecation") // unfortunately, Minecraft 1.12.2's gson requires a JSONParser object
public abstract class Ware
{
   // STATIC VARIABLES
   /** converts wares to JSON */
   static final transient Gson gson = new Gson();
   /** adds and removes properties from JSON */
   static transient JsonObject jsonObject;
   /** Used for constructing wares from JSON. 
     * Unfortunately, Minecraft 1.12.2's gson requires a JSONParser object. */
   private static transient JsonParser jsonParser = new JsonParser();

   // linked prices
   /** multipliers for wares whose linked price multipliers have recently been calculated */
   private static Map<Ware, Float> recentLinkedPriceResults = null;

   // INSTANCE VARIABLES
   /** wares used to create this ware */
   transient Ware[] components;
   /** IDs of wares used to create this ware */
   String[] componentsIDs;
   /** internal name used for creating the ware in-game and using it within the marketplace */
   String wareID;
   /** human-readable alternate name */
   String alias;
   /** unmodified price */
   float priceBase = Float.NaN;
   /** current market stock */
   int quantity = -1;
   /** how much of this ware results from a single iteration of its crafting/processing recipe */
   int yield;
   /** hierarchy level */
   byte level;

   // METHODS
   /**
    * Returns the ware's internal name.
    * @return the ware's internal name
    */
   public String getWareID() {
      return wareID;
   }

   /**
    * Returns how much of the ware is available on the market.
    * @return amount of ware for sale
    */
   public int getQuantity() {
      return quantity;
   }

   /**
    * Sets how much of the ware is available on the market.
    * @param quantity new quantity available within the market
    */
   public void setQuantity(int quantity) {
      this.quantity = quantity;
      Marketplace.markAsChanged(this);
   }

   /**
    * Increases how much of the ware is available on the market by the given amount.
    * <p>
    * Complexity: O(1)
    * @param adjustment increase to quantity available within the market
    */
   public void addQuantity(int adjustment) {
      quantity += adjustment;
      Marketplace.markAsChanged(this);
   }

   /**
    * Decreases how much of the ware is available on the market by the given amount.
    * <p>
    * Complexity: O(1)
    * @param adjustment decrease to quantity available within the market
    */
   public void subtractQuantity(int adjustment) {
      quantity -= adjustment;
      Marketplace.markAsChanged(this);
   }

   /**
    * Returns the price of the ware without considering
    * supply and demand or additional multipliers/factors.
    * @return unadjusted price of the ware
    */
   public float getBasePrice() {
      return priceBase;
   }

   /**
    * Returns the preferred human-readable alternate name for the ware.
    * @return ware's preferred alternate name
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Changes the preferred human-readable alternate name for the ware.
    * @param alias ware's unique alternate name
    */
   public void setAlias(String alias) {
      if (alias != null && !alias.isEmpty()) {
         // if the alias contains a colon,
         // remove everything before the colon
         if (alias.contains(":")) {
            this.alias = alias.substring(alias.indexOf(':') + 1, alias.length());
         } else {
            this.alias = alias;
         }
      } else {
         this.alias = null;
      }
      Marketplace.markAsChanged(this);
   }

   /**
    * Returns the hierarchy level of the ware.
    * Levels may be anywhere from 0-5, inclusive.
    * @return the hierarchy level of the ware
    */
   public byte getLevel() {
      return level;
   }

   /**
    * Changes the hierarchy level of the ware.
    * Levels may be anywhere from 0-5, inclusive.
    * @param level ware's new hierarchy level
    */
   public void setLevel(byte level) {
      // don't allow negative levels
      if (level < 0)
         this.level = 0;

      // don't allow levels past the highest level
      else if (level > 5)
         this.level = 5;

      else
         this.level = level;

      Marketplace.markAsChanged(this);
   }

   /**
    * Changes the hierarchy level of the ware by the given amount.
    * Use negative numbers to subtract from availability.
    * Levels may be anywhere from 0-5, inclusive.
    * @param adjustment change to ware's hierarchy level
    */
   public void addLevel(byte adjustment) {
      level += adjustment;

      // don't allow negative levels
      if (level < 0)
         level = 0;

      // don't allow levels past the highest level
      else if (level > 5)
         level = 5;

      Marketplace.markAsChanged(this);
   }

   /**
    * Returns true if the ware uses other wares to create itself.
    * @return <code>true</code> if the ware has component IDs
    *         <code>false</code> if the ware has no component IDs
    */
   public boolean hasComponents() {
      return componentsIDs != null;
   }

   /**
    * Changes the list of ware IDs used to create this ware 
    * and this ware's base price.
    * Assumes base price is zero.
    * <p>
    * Complexity: O(n)
    * @param componentsIDs list of ware IDs used to create this ware
    * @return <code>true</code> if components were loaded correctly
    *         <code>false</code> if components could not be loaded
    */
   boolean setComponents(String[] componentsIDs) {
      if (componentsIDs == null || componentsIDs.length == 0) {
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_COMPONENTS_SET + wareID + ": " + CommandEconomy.ERROR_COMPONENT_IDS);
         return false;
      }

      if (yield <= 0) {
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_COMPONENTS_SET + wareID + ": " + CommandEconomy.ERROR_COMPONENT_YIELD);
         return false;
      }

      // prepare a container for the new list of components
      components = new Ware[componentsIDs.length];
      this.componentsIDs = componentsIDs;
      // Java's garbage collection will delete the old lists

      // reset base price
      priceBase = 0.0f;

      // calculate base price
      // search for each component among the marketplace's loaded wares and grab component prices
      Ware component = null; // holds a component for the current ware
      for (int i = 0; i < componentsIDs.length; i++) {
         // if the component is already loaded into the program, use its price
         component = Marketplace.translateAndGrab(componentsIDs[i]); // grab component using its ware ID or alias

         // if the component is found, use it
         if (component != null) {
            components[i] = component;
            priceBase += component.getBasePrice();
         }

         // if the component is missing, report it
         else {
            priceBase  = Float.NaN; // flag for invalid ware
            break;
         }
      }

      // if there were no problems loading the current ware,
      // adjust its base price
      if (!Float.isNaN(priceBase) && yield > 1) {
         // adjust price by recipe's yield
         priceBase /= (float) yield;

         // Don't truncate or adjust the base price using a multiplier here.
         // Use multipliers in getBasePrice() to ensure prices are accurate
         // when wares are reloading after multipliers are changed.

         Marketplace.markAsChanged(this);
         return true;
      } else {
         return false;
      }
   }

   /**
    * Reloads all components using the ware's current list of ware IDs used to create itself.
    * All components are reloaded in case some were reloaded or otherwise changed.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @return empty if the ware loaded correctly or the first missing component's ID
    */
   public String reloadComponents() {
      if (componentsIDs == null || componentsIDs.length == 0)
         return CommandEconomy.ERROR_COMPONENT_IDS;

      if (yield <= 0)
         return CommandEconomy.ERROR_COMPONENT_YIELD;

      // initialize components array if it hasn't been already
      if (components == null)
         components = new Ware[componentsIDs.length];

      // reset base price
      priceBase = 0.0f;

      // calculate base price
      // search for each component among the marketplace's loaded wares and grab component prices
      Ware component = null; // holds a component for the current ware
      for (int i = 0; i < componentsIDs.length; i++) {
         // if the component is already loaded into the program, use its price
         component = Marketplace.translateAndGrab(componentsIDs[i]); // grab component using its ware ID or alias

         // if the component is found, use it
         if (component != null) {
            components[i] = component;
            priceBase += component.getBasePrice();
         }

         // if the component is missing, report it
         else {
            priceBase  = Float.NaN; // flag for invalid ware
            return componentsIDs[i];
         }
      }

      // if the base price isn't zero, adjust price by recipe's yield
      if (priceBase != 0.0f && yield > 1) 
         priceBase /= (float) yield;

      // Don't truncate or adjust the base price using a multiplier here.
      // Use multipliers in getBasePrice() so wares do not have to be reloaded
      // when multipliers are changed.
      // Additionally, not adjusting the base price here
      // allows for better code reuse.

      return "";
   }

   /**
    * Writes the ware's current properties in JSON format.
    * @return ware's current state in JSON formatting
    */
   public abstract String toJSON();

   /**
    * Constructs a ware based on the given JSON formatting.
    * @param json string with JSON formatting and ware properties
    * @return a new ware or null
    */
   @SuppressWarnings("deprecation") // unfortunately, Minecraft 1.12.2's gson requires a JSONParser object
   public static Ware fromJSON(String json) {
      try {
         // grab the ware's type to know how to process the saved information
         // unfortunately, Minecraft 1.12.2's gson requires a JSONParser object
         jsonObject      = jsonParser.parse(json).getAsJsonObject();
         String wareType = jsonObject.get("type").getAsString();

         // remove type field to avoid interfering with constructing the ware
         jsonObject.remove("type");

         // call on the right constructor
         switch (wareType) {
            case "material":
               return gson.fromJson(jsonObject, WareMaterial.class);
            case "untradeable":
               return gson.fromJson(jsonObject, WareUntradeable.class);
            case "processed":
               return gson.fromJson(jsonObject, WareProcessed.class);
            case "crafted":
               return gson.fromJson(jsonObject, WareCrafted.class);
            case "linked":
               return gson.fromJson(jsonObject, WareLinked.class);
            default:
               return null;
         }
      } catch (Exception e) {
         return null;
      }
   }

   /**
    * Thoroughly checks the ware for errors, corrects errors where possible,
    * then returns an error message for uncorrected errors or an empty string.
    * <p>
    * Complexity: O(1)
    * @return error message or an empty string
    */
   public String validate() {
      String errorMessage = "";

      // every ware should have an ID
      if (wareID == null || wareID.isEmpty())
         errorMessage = CommandEconomy.ERROR_WARE_ID_MISSING;

      // aliases shouldn't have colons nor be empty strings
      if (alias != null && !alias.isEmpty()) {
         // if the alias contains a colon,
         // remove everything before the colon
         if (alias.contains(":"))
            alias = alias.substring(alias.indexOf(':') + 1, alias.length());
      } else {
         alias = null;
      }

      // price must be numerical
      if (Float.isNaN(priceBase)) {
         if (!errorMessage.isEmpty())
            errorMessage += ", ";

         errorMessage += CommandEconomy.ERROR_WARE_NO_PRICE;
      }

      // level must be between 0 and 5 inclusive
      // don't allow negative levels
      if (level < 0)
         level = 0;
      // don't allow levels past the highest level
      else if (level > 5)
         level = 5;

      return errorMessage;
   }

   /**
    * Checks the ware for errors, corrects errors where possible,
    * then returns an error message for uncorrected errors or an empty string.
    * Will not recalculate price if it is unset.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @return error message or an empty string
    */
   String validateHasComponents() {
      String errorMessage = "";

      // every ware should have an ID
      if (wareID == null || wareID.isEmpty())
         errorMessage = CommandEconomy.ERROR_WARE_ID_MISSING;

      // aliases shouldn't have colons nor be empty strings
      if (alias != null && !alias.isEmpty()) {
         // if the alias contains a colon,
         // remove everything before the colon
         if (alias.contains(":"))
            alias = alias.substring(alias.indexOf(':') + 1, alias.length());
      } else {
         alias = null;
      }

      // level must be between 0 and 5 inclusive
      // don't allow negative levels
      if (level < 0)
         level = 0;
      // don't allow levels past the highest level
      else if (level > 5)
         level = 5;

      // yield must be non-zero and positive
      if (yield <= 0)
         yield = 1;

      // ware types relying on components should have components
      if (componentsIDs == null || componentsIDs.length == 0) {
         priceBase = Float.NaN;

         if (!errorMessage.isEmpty())
            errorMessage += ", ";

         errorMessage += CommandEconomy.ERROR_COMPONENTS_MISSING;
      }

      // components' IDs should not be blank
      else {
         for (String componentID : componentsIDs) {
            if (componentID == null || componentID.isEmpty()) {
               priceBase = Float.NaN;

               if (!errorMessage.isEmpty())
                  errorMessage += ", ";

               errorMessage += CommandEconomy.ERROR_COMPONENT_ID_BLANK;
               break;
            }
         }
      }

      return errorMessage;
   }

   /**
    * Returns a multiplier representing how much a
    * ware's components are affecting its unit price.
    * <p>
    * Complexity:<br>
    * n = number of this ware's components<br>
    * Worst-Case: O(n^2)<br>
    * Average-Case: O(n)
    * @return multiplier for adjusting ware's unit price
    */
   public float getLinkedPriceMultiplier() {
      // if linked prices is not enabled or
      // the ware has no components, do nothing
      if (!Config.shouldComponentsCurrentPricesAffectWholesPrice || componentsIDs == null || this instanceof WareLinked)
         return 1.0f;

      // set up or remake hashmap if necessary
      if (recentLinkedPriceResults == null)
            recentLinkedPriceResults = new HashMap<Ware, Float>();

      // check if a recently calculated multiplier is available
      else {
         // try to grab the multiplier
         Float multiplierSaved = recentLinkedPriceResults.get(this);

         // if the multiplier exists, check whether components
         // have changed since its calculation
         if (multiplierSaved != null) {
            if (components != null) {
               boolean useSavedValue = true; // flag for whether to use the saved multiplier

               // check whether cascading effects need to be calculated
               // by checking for components' saved multipliers
               for (Ware component : components) {
                  // if multiplier doesn't exist, there must be recalculations
                  if (recentLinkedPriceResults.get(component) == null) {
                     useSavedValue = false;
                     break;
                  }
               }

               // if possible, use the saved multiplier
               if (useSavedValue)
                  return multiplierSaved.floatValue();
            }

            // no components means the multiplier must be up-to-date
            else
               return multiplierSaved.floatValue();
         }
      }

      // initialize variables
      float priceComponentsCurrent     = 0.0f; // sum of components' current prices
      float priceComponentsEquilibrium = 0.0f; // sum of components' prices without considering supply and demand

      // get components' prices
      for (Ware component : components) {
         // tally up base prices
         priceComponentsEquilibrium += Marketplace.getPrice(null, component, 1, Marketplace.PriceType.EQUILIBRIUM_BUY);

         // get current prices
         priceComponentsCurrent += Marketplace.getPrice(null, component, 1, Marketplace.PriceType.CURRENT_BUY);
      }

      // solve for linked price effect
      float multiplier = (Config.linkedPricesPercent * priceComponentsCurrent / priceComponentsEquilibrium) + (1 - Config.linkedPricesPercent);

      // add multiplier to recently calculated multipliers
      if (recentLinkedPriceResults != null)
         recentLinkedPriceResults.put(this, Float.valueOf(multiplier));

      return multiplier;
   }


   /**
    * Removes any stored, recent calculations for this ware,
    * forcing them to be recalculated based on current values.
    * <p>
    * Complexity: O(1)
    */
   public void removeSavedCalculations() {
      if (recentLinkedPriceResults != null)
         recentLinkedPriceResults.remove(this);
   }

   /**
    * Calculates how many units of the ware could be manufactured
    * and how much manufacturing would cost.
    * <p>
    * Complexity: O(2n), where n is the number of the ware's components
    * <p>
    * @param quantity how much of the ware should be manufactured
    * @return [0]: Total cost (float, untruncated) and [1]: Quantity (int) or null
    */
   public float[] getManufacturingPrice(int quantity) {
      // if the ware cannot be manufactured or
      // nothing should be manufactured, do nothing
      if (components == null || quantity <= 0 ||
          (!(this instanceof WareCrafted) && !(this instanceof WareProcessed)))
         return null;

      // store component references and
      // how much of each component is needed
      HashMap<Ware, Integer> componentsAmounts = new HashMap<Ware, Integer>(9, 1.0f);
      int   quantityManufacturable; // how many recipe iterations a component's stock could facilitate
      int   quantityToManufacture;  // how much of the ware to create
      float totalCost;              // total expenses for manufacturing quantity ordered

      // find how much of each component is required for manufacturing
      for (Ware componentRef : components) {
         // if the component has not been parsed yet,
         // add it to the component arrays
         if (!componentsAmounts.containsKey(componentRef))
            componentsAmounts.put(componentRef, 1);

         // if the component has been parsed,
         // increment the manufacturing recipe's amount required for it
         else
            componentsAmounts.put(componentRef, componentsAmounts.get(componentRef) + 1);
      }

      // find how much of the requested ware can be manufactured
      quantityToManufacture = Integer.MAX_VALUE; // don't artificially limit manufacturing
      for (Map.Entry<Ware, Integer> entry : componentsAmounts.entrySet()) {
         // find how much could be manufactured
         quantityManufacturable = entry.getKey().getQuantity() / entry.getValue();

         // if the current component is constraining,
         // use the constrained quantity
         if (quantityToManufacture > quantityManufacturable)
            quantityToManufacture = quantityManufacturable;
      }

      // factor in recipe yield
      quantityToManufacture *= yield;

      // if more can be manufactured than is ordered,
      // then only manufacture as much as is ordered
      if (quantityToManufacture > quantity)
         quantityToManufacture = quantity;

      // if nothing can be manufactured, stop
      if (quantityToManufacture == 0)
         totalCost = 0.0f;
      else
         totalCost = Marketplace.getPrice(null, this, quantityToManufacture, Marketplace.PriceType.CEILING_BUY) * Config.buyingOutOfStockWaresPriceMult;

      // return total cost and quantity purchased
      return new float[] {totalCost, (float) quantityToManufacture};
   }

   /**
    * Manufactures ware following a given maximum budget;
    * returns the quantity manufactured and how much manufacturing cost.
    * <p>
    * Complexity: O(3n), where n is the number of components
    * @param playerID       who to send inventory space error messages to
    * @param quantity       how much of the ware should be purchased
    * @param moneyAvailable the maximum amount of funds spendable
    * @param inventorySpaceAvailable the maximum amount of items which should be manufactured
    * @return [0]: Total cost (float, untruncated) and [1]: Quantity (int) or null
    */
   protected float[] manufacture(UUID playerID, int quantity,
                             float moneyAvailable, int inventorySpaceAvailable) {
      // if the ware cannot be manufactured or
      // nothing should be manufactured, do nothing
      if (components == null || quantity <= 0 ||
          (!(this instanceof WareCrafted) && !(this instanceof WareProcessed)))
         return null;

      // lacking inventory space should have its own error message
      if (inventorySpaceAvailable <= 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.MSG_INVENTORY_NO_SPACE);
         return new float[] {0.0f, 0.0f};
      }

      // store component references and
      // how much of each component is needed
      HashMap<Ware, Integer> componentsAmounts = new HashMap<Ware, Integer>(9, 1.0f);
      final float UNIT_PRICE  = Marketplace.getPrice(null, this, 1, Marketplace.PriceType.CEILING_BUY) * Config.buyingOutOfStockWaresPriceMult;
      float totalCost         = 0.0f; // total expenses for manufacturing quantity ordered
      int   quantityToManufacture;    // how much of the ware to create
      int   remainder;                // unpurchased leftovers from manufacturing the ware
      int   recipeIterations;         // how many batches of the ware was created

      // find how much of each component is required for manufacturing
      for (Ware componentRef : components) {
         // if the component has not been parsed yet,
         // add it to the component arrays
         if (!componentsAmounts.containsKey(componentRef))
            componentsAmounts.put(componentRef, 1);

         // if the component has been parsed,
         // increment the manufacturing recipe's amount required for it
         else
            componentsAmounts.put(componentRef, componentsAmounts.get(componentRef) + 1);
      }

      // find how many batches of the requested ware can be manufactured
      recipeIterations = Integer.MAX_VALUE; // don't artificially limit manufacturing
      for (Map.Entry<Ware, Integer> entry : componentsAmounts.entrySet()) {
         // find how many times the recipe could be applied
         quantityToManufacture = entry.getKey().getQuantity() / entry.getValue();

         // if the current component is constraining,
         // use the constrained quantity
         if (recipeIterations > quantityToManufacture)
            recipeIterations = quantityToManufacture;
      }

      // factor in recipe yield
      quantityToManufacture = recipeIterations * yield;

      // if nothing can be manufactured, stop
      if (quantityToManufacture == 0)
         return null;

      // only manufacture however much the inventory can hold
      if (quantity > inventorySpaceAvailable)
         quantity = inventorySpaceAvailable;

      // if more can be manufactured than is ordered,
      // then only manufacture as much as is ordered
      if (quantityToManufacture > quantity)
         quantityToManufacture = quantity;

      // find how much should be bought within budget
      totalCost = UNIT_PRICE * quantityToManufacture;
      if (totalCost > moneyAvailable) {
         quantityToManufacture = (int) (moneyAvailable / UNIT_PRICE);
         totalCost             = UNIT_PRICE * quantityToManufacture;
      }

      // find unpurchased leftovers from recipe iterations
      remainder = quantityToManufacture % yield;

      // undo recipe yield to get iterations
      // before entering loop or critical region
      recipeIterations = quantityToManufacture / yield;

      // if remainder is nonzero, add one to recipe iterations
      // to account for fractional purchase
      if (remainder > 0)
         recipeIterations++;

      // purchase each component
      // prevent other threads from adjusting wares' properties
      Marketplace.acquireMutex();

      // subtract from each component's quantity available for sale
      for (Map.Entry<Ware, Integer> entry : componentsAmounts.entrySet()) {
         entry.getKey().subtractQuantity(recipeIterations * entry.getValue());
      }

      // add unpurchased remainder to the marketplace
      this.addQuantity(remainder);

      // allow other threads to adjust wares' properties
      Marketplace.releaseMutex();

      // return total cost and quantity purchased
      return new float[] {totalCost, (float) quantityToManufacture};
   }
 }