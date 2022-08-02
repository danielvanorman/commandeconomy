package commandeconomy;

/**
 * A ware representing a component unable to exist by itself.
 * <p>
 * Ex: A bucket of milk may exist alone, but (in Minecraft) a
 * bucketful of milk cannot exist outside of a container.
 * Thus, the bucketful of milk could be an untradeable ware
 * useful for implementing the bucket of milk as a ware.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-19
 */
public class WareUntradeable extends Ware
{
   /**
    * Untradeable Ware Constructor: Creates a ware
    * representing a component which is unable to exist by itself.
    *
    * @param wareID    ware's internal name
    * @param alias     ware's unique alternate name
    * @param priceBase ware's unmodified price
    */
   public WareUntradeable (String wareID, String alias, float priceBase) {
      components        = null;
      componentsIDs     = null;
      this.wareID       = wareID;
      setAlias(alias);
      this.priceBase = priceBase;
      quantity          = Integer.MAX_VALUE;
      yield             = 0;
      level             = 0;
   }

   /**
    * Untradeable Ware Constructor: Creates a ware
    * representing a component which is unable to exist by itself.
    *
    * @param components list of ware IDs used to create this
    * @param wareID     ware's internal name
    * @param alias      ware's unique alternate name
    * @param yield      amount of this ware created from using its recipe once
    */
   public WareUntradeable (String[] components, String wareID, String alias, int yield) {
      this.wareID   = wareID;
      setAlias(alias);
      priceBase     = Float.NaN;
      quantity      = Integer.MAX_VALUE;
      if (yield <= 0)
         yield      = 1;
      else
         this.yield = yield;
      level         = 0;

      // set base price and parse components
      setComponents(components);
   }

   /** Untradeable Ware Constructor: No-arguments for GSON. */
   public WareUntradeable () {
      // default values
      quantity = Integer.MAX_VALUE;
   }

   /**
    * Changes the list of ware IDs used to create this ware 
    * and this ware's base price.
    * <p>
    * Complexity: O(n)
    * @param componentsIDs list of ware IDs used to create this ware
    * @return <code>true</code> if components were loaded correctly
    *         <code>false</code> if components could not be loaded
    */
   @Override
   boolean setComponents(String[] componentsIDs) {
      // run default code for setting components
      boolean loadedCorrectly = super.setComponents(componentsIDs);

      // finalize base price
      if (!Float.isNaN(priceBase)) {
         // truncate the price to avoid rounding and multiplication errors
         priceBase = CommandEconomy.truncatePrice(priceBase);
      }

      return loadedCorrectly;
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
      quantity = Integer.MAX_VALUE;
      level    = 0;

      if (componentsIDs == null && yield == 0)
         return super.validate();
      else
         return validateHasComponents();
   }

   /**
    * Writes the ware's current properties in JSON format.
    * @return ware's current state in JSON formatting
    */
   public String toJSON() {
      // avoid an illegal state exception
      boolean invalidPrice = false;
      if (Float.isNaN(priceBase)) {
         invalidPrice = true;
         priceBase    = 0.0f;
      }

      jsonObject = gson.toJsonTree(this).getAsJsonObject();
      jsonObject.addProperty("type", "untradeable");

      // don't bother recording unused variables
      jsonObject.remove("quantity");
      jsonObject.remove("level");
      if (componentsIDs == null)
         jsonObject.remove("yield");
      // don't record current price of components
      else
         jsonObject.remove("priceBase");

      // don't record price is there isn't one
      if (invalidPrice)
         jsonObject.remove("priceBase");

      return jsonObject.toString();
   }
}