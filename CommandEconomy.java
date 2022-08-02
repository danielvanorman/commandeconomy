package commandeconomy;

import java.text.DecimalFormat;                                   // for formatting prices when displaying

/**
 * Implements a global marketplace within the video game Minecraft.
 * Chat (or console) commands are used to access the market.
 * <p>
 * <b>Compilation</b>
 * <p>
 * Java 8.291 or above is required to use the program.
 * <p>
 * To compile and run the program, open a terminal (such as CMD),
 * navigate to (or start in) the .java file's directory,
 * and enter the following:<br>
 * javac commandEconomy\*.java<br>
 * java commandEconomy.InterfaceTerminal
 * <p>
 * To generate external documentation files, open a terminal (such as CMD),
 * navigate to (or start in) the .java file's directory,
 * and enter the following:<br>
 * javadoc -private -d documentation commandEconomy\*.java
 * <p>
 * This will generate many .html files in the same directory as the .java file.
 * <p>
 * To run the test suite for the program, open a terminal (such as CMD),
 * navigate to (or start in) the .java file's directory,
 * and enter the following:<br>
 * javac commandEconomy\*.java<br>
 * java commandEconomy.TestSuite 2&gt; output.txt
 * <p>
 * Test results will be in the .java file's directory in output.txt.
 * <p>
 * <b>Usage</b>
 * <p>
 * The commands below may entered into the terminal once the program is running. Please use the separate user manual for more information.
 *
 * <table style="width:75%" summary="Commands usable within the market.">
 *    <tr style="text-decoration:underline;font-weight:normal;">
 *        <th>Command</th>
 *        <th>Action</th>
 *    </tr>
 *    <tr>
 *        <td>/help</td>
 *        <td>prints possible commands</td>
 *    </tr>
 *    <tr>
 *        <td>/buy &#60;ware_id&#62; &#60;quantity&#62; [max_price_acceptable] [account_id]</td>
 *        <td>purchases an item</td>
 *    </tr>
 *    <tr>
 *        <td>/sell &#60;ware_id&#62; [quantity] [min_price_acceptable] [account_id]</td>
 *        <td>sells an item</td>
 *    </tr>
 *    <tr>
 *        <td>/check &#60;ware_id&#62; [quantity]</td>
 *        <td>looks up item price and stock</td>
 *    </tr>
 *    <tr>
 *        <td>/sellall [account_id]</td>
 *        <td>sells all tradeable wares in your inventory at current market prices</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/money [account_id]</td>
 *        <td>looks up how much is in an account</td>
 *    </tr>
 *    <tr>
 *        <td>/send &#60;quantity&#62; &#60;recipient_account_id&#62; [sender_account_id]</td>
 *        <td>transfers money from one account to another</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/create &#60;account_id&#62;</td>
 *        <td>opens a new account with the specified id</td>
 *    </tr>
 *    <tr>
 *        <td>/delete &#60;account_id&#62;</td>
 *        <td>closes the account with the specified id</td>
 *    </tr>
 *    <tr>
 *        <td>/grantAccess &#60;player_name&#62; &#60;account_id&#62;</td>
 *        <td>allows a player to view and withdraw from a specified account</td>
 *    </tr>
 *    <tr>
 *        <td>/revokeAccess &#60;player_name&#62; &#60;account_id&#62;</td>
 *        <td>disallows a player to view and withdraw from a specified account</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/save</td>
 *        <td>saves market wares and accounts</td>
 *    </tr>
 *    <tr>
 *        <td>/stop</td>
 *        <td>shutdowns the market</td>
 *    </tr>
 *    <tr>
 *        <td>/reload &#60;config | wares | accounts | all&#62;</td>
 *        <td>reloads part of the marketplace from file</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/add &#60;quantity&#62; [account_id]</td>
 *        <td>summons money</td>
 *    </tr>
 *    <tr>
 *        <td>/set &#60;quantity&#62; [account_id]</td>
 *        <td>sets account's money to a specified amount</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/printMarket</td>
 *        <td>writes all wares currently tradeable to a file</td>
 *    </tr>
 *    <tr>
 *        <td>/inventory</td>
 *        <td>displays test inventory contents</td>
 *    </tr>
 *    <tr>
 *        <td>/give &#60;ware_id&#62; [quantity]</td>
 *        <td>puts a one or a specific amount of a given id into the test inventory; this id may or may not be tradeable within the marketplace</td>
 *    </tr>
 *    <tr>
 *        <td>/take &#60;ware_id&#62; [quantity]</td>
 *        <td>removes all or a specific amount of a given id from the test inventory</td>
 *    </tr>
 *    <tr>
 *        <td>/changeName &#60;player_name&#62;</td>
 *        <td>sets the test player's name and ID</td>
 *    </tr>
 * </table>
 * <p>
 * When optional account IDs ([account-id]) are not given,
 * the default is to use the command-issuing player's personal account.
 * <p>
 * A word of caution about using the commands above.
 * <p>
 * Without Minecraft, this program uses a test inventory without any enforced maximum
 * for how much a slot may hold of a single ware. In Minecraft, a single slot may only
 * hold 64 wares and multiple slots may hold the same ware. These differences between
 * the test inventory and standard Minecraft inventories affect how some commands work.
 * For example, this program's buying feature prevents overflowing a normal Minecraft
 * inventory by only capping a single purchase's size to 64 per slot multiplied by
 * the number of available slots. For the test inventory, limiting purchases to
 * 64 wares per slot is arbitrary since it won't overflow until
 * reaching 2^31 - 1 wares in a single slot.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-01-29
 */
