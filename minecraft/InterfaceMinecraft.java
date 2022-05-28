package commandeconomy;

import java.util.LinkedList;                                       // for returning properties of wares found in an inventory and autocompleting arguments
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;  // for initializing upon game's start
import net.minecraft.command.CommandHandler;                       // for registering commands
import java.util.List;                                             // for autocompleting command arguments
import net.minecraft.util.text.TextComponentString;                // for sending messages to users
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;             // for finding and interacting with specific users and getting usernames when autocompleting arguments
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;          // for accessing player inventories
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;                                    // for converting between items and ware stock levels and getting items' ware IDs
import net.minecraft.util.ResourceLocation;                        // for finding items based on namespaced ids
import net.minecraftforge.items.IItemHandler;                      // for handling player and block inventories
import net.minecraft.tileentity.TileEntity;                        // for handling block inventories
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraft.util.math.BlockPos;                           // for finding block inventories
import net.minecraft.util.NonNullList;                          // for checking the existence of item subtypes when validating ware IDs
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.command.ICommandSender;                    // for checking for server operator permissions, converting sender names to UUIDs, and simplifying sending error messages
import net.minecraftforge.oredict.OreDictionary;                // for checking Forge OreDictionary names
import net.minecraft.server.management.PlayerList;              // for checking server operator permissions
import java.util.UUID;                                          // for more securely tracking users internally
import java.util.HashMap;                                       // for mapping server and command block UUIDs to displayable names
import net.minecraft.command.EntitySelector;                    // for using command block selectors
import net.minecraft.command.CommandBase;                   // for providing other classes access to command handlers
import net.minecraftforge.common.DimensionManager;          // for getting paths to per-world save and config files
import java.io.File;                                        // for adding a file separator to the singleplayer save directory

/**
 * Contains functions for interacting with chat commands within Minecraft.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-28
 */
public class InterfaceMinecraft implements InterfaceCommand
{
   // GLOBAL VARIABLES
   /** maps the server and command block's UUIDs to displayable names */
   private static HashMap<UUID, String> uuidToNames = new HashMap<UUID, String>();

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

