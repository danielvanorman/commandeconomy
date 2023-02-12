package commandeconomy;

import net.minecraftforge.fml.common.Mod;                          // for registering as a mod and initializing the mod
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;  // for initializing the marketplace upon game's start
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;  // for canceling threads when the world stops
import java.util.LinkedList;                                       // for returning properties of wares found in an inventory and autocompleting arguments
import net.minecraft.command.CommandHandler;                       // for registering commands
import java.util.List;                                             // for autocompleting command arguments
import net.minecraft.util.text.TextComponentString;                // for sending messages to users
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;             // for finding and interacting with specific users and getting usernames when autocompleting arguments
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;          // for accessing player inventories
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;                                    // for converting between items and ware stock levels and getting items' ware IDs
import net.minecraft.util.ResourceLocation;                        // for finding items based on namespaced ids
import net.minecraftforge.items.IItemHandler;                      // for handling player and block inventories
import net.minecraft.tileentity.TileEntity;                        // for handling block inventories
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraft.util.math.BlockPos;                           // for finding block inventories
import net.minecraft.util.NonNullList;                             // for checking the existence of item subtypes when validating ware IDs
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.command.ICommandSender;                       // for checking for server operator permissions, converting sender names to UUIDs, simplifying sending error messages, and extracting command-issuing entities' positions and permissions
import net.minecraftforge.oredict.OreDictionary;                   // for checking Forge OreDictionary names
import net.minecraft.server.management.PlayerList;                 // for checking server operator permissions
import java.util.UUID;                                             // for more securely tracking users internally
import java.util.HashMap;                                          // for mapping server and command block UUIDs to displayable names
import java.util.Map;
import net.minecraft.command.CommandBase;                          // for providing other classes access to command handlers
import net.minecraftforge.common.DimensionManager;                 // for getting paths to per-world save and config files
import java.util.TreeSet;                                          // for autocompleting arguments
import java.util.Set;
import net.minecraft.command.EntitySelector;                       // for using command block selectors
import net.minecraft.server.MinecraftServer;                       // for obtaining player information when checking players' hands

/**
 * Contains functions for interacting with chat commands within Minecraft.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-28
 */
@Mod(modid = CommandEconomy.MODID, name = CommandEconomy.NAME, version = CommandEconomy.VERSION, acceptableRemoteVersions = "*")
public class UserInterfaceMinecraft implements UserInterface
{
   // GLOBAL VARIABLES
   /** maps the server and command block's UUIDs to displayable names */
   private static Map<UUID, String> uuidToNames = new HashMap<UUID, String>();
   /** NBT tag marking an item stack as not to be sold */
   private final static String NBT_TAG_NOSELL = "nosell";

   // references to command handlers
   /** handles the /buy command */
   public static final CommandBase commandBuy = new CommandBuy();
   /** handles the /sell command */
   public static final CommandBase commandSell = new CommandSell();
   /** handles the /check command */
   public static final CommandBase commandCheck = new CommandCheck();
   /** handles the /sellall command */
   public static final CommandBase commandSellAll = new CommandSellAll();
   /** handles the /nosell command */
   public static final CommandBase commandNoSell = new CommandNoSell();
   /** handles the /money command */
   public static final CommandBase commandMoney = new CommandMoney();
   /** handles the /send command */
   public static final CommandBase commandSend = new CommandSend();
   /** handles the /create command */
   public static final CommandBase commandCreate = new CommandCreate();
   /** handles the /delete command */
   public static final CommandBase commandDelete = new CommandDelete();
   /** handles the /grantAccess command */
   public static final CommandBase commandGrantAccess = new CommandGrantAccess();
   /** handles the /revokeAccess command */
   public static final CommandBase commandRevokeAccess = new CommandRevokeAccess();
   /** handles the /add command */
   public static final CommandBase commandAdd = new CommandAdd();

   /** used to signal what data should be returned in a list of strings */
   public enum AutoCompletionStringCategories {
      ACCOUNTS,
      INVENTORY,
      PLAYERS,
      WARES
   }
   /** valid arguments for referring to an inventory */
   public static final String[] INVENTORY_KEYWORDS = new String[] {CommandEconomy.INVENTORY_NONE, CommandEconomy.INVENTORY_DOWN, CommandEconomy.INVENTORY_UP, CommandEconomy.INVENTORY_NORTH, CommandEconomy.INVENTORY_EAST, CommandEconomy.INVENTORY_WEST, CommandEconomy.INVENTORY_SOUTH};
   /** valid arguments for referring to accounts */
   private static Set<String> accountIDs = null;
   /** valid arguments for referring to wares' alternate names */
   private static Set<String> wareAliases = null;

   // FUNCTIONS
   /**
    * After game start, sets up chat commands and the market.
    * It is important to load wares after other mods are loaded.
    * If all other mods have been loaded, Command Economy may
    * more accurately check for other mods and load their wares.
    *
    * @param event information concerning Minecraft's current state
    */
   @Mod.EventHandler
   public void serverStarted(FMLServerStartedEvent event) {
      // register serviceable commands
      registerCommands(event);

      // connect desired interface to the market
      Config.userInterface = this;

      // set up and run the market
      CommandEconomy.start(null);

      // prepare for autocompleting arguments
      sortAccountIDs();
      sortWareAliases();
   }

   /**
    * When the world is closing, cancel any running threads.
    *
    * @param event information concerning Minecraft's current state
    */
   @Mod.EventHandler
   public void serverStopped(FMLServerStoppedEvent event) {
      // end any threads needed by features
      Marketplace.endPeriodicEvents();
      Account.endPeriodicEvents();
   }

