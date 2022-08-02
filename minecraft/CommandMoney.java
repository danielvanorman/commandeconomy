package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.EntitySelector;                // for using command block selectors
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage
import java.util.List;                                      // for autocompleting arguments and sending command aliases
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;                                      // for more securely tracking users internally
import java.util.Arrays;                                    // for storing command aliases

public class CommandMoney extends CommandBase {
   private final List<String> aliases = Arrays.asList("wallet");

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // request can be null or have zero arguments
      // except for the command block variant
      if (!(sender instanceof EntityPlayer) &&
          (args == null || args.length == 0)) {
         InterfaceMinecraft.forwardToUser(sender, getUsage(sender));
         return;
      }

      // check for zero-length args
      if (args != null && 
          ((args.length >= 1 && (args[0] == null || args[0].length() == 0)) ||
           (args.length >= 2 && (args[1] == null || args[1].length() == 0)))) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_MONEY);
         return;
      }

      // if nothing is given,
      // use the player's personal account or default account
      if (args == null || args.length == 0) {
         Account account = Account.grabAndCheckAccount(null, sender.getCommandSenderEntity().getUniqueID());

         account.check(sender.getCommandSenderEntity().getUniqueID(), CommandEconomy.MSG_PERSONAL_ACCOUNT);
         return;
      }

      // set up variables
      String username  = null;
      String accountID = null;

      // if only an account ID is given
      if (args.length == 1) {
         username  = sender.getName();
         accountID = args[0];
      }
      // if a username and an account ID are given
      else if (args.length >= 2) {
         username  = args[0];
         accountID = args[1];
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

      // grab the account using the given account ID
      Account account = Account.grabAndCheckAccount(accountID, sender.getCommandSenderEntity().getUniqueID());
      if (account != null)
         account.check(sender.getCommandSenderEntity().getUniqueID(), accountID);
      // if the account was not found, an error message has already been printed
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_MONEY;
   }

   @Override
   public List<String> getAliases() {
      return aliases;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return CommandEconomy.CMD_USAGE_MONEY;
      else
         return CommandEconomy.CMD_USAGE_BLOCK_MONEY;
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

      if (args.length == 1)
      {
         if (sender instanceof EntityPlayer)
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
         else
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
      }
      else if (args.length == 2)
      {
         if (sender instanceof EntityPlayer)
            return new LinkedList<String>();
         else
            return InterfaceMinecraft.getAutoCompletionStrings(args[1], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
      }

      return new LinkedList<String>();
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