   // FUNCTIONS
   /**
    * Returns the path to the local game's save directory.
    *
    * @return directory of local save and config files
    */
   public String getSaveDirectory() {
      return DimensionManager.getCurrentSaveRootDirectory().getPath();
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
      IItemHandler inventory = getInventory(playerID, coordinates);

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
      else {
         maxSlots = inventory.getSlots();
      }

      for (int i = 0; i < maxSlots; i++) {
         if (inventory.getStackInSlot(i).isEmpty() ||        // checks for air blocks
             inventory.getStackInSlot(i) == ItemStack.EMPTY) // checks for empty/null
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
   public void addToInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID, int quantity) {
      // if no items should be added, do nothing
      if (quantity <= 0)
         return;

      // prepare to message the player
      EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // grab the right inventory
      IItemHandler inventory = getInventory(playerID, coordinates);

      // check if the given ID is a variant
      int meta = 0;
      int ampersandPosition = wareID.indexOf("&"); // find if and where variation begins
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            printToConsole("adding item - could not parse meta for " + wareID);
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString("adding item - could not parse meta for " + wareID);
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
         else {
            maxSlots = inventory.getSlots();
         }

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
         printToConsole(
            "commandeconomy - InterfaceMinecraft.addToInventory(), error - could not find corresponding item for " + wareID +
            "\n   is it a modded item which doesn't exist in-game?"
         );

         // warn the player
         if (player != null) {
            TextComponentString errorMessage = new TextComponentString(
               "error - could not find corresponding item for " + wareID +
               "\n   is it a modded item which doesn't exist in-game?"
            );
            errorMessage.getStyle().setColor(TextFormatting.RED);
            player.sendMessage(errorMessage);
         }
      }
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

      // check if ware id is empty
      if (wareID == null || wareID.isEmpty())
         return;

      // prepare to message the player
      EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // grab the right inventory
      IItemHandler inventory = getInventory(playerID, coordinates);
      // if no inventory was found
      if (inventory == null)
         return;

      // set up variables
      int meta = 0;            // represents either the ware's variant type or accumulated damage
      int ampersandPosition = wareID.indexOf("&"); // find if and where variation begins
      ItemStack itemStack;     // wares in the slot being parsed
      int maxSlots = 0;        // size of the inventory
      int quantityLeftover;    // the parsed slot's quantity if all quantity to be removed is taken from it
      String oreName = "";       // holds the Forge OreDictionary name being used
      boolean usingOreName = wareID.startsWith("#");

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            printToConsole("removing item - could not parse meta for " + wareID);
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString("removing item - could not parse meta for " + wareID);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // if an Forge OreDictionary name is being used,
      // try to get the ore name
      if (usingOreName) {
         oreName = wareID.substring(1, wareID.length());

         if (!OreDictionary.doesOreNameExist(oreName)) {
            // warn the console
            printToConsole("removing item - Forge OreDictionary name not found: " + oreName);

            // warn the player
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString("removing item - Forge OreDictionary name not found: " + oreName);
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
      else {
         maxSlots = inventory.getSlots();
      }

      for (int slot = 0; slot < maxSlots; slot++) {
         itemStack = inventory.getStackInSlot(slot);

         // if the slot is empty, skip it
         if (itemStack.isEmpty() ||        // checks for air blocks
             itemStack == ItemStack.EMPTY) // checks for empty/null
            continue;

         // check whether the item stack is marked as unsellable
         if (itemStack.hasTagCompound() &&
             itemStack.getTagCompound().hasKey("nosell") &&
             itemStack.getTagCompound().getBoolean("nosell"))
            continue;

         // check if current item stack contains the desired item
         // or if current item stack contains the desired ore ID
         if ((!usingOreName &&
              Item.REGISTRY.getNameForObject(itemStack.getItem()).toString().equals(wareID) &&
              (itemStack.getMetadata() == meta || itemStack.isItemStackDamageable())) ||
             (usingOreName &&
               doesItemstackUseOreName(oreName, itemStack))) {
            // pull from the item stack
            itemStack = inventory.extractItem(slot, quantity, false);

            // if nothing was extracted, there is nothing more to take
            if (itemStack == null)
               break;

            // find out how much more should be taken
            quantity -= itemStack.getCount();
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
   public LinkedList<Marketplace.Stock> checkInventory(UUID playerID, InterfaceCommand.Coordinates coordinates,
      String wareID) {
      // prepare a container for the wares
      LinkedList<Marketplace.Stock> waresFound = new LinkedList<Marketplace.Stock>();
      waresFound.add(new Marketplace.Stock(wareID, 0, 1.0f));

      // check if ware id is empty
      if (wareID == null || wareID.isEmpty())
         return waresFound;

      // prepare to message the player
      EntityPlayer player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUUID(playerID);

      // grab the right inventory
      IItemHandler inventory = getInventory(playerID, coordinates);
      // if no inventory was found
      if (inventory == null) {
         waresFound.getFirst().quantity = -1;
         return waresFound;
      }

      // set up variables
      int meta     = 0;    // represents either the ware's variant type or accumulated damage
      int ampersandPosition = wareID.indexOf("&"); // find if and where variation begins
      ItemStack itemStack;    // wares in the slot being parsed
      String itemID = "";      // temporary variable for writing ware IDs for each item stack
      int quantity = 0;       // total amount of wares found
      float percentWorth;     // holds ware's worth based on how damaged the ware is
      int maxSlots = 0;        // size of the inventory
      String oreName = "";       // holds the Forge OreDictionary name being used
      boolean usingOreName = wareID.startsWith("#");

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         // save meta for item ID to the side
         try {
            meta = Integer.parseInt(wareID.substring(ampersandPosition + 1, wareID.length()));
         } catch (NumberFormatException e) {
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString("checking item - could not parse meta for " + wareID);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
         }

         // separate meta from the item ID
         wareID = wareID.substring(0, ampersandPosition);
      }

      // if an Forge OreDictionary name is being used,
      // try to get the ore name
      if (usingOreName) {
         oreName = wareID.substring(1, wareID.length());

         if (!OreDictionary.doesOreNameExist(oreName)) {
            // warn the console
            printToConsole("checking item - Forge OreDictionary name not found: " + oreName);

            // warn the player
            if (player != null) {
               TextComponentString errorMessage = new TextComponentString("checking item - Forge OreDictionary name not found: " + oreName);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               player.sendMessage(errorMessage);
            }
            return waresFound;
         }
      }

      // search for the ware within the player's inventory
      if (inventory instanceof PlayerInvWrapper) {
         // Only check slots 0-35 since those compose the main inventory and the hotbar.
         maxSlots = 36;
      }
      // for other inventories, check everything
      else {
         maxSlots = inventory.getSlots();
      }

      for (int slot = 0; slot < maxSlots; slot++) {
         // grab wares in current inventory slot
         itemStack = inventory.getStackInSlot(slot);

         // if the slot is empty, skip it
         if (itemStack.isEmpty() ||        // checks for air blocks
             itemStack == ItemStack.EMPTY) // checks for empty/null
            continue;

         // check whether the wares in the slot are marked as unsellable
         if (itemStack.hasTagCompound() &&
             itemStack.getTagCompound().hasKey("nosell") &&
             itemStack.getTagCompound().getBoolean("nosell"))
            continue;

         // check if current item stack contains the desired item
         // or if current item stack contains the desired ore ID
         if ((!usingOreName &&
              Item.REGISTRY.getNameForObject(itemStack.getItem()).toString().equals(wareID) &&
              (itemStack.getMetadata() == meta || itemStack.isItemStackDamageable())) ||
             (usingOreName &&
               doesItemstackUseOreName(oreName, itemStack))) {
            // update total amount of wares found
            quantity += itemStack.getCount();

            // if an OreDictionary name is being used
            // and the item exists in the market,
            // use the item's ware ID
            if (usingOreName &&
                !Marketplace.translateWareID(Item.REGISTRY.getNameForObject(itemStack.getItem()).toString()).isEmpty()) {
               itemID = Marketplace.translateWareID(Item.REGISTRY.getNameForObject(itemStack.getItem()).toString());
            } else {
               itemID = wareID;
            }

            // if the wares are damageable, handle potential damage
            if (itemStack.isItemStackDamageable() && itemStack.isItemDamaged()) {
               // add wares to the container
               // if the wares are damaged,
               // record how badly they are damaged
               waresFound.add(new Marketplace.Stock(itemID + "&" + itemStack.getMetadata(), itemStack.getCount(),
                  ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage()));
            } else {
               if (itemStack.getMetadata() != 0 ||
                   Marketplace.translateWareID(wareID).isEmpty()) {
                  itemID += "&" + itemStack.getMetadata();
               }

               waresFound.add(new Marketplace.Stock(itemID,
                  itemStack.getCount(), 1.0f));
            }
         }
      }

      // update record for total quantity
      waresFound.getFirst().quantity = quantity;

      return waresFound;
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
      if (!OreDictionary.doesOreNameExist(oreName) || itemStack.isEmpty() || itemStack == ItemStack.EMPTY)
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
    * Returns the inventory which should be used.
    *
    * @param playerID    user responsible for the trading
    * @param coordinates where the inventory may be found
    * @return inventory to be manipulated
    */
   public static IItemHandler getInventory(UUID playerID,
      InterfaceCommand.Coordinates coordinates) {
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

      if (tileentity != null) {
         itemHandler = tileentity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
      }
      if (itemHandler != null)
            return itemHandler;

      // if the inventory hasn't been found,
      // return null to signal an invalid coordinate
      return null;
   }

   /**
    * Returns the player name associated with the given UUID.
    *
    * @param playerID UUID of whose name should be found
    * @return player name corresponding to given UUID
    */
   public String getDisplayName(UUID playerID) {
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
         return "";
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
      try {
         for (String username : FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getUsernames()) {
            if (username.equals(playername))
               return true;
         }
      } catch (Exception e) {}
      return false;
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
         return (FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerProfileCache().getGameProfileForUsername(playername) == null);
      } catch (Exception e) {}
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
      int ampersandPosition = wareID.indexOf("&"); // find if and where variation begins

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

      // grab the non-variant form of the ware
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
      for (ItemStack itemStack : variants) {
         if (itemStack.getMetadata() == meta)
            return true;
      }

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
      int ampersandPosition = wareID.indexOf("&"); // find if and where variation begins

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
         printToConsole("checking item - could not parse " + wareID);
      }

      return -1; // don't return 0 since it might cause a divide-by-error error
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
   public void serviceRequests() {
      return;
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

      // check for player among server operators
      PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
      if (playerList.canSendCommands(playerList.getPlayerByUUID(playerID).getGameProfile()))
         return true;

      return false;
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
      if (playerList.canSendCommands(playerList.getPlayerByUUID(playerID).getGameProfile()))
         return true;

      return false;
   }

   /**
    * Registers serviceable chat commands.
    *
    * @param event information concerning Minecraft's current state
    */
   public static void registerCommands(FMLServerStartedEvent event) {
      System.out.println("Registering serviceable commands...."); // tell the server console

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
      if (Config.investmentCostPerHierarchyLevel != 0.0f)
         handler.registerCommand(new CommandInvest());
   }

   /**
    * Returns a list of strings with a specific prefix for autocompleting command arguments.
    *
    * @param arg            what each string in the list should start with; can be empty or null
    * @param stringCategory enumeration value referencing a specific list of strings
    * @return list of strings starting with the given argument
    */
   public static List<String> getAutoCompletionStrings(String arg, AutoCompletionStringCategories stringCategory) {
      LinkedList<String> autoCompletionStrings = new LinkedList<String>();

      // prevent null pointer exception
      if (arg == null)
         arg = "";

      // generate list based on category requested
      switch (stringCategory) {
         case ACCOUNTS:
            for (String account : Account.getAllAccountNames()) {
               if (account.startsWith(arg))
                  autoCompletionStrings.add(account);
            }
            break;

         case INVENTORY:
            for (String direction : INVENTORY_KEYWORDS) {
               if (direction.startsWith(arg))
                  autoCompletionStrings.add(direction);
            }
            break;

         case PLAYERS:
            for (String username : FMLCommonHandler.instance().getMinecraftServerInstance().getOnlinePlayerNames()) {
               if (username.startsWith(arg))
                  autoCompletionStrings.add(username);
            }
            break;

         case WARES:
            for (String ware : Marketplace.getAllWareAliases()) {
               if (ware.startsWith(arg))
                  autoCompletionStrings.add(ware);
            }
      }

      return autoCompletionStrings;
   }

   /**
    * Returns a list of strings with a specific prefix for autocompleting command arguments.
    *
    * @param arg what each string in the list should start with; can be empty or null
    * @param possibleCompletionStrings possible choices
    * @return list of strings starting with the given argument
    */
   public static List<String> getAutoCompletionStrings(String arg, String[] possibleCompletionStrings) {
      if (possibleCompletionStrings == null || possibleCompletionStrings.length == 0)
         return null;

      LinkedList<String> autoCompletionStrings = new LinkedList<String>();

      // prevent null pointer exception
      if (arg == null)
         arg = "";

      // traverse through all possible values and
      // grab each plausible one
      for (String possibleCompletionString : possibleCompletionStrings) {
         if (possibleCompletionString.startsWith(arg))
            autoCompletionStrings.add(possibleCompletionString);
      }

      return autoCompletionStrings;
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
   public static boolean permissionToExecute(UUID playerID, ICommandSender sender) {
      if (playerID == null || sender == null)
         return false;

      // command blocks and the server console always have permission
      if (!(sender instanceof EntityPlayer))
         return true;

      // check if the sender is only affecting themself
      if (playerID.equals(sender.getCommandSenderEntity().getUniqueID()))
         return true;

      // check for sender among server operators
      if (FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().canSendCommands(((EntityPlayer) sender).getGameProfile()))
         return true;

      // if the sender is not a server operator,
      // they may not execute commands for other players
      return false;
   }

   /**
    * Returns whether or not a command-issuer may execute a given command.
    * Useful for when a command is executed for another player,
    * such as when a command block is autobuying.
    *
    * @param playerID name of the player being affected by the issued command or the entity being acted upon
    * @param sender   command-issuing entity or the entity acting upon other
    * @param mustBeOp whether the sender must be an admin to execute
    * @return true if the sender has permission to execute the command even if the command only affects themself
    */
   public static boolean permissionToExecute(UUID playerID, ICommandSender sender, boolean mustBeOp) {
      if (playerID == null || sender == null)
         return false;

      // command blocks and the server console always have permission
      if (!(sender instanceof EntityPlayer))
         return true;

      // check if the sender is only affecting themself
      if (!mustBeOp && playerID.equals(sender.getCommandSenderEntity().getUniqueID()))
         return true;

      // check for sender among server operators
      if (FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().canSendCommands(((EntityPlayer) sender).getGameProfile()))
         return true;

      // if the sender is not a server operator,
      // they may not execute commands for other players
      return false;
   }
}