package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;            // for finding who to send messages to
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;

public class CommandPrintMarket extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // prepare to tell the user if something is wrong
      TextComponentString message;

      // call corresponding functions
      Marketplace.printMarket();
      message = new TextComponentString(CommandEconomy.MSG_PRINT_MARKET);
      sender.sendMessage(message);
      return;
  }

  @Override
  public String getName() {
      return CommandEconomy.CMD_PRINT_MARKET;
  }

  @Override
  public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_PRINT_MARKET;
  }

   @Override
   public int getRequiredPermissionLevel()
   {
      return 4;
   }
}