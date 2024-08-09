package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players

public class CommandSaveCE extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // call corresponding functions
      Marketplace.saveWares();
      Account.saveAccounts();
      errorMessage = new TextComponentString(StringTable.MSG_SAVED_ECONOMY);
      sender.sendMessage(errorMessage);
  }

  @Override
  public String getName() {
      return StringTable.CMD_SAVECE;
  }

  @Override
  public String getUsage(ICommandSender sender) {
      return StringTable.CMD_USAGE_SAVECE;
  }

   @Override
   public int getRequiredPermissionLevel()
   {
      return 4;
   }
}