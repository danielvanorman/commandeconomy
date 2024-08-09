package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage
import java.util.List;                                      // for autocompleting arguments and sending command aliases
import java.util.LinkedList;
import java.util.Arrays;                                    // for storing command aliases

public class CommandSellAll extends CommandBase {
   private final List<String> aliases = Arrays.asList(CommandEconomy.CMD_SELLALL_LOWER);

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      CommandProcessor.sellAll(UserInterfaceMinecraft.getSenderID(sender), sender, args);
  }

   @Override
   public String getName() {
      return CommandEconomy.CMD_SELLALL;
   }

   @Override
   public List<String> getAliases() {
      return aliases;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return CommandEconomy.CMD_USAGE_SELL;
      else
         return CommandEconomy.CMD_USAGE_BLOCK_SELLALL;
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

      if (sender instanceof EntityPlayer) {
         if (args.length == 1)
            return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
         else
            return new LinkedList<String>();
      } else {
         switch(args.length)
         {
            case 1:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[0], UserInterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
            case 2:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[1], UserInterfaceMinecraft.AutoCompletionStringCategories.INVENTORY);
            case 3:  return UserInterfaceMinecraft.getAutoCompletionStrings(args[2], UserInterfaceMinecraft.AutoCompletionStringCategories.ACCOUNTS);
            default: return new LinkedList<String>();
         }
      }
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