package commandeconomy;

import java.util.Scanner;              // for reading from the console
import java.util.HashMap;              // for storing wares in the user's inventory
import java.util.LinkedList;           // for returning properties of wares found in an inventory and tracking server administrators
import java.util.Arrays;               // for removing the first element of user input before passing it to service request functions
import java.util.UUID;                 // for more securely tracking users internally
import java.util.Timer;                // for autosaving
import java.util.TimerTask;
import java.lang.StringBuilder;        // for faster output concatenation for /help

/**
 * Contains functions for interacting with a terminal
 * which would not be used to access chat commands within Minecraft.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-23
 */
public class InterfaceTerminal implements InterfaceCommand
{
   // GLOBAL VARIABLES
   /** username of the player whose request is being handled */
   protected static String playername = "John_Doe";
   /** list of server administrators, mimicking Minecraft Forge's OppedPlayerNames string array */
   protected static LinkedList<UUID> ops = new LinkedList<UUID>();
   /** maps UUIDs to player names */
   private static HashMap<UUID, String> uuidToNames = new HashMap<UUID, String>();

   /** maximum inventory capacity */
   protected static int inventorySpace = 36;
   /** terminal user's personal inventory */
   protected static HashMap<String, Integer> inventory = new HashMap<String, Integer>();
   /** northward inventory */
   protected static HashMap<String, Integer> inventoryNorth = new HashMap<String, Integer>();
   /** eastward inventory */
   protected static HashMap<String, Integer> inventoryEast = new HashMap<String, Integer>();
   /** westward inventory */
   protected static HashMap<String, Integer> inventoryWest = new HashMap<String, Integer>();
   /** southward inventory */
   protected static HashMap<String, Integer> inventorySouth = new HashMap<String, Integer>();
   /** upward inventory */
   protected static HashMap<String, Integer> inventoryUp = new HashMap<String, Integer>();
   /** downward inventory */
   protected static HashMap<String, Integer> inventoryDown = new HashMap<String, Integer>();

   /** speeds up concatenation for /help */
   private static StringBuilder sbHelpOutput   = new StringBuilder(2200);
   /** whether help output currently includes /invest */
   private static boolean sbHelpContainsInvest = false;

   // autosaving
   /** for automatically and periodically saving wares and accounts */
   private static Timer timerAutosaver = null;
   /** for stopping the autosave thread gracefully */
   private static Autosaver timertaskAutosaver = null;

   // FUNCTIONS
   /**
    * Main function for initializing the market.
    *
    * @param args unused
    */
   public static void main(String[] args) {
      // connect desired interface to the market
      Config.commandInterface = new InterfaceTerminal();

      // set up and run the market
      CommandEconomy.start(null);
      return;
   }

   /**
    * Returns the path to the local game's save directory.
    *
    * @return directory of local save and config files
    */
   public String getSaveDirectory() { return "saves"; }

   /**
    * Returns how many more stacks of wares the given inventory may hold.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return number of free slots in the given inventory
    */
   public int getInventorySpaceAvailable(UUID playerID,
      InterfaceCommand.Coordinates coordinates) {
      // grab the right inventory
      HashMap<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventory(playerID, coordinates);

         // if no inventory was found
         if (inventoryToUse == null)
            return -1;
      }
      else
         inventoryToUse = inventory;

