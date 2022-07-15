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
      // request should not be null
      if (args == null || args.length == 0) {
         InterfaceMinecraft.forwardToUser(sender, getUsage(sender));
         return;
      }

      // set up variables
      String  username          = null;
      String  wareID            = null;
      int     baseArgsLength    = args.length; // number of args, not counting special keywords
      int     quantity          = 0; // holds ware quantities
      float   pricePercent      = 1.0f;
      boolean shouldManufacture = false;       // whether or not to factor in manufacturing for purchases

      // check for and process special keywords and zero-length args
      for (String arg : args) {
         // if a zero-length arg is detected, stop
         if (arg == null || arg.length() == 0) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_CHECK);
            return;
         }

         // special keywords start with certain symbols
         if (!arg.startsWith(CommandEconomy.ARG_SPECIAL_PREFIX) && !arg.startsWith(CommandEconomy.PRICE_PERCENT))
            continue;

         // if a special keyword is detected,
         // adjust the arg length count for non-special args
         baseArgsLength--;

         // check whether user is specifying the transaction price multiplier
         if (arg.startsWith(CommandEconomy.PRICE_PERCENT)) {
            pricePercent = CommandProcessor.parsePricePercentArgument(sender.getCommandSenderEntity().getUniqueID(), arg, false);

            // check for error
            if (Float.isNaN(pricePercent))
               return; // an error message has already been printed

            continue; // skip to the next argument
         }

         // check whether user specifies manufacturing the ware
         if (arg.equals(CommandEconomy.MANUFACTURING))
            shouldManufacture = true;
      }

      // command must have the right number of args
      if (baseArgsLength < 1 ||
          baseArgsLength > 3) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_NUM_ARGS + CommandEconomy.CMD_USAGE_CHECK);
         return;
      }

      // if one argument is given,
      // the it is a ware ID
      if (baseArgsLength == 1) {
         username = sender.getName();
         wareID = args[0];
      }

      // if two arguments are given,
      // the second must be a quantity
      else if (baseArgsLength == 2) {
         try {
            // assume the second argument is a number
            quantity = Integer.parseInt(args[1]);
         } catch (NumberFormatException e) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_QUANTITY + CommandEconomy.CMD_USAGE_CHECK);
            return;
         }

         // grab remaining variables
         username = sender.getName();
         wareID = args[0];
      }

      // if three arguments are given,
      // then they include a username and a quantity
      else if (baseArgsLength == 3) {
         // try to process quantity
         try {
            quantity = Integer.parseInt(args[2]);
         } catch (NumberFormatException e) {
            InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ZERO_LEN_ARGS + CommandEconomy.CMD_USAGE_BLOCK_CHECK);
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
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_ENTITY_SELECTOR);
         return;
      }

      // grab user's UUID once
      UUID userID = InterfaceMinecraft.getPlayerIDStatic(username);

      // check if command sender has permission to
      // execute this command for other players
      if (!InterfaceMinecraft.permissionToExecute(userID, sender)) {
         InterfaceMinecraft.forwardErrorToUser(sender, CommandEconomy.ERROR_PERMISSION);
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
            InterfaceMinecraft.forwardErrorToUser(sender, PlatformStrings.ERROR_HANDS);
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
               ((float) itemStack.getMaxDamage() - itemStack.getItemDamage()) / itemStack.getMaxDamage(), shouldManufacture);
            return;
         }
      }

      // call corresponding function
      Marketplace.check(userID, wareID, quantity, pricePercent, shouldManufacture);
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
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.WARES);
         else
            return InterfaceMinecraft.getAutoCompletionStrings(args[0], InterfaceMinecraft.AutoCompletionStringCategories.PLAYERS);
      }
      else if (args.length == 2)
      {
         if (sender instanceof EntityPlayer)
            return new LinkedList<String>();
         else
            return InterfaceMinecraft.getAutoCompletionStrings(args[1], InterfaceMinecraft.AutoCompletionStringCategories.WARES);
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