   /**
    * Returns the path to the local game's save directory.
    *
    * @return directory of local save and config files
    */
   public String getSaveDirectory() {
      return DimensionManager.getCurrentSaveRootDirectory().getPath();
   }

   /**
    * Returns an inventory's coordinates using a given position and direction.
    * If the direction is invalid, sends an error message to the user.
    *
    * @param playerID  user responsible for the trading; used to send error messages
    * @param sender    player or command block executing the command; determines original position
    * @param direction string representing where the inventory may be relative to the original position
    * @return inventory's coordinates, all zeros, or null if the direction is invalid
    */
   public UserInterface.Coordinates getInventoryCoordinates(UUID playerID, Object sender, String direction) {
      BlockPos position = ((ICommandSender) sender).getPosition();
      if (position == null)
         return null;

      switch(direction)
      {
         // x-axis: west  = +x, east  = -x
         // y-axis: up    = +y, down  = -y
         // z-axis: south = +z, north = -z

         case CommandEconomy.INVENTORY_NONE:
            position = new BlockPos(0, 0, 0);
            break;

         case CommandEconomy.INVENTORY_DOWN:
            position = position.down();
            break;

         case CommandEconomy.INVENTORY_UP:
            position = position.up();
            break;

         case CommandEconomy.INVENTORY_NORTH:
            position = position.north();
            break;

         case CommandEconomy.INVENTORY_EAST:
            position = position.east();
            break;

         case CommandEconomy.INVENTORY_WEST:
            position = position.west();
            break;

         case CommandEconomy.INVENTORY_SOUTH:
            position = position.south();
            break;

         default:
            return null;
      }
      return new UserInterface.Coordinates(position.getX(), position.getY(), position.getZ(), ((ICommandSender) sender).getEntityWorld().provider.getDimension());
   }

   /**
    * Returns the inventory which should be used.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated
    */
   public static IItemHandler getInventoryContainer(UUID playerID,
      UserInterface.Coordinates coordinates) {
      // if no coordinates are given, use the user's personal inventory
      if (coordinates == null ||
          (coordinates.x == 0 &&
           coordinates.y == 0 &&
           coordinates.z == 0 &&
           coordinates.dimension == 0)) {
         // search for the player
         EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

         if (player == null)
            return null;
         else
            return new PlayerInvWrapper(player.inventory);
      }

      // search for an inventory
      TileEntity tileentity = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(coordinates.dimension).getTileEntity(new BlockPos(coordinates.x, coordinates.y, coordinates.z));
      IItemHandler itemHandler = null;

      if (tileentity != null)
         itemHandler = tileentity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
      if (itemHandler != null)
            return itemHandler;

      // if the inventory hasn't been found,
      // return null to signal an invalid coordinate
      return null;
   }

