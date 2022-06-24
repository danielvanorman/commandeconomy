package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import java.util.List;                                      // for autocompleting arguments and sending command aliases
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;                                    // for storing command aliases

public class CommandGrantAccess extends CommandBase {
   private final List<String> aliases = Arrays.asList(CommandEconomy.CMD_GRANT_ACCESS_LOWER);

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.accountGrantAccess(InterfaceMinecraft.getSenderID(sender), args);
      return;
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_GRANT_ACCESS;
   }

   @Override
   public List<String> getAliases() {
      return aliases;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_GRANT_ACCESS;
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

      switch(args.length)
      {
         case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
         case 2:  return InterfaceMinecraft.getAutoCompletionStrings(args[1], InterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
         default: return new LinkedList<String>();
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