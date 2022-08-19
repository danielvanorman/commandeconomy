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
 * @since   2022-06-20
 */
public final class PlatformStrings {
   // command names
   public final static String CMD_NOSELL       = "noSell";
   public final static String CMD_NOSELL_LOWER = "nosell";

   // command usages
   public final static String CMD_USAGE_NOSELL = "/" + CMD_NOSELL + " [" + CommandEconomy.TRUE + " | " + CommandEconomy.FALSE + " | " + CommandEconomy.ALL + "]";

   // command descriptions
   public final static String CMD_DESC_NOSELL  = " - marks an item stack to be unsellable\n";

   // command errors
   public final static String ERROR_HANDS            = "There is nothing in your hand.";
   public final static String ERROR_POSITION_MISSING = "error - your position was not found: ";
   public final static String ERROR_PLAYER_MISSING   = "error - command-issuing player not found";
   public final static String ERROR_ITEM_MISSING     = "No item was found";

   // no sell messages
   public final static String MSG_NOSELL_ON_ALL         = "All inventory items will not be sold";
   public final static String MSG_NOSELL_ON_HELD        = "The item stack will not be sold";
   public final static String MSG_NOSELL_ON_HELD_NAMED  = " will not be sold";
   public final static String MSG_NOSELL_OFF_ALL        = "All inventory items may now be sold";
   public final static String MSG_NOSELL_OFF_HELD       = "The item stack may now be sold";
   public final static String MSG_NOSELL_OFF_HELD_NAMED = " may now be sold";

   // miscellaneous errors
   public final static String ERROR_ADDING_ITEM    = "error when adding item to inventory - ";
   public final static String ERROR_REMOVING_ITEM  = "error when removing item from inventory - ";
   public final static String ERROR_CHECKING_ITEM  = "error when checking item in inventory - ";

   public final static String ERROR_ITEM_NOT_FOUND = "could not find item corresponding to wareID: ";
   public final static String ERROR_NAME_NOT_FOUND = "Forge OreDictionary name not found: ";
   public final static String ERROR_META_PARSING   = "could not parse meta for ";

   // miscellaneous
   public final static String MSG_REGISTER_COMMANDS = "Registering serviceable commands....";

   // random events
   // +++ --> Dark Green
   //  ++ --> Green
   //   + --> Light Green or Italicized Green
   // --- --> Dark Red
   //  -- --> Red
   //   - --> Light Red or Italicized 
   /** color code for positive, large changes in ware's quantities */
   public final static String PREFIX_POS_LARGE  = "§2+++";
   /** color code for positive, medium changes in ware's quantities */
   public final static String PREFIX_POS_MEDIUM = "§a++";
   /** color code for positive, small changes in ware's quantities */
   public final static String PREFIX_POS_SMALL  = "§a§o+";
   /** color code for negative, large changes in ware's quantities */
   public final static String PREFIX_NEG_LARGE  = "§4---";
   /** color code for negative, medium changes in ware's quantities */
   public final static String PREFIX_NEG_MEDIUM = "§c--";
   /** color code for negative, small changes in ware's quantities */
   public final static String PREFIX_NEG_SMALL  = "§c§o-";
   /** code for resetting color codes */
   public final static String POSTFIX           = "§r\n";
}