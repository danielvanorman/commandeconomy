package commandeconomy;

/**
 * A ware representing a product directly attached or dependent on other wares.
 * <p>
 * Ex: In Minecraft, a bale of wheat and a handful of wheat are considered
 * different items, but, for all practical purposes, represent the same ware.
 * To handle this within the marketplace, bales of wheat could be
 * linked to handfuls of wheat (or vice versa). This would mean 
 * bales of wheat's quantity, price, and other properties
 * directly derive from the properties of handfuls of wheat.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-05-12
 */
public class WareLinked extends Ware
{
   /** amounts of wares used to create this ware */
   int[] componentsAmounts;
   /** amount of sold stock which could not be converted evenly into components */
   int remainder;

   /**
    * Linked Constructor: Creates a ware representing
    * a product directly attached or dependent on other wares.
    *
    * @param components list of ware IDs used to create this
    * @param componentsAmounts how much of each component to use
    * @param wareID     ware's unique name
    * @param alias      ware's unique alternate name
    * @param yield      amount of this ware created from using its recipe once
    */
   public WareLinked (String[] components, int[] componentsAmounts, String wareID, String alias, int yield) {
      this.wareID   = wareID;
      setAlias(alias);
      priceBase     = Float.NaN;
      quantity      = -2;
      if (yield <= 0)
         this.yield = 1;
      else
         this.yield = yield;
      level         = 0;
      remainder     = 0;

      // parse components
      setComponents(components, componentsAmounts);
   }

   /** Linked Ware Constructor: No-arguments for GSON. */
   public WareLinked () {
      // default values
      quantity  = -2;
      yield     = 1;
      level     = 0;
      remainder = 0;
   }

   /**
    * Changes the list of ware IDs used to create this ware 
    * and this ware's base price.
    * Assumes base price is zero.
    * <p>
    * Complexity: O(n)
    * @param componentsIDs     list of ware IDs used to create this ware
    * @param componentsAmounts list of amounts for each ware used to create this ware
    * @return <code>true</code> if components were loaded correctly
    *         <code>false</code> if components could not be loaded
    */
   boolean setComponents(String[] componentsIDs, int[] componentsAmounts) {
      if (componentsIDs == null || componentsIDs.length == 0) {
         Config.userInterface.printToConsole(CommandEconomy.ERROR_COMPONENTS_SET + wareID + ": " + CommandEconomy.ERROR_COMPONENT_IDS);
         return false;
      }

      if (componentsAmounts == null || componentsAmounts.length == 0) {
         Config.userInterface.printToConsole(CommandEconomy.ERROR_COMPONENTS_SET + wareID + ": " + CommandEconomy.ERROR_COMPONENTS_AMOUNTS);
         return false;
      }

      if (componentsIDs.length != componentsAmounts.length) {
         Config.userInterface.printToConsole(CommandEconomy.ERROR_COMPONENTS_SET + wareID + ": " + CommandEconomy.ERROR_COMPONENTS_UNEQUAL_LEN);
         return false;
      }

      if (yield <= 0) {
         Config.userInterface.printToConsole(CommandEconomy.ERROR_COMPONENTS_SET + wareID + ": " + CommandEconomy.ERROR_COMPONENT_YIELD);
         return false;
      }

      // prepare a container for the new list of components
      components = new Ware[componentsIDs.length];
      this.componentsIDs     = componentsIDs;
      this.componentsAmounts = componentsAmounts;
      // Java's garbage collection will delete the old lists

      // calculate base price
      priceBase = 0.0f; // reset base price to prepare for calculation
      // search for each component among the marketplace's loaded wares and grab component prices
      Ware component = null; // holds a component for the current ware
      for (int i = 0; i < componentsIDs.length; i++) {
         // if the component is already loaded into the program, use its price
         component = Marketplace.translateAndGrab(componentsIDs[i]); // grab component using its ware ID or alias

         // if the component is found, use it
         if (component != null) {
            components[i] = component;

            // add component's base price to the linked ware's base price
            priceBase += components[i].getBasePrice() * componentsAmounts[i] / yield;

            // in case some features need it,
            // set level to match highest level component
            if (component.getLevel() > this.level)
               this.level = component.getLevel();
         }

         // if the component is missing, report it
         else {
            priceBase = Float.NaN; // flag for invalid ware
            return false;
         }
      }

      // truncate the price to avoid rounding and multiplication errors
      priceBase = CommandEconomy.truncatePrice(priceBase);

      // if there were no problems loading the current ware
      return true;
   }

