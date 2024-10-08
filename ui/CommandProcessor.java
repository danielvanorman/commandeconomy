package commandeconomy;

import java.util.UUID;                // for more securely tracking users internally
import java.util.HashMap;             // for storing research proposals
import java.util.Map;
import java.util.List;                // for returning properties of wares found in an inventory

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
public final class CommandProcessor
{
   // GLOBAL VARIABLES
   /** for /research, holds the latest proposal for each player */
   private static Map<UUID, ResearchProposal> researchProposals = null;

   // STRUCTS
   /**
    * For /research, holds values for paying to increase a ware's supply and demand.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2021-07-31
    */
   private static class ResearchProposal
   {
      /** reference to the ware able to be researched */
      public Ware ware;
      /** ID referring to the ware able to be researched */
      public String wareID;
      /** ID referring to the account to be charged */
      public String accountID;
      /** last calculated price */
      public float price;

      /**
       * Research Proposal Constructor: Fills fields for an offer for increasing a ware's supply and demand.
       *
       * @param ware      reference to the ware able to be researched
       * @param wareID    ID referring to the ware able to be researched
       * @param accountID ID referring to the account to be charged
       * @param price     last calculated price
       */
      public ResearchProposal (Ware ware, String wareID, String accountID, float price) {
         this.ware      = ware;
         this.wareID    = wareID;
         this.accountID = accountID;
         this.price     = price;
      }
   }

   // FUNCTIONS
   /**
    * Purchases a ware.
    * <p>
    * Expected Formats:<br>
    * &#60;ware_id&#62; &#60;quantity&#62; [max_unit_price] [account_id] [&amp;craft]<br>
    * &#60;player_name&#62; &#60;inventory_direction&#62; &#60;ware_id&#62; &#60;quantity&#62; [max_unit_price] [account_id] [&amp;craft]<br>
    * &#60;inventory_direction&#62; is none, east, west, south, up, or down
    *
    * @param senderID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command; determines original position
    * @param args     arguments given in the expected format
    */
   public static void buy(UUID senderID, Object sender, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.userInterface.printToUser(senderID, StringTable.CMD_USAGE_BUY);
         return;
      }

