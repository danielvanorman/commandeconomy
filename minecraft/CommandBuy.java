package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;                    // for handling coordinates
import net.minecraftforge.items.IItemHandler;               // for checking player and block inventories
import net.minecraft.command.EntitySelector;                // for using command block selectors
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import java.util.UUID;                                      // for more securely tracking users internally

public class CommandBuy extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // request should not be null
      if (args == null || args.length == 0) {
         InterfaceMinecraft.forwardToUser(sender, getUsage(sender));
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
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BUY);
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
            pricePercent = CommandProcessor.parsePricePercentArgument(sender.getCommandSenderEntity().getUniqueID(), arg, true);

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
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_BUY);
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
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BLOCK_BUY);
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
               InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BLOCK_BUY);
               return;
            }
            accountID = args[5];
         }

         // grab remaining variables
         username = args[0];
         wareID   = args[2];

         // translate coordinates
         BlockPos position = sender.getPosition();
         if (position == null) {
            InterfaceMinecraft.forwardErrorToUser(sender, PlatformStrings.ERROR_POSITION_MISSING + CommandEconomy.CMD_USAGE_BLOCK_BUY);
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
               InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_INVENTORY_DIR + CommandEconomy.CMD_USAGE_BLOCK_BUY);
               return;
         }
         coordinates = new InterfaceCommand.Coordinates(position.getX(), position.getY(), position.getZ(), sender.getEntityWorld().provider.getDimension());
      }

      // if no username or direction should be given
      else {
         // ensure passed args are valid types
         try {
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BUY);
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
               InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BUY);
               return;
            }
            accountID = args[3];
         }

         // grab remaining variables
         username = sender.getName();
         wareID   = args[0];
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

      // check inventory existence
      if (coordinates != null) {
         IItemHandler inventoryToUse = InterfaceMinecraft.getInventory(userID, coordinates);

         if (inventoryToUse == null) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_INVENTORY_MISSING + CommandEconomy.CMD_USAGE_BLOCK_BUY);
            return;
         }
      }

      // call corresponding function
      Marketplace.buy(userID, coordinates, accountID, wareID, quantity, priceUnit, pricePercent, shouldManufacture);
      return;
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_BUY;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return CommandEconomy.CMD_USAGE_BUY;
      else
         return CommandEconomy.CMD_USAGE_BLOCK_BUY;
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
         switch(args.length)
         {
            case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.WARES);
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            case 4:  return InterfaceMinecraft.getAutoCompletionStrings(args[3], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            default: return new LinkedList<String>();
         }
      } else {
         switch(args.length)
         {
            case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
            case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {"inventory"});
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.WARES);
            case 5:  return InterfaceMinecraft.getAutoCompletionStrings(args[4], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            case 6:  return InterfaceMinecraft.getAutoCompletionStrings(args[5], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
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