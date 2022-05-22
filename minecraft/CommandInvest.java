package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;                    // for handling coordinates
import net.minecraftforge.items.IItemHandler;               // for checking player and block inventories
import net.minecraft.command.EntitySelector;                // for using command block selectors
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import java.util.UUID;                                      // for more securely tracking users internally

public class CommandInvest extends CommandBase {
  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      if (Config.investmentCostPerHierarchyLevel != 0.0f)
         CommandProcessor.invest(InterfaceMinecraft.getSenderID(sender), args);
      else
         CommandGeneral.serviceRequestHelp(sender, args);
      return;
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_INVEST;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_INVEST;
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

      switch(args.length)
      {
         case 1:  return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"wares"});
         case 3:  return InterfaceMinecraft.getAutoCompletionStrings(args[2], new String[] {"accounts"});
         default: return new LinkedList<String>();
      }
   }

   @Override
   public boolean isUsernameIndex(java.lang.String[] args, int index)
   {
      // there doesn't appear to be a good way to check
      // whether to use the command block variant
      // without knowing who/what the sender is
      return false;
   }
}