package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.command.EntitySelector;                // for using command block selectors
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;

public class CommandSend extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // request should not be null
      if (args == null || args.length == 0) {
         InterfaceMinecraft.forwardErrorToUser(sender, getUsage(sender));
         return;
      }

      // command must have the right number of args
      if (args.length < 2 ||
          args.length > 4) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_SEND);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          args[1] == null || args[1].length() == 0 ||
          (args.length == 3 && (args[2] == null || args[2].length() == 0)) ||
          (args.length == 4 && (args[3] == null || args[3].length() == 0))) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_SEND);
         return;
      }

      // set up variables
      String username           = null;
      float  quantity           = 0.0f;
      String recipientAccountID = null;
      String senderAccountID    = null;
      Account account           = null;

      // if the first argument is a number,
      // the other arguments must be account IDs
      // otherwise, the first argument is a username
      try {
         quantity = Float.parseFloat(args[0]);
         username = sender.getName();
         recipientAccountID = args[1];

         // if a sender account ID is given, use it
         if (args.length == 3)
            senderAccountID = args[2];

         // if no sender account is given,
         // use the player's personal account
         else
            senderAccountID = sender.getName();
      } catch (NumberFormatException e) {
         // try to parse quantity
         try {
            quantity = Float.parseFloat(args[1]);
         } catch (NumberFormatException nfe) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_BLOCK_SEND);
            return;
         }

         // if a sender account ID is given, use it
         if (args.length == 4) {
            username           = args[0];
            recipientAccountID = args[2];
            senderAccountID    = args[3];
         }
         // if no sender account is given,
         // use the player's personal account
         else {
            username           = args[0];
            recipientAccountID = args[2];
            senderAccountID    = args[0];
         }

         // check for entity selectors
         try {
            if (username != null && EntitySelector.isSelector(username))
               username = EntitySelector.matchOnePlayer(sender, username).getName();

            if (recipientAccountID != null && EntitySelector.isSelector(recipientAccountID))
               recipientAccountID = EntitySelector.matchOnePlayer(sender, recipientAccountID).getName();

            if (senderAccountID != null && EntitySelector.isSelector(senderAccountID))
               senderAccountID = EntitySelector.matchOnePlayer(sender, senderAccountID).getName();
         } catch (Exception ese) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ENTITY_SELECTOR);
            return;
         }
      }

      // if a valid account is given, use it
      account = Account.grabAndCheckAccount(senderAccountID, InterfaceMinecraft.getPlayerIDStatic(username));
      if (account != null)
         // transfer the money
         account.sendMoney(InterfaceMinecraft.getPlayerIDStatic(username), quantity, senderAccountID, recipientAccountID);
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_SEND;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return CommandEconomy.CMD_USAGE_SEND;
      else
         return CommandEconomy.CMD_USAGE_BLOCK_SEND;
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
            case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            default: return new LinkedList<String>();
         }
      } else {
         switch(args.length)
         {
            case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
            case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            case 4:  return InterfaceMinecraft.getAutoCompletionStrings(args[3], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            default: return new LinkedList<String>();
         }
      }
   }

   @Override
   public boolean isUsernameIndex(String[] args, int index)
   {
      // there doesn't appear to be a good way to check
      // whether to use the command block variant
      // without knowing who/what the sender is
      return false;
   }
}