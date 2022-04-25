package commandeconomy;

import java.util.HashMap;             // for storing wares
import java.io.File;                  // for handling files
import java.util.Scanner;             // for parsing files
import java.io.FileWriter;            // for writing to files
import java.io.FileNotFoundException; // for handling missing file errors
import java.io.IOException;           // for handling miscellaneous file errors
import java.io.PrintStream;           // for toggling output of messages to the user
import java.io.ByteArrayOutputStream; // for controlling console output during tests
import java.util.LinkedList;          // for returning properties of wares found in an inventory
import java.util.ArrayDeque;          // for accessing stored ware entries for saving
import java.util.HashSet;             // for storing IDs of wares changed since last save
import java.lang.reflect.*;           // for accessing private fields and methods
import java.lang.StringBuilder;       // for faster saving, so the same line entries may be stored in two data structures
import java.util.Timer;               // for automatically rebalancing the marketplace
import java.util.UUID;                // for more securely tracking users internally

/**
 * Checks functionality and fault-tolerance of the Terminal Interface.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-02-03
 */
public class TestSuite
{
   // GLOBAL VARIABLES
   /** used to check player name and inventory */
   private static InterfaceTerminal InterfaceTerminal = new InterfaceTerminal();
   /** used to check function console output */
   private static ByteArrayOutputStream testBAOS = new ByteArrayOutputStream();

   /** number of wares in the market after resetting the test environment */
   private static int numTestWares = 0;
   /** number of ware aliases in the market after resetting the test environment */
   private static int numTestWareAliases = 0;
   /** number of accounts in the market after resetting the test environment */
   private static int numTestAccounts = 0;

   // references to test resources for simplifying tests
   private static Ware    testWare1     = null;
   private static Ware    testWare2     = null;
   private static Ware    testWare3     = null;
   private static Ware    testWare4     = null;
   private static Ware    testWareP1    = null;
   private static Ware    testWareP2    = null;
   private static Ware    testWareC1    = null;
   private static Ware    testWareC2    = null;
   private static Ware    testWareC3    = null;
   private static Ware    testWareU1    = null;
   private static Account testAccount1  = null;
   private static Account testAccount2  = null;
   private static Account testAccount3  = null;
   private static Account testAccount4  = null;
   private static Account playerAccount = null;
   private static Account adminAccount  = null;

   // references to Marketplace's private variables
   /** holds all wares in the market */
   private static HashMap<String, Ware> wares;
   /** looks up ware IDs using unique aliases */
   private static HashMap<String, String> wareAliasTranslations;
   /** holds ware entries which failed to load */
   private static ArrayDeque<String> waresErrored;
   /** holds ware IDs whose entries should be regenerated */
   private static HashSet<String> waresChangedSinceLastSave;
   /** maps ware IDs to ware entries, easing regenerating changed wares' entries for saving */
   private static HashMap<String, StringBuilder> wareEntries;
   /** holds ware entries in the order they successfully loaded in; makes reloading faster */
   private static ArrayDeque<StringBuilder> waresLoadOrder;
   /** holds alternate ware aliases and Forge OreDictionary names for saving */
   private static StringBuilder alternateAliasEntries;
   /** reference to average ware starting quantity */
   private static Field fStartQuanBaseAverage;
   /** reference to average ware base price */
   private static Field fPriceBaseAverage;

   // references to Account's private variables
   /** holds all accounts usable in the market */
   private static HashMap<String, Account> accounts;
   /** maps players to accounts they specified should be used by default for their transactions */
   private static HashMap<UUID, Account> defaultAccounts;