public class CommandEconomy {
   /** lowercase name for accessing services and information */
   public static final String MODID   = "commandeconomy";
   /** title case name for presentation */
   public static final String NAME    = "Command Economy";
   /** version of this mod */
   public static final String VERSION = "0.7";

   /** used to format currency before displaying */
   public static final DecimalFormat PRICE_FORMAT = new DecimalFormat("$###,##0.00");
   /** used to truncate floats to four decimal places */
   public static final long PRICE_PRECISION = 10000;

   // FUNCTIONS
   /**
    * Main function for initializing the market.
    * Warning: Config.commandInterface must be initialized before calling this function!
    *
    * @param args unused
    */
   public static void start(String[] args) {
      if (Config.commandInterface == null) {
         System.err.println(INITIALIZATION_ERROR);
         return;
      }

      // initialize the market
      Config.loadConfig();
         // if loading configuration fails, default values are loaded
         // new values may be loaded through the command "reload config"
      Marketplace.loadWares();
         // if loading wares fails, a warning is printed,
         // advising users to use the command "reload wares"
      Account.loadAccounts();
         // if loading accounts fails, a warning is printed,
         // advising users to use the command "reload accounts" if necessary

      // run the market
      Config.commandInterface.serviceRequests(); // runs commands until a stop command is used
      return;
   }

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

   // INTERFACE CONSTANTS
   public final static String INITIALIZATION_ERROR = "CommandEconomy initialization fatal error - must initialize Config.commandInterface before calling CommandEconomy.start()";

   // command names
   public final static String CMD_HELP                      = "help";
   public final static String CMD_BUY                       = "buy";
   public final static String CMD_SELL                      = "sell";
   public final static String CMD_CHECK                     = "check";
   public final static String CMD_SELLALL                   = "sellAll";
   public final static String CMD_SELLALL_LOWER             = "sellall";
   public final static String CMD_MONEY                     = "money";
   public final static String CMD_SEND                      = "send";
   public final static String CMD_CREATE                    = "create";
   public final static String CMD_DELETE                    = "delete";
   public final static String CMD_GRANT_ACCESS              = "grantAccess";
   public final static String CMD_GRANT_ACCESS_LOWER        = "grantaccess";
   public final static String CMD_REVOKE_ACCESS             = "revokeAccess";
   public final static String CMD_REVOKE_ACCESS_LOWER       = "revokeaccess";
   public final static String CMD_VERSION                   = "version";
   public final static String CMD_ADD                       = "add";
   public final static String CMD_SET                       = "set";
   public final static String CMD_CHANGE_STOCK              = "changeStock";
   public final static String CMD_CHANGE_STOCK_LOWER        = "changestock";
   public final static String CMD_SAVE                      = "save";
   public final static String CMD_SAVECE                    = "savece";
   public final static String CMD_SET_DEFAULT_ACCOUNT       = "setDefaultAccount";
   public final static String CMD_SET_DEFAULT_ACCOUNT_LOWER = "setdefaultaccount";
   public final static String CMD_RELOAD                    = "reload";
   public final static String CMD_PRINT_MARKET              = "printMarket";
   public final static String CMD_PRINT_MARKET_LOWER        = "printmarket";
   public final static String CMD_INVEST                    = "invest";

