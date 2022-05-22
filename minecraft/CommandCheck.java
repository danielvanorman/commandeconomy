package commandeconomy;

import net.minecraft.command.CommandBase;                   // for registering as a chat command
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;            // for finding who to send messages to
import net.minecraft.util.text.TextComponentString;         // for sending messages to players
import net.minecraft.util.text.TextFormatting;
import net.minecraft.command.EntitySelector;                       // for using command block selectors
import net.minecraft.entity.player.EntityPlayer;            // for printing command block usage and handling held items
import java.util.List;                                      // for autocompleting arguments
import java.util.LinkedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;                        // for handling held items
import net.minecraft.item.Item;
import java.util.UUID;                                      // for more securely tracking users internally

public class CommandCheck extends CommandBase {

  @Override
  public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
      // prepare to tell the user if something is wrong
      TextComponentString errorMessage;

      // request should not be null
      if (args == null || args.length == 0) {
         errorMessage = new TextComponentString(getUsage(sender));
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // command must have the right number of args
      if (args.length < 1 ||
          args.length > 3) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_CHECK);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // check for zero-length args
      if (args[0] == null || args[0].length() == 0 ||
          (args.length >= 2 && (args[1] == null || args[1].length() == 0)) ||
          (args.length == 3 && (args[2] == null || args[2].length() == 0))) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_CHECK);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // set up variables
      String username = null;
      String wareID   = null;
      int    quantity = 0; // holds ware quantities

      // if one argument is given,
      // the it is a ware ID
      if (args.length == 1) {
         username = sender.getName();
         wareID = args[0];
      }

      // if two arguments are given,
      // the second must be a quantity
      else if (args.length == 2) {
         try {
            // assume the second argument is a number
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            errorMessage = new TextComponentString(CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_CHECK);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            return;
         }

         // grab remaining variables
         username = sender.getName();
         wareID = args[0];
      }

      // if three arguments are given,
      // then they include a username and a quantity
      else if (args.length == 3) {
         // try to process quantity
         try {
            quantity = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            errorMessage = new TextComponentString(CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BLOCK_CHECK);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            return;
         }

         // grab remaining variables
         username = args[0];
         wareID = args[1];
      }

      // check for entity selectors
      try {
         if (username != null && EntitySelector.isSelector(username))
            username = EntitySelector.matchOnePlayer(sender, username).getName();
      } catch (Exception e) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_ENTITY_SELECTOR);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // grab user's UUID once
      UUID userID = InterfaceMinecraft.getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!InterfaceMinecraft.permissionToExecute(userID, sender)) {
         errorMessage = new TextComponentString(CommandEconomy.ERROR_PERMISSION);
         errorMessage.getStyle().setColor(TextFormatting.RED);
         sender.sendMessage(errorMessage);
         return;
      }

      // check whether the ware the user's is currently holding should be checked
      // the idea of checking the user's held item is from
      // DynamicEconomy ( https://dev.bukkit.org/projects/dynamiceconomy-v-01 )
      if (wareID.equalsIgnoreCase(CommandEconomy.HELD_ITEM)) {
         // get player information
         EntityPlayer player = null;
         if (username.equals(sender.getName()) &&
             (sender instanceof EntityPlayer))
            player = (EntityPlayer) sender;
         else
            player = server.getPlayerList().getPlayerByUsername(username);

         if (player == null)
            return; // no message to avoid needless messaging when automated

         // get whatever is in the player's hand
         ItemStack itemStack = player.getCommandSenderEntity().getHeldEquipment().iterator().next();

         // check if nothing is in the player's hand
         if (itemStack == null ||
             itemStack.isEmpty() ||
             itemStack == ItemStack.EMPTY) {
            errorMessage = new TextComponentString(CommandEconomy.ERROR_HANDS_MINECRAFT);
            errorMessage.getStyle().setColor(TextFormatting.RED);
            sender.sendMessage(errorMessage);
            return;
         }

         // get the ware ID of whatever is in the player's hand
         if (itemStack.isItemStackDamageable()) {
            wareID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString();
         } else {
            if (itemStack.getMetadata() == 0) {
               wareID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString();

               // in case metadata of 0 is necessary
               if (Marketplace.translateWareID(wareID).isEmpty())
                  wareID += "&0";
            }
            else
               wareID = Item.REGISTRY.getNameForObject(itemStack.getItem()).toString() + "&" + itemStack.getMetadata();
         }

         // get the amount of whatever is in the player's hand
         if (quantity == 0)
            quantity = itemStack.getCount();

         // if the wares are damageable, handle potential damage
         if (itemStack.isItemStackDamageable() && itemStack.isItemDamaged()) {
            Marketplace.check(userID, wareID, quantity,
               ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage(), false);
            return;
         }
      }

      // call corresponding function
      Marketplace.check(userID, wareID, quantity, false);
      return;
  }

   @Override
   public String getName() {
       return CommandEconomy.CMD_CHECK;
   }

   @Override
   public String getUsage(ICommandSender sender) {
      if (sender instanceof EntityPlayer)
         return CommandEconomy.CMD_USAGE_CHECK;
      else
         return CommandEconomy.CMD_USAGE_BLOCK_CHECK;
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

      if (args.length == 1)
      {
         if (sender instanceof EntityPlayer)
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"wares"});
         else
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], new String[] {"players"});
      }
      else if (args.length == 2)
      {
         if (sender instanceof EntityPlayer)
            return new LinkedList<String>();
         else
            return InterfaceMinecraft.getAutoCompletionStrings(args[1], new String[] {"wares"});
      }

      return new LinkedList<String>();
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