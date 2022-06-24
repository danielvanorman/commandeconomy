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

public class CommandSetDefaultAccount extends CommandBase {
   private final List<String> aliases = Arrays.asList(CommandEconomy.CMD_SET_DEFAULT_ACCOUNT_LOWER);

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
   public List<String> getAliases() {
      return aliases;
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