   // command keywords
   public final static String ACCOUNT_ADMIN             = "$admin$";
   public final static String TRANSACT_FEE_COLLECTION   = "cumulativeTransactionFees";
   public final static String HELD_ITEM                 = "held";
   public final static String ALL                       = "all";
   public final static String TRUE                      = "true";
   public final static String FALSE                     = "false";
   public final static String HELP_COMMAND_BLOCK        = "command_block";
   public final static String CHANGE_STOCK_EQUILIBRIUM  = "equilibrium";
   public final static String CHANGE_STOCK_OVERSTOCKED  = "overstocked";
   public final static String CHANGE_STOCK_UNDERSTOCKED = "understocked";
   public final static String RELOAD_CONFIG             = "config";
   public final static String RELOAD_WARES              = "wares";
   public final static String RELOAD_ACCOUNTS           = "accounts";
   public final static String INVENTORY_NONE            = "none";
   public final static String INVENTORY_DOWN            = "down";
   public final static String INVENTORY_UP              = "up";
   public final static String INVENTORY_NORTH           = "north";
   public final static String INVENTORY_EAST            = "east";
   public final static String INVENTORY_WEST            = "west";
   public final static String INVENTORY_SOUTH           = "south";
   public final static String YES                       = "yes";
   public final static String ARG_SPECIAL_PREFIX        = "&";
   public final static String MANUFACTURING             = ARG_SPECIAL_PREFIX + "craft";
   public final static String PRICE_PERCENT             = "%";

   // command usages
   public final static String CMD_USAGE_BUY                 = "/" + CMD_BUY + " <ware_id> <quantity> [max_unit_price] [account_id] [" + MANUFACTURING + "]";
   public final static String CMD_USAGE_SELL                = "/" + CMD_SELL + " (<ware_id> | " + HELD_ITEM + ") [<quantity> [min_unit_price] [account_id]]";
   public final static String CMD_USAGE_CHECK               = "/" + CMD_CHECK + " (<ware_id> | " + HELD_ITEM + ") [quantity] [" + MANUFACTURING + "]";
   public final static String CMD_USAGE_SELLALL             = "/" + CMD_SELLALL + " [account_id]";
   public final static String CMD_USAGE_MONEY               = "/" + CMD_MONEY + " [account_id]";
   public final static String CMD_USAGE_SEND                = "/" + CMD_SEND + " <quantity> <recipient_account_id> [sender_account_id]";
   public final static String CMD_USAGE_CREATE              = "/" + CMD_CREATE + " <account_id>";
   public final static String CMD_USAGE_DELETE              = "/" + CMD_DELETE + " <account_id>";
   public final static String CMD_USAGE_GRANT_ACCESS        = "/" + CMD_GRANT_ACCESS + " <player_name> <account_id>";
   public final static String CMD_USAGE_REVOKE_ACCESS       = "/" + CMD_REVOKE_ACCESS + " <player_name> <account_id>";
   public final static String CMD_USAGE_VERSION             = "/" + MODID + " " + CMD_VERSION;
   public final static String CMD_USAGE_ADD                 = "/" + CMD_ADD + " <quantity> [account_id]";
   public final static String CMD_USAGE_SET                 = "/" + CMD_SET + " <quantity> [account_id]";
   public final static String CMD_USAGE_CHANGE_STOCK        = "/" + CMD_CHANGE_STOCK + " <ware_id> (<quantity> | " + CHANGE_STOCK_EQUILIBRIUM + " | " + CHANGE_STOCK_OVERSTOCKED + " | " + CHANGE_STOCK_UNDERSTOCKED + ")";
   public final static String CMD_USAGE_SAVECE              = "/" + CMD_SAVECE;
   public final static String CMD_USAGE_SET_DEFAULT_ACCOUNT = "/" + CMD_SET_DEFAULT_ACCOUNT + " <account_id>";
   public final static String CMD_USAGE_RELOAD              = "/" + MODID + " " + CMD_RELOAD + " (" + RELOAD_CONFIG + " | " + RELOAD_WARES + " | " + RELOAD_ACCOUNTS + " | " + ALL + ")";
   public final static String CMD_USAGE_PRINT_MARKET        = "/" + CMD_PRINT_MARKET;
   public final static String CMD_USAGE_INVEST              = "/" + CMD_INVEST + " <ware_id> [max_price_acceptable] [account_id]";
   public final static String MSG_INVEST_USAGE_YES          = "; use /" + CMD_INVEST + " " + YES + " [max_price_acceptable] [account_id] to accept";

