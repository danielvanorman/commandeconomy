package commandeconomy;

import com.google.gson.Gson;          // for saving and loading
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
      Marketplace.saveThisWare(wareID);
   }

   /**
    * Increases how much of the ware is available on the market by the given amount.
    * <p>
    * Complexity: O(1)
    * @param adjustment increase to quantity available within the market
    */
   public void addQuantity(int adjustment) {
      quantity += adjustment;
      Marketplace.saveThisWare(wareID);
   }

   /**
    * Decreases how much of the ware is available on the market by the given amount.
    * <p>
    * Complexity: O(1)
    * @param adjustment decrease to quantity available within the market
    */
   public void subtractQuantity(int adjustment) {
      quantity -= adjustment;
      Marketplace.saveThisWare(wareID);
   }

   /**
    * Returns the price of the ware when supply and demand are balanced.
    * @return equilibrium price of the ware
    */
   public float getBasePrice() {
      return priceBase;
   }

   /**
    * Returns the preferred human-readable alternate name for the ware.
    * @return preferred alternate name for the ware
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
            this.alias = alias.substring(alias.indexOf(":") + 1, alias.length());
         } else {
            this.alias = alias;
         }
      } else {
         this.alias = null;
      }
      Marketplace.saveThisWare(wareID);
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

      Marketplace.saveThisWare(wareID);
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

      Marketplace.saveThisWare(wareID);
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

         Marketplace.saveThisWare(wareID);
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
            alias = alias.substring(alias.indexOf(":") + 1, alias.length());
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
    * Complexity:<br>
    * n = number of this ware's components<br>
    * Worst-Case: O(n^2)<br>
    * Average-Case: O(n)
    * @return multiplier for adjusting ware's unit price
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
            alias = alias.substring(alias.indexOf(":") + 1, alias.length());
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
    * Complexity: O(n), where n is number of this ware's components<br>
    * @return multiplier for adjusting ware's unit price
    */
   public float getLinkedPriceMultiplier() {
      // if linked prices is not enabled or
      // the ware has no components, do nothing
      if (!Config.shouldComponentsCurrentPricesAffectWholesPrice || componentsIDs == null || this instanceof WareLinked)
         return 1.0f;

      // get components' prices
         // tally up base prices
         // get current prices

      // solve for linked price effect
      // linked price multiplier: (Config.linkedPricesPercent * sum of component wares' current prices / sum of component wares' base prices) + (1 - Config.linkedPricesPercent)
      // (configurable effect * component price ratio) + (remaining percentage from whole's price)

      return 0.0f;
   }
 };
