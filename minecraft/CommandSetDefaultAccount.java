package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;

public class CommandSetDefaultAccount extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.setDefaultAccount(InterfaceMinecraft.getSenderID(sender), args);
      return;
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_SET_DEFAULT_ACCOUNT;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_SET_DEFAULT_ACCOUNT;
   }

   @Override
   public int getRequiredPermissionLevel()
   {
      return 0;
   }

   @Override
   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
   {
      return new LinkedList<String>();
   }
}