   // command block usages
   public final static String CMD_USAGE_BLOCK_BUY     = "/" + CMD_BUY + " <player_name> <inventory_direction> <ware_id> <quantity> [max_unit_price] [account_id] [" + MANUFACTURING + "]";
   public final static String CMD_USAGE_BLOCK_SELL    = "/" + CMD_SELL + " <username> <inventory_direction> (<ware_id> | " + HELD_ITEM + ") [<quantity> [min_unit_price] [account_id]]";
   public final static String CMD_USAGE_BLOCK_CHECK   = "/" + CMD_CHECK + " <player_name> (<ware_id> | " + HELD_ITEM + ") <quantity> [" + MANUFACTURING + "]";
   public final static String CMD_USAGE_BLOCK_SELLALL = "/" + CMD_SELLALL + " <player_name> <inventory_direction> [account_id]";
   public final static String CMD_USAGE_BLOCK_MONEY   = "/" + CMD_MONEY + " <player_name> <account_id>";
   public final static String CMD_USAGE_BLOCK_SEND    = "/" + CMD_SEND + " <player_name> <quantity> <recipient_account_id> [sender_account_id]";

   // command descriptions
   public final static String CMD_DESC_BUY                 = " - purchases an item\n";
   public final static String CMD_DESC_SELL                = " - sells an item\n";
   public final static String CMD_DESC_CHECK               = " - looks up item price and stock\n";
   public final static String CMD_DESC_SELLALL             = " - sells all tradeable items in your inventory at current market prices\n";
   public final static String CMD_DESC_MONEY               = " - looks up how much is in an account\n";
   public final static String CMD_DESC_SEND                = " [sender_account_id] - transfers money from one account to another\n";
   public final static String CMD_DESC_CREATE              = " - opens a new account with the specified id\n";
   public final static String CMD_DESC_DELETE              = " - closes the account with the specified id\n";
   public final static String CMD_DESC_GRANT_ACCESS        = " - allows a player to view and withdraw from a specified account\n";
   public final static String CMD_DESC_REVOKE_ACCESS       = " - disallows a player to view and withdraw from a specified account\n";
   public final static String CMD_DESC_INVEST              = " - increases a ware's supply and demand\n";
   public final static String CMD_DESC_VERSION             = " - says what version of Command Economy is running\n";
   public final static String CMD_DESC_ADD                 = " - summons money\n";
   public final static String CMD_DESC_SET                 = " - sets account's money to a specified amount\n";
   public final static String CMD_DESC_CHANGE_STOCK        = " - increases or decreases an item's available quantity within the marketplace or sets the quantity to a certain level\n";
   public final static String CMD_DESC_SET_DEFAULT_ACCOUNT = " - marks an account to be used in place of your personal account\n";
   public final static String CMD_DESC_SAVECE              = " - saves market wares and accounts\n";
   public final static String CMD_DESC_RELOAD              = " - reloads part or all of the marketplace from file\n";
   public final static String CMD_DESC_PRINT_MARKET        = " - writes all wares currently tradeable to a file\n";

   // command errors
   public final static String ERROR_NUM_ARGS          = "error - wrong number of arguments: ";
   public final static String ERROR_ZERO_LEN_ARGS     = "error - zero-length arguments: ";
   public final static String ERROR_QUANTITY          = "error - invalid quantity: ";
   public final static String ERROR_PRICE             = "error - invalid price: ";
   public final static String ERROR_ARG               = "error - invalid argument: ";
   public final static String ERROR_INVENTORY_DIR     = "error - invalid inventory direction: should be none, down, up, north, east, west, or south\n";
   public final static String ERROR_INVENTORY_MISSING = "error - inventory not found\n";
   public final static String ERROR_INVENTORY_SPACE   = "You don't have enough inventory space";
   public final static String ERROR_PERMISSION        = "You do not have permission to use this command for other players";
   public final static String ERROR_ENTITY_SELECTOR   = "error - failed to parse entity selector; perhaps it referenced multiple players or you're not an op?";
   public final static String ERROR_RELOAD_MISSING    = "error - must provide instructions for reload: ";
   public final static String ERROR_INVALID_CMD       = "error - invalid command\n   entering \"/" + MODID + " " + CMD_HELP + "\" will list valid commands";

