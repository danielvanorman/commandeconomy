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
   /** speeds up concatenation for /help */
   private static StringBuilder sbHelpOutput   = new StringBuilder(2200);
   /** whether help output currently includes /invest */
   private static boolean sbHelpContainsInvest = false;

   /** valid arguments for referring /ChangeStock ware stock levels */
   public static final String[] CHANGE_STOCK_KEYWORDS = new String[] {CommandEconomy.CHANGE_STOCK_EQUILIBRIUM, CommandEconomy.CHANGE_STOCK_OVERSTOCKED, CommandEconomy.CHANGE_STOCK_UNDERSTOCKED};
   /** valid arguments for referring to reloading parts of CommandEconomy */
   public static final String[] RELOAD_KEYWORDS = new String[] {CommandEconomy.RELOAD_CONFIG, CommandEconomy.RELOAD_WARES, CommandEconomy.RELOAD_ACCOUNTS, CommandEconomy.ALL};
   /** valid arguments for CommandEconomy command names */
   public static final String[] COMMAND_NAMES = new String[] {CommandEconomy.CMD_HELP, CommandEconomy.CMD_BUY, CommandEconomy.CMD_SELL, CommandEconomy.CMD_CHECK, CommandEconomy.CMD_SELLALL, CommandEconomy.CMD_MONEY, CommandEconomy.CMD_SEND, CommandEconomy.CMD_CREATE, CommandEconomy.CMD_DELETE, CommandEconomy.CMD_GRANT_ACCESS, CommandEconomy.CMD_REVOKE_ACCESS, CommandEconomy.CMD_VERSION, CommandEconomy.CMD_ADD, CommandEconomy.CMD_SET, CommandEconomy.CMD_CHANGE_STOCK, CommandEconomy.CMD_SAVE, CommandEconomy.CMD_SET_DEFAULT_ACCOUNT, CommandEconomy.CMD_RELOAD, CommandEconomy.CMD_PRINT_MARKET, CommandEconomy.CMD_INVEST};

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
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

         case PlatformStrings.CMD_NOSELL_LOWER:
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

         case CommandEconomy.CMD_INVEST:
            CommandProcessor.invest(InterfaceMinecraft.getSenderID(sender), Arrays.copyOfRange(args, 1, args.length));
            break;

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
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_INVALID_CMD);
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
      // prepare to tell the user about command usages
      TextComponentString message;

      if (args != null && args.length > 1 &&
          args[1] != null &&
          args[1].equals(CommandEconomy.HELP_COMMAND_BLOCK)) {
         message = new TextComponentString(
            CommandEconomy.CMD_USAGE_BLOCK_BUY + CommandEconomy.CMD_DESC_BUY +
            CommandEconomy.CMD_USAGE_BLOCK_SELL + CommandEconomy.CMD_DESC_SELL +
            CommandEconomy.CMD_USAGE_BLOCK_CHECK + CommandEconomy.CMD_DESC_CHECK +
            CommandEconomy.CMD_USAGE_BLOCK_SELLALL + CommandEconomy.CMD_DESC_SELLALL +
            CommandEconomy.CMD_USAGE_BLOCK_MONEY + CommandEconomy.CMD_DESC_MONEY +
            CommandEconomy.CMD_USAGE_BLOCK_SEND + CommandEconomy.CMD_DESC_SEND +
            "inventory_direction is none, down, up, north, east, west, or south\n"
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
                        .append(PlatformStrings.CMD_USAGE_NOSELL).append(PlatformStrings.CMD_DESC_NOSELL)
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
                        .append(CommandEconomy.CMD_USAGE_PRINT_MARKET).append(CommandEconomy.CMD_DESC_PRINT_MARKET);
         }

        message = new TextComponentString(sbHelpOutput.toString());
      }

      sender.sendMessage(message);
      return;
   }

   /**
    * Saves accounts and market wares.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestSave(ICommandSender sender, String[] args) {
      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // call corresponding functions
      Marketplace.saveWares();
      Account.saveAccounts();
      InterfaceMinecraft.forwardToUser(sender, CommandEconomy.MSG_SAVED_ECONOMY);
      return;
   }

   /**
    * Reloads part of the marketplace from file.<br>
    * Expected Format: (config | wares | accounts | all)
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestReload(ICommandSender sender, String[] args) {
      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
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
      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
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
      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
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
      // check if command sender has
      // permission to execute this command
      if (!InterfaceMinecraft.permissionToExecute(InterfaceMinecraft.getSenderID(sender), sender, true)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // call corresponding functions
      Marketplace.printMarket();
      InterfaceMinecraft.forwardToUser(sender, CommandEconomy.MSG_PRINT_MARKET);
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
         return InterfaceMinecraft.getAutoCompletionStrings(args[0], COMMAND_NAMES);
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
                  case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_BUY:
               return InterfaceMinecraft.commandBuy.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_CHANGE_STOCK_LOWER:
               if (args.length == 1)
                  return new LinkedList<String>();

               if (args.length == 2)
               {
                  return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.WARES);
               }
               else if (args.length == 3)
               {
                  return InterfaceMinecraft.getAutoCompletionStrings(args[1], CHANGE_STOCK_KEYWORDS);
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
                  case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
                  case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_MONEY:
               return InterfaceMinecraft.commandMoney.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case PlatformStrings.CMD_NOSELL_LOWER:
               return InterfaceMinecraft.commandNoSell.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_RELOAD:
               switch(args.length)
               {
                  case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], RELOAD_KEYWORDS);
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_SELL:
               return InterfaceMinecraft.commandSell.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_SELLALL_LOWER:
               return InterfaceMinecraft.commandSellAll.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_SEND:
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