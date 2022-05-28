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

public class CommandChangeStock extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.changeStock(InterfaceMinecraft.getSenderID(sender), args);
      return;
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_CHANGE_STOCK;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_CHANGE_STOCK;
   }

   @Override
   public int getRequiredPermissionLevel()
   {
      return 2;
   }

   @Override
   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
   {
      if (args == null || args.length == 0)
         return new LinkedList<String>();

      if (args.length == 1)
      {
         return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.WARES);
      }
      else if (args.length == 2)
      {
         return InterfaceMinecraft.getAutoCompletionStrings(args[1], CommandGeneral.CHANGE_STOCK_KEYWORDS);
      }

      return new LinkedList<String>();
   }
}