   /**
    * Reloads all components using the ware's current list of ware IDs used to create itself.
    * All components are reloaded in case some were reloaded or otherwise changed.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @return empty if the ware loaded correctly or the first missing component's ID
    */
   @Override
   public String reloadComponents() {
      if (componentsIDs == null || componentsIDs.length == 0)
         return CommandEconomy.ERROR_COMPONENT_IDS;

      if (componentsAmounts == null || componentsAmounts.length == 0)
         return CommandEconomy.ERROR_COMPONENTS_AMOUNTS;

      if (componentsIDs.length != componentsAmounts.length)
         return CommandEconomy.ERROR_COMPONENTS_UNEQUAL_LEN;

      if (yield <= 0)
         return CommandEconomy.ERROR_COMPONENT_YIELD;

      // initialize components array if it hasn't been already
      if (components == null)
         components = new Ware[componentsIDs.length];

      // calculate base price
      priceBase = 0.0f; // reset base price to prepare for calculation
      // search for each component among the marketplace's loaded wares and grab component prices
      Ware component = null; // holds a component for the current ware
      for (int i = 0; i < componentsIDs.length; i++) {
         // if the component is already loaded into the program, use its price
         component = Marketplace.translateAndGrab(componentsIDs[i]); // grab component using its ware ID or alias

         // if the component is found, use it
         if (component != null) {
            components[i] = component;

            // add component's base price to the linked ware's base price
            priceBase += components[i].getBasePrice() * componentsAmounts[i] / yield;

            // in case some features need it,
            // set level to match highest level component
            if (component.getLevel() > this.level)
               this.level = component.getLevel();
         }

         // if the component is missing, report it
         else {
            priceBase  = Float.NaN; // flag for invalid ware
            return componentsIDs[i];
         }
      }

      // truncate the price to avoid rounding and multiplication errors
      priceBase = CommandEconomy.truncatePrice(priceBase);

      // if there were no problems reloading the current ware
      return "";
   }

   /**
    * Returns how much of the ware is available on the market.
    * @return amount of ware for sale
    */
   @Override
   public int getQuantity() {
      // if something's wrong with the components, do nothing
      if (Float.isNaN(priceBase))
         return 0;

      quantity = 2147483647;
      int possibleQuantity = 0;

      // find which component is the constraining component
      for (int i = 0; i < componentsAmounts.length; i++) {
         possibleQuantity = components[i].getQuantity() / componentsAmounts[i];

         // if the current component is constraining,
         // use the constrained quantity
         if (quantity > possibleQuantity)
            quantity = possibleQuantity;
      }

      quantity *= yield;
      quantity += remainder;
      return quantity;
   }

   /**
    * Sets how much of the ware is available on the market.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @param quantity new quantity available within the market
    */
   @Override
   public void setQuantity(int quantity) {
      // if something's wrong with the components, do nothing
      if (Float.isNaN(priceBase))
         return;

      remainder     = quantity % yield;
      this.quantity = quantity - remainder;

      // set quantities of all components
      if (quantity == 0) { // avoid division by zero
         for (int i = 0; i < componentsAmounts.length; i++) {
            components[i].setQuantity(0);
            Marketplace.markAsChanged(components[i]);
         }
         Marketplace.markAsChanged(this);
         return;
      } else {
         for (int i = 0; i < componentsAmounts.length; i++) {
            components[i].setQuantity(this.quantity * componentsAmounts[i] / yield);
            Marketplace.markAsChanged(components[i]);
         }
      }

      Marketplace.markAsChanged(this);
   }

   /**
    * Increases how much of the ware is available on the market by the given amount.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @param adjustment increase to quantity available within the market
    */
   @Override
   public void addQuantity(int adjustment) {
      // if something's wrong with the components, do nothing
      if (Float.isNaN(priceBase))
         return;

      remainder += adjustment;
      quantity   = remainder / yield;
      remainder -= quantity * yield;

      // check if any work should be done
      if (quantity == 0)
         return;

      // augment quantities of all components
      for (int i = 0; i < componentsAmounts.length; i++) {
         components[i].addQuantity(quantity * componentsAmounts[i]);
         Marketplace.markAsChanged(components[i]);
      }
      Marketplace.markAsChanged(this);
   }