      // return space available
      if (inventorySpace - inventoryToUse.size() < 0)
         return 0;
      else
         return inventorySpace - inventoryToUse.size();
   }

   /**
    * Gives a specified quantity of a ware ID to a user.
    * If there is no space for the ware, does nothing.
    *
    * @param wareID      unique ID used to refer to the ware
    * @param quantity    how much to give the user
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    */
   public void addToInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID, int quantity) {
      // if no items should be added, do nothing
      if (quantity <= 0)
         return;

      // grab the right inventory
      HashMap<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventory(playerID, coordinates);

         // if no inventory was found
         if (inventoryToUse == null)
            return;
      }
      else
         inventoryToUse = inventory;

      // check available inventory space before adding an item
      if (inventorySpace - inventoryToUse.size() > 0)
         inventoryToUse.put(wareID, inventoryToUse.getOrDefault(wareID, 0) + quantity);
   }

   /**
    * Takes a specified quantity of a ware ID from a user.
    *
    * @param wareID      unique ID used to refer to the ware
    * @param quantity    how much to take from the user
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    */
   public void removeFromInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID, int quantity) {
      // if no items should be removed, do nothing
      if (quantity <= 0)
         return;

      // grab the right inventory
      HashMap<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventory(playerID, coordinates);

         // if no inventory was found
         if (inventoryToUse == null)
            return;
      }
      else
         inventoryToUse = inventory;

      // check availability before removing
      if (inventoryToUse.containsKey(wareID)) {
         // if the amount to remove is greater than the amount available,
         // remove everything
         if (inventoryToUse.get(wareID) < quantity)
            inventoryToUse.remove(wareID);
         else
            inventoryToUse.put(wareID, inventoryToUse.get(wareID) - quantity);
      }
   }

   /**
    * Returns the quantities and corresponding qualities of
    * wares with the given id the user has.
    * The list is ordered by their position within the user's inventory.
    *
    * @param wareID      unique ID used to refer to the ware
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return quantities and qualities of wares found
    */
   public LinkedList<Marketplace.Stock> checkInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID) {
      // prepare a container for the wares
      LinkedList<Marketplace.Stock> waresFound = new LinkedList<Marketplace.Stock>();

      // grab the right inventory
      HashMap<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventory(playerID, coordinates);

         // if no inventory was found
         if (inventoryToUse == null) {
            waresFound.add(new Marketplace.Stock(wareID, -1, 1.0f));
            return waresFound;
         }
      }
      else
         inventoryToUse = inventory;

      // if the ware is in the inventory, grab it
      if (inventoryToUse.containsKey(wareID) &&
          inventoryToUse.get(wareID) > 0)
         waresFound.add(new Marketplace.Stock(wareID, inventoryToUse.get(wareID), 1.0f));

         return waresFound;
   }

   /**
    * Returns the inventory which should be used.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated
    */
   public static HashMap<String, Integer> getInventory(UUID playerID,
      InterfaceCommand.Coordinates coordinates) {
      // if no coordinates are given, use the user's personal inventory
      if (coordinates == null)
         return inventory;

      // use the personal inventory
      if (coordinates.x ==  0 &&
          coordinates.y ==  0 &&
          coordinates.z ==  0)
         return inventory;

      // use the downward inventory
      if (coordinates.x ==  0 &&
          coordinates.y == -1 &&
          coordinates.z ==  0)
         return inventoryDown;

      // use the upward inventory
      if (coordinates.x ==  0 &&
          coordinates.y ==  1 &&
          coordinates.z ==  0)
         return inventoryUp;

      // use the northward inventory
      if (coordinates.x ==  0 &&
          coordinates.y ==  0 &&
          coordinates.z == -1)
         return inventoryNorth;

      // use the eastward inventory
      if (coordinates.x == -1 &&
          coordinates.y ==  0 &&
          coordinates.z ==  0)
         return inventoryEast;

      // use the westward inventory
      if (coordinates.x ==  1 &&
          coordinates.y ==  0 &&
          coordinates.z ==  0)
         return inventoryWest;

      // use the southward inventory
      if (coordinates.x ==  0 &&
          coordinates.y ==  0 &&
          coordinates.z ==  1)
         return inventorySouth;

      // if the inventory hasn't been found,
      // return null to signal an invalid coordinate
      return null;
   }

   /**
    * Returns the inventory which should be used.
    *
    * @param playerID  user responsible for the trading
    * @param direction where the inventory may be found
    * @return inventory to be manipulated
    */
   public static HashMap<String, Integer> getInventory(UUID playerID,
      String direction) {
      switch(direction)
      {
         case CommandEconomy.INVENTORY_NONE:  return inventory;
         case CommandEconomy.INVENTORY_DOWN:  return inventoryDown;
         case CommandEconomy.INVENTORY_UP:    return inventoryUp;
         case CommandEconomy.INVENTORY_NORTH: return inventoryNorth;
         case CommandEconomy.INVENTORY_EAST:  return inventoryEast;
         case CommandEconomy.INVENTORY_WEST:  return inventoryWest;
         case CommandEconomy.INVENTORY_SOUTH: return inventorySouth;
         default:      return null;
      }
   }

   /**
    * Returns the player name associated with the given UUID.
    *
    * @param playerID UUID of whose name should be found
    * @return player name corresponding to given UUID
    */
   public String getDisplayName(UUID playerID) {
      return getDisplayNameStatic(playerID);
   }

   /**
    * Returns the player name associated with the given UUID.
    * Used to make getDisplayName() part of an interface,
    * but also usable in static or Minecraft's command bases' methods.
    *
    * @param playerID UUID of whose name should be found
    * @return player name corresponding to given UUID
    */
   public static String getDisplayNameStatic(UUID playerID) {
      if (playerID == null)
         return "";

      return uuidToNames.get(playerID);
   }

   /**
    * Returns the UUID associated with the given player name.
    *
    * @param playername player name corresponding UUID
    * @return player UUID corresponding to given player name
    */
   public UUID getPlayerID(String playername) {
      return getPlayerIDStatic(playername);
   }

   /**
    * Returns the UUID associated with the given player name.
    * Used to make getPlayerID() part of an interface,
    * but also usable in static or Minecraft's command bases' methods.
    *
    * @param playername player name corresponding UUID
    * @return player UUID corresponding to given player name
    */
   public static UUID getPlayerIDStatic(String playername) {
      if (playername == null || playername.isEmpty())
         return null;

      uuidToNames.put(UUID.nameUUIDFromBytes(playername.getBytes()), playername);
      return UUID.nameUUIDFromBytes(playername.getBytes());
   }

   /**
    * Returns whether a player with the given unique identifier is currently logged into the server.
    * <p>
    * Complexity: O(1)
    * @param playerID UUID of player whose current status is needed
    * @return <code>true</code> if the player is currently online
    */
   public boolean isPlayerOnline(UUID playerID) {
      return getDisplayNameStatic(playerID).equals(playername);
   }

   /**
    * Returns whether the given string matches a player's name.
    *
    * @param playername player to check the existence of
    * @return whether the given string is in use as a player's name
    */
   public boolean doesPlayerExist(String playername) {
      return doesPlayerExistStatic(playername);
   }

   /**
    * Returns whether the given string matches a player's name.
    * Used to make doesPlayerExist() part of an interface,
    * but also usable in static or Minecraft's command bases' methods.
    *
    * @param playername player to check the existence of
    * @return whether the given string is in use as a player's name
    */
   protected static boolean doesPlayerExistStatic(String playername) {
      if (playername != null && playername.equals(InterfaceTerminal.playername))
         return true;
      else
         return false; // don't assume any other players exist
   }

   /**
    * Returns whether or not a given Forge OreDictionary name
    * exists outside of the market.
    *
    * @param name the Forge OreDictionary name
    * @return true if the name exists outside of the market
    */
   public boolean doesOreDictionaryNameExist(String name) {
      return true; // all names may be used within the terminal
   }

   /**
    * Returns whether or not a given ware exists outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return true if the ware exists outside of the market
    */
   public boolean doesWareExist(String wareID) {
      return true; // all wares may be placed within the terminal inventory
   }

   /**
    * Returns how many a stack of the ware may hold outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return the maximum amount a single stack may hold
    */
   public int getStackSize(String wareID) {
      return 64;
   }

   /**
    * Forwards a message to the specified user.
    * <p>
    * Complexity: O(n^2)
    * @param playerID who to give the message to
    * @param message what to tell the user
    */
   public void printToUser(UUID playerID, String message) {
      if (message  == null || message.isEmpty() ||
          playerID == null)
         return;

      if (!getDisplayNameStatic(playerID).equals(playername))
         System.out.println("(for " + getDisplayNameStatic(playerID) + ") " + message);
      else
         System.out.println(message);
   }

   /**
    * Forwards a message to all users.
    *
    * @param message what to tell the users
    */
   public void printToAllUsers(String message) {
      if (message  == null || message.isEmpty())
         return;

      System.out.println(message);
   }

   /**
    * Forwards an error message to the specified user.
    * <p>
    * Complexity: O(n^2)
    * @param playerID who to give the message to
    * @param message what to tell the user
    */
   public void printErrorToUser(UUID playerID, String message) {
      if (message  == null || message.isEmpty() ||
          playerID == null)
         return;

      if (!getDisplayNameStatic(playerID).equals(playername))
         System.out.println("(for " + getDisplayNameStatic(playerID) + ") " + message);
      else
         System.out.println(message);
   }

   /**
    * Handles the contents for an error message normal users shouldn't necessarily see.
    * <p>
    * Complexity: O(n^2)
    * @param message error encountered and possible details
    */
   public void printToConsole(String message) {
      if (message == null || message.isEmpty())
         return;

      System.err.println(message);
   }

   /**
    * Returns whether the player is a server administrator.
    *
    * @param playerID player whose server operator permissions should be checked
    * @return whether the player is an op
    */
   public boolean isAnOp(UUID playerID) {
      if (playerID == null)
         return false;

      return ops.contains(playerID);
   }

   /**
    * Calls other functions to fulfill commands given through the terminal.
    * <p>
    * Each possible command is separated into its own function to better organize
    * and modularize code. As an example emphasizing modularity, with each command
    * separated into its own function, the command-running interface may easily be switched
    * between a terminal and Minecraft's chat by changing the function servicing requests
    * rather than each command's code.
    */
   public void serviceRequests() {
      // prepare to service requests
      Scanner consoleInput = new Scanner(System.in);
      String[] userInput;   // holds request being parsed

      // make the current player an op 
      ops.add(getPlayerIDStatic(playername));

      // if necessary, set up autosaving
      if (!Config.disableAutoSaving && timerAutosaver == null) {
         // initialize timer objects
         timerAutosaver     = new Timer(true);
         timertaskAutosaver = new Autosaver();

         // initialize periodically saving the marketplace
         timerAutosaver.scheduleAtFixedRate(timertaskAutosaver, (long) 0, (long) 300000); // 60000 milliseconds per minute, 300000 ms per 5 min
      }

      // welcome the player
      System.out.println("\nWelcome to Command Economy!\n\nRecommended commands:\n/add # - provides free money\n/buy <ware ID> # - purchases a ware\n/sell <ware ID> # - sells a ware\n/inventory - checks owned property\n/help - prints available commands\n\nTo obtain valid wares, use /printMarket \nand look in \"config\\CommandEconomy\\market.txt\"\nor type in Minecraft item names\n(ex: wheat, apple, iron_sword, diamond).\n\nWaiting for commands....\n");

      // loop to service requests
      while (true) {
         // wait for request
         System.out.print("> ");

         // split request into separate arguments
         userInput = consoleInput.nextLine().split(" ", 0);

         // request should not be null
         if (userInput[0] == null || userInput[0].isEmpty())
            continue;

         // if the command starts with a forward slash, remove it
         if (userInput[0].startsWith("/"))
            userInput[0] = userInput[0].substring(1);

         // if the command starts with the mod's ID, skip the mod ID
         if (userInput[0].startsWith("commandeconomy"))
            userInput[0] = userInput[1];

         // parse request parameters and pass them to the right function
         switch(userInput[0].toLowerCase()) 
         {
            // When calling on service functions,
            // don't send the first element of the user's input.
            // Minecraft sends the second element and onwards.
            // Making test code better reflect final code will help
            // reduce error and simplify code.

            case CommandEconomy.CMD_HELP:
               serviceRequestHelp(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_BUY:
               serviceRequestBuy(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_SELL:
               serviceRequestSell(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_CHECK:
               serviceRequestCheck(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_SELLALL_LOWER:
               serviceRequestSellAll(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_MONEY:
               serviceRequestMoney(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_SEND:
               serviceRequestSend(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_CREATE:
               serviceRequestCreate(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_DELETE:
               serviceRequestDelete(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_GRANT_ACCESS_LOWER:
               serviceRequestGrantAccess(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_REVOKE_ACCESS_LOWER:
               serviceRequestRevokeAccess(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_INVEST:
               serviceRequestInvest(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_VERSION:
               serviceRequestVersion(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case "save":
            case CommandEconomy.CMD_SAVECE:
               serviceRequestSave(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case "stop":
            case "exit":
               System.out.print("Save before quitting? (Y/N)\n> ");
               if (consoleInput.nextLine().toLowerCase().startsWith("y"))
                  serviceRequestSave(null);

               System.out.println("Shutting down....");

               // if necessary, stop autosaving
               if (timerAutosaver != null) {
                  timerAutosaver.cancel();
                  timerAutosaver = null;
               }

               // end any threads needed by features
               Marketplace.endPeriodicEvents();
               Account.endPeriodicEvents();
               return;

            case CommandEconomy.CMD_RELOAD:
               serviceRequestReload(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_ADD:
               serviceRequestAdd(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_SET:
               serviceRequestSet(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_CHANGE_STOCK_LOWER:
               serviceRequestChangeStock(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_SET_DEFAULT_ACCOUNT_LOWER:
               serviceRequestSetDefaultAccount(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case CommandEconomy.CMD_PRINT_MARKET_LOWER:
               serviceRequestPrintMarket(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case PlatformStrings.CMD_OP:
               serviceRequestOp(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case PlatformStrings.CMD_DEOP:
               serviceRequestDeop(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case PlatformStrings.CMD_INVENTORY:
               serviceRequestInventory(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case PlatformStrings.CMD_GIVE:
               serviceRequestGive(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case PlatformStrings.CMD_TAKE:
               serviceRequestTake(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            case PlatformStrings.CMD_CHANGE_NAME_LOWER:
               serviceRequestChangeName(Arrays.copyOfRange(userInput, 1, userInput.length));
               break;

            default:
               System.out.println(CommandEconomy.ERROR_INVALID_CMD);
               break;
         }
      }
   }

   /**
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID the player being affected by the issued command or the entity being acted upon
    * @param senderID name of the command-issuing entity or the entity acting upon other
    * @param isOpCommand whether the sender must be an admin to execute even if the command only affects themself
    * @return true if the sender has permission to execute the command
    */
   public static boolean permissionToExecute(UUID playerID, UUID senderID, boolean isOpCommand) {
      if (playerID == null || senderID == null)
         return false;

      // command blocks and the server console always have permission
      // but only Minecraft has them

      // check if the sender is only affecting themself
      if (!isOpCommand && playerID.equals(senderID))
         return true;

      // check for sender among server operators
      if (ops.contains(senderID))
         return true;

      // if the sender is not a server operator,
      // they may not execute commands for other players
      return false;
   }

   /**
    * Prints possible commands.
    * <p>
    * Expected Formats:
    * [null || empty]<br>
    * command_block
    * <p>
    * Complexity: O(1)
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestHelp(String[] args) {
      if (args != null && args.length > 0 &&
          args[0] != null &&
          args[0].equals(CommandEconomy.HELP_COMMAND_BLOCK)) {
         System.out.println(
            CommandEconomy.CMD_USAGE_BLOCK_BUY + CommandEconomy.CMD_DESC_BUY +
            CommandEconomy.CMD_USAGE_BLOCK_SELL + CommandEconomy.CMD_DESC_SELL +
            CommandEconomy.CMD_USAGE_BLOCK_CHECK + CommandEconomy.CMD_DESC_CHECK +
            CommandEconomy.CMD_USAGE_BLOCK_SELLALL + CommandEconomy.CMD_DESC_SELLALL +
            CommandEconomy.CMD_USAGE_BLOCK_MONEY + CommandEconomy.CMD_DESC_MONEY +
            CommandEconomy.CMD_USAGE_BLOCK_SEND + CommandEconomy.CMD_DESC_SEND +
            "inventory_direction is none, down, up, north, east, west, or south" + System.lineSeparator()
         );
      } else {
         // in necessary, regenerate help output
         if (sbHelpOutput.length() == 0 ||
             sbHelpContainsInvest != (Config.investmentCostPerHierarchyLevel != 0.0f)) {
            // clear buffer
            sbHelpOutput.setLength(0);

            // add in standard commands
            sbHelpOutput.append(CommandEconomy.CMD_USAGE_BUY).append(CommandEconomy.CMD_DESC_BUY)
                        .append(CommandEconomy.CMD_USAGE_SELL).append(CommandEconomy.CMD_DESC_SELL)
                        .append(CommandEconomy.CMD_USAGE_CHECK).append(CommandEconomy.CMD_DESC_CHECK)
                        .append(CommandEconomy.CMD_USAGE_SELLALL).append(CommandEconomy.CMD_DESC_SELLALL)
                        .append(CommandEconomy.CMD_USAGE_MONEY).append(CommandEconomy.CMD_DESC_MONEY)
                        .append(CommandEconomy.CMD_USAGE_SEND).append(CommandEconomy.CMD_DESC_SEND)
                        .append(CommandEconomy.CMD_USAGE_CREATE).append(CommandEconomy.CMD_DESC_CREATE)
                        .append(CommandEconomy.CMD_USAGE_DELETE).append(CommandEconomy.CMD_DESC_DELETE)
                        .append(CommandEconomy.CMD_USAGE_GRANT_ACCESS).append(CommandEconomy.CMD_DESC_GRANT_ACCESS)
                        .append(CommandEconomy.CMD_USAGE_REVOKE_ACCESS).append(CommandEconomy.CMD_DESC_REVOKE_ACCESS);

            // if needed, add in /invest
            if (Config.investmentCostPerHierarchyLevel != 0.0f) {
               sbHelpContainsInvest = true;
               sbHelpOutput.append(CommandEconomy.CMD_USAGE_INVEST).append(CommandEconomy.CMD_DESC_INVEST);
            }
            else
               sbHelpContainsInvest = false;

            // add in rest of standard commands
            sbHelpOutput.append(CommandEconomy.CMD_USAGE_VERSION).append(CommandEconomy.CMD_DESC_VERSION)
                        .append(CommandEconomy.CMD_USAGE_ADD).append(CommandEconomy.CMD_DESC_ADD)
                        .append(CommandEconomy.CMD_USAGE_SET).append(CommandEconomy.CMD_DESC_SET)
                        .append(CommandEconomy.CMD_USAGE_CHANGE_STOCK).append(CommandEconomy.CMD_DESC_CHANGE_STOCK)
                        .append(CommandEconomy.CMD_USAGE_SET_DEFAULT_ACCOUNT).append(CommandEconomy.CMD_DESC_SET_DEFAULT_ACCOUNT)
                        .append(CommandEconomy.CMD_USAGE_SAVECE).append(CommandEconomy.CMD_DESC_SAVECE)
                        .append(CommandEconomy.CMD_USAGE_RELOAD).append(CommandEconomy.CMD_DESC_RELOAD)
                        .append(CommandEconomy.CMD_USAGE_PRINT_MARKET).append(CommandEconomy.CMD_DESC_PRINT_MARKET)
                        .append(PlatformStrings.CMD_USAGE_OP).append(PlatformStrings.CMD_DESC_OP)
                        .append(PlatformStrings.CMD_USAGE_DEOP).append(PlatformStrings.CMD_DESC_DEOP)
                        .append(PlatformStrings.CMD_USAGE_INVENTORY).append(PlatformStrings.CMD_DESC_INVENTORY)
                        .append(PlatformStrings.CMD_USAGE_GIVE).append(PlatformStrings.CMD_DESC_GIVE)
                        .append(PlatformStrings.CMD_USAGE_TAKE).append(PlatformStrings.CMD_DESC_TAKE)
                        .append(PlatformStrings.CMD_USAGE_CHANGE_NAME).append(PlatformStrings.CMD_DESC_CHANGE_NAME)
                        .append("/stop || exit - shutdowns the market").append(System.lineSeparator());
         }

         System.out.println(sbHelpOutput.toString());
      }

      return;
   }

   /**
    * Purchases a ware.
    * <p>
    * Expected Formats:<br>
    * &#60;ware_id&#62; &#60;quantity&#62; [max_unit_price] [account_id] [&amp;craft]<br>
    * &#60;player_name&#62; &#60;inventory_direction&#62; &#60;ware_id&#62; &#60;quantity&#62; [max_unit_price] [account_id] [&amp;craft]<br>
    * &#60;inventory_direction&#62; is none, east, west, south, up, or down
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestBuy(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(CommandEconomy.CMD_USAGE_BUY);
         return;
      }

      // set up variables
      String  username          = null;
      InterfaceCommand.Coordinates coordinates = null;
      String  accountID         = null;
      String  wareID            = null;
      int     baseArgsLength    = args.length; // number of args, not counting special keywords
      int     quantity          = 0;
      float   priceUnit         = 0.0f;
      float   pricePercent      = 1.0f;
      boolean shouldManufacture = false;       // whether or not to factor in manufacturing for purchases

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BUY);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(CommandEconomy.ARG_SPECIAL_PREFIX) && !arg.startsWith(CommandEconomy.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(CommandEconomy.PRICE_PERCENT)) {
            pricePercent = CommandProcessor.parsePricePercentArgument(getPlayerIDStatic(playername), arg, true);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }

         // check whether user specifies manufacturing the ware
         if (arg.equals(CommandEconomy.MANUFACTURING))
            shouldManufacture = true;
      }

      // command must have the right number of args
      if (baseArgsLength < 2 ||
          baseArgsLength > 6) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_BUY);
         return;
      }

      // if the second argument is a direction, a username and a direction should be given
      // otherwise, the second argument should be a number and, no username or direction should be given
      if (args[1].equals(CommandEconomy.INVENTORY_NONE) ||
          args[1].equals(CommandEconomy.INVENTORY_DOWN) ||
          args[1].equals(CommandEconomy.INVENTORY_UP) ||
          args[1].equals(CommandEconomy.INVENTORY_NORTH) ||
          args[1].equals(CommandEconomy.INVENTORY_EAST) ||
          args[1].equals(CommandEconomy.INVENTORY_WEST) ||
          args[1].equals(CommandEconomy.INVENTORY_SOUTH)) {
         // ensure passed args are valid types
         try {
            quantity = Integer.parseInt(args[3]);
         } catch (NumberFormatException e) {
            System.out.println(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BLOCK_BUY);
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
               System.out.println(CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BLOCK_BUY);
               return;
            }
            accountID = args[5];
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[2];

         // translate coordinates
         switch(args[1])
         {
            // x-axis: west  = +x, east  = -x
            // y-axis: up    = +y, down  = -y
            // z-axis: south = +z, north = -z

            case CommandEconomy.INVENTORY_NONE:
               coordinates = new Coordinates(0, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_DOWN:
               coordinates = new Coordinates(0, -1, 0, 0);
               break;

            case CommandEconomy.INVENTORY_UP:
               coordinates = new Coordinates(0, 1, 0, 0);
               break;

            case CommandEconomy.INVENTORY_NORTH:
               coordinates = new Coordinates(0, 0, -1, 0);
               break;

            case CommandEconomy.INVENTORY_EAST:
               coordinates = new Coordinates(-1, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_WEST:
               coordinates = new Coordinates(1, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_SOUTH:
               coordinates = new Coordinates(0, 0, 1, 0);
               break;

            default:
               System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + CommandEconomy.CMD_USAGE_BLOCK_BUY);
               return;
         }
      }

      // if no username or direction should be given
      else {
         // ensure passed args are valid types
         try {
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            System.out.println(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BUY);
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
               System.out.println(CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BUY);
               return;
            }
            accountID = args[3];
         }

         // grab remaining variables
         username = playername;
         wareID   = args[0];
      }

      // check for entity selectors
      if (username != null &&
          (username.equals("@p") || username.equals("@r")))
         username = playername;

      if (accountID != null &&
          (accountID.equals("@p") || accountID.equals("@r")))
         accountID = playername;

      if ((username != null && username.startsWith("@")) || (accountID != null && accountID.startsWith("@"))) {
         System.out.println(CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }

      // grab user's UUID once
      UUID playerID = getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!permissionToExecute(playerID, getPlayerIDStatic(playername), false)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // check inventory existence
      if (coordinates != null) {
         HashMap<String, Integer> inventoryToUse = InterfaceTerminal.getInventory(playerID, coordinates);

         if (inventoryToUse == null) {
            System.out.println(CommandEconomy.ERROR_INVENTORY_MISSING + CommandEconomy.CMD_USAGE_BLOCK_BUY);
            return;
         }
      }

      // call corresponding function
      Marketplace.buy(playerID, coordinates, accountID, wareID, quantity, priceUnit, pricePercent, shouldManufacture);
      return;
   }

   /**
    * Sells a ware.
    * <p>
    * Expected Formats:<br>
    * (&#60;ware_id&#62; | held) [&#60;quantity&#62; [min_unit_price] [account_id]]<br>
    * &#60;player_name&#62; &#60;inventory_direction&#62; (&#60;ware_id&#62; | held) [&#60;quantity&#62; [min_unit_price] [account_id]]<br>
    * &#60;inventory_direction&#62; == none, east, west, south, up, or down
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSell(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(CommandEconomy.CMD_USAGE_SELL);
         return;
      }

      // set up variables
      String username       = null;
      InterfaceCommand.Coordinates coordinates = null;
      String accountID      = null;
      String wareID         = null;
      int    baseArgsLength = args.length; // number of args, not counting special keywords
      int    quantity       = 0;
      float  priceUnit      = 0.0f;
      float  pricePercent   = 1.0f;

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BUY);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(CommandEconomy.ARG_SPECIAL_PREFIX) && !arg.startsWith(CommandEconomy.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(CommandEconomy.PRICE_PERCENT)) {
            pricePercent = CommandProcessor.parsePricePercentArgument(getPlayerIDStatic(playername), arg, true);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }
      }

      // command must have the right number of args
      if (baseArgsLength < 1 ||
          baseArgsLength > 6) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SELL);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (baseArgsLength >= 2 && (args[1] == null || args[1].length() == 0)) ||
          (baseArgsLength >= 3 && (args[2] == null || args[2].length() == 0)) ||
          (baseArgsLength >= 4 && (args[3] == null || args[3].length() == 0)) ||
          (baseArgsLength >= 5 && (args[4] == null || args[4].length() == 0)) ||
          (baseArgsLength == 6 && (args[5] == null || args[5].length() == 0))) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SELL);
         return;
      }

      // if the second argument is a number, no username or direction should be given
      // if the second argument is a direction, a username and a direction should be given
      // if a username and a direction should be given
      if (args.length >= 2 &&
          (args[1].equals(CommandEconomy.INVENTORY_NONE) ||
           args[1].equals(CommandEconomy.INVENTORY_DOWN) ||
           args[1].equals(CommandEconomy.INVENTORY_UP) ||
           args[1].equals(CommandEconomy.INVENTORY_NORTH) ||
           args[1].equals(CommandEconomy.INVENTORY_EAST) ||
           args[1].equals(CommandEconomy.INVENTORY_WEST) ||
           args[1].equals(CommandEconomy.INVENTORY_SOUTH))) {
         // ensure passed args are valid types
         // if at least four arguments are given,
         // the fourth must be a quantity
         if (baseArgsLength >= 4) {
            try {
               quantity = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
               System.out.println(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BLOCK_SELL);
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
               System.out.println(CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BLOCK_SELL);
               return;
            }
            accountID = args[5];
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[2];

         // translate coordinates
         switch(args[1])
         {
            // x-axis: west  = +x, east  = -x
            // y-axis: up    = +y, down  = -y
            // z-axis: south = +z, north = -z

            case CommandEconomy.INVENTORY_NONE:
               coordinates = new Coordinates(0, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_DOWN:
               coordinates = new Coordinates(0, -1, 0, 0);
               break;

            case CommandEconomy.INVENTORY_UP:
               coordinates = new Coordinates(0, 1, 0, 0);
               break;

            case CommandEconomy.INVENTORY_NORTH:
               coordinates = new Coordinates(0, 0, -1, 0);
               break;

            case CommandEconomy.INVENTORY_EAST:
               coordinates = new Coordinates(-1, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_WEST:
               coordinates = new Coordinates(1, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_SOUTH:
               coordinates = new Coordinates(0, 0, 1, 0);
               break;

            default:
               System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + CommandEconomy.CMD_USAGE_BLOCK_SELL);
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
               System.out.println(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_SELL);
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
               System.out.println(CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_SELL);
               return;
            }
            accountID = args[3];
         }

         // grab remaining variables
         username = playername;
         wareID   = args[0];
      }

      // check for entity selectors
      if (username != null &&
          (username.equals("@p") || username.equals("@r")))
         username = playername;

      if (accountID != null &&
          (accountID.equals("@p") || accountID.equals("@r")))
         accountID = playername;

      if ((username != null && username.startsWith("@")) || (accountID != null && accountID.startsWith("@"))) {
         System.out.println(CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!permissionToExecute(playerID, getPlayerIDStatic(playername), false)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // check inventory existence
      if (coordinates != null) {
         HashMap<String, Integer> inventoryToUse = InterfaceTerminal.getInventory(playerID, coordinates);

         if (inventoryToUse == null) {
            System.out.println(CommandEconomy.ERROR_INVENTORY_MISSING + CommandEconomy.CMD_USAGE_BLOCK_SELL);
            return;
         }
      }

      // check whether the ware the user's is currently holding should be sold
      // the idea of selling the user's held item is from
      // DynamicEconomy ( https://dev.bukkit.org/projects/dynamiceconomy-v-01 )
      if (wareID.equalsIgnoreCase(CommandEconomy.HELD_ITEM)) {
         System.out.println(PlatformStrings.ERROR_HANDS);
         return;
      }

      // call corresponding function
      Marketplace.sell(playerID, coordinates, accountID, wareID, quantity, priceUnit, pricePercent);
      return;
   }

   /**
    * Looks up ware price and stock.
    * <p>
    * Expected Formats:<br>
    * (&#60;ware_id&#62; | held) [quantity] [&amp;craft]<br>
    * &#60;player_name&#62; (&#60;ware_id&#62; | held) &#60;quantity&#62; [&amp;craft]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestCheck(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(CommandEconomy.CMD_USAGE_CHECK);
         return;
      }

      // set up variables
      String  username          = null;
      String  wareID            = null;
      int     baseArgsLength    = args.length; // number of args, not counting special keywords
      int     quantity          = 0;           // holds ware quantities
      float   pricePercent      = 1.0f;
      boolean shouldManufacture = false;       // whether or not to factor in manufacturing for purchases

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_CHECK);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(CommandEconomy.ARG_SPECIAL_PREFIX) && !arg.startsWith(CommandEconomy.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(CommandEconomy.PRICE_PERCENT)) {
            pricePercent = CommandProcessor.parsePricePercentArgument(getPlayerIDStatic(playername), arg, false);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }

         // check whether user specifies manufacturing the ware
         if (arg.equals(CommandEconomy.MANUFACTURING))
            shouldManufacture = true;
      }

      // command must have the right number of args
      if (baseArgsLength < 1 ||
          baseArgsLength > 3) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_CHECK);
         return;
      }

      // if one argument is given,
      // the it is a ware ID
      if (baseArgsLength == 1) {
         username = playername;
         wareID = args[0];
      }

      // if two arguments are given,
      // the second must be a quantity
      else if (baseArgsLength == 2) {
         try {
            // assume the second argument is a number
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            System.out.println(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_CHECK);
            return;
         }

         // grab remaining variables
         username = playername;
         wareID   = args[0];
      }

      // if three arguments are given,
      // then they include a username and a quantity
      else if (baseArgsLength == 3) {
         // try to process quantity
         try {
            quantity = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BLOCK_CHECK);
            return;
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[1];
      }

      // check for entity selectors
      if (username != null &&
          (username.equals("@p") || username.equals("@r")))
         username = playername;

      if (username != null && username.startsWith("@")) {
         System.out.println(CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!permissionToExecute(playerID, getPlayerIDStatic(playername), false)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // check whether the ware the user's is currently holding should be checked
      // the idea of checking the user's held item is from
      // DynamicEconomy ( https://dev.bukkit.org/projects/dynamiceconomy-v-01 )
      if (wareID.equalsIgnoreCase(CommandEconomy.HELD_ITEM)) {
         System.out.println(PlatformStrings.ERROR_HANDS);
         return;
      }

      // call corresponding function
      Marketplace.check(playerID, wareID, quantity, pricePercent, shouldManufacture);
      return;
   }

   /**
    * Sells all tradeable wares in the inventory at current market prices.
    * <p>
    * Expected Formats:<br>
    * [account_id]<br>
    * &#60;player_name&#62; &#60;inventory_direction&#62; [account_id]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSellAll(String[] args) {
      // request can be null or have zero arguments

      // set up variables
      String username  = null;
      InterfaceCommand.Coordinates coordinates = null;
      String accountID = null;
      // prepare a reformatted container for the wares
      LinkedList<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();
      HashMap<String, Integer> inventoryToUse = null;
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
               System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BUY);
               return;
            }

            // special keywords start with certain symbols
            if (!arg.startsWith(CommandEconomy.ARG_SPECIAL_PREFIX) && !arg.startsWith(CommandEconomy.PRICE_PERCENT))
               continue;

            // if a special keyword is detected,
            // adjust the arg length count for non-special args
            baseArgsLength--;

            // check whether user is specifying the transaction price multiplier
            if (arg.startsWith(CommandEconomy.PRICE_PERCENT)) {
               pricePercent = CommandProcessor.parsePricePercentArgument(getPlayerIDStatic(playername), arg, true);

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
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SELLALL);
         return;
      }

      // check for zero-length args
      if (args != null &&
          ((baseArgsLength >= 1 && (args[0] == null || args[0].length() == 0)) ||
           (baseArgsLength >= 2 && (args[1] == null || args[1].length() == 0)) ||
           (baseArgsLength == 3 && (args[2] == null || args[2].length() == 0)))) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SELLALL);
         return;
      }

      // if there are at least two arguments,
      // a username and a direction must be given
      if (args != null && baseArgsLength >= 2) {
         username = args[0];

         // translate coordinates
         switch(args[1])
         {
            // x-axis: west  = +x, east  = -x
            // y-axis: up    = +y, down  = -y
            // z-axis: south = +z, north = -z

            case CommandEconomy.INVENTORY_NONE:
               coordinates = new Coordinates(0, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_DOWN:
               coordinates = new Coordinates(0, -1, 0, 0);
               break;

            case CommandEconomy.INVENTORY_UP:
               coordinates = new Coordinates(0, 1, 0, 0);
               break;

            case CommandEconomy.INVENTORY_NORTH:
               coordinates = new Coordinates(0, 0, -1, 0);
               break;

            case CommandEconomy.INVENTORY_EAST:
               coordinates = new Coordinates(-1, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_WEST:
               coordinates = new Coordinates(1, 0, 0, 0);
               break;

            case CommandEconomy.INVENTORY_SOUTH:
               coordinates = new Coordinates(0, 0, 1, 0);
               break;

            default:
               System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + CommandEconomy.CMD_USAGE_BLOCK_SELLALL);
               return;
         }

         // if an account ID was given, use it
         if (baseArgsLength == 3)
            accountID = args[2];
         // If an account ID wasn't given, leave the ID as null.
      } else {
         username    = playername;
         coordinates = null;

         // if an account ID was given, use it
         if (args != null && baseArgsLength == 1)
            accountID = args[0];
         // If an account ID wasn't given, leave the ID as null.
      }

      // check for entity selectors
      if (username != null &&
          (username.equals("@p") || username.equals("@r")))
         username = playername;

      if (accountID != null &&
          (accountID.equals("@p") || accountID.equals("@r")))
         accountID = playername;

      if ((username != null && username.startsWith("@")) || (accountID != null && accountID.startsWith("@"))) {
         System.out.println(CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!permissionToExecute(playerID, getPlayerIDStatic(playername), false)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // get the inventory
      inventoryToUse = InterfaceTerminal.getInventory(playerID, coordinates);
      if (inventoryToUse == null) {
         System.out.println(CommandEconomy.ERROR_INVENTORY_MISSING + CommandEconomy.CMD_USAGE_BLOCK_SELLALL);
         return;
      }

      // convert the inventory to the right format
      for (String wareID : inventoryToUse.keySet()) {
         formattedInventory.add(new Marketplace.Stock(wareID, inventoryToUse.get(wareID), 1.0f));
      }

      Marketplace.sellAll(playerID, coordinates, formattedInventory, accountID, pricePercent);
      return;
   }

   /**
    * Looks up how much is in an account.
    * <p>
    * Expected Formats:<br>
    * [account_id]<br>
    * &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestMoney(String[] args) {
      // request can be null or have zero arguments

      // check for zero-length args
      if (args != null && 
          ((args.length >= 1 && (args[0] == null || args[0].length() == 0)) ||
           (args.length >= 2 && (args[1] == null || args[1].length() == 0)))) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_MONEY);
         return;
      }

      // if nothing is given,
      // use the player's personal account or default account
      if (args == null || args.length == 0) {
         Account account = Account.grabAndCheckAccount(null, getPlayerIDStatic(playername));

         account.check(getPlayerIDStatic(playername), CommandEconomy.MSG_PERSONAL_ACCOUNT);
         return;
      }

      // set up variables
      String username  = null;
      String accountID = null;

      // if only an account ID is given
      if (args.length == 1) {
         username  = playername;
         accountID = args[0];
      }
      // if a username and an account ID are given
      else if (args.length >= 2) {
         username  = args[0];
         accountID = args[1];
      }

      // check for entity selectors
      if (username != null &&
          (username.equals("@p") || username.equals("@r")))
         username = playername;

      if (accountID != null &&
          (accountID.equals("@p") || accountID.equals("@r")))
         accountID = playername;

      if ((username != null && username.startsWith("@")) || (accountID != null && accountID.startsWith("@"))) {
         System.out.println(CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }
      UUID playerID = getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!permissionToExecute(playerID, getPlayerIDStatic(playername), false)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // grab the account using the given account ID
      Account account = Account.grabAndCheckAccount(accountID, playerID);
      if (account != null)
         account.check(playerID, accountID);
      // if the account was not found, an error message has already been printed

      return;
   }

   /**
    * Transfers money from one account to another.
    * <p>
    * Expected Formats:<br>
    * &#60;quantity&#62; &#60;recipient_account_id&#62; [sender_account_id]<br>
    * &#60;player_name&#62; &#60;quantity&#62; &#60;recipient_account_id&#62; [sender_account_id]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSend(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(CommandEconomy.CMD_USAGE_SEND);
         return;
      }

      // command must have the right number of args
      if (args.length < 2 ||
          args.length > 4) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SEND);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          args[1] == null || args[1].length() == 0 ||
          (args.length == 3 && (args[2] == null || args[2].length() == 0)) ||
          (args.length == 4 && (args[3] == null || args[3].length() == 0))) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SEND);
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
         username = playername;
         recipientAccountID = args[1];

         // if a sender account ID is given, use it
         if (args.length == 3)
            senderAccountID = args[2];

         // if no sender account is given,
         // use the player's personal account
         else
            senderAccountID = playername;
      } catch (NumberFormatException e) {
         // try to parse quantity
         try {
            quantity = Float.parseFloat(args[1]);
         } catch (NumberFormatException nfe) {
            System.out.println(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BLOCK_SEND);
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
         if (username != null &&
             (username.equals("@p") || username.equals("@r")))
            username = playername;

         if (recipientAccountID != null &&
             (recipientAccountID.equals("@p") || recipientAccountID.equals("@r")))
            recipientAccountID = playername;

         if (senderAccountID != null &&
             (senderAccountID.equals("@p") || senderAccountID.equals("@r")))
            senderAccountID = playername;

         if ((username != null && username.startsWith("@")) ||
             (recipientAccountID != null && recipientAccountID.startsWith("@")) ||
             (senderAccountID != null && senderAccountID.startsWith("@"))) {
            System.out.println(CommandEconomy.ERROR_ENTITY_SELECTOR);
            return;
         }
      }

      // if a valid account is given, use it
      account = Account.grabAndCheckAccount(senderAccountID, getPlayerIDStatic(username));
      if (account != null)
         // transfer the money
         account.sendMoney(getPlayerIDStatic(username), quantity, senderAccountID, recipientAccountID);

      return;
   }

   /**
    * Opens a new account with the specified id.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestCreate(String[] args) {
      CommandProcessor.accountCreate(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Closes an account with the specified id.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestDelete(String[] args) {
      CommandProcessor.accountDelete(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Allows a player to view and withdraw from a specified account.<br>
    * Expected Format: &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestGrantAccess(String[] args) {
      CommandProcessor.accountGrantAccess(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Disallows a player to view and withdraw from a specified account.<br>
    * Expected Format: &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestRevokeAccess(String[] args) {
      CommandProcessor.accountRevokeAccess(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Saves accounts and market wares.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSave(String[] args) {
      // call corresponding functions
      Marketplace.saveWares();
      Account.saveAccounts();
      System.out.println(CommandEconomy.MSG_SAVED_ECONOMY);
      return;
   }

   /**
    * Reloads part of the marketplace from file.<br>
    * Expected Format: (config | wares | accounts | all)
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestReload(String[] args) {
      UUID playerID = getPlayerIDStatic(playername);

      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(playerID, playerID, true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.reload(playerID, args, 0);

      // if necessary, start or stop autosaving
      if (Config.disableAutoSaving && timerAutosaver == null) {
         // initialize timer objects
         timerAutosaver     = new Timer(true);
         timertaskAutosaver = new Autosaver();

         // initialize periodically rebalancing the marketplace
         timerAutosaver.scheduleAtFixedRate(timertaskAutosaver, (long) 0, (long) 300000);
      } else if (!Config.disableAutoSaving && timerAutosaver != null) {
         timertaskAutosaver.stop = true;
         timertaskAutosaver      = null;
         timerAutosaver.cancel();
         timerAutosaver = null;
      }
      return;
   }

   /**
    * Summons money.<br>
    * Expected Format: &#60;quantity&#62; [account_id]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestAdd(String[] args) {
      UUID playerID = getPlayerIDStatic(playername);

      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(playerID, playerID, true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.add(playerID, args);
      return;
   }

   /**
    * Sets account's money to a specified amount.<br>
    * Expected Format: &#60;quantity&#62; [account_id]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSet(String[] args) {
      UUID playerID = getPlayerIDStatic(playername);

      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(playerID, playerID, true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.set(playerID, args, 0);
      return;
   }

   /**
    * Increases or decreases a ware's available quantity within the marketplace
    * or sets the quantity to a certain level.<br>
    * Expected Format: &#60;ware_id&#62; (&#60;quantity&#62; | equilibrium | overstocked | understocked)
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestChangeStock(String[] args) {
      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.changeStock(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Marks an account to be used in place of a personal account.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSetDefaultAccount(String[] args) {
      CommandProcessor.setDefaultAccount(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Writes all wares currently tradeable to a file.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestPrintMarket(String[] args) {
      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // call corresponding functions
      Marketplace.printMarket();
      return;
   }

   /**
    * Spends to increase a ware's supply and demand.
    * <p>
    * Expected Formats:<br>
    * &#60;ware_id&#62; [max_unit_price] [account_id]<br>
    * yes<br>
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestInvest(String[] args) {
      CommandProcessor.invest(getPlayerIDStatic(playername), args);
      return;
   }

   /**
    * Tells the user what version of Command Economy is running.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestVersion(String[] args) {
      System.out.println(CommandEconomy.MSG_VERSION + CommandEconomy.VERSION);
      return;
   }

   /**
    * Grants admin permissions.<br>
    * Expected Format: &#60;player_name&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestOp(String[] args) {
      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(PlatformStrings.CMD_USAGE_OP);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + PlatformStrings.CMD_USAGE_OP);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + PlatformStrings.CMD_USAGE_OP);
         return;
      }

      // set up variables
      String username = args[0];

      // check for entity selectors
      if (username.equals("@p") || username.equals("@r"))
         username = playername;

      if (!ops.contains(getPlayerIDStatic(username)))
         ops.add(getPlayerIDStatic(username));
      return;
   }

   /**
    * Revokes admin permissions.<br>
    * Expected Format: &#60;player_name&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestDeop(String[] args) {
      // check if command sender has permission to
      // execute this command
      if (!permissionToExecute(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(PlatformStrings.CMD_USAGE_DEOP);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + PlatformStrings.CMD_USAGE_DEOP);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + PlatformStrings.CMD_USAGE_DEOP);
         return;
      }

      // set up variables
      String username = args[0];

      // check for entity selectors
      if (username.equals("@p") || username.equals("@r"))
         username = playername;

      ops.remove(getPlayerIDStatic(username));
      return;
   }

   /**
    * Displays terminal inventory contents<br>
    * Expected Format: [none | north | east | west | south | up | down]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestInventory(String[] args) {
      // validate the inventory
      if (args != null && args.length != 0 &&
          args[0] != null && args[0].length() != 0) {
         if (args[0].equals(CommandEconomy.INVENTORY_NONE) ||
             args[0].equals(CommandEconomy.INVENTORY_DOWN) ||
             args[0].equals(CommandEconomy.INVENTORY_UP) ||
             args[0].equals(CommandEconomy.INVENTORY_NORTH) ||
             args[0].equals(CommandEconomy.INVENTORY_EAST) ||
             args[0].equals(CommandEconomy.INVENTORY_WEST) ||
             args[0].equals(CommandEconomy.INVENTORY_SOUTH)) {
            printInventory(args[0]);
         } else {
            System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + PlatformStrings.CMD_USAGE_INVENTORY);
         }
      } else {
         printInventory(CommandEconomy.INVENTORY_NONE);
      }

      return;
   }

   /**
    * Puts a one or a specific amount of a given id into the test inventory.<br>
    * Expected Format: &#60;ware_id&#62; [quantity] [inventory_direction]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestGive(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(PlatformStrings.CMD_USAGE_GIVE);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 &&
          args.length != 2 &&
          args.length != 3) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + PlatformStrings.CMD_USAGE_GIVE);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (args.length == 2 && (args[1] == null || args[1].length() == 0)) ||
          (args.length == 3 && (args[2] == null || args[2].length() == 0))) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + PlatformStrings.CMD_USAGE_GIVE);
         return;
      }

      // set up variables
      Ware   ware     = null;
      String wareID   = "";
      int    quantity = 1; // default to one ware given
      HashMap<String, Integer> inventoryToUse = inventory;

      // translate and get the ware so we can print its alias
      ware = Marketplace.translateAndGrab(args[0]);

      // if the ware was found, use its ware ID
      // if no ware was found, allow the command to give non-existent wares
      if (ware != null)
         wareID = ware.getWareID();
      else
         wareID = args[0];

      // validate quantity or direction, if given
      if (args.length >= 2) {
         // ensure passed quantity is a valid type
         try {
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            // if the second argument isn't a quantity,
            // it might be a direction
            inventoryToUse = getInventory(getPlayerIDStatic(playername), args[1]);

            // check whether the inventory was found
            if (inventoryToUse == null) {
               System.out.println(CommandEconomy.ERROR_QUANTITY + "wrong type, " + PlatformStrings.CMD_USAGE_GIVE);
               return;
            }
         }

         // ensure quantity is a valid number
         if (quantity < 1) {
            System.out.println(CommandEconomy.ERROR_QUANTITY + "must be greater than zero, " + PlatformStrings.CMD_USAGE_GIVE);
            return;
         }
      }

      // validate direction, if quantity and direction are given
      if (args.length == 3) {
         inventoryToUse = getInventory(getPlayerIDStatic(playername), args[2]);

         // check whether the inventory was found
         if (inventoryToUse == null) {
            System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + PlatformStrings.CMD_USAGE_GIVE);
            return;
         }
      }

      // give the ware to the player
      if (inventorySpace - inventoryToUse.size() >= 0) {
         inventoryToUse.put(wareID, inventoryToUse.getOrDefault(wareID, 0) + quantity);

         // report success
         // if the ware is in the marketplace and has an alias, use the alias
         if (ware != null && ware.getAlias() != null) {
            System.out.println("You have been given " + quantity + " " + ware.getAlias() + " (" + wareID + ")");
         } else {
            System.out.println("You have been given " + quantity + " " + wareID);
         }

         // warn the player if the ware ID is invalid
         if (Marketplace.translateWareID(wareID).isEmpty())
            System.out.println("warning - " + wareID + " is not usable within the marketplace");
      } else {
         // report failure
         System.out.println(CommandEconomy.ERROR_INVENTORY_SPACE);
      }

      return;
   }

   /**
    * Removes all or a specific amount of a given id from the test inventory.<br>
    * Expected Format: &#60;ware_id&#62; [quantity] [inventory_direction]<br>
    * [inventory_direction] is none, east, west, south, up, or down
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestTake(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(PlatformStrings.CMD_USAGE_TAKE);
         return;
      }

      // command must have the right number of args
      if (args.length != 1 &&
          args.length != 2 &&
          args.length != 3) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + PlatformStrings.CMD_USAGE_TAKE);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (args.length == 2 && (args[1] == null || args[1].length() == 0)) ||
          (args.length == 3 && (args[2] == null || args[2].length() == 0))) {
         System.out.println(CommandEconomy.ERROR_ZERO_LEN_ARGS + PlatformStrings.CMD_USAGE_TAKE);
         return;
      }

      // set up variables
      String wareID   = "";
      int    quantity = 0; // default to taking all of the ware
      HashMap<String, Integer> inventoryToUse = inventory;

      // translate the given ware ID in case it is an alias
      wareID = Marketplace.translateWareID(args[0]);

      // if no ware was found, allow the command to take non-existent wares
      if (wareID.isEmpty())
         wareID = args[0];

      // validate quantity or direction, if given
      if (args.length >= 2) {
         // ensure passed quantity is a valid type
         try {
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            // if the second argument isn't a quantity,
            // it might be a direction
            inventoryToUse = getInventory(getPlayerIDStatic(playername), args[1]);

            // check whether the inventory was found
            if (inventoryToUse == null) {
               System.out.println(CommandEconomy.ERROR_QUANTITY + "wrong type, " + PlatformStrings.CMD_USAGE_TAKE);
               return;
            }
         }

         // ensure quantity is a valid number
         if (quantity < 0) {
            System.out.println(CommandEconomy.ERROR_QUANTITY + "must be greater than zero, " + PlatformStrings.CMD_USAGE_TAKE);
            return;
         }
      }

      // validate direction, if quantity and direction are given
      if (args.length == 3) {
         inventoryToUse = getInventory(getPlayerIDStatic(playername), args[2]);

         // check whether the inventory was found
         if (inventoryToUse == null) {
            System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + PlatformStrings.CMD_USAGE_TAKE);
            return;
         }
      }

      // player should have the ware to be taken from
      if (!inventoryToUse.containsKey(wareID)) {
         System.out.println(CommandEconomy.ERROR_WARE_MISSING + PlatformStrings.CMD_USAGE_TAKE);
         return;
      }

      // if no quantity was specified, remove all of the specified ware
      // otherwise remove the quantity specified
      if (quantity == 0) {
         inventoryToUse.remove(wareID);
      } else {
         // if the quantity specified is or is more than the amount
         // the player has, remove all of the specified ware
         if (inventoryToUse.get(wareID) <= quantity) {
            inventoryToUse.remove(wareID);
         } else {
            inventoryToUse.put(wareID, inventoryToUse.get(wareID) - quantity);
         }
      }

      return;
   }

   /**
    * Sets the user's name and ID.<br>
    * Expected Format: &#60;player_name&#62;
    * <p>
    * Complexity: O(n^2)
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestChangeName(String[] args) {
      // request should not be null
      if (args == null || args.length == 0) {
         System.out.println(PlatformStrings.CMD_USAGE_CHANGE_NAME);
         return;
      }

      // command must have the right number of args
      if (args.length != 1) {
         System.out.println(CommandEconomy.ERROR_NUM_ARGS + PlatformStrings.CMD_USAGE_CHANGE_NAME);
         return;
      }

      // given player name should not be null
      if (args[0] == null || args[0].length() == 0) {
         System.out.println(PlatformStrings.ERROR_CHANGE_NAME_MISSING + PlatformStrings.CMD_USAGE_CHANGE_NAME);
         return;
      }

      System.out.println("Your name is now " + args[0] + ".\nYour old name was " + playername + ".");
      playername = args[0];
      return;
   }

   /**
    * Displays a specific inventory.
    * <p>
    * Within Minecraft, the base game should handle player inventory management,
    * but implementing a inventory simple system for testing and demonstration
    * allows for more precise tests targeting specific functions without
    * the added complexity of interfacing with Minecraft.
    * For example, this test inventory allows one to test
    * buying and selling individually. That way, if there is a problem
    * with buying and selling, we can see if it is caused by
    * functions handling the market or when interfacing with Minecraft.
    * <p>
    * Complexity: O(n), where n is wares within the inventory
    *
    * @param inventoryDirection inventory whose contents should be printed; should be none, down, up, north, east, west, or south
    */
   private static void printInventory(String inventoryDirection) {
      HashMap<String, Integer> inventoryToUse; // pointer to inventory to be used
      String inventoryName;                     // title for presenting the inventory

      if (inventoryDirection == null || inventoryDirection.length() == 0) {
         inventoryToUse = inventory;
         inventoryName  = "Your";
      } else {
         // grab the right name for the inventory
         switch(inventoryDirection)
         {
            case CommandEconomy.INVENTORY_NONE:
               inventoryToUse = inventory;
               inventoryName  = "Your";
               break;

            case CommandEconomy.INVENTORY_DOWN:
               inventoryToUse = inventoryDown;
               inventoryName  = "Downward";
               break;

            case CommandEconomy.INVENTORY_UP:
               inventoryToUse = inventoryUp;
               inventoryName  = "Upward";
               break;

            case CommandEconomy.INVENTORY_NORTH:
               inventoryToUse = inventoryNorth;
               inventoryName  = "Northward";
               break;

            case CommandEconomy.INVENTORY_EAST:
               inventoryToUse = inventoryEast;
               inventoryName  = "Eastward";
               break;

            case CommandEconomy.INVENTORY_WEST:
               inventoryToUse = inventoryWest;
               inventoryName  = "Westward";
               break;

            case CommandEconomy.INVENTORY_SOUTH:
               inventoryToUse = inventorySouth;
               inventoryName  = "Southward";
               break;

            default:
               System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + PlatformStrings.CMD_USAGE_INVENTORY);
               return;
         }
      }

      // print contents
      System.out.println(inventoryName + " inventory: " + inventoryToUse.size() + "/" + inventorySpace);
      int i = 1;
      for (String key : inventoryToUse.keySet()) {
         System.out.println(" " + i + ". " + key + ": " + inventoryToUse.get(key));
         i++;
      }

      return;
   }

   /**
    * Generates and displays the Minecraft chat command
    * for summoning a given ware for a player.
    * <p>
    * Using chat commands allows this program to be used
    * before an interface to Minecraft has been finished.
    * <p>
    * -Example Usage:-<br>
    * 10 Cobblestone for Elilmalith:<br>
    * Call: printChatCommand("minecraft:cobblestone", 10)<br>
    * Output: /give Elimalith minecraft:cobblestone 10
    * <p>
    * 15 Cocoa Beans for Elilmalith:<br>
    * Call: printChatCommand("minecraft:dye&amp;3", 15)<br>
    * Output: /give Elimalith minecraft:dye 15 3
    * <p>
    * Complexity: O(n^2)
    * @param wareID   id used to summon the ware
    * @param quantity how much of the ware should be summoned
    */
   public static void printChatCommand(String wareID, int quantity) {
      String itemID   = wareID; // holds Minecraft ID corresponding to the ware
      int    itemMeta = 0;      // holds any meta data item may have

      // if the ware ID contains item meta data,
      // split the ware ID according to an ampersand
      try {
         if (itemID.contains("&")) {
            String[] processedID = wareID.split("&", 2);
            itemID = processedID[0];

            // if item has meta data, grab it
            if (!processedID[1].isEmpty())
               itemMeta = Integer.parseInt(processedID[1]);
         }
      }
      catch (Exception e) {
         System.err.println("error - chat command generation failed, could not process " + wareID
                            + "\n   failed with " + e);
         return;
      }

      // if item has meta data, print it
      if (itemMeta != 0)
         System.out.println("/give " + playername +  " " + itemID + " " + quantity + " " + itemMeta);
      else
         System.out.println("/give " + playername +  " " + itemID + " " + quantity);
      return;
   }
}