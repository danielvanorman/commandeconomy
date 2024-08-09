package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;                                    // for removing the first element of args before passing it to other commands

public class CommandGeneral extends CommandBase {
   /** speeds up concatenation for /help */
   private static StringBuilder sbHelpOutput   = new StringBuilder(2200);
   /** whether help output currently includes /research */
   private static boolean sbHelpContainsResearch = false;

   /** valid arguments for referring /ChangeStock ware stock levels */
   public static final String[] CHANGE_STOCK_KEYWORDS = new String[] {CommandEconomy.CHANGE_STOCK_EQUILIBRIUM, CommandEconomy.CHANGE_STOCK_OVERSTOCKED, CommandEconomy.CHANGE_STOCK_UNDERSTOCKED};
   /** valid arguments for referring to reloading parts of CommandEconomy */
   public static final String[] RELOAD_KEYWORDS = new String[] {CommandEconomy.RELOAD_CONFIG, CommandEconomy.RELOAD_WARES, CommandEconomy.RELOAD_ACCOUNTS, CommandEconomy.ALL};
   /** valid arguments for CommandEconomy command names */
   public static final String[] COMMAND_NAMES = new String[] {CommandEconomy.CMD_HELP, CommandEconomy.CMD_BUY, CommandEconomy.CMD_SELL, CommandEconomy.CMD_CHECK, CommandEconomy.CMD_SELLALL, CommandEconomy.CMD_MONEY, CommandEconomy.CMD_SEND, CommandEconomy.CMD_CREATE, CommandEconomy.CMD_DELETE, CommandEconomy.CMD_GRANT_ACCESS, CommandEconomy.CMD_REVOKE_ACCESS, CommandEconomy.CMD_VERSION, CommandEconomy.CMD_ADD, CommandEconomy.CMD_SET, CommandEconomy.CMD_CHANGE_STOCK, CommandEconomy.CMD_SAVE, CommandEconomy.CMD_SET_DEFAULT_ACCOUNT, CommandEconomy.CMD_RELOAD, CommandEconomy.CMD_PRINT_MARKET, CommandEconomy.CMD_RESEARCH};

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
            UserInterfaceMinecraft.commandBuy.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SELL:
            UserInterfaceMinecraft.commandSell.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_CHECK:
            UserInterfaceMinecraft.commandCheck.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SELLALL_LOWER:
            UserInterfaceMinecraft.commandSellAll.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case PlatformStrings.CMD_NOSELL_LOWER:
            UserInterfaceMinecraft.commandNoSell.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_MONEY:
            UserInterfaceMinecraft.commandMoney.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_SEND:
            UserInterfaceMinecraft.commandSend.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_CREATE:
            UserInterfaceMinecraft.commandCreate.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_DELETE:
            UserInterfaceMinecraft.commandDelete.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_GRANT_ACCESS_LOWER:
            UserInterfaceMinecraft.commandGrantAccess.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_REVOKE_ACCESS_LOWER:
            UserInterfaceMinecraft.commandRevokeAccess.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
            return;

         case CommandEconomy.CMD_RESEARCH:
            CommandProcessor.research(UserInterfaceMinecraft.getSenderID(sender), Arrays.copyOfRange(args, 1, args.length));
            break;

         case CommandEconomy.CMD_SAVE:
         case CommandEconomy.CMD_SAVECE:
            serviceRequestSave(sender, args);
            break;

         case CommandEconomy.CMD_RELOAD:
            serviceRequestReload(sender, args);
            break;

         case CommandEconomy.CMD_ADD:
            UserInterfaceMinecraft.commandAdd.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
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
            CommandProcessor.setDefaultAccount(UserInterfaceMinecraft.getSenderID(sender), Arrays.copyOfRange(args, 1, args.length));
            break;

