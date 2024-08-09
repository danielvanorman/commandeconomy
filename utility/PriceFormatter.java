package commandeconomy;

import java.text.DecimalFormat;                                   // for formatting prices when displaying

/**
 * Contains formatting functions used throughout the program.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2024-08-09
 */
public final class PriceFormatter {
   /** used to format currency before displaying */
   public static final DecimalFormat PRICE_FORMAT = new DecimalFormat("$###,##0.00");
   /** used to truncate floats to four decimal places */
   public static final long PRICE_PRECISION = 10000;

   /**
    * Truncates a given number to a standardized decimal place.
    * <p>
    * Complexity: O(1)
    * @param price number to be truncated
    * @return input truncated to the standardized decimal place
    */
   public static float truncatePrice(float price) {
      return ((long) ((price) * PRICE_PRECISION)) / ((float) PRICE_PRECISION);
   }
}