   // ware errors
   public final static String ERROR_WARE_MISSING      = "error - ware not found: ";
   public final static String ERROR_WARE_ID           = "error - no ware ID was given";
   public final static String ERROR_WARE_INVALID      = "error - invalid ware: ";

   // account errors
   public final static String ERROR_ACCOUNT_MISSING    = "error - account not found: ";
   public final static String ERROR_ACCOUNT_ID_MISSING = "error - must provide account ID: ";
   public final static String ERROR_ACCOUNT_EXISTS     = "error - account already exists: ";
   public final static String ERROR_ACCOUNT_IS_PLAYER  = "error - account ID matches existing player name: ";
   public final static String ERROR_ACCOUNT_QUANTITY   = "error - invalid quantity, transfer not completed";
   public final static String ERROR_ACCOUNT_ID_INVALID = "error - invalid quantity, transfer not completed";

   public final static String MSG_ACCOUNT_NO_MONEY               = "You do not have enough money";
   public final static String MSG_ACCOUNT_NO_MONEY_FEE           = "You do not have enough money to both send and pay the transaction fee";
   public final static String MSG_ACCOUNT_TOO_MANY               = "You may not create any more accounts";
   public final static String MSG_ACCOUNT_NUMERICAL_ID           = "Account IDs cannot be numerical";
   public final static String MSG_ACCOUNT_DENIED_ACCESS          = "You don't have permission to access ";
   public final static String MSG_ACCOUNT_DENIED_DELETE          = "You are not permitted to delete ";
   public final static String MSG_ACCOUNT_DENIED_DELETE_PERSONAL = "Personal accounts may not be deleted";
   public final static String MSG_ACCOUNT_DENIED_TRANSFER        = "You don't have permission to send from this account";
   public final static String MSG_ACCOUNT_ACCESS_GRANTED         = "You may now access ";
   public final static String MSG_ACCOUNT_ACCESS_REVOKED         = "You may no longer access ";

   // command messages
   public final static String MSG_RELOAD_CONFIG          = "Reloaded config.";
   public final static String MSG_RELOAD_WARES           = "Reloaded wares.";
   public final static String MSG_RELOAD_ACCOUNTS        = "Reloaded accounts.";
   public final static String MSG_RELOAD_ALL             = "Reloaded config, wares, and accounts.";
   public final static String MSG_PERSONAL_ACCOUNT       = "Your account";
   public final static String MSG_SAVED_ECONOMY          = "Saved the economy";
   public final static String MSG_VERSION                = "Running Command Economy version #";
   public final static String MSG_PRINT_MARKET           = "Current market wares printed to file";
   public final static String MSG_PRICE_ADJUST_NO_PERM   = "You do not have permission to use % in this command";
   public final static String ERROR_PRICE_ADJUST_INVALID = "error - invalid percentage: ";

   // trade messages
   public final static String MSG_BUY_UNTRADEABLE           = " is not for sale in that form";
   public final static String MSG_BUY_OUT_OF_STOCK          = "Market is out of ";
   public final static String MSG_BUY_NO_MONEY              = "Not enough money to buy one item";
   public final static String MSG_INVENTORY_NO_SPACE        = "No inventory space available";
   public final static String MSG_INVENTORY_MISSING         = "No inventory was found";
   public final static String MSG_SELLALL                   = "failed to sell ware: ";
   public final static String MSG_TRANSACT_FEE              = "   Transaction fee applied: ";
   public final static String MSG_TRANSACT_FEE_SALES_LOSS   = "Transaction fee is too high to make a profit";
   public final static String MSG_SELL_NO_GARBAGE_DISPOSING = "Cannot sell at or below the price floor";

   // file-handling
   public final static String ERROR_FILE_LOAD_ACCOUNTS   = "error - unable to load accounts";
   public final static String ERROR_FILE_SAVE_WARES      = "error - unable to save wares";
   public final static String ERROR_FILE_SAVE_ACCOUNTS   = "error - unable to save accounts";
   public final static String ERROR_FILE_PRINT_MARKET    = "error - unable to print wares to file";

   public final static String WARN_FILE_MISSING          = "warning - file not found: ";
   public final static String WARN_FILE_MISSED           = "warning - file went missing: ";

