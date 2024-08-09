package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import java.util.List;                                      // for sending command aliases
import java.util.Arrays;                                    // for storing command aliases

public class CommandPrintMarket extends CommandBase {
   private final List<String> aliases = Arrays.asList(CommandEconomy.CMD_PRINT_MARKET_LOWER);

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // prepare to tell the user if something is wrong
      TextComponentString message;

      // call corresponding functions
      Marketplace.printMarket();
      message = new TextComponentString(CommandEconomy.MSG_PRINT_MARKET);
      sender.sendMessage(message);
  }

  @Override
  public String getName() {
      return CommandEconomy.CMD_PRINT_MARKET;
  }

   @Override
   public List<String> getAliases() {
      return aliases;
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