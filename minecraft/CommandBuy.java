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
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // request should not be null
      if (args == null || args.length == 0) {
         errorMessage = new TextComponentString(getUsage(sender));
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // command must have the right number of args
      if (args.length < 2 ||
          args.length > 6) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_BUY);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          args[1] == null || args[1].length() == 0 ||
          (args.length >= 3 && (args[2] == null || args[2].length() == 0)) ||
          (args.length >= 4 && (args[3] == null || args[3].length() == 0)) ||
          (args.length >= 5 && (args[4] == null || args[4].length() == 0)) ||
          (args.length == 6 && (args[5] == null || args[5].length() == 0))) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BUY);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // set up variables
      String      username  = null;
      InterfaceCommand.Coordinates coordinates = null;
      String      wareID    = null;
      int         quantity  = 0;
      float       priceUnit = 0.0f;
      String      accountID = null;

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
            errorMessage = new TextComponentString(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BLOCK_BUY);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            return;
         }

         // if five arguments are given,
         // the fifth must either be a price or an account ID
         if (args.length == 5) {
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
         else if (args.length == 6) {
            try {
               priceUnit = Float.parseFloat(args[4]);
            } catch (NumberFormatException e) {
               errorMessage = new TextComponentString(CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BLOCK_BUY);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               sender.sendMessage(errorMessage);
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
            errorMessage = new TextComponentString(CommandEconomy.ERROR_POSITION_MISSING + CommandEconomy.CMD_USAGE_BLOCK_BUY);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
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
               errorMessage = new TextComponentString(CommandEconomy.ERROR_INVENTORY_DIR + CommandEconomy.CMD_USAGE_BLOCK_BUY);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               sender.sendMessage(errorMessage);
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
            errorMessage = new TextComponentString(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BUY);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            return;
         }

         // if three arguments are given,
         // the third must either be a price or an account ID
         if (args.length == 3) {
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
         else if (args.length == 4) {
            try {
               priceUnit = Float.parseFloat(args[2]);
            } catch (NumberFormatException e) {
               errorMessage = new TextComponentString(CommandEconomy.ERROR_PRICE + CommandEconomy.CMD_USAGE_BUY);
               errorMessage.getStyle().setColor(TextFormatting.RED);
               sender.sendMessage(errorMessage);
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
         errorMessage = new TextComponentString(CommandEconomy.ERROR_ENTITY_SELECTOR);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // grab user's UUID once
      UUID userID = InterfaceMinecraft.getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!InterfaceMinecraft.permissionToExecute(userID, sender)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // check inventory existence
      if (coordinates != null) {
         IItemHandler inventoryToUse = InterfaceMinecraft.getInventory(userID, coordinates);

         if (inventoryToUse == null) {
            errorMessage = new TextComponentString(CommandEconomy.ERROR_INVENTORY_MISSING + CommandEconomy.CMD_USAGE_BLOCK_BUY);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            return;
         }
      }

      // call corresponding function
      Marketplace.buy(userID, coordinates, wareID, quantity, priceUnit, accountID);
      return;
  }

   @Override
   public String getName() {
       return "buy";
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
            case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"wares"});
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], new String[] {"accounts"});
            case 4:  return InterfaceMinecraft.getAutoCompletionStrings(args[3], new String[] {"accounts"});
            default: return new LinkedList<String>();
         }
      } else {
         switch(args.length)
         {
            case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"players"});
            case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {"inventory"});
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], new String[] {"wares"});
            case 5:  return InterfaceMinecraft.getAutoCompletionStrings(args[4], new String[] {"accounts"});
            case 6:  return InterfaceMinecraft.getAutoCompletionStrings(args[5], new String[] {"accounts"});
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