   public final static String WARN_FILE_OVERWRITE        = "// warning: this file may be cleared and overwritten by the program\n\n";
   public final static String WARN_FILE_WARES_INVALID    = "\n// warning: the following ware entries could not be loaded\n\n";
   public final static String WARN_FILE_ACCOUNTS_INVALID = "\n// warning: the following accounts could not be loaded\n\n";
   public final static String FILE_HEADER_ALT_ALIASES    = "\n// alternative aliases: these entries should be kept at the end of the file\n\n";
   public final static String FILE_HEADER_PRINT_MARKET   = "\nware ID\tware alias\tprice\tquantity\tlevel\n";

   // ware file parsing
   public final static String ERROR_WARE_PARSING         = "error - could not parse ware entry: ";
   public final static String ERROR_WARE_PARSING_EXCEPT  = "error - exception occurred while parsing ware entry: ";
   public final static String ERROR_WARE_ENTRY_INVALID   = "error - invalid ware entry, ";
   public final static String ERROR_WARE_ALIAS           = "error - ware's alias is already used: ";
   public final static String ERROR_ALT_ALIAS_ENTRY      = "error - invalid alternate alias entry, ";
   public final static String ERROR_ALT_ALIAS_PARSING    = "could not parse alternate alias entry: ";
   public final static String ERROR_STARTING_QUANTITIES  = "could not set base starting quantities since base average was not greater than zero: base average is ";

   public final static String WARN_WARE_NONEXISTENT     = "warning - nonexistent ware ID was found: ";
   public final static String WARN_WARE_ID_USED         = "warning - duplicate ware ID was found: ";
   public final static String WARN_WARE_ALIAS_USED      = "warning - duplicate ware alias: ";
   public final static String WARN_WARE_NONE_LOADED     = "warning - no wares were added";
   public final static String WARN_ORE_NAME_NONEXISTENT = "warning - nonexistent OreDictionary name was found: ";
   public final static String WARN_ALT_ALIAS_UNUSED     = "warning - no loaded ware found for alternate alias: ";

   // ware parsing
   public final static String ERROR_COMPONENTS_SET         = "error - could not set components for ";
   public final static String ERROR_COMPONENT_IDS          = "no component IDs were given";
   public final static String ERROR_COMPONENT_YIELD        = "no yield was given or invalid yield was given";
   public final static String ERROR_COMPONENTS_AMOUNTS     = "missing componentsAmounts array specifying amounts of each component used";
   public final static String ERROR_COMPONENTS_UNEQUAL_LEN = "unequal lengths of component arrays";
   public final static String ERROR_WARE_ID_MISSING        = "missing ware ID";
   public final static String ERROR_WARE_NO_PRICE          = "unset price";
   public final static String ERROR_COMPONENTS_MISSING     = "missing components' IDs";
   public final static String ERROR_COMPONENT_ID_BLANK     = "blank component ID";
   public final static String ERROR_WARE_PARSING_ID        = "could not parse ware ";

   public final static String WARN_FILE_WARE_INVALID = System.lineSeparator() + "   upon saving, ware entry will be written to the bottom of ";
   public final static String WARN_CRAFTING_DEPTH    = System.lineSeparator() + "   perhaps its crafting depth is deeper than configuration's maxCraftingDepth?";
   public final static String WARN_COMPONENT_MISSING = System.lineSeparator() + "   missing component: ";

   // account file parsing
   public final static String ERROR_ACCOUNT_PARSING = "error - could not parse account: ";

   public final static String WARN_ACCOUNT_CREATION     = "warning - could not parse account creation count for ";
   public final static String WARN_ACCOUNT_DEFAULT      = "warning - could not parse default account entry for ";
   public final static String WARN_ACCOUNT_NONEXISTENT  = "warning - default account entry references nonexistent account: ";
   public final static String WARN_ACCOUNT_UUID_DEFAULT = "warning - default account entry references invalid UUID: ";

   // configuration
   public final static String ERROR_CONFIG_FILE_CREATE    = "error - unable to create config file: ";
   public final static String ERROR_CONFIG_OPTION_SET     = "error - could not set config option ";
   public final static String ERROR_CONFIG_OPTION_VALUE   = System.lineSeparator() + "   intended value was ";
   public final static String ERROR_CONFIG_OPTION_FORMAT  = "improperly formatted option: ";
   public final static String ERROR_CONFIG_OPTION_LOAD    = "error - could not load option ";
   public final static String ERROR_CONFIG_OPTION_PARSING = ", failed to parse ";
   public final static String ERROR_CONFIG_OPTION_ARRAY   = ", should hold 6 values, instead holds ";