   // repeatedly-used constants
   /** default player's UUID */
   private static final UUID PLAYER_ID = InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername);

   /**
    * Tests functions throughout the program.
    *
    * @param args does nothing
    */
   @SuppressWarnings("unchecked") // for grabbing Marketplace's private variables
   public static void main(String[] args) {
      // connect rest of marketplace to terminal interface if they are not already connected
      InterfaceCommand interfaceUsed = new InterfaceTerminal();
      Config.commandInterface        = interfaceUsed;

      // set up testing environment
      String failedTests = ""; // tracks failed tests for reporting after finishing execution
      InterfaceTerminal.ops.add(PLAYER_ID);

      // prevent config options from interfering with tests
      Config.resetConfig();
      Config.accountMaxCreatedByIndividual = -1;

      // don't overwrite user information
      Config.filenameWares     = "config" + File.separator + "CommandEconomy" + File.separator + "testWares.txt";
      Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt";
      Config.filenameAccounts  = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";
      Config.filenameConfig    = "CommandEconomy" + File.separator + "testConfig.txt";

      Config.filenameNoPathWares = "testWares.txt";
      Field filenameNoPathWaresSave = null;
      Field filenameNoPathAccounts  = null;

      try {
         filenameNoPathWaresSave = Config.class.getDeclaredField("filenameNoPathWaresSave");
         filenameNoPathAccounts  = Config.class.getDeclaredField("filenameNoPathAccounts");
         filenameNoPathWaresSave.setAccessible(true);
         filenameNoPathAccounts.setAccessible(true);
         filenameNoPathWaresSave.set(null, "testWaresSaved.txt");
         filenameNoPathAccounts.set(null,  "testAccounts.txt");
      } catch (Exception e) {
         System.err.println("failed to protect user data");
         e.printStackTrace();
      }

      // grab Marketplace's and Account's private variables
      try {
         fStartQuanBaseAverage = Marketplace.class.getDeclaredField("startQuanBaseAverage");
         fPriceBaseAverage = Marketplace.class.getDeclaredField("priceBaseAverage");
         Field fWares = Marketplace.class.getDeclaredField("wares");
         Field fWareAliasTranslations     = Marketplace.class.getDeclaredField("wareAliasTranslations");
         Field fWaresLoadOrder            = Marketplace.class.getDeclaredField("waresLoadOrder");
         Field fWaresErrored              = Marketplace.class.getDeclaredField("waresErrored");
         Field fWaresChangedSinceLastSave = Marketplace.class.getDeclaredField("waresChangedSinceLastSave");
         Field fWareEntries               = Marketplace.class.getDeclaredField("wareEntries");
         Field fAlternateAliasEntries     = Marketplace.class.getDeclaredField("alternateAliasEntries");
         Field fAccounts                  = Account.class.getDeclaredField("accounts");
         Field fDefaultAccounts           = Account.class.getDeclaredField("defaultAccounts");
         fStartQuanBaseAverage.setAccessible(true);
         fPriceBaseAverage.setAccessible(true);
         fWares.setAccessible(true);
         fWareAliasTranslations.setAccessible(true);
         fWaresLoadOrder.setAccessible(true);
         fWaresErrored.setAccessible(true);
         fWaresChangedSinceLastSave.setAccessible(true);
         fWareEntries.setAccessible(true);
         fAlternateAliasEntries.setAccessible(true);
         fAccounts.setAccessible(true);
         fDefaultAccounts.setAccessible(true);

         wares                     = (HashMap<String, Ware>) fWares.get(null);
         wareAliasTranslations     = (HashMap<String, String>) fWareAliasTranslations.get(null);
         waresLoadOrder            = (ArrayDeque<StringBuilder>) fWaresLoadOrder.get(null);
         waresErrored              = (ArrayDeque<String>) fWaresErrored.get(null);
         waresChangedSinceLastSave = (HashSet<String>) fWaresChangedSinceLastSave.get(null);
         wareEntries               = (HashMap<String, StringBuilder>) fWareEntries.get(null);
         alternateAliasEntries     = (StringBuilder) fAlternateAliasEntries.get(null);
         accounts                  = (HashMap<String, Account>) fAccounts.get(null);
         defaultAccounts           = (HashMap<UUID, Account>) fDefaultAccounts.get(null);
      } catch (Exception e) {
         System.err.println("failed to access private variables");
         e.printStackTrace();
         return;
      }

      // disable sending messages to users while testing
      PrintStream originalStream = System.out;
      PrintStream testStream = new PrintStream(testBAOS);
      System.setOut(testStream);

      System.err.println("\nexecuting test suite....\n");

      // test loading custom configuration
      if (testLoadConfig())
         System.err.println("test passed - loadConfig()\n");
      else {
         System.err.println("test failed - loadConfig()\n");
         failedTests += "   loadConfig()\n";
      }

      // test filling the market with test wares
      // even if this fails, the test environment will be reset
      // to expected values before test wares are used
      if (testLoadWares()) {
         System.err.println("test passed - loadWares()\n");
      }
      else {
         System.err.println("test failed - loadWares()\n");
         failedTests += "   loadWares()\n";
      }

      // test creating and trading wares whose
      // current properties (quantity, price, etc.)
      // are directly tied to other wares' current properties
      if (testLinkedWares())
         System.err.println("test passed - linked wares\n");
      else {
         System.err.println("test failed - linked wares\n");
         failedTests += "   linked wares\n";
      }

      // test detecting and correcting errors for wares
      if (testWareValidate())
         System.err.println("test passed - Ware.validate()\n");
      else {
         System.err.println("test failed - Ware.validate()\n");
         failedTests += "   Ware.validate()\n";
      }

      // test creating test accounts
      // even if this fails, the test environment will be reset
      // to expected values before test accounts are used
      if (testAccountCreation()) {
         System.err.println("test passed - Account's constructors and addMoney()\n");
      }
      else {
         System.err.println("test failed - Account's constructors and addMoney()\n");
         failedTests += "   Account's constructors and addMoney()\n";
      }

      // test changing account permissions
      if (testAccountAccess())
         System.err.println("test passed - Account's permissions functions\n");
      else {
         System.err.println("test failed - Account's permissions functions\n");
         failedTests += "   Account's permissions functions()\n";
      }

      // test checking account funds
      if (testAccountCheck())
         System.err.println("test passed - Account's check()\n");
      else {
         System.err.println("test failed - Account's check()\n");
         failedTests += "   Account's check()\n";
      }

      // test transferring funds between accounts
      if (testAccountSendMoney())
         System.err.println("test passed - Account's sendMoney()\n");
      else {
         System.err.println("test failed - Account's sendMoney()\n");
         failedTests += "   Account's sendMoney()\n";
      }

      // test destroying accounts
      // even if this fails, the test environment will be reset
      // to expected values before test accounts are used
      if (testAccountDeletion()) {
         System.err.println("test passed - Account's deleteAccount()\n");
      }
      else {
         System.err.println("test failed - Account's deleteAccount()\n");
         failedTests += "   Account's deleteAccount()\n";
      }

      // test setting accounts as defaults for players' transactions
      if (testDefaultAccounts()) {
         System.err.println("test passed - Account's default account functionality\n");
      }
      else {
         System.err.println("test failed - Account's default account functionality\n");
         failedTests += "   Account's default account functionality\n";
      }

      // test admin permissions
      if (testAdminPermissions())
         System.err.println("test passed - testAdminPermissions()\n");
      else {
         System.err.println("test failed - testAdminPermissions()\n");
         failedTests += "   /op, /deop, isAnOp(), and permissionToExecute()\n";
      }

      // test finding and translating ware IDs
      if (testTranslateWareID())
         System.err.println("test passed - checkWareID()\n");
      else {
         System.err.println("test failed - checkWareID()\n");
         failedTests += "   checkWareID()\n";
      }

      // test retrieving prices
      // if prices cannot retrieved, don't test buying and selling
      if (testGetPrice()) {
         System.err.println("test passed - getPrice()\n");

         // test displaying ware prices and quantities
         if (testCheck())
            System.err.println("test passed - check()\n");
         else {
            System.err.println("test failed - check()\n");
            failedTests += "   check()\n";
         }

         // test buying wares
         if (testBuy())
            System.err.println("test passed - buy()\n");
         else {
            System.err.println("test failed - buy()\n");
            failedTests += "   buy()\n";
         }

         // test selling wares
         if (testSell())
            System.err.println("test passed - sell()\n");
         else {
            System.err.println("test failed - sell()\n");
            failedTests += "   sell()\n";
         }

         // test selling all wares in inventory at once
         if (testSellAll())
            System.err.println("test passed - sellAll()\n");
         else {
            System.err.println("test failed - sellAll()\n");
            failedTests += "   sellAll()\n";
         }
      }
      else {
         System.err.println("test suite canceled execution for check(), buy(), sell(), and sellAll() - getPrice() failed testing\n");
         failedTests += "   getPrice()\n";
      }

      // test saving and loading wares
      if (testWareIO())
         System.err.println("test passed - saveWares() and loadWares()\n");
      else {
         System.err.println("test failed - saveWares() and loadWares()\n");
         failedTests += "   testWareIO()'s saveWares() and loadWares()\n";
      }

      // test saving and loading accounts
      if (testAccountIO())
         System.err.println("test passed - saveAccounts() and loadAccounts()\n");
      else {
         System.err.println("test failed - saveAccounts() and loadAccounts()\n");
         failedTests += "   testAccountIO()'s saveAccounts() and loadAccounts()\n";
      }

      // test servicing user requests
      if (testServiceRequests())
         System.err.println("test passed - various serviceRequest() functions\n");
      else {
         System.err.println("test failed - various serviceRequest() functions\n");
         failedTests += "   testServiceRequests()'s various serviceRequest() functions\n";
      }

      // test the command /create
      if (testCreate())
         System.err.println("test passed - testCreate()\n");
      else {
         System.err.println("test failed - testCreate()\n");
         failedTests += "   /create\n";
      }

      // test the command /changeStock
      if (testChangeStock())
         System.err.println("test passed - testChangeStock()\n");
      else {
         System.err.println("test failed - testChangeStock()\n");
         failedTests += "   /changeStock\n";
      }

      // test AI functionality
      if (testAI())
         System.err.println("test passed - testAI()\n");
      else {
         System.err.println("test failed - testAI()\n");
         failedTests += "   AI\n";
      }

      // teardown testing environment
      // restore file names
      Config.filenameWares     = "config" + File.separator + "CommandEconomy" + File.separator + "wares.txt";
      Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "waresSaved.txt";
      Config.filenameAccounts  = "config" + File.separator + "CommandEconomy" + File.separator + "accounts.txt";
      Config.filenameConfig    = "CommandEconomy" + File.separator + "config.txt";

      Config.filenameNoPathWares = "wares.txt";
      if (filenameNoPathWaresSave != null) {
         try {
            filenameNoPathWaresSave.set(null, "waresSaved.txt");
            filenameNoPathAccounts.set(null,  "accounts.txt");
         } catch (Exception e) { }
      }

      // restore config options
      Config.loadConfig();

      // enable sending messages to users while testing
      System.out.flush();
      System.setOut(originalStream);

      // report any test failures found
      if (!failedTests.isEmpty())
         System.err.println("Tests checking the following failed:\n" + failedTests);
      else
         System.err.println("All tests passed!");

      System.out.println("test suite finished execution\n");
   }

   /**
    * Forces the market to be a specific setup, which tests may rely on when checking for errors.
    * <p>
    * <b>Wares</b>
    * <table style="width:75%" summary="Test setup wares.">
    *    <tr style="text-decoration:underline;font-weight:normal;">
    *        <th>Class</th>
    *        <th>Ware ID</th>
    *        <th>Base Price</th>
    *        <th>Quantity</th>
    *        <th>Level</th>
    *        <th>Alias</th>
    *    </tr>
    *    <tr>
    *        <td>WareMaterial</td>
    *        <td>test:material1</td>
    *        <td>1.0</td>
    *        <td>256</td>
    *        <td>0</td>
    *        <td></td>
    *    </tr>
    *    <tr>
    *        <td>WareMaterial</td>
    *        <td>test:material2</td>
    *        <td>27.6</td>
    *        <td>5</td>
    *        <td>1</td>
    *        <td></td>
    *    </tr>
    *    <tr>
    *        <td>WareMaterial</td>
    *        <td>test:material3</td>
    *        <td>4.0</td>
    *        <td>64</td>
    *        <td>2</td>
    *        <td>mat3</td>
    *    </tr>
    *    <tr>
    *        <td>WareMaterial</td>
    *        <td>minecraft:material4</td>
    *        <td>8.0</td>
    *        <td>32</td>
    *        <td>3</td>
    *        <td>material4</td>
    *    </tr>
    *    <tr>
    *        <td>WareProcessed</td>
    *        <td>test:processed1</td>
    *        <td>1.1</td>
    *        <td>16</td>
    *        <td>4</td>
    *        <td></td>
    *    </tr>
    *    <tr>
    *        <td>WareProcessed</td>
    *        <td>test:processed2</td>
    *        <td>14.3</td>
    *        <td>8</td>
    *        <td>5</td>
    *        <td></td>
    *    </tr>
    *    <tr>
    *        <td>WareCrafted</td>
    *        <td>test:crafted1</td>
    *        <td>19.2</td>
    *        <td>128</td>
    *        <td>1</td>
    *        <td>craft1</td>
    *    </tr>
    *    <tr>
    *        <td>WareCrafted</td>
    *        <td>test:crafted2</td>
    *        <td>24.24</td>
    *        <td>64</td>
    *        <td>2</td>
    *        <td></td>
    *    </tr>
    *    <tr>
    *        <td>WareCrafted</td>
    *        <td>test:crafted3</td>
    *        <td>9.6</td>
    *        <td>32</td>
    *        <td>3</td>
    *        <td></td>
    *    </tr>
    *    <tr>
    *        <td>WareUntradeable</td>
    *        <td>test:untradeable1</td>
    *        <td>16.0</td>
    *        <td>0</td>
    *        <td>0</td>
    *        <td>notrade1</td>
    *    </tr>
    * </table>
    * <p>
    * <b>Accounts</b>
    * <table style="width:75%" summary="Test setup accounts.">
    *    <tr style="text-decoration:underline;font-weight:normal;">
    *        <th>Account ID</th>
    *        <th>Money</th>
    *        <th>Permitted Players</th>
    *    </tr>
    *    <tr>
    *        <td>testAccount1</td>
    *        <td>10.0</td>
    *        <td>[playername]</td>
    *    </tr>
    *    <tr>
    *        <td>testAccount2</td>
    *        <td>20.0</td>
    *        <td>[playername]</td>
    *    </tr>
    *    <tr>
    *        <td>testAccount3</td>
    *        <td>30.0</td>
    *        <td>possibleID</td>
    *    </tr>
    *    <tr>
    *        <td>testAccount4</td>
    *        <td>6.0</td>
    *        <td>[Empty]</td>
    *    </tr>
    *    <tr>
    *        <td>[playername]</td>
    *        <td>30.0</td>
    *        <td>[playername]</td>
    *    </tr>
    * </table>
    * <p>
    * <b>Stock Levels</b>
    * <table style="width:75%" summary="Test stock levels across hierarchy levels.">
    *    <tr style="text-decoration:underline;font-weight:normal;">
    *        <th></th>
    *        <th>0</th>
    *        <th>1</th>
    *        <th>2</th>
    *        <th>3</th>
    *        <th>4</th>
    *        <th>5</th>
    *    </tr>
    *    <tr>
    *        <td>initial</td>
    *        <td>256</td>
    *        <td>128</td>
    *        <td>64</td>
    *        <td>32</td>
    *        <td>16</td>
    *        <td>8</td>
    *    </tr>
    *    <tr>
    *        <td>overstocked</td>
    *        <td>1024</td>
    *        <td>512</td>
    *        <td>256</td>
    *        <td>128</td>
    *        <td>64</td>
    *        <td>32</td>
    *    </tr>
    *    <tr>
    *        <td>equilibrium</td>
    *        <td>256</td>
    *        <td>128</td>
    *        <td>64</td>
    *        <td>32</td>
    *        <td>16</td>
    *        <td>8</td>
    *    </tr>
    *    <tr>
    *        <td>understocked</td>
    *        <td>128</td>
    *        <td>64</td>
    *        <td>32</td>
    *        <td>16</td>
    *        <td>8</td>
    *        <td>4</td>
    *    </tr>
    * </table>
    * <p>
    * <b>Components</b>
    * <table style="width:50%" summary="Components of test wares made from other wares.">
    *    <tr style="text-decoration:underline;font-weight:normal;">
    *        <th>Ware ID</th>
    *        <th>Component IDs</th>
    *    </tr>
    *    <tr>
    *        <td>test:processed1</td>
    *        <td>test:material1</td>
    *    </tr>
    *    <tr>
    *        <td>test:processed2</td>
    *        <td>test:material1, test:material3, minecraft:material4</td>
    *    </tr>
    *    <tr>
    *        <td>test:processed3</td>
    *        <td>test:untradeable1</td>
    *    </tr>
    *    <tr>
    *        <td>test:crafted1</td>
    *        <td>test:untradeable1</td>
    *    </tr>
    *    <tr>
    *        <td>test:crafted2</td>
    *        <td>test:material1, test:crafted1</td>
    *    </tr>
    *    <tr>
    *        <td>test:crafted3</td>
    *        <td>minecraft:material4</td>
    *    </tr>
    * </table>
    */
   private static void resetTestEnvironment() {
      // clear everything
      wares.clear();
      waresLoadOrder.clear();
      waresErrored.clear();
      waresChangedSinceLastSave.clear();
      wareEntries.clear();
      wareAliasTranslations.clear();
      alternateAliasEntries.setLength(0);
      accounts.clear();
      defaultAccounts.clear();
      InterfaceTerminal.inventory.clear();
      InterfaceTerminal.inventoryNorth.clear();
      InterfaceTerminal.inventoryEast.clear();
      InterfaceTerminal.inventoryWest.clear();
      InterfaceTerminal.inventorySouth.clear();
      InterfaceTerminal.inventoryUp.clear();
      InterfaceTerminal.inventoryDown.clear();

      // config
      Config.resetConfig();

      Config.accountMaxCreatedByIndividual = -1;

      Config.startQuanBase = new int[]{ 256, 128,  64,  32, 16,  8};
      Config.quanHigh      = new int[]{1024, 512, 256, 128, 64, 32};
      Config.quanMid       = new int[]{ 256, 128,  64,  32, 16,  8};
      Config.quanLow       = new int[]{ 128,  64,  32,  16,  8,  4};

      Config.crossWorldMarketplace = true;
      Config.disableAutoSaving     = true;

      Config.priceFloor           = 0.0f;
      Config.priceFloorAdjusted   = 1.0f;
      Config.priceCeiling         = 2.0f;
      Config.priceCeilingAdjusted = -1.0f;

      // wares
      // raw materials
      wares.put("test:material1", new WareMaterial("test:material1", "", 1.0f, 256, (byte) 0));
      wares.put("test:material2", new WareMaterial("test:material2", "", 27.6f, 5, (byte) 1));
      wares.put("test:material3", new WareMaterial("test:material3", "mat3", 4.0f, 64, (byte) 2));
      wareAliasTranslations.put("mat3", "test:material3");
      wares.put("minecraft:material4", new WareMaterial("minecraft:material4", "material4", 8.0f, 32, (byte) 3));
      wareAliasTranslations.put("material4", "minecraft:material4");
      // untradeable
      wares.put("test:untradeable1", new WareUntradeable("test:untradeable1", "notrade1", 16.0f));
      wareAliasTranslations.put("notrade1", "test:untradeable1");
      // processed
      wares.put("test:processed1", new WareProcessed(new String[]{"test:material1"}, "test:processed1", "", 16, 1, (byte) 4));
      wares.put("test:processed2", new WareProcessed(new String[]{"test:material1", "test:material3", "minecraft:material4"}, "test:processed2", "", 8, 1, (byte) 5));
      wares.put("test:processed3", new WareProcessed(new String[]{"test:untradeable1"}, "test:processed3", "", 32, 10, (byte) 3));
      // crafted
      wares.put("test:crafted1", new WareCrafted(new String[]{"test:untradeable1"}, "test:crafted1", "craft1", 128, 1, (byte) 1));
      wareAliasTranslations.put("craft1", "test:crafted1");
      wares.put("test:crafted2", new WareCrafted(new String[]{"test:material1", "test:crafted1"}, "test:crafted2", "", 64, 1, (byte) 2));
      wares.put("test:crafted3", new WareCrafted(new String[]{"minecraft:material4"}, "test:crafted3", "", 32, 4, (byte) 3));

      // update load order
      StringBuilder json = new StringBuilder(wares.get("test:material1").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:material1", json);
      json = new StringBuilder(wares.get("test:material2").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:material2", json);
      json = new StringBuilder(wares.get("test:material3").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:material3", json);
      json = new StringBuilder(wares.get("minecraft:material4").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("minecraft:material4", json);
      json = new StringBuilder(wares.get("test:untradeable1").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:untradeable1", json);
      json = new StringBuilder(wares.get("test:processed1").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:processed1", json);
      json = new StringBuilder(wares.get("test:processed2").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:processed2", json);
      json = new StringBuilder(wares.get("test:processed3").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:processed3", json);
      json = new StringBuilder(wares.get("test:crafted1").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:crafted1", json);
      json = new StringBuilder(wares.get("test:crafted2").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:crafted2", json);
      json = new StringBuilder(wares.get("test:crafted3").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:crafted3", json);

      // alternate aliases
      alternateAliasEntries.append( "4,#testName,test:ware,test:material2,test:material1\n");
      alternateAliasEntries.append("4,testAlternateAlias,test:material1\n");
      wareAliasTranslations.put("#testName", "test:material2");
      wareAliasTranslations.put("testAlternateAlias", "test:material1");

      // accounts
      Account.makeAccount("testAccount1", PLAYER_ID, 10.0f);
      Account.makeAccount("testAccount2", PLAYER_ID, 20.0f);
      Account.makeAccount("testAccount3", InterfaceTerminal.getPlayerIDStatic("possibleID"), 30.0f);
      Account.makeAccount("testAccount4", null, 6.0f);
      Account.makeAccount(InterfaceTerminal.playername, PLAYER_ID, 30.0f);
      Account.makeAccount("$admin$", InterfaceTerminal.getPlayerIDStatic("$admin$"), Float.POSITIVE_INFINITY);

      // update wares pointers
      testWare1  = wares.get("test:material1");
      testWare2  = wares.get("test:material2");
      testWare3  = wares.get("test:material3");
      testWare4  = wares.get("minecraft:material4");
      testWareP1 = wares.get("test:processed1");
      testWareP2 = wares.get("test:processed2");
      testWareC1 = wares.get("test:crafted1");
      testWareC2 = wares.get("test:crafted2");
      testWareC3 = wares.get("test:crafted3");
      testWareU1 = wares.get("test:untradeable1");

      // update accounts pointers
      testAccount1  = accounts.get("testAccount1");
      testAccount2  = accounts.get("testAccount2");
      testAccount3  = accounts.get("testAccount3");
      testAccount4  = accounts.get("testAccount4");
      playerAccount = accounts.get(InterfaceTerminal.playername);
      adminAccount = accounts.get("$admin$");

      // set globals describing the test environment
      numTestWares = wares.size();
      numTestWareAliases = wareAliasTranslations.size();
      numTestAccounts = accounts.size();

      // calculate price base and starting quantity averages
      float priceBaseAverage     = 0.0f;
      float startQuanBaseAverage = 0;
      int numAverageExcludedWares = 0;
      for (Ware ware : wares.values()) {
         if (ware instanceof WareUntradeable) {
            numAverageExcludedWares++;
         }
         else {
            priceBaseAverage     += ware.getBasePrice();
            startQuanBaseAverage += ware.getQuantity();
         }
      }

      priceBaseAverage     /= numTestWares - numAverageExcludedWares;
      // truncate the price to avoid rounding and multiplication errors
      priceBaseAverage     = ((int) (priceBaseAverage * CommandEconomy.PRICE_PRECISION)) / ((float) CommandEconomy.PRICE_PRECISION);
      startQuanBaseAverage /= numTestWares - numAverageExcludedWares;

      try {
         fPriceBaseAverage.setFloat(null, priceBaseAverage);
         fStartQuanBaseAverage.setFloat(null, startQuanBaseAverage);
      } catch (Exception e) {
         System.err.println("could not set Marketplace averages");
         e.printStackTrace();
      }

      return;
   }

   /**
    * Compares a ware's fields to given values. Prints errors, if found.
    *
    * @param testWare  the ware to be checked
    * @param type      expected type for the ware
    * @param alias     expected alternative name for the ware
    * @param level     expected hierarchy level for the ware
    * @param priceBase expected base price for the ware
    * @param quantity  expected stock for the ware
    * @return true if an error was discovered
    */
   private static boolean testWareFields(Ware testWare, Class<?> type, String alias, byte level, float priceBase, int quantity) {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      if (testWare != null) {
         if (!type.isInstance(testWare)) {
            System.err.println("   unexpected type: " + testWare.getClass().getName() + ", should be " + type.getName());
            errorFound = true;
         }

         if (testWare.getAlias() != null && !testWare.getAlias().equals(alias)) {
            // give more specific error message depends on whether given alias is empty
            if (alias.equals(""))
               System.err.println("   unexpected alias: " + testWare.getAlias() + ", should not have an alias");
            else
               System.err.println("   unexpected alias: " + testWare.getAlias() + ", should be " + alias);

            errorFound = true;
         }

         if (testWare.getLevel() != level) {
            System.err.println("   unexpected hierarchy level: " + testWare.getLevel() + ", should be " + level);
            errorFound = true;
         }
         // since price bases are truncated before loading, floats are compared here without using a threshold/epsilon
         if (testWare.getBasePrice() != priceBase) {
            System.err.println("   unexpected base price: " + testWare.getBasePrice() + ", should be " + priceBase);
            errorFound = true;
         }
         if (testWare.getQuantity() != quantity) {
            System.err.println("   unexpected quantity: " + testWare.getQuantity() + ", should be " + quantity);
            errorFound = true;
         }

         // check if ware should have components
         if (testWare instanceof WareMaterial && testWare.components != null) {
            System.err.println("   ware's components should be null, first entry is " + testWare.components[0]);
            errorFound = true;
         }

         else if ((testWare instanceof WareProcessed || testWare instanceof WareCrafted) && testWare.components == null) {
            System.err.println("   ware's components shouldn't be null");
            errorFound = true;
         }

         else if (testWare instanceof WareUntradeable && testWare.yield == 0 && testWare.components != null) {
            System.err.println("   ware's components should be null, first entry is " + testWare.components[0]);
            errorFound = true;
         } 
         else if (testWare instanceof WareUntradeable && testWare.yield != 0 && testWare.components == null) {
            System.err.println("   ware's components shouldn't be null");
            errorFound = true;
         }
      } else {
         System.err.println("   ware was not loaded");
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Compares an account's fields to given values. Prints errors, if found.
    *
    * @param testAccount     the account to be checked
    * @param money           expected funds held within the account
    * @param permittedPlayer expected player who should have permission to use the account
    * @return true if an error was discovered
    */
   private static boolean testAccountFields(Account testAccount, float money, String permittedPlayer) {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      if (testAccount != null) {
         if (testAccount.getMoney() != money) {
            System.err.println("   unexpected account money: " + testAccount.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check whether player has permission to use account
         if (permittedPlayer != null && !testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic(permittedPlayer))) {
            System.err.println("   account should allow access to the player " + permittedPlayer + ", but does not");
            errorFound = true;
         }
      } else {
         System.err.println("   account was not loaded");
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Converts the inventory into a format usable within the marketplace.
    *
    * @return inventory usable for Marketplace.sellAll()
    */
   private static LinkedList<Marketplace.Stock> getFormattedInventory() {
      // prepare a reformatted container for the wares
      LinkedList<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();

      // convert the inventory to the right format
      for (String wareID : InterfaceTerminal.inventory.keySet()) {
         formattedInventory.add(new Marketplace.Stock(wareID, InterfaceTerminal.inventory.get(wareID), 1.0f));
      }

      return formattedInventory;
   }

   /**
    * Converts a given inventory into a format usable within the marketplace.
    *
    * @param inventoryToUse terminal mode inventory to pull data from
    * @return inventory usable for Marketplace.sellAll()
    */
   private static LinkedList<Marketplace.Stock> formatInventory(HashMap<String, Integer> inventoryToUse) {
      // prepare a reformatted container for the wares
      LinkedList<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();

      // convert the inventory to the right format
      for (String wareID : inventoryToUse.keySet()) {
         formattedInventory.add(new Marketplace.Stock(wareID, inventoryToUse.get(wareID), 1.0f));
      }

      return formattedInventory;
   }

   /**
    * Tests loadConfig().
    *
    * @return whether loadConfig() passed all test cases
    */
   private static boolean testLoadConfig() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // There is no need to reset configuration to known values before testing.
      // When loading configuration, every value should be set.
      // If even a single value is not set to an expected value,
      // then regardless of prior configuration, that's an error.

      // test for handling missing files
      System.err.println("loadConfig() - handling of missing files");
      testBAOS.reset(); // clear buffer holding console output
      Config.filenameConfig = "CommandEconomy" + File.separator + "tempConfig.txt";
      try {
         Config.loadConfig();
      }
      catch (Exception e) {
         System.err.println("loadConfig() - loadConfig() should not throw any exception, but it did\n   was testing for handling missing files");
         e.printStackTrace();
         return false;
      }

      // check handling of missing file
      File fileConfig = new File("config" + File.separator + Config.filenameConfig);
      if (!fileConfig.exists()){
         errorFound = true;
         System.err.println("   default config file failed to be created");
      } else {
         fileConfig.delete();
      }

      Config.filenameConfig = "CommandEconomy" + File.separator + "testConfig.txt";

      // create test config file
      try {
         // open the save file for config, create it if it doesn't exist
         FileWriter fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

         // write test wares file
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "startQuanBase = 000, 100, 200, 300, 400, 500\n" +
            "startQuanMult = 8.0\n" +
            "priceFloor  = 1.0\n" +
            "accountStartingMoney = 1024.0\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );

         // close the file
         fileWriter.close();
      } catch (Exception e) {
         System.err.println("loadConfig() - unable to create test config file");
         e.printStackTrace();
         return false;
      }

      // try to load the test file
      try {
         Config.loadConfig();
      }
      catch (Exception e) {
         System.err.println("loadConfig() - loadConfig() should not throw any exception, but it did\n   was loading test config file");
         e.printStackTrace();
         return false;
      }

      // Assertions are not used in this test suite to
      // allow tests to keep running after an error is detected,
      // rather than throw an exception and stop execution.

      // check configuration values
      try {
         System.err.println("loadConfig() - differences from defaults");
         if (Config.startQuanBase[0] != 0 ||
             Config.startQuanBase[1] != 100 ||
             Config.startQuanBase[2] != 200 ||
             Config.startQuanBase[3] != 300 ||
             Config.startQuanBase[4] != 400 ||
             Config.startQuanBase[5] != 500) {
            errorFound = true;
            System.err.println("   startQuanBase has unexpected values:\n   " +
                               Config.startQuanBase[0] + ", " + Config.startQuanBase[1] +
                               Config.startQuanBase[2] + ", " + Config.startQuanBase[3] + 
                               Config.startQuanBase[4] + ", " + Config.startQuanBase[5] + 
                               "\n   should be: 0, 100, 200, 300, 400, 500");
         }

         if (Config.startQuanMult != 8.0f) {
            errorFound = true;
            System.err.println("   startQuanMult has unexpected value: " +
                               Config.startQuanMult + ", should be 8.0");
         }

         if (Config.priceFloor != 1.0f) {
            errorFound = true;
            System.err.println("   priceFloor has unexpected value: " +
                               Config.priceFloor + ", should be 1.0");
         }

         if (Config.accountStartingMoney != 1024.0f) {
            errorFound = true;
            System.err.println("   accountStartingMoney has unexpected value: " +
                               Config.accountStartingMoney + ", should be 1024.0");
         }

         System.err.println("loadConfig() - defaults");
         if (Config.quanLow[0] != 4096 ||
             Config.quanLow[1] != 2048 ||
             Config.quanLow[2] != 1536 ||
             Config.quanLow[3] != 1024 ||
             Config.quanLow[4] !=  768 ||
             Config.quanLow[5] !=  512) {
            errorFound = true;
            System.err.println("   quanLow has unexpected values:\n   " +
                               Config.quanLow[0] + ", " + Config.quanLow[1] + ", " +
                               Config.quanLow[2] + ", " + Config.quanLow[3] + ", " +
                               Config.quanLow[4] + ", " + Config.quanLow[5] +
                               "\n   should be: 4096, 2048, 1536, 1024, 768, 512");
         }

         if (Config.startQuanSpread != 1.0f) {
            errorFound = true;
            System.err.println("   startQuanSpread has unexpected value: " +
                               Config.startQuanSpread + ", should be 1.0");
         }

         if (Config.priceBuyUpchargeMult != 1.0f) {
            errorFound = true;
            System.err.println("   priceBuyUpchargeMult has unexpected value: " +
                               Config.priceBuyUpchargeMult + ", should be 1.0");
         }
      }
      catch (Exception e) {
         System.err.println("loadConfig() - fatal error: " + e);
         return false;
      }

      System.err.println("loadConfig() - changing config file");
      // create test config file
      try {
         // open the save file for config, create it if it doesn't exist
         FileWriter fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

         // write test wares file
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "quanLow              = 1024, 512, 256, 128, 64, 32\n" +
            "startQuanSpread      = 16.0\n" +
            "priceBuyUpchargeMult = 786.0\n" +
            "accountStartingMoney = 256.0\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );

         // close the file
         fileWriter.close();
      } catch (Exception e) {
         System.err.println("loadConfig() - unable to change test config file");
         e.printStackTrace();
         return false;
      }

      // try to load the test file
      try {
         Config.loadConfig();
      }
      catch (Exception e) {
         System.err.println("loadConfig() - loadConfig() should not throw any exception, but it did\n   was loading changed test config file");
         e.printStackTrace();
         return false;
      }

      // check configuration values
      try {
         System.err.println("loadConfig() - changed differences");
         if (Config.quanLow[0] != 1024 ||
             Config.quanLow[1] !=  512 ||
             Config.quanLow[2] !=  256 ||
             Config.quanLow[3] !=  128 ||
             Config.quanLow[4] !=   64 ||
             Config.quanLow[5] !=   32) {
            errorFound = true;
            System.err.println("   quanLow has unexpected values:\n   " +
                               Config.quanLow[0] + ", " + Config.quanLow[1] + ", " +
                               Config.quanLow[2] + ", " + Config.quanLow[3] + ", " +
                               Config.quanLow[4] + ", " + Config.quanLow[5] +
                               "\n   should be: 1024, 512, 256, 128, 64, 32");
         }

         if (Config.startQuanSpread != 16.0f) {
            errorFound = true;
            System.err.println("   startQuanSpread has unexpected value: " +
                               Config.startQuanMult + ", should be 16.0");
         }

         if (Config.priceBuyUpchargeMult != 786.0f) {
            errorFound = true;
            System.err.println("   priceBuyUpchargeMult has unexpected value: " +
                               Config.priceBuyUpchargeMult + ", should be 786.0");
         }

         if (Config.accountStartingMoney != 256.0f) {
            errorFound = true;
            System.err.println("   accountStartingMoney has unexpected value: " +
                               Config.accountStartingMoney + ", should be 256.0");
         }

         System.err.println("loadConfig() - reset defaults");
         if (Config.startQuanBase[0] != 16384 ||
             Config.startQuanBase[1] !=  9216 ||
             Config.startQuanBase[2] !=  5120 ||
             Config.startQuanBase[3] !=  3072 ||
             Config.startQuanBase[4] !=  2048 ||
             Config.startQuanBase[5] !=  1024) {
            errorFound = true;
            System.err.println("   startQuanBase has unexpected values:\n   " +
                               Config.startQuanBase[0] + ", " + Config.startQuanBase[1] + ", " + 
                               Config.startQuanBase[2] + ", " + Config.startQuanBase[3] + ", " + 
                               Config.startQuanBase[4] + ", " + Config.startQuanBase[5] +
                               "\n   should be: 16384, 9216, 5120, 3072, 2048, 1024");
         }

         if (Config.startQuanMult != 1.0f) {
            errorFound = true;
            System.err.println("   startQuanMult has unexpected value: " +
                               Config.startQuanMult + ", should be 1.0");
         }

         if (Config.priceFloor != 0.0f) {
            errorFound = true;
            System.err.println("   priceFloor has unexpected value: " +
                               Config.priceFloor + ", should be 0.0");
         }
      }
      catch (Exception e) {
         System.err.println("loadConfig() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests loadWares().
    *
    * @return whether loadWares() passed all test cases
    */
   private static boolean testLoadWares() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure test environment is stable
      resetTestEnvironment();

      // ensure no save file is loaded
      Config.filenameWaresSave = "this file should not exist";

      // delete any currently loaded wares
      wares.clear();
      wareAliasTranslations.clear();

      // test for handling missing files
      System.err.println("testLoadWares() - handling of missing files");
      Config.filenameWares = "no file here";
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         System.err.println("testLoadWares() - loadWares() should not throw any exception, but it did\n   was testing for handling missing files");
         e.printStackTrace();
         return false;
      }

      // check handling of missing file
      File fileWares = new File(Config.filenameWares);
      // check local file
      if (fileWares.exists()){
         errorFound = true;
         System.err.println("   \"no file here\" file should not exist in local/world directory");
      }
      // check global file
      fileWares = new File("config" + File.separator + Config.filenameWares);
      if (fileWares.exists()){
         errorFound = true;
         System.err.println("   \"no file here\" file should not exist in global/config directory");
      }

      // further check handling of missing files
      if (wares.size() != 0) {
         System.err.println("   loaded wares despite all files having been missing");
         errorFound = true;
      }

      Config.filenameWares = "config" + File.separator + "CommandEconomy" + File.separator + "testWares.txt";

      // create test wares file
      try {
         // open the save file for wares, create it if it doesn't exist
         FileWriter fileWriter = new FileWriter(Config.filenameWares);

         // possible non-JSON ware entry formats
         // Forge OreDictionary name: 4,name,mostPreferredModelWareID,nextPreferredModelWareID,...,lastPreferredModelWareID
         // alternate alias: 4,alternateAlias,wareID

         // write test wares file
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "{\"type\":\"material\",\"wareID\":\"test:material1\",\"priceBase\":1.0,\"level\":0}\n" +
            "{\"type\":\"material\",\"wareID\":\"test:material2\",\"priceBase\":2.0,\"level\":1,\"quantity\":5}\n" +
            "{\"type\":\"material\",\"alias\":\"mat3\",\"wareID\":\"test:material3\",\"priceBase\":4.0,\"level\":2}\n" +
            "{\"type\":\"material\",\"alias\":\"material4\",\"wareID\":\"minecraft:material4\",\"priceBase\":8.0,\"level\":3}\n" +
            "{\"type\":\"untradeable\",\"alias\":\"notrade1\",\"wareID\":\"test:untradeable1\",\"priceBase\":16.0}\n" +
            "{\"type\":\"untradeable\",\"alias\":\"notrade2\",\"wareID\":\"test:untradeable2\",\"yield\":2,\"componentsIDs\":[\"test:material1\",\"test:material1\",\"test:untradeable1\"]}\n" +
            "{\"type\":\"processed\",\"wareID\":\"test:processed1\",\"priceBase\":1,\"level\":4,\"yield\":1,\"componentsIDs\":[\"test:material1\"]}\n" +
            "{\"type\":\"processed\",\"wareID\":\"test:processed2\",\"priceBase\":1,\"level\":5,\"yield\":1,\"componentsIDs\":[\"test:material1\",\"test:material3\",\"minecraft:material4\"]}\n" +
            "{\"type\":\"processed\",\"wareID\":\"test:processed3\",\"priceBase\":10,\"level\":3,\"yield\":10,\"componentsIDs\":[\"test:untradeable1\"]}\n" +
            "{\"type\":\"crafted\",\"alias\":\"craft1\",\"wareID\":\"test:crafted1\",\"priceBase\":1,\"level\":1,\"yield\":1,\"componentsIDs\":[\"test:untradeable1\"]}\n" +
            "{\"type\":\"crafted\",\"wareID\":\"test:crafted2\",\"priceBase\":1,\"level\":2,\"yield\":1,\"componentsIDs\":[\"test:material1\",\"test:crafted1\"]}\n" +
            "{\"type\":\"crafted\",\"wareID\":\"test:crafted3\",\"priceBase\":4,\"level\":3,\"yield\":4,\"componentsIDs\":[\"minecraft:material4\"]}\n" +
            "\n" +
            "4,#testName,test:ware,test:material2,test:material1\n" +
            "4,testAlternateAlias,test:material1\n"
         );

         // close the file
         fileWriter.close();
      } catch (IOException e) {
         System.err.println("testLoadWares() - unable to create test wares file");
         e.printStackTrace();
         return false;
      }

      // try to load the test file
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         System.err.println("testLoadWares() - loadWares() should not throw any exception, but it did\n   was loading test wares");
         e.printStackTrace();
         return false;
      }

      // Assertions are not used in this test suite to
      // allow tests to keep running after an error is detected,
      // rather than throw an exception and stop execution.

      // prepare to check wares
      Ware testWare; // holds ware currently being checked
      try {
         System.err.println("testLoadWares() - creation of new material ware without specified starting quantity");
         testWare = wares.get("test:material1");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "", (byte) 0, 1.0f, 256);

         System.err.println("testLoadWares() - creation of new material ware with specified starting quantity");
         testWare = wares.get("test:material2");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "", (byte) 1, 2.0f, 5);

         System.err.println("testLoadWares() - creation of new material ware with specified alias");
         testWare = wares.get("test:material3");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "mat3", (byte) 2, 4.0f, 64);
         if (!wareAliasTranslations.containsKey("mat3") ||
             !wareAliasTranslations.get("mat3").equals("test:material3")) {
            System.err.println("   test:material3 did not have expected alias");
            errorFound = true;
         }

         System.err.println("testLoadWares() - creation of new material ware with alias taken from minecraft:wareID");
         testWare = wares.get("minecraft:material4");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "material4", (byte) 3, 8.0f, 32);
         if (!wareAliasTranslations.containsKey("material4") ||
             !wareAliasTranslations.get("material4").equals("minecraft:material4")) {
            System.err.println("   minecraft:material4 did not have expected alias");
            errorFound = true;
         }

         System.err.println("testLoadWares() - creation of an untradeable ware without components");
         testWare = wares.get("test:untradeable1");
         errorFound = errorFound || testWareFields(testWare, WareUntradeable.class, "notrade1", (byte) 0, 16.0f, Integer.MAX_VALUE);
         if (!wareAliasTranslations.containsKey("notrade1") ||
             !wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
            System.err.println("   test:untradeable1 did not have expected alias");
            errorFound = true;
         }

         System.err.println("testLoadWares() - creation of an untradeable ware with components");
         testWare = wares.get("test:untradeable2");
         errorFound = errorFound || testWareFields(testWare, WareUntradeable.class, "notrade2", (byte) 0, 9.0f, Integer.MAX_VALUE);

         System.err.println("testLoadWares() - creation of a processed ware with one component ware");
         testWare = wares.get("test:processed1");
         errorFound = errorFound || testWareFields(testWare, WareProcessed.class, "", (byte) 4, 1.1f, 16);

         System.err.println("testLoadWares() - creation of a processed ware with many component wares");
         testWare = wares.get("test:processed2");
         errorFound = errorFound || testWareFields(testWare, WareProcessed.class, "", (byte) 5, 14.3f, 8);

         System.err.println("testLoadWares() - creation of a processed ware with yield affecting price");
         testWare = wares.get("test:processed3");
         errorFound = errorFound || testWareFields(testWare, WareProcessed.class, "", (byte) 3, 1.76f, 32);

         System.err.println("testLoadWares() - creation of a crafted ware with one component ware and an alias");
         testWare = wares.get("test:crafted1");
         errorFound = errorFound || testWareFields(testWare, WareCrafted.class, "craft1", (byte) 1, 19.2f, 128);
         if (!wareAliasTranslations.containsKey("craft1") ||
             !wareAliasTranslations.get("craft1").equals("test:crafted1")) {
            System.err.println("   test:crafted1 did not have expected alias");
            errorFound = true;
         }

         System.err.println("testLoadWares() - creation of a crafted ware with many component wares, including another crafted ware");
         testWare = wares.get("test:crafted2");
         errorFound = errorFound || testWareFields(testWare, WareCrafted.class, "", (byte) 2, 24.24f, 64);

         System.err.println("testLoadWares() - creation of a crafted ware with yield affecting price");
         testWare = wares.get("test:crafted3");
         errorFound = errorFound || testWareFields(testWare, WareCrafted.class, "", (byte) 3, 2.4f, 32);

         System.err.println("testLoadWares() - checking average for base price");
         if ((float) fPriceBaseAverage.get(null) != 7.8f) {
            errorFound = true;
            System.err.println("   priceBaseAverage is " + (float) fPriceBaseAverage.get(null) + ", should be 7.8");
         }

         System.err.println("testLoadWares() - checking average for base starting quantities");
         if ((float) fStartQuanBaseAverage.get(null) != 76) {
            errorFound = true;
            System.err.println("   startQuanBaseAverage is " + (float) fStartQuanBaseAverage.get(null) + ", should be 76");
         }

         System.err.println("testLoadWares() - checking ware alias translation accuracy");
         if (!wareAliasTranslations.containsKey("mat3")) {
            errorFound = true;
            System.err.println("   mat3 does not exist, should be mapped to test:material3");
         } else {
            if (!wareAliasTranslations.get("mat3").equals("test:material3")) {
               errorFound = true;
               System.err.println("   mat3's ware ID is " + wareAliasTranslations.get("mat3") + ", should be test:material3");
            }
         }
         if (!wareAliasTranslations.containsKey("material4")) {
            errorFound = true;
            System.err.println("   material4 does not exist, should be mapped to minecraft:material4");
         } else {
            if (!wareAliasTranslations.get("material4").equals("minecraft:material4")) {
               errorFound = true;
               System.err.println("   material4's ware ID is " + wareAliasTranslations.get("material4") + ", should be minecraft:material4");
            }
         }
         if (!wareAliasTranslations.containsKey("notrade1")) {
            errorFound = true;
            System.err.println("   notrade1 does not exist, should be mapped to test:untradeable1");
         } else {
            if (!wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
               errorFound = true;
               System.err.println("   notrade1's ware ID is " + wareAliasTranslations.get("notrade1") + ", should be test:untradeable1");
            }
         }
         if (!wareAliasTranslations.containsKey("craft1")) {
            errorFound = true;
            System.err.println("   craft1 does not exist, should be mapped to test:crafted1");
         } else {
            if (!wareAliasTranslations.get("craft1").equals("test:crafted1")) {
               errorFound = true;
               System.err.println("   craft1's ware ID is " + wareAliasTranslations.get("craft1") + ", should be test:crafted1");
            }
         }

         System.err.println("testLoadWares() - checking alternate ware alias translation accuracy");
         if (!wareAliasTranslations.containsKey("#testName")) {
            errorFound = true;
            System.err.println("   #testName does not exist, should be mapped to test:material2");
         } else {
            if (!wareAliasTranslations.get("#testName").equals("test:material2")) {
               errorFound = true;
               System.err.println("   #testName's ware ID is " + wareAliasTranslations.get("#testName") + ", should be test:material2");
            }
         }
         if (!wareAliasTranslations.containsKey("testAlternateAlias")) {
            errorFound = true;
            System.err.println("   testAlternateAlias does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("testAlternateAlias").equals("test:material1")) {
               errorFound = true;
               System.err.println("   testAlternateAlias's ware ID is " + wareAliasTranslations.get("testAlternateAlias") + ", should be test:material1");
            }
         }
      }
      catch (Exception e) {
         System.err.println("testLoadWares() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      // ensure loaded wares matches the expected amount
      System.err.println("testLoadWares() - checking loaded wares volume");
      if (wares.size() != 12) {
         System.err.println("   total loaded wares: " + wares.size() + ", should be 12");
         return false;
      }
      System.err.println("testLoadWares() - checking loaded ware aliases volume");
      if (wareAliasTranslations.size() != 7) {
         System.err.println("   total loaded ware aliases: " + wareAliasTranslations.size() + ", should be 7");
         return false;
      }

      // report successfulness
      return !errorFound;
   }

   /**
    * Tests creating and trading wares whose current properties
    * (quantity, price, etc.) are directly tied to other wares' current properties.
    *
    * @return whether linked wares passed all test cases
    */
   private static boolean testLinkedWares() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // create test variables
      int   quantity;
      float price;

      Ware wood;
      Ware planks;
      Ware stick;
      Ware gold_ingot;
      Ware gold_block;
      Ware pumpkin;
      Ware torch;
      Ware jack_o_lantern;

      try {
         // prepare to grab private variables
         Field yield = Ware.class.getDeclaredField("yield");
         yield.setAccessible(true);

         // add raw materials to the market
         wood = new WareMaterial("minecraft:log", "wood", 0.5f, 10, (byte) 0);
         wares.put("minecraft:log", wood);
         wareAliasTranslations.put("wood", "minecraft:log");
         gold_ingot = new WareMaterial("minecraft:gold_ingot", "gold_ingot", 12.0f, 26, (byte) 3);
         wares.put("minecraft:gold_ingot", gold_ingot);
         wareAliasTranslations.put("gold_ingot", "minecraft:gold_ingot");
         pumpkin = new WareMaterial("minecraft:pumpkin", "pumpkin", 1.2f, 4, (byte) 2);
         wares.put("minecraft:pumpkin", pumpkin);
         wareAliasTranslations.put("pumpkin", "minecraft:pumpkin");
         torch = new WareMaterial("minecraft:torch", "torch", 4.0f, 3, (byte) 1);
         wares.put("minecraft:torch", torch);
         wareAliasTranslations.put("torch", "minecraft:torch");

         System.err.println("linked wares - negative yield");
         testBAOS.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "invalidYield", "planks", -1);

         if ((int) yield.get(planks) != 1) {
            System.err.println("   unexpected yield for planks: " + yield.get(planks) + ", should be 1");
            errorFound = true;
         }

         System.err.println("linked wares - zero yield");
         testBAOS.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "invalidYield", "planks", 0);

         if ((int) yield.get(planks) != 1) {
            System.err.println("   unexpected yield for planks: " + yield.get(planks) + ", should be 1");
            errorFound = true;
         }

         System.err.println("linked wares - null component amount array");
         testBAOS.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, null, "nullComponentsAmounts", "planks", 2);

         if (!Float.isNaN(planks.getBasePrice())) {
            System.err.println("   unexpected price: " + planks.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (planks.getQuantity() != 0) {
            System.err.println("   unexpected quantity: " + planks.getQuantity() + ", should be 0");
            errorFound = true;
         }

         System.err.println("linked wares - zero length component amount array");
         testBAOS.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, new int[]{}, "zeroLengthComponentsAmounts", "planks", 2);

         if (!Float.isNaN(planks.getBasePrice())) {
            System.err.println("   unexpected price: " + planks.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (planks.getQuantity() != 0) {
            System.err.println("   unexpected quantity: " + planks.getQuantity() + ", should be 0");
            errorFound = true;
         }

         System.err.println("linked wares - unequal lengths of component arrays");
         planks = new WareLinked(new String[]{"wood"}, new int[]{1, 1}, "unequalLengths", "planks", 1);

         if (!Float.isNaN(planks.getBasePrice())) {
            System.err.println("   unexpected price: " + planks.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (planks.getQuantity() != 0) {
            System.err.println("   unexpected quantity: " + planks.getQuantity() + ", should be 0");
            errorFound = true;
         }

         System.err.println("linked wares - valid creation");
         planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "minecraft:planks", "planks", 2);
         wares.put("minecraft:planks", planks);
         wareAliasTranslations.put("planks", "minecraft:planks");
         stick = new WareLinked(new String[]{"planks"}, new int[]{1}, "minecraft:stick", "planks", 2);
         gold_block = new WareLinked(new String[]{"gold_ingot"}, new int[]{9}, "minecraft:gold_block", "gold_block", 1);
         jack_o_lantern = new WareLinked(new String[]{"pumpkin", "torch"}, new int[]{1, 1}, "minecraft:lit_pumpkin", "jack_o_lantern", 1);

         if (planks.getBasePrice() != 0.25f) {
            System.err.println("   unexpected price for planks: " + planks.getBasePrice() + ", should be 0.25f");
            errorFound = true;
         }
         if (planks.getQuantity() != 20) {
            System.err.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 20");
            errorFound = true;
         }
         if (stick.getBasePrice() != 0.125f) {
            System.err.println("   unexpected price for stick: " + stick.getBasePrice() + ", should be 0.125f");
            errorFound = true;
         }
         if (stick.getQuantity() != 40) {
            System.err.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 40");
            errorFound = true;
         }
         if (gold_block.getBasePrice() != 108.0f) {
            System.err.println("   unexpected price for gold_block: " + gold_block.getBasePrice() + ", should be 108.0f");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 2) {
            System.err.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 2");
            errorFound = true;
         }
         if (jack_o_lantern.getBasePrice() != 5.2f) {
            System.err.println("   unexpected price for jack_o_lantern: " + jack_o_lantern.getBasePrice() + ", should be 5.2f");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 3) {
            System.err.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 3");
            errorFound = true;
         }

         System.err.println("linked wares - setQuantity()");
         stick.setQuantity(17);
         gold_block.setQuantity(10);
         jack_o_lantern.setQuantity(15);

         if (wood.getQuantity() != 4) {
            System.err.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 4");
            errorFound = true;
         }
         if (planks.getQuantity() != 8) {
            System.err.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 8");
            errorFound = true;
         }
         if (stick.getQuantity() != 17) {
            System.err.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 17");
            errorFound = true;
         }

         if (gold_ingot.getQuantity() != 90) {
            System.err.println("   unexpected quantity for gold_ingot: " + gold_ingot.getQuantity() + ", should be 90");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 10) {
            System.err.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 10");
            errorFound = true;
         }

         if (pumpkin.getQuantity() != 15) {
            System.err.println("   unexpected quantity for pumpkin: " + pumpkin.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (torch.getQuantity() != 15) {
            System.err.println("   unexpected quantity for torch: " + torch.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 15) {
            System.err.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 15");
            errorFound = true;
         }

         System.err.println("linked wares - addQuantity()");
         stick.setQuantity(1);
         stick.addQuantity(16);
         gold_ingot.setQuantity(10);
         gold_block.addQuantity(1);
         jack_o_lantern.setQuantity(10);
         jack_o_lantern.addQuantity(5);

         if (wood.getQuantity() != 4) {
            System.err.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 4");
            errorFound = true;
         }
         if (planks.getQuantity() != 8) {
            System.err.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 8");
            errorFound = true;
         }
         if (stick.getQuantity() != 17) {
            System.err.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 17");
            errorFound = true;
         }

         if (gold_ingot.getQuantity() != 19) {
            System.err.println("   unexpected quantity for gold_ingot: " + gold_ingot.getQuantity() + ", should be 19");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 2) {
            System.err.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 2");
            errorFound = true;
         }

         if (pumpkin.getQuantity() != 15) {
            System.err.println("   unexpected quantity for pumpkin: " + pumpkin.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (torch.getQuantity() != 15) {
            System.err.println("   unexpected quantity for torch: " + torch.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 15) {
            System.err.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 15");
            errorFound = true;
         }

         System.err.println("linked wares - subtractQuantity()");
         stick.setQuantity(100);
         stick.subtractQuantity(33);
         gold_ingot.setQuantity(18);
         gold_block.subtractQuantity(1);
         jack_o_lantern.setQuantity(10);
         jack_o_lantern.subtractQuantity(5);

         if (wood.getQuantity() != 16) {
            System.err.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 16");
            errorFound = true;
         }
         if (planks.getQuantity() != 33) {
            System.err.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 33");
            errorFound = true;
         }
         if (stick.getQuantity() != 67) {
            System.err.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 67");
            errorFound = true;
         }

         stick.subtractQuantity(33);
         if (wood.getQuantity() != 8) {
            System.err.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 8");
            errorFound = true;
         }
         if (planks.getQuantity() != 17) {
            System.err.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 17");
            errorFound = true;
         }
         if (stick.getQuantity() != 34) {
            System.err.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 34");
            errorFound = true;
         }

         if (gold_ingot.getQuantity() != 9) {
            System.err.println("   unexpected quantity for gold_ingot: " + gold_ingot.getQuantity() + ", should be 9");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 1) {
            System.err.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 1");
            errorFound = true;
         }

         if (pumpkin.getQuantity() != 5) {
            System.err.println("   unexpected quantity for pumpkin: " + pumpkin.getQuantity() + ", should be 5");
            errorFound = true;
         }
         if (torch.getQuantity() != 5) {
            System.err.println("   unexpected quantity for torch: " + torch.getQuantity() + ", should be 5");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 5) {
            System.err.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 5");
            errorFound = true;
         }

         System.err.println("linked wares - basic getters and setters");
         if (!planks.getWareID().equals("minecraft:planks")) {
            System.err.println("   unexpected ware ID for planks: " + planks.getWareID() + ", should be minecraft:planks");
            errorFound = true;
         }
         stick.setAlias("testStick");
         if (!stick.getAlias().equals("testStick")) {
            System.err.println("   unexpected alias for stick: " + stick.getAlias() + ", should be testStick");
            errorFound = true;
         }
         gold_block.setLevel((byte) 5);
         if (gold_block.getLevel() != 5) {
            System.err.println("   unexpected level for gold_block: " + gold_block.getLevel() + ", should be 5");
            errorFound = true;
         }
         if (!jack_o_lantern.hasComponents()) {
            System.err.println("   unexpected value for jack_o_lantern.hasComponents(): " + jack_o_lantern.hasComponents() + ", should be true");
            errorFound = true;
         }

         System.err.println("linked wares - pricing");
         gold_ingot.setQuantity(27);
         wood.setQuantity(3);
         torch.setQuantity(18);
         pumpkin.setQuantity(16);
         wares.put("minecraft:gold_block", gold_block);
         wares.put("minecraft:lit_pumpkin", jack_o_lantern);

         // for accessing getCurrentPrice()
         WareLinked gold_block_linked     = (WareLinked) gold_block;
         WareLinked planks_linked         = (WareLinked) planks;
         WareLinked jack_o_lantern_linked = (WareLinked) jack_o_lantern;

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:gold_ingot", 9, false);
         if (gold_block_linked.getCurrentPrice(1, false) != price) {
            System.err.println("   unexpected price for selling 9 ingots: " + gold_block_linked.getCurrentPrice(1, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:gold_ingot", 9, true);
         if (gold_block_linked.getCurrentPrice(1, true) != price) {
            System.err.println("   unexpected price for buying 9 ingots: " + gold_block_linked.getCurrentPrice(1, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:gold_ingot", 27, false);
         if (gold_block_linked.getCurrentPrice(3, false) != price) {
            System.err.println("   unexpected price for selling 27 ingots: " + gold_block_linked.getCurrentPrice(3, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:gold_ingot", 27, true);
         if (gold_block_linked.getCurrentPrice(3, true) != price) {
            System.err.println("   unexpected price for buying 27 ingots: " + gold_block_linked.getCurrentPrice(3, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:log", 1, false);
         if (planks_linked.getCurrentPrice(2, false) != price) {
            System.err.println("   unexpected price for selling 1 wood: " + planks_linked.getCurrentPrice(2, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:log", 1, true);
         if (planks_linked.getCurrentPrice(2, true) != price) {
            System.err.println("   unexpected price for buying 1 wood: " + planks_linked.getCurrentPrice(2, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:log", 3, false);
         if (planks_linked.getCurrentPrice(6, false) != price) {
            System.err.println("   unexpected price for selling 3 wood: " + planks_linked.getCurrentPrice(6, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:log", 3, true);
         if (planks_linked.getCurrentPrice(6, true) != price) {
            System.err.println("   unexpected price for buying 3 wood: " + planks_linked.getCurrentPrice(6, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:log", 3, true);
         if (planks_linked.getCurrentPrice(6, true) != price) {
            System.err.println("   unexpected price for buying 3 wood: " + planks_linked.getCurrentPrice(6, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:torch", 16, false) + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:pumpkin", 16, false);
         if (jack_o_lantern_linked.getCurrentPrice(18, false) != price) {
            System.err.println("   unexpected price for selling jack o' lanterns: " + jack_o_lantern_linked.getCurrentPrice(18, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:torch", 16, true) + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), "minecraft:pumpkin", 16, true);
         if (jack_o_lantern_linked.getCurrentPrice(18, true) != price) {
            System.err.println("   unexpected price for buying jack o' lanterns: " + jack_o_lantern_linked.getCurrentPrice(18, true) + ", should be " + price);
            errorFound = true;
         }

         System.err.println("linked wares - saving and loading");
         Ware new_stick          = Ware.fromJSON(stick.toJSON());
         Ware new_gold_block     = Ware.fromJSON(gold_block.toJSON());
         Ware new_jack_o_lantern = Ware.fromJSON(jack_o_lantern.toJSON());

         // components must be reloaded before using price and quantity
         new_stick.reloadComponents();
         new_gold_block.reloadComponents();
         new_jack_o_lantern.reloadComponents();

         if (!new_stick.getAlias().equals(stick.getAlias())) {
            System.err.println("   unexpected alias for new_stick: " + new_stick.getAlias() + ", should be " + stick.getAlias());
            errorFound = true;
         }
         if (new_stick.getBasePrice() != stick.getBasePrice()) {
            System.err.println("   unexpected price for new_stick: " + new_stick.getBasePrice() + ", should be " + stick.getBasePrice());
            errorFound = true;
         }
         if (new_stick.getLevel() != (byte) 0) {
            System.err.println("   unexpected level for new_stick: " + new_stick.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (new_stick.getQuantity() != stick.getQuantity()) {
            System.err.println("   unexpected quantity for new_stick: " + new_stick.getQuantity() + ", should be " + stick.getQuantity());
            errorFound = true;
         }

         if (!new_gold_block.getAlias().equals(gold_block.getAlias())) {
            System.err.println("   unexpected alias for new_gold_block: " + new_gold_block.getAlias() + ", should be " + gold_block.getAlias());
            errorFound = true;
         }
         if (new_gold_block.getBasePrice() != gold_block.getBasePrice()) {
            System.err.println("   unexpected price for new_gold_block: " + new_gold_block.getBasePrice() + ", should be " + gold_block.getBasePrice());
            errorFound = true;
         }
         if (new_gold_block.getLevel() != (byte) 3) {
            System.err.println("   unexpected level for new_gold_block: " + new_gold_block.getLevel() + ", should be 3");
            errorFound = true;
         }
         if (new_gold_block.getQuantity() != gold_block.getQuantity()) {
            System.err.println("   unexpected quantity for new_gold_block: " + new_gold_block.getQuantity() + ", should be " + gold_block.getQuantity());
            errorFound = true;
         }

         if (!new_jack_o_lantern.getAlias().equals(jack_o_lantern.getAlias())) {
            System.err.println("   unexpected alias for new_jack_o_lantern: " + new_jack_o_lantern.getAlias() + ", should be " + jack_o_lantern.getAlias());
            errorFound = true;
         }
         if (new_jack_o_lantern.getBasePrice() != jack_o_lantern.getBasePrice()) {
            System.err.println("   unexpected price for new_jack_o_lantern: " + new_jack_o_lantern.getBasePrice() + ", should be " + jack_o_lantern.getBasePrice());
            errorFound = true;
         }
         if (new_jack_o_lantern.getLevel() != (byte) 2) {
            System.err.println("   unexpected level for new_jack_o_lantern: " + new_jack_o_lantern.getLevel() + ", should be 2");
            errorFound = true;
         }
         if (new_jack_o_lantern.getQuantity() != jack_o_lantern.getQuantity()) {
            System.err.println("   unexpected quantity for new_jack_o_lantern: " + new_jack_o_lantern.getQuantity() + ", should be " + jack_o_lantern.getQuantity());
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("linked wares - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      // report successfulness
      return !errorFound;
   }

   /**
    * Tests the ware's ability to check itself for errors.
    *
    * @return whether Ware.validate() passed all test cases
    */
   private static boolean testWareValidate() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // create test variables
      String   validateFeedback;
      Ware wareMaterial = new WareMaterial("wareMaterial", null, 10.0f, 50, (byte) 0);
      Ware wareCrafted = new WareCrafted(new String[]{"test:material1"}, null, "wareCrafted", 80, 1, (byte) 1);
      Ware wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 123456789, 1, (byte) 4);
      Ware wareUntradeableRaw = new WareUntradeable("wareUntradeableRaw", null, 20.0f);
      Ware wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

      try {
         // prepare to forcible set ware fields
         Field wareID        = Ware.class.getDeclaredField("wareID");
         Field alias         = Ware.class.getDeclaredField("alias");
         Field price         = Ware.class.getDeclaredField("priceBase");
         Field quantity      = Ware.class.getDeclaredField("quantity");
         Field level         = Ware.class.getDeclaredField("level");
         Field yield         = Ware.class.getDeclaredField("yield");
         Field componentsIDs = Ware.class.getDeclaredField("componentsIDs");
         wareID.setAccessible(true);
         alias.setAccessible(true);
         price.setAccessible(true);
         quantity.setAccessible(true);
         level.setAccessible(true);
         yield.setAccessible(true);
         componentsIDs.setAccessible(true);

         System.err.println("Ware.validate() - empty ware ID, empty alias, valid price, level, and quantity");
         wareID.set(wareMaterial, "");
         wareID.set(wareCrafted, "");
         wareID.set(wareProcessed, "");
         wareID.set(wareUntradeableRaw, "");
         wareID.set(wareUntradeableComponents, "");
         alias.set(wareMaterial, "");
         alias.set(wareCrafted, "");
         alias.set(wareProcessed, "");
         alias.set(wareUntradeableRaw, "");
         alias.set(wareUntradeableComponents, "");

         validateFeedback = wareMaterial.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareMaterial: " + validateFeedback);
            errorFound = true;
         }
         if (wareMaterial.getAlias() != null) {
            System.err.println("   unexpected alias for wareMaterial: " + wareMaterial.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareMaterial.getBasePrice() != 10.0f) {
            System.err.println("   unexpected price base for wareMaterial: " + wareMaterial.getBasePrice() + ", should be 10.0");
            errorFound = true;
         }
         if (wareMaterial.getQuantity() != 50) {
            System.err.println("   unexpected quantity for wareMaterial: " + wareMaterial.getQuantity() + ", should be 50");
            errorFound = true;
         }
         if (wareMaterial.getLevel() != 0) {
            System.err.println("   unexpected level for wareMaterial: " + wareMaterial.getLevel() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (wareCrafted.getAlias() != null) {
            System.err.println("   unexpected alias for wareCrafted: " + wareCrafted.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareCrafted.getBasePrice() != 1.2f) {
            System.err.println("   unexpected price base for wareCrafted: " + wareCrafted.getBasePrice() + ", should be 1.2");
            errorFound = true;
         }
         if (wareCrafted.getQuantity() != 80) {
            System.err.println("   unexpected quantity for wareCrafted: " + wareCrafted.getQuantity() + ", should be 80");
            errorFound = true;
         }
         if (wareCrafted.getLevel() != 1) {
            System.err.println("   unexpected level for wareCrafted: " + wareCrafted.getLevel() + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (wareProcessed.getAlias() != null) {
            System.err.println("   unexpected alias for wareProcessed: " + wareProcessed.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareProcessed.getBasePrice() != 2.2f) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be 2.2");
            errorFound = true;
         }
         if (wareProcessed.getQuantity() != 123456789) {
            System.err.println("   unexpected quantity for wareProcessed: " + wareProcessed.getQuantity() + ", should be 123456789");
            errorFound = true;
         }
         if (wareProcessed.getLevel() != 4) {
            System.err.println("   unexpected level for wareProcessed: " + wareProcessed.getLevel() + ", should be 4");
            errorFound = true;
         }

         validateFeedback = wareUntradeableRaw.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareUntradeableRaw: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableRaw.getAlias() != null) {
            System.err.println("   unexpected alias for wareUntradeableRaw: " + wareUntradeableRaw.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareUntradeableRaw.getBasePrice() != 20.0f) {
            System.err.println("   unexpected price base for wareUntradeableRaw: " + wareUntradeableRaw.getBasePrice() + ", should be 20.0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getQuantity() != Integer.MAX_VALUE) {
            System.err.println("   unexpected quantity for wareUntradeableRaw: " + wareUntradeableRaw.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getLevel() != 0) {
            System.err.println("   unexpected level for wareUntradeableRaw: " + wareUntradeableRaw.getLevel() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableComponents.getAlias() != null) {
            System.err.println("   unexpected alias for wareUntradeableComponents: " + wareUntradeableComponents.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareUntradeableComponents.getBasePrice() != 5.0f) {
            System.err.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be 5.0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getQuantity() != Integer.MAX_VALUE) {
            System.err.println("   unexpected quantity for wareUntradeableComponents: " + wareUntradeableComponents.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getLevel() != 0) {
            System.err.println("   unexpected level for wareUntradeableComponents: " + wareUntradeableComponents.getLevel() + ", should be 0");
            errorFound = true;
         }

         // reset for other tests
         wareMaterial = new WareMaterial("wareMaterial", null, 10.0f, 64, (byte) 0);
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, null, "wareCrafted", 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableRaw = new WareUntradeable("wareUntradeableRaw", null, 20.0f);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         System.err.println("Ware.validate() - null ware ID, alias with colon, unset price, unset quantity, level too high");
         wareID.set(wareMaterial, null);
         wareID.set(wareCrafted, null);
         wareID.set(wareProcessed, null);
         wareID.set(wareUntradeableRaw, null);
         wareID.set(wareUntradeableComponents, null);
         alias.set(wareMaterial, "test:aliasMaterial");
         alias.set(wareCrafted, "test:aliasCrafted");
         alias.set(wareProcessed, "test:aliasProcessed");
         alias.set(wareUntradeableRaw, "test:aliasUntradeableRaw");
         alias.set(wareUntradeableComponents, "test:aliasUntradeableComponents");
         price.setFloat(wareMaterial, Float.NaN);
         price.setFloat(wareCrafted, Float.NaN);
         price.setFloat(wareProcessed, Float.NaN);
         price.setFloat(wareUntradeableRaw, Float.NaN);
         price.setFloat(wareUntradeableComponents, Float.NaN);
         quantity.setInt(wareMaterial, -1);
         quantity.setInt(wareCrafted, -1);
         quantity.setInt(wareProcessed, -1);
         quantity.setInt(wareUntradeableRaw, -1);
         quantity.setInt(wareUntradeableComponents, -1);
         level.setByte(wareMaterial, (byte) 6);
         level.setByte(wareCrafted, (byte) 7);
         level.setByte(wareProcessed, (byte) 8);
         level.setByte(wareUntradeableRaw, (byte) 9);
         level.setByte(wareUntradeableComponents, (byte) 10);

         validateFeedback = wareMaterial.validate();
         if (!validateFeedback.equals("missing ware ID, unset price")) {
            System.err.println("   unexpected validate feedback for wareMaterial: " + validateFeedback);
            errorFound = true;
         }
         if (!wareMaterial.getAlias().equals("aliasMaterial")) {
            System.err.println("   unexpected alias for wareMaterial: " + wareMaterial.getAlias() + ", should be aliasMaterial");
            errorFound = true;
         }
         if (!Float.isNaN(wareMaterial.getBasePrice())) {
            System.err.println("   unexpected price base for wareMaterial: " + wareMaterial.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareMaterial.getQuantity() != -1) {
            System.err.println("   unexpected quantity for wareMaterial: " + wareMaterial.getQuantity() + ", should be -1");
            errorFound = true;
         }
         if (wareMaterial.getLevel() != 5) {
            System.err.println("   unexpected level for wareMaterial: " + wareMaterial.getLevel() + ", should be 5");
            errorFound = true;
         }

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (!wareCrafted.getAlias().equals("aliasCrafted")) {
            System.err.println("   unexpected alias for wareCrafted: " + wareCrafted.getAlias() + ", should be aliasCrafted");
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            System.err.println("   unexpected price base for wareCrafted: " + wareCrafted.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareCrafted.getQuantity() != -1) {
            System.err.println("   unexpected quantity for wareCrafted: " + wareCrafted.getQuantity() + ", should be -1");
            errorFound = true;
         }
         if (wareCrafted.getLevel() != 5) {
            System.err.println("   unexpected level for wareCrafted: " + wareCrafted.getLevel() + ", should be 5");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (!wareProcessed.getAlias().equals("aliasProcessed")) {
            System.err.println("   unexpected alias for wareProcessed: " + wareProcessed.getAlias() + ", should be aliasProcessed");
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareProcessed.getQuantity() != -1) {
            System.err.println("   unexpected quantity for wareProcessed: " + wareProcessed.getQuantity() + ", should be -1");
            errorFound = true;
         }
         if (wareProcessed.getLevel() != 5) {
            System.err.println("   unexpected level for wareProcessed: " + wareProcessed.getLevel() + ", should be 5");
            errorFound = true;
         }

         validateFeedback = wareUntradeableRaw.validate();
         if (!validateFeedback.equals("missing ware ID, unset price")) {
            System.err.println("   unexpected validate feedback for wareUntradeableRaw: " + validateFeedback);
            errorFound = true;
         }
         if (!wareUntradeableRaw.getAlias().equals("aliasUntradeableRaw")) {
            System.err.println("   unexpected alias for wareUntradeableRaw: " + wareUntradeableRaw.getAlias() + ", should be aliasUntradeableRaw");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableRaw.getBasePrice())) {
            System.err.println("   unexpected price base for wareUntradeableRaw: " + wareUntradeableRaw.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableRaw.getQuantity() != Integer.MAX_VALUE) {
            System.err.println("   unexpected quantity for wareUntradeableRaw: " + wareUntradeableRaw.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getLevel() != 0) {
            System.err.println("   unexpected level for wareUntradeableRaw: " + wareUntradeableRaw.getLevel() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            System.err.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (!wareUntradeableComponents.getAlias().equals("aliasUntradeableComponents")) {
            System.err.println("   unexpected alias for wareUntradeableComponents: " + wareUntradeableComponents.getAlias() + ", should be aliasUntradeableComponents");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            System.err.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableComponents.getQuantity() != Integer.MAX_VALUE) {
            System.err.println("   unexpected quantity for wareUntradeableComponents: " + wareUntradeableComponents.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getLevel() != 0) {
            System.err.println("   unexpected level for wareUntradeableComponents: " + wareUntradeableComponents.getLevel() + ", should be 0");
            errorFound = true;
         }

         // reset for other tests
         wareMaterial = new WareMaterial("wareMaterial", null, 10.0f, 64, (byte) 0);
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, "wareCrafted", null, 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableRaw = new WareUntradeable("wareUntradeableRaw", null, 20.0f);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         System.err.println("Ware.validate() - valid ware ID, null alias, NaN price, level too low, and valid quantity");
         price.setFloat(wareMaterial, Float.NaN);
         price.setFloat(wareCrafted, Float.NaN);
         price.setFloat(wareProcessed, Float.NaN);
         price.setFloat(wareUntradeableRaw, Float.NaN);
         price.setFloat(wareUntradeableComponents, Float.NaN);
         quantity.setInt(wareMaterial, 50);
         quantity.setInt(wareCrafted, 80);
         quantity.setInt(wareProcessed, 123456789);
         quantity.setInt(wareUntradeableRaw, 60);
         quantity.setInt(wareUntradeableComponents, 115);
         level.setByte(wareMaterial, (byte) -1);
         level.setByte(wareCrafted, (byte) -2);
         level.setByte(wareProcessed, (byte) -3);
         level.setByte(wareUntradeableRaw, (byte) -4);
         level.setByte(wareUntradeableComponents, (byte) -5);

         validateFeedback = wareMaterial.validate();
         if (!validateFeedback.equals("unset price")) {
            System.err.println("   unexpected validate feedback for wareMaterial: " + validateFeedback);
            errorFound = true;
         }
         if (wareMaterial.getAlias() != null) {
            System.err.println("   unexpected alias for wareMaterial: " + wareMaterial.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareMaterial.getBasePrice())) {
            System.err.println("   unexpected price base for wareMaterial: " + wareMaterial.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareMaterial.getLevel() != 0) {
            System.err.println("   unexpected level for wareMaterial: " + wareMaterial.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareMaterial.getQuantity() != 50) {
            System.err.println("   unexpected quantity for wareMaterial: " + wareMaterial.getQuantity() + ", should be 50");
            errorFound = true;
         }

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("")) {
            System.err.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (wareCrafted.getAlias() != null) {
            System.err.println("   unexpected alias for wareCrafted: " + wareCrafted.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            System.err.println("   unexpected price base for wareCrafted: " + wareCrafted.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareCrafted.getLevel() != 0) {
            System.err.println("   unexpected level for wareCrafted: " + wareCrafted.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareCrafted.getQuantity() != 80) {
            System.err.println("   unexpected quantity for wareCrafted: " + wareCrafted.getQuantity() + ", should be 80");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("")) {
            System.err.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (wareProcessed.getAlias() != null) {
            System.err.println("   unexpected alias for wareProcessed: " + wareProcessed.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareProcessed.getLevel() != 0) {
            System.err.println("   unexpected level for wareProcessed: " + wareProcessed.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareProcessed.getQuantity() != 123456789) {
            System.err.println("   unexpected quantity for wareProcessed: " + wareProcessed.getQuantity() + ", should be 16");
            errorFound = true;
         }

         validateFeedback = wareUntradeableRaw.validate();
         if (!validateFeedback.equals("unset price")) {
            System.err.println("   unexpected validate feedback for wareUntradeableRaw: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableRaw.getAlias() != null) {
            System.err.println("   unexpected alias for wareUntradeableRaw: " + wareUntradeableRaw.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableRaw.getBasePrice())) {
            System.err.println("   unexpected price base for wareUntradeableRaw: " + wareUntradeableRaw.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableRaw.getLevel() != 0) {
            System.err.println("   unexpected level for wareUntradeableRaw: " + wareUntradeableRaw.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getQuantity() != Integer.MAX_VALUE) {
            System.err.println("   unexpected quantity for wareUntradeableRaw: " + wareUntradeableRaw.getQuantity() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("")) {
            System.err.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableComponents.getAlias() != null) {
            System.err.println("   unexpected alias for wareUntradeableComponents: " + wareUntradeableComponents.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            System.err.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableComponents.getLevel() != 0) {
            System.err.println("   unexpected level for wareUntradeableComponents: " + wareUntradeableComponents.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getQuantity() != Integer.MAX_VALUE) {
            System.err.println("   unexpected quantity for wareUntradeableComponents: " + wareUntradeableComponents.getQuantity() + ", should be 0");
            errorFound = true;
         }

         // reset for other tests
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, "wareCrafted", null, 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         System.err.println("Ware.validate() - null components' IDs, zero yield");
         componentsIDs.set(wareCrafted, null);
         componentsIDs.set(wareProcessed, null);
         componentsIDs.set(wareUntradeableComponents, null);
         price.setFloat(wareCrafted, 1.2f);
         price.setFloat(wareProcessed, 2.2f);
         price.setFloat(wareUntradeableComponents, 5.0f);
         yield.setByte(wareCrafted, (byte) 0);
         yield.setByte(wareProcessed, (byte) 0);
         // don't set untradeable ware's yield to 0 just yet

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("missing components' IDs")) {
            System.err.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareCrafted) != 1) {
            System.err.println("   unexpected yield for wareCrafted: " + yield.get(wareCrafted) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("missing components' IDs")) {
            System.err.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareProcessed) != 1) {
            System.err.println("   unexpected yield for wareProcessed: " + yield.get(wareProcessed) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("missing components' IDs")) {
            System.err.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            System.err.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         // now test untradeable ware with zero yield
         componentsIDs.set(wareUntradeableComponents, new String[]{"test:material1", "test:material1", "minecraft:material4"});
         yield.setByte(wareUntradeableComponents, (byte) 0);
         validateFeedback = wareUntradeableComponents.validate();
         if ((int) yield.get(wareUntradeableComponents) != 1) {
            System.err.println("   unexpected yield for wareUntradeableComponents: " + yield.get(wareUntradeableComponents) + ", should be 1");
            errorFound = true;
         }

         // reset for other tests
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, "wareCrafted", null, 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         System.err.println("Ware.validate() - empty individual components' IDs, negative yield");
         componentsIDs.set(wareCrafted, new String[]{""});
         componentsIDs.set(wareProcessed, new String[]{"test:material1", null});
         componentsIDs.set(wareUntradeableComponents, new String[]{"", "test:material1", null});
         price.setFloat(wareCrafted, 1.2f);
         price.setFloat(wareProcessed, 2.2f);
         price.setFloat(wareUntradeableComponents, 5.0f);
         yield.setByte(wareCrafted, (byte) -1);
         yield.setByte(wareProcessed, (byte) -2);
         yield.setByte(wareUntradeableComponents, (byte) -3);

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("blank component ID")) {
            System.err.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareCrafted) != 1) {
            System.err.println("   unexpected yield for wareCrafted: " + yield.get(wareCrafted) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("blank component ID")) {
            System.err.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            System.err.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareProcessed) != 1) {
            System.err.println("   unexpected yield for wareProcessed: " + yield.get(wareProcessed) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("blank component ID")) {
            System.err.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            System.err.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareUntradeableComponents) != 1) {
            System.err.println("   unexpected yield for wareUntradeableComponents: " + yield.get(wareUntradeableComponents) + ", should be 1");
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("Ware.validate() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      // report successfulness
      return !errorFound;
   }

   /**
    * Tests Account class functions: constructors and addMoney().
    *
    * @return whether Account's constructors and addMoney() passed all test cases
    */
   private static boolean testAccountCreation() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // delete any currently loaded accounts
      accounts.clear();

      // make accounts start with some money
      Config.accountStartingMoney = 10.0f;

      Account testAccount; // holds account currently being checked
      try {
         System.err.println("accountCreation() - creation of account using null ID");
         Account.makeAccount(null, null);
         if (accounts.size() != 0) {
            System.err.println("   account was created using a null id");
            errorFound = true;
            accounts.clear(); // delete account so other tests run properly
         }

         System.err.println("accountCreation() - creation of account using empty ID");
         Account.makeAccount("", null);
         if (accounts.size() != 0) {
            System.err.println("   account was created using an empty id");
            errorFound = true;
            accounts.clear(); // delete account to avoid interfering with other tests
         }

         System.err.println("accountCreation() - creation of account using numerical id");
         Account.makeAccount("12345", null);
         if (accounts.size() != 0) {
            System.err.println("   account was created using a numerical id");
            errorFound = true;
            accounts.clear(); // delete account to avoid interfering with other tests
         }

         System.err.println("accountCreation() - creation of account using valid id and no given player");
         Account.makeAccount("testAccount1", null);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 10.0f, null);

         // grant account access to player for next tests
         testAccount.grantAccess(null, PLAYER_ID, null);

         System.err.println("accountCreation() - adding money to account");
         testAccount.addMoney(2.0f);
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - creation of account using existing ID");
         Account.makeAccount("testAccount1", null);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - permissions of account with valid ID");
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountCreation() - creation of account with specified starting amount");
         Account.makeAccount("testAccount2", PLAYER_ID, 14.0f);
         testAccount = accounts.get("testAccount2");
         errorFound = errorFound || testAccountFields(testAccount, 14.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - permissions of account with specified starting amount");
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountCreation() - creation of account with specified starting amount of NaN");
         Account accountNaNMoney = Account.makeAccount("", PLAYER_ID, Float.NaN);
         errorFound = errorFound || testAccountFields(accountNaNMoney, 0.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - creation of account for arbitrary player ID");
         Account.makeAccount("testAccount3", InterfaceTerminal.getPlayerIDStatic("possibleID"));
         testAccount = accounts.get("testAccount3");
         errorFound = errorFound || testAccountFields(testAccount, 10.0f, "possibleID");

         System.err.println("accountCreation() - permissions of account with arbitrary player ID");
         if (testAccount.hasAccess(PLAYER_ID)) {
            System.err.println("   " + InterfaceTerminal.playername + " has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountCreation() - creation of inaccessible account");
         Account.makeAccount("testAccount4", null, 6.0f);
         testAccount = accounts.get("testAccount4");
         if (testAccount.hasAccess(PLAYER_ID)) {
            System.err.println("   " + InterfaceTerminal.playername + " has access when they shouldn't");
            errorFound = true;
         }
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountCreation() - creation of a player's personal account");
         Account.makeAccount(InterfaceTerminal.playername, PLAYER_ID);
         testAccount = accounts.get(InterfaceTerminal.playername);
         errorFound = errorFound || testAccountFields(testAccount, 10.0f, InterfaceTerminal.playername);
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountCreation() - adding money to account created with specified starting amount");
         testAccount = accounts.get("testAccount2");
         testAccount.addMoney(3.0f);
         errorFound = errorFound || testAccountFields(testAccount, 17.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - adding NaN amount of money to account");
         testAccount = accounts.get("testAccount1");
         testAccount.addMoney(Float.NaN);
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - adding no money to account");
         testAccount.addMoney(0.0f);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - subtracting money from account");
         testAccount.addMoney(-10.0f);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 2.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - retrieving number of accounts using null input");
         int accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(null);
         if (accountsCreated != 2147483647) {
            System.err.println("   null input gave unexpected value: " + accountsCreated + ", should be 2147483647");
            errorFound = true;
         }

         System.err.println("accountCreation() - retrieving number of accounts created by nonexistent user");
         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID2"));
         if (accountsCreated != 0) {
            System.err.println("   unexpected value: " + accountsCreated + ", should be 0");
            errorFound = true;
         }

         System.err.println("accountCreation() - retrieving number of accounts created by test users");
         accountsCreated = -1;
         Account.makeAccount("possibleAccount1", InterfaceTerminal.getPlayerIDStatic("possibleID2"), 10.0f);
         Account.makeAccount("", InterfaceTerminal.getPlayerIDStatic("possibleID2"), 10.0f);
         Account.makeAccount(null, InterfaceTerminal.getPlayerIDStatic("possibleID2"), 10.0f);
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID2"));
         if (accountsCreated != 3) {
            System.err.println("   unexpected value for number of accounts created by possibleID2: " + accountsCreated + ", should be 3");
            errorFound = true;
         }

         accountsCreated = -1;
         Account.makeAccount("possibleAccount1", InterfaceTerminal.getPlayerIDStatic("possibleID3"));
         Account.makeAccount("possibleAccount2", InterfaceTerminal.getPlayerIDStatic("possibleID3"));
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID3"));
         if (accountsCreated != 1) {
            System.err.println("   unexpected value for number of accounts created by possibleID3: " + accountsCreated + ", should be 1");
            errorFound = true;
         }

         System.err.println("accountCreation() - non-personal account creation when exceeding per-player limit");
         Config.accountMaxCreatedByIndividual = 0;
         Account.makeAccount("shouldNotExist", InterfaceTerminal.getPlayerIDStatic("possibleID4"), 10.0f);
         if (accounts.containsKey("shouldNotExist")) {
            System.err.println("   account was created despite exceeding limit");
            errorFound = true;
         }

         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID4"));
         if (accountsCreated != 0) {
            System.err.println("   unexpected value for number of accounts created by possible user: " + accountsCreated + ", should be 0");
            errorFound = true;
         }

         System.err.println("accountCreation() - personal account creation when exceeding per-player limit");
         Config.accountMaxCreatedByIndividual = 0;
         Account.makeAccount("possibleID4", InterfaceTerminal.getPlayerIDStatic("possibleID4"), 10.0f);
         if (!accounts.containsKey("possibleID4")) {
            System.err.println("   account was not created when it should have been");
            errorFound = true;
         }

         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID4"));
         if (accountsCreated != 0) {
            System.err.println("   unexpected value for number of accounts created by possible user: " + accountsCreated + ", should be 0");
            errorFound = true;
         }

         System.err.println("accountCreation() - account creation after removing limit");
         Config.accountMaxCreatedByIndividual = -1;
         Account.makeAccount("shouldExist", InterfaceTerminal.getPlayerIDStatic("possibleID4"), 10.0f);
         if (!accounts.containsKey("shouldExist")) {
            System.err.println("   account was not created when it should have been");
            errorFound = true;
         }

         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID4"));
         if (accountsCreated != 1) {
            System.err.println("   unexpected value for number of accounts created by possible user: " + accountsCreated + ", should be 1");
            errorFound = true;
         }

         System.err.println("accountCreation() - non-personal account creation using nonexistent player ID");
         Account.makeAccount("possibleID5", PLAYER_ID, Config.accountStartingMoney + 10.0f);
         testAccount = accounts.get("possibleID5");
         errorFound = errorFound || testAccountFields(testAccount, Config.accountStartingMoney + 10.0f, InterfaceTerminal.playername);

         System.err.println("accountCreation() - personal account creation using existing non-personal account ID");
         accounts.get(InterfaceTerminal.playername).setMoney(10.0f); // set player account funds to a known value
         Account.makeAccount("possibleID5", InterfaceTerminal.getPlayerIDStatic("possibleID5"));
         testAccount = accounts.get("possibleID5");
         errorFound = errorFound || testAccountFields(testAccount, Config.accountStartingMoney, "possibleID5");
         if (testAccount.hasAccess(PLAYER_ID)) {
            System.err.println("   player ID has access when they shouldn't");
            errorFound = true;
         }
         testAccount = accounts.get(InterfaceTerminal.playername);
         if (testAccount.getMoney() != Config.accountStartingMoney + 20.0f) {
            System.err.println("   player's account has $" + testAccount.getMoney() + ", should be " + (Config.accountStartingMoney + 20.0f));
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("accountCreation() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      // ensure loaded wares matches the expected amount
      if (accounts.size() != 10) {
         System.err.println("only 10 test accounts should have been created, but total accounts is " + accounts.size());
         return false;
      }

      // report successfulness
      return !errorFound;
   }

   /**
    * Tests accountAccess().
    *
    * @return whether accountAccess() passed all test cases
    */
   private static boolean testAccountAccess() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         System.err.println("accountAccess() - adding permissions for null account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), null);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions for empty account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"), "");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"))) {
            System.err.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions for invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"), "some arbitrary account");

         // check console output
         if (!testBAOS.toString().startsWith("yetAnotherPossibleID may now access some arbitrary account")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"))) {
            System.err.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions for null player ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, null, "testAccount1");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions for empty player ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic(""), "testAccount1");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions for player ID already given permissions");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!testBAOS.toString().startsWith("possibleID already may access testAccount1")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions so multiple players share an account");
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID1"), "testAccount1");
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID2"), "testAccount1");

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID1"))) {
            System.err.println("   first possible player ID doesn't have access when they should");
            errorFound = true;
         }
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID2"))) {
            System.err.println("   second possible player ID doesn't have access when they should");
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions without permission to do so");
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "permissionlessPlayer";
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(InterfaceTerminal.getPlayerIDStatic("permissionlessPlayer"), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!testBAOS.toString().startsWith("You don't have permission to access testAccount1")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         InterfaceTerminal.playername = playernameOrig;

         System.err.println("accountAccess() - adding permissions with null username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount4.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount4");

         // check console output
         if (!testBAOS.toString().startsWith("(for possibleID) You may now access testAccount4")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions with empty username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.grantAccess(InterfaceTerminal.getPlayerIDStatic(""), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - adding permissions with different username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount3.grantAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"), PLAYER_ID, "testAccount3");

         // check permissions
         if (!testAccount3.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }
         if (!testAccount3.hasAccess(PLAYER_ID)) {
            System.err.println("   player doesn't have access when they should");
            errorFound = true;
         }


         System.err.println("accountAccess() - removing permissions for null account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), null);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   possible player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions for empty account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"), "");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"))) {
            System.err.println("   possible player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions for invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"), "some arbitrary account");

         // check console output
         if (!testBAOS.toString().startsWith("yetAnotherPossibleID may no longer access some arbitrary account")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"))) {
            System.err.println("   possible player ID has access when they shouldn't");
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions for null player ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic(null), "testAccount1");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions for empty player ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic(""), "testAccount1");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions for player ID without permissions");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!testBAOS.toString().startsWith("possibleID already cannot access testAccount1.")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions without permission to do so");
         playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "permissionlessPlayer";
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(InterfaceTerminal.getPlayerIDStatic("permissionlessPlayer"), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");
         InterfaceTerminal.playername = playernameOrig;

         // check console output
         if (!testBAOS.toString().startsWith("You don't have permission to access testAccount1")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions with null username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount4.revokeAccess(InterfaceTerminal.getPlayerIDStatic(null), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount4");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions with empty username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(InterfaceTerminal.getPlayerIDStatic(""), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountAccess() - removing permissions with different username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount3.revokeAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"), PLAYER_ID, "testAccount3");

         // check permissions
         if (!testAccount3.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            System.err.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }
         if (testAccount3.hasAccess(PLAYER_ID)) {
            System.err.println("   player has access when they shouldn't");
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("accountAccess() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account's check().
    *
    * @return whether Account's check() passed all test cases
    */
   private static boolean testAccountCheck() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // set up test accounts
      Account testAccountAccessible   = Account.makeAccount("", PLAYER_ID, 10.0f);
      Account testAccountInaccessible = Account.makeAccount("", null, 20.0f);
      try {
         System.err.println("accountCheck() - null account ID");
         testBAOS.reset(); // clear buffer holding console output
         playerAccount.check(PLAYER_ID, null);
         if (!testBAOS.toString().equals("")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountCheck() - empty account ID");
         testBAOS.reset(); // clear buffer holding console output
         playerAccount.check(PLAYER_ID, "");
         if (!testBAOS.toString().equals("")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountCheck() - arbitrary account ID");
         testBAOS.reset(); // clear buffer holding console output
         testAccountAccessible.check(PLAYER_ID, "Your account");
         if (!testBAOS.toString().equals("Your account: $10.00" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountCheck() - without permission");
         testBAOS.reset(); // clear buffer holding console output
         testAccountInaccessible.check(PLAYER_ID, "this inaccessible account");
         if (!testBAOS.toString().equals("You don't have permission to access this inaccessible account" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountCheck() - different username");
         testBAOS.reset(); // clear buffer holding console output
         testAccount3.check(InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount3");
         if (!testBAOS.toString().equals("(for possibleID) testAccount3: $30.00" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("accountCheck() - large number");
         testBAOS.reset(); // clear buffer holding console output
         testAccountAccessible.setMoney(1000000.1f);
         testAccountAccessible.check(PLAYER_ID, "Your account");
         if (!testBAOS.toString().equals("Your account: $1,000,000.12" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("accountCheck() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account's transferMoney().
    *
    * @return whether Account's transferMoney() passed all test cases
    */
   private static boolean testAccountSendMoney() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         System.err.println("accountSendMoney() - sending a negative amount of money");
         testAccount1.sendMoney(PLAYER_ID, -10.0f, "testAccount1", "testAccount2");
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account changed when it should not have changed");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 10.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 20.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(10.0f);
            testAccount2.setMoney(20.0f);
         }

         System.err.println("accountSendMoney() - sending no money");
         testAccount1.sendMoney(PLAYER_ID, 0.0f, "testAccount1", "testAccount2");
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account changed when it should not have changed");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 10.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 20.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(10.0f);
            testAccount2.setMoney(20.0f);
         }

         System.err.println("accountSendMoney() - sending with a null sender ID");
         testAccount1.sendMoney(PLAYER_ID, 1.0f, null, "testAccount2");
         if (testAccountFields(testAccount1, 9.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 21.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 9.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 21.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(9.0f);
            testAccount2.setMoney(21.0f);
         }

         System.err.println("accountSendMoney() - sending with an empty sender ID");
         testAccount1.sendMoney(PLAYER_ID, 1.0f, "", "testAccount2");
         if (testAccountFields(testAccount1, 8.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 22.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 8.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 22.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(8.0f);
            testAccount2.setMoney(22.0f);
         }

         System.err.println("accountSendMoney() - sending with an arbitrary sender ID");
         testAccount1.sendMoney(PLAYER_ID, 1.0f, "arbitrary account", "testAccount2");
         if (testAccountFields(testAccount1, 7.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 23.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 7.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 23.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(7.0f);
            testAccount2.setMoney(23.0f);
         }

         System.err.println("accountSendMoney() - sending with a null recipient ID");
         testAccount1.sendMoney(PLAYER_ID, 1.0f, "testAccount1", null);
         if (testAccountFields(testAccount1, 7.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account changed when it should not have changed");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 7.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(7.0f);
         }

         System.err.println("accountSendMoney() - sending with an empty recipient ID");
         testAccount1.sendMoney(PLAYER_ID, 1.0f, "testAccount1", "");
         if (testAccountFields(testAccount1, 7.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account changed when it should not have changed");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 7.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(7.0f);
         }

         System.err.println("accountSendMoney() - sending with an invalid recipient ID");
         testAccount1.sendMoney(PLAYER_ID, 10.0f, "testAccount1", "invalidAccount");
         if (testAccountFields(testAccount1, 7.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account changed when it should not have changed");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 7.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(7.0f);
         }

         System.err.println("accountSendMoney() - transferring normally between two accounts");
         testAccount1.sendMoney(PLAYER_ID, 5.0f, "testAccount1", "testAccount2");
         if (testAccountFields(testAccount1, 2.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 28.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 2.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 28.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set accounts appropriately for other tests
            testAccount1.setMoney(2.0f);
            testAccount2.setMoney(28.0f);
         }

         System.err.println("accountSendMoney() - transferring some funds from account to player");
         float playerAccountMoney = playerAccount.getMoney();
         testAccount2.sendMoney(PLAYER_ID, 20.0f, "testAccount2", InterfaceTerminal.playername);
         if (testAccountFields(testAccount2, 8.0f, InterfaceTerminal.playername)
             || testAccountFields(playerAccount, playerAccountMoney + 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 8.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   player account - money: " + playerAccount.getMoney() + ", should be " + (playerAccountMoney + 20.0f));
            System.err.println("   player account - player has permission: " + playerAccount.hasAccess(PLAYER_ID) + ", should be true");
            // set accounts appropriately for other tests
            testAccount2.setMoney(8.0f);
            playerAccount.addMoney(playerAccount.getMoney() * -1.0f);
            playerAccount.addMoney(playerAccountMoney + 20.0f);
            playerAccount.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }

         System.err.println("accountSendMoney() - transferring all funds from player to account");
         playerAccount.sendMoney(PLAYER_ID, playerAccount.getMoney(), InterfaceTerminal.playername, "testAccount1");
         if (testAccountFields(testAccount1, playerAccountMoney + 22.0f, InterfaceTerminal.playername)
             || testAccountFields(playerAccount, 0.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 52.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   player account - money: " + playerAccount.getMoney() + ", should be 0.0");
            System.err.println("   player account - player has permission: " + playerAccount.hasAccess(PLAYER_ID) + ", should be true");
         }
         // set accounts to appropriate or known values
         testAccount1.setMoney(52.0f);
         playerAccount.addMoney(playerAccount.getMoney() * -1.0f);
         playerAccount.addMoney(playerAccountMoney);
         playerAccount.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);

         System.err.println("accountSendMoney() - transferring without sufficient funds");
         testAccount1.sendMoney(PLAYER_ID, 500.0f, "testAccount1", "testAccount2");
         if (testAccountFields(testAccount1, 52.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 8.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account changed unexpectedly");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 52.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 8.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up account fields appropriately for other tests
            testAccount1.setMoney(52.0f);
            testAccount2.setMoney(8.0f);
         }

         System.err.println("accountSendMoney() - transferring without permission");
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "notPermitted";
         testAccount1.sendMoney(InterfaceTerminal.getPlayerIDStatic("notPermitted"), 1.0f, "testAccount1", "testAccount2");
         if (testAccountFields(testAccount1, 52.0f, playernameOrig)
             || testAccountFields(testAccount2, 8.0f, playernameOrig)) {
            errorFound = true;
            System.err.println("   test account changed unexpectedly");
            System.err.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 52.0");
            System.err.println("   test account 1 - player has permission: " + testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic(playernameOrig)) + ", should be true");
            System.err.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 8.0");
            System.err.println("   test account 2 - player has permission: " + testAccount2.hasAccess(InterfaceTerminal.getPlayerIDStatic(playernameOrig)) + ", should be true");
         }
         InterfaceTerminal.playername = playernameOrig;
         resetTestEnvironment();

         System.err.println("accountSendMoney() - different username");
         testAccount3.sendMoney(InterfaceTerminal.getPlayerIDStatic("possibleID"), 10.0f, "testAccount3", InterfaceTerminal.playername);
         if (testAccountFields(testAccount3, 20.0f, "possibleID")
             || testAccountFields(playerAccount, 40.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   test account 3 - money: " + testAccount3.getMoney() + ", should be 20.0");
            System.err.println("   test account 3 - possible ID has permission: " + testAccount3.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID")) + ", should be true");
            System.err.println("   player account - money: " + playerAccount.getMoney() + ", should be 40.0");
            System.err.println("   player account - player has permission: " + playerAccount.hasAccess(PLAYER_ID) + ", should be true");
         }
      }
      catch (Exception e) {
         System.err.println("accountSendMoney() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account's deleteAccount().
    *
    * @return whether Account's deleteAccount() passed all test cases
    */
   private static boolean testAccountDeletion() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // initialize test variables
      int     accountsSize = accounts.size();
      float   money;
      String  accountID;
      UUID    playerID;

      try {
         System.err.println("deleteAccount() - null account ID");
         accountID  = null;
         playerID   = PLAYER_ID;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - empty account ID");
         accountID  = "";
         playerID   = PLAYER_ID;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - invalid account ID");
         accountID  = "invalidAccount";
         playerID   = PLAYER_ID;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - null account owner");
         accountID  = "testAccount1";
         playerID   = null;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - invalid account owner");
         accountID  = "testAccount1";
         playerID   = InterfaceTerminal.getPlayerIDStatic("possibleID");

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().equals("(for possibleID) You are not permitted to delete " + accountID + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - account user instead of owner");
         accountID  = "testAccount1";
         playerID   = InterfaceTerminal.getPlayerIDStatic("possibleID");
         accounts.get(accountID).grantAccess(PLAYER_ID, playerID, accountID);

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().equals("(for possibleID) You are not permitted to delete " + accountID + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - server operator");
         accountID  = "testAccount4";
         playerID   = PLAYER_ID;
         accountsSize--;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (accounts.containsKey(accountID)) {
            System.err.println("   test account 4 exists when it shouldn't");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // prepare for next test
         accountsSize++;
         resetTestEnvironment();

         System.err.println("deleteAccount() - personal account");
         accountID  = InterfaceTerminal.playername;
         playerID   = PLAYER_ID;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().equals("Personal accounts may not be deleted" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         System.err.println("deleteAccount() - valid account ID and owner");
         accountID  = "testAccount1";
         playerID   = PLAYER_ID;
         money      = accounts.get(accountID).getMoney();
         accountsSize--;

         testBAOS.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!testBAOS.toString().equals("You received $" + money + " from " + accountID + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (accounts.containsKey(accountID)) {
            System.err.println("   test account 1 exists when it shouldn't");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            System.err.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            System.err.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f + money, InterfaceTerminal.playername)) {
            System.err.println("   unexpected values for player account");
            errorFound = true;
         }
         accountsSize++;
         resetTestEnvironment();


         // delete account_id
         System.err.println("delete() - null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(null);
         if (!testBAOS.toString().equals("/delete <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("delete() - empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{});
         if (!testBAOS.toString().equals("/delete <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("delete() - blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - must provide account ID")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("delete() - too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{});
         if (!testBAOS.toString().equals("/delete <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("delete() - too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{"possibleAccount", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("deleteAccount() - invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{"invalidAccount"});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("delete() - account user instead of owner");
         accounts.get(accountID).grantAccess(PLAYER_ID, playerID, accountID);
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "possibleID";
         accountID  = "testAccount1";
         playerID   = PLAYER_ID;

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!testBAOS.toString().equals("You are not permitted to delete " + accountID + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // prepare for next test
         InterfaceTerminal.playername = playernameOrig;
         resetTestEnvironment();

         System.err.println("delete() - server operator");
         accountID  = "testAccount4";
         accountsSize--;

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }

         // prepare for next test
         accountsSize++;
         resetTestEnvironment();

         System.err.println("delete() - personal account");
         accountID  = InterfaceTerminal.playername;

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!testBAOS.toString().equals("Personal accounts may not be deleted" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("delete() - valid account ID and owner");
         accountID  = "testAccount1";
         money      = accounts.get(accountID).getMoney();
         accountsSize--;

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!testBAOS.toString().equals("You received $" + money + " from " + accountID + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("deleteAccount() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account's handling for default accounts.
    *
    * @return whether Account's handling for default accounts passed all test cases
    */
   private static boolean testDefaultAccounts() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // initialize test variables
      Account account;
      float money;
      int   quantityToTrade;
      int   quantityWare;
      float price;
      String accountID;
      UUID playerID = PLAYER_ID;
      Config.filenameAccounts = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";

      try {
         System.err.println("setDefaultAccount() - null player ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(null, "testAccount1");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         System.err.println("setDefaultAccount() - null account ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, null);

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         System.err.println("setDefaultAccount() - empty account ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, "");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         System.err.println("setDefaultAccount() - nonexistent account ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, "invalidAccount");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         System.err.println("setDefaultAccount() - personal account ID when personal account doesn't exist");
         money = Config.accountStartingMoney;

         Account.setDefaultAccount(InterfaceTerminal.getPlayerIDStatic("possibleID"), "possibleID");

         account = Account.getAccount("possibleID");

         // check account existence
         if (account != null) {
            // check account funds
            if (account.getMoney() != money) {
               System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
               errorFound = true;
            }

            // check account reference
            if (account == playerAccount) {
               System.err.println("   unexpected account reference");
               errorFound = true;
            }
         } else {
            System.err.println("   account for possibleID does not exist when it should");
            errorFound = true;
         }
         defaultAccounts.clear();

         System.err.println("setDefaultAccount() - no permissions");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, "testAccount4");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         System.err.println("setDefaultAccount() - has permissions");
         money = testAccount1.getMoney();

         Account.setDefaultAccount(playerID, "testAccount1");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != testAccount1) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         // prepare for next tests
         defaultAccounts.put(playerID, testAccount1);

         System.err.println("default accounts - buying");
         quantityToTrade = 5;
         quantityWare    = Config.quanMid[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(playerID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         Marketplace.buy(playerID, null, "test:material1", quantityToTrade, 0.0f, null);

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         testAccount1.setMoney(10.0f);

         System.err.println("default accounts - selling");
         InterfaceTerminal.inventory.put("test:material1", 10);
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(playerID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();

         Marketplace.sell(playerID, null, "test:material1", quantityToTrade, 0.0f, null);

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         testAccount1.setMoney(10.0f);

         System.err.println("default accounts - sellall");
         quantityToTrade = 10;
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(playerID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();

         Marketplace.sellAll(playerID, null, getFormattedInventory(), null);

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         testAccount1.setMoney(10.0f);

         System.err.println("default accounts - sending");
         money           = testAccount1.getMoney();
         price           = testAccount4.getMoney();

         testAccount1.sendMoney(playerID, 5.0f, null, "testAccount4");

         if (testAccountFields(testAccount1, money - 5.0f, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount4, price + 5.0f, null)) {
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         testAccount4.setMoney(price);

         System.err.println("default accounts - receiving");
         money           = testAccount1.getMoney();
         price           = playerAccount.getMoney();

         testAccount3.sendMoney(InterfaceTerminal.getPlayerIDStatic("possibleID"), 5.0f, "testAccount3", InterfaceTerminal.playername);

         if (testAccountFields(testAccount1, money + 5.0f, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         playerAccount.setMoney(price);

         System.err.println("default accounts - deleting account");
         defaultAccounts.put(playerID, testAccount1);
         money = playerAccount.getMoney() + testAccount1.getMoney();

         Account.deleteAccount("testAccount1", playerID);
         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            System.err.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }

         System.err.println("default accounts - loading, entry with nonexistent account");
         // write to accounts file
         FileWriter fileWriter = new FileWriter(Config.filenameAccounts);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "testAccount1,30.0,9973aa2b-a115-38e1-862e-edb22f969f78\n" +
            "testAccount3,25.0,1fd68c5b-e55f-3852-9a68-f562d888abde\n" +
            "testAccount4,6.0\n" +
            "testAccountExtra,30.0,9973aa2b-a115-38e1-862e-edb22f969f78\n" +
            "John_Doe,20.0,9973aa2b-a115-38e1-862e-edb22f969f78\n\n" +
            "*,9973aa2b-a115-38e1-862e-edb22f969f78,invalidAccount\n" +
            "*,invalidUUID,testAccountExtra\n" +
            "*,1fd68c5b-e55f-3852-9a68-f562d888abde,testAccount1\n" +
            "*,06dbcaec-a326-32c0-a96c-5271703d2afa,$admin$\n"
         );
         fileWriter.close();

         // load from accounts file
         Account.loadAccounts();

         // test loading entry with nonexistent account
         playerAccount = Account.getAccount(InterfaceTerminal.playername);
         account       = Account.grabAndCheckAccount(null, playerID);

         // check account reference
         if (account != playerAccount) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }

         System.err.println("default accounts - loading, entry with invalid player ID");
         if (defaultAccounts.size() != 1) {
            System.err.println("   unexpected number of default account entries: " + defaultAccounts.size() + ", should be 1");
            errorFound = true;
         }

         System.err.println("default accounts - loading, entry mapping player to account without permissions");
         testAccount1 = Account.getAccount("testAccount1");
         account      = Account.grabAndCheckAccount(null, InterfaceTerminal.getPlayerIDStatic("possibleID"));

         // check account reference
         if (account == testAccount1) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }

         System.err.println("default accounts - loading, valid entry");
         account = Account.grabAndCheckAccount(null, UUID.nameUUIDFromBytes(("$admin$").getBytes()));

         // check account reference
         if (account != Account.getAccount("$admin$")) {
            System.err.println("   unexpected account reference");
            errorFound = true;
         }

         System.err.println("default accounts - revoking access to default account");
         // set up test and check setup
         defaultAccounts.put(playerID, testAccount1);
         account = Account.grabAndCheckAccount(null, playerID);
         if (account != testAccount1) {
            System.err.println("   unexpected account reference: failed to set up test");
            errorFound = true;
         }

         //run test
         else {
            testAccount1.revokeAccess(playerID, playerID, "testAccount1");
            account = Account.grabAndCheckAccount(null, playerID);

            // check account reference
            if (account != playerAccount) {
               System.err.println("   unexpected account reference");
               errorFound = true;
            }
         }

         System.err.println("default accounts - saving and loading");
         // add data to be saved
         Account.setDefaultAccount(InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount3");

         Account.saveAccounts();
         Account.loadAccounts();

         // check account references
         account = Account.grabAndCheckAccount(null, UUID.nameUUIDFromBytes(("$admin$").getBytes()));
         if (account != Account.getAccount("$admin$")) {
            System.err.println("   unexpected account reference for $admin$");
            errorFound = true;
         }
         account = Account.grabAndCheckAccount(null, playerID);
         if (account != Account.getAccount(InterfaceTerminal.playername)) {
            System.err.println("   unexpected account reference for " + InterfaceTerminal.playername);
            errorFound = true;
         }
         account = Account.grabAndCheckAccount(null, InterfaceTerminal.getPlayerIDStatic("possibleID"));
         if (account != Account.getAccount("testAccount3")) {
            System.err.println("   unexpected account reference for possibleID");
            errorFound = true;
         }

         // completely reset test environment to
         // reduce possibility of interference
         resetTestEnvironment();

         // test requests
         System.err.println("default accounts - request: null arg");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(null);
         if (!testBAOS.toString().equals("/setDefaultAccount <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("default accounts - request: blank arg");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{""});
         if (!testBAOS.toString().equals("error - zero-length arguments: /setDefaultAccount <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("default accounts - request: empty args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{});
         if (!testBAOS.toString().equals("/setDefaultAccount <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("default accounts - request: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"testAccount1", "excessArgument"});
         if (!testBAOS.toString().equals("error - wrong number of arguments: /setDefaultAccount <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("default accounts - request: invalid account");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"invalidAccount"});
         if (!testBAOS.toString().equals("error - account not found: invalidAccount" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("default accounts - request: no permissions");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"testAccount4"});
         if (!testBAOS.toString().equals("You don't have permission to access testAccount4" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("default accounts - request: valid usage");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"testAccount1"});
         if (!testBAOS.toString().equals("testAccount1 will now be used in place of your personal account" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("default accounts - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests /op, /deop, isAnOp(), and permissionToExecute().
    *
    * @return whether /op, /deop, isAnOp(), and permissionToExecute() passed all test cases
    */
   private static boolean testAdminPermissions() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         System.err.println("/op - null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(null);
         if (!testBAOS.toString().startsWith("/op <player_name>")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/op - blank username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/op - too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{});
         if (!testBAOS.toString().equals("/op <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/op - too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{"possibleID", "excessArgument"});
         if (!testBAOS.toString().equals("error - wrong number of arguments: /op <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/op - valid username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{"opID"});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/deop - null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(null);
         if (!testBAOS.toString().startsWith("/deop <player_name>")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/deop - blank username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/deop - too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{});
         if (!testBAOS.toString().equals("/deop <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/deop - too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{"possibleID", "excessArgument"});
         if (!testBAOS.toString().equals("error - wrong number of arguments: /deop <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("/deop - valid username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("isAnOp() - null username");
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic(null))) {
            System.err.println("   unexpected value: true");
            errorFound = true;
         }

         System.err.println("isAnOp() - blank username");
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic(""))) {
            System.err.println("   unexpected value: true");
            errorFound = true;
         }

         System.err.println("isAnOp() - checking for non-op");
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic("nonopID"))) {
            System.err.println("   unexpected value: true");
            errorFound = true;
         }

         System.err.println("isAnOp() - checking for op");
         if (!InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic("opID"))) {
            System.err.println("   unexpected value: false");
            errorFound = true;
         }

         System.err.println("adminPermissions() - giving multiple times, then revoking once");
         InterfaceTerminal.serviceRequestOp(new String[]{"nonopID"});
         InterfaceTerminal.serviceRequestOp(new String[]{"nonopID"});
         InterfaceTerminal.serviceRequestOp(new String[]{"nonopID"});
         InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic("nonopID"))) {
            System.err.println("   unexpected value: true");
            errorFound = true;

            // reset for other tests
            InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
            InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
            InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
         }

         System.err.println("permissionToExecute() - non-op executing non-op command on self");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("nonopID"), InterfaceTerminal.getPlayerIDStatic("nonopID"), false)) {
            System.err.println("   unexpected value: false");
            errorFound = true;
         }

         System.err.println("permissionToExecute() - non-op executing non-op command on others");
         if (InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("opID"), InterfaceTerminal.getPlayerIDStatic("nonopID"), false)) {
            System.err.println("   unexpected value: true");
            errorFound = true;
         }

         System.err.println("permissionToExecute() - non-op executing op command");
         if (InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("nonopID"), InterfaceTerminal.getPlayerIDStatic("nonopID"), true)) {
            System.err.println("   unexpected value: true");
            errorFound = true;
         }

         System.err.println("permissionToExecute() - op executing non-op command on self");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("opID"), InterfaceTerminal.getPlayerIDStatic("opID"), false)) {
            System.err.println("   unexpected value: false");
            errorFound = true;
         }

         System.err.println("permissionToExecute() - op executing non-op command on others");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("nonopID"), InterfaceTerminal.getPlayerIDStatic("opID"), false)) {
            System.err.println("   unexpected value: false");
            errorFound = true;
         }

         System.err.println("permissionToExecute() - op executing op command");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("opID"), InterfaceTerminal.getPlayerIDStatic("opID"), true)) {
            System.err.println("   unexpected value: false");
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("adminPermissions() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests translateWareID().
    *
    * @return whether translateWareID() passed all test cases
    */
   private static boolean testTranslateWareID() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // create new wares for testing variants
      wares.put("test:material1&1", new WareMaterial("test:material1&1", "testMat1.1", 10.0f, 64, (byte) 0));
      wareAliasTranslations.put("testMat1.1", "test:material1&1");
      wares.put("test:untradeable1&1", new WareUntradeable("test:untradeable1&1", "notrade1&1", 32.0f));
      wareAliasTranslations.put("notrade1&1", "test:untradeable1&1");

      try {
         System.err.println("translateWareID() - null ware ID");
         if (!Marketplace.translateWareID(null).isEmpty()) {
            System.err.println("   unexpected console output: " + Marketplace.translateWareID(null));
            errorFound = true;
         }

         System.err.println("translateWareID() - empty ware ID");
         if (!Marketplace.translateWareID("").isEmpty()) {
            System.err.println("   unexpected console output: " + Marketplace.translateWareID(""));
            errorFound = true;
         }

         System.err.println("translateWareID() - invalid ware ID");
         if (!Marketplace.translateWareID("test:invalidWareID").isEmpty()) {
            System.err.println("   unexpected console output: " + Marketplace.translateWareID("test:invalidWareID"));
            errorFound = true;
         }

         System.err.println("translateWareID() - invalid ware alias");
         if (!Marketplace.translateWareID("invalidWareAlias").isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("translateWareID() - invalid variant ware ID");
         if (!Marketplace.translateWareID("test:invalidWareID&6").isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("translateWareID() - invalid variant ware alias");
         if (!Marketplace.translateWareID("invalidWareAlias&6").isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("translateWareID() - valid ware ID");
         if (!Marketplace.translateWareID("test:material1").equals("test:material1")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("test:material1") + ", should be test:material1");
         }
         if (!Marketplace.translateWareID("mat3").equals("test:material3")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("mat3") + ", should be test:material3");
         }

         System.err.println("translateWareID() - valid variant ware ID");
         if (!Marketplace.translateWareID("test:material1&1").equals("test:material1&1")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("test:material1&1") + ", should be test:material1&1");
         }

         System.err.println("translateWareID() - valid variant ware alias");
         if (!Marketplace.translateWareID("notrade1&1").equals("test:untradeable1&1")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("notrade1&1") + ", should be test:untradeable1&1");
         }

         System.err.println("translateWareID() - unknown variant of valid ware ID");
         if (!Marketplace.translateWareID("test:material1&2").equals("test:material1")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("test:material1&2") + ", should be test:material1");
         }

         System.err.println("translateWareID() - unknown variant of valid ware alias");
         if (!Marketplace.translateWareID("craft1&6").equals("test:crafted1")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("craft1&6") + ", should be test:crafted1");
         }

         System.err.println("translateWareID() - existing Forge OreDictionary Name");
         wareAliasTranslations.put("#testName", "test:material2");
         if (!Marketplace.translateWareID("#testName").equals("test:material2")) {
            errorFound = true;
            System.err.println("   unexpected result: " + Marketplace.translateWareID("#testName") + ", should be test:material2");
         }

         System.err.println("translateWareID() - nonexistent Forge OreDictionary Name");
         if (!Marketplace.translateWareID("#invalidName").isEmpty()) {
            System.err.println("   unexpected console output: " + Marketplace.translateWareID("#invalidName"));
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("translateWareID() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests getPrice().
    *
    * @return whether getPrice() passed all test cases
    */
   private static boolean testGetPrice() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      float currentPrice = 0.0f;                   // holds price being check
      Ware testWare = wares.get("test:material2"); // for testing quantity's effect on price
      float expectedPrice = 0.0f;                   // holds price to be checked against
      int   quantity = 0;                           // holds ware's quantity available for sale
      try {
         System.err.println("getPrice() - using null ware ID");
         currentPrice = Marketplace.getPrice(PLAYER_ID, null, 0, false);
         if (!Float.isNaN(currentPrice)) {
            System.err.println("   incorrect price: " + currentPrice + ", should be NaN");
            errorFound = true;
         }

         System.err.println("getPrice() - using empty ware ID");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "", 0, false);
         if (!Float.isNaN(currentPrice)) {
            System.err.println("   incorrect price: " + currentPrice + ", should be NaN");
            errorFound = true;
         }

         System.err.println("getPrice() - using invalid ware ID");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "invalidWare", 0, false);
         if (!Float.isNaN(currentPrice)) {
            System.err.println("   incorrect price: " + currentPrice + ", should be NaN");
            errorFound = true;
         }

         System.err.println("getPrice() - using level 0 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false);
         if (currentPrice != 1.0f) {
            System.err.println("   incorrect price (test:material1): " + currentPrice + ", should be 1.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using level 1 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);
         if (currentPrice != 19.2f) {
            System.err.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 19.2");
            errorFound = true;
         }

         System.err.println("getPrice() - using level 2 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material3", 0, false);
         if (currentPrice != 4.0f) {
            System.err.println("   incorrect price (test:material3): " + currentPrice + ", should be 4.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using level 3 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", 0, false);
         if (currentPrice != 8.0f) {
            System.err.println("   incorrect price (minecraft:material4): " + currentPrice + ", should be 8.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using level 4 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:processed1", 0, false);
         if (currentPrice != 1.1f) {
            System.err.println("   incorrect price (test:processed1): " + currentPrice + ", should be 1.1");
            errorFound = true;
         }

         System.err.println("getPrice() - using level 5 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:processed2", 0, false);
         if (currentPrice != 14.3f) {
            System.err.println("   incorrect price (test:processed2): " + currentPrice + ", should be 14.3");
            errorFound = true;
         }

         System.err.println("getPrice() - purchase without buying upcharge");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true);
         if (currentPrice != 1.0f) {
            System.err.println("   incorrect price (test:material1): " + currentPrice + ", should be 1.0");
            errorFound = true;
         }

         System.err.println("getPrice() - purchase with buying upcharge");
         Config.priceBuyUpchargeMult = 2.0f;
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true);
         if (currentPrice != 2.0f) {
            System.err.println("   incorrect price (test:material1): " + currentPrice + ", should be 2.0");
            errorFound = true;
         }

         // let's keep the math simple so it's easy to check
         // set price to $2
         Field testPriceBase = Ware.class.getDeclaredField("priceBase");
         testPriceBase.setAccessible(true);
         testPriceBase.setFloat(testWare, 2.0f);

         System.err.println("getPrice() - using no quantity ware");
         testWare.setQuantity(0);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 4.0f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 4.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using understocked ware");
         testWare.setQuantity(63);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 4.0f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 4.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using low stock ware");
         testWare.setQuantity(79);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 3.5f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 3.5");
            errorFound = true;
         }

         System.err.println("getPrice() - using pretty low stock ware");
         testWare.setQuantity(95);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 3.0f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 3.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using kinda low stock ware");
         testWare.setQuantity(111);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 2.5f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 2.5");
            errorFound = true;
         }

         System.err.println("getPrice() - using kinda high stock ware");
         testWare.setQuantity(223);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 1.5f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 1.5");
            errorFound = true;
         }

         System.err.println("getPrice() - using pretty high stock ware");
         testWare.setQuantity(319);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 1.0f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 1.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using high stock ware");
         testWare.setQuantity(415);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 0.5f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 0.5");
            errorFound = true;
         }

         System.err.println("getPrice() - using overstocked ware");
         testWare.setQuantity(511);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 0.0f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 0.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using excessively overstocked ware");
         testWare.setQuantity(1023);
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material2", 0, false);
         if (currentPrice != 0.0f) {
            System.err.println("   incorrect price (test:material2): " + currentPrice + ", should be 0.0");
            errorFound = true;
         }

         // set average price base and start quantities to values used in expected calculations
         fPriceBaseAverage.setFloat(null, 9.23f);
         fStartQuanBaseAverage.setFloat(null, 87);

         Config.priceSpread = 2.0f;
         System.err.println("getPrice() - using high spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material3", 0, false);
         if (currentPrice != 0.0f) {
            System.err.println("   incorrect price (test:material3): " + currentPrice + ", should be 0.0");
            errorFound = true;
         }

         System.err.println("getPrice() - using high spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);
         if (currentPrice != 29.17f) {
            System.err.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 29.17");
            errorFound = true;
         }

         Config.priceSpread = 1.5f;
         System.err.println("getPrice() - using fairly high spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material3", 0, false);
         if (currentPrice != 1.385f) {
            System.err.println("   incorrect price (test:material3): " + currentPrice + ", should be 1.385");
            errorFound = true;
         }

         System.err.println("getPrice() - using fairly high spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);
         if (currentPrice != 24.185f) {
            System.err.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 24.185");
            errorFound = true;
         }

         Config.priceSpread = 0.75f;
         System.err.println("getPrice() - using fairly low spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material3", 0, false);
         if (currentPrice != 5.3075f) {
            System.err.println("   incorrect price (test:material3): " + currentPrice + ", should be 1.385");
            errorFound = true;
         }

         System.err.println("getPrice() - using fairly low spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);
         if (currentPrice != 16.7075f) {
            System.err.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 16.7075");
            errorFound = true;
         }

         Config.priceSpread = 0.5f;
         System.err.println("getPrice() - using low spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material3", 0, false);
         if (currentPrice != 6.615f) {
            System.err.println("   incorrect price (test:material3): " + currentPrice + ", should be 6.615");
            errorFound = true;
         }

         System.err.println("getPrice() - using low spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);
         if (currentPrice != 14.215f) {
            System.err.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 14.215");
            errorFound = true;
         }

         Config.priceSpread = 0.0f;
         System.err.println("getPrice() - using zero spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material3", 0, false);
         if (currentPrice != 9.2299f) {
            System.err.println("   incorrect price (test:material3): " + currentPrice + ", should be 9.23");
            errorFound = true;
         }

         System.err.println("getPrice() - using zero spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);
         if (currentPrice != 9.2299f) {
            System.err.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 9.23");
            errorFound = true;
         }

         // prepare for next tests
         Config.priceSpread        =  1.0f;
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         double quanCeilingFromEquilibrium = Config.quanHigh[testWare1.getLevel()] - Config.quanMid[testWare1.getLevel()];

         System.err.println("getPrice() - negative prices, at -100% cost");
         expectedPrice = testWare1.getBasePrice() * Config.priceFloor;
         quantity      = Config.quanHigh[testWare1.getLevel()];
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareP1.getBasePrice() * Config.priceFloor;
         quantity      = Config.quanHigh[testWareP1.getLevel()];
         testWareP1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:processed1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         System.err.println("getPrice() - negative prices, at -50% cost");
         expectedPrice = testWare1.getBasePrice() * Config.priceFloor * 0.50f;
         quantity      = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75) - 1;
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareP1.getBasePrice() * Config.priceFloor * 0.50f;
         quantity      = Config.quanMid[testWareP1.getLevel()] + (int) ((Config.quanHigh[testWareP1.getLevel()] - Config.quanMid[testWareP1.getLevel()]) * 0.75) - 1;
         testWareP1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:processed1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         System.err.println("getPrice() - negative prices, at no cost");
         expectedPrice = 0.0f;
         quantity      = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.5) - 1;
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = 0.0f;
         quantity      = Config.quanMid[testWareC1.getLevel()] + (int) ((Config.quanHigh[testWareC1.getLevel()] - Config.quanMid[testWareC1.getLevel()]) * 0.5) - 1;
         testWareC1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         System.err.println("getPrice() - negative prices, at 50% cost");
         expectedPrice = testWare1.getBasePrice() * Config.priceFloor * -0.50f;
         quantity      = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25) - 1;
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareP2.getBasePrice() * Config.priceFloor * -0.50f;
         quantity      = Config.quanMid[testWareP2.getLevel()] + (int) ((Config.quanHigh[testWareP2.getLevel()] - Config.quanMid[testWareP2.getLevel()]) * 0.25) - 1;
         testWareP2.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:processed2", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         System.err.println("getPrice() - negative prices, at equilibrium");
         expectedPrice = testWare1.getBasePrice();
         quantity      = Config.quanMid[testWare1.getLevel()];
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareC2.getBasePrice();
         quantity      = Config.quanMid[testWareC2.getLevel()];
         testWareC2.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, "test:crafted2", 0, false);

         if (currentPrice != expectedPrice) {
            System.err.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("getPrice() - fatal error: " + e);
         errorFound = true;
      }

      return !errorFound;
   }

   /**
    * Tests check().
    *
    * @return whether check() passed all test cases
    */
   private static boolean testCheck() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // set up test variables
      float price;
      int   quantity;

      try {
         System.err.println("check() - null ware ID");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, null, 1);
         if (!testBAOS.toString().equals("error - no ware ID was given" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - empty ware ID");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "", 1);
         if (!testBAOS.toString().equals("error - no ware ID was given" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - invalid ware ID");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "invalidID", 1);
         if (!testBAOS.toString().equals("error - ware not found: invalidID" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - untradeable ware ID");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:untradeable1", 1);
         if (!testBAOS.toString().equals("notrade1 (test:untradeable1): $16.00" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using valid ware ID without alias and buying upcharge");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material1", 1);
         if (!testBAOS.toString().equals("test:material1: $1.00, 256" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using valid ware ID with alias and without buying upcharge");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material3", 1);
         if (!testBAOS.toString().equals("mat3 (test:material3): $4.00, 64" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         Config.priceBuyUpchargeMult = 2.0f;
         System.err.println("check() - using valid ware ID without alias and with buying upcharge");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:processed2", 1);
         if (!testBAOS.toString().equals("test:processed2: Buy - $28.60 | Sell - $14.30, 8" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using valid ware ID with alias and buying upcharge");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:crafted1", 1);
         if (!testBAOS.toString().equals("craft1 (test:crafted1): Buy - $38.40 | Sell - $19.20, 128" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using zero quantity with buying upcharge");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", 0);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): Buy - $16.00 | Sell - $8.00, 32" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using high quantity with buying upcharge");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", 10);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): Buy - $16.00 | Sell - $8.00, 32" + System.lineSeparator() + "   for 10: Buy - $205.00 | Sell - $75.50" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         Config.priceBuyUpchargeMult = 1.0f;

         System.err.println("check() - using negative quantity");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", -1);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using zero quantity");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", 0);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using singular quantity");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", 1);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using double quantity");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", 2);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for 2: Buy - $16.50 | Sell - $15.83" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - using high quantity");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "minecraft:material4", 10);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for 10: Buy - $102.50 | Sell - $75.50" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - referencing ware using alias");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "material4", 10);
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for 10: Buy - $102.50 | Sell - $75.50" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - null username");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(InterfaceTerminal.getPlayerIDStatic(null), "material4", 10);
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - empty username");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(InterfaceTerminal.getPlayerIDStatic(""), "material4", 10);
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("check() - different username");
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(InterfaceTerminal.getPlayerIDStatic("possibleID"), "material4", 10);
         if (!testBAOS.toString().startsWith("(for possibleID) material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "(for possibleID)    for 10: Buy - $102.50 | Sell - $75.50" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium = Config.quanHigh[testWare1.getLevel()] - Config.quanMid[testWare1.getLevel()];

         System.err.println("check() - negative prices, at -100% cost");
         price    = testWare1.getBasePrice() * Config.priceFloor;
         quantity = Config.quanHigh[testWare1.getLevel()];
         testWare1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material1", 1);

         if (!testBAOS.toString().startsWith("test:material1: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (material): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         price    = testWareP1.getBasePrice() * Config.priceFloor;
         quantity = Config.quanHigh[testWareP1.getLevel()];
         testWareP1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:processed1", 1);

         if (!testBAOS.toString().startsWith("test:processed1: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (manufactured): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         System.err.println("check() - negative prices, at -50% cost");
         price    = testWare1.getBasePrice() * Config.priceFloor * 0.50f;
         quantity = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f) - 1;
         testWare1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material1", 1);

         if (!testBAOS.toString().startsWith("test:material1: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (material): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         price    = testWareC2.getBasePrice() * Config.priceFloor * 0.50f;
         quantity = Config.quanMid[testWareC2.getLevel()] + (int) ((Config.quanHigh[testWareC2.getLevel()] - Config.quanMid[testWareC2.getLevel()]) * 0.75f) - 1;
         testWareC2.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:crafted2", 1);

         if (!testBAOS.toString().startsWith("test:crafted2: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (manufactured): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         System.err.println("check() - negative prices, at no cost");
         price    = 0.0f;
         quantity = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f) - 1;
         testWare1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material1", 1);

         if (!testBAOS.toString().startsWith("test:material1: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (material): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         price    = 0.0f;
         quantity = Config.quanMid[testWareP2.getLevel()] + (int) ((Config.quanHigh[testWareP2.getLevel()] - Config.quanMid[testWareP2.getLevel()]) * 0.50f) - 1;
         testWareP2.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:processed2", 1);

         if (!testBAOS.toString().startsWith("test:processed2: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (manufactured): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         System.err.println("check() - negative prices, at 50% cost");
         price    = testWare1.getBasePrice() * Config.priceFloor * -0.50f;
         quantity = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25f) - 1;
         testWare1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material1", 1);

         if (!testBAOS.toString().startsWith("test:material1: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (material): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         price    = testWareC1.getBasePrice() * Config.priceFloor * -0.50f;
         quantity = Config.quanMid[testWareC1.getLevel()] + (int) ((Config.quanHigh[testWareC1.getLevel()] - Config.quanMid[testWareC1.getLevel()]) * 0.25f) - 1;
         testWareC1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:crafted1", 1);

         if (!testBAOS.toString().startsWith("craft1 (test:crafted1): " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (manufactured): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         System.err.println("check() - negative prices, at equilibrium");
         price    = testWare1.getBasePrice();
         quantity = Config.quanMid[testWare1.getLevel()];
         testWare1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:material1", 1);

         if (!testBAOS.toString().startsWith("test:material1: " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (material): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }

         price    = testWareC1.getBasePrice();
         quantity = Config.quanMid[testWareC1.getLevel()];
         testWareC1.setQuantity(quantity);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "test:crafted1", 1);

         if (!testBAOS.toString().startsWith("craft1 (test:crafted1): " +  CommandEconomy.PRICE_FORMAT.format(price) + ", " + quantity + System.lineSeparator())) {
            System.err.println("   unexpected console output (manufactured): " + testBAOS.toString());
            System.err.println("   expected price: " + CommandEconomy.PRICE_FORMAT.format(price) + "\n   expected quantity: " + quantity);
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("check() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests buy().
    *
    * @return whether buy() passed all test cases
    */
   private static boolean testBuy() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // track changes to variables
      int   quantityToTrade;
      int   quantityWare;
      float price;
      float money;

      try {
         System.err.println("buy() - null account ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = playerAccount.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, null);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - empty account ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = playerAccount.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - invalid account ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = playerAccount.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "invalidAccount");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - using account without permission");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount4.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "testAccount4");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccount4.getMoney() != money) {
            errorFound = true;
            System.err.println("   unexpected account funds: " + testAccount4.getMoney() + ", should be " + money);
         }
         resetTestEnvironment();

         System.err.println("buy() - null ware ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, null, quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - empty ware ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - invalid ware ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "invalidWare", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - untradeable ware ID");
         quantityToTrade = 1;
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:untradeable1", quantityToTrade, 0.0f, "testAccount1");
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying no quantity of ware");
         quantityToTrade = 0;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying negative quantity of ware");
         quantityToTrade = -1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying out-of-stock ware");
         testWare1.setQuantity(0); // set ware to be out-of-stock
         quantityWare      = 0;
         money             = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", 1, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying without inventory space");
         int inventorySpaceOrig = InterfaceTerminal.inventorySpace;
         InterfaceTerminal.inventorySpace = 0; // maximum inventory space is no inventory
         quantityWare = testWare1.getQuantity();
         money        = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", 1, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();
         InterfaceTerminal.inventorySpace = inventorySpaceOrig; // reset maximum inventory space

         System.err.println("buy() - buying without any money");
         testAccount1.setMoney(0.0f);
         Marketplace.buy(PLAYER_ID, null, "test:material1", 1, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, 256)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 0.0f, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad4 to quad4) overstocked to overstocked");
         Config.priceFloor = 0.1f;
         quantityToTrade = 100; // only able to buy this much
         quantityWare    = Config.quanHigh[testWare1.getLevel()] * 2;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         Marketplace.buy(PLAYER_ID, null, "test:material1", 200, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         Config.priceFloor = 0.0f;
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad4 to quad3), overstocked to above equilibrium");
         Config.priceFloor = 0.1f;
         quantityToTrade = 128; // only able to buy this much
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         Marketplace.buy(PLAYER_ID, null, "test:material1", 200, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         Config.priceFloor = 0.0f;
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad4 to quad2), overstocked to below equilibrium");
         Config.priceFloor = 0.1f;
         quantityToTrade = 832; // only able to buy this much
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true) + testWare1.getBasePrice() / 2;
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 999, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         Config.priceFloor = 0.0f;
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad4 to quad1), overstocked to understocked");
         Config.priceFloor = 0.1f;
         quantityToTrade = 934; // only able to buy this much
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true) + testWare1.getBasePrice() / 2;
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 999, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         Config.priceFloor = 0.0f;
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad3 to quad3), above equilibrium to above equilibrium");
         quantityToTrade = 14; // only able to buy this much
         quantityWare    = testWare1.getQuantity() * 2;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         Marketplace.buy(PLAYER_ID, null, "test:material1", 20, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad3 to quad2), above equilibrium to below equilibrium");
         quantityToTrade = testWare1.getQuantity() * 2 - Config.quanLow[testWare1.getLevel()] - 10; // only able to buy this much
         quantityWare    = testWare1.getQuantity() * 2;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true) + testWare1.getBasePrice();
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 999, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad3 to quad1), above equilibrium to understocked");
         quantityToTrade = 448; // only able to buy this much
         quantityWare    = testWare1.getQuantity() * 2;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true) + testWare1.getBasePrice();
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 999, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - (equil to quad2) equilibrium to below equilibrium");
         quantityToTrade = 1; // only able to buy this much
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = 1.0f;
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 20, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad2 to quad2), below equilibrium to below equilibrium");
         quantityToTrade = 6; // only able to buy this much
         quantityWare    = 192;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         Marketplace.buy(PLAYER_ID, null, "test:material1", 20, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         quantityToTrade = 64; // only able to buy this much
         quantityWare    = 224;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true) + testWare1.getBasePrice();
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 999, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad2 to quad1), below equilibrium to understocked");
         quantityToTrade = 192 - Config.quanLow[testWare1.getLevel()] + 20; // only able to buy this much
         quantityWare    = 192;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true) + testWare1.getBasePrice();
         testAccount1.setMoney(money);

         Marketplace.buy(PLAYER_ID, null, "test:material1", 500, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - over-ordering, (quad1 to quad1), understocked to understocked");
         quantityToTrade = 5; // only able to buy this much
         quantityWare    = testWare1.getQuantity() / 3;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         Marketplace.buy(PLAYER_ID, null, "test:material1", 20, 0.0f, "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + testAccount1.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying some of a ware with means to buy more");
         quantityToTrade = 8;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying-out ware by requesting more than is available");
         testAccount1.setMoney(1000.0f); // make sure there's enough money to buy-out ware
         quantityToTrade = testWare1.getQuantity(); // will buy this much
         quantityWare    = 0;
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = 1000.0f;
         Marketplace.buy(PLAYER_ID, null, "test:material1", 1000, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying ware with max price acceptable being NaN");
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", 1, Float.NaN, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying ware with max price acceptable being too low");
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", 1, 0.1f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - buying ware with max price acceptable being high");
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 100.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         // Effects of scarcity and price ceilings are not tested here
         // since those tests are more appropriate in Marketplace.getPrice().
         // buy() handles purchasing wares, ensuring stock is reduced and accounts are charged
         // when appropriate, not managing the cascading effects of those actions.

         System.err.println("buy() - referencing ware using alias");
         quantityToTrade = 2;
         quantityWare    = testWare3.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "mat3", quantityToTrade, 100.0f, "testAccount1");
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - null coordinates");
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "test:material1", quantityToTrade, 100.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - invalid coordinates");
         // note: coordinates below are invalid for the Terminal Interface, but not the Minecraft Interface
         testBAOS.reset(); // clear buffer holding console output
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, new InterfaceCommand.Coordinates(1, 2, 3, 0), "test:material1", 5, 100.0f, "testAccount1");
         if (!testBAOS.toString().equals("No inventory was found" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - valid coordinates");
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, new InterfaceCommand.Coordinates(0, 0, -1, 0), "test:material1", quantityToTrade, 100.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventoryNorth.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryNorth.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryNorth.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - zeroed coordinates");
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, new InterfaceCommand.Coordinates(0, 0, 0, 0), "test:material1", quantityToTrade, 100.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - null username");
         testBAOS.reset(); // clear buffer holding console output
         quantityWare    = testWare1.getQuantity();
         money           = testAccount4.getMoney();
         Marketplace.buy(InterfaceTerminal.getPlayerIDStatic(null), null, "test:material1", 3, 100.0f, "testAccount4");
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount4, money, null)) {
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - empty username");
         testBAOS.reset(); // clear buffer holding console output
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(InterfaceTerminal.getPlayerIDStatic(""), null, "test:material1", 3, 100.0f, null);
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (accounts.containsKey("")) {
            System.err.println("   account was created when it should not have been");
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - different username");
         quantityToTrade = 20;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount3.getMoney();
         Marketplace.buy(InterfaceTerminal.getPlayerIDStatic("possibleID"), null, "test:material1", quantityToTrade, 100.0f, "testAccount3");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount3, money - price, "possibleID")) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         // buy ware_id quantity [max_unit_price] [account_id]
         System.err.println("buy() - request: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(null);
         if (!testBAOS.toString().equals("/buy <ware_id> <quantity> [max_unit_price] [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("buy() - request: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{});
         if (!testBAOS.toString().equals("/buy <ware_id> <quantity> [max_unit_price] [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("buy() - request: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("buy() - request: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1"});
         if (!testBAOS.toString().equals("error - wrong number of arguments: /buy <ware_id> <quantity> [max_unit_price] [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("buy() - request: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "10.0", "testAccount1", "excessArgument", "excessArgument", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: invalid ware ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"invalidWare", "10"});
         if (!testBAOS.toString().startsWith("error - ware not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: invalid price");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "invalidPrice", "testAccount1"});
         if (!testBAOS.toString().startsWith("error - invalid price")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: invalid account ID without given price");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: invalid account ID with valid price");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "10.0", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: minimum args");
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade)});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: valid price");
         testWare1.setQuantity(256);
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "10.0"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: valid account ID");
         testWare1.setQuantity(256);
         quantityToTrade    = 10;
         price              = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money              = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount2"});
         if (testAccountFields(testAccount2, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: valid price and account ID");
         testWare1.setQuantity(256);
         testAccount2.setMoney(20.0f);
         quantityToTrade    = 10;
         price              = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money              = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "10.0", "testAccount2"});
         if (testAccountFields(testAccount2, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: referencing ware using alias");
         testWare4.setQuantity(32);
         testAccount2.setMoney(20.0f);
         quantityToTrade    = 2;
         price              = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, true);
         money              = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestBuy(new String[]{"material4", String.valueOf(quantityToTrade), "100.0", "testAccount2"});
         if (testAccountFields(testAccount2, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         // reset changed variables to avoid interfering with other tests
         resetTestEnvironment();

         System.err.println("buy() - request: null coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, null, "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: invalid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, "invalidCoordinates", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - request: valid coordinates");
         quantityToTrade = 10;
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, "south", "test:material1", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().startsWith("Bought 10 test:material1 for $" + String.format("%.2f", price) + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventorySouth.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventorySouth.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventorySouth.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - request: zeroed coordinates");
         quantityToTrade = 10;
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, "none", "test:material1", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().startsWith("Bought 10 test:material1 for $" + String.format("%.2f", price) + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - request: null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{null, "none", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: empty username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"", "none", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("buy() - request: different username");
         quantityToTrade = 20;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount3.getMoney();
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"possibleID", "none", "test:material1", String.valueOf(quantityToTrade), "testAccount3"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount3, money - price, "possibleID")) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         if (!testBAOS.toString().startsWith("(for possibleID) Bought " + quantityToTrade + " test:material1 for " + CommandEconomy.PRICE_FORMAT.format(price) + " taken from testAccount3" + System.lineSeparator())
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            System.err.println("   expected: " + "(for possibleID) Bought " + quantityToTrade + " test:material1 for " + CommandEconomy.PRICE_FORMAT.format(price) + " taken from testAccount3" + System.lineSeparator());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - existing Forge OreDictionary Name");
         wareAliasTranslations.put("#testName", "test:material2");
         testWare2.setQuantity(1000);
         quantityToTrade = 2;
         quantityWare    = testWare2.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material2", quantityToTrade, true);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "#testName", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare2, WareMaterial.class, "", (byte) 1, 27.6f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - nonexistent Forge OreDictionary Name");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "#invalidName", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - request: admin account");
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "$admin$"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   console output: " + testBAOS.toString());
            errorFound = true;
         }

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium = Config.quanHigh[testWare1.getLevel()] - Config.quanMid[testWare1.getLevel()];

         System.err.println("buy() - negative prices, at -100% cost");
         quantityToTrade = 10;
         quantityWare    = Config.quanHigh[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (money >= testAccount1.getMoney()) {
            System.err.println("   account funds are " + testAccount1.getMoney() + ", should be greater than " + money);
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - negative prices, at -50% cost");
         quantityToTrade = 20;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f);
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (money >= testAccount1.getMoney()) {
            System.err.println("   account funds are " + testAccount1.getMoney() + ", should be greater than " + money);
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - negative prices, at no cost");
         quantityToTrade = 5;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f);
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - negative prices, at 50% cost");
         quantityToTrade = 5;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f);
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - negative prices, at no cost");
         quantityToTrade = 10;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f);
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - negative prices, at equilibrium");
         quantityToTrade = 9;
         quantityWare    = Config.quanMid[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - negative prices, overbuying");
         quantityToTrade = 777;
         quantityWare    = Config.quanHigh[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("buy() - acceptable price, (quad4 to quad4), overstocked to overstocked");
         quantityToTrade = 12;
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "0.0001", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad4 to quad3), overstocked to above equilibrium");
         quantityToTrade = 395;
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "0.5", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad4 to quad2), overstocked to below equilibrium");
         quantityToTrade = 843;
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "1.5", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad4 to quad1), overstocked to understocked");
         quantityToTrade = 1034;
         quantityWare    = Config.quanHigh[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "3.0", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad3 to quad3), above equilibrium to above equilibrium");
         quantityToTrade = 192;
         quantityWare    = 639;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "0.75", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad3 to quad2), above equilibrium to below equilibrium");
         quantityToTrade = 416;
         quantityWare    = 639;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "1.25", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad3 to quad1), above equilibrium to understocked");
         quantityToTrade = 511;
         quantityWare    = 639;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "1.99", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad2 to quad2), below equilibrium to below equilibrium");
         quantityToTrade = 32;
         quantityWare    = 191;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "1.75", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad2 to quad1), below equilibrium to understocked");
         quantityToTrade = 191;
         quantityWare    = 191;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "9999", "2.0", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("buy() - acceptable price, (quad2 to quad1), below equilibrium to understocked");
         quantityToTrade = 128;
         quantityWare    = 128;
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, true);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "2.0", "$admin$"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            System.err.println("   purchased quantity: " + (quantityWare - testWare1.getQuantity()));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, true));
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
      }
      catch (Exception e) {
         System.err.println("buy() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests sell().
    *
    * @return whether sell() passed all test cases
    */
   private static boolean testSell() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // give wares to the player
      InterfaceTerminal.inventory.put("test:material1", 1024);

      // track changes to variables
      int   quantityToTrade;
      int   quantityWare;
      float price;
      float money;

      try {
         System.err.println("sell() - null account ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, null);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         System.err.println("sell() - empty account ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         System.err.println("sell() - invalid account ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         Marketplace.sell(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "invalidAccount");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }

         System.err.println("sell() - using account without permission");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount4.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", quantityToTrade, 0.0f, "testAccount4");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccount4.getMoney() != money) {
            errorFound = true;
            System.err.println("   unexpected account funds: " + testAccount4.getMoney() + ", should be " + money);
         }
         resetTestEnvironment();

         System.err.println("sell() - null ware ID");
         money = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, null, 1, 0.0f, "testAccount1");
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - empty ware ID");
         money = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "", 1, 0.0f, "testAccount1");
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - invalid ware ID");
         money = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "invalidWare", 1, 0.0f, "testAccount1");
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - selling no quantity of ware");
         InterfaceTerminal.inventory.put("test:material1", 100); // set stock amount to known value
         quantityToTrade = 100;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", 0, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();
         InterfaceTerminal.inventory.put("test:material1", 1024); // reset stock amount for other tests

         System.err.println("sell() - selling negative quantity of ware");
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", -1, 0.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         System.err.println("sell() - selling ware player does not have");
         quantityWare    = testWare2.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material2", 1, 0.0f, "testAccount1");
         if (testWareFields(testWare2, WareMaterial.class, "", (byte) 1, 27.6f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - selling without sufficient stock to fill entire order");
         InterfaceTerminal.inventory.put("test:processed1", 10);    // give player some of the ware to be sold
         quantityToTrade = 10;
         quantityWare    = testWareP1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:processed1", 20, 0.0f, "testAccount1");
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - selling some of a ware with means to sell more");
         InterfaceTerminal.inventory.put("test:processed1", 10);    // give player some of the ware to be sold
         quantityToTrade = 8;
         quantityWare    = testWareP1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:processed1", quantityToTrade, 0.0f, "testAccount1");
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - selling ware with min price acceptable being NaN");
         InterfaceTerminal.inventory.put("test:material1", 10);    // give player some of a sellable ware
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", 1, Float.NaN, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - selling ware with min price acceptable being too high");
         InterfaceTerminal.inventory.put("test:material1", 10);    // give player some of a sellable ware
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", 1, 10.0f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - selling ware with min price acceptable being low");
         InterfaceTerminal.inventory.put("test:material1", 10);    // give player some of a sellable ware
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", quantityToTrade, 0.1f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         // Effects of surplus and price floors are not tested here
         // since those tests are more appropriate in Marketplace.getPrice().
         // sell() handles vending wares, ensuring stock is increased and accounts are paid
         // when appropriate, not managing the cascading effects of those actions.

         System.err.println("sell() - referencing ware using alias");
         InterfaceTerminal.inventory.put("test:material3", 10);    // give player a ware with an alias
         quantityToTrade = 2;
         quantityWare    = testWare3.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "mat3", quantityToTrade, 0.1f, "testAccount1");
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - null coordinates");
         InterfaceTerminal.inventory.put("test:material1", 10);
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "test:material1", quantityToTrade, 0.1f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 5) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 5");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - invalid coordinates");
         // note: coordinates below are invalid for the Terminal Interface, but not the Chat Interface
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10);
         Marketplace.sell(PLAYER_ID, new InterfaceCommand.Coordinates(1, 2, 3, 0), "test:material1", 5, 0.1f, "testAccount1");
         if (!testBAOS.toString().equals("No inventory was found" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - valid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryEast.put("test:material1", 10);
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, new InterfaceCommand.Coordinates(-1, 0, 0, 0), "test:material1", quantityToTrade, 0.1f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventoryEast.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryEast.get("test:material1") != 5) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryEast.get("test:material1") + " test:material1, should contain 5");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - zeroed coordinates");
         InterfaceTerminal.inventory.put("test:material1", 10);
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, new InterfaceCommand.Coordinates(0, 0, 0, 0), "test:material1", quantityToTrade, 0.1f, "testAccount1");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 5) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 5");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10);
         quantityWare    = testWare1.getQuantity();
         money           = testAccount4.getMoney();
         Marketplace.sell(InterfaceTerminal.getPlayerIDStatic(null), null, "test:material1", 3, 0.1f, "testAccount4");
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount4, money, null)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - empty username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10);
         quantityWare    = testWare1.getQuantity();
         money           = testAccount4.getMoney();
         Marketplace.sell(InterfaceTerminal.getPlayerIDStatic(""), null, "test:material1", 3, 0.1f, null);
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (accounts.containsKey("")) {
            System.err.println("   account was created when it should not have been");
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - different username");
         InterfaceTerminal.inventory.put("test:material1", 30);
         quantityToTrade = 20;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount3.getMoney();
         Marketplace.sell(InterfaceTerminal.getPlayerIDStatic("possibleID"), null, "test:material1", quantityToTrade, 0.1f, "testAccount3");
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount3, money + price, "possibleID")) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }
         resetTestEnvironment();

         // sell ware_id [quantity] [min_unit_price] [account_id]
         System.err.println("sell() - request: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(null);
         if (!testBAOS.toString().equals("/sell (<ware_id> | held) [<quantity> [min_unit_price] [account_id]]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sell() - request: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{});
         if (!testBAOS.toString().equals("/sell (<ware_id> | held) [<quantity> [min_unit_price] [account_id]]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sell() - request: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sell() - request: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{});
         if (!testBAOS.toString().equals("/sell (<ware_id> | held) [<quantity> [min_unit_price] [account_id]]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sell() - request: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "10", "0.1", "testAccount1", "excessArgument", "excessArgument", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: invalid ware ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"invalidWare", "10"});
         if (!testBAOS.toString().startsWith("error - ware not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: invalid price");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "10", "invalidPrice", "testAccount1"});
         if (!testBAOS.toString().startsWith("error - invalid price")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: invalid account ID");
         InterfaceTerminal.inventory.put("test:material1", 20);   // give wares to the player
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "10", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: minimum args");
         InterfaceTerminal.inventory.put("test:material1", 20);   // give wares to the player
         testWare1.setQuantity(256);
         quantityToTrade = 20;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: valid quantity");
         InterfaceTerminal.inventory.put("test:material1", 100);   // give wares to the player
         testWare1.setQuantity(256);
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade)});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: valid price");
         InterfaceTerminal.inventory.put("test:material1", 100);   // give wares to the player
         testWare1.setQuantity(256);
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "0.1"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: valid account ID");
         InterfaceTerminal.inventory.put("test:material1", 100); // give wares to the player
         testWare1.setQuantity(256);
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         testAccount2.setMoney(20.0f);
         money           = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "testAccount2"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount2, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: valid price and account ID");
         InterfaceTerminal.inventory.put("test:material1", 100); // give wares to the player
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "0.1", "testAccount2"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount2, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: referencing ware using alias");
         InterfaceTerminal.inventory.put("minecraft:material4", 100);
         quantityToTrade = 10;
         quantityWare    = testWare4.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, false);
         money           = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestSell(new String[]{"material4", String.valueOf(quantityToTrade), "0.1", "testAccount2"});
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount2, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: null coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{InterfaceTerminal.playername, null, "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: invalid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{InterfaceTerminal.playername, "invalidCoordinates", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: valid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryUp.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{InterfaceTerminal.playername, "up", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("Sold 10 test:material1 for $9.93" + System.lineSeparator())
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventoryUp.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryUp.get("test:material1") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryUp.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: zeroed coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{InterfaceTerminal.playername, "none", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("Sold 10 test:material1 for $9.93" + System.lineSeparator())
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{null, "none", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sell() - request: empty username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{"", "none", "test:material1", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sell() - request: different username");
         InterfaceTerminal.inventory.put("test:material1", 40); // give wares to the player
         quantityToTrade = 20;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = testAccount3.getMoney();
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"possibleID", "none", "test:material1", String.valueOf(quantityToTrade), "testAccount3"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount3, money + price, "possibleID")) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 20) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 20");
            errorFound = true;
         }
         if (!testBAOS.toString().startsWith("(for possibleID) Sold " + quantityToTrade + " test:material1 for $" + String.format("%.2f", price) + ", sent money to testAccount3" + System.lineSeparator())
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // The Terminal interface cannot test selling wares using Forge OreDictionary names
         // since its wares do not use Forge OreDictionary names.

         System.err.println("sell() - request: admin account");
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "$admin$"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium = Config.quanHigh[testWare1.getLevel()] - Config.quanMid[testWare1.getLevel()];

         System.err.println("sell() - negative prices, at -100% cost");
         quantityToTrade = 5;
         quantityWare    = Config.quanHigh[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (money <= playerAccount.getMoney()) {
            System.err.println("   account funds are " + playerAccount.getMoney() + " should be less than " + money);
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, at -50% cost");
         quantityToTrade = 5;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f);
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (money <= playerAccount.getMoney()) {
            System.err.println("   account funds are " + playerAccount.getMoney() + " should be less than " + money);
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, at no cost");
         quantityToTrade = 10;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f);
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "-3.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, at 50% cost");
         quantityToTrade = 15;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25f);
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "-3.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, at equilibrium");
         quantityToTrade = 10;
         quantityWare    = Config.quanMid[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", String.valueOf(quantityToTrade), "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         playerAccount.setMoney(30.0f);

         /*
         System.err.println("sell() - negative prices, overselling, (quad4 to quad4) overstocked to overstocked");
         quantityToTrade = 30;
         quantityWare    = Config.quanHigh[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         if (money <= playerAccount.getMoney()) {
            System.err.println("   account funds are " + playerAccount.getMoney() + " should be less than " + money);
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, overselling, (quad3 to quad4) above equilibrium to overstocked");
         quantityToTrade = 30;
         quantityWare    = Config.quanHigh[testWare1.getLevel()] - 20;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         if (money <= playerAccount.getMoney()) {
            System.err.println("   account funds are " + playerAccount.getMoney() + " should be less than " + money);
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, overselling, (quad3 to quad3) above equilibrium to above equilibrium");
         quantityToTrade = 89;
         quantityWare    = Config.quanHigh[testWare1.getLevel()] - 300;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         if (money <= playerAccount.getMoney()) {
            System.err.println("   account funds are " + playerAccount.getMoney() + " should be less than " + money);
            errorFound = true;
         }
         playerAccount.setMoney(203.0f);

         System.err.println("sell() - negative prices, overselling, (quad3 to quad4) above equilibrium to overstocked");
         quantityToTrade = 768;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, overselling, (quad2 to quad3) below equilibrium to above equilibrium");
         quantityToTrade = 525;
         quantityWare    = Config.quanMid[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         playerAccount.setMoney(203.0f);

         System.err.println("sell() - negative prices, overselling, (quad2 to quad4) below equilibrium to overstocked");
         quantityToTrade = 906;
         quantityWare    = Config.quanLow[testWare1.getLevel()] + 10;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         playerAccount.setMoney(30.0f);

         System.err.println("sell() - negative prices, overselling, (quad1 to quad3) understocked to above equilibrium");
         quantityToTrade = 673;
         quantityWare    = Config.quanLow[testWare1.getLevel()] - 10;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         playerAccount.setMoney(203.0f);

         System.err.println("sell() - negative prices, overselling, (quad1 to quad4) understocked to overstocked");
         quantityToTrade = 916;
         quantityWare    = Config.quanLow[testWare1.getLevel()] - 10;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "-5.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money + price, InterfaceTerminal.playername)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("      remaining money: " + playerAccount.getMoney());
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         */
         resetTestEnvironment();

         System.err.println("sell() - acceptable price, (quad1 to quad1), understocked to understocked");
         quantityToTrade = 64;
         quantityWare    = 64;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "2.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad1 to quad2), understocked to below equilibrium");
         quantityToTrade = 127;
         quantityWare    = 64;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "1.5"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad1 to quad3), understocked to above equilibrium");
         quantityToTrade = 575;
         quantityWare    = 64;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.5"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad1 to quad4), understocked to overstocked");
         quantityToTrade = 9999;
         quantityWare    = 64;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad2 to quad2), below equilibrium to below equilibrium");
         quantityToTrade = 32;
         quantityWare    = 191;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "1.25"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad2 to quad3), below equilibrium to above equilibrium");
         quantityToTrade = 448;
         quantityWare    = 191;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.5"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad2 to quad4), below equilibrium to overstocked equilibrium");
         quantityToTrade = 831;
         quantityWare    = 191;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.0001"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad3 to quad3), above equilibrium to above equilibrium");
         quantityToTrade = 192;
         quantityWare    = 639;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.25"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad3 to quad4), above equilibrium to overstocked");
         quantityToTrade = 9999;
         quantityWare    = 639;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();

         System.err.println("sell() - acceptable price, (quad4 to quad4), overstocked equilibrium to overstocked");
         quantityToTrade = 9999;
         quantityWare    = 1034;
         testWare1.setQuantity(quantityWare);
         InterfaceTerminal.inventory.put("test:material1", 9999);
         price           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade, false);
         money           = playerAccount.getMoney();

         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "9999", "0.0"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            System.err.println("        sold quantity: " + (testWare1.getQuantity() - quantityWare));
            System.err.println("    expected quantity: " + quantityToTrade);
            System.err.println("     final unit price: " + Marketplace.getPrice(PLAYER_ID, "test:material1", 0, false));
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
      }
      catch (Exception e) {
         System.err.println("sell() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests sellAll().
    *
    * @return whether sellAll() passed all test cases
    */
   private static boolean testSellAll() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // track changes to variables
      int   quantityToTrade1;
      int   quantityToTrade2;
      int   quantityToTrade3;
      int   quantityWare1;
      int   quantityWare2;
      int   quantityWare3;
      float price1;
      float price2;
      float price3;
      float money;

      try {
         System.err.println("sellAll() - null account ID");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         money            = playerAccount.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), null);

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check player's account
         if (testAccountFields(playerAccount, CommandEconomy.truncatePrice(money + price1 + price2), InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();


         System.err.println("sellAll() - empty account ID");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         money           = playerAccount.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "");

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check player's account
         if (testAccountFields(playerAccount, CommandEconomy.truncatePrice(money + price1 + price2), InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();


         System.err.println("sellAll() - invalid account ID");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "invalidAccount");

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2)) {
            errorFound = true;
         }
         // check player's account
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            // set up account appropriately for other tests
            playerAccount.setMoney(30.0f);
            playerAccount.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }
         // check test account
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            // set up account appropriately for other tests
            testAccount1.setMoney(10.0f);
            testAccount1.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }
         // check locked test account
         if (testAccount4.getMoney() != 6.0f) {
            errorFound = true;
            System.err.println("   testAccount4's money should be 6.0f, is " + testAccount4.getMoney());
         }
         // check inventory for first test ware
         if (!InterfaceTerminal.inventory.containsKey("test:material1")
             || InterfaceTerminal.inventory.get("test:material1") != 100) {
            errorFound = true;
            if (!InterfaceTerminal.inventory.containsKey("test:material1"))
               System.err.println("   test:material1 is missing when it should be in the inventory");
            else
               System.err.println("   test:material1 should have 100 stock, has " + InterfaceTerminal.inventory.get("test:material1"));
         }
         // check inventory for second test ware
         if (!InterfaceTerminal.inventory.containsKey("test:material3")
             || InterfaceTerminal.inventory.get("test:material3") != 10) {
            errorFound = true;
            if (!InterfaceTerminal.inventory.containsKey("test:material3"))
               System.err.println("   test:material3 is missing when it should be in the inventory");
            else
               System.err.println("   test:material3 should have 100 stock, has " + InterfaceTerminal.inventory.get("test:material3"));
         }

         // reset changed values for next test
         InterfaceTerminal.inventory.clear();


         System.err.println("sellAll() - using account without permission");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "testAccount4");

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2)) {
            errorFound = true;
         }
         // check player's account
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            // set up account appropriately for other tests
            playerAccount.setMoney(30.0f);
            playerAccount.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }
         // check test account
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            // set up account appropriately for other tests
            testAccount1.setMoney(10.0f);
            testAccount1.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }
         // check locked test account
         if (testAccount4.getMoney() != 6.0f) {
            errorFound = true;
            System.err.println("   testAccount4's money should be 6.0f, is " + testAccount4.getMoney());
         }
         // check inventory for first test ware
         if (!InterfaceTerminal.inventory.containsKey("test:material1")
             || InterfaceTerminal.inventory.get("test:material1") != 100) {
            errorFound = true;
            if (!InterfaceTerminal.inventory.containsKey("test:material1"))
               System.err.println("   test:material1 is missing when it should be in the inventory");
            else
               System.err.println("   test:material1 should have 100 stock, has " + InterfaceTerminal.inventory.get("test:material1"));
         }
         // check inventory for second test ware
         if (!InterfaceTerminal.inventory.containsKey("test:material3")
             || InterfaceTerminal.inventory.get("test:material3") != 10) {
            errorFound = true;
            if (!InterfaceTerminal.inventory.containsKey("test:material3"))
               System.err.println("   test:material3 is missing when it should be in the inventory");
            else
               System.err.println("   test:material3 should have 100 stock, has " + InterfaceTerminal.inventory.get("test:material3"));
         }

         // reset changed values for next test
         InterfaceTerminal.inventory.clear();


         System.err.println("sellAll() - with only valid wares");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 20;
         quantityWare2    = testWare3.getQuantity();
         quantityToTrade3 = 10;
         quantityWare3    = testWareP1.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         price3           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade3, false);
         money           = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade3);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "testAccount1");

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check third test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare3 + quantityToTrade3)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money + price1 + price2 + price3 - (float) 1e-4, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();


         System.err.println("sellAll() - with both valid and invalid wares");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 20;
         quantityWare2    = testWare3.getQuantity();
         quantityToTrade3 = 10;
         quantityWare3    = testWareP1.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         price3           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade3, false);
         money           = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade3);
         InterfaceTerminal.inventory.put("invalidWare", 10);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "testAccount1");

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check third test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare3 + quantityToTrade3)) {
            errorFound = true;
         }
         // check invalid ware
         if (!InterfaceTerminal.inventory.containsKey("invalidWare")
             || InterfaceTerminal.inventory.get("invalidWare") != 10) {
            errorFound = true;
            if (!InterfaceTerminal.inventory.containsKey("invalidWare"))
               System.err.println("   invalid ware is missing when it should be in the inventory");
            else
               System.err.println("   invalid ware should have 10 stock, has " + InterfaceTerminal.inventory.get("invalidWare"));
         }
         // check test account
         if (testAccountFields(testAccount1, money + price1 + price2 + price3 - (float) 1e-4, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - null coordinates");
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("minecraft:material4", quantityToTrade2);
         money           = testAccount1.getMoney();

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price1 + price2, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1") &&
             InterfaceTerminal.inventory.get("test:material1") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("minecraft:material4") &&
             InterfaceTerminal.inventory.get("minecraft:material4") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("minecraft:material4") + " minecraft:material4, should contain 0");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - invalid coordinates");
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("minecraft:material4", quantityToTrade2);

         // note: coordinates below are invalid for the Terminal Interface, but not the Chat Interface
         testBAOS.reset(); // clear buffer holding console output
         Marketplace.sellAll(PLAYER_ID, new InterfaceCommand.Coordinates(1, 2, 3, 0), getFormattedInventory(), "testAccount1");

         if (!testBAOS.toString().equals("No inventory was found" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - valid coordinates");
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventoryWest.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventoryWest.put("minecraft:material4", quantityToTrade2);
         money           = testAccount1.getMoney();

         Marketplace.sellAll(PLAYER_ID, new InterfaceCommand.Coordinates(1, 0, 0, 0), formatInventory(InterfaceTerminal.inventoryWest), "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price1 + price2, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (InterfaceTerminal.inventoryWest.containsKey("test:material1") &&
             InterfaceTerminal.inventoryWest.get("test:material1") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryWest.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }
         if (InterfaceTerminal.inventoryWest.containsKey("minecraft:material4") &&
             InterfaceTerminal.inventoryWest.get("minecraft:material4") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryWest.get("minecraft:material4") + " minecraft:material4, should contain 0");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - zeroed coordinates");
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("minecraft:material4", quantityToTrade2);
         money           = testAccount1.getMoney();

         Marketplace.sellAll(PLAYER_ID, new InterfaceCommand.Coordinates(0, 0, 0, 0), getFormattedInventory(), "testAccount1");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price1 + price2, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1") &&
             InterfaceTerminal.inventory.get("test:material1") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("minecraft:material4") &&
             InterfaceTerminal.inventory.get("minecraft:material4") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("minecraft:material4") + " minecraft:material4, should contain 0");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - null username");
         testBAOS.reset(); // clear buffer holding console output
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("minecraft:material4", quantityToTrade2);

         Marketplace.sellAll(InterfaceTerminal.getPlayerIDStatic(null), null, getFormattedInventory(), "testAccount4");

         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare2)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade1) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade1);
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("minecraft:material4")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("minecraft:material4") != quantityToTrade2) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("minecraft:material4") + " minecraft:material4, should contain " + quantityToTrade2);
            errorFound = true;
         }

         System.err.println("sellAll() - empty username");
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("minecraft:material4", quantityToTrade2);

         testBAOS.reset(); // clear buffer holding console output
         Marketplace.sellAll(InterfaceTerminal.getPlayerIDStatic(""), null, getFormattedInventory(), null);

         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare2)) {
            errorFound = true;
         }
         if (accounts.containsKey("")) {
            System.err.println("   account was created when it should not have been");
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade1) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade1);
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("minecraft:material4")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("minecraft:material4") != quantityToTrade2) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("minecraft:material4") + " minecraft:material4, should contain " + quantityToTrade2);
            errorFound = true;
         }

         System.err.println("sellAll() - different username");
         quantityToTrade1 = 5;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare4.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("minecraft:material4", quantityToTrade2);
         money           = testAccount3.getMoney();

         Marketplace.sellAll(InterfaceTerminal.getPlayerIDStatic("possibleID"), null, getFormattedInventory(), "testAccount3");

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount3, CommandEconomy.truncatePrice(money + price1 + price2), "possibleID")) {
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1") &&
             InterfaceTerminal.inventory.get("test:material1") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }
         if (InterfaceTerminal.inventory.containsKey("minecraft:material4") &&
             InterfaceTerminal.inventory.get("minecraft:material4") != 0) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("minecraft:material4") + " minecraft:material4, should contain 0");
            errorFound = true;
         }
         resetTestEnvironment();

         // sellall [account_id]
         System.err.println("sellAll() - request: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(null);
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sellAll() - request: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sellAll() - request: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("sellAll() - request: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "excessArgument", "excessArgument", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sellAll() - request: invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         // give wares to the player so there is something to sell
         InterfaceTerminal.inventory.put("test:material1", 100);
         InterfaceTerminal.serviceRequestSellAll(new String[]{"invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: minimum args");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3", quantityToTrade2);
         money           = playerAccount.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(playerAccount, CommandEconomy.truncatePrice(money + price1 + price2), InterfaceTerminal.playername)) {
            errorFound = true;
         }
         // check printed statement
         if (!testBAOS.toString().startsWith("Sold " + (quantityToTrade1 + quantityToTrade2) + " items for $" + String.format("%.2f", price1 + price2))) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: valid account ID");
         quantityToTrade1 = 10;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3", quantityToTrade2);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{"testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money + price1 + price2, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         // check printed statement
         if (!testBAOS.toString().startsWith("Sold " + (quantityToTrade1 + quantityToTrade2) + " items for $" + String.format("%.2f", price1 + price2))) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: null coordinates");
         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.inventory.put("test:material3",  10);

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, null, "testAccount1"});

         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sellAll() - request: invalid coordinates");
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.inventory.put("test:material3",  10);

         testBAOS.reset(); // clear buffer holding console output
         // give wares to the player
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "invalidCoordinates", "testAccount1"});

         if (!testBAOS.toString().startsWith("error - invalid inventory direction")
         ) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: valid coordinates");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         InterfaceTerminal.inventoryDown.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventoryDown.put("test:material3", quantityToTrade2);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         // give wares to the player
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "down", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, CommandEconomy.truncatePrice(money + price1 + price2), InterfaceTerminal.playername)) {
            errorFound = true;
         }
         // check printed statement
         if (!testBAOS.toString().startsWith("Sold " + (quantityToTrade1 + quantityToTrade2) + " items for $" + String.format("%.2f", price1 + price2))) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: zeroed coordinates");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3", quantityToTrade2);
         money           = testAccount1.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
            resetTestEnvironment();
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
            resetTestEnvironment();
         }
         // check test account
         if (testAccountFields(testAccount1, CommandEconomy.truncatePrice(money + price1 + price2), InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }
         // check printed statement
         if (!testBAOS.toString().startsWith("Sold " + (quantityToTrade1 + quantityToTrade2) + " items for $" + String.format("%.2f", price1 + price2))) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sellAll() - request: null username");
         testBAOS.reset(); // clear buffer holding console output
         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.inventory.put("test:material3",  10);

         InterfaceTerminal.serviceRequestSellAll(new String[]{null, "none", "testAccount1"});

         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sellAll() - request: empty username");
         testBAOS.reset(); // clear buffer holding console output
         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.inventory.put("test:material3",  10);

         InterfaceTerminal.serviceRequestSellAll(new String[]{"", "none", "testAccount1"});

         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: different username");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:material3", quantityToTrade2, false);
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3", quantityToTrade2);
         money           = testAccount3.getMoney();

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{"possibleID", "none", "testAccount3"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount3, CommandEconomy.truncatePrice(money + price1 + price2), "possibleID")) {
            errorFound = true;
         }
         // check printed statement
         if (!testBAOS.toString().startsWith("(for possibleID) Sold " + (quantityToTrade1 + quantityToTrade2) + " items for $" + String.format("%.2f", price1 + price2) + ", sent money to testAccount3")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("sellAll() - request: selling no items");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("sellAll() - request: admin account");
         quantityToTrade1 = 10;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 10;
         quantityWare2    = testWare3.getQuantity();
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3", quantityToTrade2);

         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{"$admin$"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium1 = Config.quanHigh[testWare1.getLevel()] - Config.quanMid[testWare1.getLevel()];
         float quanCeilingFromEquilibrium2 = Config.quanHigh[testWareP1.getLevel()] - Config.quanMid[testWareP1.getLevel()];

         System.err.println("sellAll() - negative prices, at -100% cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanHigh[testWare1.getLevel()];
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanHigh[testWareP1.getLevel()];
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade2, false);
         money            = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade2);

         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         testAccount1.setMoney(30.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("sellAll() - negative prices, at -50% cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium1 * 0.75f) - 1;
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanMid[testWareP1.getLevel()] + (int) (quanCeilingFromEquilibrium2 * 0.75f) - 1;
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade2, false);
         money            = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade2);

         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("sellAll() - negative prices, at no cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium1 * 0.50f) - 1;
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanMid[testWareP1.getLevel()] + (int) (quanCeilingFromEquilibrium2 * 0.50f) - 1;
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade2, false);
         money            = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade2);

         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("sellAll() - negative prices, at 50% cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanMid[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium1 * 0.25f) - 1;
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanMid[testWareP1.getLevel()] + (int) (quanCeilingFromEquilibrium2 * 0.25f) - 1;
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade2, false);
         money            = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade2);

         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money + price1 + price2, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         testAccount1.setMoney(10.0f);
         InterfaceTerminal.inventory.clear();

         System.err.println("sellAll() - negative prices, at equilibrium");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanMid[testWare1.getLevel()];
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanMid[testWareP1.getLevel()];
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, "test:material1", quantityToTrade1, false);
         price2           = Marketplace.getPrice(PLAYER_ID, "test:processed1", quantityToTrade2, false);
         money            = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade2);

         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         // check test results
         // check first test ware
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare1 + quantityToTrade1)) {
            errorFound = true;
         }
         // check second test ware
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare2 + quantityToTrade2)) {
            errorFound = true;
         }
         // check test account
         if (testAccountFields(testAccount1, money + price1 + price2 + (float) 1e-5 - (float) 8e-6, InterfaceTerminal.playername)) {
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("sellAll() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests saveWares() and loadWares().
    *
    * @return whether saveWares() and loadWares() passed all test cases
    */
   private static boolean testWareIO() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // add new wares to be tested
      // numerical IDs (such as 1.7.10's IDs)
      wares.put("17", new WareMaterial("17", "wood", 0.5f, 256, (byte) 0));
      wareAliasTranslations.put("wood", "17");
      StringBuilder json = new StringBuilder(wares.get("17").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("17", json);
      waresChangedSinceLastSave.add("17");
      wares.put("58", new WareCrafted(new String[]{"17"}, "58", "crafting_table", 256, 1, (byte) 0));
      wareAliasTranslations.put("crafting_table", "58");
      json = new StringBuilder(wares.get("58").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("58", json);

      // prepare to check wares
      Ware testWare; // holds ware currently being checked

      System.err.println("testWareIO() - saving to file");
      Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt"; // save wares to test file, don't overwrite any existing save

      // try to save to the test file
      try {
         Marketplace.saveWares();
      }
      catch (Exception e) {
         System.err.println("   saveWares() should not throw any exception, but it did\n   was saving test wares");
         e.printStackTrace();
         return false;
      }

      System.err.println("testWareIO() - loading from file");
      // try to load the test file
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         System.err.println("testWareIO() - loadWares() should not throw any exception, but it did\n   was loading test wares");
         e.printStackTrace();
         return false;
      }

      // check whether wares match known values they were set to
      try {
         // for fixing prices if errors are found
         Field testPriceBase = Ware.class.getDeclaredField("priceBase");
         testPriceBase.setAccessible(true);

         testWare = wares.get("test:material1");
         if (testWareFields(testWare, WareMaterial.class, "", (byte) 0, 1.0f, 256)) {
            System.err.println("   test:material1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 0);
            testPriceBase.setFloat(testWare, 1.0f);
            testWare.setQuantity(256);
         }

         testWare = wares.get("test:material2");
         if (testWareFields(testWare, WareMaterial.class, "", (byte) 1, 27.6f, 5)) {
            System.err.println("   test:material2 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 1);
            testPriceBase.setFloat(testWare, 27.6f);
            testWare.setQuantity(5);
         }

         testWare = wares.get("test:material3");
         if (testWareFields(testWare, WareMaterial.class, "mat3", (byte) 2, 4.0f, 64)) {
            System.err.println("   test:material3 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 2);
            testPriceBase.setFloat(testWare, 4.0f);
            testWare.setQuantity(64);
         }
         if (!wareAliasTranslations.containsKey("mat3") ||
             !wareAliasTranslations.get("mat3").equals("test:material3")) {
            System.err.println("   test:material3 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("minecraft:material4");
         if (testWareFields(testWare, WareMaterial.class, "material4", (byte) 3, 8.0f, 32)) {
            System.err.println("   minecraft:material4 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("material4");
            testWare.setLevel((byte) 3);
            testPriceBase.setFloat(testWare, 8.0f);
            testWare.setQuantity(32);
         }
         if (!wareAliasTranslations.containsKey("material4") ||
             !wareAliasTranslations.get("material4").equals("minecraft:material4")) {
            System.err.println("   minecraft:material4 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:untradeable1");
         if (testWareFields(testWare, WareUntradeable.class, "notrade1", (byte) 0, 16.0f, Integer.MAX_VALUE)) {
            System.err.println("   test:untradeable1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("notrade1");
            testWare.setLevel((byte) 0);
            testPriceBase.setFloat(testWare, 16.0f);
            testWare.setQuantity(0);
         }
         if (!wareAliasTranslations.containsKey("notrade1") ||
             !wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
            System.err.println("   test:untradeable1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:processed1");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 4, 1.1f, 16)) {
            System.err.println("   test:processed1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 4);
            testPriceBase.setFloat(testWare, 1.1f);
            testWare.setQuantity(16);
         }

         testWare = wares.get("test:processed2");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 5, 14.3f, 8)) {
            System.err.println("   test:processed2 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 5);
            testPriceBase.setFloat(testWare, 14.3f);
            testWare.setQuantity(8);
         }

         testWare = wares.get("test:processed3");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 3, 1.76f, 32)) {
            System.err.println("   test:processed3 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 3);
            testPriceBase.setFloat(testWare, 1.76f);
            testWare.setQuantity(32);
         }

         testWare = wares.get("test:crafted1");
         if (testWareFields(testWare, WareCrafted.class, "craft1", (byte) 1, 19.2f, 128)) {
            System.err.println("   test:crafted1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("craft1");
            testWare.setLevel((byte) 1);
            testPriceBase.setFloat(testWare, 19.2f);
            testWare.setQuantity(128);
         }
         if (!wareAliasTranslations.containsKey("craft1") ||
             !wareAliasTranslations.get("craft1").equals("test:crafted1")) {
            System.err.println("   test:crafted1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:crafted2");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 2, 24.24f, 64)) {
            System.err.println("   test:crafted2 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 2);
            testPriceBase.setFloat(testWare, 24.24f);
            testWare.setQuantity(64);
         }

         testWare = wares.get("test:crafted3");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 3, 2.4f, 32)) {
            System.err.println("   test:crafted3 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 3);
            testPriceBase.setFloat(testWare, 2.2f);
            testWare.setQuantity(32);
         }

         // ore names and alternate aliases
         if (!wareAliasTranslations.containsKey("#testName")) {
            errorFound = true;
            System.err.println("   #testName does not exist, should be mapped to test:material2");
         } else {
            if (!wareAliasTranslations.get("#testName").equals("test:material2")) {
               errorFound = true;
               System.err.println("   #testName's ware ID is " + wareAliasTranslations.get("#testName") + ", should be test:material2");
            }
         }
         if (!wareAliasTranslations.containsKey("testAlternateAlias")) {
            errorFound = true;
            System.err.println("   testAlternateAlias does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("testAlternateAlias").equals("test:material1")) {
               errorFound = true;
               System.err.println("   testAlternateAlias's ware ID is " + wareAliasTranslations.get("testAlternateAlias") + ", should be test:material1");
            }
         }

         // numerical IDs
         testWare = wares.get("17");
         if (testWareFields(testWare, WareMaterial.class, "wood", (byte) 0, 0.5f, 256)) {
            System.err.println("   ware #17 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("wood") ||
             !wareAliasTranslations.get("wood").equals("17")) {
            System.err.println("   ware #17 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("58");
         if (testWareFields(testWare, WareCrafted.class, "crafting_table", (byte) 0, 0.6f, 256)) {
            System.err.println("   ware #58 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("crafting_table") ||
             !wareAliasTranslations.get("crafting_table").equals("58")) {
            System.err.println("   ware #58 did not have expected alias");
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("   fatal error while checking wares: " + e);
         return false;
      }

      System.err.println("testWareIO() - saving after removing a ware and creating new ones");

      // remove known ware
      json = wareEntries.get("test:material2");
      waresLoadOrder.remove(json);
      wares.remove("test:material2");
      wareEntries.remove("test:material2");
      waresChangedSinceLastSave.remove("test:material2");

      // create new wares
      wares.put("test:newWare1", new WareMaterial("test:newWare1", "brandNewButUnexciting", 100.0f, 10, (byte) 4));
      wareAliasTranslations.put("brandNewButUnexciting", "test:newWare1");
      wares.put("test:newWare2", new WareCrafted(new String[]{"test:newWare1"}, "test:newWare2", "", 1, 1, (byte) 3));
      wares.put("test:untradeable2", new WareUntradeable(new String[]{"test:material1", "test:material1", "test:untradeable1"}, "test:untradeable2", "notrade2", 2));
      wareAliasTranslations.put("notrade2", "test:untradeable2");

      json = new StringBuilder(wares.get("test:newWare1").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:newWare1", json);
      waresChangedSinceLastSave.add("test:newWare1");
      json = new StringBuilder(wares.get("test:newWare2").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:newWare2", json);
      json = new StringBuilder(wares.get("test:untradeable2").toJSON() + "\n");
      waresLoadOrder.add(json);
      wareEntries.put("test:untradeable2", json);

      if (wares.size() != 15)
         System.err.println("   warning: total number of wares is expected to be 15, but is " + wares.size());
      if (wareAliasTranslations.size() != 10)
         System.err.println("   warning: total number of ware aliases is expected to be 10, but is " + wareAliasTranslations.size());

      // try to save to the test file
      try {
         Marketplace.saveWares();
      }
      catch (Exception e) {
         System.err.println("   saveWares() should not throw any exception, but it did\n   was saving changed test wares");
         e.printStackTrace();
         return false;
      }

      System.err.println("testWareIO() - loading from file");
      // try to load the test file
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         System.err.println("testWareIO() - loadWares() should not throw any exception, but it did\n   was loading changed test wares");
         e.printStackTrace();
         return false;
      }

      // check whether wares match known values they were set to
      try {
         testWare = wares.get("test:material1");
         if (testWareFields(testWare, WareMaterial.class, "", (byte) 0, 1.0f, 256)) {
            System.err.println("   test:material1 did not match expected values");
            errorFound = true;
         }

         // check whether the deleted ware exists
         if (wares.containsKey("test:material2")) {
            errorFound = true;
            System.err.println("   test:material2 was deleted, but still exists");
         }

         testWare = wares.get("test:material3");
         if (testWareFields(testWare, WareMaterial.class, "mat3", (byte) 2, 4.0f, 64)) {
            System.err.println("   test:material3 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("mat3") ||
             !wareAliasTranslations.get("mat3").equals("test:material3")) {
            System.err.println("   test:material3 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("minecraft:material4");
         if (testWareFields(testWare, WareMaterial.class, "material4", (byte) 3, 8.0f, 32)) {
            System.err.println("   minecraft:material4 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("material4") ||
             !wareAliasTranslations.get("material4").equals("minecraft:material4")) {
            System.err.println("   minecraft:material4 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:untradeable1");
         if (testWareFields(testWare, WareUntradeable.class, "notrade1", (byte) 0, 16.0f, Integer.MAX_VALUE)) {
            System.err.println("   test:untradeable1 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("notrade1") ||
             !wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
            System.err.println("   test:untradeable1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:processed1");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 4, 1.1f, 16)) {
            System.err.println("   test:processed1 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:processed2");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 5, 14.3f, 8)) {
            System.err.println("   test:processed2 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:processed3");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 3, 1.76f, 32)) {
            System.err.println("   test:processed3 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:crafted1");
         if (testWareFields(testWare, WareCrafted.class, "craft1", (byte) 1, 19.2f, 128)) {
            System.err.println("   test:crafted1 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("craft1") ||
             !wareAliasTranslations.get("craft1").equals("test:crafted1")) {
            System.err.println("   test:crafted1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:crafted2");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 2, 24.24f, 64)) {
            System.err.println("   test:crafted2 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:crafted3");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 3, 2.4f, 32)) {
            System.err.println("   test:crafted3 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:newWare1");
         if (testWareFields(testWare, WareMaterial.class, "brandNewButUnexciting", (byte) 4, 100.0f, 10)) {
            System.err.println("   test:newWare1 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("brandNewButUnexciting") ||
             !wareAliasTranslations.get("brandNewButUnexciting").equals("test:newWare1")) {
            System.err.println("   test:newWare1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:newWare2");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 3, 120.0f, 1)) {
            System.err.println("   test:newWare2 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:untradeable2");
         if (testWareFields(testWare, WareUntradeable.class, "notrade2", (byte) 0, 9.0f, Integer.MAX_VALUE)) {
            System.err.println("   test:untradeable2 did not match expected values");
            errorFound = true;
         }

         // ore names and alternate aliases
         if (!wareAliasTranslations.containsKey("#testName")) {
            errorFound = true;
            System.err.println("   #testName does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("#testName").equals("test:material1")) {
               errorFound = true;
               System.err.println("   #testName's ware ID is " + wareAliasTranslations.get("#testName") + ", should be test:material1");
            }
         }
         if (!wareAliasTranslations.containsKey("testAlternateAlias")) {
            errorFound = true;
            System.err.println("   testAlternateAlias does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("testAlternateAlias").equals("test:material1")) {
               errorFound = true;
               System.err.println("   testAlternateAlias's ware ID is " + wareAliasTranslations.get("testAlternateAlias") + ", should be test:material1");
            }
         }

         // numerical IDs
         testWare = wares.get("17");
         if (testWareFields(testWare, WareMaterial.class, "wood", (byte) 0, 0.5f, 256)) {
            System.err.println("   ware #17 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("wood") ||
             !wareAliasTranslations.get("wood").equals("17")) {
            System.err.println("   ware #17 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("58");
         if (testWareFields(testWare, WareCrafted.class, "crafting_table", (byte) 0, 0.6f, 256)) {
            System.err.println("   ware #58 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("crafting_table") ||
             !wareAliasTranslations.get("crafting_table").equals("58")) {
            System.err.println("   ware #58 did not have expected alias");
            errorFound = true;
         }
      }
      catch (Exception e) {
         System.err.println("   fatal error while checking changed wares: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account.saveAccounts() and Account.loadAccounts().
    *
    * @return whether Account.saveAccounts() and Account.loadAccounts() passed all test cases
    */
   private static boolean testAccountIO() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // make accounts start with some money
      Config.accountStartingMoney = 10.0f;

      // test handling missing file
      System.err.println("testAccountIO() - handling of missing file");
      Config.filenameAccounts = "no file here";
      testBAOS.reset(); // clear buffer holding console output
      int numAccounts = accounts.size();
      try {
         Account.loadAccounts();
      }
      catch (Exception e) {
         System.err.println("   Account.loadAccounts() should not throw any exception, but it did\n   was testing for handling missing file");
         e.printStackTrace();
         return false;
      }

      // check handling of missing file
      File fileAccounts = new File(Config.filenameAccounts);
      // check local file
      if (fileAccounts.exists()){
         errorFound = true;
         System.err.println("   \"no file here\" file should not exist in local/world directory");
      }
      // check global file
      fileAccounts = new File("config" + File.separator + Config.filenameAccounts);
      if (fileAccounts.exists()){
         errorFound = true;
         System.err.println("   \"no file here\" file should not exist in global/config directory");
      }
      if (numAccounts != accounts.size()) {
         System.err.println("   unexpected number of accounts: " + accounts.size() + ", should be " + numAccounts);
         errorFound = true;
      }
      Config.filenameAccounts = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";

      System.err.println("testAccountIO() - saving to file");
      // try to save to the test file
      try {
         Account.saveAccounts();
      }
      catch (Exception e) {
         System.err.println("   Account.saveAccounts() should not throw any exception, but it did\n   was saving test accounts");
         e.printStackTrace();
         return false;
      }

      System.err.println("testAccountIO() - loading from file");
      // try to load the test file
      try {
         Account.loadAccounts();
      }
      catch (Exception e) {
         System.err.println("testAccountIO() - Account.loadAccounts() should not throw any exception, but it did\n   was loading test accounts");
         e.printStackTrace();
         return false;
      }

      // check whether accounts match known values they were set to
      try {
         // test properties
         testAccount1  = accounts.get("testAccount1");
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   testAccount1 had unexpected values");
         }
         // test permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccount1 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         testAccount2  = accounts.get("testAccount2");
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   testAccount2 had unexpected values");
         }
         // test permissions
         if (testAccount2.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccount2 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         testAccount3  = accounts.get("testAccount3");
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            errorFound = true;
            System.err.println("   testAccount3 had unexpected values");
         }
         // test permissions
         if (testAccount3.hasAccess(PLAYER_ID)) {
            errorFound = true;
            System.err.println("   testAccount3 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }

         Account testAccount4  = accounts.get("testAccount4");
         // test properties
         if (testAccount4.getMoney() != 6.0f) {
            errorFound = true;
            System.err.println("   testAccount4's money should be 6.0f, is " + testAccount4.getMoney());
         }
         // test permissions
         if (testAccount4.hasAccess(PLAYER_ID)) {
            errorFound = true;
            System.err.println("   testAccount4 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccount4.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccount4 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         playerAccount  = accounts.get(InterfaceTerminal.playername);
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   playerAccount had unexpected values");
         }
         // test permissions
         if (playerAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   playerAccount allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         adminAccount  = accounts.get("$admin$");
         if (testAccountFields(adminAccount, Float.POSITIVE_INFINITY, "$admin$")) {
            errorFound = true;
            System.err.println("   adminAccount had unexpected values");
         }
         // test permissions
         if (adminAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   playerAccount allows access to an arbitrary player ID when it shouldn't");
         }

         if (accounts.size() != 6)
            System.err.println("   warning: total number of accounts is expected to be 6, but is " + accounts.size());
      }
      catch (Exception e) {
         System.err.println("   fatal error while checking accounts: " + e);
         return false;
      }

      System.err.println("testAccountIO() - saving and loading after removing an account and creating new ones");

      // remove known account
      accounts.remove("testAccount3");

      // create new accounts
      Account.makeAccount("arbitraryAccountID", InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID"));
      Account.makeAccount("arbitraryID", InterfaceTerminal.getPlayerIDStatic("arbitraryID"), 42.0f);

      // give multiple users access to one of the accounts
      Account testAccountNew2 = accounts.get("arbitraryID");
      testAccountNew2.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID1"), null);
      testAccountNew2.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID2"), null);
      testAccountNew2.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID3"), null);

      if (accounts.size() != 7)
         System.err.println("   warning: total number of accounts is expected to be 7, but is " + accounts.size());

      // try to save to the test file
      try {
         Account.saveAccounts();
      }
      catch (Exception e) {
         System.err.println("   Account.saveAccounts() should not throw any exception, but it did\n   was saving changed test accounts");
         e.printStackTrace();
         return false;
      }

      System.err.println("testAccountIO() - loading from file");
      // try to load the test file
      try {
         Account.loadAccounts();
      }
      catch (Exception e) {
         System.err.println("testAccountIO() - Account.loadAccounts() should not throw any exception, but it did\n   was loading changed test accounts");
         e.printStackTrace();
         return false;
      }

      // check whether accounts match known values they were set to
      try {
         // test properties
         testAccount1  = accounts.get("testAccount1");
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   testAccount1 had unexpected values");
         }
         // test permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccount1 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         testAccount2  = accounts.get("testAccount2");
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   testAccount2 had unexpected values");
            // set up accounts appropriately for other tests
            testAccount2.setMoney(20.0f);
            testAccount2.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }
         // test permissions
         if (testAccount2.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccount2 allows access to an arbitrary player ID when it shouldn't");
         }

         // check whether the deleted account exists
         if (accounts.containsKey("testAccount3")) {
            errorFound = true;
            System.err.println("   testAccount3 was deleted, but still exists");
         }

         // test properties
         testAccount4 = accounts.get("testAccount4");
         if (testAccount4.getMoney() != 6.0f) {
            errorFound = true;
            System.err.println("   testAccount4's money should be 6.0f, is " + testAccount4.getMoney());
         }
         // test permissions
         if (testAccount4.hasAccess(PLAYER_ID)) {
            errorFound = true;
            System.err.println("   testAccount4 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccount4.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccount4 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         playerAccount = accounts.get(InterfaceTerminal.playername);
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            System.err.println("   playerAccount had unexpected values");
         }
         // test permissions
         if (playerAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   playerAccount allows access to an arbitrary player ID when it shouldn't");
         }

         Account testAccountNew1 = accounts.get("arbitraryAccountID");
         // test properties
         if (testAccountFields(testAccountNew1, 10.0f, "arbitraryPlayerID")) {
            errorFound = true;
            System.err.println("   testAccountNew1 had unexpected values");
         }
         // test permissions
         if (testAccountNew1.hasAccess(PLAYER_ID)) {
            errorFound = true;
            System.err.println("   testAccountNew1 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccountNew1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccountNew1 allows access to an arbitrary player ID when it shouldn't");
         }

         testAccountNew2 = accounts.get("arbitraryID");
         // test properties
         if (testAccountFields(testAccountNew2, 42.0f, "arbitraryID")) {
            errorFound = true;
            System.err.println("   testAccountNew2 had unexpected values");
         }
         // test permissions
         if (testAccountNew2.hasAccess(PLAYER_ID)) {
            errorFound = true;
            System.err.println("   testAccountNew2 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            System.err.println("   testAccountNew2 allows access to an arbitrary player ID when it shouldn't");
         }
         if (!testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID1")) ||
             !testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID2")) ||
             !testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID3"))) {
            errorFound = true;
            System.err.println("   testAccountNew2 does not allow shared access when it should");
         }

         // check account creation counts
         if (Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID")) != 1) {
            errorFound = true;
            System.err.println("   arbitraryPlayerID's account creation count is " + Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID")) + ", should be 1");
         }
         if (Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryID")) != 0) {
            errorFound = true;
            System.err.println("   arbitraryID's account creation count is " + Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryID")) + ", should be 0");
         }

         if (accounts.size() != 7)
            System.err.println("   warning: total number of accounts is expected to be 7, but is " + accounts.size());
      }
      catch (Exception e) {
         System.err.println("   fatal error while checking changed accounts: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests various serviceRequest() functions.
    *
    * @return whether all tested serviceRequest() functions passed all of their test cases
    */
   private static boolean testServiceRequests() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // track changes to variables
      int   quantityToTrade;
      float price1;
      float price2;

      try {
         // check ware_id [quantity]
         System.err.println("serviceRequests() - check: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(null);
         if (!testBAOS.toString().equals("/check (<ware_id> | held) [quantity]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{});
         if (!testBAOS.toString().equals("/check (<ware_id> | held) [quantity]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{});
         if (!testBAOS.toString().equals("/check (<ware_id> | held) [quantity]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"test:material1", "10", "excessArgument", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - check: invalid ware ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"invalidWareID"});
         if (!testBAOS.toString().equals("error - ware not found: invalidWareID" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - check: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"test:material1", "invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - check: minimum args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"test:material1"});
         if (!testBAOS.toString().equals("test:material1: $1.00, 256" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: valid quantity");
         quantityToTrade = 10;
         price1          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, true);
         price2          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, false);
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"minecraft:material4", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for " + quantityToTrade + ": Buy - $" + String.format("%.2f", price1) + " | Sell - $" + String.format("%.2f", price2) + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: referencing ware using alias");
         quantityToTrade = 10;
         price1          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, true);
         price2          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, false);
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"material4", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for " + quantityToTrade + ": Buy - $" + String.format("%.2f", price1) + " | Sell - $" + String.format("%.2f", price2) + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{null, "material4", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: empty username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"", "material4", "10"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: different username");
         quantityToTrade = 10;
         price1          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, true);
         price2          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, false);
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"possibleID", "material4", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().equals("(for possibleID) material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "(for possibleID)    for " + quantityToTrade + ": Buy - $" + String.format("%.2f", price1) + " | Sell - $" + String.format("%.2f", price2) + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: command block variant without permissions");
         quantityToTrade = 1;
         price1          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, true);
         price2          = Marketplace.getPrice(PLAYER_ID, "minecraft:material4", quantityToTrade, false);
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "notAnOp";
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"notAnOp", "material4", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().startsWith("material4 (minecraft:material4): $8.00, 32")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: checking for others without permissions");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"possibleID", "material4", String.valueOf(quantityToTrade)});
         if (!testBAOS.toString().startsWith("You do not have permission to use this command for other players")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         InterfaceTerminal.playername = playernameOrig;

         System.err.println("serviceRequests() - check: existing Forge OreDictionary Name");
         wareAliasTranslations.put("#testName", "test:material2");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"#testName"});
         if (!testBAOS.toString().equals("test:material2: $55.20, 5" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - check: nonexistent Forge OreDictionary Name");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"#invalidName"});
         if (!testBAOS.toString().equals("error - ware not found: #invalidName" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // money [account_id]
         System.err.println("serviceRequests() - money: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(null);
         if (!testBAOS.toString().equals("Your account: $30.00" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - money: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{});
         if (!testBAOS.toString().equals("Your account: $30.00" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - money: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{InterfaceTerminal.playername, "testAccount2", "excessArgument"});
         if (!testBAOS.toString().startsWith("testAccount2: $")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: blank account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found: invalidAccount")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: minimum args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{});
         if (!testBAOS.toString().startsWith("Your account: $")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: valid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"testAccount2"});
         if (!testBAOS.toString().startsWith("testAccount2: $")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{null, "testAccount2"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: empty username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"", "testAccount2"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - money: different username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"possibleID", "testAccount3"});
         if (!testBAOS.toString().startsWith("(for possibleID) testAccount3: $")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // send quantity recipient_account_id [sender_account_id]
         System.err.println("serviceRequests() - send: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(null);
         if (!testBAOS.toString().equals("/send <quantity> <recipient_account_id> [sender_account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - send: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{});
         if (!testBAOS.toString().equals("/send <quantity> <recipient_account_id> [sender_account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - send: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - send: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"10.0"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - send: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{InterfaceTerminal.playername, "10.0", "testAccount1", "testAccount2", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"invalidQuantity", "testAccount1"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: invalid sender ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"10.0", "testAccount1", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found: invalidAccount")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: invalid recipient ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"10.0", "invalidAccount", "testAccount2"});
         if (!testBAOS.toString().startsWith("error - account not found: invalidAccount")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: minimum args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"10.0", "testAccount1"});
         if (!testBAOS.toString().startsWith("Successfully transferred $10")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: valid sender ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"5.0", "testAccount1", "testAccount2"});
         if (!testBAOS.toString().startsWith("Successfully transferred $5")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: null username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{null, "5.0", "testAccount1", "testAccount2"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: empty username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"", "5.0", "testAccount1", "testAccount2"});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - send: different username");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"possibleID", "5.0", "testAccount1", "testAccount3"});
         if (!testBAOS.toString().startsWith("(for possibleID) Successfully transferred $5")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // grantAccess player_id account_id
         System.err.println("serviceRequests() - grantAccess: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(null);
         if (!testBAOS.toString().equals("/grantAccess <player_name> <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - grantAccess: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{});
         if (!testBAOS.toString().equals("/grantAccess <player_name> <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - grantAccess: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"", ""});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - grantAccess: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - grantAccess: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID", "testAccount1", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - grantAccess: invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - grantAccess: valid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID", "testAccount1"});
         if (!testBAOS.toString().startsWith("possibleID may now access testAccount1")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // revokeAccess player_id account_id
         System.err.println("serviceRequests() - revokeAccess: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(null);
         if (!testBAOS.toString().equals("/revokeAccess <player_name> <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - revokeAccess: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{});
         if (!testBAOS.toString().equals("/revokeAccess <player_name> <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - revokeAccess: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"", ""});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - revokeAccess: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - revokeAccess: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID", "testAccount1", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - revokeAccess: invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - revokeAccess: valid account ID");
         testAccount1.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("possibleID"), null);
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID", "testAccount1"});
         if (!testBAOS.toString().startsWith("possibleID may no longer access testAccount1")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // save
         // don't overwrite user saves
         Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt";
         Config.filenameAccounts = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";

         System.err.println("serviceRequests() - save: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(null);
         if (!testBAOS.toString().equals("Saved the economy" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - save: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{});
         if (!testBAOS.toString().equals("Saved the economy" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - save: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{""});
         if (!testBAOS.toString().equals("Saved the economy" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - save: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{"excessArgument"});
         if (!testBAOS.toString().equals("Saved the economy" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - save: expected usage");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{});
         if (!testBAOS.toString().equals("Saved the economy" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // reload (config || wares || accounts || all)
         System.err.println("serviceRequests() - reload: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(null);
         if (!testBAOS.toString().equals("/commandeconomy reload (config | wares | accounts | all)" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{});
         if (!testBAOS.toString().equals("/commandeconomy reload (config | wares | accounts | all)" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{""});
         if (!testBAOS.toString().startsWith("error - must provide instructions for reload")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{});
         if (!testBAOS.toString().equals("/commandeconomy reload (config | wares | accounts | all)" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"wares", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: invalid arg");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"invalidArgument"});
         if (!testBAOS.toString().startsWith("error - invalid argument")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: config");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         if (!testBAOS.toString().equals("Reloaded config." + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: wares");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"wares"});
         if (!testBAOS.toString().equals("Reloaded wares." + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: accounts");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"accounts"});
         if (!testBAOS.toString().equals("Reloaded accounts." + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - reload: total reload");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"all"});
         if (!testBAOS.toString().equals("Reloaded config, wares, and accounts." + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // for paranoia's sake, reload the testing environment
         // to avoid any possibility of interfering with other tests
         resetTestEnvironment();

         // add quantity [account_id]
         System.err.println("serviceRequests() - add: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(null);
         if (!testBAOS.toString().equals("/add <quantity> [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - add: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{});
         if (!testBAOS.toString().equals("/add <quantity> [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - add: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - add: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{});
         if (!testBAOS.toString().equals("/add <quantity> [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - add: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0", "testAccount2", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - add: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{"invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - add: invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - add: minimum args");
         playerAccount.setMoney(30.0f);
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0"});
         if (testAccountFields(playerAccount, 40.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - add: valid account ID");
         testAccount2.setMoney(20.0f);
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0", "testAccount2"});
         if (testAccountFields(testAccount2, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         // set quantity [account_id]
         System.err.println("serviceRequests() - set: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(null);
         if (!testBAOS.toString().equals("/set <quantity> [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - set: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{});
         if (!testBAOS.toString().equals("/set <quantity> [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - set: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - set: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{});
         if (!testBAOS.toString().equals("/set <quantity> [account_id]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - set: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0", "testAccount2", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - set: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{"invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - set: invalid account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0", "invalidAccount"});
         if (!testBAOS.toString().startsWith("error - account not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - set: minimum args");
         playerAccount.setMoney(30.0f);
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0"});
         if (testAccountFields(playerAccount, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - set: valid account ID");
         testAccount2.setMoney(20.0f);
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0", "testAccount2"});
         if (testAccountFields(testAccount2, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         // printMarket
         // don't worry about changing the file written to since it's data is meant to be temporary
         System.err.println("serviceRequests() - printMarket: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(null);
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - printMarket: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - printMarket: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{""});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - printMarket: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{"excessArgument"});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - printMarket: expected usage");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{});
         if (!testBAOS.toString().isEmpty()) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // inventory
         System.err.println("serviceRequests() - inventory: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(null);
         if (!testBAOS.toString().startsWith("Your inventory: ")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{});
         if (!testBAOS.toString().startsWith("Your inventory: ")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{""});
         if (!testBAOS.toString().startsWith("Your inventory: ")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{"", "excessArgument"});
         if (!testBAOS.toString().startsWith("Your inventory: ")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: expected usage");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{});
         if (!testBAOS.toString().startsWith("Your inventory: ")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: invalid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"invalidCoordinates"});
         if (!testBAOS.toString().startsWith("error - invalid inventory direction")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: zeroed coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 15);
         InterfaceTerminal.serviceRequestInventory(new String[]{"none"});
         if (!testBAOS.toString().startsWith("Your inventory: ") ||
             !testBAOS.toString().contains(" 1. test:material1: 15")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: northward coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryNorth.put("test:material3", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"north"});
         if (!testBAOS.toString().startsWith("Northward inventory: ") ||
             !testBAOS.toString().contains(" 1. test:material3: 10")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: eastward coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryEast.put("minecraft:material4", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"east"});
         if (!testBAOS.toString().startsWith("Eastward inventory: ") ||
             !testBAOS.toString().contains(" 1. minecraft:material4: 10")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: westward coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryWest.put("test:material1", 5);
         InterfaceTerminal.serviceRequestInventory(new String[]{"west"});
         if (!testBAOS.toString().startsWith("Westward inventory: ") ||
             !testBAOS.toString().contains(" 1. test:material1: 5")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: southward coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventorySouth.put("test:material2", 5);
         InterfaceTerminal.serviceRequestInventory(new String[]{"south"});
         if (!testBAOS.toString().startsWith("Southward inventory: ") ||
             !testBAOS.toString().contains(" 1. test:material2: 5")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: upward coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryUp.put("test:material3", 5);
         InterfaceTerminal.serviceRequestInventory(new String[]{"up"});
         if (!testBAOS.toString().startsWith("Upward inventory: ") ||
             !testBAOS.toString().contains(" 1. test:material3: 5")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - inventory: downward coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryDown.put("test:material2", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"down"});
         if (!testBAOS.toString().startsWith("Downward inventory: ") ||
             !testBAOS.toString().contains(" 1. test:material2: 10")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         // give <ware_id> [quantity] [inventory_direction]
         System.err.println("serviceRequests() - give: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(null);
         if (!testBAOS.toString().equals("/give <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{});
         if (!testBAOS.toString().equals("/give <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{});
         if (!testBAOS.toString().equals("/give <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "10", "excessArgument", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - give: invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity: wrong type")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - give: zero quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "0"});
         if (!testBAOS.toString().startsWith("error - invalid quantity: must be greater than zero")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - give: negative quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "-1"});
         if (!testBAOS.toString().startsWith("error - invalid quantity: must be greater than zero")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - give: with insufficient inventory space");
         testBAOS.reset(); // clear buffer holding console output
         int inventorySpaceOrig = InterfaceTerminal.inventorySpace;
         InterfaceTerminal.inventorySpace = 0; // maximum inventory space is no inventory
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "100"});
         InterfaceTerminal.inventorySpace = inventorySpaceOrig;
         if (!testBAOS.toString().startsWith("You don't have enough inventory space")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - give: invalid ware ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:invalidWareID", "10"});
         if (!testBAOS.toString().equals("You have been given 10 test:invalidWareID" + System.lineSeparator() + "warning - test:invalidWareID is not usable within the marketplace" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (!InterfaceTerminal.inventory.containsKey("test:invalidWareID")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:invalidWareID") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:invalidWareID") + " test:invalidWareID, should contain 10");
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: invalid ware alias");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"invalidWareAlias", "5"});
         if (!testBAOS.toString().equals("You have been given 5 invalidWareAlias" + System.lineSeparator() + "warning - invalidWareAlias is not usable within the marketplace" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("invalidWareAlias")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("invalidWareAlias") != 5) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("invalidWareAlias") + " invalidWareAlias, should contain 5");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("serviceRequests() - give: valid ware ID");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1"});
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 1) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 1");
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: valid ware alias");
         InterfaceTerminal.serviceRequestGive(new String[]{"mat3"});
         if (!InterfaceTerminal.inventory.containsKey("test:material3")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material3") != 1) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material3") + " test:material3, should contain 1");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("serviceRequests() - give: invalid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "invalidCoordinates"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: valid coordinates");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "down"});
         if (!InterfaceTerminal.inventoryDown.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryDown.get("test:material1") != 1) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryDown.get("test:material1") + " test:material1, should contain 1");
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: zeroed coordinates");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "none"});
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 1) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 1");
            errorFound = true;
         }

         System.err.println("serviceRequests() - give: quantity and coordinates");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "10", "up"});
         if (!InterfaceTerminal.inventoryUp.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryUp.get("test:material1") != 10) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryUp.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }

         // to avoid interfering with other tests,
         // reset the testing environment
         resetTestEnvironment();

         // take <ware_id> [quantity] [inventory_direction]
         System.err.println("serviceRequests() - take: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(null);
         if (!testBAOS.toString().equals("/take <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{});
         if (!testBAOS.toString().equals("/take <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{});
         if (!testBAOS.toString().equals("/take <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "10", "excessArgument", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - take: invalid quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity: wrong type")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - take: zero quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "0"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory contains ware when it should not");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: negative quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "-1"});
         if (!testBAOS.toString().startsWith("error - invalid quantity: must be greater than zero")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - take: excessive quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "100"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory contains ware when it should not");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: unowned ware ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"unownedWareID"});
         if (!testBAOS.toString().startsWith("error - ware not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("serviceRequests() - take: invalid ware ID");
         InterfaceTerminal.inventory.put("test:invalidWareID", 100); // give the player some non-marketplace-compatible wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:invalidWareID", "10"});
         if (!InterfaceTerminal.inventory.containsKey("test:invalidWareID")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:invalidWareID") != 90) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:invalidWareID") + " test:invalidWareID, should contain 90");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: invalid ware alias");
         InterfaceTerminal.inventory.put("invalidWareAlias", 10); // give the player some non-marketplace-compatible wares
         InterfaceTerminal.serviceRequestTake(new String[]{"invalidWareAlias", "5"});
         if (!InterfaceTerminal.inventory.containsKey("invalidWareAlias")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("invalidWareAlias") != 5) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("invalidWareAlias") + " invalidWareAlias, should contain 5");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: valid ware ID");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory contains ware when it should not");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: valid ware alias");
         InterfaceTerminal.inventory.put("test:material3", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"mat3"});
         if (InterfaceTerminal.inventory.containsKey("test:material3")) {
            System.err.println("   inventory contains ware when it should not");
            errorFound = true;
         }
         resetTestEnvironment();

         System.err.println("serviceRequests() - take: invalid coordinates");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "invalidCoordinates"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: valid coordinates");
         InterfaceTerminal.inventoryDown.put("test:material1", 10);
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "down"});
         if (InterfaceTerminal.inventoryDown.containsKey("test:material1")) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryDown.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: zeroed coordinates");
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "none"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }

         System.err.println("serviceRequests() - take: quantity and coordinates");
         InterfaceTerminal.inventoryUp.put("test:material1", 10);
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "5", "up"});
         if (!InterfaceTerminal.inventoryUp.containsKey("test:material1")) {
            System.err.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryUp.get("test:material1") != 5) {
            System.err.println("   inventory contains " + InterfaceTerminal.inventoryUp.get("test:material1") + " test:material1, should contain 5");
            errorFound = true;
         }

         // changeName playername
         System.err.println("serviceRequests() - changeName: null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(null);
         if (!testBAOS.toString().equals("/changeName <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - changeName: empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{});
         if (!testBAOS.toString().equals("/changeName <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - changeName: blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{""});
         if (!testBAOS.toString().startsWith("error - must provide name or ID")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - changeName: too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{});
         if (!testBAOS.toString().equals("/changeName <player_name>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - changeName: too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{"possibleID", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("serviceRequests() - changeName: valid args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{"John Doe"});
         if (!testBAOS.toString().equals("Your name is now John Doe.\nYour old name was John_Doe." + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         InterfaceTerminal.playername = "John_Doe";
      }
      catch (Exception e) {
         System.err.println("serviceRequests() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests the command /create for the terminal interface.
    *
    * @return whether the /create request handler passed all test cases
    */
   private static boolean testCreate() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         // create account_id
         System.err.println("create() - null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(null);
         if (!testBAOS.toString().equals("/create <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("create() - empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{});
         if (!testBAOS.toString().equals("/create <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("create() - blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - must provide account ID")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("create() - too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{});
         if (!testBAOS.toString().equals("/create <account_id>" + System.lineSeparator())) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("create() - too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{"possibleAccount", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("create() - existing account ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{"testAccount1"});
         if (!testBAOS.toString().startsWith("error - account already exists")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("create() - valid account ID");
         InterfaceTerminal.serviceRequestCreate(new String[]{"possibleAccount"});
         if (!accounts.containsKey("possibleAccount")) {
            System.err.println("   failed to create account");
            errorFound = true;
            resetTestEnvironment();
         }
      }
      catch (Exception e) {
         System.err.println("create() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests the command /changeStock for the terminal interface.
    *
    * @return whether the /changeStock request handler passed all test cases
    */
   private static boolean testChangeStock() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // /changeStock <ware_ID> (quantity | equilibrium | overstocked | understocked)
      try {
         InterfaceTerminal.ops.add(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername));

         System.err.println("changeStock() - null input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(null);
         if (!testBAOS.toString().startsWith("/changeStock <ware_id> (<quantity> | equilibrium | overstocked | understocked)")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - empty input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{});
         if (!testBAOS.toString().startsWith("/changeStock <ware_id> (<quantity> | equilibrium | overstocked | understocked)")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - blank input");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"", ""});
         if (!testBAOS.toString().startsWith("error - zero-length arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - too few args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{""});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - too many args");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"", "", "excessArgument"});
         if (!testBAOS.toString().startsWith("error - wrong number of arguments")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - invalid ware ID");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"invalidWare", "0"});
         if (!testBAOS.toString().startsWith("error - ware not found")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - invalid quantity");
         testBAOS.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material1", "invalidQuantity"});
         if (!testBAOS.toString().startsWith("error - invalid quantity")) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }

         System.err.println("changeStock() - zero quantity");
         testBAOS.reset(); // clear buffer holding console output
         int quantity = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material1", "0"});
         if (!testBAOS.toString().startsWith("test:material1's stock is now " + quantity)) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (quantity != testWare1.getQuantity()) {
            System.err.println("   unexpected ware stock: " + testWare1.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("changeStock() - negative quantity");
         testBAOS.reset(); // clear buffer holding console output
         quantity = testWare3.getQuantity() - 10;
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material3", "-10"});
         if (!testBAOS.toString().startsWith("test:material3's stock is now " + quantity)) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (quantity != testWare3.getQuantity()) {
            System.err.println("   unexpected ware stock: " + testWare3.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("changeStock() - positive quantity");
         testBAOS.reset(); // clear buffer holding console output
         quantity = testWareP1.getQuantity() + 100;
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:processed1", "100"});
         if (!testBAOS.toString().startsWith("test:processed1's stock is now " + quantity)) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (quantity != testWareP1.getQuantity()) {
            System.err.println("   unexpected ware stock: " + testWareP1.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("changeStock() - equilibrium");
         testBAOS.reset(); // clear buffer holding console output
         quantity = Config.quanMid[testWare4.getLevel()];
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"minecraft:material4", "equilibrium"});
         if (!testBAOS.toString().startsWith("minecraft:material4's stock is now " + quantity)) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (quantity != testWare4.getQuantity()) {
            System.err.println("   unexpected ware stock: " + testWare4.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("changeStock() - overstocked");
         testBAOS.reset(); // clear buffer holding console output
         quantity = Config.quanHigh[testWare1.getLevel()];
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material1", "overstocked"});
         if (!testBAOS.toString().startsWith("test:material1's stock is now " + quantity)) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (quantity != testWare1.getQuantity()) {
            System.err.println("   unexpected ware stock: " + testWare1.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         System.err.println("changeStock() - understocked");
         testBAOS.reset(); // clear buffer holding console output
         quantity = Config.quanLow[testWare3.getLevel()];
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material3", "understocked"});
         if (!testBAOS.toString().startsWith("test:material3's stock is now " + quantity)) {
            System.err.println("   unexpected console output: " + testBAOS.toString());
            errorFound = true;
         }
         if (quantity != testWare3.getQuantity()) {
            System.err.println("   unexpected ware stock: " + testWare3.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }
      }
      catch (Exception e) {
         System.err.println("changeStock() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests AI's trading, configuration, and handling.
    *
    * @return whether AI passed all test cases
    */
   private static boolean testAI() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.filenameAIProfessions = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";
      File fileAIProfessions       = new File(Config.filenameAIProfessions);
      FileWriter fileWriter;

      // set up test AI
      AI testAI1;
      AI testAI2;
      AI testAI3;

      // prepare to grab internal variables
      Field     fTimer;           // used to check whether feature is running
      Timer     timerAITrades;
      Field     fTimerTask;
      AIHandler aiHandler;
      Field     fProfessions;     // used to check loaded AI professions
      Object[]  professions;
      Field     fActiveAI;        // used to check AI currently running
      Object[]  activeAI;
      Field     fPurchasablesIDs; // used to check loading wares from file
      Object[]  purchasablesIDs;  // IDs for wares the AI may buy
      Field     fPurchasables;    // wares the AI may buy
      Object[]  purchasables;

      // track changes to variables
      AI   ai;               // current test case's AI
      Ware ware1;            // current test case's first ware
      Ware ware2;            // current test case's second ware
      int  quantityToTrade1; // how much AI should trade for the first ware
      int  quantityToTrade2; // how much AI should trade for the second ware
      int  quantityWare1;    // first ware's quantity available for sale
      int  quantityWare2;    // second ware's quantity available for sale

      // ensure professions file doesn't affect next test run
      if (fileAIProfessions.exists())
         fileAIProfessions.delete();

      try {
         // set up test AI
         // testAI1: simple as possible
         // buys testWare1
         testAI1 = null;

         // testAI2: simple  + buys and sells
         // buys testWare2
         // sells testWareC1

         // testAI3: has preferences
         // buys testWare1, testWare3
         // sells testWareC2
         // prefers testWare3 by +10%

         // grab references to attributes

         System.err.println("AI - missing file");
         // initialize AI

         // check loaded AI professions

         System.err.println("AI - empty file");
         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test events file
            fileWriter.write(
               ""
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            System.err.println("   unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // try to load the test file

         // check loaded AI professions

         System.err.println("AI - loading valid and invalid professions");
         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               ""
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            System.err.println("   unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // try to load the test file

         // check loaded AI professions

         System.err.println("AI - loading professions with invalid wares");
         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               ""
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            System.err.println("   unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // try to load the test file

         // check loaded AI professions

         System.err.println("AI - sales, volume");
         ai               = testAI1;
         ware1            = testWare1;
         quantityToTrade1 = 0;
         quantityWare1    = Config.quanMid[ware1.getLevel()];
         ware1.setQuantity(quantityWare1);

         //ai.trade();

         if (ware1.getQuantity() != quantityWare1 + quantityToTrade1) {
            System.err.println("   unexpected quantity: " + ware1.getQuantity() + ", should be " + (quantityWare1 + quantityToTrade1));
            errorFound = true;
         }

         System.err.println("AI - sales, profession");
         System.err.println("AI - purchases, volume");
         System.err.println("AI - purchases, profession");
         System.err.println("AI - trade decisions, supply and demand");
         System.err.println("AI - trade decisions, preferences");
         System.err.println("AI - reloading professions");
         System.err.println("AI - reloading trading frequency");
         System.err.println("AI - reloading trading quantity");
         System.err.println("AI - toggling feature by reloading configuration");
      }
      catch (Exception e) {
         System.err.println("AI - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }
}