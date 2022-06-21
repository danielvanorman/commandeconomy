package commandeconomy;

import java.util.LinkedList; // for returning properties of wares found in an inventory
import java.util.UUID;       // for more securely tracking users internally

/**
 * Describes functions for interacting with a source of user input,
 * such as a command prompt or Minecraft's chat commands.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-23
 */
public interface InterfaceCommand
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
   public class Coordinates
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
      public Coordinates (int x, int y,
         int z, int dimension) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.dimension = dimension;
      }
   };

   // FUNCTIONS
   /**
    * Returns the path to the local game's save directory.
    *
    * @return directory of local save and config files
    */
   public String getSaveDirectory();

   /**
    * Returns how many more stacks of wares the given inventory may hold.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return number of free slots in the given inventory
    */
   public int getInventorySpaceAvailable(UUID playerID,
      InterfaceCommand.Coordinates coordinates);

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
      String wareID, int quantity);

   /**
    * Takes a specified quantity of a ware ID from a user.
    *
    * @param wareID      unique ID used to refer to the ware
    * @param quantity    how much to take from the user
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    */
   public void removeFromInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID, int quantity);

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
   public LinkedList<Marketplace.Stock> checkInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID);

   /**
    * Returns the player name associated with the given UUID.
    *
    * @param playerID UUID of whose name should be found
    * @return player name corresponding to given UUID
    */
   public String getDisplayName(UUID playerID);

   /**
    * Returns the UUID associated with the given player name.
    *
    * @param playername player name corresponding UUID
    * @return player UUID corresponding to given player name
    */
   public UUID getPlayerID(String playername);

   /**
    * Returns whether the given string matches a player name.
    *
    * @param username player to check the existence of
    * @return whether the given string is in use as a player's name
    */
   public boolean doesPlayerExist(String username);

   /**
    * Returns whether or not a given Forge OreDictionary name exists outside of the market.
    *
    * @param name the Forge OreDictionary name
    * @return true if the name exists outside of the market
    */
   public boolean doesOreDictionaryNameExist(String name);

   /**
    * Returns whether or not a given ware exists outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return true if the ware exists outside of the market
    */
   public boolean doesWareExist(String wareID);

   /**
    * Returns how many a stack of the ware may hold outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return the maximum amount a single stack may hold
    */
   public int getStackSize(String wareID);

   /**
    * Forwards a message to the specified user.
    *
    * @param playerID who to give the message to
    * @param message what to tell the user
    */
   public void printToUser(UUID playerID, String message);

   /**
    * Forwards a message to all users.
    *
    * @param message what to tell the users
    */
   public void printToAllUsers(String message);

   /**
    * Forwards an error message to the specified user.
    *
    * @param playerID who to give the message to
    * @param message what to tell the user
    */
   public void printErrorToUser(UUID playerID, String message);

   /**
    * Handles the contents for an error message normal users shouldn't necessarily see.
    *
    * @param message error encountered and possible details
    */
   public void printToConsole(String message);

   /**
    * Returns whether the player is a server administrator.
    *
    * @param playerID player whose server operator permissions should be checked
    * @return whether the player is an op
    */
   public boolean isAnOp(UUID playerID);

   /**
    * Fulfills commands given through the interface.
    */
   public void serviceRequests();
}