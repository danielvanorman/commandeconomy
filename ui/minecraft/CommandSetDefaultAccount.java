package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import java.util.List;                                      // for autocompleting arguments and sending command aliases
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;
import java.util.Arrays;                                    // for storing command aliases

public class CommandSetDefaultAccount extends CommandBase {
   private final List<String> aliases = Arrays.asList(StringTable.CMD_SET_DEFAULT_ACCOUNT_LOWER);

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.setDefaultAccount(UserInterfaceMinecraft.getSenderID(sender), args);
  }

   @Override
   public String getName() {
       return StringTable.CMD_SET_DEFAULT_ACCOUNT;
   }

   @Override
   public List<String> getAliases() {
      return aliases;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return StringTable.CMD_USAGE_SET_DEFAULT_ACCOUNT;
   }

   @Override
   public int getRequiredPermissionLevel()
   {
      return 0;
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
}