   /**
    * Returns an inventory of wares to be sold or null.
    * Used by /sellAll.
    * <br>
    * Complexity: O(n), where n is the number of slots the inventory has
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated or null
    */
   public List<Marketplace.Stock> getInventoryContents(UUID playerID,
      UserInterface.Coordinates coordinates) {
      IItemHandler inventoryToUse = getInventoryContainer(playerID, coordinates);
      if (inventoryToUse == null)
         return null;

      // set up variables
      ItemStack itemStack;       // wares in the slot being parsed
      String    itemID   = "";   // for writing ware IDs for each item stack
      Ware      ware     = null; // reference to ware within the marketplace to be traded
      int       maxSlots = 0;    // size of the inventory
      LinkedList<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();

      // search for sellable wares within the player's inventory
      if (inventoryToUse instanceof PlayerInvWrapper)
         // Only check slots 0-35 since those compose the main inventory and the hotbar.
         maxSlots = 36;
      // for other inventories, check everything
      else
         maxSlots = inventoryToUse.getSlots();

      for (int slot = 0; slot < maxSlots; slot++) {
         ware      = null; // reset ware corresponding to slot
         itemStack = inventoryToUse.getStackInSlot(slot); // grab wares in current inventory slot

         // if the slot is empty, skip it
         if (itemStack == null   ||        // prevents null pointer exception
             itemStack.isEmpty() ||        // checks for air blocks
             itemStack == ItemStack.EMPTY) // checks for special empty token
            continue;

         // check whether the wares in the slot are marked as unsellable
         if (itemStack.hasTagCompound() &&
             itemStack.getTagCompound().hasKey(NBT_TAG_NOSELL) &&
             itemStack.getTagCompound().getBoolean(NBT_TAG_NOSELL))
            continue;

         // get item's ware ID and ware
         itemID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString() + "&" + itemStack.getMetadata();
         ware   = Marketplace.translateAndGrab(itemID);

         // if the item is not in the market,
         // check whether the item has an ore name within the market
         if (ware == null && Config.allowWareTagSubstitution) {
            // get the item stack's numerical OreDictionary IDs
            int[] oreIDs = OreDictionary.getOreIDs(itemStack);

            // loop through item stack's used ore names and
            // use the first one which is used within the market
            for (int oreID : oreIDs) {
               // get current ore ID's model ware ID
               String wareID = Marketplace.translateAlias('#' + OreDictionary.getOreName(oreID));

               // if a model ware is found, use it
               if (wareID != null) {
                  ware = Marketplace.translateAndGrab(wareID);
                  break;
               }
            }
         }

         // if the item is not in the marketplace, skip it
         if (ware == null)
            continue;

         // add item stack to list of potentially sellable wares
         // if the wares are damageable, handle potential damage
         if (itemStack.isItemStackDamageable() && itemStack.isItemDamaged()) {
            // add wares to the container
            // if the wares are damaged,
            // record how badly they are damaged
            formattedInventory.add(new Marketplace.Stock(itemID, ware, itemStack.getCount(),
               ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage()));
         } else
            formattedInventory.add(new Marketplace.Stock(itemID,
                                   ware, itemStack.getCount(), 1.0f));
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
      UserInterface.Coordinates coordinates) {
      // grab the right inventory
      IItemHandler inventory = getInventoryContainer(playerID, coordinates);

      int maxSlots = 0;
      int freeSlots = 0;

      // for players, only check slots 0-36
      if (inventory instanceof PlayerInvWrapper) {
         /*
          * When parsing a player inventory, some ranges of slots
          * should and should not have their items sold.
          *
          * Specifically, only sell from slots 0-35.
          * Slots 0-35 is the hotbar (0-8) and main inventory (9-35).
          * Do not sell from:
          * Slots 100-103 - Armor
          * Slots 106    - Offhand
          * Slots  80-83  - Crafting
          */
         maxSlots = 36;
      }
      // for other inventories, check everything
      else
         maxSlots = inventory.getSlots();

      for (int i = 0; i < maxSlots; i++) {
         if (inventory.getStackInSlot(i) == null   ||        // prevents null pointer exception
             inventory.getStackInSlot(i).isEmpty() ||        // checks for air blocks
             inventory.getStackInSlot(i) == ItemStack.EMPTY) // checks for special empty token
            freeSlots++;
      }

      return freeSlots;
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
   public void addToInventory(UUID playerID, UserInterface.Coordinates coordinates,
      String wareID, int quantity) {
      // if no items should be added, do nothing
      if (quantity <= 0)
         return;

      // prepare to message the player
      EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // grab the right inventory
      IItemHandler inventory = getInventoryContainer(playerID, coordinates);

      // check if the given ID is a variant
      int meta = 0;
      int ampersandPosition = wareID.indexOf('&'); // find if and where variation begins
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            printToConsole(PlatformStrings.ERROR_ADDING_ITEM + PlatformStrings.ERROR_META_PARSING + wareID);
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString(PlatformStrings.ERROR_ADDING_ITEM + PlatformStrings.ERROR_META_PARSING + wareID);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // if giving an item fails, warn the player and the server
      try {
         // set up ware for giving to player
         Item item = Item.REGISTRY.getObject(new ResourceLocation(wareID));
         ItemStack remaining = new ItemStack(item, quantity, meta);
         int maxSlots = 0;

         // give ware to inventory
         if (inventory instanceof PlayerInvWrapper) {
            // Only check slots 0-35 since those compose the main inventory and the hotbar.
            maxSlots = 36;
         }
         // for other inventories, check everything
         else
            maxSlots = inventory.getSlots();

         for (int i = 0; i < maxSlots; i++) {
            // attempt to insert the items into the given slot,
            // stacking if the inventory already has items of the same type
            remaining = inventory.insertItem(i, remaining, false);

            // if all items to be given have been given, stop
            if (remaining == null)
               break;
         }
      } catch (Exception e) {
         // warn the server
         printToConsole(PlatformStrings.ERROR_ADDING_ITEM + PlatformStrings.ERROR_ITEM_NOT_FOUND + wareID );

         // warn the player
         if (player != null) {
            TextComponentString errorMessage = new TextComponentString(PlatformStrings.ERROR_ITEM_NOT_FOUND + wareID);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            player.sendMessage(errorMessage);
         }
      }
   }

   /**
    * Takes a specified quantity of a ware ID from a user.
    *
    * @param wareID        unique ID used to refer to the ware
    * @param quantity      how much to take from the user
    * @param inventorySlot where to begin selling within a container
    * @param playerID      user responsible for the trading
    * @param coordinates   where the inventory may be found
    */
   public void removeFromInventory(UUID playerID, UserInterface.Coordinates coordinates,
      String wareID, int inventorySlot, int quantity) {
      // if no items should be removed, do nothing
      if (quantity <= 0)
         return;

      // check if ware id is empty
      if (wareID == null || wareID.isEmpty())
         return;

      // inventory slots must be zero or positive
      if (inventorySlot < 0)
         inventorySlot = 0;

      // prepare to message the player
      EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // grab the right inventory
      IItemHandler inventory = getInventoryContainer(playerID, coordinates);
      // if no inventory was found
      if (inventory == null)
         return;

      // set up variables
      int meta = 0;            // represents either the ware's variant type or accumulated damage
      int ampersandPosition = wareID.indexOf('&'); // find if and where variation begins
      ItemStack itemStack;     // wares in the slot being parsed
      int maxSlots = 0;        // size of the inventory
      String oreName = "";       // holds the Forge OreDictionary name being used
      final boolean USING_ORE_NAME = wareID.startsWith("#");
      final boolean NONZERO_INVENTORY_SLOT = inventorySlot > 0;

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            printToConsole(PlatformStrings.ERROR_REMOVING_ITEM + PlatformStrings.ERROR_META_PARSING + wareID);
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString(PlatformStrings.ERROR_REMOVING_ITEM + PlatformStrings.ERROR_META_PARSING + wareID);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // if an Forge OreDictionary name is being used,
      // try to get the ore name
      if (USING_ORE_NAME) {
         oreName = wareID.substring(1, wareID.length());

         if (!OreDictionary.doesOreNameExist(oreName)) {
            // warn the console
            printToConsole(PlatformStrings.ERROR_REMOVING_ITEM + PlatformStrings.ERROR_NAME_NOT_FOUND + oreName);

            // warn the player
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString(PlatformStrings.ERROR_REMOVING_ITEM + PlatformStrings.ERROR_NAME_NOT_FOUND + oreName);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
            return;
         }
      }

      // parse the inventory
      if (inventory instanceof PlayerInvWrapper) {
         // Only check slots 0-35 since those compose the main inventory and the hotbar.
         maxSlots = 36;
      }
      // for other inventories, check everything
      else
         maxSlots = inventory.getSlots();
      // don't try to access beyond the last slots
      if (inventorySlot >= maxSlots)
         inventorySlot = maxSlots - 1;

      for (int slot = inventorySlot; slot < maxSlots && quantity > 0; slot++) {
         itemStack = inventory.getStackInSlot(slot);

         // if the slot is empty, skip it
         if (itemStack == null   ||        // prevents null pointer exception
             itemStack.isEmpty() ||        // checks for air blocks
             itemStack == ItemStack.EMPTY) // checks for special empty token
            continue;

         // check whether the item stack is marked as unsellable
         if (itemStack.hasTagCompound() &&
             itemStack.getTagCompound().hasKey(NBT_TAG_NOSELL) &&
             itemStack.getTagCompound().getBoolean(NBT_TAG_NOSELL))
            continue;

         // check if current item stack contains the desired item
         // or if current item stack contains the desired ore ID
         if ((!USING_ORE_NAME &&
              Item.REGISTRY.getNameForObject(itemStack.getItem()).toString().equals(wareID) &&
              (itemStack.getMetadata() == meta || itemStack.isItemStackDamageable())) ||
             (USING_ORE_NAME &&
               doesItemstackUseOreName(oreName, itemStack))) {
            // pull from the item stack
            itemStack = inventory.extractItem(slot, quantity, false);

            // if nothing was extracted, there is nothing more to take
            if (itemStack == null)
               break;

            // find out how much more should be taken
            quantity -= itemStack.getCount();
         }

         // if parsing started on partway in a container,
         // wraparound if necessary
         if (NONZERO_INVENTORY_SLOT && slot == maxSlots - 1 &&
             inventorySlot != maxSlots) {
            slot     = 0;             // wrap back to the beginning
            maxSlots = inventorySlot; // stop at the original slot
         }
      }
   }

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
   public List<Marketplace.Stock> checkInventory(UUID playerID, UserInterface.Coordinates coordinates,
      String wareID) {
      // prepare a container for the wares
      LinkedList<Marketplace.Stock> waresFound = new LinkedList<Marketplace.Stock>();

      // prepare to message the player
      EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // grab the right inventory
      IItemHandler inventory = getInventoryContainer(playerID, coordinates);
      // if no inventory was found
      if (inventory == null) {
         waresFound.add(new Marketplace.Stock(wareID, null, -1, 1.0f));
         return waresFound;
      }

      // set up variables
      ItemStack itemStack;                                // wares in the slot being parsed
      String    itemID             = "";                  // for writing ware IDs for each item stack
      String    oreName            = "";                  // holds the Forge OreDictionary name being used
      Ware      ware               = null;                // reference to ware within the marketplace to be traded
      int       meta               = 0;                   // represents either the ware's variant type or accumulated damage
      int       ampersandPosition  = wareID.indexOf('&'); // find if and where variation begins
      int       maxSlots           = 0;                   // size of the inventory
      final boolean USING_ORE_NAME = wareID.startsWith("#");

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString(PlatformStrings.ERROR_CHECKING_ITEM + PlatformStrings.ERROR_META_PARSING + wareID);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // if an Forge OreDictionary name is being used,
      // try to get the ore name
      if (USING_ORE_NAME) {
         oreName = wareID.substring(1, wareID.length());

         // if the ore name doesn't exist, tell the player
         if (!OreDictionary.doesOreNameExist(oreName)) {
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString(PlatformStrings.ERROR_CHECKING_ITEM + PlatformStrings.ERROR_NAME_NOT_FOUND + oreName);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
            return waresFound;
         }
      }

      // search for the ware within the player's inventory
      if (inventory instanceof PlayerInvWrapper)
         // Only check slots 0-35 since those compose the main inventory and the hotbar.
         maxSlots = 36;
      // for other inventories, check everything
      else
         maxSlots = inventory.getSlots();

      for (int slot = 0; slot < maxSlots; slot++) {
         // grab wares in current inventory slot
         itemStack = inventory.getStackInSlot(slot);

         // if the slot is empty, skip it
         if (itemStack == null   ||        // prevents null pointer exception
             itemStack.isEmpty() ||        // checks for air blocks
             itemStack == ItemStack.EMPTY) // checks for special empty token
            continue;

         // check whether the wares in the slot are marked as unsellable
         if (itemStack.hasTagCompound() &&
             itemStack.getTagCompound().hasKey(NBT_TAG_NOSELL) &&
             itemStack.getTagCompound().getBoolean(NBT_TAG_NOSELL))
            continue;

         // check if current item stack contains the desired item
         // or if current item stack contains the desired ore ID
         if ((!USING_ORE_NAME &&
              Item.REGISTRY.getNameForObject(itemStack.getItem()).toString().equals(wareID) &&
              (itemStack.getMetadata() == meta || itemStack.isItemStackDamageable())) ||
             (USING_ORE_NAME && doesItemstackUseOreName(oreName, itemStack))) {
            // if an OreDictionary name is being used,
            // grab the item's ware ID
            if (USING_ORE_NAME)
               itemID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString() + "&" + itemStack.getMetadata();
            else
               itemID = wareID + "&" + itemStack.getMetadata();
            // record item variation or damage

            // search the marketplace for the ware to be manipulated
            ware = Marketplace.translateAndGrab(itemID);
            // if ware is not in the market, check for a substitute
            if (ware == null && Config.allowWareTagSubstitution)
               ware = Config.userInterface.getOreDictionarySubstitution(itemID);

            // if the wares are damageable, handle potential damage
            if (itemStack.isItemStackDamageable() && itemStack.isItemDamaged()) {
               // add wares to the container
               // if the wares are damaged,
               // record how badly they are damaged
               waresFound.add(new Marketplace.Stock(itemID, ware, itemStack.getCount(),
                  ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage()));
            } else {
               waresFound.add(new Marketplace.Stock(itemID, ware, itemStack.getCount(), 1.0f));
            }
         }
      }

      return waresFound;
   }

   /**
    * Returns the ID and quantity of whatever a payer is holding or
    * null if they are not holding anything.
    * Prints an error if nothing is found.
    * <br>
    * The idea for the ware the user's is currently holding
    * is from DynamicEconomy ( https://dev.bukkit.org/projects/dynamiceconomy-v-01 ).  
    *
    * @param playerID user responsible for the trading; used to send error messages
    * @param sender   player or command block executing the command
    * @param server   host running the game instance; used for obtaining information regarding players
    * @param username display name of user responsible for the trading
    * @return ware player is holding and how much or null
    */
   public UserInterface.Handful checkHand(UUID playerID, Object sender, Object server, String username) {
      String wareID;
      float  percentWorth = 1.0f;
      ICommandSender iSender = (ICommandSender) sender;

      // get player information
      EntityPlayer player = null;
      if (username.equals(iSender.getName()) &&
          (iSender instanceof EntityPlayer))
         player = (EntityPlayer) iSender;
      else
         player = ((MinecraftServer) server).getPlayerList().getPlayerByUsername(username);

      if (player == null)
         return null; // no message to avoid needless messaging when automated

      // get whatever is in the player's hand
      ItemStack itemStack = player.getCommandSenderEntity().getHeldEquipment().iterator().next();

      // check if nothing is in the player's hand
      if (itemStack == null ||            // prevents null pointer exception
          itemStack.isEmpty() ||          // checks for air blocks
          itemStack == ItemStack.EMPTY) { // checks for special empty token
         forwardErrorToUser(iSender, PlatformStrings.ERROR_HANDS);
         return null;
      }

      // get the ware ID of whatever is in the player's hand
      if (itemStack.isItemStackDamageable()) {
         wareID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString();
         if (itemStack.isItemDamaged())
            percentWorth = ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage();
      } else {
         if (itemStack.getMetadata() == 0) {
            wareID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString();

            // in case metadata of 0 is necessary
            if (Marketplace.translateWareID(wareID).isEmpty())
               wareID += "&0";
         }
         else
            wareID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString() + "&" + itemStack.getMetadata();
      }

      // get the amount of whatever is in the player's hand
      return new UserInterface.Handful(wareID, player.inventory.currentItem,
         itemStack.getCount(), percentWorth);
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

      // try to get the player's name
      try {
         return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getProfileByUUID(playerID).getName();
      } catch (Exception e) {}

      // try to get the server or command block's name if there is one
      if (uuidToNames.containsKey(playerID))
         return uuidToNames.get(playerID);
      else
         return null;
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

      // try to get the player's UUID
      try {
         return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getGameProfileForUsername(playername).getId();
      } catch (Exception e) {}

      // create a UUID for command blocks and the server
      // map the UUID to the given, displayable name
      UUID uuid = UUID.nameUUIDFromBytes(playername.getBytes());
      uuidToNames.put(uuid, playername);
      return uuid;
   }

   /**
    * Returns the UUID associated with the given sender.
    *
    * @param sender sender's identity and properties
    * @return given sender's UUID
    */
   public static UUID getSenderID(ICommandSender sender) {
      // check whether sender is a player
      if (sender instanceof EntityPlayer)
         return sender.getCommandSenderEntity().getUniqueID();

      // if the sender is not a player, they must be a server, command block, or script
      else
         // check whether the sender is named after a player
         // if so, return the player's ID
         // if not, convert the sender's name into a UUID
         return getPlayerIDStatic(sender.getName());
   }

   /**
    * Returns whether the given string matches a player's name.
    * <p>
    * Warning: This function only checks players who have logged in recently.
    * Players who have not logged in recently will be marked as nonexistent.
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
    * but also usable in command base methods.
    *
    * @param playername player to check the existence of
    * @return whether the given string is in use as a player's name
    */
   protected static boolean doesPlayerExistStatic(String playername) {
      try {
         return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getGameProfileForUsername(playername) != null;
      } catch (Exception e) {}
      return false;
   }

   /**
    * Returns whether a player with the given unique identifier is currently logged into the server.
    * <p>
    * Complexity: O(1)
    * @param playerID UUID of player whose current status is needed
    * @return <code>true</code> if the player is currently online
    */
   public boolean isPlayerOnline(UUID playerID) {
      if (playerID == null)
         return false;

      // search for the user
      EntityPlayer user = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // report whether the specified user was found
      return user != null;
   }

   /**
    * Returns whether the player is a server administrator.
    *
    * @param playerID player whose server operator permissions should be checked
    * @return whether the player is an op
    */
   public boolean isAnOp(UUID playerID) {
      return isAnOpStatic(playerID);
   }

   /**
    * Returns whether the player is a server administrator.
    *
    * @param playerID player whose server operator permissions should be checked
    * @return whether the player is an op
    */
   public static boolean isAnOpStatic(UUID playerID) {
      if (playerID == null)
         return false;

      // check for player among server operators
      PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
      return playerList.canSendCommands(playerList.getPlayerByUUID(playerID).getGameProfile());
   }

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
   public boolean permissionToExecute(UUID playerID, Object sender, boolean isOpCommand) {
      if (playerID == null || sender == null)
         return false;

      ICommandSender iSender = (ICommandSender) sender;

      // command blocks and the server console always have permission
      if (!(iSender instanceof EntityPlayer))
         return true;

      // check if the sender is only affecting themself
      if (!isOpCommand && playerID.equals(iSender.getCommandSenderEntity().getUniqueID()))
         return true;

      // check for sender among server operators
      // to determine whether they may execute commands for other players
      return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().canSendCommands(((EntityPlayer) iSender).getGameProfile());
   }

   /**
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID    name of the player being affected by the issued command or the entity being acted upon
    * @param sender      command-issuing entity or the entity acting upon another
    * @param isOpCommand whether the sender must be an admin to execute even if the command only affects themself
    * @return true if the sender has permission to execute the command even if the command only affects themself
    */
   public static boolean permissionToExecuteStatic(UUID playerID, ICommandSender sender, boolean isOpCommand) {
      if (playerID == null || sender == null)
         return false;

      // command blocks and the server console always have permission
      if (!(sender instanceof EntityPlayer))
         return true;

      // check if the sender is only affecting themself
      if (!isOpCommand && playerID.equals(sender.getCommandSenderEntity().getUniqueID()))
         return true;

      // check for sender among server operators
      // to determine whether they may execute commands for other players
      return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().canSendCommands(((EntityPlayer) sender).getGameProfile());
   }

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
   public String parseEntitySelector(Object sender, String selector) {
      if (selector == null)
         return null;

      try {
         if (EntitySelector.isSelector(selector))
            return EntitySelector.matchOnePlayer((ICommandSender) sender, selector).getName();
      } catch (Exception e) {
         return "";
      }

      return selector;
   }

   /**
    * Returns whether the item stack uses the given ore name.
    *
    * @param oreName   the Forge OreDictionary name
    * @param itemstack the item stack to be checked
    * @return whether the item stack uses the given ore name
    */
   private static boolean doesItemstackUseOreName(String oreName,
      ItemStack itemStack) {
      if (!OreDictionary.doesOreNameExist(oreName) || itemStack == null || itemStack.isEmpty() || itemStack == ItemStack.EMPTY)
         return false;

      // Below, numerical ore IDs are used to find
      // whether the item stack uses the given ore name.
      // This method is used since comparing the given ore ID to
      // all of the item stack's ore IDs is given  faster than
      // the alternative of comparing the item stack to
      // all item stacks using the given ore name.
      // Item stacks generally use fewer ore IDs than
      // there are item stacks using a given ore name.

      // convert the ore name to its corresponding numerical ID
      int keyID = OreDictionary.getOreID(oreName);

      // get the item stack's numerical OreDictionary IDs
      int[] oreIDs = OreDictionary.getOreIDs(itemStack);

      // compare the given ore ID to the item stack's ore IDs
      for (int oreID : oreIDs)
         if (oreID == keyID)
            return true;

      // if the given ore ID was not found
         return false;
   }

   /**
    * Returns whether or not a given Forge OreDictionary name exists outside of the market.
    *
    * @param name the Forge OreDictionary name
    * @return true if the name exists outside of the market
    */
   public boolean doesOreDictionaryNameExist(String name) {
      return OreDictionary.doesOreNameExist(name);
   }

   /**
    * Returns whether or not a given ware exists outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return true if the ware exists outside of the market
    */
   public boolean doesWareExist(String wareID) {
      // set up variables
      int meta = 0; // represents either the ware's variant type or accumulated damage
      int ampersandPosition = wareID.indexOf('&'); // find if and where variation begins

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            return false;
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // if the given ID is not a variant,
      // then only check whether the item's base ID is valid
      if (meta == 0 || Config.itemExistenceCheckIgnoresMeta)
         return Item.REGISTRY.containsKey(new ResourceLocation(wareID));

      // grab the ware's non-variant form
      Item item = null;
      try {
         item = Item.REGISTRY.getObject(new ResourceLocation(wareID));
      } catch (Exception e) {
         return false;
      }

      // if the given ID is a variant
      // and its base ID has no variants
      if (item == null || !item.getHasSubtypes())
         return false;

      // check possible variants
      NonNullList<ItemStack> variants = NonNullList.create();
      item.getSubItems(CreativeTabs.SEARCH, variants);
      for (ItemStack itemStack : variants)
         if (itemStack.getMetadata() == meta)
            return true;

      // none of the possible variants matched
      return false;
   }

   /**
    * Returns how many a stack of the ware may hold outside of the market.
    *
    * @param wareID unique ID used to refer to the ware
    * @return the maximum amount a single stack may hold
    */
   public int getStackSize(String wareID) {
      // check if ware id is empty
      if (wareID == null || wareID.isEmpty())
         return -1; // don't return 0 since it might cause a divide-by-error error

      // set up variables
      int meta = 0; // represents either the ware's variant type or accumulated damage
      int ampersandPosition = wareID.indexOf('&'); // find if and where variation begins

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            return -1; // don't return 0 since it might cause a divide-by-error error
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // try to get the desired item using the ware ID
      try {
         Item item = Item.REGISTRY.getObject(new ResourceLocation(wareID));
         ItemStack itemstack = new ItemStack(item, 1, meta);
         return itemstack.getMaxStackSize();
      } catch (Exception e) {
         // warn the console
         printToConsole(PlatformStrings.ERROR_CHECKING_ITEM + PlatformStrings.ERROR_ITEM_NOT_FOUND + wareID);
      }

      return -1; // don't return 0 since it might cause a divide-by-error error
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
      if (wareID == null || wareID.isEmpty())
         return null;

      // set up variables
      Item      item              = null;                // in-game/external version of the ware
      ItemStack itemStack         = null;                // used to check for item variants
      String    substituteID      = null;                // ID of the ware to be used in place of the given ware
      int       meta              = 0;                   // represents either the ware's variant type or accumulated damage
      int       ampersandPosition = wareID.indexOf('&'); // find if and where variation begins

      // Retrieve the item corresponding to the given ID
      // so its ore names may be checked.

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            return null;
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // grab the ware's non-variant form
      try {
         item = Item.REGISTRY.getObject(new ResourceLocation(wareID));
      } catch (Exception e) {
         return null;
      }

      // if the ware ID doesn't correspond to any item, stop
      if (item == null)
         return null;

      // if the given ID is a variant, search for it
      if (meta != 0) {
         // if no variants exist
         if (!item.getHasSubtypes()) {
            // and variants should be checked, stop
            if (!Config.itemExistenceCheckIgnoresMeta)
               return null;
            else
               itemStack = new ItemStack(item); // use the base item
         }
         // otherwise, search among the base item's variants
         else {
            NonNullList<ItemStack> variants = NonNullList.create();
            item.getSubItems(CreativeTabs.SEARCH, variants);
            for (ItemStack itemStackVariant : variants)
               if (itemStackVariant.getMetadata() == meta)
                  itemStack = itemStackVariant;

            if (itemStack == null)
               return null;
         }
      }
      // if the item isn't a variant, use the base item
      else
         itemStack = new ItemStack(item);

      // get the item stack's numerical OreDictionary IDs
      int[] oreIDs = OreDictionary.getOreIDs(itemStack);

      // if there are no ore names, there is no substitution
      if (oreIDs == null || oreIDs.length == 0)
         return null;

      // check registered aliases for each ore name
      for (int oreID : oreIDs) {
         substituteID = Marketplace.translateAlias('#' + OreDictionary.getOreName(oreID));
         if (substituteID != null)
            return Marketplace.translateAndGrab(substituteID);
      }

      // no substitution was found
      return null;
   }

   /**
    * Forwards a message to the specified user.
    *
    * @param playerID who to give the message to
    * @param message  what to tell the user
    */
   public void printToUser(UUID playerID, String message) {
      if (message == null || message.isEmpty() ||
          playerID == null)
         return;

      // make sure the console can use commands too
      if (playerID.equals(UUID.fromString("3c00b58d-a066-4f52-a2b7-615641dcbe00"))) {
         System.out.println(message);
         return;
      }

      // find the user to send the message to
      EntityPlayer user = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // if the specified user was found, try to send the message
      if (user != null) {
         TextComponentString formattedMessage = new TextComponentString(message);
         user.sendMessage(formattedMessage);
      }
   }

   /**
    * Forwards a message to all users.
    *
    * @param message what to tell the users
    */
   public void printToAllUsers(String message) {
      if (message  == null || message.isEmpty())
         return;

      TextComponentString formattedMessage = new TextComponentString(message);

      // print to all players currently online
      for (EntityPlayer user : FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayers())
         user.sendMessage(formattedMessage);
   }

   /**
    * Forwards an message to a given user.
    *
    * @param player  who to give the message to
    * @param message what to tell the user
    */
   public static void forwardToUser(ICommandSender player, String message) {
      if (message == null || message.isEmpty() ||
          player == null)
         return;

      // prepare to tell the user if something is wrong
      TextComponentString errorMessage = new TextComponentString(message);
      player.sendMessage(errorMessage);
   }

   /**
    * Forwards an error message to a given user.
    *
    * @param player  who to give the message to
    * @param message what to tell the user
    */
   public static void forwardErrorToUser(ICommandSender player, String message) {
      if (message == null || message.isEmpty() ||
          player == null)
         return;

      // prepare to tell the user if something is wrong
      TextComponentString errorMessage = new TextComponentString(message);
      errorMessage.getStyle().setColor(TextFormatting.RED);
      player.sendMessage(errorMessage);
   }

   /**
    * Forwards an error message to the specified user.
    *
    * @param playerID who to give the message to
    * @param message  what to tell the user
    */
   public void printErrorToUser(UUID playerID, String message) {
      if (message == null || message.isEmpty() ||
          playerID == null)
         return;

      // make sure the console can use commands too
      if (playerID.equals(UUID.fromString("3c00b58d-a066-4f52-a2b7-615641dcbe00"))) {
         System.err.println(message);
         return;
      }

      // find the user to send the message to
      EntityPlayer user = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // if the specified user was found, try to send the message
      if (user != null) {
         TextComponentString formattedMessage = new TextComponentString(message);
         formattedMessage.getStyle().setColor(TextFormatting.RED);
         user.sendMessage(formattedMessage);
      }
   }

   /**
    * Handles the contents for an error message normal users shouldn't necessarily see.
    *
    * @param message error encountered and possible details
    */
   public void printToConsole(String message) {
      if (message == null || message.isEmpty())
         return;

      System.out.println(message); // tell the server console
   }

   /**
    * CommandBase classes handle servicing requests.
    */
   public void serviceRequests() { }

   /**
    * Registers serviceable chat commands.
    *
    * @param event information concerning Minecraft's current state
    */
   public static void registerCommands(FMLServerStartedEvent event) {
      System.out.println(PlatformStrings.MSG_REGISTER_COMMANDS); // tell the server console

      // set up commands for both clients and the server
      CommandHandler handler = ((CommandHandler) FMLCommonHandler.instance().getSidedDelegate().getServer().getCommandManager());

      // register commands
      handler.registerCommand(commandBuy);
      handler.registerCommand(commandSell);
      handler.registerCommand(commandCheck);
      handler.registerCommand(commandSellAll);
      handler.registerCommand(commandNoSell);
      handler.registerCommand(commandMoney);
      handler.registerCommand(commandSend);
      handler.registerCommand(commandCreate);
      handler.registerCommand(commandDelete);
      handler.registerCommand(commandGrantAccess);
      handler.registerCommand(commandRevokeAccess);
      handler.registerCommand(commandAdd);
      handler.registerCommand(new CommandSaveCE());
      handler.registerCommand(new CommandGeneral());
      handler.registerCommand(new CommandPrintMarket());
      handler.registerCommand(new CommandChangeStock());
      if (Config.researchCostPerHierarchyLevel != 0.0f)
         handler.registerCommand(new CommandResearch());
   }

   /**
    * Returns a list of strings with a specific prefix for autocompleting command arguments.
    *
    * @param prefix         what each string in the list should start with; can be empty or null
    * @param stringCategory enumeration value referencing a specific list of strings
    * @return list of strings starting with the given argument
    */
   public static List<String> getAutoCompletionStrings(String prefix, AutoCompletionStringCategories stringCategory) {
      List<String> autoCompletionStrings = new LinkedList<String>();

      // prevent null pointer exception
      if (prefix == null)
         prefix = "";

      // generate list based on category requested
      switch (stringCategory) {
         case ACCOUNTS:
            findAutoCompletionStrings(prefix, accountIDs, autoCompletionStrings);
            break;

         case INVENTORY:
            for (String direction : INVENTORY_KEYWORDS) {
               if (direction.startsWith(prefix))
                  autoCompletionStrings.add(direction);
            }
            break;

         case PLAYERS:
            for (String username : FMLCommonHandler.instance().getMinecraftServerInstance().getOnlinePlayerNames()) {
               if (username.startsWith(prefix))
                  autoCompletionStrings.add(username);
            }
            break;

         case WARES:
            findAutoCompletionStrings(prefix, wareAliases, autoCompletionStrings);
      }

      return autoCompletionStrings;
   }

   /**
    * Returns a list of strings with a specific prefix for autocompleting command arguments.
    *
    * @param prefix what each string in the list should start with; can be empty or null
    * @param possibleCompletionStrings possible choices
    * @return list of strings starting with the given argument
    */
   public static List<String> getAutoCompletionStrings(String prefix, String[] possibleCompletionStrings) {
      if (possibleCompletionStrings == null || possibleCompletionStrings.length == 0)
         return null;

      List<String> autoCompletionStrings = new LinkedList<String>();

      // prevent null pointer exception
      if (prefix == null)
         prefix = "";

      // traverse through all possible values and
      // grab each plausible one
      for (String possibleCompletionString : possibleCompletionStrings)
         if (possibleCompletionString.startsWith(prefix))
            autoCompletionStrings.add(possibleCompletionString);

      return autoCompletionStrings;
   }

   /**
    * Adds strings with a specific prefix to a given string list.
    * <br>
    * Complexity: O(ln n), where n is the number of potential matches
    * @param prefix                term to be finished
    * @param potentialMatches      all valid possibilities for finishing the term
    * @param autoCompletionStrings string list to be augmented
    */
   private static void findAutoCompletionStrings(String prefix,Set<String> potentialMatches,
                                          List<String> autoCompletionStrings) {
      // if no matches are given or can be returned, stop
      if (prefix == null || potentialMatches == null || autoCompletionStrings == null)
         return;

      // grab all potential matches that might contain the prefix
      Set<String> tailSet = ((TreeSet<String>) potentialMatches).tailSet(prefix);

      // add all potential matches starting with the prefix
      for (String potentialMatch : tailSet) {
         if (potentialMatch.startsWith(prefix))
            autoCompletionStrings.add(potentialMatch);
         else // matches beginning with the prefix are listed first, so stop when the prefix isn't found
            break;
      }
   }

   /**
    * Updates a tree of account IDs used for autocompletion with current values.
    * <br>
    * Complexity: O(n ln n), where n is the number of account IDs
    */
   public static void sortAccountIDs() {
      // use a tree to sort unique IDs
      // to optimize lookup times
      accountIDs = new TreeSet<String>();

      // add each ID to the tree
      accountIDs.addAll(Account.getAllAccountNames());
   }

   /**
    * Updates a tree of ware aliases used for autocompletion with current values.
    * <br>
    * Complexity: O(n ln n), where n is the number of ware aliases
    */
   public static void sortWareAliases() {
      // use a tree to sort unique aliases
      // to optimize lookup times
      wareAliases = new TreeSet<String>();

      // add each ID to the tree
      wareAliases.addAll(Marketplace.getAllWareAliases());
   }

   /**
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID name of the player being affected by the issued command or the entity being acted upon
    * @param sender   command-issuing entity or the entity acting upon other
    * @return true if the sender has permission to execute the command
    */
   public static boolean permissionToExecuteStatic(UUID playerID, ICommandSender sender) {
      if (playerID == null || sender == null)
         return false;

      // command blocks and the server console always have permission
      if (!(sender instanceof EntityPlayer))
         return true;

      // check if the sender is only affecting themself
      if (playerID.equals(sender.getCommandSenderEntity().getUniqueID()))
         return true;

      // check for sender among server operators
      // to determine whether they may execute commands for other players
      return FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().canSendCommands(((EntityPlayer) sender).getGameProfile());
   }
}