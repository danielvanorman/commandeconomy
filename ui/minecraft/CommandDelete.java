package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;

public class CommandDelete extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.accountDelete(UserInterfaceMinecraft.getSenderID(sender), args);
  }

   @Override
   public String getName() {
       return StringTable.CMD_DELETE;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return StringTable.CMD_USAGE_DELETE;
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
         return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);

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