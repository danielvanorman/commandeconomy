package commandeconomy;

/**
 * A ware representing a smelted, mechanically-worked,
 * baked, or otherwise processed version of other wares.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-03
 */
public class WareProcessed extends Ware
{
   /**
    * Processed Ware Constructor: Creates a ware
    * representing a ware created from using other wares.
    *
    * @param components list of ware IDs used to create this
    * @param wareID     ware's internal name
    * @param alias      ware's unique alternate name
    * @param quantity   quantity available within the market
    * @param yield      amount of this ware created from using its recipe once
    * @param level      ware's hierarchy level
    */
   public WareProcessed (String[] components, String wareID, String alias, int quantity, int yield, byte level) {
      this.wareID   = wareID;
      setAlias(alias);
      priceBase     = Float.NaN;
      this.quantity = quantity;
      if (yield <= 0)
         this.yield = 1;
      else
         this.yield = yield;
      setLevel(level);

      // set base price and parse components
      setComponents(components);
   }

   /** Processed Ware Constructor: No-arguments for GSON. */
   public WareProcessed () {
      // default values
      yield    = 1;
   }

   /**
    * Returns the price of the ware when supply and demand are balanced;
    * price is adjusted by the processed multiplier.
    * @return equilibrium price of the ware
    */
   @Override
   public float getBasePrice() {
      if (Float.isNaN(priceBase)) {
         return Float.NaN;
      }
      else {
         // adjust price by its corresponding multiplier
         // truncate the price to avoid rounding and multiplication errors
         return CommandEconomy.truncatePrice(priceBase * Config.priceProcessed);
      }
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
      return validateHasComponents();
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
      jsonObject.addProperty("type", "processed");

      // don't record current price of components
      jsonObject.remove("priceBase");

      return jsonObject.toString();
   }
}