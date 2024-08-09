package commandeconomy;
/**
 * A ware representing a raw material.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-02-04
 */
public class WareMaterial extends Ware
{
   /**
    * Material Ware Constructor: Creates a ware representing a raw material.
    *
    * @param wareID    ware's internal name
    * @param alias     ware's unique alternate name
    * @param priceBase ware's unmodified price
    * @param quantity  stock available within the market
    * @param level     ware's hierarchy level
    */
   public WareMaterial (String wareID, String alias, float priceBase, int quantity,
      byte level) {
      this.components    = null;
      this.componentsIDs = null;
      this.wareID = wareID;
      setAlias(alias);
      this.priceBase = priceBase;
      this.quantity      = quantity;
      this.yield         = 1;
      setLevel(level);
   }

   /** Material Ware Constructor: No-arguments for GSON. */
   public WareMaterial () {
      // default values
      yield    = 1;
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
      jsonObject.addProperty("type", "material");
      jsonObject.remove("yield"); // don't bother recording an unused variable

      // don't record price is there isn't one
      if (invalidPrice)
         jsonObject.remove("priceBase");

      return jsonObject.toString();
   }
 }