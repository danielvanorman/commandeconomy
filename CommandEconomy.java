package commandeconomy;

/**
 * Implements a global marketplace within the video game Minecraft.
 * Chat (or console) commands are used to access the market.
 * <p>
 * <b>Compilation</b>
 * <p>
 * Java 8.291 or above is required to use the program.
 * <p>
 * To compile and run the program, open a terminal (such as CMD),
 * navigate to (or start in) the .java file's directory,
 * and enter the following:<br>
 * javac commandEconomy\*.java<br>
 * java commandEconomy.UserInterfaceTerminal
 * <p>
 * To generate external documentation files, open a terminal (such as CMD),
 * navigate to (or start in) the .java file's directory,
 * and enter the following:<br>
 * javadoc -private -d documentation commandEconomy\*.java
 * <p>
 * This will generate many .html files in the same directory as the .java file.
 * <p>
 * To run the test suite for the program, open a terminal (such as CMD),
 * navigate to (or start in) the .java file's directory,
 * and enter the following:<br>
 * javac commandEconomy\*.java<br>
 * java commandEconomy.TestSuite 2&gt; output.txt
 * <p>
 * Test results will be in the .java file's directory in output.txt.
 * <p>
 * <b>Usage</b>
 * <p>
 * The commands below may entered into the terminal once the program is running. Please use the separate user manual for more information.
 *
 * <table style="width:75%" summary="Commands usable within the market.">
 *    <tr style="text-decoration:underline;font-weight:normal;">
 *        <th>Command</th>
 *        <th>Action</th>
 *    </tr>
 *    <tr>
 *        <td>/help</td>
 *        <td>prints possible commands</td>
 *    </tr>
 *    <tr>
 *        <td>/buy &#60;ware_id&#62; &#60;quantity&#62; [max_price_acceptable] [account_id]</td>
 *        <td>purchases an item</td>
 *    </tr>
 *    <tr>
 *        <td>/sell &#60;ware_id&#62; [quantity] [min_price_acceptable] [account_id]</td>
 *        <td>sells an item</td>
 *    </tr>
 *    <tr>
 *        <td>/check &#60;ware_id&#62; [quantity]</td>
 *        <td>looks up item price and stock</td>
 *    </tr>
 *    <tr>
 *        <td>/sellall [account_id]</td>
 *        <td>sells all tradeable wares in your inventory at current market prices</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/money [account_id]</td>
 *        <td>looks up how much is in an account</td>
 *    </tr>
 *    <tr>
 *        <td>/send &#60;quantity&#62; &#60;recipient_account_id&#62; [sender_account_id]</td>
 *        <td>transfers money from one account to another</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/create &#60;account_id&#62;</td>
 *        <td>opens a new account with the specified id</td>
 *    </tr>
 *    <tr>
 *        <td>/delete &#60;account_id&#62;</td>
 *        <td>closes the account with the specified id</td>
 *    </tr>
 *    <tr>
 *        <td>/grantAccess &#60;player_name&#62; &#60;account_id&#62;</td>
 *        <td>allows a player to view and withdraw from a specified account</td>
 *    </tr>
 *    <tr>
 *        <td>/revokeAccess &#60;player_name&#62; &#60;account_id&#62;</td>
 *        <td>disallows a player to view and withdraw from a specified account</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/save</td>
 *        <td>saves market wares and accounts</td>
 *    </tr>
 *    <tr>
 *        <td>/stop</td>
 *        <td>shutdowns the market</td>
 *    </tr>
 *    <tr>
 *        <td>/reload &#60;config | wares | accounts | all&#62;</td>
 *        <td>reloads part of the marketplace from file</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/add &#60;quantity&#62; [account_id]</td>
 *        <td>summons money</td>
 *    </tr>
 *    <tr>
 *        <td>/set &#60;quantity&#62; [account_id]</td>
 *        <td>sets account's money to a specified amount</td>
 *    </tr>
 *    <tr><td></td><td></td></tr>
 *    <tr>
 *        <td>/printMarket</td>
 *        <td>writes all wares currently tradeable to a file</td>
 *    </tr>
 *    <tr>
 *        <td>/inventory</td>
 *        <td>displays test inventory contents</td>
 *    </tr>
 *    <tr>
 *        <td>/give &#60;ware_id&#62; [quantity]</td>
 *        <td>puts a one or a specific amount of a given id into the test inventory; this id may or may not be tradeable within the marketplace</td>
 *    </tr>
 *    <tr>
 *        <td>/take &#60;ware_id&#62; [quantity]</td>
 *        <td>removes all or a specific amount of a given id from the test inventory</td>
 *    </tr>
 *    <tr>
 *        <td>/changeName &#60;player_name&#62;</td>
 *        <td>sets the test player's name and ID</td>
 *    </tr>
 * </table>
 * <p>
 * When optional account IDs ([account-id]) are not given,
 * the default is to use the command-issuing player's personal account.
 * <p>
 * A word of caution about using the commands above.
 * <p>
 * Without Minecraft, this program uses a test inventory without any enforced maximum
 * for how much a slot may hold of a single ware. In Minecraft, a single slot may only
 * hold 64 wares and multiple slots may hold the same ware. These differences between
 * the test inventory and standard Minecraft inventories affect how some commands work.
 * For example, this program's buying feature prevents overflowing a normal Minecraft
 * inventory by only capping a single purchase's size to 64 per slot multiplied by
 * the number of available slots. For the test inventory, limiting purchases to
 * 64 wares per slot is arbitrary since it won't overflow until
 * reaching 2^31 - 1 wares in a single slot.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-01-29
 */
public final class CommandEconomy {
   /** lowercase name for accessing services and information */
   public static final String MODID   = StringTable.MODID;
   /** title case name for presentation */
   public static final String NAME    = "Command Economy";
   /** version of this mod */
   public static final String VERSION = "0.8.2";

   // FUNCTIONS
   /**
    * Main function for initializing the market.
    * Warning: Config.userInterface must be initialized before calling this function!
    *
    * @param args unused
    */
   public static void start(String[] args) {
      if (Config.userInterface == null) {
         System.err.println(StringTable.INITIALIZATION_ERROR);
         return;
      }

      // initialize the market
      Config.loadConfig();
         // if loading configuration fails, default values are loaded
         // new values may be loaded through the command "reload config"
      Marketplace.loadWares();
         // if loading wares fails, a warning is printed,
         // advising users to use the command "reload wares"
      Account.loadAccounts();
         // if loading accounts fails, a warning is printed,
         // advising users to use the command "reload accounts" if necessary

      // run the market
      Config.userInterface.serviceRequests(); // runs commands until a stop command is used
   }
}