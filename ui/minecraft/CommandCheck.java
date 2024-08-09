package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage and handling held items
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;

public class CommandCheck extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.check(UserInterfaceMinecraft.getSenderID(sender), sender, server, args);
  }

   @Override
   public String getName() {
       return StringTable.CMD_CHECK;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return StringTable.CMD_USAGE_CHECK;
      else
         return StringTable.CMD_USAGE_BLOCK_CHECK;
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
            return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.WARES);
         else
            return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
      }
      else if (args.length == 2)
      {
         if (sender instanceof EntityPlayer)
            return new LinkedList<String>();
         else
            return UserInterfaceMinecraft.getAutoCompletionStrings(args[1], UserInterfaceMinecraft.AutoCompletionStringCategories.WARES);
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