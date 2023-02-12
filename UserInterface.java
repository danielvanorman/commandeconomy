package commandeconomy;

import java.util.List;       // for returning properties of wares found in an inventory
import java.util.UUID;       // for more securely tracking users internally

/**
 * Describes functions for interacting with a source of user input,
 * such as a command prompt or Minecraft's chat commands.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-23
 */
public interface UserInterface
{
   // STRUCTS
   /**
    * Used to locate a container within the world.
    * Implemented as a struct rather than an object to simplify code.
    * <p>
    * Within Minecraft:<br>
    * x-axis: west  = +x, east  = -x<br>
    * y-axis: up    = +y, down  = -y<br>
    * z-axis: south = +z, north = -z
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2021-05-19
    */
   class Coordinates
   {
      /** container's x-axis coordinate */
      public int x;
      /** container's y-axis coordinate */
      public int y;
      /** container's z-axis coordinate */
      public int z;
      /** container's world's reference number */
      public int dimension;

      /**
       * Coordinates Constructor: Fills fields for coordinates
       * marking where a container may be found.
       *
       * @param x         container's x-axis coordinate
       * @param y         container's y-axis coordinate
       * @param z         container's z-axis coordinate
       * @param dimension container's world's reference number
       */
      public Coordinates (final int x, final int y,
                          final int z, final int dimension) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.dimension = dimension;
      }
   }

   /**
    * Used to return a string and an integer when
    * checking what a player is holding.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2022-08-02
    */
   class Handful
   {
      /** ID for the ware a player is holding */
      public String wareID;
      /** player's currently active item slot */
      public int    inventorySlot;
      /** how much of the ware a player is holding */
      public int    quantity;
      /** how damaged the ware is */
      public float  percentWorth;

      /**
       * Handful Constructor: Fills fields describing
       * whatever a player might be holding.
       *
       * @param wareID        ID for the ware a player is holding
       * @param inventorySlot player's currently active item slot
       * @param quantity      how much of the ware a player is holding
       * @param percentWorth  how damaged the ware is
       */
      public Handful (final String wareID, final int inventorySlot,
                      final int quantity, final float percentWorth) {
         this.wareID        =  wareID;
         this.inventorySlot =  inventorySlot;
         this.quantity      = quantity;
         this.percentWorth  = percentWorth;
      }
   }

   // FUNCTIONS
   /**
    * Returns the path to the local game's save directory.
    *
    * @return directory of local save and config files
    */
   String getSaveDirectory();

   /**
    * Returns an inventory's coordinates using a given position and direction.
    * If the direction is invalid, returns null.
    *
    * @param playerID  user responsible for the trading; used to send error messages
    * @param sender    player or command block executing the command; determines original position
    * @param direction string representing where the inventory may be relative to the original position
    * @return inventory's coordinates, all zeros, or null if the direction is invalid
    */
   UserInterface.Coordinates getInventoryCoordinates(UUID playerID, Object sender, String direction);

   /**
    * Returns an inventory of wares to be sold or null.
    * Used by /sellAll.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated or null
    */
   List<Marketplace.Stock> getInventoryContents(UUID playerID,
      UserInterface.Coordinates coordinates);

   /**
    * Returns how many more stacks of wares the given inventory may hold.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return number of free slots in the given inventory
    */
   int getInventorySpaceAvailable(UUID playerID,
      UserInterface.Coordinates coordinates);

   /**
    * Gives a specified quantity of a ware ID to a user.
    * If there is no space for the ware, does nothing.
    *
    * @param wareID      unique ID used to refer to the ware
    * @param quantity    how much to give the user
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    */
   void addToInventory(UUID playerID, UserInterface.Coordinates coordinates,
      String wareID, int quantity);

   /**
    * Takes a specified quantity of a ware ID from a user.
    *
    * @param playerID      user responsible for the trading
    * @param coordinates   where the inventory may be found
    * @param wareID        unique ID used to refer to the ware
    * @param inventorySlot where to begin selling within a container
    * @param quantity      how much to take from the inventory
    */
   void removeFromInventory(UUID playerID, UserInterface.Coordinates coordinates,
      String wareID, int inventorySlot, int quantity);

   /**
    * Returns the quantities and corresponding qualities of
    * wares with the given id the user has.
    * <p>
    * The first entry has the total quantity of wares found.
    * The list is ordered by their position within the user's inventory.
    *
    * @param wareID      unique ID used to refer to the ware
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return quantities and qualities of wares found
    */
   List<Marketplace.Stock> checkInventory(UUID playerID, UserInterface.Coordinates coordinates,
      String wareID);

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
   UserInterface.Handful checkHand(UUID playerID, Object sender, Object server, String username);

   /**
    * Returns the player name associated with the given UUID.
    *
    * @param playerID UUID of whose name should be found
    * @return player name corresponding to given UUID
    */
   String getDisplayName(UUID playerID);

   /**
    * Returns the UUID associated with the given player name.
    *
    * @param playername player name corresponding UUID
    * @return player UUID corresponding to given player name
    */
   UUID getPlayerID(String playername);

   /**
    * Returns whether the given string matches a player name.
    *
    * @param username player to check the existence of
    * @return whether the given string is in use as a player's name
    */
   boolean doesPlayerExist(String username);

   /**
    * Returns whether a player with the given unique identifier is currently logged into the server.
    *
    * @param playerID UUID of player whose current status is needed
    * @return <code>true</code> if the player is currently online
    */
   boolean isPlayerOnline(UUID playerID);

   /**
    * Returns whether the player is a server administrator.
    *
    * @param playerID player whose server operator permissions should be checked
    * @return whether the player is an op
    */
   boolean isAnOp(UUID playerID);

   /**
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID    the player being affected by the issued command or the entity being acted upon
    * @param sender      command-issuing entity or the entity acting upon another
    * @param isOpCommand whether the sender must be an admin to execute even if the command only affects themself
    * @return true if the sender has permission to execute the command
    */
   boolean permissionToExecute(UUID playerID, Object sender, boolean isOpCommand);

   /**
    * Returns the value of a special token referring to a player name,
    * null if the token is invalid, or an error message for the user
    * if the user lacks permission to use such tokens.
    * Does not print an error if one occurs
    * in case multiple selectors are processed in series.
    * The appropriate error to print is CommandEconomy.ERROR_ENTITY_SELECTOR.
    *
    * @param sender   player or command block executing the command
    * @param selector string which might be an entity selector
    * @return player being referred to, an empty string if an error occurs, or the input string if the string is not an entity selector
    */
   String parseEntitySelector(Object sender, String selector);

   /**
    * Returns whether or not a given Forge OreDictionary name exists outside of the market.
    *
    * @param name the Forge OreDictionary name
    * @return true if the name exists outside of the market
    */
   boolean doesOreDictionaryNameExist(String name);

   /**
    * Returns whether or not a given ware exists outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return true if the ware exists outside of the market
    */
   boolean doesWareExist(String wareID);

   /**
    * Returns how many a stack of the ware may hold outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return the maximum amount a single stack may hold
    */
   int getStackSize(String wareID);

   /**
    * Returns a model ware that should be manipulated in place of
    * a given item being referred to. Uses one of the item's ore names
    * to determine an appropriate substitution.
    * Returns null if no substitution is found.
    *
    * @param wareID unique ID used to refer to the ware
    * @return ware corresponding to given ware ID's ore name or null
    */
   Ware getOreDictionarySubstitution(String wareID);

   /**
    * Forwards a message to the specified user.
    *
    * @param playerID who to give the message to
    * @param message what to tell the user
    */
   void printToUser(UUID playerID, String message);

   /**
    * Forwards a message to all users.
    *
    * @param message what to tell the users
    */
   void printToAllUsers(String message);

   /**
    * Forwards an error message to the specified user.
    *
    * @param playerID who to give the message to
    * @param message what to tell the user
    */
   void printErrorToUser(UUID playerID, String message);

   /**
    * Handles the contents for an error message normal users shouldn't necessarily see.
    *
    * @param message error encountered and possible details
    */
   void printToConsole(String message);

   /**
    * Fulfills commands given through the interface.
    */
   void serviceRequests();
}