         default:
            UserInterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_INVALID_CMD);
            break;
      }
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
                        .append(PlatformStrings.CMD_USAGE_NOSELL).append(PlatformStrings.CMD_DESC_NOSELL)
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
                        .append(CommandEconomy.CMD_USAGE_PRINT_MARKET).append(CommandEconomy.CMD_DESC_PRINT_MARKET);
         }

        message = new TextComponentString(sbHelpOutput.toString());
      }

      sender.sendMessage(message);
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
      if (!UserInterfaceMinecraft.permissionToExecuteStatic(UserInterfaceMinecraft.getSenderID(sender), sender, true)) {
         UserInterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // call corresponding functions
      Marketplace.saveWares();
      Account.saveAccounts();
      UserInterfaceMinecraft.forwardToUser(sender, CommandEconomy.MSG_SAVED_ECONOMY);
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
      if (!UserInterfaceMinecraft.permissionToExecuteStatic(UserInterfaceMinecraft.getSenderID(sender), sender, true)) {
         UserInterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
      }

      CommandProcessor.reload(UserInterfaceMinecraft.getSenderID(sender), args, 1);

      // reload autocompletion strings for ware aliases and account IDs
      // in case they were changed if wares or accounts were reloaded
      UserInterfaceMinecraft.sortAccountIDs();
      UserInterfaceMinecraft.sortWareAliases();
   }

   /**
    * Tells the user what version of Command Economy is running.<br>
    * Expected Format: [null | empty]
    *
    * @param args arguments given in the expected format
    */
   protected static void serviceRequestVersion(ICommandSender sender, String[] args) {
      sender.sendMessage(new TextComponentString(CommandEconomy.MSG_VERSION + CommandEconomy.VERSION));
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
      if (!UserInterfaceMinecraft.permissionToExecuteStatic(UserInterfaceMinecraft.getSenderID(sender), sender, true)) {
         UserInterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.set(UserInterfaceMinecraft.getSenderID(sender), args, 1);
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
      if (!UserInterfaceMinecraft.permissionToExecuteStatic(UserInterfaceMinecraft.getSenderID(sender), sender, true)) {
         UserInterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      CommandProcessor.changeStock(UserInterfaceMinecraft.getSenderID(sender), Arrays.copyOfRange(args, 1, args.length));
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
      if (!UserInterfaceMinecraft.permissionToExecuteStatic(UserInterfaceMinecraft.getSenderID(sender), sender, true)) {
         UserInterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
         return;
      }

      // call corresponding functions
      Marketplace.printMarket();
      UserInterfaceMinecraft.forwardToUser(sender, CommandEconomy.MSG_PRINT_MARKET);
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
         return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], COMMAND_NAMES);
      }

      if (args.length >= 2)
      {
         switch(args[0].toLowerCase())
         {
            case CommandEconomy.CMD_HELP: return UserInterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {CommandEconomy.HELP_COMMAND_BLOCK});

            case CommandEconomy.CMD_ADD:
            case CommandEconomy.CMD_SET:
               switch(args.length)
               {
                  case 3:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[2], UserInterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_BUY:
               return UserInterfaceMinecraft.commandBuy.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_CHANGE_STOCK_LOWER:
               if (args.length == 1)
                  return new LinkedList<String>();

               if (args.length == 2)
               {
                  return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.WARES);
               }
               else if (args.length == 3)
               {
                  return UserInterfaceMinecraft.getAutoCompletionStrings(args[1], CHANGE_STOCK_KEYWORDS);
               }

               return new LinkedList<String>();

            case CommandEconomy.CMD_CHECK:
               return UserInterfaceMinecraft.commandCheck.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_CREATE:
               return UserInterfaceMinecraft.commandCreate.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_DELETE:
               return UserInterfaceMinecraft.commandDelete.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_GRANT_ACCESS_LOWER:
            case CommandEconomy.CMD_REVOKE_ACCESS_LOWER:
               switch(args.length)
               {
                  case 2:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[1], UserInterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
                  case 3:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[2], UserInterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_MONEY:
               return UserInterfaceMinecraft.commandMoney.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case PlatformStrings.CMD_NOSELL_LOWER:
               return UserInterfaceMinecraft.commandNoSell.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_RELOAD:
               switch(args.length)
               {
                  case 2:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[1], RELOAD_KEYWORDS);
                  default: return new LinkedList<String>();
               }

            case CommandEconomy.CMD_SELL:
               return UserInterfaceMinecraft.commandSell.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_SELLALL_LOWER:
               return UserInterfaceMinecraft.commandSellAll.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            case CommandEconomy.CMD_SEND:
               return UserInterfaceMinecraft.commandSend.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length), pos);

            default: return new LinkedList<String>();
         }
      }

      return new LinkedList<String>();
   }

   @Override
   public boolean isUsernameIndex(String[] args, int index)
   {
      return index == 1 && args.length >= 2 &&
             (args[0].equalsIgnoreCase(CommandEconomy.CMD_GRANT_ACCESS_LOWER) ||
              args[0].equalsIgnoreCase(CommandEconomy.CMD_REVOKE_ACCESS_LOWER));
   }
}