      // set up variables
      String  username          = null;
      UserInterface.Coordinates coordinates = null;
      String  accountID         = null;
      String  wareID            = null;
      int     baseArgsLength    = args.length; // number of args, not counting special keywords
      int     quantity          = 0;
      float   priceUnit         = Float.NaN;
      float   pricePercent      = 1.0f;

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_BUY);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(StringTable.ARG_SPECIAL_PREFIX) && !arg.startsWith(StringTable.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(StringTable.PRICE_PERCENT)) {
            pricePercent = parsePricePercentArgument(senderID, arg, true);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }
      }

      // command must have the right number of args
      if (baseArgsLength < 2 ||
          baseArgsLength > 6) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_BUY);
         return;
      }

      // if the second argument is a direction, a username and a direction should be given
      // otherwise, the second argument should be a number and, no username or direction should be given
      if (args[1].equals(StringTable.INVENTORY_NONE) ||
          args[1].equals(StringTable.INVENTORY_DOWN) ||
          args[1].equals(StringTable.INVENTORY_UP) ||
          args[1].equals(StringTable.INVENTORY_NORTH) ||
          args[1].equals(StringTable.INVENTORY_EAST) ||
          args[1].equals(StringTable.INVENTORY_WEST) ||
          args[1].equals(StringTable.INVENTORY_SOUTH)) {
         // ensure passed args are valid types
         try {
            quantity = Integer.parseInt(args[3]);
         } catch (NumberFormatException e) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_BLOCK_BUY);
            return;
         }

         // if five arguments are given,
         // the fifth must either be a price or an account ID
         if (baseArgsLength == 5) {
            try {
               // assume the fifth argument is a price
               priceUnit = Float.parseFloat(args[4]);
            } catch (NumberFormatException e) {
               // if the fifth argument is not a price,
               // it must be an account ID
               accountID = args[4];
            }
         }

         // if seven arguments are given,
         // they must be a price and an account ID
         else if (baseArgsLength == 6) {
            try {
               priceUnit = Float.parseFloat(args[4]);
            } catch (NumberFormatException e) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PRICE + StringTable.CMD_USAGE_BLOCK_BUY);
               return;
            }
            accountID = args[5];
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[2];

         // translate coordinates
         coordinates = Config.userInterface.getInventoryCoordinates(senderID, sender, args[1]);
         if (coordinates == null) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_INVENTORY_DIR + StringTable.CMD_USAGE_BLOCK_BUY);
            return;
         }
      }

      // if no username or direction should be given
      else {
         // ensure passed args are valid types
         try {
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_BUY);
            return;
         }

         // if three arguments are given,
         // the third must either be a price or an account ID
         if (baseArgsLength == 3) {
            try {
               // assume the third argument is a price
               priceUnit = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
               // if the third argument is not a price,
               // it must be an account ID
               accountID = args[2];
            }
         }

         // if four arguments are given,
         // they must be a price and an account ID
         else if (baseArgsLength == 4) {
            try {
               priceUnit = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PRICE + StringTable.CMD_USAGE_BUY);
               return;
            }
            accountID = args[3];
         }

         // grab remaining variables
         username = Config.userInterface.getDisplayName(senderID);
         wareID   = args[0];
      }

      // check for entity selectors
      username  = Config.userInterface.parseEntitySelector(sender, username);
      accountID = Config.userInterface.parseEntitySelector(sender, accountID);
      if ((username != null && username.equals("")) ||
          (accountID != null && accountID.equals(""))) { // empty string indicates an invalid selector
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = Config.userInterface.getPlayerID(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!Config.userInterface.permissionToExecute(playerID, sender, false)) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_PERMISSION);
         return;
      }

      // check inventory existence
      if (coordinates != null &&
          Config.userInterface.getInventorySpaceAvailable(playerID, coordinates) == -1) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_INVENTORY_MISSING + StringTable.CMD_USAGE_BLOCK_BUY);
         return;
      }

      // call corresponding function
      Marketplace.buy(playerID, coordinates, accountID, wareID, quantity, priceUnit, pricePercent);
   }

   /**
    * Sells a ware.
    * <p>
    * Expected Formats:<br>
    * (&#60;ware_id&#62; | held) [&#60;quantity&#62; [min_unit_price] [account_id]]<br>
    * &#60;player_name&#62; &#60;inventory_direction&#62; (&#60;ware_id&#62; | held) [&#60;quantity&#62; [min_unit_price] [account_id]]<br>
    * &#60;inventory_direction&#62; == none, east, west, south, up, or down
    *
    * @param senderID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command; determines original position
    * @param server   host running the game instance; used for obtaining player information
    * @param args     arguments given in the expected format
    */
   public static void sell(UUID senderID, Object sender, Object server, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.userInterface.printToUser(senderID, StringTable.CMD_USAGE_SELL);
         return;
      }

      // set up variables
      String username       = null;
      UserInterface.Coordinates coordinates = null;
      String accountID      = null;
      String wareID         = null;
      int    baseArgsLength = args.length; // number of args, not counting special keywords
      int    inventorySlot  = 0;           // where to begin selling within a container
      int    quantity       = -1;
      float  priceUnit      = Float.NaN;
      float  pricePercent   = 1.0f;

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_BUY);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(StringTable.ARG_SPECIAL_PREFIX) && !arg.startsWith(StringTable.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(StringTable.PRICE_PERCENT)) {
            pricePercent = parsePricePercentArgument(senderID, arg, true);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }
      }

      // command must have the right number of args
      if (baseArgsLength < 1 ||
          baseArgsLength > 6) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_SELL);
         return;
      }

      // if the second argument is a number, no username or direction should be given
      // if the second argument is a direction, a username and a direction should be given
      // if a username and a direction should be given
      if (baseArgsLength >= 3 &&
          (args[1].equals(StringTable.INVENTORY_NONE) ||
           args[1].equals(StringTable.INVENTORY_DOWN) ||
           args[1].equals(StringTable.INVENTORY_UP) ||
           args[1].equals(StringTable.INVENTORY_NORTH) ||
           args[1].equals(StringTable.INVENTORY_EAST) ||
           args[1].equals(StringTable.INVENTORY_WEST) ||
           args[1].equals(StringTable.INVENTORY_SOUTH))) {
         // ensure passed args are valid types
         // if at least four arguments are given,
         // the fourth must be a quantity
         if (baseArgsLength >= 4) {
            try {
               quantity = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_BLOCK_SELL);
               return;
            }
         }

         // if five arguments are given,
         // the fifth must either be a price or an account ID
         if (baseArgsLength == 5) {
            try {
               // assume the third argument is a price
               priceUnit = Float.parseFloat(args[4]);
            } catch (NumberFormatException e) {
               // if the third argument is not a price,
               // it must be an account ID
               accountID = args[4];
            }
         }

         // if six arguments are given,
         // they must be a price and an account ID
         else if (baseArgsLength == 6) {
            try {
                  priceUnit = Float.parseFloat(args[4]);
            } catch (NumberFormatException e) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PRICE + StringTable.CMD_USAGE_BLOCK_SELL);
               return;
            }
            accountID = args[5];
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[2];

         // translate coordinates
         coordinates = Config.userInterface.getInventoryCoordinates(senderID, sender, args[1]);
         if (coordinates == null) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_INVENTORY_DIR + StringTable.CMD_USAGE_BLOCK_SELL);
            return;
         }
      }

      // if no username or direction should be given
      else {
         // ensure passed args are valid types
         // if at least two arguments are given,
         // the second must be a quantity
         if (baseArgsLength > 1) {
            try {
               quantity = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_SELL);
               return;
            }
         }

         // if three arguments are given,
         // the third must either be a price or an account ID
         if (baseArgsLength == 3) {
            try {
               // assume the third argument is a price
               priceUnit = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
               // if the third argument is not a price,
               // it must be an account ID
               accountID = args[2];
            }
         }

         // if four arguments are given,
         // they must be a price and an account ID
         else if (baseArgsLength == 4) {
            try {
                  priceUnit = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PRICE + StringTable.CMD_USAGE_SELL);
               return;
            }
            accountID = args[3];
         }

         // grab remaining variables
         username = Config.userInterface.getDisplayName(senderID);
         wareID   = args[0];
      }

      // check for entity selectors
      username  = Config.userInterface.parseEntitySelector(sender, username);
      accountID = Config.userInterface.parseEntitySelector(sender, accountID);
      if ((username != null && username.equals("")) ||
          (accountID != null && accountID.equals(""))) { // empty string indicates an invalid selector
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = Config.userInterface.getPlayerID(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!Config.userInterface.permissionToExecute(playerID, sender, false)) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PERMISSION);
         return;
      }

      // check inventory existence
      if (coordinates != null &&
          Config.userInterface.getInventorySpaceAvailable(playerID, coordinates) == -1) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_INVENTORY_MISSING + StringTable.CMD_USAGE_BLOCK_BUY);
         return;
      }

      // check whether the ware the user is currently holding should be sold
      // the idea of selling the user's held item is from
      // DynamicEconomy ( https://dev.bukkit.org/projects/dynamiceconomy-v-01 )
      if (wareID.equalsIgnoreCase(StringTable.HELD_ITEM)) {
         UserInterface.Handful handful = Config.userInterface.checkHand(playerID, sender, server, username);
         if (handful == null)
            return; // an error message has already been printed

         // get whatever is in the player's hand
         wareID        = handful.wareID;
         inventorySlot = handful.inventorySlot;
         if (quantity == -1)
            quantity = handful.quantity;
      }

      // if quantity hasn't been set,
      // set it to sell everything
      if (quantity == -1)
         quantity = 0;

      // call corresponding function
      Marketplace.sell(playerID, coordinates, accountID, wareID, inventorySlot, quantity, priceUnit, pricePercent);
   }

   /**
    * Looks up ware price and stock.
    * <p>
    * Expected Formats:<br>
    * (&#60;ware_id&#62; | held) [quantity] [&amp;craft]<br>
    * &#60;player_name&#62; (&#60;ware_id&#62; | held) &#60;quantity&#62; [&amp;craft]
    *
    * @param senderID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command; determines original position
    * @param server   host running the game instance; used for obtaining player information
    * @param args     arguments given in the expected format
    */
   public static void check(UUID senderID, Object sender, Object server, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.userInterface.printToUser(senderID, StringTable.CMD_USAGE_CHECK);
         return;
      }

      // set up variables
      String  username          = null;
      String  wareID            = null;
      int     baseArgsLength    = args.length; // number of args, not counting special keywords
      int     quantity          = 0;           // holds ware quantities
      float   pricePercent      = 1.0f;

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_CHECK);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(StringTable.ARG_SPECIAL_PREFIX) && !arg.startsWith(StringTable.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(StringTable.PRICE_PERCENT)) {
            pricePercent = parsePricePercentArgument(senderID, arg, false);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }
      }

      // command must have the right number of args
      if (baseArgsLength < 1 ||
          baseArgsLength > 3) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_CHECK);
         return;
      }

      // if one argument is given,
      // the it is a ware ID
      if (baseArgsLength == 1) {
         username = Config.userInterface.getDisplayName(senderID);
         wareID = args[0];
      }

      // if two arguments are given,
      // the second must be a quantity
      else if (baseArgsLength == 2) {
         try {
            // assume the second argument is a number
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_CHECK);
            return;
         }

         // grab remaining variables
         username = Config.userInterface.getDisplayName(senderID);
         wareID   = args[0];
      }

      // if three arguments are given,
      // then they include a username and a quantity
      else if (baseArgsLength == 3) {
         // try to process quantity
         try {
            quantity = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_BLOCK_CHECK);
            return;
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[1];
      }

      // check for entity selectors
      username = Config.userInterface.parseEntitySelector(sender, username);
      if (username != null && username.equals("")) { // empty string indicates an invalid selector
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = Config.userInterface.getPlayerID(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!Config.userInterface.permissionToExecute(playerID, sender, false)) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PERMISSION);
         return;
      }

      // check whether the ware the user is currently holding should be checked
      // the idea of checking the user's held item is from
      // DynamicEconomy ( https://dev.bukkit.org/projects/dynamiceconomy-v-01 )
      if (wareID.equalsIgnoreCase(StringTable.HELD_ITEM)) {
         UserInterface.Handful handful = Config.userInterface.checkHand(playerID, sender, server, username);
         if (handful == null)
            return; // an error message has already been printed

         // if quantity isn't specified, use however much the player is holding
         if (quantity == 0)
            quantity = handful.quantity;

         // if whatever is in the player's hand is damaged,
         // examine its damage when determining its price
         if (handful.percentWorth != 1.0f) {
            Marketplace.check(playerID, handful.wareID, quantity, handful.percentWorth, pricePercent);
            return;
         }

         // get whatever is in the player's hand
         else
            wareID = handful.wareID;
      }

      // call corresponding function
      Marketplace.check(playerID, wareID, quantity, pricePercent);
   }

   /**
    * Sells all tradeable wares in the inventory at current market prices.
    * <p>
    * Expected Formats:<br>
    * [account_id]<br>
    * &#60;player_name&#62; &#60;inventory_direction&#62; [account_id]
    *
    * @param senderID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command; determines original position
    * @param args     arguments given in the expected format
    */
   public static void sellAll(UUID senderID, Object sender, String[] args) {
      // request can be null or have zero arguments

      // set up variables
      String username  = null;
      UserInterface.Coordinates coordinates = null;
      String accountID = null;
      List<Marketplace.Stock> inventory = null; // wares to be sold
      int   baseArgsLength; // number of args, not counting special keywords
      float pricePercent = 1.0f;

      // avoid null pointer exception
      if (args == null)
         baseArgsLength = 0;
      else {
         baseArgsLength = args.length;

         // check for and process special keywords and zero-length args
         for (String arg : args) {
            // if a zero-length arg is detected, stop
            if (arg == null || arg.length() == 0) {
               Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_BUY);
               return;
            }

            // special keywords start with certain symbols
            if (!arg.startsWith(StringTable.ARG_SPECIAL_PREFIX) && !arg.startsWith(StringTable.PRICE_PERCENT))
               continue;

            // if a special keyword is detected,
            // adjust the arg length count for non-special args
            baseArgsLength--;

            // check whether user is specifying the transaction price multiplier
            if (arg.startsWith(StringTable.PRICE_PERCENT)) {
               pricePercent = parsePricePercentArgument(senderID, arg, true);

               // check for error
               if (Float.isNaN(pricePercent))
                  return; // an error message has already been printed

               continue; // skip to the next argument
            }
         }
      }

      // command must have the right number of args
      if (args != null &&
          (baseArgsLength < 0 ||
           baseArgsLength > 3)) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_SELLALL);
         return;
      }

      // if there are at least two arguments,
      // a username and a direction must be given
      if (args != null && baseArgsLength >= 2) {
         username = args[0];

         // translate coordinates
         coordinates = Config.userInterface.getInventoryCoordinates(senderID, sender, args[1]);
         if (coordinates == null) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_INVENTORY_DIR + StringTable.CMD_USAGE_BLOCK_SELLALL);
            return;
         }

         // if an account ID was given, use it
         if (baseArgsLength == 3)
            accountID = args[2];
         // If an account ID wasn't given, leave the ID as null.
      } else {
         username    = Config.userInterface.getDisplayName(senderID);
         coordinates = null;

         // if an account ID was given, use it
         if (args != null && baseArgsLength == 1)
            accountID = args[0];
         // If an account ID wasn't given, leave the ID as null.
      }

      // check for entity selectors
      username  = Config.userInterface.parseEntitySelector(sender, username);
      accountID = Config.userInterface.parseEntitySelector(sender, accountID);
      if ((username != null && username.equals("")) ||
          (accountID != null && accountID.equals(""))) { // empty string indicates an invalid selector
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = Config.userInterface.getPlayerID(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!Config.userInterface.permissionToExecute(playerID, sender, false)) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PERMISSION);
         return;
      }

      // get the inventory
      inventory = Config.userInterface.getInventoryContents(playerID, coordinates);
      if (inventory == null) {
         System.out.println(StringTable.ERROR_INVENTORY_MISSING + StringTable.CMD_USAGE_BLOCK_SELLALL);
         return;
      }

      Marketplace.sellAll(playerID, coordinates, inventory, accountID, pricePercent);
   }

   /**
    * Looks up how much is in an account.
    * <p>
    * Expected Formats:<br>
    * [account_id]<br>
    * &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param senderID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command; determines original position
    * @param args arguments given in the expected format
    */
   public static void money(UUID senderID, Object sender, String[] args) {
      // request can be null or have zero arguments

      // check for zero-length args
      if (args != null && 
          ((args.length >= 1 && (args[0] == null || args[0].length() == 0)) ||
           (args.length >= 2 && (args[1] == null || args[1].length() == 0)))) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_MONEY);
         return;
      }

      // if nothing is given,
      // use the player's personal account or default account
      if (args == null || args.length == 0) {
         Account account = Account.grabAndCheckAccount(null, senderID);

         account.check(senderID, StringTable.MSG_PERSONAL_ACCOUNT);
         return;
      }

      // set up variables
      String username  = null;
      String accountID = null;

      // if only an account ID is given
      if (args.length == 1) {
         username  = Config.userInterface.getDisplayName(senderID);
         accountID = args[0];
      }
      // if a username and an account ID are given
      else if (args.length >= 2) {
         username  = args[0];
         accountID = args[1];
      }

      // check for entity selectors
      username  = Config.userInterface.parseEntitySelector(sender, username);
      accountID = Config.userInterface.parseEntitySelector(sender, accountID);
      if ((username != null && username.equals("")) ||
          (accountID != null && accountID.equals(""))) { // empty string indicates an invalid selector
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = Config.userInterface.getPlayerID(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!Config.userInterface.permissionToExecute(playerID, sender, false)) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_PERMISSION);
         return;
      }

      // grab the account using the given account ID
      Account account = Account.grabAndCheckAccount(accountID, playerID);
      if (account != null)
         account.check(playerID, accountID);
      // if the account was not found, an error message has already been printed
   }

   /**
    * Transfers money from one account to another.
    * <p>
    * Expected Formats:<br>
    * &#60;quantity&#62; &#60;recipient_account_id&#62; [sender_account_id]<br>
    * &#60;player_name&#62; &#60;quantity&#62; &#60;recipient_account_id&#62; [sender_account_id]
    *
    * @param senderID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command; determines original position
    * @param args arguments given in the expected format
    */
   public static void send(UUID senderID, Object sender, String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.userInterface.printToUser(senderID, StringTable.CMD_USAGE_SEND);
         return;
      }

      // command must have the right number of args
      if (args.length < 2 ||
          args.length > 4) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_SEND);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          args[1] == null || args[1].length() == 0 ||
          (args.length == 3 && (args[2] == null || args[2].length() == 0)) ||
          (args.length == 4 && (args[3] == null || args[3].length() == 0))) {
         Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_SEND);
         return;
      }

      // set up variables
      String username           = null;
      float  quantity           = 0.0f;
      String recipientAccountID = null;
      String senderAccountID    = null;
      Account account           = null;

      // if the first argument is a number,
      // the other arguments must be account IDs
      // otherwise, the first argument is a username
      try {
         quantity = Float.parseFloat(args[0]);
         username = Config.userInterface.getDisplayName(senderID);
         recipientAccountID = args[1];

         // if a sender account ID is given, use it
         if (args.length == 3)
            senderAccountID = args[2];

         // if no sender account is given,
         // use the player's personal account
         else
            senderAccountID = Config.userInterface.getDisplayName(senderID);
      } catch (NumberFormatException e) {
         // try to parse quantity
         try {
            quantity = Float.parseFloat(args[1]);
         } catch (NumberFormatException nfe) {
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_BLOCK_SEND);
            return;
         }

         // if a sender account ID is given, use it
         if (args.length == 4) {
            username           = args[0];
            recipientAccountID = args[2];
            senderAccountID    = args[3];
         }
         // if no sender account is given,
         // use the player's personal account
         else if (args.length == 3) {
            username           = args[0];
            recipientAccountID = args[2];
            senderAccountID    = args[0];
         }

         // check for entity selectors
         username           = Config.userInterface.parseEntitySelector(sender, username);
         recipientAccountID = Config.userInterface.parseEntitySelector(sender, recipientAccountID);
         senderAccountID    = Config.userInterface.parseEntitySelector(sender, senderAccountID);
         if ((username != null && username.equals("")) ||
             (recipientAccountID != null && recipientAccountID.equals("")) ||
             (senderAccountID != null && senderAccountID.equals(""))) { // empty string indicates an invalid selector
            Config.userInterface.printErrorToUser(senderID, StringTable.ERROR_ENTITY_SELECTOR);
            return;
         }
      }

      // if a valid account is given, use it
      account = Account.grabAndCheckAccount(senderAccountID, Config.userInterface.getPlayerID(username));
      if (account != null)
         // transfer the money
         account.sendMoney(Config.userInterface.getPlayerID(username), quantity, senderAccountID, recipientAccountID);
   }

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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_CREATE);
         return;
      }

      // check for zero-length args
      if (args.length < 1 || args[0] == null || args[0].length() == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_ID_MISSING + StringTable.CMD_USAGE_CREATE);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_CREATE);
         return;
      }

      // don't overwrite an existing account
      if (Account.getAccount(args[0]) != null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_EXISTS + StringTable.CMD_USAGE_CREATE);
         return;
      }

      // don't create an account with the same name as a player
      // unless the account creator is that player
      if (!Config.userInterface.getDisplayName(playerID).equals(args[0]) && Config.userInterface.doesPlayerExist(args[0])) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_IS_PLAYER + StringTable.CMD_USAGE_CREATE);
         return;
      }

      // call corresponding function
      Account.makeAccount(args[0].intern(), playerID);

      // only report success if the account was actually created
      if (Account.getAccount(args[0].intern()) != null)
         Config.userInterface.printToUser(playerID, "Created new account: " + args[0]);
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_DELETE);
         return;
      }

      // check for zero-length args
      if (args.length < 1 || args[0] == null || args[0].length() == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_ID_MISSING);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_DELETE);
         return;
      }

      // call corresponding function
      Account.deleteAccount(args[0].intern(), playerID);
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_GRANT_ACCESS);
         return;
      }

      // command must have the right number of args
      if (args.length != 2) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_GRANT_ACCESS);
         return;
      }

      // validate the given account ID
      // given account ID should not be null
      if (args[1] == null || args[1].length() == 0)
         return;
      // don't overwrite an existing account
      Account account = Account.getAccount(args[1].intern());
      if (account == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_MISSING + StringTable.CMD_USAGE_GRANT_ACCESS);
         return;
      }

      // call corresponding function
      account.grantAccess(playerID, Config.userInterface.getPlayerID(args[0]), args[1].intern());
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_REVOKE_ACCESS);
         return;
      }

      // command must have the right number of args
      if (args.length != 2) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_REVOKE_ACCESS);
         return;
      }

      // validate the given account ID
      // given account ID should not be null
      if (args[1] == null || args[1].length() == 0)
         return;
      // don't overwrite an existing account
      Account account = Account.getAccount(args[1].intern());
      if (account == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_MISSING + StringTable.CMD_USAGE_REVOKE_ACCESS);
         return;
      }

      // call corresponding function
      account.revokeAccess(playerID, Config.userInterface.getPlayerID(args[0]), args[1].intern());
   }

   /**
    * Spends to increase a ware's supply and demand.
    * <p>
    * Expected Formats:<br>
    * &#60;ware_id&#62; [max_price_acceptable] [account_id]<br>
    * yes<br>
    *
    * @param playerID player executing the command
    * @param args     arguments given in the expected format
    */
   public static void research(UUID playerID, String[] args) {
      // check if the industrial research command is enabled
      if (Config.researchCostPerHierarchyLevel == 0.0f) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_RESEARCH_DISABLED);
         return;
      }

      // request should not be null
      if (args == null || args.length == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_RESEARCH);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (args.length >= 2 && (args[1] == null || args[1].length() == 0)) ||
          (args.length >= 3 && (args[2] == null || args[2].length() == 0))) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_RESEARCH);
         return;
      }

      // command must have the right number of args
      if (args.length > 3) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_RESEARCH);
         return;
      }

      // don't allocate memory for holding research proposals unless /research is used
      if (researchProposals == null)
         researchProposals = new HashMap<UUID, ResearchProposal>();

      // set up variables
            ResearchProposal researchProposal = null;
            StringBuilder    consoleOutput;           // for concatenating text to be printed
            String           accountID        = null;
            float            priceAcceptable  = 0.0f;
            float            fee              = 0.0f; // potential brokerage fee
      final float priceFluctSurplusOld;               // ware's current price fluctuation
      final float priceFluctSurplusNew;               // ware's price fluctuation after research
      final float priceFluctScarcityOld;
      final float priceFluctScarcityNew;

      // if two arguments are given,
      // the second must either be a price or an account ID
      if (args.length == 2) {
         try {
            // assume the second argument is a price
            priceAcceptable = Float.parseFloat(args[1]);
         } catch (NumberFormatException e) {
            // if the fifth argument is not a price,
            // it must be an account ID
            accountID = args[1].intern();
         }
      }

      // if three arguments are given,
      // they must be a price and an account ID
      else if (args.length == 3) {
         try {
            priceAcceptable = Float.parseFloat(args[1]);
         } catch (NumberFormatException e) {
            Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_PRICE + StringTable.CMD_USAGE_RESEARCH);
            return;
         }
         accountID = args[2].intern();
      }

      // check if player is accepting a proposal
      if (args[0].equalsIgnoreCase(StringTable.YES)) {
         // grab the old research proposal
         ResearchProposal oldProposal = researchProposals.get(playerID);

         // if there is no proposal, tell the player
         if (oldProposal == null) {
            Config.userInterface.printErrorToUser(playerID, StringTable.MSG_RESEARCH_NO_OFFERS);
            return;
         }

         // if no account ID is specified, use the old offer's account ID
         if (accountID == null || accountID.isEmpty())
            accountID = oldProposal.accountID;

         // generate a current research proposal
         researchProposal = generateResearchProposal(playerID, oldProposal.wareID, accountID);

         // This shouldn't happen, but if there is a problem regenerating an offer, just exit.
         if (researchProposal == null)
            return;

         // if necessary, add a brokerage fee
         // calculate the fee here rather than when generating the proposal
         // since it is unknown how long the proposal has been standing
         if (Config.chargeTransactionFees &&
             Config.transactionFeeResearching != 0.0f) {
            // find fee's charge
            if (Config.transactionFeeResearchingIsMult) {
               fee = researchProposal.price * Config.transactionFeeResearching;

               // if the price and fee percentage have opposite signs, flip the fee's sign
               // so positive rates don't pay out anything and negative rates don't take anything
               if ((fee < 0.0f && Config.transactionFeeResearching > 0.0f) ||
                   (Config.transactionFeeResearching < 0.0f && fee > 0.0f))
                  fee = -fee;
            }
            else
               fee = Config.transactionFeeResearching;

            // if the fee is negative, adjust by how much may be paid
            if (Config.transactionFeeResearching < 0.0f)
               fee += Account.canNegativeFeeBePaid(fee);
         }

         // compare current proposal to old proposal
         // If the current proposal's price is higher than 5% of the old price
         // and no max price acceptable is specified,
         // or if the current proposal's price is higher than the specified max price acceptable,
         // present the new proposal instead of processing it.
         if ((priceAcceptable == 0.0f && researchProposal.price > (oldProposal.price * 1.05f)) ||
             (priceAcceptable != 0.0f && researchProposal.price + fee > priceAcceptable)) {
            consoleOutput = new StringBuilder();
            if (researchProposal.ware.getAlias() != null && !researchProposal.ware.getAlias().isEmpty())
               consoleOutput.append(researchProposal.ware.getAlias());
            else
               consoleOutput.append(researchProposal.wareID);

            priceFluctSurplusOld  = ((Config.priceCeiling - 1.0f) / (Config.quanEquilibrium[researchProposal.ware.getLevel()]      - Config.quanDeficient[researchProposal.ware.getLevel()]));
            priceFluctSurplusNew  = ((Config.priceCeiling - 1.0f) / (Config.quanEquilibrium[researchProposal.ware.getLevel() - 1]  - Config.quanDeficient[researchProposal.ware.getLevel() - 1]));
            priceFluctScarcityOld = ((Config.priceCeiling - 1.0f) / (Config.quanExcessive[researchProposal.ware.getLevel()]        - Config.quanEquilibrium[researchProposal.ware.getLevel()]));
            priceFluctScarcityNew = ((Config.priceCeiling - 1.0f) / (Config.quanExcessive[researchProposal.ware.getLevel() - 1]    - Config.quanEquilibrium[researchProposal.ware.getLevel() - 1]));
            consoleOutput.append(" research price is ").append(PriceFormatter.truncatePrice(researchProposal.price + fee)).append(StringTable.MSG_RESEARCH_USAGE_YES)
                         .append("\n   price fluctuation during surplus: ").append(PriceFormatter.truncatePrice((1.0f - (priceFluctSurplusNew / priceFluctSurplusOld)) * 100.0f))
                         .append("% lower\n   price fluctuation during scarcity: ").append(PriceFormatter.truncatePrice((1.0f - (priceFluctScarcityNew / priceFluctScarcityOld)) * 100.0f)).append("% lower");

            if (researchProposal.ware.getQuantity() < Config.quanEquilibrium[researchProposal.ware.getLevel() - 1])
               consoleOutput.append("\n   quantity available for sale: ").append(researchProposal.ware.getQuantity()).append(" to ").append(Config.quanEquilibrium[researchProposal.ware.getLevel() - 1]);

            Config.userInterface.printToUser(playerID, consoleOutput.toString());
            researchProposals.put(playerID, researchProposal);
            return;
         }
         // The current research proposal is processed later on.
      }      // end of research proposal acceptance if statement
      else { // if an research proposal isn't being accepted
         // create a proposal
         // translates the ware ID, grabs the ware, and calculates the price
         researchProposal = generateResearchProposal(playerID, args[0].intern(), accountID);

         // If there was a problem, a message would have already been printed.
         if (researchProposal == null)
            return;

         // Don't store/save the current proposal here.
         // The offer might be processed,
         // so it might be better not to store it at all.

         // if necessary, add a brokerage fee
         // calculate the fee here rather than when generating the offer
         // since it is unknown how long the offer has been standing
         if (Config.chargeTransactionFees &&
             Config.transactionFeeResearching != 0.0f) {
            // find fee's charge
            if (Config.transactionFeeResearchingIsMult) {
               fee = researchProposal.price * Config.transactionFeeResearching;

               // if the price and fee percentage have opposite signs, flip the fee's sign
               // so positive rates don't pay out anything and negative rates don't take anything
               if ((fee < 0.0f && Config.transactionFeeResearching > 0.0f) ||
                   (Config.transactionFeeResearching < 0.0f && fee > 0.0f))
                  fee = -fee;
            }
            else
               fee = Config.transactionFeeResearching;

            // if the fee is negative, adjust by how much may be paid
            if (Config.transactionFeeResearching < 0.0f)
               fee += Account.canNegativeFeeBePaid(fee);
         }

         // If the max price acceptable is too little, tell the player.
         // Do not worry about printing messages - there is no autoinvesting.
         // If the max price acceptable is unset and research price is $0,
         // don't assume the player wants to invest - they may just be watching the price or curious.
         if (priceAcceptable == 0.0f || researchProposal.price + fee > priceAcceptable) {
            consoleOutput = new StringBuilder();
            if (researchProposal.ware.getAlias() != null && !researchProposal.ware.getAlias().isEmpty())
               consoleOutput.append(researchProposal.ware.getAlias());
            else
               consoleOutput.append(researchProposal.wareID);

            priceFluctSurplusOld  = ((Config.priceCeiling - 1.0f) / (Config.quanEquilibrium[researchProposal.ware.getLevel()]      - Config.quanDeficient[researchProposal.ware.getLevel()]));
            priceFluctSurplusNew  = ((Config.priceCeiling - 1.0f) / (Config.quanEquilibrium[researchProposal.ware.getLevel() - 1]  - Config.quanDeficient[researchProposal.ware.getLevel() - 1]));
            priceFluctScarcityOld = ((Config.priceCeiling - 1.0f) / (Config.quanExcessive[researchProposal.ware.getLevel()]        - Config.quanEquilibrium[researchProposal.ware.getLevel()]));
            priceFluctScarcityNew = ((Config.priceCeiling - 1.0f) / (Config.quanExcessive[researchProposal.ware.getLevel() - 1]    - Config.quanEquilibrium[researchProposal.ware.getLevel() - 1]));
            consoleOutput.append(" research price is ").append(PriceFormatter.truncatePrice(researchProposal.price + fee)).append(StringTable.MSG_RESEARCH_USAGE_YES)
                         .append("\n   price fluctuation during surplus: ").append(PriceFormatter.truncatePrice((1.0f - (priceFluctSurplusNew / priceFluctSurplusOld)) * 100.0f))
                         .append("% lower\n   price fluctuation during scarcity: ").append(PriceFormatter.truncatePrice((1.0f - (priceFluctScarcityNew / priceFluctScarcityOld)) * 100.0f)).append("% lower");

            if (researchProposal.ware.getQuantity() < Config.quanEquilibrium[researchProposal.ware.getLevel() - 1])
               consoleOutput.append("\n   quantity available for sale: ").append(researchProposal.ware.getQuantity()).append(" to ").append(Config.quanEquilibrium[researchProposal.ware.getLevel() - 1]);

            Config.userInterface.printToUser(playerID, consoleOutput.toString());
            researchProposals.put(playerID, researchProposal);
            return;
         }
      }

      // grab the account to be used
      Account account = Account.grabAndCheckAccount(accountID, playerID, researchProposal.price + fee);

      // if something's wrong with the account, stop
      if (account == null)
         return; // an error message has already been sent to the player

      // process the research proposal
      // lower the ware's hierarchy level
      researchProposal.ware.addLevel((byte) -1);

      // if the ware's supply is not higher than its new level's starting level, reset its stock
      if (researchProposal.ware.getQuantity() < Config.startQuanBase[researchProposal.ware.getLevel()])
         researchProposal.ware.setQuantity(Config.startQuanBase[researchProposal.ware.getLevel()]);

      // take the money
      account.subtractMoney(researchProposal.price);

      // print results
      consoleOutput = new StringBuilder(128);
      if (researchProposal.ware.getAlias() != null && !researchProposal.ware.getAlias().isEmpty())
         consoleOutput.append(researchProposal.ware.getAlias());
      else
         consoleOutput.append(researchProposal.wareID);

      priceFluctSurplusOld  = ((Config.priceCeiling - 1.0f) / (Config.quanEquilibrium[researchProposal.ware.getLevel() + 1] - Config.quanDeficient[researchProposal.ware.getLevel() + 1]));
      priceFluctSurplusNew  = ((Config.priceCeiling - 1.0f) / (Config.quanEquilibrium[researchProposal.ware.getLevel()]     - Config.quanDeficient[researchProposal.ware.getLevel()]));
      priceFluctScarcityOld = ((Config.priceCeiling - 1.0f) / (Config.quanExcessive[researchProposal.ware.getLevel() + 1]   - Config.quanEquilibrium[researchProposal.ware.getLevel() + 1]));
      priceFluctScarcityNew = ((Config.priceCeiling - 1.0f) / (Config.quanExcessive[researchProposal.ware.getLevel()]       - Config.quanEquilibrium[researchProposal.ware.getLevel()]));
      consoleOutput.append(StringTable.MSG_RESEARCH_SUCCESS)
                   .append("\n   price fluctuation during surplus: ").append(PriceFormatter.truncatePrice((1.0f - (priceFluctSurplusNew / priceFluctSurplusOld)) * 100.0f))
                   .append("% lower\n   price fluctuation during scarcity: ").append(PriceFormatter.truncatePrice((1.0f - (priceFluctScarcityNew / priceFluctScarcityOld)) * 100.0f)).append("% lower");

      if (Config.quanEquilibrium[researchProposal.ware.getLevel()] == researchProposal.ware.getQuantity())
         consoleOutput.append("\n   new quantity available for sale: ").append(researchProposal.ware.getQuantity());

      Config.userInterface.printToUser(playerID, consoleOutput.toString());

      // pay the transaction fee
      if (Config.chargeTransactionFees &&
          Config.transactionFeeResearching != 0.0f) {
         // check whether a fee collection account should be used
         if (Config.transactionFeesShouldPutFeesIntoAccount)
            // if the fee is negative and unaffordable, don't pay it
            if (Account.depositTransactionFee(fee))
               return;

         // pay the fee
         account.subtractMoney(fee);

         // report fee payment
         Config.userInterface.printToUser(playerID, StringTable.MSG_TRANSACT_FEE + PriceFormatter.PRICE_FORMAT.format(fee));
      }

      // remove any old proposal
      researchProposals.remove(playerID);
   }

   /**
    * Saves accounts and market wares.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   public static void save(String[] args) {
      Marketplace.saveWares();
      Account.saveAccounts();
      Config.userInterface.printToConsole(StringTable.MSG_SAVED_ECONOMY);
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_RELOAD);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 + indexOffset) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_RELOAD);
         return;
      }

      // input argument should not be null
      if (args[indexOffset] == null || args[indexOffset].length() == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_RELOAD_MISSING + StringTable.CMD_USAGE_RELOAD);
         return;
      }

      // call corresponding function or report error
      if (args[indexOffset].equals(StringTable.RELOAD_CONFIG)) {
         Config.loadConfig();
         Config.userInterface.printToUser(playerID, StringTable.MSG_RELOAD_CONFIG);
      }
      else if (args[indexOffset].equals(StringTable.RELOAD_WARES)) {
         Marketplace.loadWares();
         Config.userInterface.printToUser(playerID, StringTable.MSG_RELOAD_WARES);
      }
      else if (args[indexOffset].equals(StringTable.RELOAD_ACCOUNTS)) {
         Account.loadAccounts();
         Config.userInterface.printToUser(playerID, StringTable.MSG_RELOAD_ACCOUNTS);
      }
      else if (args[indexOffset].equals(StringTable.ALL)) {
         Config.loadConfig();
         Marketplace.loadWares();
         Account.loadAccounts();
         Config.userInterface.printToUser(playerID, StringTable.MSG_RELOAD_ALL);
      }
      else {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ARG + StringTable.CMD_USAGE_RELOAD);
      }
   }

   /**
    * Summons money.<br>
    * Expected Format: &#60;quantity&#62; [account_id]
    *
    * @param playerID    player executing the command
    * @param args        arguments given in the expected format
    */
   public static void add(final UUID playerID, final String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_ADD);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 &&
          args.length != 2) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_ADD);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (args.length == 2 && (args[1] == null || args[1].length() == 0))) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_ADD);
         return;
      }

      // set up variables
      float quantity = 0.0f;

      // ensure passed quantity is a valid type
      try {
         quantity = Float.parseFloat(args[0]);
      } catch (NumberFormatException e) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_ADD);
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
         account = Account.grabPlayerAccount(args[1], args[1], null);
         if (account == null) {
            Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_MISSING + StringTable.CMD_USAGE_ADD);
            return;
         }

         // summon money for the account
         account.addMoney(quantity);

         // tell players about the money
         Config.userInterface.printToUser(playerID, "Gave " + PriceFormatter.PRICE_FORMAT.format(quantity) + " to " + args[1]);
         Config.userInterface.printToUser(account.getOwner(), "Received " + PriceFormatter.PRICE_FORMAT.format(quantity));
      }
      // if no account ID is given,
      // use the player's personal account
      else {
         // grab personal or default account
         account = Account.grabPlayerAccount(null, null, playerID);

         // summon money for the account
         account.addMoney(quantity);

         // tell the player about the money
         Config.userInterface.printToUser(playerID, "Received " + PriceFormatter.PRICE_FORMAT.format(quantity));
      }
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_SET);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 + indexOffset &&
          args.length != 2 + indexOffset) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_SET);
         return;
      }

      // check for zero-length args
      if (args[indexOffset] == null || args[indexOffset].length() == 0 ||
          (args.length == 2 + indexOffset && (args[1 + indexOffset] == null || args[1 + indexOffset].length() == 0))) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_SET);
         return;
      }

      // set up variables
      float quantity = 0.0f;

      // ensure passed quantity is a valid type
      try {
         quantity = Float.parseFloat(args[indexOffset]);
      } catch (NumberFormatException e) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_SET);
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
            Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_MISSING + StringTable.CMD_USAGE_SET);
            return;
         }

         // set account's money
         account.setMoney(quantity);

         // tell players about the money
         Config.userInterface.printToUser(playerID, "Set " + args[1 + indexOffset] + " funds to " + PriceFormatter.PRICE_FORMAT.format(quantity));
         if (!playerID.equals(account.getOwner()))
            Config.userInterface.printToUser(account.getOwner(), args[1 + indexOffset] + " funds to " + PriceFormatter.PRICE_FORMAT.format(quantity));
      }
      // if no account ID is given,
      // use the player's personal account
      else {
         String playername = Config.userInterface.getDisplayName(playerID);

         // if the player doesn't have a personal account, make one
         account = Account.getAccount(playername);
         if (account == null)
            account = Account.makeAccount(playername, playerID);

         // set account's money
         account.setMoney(quantity);

         // tell the player about the money
         Config.userInterface.printToUser(playerID, "Personal funds set to " + PriceFormatter.PRICE_FORMAT.format(quantity));
      }
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // command must have the right number of args
      if (args.length != 2) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          args[1] == null || args[1].length() == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // set up variables
      int  quantity = 2147483647;
      Ware ware     = null;

      // grab the ware to be used
      ware = Marketplace.translateAndGrab(args[0].intern());
      // if ware is not in the market, stop
      if (ware == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_MISSING + args[0]);
         return;
      }

      // find where to set stock to
      if (args[1].equalsIgnoreCase(StringTable.CHANGE_STOCK_EQUILIBRIUM))
         quantity = Config.quanEquilibrium[ware.getLevel()];
      else if (args[1].equalsIgnoreCase(StringTable.CHANGE_STOCK_OVERSTOCKED))
         quantity = Config.quanExcessive[ware.getLevel()];
      else if (args[1].equalsIgnoreCase(StringTable.CHANGE_STOCK_UNDERSTOCKED))
         quantity = Config.quanDeficient[ware.getLevel()];
      else {
         try {
            quantity = Integer.parseInt(args[1]) + ware.getQuantity();
         } catch (NumberFormatException e) {
            // error message printed later
         }
      }
      if (quantity == 2147483647) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_QUANTITY + StringTable.CMD_USAGE_CHANGE_STOCK);
         return;
      }

      // set the ware's stock
      ware.setQuantity(quantity);

      // report success
      Config.userInterface.printToUser(playerID, ware.getWareID() + "'s stock is now " + Integer.toString(ware.getQuantity()));
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
         Config.userInterface.printErrorToUser(playerID, StringTable.CMD_USAGE_SET_DEFAULT_ACCOUNT);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_NUM_ARGS + StringTable.CMD_USAGE_SET_DEFAULT_ACCOUNT);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ZERO_LEN_ARGS + StringTable.CMD_USAGE_SET_DEFAULT_ACCOUNT);
         return;
      }

      // try to set the account
      if (Account.setDefaultAccount(playerID, args[0].intern())) {
         // report success
         Config.userInterface.printToUser(playerID, args[0] + " will now be used in place of your personal account");
      }
      // if it didn't work, an error has already been printed
   }

   /**
    * Finds whether a ware's supply and demand may be increased and, if so, the price of doing so.
    * <p>
    * Complexity: O(1)
    * @param playerID  player executing the command
    * @param wareID    unique identifier of ware to be used
    * @param accountID account to be charged when the research is performed
    * @return complete offer and associated information for some research
    */
   private static ResearchProposal generateResearchProposal(UUID playerID, String wareID, String accountID) {
      // grab the ware to be used
      Ware ware = Marketplace.translateAndGrab(wareID);
      // if ware is not in the market, stop
      if (ware == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_MISSING + wareID);
         return null;
      }
      wareID = ware.getWareID();

      final boolean HAS_ALIAS = (ware.getAlias() != null && !ware.getAlias().isEmpty());

      // verify that the ware is suitable for research
      if (ware.getLevel() == 0) {
         if (HAS_ALIAS)
            Config.userInterface.printErrorToUser(playerID, ware.getAlias() + StringTable.MSG_RESEARCH_LOWEST_LEVEL);
         else
            Config.userInterface.printErrorToUser(playerID, wareID + StringTable.MSG_RESEARCH_LOWEST_LEVEL);

         return null;
      }

      if (ware instanceof WareUntradeable) {
         if (HAS_ALIAS)
            Config.userInterface.printErrorToUser(playerID, ware.getAlias() + StringTable.MSG_BUY_UNTRADEABLE);
         else
            Config.userInterface.printErrorToUser(playerID, wareID + StringTable.MSG_BUY_UNTRADEABLE);

         return null;
      }

      if (ware instanceof WareLinked) {
         if (HAS_ALIAS)
            Config.userInterface.printErrorToUser(playerID, ware.getAlias() + StringTable.MSG_RESEARCH_LINKED);
         else
            Config.userInterface.printErrorToUser(playerID, wareID + StringTable.MSG_RESEARCH_LINKED);

         return null;
      }

      if (ware.getQuantity() >= Config.quanExcessive[ware.getLevel()]) {
         if (HAS_ALIAS)
            Config.userInterface.printErrorToUser(playerID, ware.getAlias() + StringTable.MSG_RESEARCH_QUAN_HIGH);
         else
            Config.userInterface.printErrorToUser(playerID, wareID + StringTable.MSG_RESEARCH_QUAN_HIGH);

         return null;
      }

      // find research cost
      float priceResearch = Marketplace.getPrice(playerID, ware, 1, Marketplace.PriceType.CURRENT_BUY) * ware.getLevel() * Config.researchCostPerHierarchyLevel;
      if (Config.researchCostIsAMultOfAvgPrice)
         priceResearch *= Marketplace.getCurrentPriceAverage();

      // truncate the price to avoid rounding and multiplication errors
      priceResearch = PriceFormatter.truncatePrice(priceResearch);

      // if research price is 0, the ware cannot be researched
      if (priceResearch == 0.0f) {
         if (HAS_ALIAS)
            Config.userInterface.printErrorToUser(playerID, ware.getAlias() + StringTable.MSG_RESEARCH_FAILED);
         else
            Config.userInterface.printErrorToUser(playerID, wareID + StringTable.MSG_RESEARCH_FAILED);

         return null;
      }

      // generate struct
      return new ResearchProposal(ware, wareID, accountID, priceResearch);
   }

   /**
    * Interprets a price multiplier argument from a user.
    * If an error occurs, prints a message and returns Float.NaN.
    * <p>
    * Complexity: O(1)
    * @param playerID  user responsible for the trading
    * @param arg       passed parameter assumed to contain a transaction price multiplier
    * @param isTrading <code>true</code> if buying or selling
    *                  <code>false</code> if checking prices or otherwise not affecting wares
    * @return transaction price multiplier to be used or Float.NaN to signal error
    */
   public static float parsePricePercentArgument(UUID playerID, String arg, boolean isTrading) {
      // remove keyword, if present
      if (arg.startsWith(StringTable.PRICE_PERCENT))
         arg = arg.substring(StringTable.PRICE_PERCENT.length()); // remove identifying character(s)

      // check user permissions
      if ((isTrading && Config.userInterface.isAnOp(playerID)) || // buying/selling requires permission to change prices
          !isTrading) {                                              // checking doesn't require any permissions
         // attempt to parse user input
         try {
            return Float.parseFloat(arg);
         }
         catch (Exception e) {
            Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_PRICE_ADJUST_INVALID + arg);
            return Float.NaN;
         }
      }

      // invalid permissions
      else {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_PRICE_ADJUST_NO_PERM);
         return Float.NaN;
      }
   }
}