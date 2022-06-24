package commandeconomy;

import java.util.LinkedList;                                // for returning properties of wares found in an inventory
import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import java.util.HashMap;                                   // for convert the player's inventory into a format usable within the marketplace
import net.minecraftforge.items.IItemHandler;               // for handling player and block inventories
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;                 // for finding namespaced ids based on items
import net.minecraft.util.math.BlockPos;                    // for handling coordinates
import net.minecraft.command.EntitySelector;                // for using command block selectors
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage
import java.util.List;                                      // for autocompleting arguments and sending command aliases
import net.minecraftforge.oredict.OreDictionary;            // for checking Forge OreDictionary names
import java.util.UUID;                                      // for more securely tracking users internally
import java.util.Arrays;                                    // for storing command aliases

public class CommandSellAll extends CommandBase {
   private final List<String> aliases = Arrays.asList(CommandEconomy.CMD_SELLALL_LOWER);

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // request can be null or have zero arguments
      // except for the command block variant
      if (!(sender instanceof EntityPlayer) &&
          (args == null || args.length == 0)) {
         InterfaceMinecraft.forwardErrorToUser(sender, getUsage(sender));
         return;
      }

      // command must have the right number of args
      if (args != null &&
          (args.length < 0 ||
           args.length > 3)) {
         System.out.println();
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SELLALL);
         return;
      }

      // check for zero-length args
      if (args != null &&
          ((args.length >= 1 && (args[0] == null || args[0].length() == 0)) ||
           (args.length >= 2 && (args[1] == null || args[1].length() == 0)) ||
           (args.length == 3 && (args[2] == null || args[2].length() == 0)))) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SELLALL);
         return;
      }

      // set up variables
      String      username        = null;
      InterfaceCommand.Coordinates coordinates     = null;
      String      accountID       = null;
      // prepare a reformatted container for the wares
      LinkedList<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();
      IItemHandler inventoryToUse = null;

      // if there are at least two arguments,
      // a username and a direction must be given
      if (args != null && args.length >= 2) {
         username = args[0];

         // translate coordinates
         BlockPos position = sender.getPosition();
         if (position == null) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_POSITION_MISSING + CommandEconomy.CMD_USAGE_BLOCK_SELLALL);
            return;
         }

         switch(args[1])
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
               InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_INVENTORY_DIR + CommandEconomy.CMD_USAGE_BLOCK_SELLALL);
               return;
         }
         coordinates = new InterfaceCommand.Coordinates(position.getX(), position.getY(), position.getZ(), sender.getEntityWorld().provider.getDimension());

         // if an account ID was given, use it
         if (args.length == 3)
            accountID = args[2];
         // If an account ID wasn't given, leave the ID as null.
      } else {
         username    = sender.getName();
         coordinates = null;

         // if an account ID was given, use it
         if (args != null && args.length == 1)
            accountID = args[0];
         // If an account ID wasn't given, leave the ID as null.
      }

      // check for entity selectors
      try {
         if (username != null && EntitySelector.isSelector(username))
            username = EntitySelector.matchOnePlayer(sender, username).getName();

         if (accountID != null && EntitySelector.isSelector(accountID))
            accountID = EntitySelector.matchOnePlayer(sender, accountID).getName();
      } catch (Exception e) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }

      // grab user's UUID once
      UUID userID = InterfaceMinecraft.getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!InterfaceMinecraft.permissionToExecute(userID, sender)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // get the inventory
      inventoryToUse = InterfaceMinecraft.getInventory(userID, coordinates);
      if (inventoryToUse == null) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_INVENTORY_MISSING + CommandEconomy.CMD_USAGE_BLOCK_SELLALL);
         return;
      }

      // set up variables
      ItemStack itemStack; // wares in the slot being parsed
      String itemID = "";  // temporary variable for writing ware IDs for each item stack
      float percentWorth;  // holds ware's worth based on how damaged the ware is
      int maxSlots = 0;    // size of the inventory

      // search for sellable wares within the player's inventory
      if (inventoryToUse instanceof PlayerInvWrapper) {
         // Only check slots 0-35 since those compose the main inventory and the hotbar.
         maxSlots = 36;
      }
      // for other inventories, check everything
      else {
         maxSlots = inventoryToUse.getSlots();
      }

      for (int slot = 0; slot < maxSlots; slot++) {
         // grab wares in current inventory slot
         itemStack = inventoryToUse.getStackInSlot(slot);

         // if the slot is empty, skip it
         if (itemStack.isEmpty() ||        // checks for air blocks
             itemStack == ItemStack.EMPTY) // checks for empty/null
            continue;

         // check whether the wares in the slot are marked as unsellable
         if (itemStack.hasTagCompound() &&
             itemStack.getTagCompound().hasKey("nosell") &&
             itemStack.getTagCompound().getBoolean("nosell"))
            continue;

            // get item's ware ID
            itemID = Marketplace.translateWareID(Item.REGISTRY.getNameForObject(itemStack.getItem()).toString() + "&" + itemStack.getMetadata());
            // if the item and its variation is not in the market,
            // check whether the item has an ore name within the market
            if (itemID.isEmpty() && Config.allowOreDictionarySubstitution) {
               // get the item stack's numerical OreDictionary IDs
               int[] oreIDs = OreDictionary.getOreIDs(itemStack);

               // loop through item stack's used ore names and
               // use the first one which is used within the market
               for (int oreID : oreIDs) {
                  // get current ore ID's model ware ID
                  itemID = Marketplace.translateAlias("#" + OreDictionary.getOreName(oreID));

                  // if a model ware is found, use it
                  if (itemID != null)
                     break;
               }
            }

         // add item stack to list of potentially sellable wares
         // if the wares are damageable, handle potential damage
         if (itemStack.isItemStackDamageable() && itemStack.isItemDamaged()) {
            // add wares to the container
            // if the wares are damaged,
            // record how badly they are damaged
            formattedInventory.add(new Marketplace.Stock(itemID
               + "&" + itemStack.getMetadata(), itemStack.getCount(),
               ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage()));
         } else {
            formattedInventory.add(new Marketplace.Stock(itemID,
               itemStack.getCount(), 1.0f));
         }
      }

      Marketplace.sellAll(userID, coordinates, formattedInventory, accountID);
      return;
  }

   @Override
   public String getName() {
      return CommandEconomy.CMD_SELLALL;
   }

   @Override
   public List<String> getAliases() {
      return aliases;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return CommandEconomy.CMD_USAGE_SELL;
      else
         return CommandEconomy.CMD_USAGE_BLOCK_SELLALL;
   }

   @Override
   public int getRequiredPermissionLevel()
   {
      return 0;
   }

   /* Returns true if the given command sender is allowed to use this command. */
   @Override
   public boolean checkPermission(MinecraftServer server, ICommandSender sender)
   {
      // permission to execute the command for
      // other players is checked within execute()
      return true;
   }

   @Override
   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
   {
      if (args == null || args.length == 0)
         return new LinkedList<String>();

      if (sender instanceof EntityPlayer) {
         if (args.length == 1)
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
         else
            return new LinkedList<String>();
      } else {
         switch(args.length)
         {
            case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
            case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], InterfaceMinecraft.AutoCompletionStringCategories.INVENTORY);
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            default: return new LinkedList<String>();
         }
      }
   }

   @Override
   public boolean isUsernameIndex(java.lang.String[] args, int index)
   {
      // there doesn't appear to be a good way to check
      // whether to use the command block variant
      // without knowing who/what the sender is
      return false;
   }
}