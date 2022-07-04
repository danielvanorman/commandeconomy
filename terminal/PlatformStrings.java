package commandeconomy;

/**
 * This class contains string constants pertaining to a specific platform, such as color codes.
 * This eases switching between platforms and dividing code.
 * These string constants are not included in platform-specific command interfaces,
 * which are set at runtime, to ensure compiler optimizations may occur
 * without sacrificing modularity and maintainability when switching between platforms.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2022-07-04
 */
public class PlatformStrings {
   // command names
   public final static String CMD_OP                = "op";
   public final static String CMD_DEOP              = "deop";
   public final static String CMD_INVENTORY         = "inventory";
   public final static String CMD_GIVE              = "give";
   public final static String CMD_TAKE              = "take";
   public final static String CMD_CHANGE_NAME       = "changeName";
   public final static String CMD_CHANGE_NAME_LOWER = "changename";

   // command usages
   public final static String CMD_USAGE_OP          = "/" + CMD_OP + " <player_name>";
   public final static String CMD_USAGE_DEOP        = "/" + CMD_DEOP + " <player_name>";
   public final static String CMD_USAGE_INVENTORY   = "/" + CMD_INVENTORY + " [inventory_direction]";
   public final static String CMD_USAGE_GIVE        = "/" + CMD_GIVE + " <ware_id> [quantity] [inventory_direction]";
   public final static String CMD_USAGE_TAKE        = "/" + CMD_TAKE + " <ware_id> [quantity] [inventory_direction]";
   public final static String CMD_USAGE_CHANGE_NAME = "/" + CMD_CHANGE_NAME + " <player_name>";

   // command descriptions
   public final static String CMD_DESC_OP           = " - grants admin permissions\n";
   public final static String CMD_DESC_DEOP         = " - revokes admin permissions\n";
   public final static String CMD_DESC_INVENTORY    = " - displays inventory contents\n";
   public final static String CMD_DESC_GIVE         = " - puts one or a specific amount of a given id into the inventory\n";
   public final static String CMD_DESC_TAKE         = " - removes all or a specific amount of a given id from the inventory\n";
   public final static String CMD_DESC_CHANGE_NAME  = " - sets the player's name and ID\n";

   // command errors
   public final static String ERROR_HANDS               = "Do consoles even have hands?";
   public final static String ERROR_CHANGE_NAME_MISSING = "error - must provide name or ID: ";

   // random events
   // +++ --> Dark Green
   //  ++ --> Green
   //   + --> Light Green or Italicized Green
   // --- --> Dark Red
   //  -- --> Red
   //   - --> Light Red or Italicized 
   /** color code for positive, large changes in ware's quantities */
   public final static String PREFIX_POS_LARGE  = "\033[1m\033[32m+++";
   /** color code for positive, medium changes in ware's quantities */
   public final static String PREFIX_POS_MEDIUM = "\033[32m++";
   /** color code for positive, small changes in ware's quantities */
   public final static String PREFIX_POS_SMALL  = "\033[32;1m+";
   /** color code for negative, large changes in ware's quantities */
   public final static String PREFIX_NEG_LARGE  = "\033[1m\033[31m---";
   /** color code for negative, medium changes in ware's quantities */
   public final static String PREFIX_NEG_MEDIUM = "\033[31m--";
   /** color code for negative, small changes in ware's quantities */
   public final static String PREFIX_NEG_SMALL  = "\033[31;1m-";
   /** code for resetting color codes */
   public final static String POSTFIX           = "\033[0m\n";
}