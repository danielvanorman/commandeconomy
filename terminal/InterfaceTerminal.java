package commandeconomy;

import java.util.Scanner;              // for reading from the console
import java.util.HashMap;              // for storing wares in the user's inventory
import java.util.Map;
import java.util.LinkedList;           // for returning properties of wares found in an inventory and tracking server administrators
import java.util.List;
import java.util.Arrays;               // for removing the first element of user input before passing it to service request functions
import java.util.UUID;                 // for more securely tracking users internally
import java.util.Timer;                // for autosaving

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
   protected static List<UUID> ops = new LinkedList<UUID>();
   /** maps UUIDs to player names */
   private static Map<UUID, String> uuidToNames = new HashMap<UUID, String>();

   /** maximum inventory capacity */
   protected static int inventorySpace = 36;
   /** terminal user's personal inventory */
   protected static Map<String, Integer> inventory      = new HashMap<String, Integer>();
   /** northward inventory */
   protected static Map<String, Integer> inventoryNorth = new HashMap<String, Integer>();
   /** eastward inventory */
   protected static Map<String, Integer> inventoryEast  = new HashMap<String, Integer>();
   /** westward inventory */
   protected static Map<String, Integer> inventoryWest  = new HashMap<String, Integer>();
   /** southward inventory */
   protected static Map<String, Integer> inventorySouth = new HashMap<String, Integer>();
   /** upward inventory */
   protected static Map<String, Integer> inventoryUp    = new HashMap<String, Integer>();
   /** downward inventory */
   protected static Map<String, Integer> inventoryDown  = new HashMap<String, Integer>();

   /** speeds up concatenation for /help */
   private static StringBuilder sbHelpOutput   = new StringBuilder(2200);
   /** whether help output currently includes /research */
   private static boolean sbHelpContainsResearch = false;

   // autosaving
   /** for automatically and periodically saving wares and accounts */
   private static Timer timerAutosaver = null;
   /** for stopping the autosave thread gracefully */
   private static Autosaver timertaskAutosaver = null;

   /** translates user input to functions that should fulfill users' requests */
   private static Map<String, Command> serviceableCommands = new HashMap<String, Command>(29, 1.0f);

   // INTERFACES
   /**
    * Enables storing function calls within a hashmap,
    * easing calling request service methods based on user input.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2022-10-21
    */
   private static interface Command
   {
      /**
       * Fulfills a certain request.
       * <p>
       * @param args arguments given in the expected format
       */
      void run(String[] args);
   }

   // FUNCTIONS
   /**
    * Main function for initializing the market.
    *
    * @param args unused
    */
   public static void main(String[] args) {
      // connect desired interface to the market
      Config.commandInterface = new InterfaceTerminal();

      // register commands into a table for
      // translating user input to functions processing input
      serviceableCommands.put(CommandEconomy.CMD_HELP,
         new Command() { public void run(String[] args) {
               serviceRequestHelp(args); }});

      serviceableCommands.put(CommandEconomy.CMD_BUY,
         new Command() { public void run(String[] args) {
               serviceRequestBuy(args); }});

      serviceableCommands.put(CommandEconomy.CMD_SELL,
         new Command() { public void run(String[] args) {
               serviceRequestSell(args); }});

      serviceableCommands.put(CommandEconomy.CMD_CHECK,
         new Command() { public void run(String[] args) {
               serviceRequestCheck(args); }});

      serviceableCommands.put(CommandEconomy.CMD_SELLALL_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestSellAll(args); }});

      serviceableCommands.put(CommandEconomy.CMD_MONEY,
         new Command() { public void run(String[] args) {
               serviceRequestMoney(args); }});

      serviceableCommands.put(CommandEconomy.CMD_SEND,
         new Command() { public void run(String[] args) {
               serviceRequestSend(args); }});

      serviceableCommands.put(CommandEconomy.CMD_CREATE,
         new Command() { public void run(String[] args) {
               serviceRequestCreate(args); }});

      serviceableCommands.put(CommandEconomy.CMD_DELETE,
         new Command() { public void run(String[] args) {
               serviceRequestDelete(args); }});

      serviceableCommands.put(CommandEconomy.CMD_GRANT_ACCESS_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestGrantAccess(args); }});

      serviceableCommands.put(CommandEconomy.CMD_REVOKE_ACCESS_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestRevokeAccess(args); }});

      serviceableCommands.put(CommandEconomy.CMD_RESEARCH,
         new Command() { public void run(String[] args) {
               serviceRequestResearch(args); }});

      serviceableCommands.put(CommandEconomy.CMD_VERSION,
         new Command() { public void run(String[] args) {
               serviceRequestVersion(args); }});

      Command save = new Command() { public void run(String[] args) {
               serviceRequestSave(args); }};
      serviceableCommands.put("save", save);
      serviceableCommands.put(CommandEconomy.CMD_SAVECE, save);

      Command stop = new Command() { public void run(String[] args) {
               System.out.print("Save before quitting? (Y/N)\n> ");
               Scanner consoleInput = new Scanner(System.in);
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

               consoleInput.close(); }};
      serviceableCommands.put("stop", stop);
      serviceableCommands.put("exit", stop);

      serviceableCommands.put(CommandEconomy.CMD_RELOAD,
         new Command() { public void run(String[] args) {
               serviceRequestReload(args); }});

      serviceableCommands.put(CommandEconomy.CMD_ADD,
         new Command() { public void run(String[] args) {
               serviceRequestAdd(args); }});

      serviceableCommands.put(CommandEconomy.CMD_SET,
         new Command() { public void run(String[] args) {
               serviceRequestSet(args); }});

      serviceableCommands.put(CommandEconomy.CMD_CHANGE_STOCK_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestChangeStock(args); }});

      serviceableCommands.put(CommandEconomy.CMD_SET_DEFAULT_ACCOUNT_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestSetDefaultAccount(args); }});

      serviceableCommands.put(CommandEconomy.CMD_PRINT_MARKET_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestPrintMarket(args); }});

      serviceableCommands.put(PlatformStrings.CMD_OP,
         new Command() { public void run(String[] args) {
               serviceRequestOp(args); }});

      serviceableCommands.put(PlatformStrings.CMD_DEOP,
         new Command() { public void run(String[] args) {
               serviceRequestDeop(args); }});

      serviceableCommands.put(PlatformStrings.CMD_INVENTORY,
         new Command() { public void run(String[] args) {
               serviceRequestInventory(args); }});

      serviceableCommands.put(PlatformStrings.CMD_GIVE,
         new Command() { public void run(String[] args) {
               serviceRequestGive(args); }});

      serviceableCommands.put(PlatformStrings.CMD_TAKE,
         new Command() { public void run(String[] args) {
               serviceRequestTake(args); }});

      serviceableCommands.put(PlatformStrings.CMD_CHANGE_NAME_LOWER,
         new Command() { public void run(String[] args) {
               serviceRequestChangeName(args); }});

      // set up and run the market
      CommandEconomy.start(null);
   }

   /**
    * Returns the path to the local game's save directory.
    *
    * @return directory of local save and config files
    */
   public String getSaveDirectory() { return "saves"; }

   /**
    * Returns an inventory's coordinates using a given position and direction.
    * If the direction is invalid, returns null.
    * <br>
    * Complexity: O(1)
    * @param playerID  user responsible for the trading; used to send error messages
    * @param sender    player or command block executing the command; determines original position
    * @param direction string representing where the inventory may be relative to the original position
    * @return inventory's coordinates, all zeros if no inventory should be used, or null if the direction is invalid
    */
   public InterfaceCommand.Coordinates getInventoryCoordinates(UUID playerID, Object sender, String direction) {
      switch(direction)
      {
         // x-axis: west  = +x, east  = -x
         // y-axis: up    = +y, down  = -y
         // z-axis: south = +z, north = -z

         case CommandEconomy.INVENTORY_NONE:  return new Coordinates( 0,  0,  0, 0);
         case CommandEconomy.INVENTORY_DOWN:  return new Coordinates( 0, -1,  0, 0);
         case CommandEconomy.INVENTORY_UP:    return new Coordinates( 0,  1,  0, 0);
         case CommandEconomy.INVENTORY_NORTH: return new Coordinates( 0,  0, -1, 0);
         case CommandEconomy.INVENTORY_EAST:  return new Coordinates(-1,  0,  0, 0);
         case CommandEconomy.INVENTORY_WEST:  return new Coordinates( 1,  0,  0, 0);
         case CommandEconomy.INVENTORY_SOUTH: return new Coordinates( 0,  0,  1, 0);
         default:                             return null;
      }
   }

   /**
    * Returns the inventory which should be used.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated
    */
   private static Map<String, Integer> getInventoryContainer(UUID playerID,
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
   private static Map<String, Integer> getInventoryContainer(UUID playerID,
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
         default:                             return null;
      }
   }

   /**
    * Returns an inventory of wares to be sold or null.
    * Used by /sellAll.
    * <br>
    * Complexity: O(n), where n is the number of wares in an inventory
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated or null
    */
   public List<Marketplace.Stock> getInventoryContents(UUID playerID,
      InterfaceCommand.Coordinates coordinates) {
      // get the inventory
      Map<String, Integer> inventoryToUse = getInventoryContainer(playerID, coordinates);
      if (inventoryToUse == null)
         return null;

      // convert the inventory to the right format
      List<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();
      for (String wareID : inventoryToUse.keySet()) {
         formattedInventory.add(new Marketplace.Stock(wareID, Marketplace.translateAndGrab(wareID), inventoryToUse.get(wareID), 1.0f));
      }
      return formattedInventory;
   }

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
      Map<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventoryContainer(playerID, coordinates);

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
      Map<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventoryContainer(playerID, coordinates);

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
      Map<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventoryContainer(playerID, coordinates);

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
   public List<Marketplace.Stock> checkInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID) {
      // prepare a container for the wares
      List<Marketplace.Stock> waresFound = new LinkedList<Marketplace.Stock>();

      // grab the right inventory
      Map<String, Integer> inventoryToUse;
      if (coordinates != null) {
         inventoryToUse = getInventoryContainer(playerID, coordinates);

         // if no inventory was found
         if (inventoryToUse == null) {
            waresFound.add(new Marketplace.Stock(wareID, null, -1, 1.0f));
            return waresFound;
         }
      }
      else
         inventoryToUse = inventory;

      // if the ware is in the inventory, grab it
      if (inventoryToUse.containsKey(wareID) &&
          inventoryToUse.get(wareID) > 0)
         waresFound.add(new Marketplace.Stock(wareID, Marketplace.translateAndGrab(wareID), inventoryToUse.get(wareID), 1.0f));

         return waresFound;
   }

   /**
    * Returns the ID and quantity of whatever a payer is holding or
    * null if they are not holding anything.
    * Prints an error if nothing is found.
    *
    * @param playerID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command
    * @param server   host running the game instance; used for obtaining player information
    * @param username display name of user responsible for the trading
    * @return ware player is holding and how much or null
    */
   public InterfaceCommand.Handful checkHand(UUID playerID, Object sender, Object server, String username) {
      System.out.println(PlatformStrings.ERROR_HANDS);
      return null;
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
    * Returns whether the given string matches a player's name.
    *
    * @param username player to check the existence of
    * @return whether the given string is in use as a player's name
    */
   public boolean doesPlayerExist(String username) {
      return username != null && username.equals(InterfaceTerminal.playername); // don't assume any players other than the one currently logged in exists
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
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID the player being affected by the issued command or the entity being acted upon
    * @param sender   the command-issuing entity or the entity acting upon other
    * @param isOpCommand whether the sender must be an admin to execute even if the command only affects themself
    * @return true if the sender has permission to execute the command
    */
   public boolean permissionToExecute(UUID playerID, Object sender, boolean isOpCommand) {
      return permissionToExecuteStatic(playerID, (UUID) sender, isOpCommand);
   }

   /**
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID the player being affected by the issued command or the entity being acted upon
    * @param senderID ID of the command-issuing entity or the entity acting upon other
    * @param isOpCommand whether the sender must be an admin to execute even if the command only affects themself
    * @return true if the sender has permission to execute the command
    */
   public static boolean permissionToExecuteStatic(UUID playerID, UUID senderID, boolean isOpCommand) {
      if (playerID == null || senderID == null)
         return false;

      // check if the sender is only affecting themself
      if (!isOpCommand && playerID.equals(senderID))
         return true;

      // check for sender among server operators
      // to determine whether they may execute commands for other players
      return ops.contains(senderID);
   }

   /**
    * Returns the value of a special token referring to a player name,
    * null if the token is invalid, or an error message for the user
    * if the user lacks permission to use such tokens.
    * Does not print an error if the user lacks permissions.
    * <br>
    * Complexity: O(1)
    * @param sender   player or command block executing the command
    * @param selector string which might be an entity selector
    * @return player being referred to, null if user lacks permission, or the input string if the string is not an entity selector
    */
   public String parseEntitySelector(Object sender, String selector) {
      if (selector == null)
         return null;

      if (selector.equals("@p") || selector.equals("@r"))
         return playername;
      else if (selector.startsWith("@"))
         return "";
      else
         return selector;
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
    * Returns a model ware that should be manipulated in place of
    * a given item being referred to. Uses one of the item's ore names
    * to determine an appropriate substitution.
    * Returns null if no substitution is found.
    *
    * @param wareID unique ID used to refer to the ware
    * @return ware corresponding to given ware ID's ore name or null
    */
   public Ware getOreDictionarySubstitution(String wareID) {
      return Marketplace.translateAndGrab(wareID);
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
      String[] userInput;          // holds request being parsed
      Command command      = null; // holds function to fulfill request

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
            userInput = Arrays.copyOfRange(userInput, 1, userInput.length);

         // parse request parameters and pass them to the right function
         command = serviceableCommands.get(userInput[0]);

         // if the command is not found, say so
         if (command == null)
            System.out.println(CommandEconomy.ERROR_INVALID_CMD);
         else
            command.run(Arrays.copyOfRange(userInput, 1, userInput.length));
      }
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
            CommandEconomy.CMD_DESC_INVENTORY_DIRECTION
         );
      } else {
         // in necessary, regenerate help output
         if (sbHelpOutput.length() == 0 ||
             sbHelpContainsResearch != (Config.researchCostPerHierarchyLevel != 0.0f)) {
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

            // if needed, add in /research
            if (Config.researchCostPerHierarchyLevel != 0.0f) {
               sbHelpContainsResearch = true;
               sbHelpOutput.append(CommandEconomy.CMD_USAGE_RESEARCH).append(CommandEconomy.CMD_DESC_RESEARCH);
            }
            else
               sbHelpContainsResearch = false;

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
                        .append(PlatformStrings.CMD_USAGE_STOP).append(PlatformStrings.CMD_DESC_STOP).append(System.lineSeparator());
         }

         System.out.println(sbHelpOutput.toString());
      }
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
      CommandProcessor.buy(getPlayerIDStatic(playername), getPlayerIDStatic(playername), args);
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
      CommandProcessor.sell(getPlayerIDStatic(playername), getPlayerIDStatic(playername), null, args);
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
      CommandProcessor.check(getPlayerIDStatic(playername), getPlayerIDStatic(playername), null, args);
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
      CommandProcessor.sellAll(getPlayerIDStatic(playername), getPlayerIDStatic(playername), args);
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
      CommandProcessor.money(getPlayerIDStatic(playername), getPlayerIDStatic(playername), args);
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
      CommandProcessor.send(getPlayerIDStatic(playername), null, args);
   }

   /**
    * Opens a new account with the specified id.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestCreate(String[] args) {
      CommandProcessor.accountCreate(getPlayerIDStatic(playername), args);
   }

   /**
    * Closes an account with the specified id.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestDelete(String[] args) {
      CommandProcessor.accountDelete(getPlayerIDStatic(playername), args);
   }

   /**
    * Allows a player to view and withdraw from a specified account.<br>
    * Expected Format: &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestGrantAccess(String[] args) {
      CommandProcessor.accountGrantAccess(getPlayerIDStatic(playername), args);
   }

   /**
    * Disallows a player to view and withdraw from a specified account.<br>
    * Expected Format: &#60;player_name&#62; &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestRevokeAccess(String[] args) {
      CommandProcessor.accountRevokeAccess(getPlayerIDStatic(playername), args);
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
      if (!permissionToExecuteStatic(playerID, playerID, true)) {
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
      if (!permissionToExecuteStatic(playerID, playerID, true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.add(playerID, args);
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
      if (!permissionToExecuteStatic(playerID, playerID, true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.set(playerID, args, 0);
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
      if (!permissionToExecuteStatic(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.changeStock(getPlayerIDStatic(playername), args);
   }

   /**
    * Marks an account to be used in place of a personal account.<br>
    * Expected Format: &#60;account_id&#62;
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSetDefaultAccount(String[] args) {
      CommandProcessor.setDefaultAccount(getPlayerIDStatic(playername), args);
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
      if (!permissionToExecuteStatic(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
         System.out.println(CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // call corresponding functions
      Marketplace.printMarket();
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
   protected static void serviceRequestResearch(String[] args) {
      CommandProcessor.research(getPlayerIDStatic(playername), args);
   }

   /**
    * Tells the user what version of Command Economy is running.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestVersion(String[] args) {
      System.out.println(CommandEconomy.MSG_VERSION + CommandEconomy.VERSION);
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
      if (!permissionToExecuteStatic(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
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
      if (!permissionToExecuteStatic(getPlayerIDStatic(playername), getPlayerIDStatic(playername), true)) {
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
         } else
            System.out.println(CommandEconomy.ERROR_INVENTORY_DIR + PlatformStrings.CMD_USAGE_INVENTORY);
      } else
         printInventory(CommandEconomy.INVENTORY_NONE);
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
      Map<String, Integer> inventoryToUse = inventory;

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
            inventoryToUse = getInventoryContainer(getPlayerIDStatic(playername), args[1]);

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
         inventoryToUse = getInventoryContainer(getPlayerIDStatic(playername), args[2]);

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
      Map<String, Integer> inventoryToUse = inventory;

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
            inventoryToUse = getInventoryContainer(getPlayerIDStatic(playername), args[1]);

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
         inventoryToUse = getInventoryContainer(getPlayerIDStatic(playername), args[2]);

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
      Map<String, Integer> inventoryToUse; // pointer to inventory to be used
      String inventoryName;                // title for presenting the inventory

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
   }
}