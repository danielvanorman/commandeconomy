package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;                    // for handling coordinates
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;

public class CommandResearch extends CommandBase {
  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.research(UserInterfaceMinecraft.getSenderID(sender), args);
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_RESEARCH;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_RESEARCH;
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
         case 1:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.WARES);
         case 3:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[2], UserInterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
         default: return new LinkedList<String>();
      }
   }

   @Override
   public boolean isUsernameIndex(String[] args, int index)
   {
      return false;
   }
}