   // AI
   public final static String ERROR_AI_MISFORMAT          = "error - AI professions JSON file is improperly formatted: ";
   public final static String ERROR_AI_PARSING            = "error - could not parse AI professions JSON file: ";
   public final static String ERROR_AI_MISSING            = "error - AI profession not found: ";
   public final static String ERROR_AI_PREFS_MISMATCH_PRO = "error - AI profession failed to load due to mismatching preferences; AI profession: ";
   public final static String ERROR_AI_PREFS_MISMATCH_IDS = System.lineSeparator() + "   mismatching ware IDs: ";
   public final static String WARN_AI_NONE_LOADED         = "warning - no AI professions were loaded";
   public final static String WARN_AI_INVALID             = "warning - no AI professions were valid";
   public final static String WARN_AI_INVALID_WARE_PRO    = "warning - invalid ware ID for AI profession ";
   public final static String WARN_AI_INVALID_WARE_IDS    = ": ";
   public final static String WARN_AI_INVALID_PREF_PRO    = "warning - invalid preference for AI profession ";
   public final static String WARN_AI_INVALID_PREF_IDS    = ": ";

   // investments
   public final static String ERROR_INVEST_DISABLED   = "error - investments are disabled";
   public final static String MSG_INVEST_NO_OFFERS    = "You don't have any pending investment offers";
   public final static String MSG_INVEST_LOWEST_LEVEL = " is already as plentiful as possible";
   public final static String MSG_INVEST_LINKED       = " is unsuitable for investment in that form";
   public final static String MSG_INVEST_QUAN_HIGH    = " is already plentiful";
   public final static String MSG_INVEST_FAILED       = " cannot be invested in";
   public final static String MSG_INVEST_SUCCESS      = "'s supply and demand has increased";

   // random events
   public final static String ERROR_FILE_RANDOM_EVENTS_INVALID      = "error - random events JSON file is improperly formatted: ";
   public final static String ERROR_FILE_RANDOM_EVENTS_PARSING      = "error - could not parse random events JSON file: ";
   public final static String ERROR_RANDOM_EVENTS_SLEEP             = "error - random events: failure while waiting for the next event to occur, ";

   public final static String WARN_RANDOM_EVENTS_NONE_LOADED        = "warning - no random events were loaded";

   public final static String MSG_RANDOM_EVENT_DESC                 = System.lineSeparator() + "   event's description: ";
   public final static String MSG_RANDOM_EVENTS_CHANGES             = System.lineSeparator() + "   magnitudes must be: -3, -2, -1, 1, 2, or 3";

   public final static String ERROR_RANDOM_EVENT_DESC_MISSING       = "error - random event is missing an event description" + System.lineSeparator();
   public final static String ERROR_RANDOM_EVENT_MAGNITUDES_MISSING = "error - random event is missing changeMagnitudes; events must affect at least one ware";
   public final static String ERROR_RANDOM_EVENT_MAGNITUDES_BLANK   = "error - random event has no changeMagnitudes entries; events must affect at least one ware";
   public final static String ERROR_RANDOM_EVENT_MAGNITUDES_INVALID = "error - random event's changeMagnitudes entry is invalid: ";
   public final static String ERROR_RANDOM_EVENT_WARES_MISSING      = "error - random event is missing changedWaresIDs; events must affect at least one ware";
   public final static String ERROR_RANDOM_EVENT_WARES_BLANK        = "error - random event has no changedWaresIDs entries; events must affect at least one ware";
   public final static String ERROR_RANDOM_EVENT_WARES_INVALID      = "error - random event is using invalid ware ID or alias" + System.lineSeparator();
   public final static String ERROR_RANDOM_EVENT_WARES_INVALID_LIST = System.lineSeparator() + "Invalid IDs: ";
   public final static String ERROR_RANDOM_EVENT_WARES_NO_VALID     = "error - random event lacks valid wares";
   public final static String ERROR_RANDOM_EVENT_CHANGES_MISMATCH   = "error - random event's changeMagnitudes and changedWaresIDs are not the same size; the number of magnitudes for affecting wares must match the number of ware affected";
}