   /**
    * Decreases how much of the ware is available on the market by the given amount.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @param adjustment decrease to quantity available within the market
    */
   @Override
   public void subtractQuantity(int adjustment) {
      // if something's wrong with the components, do nothing
      if (Float.isNaN(priceBase))
         return;

      remainder = adjustment - remainder; // remainder to subtract = amount to subtract - positive remainder of last transaction
      quantity   = remainder / yield; // quantity to subtract from components = amount to subtract / yield from using components
      remainder -= quantity * yield; // record partial components remaining for next transaction

      // if remainder is greater than zero,
      // subtract one more from components
      // to account for partial components remaining
      if (remainder > 0)
         quantity++;

      // check if any work should be done
      if (quantity == 0)
         return;

      // subtract quantities of all components
      for (int i = 0; i < componentsAmounts.length; i++) {
         components[i].subtractQuantity(quantity * componentsAmounts[i]);
         Marketplace.markAsChanged(components[i]);
      }
      Marketplace.markAsChanged(this);
   }

   /**
    * Returns the current price of the ware, factoring in supply and demand.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @param quantity   how much to buy or sell
    * @param isPurchase <code>true</code> if the price should reflect buying the ware
    *                   <code>false</code> if the price should reflect selling the ware
    * @return current price of the ware
    */
   public float getCurrentPrice(int quantity, boolean isPurchase) {
      // if something's wrong with the components, set the invalid flag
      if (Float.isNaN(priceBase))
         return Float.NaN;

      // if quantity requested is greater than quantity available, use quantity available
      if (quantity > getQuantity())
         quantity = this.quantity;

      // prepare to get components' current prices
      float price = 0.0f;

      // loop through components to get their current prices
      if (isPurchase) {
         for (int i = 0; i < componentsAmounts.length; i++) {
            price += Marketplace.getPrice(null, components[i], quantity * componentsAmounts[i], Marketplace.PriceType.CURRENT_BUY)  / yield;
         }
      } else {
         for (int i = 0; i < componentsAmounts.length; i++) {
            price += Marketplace.getPrice(null, components[i], quantity * componentsAmounts[i], Marketplace.PriceType.CURRENT_SELL)  / yield;
         }
      }

      // avoid division by zero
      if (price == 0.0f)
         return 0.0f;

      // truncate the price to avoid rounding and multiplication errors
      return CommandEconomy.truncatePrice(price);
   }

   /**
    * Checks the ware for errors, corrects errors where possible,
    * then returns an error message for uncorrected errors or an empty string.
    * Will not recalculate price if it is unset.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @return error message or an empty string
    */
   @Override
   public String validate() {
      String errorMessage = validateHasComponents();

      if (componentsAmounts == null || componentsAmounts.length == 0) {
         if (!errorMessage.isEmpty())
            errorMessage += ", ";

         errorMessage += CommandEconomy.ERROR_COMPONENTS_AMOUNTS;
      }

      else if (componentsIDs != null && componentsIDs.length > 0 &&
          componentsIDs.length != componentsAmounts.length) {
         if (!errorMessage.isEmpty())
            errorMessage += ", ";

         errorMessage += CommandEconomy.ERROR_COMPONENTS_UNEQUAL_LEN;
      }

      return errorMessage;
   }

   /**
    * Writes the ware's current properties in JSON format.
    * @return ware's current state in JSON formatting
    */
   public String toJSON() {
      // avoid an illegal state exception
      if (Float.isNaN(priceBase))
         priceBase    = 0.0f;

      jsonObject = gson.toJsonTree(this).getAsJsonObject();
      jsonObject.addProperty("type", "linked");

      // don't record current price of components
      jsonObject.remove("priceBase");
      // don't record current divisible quantity of components
      jsonObject.remove("quantity");
      // don't record level
      jsonObject.remove("level");

      return jsonObject.toString();
   }

   /**
    * Returns the number of quantity available for sale minus 1 recipe iteration this ware would have
    * when any component's quantity available for sale reaches an excessive surplus.
    * <p>
    * Complexity: O(n), whether n is the number of wares used to create this ware
    * @return ware's number of units when a component has excessive surplus
    */
   public int getQuanWhenReachedPriceFloor() {
      // if something's wrong with the components, do nothing
      if (Float.isNaN(priceBase))
         return 0;

      int quanExcessive         = Integer.MAX_VALUE;
      int possibleQuanExcessive = 0;

      // find which component is the constraining component
      for (int i = 0; i < componentsAmounts.length; i++) {
         possibleQuanExcessive = Config.quanExcessive[components[i].getLevel()] / componentsAmounts[i];

         // if the current component is constraining,
         // use the constrained quantity
         if (quanExcessive > possibleQuanExcessive)
            quanExcessive = possibleQuanExcessive;
      }

      quanExcessive *= yield;
      return quanExcessive - yield;
   }
}