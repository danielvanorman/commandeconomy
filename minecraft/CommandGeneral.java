package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;            // for finding who to send messages to
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;                                      // for more securely tracking users internally
import java.util.Arrays;                                    // for removing the first element of args before passing it to other commands

public class CommandGeneral extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // request should not be null
      if (args == null || args.length == 0) {
         serviceRequestHelp(sender, args);
         return;
      }

      // parse request parameters and pass them to the right function
      switch(args[0].toLowerCase()) 
      { 
         case CommandEconomy.CMD_HELP:
            serviceRequestHelp(sender, args);
            break;

         case CommandEconomy.CMD_BUY:
            InterfaceMinecraft.commandBuy.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SELL:
            InterfaceMinecraft.commandSell.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_CHECK:
            InterfaceMinecraft.commandCheck.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SELLALL_LOWER:
            InterfaceMinecraft.commandSellAll.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_NOSELL_LOWER:
            InterfaceMinecraft.commandNoSell.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_MONEY:
            InterfaceMinecraft.commandMoney.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SEND:
            InterfaceMinecraft.commandSend.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_CREATE:
            InterfaceMinecraft.commandCreate.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_DELETE:
            InterfaceMinecraft.commandDelete.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_GRANT_ACCESS_LOWER:
            InterfaceMinecraft.commandGrantAccess.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_REVOKE_ACCESS_LOWER:
            InterfaceMinecraft.commandRevokeAccess.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SAVE:
         case CommandEconomy.CMD_SAVECE:
            serviceRequestSave(sender, args);
            break;

         case CommandEconomy.CMD_RELOAD:
            serviceRequestReload(sender, args);
            break;

         case CommandEconomy.CMD_ADD:
            InterfaceMinecraft.commandAdd.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SET:
            serviceRequestSet(sender, args);
            break;

         case CommandEconomy.CMD_PRINT_MARKET_LOWER:
            serviceRequestPrintMarket(sender, args);
            break;

         case CommandEconomy.CMD_VERSION:
            serviceRequestVersion(sender, args);
            break;

         case CommandEconomy.CMD_CHANGE_STOCK_LOWER:
            serviceRequestChangeStock(sender, args);
            break;

         case CommandEconomy.CMD_SET_DEFAULT_ACCOUNT_LOWER:
            CommandProcessor.setDefaultAccount(InterfaceMinecraft.getSenderID(sender), Arrays.copyOfRange(args, 1, args.length));
            break;

         default:
            errorMessage = new TextComponentString(CommandEconomy.ERROR_INVALID_CMD);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            break;
      }

      return;
  }

   /**
    * Prints possible commands.
    * Expected Format: help
    * <p>
    * Complexity: O(1)
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestHelp(ICommandSender sender, String[] args) {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      if (args != null && args.length > 1 &&
          args[1] != null &&
          args[1].equals(CommandEconomy.HELP_COMMAND_BLOCK)) {
         errorMessage = new TextComponentString(
            CommandEconomy.CMD_USAGE_BLOCK_BUY + " // purchases an item\n" +
            CommandEconomy.CMD_USAGE_BLOCK_SELL + " // sells an item\n" +
            CommandEconomy.CMD_USAGE_BLOCK_CHECK + " // looks up item price and stock\n" +
            CommandEconomy.CMD_USAGE_BLOCK_SELLALL + " // sells all tradeable wares in your inventory at current market prices\n" +
            CommandEconomy.CMD_USAGE_BLOCK_MONEY + " // looks up how much is in an account\n" +
            CommandEconomy.CMD_USAGE_BLOCK_SEND + " // transfers money from one account to another\n" +
            "inventory_direction is none, down, up, north, east, west, or south\n"
         );
      } else {
        errorMessage = new TextComponentString(
           CommandEconomy.CMD_USAGE_BUY + " - purchases an item\n" +
           CommandEconomy.CMD_USAGE_SELL + " - sells an item\n" +
           CommandEconomy.CMD_USAGE_CHECK + " - looks up item price and stock\n" +
           CommandEconomy.CMD_USAGE_SELLALL + " - sells all tradeable items in your inventory at current market prices\n" +
           CommandEconomy.CMD_USAGE_NOSELL + " - marks an item stack to be unsellable\n" +
           CommandEconomy.CMD_USAGE_MONEY + " - looks up how much is in an account\n" +
           CommandEconomy.CMD_USAGE_SEND + " [sender_account_id] - transfers money from one account to another\n" +
           CommandEconomy.CMD_USAGE_CREATE + " - opens a new account with the specified id\n" +
           CommandEconomy.CMD_USAGE_DELETE + " - closes the account with the specified id\n" +
           CommandEconomy.CMD_USAGE_GRANT_ACCESS + " - allows a player to view and withdraw from a specified account\n" +
           CommandEconomy.CMD_USAGE_REVOKE_ACCESS + " - disallows a player to view and withdraw from a specified account\n" +
           CommandEconomy.CMD_USAGE_VERSION + " - says what version of Command Economy is running\n" +
           CommandEconomy.CMD_USAGE_ADD + " - summons money\n" +
           CommandEconomy.CMD_USAGE_SET + " - sets account's money to a specified amount\n" +
           CommandEconomy.CMD_USAGE_CHANGE_STOCK + " - increases or decreases an item's available quantity within the marketplace or sets the quantity to a certain level\n" +
           CommandEconomy.CMD_USAGE_SET_DEFAULT_ACCOUNT + " - marks an account to be used in place of your personal account\n" +
           CommandEconomy.CMD_USAGE_SAVECE + " - saves market wares and accounts\n" +
           CommandEconomy.CMD_USAGE_RELOAD + " - reloads part of the marketplace from file\n" +
           CommandEconomy.CMD_USAGE_PRINT_MARKET + " - writes all wares currently tradeable to a file"
        );
      }
      sender.sendMessage(errorMessage);

      return;
   }

   /**
    * Saves accounts and market wares.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSave(ICommandSender sender, String[] args) {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // call corresponding functions
      Marketplace.saveWares();
      Account.saveAccounts();
      errorMessage = new TextComponentString(CommandEconomy.MSG_SAVED_ECONOMY);
      sender.sendMessage(errorMessage);
      return;
   }

   /**
    * Reloads part of the marketplace from file.<br>
    * Expected Format: (config | wares | accounts | all)
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestReload(ICommandSender sender, String[] args) {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      CommandProcessor.reload(InterfaceMinecraft.getSenderID(sender), args, 1);
      return;
   }

   /**
    * Tells the user what version of Command Economy is running.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestVersion(ICommandSender sender, String[] args) {
      sender.sendMessage(new TextComponentString(CommandEconomy.MSG_VERSION + CommandEconomy.VERSION));
      return;
   }

   /**
    * Sets account's money to a specified amount.<br>
    * Expected Format: &#60;quantity&#62; [account_id]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSet(ICommandSender sender, String[] args) {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      CommandProcessor.set(InterfaceMinecraft.getSenderID(sender), args, 1);
      return;
   }

   /**
    * Increases or decreases a ware's available quantity within the marketplace
    * or sets the quantity to a certain level.<br>
    * Expected Format: &#60;ware_id&#62; (&#60;quantity&#62; | equilibrium | overstocked | understocked)
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestChangeStock(ICommandSender sender, String[] args) {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      CommandProcessor.changeStock(InterfaceMinecraft.getSenderID(sender), Arrays.copyOfRange(args, 1, args.length));
      return;
   }

   /**
    * Writes all wares currently tradeable to a file.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestPrintMarket(ICommandSender sender, String[] args) {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // call corresponding functions
      Marketplace.printMarket();
      errorMessage = new TextComponentString(CommandEconomy.MSG_PRINT_MARKET);
      sender.sendMessage(errorMessage);
      return;
   }

   @Override
   public String getName() {
      return CommandEconomy.MODID;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return "/" + CommandEconomy.MODID;
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
      // permissions are checked elsewhere
      return true;
   }

   @Override
   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
   {
      if (args == null || args.length == 0)
         return new LinkedList<String>();

      if (args.length == 1)
      {
         return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"help", "add", "buy", "changeStock", "check", "create", "delete", "grantAccess", "money", "noSell", "printMarket", "reload", "revokeAccess", "save", "sell", "sellAll", "send", "set", "setDefaultAccount", "version"});
      }

      if (args.length >= 2)
      {
         switch(args[0].toLowerCase())
         {
            case CommandEconomy.CMD_HELP: return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {CommandEconomy.HELP_COMMAND_BLOCK});

            case CommandEconomy.CMD_ADD:
            case CommandEconomy.CMD_SET:
               switch(args.length)
               {
                  case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], new String[] {"accounts"});
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_BUY:
               return InterfaceMinecraft.commandBuy.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_CHANGE_STOCK_LOWER:
               if (args.length == 1)
                  return new LinkedList<String>();

               if (args.length == 2)
               {
                  return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"wares"});
               }
               else if (args.length == 3)
               {
                  return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {CommandEconomy.CHANGE_STOCK_EQUILIBRIUM, CommandEconomy.CHANGE_STOCK_OVERSTOCKED, CommandEconomy.CHANGE_STOCK_UNDERSTOCKED});
               }

               return new LinkedList<String>();

            case CommandEconomy.CMD_CHECK:
               return InterfaceMinecraft.commandCheck.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_CREATE:
               return InterfaceMinecraft.commandCreate.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_DELETE:
               return InterfaceMinecraft.commandDelete.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_GRANT_ACCESS_LOWER:
            case CommandEconomy.CMD_REVOKE_ACCESS_LOWER:
               switch(args.length)
               {
                  case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {"players"});
                  case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], new String[] {"accounts"});
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_MONEY:
               return InterfaceMinecraft.commandMoney.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_NOSELL_LOWER:
               return InterfaceMinecraft.commandNoSell.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_RELOAD:
               switch(args.length)
               {
                  case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {CommandEconomy.RELOAD_CONFIG, CommandEconomy.RELOAD_WARES, CommandEconomy.RELOAD_ACCOUNTS, CommandEconomy.ALL});
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_SELL:
               return InterfaceMinecraft.commandSell.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_SELLALL_LOWER:
               return InterfaceMinecraft.commandSellAll.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case "send":
               return InterfaceMinecraft.commandSend.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            default: return new LinkedList<String>();
         }
      }

      return new LinkedList<String>();
   }

   @Override
   public boolean isUsernameIndex(java.lang.String[] args, int index)
   {
      if (index == 1 && args.length >= 2 &&
          (args[0].equalsIgnoreCase(CommandEconomy.CMD_GRANT_ACCESS_LOWER) ||
           args[0].equalsIgnoreCase(CommandEconomy.CMD_REVOKE_ACCESS_LOWER)))
      {
         return true;
      }

      return false;
   }
}