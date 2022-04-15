package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;            // for finding who to send messages to
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import net.minecraft.entity.player.EntityPlayer;            // for accessing the command-issuing player's inventory
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;                    // for accessing marking itemstacks to not be sold
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;

public class CommandNoSell extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // prepare to grab the player
      EntityPlayer player = null;

      // only use the command on players who can hold items
      if (sender instanceof EntityPlayer) {
         player = (EntityPlayer) sender;
      } else {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PLAYER_MISSING);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // if a boolean is given, try to grab it
      // otherwise, assume the item should not be sold
      Boolean shouldSell = false;
      if (args.length > 0 &&
          args[0] != null && args[0].length() != 0 &&
          args[0].equalsIgnoreCase(CommandEconomy.FALSE)) {
         shouldSell = true;
      }

      // check whether one or all items should be affected
      if ((args.length >= 1 &&
          args[0] != null && args[0].length() != 0 &&
          args[0].equalsIgnoreCase(CommandEconomy.ALL)) ||
          (args.length >= 2 &&
           args[1] != null && args[1].length() != 0 &&
           args[1].equalsIgnoreCase(CommandEconomy.ALL))) {
         // loop through the player's main inventory
         ItemStack itemStack;
         for (int slot = 0; slot < 36; slot++) {
            // grab wares in current inventory slot
            itemStack = player.inventory.getStackInSlot(slot);

            // if the slot is empty, skip it
            if (itemStack.isEmpty() ||
                itemStack == ItemStack.EMPTY)
               continue;

            // tag the current item
            if (!shouldSell) {
               // if the item has no tags, create a container for them
               if (!itemStack.hasTagCompound())
                  itemStack.setTagCompound(new NBTTagCompound());

               // mark the item to not be sold
               itemStack.getTagCompound().setBoolean("nosell", true);
            } else {
               // if the item has no tags, there is nothing to do
               if (!itemStack.hasTagCompound())
                  return;

               // remove the item's mark to not be sold
               itemStack.getTagCompound().removeTag("nosell");

               // if the item has no tags, remove the tag compound
               // so it may stack with other items of the same type
               if (itemStack.getTagCompound().hasNoTags())
                  itemStack.setTagCompound(null);
            }
         }

         // tag the current item
         if (!shouldSell)
            errorMessage = new TextComponentString(CommandEconomy.MSG_NOSELL_ON_ALL);
         else
            errorMessage = new TextComponentString(CommandEconomy.MSG_NOSELL_OFF_ALL);
         sender.sendMessage(errorMessage);
      }
      // only change the status of one item
      else {
         // grab the player's held item
         ItemStack heldItem = player.inventory.getCurrentItem();

         // check whether an item was found
         if (heldItem.isEmpty() ||
                heldItem == ItemStack.EMPTY) {
            errorMessage = new TextComponentString(CommandEconomy.ERROR_ITEM_MISSING);
            sender.sendMessage(errorMessage);
            return;
         }

         // if the item should not be sold, mark it as such
         if (!shouldSell) {
            // if the item has no tags, create a container for them
            if (!heldItem.hasTagCompound())
               heldItem.setTagCompound(new NBTTagCompound());

            // mark the item to not be sold
            heldItem.getTagCompound().setBoolean("nosell", true);

            // report success
            if (heldItem.hasDisplayName())
               errorMessage = new TextComponentString(heldItem.getDisplayName() + CommandEconomy.MSG_NOSELL_ON_HELD);
            else
               errorMessage = new TextComponentString(CommandEconomy.MSG_NOSELL_ON_HELD_NAMED);

            sender.sendMessage(errorMessage);
         }
         // if the item should be sold, remove any mark saying otherwise
         else {
            // if the item has no tags, there is nothing to do
            if (!heldItem.hasTagCompound())
               return;

            // remove the item's mark to not be sold
            heldItem.getTagCompound().removeTag("nosell");

            // if the item has no tags, remove the tag compound
            // so it may stack with other items of the same type
            if (heldItem.getTagCompound().hasNoTags())
               heldItem.setTagCompound(null);

            // report success
            if (heldItem.hasDisplayName())
               errorMessage = new TextComponentString(heldItem.getDisplayName() + CommandEconomy.MSG_NOSELL_OFF_HELD_NAMED);
            else
               errorMessage = new TextComponentString(CommandEconomy.MSG_NOSELL_OFF_HELD);

            sender.sendMessage(errorMessage);
         }
      }

      return;
  }

  @Override
  public String getName() {
      return CommandEconomy.CMD_NOSELL;
  }

  @Override
  public String getUsage(ICommandSender sender) {
      return CommandEconomy.CMD_USAGE_NOSELL;
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
      return true;
   }

   @Override
   public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos)
   {
      if (args == null || args.length == 0)
         return new LinkedList<String>();

      if (args.length == 1)
      {
         return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {CommandEconomy.TRUE, CommandEconomy.FALSE, CommandEconomy.ALL});
      }
      else if (args.length == 2)
      {
         return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {CommandEconomy.ALL});
      }

      return new LinkedList<String>();
   }

   @Override
   public boolean isUsernameIndex(java.lang.String[] args, int index)
   {
      return false;
   }
}