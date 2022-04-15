package commandeconomy;

import java.util.UUID;                // for more securely tracking users internally

/**
 * Processes user input for commands whose execution is highly similar between interfaces.
 * When implementing an interface, using the functions below is optional, but recommended.
 * 
 * Reusing code when possible eases testing and implementing new interfaces,
 * adding stability and easing development. However, extra function calls and processing
 * from accessing data through an interface may reduce performance.
 * 
 * The commands processed here should not require the user's position
 * nor use entity selectors since processing for those are
 * highly dependent on the interface used and not particularly modular.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-08-27
 */
public class CommandProcessor
{
   // FUNCTIONS
   /**
    * Opens a new account with the specified id.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void accountCreate(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_CREATE);
         return;
      }

      // check for zero-length args
      if (args.length < 1 || args[0] == null || args[0].length() == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_ID_MISSING + CommandEconomy.CMD_USAGE_CREATE);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_CREATE);
         return;
      }

      // don't overwrite an existing account
      if (Account.getAccount(args[0]) != null) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_EXISTS + CommandEconomy.CMD_USAGE_CREATE);
         return;
      }

      // don't create an account with the same name as a player
      // unless the account creator is that player
      if (!Config.commandInterface.getDisplayName(playerID).equals(args[0]) && Config.commandInterface.doesPlayerExist(args[0])) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_IS_PLAYER + CommandEconomy.CMD_USAGE_CREATE);
         return;
      }

      // call corresponding function
      Account.makeAccount(args[0], playerID);

      // only report success if the account was actually created
      if (Account.getAccount(args[0]) != null)
         Config.commandInterface.printToUser(playerID, "Created new account: " + args[0]);
      return;
   }

   /**
    * Closes an account with the specified id.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void accountDelete(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_DELETE);
         return;
      }

      // check for zero-length args
      if (args.length < 1 || args[0] == null || args[0].length() == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_ID_MISSING);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_DELETE);
         return;
      }

      // call corresponding function
      Account.deleteAccount(args[0], playerID);
      return;
   }

   /**
    * Allows a player to view and withdraw from a specified account.<br>
    * Expected Format: &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void accountGrantAccess(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_GRANT_ACCESS);
         return;
      }

      // command must have the right number of args
      if (args.length != 2) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_GRANT_ACCESS);
         return;
      }

      // validate the given account ID
      // given account ID should not be null
      if (args[1] == null || args[1].length() == 0)
         return;
      // don't overwrite an existing account
      Account account = Account.getAccount(args[1]);
      if (account == null) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_MISSING + CommandEconomy.CMD_USAGE_GRANT_ACCESS);
         return;
      }

      // call corresponding function
      account.grantAccess(playerID, Config.commandInterface.getPlayerID(args[0]), args[1]);
      return;
   }

   /**
    * Disallows a player to view and withdraw from a specified account.<br>
    * Expected Format: &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void accountRevokeAccess(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_REVOKE_ACCESS);
         return;
      }

      // command must have the right number of args
      if (args.length != 2) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_REVOKE_ACCESS);
         return;
      }

      // validate the given account ID
      // given account ID should not be null
      if (args[1] == null || args[1].length() == 0)
         return;
      // don't overwrite an existing account
      Account account = Account.getAccount(args[1]);
      if (account == null) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_MISSING + CommandEconomy.CMD_USAGE_REVOKE_ACCESS);
         return;
      }

      // call corresponding function
      account.revokeAccess(playerID, Config.commandInterface.getPlayerID(args[0]), args[1]);
      return;
   }

   /**
    * Reloads part of the marketplace from file.<br>
    * Expected Format: (config | wares | accounts | all)
    *
    * @param playerID    player executing the command
    * @param args        arguments given in the expected format
    * @param indexOffset how many indices to skip to reach the first argument to be used
    */
   public static void reload(UUID playerID, String[] args, int indexOffset) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_RELOAD);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 + indexOffset) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_RELOAD);
         return;
      }

      // input argument should not be null
      if (args[indexOffset] == null || args[indexOffset].length() == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_RELOAD_MISSING + CommandEconomy.CMD_USAGE_RELOAD);
         return;
      }

      // call corresponding function or report error
      if (args[indexOffset].equals(CommandEconomy.RELOAD_CONFIG)) {
         Config.loadConfig();
         Config.commandInterface.printToUser(playerID, CommandEconomy.MSG_RELOAD_CONFIG);
      }
      else if (args[indexOffset].equals(CommandEconomy.RELOAD_WARES)) {
         Marketplace.loadWares();
         Config.commandInterface.printToUser(playerID, CommandEconomy.MSG_RELOAD_WARES);
      }
      else if (args[indexOffset].equals(CommandEconomy.RELOAD_ACCOUNTS)) {
         Account.loadAccounts();
         Config.commandInterface.printToUser(playerID, CommandEconomy.MSG_RELOAD_ACCOUNTS);
      }
      else if (args[indexOffset].equals(CommandEconomy.ALL)) {
         Config.loadConfig();
         Marketplace.loadWares();
         Account.loadAccounts();
         Config.commandInterface.printToUser(playerID, CommandEconomy.MSG_RELOAD_ALL);
      }
      else {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ARG + CommandEconomy.CMD_USAGE_RELOAD);
      }
      return;
   }

   /**
    * Summons money.<br>
    * Expected Format: &#60;quantity&#62; [account_id]
    *
    * @param playerID    player executing the command
    * @param args        arguments given in the expected format
    */
   public static void add(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_ADD);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 &&
          args.length != 2) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_ADD);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (args.length == 2 && (args[1] == null || args[1].length() == 0))) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_ADD);
         return;
      }

      // set up variables
      float quantity = 0.0f;

      // ensure passed quantity is a valid type
      try {
         quantity = Float.parseFloat(args[0]);
      } catch (NumberFormatException e) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_ADD);
         return;
      }

      // if an account ID is given, try to use it
      // otherwise, use the player's personal account
      Account account;
      if (args.length == 2) {
         // given account ID should not be null
         if (args[1] == null || args[1].length() == 0)
            return;
         // check if account exists
         account = Account.getAccount(args[1]);
         if (account == null) {
            Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_MISSING + CommandEconomy.CMD_USAGE_ADD);
            return;
         }

         // summon money for the account
         account.addMoney(quantity);

         // tell players about the money
         Config.commandInterface.printToUser(playerID, "Gave " + CommandEconomy.PRICE_FORMAT.format(quantity) + " to " + args[1]);
         Config.commandInterface.printToUser(account.getOwner(), "Received " + CommandEconomy.PRICE_FORMAT.format(quantity));
      }
      // if no account ID is given,
      // use the player's personal account
      else {
         String playername = Config.commandInterface.getDisplayName(playerID);

         // if the player doesn't have a personal account, make one
         account = Account.getAccount(playername);
         if (account == null)
            account = Account.makeAccount(playername, playerID);

         // summon money for the account
         account.addMoney(quantity);

         // tell the player about the money
         Config.commandInterface.printToUser(playerID, "Received " + CommandEconomy.PRICE_FORMAT.format(quantity));
      }

      return;
   }

   /**
    * Sets account's money to a specified amount.<br>
    * Expected Format: &#60;quantity&#62; [account_id]
    *
    * @param playerID    player executing the command
    * @param args        arguments given in the expected format
    * @param indexOffset how many indices to skip to reach the first argument to be used
    */
   public static void set(UUID playerID, String[] args, int indexOffset) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_SET);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 + indexOffset &&
          args.length != 2 + indexOffset) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SET);
         return;
      }

      // check for zero-length args
      if (args[indexOffset] == null || args[indexOffset].length() == 0 ||
          (args.length == 2 + indexOffset && (args[1 + indexOffset] == null || args[1 + indexOffset].length() == 0))) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SET);
         return;
      }

      // set up variables
      float quantity = 0.0f;

      // ensure passed quantity is a valid type
      try {
         quantity = Float.parseFloat(args[indexOffset]);
      } catch (NumberFormatException e) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_SET);
         return;
      }

      // if an account ID is given, try to use it
      // otherwise, use the player's personal account
      Account account;
      if (args.length == 2 + indexOffset) {
         // given account ID should not be null
         if (args[1 + indexOffset] == null || args[1 + indexOffset].length() == 0)
            return;
         // check if account exists
         account = Account.getAccount(args[1 + indexOffset]);
         if (account == null) {
            Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ACCOUNT_MISSING + CommandEconomy.CMD_USAGE_SET);
            return;
         }

         // set account's money
         account.setMoney(quantity);

         // tell players about the money
         Config.commandInterface.printToUser(playerID, "Set " + args[1 + indexOffset] + " funds to " + CommandEconomy.PRICE_FORMAT.format(quantity));
         if (!playerID.equals(account.getOwner()))
            Config.commandInterface.printToUser(account.getOwner(), args[1 + indexOffset] + " funds to " + CommandEconomy.PRICE_FORMAT.format(quantity));
      }
      // if no account ID is given,
      // use the player's personal account
      else {
         String playername = Config.commandInterface.getDisplayName(playerID);

         // if the player doesn't have a personal account, make one
         account = Account.getAccount(playername);
         if (account == null)
            account = Account.makeAccount(playername, playerID);

         // set account's money
         account.setMoney(quantity);

         // tell the player about the money
         Config.commandInterface.printToUser(playerID, "Personal funds set to " + CommandEconomy.PRICE_FORMAT.format(quantity));
      }

      return;
   }

   /**
    * Increases or decreases a ware's available quantity within the marketplace
    * or sets the quantity to a certain level.<br>
    * Expected Format: &#60;ware_id&#62; (&#60;quantity&#62; | equilibrium | overstocked | understocked)
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void changeStock(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // command must have the right number of args
      if (args.length != 2) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          args[1] == null || args[1].length() == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // set up variables
      int  quantity = 2147483647;
      Ware ware     = null;

      // grab the ware to be used
      ware = Marketplace.translateAndGrab(args[0]);
      // if ware is not in the market, stop
      if (ware == null) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_WARE_MISSING + args[0]);
         return;
      }

      // find where to set stock to
      if (args[1].equalsIgnoreCase(CommandEconomy.CHANGE_STOCK_EQUILIBRIUM))
         quantity = Config.quanMid[ware.getLevel()];
      else if (args[1].equalsIgnoreCase(CommandEconomy.CHANGE_STOCK_OVERSTOCKED))
         quantity = Config.quanHigh[ware.getLevel()];
      else if (args[1].equalsIgnoreCase(CommandEconomy.CHANGE_STOCK_UNDERSTOCKED))
         quantity = Config.quanLow[ware.getLevel()];
      else {
         try {
            quantity = Integer.parseInt(args[1]) + ware.getQuantity();
         } catch (NumberFormatException e) {
            // error message printed later
         }
      }
      if (quantity == 2147483647) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // set the ware's stock
      ware.setQuantity(quantity);

      // report success
      Config.commandInterface.printToUser(playerID, ware.getWareID() + "'s stock is now " + ware.getQuantity());
      return;
   }

   /**
    * Marks an account to be used in place of a personal account.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void setDefaultAccount(UUID playerID, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.CMD_USAGE_SET_DEFAULT_ACCOUNT);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SET_DEFAULT_ACCOUNT);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0) {
         Config.commandInterface.printErrorToUser(playerID, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SET_DEFAULT_ACCOUNT);
         return;
      }

      // try to set the account
      if (Account.setDefaultAccount(playerID, args[0])) {
         // report success
         Config.commandInterface.printToUser(playerID, args[0] + " will now be used in place of your personal account");
      }
      // if it didn't work, an error has already been printed

      return;
   }
 };
