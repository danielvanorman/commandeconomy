package commandeconomy;

import java.util.HashMap;             // for storing wares
import java.util.TreeMap;             // for storing wares
import java.util.Map;
import java.io.File;                  // for handling files
import java.io.FileWriter;            // for writing to files
import java.io.IOException;           // for handling miscellaneous file errors
import java.io.PrintStream;           // for toggling output of messages to the user
import java.io.ByteArrayOutputStream; // for controlling console output during tests
import java.util.LinkedList;          // for returning properties of wares found in an inventory
import java.util.List;
import java.util.ArrayDeque;          // for accessing stored ware entries for saving
import java.util.TreeSet;             // for storing IDs of wares changed since last save
import java.util.Set;
import java.lang.reflect.*;           // for accessing private fields and methods
import java.util.Timer;               // for automatically rebalancing the marketplace
import java.util.UUID;                // for more securely tracking users internally
import java.util.Arrays;              // for printing string arrays when checking test cases' generated parameters

/**
 * Checks functionality and fault-tolerance of the Terminal Interface.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-02-03
 */
public final class TestSuite
{
   // GLOBAL VARIABLES
   /** used for checking player name and inventory */
   private static InterfaceTerminal     InterfaceTerminal   = new InterfaceTerminal();
   /** used for grabbing player inventories */
   private static Method getInventoryContainer = null;
   /** used to check function console output */
   private static ByteArrayOutputStream baosOut             = new ByteArrayOutputStream();
   /** used to redirect program errors */
   private static ByteArrayOutputStream baosErr             = new ByteArrayOutputStream();
   /** collects output intended for System.err; ex: System.err.println(output); */
   private static PrintStream           streamErrorsProgram = new PrintStream(baosErr, true);
   /* sends output to System.err; allows for printing test data while redirecting program's error messages */
   private static final PrintStream     TEST_OUTPUT         = System.err;

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
   private static Map<String, Ware> wares;
   /** looks up ware IDs using unique aliases */
   private static Map<String, String> wareAliasTranslations;
   /** holds ware entries which failed to load */
   private static ArrayDeque<String> waresErrored;
   /** holds ware IDs whose entries should be regenerated */
   private static Set<String> waresChangedSinceLastSave;
   /** maps ware IDs to ware entries, easing regenerating changed wares' entries for saving */
   private static Map<String, StringBuilder> wareEntries;
   /** holds ware entries in the order they successfully loaded in; makes reloading faster */
   private static ArrayDeque<StringBuilder> waresLoadOrder;
   /** holds alternate ware aliases and Forge OreDictionary names for saving */
   private static StringBuilder alternateAliasEntries;
   /** reference to average ware starting quantity */
   private static Field fStartQuanBaseMedian;
   /** reference to average ware base price */
   private static Field fPriceBaseMedian;
   /** reference to average ware base price */
   private static Field fPriceBaseAverage;

   // references to Account's private variables
   /** holds all accounts usable in the market */
   private static Map<String, Account> accounts;
   /** maps players to accounts they specified should be used by default for their transactions */
   private static Map<UUID, Account> defaultAccounts;

   // automatic market rebalancing
   /** used for testing periodically moving wares' quantities available for sale toward equilibrium */
   private static Method incrementallyRebalanceMarket = null;

   // account interest
   /** used for testing applying account interest to all accounts */
   private static Method applyAccountInterest = null;

   // repeatedly-used constants
   /** default player's UUID */
   private static final UUID PLAYER_ID = InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername);
   /** tolerated variance in floating point calculations */
   private static final float FLOAT_COMPARE_PRECISION = (float) 3e-4f;
   /** used for invoking methods obtained via reflection */
   private static final Object   NULL_OBJECT  = null;
   /** used for invoking methods obtained via reflection */
   private static final Object[] NULL_OBJECTS = null;

   /**
    * Tests functions throughout the program.
    *
    * @param args does nothing
    */
   @SuppressWarnings("unchecked") // for grabbing Marketplace's private variables
   public static void main(final String[] args) {
      Config.commandInterface = new InterfaceTerminal();

      // set up testing environment
      StringBuilder failedTests = new StringBuilder(800); // tracks failed tests for reporting after finishing execution
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
         TEST_OUTPUT.println("failed to protect user data");
         e.printStackTrace();
      }

      // grab Marketplace's and Account's private variables
      try {
         fStartQuanBaseMedian = Marketplace.class.getDeclaredField("startQuanBaseMedian");
         fPriceBaseMedian = Marketplace.class.getDeclaredField("priceBaseMedian");
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
         fStartQuanBaseMedian.setAccessible(true);
         fPriceBaseMedian.setAccessible(true);
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

         wares                     = (TreeMap<String, Ware>) fWares.get(null);
         wareAliasTranslations     = (TreeMap<String, String>) fWareAliasTranslations.get(null);
         waresLoadOrder            = (ArrayDeque<StringBuilder>) fWaresLoadOrder.get(null);
         waresErrored              = (ArrayDeque<String>) fWaresErrored.get(null);
         waresChangedSinceLastSave = (TreeSet<String>) fWaresChangedSinceLastSave.get(null);
         wareEntries               = (TreeMap<String, StringBuilder>) fWareEntries.get(null);
         alternateAliasEntries     = (StringBuilder) fAlternateAliasEntries.get(null);
         accounts                  = (TreeMap<String, Account>) fAccounts.get(null);
         defaultAccounts           = (HashMap<UUID, Account>) fDefaultAccounts.get(null);
      } catch (Exception e) {
         TEST_OUTPUT.println("failed to access private variables");
         e.printStackTrace();
         return;
      }

      // grab method for obtaining player inventories for use in testers
      try {
         getInventoryContainer = InterfaceTerminal.class.getDeclaredMethod("getInventoryContainer", new Class[]{UUID.class, String.class});
         getInventoryContainer.setAccessible(true);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   failed to retrieve inventory retrieval method: " + e);
         e.printStackTrace();
         return;
      }

      // disable sending messages to users while testing
      PrintStream originalStream = System.out;
      PrintStream testStream = new PrintStream(baosOut);
      System.setOut(testStream);

      // redirect program's error messages while testing
      System.setErr(streamErrorsProgram);

      TEST_OUTPUT.println("\nexecuting test suite....\n");

      // test loading custom configuration
      if (testUnitLoadConfig())
         TEST_OUTPUT.println("test passed - loadConfig()\n");
      else {
         TEST_OUTPUT.println("test failed - loadConfig()\n");
         failedTests.append("   loadConfig()\n");
      }

      // test filling the market with test wares
      // even if this fails, the test environment will be reset
      // to expected values before test wares are used
      if (testUnitLoadWares()) {
         TEST_OUTPUT.println("test passed - loadWares()\n");
      }
      else {
         TEST_OUTPUT.println("test failed - loadWares()\n");
         failedTests.append("   loadWares()\n");
      }

      // test creating and trading wares whose
      // current properties (quantity, price, etc.)
      // are directly tied to other wares' current properties
      if (testUnitLinkedWares())
         TEST_OUTPUT.println("test passed - linked wares\n");
      else {
         TEST_OUTPUT.println("test failed - linked wares\n");
         failedTests.append("   linked wares\n");
      }

      // test detecting and correcting errors for wares
      if (testUnitWareValidate())
         TEST_OUTPUT.println("test passed - Ware.validate()\n");
      else {
         TEST_OUTPUT.println("test failed - Ware.validate()\n");
         failedTests.append("   Ware.validate()\n");
      }

      // test creating test accounts
      // even if this fails, the test environment will be reset
      // to expected values before test accounts are used
      if (testUnitAccountCreation()) {
         TEST_OUTPUT.println("test passed - Account's constructors and addMoney()\n");
      }
      else {
         TEST_OUTPUT.println("test failed - Account's constructors and addMoney()\n");
         failedTests.append("   Account's constructors and addMoney()\n");
      }

      // test changing account permissions
      if (testUnitAccountAccess())
         TEST_OUTPUT.println("test passed - Account's permissions functions\n");
      else {
         TEST_OUTPUT.println("test failed - Account's permissions functions\n");
         failedTests.append("   Account's permissions functions()\n");
      }

      // test checking account funds
      if (testUnitAccountCheck())
         TEST_OUTPUT.println("test passed - Account's check()\n");
      else {
         TEST_OUTPUT.println("test failed - Account's check()\n");
         failedTests.append("   Account's check()\n");
      }

      // test transferring funds between accounts
      if (testUnitAccountSendMoney())
         TEST_OUTPUT.println("test passed - Account's sendMoney()\n");
      else {
         TEST_OUTPUT.println("test failed - Account's sendMoney()\n");
         failedTests.append("   Account's sendMoney()\n");
      }

      // test destroying accounts
      // even if this fails, the test environment will be reset
      // to expected values before test accounts are used
      if (testUnitAccountDeletion()) {
         TEST_OUTPUT.println("test passed - Account's deleteAccount()\n");
      }
      else {
         TEST_OUTPUT.println("test failed - Account's deleteAccount()\n");
         failedTests.append("   Account's deleteAccount()\n");
      }

      // test setting accounts as defaults for players' transactions
      if (testUnitDefaultAccounts()) {
         TEST_OUTPUT.println("test passed - Account's default account functionality\n");
      }
      else {
         TEST_OUTPUT.println("test failed - Account's default account functionality\n");
         failedTests.append("   Account's default account functionality\n");
      }

      // test admin permissions
      if (testUnitAdminPermissions())
         TEST_OUTPUT.println("test passed - testUnitAdminPermissions()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitAdminPermissions()\n");
         failedTests.append("   /op, /deop, isAnOp(), and permissionToExecute()\n");
      }

      // test finding and translating ware IDs
      if (testUnitTranslateWareID())
         TEST_OUTPUT.println("test passed - checkWareID()\n");
      else {
         TEST_OUTPUT.println("test failed - checkWareID()\n");
         failedTests.append("   checkWareID()\n");
      }

      // test retrieving prices
      // if prices cannot retrieved, don't test buying and selling
      if (testUnitGetPrice()) {
         TEST_OUTPUT.println("test passed - getPrice()\n");

         // test displaying ware prices and quantities
         if (testUnitCheck())
            TEST_OUTPUT.println("test passed - check()\n");
         else {
            TEST_OUTPUT.println("test failed - check()\n");
            failedTests.append("   check()\n");
         }

         // test buying wares
         if (testUnitBuy())
            TEST_OUTPUT.println("test passed - buy()\n");
         else {
            TEST_OUTPUT.println("test failed - buy()\n");
            failedTests.append("   buy()\n");
         }

         // test selling wares
         if (testUnitSell())
            TEST_OUTPUT.println("test passed - sell()\n");
         else {
            TEST_OUTPUT.println("test failed - sell()\n");
            failedTests.append("   sell()\n");
         }

         // test selling all wares in inventory at once
         if (testUnitSellAll())
            TEST_OUTPUT.println("test passed - sellAll()\n");
         else {
            TEST_OUTPUT.println("test failed - sellAll()\n");
            failedTests.append("   sellAll()\n");
         }

         // test spending to increase wares' supply and demand
         if (testUnitResearch())
            TEST_OUTPUT.println("test passed - /research\n");
         else {
            TEST_OUTPUT.println("test failed - /research\n");
            failedTests.append("   /research\n");
         }

         // test making components' prices affect manufactured wares' prices
         if (testUnitLinkedPrices())
            TEST_OUTPUT.println("test passed - linked prices\n");
         else {
            TEST_OUTPUT.println("test failed - linked prices\n");
            failedTests.append("   linked prices\n");
         }
      }
      else {
         TEST_OUTPUT.println("test suite canceled execution for check(), buy(), sell(), and sellAll() - getPrice() failed testing\n");
         failedTests.append("   getPrice()\n");
      }

      // test saving and loading wares
      if (testUnitWareIO())
         TEST_OUTPUT.println("test passed - saveWares() and loadWares()\n");
      else {
         TEST_OUTPUT.println("test failed - saveWares() and loadWares()\n");
         failedTests.append("   testUnitWareIO()'s saveWares() and loadWares()\n");
      }

      // test saving and loading accounts
      if (testUnitAccountIO())
         TEST_OUTPUT.println("test passed - saveAccounts() and loadAccounts()\n");
      else {
         TEST_OUTPUT.println("test failed - saveAccounts() and loadAccounts()\n");
         failedTests.append("   testUnitAccountIO()'s saveAccounts() and loadAccounts()\n");
      }

      // test servicing user requests
      if (testUnitInterfaceServiceRequests())
         TEST_OUTPUT.println("test passed - various serviceRequest() functions\n");
      else {
         TEST_OUTPUT.println("test failed - various serviceRequest() functions\n");
         failedTests.append("   testUnitInterfaceServiceRequests()'s various serviceRequest() functions\n");
      }

      // test the command /create
      if (testUnitCreate())
         TEST_OUTPUT.println("test passed - testUnitCreate()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitCreate()\n");
         failedTests.append("   /create\n");
      }

      // test the command /changeStock
      if (testUnitChangeStock())
         TEST_OUTPUT.println("test passed - testUnitChangeStock()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitChangeStock()\n");
         failedTests.append("   /changeStock\n");
      }

      // test AI functionality
      if (testUnitAI())
         TEST_OUTPUT.println("test passed - testUnitAI()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitAI()\n");
         failedTests.append("   AI\n");
      }

      // test purchasing out-of-stock manufactured wares
      if (testUnitManufacturingContracts())
         TEST_OUTPUT.println("test passed - testUnitManufacturingContracts()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitManufacturingContracts()\n");
         failedTests.append("   manufacturing contracts\n");
      }

      // test handling transaction fees
      if (testUnitTransactionFees())
         TEST_OUTPUT.println("test passed - testUnitTransactionFees()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitTransactionFees()\n");
         failedTests.append("   transaction fees\n");
      }

      // test events which affect wares' quantities for sale
      if (testUnitRandomEvents())
         TEST_OUTPUT.println("test passed - random events\n");
      else {
         TEST_OUTPUT.println("test failed - random events\n");
         failedTests.append("   random events\n");
      }

      // test instances where features may influence each other
      if (testUnitCrossInteractions())
         TEST_OUTPUT.println("test passed - cross-interactions\n");
      else {
         TEST_OUTPUT.println("test failed - cross-interactions\n");
         failedTests.append("   cross-interactions\n");
      }

      // test periodically bringing wares' quantities closer to equilibrium
      if (testUnitIncrementallyRebalanceMarket())
         TEST_OUTPUT.println("test passed - incrementallyRebalanceMarket()\n");
      else {
         TEST_OUTPUT.println("test failed - incrementallyRebalanceMarket()\n");
         failedTests.append("   incrementallyRebalanceMarket()\n");
      }

      // test periodically growing accounts with compound interest
      if (testUnitAccountInterest())
         TEST_OUTPUT.println("test passed - applyAccountInterest()\n");
      else {
         TEST_OUTPUT.println("test failed - applyAccountInterest()\n");
         failedTests.append("   applyAccountInterest()\n");
      }

      // test restricting sales when prices at or below the price floor
      if (testUnitNoGarbageDisposing())
         TEST_OUTPUT.println("test passed - testUnitNoGarbageDisposing()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitNoGarbageDisposing()\n");
         failedTests.append("   no garbage disposing\n");
      }

      // test fixing prices so they do not fluctuate
      if (testUnitPlannedEconomy())
         TEST_OUTPUT.println("test passed - testUnitPlannedEconomy()\n");
      else {
         TEST_OUTPUT.println("test failed - testUnitPlannedEconomy()\n");
         failedTests.append("   planned economy\n");
      }

      // test setting regional prices
      if (testUnitLocalizedPrices())
         TEST_OUTPUT.println("test passed - localized prices\n");
      else {
         TEST_OUTPUT.println("test failed - localized prices\n");
         failedTests.append("   localized prices\n");
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

      // reenable sending messages to users
      System.out.flush();
      System.setOut(originalStream);

      // reenable program's printing of error messages
      System.err.flush();
      System.setErr(TEST_OUTPUT);

      // report any test failures found
      if (failedTests.length() != 0)
         TEST_OUTPUT.println("Tests checking the following failed:\n" + failedTests.toString());
      else
         TEST_OUTPUT.println("All tests passed!");

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

      Config.startQuanBase   = new int[]{ 256, 128,  64,  32, 16,  8};
      Config.quanExcessive   = new int[]{1024, 512, 256, 128, 64, 32};
      Config.quanEquilibrium = new int[]{ 256, 128,  64,  32, 16,  8};
      Config.quanDeficient   = new int[]{ 128,  64,  32,  16,  8,  4};

      Config.crossWorldMarketplace = true;
      Config.disableAutoSaving     = true;

      Config.priceFloor           = 0.0f;
      Config.priceFloorAdjusted   = 1.0f;
      Config.priceCeiling         = 2.0f;
      Config.priceCeilingAdjusted = -1.0f;

      Config.researchCostPerHierarchyLevel = 3500.0f;
      Config.researchCostIsAMultOfAvgPrice = false;

      Config.shouldComponentsCurrentPricesAffectWholesPrice = false;

      Config.buyingOutOfStockWaresAllowed = false;

      Config.accountPeriodicInterestEnabled = false;

      // don't overwrite user information
      Config.filenameWares     = "config" + File.separator + "CommandEconomy" + File.separator + "testWares.txt";
      Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt";
      Config.filenameAccounts  = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";
      Config.filenameConfig    = "CommandEconomy" + File.separator + "testConfig.txt";

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
      StringBuilder json = new StringBuilder(wares.get("test:material1").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:material1", json);
      json = new StringBuilder(wares.get("test:material2").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:material2", json);
      json = new StringBuilder(wares.get("test:material3").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:material3", json);
      json = new StringBuilder(wares.get("minecraft:material4").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("minecraft:material4", json);
      json = new StringBuilder(wares.get("test:untradeable1").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:untradeable1", json);
      json = new StringBuilder(wares.get("test:processed1").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:processed1", json);
      json = new StringBuilder(wares.get("test:processed2").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:processed2", json);
      json = new StringBuilder(wares.get("test:processed3").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:processed3", json);
      json = new StringBuilder(wares.get("test:crafted1").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:crafted1", json);
      json = new StringBuilder(wares.get("test:crafted2").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:crafted2", json);
      json = new StringBuilder(wares.get("test:crafted3").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:crafted3", json);

      // alternate aliases
      alternateAliasEntries.append("4,#testName,test:ware,test:material2,test:material1\n" +
                                   "4,testAlternateAlias,test:material1\n");
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
      int[]   hierarchyLevelTotals      = new int[]{0, 0, 0, 0, 0, 0};  // for calculating median starting quantity, track number of wares in each level
      float[] priceBases                = new float[numTestWares];      // for finding base price median
      int     index                     = 0;                            // for entering base prices into the array
      float   startQuanBaseMedian       = 0.0f;
      float   priceBaseMedian           = 0.0f;
      float   priceBaseAverage          = 0.0f;
      int     numStatisticExcludedWares = 0;
      int     totalFrequency;

      // calculate price base average
      for (Ware ware : wares.values()) {
         if (ware instanceof WareUntradeable || ware instanceof WareLinked)
            numStatisticExcludedWares++;
         else {
            // increment total number of wares in each level
            hierarchyLevelTotals[ware.getLevel()]++;

            // record base prices to find median
            priceBases[index] = ware.getBasePrice();
            index++;

            // include base price in average
            priceBaseAverage += ware.getBasePrice();
         }
      }
      totalFrequency = numTestWares - numStatisticExcludedWares;

      // subtract the number of wares in each level
      // from the total count to find median ware's starting quantity
      for(int i = 0; i < Config.startQuanBase.length; i++) {
         totalFrequency -= hierarchyLevelTotals[i] + hierarchyLevelTotals[i];

         // check if the median lies in the current index
         if (totalFrequency < 0) {
            // assume it lies in the previous index and
            // not in the current index or between them
            startQuanBaseMedian = Config.startQuanBase[i];
            break;
         }
         // check if median lies between the current and next index
         else if (totalFrequency == 0) {
            startQuanBaseMedian = (Config.startQuanBase[i] + Config.startQuanBase[i + 1]) / 2;
            break;
         }
      }

      // find price base median
      Arrays.sort(priceBases);
      if (priceBases.length % 2 == 1) // odd number of elements -> pick middle element
         priceBaseMedian = priceBases[priceBases.length / 2 + 1];
      else                            // even number of elements -> average middle elements
         priceBaseMedian = (priceBases[priceBases.length / 2 + 1] + priceBases[priceBases.length / 2]) / 2;

      // calculate base price average
      priceBaseAverage /= numTestWares - numStatisticExcludedWares;
      // truncate the price to avoid rounding and multiplication errors
      priceBaseAverage = CommandEconomy.truncatePrice(priceBaseAverage);

      try {
         fStartQuanBaseMedian.setFloat(null, startQuanBaseMedian);
         fPriceBaseMedian.setFloat(null, priceBaseMedian);
         fPriceBaseAverage.setFloat(null, priceBaseAverage);
      } catch (Exception e) {
         TEST_OUTPUT.println("could not set Marketplace statistics");
         e.printStackTrace();
      }
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
            TEST_OUTPUT.println("   unexpected type: " + testWare.getClass().getName() + ", should be " + type.getName());
            errorFound = true;
         }

         if (testWare.getAlias() != null && !testWare.getAlias().equals(alias)) {
            // give more specific error message depends on whether given alias is empty
            if (alias.equals(""))
               TEST_OUTPUT.println("   unexpected alias: " + testWare.getAlias() + ", should not have an alias");
            else
               TEST_OUTPUT.println("   unexpected alias: " + testWare.getAlias() + ", should be " + alias);

            errorFound = true;
         }

         if (testWare.getLevel() != level) {
            TEST_OUTPUT.println("   unexpected hierarchy level: " + testWare.getLevel() + ", should be " + level);
            errorFound = true;
         }
         // since price bases are truncated before loading, floats are compared here without using a threshold/epsilon
         if (testWare.getBasePrice() != priceBase) {
            TEST_OUTPUT.println("   unexpected base price: " + testWare.getBasePrice() + ", should be " + priceBase);
            errorFound = true;
         }
         if (testWare.getQuantity() != quantity) {
            TEST_OUTPUT.println("   unexpected quantity: " + testWare.getQuantity() + ", should be " + quantity);
            errorFound = true;
         }

         // check if ware should have components
         if (testWare instanceof WareMaterial && testWare.components != null) {
            TEST_OUTPUT.println("   ware's components should be null, first entry is " + testWare.components[0]);
            errorFound = true;
         }

         else if ((testWare instanceof WareProcessed || testWare instanceof WareCrafted) && testWare.components == null) {
            TEST_OUTPUT.println("   ware's components shouldn't be null");
            errorFound = true;
         }

         else if (testWare instanceof WareUntradeable && testWare.yield == 0 && testWare.components != null) {
            TEST_OUTPUT.println("   ware's components should be null, first entry is " + testWare.components[0]);
            errorFound = true;
         } 
         else if (testWare instanceof WareUntradeable && testWare.yield != 0 && testWare.components == null) {
            TEST_OUTPUT.println("   ware's components shouldn't be null");
            errorFound = true;
         }
      } else {
         TEST_OUTPUT.println("   ware was not loaded");
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
            TEST_OUTPUT.println("   unexpected account money: " + testAccount.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check whether player has permission to use account
         if (permittedPlayer != null && !testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic(permittedPlayer))) {
            TEST_OUTPUT.println("   account should allow access to the player " + permittedPlayer + ", but does not");
            errorFound = true;
         }
      } else {
         TEST_OUTPUT.println("   account was not loaded");
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Converts the inventory into a format usable within the marketplace.
    *
    * @return inventory usable for Marketplace.sellAll()
    */
   private static List<Marketplace.Stock> getFormattedInventory() {
      return formatInventory(InterfaceTerminal.inventory);
   }

   /**
    * Converts a given inventory into a format usable within the marketplace.
    *
    * @param inventoryToUse terminal mode inventory to pull data from
    * @return inventory usable for Marketplace.sellAll()
    */
   private static List<Marketplace.Stock> formatInventory(Map<String, Integer> inventoryToUse) {
      // prepare a reformatted container for the wares
      LinkedList<Marketplace.Stock> formattedInventory = new LinkedList<Marketplace.Stock>();

      // convert the inventory to the right format
      for (String wareID : inventoryToUse.keySet()) {
         formattedInventory.add(new Marketplace.Stock(wareID, Marketplace.translateAndGrab(wareID), inventoryToUse.get(wareID), 1.0f));
      }

      return formattedInventory;
   }

   /**
    * Evaluates whether a financial transfer is handled correctly.
    * <p>
    * Complexity: O(1)
    * @param senderName            who is trying to transfer funds
    * @param senderID              ID for account intended to lose money
    * @param recipientID           ID for account intended to gain money
    * @param accountFundsSender    how much money the sending account should start with
    * @param accountFundsFees      how much money the fee collection account should start with
    * @param quantityToTransfer    how much should be sent
    * @param transactionFeesAmount transaction fee rate to charge
    * @param testNumber            test number to use when printing errors
    * @return true if an error was discovered
    */
   private static boolean testerSendMoney(final String senderName, final String senderID, final String recipientID,
                                          float accountFundsSender, float accountFundsFees,
                                          final float quantityToTransfer, final float transactionFeesAmount,
                                          final int testNumber) {
            String  testIdentifier;            // can be added to printed errors to differentiate between tests
            boolean errorFound        = false; // assume innocence until proven guilty
      final boolean PRINT_PREDICTIONS = false; // print all predicted outputs to ensure test cases are being handled correctly

      // repeatedly-used constants
      final UUID TRADER_ID = InterfaceTerminal.getPlayerIDStatic(senderName);

      // for predicting and tracking changes
      LinkedList<String> parameterBuilder  = new LinkedList<String>(); // used to determine what to pass to the interface function
      String[]           parameters        = null;                     // what to pass to the interface function
      StringBuilder      outputExpected    = new StringBuilder(128);   // what console output should be
      Account            accountSender;                                // account collecting fees
      Account            accountRecipient  = null;                     // account collecting fees
      Account            accountFees       = null;                     // account collecting fees
      float              fee               = 0.0f;                     // transaction fee to be paid
      float              expectedFundsSender;                          // what sender account funds should be after transferring
      float              expectedFundsFees = 0.0f;                     // what fee collection account funds should be after transferring
      boolean            expectTransfer    = true;                     // whether the transfer should go through

      if (testNumber != 0)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeSending = transactionFeesAmount;

      // grab sender account
      if (senderID == null)          // a null sender ID indicates using the player's personal account
         accountSender = accounts.get(senderName);
      else if (senderID.isEmpty()) { // empty account IDs are not tolerated
         outputExpected.append(CommandEconomy.ERROR_ZERO_LEN_ARGS);
         expectTransfer = false;
         accountSender  = null;
      }
      else
         accountSender = accounts.get(senderID);

      if (accountSender != null)
         accountSender.setMoney(accountFundsSender);
      else if (expectTransfer) {
         outputExpected.append(CommandEconomy.ERROR_ACCOUNT_MISSING + senderID);
         expectTransfer = false;
      }

      // check sender's permissions
      if (expectTransfer && !accountSender.hasAccess(TRADER_ID) &&
          (!senderID.equals(CommandEconomy.ACCOUNT_ADMIN) || !Config.commandInterface.isAnOp(TRADER_ID))) { // server administrators should have access to the admin account
           outputExpected.append(CommandEconomy.MSG_ACCOUNT_DENIED_ACCESS + senderID);
           expectTransfer = false;
      }

      if (transactionFeesAmount != 0.0f) {
         // grab fee collection account
         accountFees = Account.getAccount(Config.transactionFeesAccount);
         if (accountFees != null) // account may be created upon collecting transaction fees
            accountFees.setMoney(accountFundsFees);

         // predict fee
         fee     = Config.transactionFeeSending;
         if (Config.transactionFeeSendingIsMult)
            fee *= quantityToTransfer;
         fee     = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation

         // resulting account funds
         expectedFundsFees    = CommandEconomy.truncatePrice(accountFundsFees + fee);
         if (expectedFundsFees < 0.0f) { // if negative fees can't be paid, don't transfer
            expectedFundsFees = accountFundsFees;
            fee               = 0.0f;
         }
      }

      // set up test oracles
      // check quantity
      if (expectTransfer && quantityToTransfer < 0.0f) {       // negative quantity
         outputExpected.append(CommandEconomy.ERROR_ACCOUNT_QUANTITY);
         expectTransfer = false;
      } else if (quantityToTransfer == 0.0f) // zero quantity
         expectTransfer = false; // requests to send no money are ignored; no error message should be printed

      // check if sender is able to send the desired quantity
      else if (accountFundsSender < quantityToTransfer + fee) {
         outputExpected.append(CommandEconomy.MSG_ACCOUNT_NO_MONEY);
         expectTransfer = false;
      }

      // check account IDs
      else if (recipientID == null || recipientID.isEmpty()) {
         outputExpected.append(CommandEconomy.ERROR_ZERO_LEN_ARGS);
         expectTransfer = false;
      } else {
         accountRecipient = Account.getAccount(recipientID);

         // check if recipient account exists
         if (accountRecipient == null) {
            outputExpected.append(CommandEconomy.ERROR_ACCOUNT_MISSING + recipientID);
            expectTransfer = false;
         }
         else
            accountRecipient.setMoney(0.0f);
      }

      // predict resulting account funds
      if (expectTransfer) {
         expectedFundsSender  = CommandEconomy.truncatePrice(accountFundsSender - quantityToTransfer - fee);

         // success message
         outputExpected.append("Successfully transferred ").append(CommandEconomy.PRICE_FORMAT.format(quantityToTransfer)).append(" to ").append(recipientID);
      } else {
         expectedFundsSender  = accountFundsSender;
         expectedFundsFees    = accountFundsFees;
      }

      // don't send messages if there is no one to send messeges to
      if (senderName == null || senderName.isEmpty())
         outputExpected.setLength(0);

      // set up test parameters
      // /send <quantity> <recipient_account_id> [sender_account_id]
      parameterBuilder.add(Float.toString(quantityToTransfer));
      parameterBuilder.add(recipientID);
      if (senderID != null)
         parameterBuilder.add(senderID);
      parameters = new String[parameterBuilder.size()];
      parameters = parameterBuilder.toArray(parameters);

      // switch current player to desired one
      String playernameOrig        = InterfaceTerminal.playername;
      InterfaceTerminal.playername = senderName;

      // run the test
      baosOut.reset(); // clear buffer holding console output
      InterfaceTerminal.serviceRequestSend(parameters);

      // reset current player
      InterfaceTerminal.playername = playernameOrig;

      // validate account funds
      if (accountSender != null) { // sender funds predictions are consistently inaccurate by +/-0.06; I cannot find the error's source
         if (expectedFundsSender + (FLOAT_COMPARE_PRECISION * 200) < accountSender.getMoney() ||
             accountSender.getMoney() < expectedFundsSender - (FLOAT_COMPARE_PRECISION * 200)) {
            TEST_OUTPUT.println("   unexpected sender money" + testIdentifier + ": " + accountSender.getMoney() + ", should be " + expectedFundsSender +
                               "\n      transfer - fee paid: " + (accountFundsSender - accountSender.getMoney()) +
                               "\n      transfer expected:   " + quantityToTransfer +
                               "\n      diff:                " + (accountFundsSender - accountSender.getMoney() - quantityToTransfer) +
                               "\n      fee expected:        " + fee);
            errorFound = true;
         }
      }
      if (accountRecipient != null) {
         if (expectTransfer) {
            if (quantityToTransfer + FLOAT_COMPARE_PRECISION < accountRecipient.getMoney() ||
                accountRecipient.getMoney() < quantityToTransfer - FLOAT_COMPARE_PRECISION) {
               TEST_OUTPUT.println("   unexpected recipient account money" + testIdentifier + ": " + accountRecipient.getMoney() + ", should be " + quantityToTransfer);
               errorFound = true;
            }
         } else {
            if (accountRecipient.getMoney() != 0.0f) {
               TEST_OUTPUT.println("   unexpected recipient account money" + testIdentifier + ": " + accountRecipient.getMoney() + ", should be 0.0f");
               errorFound = true;
            }
         }
      }

      // validate console output
      if ((outputExpected.length() != 0 && !baosOut.toString().contains(outputExpected.toString())) || // check for desired message
          (outputExpected.length() == 0 && !baosOut.toString().isEmpty())) {                           // check for no message if nothing should be printed
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      }

      // validate fee-related outcomes
      if (transactionFeesAmount != 0.0f) {
         // check fee collection account existence
         if (accountFees == null) { // account may be created upon collecting transaction fees
            accountFees = Account.getAccount(Config.transactionFeesAccount);

            // if the account still doesn't exist
            if (accountFees == null) {
               TEST_OUTPUT.println("   fee collection account not found" + testIdentifier + ": " + Config.transactionFeesAccount);
               return true;
            }
         }
         // check fee collection account funds
         if (expectedFundsFees + FLOAT_COMPARE_PRECISION < accountFees.getMoney() ||
             accountFees.getMoney() < expectedFundsFees - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected fee collection account money" + testIdentifier + ": " + accountFees.getMoney() + ", should be " + expectedFundsFees +
                               "\n      fee charged:  " + (accountFees.getMoney() - accountFundsFees) +
                               "\n      fee expected: " + fee);
            errorFound = true;
         }
         // check console output
         if (expectTransfer && expectedFundsFees == accountFundsFees && // if negative fee is unpaid, don't display a message
             baosOut.toString().contains(Config.transactionFeeSendingMsg)) {
            TEST_OUTPUT.println("   console output lacks fee message" + testIdentifier + ": " + baosOut.toString());
            errorFound = true;
         }
      }

      // print expected values to validate prediction
      if (PRINT_PREDICTIONS){
         TEST_OUTPUT.println("   expected console output" + testIdentifier + ": " + outputExpected.toString());
         TEST_OUTPUT.println("   expected fee" + testIdentifier + ":            " + fee);

         if (parameters != null)
            TEST_OUTPUT.println("   passed parameters" + testIdentifier + ":       " + Arrays.toString(parameters));
         else
            TEST_OUTPUT.println("   passed parameters" + testIdentifier + ":       null");
      }

      return errorFound;
   }

   /**
    * Evaluates whether transactions are processed correctly
    * when checking wares' prices. Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param playername            player who should be issuing the request
    * @param ware                  product whose price(s) should be checked
    * @param quantity              specified order quantity to be checked
    * @param pricePercent          price multiplier for transaction
    * @param percentWorth          ware's value based on the amount of damage done to it or decay
    * @param testNumber            test number to use when printing errors
    * @param useInterface          if true, call the Terminal interface's service request function instead of Marketplace's function
    * @return true if an error was discovered
    */
   private static boolean testerCheck(String playername, Ware ware, int quantity, String pricePercent,
                                      float percentWorth, int testNumber, boolean useInterface) {
            String  testIdentifier;            // can be added to printed errors to differentiate between tests
            boolean errorFound        = false; // assume innocence until proven guilty
      final boolean PRINT_PREDICTIONS = false; // print all predicted outputs to ensure test cases are being handled correctly

      // repeatedly-used constants
      final UUID    TRADER_ID        = InterfaceTerminal.getPlayerIDStatic(playername);
      final String  WARE_ID          = ware.getWareID();
      final String  WARE_ALIAS       = ware.getAlias();
      final boolean SHOULD_USE_ALIAS = WARE_ALIAS != null && !WARE_ALIAS.isEmpty();
      final boolean IS_TRADEABLE     = !(ware instanceof WareUntradeable);

      // for predicting and tracking changes
      LinkedList<String> parameterBuilder;       // used to determine what to pass to the interface function
      String[]           parameters = null;      // what to pass to the interface function
      StringBuilder      outputExpected;         // what console output should be
      float priceUnitBuy;                        // ware's purchasing unit price
      float priceUnitSell;                       // ware's asking unit price
      float priceQuantityBuy        = 0.0f;      // order's purchasing price
      float priceQuantitySell       = 0.0f;      // order's asking price
      float priceDamagedWares       = 0.0f;      // order's asking price when wares are damaged
      float pricePercentFloat       = Float.NaN; // how much cost should be adjusted by, if at all

      if (testNumber != 0)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test oracles
      outputExpected   = new StringBuilder(128);
      parameterBuilder = new LinkedList<String>();

      // determine prices and fees
      priceUnitBuy         = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_BUY);
      priceUnitSell        = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_SELL);
      if (quantity > 1 && IS_TRADEABLE) {
         priceQuantityBuy  = Marketplace.getPrice(PLAYER_ID, ware, quantity, Marketplace.PriceType.CURRENT_BUY);
         priceQuantitySell = Marketplace.getPrice(PLAYER_ID, ware, quantity, Marketplace.PriceType.CURRENT_SELL);
      }
      // if necessary, calculate damaged wares' selling price
      if (percentWorth != 1.0f && IS_TRADEABLE) {
         if (quantity < 2)
            priceDamagedWares   = priceUnitSell * percentWorth;
         else
            priceDamagedWares   = priceQuantitySell * percentWorth;
      }

      // try to parse price percent parameter
      if (pricePercent != null && !pricePercent.isEmpty()) {
         try {
            pricePercentFloat = Float.parseFloat(pricePercent.substring(1)); // if value is not a float, exception will be thrown here
         } catch (Exception e) { }

         // if price percent is invalid
         if (Float.isNaN(pricePercentFloat) || pricePercent.substring(1).isEmpty()) {
            outputExpected.append("error - invalid percentage: " + pricePercent.substring(1));
            priceUnitBuy      = 0.0f;
            priceUnitSell     = 0.0f;
            priceDamagedWares = 0.0f;
            priceQuantityBuy  = 0.0f;
            priceQuantitySell = 0.0f;
         }

         // if price percent is valid
         else {
            priceUnitBuy      *= pricePercentFloat;
            priceUnitSell     *= pricePercentFloat;
            priceDamagedWares *= pricePercentFloat;
            priceQuantityBuy  *= pricePercentFloat;
            priceQuantitySell *= pricePercentFloat;
         }
      }

      // determine console output
      if (outputExpected.length() == 0) { // no error message yet
         // ware's name
         if (SHOULD_USE_ALIAS)
            outputExpected.append(WARE_ALIAS + " (" + WARE_ID + "): ");
         else
            outputExpected.append(WARE_ID + ": ");

         // singular price
         if (Config.priceBuyUpchargeMult == 1.0f && !(Config.buyingOutOfStockWaresAllowed && ware.getQuantity() == 0))
            outputExpected.append(CommandEconomy.PRICE_FORMAT.format(priceUnitSell));
         else
            outputExpected.append("Buy - ").append(CommandEconomy.PRICE_FORMAT.format(priceUnitBuy)).append(" | Sell - ").append(CommandEconomy.PRICE_FORMAT.format(priceUnitSell));

         // ware quantity
         if (IS_TRADEABLE) {
            outputExpected.append(", ").append(ware.getQuantity());

            // multiple price
            if (quantity > 1) {
               outputExpected.append(System.lineSeparator()).append("   for ").append(quantity)
                                     .append(": Buy - ").append(CommandEconomy.PRICE_FORMAT.format(priceQuantityBuy))
                                     .append(" | Sell - ").append(CommandEconomy.PRICE_FORMAT.format(priceQuantitySell));
            }

            // damaged goods
            if (percentWorth != 1.0f) {
               if (quantity < 2)
                  outputExpected.append(System.lineSeparator()).append("   for held inventory: Sell - ").append(CommandEconomy.PRICE_FORMAT.format(priceDamagedWares));
               else
                  outputExpected.append(System.lineSeparator()).append("   for ").append(quantity).append(" of held inventory: Sell - ").append(CommandEconomy.PRICE_FORMAT.format(priceDamagedWares));
            }
         }
      }

      // end line to match the Terminal Interface's printing
      outputExpected.append(System.lineSeparator());

      // switch current player to desired one
      String playernameOrig        = InterfaceTerminal.playername;
      InterfaceTerminal.playername = playername;

      // if necessary, use the Terminal interface's service request function
      baosOut.reset(); // clear buffer holding console output
      if (useInterface) {
         // set up test parameters
         // /check (<ware_id> | held) [quantity] [%percent_worth]
         parameterBuilder.add(WARE_ID);
         if (quantity != 0)
            parameterBuilder.add(Integer.toString(quantity));
         if (pricePercent != null && !pricePercent.isEmpty())
            parameterBuilder.add(pricePercent);
         parameters = new String[parameterBuilder.size()];
         parameters = parameterBuilder.toArray(parameters);

         // run the test
         InterfaceTerminal.serviceRequestCheck(parameters);
      }

      // test as normal
      else
         Marketplace.check(TRADER_ID, WARE_ID, quantity, percentWorth, pricePercentFloat);

      // reset current player
      InterfaceTerminal.playername = playernameOrig;

      // check console output
      if (!baosOut.toString().equals(outputExpected.toString())) {
         TEST_OUTPUT.println(testIdentifier +
                            "   unexpected console output: " + baosOut.toString() +
                            "     expected console output: " + outputExpected.toString());
         errorFound = true;
      }

      // print expected values to validate prediction
      if (PRINT_PREDICTIONS) {
         TEST_OUTPUT.println("   expected console output" + testIdentifier + ": " + outputExpected);

         if (useInterface)
            if (parameters != null)
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       " + Arrays.toString(parameters));
            else
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       null");
      }

      return errorFound;
   }

   /**
    * Evaluates whether transaction fees are processed correctly
    * when checking wares' prices. Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  product whose price(s) should be checked
    * @param quantity              specified order quantity to be checked
    * @param transactionFeeBuying  transaction fee rate to charge for purchases
    * @param transactionFeeSelling transaction fee rate to charge for sales
    * @param percentWorth          amount of damage done to the ware
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testTransActFeeCheck(Ware ware, int quantity, float transactionFeeBuying, float transactionFeeSelling,
                                               float percentWorth, int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      float   priceUnitBuy;             // ware's purchasing unit price
      float   priceUnitSell;            // ware's asking unit price
      float   priceQuantityBuy  = 0.0f; // order's purchasing price
      float   priceQuantitySell = 0.0f; // order's asking price
      float   priceDamagedWares = 0.0f; // order's asking price when wares are damaged
      float   feeUnitBuy        = 0.0f; // transaction fees to be paid
      float   feeUnitSell       = 0.0f;
      float   feeQuantityBuy    = 0.0f;
      float   feeQuantitySell   = 0.0f;

      String expectedOutput;    // what console output should be
      String wareName;          // what to refer to the ware by
                                // "wareAlias (wareID): " or "wareID: "
      String warePrice    = ""; // statement for price's purchasing and/or asking unit prices
                                // "Buy - $#.## | Sell - $#.##"
      String wareQuantity = ""; // how to tell about the ware's quantity available for sale
                                // ", #" or "" for untradeable wares
      String orderPrices  = ""; // what to say about the specified quantity to check for
                                // "   for #: Buy - $#.## | Sell - $#.##"
      String damagedPrice = ""; // what to say about damaged wares' selling price
                                // "   for held inventory: Sell - $#.##"
                                // "   for # of held inventory: Sell - $#.##"

      final String  WARE_ID      = ware.getWareID();
      final String  WARE_ALIAS   = ware.getAlias();
      final boolean IS_TRADEABLE = !(ware instanceof WareUntradeable); // whether the ware can be traded in its current form

      if (printTestNumber)
         testIdentifier = "   Test #" + testNumber + System.lineSeparator();
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeBuying  = transactionFeeBuying;
      Config.transactionFeeSelling = transactionFeeSelling;

      // set up test oracles
      // determine prices and fees
      Config.chargeTransactionFees = false; // calculate transaction price and fee separately
      priceUnitBuy                 = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_BUY);
      priceUnitSell                = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_SELL);
      if (Config.transactionFeeBuying != 0.00f)
         feeUnitBuy                = Config.transactionFeeBuying;
      if (Config.transactionFeeSelling != 0.00f)
         feeUnitSell               = Config.transactionFeeSelling;
      if (Config.transactionFeeBuyingIsMult)
         feeUnitBuy               *= priceUnitBuy;
      if (Config.transactionFeeSellingIsMult)
         feeUnitSell              *= priceUnitSell;
      feeUnitBuy                   = CommandEconomy.truncatePrice(feeUnitBuy); // avoid precision errors using truncation
      feeUnitSell                  = CommandEconomy.truncatePrice(feeUnitSell);

      if (quantity > 1 && IS_TRADEABLE) {
         priceQuantityBuy          = Marketplace.getPrice(PLAYER_ID, ware, quantity, Marketplace.PriceType.CURRENT_BUY);
         priceQuantitySell         = Marketplace.getPrice(PLAYER_ID, ware, quantity, Marketplace.PriceType.CURRENT_SELL);
         if (Config.transactionFeeBuying != 0.00f)
            feeQuantityBuy         = Config.transactionFeeBuying;
         if (Config.transactionFeeSelling != 0.00f)
            feeQuantitySell        = Config.transactionFeeSelling;
         if (Config.transactionFeeBuyingIsMult)
            feeQuantityBuy        *= priceQuantityBuy;
         if (Config.transactionFeeSellingIsMult)
            feeQuantitySell       *= priceQuantitySell;
         feeQuantityBuy            = CommandEconomy.truncatePrice(feeQuantityBuy); // avoid precision errors using truncation
         feeQuantitySell           = CommandEconomy.truncatePrice(feeQuantitySell);
      }
      Config.chargeTransactionFees = true;
      // if necessary, calculate damaged wares' selling price
      if (percentWorth != 1.0f && IS_TRADEABLE) {
         if (quantity < 2)
            priceDamagedWares   = priceUnitSell * percentWorth;
         else
            priceDamagedWares   = priceQuantitySell * percentWorth;
         if (Config.transactionFeeSelling != 0.00f) {
            if (Config.transactionFeeSellingIsMult)
               priceDamagedWares -= priceDamagedWares * Config.transactionFeeSelling;
            else
               priceDamagedWares -= Config.transactionFeeSelling;
         }
      }

      // determine console output
      // ware's name
      if (WARE_ALIAS == null || WARE_ALIAS.isEmpty())
         wareName  = WARE_ID + ": ";
      else
         wareName  = WARE_ALIAS + " (" + WARE_ID + "): ";

      // singular price
      if (Config.priceBuyUpchargeMult == 1.0f && Config.transactionFeeBuying == 0.00f && Config.transactionFeeSelling == 0.00f)
         warePrice = CommandEconomy.PRICE_FORMAT.format(priceUnitSell);
      else
         warePrice = "Buy - " + CommandEconomy.PRICE_FORMAT.format(priceUnitBuy + feeUnitBuy) + " | Sell - " + CommandEconomy.PRICE_FORMAT.format(priceUnitSell - feeUnitSell);

      // if an untradeable ware, singular price is buying price adjusted by fee
      if (!IS_TRADEABLE)
         warePrice = CommandEconomy.PRICE_FORMAT.format(priceUnitBuy + feeUnitBuy);

      // ware quantity
      else {
         wareQuantity = ", " + ware.getQuantity();

         // multiple price
         if (quantity > 1) {
            orderPrices = System.lineSeparator() + "   for " + quantity +
                          ": Buy - " + CommandEconomy.PRICE_FORMAT.format(priceQuantityBuy + feeQuantityBuy) +
                          " | Sell - " + CommandEconomy.PRICE_FORMAT.format(priceQuantitySell - feeQuantitySell);
         }

         // damaged goods
         if (percentWorth != 1.0f) {
            if (quantity < 2)
               damagedPrice = System.lineSeparator() + "   for held inventory: Sell - " + CommandEconomy.PRICE_FORMAT.format(priceDamagedWares);
            else
               damagedPrice = System.lineSeparator() + "   for " + quantity + " of held inventory: Sell - " + CommandEconomy.PRICE_FORMAT.format(priceDamagedWares);
         }
      }

      // summation
      expectedOutput = wareName + warePrice + wareQuantity + orderPrices + damagedPrice + System.lineSeparator();

      // run the test
      baosOut.reset(); // clear buffer holding console output
      Marketplace.check(PLAYER_ID, WARE_ID, quantity, percentWorth, 1.0f);

      // check console output
      if (!baosOut.toString().equals(expectedOutput)) {
         TEST_OUTPUT.println(testIdentifier +
                            "   unexpected console output: " + baosOut.toString() +
                            "     expected console output: " + expectedOutput);
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether a passed price multiplier was processed correctly
    * for a typical case when buying or selling something.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param playername            player who should be issuing the request
    * @param coordinates           where to find the inventory to be used or null
    * @param ware                  the ware to be purchased
    * @param accountID             identifier for account to be used for the transaction
    * @param pricePercent          price multiplier for transaction
    * @param unitPrice             stop trading if unit price is above (buying) or below (selling) this amount
    * @param accountMoney          what to set funds to for the account to be used
    * @param quantityWare          what to set the ware's quantity available for sale to
    * @param quantityToTrade       how much of the ware should be purchased
    * @param quantityToOffer       how much of the ware should be requested to be purchased
    * @param testNumber            test number to use when printing errors
    * @param attemptManufacturing  if true, try to manufacture the ware when purchasing
    * @param isPurchase            whether the transaction should buy wares
    * @param useInterface          if true, call the Terminal interface's service request function instead of Marketplace's function
    * @return true if an error was discovered
    */
   @SuppressWarnings("unchecked") // for grabbing player inventories
   private static boolean testerTrade(final String playername, final String coordinates, final Ware ware,
                                      final String accountID, final String pricePercent, final String unitPrice,
                                      final float accountMoney, int quantityWare, int quantityToTrade, final int quantityToOffer, final int testNumber,
                                      final boolean attemptManufacturing, final boolean isPurchase, final boolean useInterface) {
      final String  testIdentifier;            // can be added to printed errors to differentiate between tests
            boolean errorFound        = false; // assume innocence until proven guilty
      final boolean PRINT_PREDICTIONS = false; // print all predicted outputs to ensure test cases are being handled correctly

      // repeatedly-used constants
      final UUID    TRADER_ID            = InterfaceTerminal.getPlayerIDStatic(playername); // identifier for the player requesting the transaction
      final String  WARE_ID              = ware.getWareID();
      final String  WARE_ALIAS           = ware.getAlias();
      final boolean SHOULD_USE_ALIAS     = WARE_ALIAS != null && !WARE_ALIAS.isEmpty();
      final boolean IS_ACCOUNT_SPECIFIED = accountID  != null && !accountID.isEmpty() && !accountID.equals(InterfaceTerminal.playername);

      if (testNumber != 0)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // for predicting and tracking changes
      List<String>         parameterBuilder  = new LinkedList<String>();    // used to determine what to pass to the interface function
      String[]             parameters        = null;                        // what to pass to the interface function
      Map<String, Integer> inventory         = InterfaceTerminal.inventory; // where to place wares to be sold
      Account              account           = null;                        // account to be used for the transaction
      float                price             = 0.0f;                        // how much traded wares should cost
      float                pricePercentFloat = 1.0f;                        // how much cost should be adjusted by, if at all
      float                priceUnitFloat;                                  // stop trading if unit price is above (buying) or below (selling) this amount
      float                startingMoney;                                   // how much funds the account started with
      if (isPurchase)
         priceUnitFloat = 1.0f;
      else
         priceUnitFloat = 0.0f;

      // variables used for storing predicted results
      // useful for reusing code for buying and selling
      StringBuilder outputExpected = new StringBuilder(128); // what console output should be
      float         moneyExpected           = 0.0f; // what player's funds should be after the transaction
      float         priceCharged;                   // how much funds changed during the transaction
      int           quantityExpected        = 0;    // predicted quantity for sale after the transaction
      int           quantityTraded;                 // how much quantity for sale changed during the transaction
      int           inventorySpaceAvailable = Config.commandInterface.getInventorySpaceAvailable(TRADER_ID, null) * Config.commandInterface.getStackSize(WARE_ID); // how much the player may hold
      boolean       expectSuccessfulTrade   = true;

      // try to find the inventory to be used
      if (coordinates != null) {
         try {
            inventory = (HashMap<String, Integer>) getInventoryContainer.invoke(NULL_OBJECT, new Object[]{PLAYER_ID, coordinates});
         } catch (Exception e) {
            
         }
         // if coordinates are invalid
         if (inventory == null) {
            inventory = InterfaceTerminal.inventory;
            if (useInterface)
               outputExpected.append("error - invalid quantity");
            else
               outputExpected.append(CommandEconomy.MSG_INVENTORY_MISSING);
            expectSuccessfulTrade = false;
         }
      }
      inventory.clear();

      // try to find the account to be used
      if (IS_ACCOUNT_SPECIFIED)
         account = Account.getAccount(accountID);

      // use the player's personal account if no account is specified
      else
         account = playerAccount;

      if (account == null) {
         account = playerAccount;
         expectSuccessfulTrade = false;
      }
      else {
         if (Float.isNaN(accountMoney))
            account.setMoney(10000.0f);
         else
            account.setMoney(accountMoney);
      }

      // check account permissions
      if (expectSuccessfulTrade && !account.hasAccess(TRADER_ID) &&                                        // player has account permissions
          !(IS_ACCOUNT_SPECIFIED && accountID.equals("$admin$") && InterfaceTerminal.isAnOp(TRADER_ID))) { // player is an op using the admin account
         outputExpected.append(CommandEconomy.MSG_ACCOUNT_DENIED_ACCESS + accountID);
         expectSuccessfulTrade = false;

         // if selling, ensure there are wares to try to use with the account
         if (!isPurchase && quantityToTrade == 0)
            inventory.put(WARE_ID, quantityToOffer);
      }
      startingMoney = account.getMoney();

      // try to parse percent worth parameter
      if (expectSuccessfulTrade && pricePercent != null && !pricePercent.isEmpty()) {
         if (InterfaceTerminal.isAnOp(TRADER_ID)) {
            try {
               pricePercentFloat = Float.parseFloat(pricePercent.substring(1)); // if value is not a float, exception will be thrown here
            } catch (Exception e) { }

            // if percent worth is invalid
            if (Float.isNaN(pricePercentFloat) || pricePercent.substring(1).isEmpty()) {
               outputExpected.append("error - invalid percentage: " + pricePercent.substring(1));
               expectSuccessfulTrade = false;
            }
         }
         // if the player is not an op
         else {
            outputExpected.append("You do not have permission to use % in this command");
            expectSuccessfulTrade = false;
         }
      }

      // if the ware is untradeable, expect an error messages
      // and skip setting up test oracles and conditions
      if (expectSuccessfulTrade) {
         if (ware instanceof WareUntradeable) {
            // set up test oracles
            outputExpected.append(WARE_ID + " is not for sale in that form" + System.lineSeparator());
            expectSuccessfulTrade = false;
         } else {
            // set up test conditions
            ware.setQuantity(quantityWare);

            // if selling, give the player some wares to sell
            if (!isPurchase) {
               inventory.clear();
               // if not specifying quantity and selling everything or
               // offering more than is sold,
               // ensure there are sufficient wares to sell
               if (quantityToOffer == 0 && quantityToTrade != 0)
                  inventory.put(WARE_ID, quantityToTrade);
               // if specifying quantity, give specified quantity
               else
                  inventory.put(WARE_ID, quantityToOffer * 2);
            }

            // if buying, evaluate inventory space
            else {
               // if the player cannot hold any more wares
               if (inventorySpaceAvailable == 0) {
                  outputExpected.append(CommandEconomy.MSG_INVENTORY_NO_SPACE + System.lineSeparator());
                  expectSuccessfulTrade = false;
               }

               // if nothing is available for purchase
               else if (quantityWare == 0) {
                  outputExpected.append("Market is out of " + WARE_ID + System.lineSeparator());
                  expectSuccessfulTrade = false;
               }

               // if nothing is available for purchase
               else if (quantityWare == 0) {
                  outputExpected.append("Market is out of " + WARE_ID + System.lineSeparator());
                  expectSuccessfulTrade = false;
               }
            }

            // try to parse unit price parameter
            if (expectSuccessfulTrade && unitPrice != null && !unitPrice.isEmpty()) {
               try {
                  priceUnitFloat = Float.parseFloat(unitPrice); // if value is not a float, exception will be thrown here
               } catch (Exception e) { }

               // if percent worth is invalid
               if (Float.isNaN(priceUnitFloat))
                  expectSuccessfulTrade = false;
            }

            // set up test oracles
            if (expectSuccessfulTrade && quantityToTrade > 0)
               if (isPurchase)
                  price = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
               else
                  price = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
            if (!Float.isNaN(pricePercentFloat))
               price *= pricePercentFloat;

            // predict rest of console output
            if (TRADER_ID != null) {
               // if something can be bought
               if (expectSuccessfulTrade && quantityToTrade > 0) {
                  if (isPurchase) {
                     outputExpected.append("Bought ").append(quantityToTrade).append(' ');
                     if (SHOULD_USE_ALIAS)
                        outputExpected.append(WARE_ALIAS);
                     else
                        outputExpected.append(WARE_ID);
                     outputExpected.append(" for ").append(CommandEconomy.PRICE_FORMAT.format(price));
                     if (IS_ACCOUNT_SPECIFIED)
                        outputExpected.append(" taken from ").append(accountID);
                  }
                  else {
                     outputExpected.append("Sold ").append(quantityToTrade).append(' ');
                     if (SHOULD_USE_ALIAS)
                        outputExpected.append(WARE_ALIAS);
                     else
                        outputExpected.append(WARE_ID);
                     outputExpected.append(" for ").append(CommandEconomy.PRICE_FORMAT.format(price));
                     if (IS_ACCOUNT_SPECIFIED && !accountID.equals("$admin$"))
                        outputExpected.append(", sent money to ").append(accountID);
                  }
               }
            }
         }
      }

      // if no user is trading, no output should be expected
      if (TRADER_ID == null)
         outputExpected.setLength(0);

      // if trading should be canceled, don't expect any changes
      if (!expectSuccessfulTrade) {
         quantityToTrade = 0;
         price           = 0.0f;
         quantityWare    = ware.getQuantity();
      }

      // predict changes in account funds and quantities available for sale
      if (isPurchase) {
         moneyExpected    = startingMoney - price;
         quantityExpected = quantityWare - quantityToTrade;
      } else {
         moneyExpected    = startingMoney + price;
         quantityExpected = quantityWare + quantityToTrade;
      }

      // switch current player to desired one
      String playernameOrig        = InterfaceTerminal.playername;
      InterfaceTerminal.playername = playername;

      // if necessary, use the Terminal interface's service request functions
      baosOut.reset(); // clear buffer holding console output
      if (useInterface) {
         // set up test parameters
         // /buy <ware_id> <quantity> [max_unit_price] [account_id] [%percent_worth]
         // /sell <ware_id> [<quantity> [min_unit_price] [account_id]] [%percent_worth]
         if (coordinates != null) {
            parameterBuilder.add(playername);
            parameterBuilder.add(coordinates);
         }
         parameterBuilder.add(WARE_ID);
         if (isPurchase ||                          // purchases require specifying max quantity to trade
             (!isPurchase && quantityToOffer != 0)) // when selling, not specifying a quantity means sell everything
            parameterBuilder.add(Integer.toString(quantityToOffer));
         if (unitPrice != null && !unitPrice.isEmpty())
            parameterBuilder.add(unitPrice);
         if (IS_ACCOUNT_SPECIFIED)
            parameterBuilder.add(accountID);
         if (pricePercent != null && !pricePercent.isEmpty())
            parameterBuilder.add(pricePercent);
         if (isPurchase && attemptManufacturing)
            parameterBuilder.add("&craft");
         parameters = new String[parameterBuilder.size()];
         parameters = parameterBuilder.toArray(parameters);

         // run the test
         if (isPurchase)
            InterfaceTerminal.serviceRequestBuy(parameters);
         else
            InterfaceTerminal.serviceRequestSell(parameters);
      }

      // test as normal
      else {
         // if necessary, translate coordinates
         InterfaceCommand.Coordinates coordinatesObject = null;
         if (coordinates != null) {
            coordinatesObject = InterfaceTerminal.getInventoryCoordinates(PLAYER_ID, null, coordinates);

            // if given coordinates are invalid,
            // set coordinates to be invalid
            if (coordinatesObject == null)
               coordinatesObject = new InterfaceCommand.Coordinates(1, 2, 3, 0);
         }

         if (isPurchase)
            Marketplace.buy(TRADER_ID, coordinatesObject, accountID, WARE_ID, quantityToOffer, priceUnitFloat, pricePercentFloat);
         else
            Marketplace.sell(TRADER_ID, coordinatesObject, accountID, WARE_ID, quantityToOffer, priceUnitFloat, pricePercentFloat);
      }

      // reset current player
      InterfaceTerminal.playername = playernameOrig;

      // collect test results
      if (isPurchase) {
         priceCharged   = startingMoney - account.getMoney();
         quantityTraded = quantityWare - ware.getQuantity();
      } else {
         priceCharged   = account.getMoney() - startingMoney;
         quantityTraded = ware.getQuantity() - quantityWare;
      }

      // check ware properties
      if (ware.getQuantity() != quantityExpected) {
         TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + ": " + ware.getQuantity() +
                            ", should be " + quantityExpected +
                            "\n      quantity traded:   " + quantityTraded +
                            "\n      quantity expected: " + quantityToTrade +
                            "\n      diff:              " + (quantityTraded - quantityToTrade));
         if (isPurchase)
            TEST_OUTPUT.println("      unit price:        " + Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_BUY));
         else
            TEST_OUTPUT.println("      unit price:        " + Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_SELL));
         errorFound = true;
      }
      // check account funds
      if (moneyExpected + FLOAT_COMPARE_PRECISION < account.getMoney() ||
          account.getMoney() < moneyExpected - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + account.getMoney() + ", should be " + moneyExpected +
                            "\n      money diff:     " + (account.getMoney() - moneyExpected) + // account is hardcoded to be set to $0.0 or $10,000
                            "\n      price charged:  " + priceCharged +
                            "\n      price expected: " + price +
                            "\n      price diff:     " + (priceCharged - price));
         errorFound = true;
      }
      // check console output
      if (!baosOut.toString().contains(outputExpected.toString())) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         TEST_OUTPUT.println("   expected console output" + testIdentifier + ":   " + outputExpected.toString());
         errorFound = true;
      }

      // print expected values to validate prediction
      if (PRINT_PREDICTIONS) {
         TEST_OUTPUT.println("   expected price" + testIdentifier + ":          " + price +
                             "\n   expected console output" + testIdentifier + ": " + outputExpected);

         if (useInterface)
            if (parameters != null)
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       " + Arrays.toString(parameters));
            else
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       null");
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction fee was processed correctly
    * for a typical case when buying or selling something.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be purchased or sold
    * @param quantityWare          what to set the ware's quantity available for sale to
    * @param quantityInventory     how many units the player's inventory should have when selling
    * @param quantityToTrade       how much of the ware to purchase or sell
    * @param transactionFeesAmount transaction fee rate to charge
    * @param accountMoney          what to set the player's account funds to
    * @param testNumber            test number to use when printing errors; 0 means don't print a number
    * @param shouldOverorder       whether to request trading more than the quantity specified while
    *                              constraining resources to only accommodate the quantity specified
    * @param isPurchase            whether the transaction should buy wares
    * @return true if an error was discovered
    */
   private static boolean testerTradeTransActFee(Ware ware, int quantityWare, int quantityInventory,
                                                 int quantityToTrade, float transactionFeesAmount,
                                                 float accountMoney, int testNumber,
                                                 boolean shouldOverorder, boolean isPurchase) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      String  expectedOutput = "";   // what console output should be
      float   price;                 // how much traded wares should cost
      float   fee;                   // transaction fee to be paid
      float   expectedMoney;         // what seller's funds should be after transferring
      boolean isProfitable   = true; // whether selling will make the seller money

      // repeatedly-used constants
      final String WARE_ID = ware.getWareID();

      if (testNumber != 0)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      if (isPurchase)
         Config.transactionFeeBuying = transactionFeesAmount;
      else {
         Config.transactionFeeSelling = transactionFeesAmount;
         InterfaceTerminal.inventory.clear();
         InterfaceTerminal.inventory.put(ware.getWareID(), quantityInventory);
      }
      ware.setQuantity(quantityWare);
      playerAccount.setMoney(accountMoney);

      // set up test oracles
      Config.chargeTransactionFees = false; // calculate transaction price and fee separately
      if (isPurchase) {
         price                     = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         if (Config.transactionFeeBuying != 0.00f) { // only expect a fee if feature is enabled
            fee                    = Config.transactionFeeBuying;
            if (Config.transactionFeeBuyingIsMult) { // if fee is a multiplier rather than a flat rate
               fee                *= price;

               // if the price and fee percentage have opposite signs, flip the fee's sign
               // so positive rates don't pay out anything and negative rates don't take anything
               if ((Config.transactionFeeBuying > 0.0f && fee < 0.0f) ||
                   (Config.transactionFeeBuying < 0.0f && fee > 0.0f))
                  fee = -fee;
            }
            fee                    = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
         }
         else
            fee                    = 0.0f;
      }
      else {
         price                     = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         if (Config.transactionFeeSelling != 0.00f) { // only expect a fee if feature is enabled
            fee                    = Config.transactionFeeSelling;
            if (Config.transactionFeeSellingIsMult) { // if fee is a multiplier rather than a flat rate
               fee                *= price;

               // if the price and fee percentage have opposite signs, flip the fee's sign
               // so positive rates don't pay out anything and negative rates don't take anything
               if ((Config.transactionFeeSelling > 0.0f && fee < 0.0f) ||
                   (Config.transactionFeeSelling < 0.0f && fee > 0.0f))
                  fee = -fee;
            }
            fee                    = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
         }
         else
            fee                    = 0.0f;
      }
      Config.chargeTransactionFees = true;
      if (shouldOverorder) // if ordering more than can be fulfilled, don't expect to fill the order
            accountMoney           = CommandEconomy.truncatePrice(price + fee + 0.5f); // avoid precision errors using truncation
      // if buying, subtract money from account funds
      if (isPurchase) {
         expectedMoney             = CommandEconomy.truncatePrice(accountMoney - price - fee);
         expectedOutput            = Config.transactionFeeBuyingMsg + CommandEconomy.PRICE_FORMAT.format(fee);
      }
      // evaluate whether selling will make a profit
      // to determine whether to sell anything
      else {
         if (price >= 0.0f) // if the ware's price is negative, assume it is okay to sell at a negative unit price
            isProfitable           = price - fee > 0.0f;
         if (!isProfitable) // if selling won't make a profit, don't sell
            expectedMoney          = accountMoney;
         else {
            expectedMoney          = CommandEconomy.truncatePrice(accountMoney + price - fee);
            expectedOutput         = Config.transactionFeeSellingMsg + CommandEconomy.PRICE_FORMAT.format(fee);
         }
      }

      // test as normal
      baosOut.reset(); // clear buffer holding console output
      if (!shouldOverorder) {
         if (isPurchase)
            Marketplace.buy(PLAYER_ID, null, null, WARE_ID, quantityToTrade, 0.0f, 1.0f);
         else {
            // if the ware's price is negative, assume it is okay to sell at a negative unit price
            if (price < 0.0f)
               Marketplace.sell(PLAYER_ID, null, null, WARE_ID, quantityToTrade, -100.0f, 1.0f);
            else
               Marketplace.sell(PLAYER_ID, null, null, WARE_ID, quantityToTrade, 0.1f, 1.0f);
         }
      }

      // order more than can be fulfilled
      else {
         playerAccount.setMoney(accountMoney); // constrain resources
         if (isPurchase)
            Marketplace.buy(PLAYER_ID, null, null, WARE_ID, quantityToTrade * 2, 0.0f, 1.0f);
         else {
            // if the ware's price is negative, assume it is okay to sell at a negative unit price
            if (price < 0.0f)
               Marketplace.sell(PLAYER_ID, null, null, WARE_ID, quantityInventory, -100.0f, 1.0f);
            else
               Marketplace.sell(PLAYER_ID, null, null, WARE_ID, quantityInventory, 0.1f, 1.0f);
         }
      }

      // check ware properties
      if (isPurchase)
         quantityToTrade = -quantityToTrade; // to ease checking ware's quantity available for sale
      if (isProfitable && ware.getQuantity() != quantityWare + quantityToTrade) {
         TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + ": " + ware.getQuantity() +
                            ", should be " + (quantityWare + quantityToTrade));
         errorFound = true;
      }
      // check account funds
      if (expectedMoney + FLOAT_COMPARE_PRECISION < playerAccount.getMoney() ||
          playerAccount.getMoney() < expectedMoney - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + playerAccount.getMoney() + ", should be " + expectedMoney +
                            "\n      price + fee charged: " + (accountMoney - playerAccount.getMoney()) +
                            "\n      price expected:      " + price +
                            "\n      diff:                " + (accountMoney - playerAccount.getMoney() - price) +
                            "\n      fee expected:        " + fee);
         errorFound = true;
      }
      // check console output
      if (isProfitable && fee != 0.0f && !baosOut.toString().contains(expectedOutput)) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      } else if ((!isProfitable || fee == 0.0f) && (baosOut.toString().contains(Config.transactionFeeBuyingMsg) || baosOut.toString().contains(Config.transactionFeeSellingMsg))) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction fee was processed correctly
    * for a typical case when buying something.
    * Prints errors, if found.
    * <p>
    * Assumes ware quantity and account funds should be high.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be purchased
    * @param quantityToTrade       how much of the ware to purchase
    * @param transactionFeesAmount transaction fee rate to charge
    * @param testNumber            test number to use when printing errors; 0 means don't print a number
    * @return true if an error was discovered
    */
   private static boolean testerBuyTransActFee(Ware ware, int quantityToTrade,
                                             float transactionFeesAmount, int testNumber) {
      return testerTradeTransActFee(ware, Config.quanEquilibrium[ware.getLevel()], 0, quantityToTrade,
                                    transactionFeesAmount, 10000.0f, testNumber, false, true);
   }

   /**
    * Evaluates whether a transaction fee was deposited
    * into the fee collection account correctly.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be purchased
    * @param quantityToTrade       how much of the ware to purchase
    * @param transactionFeesAmount transaction fee rate to charge
    * @param accountMoney          how much money the fee collection account should start with
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testerBuyAccountTransActFee(Ware ware, int quantityToTrade,
                                                    float transactionFeesAmount, float accountMoney,
                                                    int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      Account account;     // account collecting fees
      float price;         // how much traded wares should cost
      float fee;           // transaction fee to be paid
      float expectedMoney; // what account funds should be after transferring

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeBuying = transactionFeesAmount;
      ware.setQuantity(Config.quanEquilibrium[ware.getLevel()]);
      account = Account.getAccount(Config.transactionFeesAccount);
      if (account != null) // account may be created upon collecting transaction fees
         account.setMoney(accountMoney);

      // set up test oracles
      Config.chargeTransactionFees = false; // calculate transaction price and fee separately
      price                        = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
      Config.chargeTransactionFees = true;
      if (Config.transactionFeeBuying != 0.00f) {
         fee                       = Config.transactionFeeBuying;
         if (Config.transactionFeeBuyingIsMult)
            fee                   *= price;
         fee                       = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
      }
      else
         fee                       = 0.0f;
      expectedMoney                = CommandEconomy.truncatePrice(accountMoney + fee);
      if (expectedMoney < 0.0f) // if negative fees should not be paid
         expectedMoney = accountMoney;

      baosOut.reset(); // clear buffer holding console output
      Marketplace.buy(PLAYER_ID, null, CommandEconomy.ACCOUNT_ADMIN, ware.getWareID(), quantityToTrade, 0.0f, 1.0f);

      // check account existence
      if (account == null) { // account may be created upon collecting transaction fees
         account = Account.getAccount(Config.transactionFeesAccount);

         // if the account still doesn't exist
         if (account == null) {
            TEST_OUTPUT.println("   account not found" + testIdentifier + ": " + Config.transactionFeesAccount);
            return true;
         }
      }
      // check account funds
      if (expectedMoney + FLOAT_COMPARE_PRECISION < account.getMoney() ||
          account.getMoney() < expectedMoney - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + account.getMoney() + ", should be " + expectedMoney +
                            "\n      fee charged:  " + (account.getMoney() - accountMoney) +
                            "\n      fee expected: " + fee);
         errorFound = true;
      }
      // check console output
      if (expectedMoney == accountMoney && // if negative fee is unpaid, don't display a message
          baosOut.toString().contains(Config.transactionFeeBuyingMsg)) {
         TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction was processed correctly
    * for a typical case when buying or manufacturing something.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware1                 the ware to be manufactured
    * @param ware2                 possible component or null
    * @param ware3                 possible component or null
    * @param ware4                 possible component or null
    * @param accountID             identifier for account to be used for the transaction
    * @param unitPrice             stop trading if unit price is above (buying) or below (selling) this amount
    * @param quantityWare1         what to set the first ware's quantity available for sale to
    * @param quantityWare2         what to set the second ware's quantity available for sale to
    * @param quantityWare3         what to set the third ware's quantity available for sale to
    * @param quantityWare4         what to set the fourth ware's quantity available for sale to
    * @param quantityToTrade1      how much of the first ware to purchase, including manufacturing
    * @param quantityToTrade2      how much of the second ware to purchase
    * @param quantityToTrade3      how much of the third ware to purchase
    * @param quantityToTrade4      how much of the fourth ware to purchase
    * @param quantityToOffer       how much of the ware to try to buy
    * @param testNumber            test number to use when printing errors; 0 means don't print a number
    * @param useInterface          if true, call the Terminal interface's service request function instead of Marketplace's function
    * @return true if an error was discovered
    */
   private static boolean testerBuyManufacturing(Ware ware1, Ware ware2, Ware ware3, Ware ware4,
                                                 String accountID, float unitPrice,
                                                 int quantityWare1, int quantityWare2, int quantityWare3, int quantityWare4,
                                                 int quantityToTrade1, int quantityToTrade2, int quantityToTrade3, int quantityToTrade4,
                                                 int quantityToOffer, int testNumber, boolean useInterface) {
            String  testIdentifier;            // can be added to printed errors to differentiate between tests
            boolean errorFound        = false; // assume innocence until proven guilty
      final boolean PRINT_PREDICTIONS = false; // print all predicted outputs to ensure test cases are being handled correctly

      // repeatedly-used constants
      final String  WARE_ID              = ware1.getWareID();
      final String  WARE_ALIAS           = ware1.getAlias();
      final boolean SHOULD_USE_ALIAS     = WARE_ALIAS != null && !WARE_ALIAS.isEmpty();
      final boolean IS_ACCOUNT_SPECIFIED = accountID  != null && !accountID.isEmpty() && !accountID.equals(InterfaceTerminal.playername);
      final boolean IS_WARE_2_TRADEABLE  = ware2 != null && !(ware2 instanceof WareUntradeable);
      final boolean IS_WARE_3_TRADEABLE  = ware3 != null && !(ware3 instanceof WareUntradeable);
      final boolean IS_WARE_4_TRADEABLE  = ware4 != null && !(ware4 instanceof WareUntradeable);

      if (testNumber != 0)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // for predicting and tracking changes
      LinkedList<String> parameterBuilder;                        // used to determine what to pass to the interface function
      String[]           parameters              = null;          // what to pass to the interface function
      String             expectedOutput          = null;          // what console output should be
      Account            account                 = null;          // account to be used for the transaction
      Field              fYield;                                  // for checking whether a recipe produces multiple units per iteration
      float              startingMoney           = 10000.0f;      // what to set funds to the account to be used
      float              expectedMoney           = startingMoney; // what seller's funds should be after the transaction
      float              price                   = 0.0f;          // how much traded wares should cost
      int                inventorySpaceAvailable = Config.commandInterface.getInventorySpaceAvailable(PLAYER_ID, null) * Config.commandInterface.getStackSize(WARE_ID); // how much the player may hold
      int                yield;                                   // how many units a recipe produces per iteration
      int                quantityWare1Expected   = quantityWare1; // how much of the first ware should be available for sale after the transaction

      // set up test conditions
      if (ware1 != null) {
         if (!(ware1 instanceof WareUntradeable))
            ware1.setQuantity(quantityWare1);
      }
      else {
            TEST_OUTPUT.println("   error - ware1 cannot be null" + testIdentifier);
            return true;
      }
      if (IS_WARE_2_TRADEABLE)
         ware2.setQuantity(quantityWare2);
      if (IS_WARE_3_TRADEABLE)
         ware3.setQuantity(quantityWare3);
      if (IS_WARE_4_TRADEABLE)
         ware4.setQuantity(quantityWare4);

      // set up test oracles
      if (Config.buyingOutOfStockWaresAllowed &&
          // check whether ware1 is manufacturable
          ware1.hasComponents() && !(ware1 instanceof WareUntradeable) &&
          // check whether there is any inventory space
          inventorySpaceAvailable > 0 &&
          // check whether ware2 is preventing manufacturing
          (ware2 == null ||                                   // is not specified or
           (ware2 != null &&                                  // is specified
            !(IS_WARE_2_TRADEABLE && quantityWare2 == 0))) && // and has stock
          // check whether ware3 is preventing manufacturing
          (ware3 == null ||                                   // is not specified or
           (ware3 != null &&                                  // is specified
            !(IS_WARE_3_TRADEABLE && quantityWare3 == 0))) && // and has stock
          // check whether ware4 is preventing manufacturing
          (ware4 == null ||                                   // is not specified or
           (ware4 != null &&                                  // is specified
            !(IS_WARE_4_TRADEABLE && quantityWare4 == 0))))   // and has stock
      {
         // adjust amount purchased for inventory space
         if (inventorySpaceAvailable < quantityToTrade1)
            quantityToTrade1 = inventorySpaceAvailable;

         // predict price
         if (quantityToTrade1 != 0) {
            if (quantityWare1 != 0) {
               // only buy up as much as is available
               if (quantityToTrade1 > quantityWare1) {
                  // only apply manufacturing fee multipliers to the quantity that was manufactured
                  price = Marketplace.getPrice(PLAYER_ID, ware1, quantityWare1, Marketplace.PriceType.CURRENT_BUY) +
                          (Marketplace.getPrice(null, ware1, (quantityToTrade1 - quantityWare1), Marketplace.PriceType.CEILING_BUY) * Config.buyingOutOfStockWaresPriceMult);
                  quantityWare1Expected = 0;
               }
               else {
                  price = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade1, Marketplace.PriceType.CURRENT_BUY);
                  quantityWare1Expected = quantityWare1 - quantityToTrade1;
               }
            } else
               price = Marketplace.getPrice(null, ware1, quantityToTrade1, Marketplace.PriceType.CEILING_BUY) * Config.buyingOutOfStockWaresPriceMult;
         }
         // truncate price for neatness and avoiding problematic rounding
         price         = CommandEconomy.truncatePrice(price);
         expectedMoney = startingMoney - price;

         // predict quantity available for sale
         // Some recipes yield multiple units.
         // If only some units are purchased,
         // the remainder is made available for sale on the market.
         try {
            // grab yield
            fYield = Ware.class.getDeclaredField("yield");
            fYield.setAccessible(true);
            yield = (int) fYield.get(ware1);

            // check for high yield
            if (yield != 1) {
               // if all produced units will not be purchased,
               // expect leftovers to be available for sale
               if (quantityToTrade1 % yield != 0)
                  quantityWare1Expected = quantityToTrade1 % yield;
            }
         } catch (Exception e) {
            TEST_OUTPUT.println("   error - failed to obtain yield" + testIdentifier);
            e.printStackTrace();
            return true;
         }

         // predict console output
         if (quantityToTrade1 > 0) {
            if (SHOULD_USE_ALIAS)
               expectedOutput  = "Bought " + Integer.toString(quantityToTrade1) + " " + WARE_ALIAS + " for " + CommandEconomy.PRICE_FORMAT.format(price);
            else
               expectedOutput  = "Bought " + Integer.toString(quantityToTrade1) + " " + WARE_ID + " for " + CommandEconomy.PRICE_FORMAT.format(price);
            if (IS_ACCOUNT_SPECIFIED)
               expectedOutput += " taken from " + accountID;
         }
      }

      // if manufacturing is disabled or impossible
      else
         return testerTrade(InterfaceTerminal.playername, null, ware1, accountID, null, Float.toString(unitPrice), Float.NaN, quantityWare1, quantityToTrade1, quantityToOffer, testNumber, true, true, useInterface);

      // try to find the account to be used
      if (IS_ACCOUNT_SPECIFIED)
         account = Account.getAccount(accountID);

      // use the player's personal account if no account is specified
      else
         account = playerAccount;

      // give the account sufficient funds
      if (account != null)
         account.setMoney(startingMoney);
      else {
         TEST_OUTPUT.println("   error - account not found" + testIdentifier);
         return true;
      }

      // if necessary, use the Terminal interface's service request function to make the purchase
      baosOut.reset(); // clear buffer holding console output
      if (useInterface) {
         // Expected Format: <ware_id> <quantity> [max_unit_price] [account_id]
         parameterBuilder = new LinkedList<String>();
         parameterBuilder.add(ware1.getWareID());
         parameterBuilder.add(String.valueOf(quantityToOffer));
         if (unitPrice != 0.0f)
            parameterBuilder.add(String.valueOf(unitPrice));
         if (IS_ACCOUNT_SPECIFIED)
            parameterBuilder.add(accountID);

         // convert arguments to a string array
         parameters = new String[parameterBuilder.size()];
         parameters = parameterBuilder.toArray(parameters);

         InterfaceTerminal.serviceRequestBuy(parameters);
      }

      // test as normal
      else
         Marketplace.buy(PLAYER_ID, null, accountID, WARE_ID, quantityToOffer, unitPrice, 1.0f);

      // check ware properties
      if (ware1.getQuantity() != quantityWare1Expected && quantityWare1Expected != -2) {
         TEST_OUTPUT.println("   unexpected quantity for " + WARE_ID + testIdentifier + ": " + ware1.getQuantity() +
                               ", should be " + quantityWare1Expected);
         errorFound = true;
      }
      if (IS_WARE_2_TRADEABLE && ware2.getQuantity() != quantityWare2 - quantityToTrade2) {
         TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                               ", should be " + (quantityWare2 - quantityToTrade2));
         errorFound = true;
      }
      if (IS_WARE_3_TRADEABLE && ware3.getQuantity() != quantityWare3 - quantityToTrade3) {
         TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                               ", should be " + (quantityWare3 - quantityToTrade3));
         errorFound = true;
      }
      if (IS_WARE_4_TRADEABLE && ware4.getQuantity() != quantityWare4 - quantityToTrade4) {
         TEST_OUTPUT.println("   unexpected quantity for " + ware4.getWareID() + testIdentifier + ": " + ware4.getQuantity() +
                               ", should be " + (quantityWare4 - quantityToTrade4));
         errorFound = true;
      }
      // check account funds
      if (expectedMoney + (FLOAT_COMPARE_PRECISION * 2) < account.getMoney() ||
          account.getMoney() < expectedMoney - (FLOAT_COMPARE_PRECISION * 2)) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + account.getMoney() + ", should be " + expectedMoney +
                             "\n      price charged:       " + (startingMoney - account.getMoney()) +
                             "\n      price expected:      " + price +
                             "\n      diff:                " + (startingMoney - account.getMoney() - price));
         errorFound = true;
      }
      // check console output
      if (expectedOutput != null && !baosOut.toString().contains(expectedOutput)) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         TEST_OUTPUT.println("   expected console output" + testIdentifier + ":   " + expectedOutput);
         errorFound = true;
      }

      // print expected values to validate prediction
      if (PRINT_PREDICTIONS) {
         TEST_OUTPUT.println("   expected price" + testIdentifier + ":          " + price +
                             "\n   expected console output" + testIdentifier + ": " + expectedOutput);

         if (useInterface)
            if (parameters != null)
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       " + Arrays.toString(parameters));
            else
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       null");
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction fee was processed correctly
    * for a typical case of selling something.
    * Prints errors, if found.
    * <p>
    * Assumes inventory quantity and account funds should be high.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be purchased
    * @param quantityToTrade       how much of the ware to purchase
    * @param transactionFeesAmount transaction fee rate to charge
    * @param testNumber            test number to use when printing errors; 0 means don't print a number
    * @return true if an error was discovered
    */
   private static boolean testerSellTransActFee(Ware ware, int quantityToTrade,
                                              float transactionFeesAmount, int testNumber) {
      return testerTradeTransActFee(ware, Config.quanEquilibrium[ware.getLevel()], Config.quanEquilibrium[ware.getLevel()], quantityToTrade,
                                    transactionFeesAmount, 1000.0f, testNumber, false, false);
   }

   /**
    * Evaluates whether a transaction fee outputted the correct error message
    * for a typical case of selling something.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be sold
    * @param quantityWare          what to set the ware's quantity available for sale to
    * @param quantityToTrade       how much of the ware to sell
    * @param quantityToOffer       how much of the ware to try to sell
    * @param minUnitPrice          seller's minimum acceptable unit price
    * @param transactionFeesAmount transaction fee rate to charge
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testerSellErrorMsgsTransActFee(Ware ware, int quantityWare,
                                                       int quantityToTrade, int quantityToOffer,
                                                       float minUnitPrice, float transactionFeesAmount,
                                                       int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      float   price;               // how much traded wares should cost
      float   fee;                 // transaction fee to be paid
      boolean isProfitable = true; // whether selling will make the seller money

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeSelling = transactionFeesAmount;
      ware.setQuantity(quantityWare);
      InterfaceTerminal.inventory.clear();
      InterfaceTerminal.inventory.put(ware.getWareID(), quantityToOffer);

      // set up test oracles
      Config.chargeTransactionFees = false;  // calculate transaction price and fee separately
      price                        = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
      Config.chargeTransactionFees = true;
      if (Config.transactionFeeSelling != 0.00f) { // only expect a fee if feature is enabled
         fee                       = Config.transactionFeeSelling;
         if (Config.transactionFeeSellingIsMult)       // if fee is a multiplier rather than a flat rate
            fee                   *= price;
         fee                       = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
      }
      else
         fee                       = 0.0f;
      isProfitable                 = price - fee > 0.0f || minUnitPrice < 0.0f; // if a negative price is acceptable, then no profit is acceptable

      // test as normal
      baosOut.reset(); // clear buffer holding console output
      Marketplace.sell(PLAYER_ID, null, null, ware.getWareID(), quantityToOffer, minUnitPrice, 1.0f);

      // check ware properties
      if (ware.getQuantity() != quantityWare + quantityToTrade) {
         TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + ": " + ware.getQuantity() +
                            ", should be " + (quantityWare + quantityToTrade));
         errorFound = true;
      }
      // check console output
      // check whether transaction applied message should be and is included
      if (isProfitable && fee != 0.0f && !baosOut.toString().contains(Config.transactionFeeSellingMsg + CommandEconomy.PRICE_FORMAT.format(fee))) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                             "   expected: \"Transaction fee applied\"");
         errorFound = true;
      } else if ((!isProfitable || fee == 0.0f) && baosOut.toString().contains(Config.transactionFeeSellingMsg)) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                             "   unexpected: \"Transaction fee applied\"");
         errorFound = true;
      }

      // check whether the appropriate message is given if the ware is free
      if (!isProfitable) {
         // if the transaction fee makes the ware unprofitable, say so
         if (fee > 0.0f) {
            if (!baosOut.toString().contains("Transaction fee is too high to make a profit")) {
               TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                                   "   expected: \"Transaction fee is too high to make a profit\"");
               errorFound = true;
            }
         }

         // if the transaction is unprofitable and not because of the fee,
         // don't blame the fee
         else {
            if (baosOut.toString().contains("Transaction fee is too high to make a profit")) {
               TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                                    "   unexpected: \"Transaction fee is too high to make a profit\"");
               errorFound = true;
            }
         }
      }

      // check for loss warnings when accepting losses
      else {
         if (baosOut.toString().contains("Transaction fee is too high to make a profit")) {
            TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                                 "   unexpected: \"Transaction fee is too high to make a profit\"");
            errorFound = true;
         }
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction fee was deposited
    * into the fee collection account correctly.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be purchased
    * @param quantityToTrade       how much of the ware to sell
    * @param transactionFeesAmount transaction fee rate to charge
    * @param accountMoney          how much money the fee collection account should start with
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testerSellAccountTransActFee(Ware ware, int quantityToTrade,
                                                     float transactionFeesAmount, float accountMoney,
                                                     int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      Account account;     // account collecting fees
      float price;         // how much traded wares should cost
      float fee;           // transaction fee to be paid
      float expectedMoney; // what account funds should be after transferring

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeSelling = transactionFeesAmount;
      ware.setQuantity(Config.quanEquilibrium[ware.getLevel()]);
      account = Account.getAccount(Config.transactionFeesAccount);
      if (account != null) // account may be created upon collecting transaction fees
         account.setMoney(accountMoney);
      InterfaceTerminal.inventory.clear();
      InterfaceTerminal.inventory.put(ware.getWareID(), quantityToTrade);

      // set up test oracles
      Config.chargeTransactionFees = false; // calculate transaction price and fee separately
      price                        = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
      Config.chargeTransactionFees = true;
      fee                          = Config.transactionFeeSelling;
      if (Config.transactionFeeSellingIsMult)
         fee                      *= price;
      fee                          = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
      expectedMoney                = CommandEconomy.truncatePrice(accountMoney + fee);
      if (expectedMoney < 0.0f) // if negative fees should not be paid
         expectedMoney = accountMoney;

      baosOut.reset(); // clear buffer holding console output
      Marketplace.sell(PLAYER_ID, null, null, ware.getWareID(), quantityToTrade, 0.1f, 1.0f);

      // check account existence
      if (account == null) { // account may be created upon collecting transaction fees
         account = Account.getAccount(Config.transactionFeesAccount);

         // if the account still doesn't exist
         if (account == null) {
            TEST_OUTPUT.println("   account not found" + testIdentifier + ": " + Config.transactionFeesAccount);
            return true;
         }
      }
      // check account funds
      if (expectedMoney + FLOAT_COMPARE_PRECISION < account.getMoney() ||
          account.getMoney() < expectedMoney - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + account.getMoney() + ", should be " + expectedMoney +
                            "\n      fee charged:  " + (account.getMoney() - accountMoney) +
                            "\n      fee expected: " + fee);
         errorFound = true;
      }
      // check console output
      if (expectedMoney == accountMoney && // if negative fee is unpaid, don't display a message
          baosOut.toString().contains(Config.transactionFeeSellingMsg)) {
         TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether a passed price multiplier was processed correctly
    * for a typical case of selling all of an inventory.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param playername            player who should be issuing the request
    * @param coordinates           where to find the inventory to be used or null
    * @param accountID             identifier for account to be used for the transaction
    * @param pricePercent          price multiplier for transaction
    * @param ware1                 ware to be sold or null
    * @param ware2                 ware to be sold or null
    * @param ware3                 ware to be sold or null
    * @param quantityToTrade1      how much of the first ware to sell
    * @param quantityToTrade2      how much of the second ware to sell
    * @param quantityToTrade3      how much of the third ware to sell
    * @param testNumber            test number to use when printing errors
    * @param useInterface          if true, call the Terminal interface's service request function instead of Marketplace's function
    * @return true if an error was discovered
    */
   @SuppressWarnings("unchecked") // for grabbing player inventories
   private static boolean testerSellAll(final String playername, final String coordinates,
                                        final String accountID, final String pricePercent,
                                        final Ware ware1, final Ware ware2, final Ware ware3,
                                        int quantityToTrade1, int quantityToTrade2, int quantityToTrade3,
                                        final int testNumber, final boolean useInterface) {
      final String  testIdentifier;            // can be added to printed errors to differentiate between tests
            boolean errorFound        = false; // assume innocence until proven guilty
      final boolean PRINT_PREDICTIONS = false; // print all predicted outputs to ensure test cases are being handled correctly

      if (testNumber != 0)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // variables used in preparing the test and predicting results
      List<String>         parameterBuilder;              // used to determine what to pass to the interface function
      Map<String, Integer> inventory         = InterfaceTerminal.inventory; // where to place wares to be sold
      String[]             parameters        = null;      // what to pass to the interface function
      Account              account           = null;      // account to be used for the transaction
      float                price1            = 0.0f;      // profits from each ware
      float                price2            = 0.0f;
      float                price3            = 0.0f;
      float                priceTotal        = 0.0f;
      int                  quantityWare1     = 0;         // wares' quantities available for sale
      int                  quantityWare2     = 0;
      int                  quantityWare3     = 0;
      boolean              sellWare1         = true;      // don't sell if it will not make money
      boolean              sellWare2         = true;
      boolean              sellWare3         = true;
      float                pricePercentFloat = Float.NaN; // how much cost should be adjusted by, if at all

      // repeatedly-used constants
      final UUID    TRADER_ID            = InterfaceTerminal.getPlayerIDStatic(playername); // identifier for the player requesting the transaction
      final boolean IS_ACCOUNT_SPECIFIED = accountID != null && !accountID.isEmpty();

      // variables used for storing predicted results
      StringBuilder outputExpected        = new StringBuilder(128); // what console output should be
      float         moneyExpected;                                  // what player's funds should be after the transaction
      int           quantityTradedExpected;                         // how much quantities for sale should change during the transaction
      boolean       expectSuccessfulTrade = true;

      // try to find the inventory to be used
      if (coordinates != null) {
         try {
            inventory = (HashMap<String, Integer>) getInventoryContainer.invoke(NULL_OBJECT, new Object[]{PLAYER_ID, coordinates});
         } catch (Exception e) { }
         // if coordinates are invalid
         if (inventory == null) {
            inventory = InterfaceTerminal.inventory;
            if (useInterface)
               outputExpected.append(CommandEconomy.ERROR_INVENTORY_DIR);
            else
               outputExpected.append(CommandEconomy.MSG_INVENTORY_MISSING);
            expectSuccessfulTrade = false;
         }
      }
      inventory.clear();

      // try to find the account to be used
      if (IS_ACCOUNT_SPECIFIED)
         account = Account.getAccount(accountID);

      // use the player's personal account if no account is specified
      else
         account = playerAccount;

      if (account == null) {
         account = playerAccount;
         expectSuccessfulTrade = false;
      }
      account.setMoney(0.0f); // clear the account's funds

      // check account permissions
      if (expectSuccessfulTrade && !account.hasAccess(TRADER_ID) &&                                        // player has account permissions
          !(IS_ACCOUNT_SPECIFIED && accountID.equals("$admin$") && InterfaceTerminal.isAnOp(TRADER_ID))) { // player is an op using the admin account
         outputExpected.append(CommandEconomy.MSG_ACCOUNT_DENIED_ACCESS + accountID);
         expectSuccessfulTrade = false;
      }

      // set up wares' conditions and test oracles
      if (ware1 != null) {
         quantityWare1 = Config.quanEquilibrium[ware1.getLevel()];
         ware1.setQuantity(quantityWare1);
         inventory.put(ware1.getWareID(), quantityToTrade1);

         // don't sell if there is no profit to be made
         price1       = Marketplace.getPrice(PLAYER_ID, ware1, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price1 > 0.0001f)
            price1    = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         else {
            sellWare1        = false;
            price1           = 0.0f;
            quantityToTrade1 = 0;
         }
      }
      if (ware2 != null) {
         quantityWare2 = Config.quanEquilibrium[ware2.getLevel()];
         ware2.setQuantity(quantityWare2);
         inventory.put(ware2.getWareID(), quantityToTrade2);

         // don't sell if there is no profit to be made
         price2       = Marketplace.getPrice(PLAYER_ID, ware2, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price2 > 0.0001f)
            price2    = Marketplace.getPrice(PLAYER_ID, ware2, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
         else {
            sellWare2        = false;
            price2           = 0.0f;
            quantityToTrade2 = 0;
         }
      }
      if (ware3 != null) {
         quantityWare3 = Config.quanEquilibrium[ware3.getLevel()];
         ware3.setQuantity(quantityWare3);
         inventory.put(ware3.getWareID(), quantityToTrade3);

         // don't sell if there is no profit to be made
         price3       = Marketplace.getPrice(PLAYER_ID, ware3, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price3 > 0.0001f)
            price3    = Marketplace.getPrice(PLAYER_ID, ware3, quantityToTrade3, Marketplace.PriceType.CURRENT_SELL);
         else {
            sellWare3        = false;
            price3           = 0.0f;
            quantityToTrade3 = 0;
         }
      }

      // set up test oracles
      parameterBuilder = new LinkedList<String>();

      // try to parse percent worth parameter
      if (pricePercent != null && !pricePercent.isEmpty()) {
         try {
            pricePercentFloat = Float.parseFloat(pricePercent.substring(1)); // if value is not a float, exception will be thrown here
         } catch (Exception e) { }

         if (expectSuccessfulTrade) {
            if (InterfaceTerminal.isAnOp(TRADER_ID)) {
               // if percent worth is invalid
               if (Float.isNaN(pricePercentFloat) || pricePercent.substring(1).isEmpty()) {
                  outputExpected.append("error - invalid percentage: " + pricePercent.substring(1));
                  expectSuccessfulTrade = false;
               }

               // if percent worth is valid
               else {
                  price1 *= pricePercentFloat;
                  price2 *= pricePercentFloat;
                  price3 *= pricePercentFloat;
               }
            }
            // if the player is not an op
            else {
               outputExpected.append("You do not have permission to use % in this command");
               expectSuccessfulTrade = false;
            }
         }
      }
      priceTotal    = CommandEconomy.truncatePrice(price1 + price2 + price3); // avoid precision errors using truncation
      moneyExpected = priceTotal;

      // /sellall doesn't sell for free
      if (priceTotal <= 0.0f)
         expectSuccessfulTrade = false;
      quantityTradedExpected = quantityToTrade1 + quantityToTrade2 + quantityToTrade3;

      // evaluate whether trading should occur
      if (!expectSuccessfulTrade) {
         moneyExpected          = 0.0f;
         price1                 = 0.0f;
         price2                 = 0.0f;
         price3                 = 0.0f;
         quantityToTrade1       = 0;
         quantityToTrade2       = 0;
         quantityToTrade3       = 0;
         quantityTradedExpected = 0;
      }

      // predict rest of console output
      if (TRADER_ID != null) {
         if (outputExpected.length() == 0 && // no error message yet
             quantityTradedExpected > 0) {   // something should be ordered
            outputExpected.append("Sold ").append(quantityTradedExpected).append(" items for ").append(CommandEconomy.PRICE_FORMAT.format(priceTotal));
            if (IS_ACCOUNT_SPECIFIED && !accountID.equals("$admin$"))
               outputExpected.append(", sent money to ").append(accountID);
         }
      }
      // if no user is trading, no output should be expected
      else
         outputExpected.setLength(0);

      // if necessary, use the Terminal interface's service request functions
      baosOut.reset(); // clear buffer holding console output
      if (useInterface) {
         // set up test parameters
         // /sellall [account_id] [%percent_worth]
         // /sellall <player_name> <inventory_direction> [account_id]
         if (coordinates != null) {
            parameterBuilder.add(playername);
            parameterBuilder.add(coordinates);
         }
         if (IS_ACCOUNT_SPECIFIED)
            parameterBuilder.add(accountID);
         if (pricePercent != null && !pricePercent.isEmpty())
            parameterBuilder.add(pricePercent);
         parameters = new String[parameterBuilder.size()];
         parameters = parameterBuilder.toArray(parameters);

         // run the test
         String playernameOrig        = InterfaceTerminal.playername;
         InterfaceTerminal.playername = playername;
         InterfaceTerminal.serviceRequestSellAll(parameters);
         InterfaceTerminal.playername = playernameOrig;
      }

      // test as normal
      else {
         // if necessary, translate coordinates
         InterfaceCommand.Coordinates coordinatesObject = null;
         if (coordinates != null) {
            coordinatesObject = InterfaceTerminal.getInventoryCoordinates(PLAYER_ID, null, coordinates);

            // if given coordinates are invalid,
            // set coordinates to be invalid
            if (coordinatesObject == null)
               coordinatesObject = new InterfaceCommand.Coordinates(1, 2, 3, 0);
         }

         Marketplace.sellAll(TRADER_ID, coordinatesObject, formatInventory(inventory), accountID, pricePercentFloat);
      }

      // check ware properties
      if (quantityWare1 != 0) {
         if (sellWare1) {
            if (ware1.getQuantity() != quantityWare1 + quantityToTrade1) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware1.getWareID() + testIdentifier + ": " + ware1.getQuantity() +
                                  ", should be " + (quantityWare1 + quantityToTrade1));
               errorFound = true;
            }
         } else {
            if (ware1.getQuantity() != quantityWare1) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware1.getWareID() + testIdentifier + ": " + ware1.getQuantity() +
                                  ", should be " + quantityWare1);
               errorFound = true;
            }
         }
      }
      if (quantityWare2 != 0) {
         if (sellWare2) {
            if (ware2.getQuantity() != quantityWare2 + quantityToTrade2) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                                  ", should be " + (quantityWare2 + quantityToTrade2));
               errorFound = true;
            }
         } else {
            if (ware2.getQuantity() != quantityWare2) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                                  ", should be " + quantityWare2);
               errorFound = true;
            }
         }
      }
      if (quantityWare3 != 0) {
         if (sellWare3) {
            if (ware3.getQuantity() != quantityWare3 + quantityToTrade3) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                                  ", should be " + (quantityWare3 + quantityToTrade3));
               errorFound = true;
            }
         } else {
            if (ware3.getQuantity() != quantityWare3) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                                  ", should be " + quantityWare3);
               errorFound = true;
            }
         }
      }
      // check account funds
      if (moneyExpected + FLOAT_COMPARE_PRECISION < account.getMoney() ||
          account.getMoney() < moneyExpected - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + account.getMoney() + ", should be " + moneyExpected +
                            "\n      price paid:     " + account.getMoney() +
                            "\n      price expected: " + priceTotal +
                            "\n      price diff:     " + (account.getMoney() - priceTotal));
         errorFound = true;
      }
      // check console output
      if (!baosOut.toString().contains(outputExpected.toString())) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         TEST_OUTPUT.println("   expected console output" + testIdentifier + ":   " + outputExpected.toString());
         errorFound = true;
      }

      // print expected values to validate prediction
      if (PRINT_PREDICTIONS) {
         TEST_OUTPUT.println("   expected money" + testIdentifier + ":          " + moneyExpected +
                             "\n   expected console output" + testIdentifier + ": " + outputExpected);

         if (useInterface)
            if (parameters != null)
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       " + Arrays.toString(parameters));
            else
               TEST_OUTPUT.println("\n   passed parameters" + testIdentifier + ":       null");
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction fee was processed correctly
    * for a typical case of selling all of an inventory.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware1                 ware to be sold or null
    * @param ware2                 ware to be sold or null
    * @param ware3                 ware to be sold or null
    * @param quantityToTrade1      how much of the first ware to sell
    * @param quantityToTrade2      how much of the second ware to sell
    * @param quantityToTrade3      how much of the third ware to sell
    * @param transactionFeesAmount transaction fee rate to charge
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testerSellTransActFeeAll(Ware ware1, Ware ware2, Ware ware3,
                                                 int quantityToTrade1, int quantityToTrade2, int quantityToTrade3,
                                                 float transactionFeesAmount,
                                                 int testNumber, boolean printTestNumber) {
      String  testIdentifier;        // can be added to printed errors to differentiate between tests
      boolean errorFound    = false; // assume innocence until proven guilty

      float   price1        = 0.0f;  // profits from each ware
      float   price2        = 0.0f;
      float   price3        = 0.0f;
      int     quantityWare1 = 0;     // wares' quantities available for sale
      int     quantityWare2 = 0;
      int     quantityWare3 = 0;
      boolean sellWare1     = true;  // don't sell if it will not make money
      boolean sellWare2     = true;
      boolean sellWare3     = true;
      float   priceTotal;            // transaction's income
      float   fee;                   // transaction fee to be paid
      float   expectedMoney;         // what seller's funds should be after transferring
      boolean isProfitable  = true;  // whether selling will make the seller money

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeSelling = transactionFeesAmount;
      playerAccount.setMoney(0.0f);
      InterfaceTerminal.inventory.clear();

      // set up wares
      Config.chargeTransactionFees = false; // calculate transaction price and fee separately
      if (ware1 != null) {
         quantityWare1 = Config.quanEquilibrium[ware1.getLevel()];
         ware1.setQuantity(quantityWare1);
         InterfaceTerminal.inventory.put(ware1.getWareID(), quantityToTrade1);

         // don't sell if there is no profit to be made
         price1       = Marketplace.getPrice(PLAYER_ID, ware1, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price1 > 0.0001f)
            price1    = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         else {
            price1    = 0.0f;
            sellWare1 = false;
         }
      }
      if (ware2 != null) {
         quantityWare2 = Config.quanEquilibrium[ware2.getLevel()];
         ware2.setQuantity(quantityWare2);
         InterfaceTerminal.inventory.put(ware2.getWareID(), quantityToTrade2);

         // don't sell if there is no profit to be made
         price2       = Marketplace.getPrice(PLAYER_ID, ware2, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price2 > 0.0001f)
            price2    = Marketplace.getPrice(PLAYER_ID, ware2, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
         else {
            price2    = 0.0f;
            sellWare2 = false;
         }
      }
      if (ware3 != null) {
         quantityWare3 = Config.quanEquilibrium[ware3.getLevel()];
         ware3.setQuantity(quantityWare3);
         InterfaceTerminal.inventory.put(ware3.getWareID(), quantityToTrade3);

         // don't sell if there is no profit to be made
         price3       = Marketplace.getPrice(PLAYER_ID, ware3, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price3 > 0.0001f)
            price3    = Marketplace.getPrice(PLAYER_ID, ware3, quantityToTrade3, Marketplace.PriceType.CURRENT_SELL);
         else {
            price3    = 0.0f;
            sellWare3 = false;
         }
      }
      Config.chargeTransactionFees = true;

      // set up test oracles
      priceTotal                   = CommandEconomy.truncatePrice(price1 + price2 + price3); // avoid precision errors using truncation
      if (Config.transactionFeeSelling != 0.00f) {
         fee                       = Config.transactionFeeSelling;
         if (Config.transactionFeeSellingIsMult)
               fee                *= priceTotal;
         fee                       = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
      }
      else
         fee                       = 0.0f;
      isProfitable                 = priceTotal - fee > 0.0f;
      if (!isProfitable) { // if selling won't make a profit, don't sell
         expectedMoney             = playerAccount.getMoney();
         sellWare1 = sellWare2 = sellWare3 = false;
      }
      else
         expectedMoney             = CommandEconomy.truncatePrice(priceTotal - fee);

      // run the test
      baosOut.reset(); // clear buffer holding console output
      Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), null, 1.0f);

      // check ware properties
      if (quantityWare1 != 0) {
         if (sellWare1) {
            if (ware1.getQuantity() != quantityWare1 + quantityToTrade1) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware1.getWareID() + testIdentifier + ": " + ware1.getQuantity() +
                                  ", should be " + (quantityWare1 + quantityToTrade1));
               errorFound = true;
            }
         } else {
            if (ware1.getQuantity() != quantityWare1) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware1.getWareID() + testIdentifier + ": " + ware1.getQuantity() +
                                  ", should be " + quantityWare1);
               errorFound = true;
            }
         }
      }
      if (quantityWare2 != 0) {
         if (sellWare2) {
            if (ware2.getQuantity() != quantityWare2 + quantityToTrade2) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                                  ", should be " + (quantityWare2 + quantityToTrade2));
               errorFound = true;
            }
         } else {
            if (ware2.getQuantity() != quantityWare2) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                                  ", should be " + quantityWare2);
               errorFound = true;
            }
         }
      }
      if (quantityWare3 != 0) {
         if (sellWare3) {
            if (ware3.getQuantity() != quantityWare3 + quantityToTrade3) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                                  ", should be " + (quantityWare3 + quantityToTrade3));
               errorFound = true;
            }
         } else {
            if (ware3.getQuantity() != quantityWare3) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                                  ", should be " + quantityWare3);
               errorFound = true;
            }
         }
      }
      // check account funds
      if (expectedMoney + FLOAT_COMPARE_PRECISION < playerAccount.getMoney() ||
          playerAccount.getMoney() < expectedMoney - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + playerAccount.getMoney() + ", should be " + expectedMoney +
                            "\n      price paid:     " + (playerAccount.getMoney()) +
                            "\n      price expected: " + priceTotal +
                            "\n      price diff:     " + (playerAccount.getMoney() - priceTotal) +
                            "\n      fee expected:   " + fee);
         errorFound = true;
      }
      // check console output
      if (isProfitable && fee != 0.0f && !baosOut.toString().contains(Config.transactionFeeSellingMsg + CommandEconomy.PRICE_FORMAT.format(fee))) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      } else if ((!isProfitable || fee == 0.0f) && baosOut.toString().contains(Config.transactionFeeSellingMsg)) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether a transaction fee was deposited
    * into the fee collection account correctly.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware1                 ware to be sold or null
    * @param ware2                 ware to be sold or null
    * @param quantityToTrade1      how much of the first ware to sell
    * @param quantityToTrade2      how much of the second ware to sell
    * @param transactionFeesAmount transaction fee rate to charge
    * @param accountMoney          how much money the fee collection account should start with
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testerSellTransActFeeAllAccount(Ware ware1, Ware ware2,
                                                        int quantityToTrade1, int quantityToTrade2,
                                                        float transactionFeesAmount, float accountMoney,
                                                        int testNumber, boolean printTestNumber) {
      String  testIdentifier;        // can be added to printed errors to differentiate between tests
      boolean errorFound    = false; // assume innocence until proven guilty

      Account account;               // account collecting fees
      float   price1        = 0.0f;  // profits from each ware
      float   price2        = 0.0f;
      int     quantityWare1 = 0;     // wares' quantities available for sale
      int     quantityWare2 = 0;
      float   priceTotal;            // transaction's income
      float   fee;                   // transaction fee to be paid
      float   expectedMoney;         // what seller's funds should be after transferring

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.transactionFeeSelling = transactionFeesAmount;
      InterfaceTerminal.inventory.clear();
      account = Account.getAccount(Config.transactionFeesAccount);
      if (account != null) // account may be created upon collecting transaction fees
         account.setMoney(accountMoney);

      // set up wares
      Config.chargeTransactionFees = false; // calculate transaction price and fee separately
      if (ware1 != null) {
         quantityWare1 = Config.quanEquilibrium[ware1.getLevel()];
         ware1.setQuantity(quantityWare1);
         InterfaceTerminal.inventory.put(ware1.getWareID(), quantityToTrade1);
         price1       = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
      }
      if (ware2 != null) {
         quantityWare2 = Config.quanEquilibrium[ware2.getLevel()];
         ware2.setQuantity(quantityWare2);
         InterfaceTerminal.inventory.put(ware2.getWareID(), quantityToTrade2);
         price2       = Marketplace.getPrice(PLAYER_ID, ware2, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
      }

      // set up test oracles
      priceTotal                   = CommandEconomy.truncatePrice(price1 + price2); // avoid precision errors using truncation
      fee                          = Config.transactionFeeSelling;
      if (Config.transactionFeeSellingIsMult)
         fee                      *= priceTotal;
      fee                          = CommandEconomy.truncatePrice(fee); // avoid precision errors using truncation
      expectedMoney                = CommandEconomy.truncatePrice(accountMoney + fee);
      Config.chargeTransactionFees = true;
      if (expectedMoney < 0.0f) // if negative fees should not be paid
         expectedMoney = accountMoney;

      baosOut.reset(); // clear buffer holding console output
      Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), null, 1.0f);

      // check account existence
      if (account == null) { // account may be created upon collecting transaction fees
         account = Account.getAccount(Config.transactionFeesAccount);

         // if the account still doesn't exist
         if (account == null) {
            TEST_OUTPUT.println("   account not found" + testIdentifier + ": " + Config.transactionFeesAccount);
            return true;
         }
      }
      // check account funds
      if (expectedMoney + FLOAT_COMPARE_PRECISION < account.getMoney() ||
          account.getMoney() < expectedMoney - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected account money" + testIdentifier + ": " + account.getMoney() + ", should be " + expectedMoney +
                            "\n      fee charged:  " + (account.getMoney() - accountMoney) +
                            "\n      fee expected: " + fee);
         errorFound = true;
      }
      // check console output
      if (expectedMoney == accountMoney && // if negative fee is unpaid, don't display a message
          baosOut.toString().contains(Config.transactionFeeSellingMsg)) {
         TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Tests loadConfig().
    *
    * @return whether loadConfig() passed all test cases
    */
   private static boolean testUnitLoadConfig() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // There is no need to reset configuration to known values before testing.
      // When loading configuration, every value should be set.
      // If even a single value is not set to an expected value,
      // then regardless of prior configuration, that's an error.

      // test for handling missing files
      TEST_OUTPUT.println("loadConfig() - handling of missing files");
      baosOut.reset(); // clear buffer holding console output
      Config.filenameConfig = "CommandEconomy" + File.separator + "tempConfig.txt";
      try {
         Config.loadConfig();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("loadConfig() - loadConfig() should not throw any exception, but it did\n   was testing for handling missing files");
         e.printStackTrace();
         return false;
      }

      // check handling of missing file
      File fileConfig = new File("config" + File.separator + Config.filenameConfig);
      if (!fileConfig.exists()){
         errorFound = true;
         TEST_OUTPUT.println("   default config file failed to be created");
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
         TEST_OUTPUT.println("loadConfig() - unable to create test config file");
         e.printStackTrace();
         return false;
      }

      // try to load the test file
      try {
         Config.loadConfig();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("loadConfig() - loadConfig() should not throw any exception, but it did\n   was loading test config file");
         e.printStackTrace();
         return false;
      }

      // Assertions are not used in this test suite to
      // allow tests to keep running after an error is detected,
      // rather than throw an exception and stop execution.

      // check configuration values
      try {
         TEST_OUTPUT.println("loadConfig() - differences from defaults");
         if (Config.startQuanBase[0] != 0 ||
             Config.startQuanBase[1] != 100 ||
             Config.startQuanBase[2] != 200 ||
             Config.startQuanBase[3] != 300 ||
             Config.startQuanBase[4] != 400 ||
             Config.startQuanBase[5] != 500) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanBase has unexpected values:\n   " +
                               Config.startQuanBase[0] + ", " + Config.startQuanBase[1] +
                               Config.startQuanBase[2] + ", " + Config.startQuanBase[3] + 
                               Config.startQuanBase[4] + ", " + Config.startQuanBase[5] + 
                               "\n   should be: 0, 100, 200, 300, 400, 500");
         }

         if (Config.startQuanMult != 8.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanMult has unexpected value: " +
                               Config.startQuanMult + ", should be 8.0");
         }

         if (Config.priceFloor != 1.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   priceFloor has unexpected value: " +
                               Config.priceFloor + ", should be 1.0");
         }

         if (Config.accountStartingMoney != 1024.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   accountStartingMoney has unexpected value: " +
                               Config.accountStartingMoney + ", should be 1024.0");
         }

         TEST_OUTPUT.println("loadConfig() - defaults");
         if (Config.quanDeficient[0] != 4096 ||
             Config.quanDeficient[1] != 2048 ||
             Config.quanDeficient[2] != 1536 ||
             Config.quanDeficient[3] != 1024 ||
             Config.quanDeficient[4] !=  768 ||
             Config.quanDeficient[5] !=  512) {
            errorFound = true;
            TEST_OUTPUT.println("   quanDeficient has unexpected values:\n   " +
                               Config.quanDeficient[0] + ", " + Config.quanDeficient[1] + ", " +
                               Config.quanDeficient[2] + ", " + Config.quanDeficient[3] + ", " +
                               Config.quanDeficient[4] + ", " + Config.quanDeficient[5] +
                               "\n   should be: 4096, 2048, 1536, 1024, 768, 512");
         }

         if (Config.startQuanSpread != 1.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanSpread has unexpected value: " +
                               Config.startQuanSpread + ", should be 1.0");
         }

         if (Config.priceBuyUpchargeMult != 1.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   priceBuyUpchargeMult has unexpected value: " +
                               Config.priceBuyUpchargeMult + ", should be 1.0");
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("loadConfig() - fatal error: " + e);
         return false;
      }

      TEST_OUTPUT.println("loadConfig() - changing config file");
      // create test config file
      try {
         // open the save file for config, create it if it doesn't exist
         FileWriter fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

         // write test wares file
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "quanDeficient              = 1024, 512, 256, 128, 64, 32\n" +
            "startQuanSpread      = 16.0\n" +
            "priceBuyUpchargeMult = 786.0\n" +
            "accountStartingMoney = 256.0\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );

         // close the file
         fileWriter.close();
      } catch (Exception e) {
         TEST_OUTPUT.println("loadConfig() - unable to change test config file");
         e.printStackTrace();
         return false;
      }

      // try to load the test file
      try {
         Config.loadConfig();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("loadConfig() - loadConfig() should not throw any exception, but it did\n   was loading changed test config file");
         e.printStackTrace();
         return false;
      }

      // check configuration values
      try {
         TEST_OUTPUT.println("loadConfig() - changed differences");
         if (Config.quanDeficient[0] != 1024 ||
             Config.quanDeficient[1] !=  512 ||
             Config.quanDeficient[2] !=  256 ||
             Config.quanDeficient[3] !=  128 ||
             Config.quanDeficient[4] !=   64 ||
             Config.quanDeficient[5] !=   32) {
            errorFound = true;
            TEST_OUTPUT.println("   quanDeficient has unexpected values:\n   " +
                               Config.quanDeficient[0] + ", " + Config.quanDeficient[1] + ", " +
                               Config.quanDeficient[2] + ", " + Config.quanDeficient[3] + ", " +
                               Config.quanDeficient[4] + ", " + Config.quanDeficient[5] +
                               "\n   should be: 1024, 512, 256, 128, 64, 32");
         }

         if (Config.startQuanSpread != 16.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanSpread has unexpected value: " +
                               Config.startQuanMult + ", should be 16.0");
         }

         if (Config.priceBuyUpchargeMult != 786.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   priceBuyUpchargeMult has unexpected value: " +
                               Config.priceBuyUpchargeMult + ", should be 786.0");
         }

         if (Config.accountStartingMoney != 256.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   accountStartingMoney has unexpected value: " +
                               Config.accountStartingMoney + ", should be 256.0");
         }

         TEST_OUTPUT.println("loadConfig() - reset defaults");
         if (Config.startQuanBase[0] != 16384 ||
             Config.startQuanBase[1] !=  9216 ||
             Config.startQuanBase[2] !=  5120 ||
             Config.startQuanBase[3] !=  3072 ||
             Config.startQuanBase[4] !=  2048 ||
             Config.startQuanBase[5] !=  1024) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanBase has unexpected values:\n   " +
                               Config.startQuanBase[0] + ", " + Config.startQuanBase[1] + ", " + 
                               Config.startQuanBase[2] + ", " + Config.startQuanBase[3] + ", " + 
                               Config.startQuanBase[4] + ", " + Config.startQuanBase[5] +
                               "\n   should be: 16384, 9216, 5120, 3072, 2048, 1024");
         }

         if (Config.startQuanMult != 1.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanMult has unexpected value: " +
                               Config.startQuanMult + ", should be 1.0");
         }

         if (Config.priceFloor != 0.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   priceFloor has unexpected value: " +
                               Config.priceFloor + ", should be 0.0");
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("loadConfig() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests loadWares().
    *
    * @return whether loadWares() passed all test cases
    */
   private static boolean testUnitLoadWares() {
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
      TEST_OUTPUT.println("testUnitLoadWares() - handling of missing files");
      Config.filenameWares = "no file here";
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitLoadWares() - loadWares() should not throw any exception, but it did\n   was testing for handling missing files");
         e.printStackTrace();
         return false;
      }

      // check handling of missing file
      File fileWares = new File(Config.filenameWares);
      // check local file
      if (fileWares.exists()){
         errorFound = true;
         TEST_OUTPUT.println("   \"no file here\" file should not exist in local/world directory");
      }
      // check global file
      fileWares = new File("config" + File.separator + Config.filenameWares);
      if (fileWares.exists()){
         errorFound = true;
         TEST_OUTPUT.println("   \"no file here\" file should not exist in global/config directory");
      }

      // further check handling of missing files
      if (!wares.isEmpty()) {
         TEST_OUTPUT.println("   loaded wares despite all files having been missing");
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
         TEST_OUTPUT.println("testUnitLoadWares() - unable to create test wares file");
         e.printStackTrace();
         return false;
      }

      // try to load the test file
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitLoadWares() - loadWares() should not throw any exception, but it did\n   was loading test wares");
         e.printStackTrace();
         return false;
      }

      // Assertions are not used in this test suite to
      // allow tests to keep running after an error is detected,
      // rather than throw an exception and stop execution.

      // prepare to check wares
      Ware testWare; // holds ware currently being checked
      try {
         TEST_OUTPUT.println("testUnitLoadWares() - creation of new material ware without specified starting quantity");
         testWare = wares.get("test:material1");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "", (byte) 0, 1.0f, 256);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of new material ware with specified starting quantity");
         testWare = wares.get("test:material2");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "", (byte) 1, 2.0f, 5);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of new material ware with specified alias");
         testWare = wares.get("test:material3");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "mat3", (byte) 2, 4.0f, 64);
         if (!wareAliasTranslations.containsKey("mat3") ||
             !wareAliasTranslations.get("mat3").equals("test:material3")) {
            TEST_OUTPUT.println("   test:material3 did not have expected alias");
            errorFound = true;
         }

         TEST_OUTPUT.println("testUnitLoadWares() - creation of new material ware with alias taken from minecraft:wareID");
         testWare = wares.get("minecraft:material4");
         errorFound = errorFound || testWareFields(testWare, WareMaterial.class, "material4", (byte) 3, 8.0f, 32);
         if (!wareAliasTranslations.containsKey("material4") ||
             !wareAliasTranslations.get("material4").equals("minecraft:material4")) {
            TEST_OUTPUT.println("   minecraft:material4 did not have expected alias");
            errorFound = true;
         }

         TEST_OUTPUT.println("testUnitLoadWares() - creation of an untradeable ware without components");
         testWare = wares.get("test:untradeable1");
         errorFound = errorFound || testWareFields(testWare, WareUntradeable.class, "notrade1", (byte) 0, 16.0f, Integer.MAX_VALUE);
         if (!wareAliasTranslations.containsKey("notrade1") ||
             !wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
            TEST_OUTPUT.println("   test:untradeable1 did not have expected alias");
            errorFound = true;
         }

         TEST_OUTPUT.println("testUnitLoadWares() - creation of an untradeable ware with components");
         testWare = wares.get("test:untradeable2");
         errorFound = errorFound || testWareFields(testWare, WareUntradeable.class, "notrade2", (byte) 0, 9.0f, Integer.MAX_VALUE);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of a processed ware with one component ware");
         testWare = wares.get("test:processed1");
         errorFound = errorFound || testWareFields(testWare, WareProcessed.class, "", (byte) 4, 1.1f, 16);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of a processed ware with many component wares");
         testWare = wares.get("test:processed2");
         errorFound = errorFound || testWareFields(testWare, WareProcessed.class, "", (byte) 5, 14.3f, 8);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of a processed ware with yield affecting price");
         testWare = wares.get("test:processed3");
         errorFound = errorFound || testWareFields(testWare, WareProcessed.class, "", (byte) 3, 1.76f, 32);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of a crafted ware with one component ware and an alias");
         testWare = wares.get("test:crafted1");
         errorFound = errorFound || testWareFields(testWare, WareCrafted.class, "craft1", (byte) 1, 19.2f, 128);
         if (!wareAliasTranslations.containsKey("craft1") ||
             !wareAliasTranslations.get("craft1").equals("test:crafted1")) {
            TEST_OUTPUT.println("   test:crafted1 did not have expected alias");
            errorFound = true;
         }

         TEST_OUTPUT.println("testUnitLoadWares() - creation of a crafted ware with many component wares, including another crafted ware");
         testWare = wares.get("test:crafted2");
         errorFound = errorFound || testWareFields(testWare, WareCrafted.class, "", (byte) 2, 24.24f, 64);

         TEST_OUTPUT.println("testUnitLoadWares() - creation of a crafted ware with yield affecting price");
         testWare = wares.get("test:crafted3");
         errorFound = errorFound || testWareFields(testWare, WareCrafted.class, "", (byte) 3, 2.4f, 32);

         TEST_OUTPUT.println("testUnitLoadWares() - checking median for base starting quantities");
         if ((float) fStartQuanBaseMedian.get(null) != 48) {
            errorFound = true;
            TEST_OUTPUT.println("   startQuanBaseMedian is " + (float) fStartQuanBaseMedian.get(null) + ", should be 48");
         }

         TEST_OUTPUT.println("testUnitLoadWares() - checking median for base price");
         if ((float) fPriceBaseMedian.get(null) != 3.2f) {
            errorFound = true;
            TEST_OUTPUT.println("   priceBaseMedian is " + (float) fPriceBaseMedian.get(null) + ", should be 3.2");
         }

         TEST_OUTPUT.println("testUnitLoadWares() - checking average for base price");
         if ((double) fPriceBaseAverage.get(null) != 7.800000190734863) {
            errorFound = true;
            TEST_OUTPUT.println("   priceBaseAverage is " + (double) fPriceBaseAverage.get(null) + ", should be 7.8");
         }

         TEST_OUTPUT.println("testUnitLoadWares() - checking ware alias translation accuracy");
         if (!wareAliasTranslations.containsKey("mat3")) {
            errorFound = true;
            TEST_OUTPUT.println("   mat3 does not exist, should be mapped to test:material3");
         } else {
            if (!wareAliasTranslations.get("mat3").equals("test:material3")) {
               errorFound = true;
               TEST_OUTPUT.println("   mat3's ware ID is " + wareAliasTranslations.get("mat3") + ", should be test:material3");
            }
         }
         if (!wareAliasTranslations.containsKey("material4")) {
            errorFound = true;
            TEST_OUTPUT.println("   material4 does not exist, should be mapped to minecraft:material4");
         } else {
            if (!wareAliasTranslations.get("material4").equals("minecraft:material4")) {
               errorFound = true;
               TEST_OUTPUT.println("   material4's ware ID is " + wareAliasTranslations.get("material4") + ", should be minecraft:material4");
            }
         }
         if (!wareAliasTranslations.containsKey("notrade1")) {
            errorFound = true;
            TEST_OUTPUT.println("   notrade1 does not exist, should be mapped to test:untradeable1");
         } else {
            if (!wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
               errorFound = true;
               TEST_OUTPUT.println("   notrade1's ware ID is " + wareAliasTranslations.get("notrade1") + ", should be test:untradeable1");
            }
         }
         if (!wareAliasTranslations.containsKey("craft1")) {
            errorFound = true;
            TEST_OUTPUT.println("   craft1 does not exist, should be mapped to test:crafted1");
         } else {
            if (!wareAliasTranslations.get("craft1").equals("test:crafted1")) {
               errorFound = true;
               TEST_OUTPUT.println("   craft1's ware ID is " + wareAliasTranslations.get("craft1") + ", should be test:crafted1");
            }
         }

         TEST_OUTPUT.println("testUnitLoadWares() - checking alternate ware alias translation accuracy");
         if (!wareAliasTranslations.containsKey("#testName")) {
            errorFound = true;
            TEST_OUTPUT.println("   #testName does not exist, should be mapped to test:material2");
         } else {
            if (!wareAliasTranslations.get("#testName").equals("test:material2")) {
               errorFound = true;
               TEST_OUTPUT.println("   #testName's ware ID is " + wareAliasTranslations.get("#testName") + ", should be test:material2");
            }
         }
         if (!wareAliasTranslations.containsKey("testAlternateAlias")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAlternateAlias does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("testAlternateAlias").equals("test:material1")) {
               errorFound = true;
               TEST_OUTPUT.println("   testAlternateAlias's ware ID is " + wareAliasTranslations.get("testAlternateAlias") + ", should be test:material1");
            }
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitLoadWares() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      // ensure loaded wares matches the expected amount
      TEST_OUTPUT.println("testUnitLoadWares() - checking loaded wares volume");
      if (wares.size() != 12) {
         TEST_OUTPUT.println("   total loaded wares: " + wares.size() + ", should be 12");
         return false;
      }
      TEST_OUTPUT.println("testUnitLoadWares() - checking loaded ware aliases volume");
      if (wareAliasTranslations.size() != 7) {
         TEST_OUTPUT.println("   total loaded ware aliases: " + wareAliasTranslations.size() + ", should be 7");
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
   private static boolean testUnitLinkedWares() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // create test variables
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

         TEST_OUTPUT.println("linked wares - negative yield");
         baosOut.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "invalidYield", "planks", -1);

         if ((int) yield.get(planks) != 1) {
            TEST_OUTPUT.println("   unexpected yield for planks: " + yield.get(planks) + ", should be 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - zero yield");
         baosOut.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "invalidYield", "planks", 0);

         if ((int) yield.get(planks) != 1) {
            TEST_OUTPUT.println("   unexpected yield for planks: " + yield.get(planks) + ", should be 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - null component amount array");
         baosOut.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, null, "nullComponentsAmounts", "planks", 2);

         if (!Float.isNaN(planks.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price: " + planks.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (planks.getQuantity() != 0) {
            TEST_OUTPUT.println("   unexpected quantity: " + planks.getQuantity() + ", should be 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - zero length component amount array");
         baosOut.reset(); // clear buffer holding console output
         planks = new WareLinked(new String[]{"wood"}, new int[]{}, "zeroLengthComponentsAmounts", "planks", 2);

         if (!Float.isNaN(planks.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price: " + planks.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (planks.getQuantity() != 0) {
            TEST_OUTPUT.println("   unexpected quantity: " + planks.getQuantity() + ", should be 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - unequal lengths of component arrays");
         planks = new WareLinked(new String[]{"wood"}, new int[]{1, 1}, "unequalLengths", "planks", 1);

         if (!Float.isNaN(planks.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price: " + planks.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (planks.getQuantity() != 0) {
            TEST_OUTPUT.println("   unexpected quantity: " + planks.getQuantity() + ", should be 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - valid creation");
         planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "minecraft:planks", "planks", 2);
         wares.put("minecraft:planks", planks);
         wareAliasTranslations.put("planks", "minecraft:planks");
         stick = new WareLinked(new String[]{"planks"}, new int[]{1}, "minecraft:stick", "planks", 2);
         gold_block = new WareLinked(new String[]{"gold_ingot"}, new int[]{9}, "minecraft:gold_block", "gold_block", 1);
         jack_o_lantern = new WareLinked(new String[]{"pumpkin", "torch"}, new int[]{1, 1}, "minecraft:lit_pumpkin", "jack_o_lantern", 1);

         if (planks.getBasePrice() != 0.25f) {
            TEST_OUTPUT.println("   unexpected price for planks: " + planks.getBasePrice() + ", should be 0.25f");
            errorFound = true;
         }
         if (planks.getQuantity() != 20) {
            TEST_OUTPUT.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 20");
            errorFound = true;
         }
         if (stick.getBasePrice() != 0.125f) {
            TEST_OUTPUT.println("   unexpected price for stick: " + stick.getBasePrice() + ", should be 0.125f");
            errorFound = true;
         }
         if (stick.getQuantity() != 40) {
            TEST_OUTPUT.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 40");
            errorFound = true;
         }
         if (gold_block.getBasePrice() != 108.0f) {
            TEST_OUTPUT.println("   unexpected price for gold_block: " + gold_block.getBasePrice() + ", should be 108.0f");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 2) {
            TEST_OUTPUT.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 2");
            errorFound = true;
         }
         if (jack_o_lantern.getBasePrice() != 5.2f) {
            TEST_OUTPUT.println("   unexpected price for jack_o_lantern: " + jack_o_lantern.getBasePrice() + ", should be 5.2f");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 3) {
            TEST_OUTPUT.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 3");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - setQuantity()");
         stick.setQuantity(17);
         gold_block.setQuantity(10);
         jack_o_lantern.setQuantity(15);

         if (wood.getQuantity() != 4) {
            TEST_OUTPUT.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 4");
            errorFound = true;
         }
         if (planks.getQuantity() != 8) {
            TEST_OUTPUT.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 8");
            errorFound = true;
         }
         if (stick.getQuantity() != 17) {
            TEST_OUTPUT.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 17");
            errorFound = true;
         }

         if (gold_ingot.getQuantity() != 90) {
            TEST_OUTPUT.println("   unexpected quantity for gold_ingot: " + gold_ingot.getQuantity() + ", should be 90");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 10) {
            TEST_OUTPUT.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 10");
            errorFound = true;
         }

         if (pumpkin.getQuantity() != 15) {
            TEST_OUTPUT.println("   unexpected quantity for pumpkin: " + pumpkin.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (torch.getQuantity() != 15) {
            TEST_OUTPUT.println("   unexpected quantity for torch: " + torch.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 15) {
            TEST_OUTPUT.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 15");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - addQuantity()");
         stick.setQuantity(1);
         stick.addQuantity(16);
         gold_ingot.setQuantity(10);
         gold_block.addQuantity(1);
         jack_o_lantern.setQuantity(10);
         jack_o_lantern.addQuantity(5);

         if (wood.getQuantity() != 4) {
            TEST_OUTPUT.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 4");
            errorFound = true;
         }
         if (planks.getQuantity() != 8) {
            TEST_OUTPUT.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 8");
            errorFound = true;
         }
         if (stick.getQuantity() != 17) {
            TEST_OUTPUT.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 17");
            errorFound = true;
         }

         if (gold_ingot.getQuantity() != 19) {
            TEST_OUTPUT.println("   unexpected quantity for gold_ingot: " + gold_ingot.getQuantity() + ", should be 19");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 2) {
            TEST_OUTPUT.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 2");
            errorFound = true;
         }

         if (pumpkin.getQuantity() != 15) {
            TEST_OUTPUT.println("   unexpected quantity for pumpkin: " + pumpkin.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (torch.getQuantity() != 15) {
            TEST_OUTPUT.println("   unexpected quantity for torch: " + torch.getQuantity() + ", should be 15");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 15) {
            TEST_OUTPUT.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 15");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - subtractQuantity()");
         stick.setQuantity(100);
         stick.subtractQuantity(33);
         gold_ingot.setQuantity(18);
         gold_block.subtractQuantity(1);
         jack_o_lantern.setQuantity(10);
         jack_o_lantern.subtractQuantity(5);

         if (wood.getQuantity() != 16) {
            TEST_OUTPUT.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 16");
            errorFound = true;
         }
         if (planks.getQuantity() != 33) {
            TEST_OUTPUT.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 33");
            errorFound = true;
         }
         if (stick.getQuantity() != 67) {
            TEST_OUTPUT.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 67");
            errorFound = true;
         }

         stick.subtractQuantity(33);
         if (wood.getQuantity() != 8) {
            TEST_OUTPUT.println("   unexpected quantity for wood: " + wood.getQuantity() + ", should be 8");
            errorFound = true;
         }
         if (planks.getQuantity() != 17) {
            TEST_OUTPUT.println("   unexpected quantity for planks: " + planks.getQuantity() + ", should be 17");
            errorFound = true;
         }
         if (stick.getQuantity() != 34) {
            TEST_OUTPUT.println("   unexpected quantity for stick: " + stick.getQuantity() + ", should be 34");
            errorFound = true;
         }

         if (gold_ingot.getQuantity() != 9) {
            TEST_OUTPUT.println("   unexpected quantity for gold_ingot: " + gold_ingot.getQuantity() + ", should be 9");
            errorFound = true;
         }
         if (gold_block.getQuantity() != 1) {
            TEST_OUTPUT.println("   unexpected quantity for gold_block: " + gold_block.getQuantity() + ", should be 1");
            errorFound = true;
         }

         if (pumpkin.getQuantity() != 5) {
            TEST_OUTPUT.println("   unexpected quantity for pumpkin: " + pumpkin.getQuantity() + ", should be 5");
            errorFound = true;
         }
         if (torch.getQuantity() != 5) {
            TEST_OUTPUT.println("   unexpected quantity for torch: " + torch.getQuantity() + ", should be 5");
            errorFound = true;
         }
         if (jack_o_lantern.getQuantity() != 5) {
            TEST_OUTPUT.println("   unexpected quantity for jack_o_lantern: " + jack_o_lantern.getQuantity() + ", should be 5");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - basic getters and setters");
         if (!planks.getWareID().equals("minecraft:planks")) {
            TEST_OUTPUT.println("   unexpected ware ID for planks: " + planks.getWareID() + ", should be minecraft:planks");
            errorFound = true;
         }
         stick.setAlias("testStick");
         if (!stick.getAlias().equals("testStick")) {
            TEST_OUTPUT.println("   unexpected alias for stick: " + stick.getAlias() + ", should be testStick");
            errorFound = true;
         }
         gold_block.setLevel((byte) 5);
         if (gold_block.getLevel() != 5) {
            TEST_OUTPUT.println("   unexpected level for gold_block: " + gold_block.getLevel() + ", should be 5");
            errorFound = true;
         }
         if (!jack_o_lantern.hasComponents()) {
            TEST_OUTPUT.println("   unexpected value for jack_o_lantern.hasComponents(): " + jack_o_lantern.hasComponents() + ", should be true");
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - pricing");
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

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), gold_ingot, 9, Marketplace.PriceType.CURRENT_SELL);
         if (gold_block_linked.getCurrentPrice(1, false) != price) {
            TEST_OUTPUT.println("   unexpected price for selling 9 ingots: " + gold_block_linked.getCurrentPrice(1, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), gold_ingot, 9, Marketplace.PriceType.CURRENT_BUY);
         if (gold_block_linked.getCurrentPrice(1, true) != price) {
            TEST_OUTPUT.println("   unexpected price for buying 9 ingots: " + gold_block_linked.getCurrentPrice(1, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), gold_ingot, 27, Marketplace.PriceType.CURRENT_SELL);
         if (gold_block_linked.getCurrentPrice(3, false) != price) {
            TEST_OUTPUT.println("   unexpected price for selling 27 ingots: " + gold_block_linked.getCurrentPrice(3, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), gold_ingot, 27, Marketplace.PriceType.CURRENT_BUY);
         if (gold_block_linked.getCurrentPrice(3, true) != price) {
            TEST_OUTPUT.println("   unexpected price for buying 27 ingots: " + gold_block_linked.getCurrentPrice(3, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), wood, 1, Marketplace.PriceType.CURRENT_SELL);
         if (planks_linked.getCurrentPrice(2, false) != price) {
            TEST_OUTPUT.println("   unexpected price for selling 1 wood: " + planks_linked.getCurrentPrice(2, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), wood, 1, Marketplace.PriceType.CURRENT_BUY);
         if (planks_linked.getCurrentPrice(2, true) != price) {
            TEST_OUTPUT.println("   unexpected price for buying 1 wood: " + planks_linked.getCurrentPrice(2, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), wood, 3, Marketplace.PriceType.CURRENT_SELL);
         if (planks_linked.getCurrentPrice(6, false) != price) {
            TEST_OUTPUT.println("   unexpected price for selling 3 wood: " + planks_linked.getCurrentPrice(6, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), wood, 3, Marketplace.PriceType.CURRENT_BUY);
         if (planks_linked.getCurrentPrice(6, true) != price) {
            TEST_OUTPUT.println("   unexpected price for buying 3 wood: " + planks_linked.getCurrentPrice(6, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), wood, 3, Marketplace.PriceType.CURRENT_BUY);
         if (planks_linked.getCurrentPrice(6, true) != price) {
            TEST_OUTPUT.println("   unexpected price for buying 3 wood: " + planks_linked.getCurrentPrice(6, true) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), torch, 16, Marketplace.PriceType.CURRENT_SELL) + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), pumpkin, 16, Marketplace.PriceType.CURRENT_SELL);
         if (jack_o_lantern_linked.getCurrentPrice(18, false) != price) {
            TEST_OUTPUT.println("   unexpected price for selling jack o' lanterns: " + jack_o_lantern_linked.getCurrentPrice(18, false) + ", should be " + price);
            errorFound = true;
         }

         price = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), torch, 16, Marketplace.PriceType.CURRENT_BUY) + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic("tester"), pumpkin, 16, Marketplace.PriceType.CURRENT_BUY);
         if (jack_o_lantern_linked.getCurrentPrice(18, true) != price) {
            TEST_OUTPUT.println("   unexpected price for buying jack o' lanterns: " + jack_o_lantern_linked.getCurrentPrice(18, true) + ", should be " + price);
            errorFound = true;
         }

         TEST_OUTPUT.println("linked wares - saving and loading");
         Ware new_stick          = Ware.fromJSON(stick.toJSON());
         Ware new_gold_block     = Ware.fromJSON(gold_block.toJSON());
         Ware new_jack_o_lantern = Ware.fromJSON(jack_o_lantern.toJSON());

         // components must be reloaded before using price and quantity
         new_stick.reloadComponents();
         new_gold_block.reloadComponents();
         new_jack_o_lantern.reloadComponents();

         if (!new_stick.getAlias().equals(stick.getAlias())) {
            TEST_OUTPUT.println("   unexpected alias for new_stick: " + new_stick.getAlias() + ", should be " + stick.getAlias());
            errorFound = true;
         }
         if (new_stick.getBasePrice() != stick.getBasePrice()) {
            TEST_OUTPUT.println("   unexpected price for new_stick: " + new_stick.getBasePrice() + ", should be " + stick.getBasePrice());
            errorFound = true;
         }
         if (new_stick.getLevel() != (byte) 0) {
            TEST_OUTPUT.println("   unexpected level for new_stick: " + new_stick.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (new_stick.getQuantity() != stick.getQuantity()) {
            TEST_OUTPUT.println("   unexpected quantity for new_stick: " + new_stick.getQuantity() + ", should be " + stick.getQuantity());
            errorFound = true;
         }

         if (!new_gold_block.getAlias().equals(gold_block.getAlias())) {
            TEST_OUTPUT.println("   unexpected alias for new_gold_block: " + new_gold_block.getAlias() + ", should be " + gold_block.getAlias());
            errorFound = true;
         }
         if (new_gold_block.getBasePrice() != gold_block.getBasePrice()) {
            TEST_OUTPUT.println("   unexpected price for new_gold_block: " + new_gold_block.getBasePrice() + ", should be " + gold_block.getBasePrice());
            errorFound = true;
         }
         if (new_gold_block.getLevel() != (byte) 3) {
            TEST_OUTPUT.println("   unexpected level for new_gold_block: " + new_gold_block.getLevel() + ", should be 3");
            errorFound = true;
         }
         if (new_gold_block.getQuantity() != gold_block.getQuantity()) {
            TEST_OUTPUT.println("   unexpected quantity for new_gold_block: " + new_gold_block.getQuantity() + ", should be " + gold_block.getQuantity());
            errorFound = true;
         }

         if (!new_jack_o_lantern.getAlias().equals(jack_o_lantern.getAlias())) {
            TEST_OUTPUT.println("   unexpected alias for new_jack_o_lantern: " + new_jack_o_lantern.getAlias() + ", should be " + jack_o_lantern.getAlias());
            errorFound = true;
         }
         if (new_jack_o_lantern.getBasePrice() != jack_o_lantern.getBasePrice()) {
            TEST_OUTPUT.println("   unexpected price for new_jack_o_lantern: " + new_jack_o_lantern.getBasePrice() + ", should be " + jack_o_lantern.getBasePrice());
            errorFound = true;
         }
         if (new_jack_o_lantern.getLevel() != (byte) 2) {
            TEST_OUTPUT.println("   unexpected level for new_jack_o_lantern: " + new_jack_o_lantern.getLevel() + ", should be 2");
            errorFound = true;
         }
         if (new_jack_o_lantern.getQuantity() != jack_o_lantern.getQuantity()) {
            TEST_OUTPUT.println("   unexpected quantity for new_jack_o_lantern: " + new_jack_o_lantern.getQuantity() + ", should be " + jack_o_lantern.getQuantity());
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("linked wares - fatal error: " + e);
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
   private static boolean testUnitWareValidate() {
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

         TEST_OUTPUT.println("Ware.validate() - empty ware ID, empty alias, valid price, level, and quantity");
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
            TEST_OUTPUT.println("   unexpected validate feedback for wareMaterial: " + validateFeedback);
            errorFound = true;
         }
         if (wareMaterial.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareMaterial: " + wareMaterial.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareMaterial.getBasePrice() != 10.0f) {
            TEST_OUTPUT.println("   unexpected price base for wareMaterial: " + wareMaterial.getBasePrice() + ", should be 10.0");
            errorFound = true;
         }
         if (wareMaterial.getQuantity() != 50) {
            TEST_OUTPUT.println("   unexpected quantity for wareMaterial: " + wareMaterial.getQuantity() + ", should be 50");
            errorFound = true;
         }
         if (wareMaterial.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareMaterial: " + wareMaterial.getLevel() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (wareCrafted.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareCrafted: " + wareCrafted.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareCrafted.getBasePrice() != 1.2f) {
            TEST_OUTPUT.println("   unexpected price base for wareCrafted: " + wareCrafted.getBasePrice() + ", should be 1.2");
            errorFound = true;
         }
         if (wareCrafted.getQuantity() != 80) {
            TEST_OUTPUT.println("   unexpected quantity for wareCrafted: " + wareCrafted.getQuantity() + ", should be 80");
            errorFound = true;
         }
         if (wareCrafted.getLevel() != 1) {
            TEST_OUTPUT.println("   unexpected level for wareCrafted: " + wareCrafted.getLevel() + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (wareProcessed.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareProcessed: " + wareProcessed.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareProcessed.getBasePrice() != 2.2f) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be 2.2");
            errorFound = true;
         }
         if (wareProcessed.getQuantity() != 123456789) {
            TEST_OUTPUT.println("   unexpected quantity for wareProcessed: " + wareProcessed.getQuantity() + ", should be 123456789");
            errorFound = true;
         }
         if (wareProcessed.getLevel() != 4) {
            TEST_OUTPUT.println("   unexpected level for wareProcessed: " + wareProcessed.getLevel() + ", should be 4");
            errorFound = true;
         }

         validateFeedback = wareUntradeableRaw.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableRaw: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableRaw.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareUntradeableRaw: " + wareUntradeableRaw.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareUntradeableRaw.getBasePrice() != 20.0f) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableRaw: " + wareUntradeableRaw.getBasePrice() + ", should be 20.0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getQuantity() != Integer.MAX_VALUE) {
            TEST_OUTPUT.println("   unexpected quantity for wareUntradeableRaw: " + wareUntradeableRaw.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareUntradeableRaw: " + wareUntradeableRaw.getLevel() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableComponents.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareUntradeableComponents: " + wareUntradeableComponents.getAlias() + ", should be null");
            errorFound = true;
         }
         if (wareUntradeableComponents.getBasePrice() != 5.0f) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be 5.0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getQuantity() != Integer.MAX_VALUE) {
            TEST_OUTPUT.println("   unexpected quantity for wareUntradeableComponents: " + wareUntradeableComponents.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareUntradeableComponents: " + wareUntradeableComponents.getLevel() + ", should be 0");
            errorFound = true;
         }

         // reset for other tests
         wareMaterial = new WareMaterial("wareMaterial", null, 10.0f, 64, (byte) 0);
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, null, "wareCrafted", 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableRaw = new WareUntradeable("wareUntradeableRaw", null, 20.0f);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         TEST_OUTPUT.println("Ware.validate() - null ware ID, alias with colon, unset price, unset quantity, level too high");
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
            TEST_OUTPUT.println("   unexpected validate feedback for wareMaterial: " + validateFeedback);
            errorFound = true;
         }
         if (!wareMaterial.getAlias().equals("aliasMaterial")) {
            TEST_OUTPUT.println("   unexpected alias for wareMaterial: " + wareMaterial.getAlias() + ", should be aliasMaterial");
            errorFound = true;
         }
         if (!Float.isNaN(wareMaterial.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareMaterial: " + wareMaterial.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareMaterial.getQuantity() != -1) {
            TEST_OUTPUT.println("   unexpected quantity for wareMaterial: " + wareMaterial.getQuantity() + ", should be -1");
            errorFound = true;
         }
         if (wareMaterial.getLevel() != 5) {
            TEST_OUTPUT.println("   unexpected level for wareMaterial: " + wareMaterial.getLevel() + ", should be 5");
            errorFound = true;
         }

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (!wareCrafted.getAlias().equals("aliasCrafted")) {
            TEST_OUTPUT.println("   unexpected alias for wareCrafted: " + wareCrafted.getAlias() + ", should be aliasCrafted");
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareCrafted: " + wareCrafted.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareCrafted.getQuantity() != -1) {
            TEST_OUTPUT.println("   unexpected quantity for wareCrafted: " + wareCrafted.getQuantity() + ", should be -1");
            errorFound = true;
         }
         if (wareCrafted.getLevel() != 5) {
            TEST_OUTPUT.println("   unexpected level for wareCrafted: " + wareCrafted.getLevel() + ", should be 5");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (!wareProcessed.getAlias().equals("aliasProcessed")) {
            TEST_OUTPUT.println("   unexpected alias for wareProcessed: " + wareProcessed.getAlias() + ", should be aliasProcessed");
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareProcessed.getQuantity() != -1) {
            TEST_OUTPUT.println("   unexpected quantity for wareProcessed: " + wareProcessed.getQuantity() + ", should be -1");
            errorFound = true;
         }
         if (wareProcessed.getLevel() != 5) {
            TEST_OUTPUT.println("   unexpected level for wareProcessed: " + wareProcessed.getLevel() + ", should be 5");
            errorFound = true;
         }

         validateFeedback = wareUntradeableRaw.validate();
         if (!validateFeedback.equals("missing ware ID, unset price")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableRaw: " + validateFeedback);
            errorFound = true;
         }
         if (!wareUntradeableRaw.getAlias().equals("aliasUntradeableRaw")) {
            TEST_OUTPUT.println("   unexpected alias for wareUntradeableRaw: " + wareUntradeableRaw.getAlias() + ", should be aliasUntradeableRaw");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableRaw.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableRaw: " + wareUntradeableRaw.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableRaw.getQuantity() != Integer.MAX_VALUE) {
            TEST_OUTPUT.println("   unexpected quantity for wareUntradeableRaw: " + wareUntradeableRaw.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareUntradeableRaw: " + wareUntradeableRaw.getLevel() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("missing ware ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (!wareUntradeableComponents.getAlias().equals("aliasUntradeableComponents")) {
            TEST_OUTPUT.println("   unexpected alias for wareUntradeableComponents: " + wareUntradeableComponents.getAlias() + ", should be aliasUntradeableComponents");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableComponents.getQuantity() != Integer.MAX_VALUE) {
            TEST_OUTPUT.println("   unexpected quantity for wareUntradeableComponents: " + wareUntradeableComponents.getQuantity() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareUntradeableComponents: " + wareUntradeableComponents.getLevel() + ", should be 0");
            errorFound = true;
         }

         // reset for other tests
         wareMaterial = new WareMaterial("wareMaterial", null, 10.0f, 64, (byte) 0);
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, "wareCrafted", null, 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableRaw = new WareUntradeable("wareUntradeableRaw", null, 20.0f);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         TEST_OUTPUT.println("Ware.validate() - valid ware ID, null alias, NaN price, level too low, and valid quantity");
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
            TEST_OUTPUT.println("   unexpected validate feedback for wareMaterial: " + validateFeedback);
            errorFound = true;
         }
         if (wareMaterial.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareMaterial: " + wareMaterial.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareMaterial.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareMaterial: " + wareMaterial.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareMaterial.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareMaterial: " + wareMaterial.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareMaterial.getQuantity() != 50) {
            TEST_OUTPUT.println("   unexpected quantity for wareMaterial: " + wareMaterial.getQuantity() + ", should be 50");
            errorFound = true;
         }

         validateFeedback = wareCrafted.validate();
         if (!validateFeedback.equals("")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (wareCrafted.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareCrafted: " + wareCrafted.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareCrafted: " + wareCrafted.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareCrafted.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareCrafted: " + wareCrafted.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareCrafted.getQuantity() != 80) {
            TEST_OUTPUT.println("   unexpected quantity for wareCrafted: " + wareCrafted.getQuantity() + ", should be 80");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (wareProcessed.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareProcessed: " + wareProcessed.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareProcessed.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareProcessed: " + wareProcessed.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareProcessed.getQuantity() != 123456789) {
            TEST_OUTPUT.println("   unexpected quantity for wareProcessed: " + wareProcessed.getQuantity() + ", should be 16");
            errorFound = true;
         }

         validateFeedback = wareUntradeableRaw.validate();
         if (!validateFeedback.equals("unset price")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableRaw: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableRaw.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareUntradeableRaw: " + wareUntradeableRaw.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableRaw.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableRaw: " + wareUntradeableRaw.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableRaw.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareUntradeableRaw: " + wareUntradeableRaw.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableRaw.getQuantity() != Integer.MAX_VALUE) {
            TEST_OUTPUT.println("   unexpected quantity for wareUntradeableRaw: " + wareUntradeableRaw.getQuantity() + ", should be 0");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (wareUntradeableComponents.getAlias() != null) {
            TEST_OUTPUT.println("   unexpected alias for wareUntradeableComponents: " + wareUntradeableComponents.getAlias() + ", should be null");
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if (wareUntradeableComponents.getLevel() != 0) {
            TEST_OUTPUT.println("   unexpected level for wareUntradeableComponents: " + wareUntradeableComponents.getLevel() + ", should be 0");
            errorFound = true;
         }
         if (wareUntradeableComponents.getQuantity() != Integer.MAX_VALUE) {
            TEST_OUTPUT.println("   unexpected quantity for wareUntradeableComponents: " + wareUntradeableComponents.getQuantity() + ", should be 0");
            errorFound = true;
         }

         // reset for other tests
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, "wareCrafted", null, 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         TEST_OUTPUT.println("Ware.validate() - null components' IDs, zero yield");
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
            TEST_OUTPUT.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareCrafted) != 1) {
            TEST_OUTPUT.println("   unexpected yield for wareCrafted: " + yield.get(wareCrafted) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("missing components' IDs")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareProcessed) != 1) {
            TEST_OUTPUT.println("   unexpected yield for wareProcessed: " + yield.get(wareProcessed) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("missing components' IDs")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         // now test untradeable ware with zero yield
         componentsIDs.set(wareUntradeableComponents, new String[]{"test:material1", "test:material1", "minecraft:material4"});
         yield.setByte(wareUntradeableComponents, (byte) 0);
         validateFeedback = wareUntradeableComponents.validate();
         if ((int) yield.get(wareUntradeableComponents) != 1) {
            TEST_OUTPUT.println("   unexpected yield for wareUntradeableComponents: " + yield.get(wareUntradeableComponents) + ", should be 1");
            errorFound = true;
         }

         // reset for other tests
         wareCrafted = new WareCrafted(new String[]{"test:material1"}, "wareCrafted", null, 64, 1, (byte) 1);
         wareProcessed = new WareProcessed(new String[]{"test:material1", "test:material1"}, "wareProcessed", null, 16, 1, (byte) 4);
         wareUntradeableComponents = new WareUntradeable(new String[]{"test:material1", "test:material1", "minecraft:material4"}, "wareUntradeableComponents", null, 2);

         TEST_OUTPUT.println("Ware.validate() - empty individual components' IDs, negative yield");
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
            TEST_OUTPUT.println("   unexpected validate feedback for wareCrafted: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareCrafted.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareCrafted) != 1) {
            TEST_OUTPUT.println("   unexpected yield for wareCrafted: " + yield.get(wareCrafted) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareProcessed.validate();
         if (!validateFeedback.equals("blank component ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareProcessed: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareProcessed.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareProcessed: " + wareProcessed.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareProcessed) != 1) {
            TEST_OUTPUT.println("   unexpected yield for wareProcessed: " + yield.get(wareProcessed) + ", should be 1");
            errorFound = true;
         }

         validateFeedback = wareUntradeableComponents.validate();
         if (!validateFeedback.equals("blank component ID")) {
            TEST_OUTPUT.println("   unexpected validate feedback for wareUntradeableComponents: " + validateFeedback);
            errorFound = true;
         }
         if (!Float.isNaN(wareUntradeableComponents.getBasePrice())) {
            TEST_OUTPUT.println("   unexpected price base for wareUntradeableComponents: " + wareUntradeableComponents.getBasePrice() + ", should be NaN");
            errorFound = true;
         }
         if ((int) yield.get(wareUntradeableComponents) != 1) {
            TEST_OUTPUT.println("   unexpected yield for wareUntradeableComponents: " + yield.get(wareUntradeableComponents) + ", should be 1");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("Ware.validate() - fatal error: " + e);
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
   private static boolean testUnitAccountCreation() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // delete any currently loaded accounts
      accounts.clear();

      // make accounts start with some money
      Config.accountStartingMoney = 10.0f;

      Account testAccount; // holds account currently being checked
      try {
         TEST_OUTPUT.println("accountCreation() - creation of account using null ID");
         Account.makeAccount(null, null);
         if (!accounts.isEmpty()) {
            TEST_OUTPUT.println("   account was created using a null id");
            errorFound = true;
            accounts.clear(); // delete account so other tests run properly
         }

         TEST_OUTPUT.println("accountCreation() - creation of account using empty ID");
         Account.makeAccount("", null);
         if (!accounts.isEmpty()) {
            TEST_OUTPUT.println("   account was created using an empty id");
            errorFound = true;
            accounts.clear(); // delete account to avoid interfering with other tests
         }

         TEST_OUTPUT.println("accountCreation() - creation of account using numerical id");
         Account.makeAccount("12345", null);
         if (!accounts.isEmpty()) {
            TEST_OUTPUT.println("   account was created using a numerical id");
            errorFound = true;
            accounts.clear(); // delete account to avoid interfering with other tests
         }

         TEST_OUTPUT.println("accountCreation() - creation of account using valid id and no given player");
         Account.makeAccount("testAccount1", null);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 10.0f, null);

         // grant account access to player for next tests
         testAccount.grantAccess(null, PLAYER_ID, null);

         TEST_OUTPUT.println("accountCreation() - adding money to account");
         testAccount.addMoney(2.0f);
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - creation of account using existing ID");
         Account.makeAccount("testAccount1", null);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - permissions of account with valid ID");
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - creation of account with specified starting amount");
         Account.makeAccount("testAccount2", PLAYER_ID, 14.0f);
         testAccount = accounts.get("testAccount2");
         errorFound = errorFound || testAccountFields(testAccount, 14.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - permissions of account with specified starting amount");
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - creation of account with specified starting amount of NaN");
         Account accountNaNMoney = Account.makeAccount("", PLAYER_ID, Float.NaN);
         errorFound = errorFound || testAccountFields(accountNaNMoney, 0.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - creation of account for arbitrary player ID");
         Account.makeAccount("testAccount3", InterfaceTerminal.getPlayerIDStatic("possibleID"));
         testAccount = accounts.get("testAccount3");
         errorFound = errorFound || testAccountFields(testAccount, 10.0f, "possibleID");

         TEST_OUTPUT.println("accountCreation() - permissions of account with arbitrary player ID");
         if (testAccount.hasAccess(PLAYER_ID)) {
            TEST_OUTPUT.println("   " + InterfaceTerminal.playername + " has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - creation of inaccessible account");
         Account.makeAccount("testAccount4", null, 6.0f);
         testAccount = accounts.get("testAccount4");
         if (testAccount.hasAccess(PLAYER_ID)) {
            TEST_OUTPUT.println("   " + InterfaceTerminal.playername + " has access when they shouldn't");
            errorFound = true;
         }
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - creation of a player's personal account");
         Account.makeAccount(InterfaceTerminal.playername, PLAYER_ID);
         testAccount = accounts.get(InterfaceTerminal.playername);
         errorFound = errorFound || testAccountFields(testAccount, 10.0f, InterfaceTerminal.playername);
         if (testAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   arbitrary player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - adding money to account created with specified starting amount");
         testAccount = accounts.get("testAccount2");
         testAccount.addMoney(3.0f);
         errorFound = errorFound || testAccountFields(testAccount, 17.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - adding NaN amount of money to account");
         testAccount = accounts.get("testAccount1");
         testAccount.addMoney(Float.NaN);
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - adding no money to account");
         testAccount.addMoney(0.0f);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 12.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - subtracting money from account");
         testAccount.addMoney(-10.0f);
         testAccount = accounts.get("testAccount1");
         errorFound = errorFound || testAccountFields(testAccount, 2.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - retrieving number of accounts using null input");
         int accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(null);
         if (accountsCreated != 2147483647) {
            TEST_OUTPUT.println("   null input gave unexpected value: " + accountsCreated + ", should be 2147483647");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - retrieving number of accounts created by nonexistent user");
         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID2"));
         if (accountsCreated != 0) {
            TEST_OUTPUT.println("   unexpected value: " + accountsCreated + ", should be 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - retrieving number of accounts created by test users");
         accountsCreated = -1;
         Account.makeAccount("possibleAccount1", InterfaceTerminal.getPlayerIDStatic("possibleID2"), 10.0f);
         Account.makeAccount("", InterfaceTerminal.getPlayerIDStatic("possibleID2"), 10.0f);
         Account.makeAccount(null, InterfaceTerminal.getPlayerIDStatic("possibleID2"), 10.0f);
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID2"));
         if (accountsCreated != 3) {
            TEST_OUTPUT.println("   unexpected value for number of accounts created by possibleID2: " + accountsCreated + ", should be 3");
            errorFound = true;
         }

         accountsCreated = -1;
         Account.makeAccount("possibleAccount1", InterfaceTerminal.getPlayerIDStatic("possibleID3"));
         Account.makeAccount("possibleAccount2", InterfaceTerminal.getPlayerIDStatic("possibleID3"));
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID3"));
         if (accountsCreated != 1) {
            TEST_OUTPUT.println("   unexpected value for number of accounts created by possibleID3: " + accountsCreated + ", should be 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - non-personal account creation when exceeding per-player limit");
         Config.accountMaxCreatedByIndividual = 0;
         Account.makeAccount("shouldNotExist", InterfaceTerminal.getPlayerIDStatic("possibleID4"), 10.0f);
         if (accounts.containsKey("shouldNotExist")) {
            TEST_OUTPUT.println("   account was created despite exceeding limit");
            errorFound = true;
         }

         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID4"));
         if (accountsCreated != 0) {
            TEST_OUTPUT.println("   unexpected value for number of accounts created by possible user: " + accountsCreated + ", should be 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - personal account creation when exceeding per-player limit");
         Config.accountMaxCreatedByIndividual = 0;
         Account.makeAccount("possibleID4", InterfaceTerminal.getPlayerIDStatic("possibleID4"), 10.0f);
         if (!accounts.containsKey("possibleID4")) {
            TEST_OUTPUT.println("   account was not created when it should have been");
            errorFound = true;
         }

         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID4"));
         if (accountsCreated != 0) {
            TEST_OUTPUT.println("   unexpected value for number of accounts created by possible user: " + accountsCreated + ", should be 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - account creation after removing limit");
         Config.accountMaxCreatedByIndividual = -1;
         Account.makeAccount("shouldExist", InterfaceTerminal.getPlayerIDStatic("possibleID4"), 10.0f);
         if (!accounts.containsKey("shouldExist")) {
            TEST_OUTPUT.println("   account was not created when it should have been");
            errorFound = true;
         }

         accountsCreated = -1;
         accountsCreated = Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("possibleID4"));
         if (accountsCreated != 1) {
            TEST_OUTPUT.println("   unexpected value for number of accounts created by possible user: " + accountsCreated + ", should be 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCreation() - non-personal account creation using nonexistent player ID");
         Account.makeAccount("possibleID5", PLAYER_ID, Config.accountStartingMoney + 10.0f);
         testAccount = accounts.get("possibleID5");
         errorFound = errorFound || testAccountFields(testAccount, Config.accountStartingMoney + 10.0f, InterfaceTerminal.playername);

         TEST_OUTPUT.println("accountCreation() - personal account creation using existing non-personal account ID");
         accounts.get(InterfaceTerminal.playername).setMoney(10.0f); // set player account funds to a known value
         Account.makeAccount("possibleID5", InterfaceTerminal.getPlayerIDStatic("possibleID5"));
         testAccount = accounts.get("possibleID5");
         errorFound = errorFound || testAccountFields(testAccount, Config.accountStartingMoney, "possibleID5");
         if (testAccount.hasAccess(PLAYER_ID)) {
            TEST_OUTPUT.println("   player ID has access when they shouldn't");
            errorFound = true;
         }
         testAccount = accounts.get(InterfaceTerminal.playername);
         if (testAccount.getMoney() != Config.accountStartingMoney + 20.0f) {
            TEST_OUTPUT.println("   player's account has $" + testAccount.getMoney() + ", should be " + (Config.accountStartingMoney + 20.0f));
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("accountCreation() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      // ensure loaded wares matches the expected amount
      if (accounts.size() != 10) {
         TEST_OUTPUT.println("only 10 test accounts should have been created, but total accounts is " + accounts.size());
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
   private static boolean testUnitAccountAccess() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         TEST_OUTPUT.println("accountAccess() - adding permissions for null account ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), null);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions for empty account ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"), "");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"))) {
            TEST_OUTPUT.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions for invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"), "some arbitrary account");

         // check console output
         if (!baosOut.toString().startsWith("yetAnotherPossibleID may now access some arbitrary account")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"))) {
            TEST_OUTPUT.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions for null player ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, null, "testAccount1");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions for empty player ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic(""), "testAccount1");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions for player ID already given permissions");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!baosOut.toString().startsWith("possibleID already may access testAccount1")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions so multiple players share an account");
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID1"), "testAccount1");
         testAccount1.grantAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID2"), "testAccount1");

         // check permissions
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID1"))) {
            TEST_OUTPUT.println("   first possible player ID doesn't have access when they should");
            errorFound = true;
         }
         if (!testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID2"))) {
            TEST_OUTPUT.println("   second possible player ID doesn't have access when they should");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions without permission to do so");
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "permissionlessPlayer";
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(InterfaceTerminal.getPlayerIDStatic("permissionlessPlayer"), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!baosOut.toString().startsWith("You don't have permission to access testAccount1")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         InterfaceTerminal.playername = playernameOrig;

         TEST_OUTPUT.println("accountAccess() - adding permissions with null username");
         baosOut.reset(); // clear buffer holding console output
         testAccount4.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount4");

         // check console output
         if (!baosOut.toString().startsWith("(for possibleID) You may now access testAccount4")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions with empty username");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.grantAccess(InterfaceTerminal.getPlayerIDStatic(""), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - adding permissions with different username");
         baosOut.reset(); // clear buffer holding console output
         testAccount3.grantAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"), PLAYER_ID, "testAccount3");

         // check permissions
         if (!testAccount3.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }
         if (!testAccount3.hasAccess(PLAYER_ID)) {
            TEST_OUTPUT.println("   player doesn't have access when they should");
            errorFound = true;
         }


         TEST_OUTPUT.println("accountAccess() - removing permissions for null account ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), null);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   possible player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions for empty account ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"), "");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("anotherPossibleID"))) {
            TEST_OUTPUT.println("   possible player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions for invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"), "some arbitrary account");

         // check console output
         if (!baosOut.toString().startsWith("yetAnotherPossibleID may no longer access some arbitrary account")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("yetAnotherPossibleID"))) {
            TEST_OUTPUT.println("   possible player ID has access when they shouldn't");
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions for null player ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic(null), "testAccount1");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions for empty player ID");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic(""), "testAccount1");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions for player ID without permissions");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(PLAYER_ID, InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!baosOut.toString().startsWith("possibleID already cannot access testAccount1.")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions without permission to do so");
         playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "permissionlessPlayer";
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(InterfaceTerminal.getPlayerIDStatic("permissionlessPlayer"), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");
         InterfaceTerminal.playername = playernameOrig;

         // check console output
         if (!baosOut.toString().startsWith("You don't have permission to access testAccount1")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions with null username");
         baosOut.reset(); // clear buffer holding console output
         testAccount4.revokeAccess(InterfaceTerminal.getPlayerIDStatic(null), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount4");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions with empty username");
         baosOut.reset(); // clear buffer holding console output
         testAccount1.revokeAccess(InterfaceTerminal.getPlayerIDStatic(""), InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount1");

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountAccess() - removing permissions with different username");
         baosOut.reset(); // clear buffer holding console output
         testAccount3.revokeAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"), PLAYER_ID, "testAccount3");

         // check permissions
         if (!testAccount3.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            TEST_OUTPUT.println("   possible player ID doesn't have access when they should");
            errorFound = true;
         }
         if (testAccount3.hasAccess(PLAYER_ID)) {
            TEST_OUTPUT.println("   player has access when they shouldn't");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("accountAccess() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account's check().
    *
    * @return whether Account's check() passed all test cases
    */
   private static boolean testUnitAccountCheck() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // set up test accounts
      Account testUnitAccountAccessible   = Account.makeAccount("", PLAYER_ID, 10.0f);
      Account testAccountInaccessible = Account.makeAccount("", null, 20.0f);
      try {
         TEST_OUTPUT.println("accountCheck() - null account ID");
         baosOut.reset(); // clear buffer holding console output
         playerAccount.check(PLAYER_ID, null);
         if (!baosOut.toString().equals("")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCheck() - empty account ID");
         baosOut.reset(); // clear buffer holding console output
         playerAccount.check(PLAYER_ID, "");
         if (!baosOut.toString().equals("")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCheck() - arbitrary account ID");
         baosOut.reset(); // clear buffer holding console output
         testUnitAccountAccessible.check(PLAYER_ID, "Your account");
         if (!baosOut.toString().equals("Your account: $10.00" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCheck() - without permission");
         baosOut.reset(); // clear buffer holding console output
         testAccountInaccessible.check(PLAYER_ID, "this inaccessible account");
         if (!baosOut.toString().equals("You don't have permission to access this inaccessible account" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCheck() - different username");
         baosOut.reset(); // clear buffer holding console output
         testAccount3.check(InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount3");
         if (!baosOut.toString().equals("(for possibleID) testAccount3: $30.00" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountCheck() - large number");
         baosOut.reset(); // clear buffer holding console output
         testUnitAccountAccessible.setMoney(1000000.1f);
         testUnitAccountAccessible.check(PLAYER_ID, "Your account");
         if (!baosOut.toString().equals("Your account: $1,000,000.12" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("accountCheck() - fatal error: " + e);
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
   private static boolean testUnitAccountSendMoney() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         // send quantity recipient_account_id [sender_account_id]
         TEST_OUTPUT.println("accountSendMoney() - null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(null);
         if (!baosOut.toString().equals("/send <quantity> <recipient_account_id> [sender_account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountSendMoney() - empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{});
         if (!baosOut.toString().equals("/send <quantity> <recipient_account_id> [sender_account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountSendMoney() - blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountSendMoney() - too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{"10.0"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("accountSendMoney() - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSend(new String[]{InterfaceTerminal.playername, "10.0", "testAccount1", "testAccount2", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("accountSendMoney() - sending negative amount of money");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       10.0f, 0.0f,  -10.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - sending no money");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       10.0f, 0.0f, 0.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - null sender ID");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, null, "testAccount2",
                                       10.0f, 0.0f, 1.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - empty sender ID");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "", "testAccount2",
                                       10.0f, 0.0f, 1.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - arbitrary sender ID");
         testAccount1.setMoney(8.0f);
         testAccount2.setMoney(22.0f);
         testAccount1.sendMoney(PLAYER_ID, 1.0f, "arbitrary account", "testAccount2");
         if (testAccountFields(testAccount1, 7.0f, InterfaceTerminal.playername)
             || testAccountFields(testAccount2, 23.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   test account 1 - money: " + testAccount1.getMoney() + ", should be 7.0");
            TEST_OUTPUT.println("   test account 1 - player has permission: " + testAccount1.hasAccess(PLAYER_ID) + ", should be true");
            TEST_OUTPUT.println("   test account 2 - money: " + testAccount2.getMoney() + ", should be 23.0");
            TEST_OUTPUT.println("   test account 2 - player has permission: " + testAccount2.hasAccess(PLAYER_ID) + ", should be true");
            // set up accounts appropriately for other tests
            testAccount1.setMoney(7.0f);
            testAccount2.setMoney(23.0f);
         }

         TEST_OUTPUT.println("accountSendMoney() - null recipient ID");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", null,
                                       10.0f, 0.0f, 1.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - empty recipient ID");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "",
                                       10.0f, 0.0f, 1.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - invalid recipient ID");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "invalidAccount",
                                       10.0f, 0.0f, 1.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - normal usage");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       10.0f, 0.0f, 10.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - some funds from account to player");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       10.0f, 0.0f, 5.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - all funds from player to account");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       10.0f, 0.0f, 10.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - insufficient funds");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       10.0f, 0.0f, 500.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - transferring without permission");
         errorFound |= testerSendMoney("notPermitted", "testAccount1", "testAccount2",
                                       10.0f, 0.0f, 1.0f, 0.0f, 0);

         TEST_OUTPUT.println("accountSendMoney() - different username");
         errorFound |= testerSendMoney("possibleID", "testAccount3", InterfaceTerminal.playername,
                                       10.0f, 0.0f, 10.0f, 0.0f, 0);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("accountSendMoney() - fatal error: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account's deleteAccount().
    *
    * @return whether Account's deleteAccount() passed all test cases
    */
   private static boolean testUnitAccountDeletion() {
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
         TEST_OUTPUT.println("deleteAccount() - null account ID");
         accountID  = null;
         playerID   = PLAYER_ID;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - empty account ID");
         accountID  = "";
         playerID   = PLAYER_ID;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - invalid account ID");
         accountID  = "invalidAccount";
         playerID   = PLAYER_ID;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - null account owner");
         accountID  = "testAccount1";
         playerID   = null;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - invalid account owner");
         accountID  = "testAccount1";
         playerID   = InterfaceTerminal.getPlayerIDStatic("possibleID");

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().equals("(for possibleID) You are not permitted to delete " + accountID + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - account user instead of owner");
         accountID  = "testAccount1";
         playerID   = InterfaceTerminal.getPlayerIDStatic("possibleID");
         accounts.get(accountID).grantAccess(PLAYER_ID, playerID, accountID);

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().equals("(for possibleID) You are not permitted to delete " + accountID + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - server operator");
         accountID  = "testAccount4";
         playerID   = PLAYER_ID;
         accountsSize--;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (accounts.containsKey(accountID)) {
            TEST_OUTPUT.println("   test account 4 exists when it shouldn't");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // prepare for next test
         accountsSize++;
         resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - personal account");
         accountID  = InterfaceTerminal.playername;
         playerID   = PLAYER_ID;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().equals("Personal accounts may not be deleted" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 1");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }

         // if necessary, reset test environment
         if (errorFound)
            resetTestEnvironment();

         TEST_OUTPUT.println("deleteAccount() - valid account ID and owner");
         accountID  = "testAccount1";
         playerID   = PLAYER_ID;
         money      = accounts.get(accountID).getMoney();
         accountsSize--;

         baosOut.reset(); // clear buffer holding console output
         Account.deleteAccount(accountID, playerID);

         // check console output
         if (!baosOut.toString().equals("You received $" + money + " from " + accountID + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
         if (accounts.containsKey(accountID)) {
            TEST_OUTPUT.println("   test account 1 exists when it shouldn't");
            errorFound = true;
         }
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for test account 2");
            errorFound = true;
         }
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            TEST_OUTPUT.println("   unexpected values for test account 3");
            errorFound = true;
         }
         if (testAccountFields(testAccount4, 6.0f, null)) {
            TEST_OUTPUT.println("   unexpected values for test account 4");
            errorFound = true;
         }
         if (testAccountFields(playerAccount, 30.0f + money, InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected values for player account");
            errorFound = true;
         }
         accountsSize++;
         resetTestEnvironment();


         // delete account_id
         TEST_OUTPUT.println("delete() - null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(null);
         if (!baosOut.toString().equals("/delete <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("delete() - empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{});
         if (!baosOut.toString().equals("/delete <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("delete() - blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - must provide account ID")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("delete() - too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{});
         if (!baosOut.toString().equals("/delete <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("delete() - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{"possibleAccount", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("deleteAccount() - invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{"invalidAccount"});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("delete() - account user instead of owner");
         accounts.get(accountID).grantAccess(PLAYER_ID, playerID, accountID);
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "possibleID";
         accountID  = "testAccount1";
         playerID   = PLAYER_ID;

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!baosOut.toString().equals("You are not permitted to delete " + accountID + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // prepare for next test
         InterfaceTerminal.playername = playernameOrig;
         resetTestEnvironment();

         TEST_OUTPUT.println("delete() - server operator");
         accountID  = "testAccount4";
         accountsSize--;

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }

         // prepare for next test
         accountsSize++;
         resetTestEnvironment();

         TEST_OUTPUT.println("delete() - personal account");
         accountID  = InterfaceTerminal.playername;

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!baosOut.toString().equals("Personal accounts may not be deleted" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("delete() - valid account ID and owner");
         accountID  = "testAccount1";
         money      = accounts.get(accountID).getMoney();
         accountsSize--;

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDelete(new String[]{accountID});

         // check console output
         if (!baosOut.toString().equals("You received $" + money + " from " + accountID + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // check accounts
         if (accounts.size() != accountsSize) {
            TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + accountsSize);
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("deleteAccount() - fatal error: " + e);
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
   private static boolean testUnitDefaultAccounts() {
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
      UUID playerID = PLAYER_ID;
      Config.filenameAccounts = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";

      try {
         TEST_OUTPUT.println("setDefaultAccount() - null player ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(null, "testAccount1");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         TEST_OUTPUT.println("setDefaultAccount() - null account ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, null);

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         TEST_OUTPUT.println("setDefaultAccount() - empty account ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, "");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         TEST_OUTPUT.println("setDefaultAccount() - nonexistent account ID");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, "invalidAccount");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         TEST_OUTPUT.println("setDefaultAccount() - personal account ID when personal account doesn't exist");
         money = Config.accountStartingMoney;

         Account.setDefaultAccount(InterfaceTerminal.getPlayerIDStatic("possibleID"), "possibleID");

         account = Account.getAccount("possibleID");

         // check account existence
         if (account != null) {
            // check account funds
            if (account.getMoney() != money) {
               TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
               errorFound = true;
            }

            // check account reference
            if (account == playerAccount) {
               TEST_OUTPUT.println("   unexpected account reference");
               errorFound = true;
            }
         } else {
            TEST_OUTPUT.println("   account for possibleID does not exist when it should");
            errorFound = true;
         }
         defaultAccounts.clear();

         TEST_OUTPUT.println("setDefaultAccount() - no permissions");
         money = playerAccount.getMoney();

         Account.setDefaultAccount(playerID, "testAccount4");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         TEST_OUTPUT.println("setDefaultAccount() - has permissions");
         money = testAccount1.getMoney();

         Account.setDefaultAccount(playerID, "testAccount1");

         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != testAccount1) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }
         defaultAccounts.clear();

         // prepare for next tests
         defaultAccounts.put(playerID, testAccount1);

         TEST_OUTPUT.println("default accounts - buying");
         quantityToTrade = 5;
         quantityWare    = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(playerID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount1.getMoney();

         Marketplace.buy(playerID, null, null, "test:material1", quantityToTrade, 0.0f, 1.0f);

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         testAccount1.setMoney(10.0f);

         TEST_OUTPUT.println("default accounts - selling");
         InterfaceTerminal.inventory.put("test:material1", 10);
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(playerID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount1.getMoney();

         Marketplace.sell(playerID, null, null, "test:material1", quantityToTrade, 0.0f, 1.0f);

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         testAccount1.setMoney(10.0f);

         TEST_OUTPUT.println("default accounts - sellall");
         quantityToTrade = 10;
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade);
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(playerID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount1.getMoney();

         Marketplace.sellAll(playerID, null, getFormattedInventory(), null, 1.0f);

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         InterfaceTerminal.inventory.clear();
         testAccount1.setMoney(10.0f);

         TEST_OUTPUT.println("default accounts - sending");
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

         TEST_OUTPUT.println("default accounts - receiving");
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

         TEST_OUTPUT.println("default accounts - deleting account");
         defaultAccounts.put(playerID, testAccount1);
         money = playerAccount.getMoney() + testAccount1.getMoney();

         Account.deleteAccount("testAccount1", playerID);
         account = Account.grabAndCheckAccount(null, playerID);

         // check account funds
         if (account.getMoney() != money) {
            TEST_OUTPUT.println("   unexpected account funds: " + account.getMoney() + ", should be " + money);
            errorFound = true;
         }

         // check account reference
         if (account != playerAccount) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - loading, entry with nonexistent account");
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
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - loading, entry with invalid player ID");
         if (defaultAccounts.size() != 1) {
            TEST_OUTPUT.println("   unexpected number of default account entries: " + defaultAccounts.size() + ", should be 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - loading, entry mapping player to account without permissions");
         testAccount1 = Account.getAccount("testAccount1");
         account      = Account.grabAndCheckAccount(null, InterfaceTerminal.getPlayerIDStatic("possibleID"));

         // check account reference
         if (account == testAccount1) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - loading, valid entry");
         account = Account.grabAndCheckAccount(null, UUID.nameUUIDFromBytes(("$admin$").getBytes()));

         // check account reference
         if (account != Account.getAccount("$admin$")) {
            TEST_OUTPUT.println("   unexpected account reference");
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - revoking access to default account");
         // set up test and check setup
         defaultAccounts.put(playerID, testAccount1);
         account = Account.grabAndCheckAccount(null, playerID);
         if (account != testAccount1) {
            TEST_OUTPUT.println("   unexpected account reference: failed to set up test");
            errorFound = true;
         }

         //run test
         else {
            testAccount1.revokeAccess(playerID, playerID, "testAccount1");
            account = Account.grabAndCheckAccount(null, playerID);

            // check account reference
            if (account != playerAccount) {
               TEST_OUTPUT.println("   unexpected account reference");
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("default accounts - saving and loading");
         // add data to be saved
         Account.setDefaultAccount(InterfaceTerminal.getPlayerIDStatic("possibleID"), "testAccount3");

         Account.saveAccounts();
         Account.loadAccounts();

         // check account references
         account = Account.grabAndCheckAccount(null, UUID.nameUUIDFromBytes(("$admin$").getBytes()));
         if (account != Account.getAccount("$admin$")) {
            TEST_OUTPUT.println("   unexpected account reference for $admin$");
            errorFound = true;
         }
         account = Account.grabAndCheckAccount(null, playerID);
         if (account != Account.getAccount(InterfaceTerminal.playername)) {
            TEST_OUTPUT.println("   unexpected account reference for " + InterfaceTerminal.playername);
            errorFound = true;
         }
         account = Account.grabAndCheckAccount(null, InterfaceTerminal.getPlayerIDStatic("possibleID"));
         if (account != Account.getAccount("testAccount3")) {
            TEST_OUTPUT.println("   unexpected account reference for possibleID");
            errorFound = true;
         }

         // completely reset test environment to
         // reduce possibility of interference
         resetTestEnvironment();

         // test requests
         TEST_OUTPUT.println("default accounts - request: null arg");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(null);
         if (!baosOut.toString().equals("/setDefaultAccount <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - request: blank arg");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{""});
         if (!baosOut.toString().equals("error - zero-length arguments: /setDefaultAccount <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - request: empty args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{});
         if (!baosOut.toString().equals("/setDefaultAccount <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - request: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"testAccount1", "excessArgument"});
         if (!baosOut.toString().equals("error - wrong number of arguments: /setDefaultAccount <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - request: invalid account");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"invalidAccount"});
         if (!baosOut.toString().equals("error - account not found: invalidAccount" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - request: no permissions");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"testAccount4"});
         if (!baosOut.toString().equals("You don't have permission to access testAccount4" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("default accounts - request: valid usage");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSetDefaultAccount(new String[]{"testAccount1"});
         if (!baosOut.toString().equals("testAccount1 will now be used in place of your personal account" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("default accounts - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests /op, /deop, isAnOp(), and permissionToExecute().
    *
    * @return whether /op, /deop, isAnOp(), and permissionToExecute() passed all test cases
    */
   private static boolean testUnitAdminPermissions() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         TEST_OUTPUT.println("/op - null username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(null);
         if (!baosOut.toString().startsWith("/op <player_name>")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/op - blank username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/op - too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{});
         if (!baosOut.toString().equals("/op <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/op - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{"possibleID", "excessArgument"});
         if (!baosOut.toString().equals("error - wrong number of arguments: /op <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/op - valid username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestOp(new String[]{"opID"});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/deop - null username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(null);
         if (!baosOut.toString().startsWith("/deop <player_name>")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/deop - blank username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/deop - too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{});
         if (!baosOut.toString().equals("/deop <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/deop - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{"possibleID", "excessArgument"});
         if (!baosOut.toString().equals("error - wrong number of arguments: /deop <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("/deop - valid username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("isAnOp() - null username");
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic(null))) {
            TEST_OUTPUT.println("   unexpected value: true");
            errorFound = true;
         }

         TEST_OUTPUT.println("isAnOp() - blank username");
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic(""))) {
            TEST_OUTPUT.println("   unexpected value: true");
            errorFound = true;
         }

         TEST_OUTPUT.println("isAnOp() - checking for non-op");
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic("nonopID"))) {
            TEST_OUTPUT.println("   unexpected value: true");
            errorFound = true;
         }

         TEST_OUTPUT.println("isAnOp() - checking for op");
         if (!InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic("opID"))) {
            TEST_OUTPUT.println("   unexpected value: false");
            errorFound = true;
         }

         TEST_OUTPUT.println("adminPermissions() - giving multiple times, then revoking once");
         InterfaceTerminal.serviceRequestOp(new String[]{"nonopID"});
         InterfaceTerminal.serviceRequestOp(new String[]{"nonopID"});
         InterfaceTerminal.serviceRequestOp(new String[]{"nonopID"});
         InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
         if (InterfaceTerminal.isAnOp(InterfaceTerminal.getPlayerIDStatic("nonopID"))) {
            TEST_OUTPUT.println("   unexpected value: true");
            errorFound = true;

            // reset for other tests
            InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
            InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
            InterfaceTerminal.serviceRequestDeop(new String[]{"nonopID"});
         }

         TEST_OUTPUT.println("permissionToExecute() - non-op executing non-op command on self");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("nonopID"), InterfaceTerminal.getPlayerIDStatic("nonopID"), false)) {
            TEST_OUTPUT.println("   unexpected value: false");
            errorFound = true;
         }

         TEST_OUTPUT.println("permissionToExecute() - non-op executing non-op command on others");
         if (InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("opID"), InterfaceTerminal.getPlayerIDStatic("nonopID"), false)) {
            TEST_OUTPUT.println("   unexpected value: true");
            errorFound = true;
         }

         TEST_OUTPUT.println("permissionToExecute() - non-op executing op command");
         if (InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("nonopID"), InterfaceTerminal.getPlayerIDStatic("nonopID"), true)) {
            TEST_OUTPUT.println("   unexpected value: true");
            errorFound = true;
         }

         TEST_OUTPUT.println("permissionToExecute() - op executing non-op command on self");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("opID"), InterfaceTerminal.getPlayerIDStatic("opID"), false)) {
            TEST_OUTPUT.println("   unexpected value: false");
            errorFound = true;
         }

         TEST_OUTPUT.println("permissionToExecute() - op executing non-op command on others");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("nonopID"), InterfaceTerminal.getPlayerIDStatic("opID"), false)) {
            TEST_OUTPUT.println("   unexpected value: false");
            errorFound = true;
         }

         TEST_OUTPUT.println("permissionToExecute() - op executing op command");
         if (!InterfaceTerminal.permissionToExecute(InterfaceTerminal.getPlayerIDStatic("opID"), InterfaceTerminal.getPlayerIDStatic("opID"), true)) {
            TEST_OUTPUT.println("   unexpected value: false");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("adminPermissions() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests translateWareID().
    *
    * @return whether translateWareID() passed all test cases
    */
   private static boolean testUnitTranslateWareID() {
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
         TEST_OUTPUT.println("translateWareID() - null ware ID");
         if (!Marketplace.translateWareID(null).isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + Marketplace.translateWareID(null));
            errorFound = true;
         }

         TEST_OUTPUT.println("translateWareID() - empty ware ID");
         if (!Marketplace.translateWareID("").isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + Marketplace.translateWareID(""));
            errorFound = true;
         }

         TEST_OUTPUT.println("translateWareID() - invalid ware ID");
         if (!Marketplace.translateWareID("test:invalidWareID").isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + Marketplace.translateWareID("test:invalidWareID"));
            errorFound = true;
         }

         TEST_OUTPUT.println("translateWareID() - invalid ware alias");
         if (!Marketplace.translateWareID("invalidWareAlias").isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("translateWareID() - invalid variant ware ID");
         if (!Marketplace.translateWareID("test:invalidWareID&6").isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("translateWareID() - invalid variant ware alias");
         if (!Marketplace.translateWareID("invalidWareAlias&6").isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("translateWareID() - valid ware ID");
         if (!Marketplace.translateWareID("test:material1").equals("test:material1")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("test:material1") + ", should be test:material1");
         }
         if (!Marketplace.translateWareID("mat3").equals("test:material3")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("mat3") + ", should be test:material3");
         }

         TEST_OUTPUT.println("translateWareID() - valid variant ware ID");
         if (!Marketplace.translateWareID("test:material1&1").equals("test:material1&1")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("test:material1&1") + ", should be test:material1&1");
         }

         TEST_OUTPUT.println("translateWareID() - valid variant ware alias");
         if (!Marketplace.translateWareID("notrade1&1").equals("test:untradeable1&1")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("notrade1&1") + ", should be test:untradeable1&1");
         }

         TEST_OUTPUT.println("translateWareID() - unknown variant of valid ware ID");
         if (!Marketplace.translateWareID("test:material1&2").equals("test:material1")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("test:material1&2") + ", should be test:material1");
         }

         TEST_OUTPUT.println("translateWareID() - unknown variant of valid ware alias");
         if (!Marketplace.translateWareID("craft1&6").equals("test:crafted1")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("craft1&6") + ", should be test:crafted1");
         }

         TEST_OUTPUT.println("translateWareID() - existing Forge OreDictionary Name");
         wareAliasTranslations.put("#testName", "test:material2");
         if (!Marketplace.translateWareID("#testName").equals("test:material2")) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected result: " + Marketplace.translateWareID("#testName") + ", should be test:material2");
         }

         TEST_OUTPUT.println("translateWareID() - nonexistent Forge OreDictionary Name");
         if (!Marketplace.translateWareID("#invalidName").isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + Marketplace.translateWareID("#invalidName"));
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("translateWareID() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests getPrice().
    *
    * @return whether getPrice() passed all test cases
    */
   private static boolean testUnitGetPrice() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      float currentPrice = 0.0f;                   // holds price being check
      Ware testWare = wares.get("test:material2"); // for testing quantity's effect on price
      float expectedPrice = 0.0f;                   // holds price to be checked against
      int   quantity = 0;                           // holds ware's quantity available for sale
      try {
         TEST_OUTPUT.println("getPrice() - using null ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, null, 0, Marketplace.PriceType.CURRENT_SELL);
         if (!Float.isNaN(currentPrice)) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be NaN");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using level 0 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 1.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material1): " + currentPrice + ", should be 1.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using level 1 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 19.2f) {
            TEST_OUTPUT.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 19.2");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using level 2 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare3, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 4.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material3): " + currentPrice + ", should be 4.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using level 3 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare4, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 8.0f) {
            TEST_OUTPUT.println("   incorrect price (minecraft:material4): " + currentPrice + ", should be 8.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using level 4 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareP1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 1.1f) {
            TEST_OUTPUT.println("   incorrect price (test:processed1): " + currentPrice + ", should be 1.1");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using level 5 ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareP2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 14.3f) {
            TEST_OUTPUT.println("   incorrect price (test:processed2): " + currentPrice + ", should be 14.3");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - purchase without buying upcharge");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_BUY);
         if (currentPrice != 1.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material1): " + currentPrice + ", should be 1.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - purchase with buying upcharge");
         Config.priceBuyUpchargeMult = 2.0f;
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_BUY);
         if (currentPrice != 2.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material1): " + currentPrice + ", should be 2.0");
            errorFound = true;
         }

         // let's keep the math simple so it's easy to check
         // set price to $2
         Field testPriceBase = Ware.class.getDeclaredField("priceBase");
         testPriceBase.setAccessible(true);
         testPriceBase.setFloat(testWare, 2.0f);

         TEST_OUTPUT.println("getPrice() - using no quantity ware");
         testWare.setQuantity(0);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 4.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 4.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using understocked ware");
         testWare.setQuantity(63);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 4.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 4.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using low stock ware");
         testWare.setQuantity(79);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 3.5f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 3.5");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using pretty low stock ware");
         testWare.setQuantity(95);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 3.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 3.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using kinda low stock ware");
         testWare.setQuantity(111);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 2.5f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 2.5");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using kinda high stock ware");
         testWare.setQuantity(223);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 1.5f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 1.5");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using pretty high stock ware");
         testWare.setQuantity(319);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 1.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 1.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using high stock ware");
         testWare.setQuantity(415);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 0.5f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 0.5");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using overstocked ware");
         testWare.setQuantity(511);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 0.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 0.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using excessively overstocked ware");
         testWare.setQuantity(1023);
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare2, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 0.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material2): " + currentPrice + ", should be 0.0");
            errorFound = true;
         }

         // set average price base and start quantities to values used in expected calculations
         fPriceBaseAverage.setFloat(null, 9.23f);
         fStartQuanBaseMedian.setFloat(null, 87);

         Config.priceSpread = 2.0f;
         TEST_OUTPUT.println("getPrice() - using high spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare3, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 0.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material3): " + currentPrice + ", should be 0.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using high spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 30.4f) {
            TEST_OUTPUT.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 30.4");
            errorFound = true;
         }

         Config.priceSpread = 1.5f;
         TEST_OUTPUT.println("getPrice() - using fairly high spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare3, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 2.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material3): " + currentPrice + ", should be 2.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using fairly high spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 24.8f) {
            TEST_OUTPUT.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 24.8");
            errorFound = true;
         }

         Config.priceSpread = 0.75f;
         TEST_OUTPUT.println("getPrice() - using fairly low spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare3, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 5.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material3): " + currentPrice + ", should be 5.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using fairly low spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 16.4f) {
            TEST_OUTPUT.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 16.4");
            errorFound = true;
         }

         Config.priceSpread = 0.5f;
         TEST_OUTPUT.println("getPrice() - using low spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare3, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 6.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material3): " + currentPrice + ", should be 6.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using low spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 13.6f) {
            TEST_OUTPUT.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 13.6");
            errorFound = true;
         }

         Config.priceSpread = 0.0f;
         TEST_OUTPUT.println("getPrice() - using zero spread with inexpensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare3, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 8.0f) {
            TEST_OUTPUT.println("   incorrect price (test:material3): " + currentPrice + ", should be 8.0");
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - using zero spread with expensive ware");
         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);
         if (currentPrice != 8.0f) {
            TEST_OUTPUT.println("   incorrect price (test:crafted1): " + currentPrice + ", should be 8.0");
            errorFound = true;
         }

         // prepare for next tests
         Config.priceSpread        =  1.0f;
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         double quanCeilingFromEquilibrium = Config.quanExcessive[testWare1.getLevel()] - Config.quanEquilibrium[testWare1.getLevel()];

         TEST_OUTPUT.println("getPrice() - negative prices, at -100% cost");
         expectedPrice = testWare1.getBasePrice() * Config.priceFloor;
         quantity      = Config.quanExcessive[testWare1.getLevel()];
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareP1.getBasePrice() * Config.priceFloor;
         quantity      = Config.quanExcessive[testWareP1.getLevel()];
         testWareP1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareP1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - negative prices, at -50% cost");
         expectedPrice = testWare1.getBasePrice() * Config.priceFloor * 0.50f;
         quantity      = Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75) - 1;
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareP1.getBasePrice() * Config.priceFloor * 0.50f;
         quantity      = Config.quanEquilibrium[testWareP1.getLevel()] + (int) ((Config.quanExcessive[testWareP1.getLevel()] - Config.quanEquilibrium[testWareP1.getLevel()]) * 0.75) - 1;
         testWareP1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareP1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - negative prices, at no cost");
         expectedPrice = 0.0f;
         quantity      = Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.5) - 1;
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = 0.0f;
         quantity      = Config.quanEquilibrium[testWareC1.getLevel()] + (int) ((Config.quanExcessive[testWareC1.getLevel()] - Config.quanEquilibrium[testWareC1.getLevel()]) * 0.5) - 1;
         testWareC1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - negative prices, at 50% cost");
         expectedPrice = testWare1.getBasePrice() * Config.priceFloor * -0.50f;
         quantity      = Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25) - 1;
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareP2.getBasePrice() * Config.priceFloor * -0.50f;
         quantity      = Config.quanEquilibrium[testWareP2.getLevel()] + (int) ((Config.quanExcessive[testWareP2.getLevel()] - Config.quanEquilibrium[testWareP2.getLevel()]) * 0.25) - 1;
         testWareP2.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareP2, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         TEST_OUTPUT.println("getPrice() - negative prices, at equilibrium");
         expectedPrice = testWare1.getBasePrice();
         quantity      = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWare1, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }

         expectedPrice = testWareC2.getBasePrice();
         quantity      = Config.quanEquilibrium[testWareC2.getLevel()];
         testWareC2.setQuantity(quantity);

         currentPrice = Marketplace.getPrice(PLAYER_ID, testWareC2, 0, Marketplace.PriceType.CURRENT_SELL);

         if (currentPrice != expectedPrice) {
            TEST_OUTPUT.println("   incorrect price: " + currentPrice + ", should be " + expectedPrice);
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("getPrice() - fatal error: " + e);
         errorFound = true;
      }

      return !errorFound;
   }

   /**
    * Tests check().
    *
    * @return whether check() passed all test cases
    */
   private static boolean testUnitCheck() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // set up test variables
      int   quantity;
      float price1;
      float price2;

      try {
         TEST_OUTPUT.println("check() - null ware ID");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, null, 1, 1.0f);
         if (!baosOut.toString().equals("error - no ware ID was given" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - empty ware ID");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "", 1, 1.0f);
         if (!baosOut.toString().equals("error - no ware ID was given" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "invalidID", 1, 1.0f);
         if (!baosOut.toString().equals("error - ware not found: invalidID" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - untradeable ware ID");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareU1, 1, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using valid ware ID without alias and buying upcharge");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using valid ware ID with alias and without buying upcharge");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare3, 1, null,
                                   1.0f, 0, false);

         Config.priceBuyUpchargeMult = 2.0f;
         TEST_OUTPUT.println("check() - using valid ware ID without alias and with buying upcharge");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareP2, 1, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using valid ware ID with alias and buying upcharge");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareC1, 1, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using zero quantity with buying upcharge");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 0, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using high quantity with buying upcharge");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 10, null,
                                   1.0f, 0, false);
         Config.priceBuyUpchargeMult = 1.0f;

         TEST_OUTPUT.println("check() - using negative quantity");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, -1, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using zero quantity");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 0, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using singular quantity");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 1, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using double quantity");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 2, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - using high quantity");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 10, null,
                                   1.0f, 0, false);

         TEST_OUTPUT.println("check() - referencing ware using alias");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(PLAYER_ID, "material4", 10, 1.0f);
         if (!baosOut.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for 10: Buy - $102.50 | Sell - $75.50" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - null username");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(InterfaceTerminal.getPlayerIDStatic(null), "material4", 10, 1.0f);
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - empty username");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(InterfaceTerminal.getPlayerIDStatic(""), "material4", 10, 1.0f);
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - different username");
         baosOut.reset(); // clear buffer holding console output
         Marketplace.check(InterfaceTerminal.getPlayerIDStatic("possibleID"), "material4", 10, 1.0f);
         if (!baosOut.toString().startsWith("(for possibleID) material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "(for possibleID)    for 10: Buy - $102.50 | Sell - $75.50" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium = Config.quanExcessive[testWare1.getLevel()] - Config.quanEquilibrium[testWare1.getLevel()];

         TEST_OUTPUT.println("check() - negative prices, at -100% cost");
         testWare1.setQuantity(Config.quanExcessive[testWare1.getLevel()]);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 1, false);

         testWareP1.setQuantity(Config.quanExcessive[testWareP1.getLevel()]);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareP1, 1, null,
                                   1.0f, 2, false);

         TEST_OUTPUT.println("check() - negative prices, at -50% cost");
         testWareP1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f) - 1);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 1, false);

         testWareC2.setQuantity(Config.quanEquilibrium[testWareC2.getLevel()] + (int) ((Config.quanExcessive[testWareC2.getLevel()] - Config.quanEquilibrium[testWareC2.getLevel()]) * 0.75f) - 1);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareC2, 1, null,
                                   1.0f, 2, false);

         TEST_OUTPUT.println("check() - negative prices, at no cost");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f) - 1);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 1, false);

         testWareP2.setQuantity(Config.quanEquilibrium[testWareP2.getLevel()] + (int) ((Config.quanExcessive[testWareP2.getLevel()] - Config.quanEquilibrium[testWareP2.getLevel()]) * 0.50f) - 1);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareP2, 1, null,
                                   1.0f, 2, false);

         TEST_OUTPUT.println("check() - negative prices, at 50% cost");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25f) - 1);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 1, false);

         testWareC1.setQuantity(Config.quanEquilibrium[testWareC1.getLevel()] + (int) ((Config.quanExcessive[testWareC1.getLevel()] - Config.quanEquilibrium[testWareC1.getLevel()]) * 0.25f) - 1);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareC1, 1, null,
                                   1.0f, 1, false);

         TEST_OUTPUT.println("check() - negative prices, at equilibrium");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()]);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 1, false);

         testWareC1.setQuantity(Config.quanEquilibrium[testWareC1.getLevel()]);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareC1, 1, null,
                                   1.0f, 1, false);

         // check ware_id [quantity]
         TEST_OUTPUT.println("check() - request: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(null);
         if (!baosOut.toString().equals("/check (<ware_id> | held) [quantity]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{});
         if (!baosOut.toString().equals("/check (<ware_id> | held) [quantity]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{});
         if (!baosOut.toString().equals("/check (<ware_id> | held) [quantity]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"test:material1", "10", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("check() - request: invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"invalidWareID"});
         if (!baosOut.toString().equals("error - ware not found: invalidWareID" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("check() - request: invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"test:material1", "invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("check() - request: minimum args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"test:material1"});
         if (!baosOut.toString().equals("test:material1: $1.00, 256" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: valid quantity");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare4, 10, null,
                                   1.0f, 0, true);

         TEST_OUTPUT.println("check() - request: referencing ware using alias");
         quantity = 10;
         price1   = Marketplace.getPrice(PLAYER_ID, testWare4, quantity, Marketplace.PriceType.CURRENT_BUY);
         price2   = Marketplace.getPrice(PLAYER_ID, testWare4, quantity, Marketplace.PriceType.CURRENT_SELL);
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"material4", String.valueOf(quantity)});
         if (!baosOut.toString().equals("material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "   for " + quantity + ": Buy - $" + String.format("%.2f", price1) + " | Sell - $" + String.format("%.2f", price2) + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: null username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{null, "material4", "10"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: empty username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"", "material4", "10"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: different username");
         quantity = 10;
         price1   = Marketplace.getPrice(PLAYER_ID, testWare4, quantity, Marketplace.PriceType.CURRENT_BUY);
         price2   = Marketplace.getPrice(PLAYER_ID, testWare4, quantity, Marketplace.PriceType.CURRENT_SELL);
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"possibleID", "material4", String.valueOf(quantity)});
         if (!baosOut.toString().equals("(for possibleID) material4 (minecraft:material4): $8.00, 32" + System.lineSeparator() + "(for possibleID)    for " + quantity + ": Buy - $" + String.format("%.2f", price1) + " | Sell - $" + String.format("%.2f", price2) + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: command block variant without permissions");
         quantity = 1;
         price1   = Marketplace.getPrice(PLAYER_ID, testWare4, quantity, Marketplace.PriceType.CURRENT_BUY);
         price2   = Marketplace.getPrice(PLAYER_ID, testWare4, quantity, Marketplace.PriceType.CURRENT_SELL);
         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "notAnOp";
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"notAnOp", "material4", String.valueOf(quantity)});
         if (!baosOut.toString().startsWith("material4 (minecraft:material4): $8.00, 32")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: checking for others without permissions");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"possibleID", "material4", String.valueOf(quantity)});
         if (!baosOut.toString().startsWith("You do not have permission to use this command for other players")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         InterfaceTerminal.playername = playernameOrig;

         TEST_OUTPUT.println("check() - request: existing Forge OreDictionary Name");
         wareAliasTranslations.put("#testName", "test:material2");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"#testName"});
         if (!baosOut.toString().equals("test:material2: $55.20, 5" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("check() - request: nonexistent Forge OreDictionary Name");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCheck(new String[]{"#invalidName"});
         if (!baosOut.toString().equals("error - ware not found: #invalidName" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("check() - fatal error: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests buy().
    *
    * @return whether buy() passed all test cases
    */
   private static boolean testUnitBuy() {
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
         TEST_OUTPUT.println("buy() - null account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - empty account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - invalid account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "invalidAccount", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - using account without permission");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount4", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - null ware ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", null, quantityToTrade, 0.0f, 1.0f);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - empty ware ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", "", quantityToTrade, 0.0f, 1.0f);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - invalid ware ID");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", "invalidWare", quantityToTrade, 0.0f, 1.0f);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - untradeable ware ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWareU1, "testAccount1", null, null, Float.NaN,
                                   0, 1, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying no quantity of ware");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 0, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying negative quantity of ware");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, -1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying out-of-stock ware");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   0, 1, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying without inventory space");
         int inventorySpaceOrig = InterfaceTerminal.inventorySpace;
         InterfaceTerminal.inventorySpace = 0; // maximum inventory space is no inventory
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, true, false);
         InterfaceTerminal.inventorySpace = inventorySpaceOrig; // reset maximum inventory space

         TEST_OUTPUT.println("buy() - buying without any money");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, 0.0f,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 1, 0, false, true, false);
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - over-ordering, (quad4 to quad4) overstocked to overstocked");
         Config.priceFloor = 0.1f;
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, 10.0f,
                                   Config.quanExcessive[testWare1.getLevel()] * 2, 100, 200, 0, false, true, false);

         TEST_OUTPUT.println("buy() - over-ordering, (quad4 to quad3), overstocked to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, 10.0f,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 128, 200, 0, false, true, false);
         InterfaceTerminal.inventory.clear();

         TEST_OUTPUT.println("buy() - over-ordering, (quad4 to quad2), overstocked to below equilibrium");
         testWare1.setQuantity(Config.quanExcessive[testWare1.getLevel()] + 10);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Marketplace.getPrice(PLAYER_ID, testWare1, 832, Marketplace.PriceType.CURRENT_BUY) + testWare1.getBasePrice() / 2,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 832, 999, 0, false, true, false);

         TEST_OUTPUT.println("buy() - over-ordering, (quad4 to quad1), overstocked to understocked");
         testWare1.setQuantity(Config.quanExcessive[testWare1.getLevel()] + 10);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Marketplace.getPrice(PLAYER_ID, testWare1, 934, Marketplace.PriceType.CURRENT_BUY) + testWare1.getBasePrice() / 2,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 934, 999, 0, false, true, false);
         Config.priceFloor = 0.0f;

         TEST_OUTPUT.println("buy() - over-ordering, (quad3 to quad3), above equilibrium to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, 10.0f,
                                   Config.quanEquilibrium[testWare1.getLevel()] * 2, 14, 20, 0, false, true, false);

         TEST_OUTPUT.println("buy() - over-ordering, (quad3 to quad2), above equilibrium to below equilibrium");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()] * 2);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Marketplace.getPrice(PLAYER_ID, testWare1, Config.quanEquilibrium[testWare1.getLevel()] * 2 - Config.quanDeficient[testWare1.getLevel()] - 10, Marketplace.PriceType.CURRENT_BUY) + testWare1.getBasePrice(),
                                   Config.quanEquilibrium[testWare1.getLevel()] * 2, Config.quanEquilibrium[testWare1.getLevel()] * 2 - Config.quanDeficient[testWare1.getLevel()] - 10, 999, 0, false, true, false);

         TEST_OUTPUT.println("buy() - over-ordering, (quad3 to quad1), above equilibrium to understocked");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()] * 2);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Marketplace.getPrice(PLAYER_ID, testWare1, 448, Marketplace.PriceType.CURRENT_BUY) + testWare1.getBasePrice(),
                                   Config.quanEquilibrium[testWare1.getLevel()] * 2, 448, 999, 0, false, true, false);

         TEST_OUTPUT.println("buy() - (equil to quad2) equilibrium to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", 1.0f,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 999, 0, false, true, false);
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - over-ordering, (quad2 to quad2), below equilibrium to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", 10.0f,
                                   192, 6, 999, 1, false, true, false);

         testWare1.setQuantity(224);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Marketplace.getPrice(PLAYER_ID, testWare1, 64, Marketplace.PriceType.CURRENT_BUY) + testWare1.getBasePrice(),
                                   224, 64, 999, 2, false, true, false);

         TEST_OUTPUT.println("buy() - over-ordering, (quad2 to quad1), below equilibrium to understocked");
         testWare1.setQuantity(192);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Marketplace.getPrice(PLAYER_ID, testWare1, 192 - Config.quanDeficient[testWare1.getLevel()] + 20, Marketplace.PriceType.CURRENT_BUY) + testWare1.getBasePrice(),
                                   192, 192 - Config.quanDeficient[testWare1.getLevel()] + 20, 999, 0, false, true, false);

         TEST_OUTPUT.println("buy() - over-ordering, (quad1 to quad1), understocked to understocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", 10.0f,
                                   Config.quanEquilibrium[testWare1.getLevel()] / 3, 5, 999, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying some of a ware with means to buy more");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 8, 8, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying-out ware by ordering more than is available");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWare1.getLevel()], 1000, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying ware with max price acceptable being NaN");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, Float.toString(Float.NaN), Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying ware with max price acceptable being too low");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "0.1", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 1, 0, false, true, false);

         TEST_OUTPUT.println("buy() - buying ware with max price acceptable being high");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, true, false);

         // Effects of scarcity and price ceilings are not tested here
         // since those tests are more appropriate in Marketplace.getPrice().
         // buy() handles purchasing wares, ensuring stock is reduced and accounts are charged
         // when appropriate, not managing the cascading effects of those actions.

         TEST_OUTPUT.println("buy() - referencing ware using alias");
         quantityToTrade = 2;
         quantityWare    = testWare3.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare3, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", "mat3", quantityToTrade, 100.0f, 1.0f);
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - null coordinates");
         quantityToTrade = 5;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", "test:material1", quantityToTrade, 100.0f, 1.0f);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - invalid coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, "invalidCoordinates", testWare1, "testAccount1", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 5, 0, false, true, false);
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - valid coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NORTH, testWare1, "testAccount1", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, true, false);

         TEST_OUTPUT.println("buy() - zeroed coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NONE, testWare1, "testAccount1", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, true, false);

         TEST_OUTPUT.println("buy() - null username");
         errorFound |= testerTrade(null, null, testWare1, "testAccount4", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 3, 0, false, true, false);

         TEST_OUTPUT.println("buy() - empty username");
         errorFound |= testerTrade("", null, testWare1, null, null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 3, 0, false, true, false);

         TEST_OUTPUT.println("buy() - different username");
         errorFound |= testerTrade("possibleID", null, testWare1, "testAccount3", null, "100.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 20, 20, 0, false, true, false);

         // buy ware_id quantity [max_unit_price] [account_id]
         TEST_OUTPUT.println("buy() - request: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(null);
         if (!baosOut.toString().equals("/buy <ware_id> <quantity> [max_unit_price] [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("buy() - request: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{});
         if (!baosOut.toString().equals("/buy <ware_id> <quantity> [max_unit_price] [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("buy() - request: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("buy() - request: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1"});
         if (!baosOut.toString().equals("error - wrong number of arguments: /buy <ware_id> <quantity> [max_unit_price] [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("buy() - request: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "10.0", "testAccount1", "excessArgument", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"invalidWare", "10"});
         if (!baosOut.toString().startsWith("error - ware not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: invalid price");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "invalidPrice", "testAccount1"});
         if (!baosOut.toString().startsWith("error - invalid price")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: invalid account ID without price");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: invalid account ID with valid price");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "10", "10.0", "invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: minimum args");
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade)});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: valid price");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - request: valid account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount2", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10,
                                   0, false, true, true);

         TEST_OUTPUT.println("buy() - request: valid price and account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount2", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10,
                                   0, false, true, true);

         TEST_OUTPUT.println("buy() - request: referencing ware using alias");
         testWare4.setQuantity(32);
         testAccount2.setMoney(20.0f);
         quantityToTrade    = 2;
         price              = Marketplace.getPrice(PLAYER_ID, testWare4, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money              = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestBuy(new String[]{"material4", String.valueOf(quantityToTrade), "100.0", "testAccount2"});
         if (testAccountFields(testAccount2, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         // reset changed variables to avoid interfering with other tests
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - request: null coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, null, "test:material1", "10"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: invalid coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, "invalidCoordinates", "test:material1", "10"});
         if (!baosOut.toString().startsWith("error - invalid quantity")
         ) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - request: valid coordinates");
         quantityToTrade = 10;
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, "south", "test:material1", String.valueOf(quantityToTrade)});
         if (!baosOut.toString().startsWith("Bought 10 test:material1 for $" + String.format("%.2f", price) + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventorySouth.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventorySouth.get("test:material1") != quantityToTrade) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventorySouth.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - request: zeroed coordinates");
         quantityToTrade = 10;
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{InterfaceTerminal.playername, "none", "test:material1", String.valueOf(quantityToTrade)});
         if (!baosOut.toString().startsWith("Bought 10 test:material1 for $" + String.format("%.2f", price) + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - request: null username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{null, "none", "test:material1", "10"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: empty username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"", "none", "test:material1", "10"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does contains ware when it shouldn't");
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("buy() - request: different username");
         quantityToTrade = 20;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount3.getMoney();
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"possibleID", "none", "test:material1", String.valueOf(quantityToTrade), "testAccount3"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount3, money - price, "possibleID")) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         if (!baosOut.toString().startsWith("(for possibleID) Bought " + quantityToTrade + " test:material1 for " + CommandEconomy.PRICE_FORMAT.format(price) + " taken from testAccount3" + System.lineSeparator())
         ) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            TEST_OUTPUT.println("   expected: " + "(for possibleID) Bought " + quantityToTrade + " test:material1 for " + CommandEconomy.PRICE_FORMAT.format(price) + " taken from testAccount3" + System.lineSeparator());
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - existing Forge OreDictionary Name");
         wareAliasTranslations.put("#testName", "test:material2");
         testWare2.setQuantity(1000);
         quantityToTrade = 2;
         quantityWare    = testWare2.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare2, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", "#testName", quantityToTrade, 0.0f, 1.0f);
         if (testWareFields(testWare2, WareMaterial.class, "", (byte) 1, 27.6f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - nonexistent Forge OreDictionary Name");
         quantityToTrade = 1;
         quantityWare    = testWare1.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.buy(PLAYER_ID, null, "testAccount1", "#invalidName", quantityToTrade, 0.0f, 1.0f);
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - request: admin account");
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade), "$admin$"});
         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            TEST_OUTPUT.println("   console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("buy() - insufficient money with specified price");
         quantityToTrade = 10;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = price + FLOAT_COMPARE_PRECISION;
         playerAccount.setMoney(money);

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade * 2), "100"});

         if (testWare1.getQuantity() != quantityWare - quantityToTrade) {
            TEST_OUTPUT.println("   unexpected quantity: " + testWare1.getQuantity() + ", should be " + (quantityWare - quantityToTrade));
            errorFound = true;
         }
         if (playerAccount.getMoney() != money - price) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected account funds: " + playerAccount.getMoney() + ", should be " + (money - price));
         }

         TEST_OUTPUT.println("buy() - insufficient money with specified price and account");
         quantityToTrade = 8;
         quantityWare    = testWare1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = price + FLOAT_COMPARE_PRECISION;
         playerAccount.setMoney(money);

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", String.valueOf(quantityToTrade * 2), "10", InterfaceTerminal.playername});

         if (testWare1.getQuantity() != quantityWare - quantityToTrade) {
            TEST_OUTPUT.println("   unexpected quantity: " + testWare1.getQuantity() + ", should be " + (quantityWare - quantityToTrade));
            errorFound = true;
         }
         if (playerAccount.getMoney() != money - price) {
            errorFound = true;
            TEST_OUTPUT.println("   unexpected account funds: " + playerAccount.getMoney() + ", should be " + (money - price));
         }

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium = Config.quanExcessive[testWare1.getLevel()] - Config.quanEquilibrium[testWare1.getLevel()];

         TEST_OUTPUT.println("buy() - negative prices, at -100% cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Float.NaN,
                                   Config.quanExcessive[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - negative prices, at -50% cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f),
                                   20, 20, 0, false, true, true);

         TEST_OUTPUT.println("buy() - negative prices, at no cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f),
                                   5, 5, 0, false, true, true);

         TEST_OUTPUT.println("buy() - negative prices, at 50% cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25f),
                                   5, 5, 0, false, true, true);

         TEST_OUTPUT.println("buy() - negative prices, at equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 9, 9, 0, false, true, true);

         TEST_OUTPUT.println("buy() - negative prices, overbuying");
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;

         quantityToTrade = 777;
         quantityWare    = Config.quanExcessive[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare);
         price           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY);
         money           = testAccount1.getMoney();

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestBuy(new String[]{"test:material1", "99999", "testAccount1"});

         if (testWareFields(testWare1, WareMaterial.class, "", (byte) 0, 1.0f, quantityWare - quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != quantityToTrade) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain " + quantityToTrade);
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("buy() - acceptable price, (quad4 to quad4), overstocked to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "0.0001", Float.NaN,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 12, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad4 to quad3), overstocked to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "0.5", Float.NaN,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 395, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad4 to quad2), overstocked to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "1.5", Float.NaN,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 843, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad4 to quad1), overstocked to understocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "3.0", Float.NaN,
                                   Config.quanExcessive[testWare1.getLevel()] + 10, 1034, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad3 to quad3), above equilibrium to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "0.75", Float.NaN,
                                   639, 192, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad3 to quad2), above equilibrium to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "1.25", Float.NaN,
                                   639, 416, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad3 to quad1), above equilibrium to understocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "1.99", Float.NaN,
                                   639, 511, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad2 to quad2), below equilibrium to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "1.75", Float.NaN,
                                   191, 32, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad2 to quad1), below equilibrium to understocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "2.0", Float.NaN,
                                   191, 191, 99999, 0, false, true, true);

         TEST_OUTPUT.println("buy() - acceptable price, (quad2 to quad1), below equilibrium to understocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "2.0", Float.NaN,
                                   128, 128, 99999, 0, false, true, true);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("buy() - fatal error: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests sell().
    *
    * @return whether sell() passed all test cases
    */
   private static boolean testUnitSell() {
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
         TEST_OUTPUT.println("sell() - null account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, false, false);

         TEST_OUTPUT.println("sell() - empty account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 1, 1, 0, false, false, false);

         TEST_OUTPUT.println("sell() - invalid account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "invalidAccount", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 1, 0, false, false, false);

         TEST_OUTPUT.println("sell() - using account without permission");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount4", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 1, 0, false, false, false);

         TEST_OUTPUT.println("sell() - null ware ID");
         money = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", null, 1, 0.0f, 1.0f);
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - empty ware ID");
         money = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", "", 1, 0.0f, 1.0f);
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - invalid ware ID");
         money = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", "invalidWare", 1, 0.0f, 1.0f);
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - selling no quantity of ware");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 100, 0, 0, false, false, false);

         TEST_OUTPUT.println("sell() - selling negative quantity of ware");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, -1, 0, false, false, false);
         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - selling ware player does not have");
         quantityWare    = testWare2.getQuantity();
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", "test:material2", 1, 0.0f, 1.0f);
         if (testWareFields(testWare2, WareMaterial.class, "", (byte) 1, 27.6f, quantityWare)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - selling without sufficient stock to fill entire order");
         InterfaceTerminal.inventory.put("test:processed1", 10);    // give player some of the ware to be sold
         quantityToTrade = 10;
         quantityWare    = testWareP1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", "test:processed1", 20, 0.0f, 1.0f);
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - selling some of a ware with means to sell more");
         InterfaceTerminal.inventory.put("test:processed1", 10);    // give player some of the ware to be sold
         quantityToTrade = 8;
         quantityWare    = testWareP1.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", "test:processed1", quantityToTrade, 0.0f, 1.0f);
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) 4, 1.1f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("sell() - selling ware with min price acceptable being NaN");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWareP1, "testAccount1", null, Float.toString(Float.NaN), Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, false);

         TEST_OUTPUT.println("sell() - selling ware with min price acceptable being too high");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "10.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, false);
         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - selling ware with min price acceptable being low");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "0.1", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, false, false);
         resetTestEnvironment();

         // Effects of surplus and price floors are not tested here
         // since those tests are more appropriate in Marketplace.getPrice().
         // sell() handles vending wares, ensuring stock is increased and accounts are paid
         // when appropriate, not managing the cascading effects of those actions.Marketplace.getPrice(PLAYER_ID, testWare4, quantityToTrade, Marketplace.PriceType.CURRENT_SELL

         TEST_OUTPUT.println("sell() - referencing ware using alias");
         InterfaceTerminal.inventory.put("test:material3", 10);    // give player a ware with an alias
         quantityToTrade = 2;
         quantityWare    = testWare3.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare3, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount1.getMoney();
         Marketplace.sell(PLAYER_ID, null, "testAccount1", "mat3", quantityToTrade, 0.1f, 1.0f);
         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) 2, 4.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("sell() - null coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, false, false);

         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - invalid coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, "invalidCoordinates", testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 5, 0, false, false, false);

         TEST_OUTPUT.println("sell() - valid coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_EAST, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, false, false);

         TEST_OUTPUT.println("sell() - zeroed coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NONE, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 5, 5, 0, false, false, false);

         TEST_OUTPUT.println("sell() - null username");
         errorFound |= testerTrade(null, null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 5, 0, false, false, false);

         TEST_OUTPUT.println("sell() - empty username");
         errorFound |= testerTrade("", null, testWare1, "testAccount1", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 5, 0, false, false, false);

         TEST_OUTPUT.println("sell() - different username");
         errorFound |= testerTrade("possibleID", null, testWare1, "testAccount3", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 20, 20, 0, false, false, false);

         // sell ware_id [quantity] [min_unit_price] [account_id]
         TEST_OUTPUT.println("sell() - request: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(null);
         if (!baosOut.toString().equals("/sell (<ware_id> | held) [<quantity> [min_unit_price] [account_id]]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sell() - request: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{});
         if (!baosOut.toString().equals("/sell (<ware_id> | held) [<quantity> [min_unit_price] [account_id]]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sell() - request: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sell() - request: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{});
         if (!baosOut.toString().equals("/sell (<ware_id> | held) [<quantity> [min_unit_price] [account_id]]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sell() - request: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "10", "0.1", "testAccount1", "excessArgument", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - request: invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"invalidWare", "10"});
         if (!baosOut.toString().startsWith("error - ware not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - request: invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSell(new String[]{"test:material1", "invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - request: invalid price");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", null, "invalidPrice", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: invalid account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "invalidAccount", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: minimum args");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: valid price");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.1", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: valid account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount2", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);
         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - request: valid price and account ID");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount2", null, "0.1", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: referencing ware using alias");
         InterfaceTerminal.inventory.put("minecraft:material4", 100);
         quantityToTrade = 10;
         quantityWare    = testWare4.getQuantity();
         price           = Marketplace.getPrice(PLAYER_ID, testWare4, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount2.getMoney();
         InterfaceTerminal.serviceRequestSell(new String[]{"material4", String.valueOf(quantityToTrade), "0.1", "testAccount2"});
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) 3, 8.0f, quantityWare + quantityToTrade)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount2, money + price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("sell() - request: null coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 20); // give wares to the player
         InterfaceTerminal.serviceRequestSell(new String[]{InterfaceTerminal.playername, null, "test:material1", "10"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sell() - request: invalid coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, "invalidCoordinates", testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: valid coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_UP, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: zeroed coordinates");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NONE, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: null username");
         errorFound |= testerTrade(null, CommandEconomy.INVENTORY_NONE, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: empty username");
         errorFound |= testerTrade("", CommandEconomy.INVENTORY_NONE, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - request: different username");
         errorFound |= testerTrade("possibleID", CommandEconomy.INVENTORY_NONE, testWare1, "testAccount3", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 20, 20, 0, false, false, true);

         // The Terminal interface cannot test selling wares using Forge OreDictionary names
         // since its wares do not use Forge OreDictionary names.

         TEST_OUTPUT.println("sell() - request: admin account");
         errorFound |= testerTrade(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NONE, testWare1, "$admin$", null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         // prepare for next tests
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium = Config.quanExcessive[testWare1.getLevel()] - Config.quanEquilibrium[testWare1.getLevel()];

         TEST_OUTPUT.println("sell() - negative prices, at -100% cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", Float.NaN,
                                   Config.quanExcessive[testWare1.getLevel()], 5, 5, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, at -50% cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.75f), 5, 5, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, at no cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-3.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.50f), 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, at 50% cost");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-3.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium * 0.25f), 15, 15, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, at equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-3.0", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         /*
         These commented-out tests check whether paying to get rid of undesirable wares
         is limited by account funds. Currently, this feature is not implemented.
         Paying to rid of undesirable wares may induce large amounts of debt.
         */
         /*
         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad4 to quad4) overstocked to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 30.0f,
                                   Config.quanExcessive[testWare1.getLevel()], 30, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad3 to quad4) above equilibrium to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 30.0f,
                                   Config.quanExcessive[testWare1.getLevel()] - 20, 30, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad3 to quad3) above equilibrium to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 30.0f,
                                   Config.quanExcessive[testWare1.getLevel()] - 300, 89, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad3 to quad4) above equilibrium to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 203.0f,
                                   Config.quanEquilibrium[testWare1.getLevel()] + 10, 768, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad2 to quad3) below equilibrium to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 30.0f,
                                   Config.quanEquilibrium[testWare1.getLevel()] + 10, 525, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad2 to quad4) below equilibrium to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 203.0f,
                                   Config.quanDeficient[testWare1.getLevel()] + 10, 906, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad1 to quad3) understocked to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 203.0f,
                                   Config.quanDeficient[testWare1.getLevel()] - 10, 673, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - negative prices, overselling, (quad1 to quad4) understocked to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "-5.0", 203.0f,
                                   Config.quanDeficient[testWare1.getLevel()] - 10, 916, 9999, 0, false, false, true);
         */

         // prepare for next tests
         Config.priceFloor         = 0.0f;
         Config.priceFloorAdjusted = 1.0f;

         TEST_OUTPUT.println("sell() - acceptable price, (quad1 to quad1), understocked to understocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "2.0", Float.NaN,
                                   64, 64, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad1 to quad2), understocked to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "1.5", Float.NaN,
                                   64, 127, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad1 to quad3), understocked to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.5", Float.NaN,
                                   64, 575, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad1 to quad4), understocked to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.0", Float.NaN,
                                   64, 9999, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad2 to quad2), below equilibrium to below equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "1.25", Float.NaN,
                                   191, 32, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad2 to quad3), below equilibrium to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.5", Float.NaN,
                                   191, 448, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad2 to quad4), below equilibrium to overstocked equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.0001", Float.NaN,
                                   191, 831, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad3 to quad3), above equilibrium to above equilibrium");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.25", Float.NaN,
                                   639, 192, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad3 to quad4), above equilibrium to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.0", Float.NaN,
                                   639, 9999, 9999, 0, false, false, true);

         TEST_OUTPUT.println("sell() - acceptable price, (quad4 to quad4), overstocked equilibrium to overstocked");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, "0.0", Float.NaN,
                                   1034, 9999, 9999, 0, false, false, true);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("sell() - fatal error: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests sellAll().
    *
    * @return whether sellAll() passed all test cases
    */
   private static boolean testUnitSellAll() {
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
         TEST_OUTPUT.println("sellAll() - null account ID");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, null,
                                     testWare1, testWare3, null,
                                     100, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - empty account ID");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "", null,
                                     testWare1, testWare3, null,
                                     100, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - invalid account ID");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "invalidAccount", null,
                                     testWare1, testWare3, null,
                                     100, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - using account without permission");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "testAccount4", null,
                                     testWare1, testWare3, null,
                                     100, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - with only valid wares");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "testAccount1", null,
                                     testWare1, testWare3, testWareP1,
                                     100, 20, 10, 0, false);
         resetTestEnvironment();

         TEST_OUTPUT.println("sellAll() - with both valid and invalid wares");
         quantityToTrade1 = 100;
         quantityWare1    = testWare1.getQuantity();
         quantityToTrade2 = 20;
         quantityWare2    = testWare3.getQuantity();
         quantityToTrade3 = 10;
         quantityWare3    = testWareP1.getQuantity();
         price1           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         price2           = Marketplace.getPrice(PLAYER_ID, testWare3, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
         price3           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade3, Marketplace.PriceType.CURRENT_SELL);
         money           = testAccount1.getMoney();

         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", quantityToTrade1);
         InterfaceTerminal.inventory.put("test:material3",  quantityToTrade2);
         InterfaceTerminal.inventory.put("test:processed1", quantityToTrade3);
         InterfaceTerminal.inventory.put("invalidWare", 10);

         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), "testAccount1", 1.0f);

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
               TEST_OUTPUT.println("   invalid ware is missing when it should be in the inventory");
            else
               TEST_OUTPUT.println("   invalid ware should have 10 stock, has " + InterfaceTerminal.inventory.get("invalidWare"));
         }
         // check test account
         if (testAccountFields(testAccount1, money + price1 + price2 + price3 - (float) 1e-4, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("sellAll() - null coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "testAccount1", null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - invalid coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, "invalidCoordinates", "testAccount1", null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - valid coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, CommandEconomy.INVENTORY_WEST, "testAccount1", null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - zeroed coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NONE, "testAccount1", null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - null username");
         errorFound |= testerSellAll(null, null, "testAccount4", null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - empty username");
         errorFound |= testerSellAll("", null, null, null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         TEST_OUTPUT.println("sellAll() - different username");
         errorFound |= testerSellAll("possibleID", null, "testAccount3", null,
                                     testWare1, testWare4, null, 5, 10, 0, 0, false);

         // /sellall [account_id]
         TEST_OUTPUT.println("sellAll() - request: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(null);
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sellAll() - request: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sellAll() - request: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("sellAll() - request: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "excessArgument", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("sellAll() - request: minimum args");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, null,
                                     testWare1, testWare3, null, 100, 10, 0, 0, true);

         TEST_OUTPUT.println("sellAll() - request: invalid account ID");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "invalidAccount", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);

         TEST_OUTPUT.println("sellAll() - request: valid account ID");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "testAccount1", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);

         TEST_OUTPUT.println("sellAll() - request: invalid coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, "invalidCoordinates", "testAccount1", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);

         TEST_OUTPUT.println("sellAll() - request: valid coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, CommandEconomy.INVENTORY_DOWN, "testAccount1", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);

         TEST_OUTPUT.println("sellAll() - request: zeroed coordinates");
         errorFound |= testerSellAll(InterfaceTerminal.playername, CommandEconomy.INVENTORY_NONE, "testAccount1", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);

         TEST_OUTPUT.println("sellAll() - request: null username");
         baosOut.reset(); // clear buffer holding console output
         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.inventory.put("test:material3",  10);

         InterfaceTerminal.serviceRequestSellAll(new String[]{null, "none", "testAccount1"});

         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sellAll() - request: empty username");
         baosOut.reset(); // clear buffer holding console output
         // give wares to the player
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.inventory.put("test:material3",  10);

         InterfaceTerminal.serviceRequestSellAll(new String[]{"", "none", "testAccount1"});

         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("sellAll() - request: different username");
         errorFound |= testerSellAll("possibleID", CommandEconomy.INVENTORY_NONE, "testAccount3", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);
         resetTestEnvironment();

         TEST_OUTPUT.println("sellAll() - request: selling no items");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSellAll(new String[]{InterfaceTerminal.playername, "none", "testAccount1"});

         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("sellAll() - request: admin account");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "$admin$", null,
                                     testWare1, testWare3, null, 10, 10, 0, 0, true);

         // prepare for next tests
         resetTestEnvironment();
         Config.priceFloor         = -1.0f;
         Config.priceFloorAdjusted =  2.0f;
         float quanCeilingFromEquilibrium1 = Config.quanExcessive[testWare1.getLevel()] - Config.quanEquilibrium[testWare1.getLevel()];
         float quanCeilingFromEquilibrium2 = Config.quanExcessive[testWareP1.getLevel()] - Config.quanEquilibrium[testWareP1.getLevel()];

         TEST_OUTPUT.println("sellAll() - negative prices, at -100% cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanExcessive[testWare1.getLevel()];
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanExcessive[testWareP1.getLevel()];
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         price2           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
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

         TEST_OUTPUT.println("sellAll() - negative prices, at -50% cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium1 * 0.75f) - 1;
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanEquilibrium[testWareP1.getLevel()] + (int) (quanCeilingFromEquilibrium2 * 0.75f) - 1;
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         price2           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
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

         TEST_OUTPUT.println("sellAll() - negative prices, at no cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium1 * 0.50f) - 1;
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanEquilibrium[testWareP1.getLevel()] + (int) (quanCeilingFromEquilibrium2 * 0.50f) - 1;
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         price2           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
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

         TEST_OUTPUT.println("sellAll() - negative prices, at 50% cost");
         quantityToTrade1 = 10;
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()] + (int) (quanCeilingFromEquilibrium1 * 0.25f) - 1;
         quantityToTrade2 = 10;
         quantityWare2    = Config.quanEquilibrium[testWareP1.getLevel()] + (int) (quanCeilingFromEquilibrium2 * 0.25f) - 1;
         testWare1.setQuantity(quantityWare1);
         testWareP1.setQuantity(quantityWare2);
         price1           = Marketplace.getPrice(PLAYER_ID, testWare1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         price2           = Marketplace.getPrice(PLAYER_ID, testWareP1, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
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

         TEST_OUTPUT.println("sellAll() - negative prices, at equilibrium");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "testAccount1", null,
                                     testWare1, testWareP1, null, 10, 10, 0, 0, true);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("sellAll() - fatal error: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests saveWares() and loadWares().
    *
    * @return whether saveWares() and loadWares() passed all test cases
    */
   private static boolean testUnitWareIO() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // add new wares to be tested
      // numerical IDs (such as 1.7.10's IDs)
      wares.put("17", new WareMaterial("17", "wood", 0.5f, 256, (byte) 0));
      wareAliasTranslations.put("wood", "17");
      StringBuilder json = new StringBuilder(wares.get("17").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("17", json);
      waresChangedSinceLastSave.add("17");
      wares.put("58", new WareCrafted(new String[]{"17"}, "58", "crafting_table", 256, 1, (byte) 0));
      wareAliasTranslations.put("crafting_table", "58");
      json = new StringBuilder(wares.get("58").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("58", json);

      // prepare to check wares
      Ware testWare; // holds ware currently being checked

      TEST_OUTPUT.println("testUnitWareIO() - saving to file");
      Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt"; // save wares to test file, don't overwrite any existing save

      // try to save to the test file
      try {
         Marketplace.saveWares();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   saveWares() should not throw any exception, but it did\n   was saving test wares");
         e.printStackTrace();
         return false;
      }

      TEST_OUTPUT.println("testUnitWareIO() - loading from file");
      // try to load the test file
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitWareIO() - loadWares() should not throw any exception, but it did\n   was loading test wares");
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
            TEST_OUTPUT.println("   test:material1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 0);
            testPriceBase.setFloat(testWare, 1.0f);
            testWare.setQuantity(256);
         }

         testWare = wares.get("test:material2");
         if (testWareFields(testWare, WareMaterial.class, "", (byte) 1, 27.6f, 5)) {
            TEST_OUTPUT.println("   test:material2 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 1);
            testPriceBase.setFloat(testWare, 27.6f);
            testWare.setQuantity(5);
         }

         testWare = wares.get("test:material3");
         if (testWareFields(testWare, WareMaterial.class, "mat3", (byte) 2, 4.0f, 64)) {
            TEST_OUTPUT.println("   test:material3 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 2);
            testPriceBase.setFloat(testWare, 4.0f);
            testWare.setQuantity(64);
         }
         if (!wareAliasTranslations.containsKey("mat3") ||
             !wareAliasTranslations.get("mat3").equals("test:material3")) {
            TEST_OUTPUT.println("   test:material3 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("minecraft:material4");
         if (testWareFields(testWare, WareMaterial.class, "material4", (byte) 3, 8.0f, 32)) {
            TEST_OUTPUT.println("   minecraft:material4 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("material4");
            testWare.setLevel((byte) 3);
            testPriceBase.setFloat(testWare, 8.0f);
            testWare.setQuantity(32);
         }
         if (!wareAliasTranslations.containsKey("material4") ||
             !wareAliasTranslations.get("material4").equals("minecraft:material4")) {
            TEST_OUTPUT.println("   minecraft:material4 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:untradeable1");
         if (testWareFields(testWare, WareUntradeable.class, "notrade1", (byte) 0, 16.0f, Integer.MAX_VALUE)) {
            TEST_OUTPUT.println("   test:untradeable1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("notrade1");
            testWare.setLevel((byte) 0);
            testPriceBase.setFloat(testWare, 16.0f);
            testWare.setQuantity(0);
         }
         if (!wareAliasTranslations.containsKey("notrade1") ||
             !wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
            TEST_OUTPUT.println("   test:untradeable1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:processed1");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 4, 1.1f, 16)) {
            TEST_OUTPUT.println("   test:processed1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 4);
            testPriceBase.setFloat(testWare, 1.1f);
            testWare.setQuantity(16);
         }

         testWare = wares.get("test:processed2");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 5, 14.3f, 8)) {
            TEST_OUTPUT.println("   test:processed2 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 5);
            testPriceBase.setFloat(testWare, 14.3f);
            testWare.setQuantity(8);
         }

         testWare = wares.get("test:processed3");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 3, 1.76f, 32)) {
            TEST_OUTPUT.println("   test:processed3 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 3);
            testPriceBase.setFloat(testWare, 1.76f);
            testWare.setQuantity(32);
         }

         testWare = wares.get("test:crafted1");
         if (testWareFields(testWare, WareCrafted.class, "craft1", (byte) 1, 19.2f, 128)) {
            TEST_OUTPUT.println("   test:crafted1 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("craft1");
            testWare.setLevel((byte) 1);
            testPriceBase.setFloat(testWare, 19.2f);
            testWare.setQuantity(128);
         }
         if (!wareAliasTranslations.containsKey("craft1") ||
             !wareAliasTranslations.get("craft1").equals("test:crafted1")) {
            TEST_OUTPUT.println("   test:crafted1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:crafted2");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 2, 24.24f, 64)) {
            TEST_OUTPUT.println("   test:crafted2 did not match expected values");
            errorFound = true;
            // set up ware appropriately for other tests
            testWare.setAlias("");
            testWare.setLevel((byte) 2);
            testPriceBase.setFloat(testWare, 24.24f);
            testWare.setQuantity(64);
         }

         testWare = wares.get("test:crafted3");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 3, 2.4f, 32)) {
            TEST_OUTPUT.println("   test:crafted3 did not match expected values");
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
            TEST_OUTPUT.println("   #testName does not exist, should be mapped to test:material2");
         } else {
            if (!wareAliasTranslations.get("#testName").equals("test:material2")) {
               errorFound = true;
               TEST_OUTPUT.println("   #testName's ware ID is " + wareAliasTranslations.get("#testName") + ", should be test:material2");
            }
         }
         if (!wareAliasTranslations.containsKey("testAlternateAlias")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAlternateAlias does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("testAlternateAlias").equals("test:material1")) {
               errorFound = true;
               TEST_OUTPUT.println("   testAlternateAlias's ware ID is " + wareAliasTranslations.get("testAlternateAlias") + ", should be test:material1");
            }
         }

         // numerical IDs
         testWare = wares.get("17");
         if (testWareFields(testWare, WareMaterial.class, "wood", (byte) 0, 0.5f, 256)) {
            TEST_OUTPUT.println("   ware #17 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("wood") ||
             !wareAliasTranslations.get("wood").equals("17")) {
            TEST_OUTPUT.println("   ware #17 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("58");
         if (testWareFields(testWare, WareCrafted.class, "crafting_table", (byte) 0, 0.6f, 256)) {
            TEST_OUTPUT.println("   ware #58 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("crafting_table") ||
             !wareAliasTranslations.get("crafting_table").equals("58")) {
            TEST_OUTPUT.println("   ware #58 did not have expected alias");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   fatal error while checking wares: " + e);
         return false;
      }

      TEST_OUTPUT.println("testUnitWareIO() - saving after removing a ware and creating new ones");

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

      json = new StringBuilder(wares.get("test:newWare1").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:newWare1", json);
      waresChangedSinceLastSave.add("test:newWare1");
      json = new StringBuilder(wares.get("test:newWare2").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:newWare2", json);
      json = new StringBuilder(wares.get("test:untradeable2").toJSON()).append('\n');
      waresLoadOrder.add(json);
      wareEntries.put("test:untradeable2", json);

      if (wares.size() != 15)
         TEST_OUTPUT.println("   warning: total number of wares is expected to be 15, but is " + wares.size());
      if (wareAliasTranslations.size() != 10)
         TEST_OUTPUT.println("   warning: total number of ware aliases is expected to be 10, but is " + wareAliasTranslations.size());

      // try to save to the test file
      try {
         Marketplace.saveWares();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   saveWares() should not throw any exception, but it did\n   was saving changed test wares");
         e.printStackTrace();
         return false;
      }

      TEST_OUTPUT.println("testUnitWareIO() - loading from file");
      // try to load the test file
      try {
         Marketplace.loadWares();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitWareIO() - loadWares() should not throw any exception, but it did\n   was loading changed test wares");
         e.printStackTrace();
         return false;
      }

      // check whether wares match known values they were set to
      try {
         testWare = wares.get("test:material1");
         if (testWareFields(testWare, WareMaterial.class, "", (byte) 0, 1.0f, 256)) {
            TEST_OUTPUT.println("   test:material1 did not match expected values");
            errorFound = true;
         }

         // check whether the deleted ware exists
         if (wares.containsKey("test:material2")) {
            errorFound = true;
            TEST_OUTPUT.println("   test:material2 was deleted, but still exists");
         }

         testWare = wares.get("test:material3");
         if (testWareFields(testWare, WareMaterial.class, "mat3", (byte) 2, 4.0f, 64)) {
            TEST_OUTPUT.println("   test:material3 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("mat3") ||
             !wareAliasTranslations.get("mat3").equals("test:material3")) {
            TEST_OUTPUT.println("   test:material3 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("minecraft:material4");
         if (testWareFields(testWare, WareMaterial.class, "material4", (byte) 3, 8.0f, 32)) {
            TEST_OUTPUT.println("   minecraft:material4 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("material4") ||
             !wareAliasTranslations.get("material4").equals("minecraft:material4")) {
            TEST_OUTPUT.println("   minecraft:material4 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:untradeable1");
         if (testWareFields(testWare, WareUntradeable.class, "notrade1", (byte) 0, 16.0f, Integer.MAX_VALUE)) {
            TEST_OUTPUT.println("   test:untradeable1 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("notrade1") ||
             !wareAliasTranslations.get("notrade1").equals("test:untradeable1")) {
            TEST_OUTPUT.println("   test:untradeable1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:processed1");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 4, 1.1f, 16)) {
            TEST_OUTPUT.println("   test:processed1 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:processed2");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 5, 14.3f, 8)) {
            TEST_OUTPUT.println("   test:processed2 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:processed3");
         if (testWareFields(testWare, WareProcessed.class, "", (byte) 3, 1.76f, 32)) {
            TEST_OUTPUT.println("   test:processed3 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:crafted1");
         if (testWareFields(testWare, WareCrafted.class, "craft1", (byte) 1, 19.2f, 128)) {
            TEST_OUTPUT.println("   test:crafted1 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("craft1") ||
             !wareAliasTranslations.get("craft1").equals("test:crafted1")) {
            TEST_OUTPUT.println("   test:crafted1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:crafted2");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 2, 24.24f, 64)) {
            TEST_OUTPUT.println("   test:crafted2 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:crafted3");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 3, 2.4f, 32)) {
            TEST_OUTPUT.println("   test:crafted3 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:newWare1");
         if (testWareFields(testWare, WareMaterial.class, "brandNewButUnexciting", (byte) 4, 100.0f, 10)) {
            TEST_OUTPUT.println("   test:newWare1 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("brandNewButUnexciting") ||
             !wareAliasTranslations.get("brandNewButUnexciting").equals("test:newWare1")) {
            TEST_OUTPUT.println("   test:newWare1 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("test:newWare2");
         if (testWareFields(testWare, WareCrafted.class, "", (byte) 3, 120.0f, 1)) {
            TEST_OUTPUT.println("   test:newWare2 did not match expected values");
            errorFound = true;
         }

         testWare = wares.get("test:untradeable2");
         if (testWareFields(testWare, WareUntradeable.class, "notrade2", (byte) 0, 9.0f, Integer.MAX_VALUE)) {
            TEST_OUTPUT.println("   test:untradeable2 did not match expected values");
            errorFound = true;
         }

         // ore names and alternate aliases
         if (!wareAliasTranslations.containsKey("#testName")) {
            errorFound = true;
            TEST_OUTPUT.println("   #testName does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("#testName").equals("test:material1")) {
               errorFound = true;
               TEST_OUTPUT.println("   #testName's ware ID is " + wareAliasTranslations.get("#testName") + ", should be test:material1");
            }
         }
         if (!wareAliasTranslations.containsKey("testAlternateAlias")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAlternateAlias does not exist, should be mapped to test:material1");
         } else {
            if (!wareAliasTranslations.get("testAlternateAlias").equals("test:material1")) {
               errorFound = true;
               TEST_OUTPUT.println("   testAlternateAlias's ware ID is " + wareAliasTranslations.get("testAlternateAlias") + ", should be test:material1");
            }
         }

         // numerical IDs
         testWare = wares.get("17");
         if (testWareFields(testWare, WareMaterial.class, "wood", (byte) 0, 0.5f, 256)) {
            TEST_OUTPUT.println("   ware #17 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("wood") ||
             !wareAliasTranslations.get("wood").equals("17")) {
            TEST_OUTPUT.println("   ware #17 did not have expected alias");
            errorFound = true;
         }

         testWare = wares.get("58");
         if (testWareFields(testWare, WareCrafted.class, "crafting_table", (byte) 0, 0.6f, 256)) {
            TEST_OUTPUT.println("   ware #58 did not match expected values");
            errorFound = true;
         }
         if (!wareAliasTranslations.containsKey("crafting_table") ||
             !wareAliasTranslations.get("crafting_table").equals("58")) {
            TEST_OUTPUT.println("   ware #58 did not have expected alias");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   fatal error while checking changed wares: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests Account.saveAccounts() and Account.loadAccounts().
    *
    * @return whether Account.saveAccounts() and Account.loadAccounts() passed all test cases
    */
   private static boolean testUnitAccountIO() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // make accounts start with some money
      Config.accountStartingMoney = 10.0f;

      // test handling missing file
      TEST_OUTPUT.println("testUnitAccountIO() - handling of missing file");
      Config.filenameAccounts = "no file here";
      baosOut.reset(); // clear buffer holding console output
      int numAccounts = accounts.size();
      try {
         Account.loadAccounts();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   Account.loadAccounts() should not throw any exception, but it did\n   was testing for handling missing file");
         e.printStackTrace();
         return false;
      }

      // check handling of missing file
      File fileAccounts = new File(Config.filenameAccounts);
      // check local file
      if (fileAccounts.exists()){
         errorFound = true;
         TEST_OUTPUT.println("   \"no file here\" file should not exist in local/world directory");
      }
      // check global file
      fileAccounts = new File("config" + File.separator + Config.filenameAccounts);
      if (fileAccounts.exists()){
         errorFound = true;
         TEST_OUTPUT.println("   \"no file here\" file should not exist in global/config directory");
      }
      if (numAccounts != accounts.size()) {
         TEST_OUTPUT.println("   unexpected number of accounts: " + accounts.size() + ", should be " + numAccounts);
         errorFound = true;
      }
      Config.filenameAccounts = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";

      TEST_OUTPUT.println("testUnitAccountIO() - saving to file");
      // try to save to the test file
      try {
         Account.saveAccounts();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   Account.saveAccounts() should not throw any exception, but it did\n   was saving test accounts");
         e.printStackTrace();
         return false;
      }

      TEST_OUTPUT.println("testUnitAccountIO() - loading from file");
      // try to load the test file
      try {
         Account.loadAccounts();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitAccountIO() - Account.loadAccounts() should not throw any exception, but it did\n   was loading test accounts");
         e.printStackTrace();
         return false;
      }

      // check whether accounts match known values they were set to
      try {
         // test properties
         testAccount1  = accounts.get("testAccount1");
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount1 had unexpected values");
         }
         // test permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount1 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         testAccount2  = accounts.get("testAccount2");
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount2 had unexpected values");
         }
         // test permissions
         if (testAccount2.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount2 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         testAccount3  = accounts.get("testAccount3");
         if (testAccountFields(testAccount3, 30.0f, "possibleID")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount3 had unexpected values");
         }
         // test permissions
         if (testAccount3.hasAccess(PLAYER_ID)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount3 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }

         Account testAccount4  = accounts.get("testAccount4");
         // test properties
         if (testAccount4.getMoney() != 6.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount4's money should be 6.0f, is " + testAccount4.getMoney());
         }
         // test permissions
         if (testAccount4.hasAccess(PLAYER_ID)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount4 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccount4.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount4 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         playerAccount  = accounts.get(InterfaceTerminal.playername);
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   playerAccount had unexpected values");
         }
         // test permissions
         if (playerAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   playerAccount allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         adminAccount  = accounts.get("$admin$");
         if (testAccountFields(adminAccount, Float.POSITIVE_INFINITY, "$admin$")) {
            errorFound = true;
            TEST_OUTPUT.println("   adminAccount had unexpected values");
         }
         // test permissions
         if (adminAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   playerAccount allows access to an arbitrary player ID when it shouldn't");
         }

         if (accounts.size() != 6)
            TEST_OUTPUT.println("   warning: total number of accounts is expected to be 6, but is " + accounts.size());
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   fatal error while checking accounts: " + e);
         return false;
      }

      TEST_OUTPUT.println("testUnitAccountIO() - saving and loading after removing an account and creating new ones");

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
         TEST_OUTPUT.println("   warning: total number of accounts is expected to be 7, but is " + accounts.size());

      // try to save to the test file
      try {
         Account.saveAccounts();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   Account.saveAccounts() should not throw any exception, but it did\n   was saving changed test accounts");
         e.printStackTrace();
         return false;
      }

      TEST_OUTPUT.println("testUnitAccountIO() - loading from file");
      // try to load the test file
      try {
         Account.loadAccounts();
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitAccountIO() - Account.loadAccounts() should not throw any exception, but it did\n   was loading changed test accounts");
         e.printStackTrace();
         return false;
      }

      // check whether accounts match known values they were set to
      try {
         // test properties
         testAccount1  = accounts.get("testAccount1");
         if (testAccountFields(testAccount1, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount1 had unexpected values");
         }
         // test permissions
         if (testAccount1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount1 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         testAccount2  = accounts.get("testAccount2");
         if (testAccountFields(testAccount2, 20.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount2 had unexpected values");
            // set up accounts appropriately for other tests
            testAccount2.setMoney(20.0f);
            testAccount2.grantAccess(PLAYER_ID, PLAYER_ID, InterfaceTerminal.playername);
         }
         // test permissions
         if (testAccount2.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount2 allows access to an arbitrary player ID when it shouldn't");
         }

         // check whether the deleted account exists
         if (accounts.containsKey("testAccount3")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount3 was deleted, but still exists");
         }

         // test properties
         testAccount4 = accounts.get("testAccount4");
         if (testAccount4.getMoney() != 6.0f) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount4's money should be 6.0f, is " + testAccount4.getMoney());
         }
         // test permissions
         if (testAccount4.hasAccess(PLAYER_ID)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount4 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccount4.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccount4 allows access to an arbitrary player ID when it shouldn't");
         }

         // test properties
         playerAccount = accounts.get(InterfaceTerminal.playername);
         if (testAccountFields(playerAccount, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            TEST_OUTPUT.println("   playerAccount had unexpected values");
         }
         // test permissions
         if (playerAccount.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   playerAccount allows access to an arbitrary player ID when it shouldn't");
         }

         Account testAccountNew1 = accounts.get("arbitraryAccountID");
         // test properties
         if (testAccountFields(testAccountNew1, 10.0f, "arbitraryPlayerID")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew1 had unexpected values");
         }
         // test permissions
         if (testAccountNew1.hasAccess(PLAYER_ID)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew1 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccountNew1.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew1 allows access to an arbitrary player ID when it shouldn't");
         }

         testAccountNew2 = accounts.get("arbitraryID");
         // test properties
         if (testAccountFields(testAccountNew2, 42.0f, "arbitraryID")) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew2 had unexpected values");
         }
         // test permissions
         if (testAccountNew2.hasAccess(PLAYER_ID)) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew2 allows access to the player " + InterfaceTerminal.playername + " when it shouldn't");
         }
         if (testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("possibleID"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew2 allows access to an arbitrary player ID when it shouldn't");
         }
         if (!testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID1")) ||
             !testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID2")) ||
             !testAccountNew2.hasAccess(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID3"))) {
            errorFound = true;
            TEST_OUTPUT.println("   testAccountNew2 does not allow shared access when it should");
         }

         // check account creation counts
         if (Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID")) != 1) {
            errorFound = true;
            TEST_OUTPUT.println("   arbitraryPlayerID's account creation count is " + Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryPlayerID")) + ", should be 1");
         }
         if (Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryID")) != 0) {
            errorFound = true;
            TEST_OUTPUT.println("   arbitraryID's account creation count is " + Account.getNumAccountsCreatedByUser(InterfaceTerminal.getPlayerIDStatic("arbitraryID")) + ", should be 0");
         }

         if (accounts.size() != 7)
            TEST_OUTPUT.println("   warning: total number of accounts is expected to be 7, but is " + accounts.size());
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   fatal error while checking changed accounts: " + e);
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests various serviceRequest() functions.
    *
    * @return whether all tested serviceRequest() functions passed all of their test cases
    */
   private static boolean testUnitInterfaceServiceRequests() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         // money [account_id]
         TEST_OUTPUT.println("serviceRequests() - money: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(null);
         if (!baosOut.toString().equals("Your account: $30.00" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - money: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{});
         if (!baosOut.toString().equals("Your account: $30.00" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - money: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{InterfaceTerminal.playername, "testAccount2", "excessArgument"});
         if (!baosOut.toString().startsWith("testAccount2: $")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: blank account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found: invalidAccount")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: minimum args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{});
         if (!baosOut.toString().startsWith("Your account: $")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: valid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"testAccount2"});
         if (!baosOut.toString().startsWith("testAccount2: $")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: null username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{null, "testAccount2"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: empty username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"", "testAccount2"});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - money: different username");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestMoney(new String[]{"possibleID", "testAccount3"});
         if (!baosOut.toString().startsWith("(for possibleID) testAccount3: $")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // grantAccess player_id account_id
         TEST_OUTPUT.println("serviceRequests() - grantAccess: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(null);
         if (!baosOut.toString().equals("/grantAccess <player_name> <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - grantAccess: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{});
         if (!baosOut.toString().equals("/grantAccess <player_name> <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - grantAccess: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"", ""});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - grantAccess: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - grantAccess: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID", "testAccount1", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - grantAccess: invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID", "invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - grantAccess: valid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGrantAccess(new String[]{"possibleID", "testAccount1"});
         if (!baosOut.toString().startsWith("possibleID may now access testAccount1")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // revokeAccess player_id account_id
         TEST_OUTPUT.println("serviceRequests() - revokeAccess: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(null);
         if (!baosOut.toString().equals("/revokeAccess <player_name> <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - revokeAccess: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{});
         if (!baosOut.toString().equals("/revokeAccess <player_name> <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - revokeAccess: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"", ""});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - revokeAccess: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - revokeAccess: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID", "testAccount1", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - revokeAccess: invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID", "invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - revokeAccess: valid account ID");
         testAccount1.grantAccess(null, InterfaceTerminal.getPlayerIDStatic("possibleID"), null);
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestRevokeAccess(new String[]{"possibleID", "testAccount1"});
         if (!baosOut.toString().startsWith("possibleID may no longer access testAccount1")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         // save
         // don't overwrite user saves
         Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt";
         Config.filenameAccounts = "config" + File.separator + "CommandEconomy" + File.separator + "testAccounts.txt";

         TEST_OUTPUT.println("serviceRequests() - save: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(null);
         if (!baosOut.toString().equals("Saved the economy" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - save: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{});
         if (!baosOut.toString().equals("Saved the economy" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - save: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{""});
         if (!baosOut.toString().equals("Saved the economy" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - save: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{"excessArgument"});
         if (!baosOut.toString().equals("Saved the economy" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - save: expected usage");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSave(new String[]{});
         if (!baosOut.toString().equals("Saved the economy" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // reload (config || wares || accounts || all)
         TEST_OUTPUT.println("serviceRequests() - reload: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(null);
         if (!baosOut.toString().equals("/commandeconomy reload (config | wares | accounts | all)" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{});
         if (!baosOut.toString().equals("/commandeconomy reload (config | wares | accounts | all)" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{""});
         if (!baosOut.toString().startsWith("error - must provide instructions for reload")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{});
         if (!baosOut.toString().equals("/commandeconomy reload (config | wares | accounts | all)" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"wares", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: invalid arg");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"invalidArgument"});
         if (!baosOut.toString().startsWith("error - invalid argument")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: config");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         if (!baosOut.toString().equals("Reloaded config." + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: wares");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"wares"});
         if (!baosOut.toString().equals("Reloaded wares." + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: accounts");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"accounts"});
         if (!baosOut.toString().equals("Reloaded accounts." + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - reload: total reload");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestReload(new String[]{"all"});
         if (!baosOut.toString().equals("Reloaded config, wares, and accounts." + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // for paranoia's sake, reload the testing environment
         // to avoid any possibility of interfering with other tests
         resetTestEnvironment();

         // add quantity [account_id]
         TEST_OUTPUT.println("serviceRequests() - add: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(null);
         if (!baosOut.toString().equals("/add <quantity> [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - add: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{});
         if (!baosOut.toString().equals("/add <quantity> [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - add: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - add: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{});
         if (!baosOut.toString().equals("/add <quantity> [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - add: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0", "testAccount2", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - add: invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{"invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - add: invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0", "invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - add: minimum args");
         playerAccount.setMoney(30.0f);
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0"});
         if (testAccountFields(playerAccount, 40.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - add: valid account ID");
         testAccount2.setMoney(20.0f);
         InterfaceTerminal.serviceRequestAdd(new String[]{"10.0", "testAccount2"});
         if (testAccountFields(testAccount2, 30.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         // set quantity [account_id]
         TEST_OUTPUT.println("serviceRequests() - set: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(null);
         if (!baosOut.toString().equals("/set <quantity> [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - set: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{});
         if (!baosOut.toString().equals("/set <quantity> [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - set: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - set: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{});
         if (!baosOut.toString().equals("/set <quantity> [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - set: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0", "testAccount2", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - set: invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{"invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - set: invalid account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0", "invalidAccount"});
         if (!baosOut.toString().startsWith("error - account not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - set: minimum args");
         playerAccount.setMoney(30.0f);
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0"});
         if (testAccountFields(playerAccount, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - set: valid account ID");
         testAccount2.setMoney(20.0f);
         InterfaceTerminal.serviceRequestSet(new String[]{"10.0", "testAccount2"});
         if (testAccountFields(testAccount2, 10.0f, InterfaceTerminal.playername)) {
            errorFound = true;
            resetTestEnvironment();
         }

         // printMarket
         // don't worry about changing the file written to since it's data is meant to be temporary
         TEST_OUTPUT.println("serviceRequests() - printMarket: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(null);
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - printMarket: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - printMarket: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{""});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - printMarket: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{"excessArgument"});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - printMarket: expected usage");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestPrintMarket(new String[]{});
         if (!baosOut.toString().isEmpty()) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // inventory
         TEST_OUTPUT.println("serviceRequests() - inventory: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(null);
         if (!baosOut.toString().startsWith("Your inventory: ")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{});
         if (!baosOut.toString().startsWith("Your inventory: ")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{""});
         if (!baosOut.toString().startsWith("Your inventory: ")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{"", "excessArgument"});
         if (!baosOut.toString().startsWith("Your inventory: ")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: expected usage");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestInventory(new String[]{});
         if (!baosOut.toString().startsWith("Your inventory: ")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: invalid coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"invalidCoordinates"});
         if (!baosOut.toString().startsWith("error - invalid inventory direction")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: zeroed coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 15);
         InterfaceTerminal.serviceRequestInventory(new String[]{"none"});
         if (!baosOut.toString().startsWith("Your inventory: ") ||
             !baosOut.toString().contains(" 1. test:material1: 15")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: northward coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryNorth.put("test:material3", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"north"});
         if (!baosOut.toString().startsWith("Northward inventory: ") ||
             !baosOut.toString().contains(" 1. test:material3: 10")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: eastward coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryEast.put("minecraft:material4", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"east"});
         if (!baosOut.toString().startsWith("Eastward inventory: ") ||
             !baosOut.toString().contains(" 1. minecraft:material4: 10")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: westward coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryWest.put("test:material1", 5);
         InterfaceTerminal.serviceRequestInventory(new String[]{"west"});
         if (!baosOut.toString().startsWith("Westward inventory: ") ||
             !baosOut.toString().contains(" 1. test:material1: 5")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: southward coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventorySouth.put("test:material2", 5);
         InterfaceTerminal.serviceRequestInventory(new String[]{"south"});
         if (!baosOut.toString().startsWith("Southward inventory: ") ||
             !baosOut.toString().contains(" 1. test:material2: 5")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: upward coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryUp.put("test:material3", 5);
         InterfaceTerminal.serviceRequestInventory(new String[]{"up"});
         if (!baosOut.toString().startsWith("Upward inventory: ") ||
             !baosOut.toString().contains(" 1. test:material3: 5")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - inventory: downward coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventoryDown.put("test:material2", 10);
         InterfaceTerminal.serviceRequestInventory(new String[]{"down"});
         if (!baosOut.toString().startsWith("Downward inventory: ") ||
             !baosOut.toString().contains(" 1. test:material2: 10")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         // give <ware_id> [quantity] [inventory_direction]
         TEST_OUTPUT.println("serviceRequests() - give: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(null);
         if (!baosOut.toString().equals("/give <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{});
         if (!baosOut.toString().equals("/give <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{});
         if (!baosOut.toString().equals("/give <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "10", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - give: invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity: wrong type")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - give: zero quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "0"});
         if (!baosOut.toString().startsWith("error - invalid quantity: must be greater than zero")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - give: negative quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "-1"});
         if (!baosOut.toString().startsWith("error - invalid quantity: must be greater than zero")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - give: with insufficient inventory space");
         baosOut.reset(); // clear buffer holding console output
         int inventorySpaceOrig = InterfaceTerminal.inventorySpace;
         InterfaceTerminal.inventorySpace = 0; // maximum inventory space is no inventory
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "100"});
         InterfaceTerminal.inventorySpace = inventorySpaceOrig;
         if (!baosOut.toString().startsWith("You don't have enough inventory space")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - give: invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:invalidWareID", "10"});
         if (!baosOut.toString().equals("You have been given 10 test:invalidWareID" + System.lineSeparator() + "warning - test:invalidWareID is not usable within the marketplace" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }
         if (!InterfaceTerminal.inventory.containsKey("test:invalidWareID")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:invalidWareID") != 10) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:invalidWareID") + " test:invalidWareID, should contain 10");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: invalid ware alias");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"invalidWareAlias", "5"});
         if (!baosOut.toString().equals("You have been given 5 invalidWareAlias" + System.lineSeparator() + "warning - invalidWareAlias is not usable within the marketplace" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (!InterfaceTerminal.inventory.containsKey("invalidWareAlias")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("invalidWareAlias") != 5) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("invalidWareAlias") + " invalidWareAlias, should contain 5");
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("serviceRequests() - give: valid ware ID");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1"});
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 1) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: valid ware alias");
         InterfaceTerminal.serviceRequestGive(new String[]{"mat3"});
         if (!InterfaceTerminal.inventory.containsKey("test:material3")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material3") != 1) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material3") + " test:material3, should contain 1");
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("serviceRequests() - give: invalid coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "invalidCoordinates"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: valid coordinates");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "down"});
         if (!InterfaceTerminal.inventoryDown.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryDown.get("test:material1") != 1) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventoryDown.get("test:material1") + " test:material1, should contain 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: zeroed coordinates");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "none"});
         if (!InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:material1") != 1) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 1");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - give: quantity and coordinates");
         InterfaceTerminal.serviceRequestGive(new String[]{"test:material1", "10", "up"});
         if (!InterfaceTerminal.inventoryUp.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryUp.get("test:material1") != 10) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventoryUp.get("test:material1") + " test:material1, should contain 10");
            errorFound = true;
         }

         // to avoid interfering with other tests,
         // reset the testing environment
         resetTestEnvironment();

         // take <ware_id> [quantity] [inventory_direction]
         TEST_OUTPUT.println("serviceRequests() - take: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(null);
         if (!baosOut.toString().equals("/take <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{});
         if (!baosOut.toString().equals("/take <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{});
         if (!baosOut.toString().equals("/take <ware_id> [quantity] [inventory_direction]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "10", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - take: invalid quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity: wrong type")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - take: zero quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "0"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory contains ware when it should not");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: negative quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "-1"});
         if (!baosOut.toString().startsWith("error - invalid quantity: must be greater than zero")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - take: excessive quantity");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "100"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory contains ware when it should not");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: unowned ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestTake(new String[]{"unownedWareID"});
         if (!baosOut.toString().startsWith("error - ware not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("serviceRequests() - take: invalid ware ID");
         InterfaceTerminal.inventory.put("test:invalidWareID", 100); // give the player some non-marketplace-compatible wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:invalidWareID", "10"});
         if (!InterfaceTerminal.inventory.containsKey("test:invalidWareID")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("test:invalidWareID") != 90) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:invalidWareID") + " test:invalidWareID, should contain 90");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: invalid ware alias");
         InterfaceTerminal.inventory.put("invalidWareAlias", 10); // give the player some non-marketplace-compatible wares
         InterfaceTerminal.serviceRequestTake(new String[]{"invalidWareAlias", "5"});
         if (!InterfaceTerminal.inventory.containsKey("invalidWareAlias")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventory.get("invalidWareAlias") != 5) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("invalidWareAlias") + " invalidWareAlias, should contain 5");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: valid ware ID");
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory contains ware when it should not");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: valid ware alias");
         InterfaceTerminal.inventory.put("test:material3", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"mat3"});
         if (InterfaceTerminal.inventory.containsKey("test:material3")) {
            TEST_OUTPUT.println("   inventory contains ware when it should not");
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("serviceRequests() - take: invalid coordinates");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.inventory.put("test:material1", 10); // give the player some wares
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "invalidCoordinates"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: valid coordinates");
         InterfaceTerminal.inventoryDown.put("test:material1", 10);
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "down"});
         if (InterfaceTerminal.inventoryDown.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventoryDown.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: zeroed coordinates");
         InterfaceTerminal.inventory.put("test:material1", 10);
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "none"});
         if (InterfaceTerminal.inventory.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventory.get("test:material1") + " test:material1, should contain 0");
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - take: quantity and coordinates");
         InterfaceTerminal.inventoryUp.put("test:material1", 10);
         InterfaceTerminal.serviceRequestTake(new String[]{"test:material1", "5", "up"});
         if (!InterfaceTerminal.inventoryUp.containsKey("test:material1")) {
            TEST_OUTPUT.println("   inventory does not contain expected ware");
            errorFound = true;
         } else if (InterfaceTerminal.inventoryUp.get("test:material1") != 5) {
            TEST_OUTPUT.println("   inventory contains " + InterfaceTerminal.inventoryUp.get("test:material1") + " test:material1, should contain 5");
            errorFound = true;
         }

         // changeName playername
         TEST_OUTPUT.println("serviceRequests() - changeName: null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(null);
         if (!baosOut.toString().equals("/changeName <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - changeName: empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{});
         if (!baosOut.toString().equals("/changeName <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - changeName: blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{""});
         if (!baosOut.toString().startsWith("error - must provide name or ID")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - changeName: too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{});
         if (!baosOut.toString().equals("/changeName <player_name>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - changeName: too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{"possibleID", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("serviceRequests() - changeName: valid args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeName(new String[]{"John Doe"});
         if (!baosOut.toString().equals("Your name is now John Doe.\nYour old name was John_Doe." + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         InterfaceTerminal.playername = "John_Doe";
      }
      catch (Exception e) {
         TEST_OUTPUT.println("serviceRequests() - fatal error: " + e);
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
   private static boolean testUnitCreate() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         // create account_id
         TEST_OUTPUT.println("create() - null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(null);
         if (!baosOut.toString().equals("/create <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("create() - empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{});
         if (!baosOut.toString().equals("/create <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("create() - blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - must provide account ID")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("create() - too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{});
         if (!baosOut.toString().equals("/create <account_id>" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("create() - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{"possibleAccount", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("create() - existing account ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestCreate(new String[]{"testAccount1"});
         if (!baosOut.toString().startsWith("error - account already exists")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("create() - valid account ID");
         InterfaceTerminal.serviceRequestCreate(new String[]{"possibleAccount"});
         if (!accounts.containsKey("possibleAccount")) {
            TEST_OUTPUT.println("   failed to create account");
            errorFound = true;
            resetTestEnvironment();
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("create() - fatal error: " + e);
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
   private static boolean testUnitChangeStock() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // /changeStock <ware_ID> (quantity | equilibrium | overstocked | understocked)
      try {
         InterfaceTerminal.ops.add(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername));

         TEST_OUTPUT.println("changeStock() - null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(null);
         if (!baosOut.toString().startsWith("/changeStock <ware_id> (<quantity> | equilibrium | overstocked | understocked)")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{});
         if (!baosOut.toString().startsWith("/changeStock <ware_id> (<quantity> | equilibrium | overstocked | understocked)")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - too few args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{""});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"", "", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"invalidWare", "0"});
         if (!baosOut.toString().startsWith("error - ware not found")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - invalid quantity");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material1", "invalidQuantity"});
         if (!baosOut.toString().startsWith("error - invalid quantity")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("changeStock() - zero quantity");
         baosOut.reset(); // clear buffer holding console output
         int quantity = testWare1.getQuantity();
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material1", "0"});
         if (!baosOut.toString().startsWith("test:material1's stock is now " + quantity)) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (quantity != testWare1.getQuantity()) {
            TEST_OUTPUT.println("   unexpected ware stock: " + testWare1.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("changeStock() - negative quantity");
         baosOut.reset(); // clear buffer holding console output
         quantity = testWare3.getQuantity() - 10;
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material3", "-10"});
         if (!baosOut.toString().startsWith("test:material3's stock is now " + quantity)) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (quantity != testWare3.getQuantity()) {
            TEST_OUTPUT.println("   unexpected ware stock: " + testWare3.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("changeStock() - positive quantity");
         baosOut.reset(); // clear buffer holding console output
         quantity = testWareP1.getQuantity() + 100;
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:processed1", "100"});
         if (!baosOut.toString().startsWith("test:processed1's stock is now " + quantity)) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (quantity != testWareP1.getQuantity()) {
            TEST_OUTPUT.println("   unexpected ware stock: " + testWareP1.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("changeStock() - equilibrium");
         baosOut.reset(); // clear buffer holding console output
         quantity = Config.quanEquilibrium[testWare4.getLevel()];
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"minecraft:material4", "equilibrium"});
         if (!baosOut.toString().startsWith("minecraft:material4's stock is now " + quantity)) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (quantity != testWare4.getQuantity()) {
            TEST_OUTPUT.println("   unexpected ware stock: " + testWare4.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("changeStock() - overstocked");
         baosOut.reset(); // clear buffer holding console output
         quantity = Config.quanExcessive[testWare1.getLevel()];
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material1", "overstocked"});
         if (!baosOut.toString().startsWith("test:material1's stock is now " + quantity)) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (quantity != testWare1.getQuantity()) {
            TEST_OUTPUT.println("   unexpected ware stock: " + testWare1.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("changeStock() - understocked");
         baosOut.reset(); // clear buffer holding console output
         quantity = Config.quanDeficient[testWare3.getLevel()];
         InterfaceTerminal.serviceRequestChangeStock(new String[]{"test:material3", "understocked"});
         if (!baosOut.toString().startsWith("test:material3's stock is now " + quantity)) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (quantity != testWare3.getQuantity()) {
            TEST_OUTPUT.println("   unexpected ware stock: " + testWare3.getQuantity() + ", should be " + quantity);
            errorFound = true;
            resetTestEnvironment();
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("changeStock() - fatal error: " + e);
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
   @SuppressWarnings("unchecked") // for grabbing private variables
   private static boolean testUnitAI() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.filenameNoPathAIProfessions = "testAIProfessions.json";
      Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";
      File       fileAIProfessions       = new File(Config.filenameAIProfessions);
      FileWriter fileWriter;

      Config.enableAI         = true;
      Config.aiTradeFrequency = 999999999;
      Config.aiRandomness     = 0.0f;

      // prepare to set up test AI
      AI testAI1;
      AI testAI2;
      AI testAI3;
      String[] purchasablesIDs;           // IDs for wares the AI may buy
      String[] sellablesIDs;              // IDs for wares the AI may sell
      HashMap<String, Float> preferences; // biases the AI carries toward wares and trade decisions
      HashMap<Ware, Integer> tradesPending = new HashMap<Ware, Integer>(); // contains AI trade decisions before finalization

      // prepare to grab internal variables
      Field     fTimer;           // used to check whether feature is running
      Timer     timerAIHandler;
      Field     fTimerTask;
      AIHandler aiHandler;
      Field     fTradeFrequency;  // how often AI make trading decisions
      long      tradeFrequency;
      Field     fProfessions;     // used to check loaded AI professions
      HashMap<String, AI> professions;
      Field     fActiveAI;        // used to check AI currently running
      AI[]      activeAI;
      Field     fPurchasablesIDs; // used to check loading wares from file
      Field     fSellablesIDs;    // used to check loading wares from file
      Field     fPurchasables;    // wares the AI may buy
      Object[]  purchasables;

      // track changes to variables
      AI  ai;               // AI currently being checked
      int quantityToTrade1; // how many units AI should trade at once for the test case's first ware
      int quantityToTrade2; // how many units AI should trade at once for the test case's second ware
      int quantityToTrade3; // how many units AI should trade at once for the test case's third ware
      int quantityWare1;    // ware's quantity available for sale for the test case's first ware
      int quantityWare2;    // ware's quantity available for sale for the test case's second ware
      int quantityWare3;    // ware's quantity available for sale for the test case's third ware

      // ensure professions file doesn't affect next test run
      if (fileAIProfessions.exists())
         fileAIProfessions.delete();

      try {
         // set up test AI
         // testAI1: simple as possible
         // buys testWare1
         purchasablesIDs = new String[]{"test:material1"};
         testAI1         = new AI("testAI1", purchasablesIDs, null, null);

         // testAI2: simple + buys and sells
         // buys testWare2
         // sells testWareC1
         purchasablesIDs = new String[]{"test:material2"};
         sellablesIDs    = new String[]{"test:crafted1"};
         testAI2         = new AI("testAI2", purchasablesIDs, sellablesIDs, null);

         // testAI3: has preferences
         // buys testWare1, testWare3
         // sells testWareC2
         // prefers testWare3 by +10%
         purchasablesIDs = new String[]{"test:material1", "test:material3"};
         sellablesIDs    = new String[]{"test:crafted2"};
         preferences     = new HashMap<String, Float>(1, 1.0f);
         preferences.put("test:crafted2", 1.10f);
         testAI3         = new AI("testAI3", purchasablesIDs, sellablesIDs, preferences);

         // grab references to AI handler attributes
         fProfessions    = AIHandler.class.getDeclaredField("professions");
         fActiveAI       = AIHandler.class.getDeclaredField("activeAI");
         fTimer          = AIHandler.class.getDeclaredField("timerAIHandler");
         fTimerTask      = AIHandler.class.getDeclaredField("timerTaskAIHandler");
         fTradeFrequency = AIHandler.class.getDeclaredField("oldFrequency");
         fProfessions.setAccessible(true);
         fActiveAI.setAccessible(true);
         fTimer.setAccessible(true);
         fTimerTask.setAccessible(true);
         fTradeFrequency.setAccessible(true);

         // grab references to AI attributes
         fPurchasablesIDs = AI.class.getDeclaredField("purchasablesIDs");
         fSellablesIDs    = AI.class.getDeclaredField("sellablesIDs");
         fPurchasables    = AI.class.getDeclaredField("purchasables");
         fPurchasablesIDs.setAccessible(true);
         fSellablesIDs.setAccessible(true);
         fPurchasables.setAccessible(true);

         // ensure config file supports AI
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "enableAI              = true\n" +
            "aiTradeFrequency      = 999999999\n" +
            "aiRandomness          = 0.0\n" +
            "disableAutoSaving     = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // set up AI thread
         AIHandler.startOrReconfig();                  // create thread
         timerAIHandler = (Timer) fTimer.get(null);     // grab references to thread
         aiHandler      = (AIHandler) fTimerTask.get(null);

         TEST_OUTPUT.println("AI - missing file");
         // initialize AI
         try {
            aiHandler.run(); // process command to load
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   startOrReconfig() should not throw any exception, but it did while loading missing AI professions file");
            e.printStackTrace();
            errorFound = true;
         }

         // check loaded AI information
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (professions != null) {
            TEST_OUTPUT.println("   professions were loaded when they shouldn't have been");
            professions = null;
            errorFound = true;
         }
         activeAI = (AI[]) fActiveAI.get(null);
         if (activeAI != null) {
            TEST_OUTPUT.println("   activeAI were loaded when they shouldn't have been");
            activeAI = null;
            errorFound = true;
         }


         TEST_OUTPUT.println("AI - empty file");
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
            TEST_OUTPUT.println("   unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // try to load the test file
         try {
            AIHandler.load();
            aiHandler.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did while loading test professions file");
            e.printStackTrace();
            errorFound = true;
         }

         // check loaded AI information
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (professions != null) {
            TEST_OUTPUT.println("   professions were loaded when they shouldn't have been");
            professions = null;
            errorFound = true;
         }
         activeAI = (AI[]) fActiveAI.get(null);
         if (activeAI != null) {
            TEST_OUTPUT.println("   activeAI were loaded when they shouldn't have been");
            activeAI = null;
            errorFound = true;
         }


         TEST_OUTPUT.println("AI - loading valid and invalid professions");
         Config.activeAI = new String[]{"validAIProfession", "invalidAIProfession"}; // prevents feature from shutting down due to lack of work

         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               "{\n" +
               "\"validAIProfession\": {\n" +
               "\"purchasablesIDs\": [\"test:material1\",\"test:crafted1\"],\n" +
               "\"sellablesIDs\": [\"test:material1\"],\n" +
               "\"preferences\": { \"test:crafted1\": 0.15 }\n" +
               "},\n" +
               "\"invalidAIProfession\": {\n" +
               "\"purchasablesIDs\": [\"test:material1\",\"test:crafted1\"],\n" +
               "\"sellablesIDs\": [\"test:material1\"],\n" +
               "\"preferences\": { \"test:crafted2\": 0.15 }\n" +
               "}\n" +
               "}\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // prepare to check for printed error messages
         baosErr.reset();

         // try to load the test file
         try {
            AIHandler.load();
            aiHandler.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did while loading test professions file");
            e.printStackTrace();
            errorFound = true;
         }

         // stop checking for printed error messages
         streamErrorsProgram.flush();

         // check loaded AI information
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (professions == null) {
            TEST_OUTPUT.println("   AI professions should have loaded, but professions is null");
            errorFound = true;
         } else if (professions.size() != 2) {
            TEST_OUTPUT.println("   AI professions loaded: " + professions.size() + ", should be 2");
            errorFound = true;
         }

         // check error messages
         if (!baosErr.toString().startsWith(CommandEconomy.ERROR_AI_PREFS_MISMATCH_PRO) ||
            !baosErr.toString().contains("test:crafted2")) {
            TEST_OUTPUT.println("   unexpected error output: " + baosErr.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - loading professions with invalid wares");
         Config.activeAI = new String[]{"hasPreferences", "hasNoPreferences"}; // prevents feature from shutting down due to lack of work

         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               "{\n" +
               "\"hasPreferences\": {\n" +
               "\"purchasablesIDs\": [\"test:material1\",\"invalidWareAlias\"],\n" +
               "\"sellablesIDs\": [\"test:material1\"],\n" +
               "\"preferences\": { \"invalidWareAlias\": 0.15 }\n" +
               "},\n" +
               "\"hasNoPreferences\": {\n" +
               "\"purchasablesIDs\": [\"test:material1\",\"test:invalidWareID\"],\n" +
               "\"sellablesIDs\": [\"test:material1\"]\n" +
               "}\n" +
               "}\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // try to load the test file
         try {
            AIHandler.load();
            aiHandler.run();
            aiHandler.stop = true; // prevent any unintentional trading
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did while loading test professions file");
            e.printStackTrace();
            errorFound = true;
         }

         // check loaded AI information
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (professions == null) {
            TEST_OUTPUT.println("   AI professions should have loaded, but professions is null");
            errorFound = true;
         } else {
            // check total size
            if (professions.size() != 2) {
               TEST_OUTPUT.println("   AI professions loaded: " + professions.size() + ", should be 2");
               errorFound = true;
            }

            // check AI profession with preferences
            ai = professions.get("hasPreferences");
            if (ai != null) {
               // grab data
               purchasablesIDs = (String[]) fPurchasablesIDs.get(ai);
               purchasables    = (Object[]) fPurchasables.get(ai);

               // check saved ware IDs
               if (purchasablesIDs == null) {
                  TEST_OUTPUT.println("   AI profession with preferences - purchasablesIDs were not loaded");
                  errorFound = true;
               }
               else {
                  if (purchasablesIDs.length > 0) {
                     if (!purchasablesIDs[0].equals("test:material1")) {
                        TEST_OUTPUT.println("   AI profession with preferences - unexpected purchasablesIDs[0]: " + purchasablesIDs[0] + ", should be test:material1");
                        errorFound = true;
                     }

                     if (purchasablesIDs.length > 1) {
                        if (!purchasablesIDs[1].equals("invalidWareAlias")) {
                           TEST_OUTPUT.println("   AI profession with preferences - unexpected purchasablesIDs[1]: " + purchasablesIDs[1] + ", should be invalidWareAlias");
                           errorFound = true;
                        }
                     }
                     else {
                        TEST_OUTPUT.println("   AI profession with preferences - purchasablesIDs length is 1, should be 2");
                        errorFound = true;
                     }
                  }
                  else {
                     TEST_OUTPUT.println("   AI profession with preferences - purchasablesIDs length is 0, should be 2");
                     errorFound = true;
                  }
               }

               // check loaded wares
               if (purchasables == null) {
                  TEST_OUTPUT.println("   AI profession with preferences - purchasables were not loaded");
                  errorFound = true;
               }
               else {
                  if (purchasables.length == 1) {
                     if (!purchasablesIDs[0].equals(testWare1.getWareID())) {
                        TEST_OUTPUT.println("   AI profession with preferences - unexpected purchasables[0]: " + purchasablesIDs[0] + ", should be test:material1");
                        errorFound = true;
                     }
                  }
                  else {
                     TEST_OUTPUT.println("   AI profession with preferences - purchasables length is " + purchasables.length + ", should be 1");
                     errorFound = true;
                  }
               }
            }
         }


         TEST_OUTPUT.println("AI - purchases");
         quantityToTrade1 = (int) (Config.quanEquilibrium[testWare1.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare1);

         testAI1.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity (test #1): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }


         // testAI2 buys testWare2 and sells testWareC1
         // Since both are at equilibrium, testAI2 should buy before selling.
         quantityToTrade1 = (int) (Config.quanEquilibrium[testWare2.getLevel()] * Config.aiTradeQuantityPercent);
         quantityToTrade2 = 0;
         quantityWare1    = Config.quanEquilibrium[testWare2.getLevel()];
         quantityWare2    = Config.quanEquilibrium[testWareC1.getLevel()];
         testWare2.setQuantity(quantityWare1);
         testWareC1.setQuantity(quantityWare2);

         testAI2.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare2.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2 (test #2): " + testWare2.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare2 + quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1 (test #2): " + testWareC1.getQuantity() + ", should be " + (quantityWare2 + quantityToTrade2));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - sales");
         quantityToTrade1 = 0;
         quantityToTrade2 = (int) (Config.quanEquilibrium[testWareC1.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = 0; // overstock to avoid selling anything
         quantityWare2    = Config.quanEquilibrium[testWareC1.getLevel()];
         testWare2.setQuantity(quantityWare1);
         testWareC1.setQuantity(quantityWare2);

         testAI2.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare2.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2 (test #1): " + testWare2.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare2 + quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1 (test #1): " + testWareC1.getQuantity() + ", should be " + (quantityWare2 + quantityToTrade2));
            errorFound = true;
         }

         quantityToTrade1 = 0;
         quantityToTrade2 = 0;
         quantityToTrade3 = (int) (Config.quanEquilibrium[testWareC2.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = 0; // nothing to buy means don't buy anything
         quantityWare2    = 0;
         quantityWare3    = Config.quanEquilibrium[testWareC2.getLevel()];
         testWare1.setQuantity(quantityWare1);
         testWare3.setQuantity(quantityWare2);
         testWareC2.setQuantity(quantityWare3);

         testAI3.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1 (test #2): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWare3.getQuantity() != quantityWare2 - quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare3 (test #2): " + testWare3.getQuantity() + ", should be " + (quantityWare2 - quantityToTrade2));
            errorFound = true;
         }
         if (testWareC2.getQuantity() != quantityWare3 + quantityToTrade3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC2 (test #2): " + testWareC2.getQuantity() + ", should be " + (quantityWare3 + quantityToTrade3));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - trade decisions, supply and demand");
         quantityToTrade1 = 0;
         quantityToTrade2 = (int) (Config.quanEquilibrium[testWareC1.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = Config.quanEquilibrium[testWare2.getLevel()];
         quantityWare2    = Config.quanEquilibrium[testWareC1.getLevel()] - quantityToTrade2; // lower supply to encourage selling
         testWare2.setQuantity(quantityWare1);
         testWareC1.setQuantity(quantityWare2);

         testAI2.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare2.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2 (test #1): " + testWare2.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare2 + quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1 (test #1): " + testWareC1.getQuantity() + ", should be " + (quantityWare2 + quantityToTrade2));
            errorFound = true;
         }

         quantityToTrade1 = (int) (Config.quanEquilibrium[testWare2.getLevel()] * Config.aiTradeQuantityPercent);
         quantityToTrade2 = 0;
         quantityWare1    = Config.quanEquilibrium[testWare2.getLevel()] + (quantityToTrade1 * 5); // raise supply to increase demand
         quantityWare2    = Config.quanEquilibrium[testWareC1.getLevel()] - (int) (Config.quanEquilibrium[testWareC1.getLevel()] * Config.aiTradeQuantityPercent); // lower supply, but not enough to encourage selling; comparatively, scarity raises prices more than surplus lowers them
         testWare2.setQuantity(quantityWare1);
         testWareC1.setQuantity(quantityWare2);

         testAI2.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare2.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2 (test #2): " + testWare2.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare2 + quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1 (test #2): " + testWareC1.getQuantity() + ", should be " + (quantityWare2 + quantityToTrade2));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - trade decisions, preferences");
         // when everything is at equilibrium, choose the most preferred ware
         quantityToTrade1 = 0;
         quantityToTrade2 = 0;
         quantityToTrade3 = (int) (Config.quanEquilibrium[testWareC2.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         quantityWare2    = Config.quanEquilibrium[testWare3.getLevel()];
         quantityWare3    = Config.quanEquilibrium[testWareC2.getLevel()];
         testWare1.setQuantity(quantityWare1);
         testWare3.setQuantity(quantityWare2);
         testWareC2.setQuantity(quantityWare3);

         testAI3.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1 (test #1): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWare3.getQuantity() != quantityWare2 - quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare3 (test #1): " + testWare3.getQuantity() + ", should be " + (quantityWare2 - quantityToTrade2));
            errorFound = true;
         }
         if (testWareC2.getQuantity() != quantityWare3 + quantityToTrade3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC2 (test #1): " + testWareC2.getQuantity() + ", should be " + (quantityWare3 + quantityToTrade3));
            errorFound = true;
         }

         // despite prices being better by 5%, choose the ware preferred more by 10%
         quantityToTrade1 = 0;
         quantityToTrade2 = 0;
         quantityToTrade3 = (int) (Config.quanEquilibrium[testWare3.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = (int) (Config.quanEquilibrium[testWare1.getLevel()] * 1.05f);
         quantityWare2    = Config.quanEquilibrium[testWare3.getLevel()];
         quantityWare3    = (int) (Config.quanEquilibrium[testWareC2.getLevel()] * 0.95f);
         testWare1.setQuantity(quantityWare1);
         testWare3.setQuantity(quantityWare2);
         testWareC2.setQuantity(quantityWare3);

         testAI3.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1 (test #2): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWare3.getQuantity() != quantityWare2 - quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare3 (test #2): " + testWare3.getQuantity() + ", should be " + (quantityWare2 - quantityToTrade2));
            errorFound = true;
         }
         if (testWareC2.getQuantity() != quantityWare3 + quantityToTrade3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC2 (test #2): " + testWareC2.getQuantity() + ", should be " + (quantityWare3 + quantityToTrade3));
            errorFound = true;
         }


         // when prices are too good to pass up, choose the best deal
         quantityToTrade1 = 0;
         quantityToTrade2 = 0;
         quantityToTrade3 = (int) (Config.quanEquilibrium[testWareC2.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = (int) (Config.quanEquilibrium[testWare1.getLevel()] * 1.05f);
         quantityWare2    = Config.quanEquilibrium[testWare3.getLevel()];
         quantityWare3    = (int) (Config.quanEquilibrium[testWareC2.getLevel()] * 0.85f);
         testWare1.setQuantity(quantityWare1);
         testWare3.setQuantity(quantityWare2);
         testWareC2.setQuantity(quantityWare3);

         testAI3.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1 (test #3): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWare3.getQuantity() != quantityWare2 - quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare3 (test #3): " + testWare3.getQuantity() + ", should be " + (quantityWare2 - quantityToTrade2));
            errorFound = true;
         }
         if (testWareC2.getQuantity() != quantityWare3 + quantityToTrade3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC2 (test #3): " + testWareC2.getQuantity() + ", should be " + (quantityWare3 + quantityToTrade3));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - trade decisions, multiple, one ware");
         quantityToTrade1 = ((int) (Config.quanEquilibrium[testWare1.getLevel()] * Config.aiTradeQuantityPercent)) * 5;
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare1);

         testAI1.resetDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity (test #1): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }


         quantityToTrade1 = ((int) (Config.quanEquilibrium[testWare1.getLevel()] * Config.aiTradeQuantityPercent)) * 16;
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare1);

         testAI1.resetDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.incrementDecisionsPerTradeEvent();
         testAI1.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity (test #2): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - trade decisions, multiple, multiple wares");
         // testAI2 buys testWare2 and sells testWareC1
         // Since both are at equilibrium and testAI2 should make three trade decisions,
         // testAI2 should buy twice and sell once.
         quantityToTrade1 = ((int) (Config.quanEquilibrium[testWare2.getLevel()] * Config.aiTradeQuantityPercent)) * 2;
         quantityToTrade2 = (int) (Config.quanEquilibrium[testWareC1.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = Config.quanEquilibrium[testWare2.getLevel()];
         quantityWare2    = Config.quanEquilibrium[testWareC1.getLevel()];
         testWare2.setQuantity(quantityWare1);
         testWareC1.setQuantity(quantityWare2);

         testAI2.resetDecisionsPerTradeEvent();
         testAI2.incrementDecisionsPerTradeEvent();
         testAI2.incrementDecisionsPerTradeEvent();
         testAI2.incrementDecisionsPerTradeEvent();
         testAI2.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare2.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2 (test #1): " + testWare2.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare2 + quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1 (test #1): " + testWareC1.getQuantity() + ", should be " + (quantityWare2 + quantityToTrade2));
            errorFound = true;
         }

         // when making two trade decisions, choose the two best deals
         quantityToTrade1 = 0;
         quantityToTrade2 = (int) (Config.quanEquilibrium[testWare3.getLevel()] * Config.aiTradeQuantityPercent);
         quantityToTrade3 = (int) (Config.quanEquilibrium[testWareC2.getLevel()] * Config.aiTradeQuantityPercent);
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         quantityWare2    = Config.quanEquilibrium[testWare3.getLevel()] + quantityToTrade2 + quantityToTrade2;
         quantityWare3    = Config.quanEquilibrium[testWareC2.getLevel()] - quantityToTrade3 - quantityToTrade3 - quantityToTrade3;
         testWare1.setQuantity(quantityWare1);
         testWare3.setQuantity(quantityWare2);
         testWareC2.setQuantity(quantityWare3);

         testAI3.resetDecisionsPerTradeEvent();
         testAI3.incrementDecisionsPerTradeEvent();
         testAI3.incrementDecisionsPerTradeEvent();
         testAI3.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1 (test #2): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }
         if (testWare3.getQuantity() != quantityWare2 - quantityToTrade2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare3 (test #2): " + testWare3.getQuantity() + ", should be " + (quantityWare2 - quantityToTrade2));
            errorFound = true;
         }
         if (testWareC2.getQuantity() != quantityWare3 + quantityToTrade3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC2 (test #2): " + testWareC2.getQuantity() + ", should be " + (quantityWare3 + quantityToTrade3));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - reloading professions");
         // set state to known values
         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               "{\n" +
               "\"possibleAI\": {\n" +
               "\"purchasablesIDs\": [\"test:material1\"],\n" +
               "\"sellablesIDs\": [\"test:material2\"]\n" +
               "}\n" +
               "}\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   (initial setup) unable to create test AI professions file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // try to load the test file
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";

            // grab new references to threads in case it closed during earlier testing
            timerAIHandler = (Timer) fTimer.get(null);     // grab references to thread
            aiHandler      = (AIHandler) fTimerTask.get(null);

            Config.activeAI = new String[]{"possibleAI"}; // ensure there are AI to be run so thread doesn't close

            aiHandler.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   (initial setup) load() should not throw any exception, but it did while loading test professions file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            errorFound = true;
         }

         // check loaded AI information
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (professions == null) {
            TEST_OUTPUT.println("   (initial setup) AI profession should have loaded, but professions is null");
            errorFound = true;
         } else {
            // check AI profession's wares
            ai = professions.get("possibleAI");
            if (ai != null) {
               // grab data
               purchasablesIDs = (String[]) fPurchasablesIDs.get(ai);
               sellablesIDs    = (String[]) fSellablesIDs.get(ai);

               // check saved ware IDs for buying
               if (purchasablesIDs == null) {
                  TEST_OUTPUT.println("   (initial setup) purchasablesIDs were not loaded");
                  errorFound = true;
               }
               else {
                  if (purchasablesIDs.length == 1) {
                     if (!purchasablesIDs[0].equals("test:material1")) {
                        TEST_OUTPUT.println("   (initial setup) unexpected purchasablesIDs[0]: " + purchasablesIDs[0] + ", should be test:material1");
                        errorFound = true;
                     }
                  }
                  else {
                     TEST_OUTPUT.println("   (initial setup) purchasablesIDs length is " + purchasablesIDs.length + ", should be 1");
                     errorFound = true;
                  }
               }

               // check saved ware IDs for selling
               if (sellablesIDs == null) {
                  TEST_OUTPUT.println("   (initial setup) sellablesIDs were not loaded");
                  errorFound = true;
               }
               else {
                  if (sellablesIDs.length == 1) {
                     if (!sellablesIDs[0].equals("test:material2")) {
                        TEST_OUTPUT.println("   (initial setup) unexpected sellablesIDs[0]: " + sellablesIDs[0] + ", should be test:material2");
                        errorFound = true;
                     }
                  }
                  else {
                     TEST_OUTPUT.println("   (initial setup) sellablesIDs length is " + sellablesIDs.length + ", should be 1");
                     errorFound = true;
                  }
               }
            } else {
               TEST_OUTPUT.println("   (initial setup) AI profession was not loaded when it should have been");
               errorFound = true;
            }
         }


         // change state to new values
         // create test AI professions file
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               "{\n" +
               "\"possibleAI\": {\n" +
               "\"purchasablesIDs\": [\"test:processed1\", \"material4\"],\n" +
               "\"sellablesIDs\": [\"test:crafted3\", \"test:material1\", \"test:processed1\"]\n" +
               "}\n" +
               "}\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   (changed values) unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // try to load the test file
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";

            // grab new references to threads in case it closed during earlier testing
            timerAIHandler = (Timer) fTimer.get(null);     // grab references to thread
            aiHandler      = (AIHandler) fTimerTask.get(null);

            Config.activeAI = new String[]{"possibleAI"}; // ensure there are AI to be run so thread doesn't close

            aiHandler.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   (changed values) load() should not throw any exception, but it did while loading test professions file");
            e.printStackTrace();
            errorFound = true;
         }

         // check loaded AI information
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (professions == null) {
            TEST_OUTPUT.println("   (changed values) AI profession should have loaded, but professions is null");
            errorFound = true;
         } else {
            // check AI profession's wares
            ai = professions.get("possibleAI");
            if (ai != null) {
               // grab data
               purchasablesIDs = (String[]) fPurchasablesIDs.get(ai);
               sellablesIDs    = (String[]) fSellablesIDs.get(ai);

               // check saved ware IDs for buying
               if (purchasablesIDs == null) {
                  TEST_OUTPUT.println("   (changed values) purchasablesIDs were not loaded");
                  errorFound = true;
               }
               else {
                  if (purchasablesIDs.length == 2) {
                     if (!purchasablesIDs[0].equals("test:processed1")) {
                        TEST_OUTPUT.println("   (changed values) unexpected purchasablesIDs[0]: " + purchasablesIDs[0] + ", should be test:processed1");
                        errorFound = true;
                     }
                     if (!purchasablesIDs[1].equals("material4")) {
                        TEST_OUTPUT.println("   (changed values) unexpected purchasablesIDs[1]: " + purchasablesIDs[1] + ", should be material4");
                        errorFound = true;
                     }
                  }
                  else {
                     TEST_OUTPUT.println("   (changed values) purchasablesIDs length is " + purchasablesIDs.length + ", should be 2");
                     errorFound = true;
                  }
               }

               // check saved ware IDs for selling
               if (sellablesIDs == null) {
                  TEST_OUTPUT.println("   (changed values) sellablesIDs were not loaded");
                  errorFound = true;
               }
               else {
                  if (sellablesIDs.length == 3) {
                     if (!sellablesIDs[0].equals("test:crafted3")) {
                        TEST_OUTPUT.println("   (changed values) unexpected sellablesIDs[0]: " + sellablesIDs[0] + ", should be test:crafted3");
                        errorFound = true;
                     }
                     if (!sellablesIDs[1].equals("test:material1")) {
                        TEST_OUTPUT.println("   (changed values) unexpected sellablesIDs[1]: " + sellablesIDs[1] + ", should be test:material1");
                        errorFound = true;
                     }
                     if (!sellablesIDs[2].equals("test:processed1")) {
                        TEST_OUTPUT.println("   (changed values) unexpected sellablesIDs[2]: " + sellablesIDs[2] + ", should be test:processed1");
                        errorFound = true;
                     }
                  }
                  else {
                     TEST_OUTPUT.println("   (changed values) sellablesIDs length is " + sellablesIDs.length + ", should be 3");
                     errorFound = true;
                  }
               }
            } else {
               TEST_OUTPUT.println("   (changed values) AI profession was not loaded when it should have been");
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("AI - reloading trading frequency");
         // set up config file to known value
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "aiTradeFrequency       = 100\n" +
            "enableAI               = true\n" +
            "aiRandomness           = 0.0\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // try to reload configuration
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did");
            e.printStackTrace();
            errorFound = true;
         }

         // check trade frequency
         tradeFrequency = (long) fTradeFrequency.get(null);
         if (tradeFrequency != 6000000L) { // 60000 ms per min.
            TEST_OUTPUT.println("   unexpected frequency (test #1): " + tradeFrequency + ", should be 6000000");
            errorFound = true;
         }

         // set up config file to new value
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "aiTradeFrequency       = 123456\n" + // beyond maximum value
            "enableAI               = true\n" +
            "aiRandomness           = 0.0\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // try to reload configuration
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";
         } 
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did");
            e.printStackTrace();
            errorFound = true;
         }

         // check trade frequency
         tradeFrequency = (long) fTradeFrequency.get(null);
         if (tradeFrequency != 2147460000L) { // 60000 ms per min.
            TEST_OUTPUT.println("   unexpected frequency (test #2): " + tradeFrequency + ", should be 2147460000");
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - reloading trading quantity");
         // create a dummy AI so the thread does not shut down due to a lack of active AI
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               "{\n" +
               "\"possibleAI\": {\n" +
               "\"purchasablesIDs\": [\"test:processed1\", \"material4\"],\n" +
               "\"sellablesIDs\": [\"test:crafted3\", \"test:processed1\"]\n" +
               "}\n" +
               "}\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   (changed values) unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // ensure trade decisions are set to 1
         testAI1.resetDecisionsPerTradeEvent(); // set trade decisions to 1
         testAI1.incrementDecisionsPerTradeEvent();

         // predict trade quantity with new setting
         quantityToTrade1 = (int) (Config.quanEquilibrium[testWare1.getLevel()] * 0.5f);
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare1);

         // set up config file
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "aiTradeQuantityPercent = 0.5\n" +
            "activeAI               = possibleAI\n" +
            "enableAI               = true\n" +
            "aiTradeFrequency       = 999999999\n" +
            "aiRandomness           = 0.0\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // try to reload configuration
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";

            // grab new reference to thread since reloading frequency (the previous test case) should have restarted it
            aiHandler.stop = true; // prevent any unintentional trading
            aiHandler = (AIHandler) fTimerTask.get(null);

            aiHandler.run(); // preliminary setup from previous test case
            aiHandler.run(); // preliminary setup from current test case
            aiHandler.run(); // recalculation
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did");
            e.printStackTrace();
            errorFound = true;
         }

         // run the test
         testAI1.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity (test #1): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }

         // predict trade quantity with another new setting
         quantityToTrade1 = (int) (Config.quanEquilibrium[testWare1.getLevel()] * 0.25f);
         quantityWare1    = Config.quanEquilibrium[testWare1.getLevel()];
         testWare1.setQuantity(quantityWare1);

         // set up config file
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "aiTradeQuantityPercent = 0.25\n" +
            "activeAI               = possibleAI\n" +
            "enableAI               = true\n" +
            "aiTradeFrequency       = 999999999\n" +
            "aiRandomness           = 0.0\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // try to reload configuration
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";

            // grab new references to thread in case it was closed for whatever reason
            timerAIHandler = (Timer) fTimer.get(null);     // grab references to thread
            aiHandler      = (AIHandler) fTimerTask.get(null);

            aiHandler.run(); // preliminary setup
            aiHandler.run(); // recalculation
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   load() should not throw any exception, but it did");
            e.printStackTrace();
            errorFound = true;
         }

         // run the test
         testAI1.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         if (testWare1.getQuantity() != quantityWare1 - quantityToTrade1) {
            TEST_OUTPUT.println("   unexpected quantity (test #2): " + testWare1.getQuantity() + ", should be " + (quantityWare1 - quantityToTrade1));
            errorFound = true;
         }

         TEST_OUTPUT.println("AI - reloading active AI");
         // create several different professions to choose from
         try {
            // open the config file for AI professions, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameAIProfessions);

            // write test professions file
            fileWriter.write(
               "{\n" +
               "\"possibleAI1\": {\n" +
               "\"purchasablesIDs\": [\"test:processed1\", \"material4\"],\n" +
               "\"sellablesIDs\": [\"test:crafted3\", \"test:processed1\"]\n" +
               "},\n" +
               "\"possibleAI2\": {\n" +
               "\"purchasablesIDs\": [\"test:processed1\", \"material4\"]\n" +
               "},\n" +
               "\"possibleAI3\": {\n" +
               "\"sellablesIDs\": [\"test:crafted3\", \"test:processed1\"]\n" +
               "}\n" +
               "}\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   (changed values) unable to create test AI professions file");
            e.printStackTrace();
            return false;
         }

         // set up config file
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "activeAI               = possibleAI1,possibleAI1,possibleAI2\n" +
            "enableAI               = true\n" +
            "aiTradeFrequency       = 999999999\n" +
            "aiRandomness           = 0.0\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // try to reload configuration
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";
            aiHandler.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   run() should not throw any exception, but it did");
            e.printStackTrace();
            errorFound = true;
         }

         // grab and check active AI
         activeAI = (AI[]) fActiveAI.get(null);
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (activeAI == null) {
            TEST_OUTPUT.println("   AI should have been activated, but activeAI is null");
            errorFound = true;
         } else if (activeAI.length != 2) {
            TEST_OUTPUT.println("   AI activated loaded: " + activeAI.length + ", should be 2");
            errorFound = true;
         } else {
            // check whether first AI is active
            ai = professions.get("possibleAI1"); // grab AI reference to compare against
            if (activeAI[0] != ai && activeAI[1] != ai) {
               TEST_OUTPUT.println("   possibleAI1 is not active when it should be");
               errorFound = true;
            }

            // check whether second AI is active
            ai = professions.get("possibleAI2"); // grab AI reference to compare against
            if (activeAI[0] != ai && activeAI[1] != ai) {
               TEST_OUTPUT.println("   possibleAI2 is not active when it should be");
               errorFound = true;
            }
         }

         // change to using only the third AI profession to test changing active AI mid-game
         // set up config file
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "activeAI               = possibleAI3\n" +
            "enableAI               = true\n" +
            "aiTradeFrequency       = 999999999\n" +
            "aiRandomness           = 0.0\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // try to reload configuration
         try {
            InterfaceTerminal.serviceRequestReload(new String[]{"config"});
            Config.filenameNoPathAIProfessions = "testAIProfessions.json";
            Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";
            aiHandler.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   run() should not throw any exception, but it did");
            e.printStackTrace();
            errorFound = true;
         }

         // grab and check active AI
         activeAI = (AI[]) fActiveAI.get(null);
         professions = (HashMap<String, AI>) fProfessions.get(null);
         if (activeAI == null) {
            TEST_OUTPUT.println("   AI should have been activated, but activeAI is null");
            errorFound = true;
         } else if (activeAI.length != 1) {
            TEST_OUTPUT.println("   AI activated loaded: " + activeAI.length + ", should be 1");
            errorFound = true;
         } else {
            // check whether first AI is active
            ai = professions.get("possibleAI3"); // grab AI reference to compare against
            if (activeAI[0] != ai) {
               TEST_OUTPUT.println("   possibleAI3 is not active when it should be");
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("AI - toggling feature by reloading configuration");
         // write to config file to turn off feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "enableAI               = false\n" +
            "aiTradeFrequency       = 999999999\n" +
            "aiRandomness           = 0.0\n" +
            "activeAI               = possibleAI1, possibleAI2\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // attempt to turn off the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         Config.filenameNoPathAIProfessions = "testAIProfessions.json";
         Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";

         // check whether the feature is disabled
         timerAIHandler = (Timer) fTimer.get(null);
         if (timerAIHandler != null) {
            TEST_OUTPUT.println("   feature did not turn off when it should have");
            errorFound = true;
         }

         // write to config file to turn on feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "enableAI               = true\n" +
            "aiTradeFrequency       = 999999999\n" +
            "aiRandomness           = 0.0\n" +
            "activeAI               = possibleAI1, possibleAI2\n" +
            "disableAutoSaving      = true\n" +
            "crossWorldMarketplace  = true\n"
         );
         fileWriter.close();

         // attempt to turn on the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         Config.filenameNoPathAIProfessions = "testAIProfessions.json";
         Config.filenameAIProfessions       = "config" + File.separator + "CommandEconomy" + File.separator + "testAIProfessions.json";

         // check whether the feature is enabled
         timerAIHandler = (Timer) fTimer.get(null);
         if (timerAIHandler == null) {
            TEST_OUTPUT.println("   feature did not turn on when it should have");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("AI - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests /research.
    *
    * @return whether /research passed all test cases
    */
   private static boolean testUnitResearch() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // track changes to variables
      byte  wareLevel;
      int   wareQuantity;
      float price;
      float money;

      try {
         // /research <ware_id> [max_unit_price] [account_id] - spends to increase a ware's supply and demand

         // researchCostIsAMultOfAvgPrice == false; cost per hierarchy level = current market price * hierarchy level * Config.researchCostPerHierarchyLevel

         TEST_OUTPUT.println("research() - when disabled");
         Config.researchCostPerHierarchyLevel = 0.0f;
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material2"});
         if (!baosOut.toString().equals("error - industrial research is disabled" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("research() - null input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(null);
         if (!baosOut.toString().equals("/research <ware_id> [max_price_acceptable] [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - empty input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{});
         if (!baosOut.toString().equals("/research <ware_id> [max_price_acceptable] [account_id]" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - blank input");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"", ""});
         if (!baosOut.toString().startsWith("error - zero-length arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - too many args");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material1", "excessArgument", "excessArgument", "excessArgument"});
         if (!baosOut.toString().startsWith("error - wrong number of arguments")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("research() - invalid ware ID");
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"invalidWareID"});
         if (!baosOut.toString().equals("error - ware not found: invalidWareID" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
            resetTestEnvironment();
         }

         TEST_OUTPUT.println("research() - insufficient funds");
         wareLevel    = testWare2.getLevel();
         wareQuantity = testWare2.getQuantity();
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare2, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1.0f;
         playerAccount.setMoney(money);

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material2"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (!baosOut.toString().contains("test:material2 research price is " + CommandEconomy.truncatePrice(price) + "; to accept, use /research yes [max_price_acceptable] [account_id]") ||
             !baosOut.toString().contains("You do not have enough money" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (testWareFields(testWare2, WareMaterial.class, "", wareLevel, 27.6f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("research() - ware at lowest level");
         wareLevel    = testWare1.getLevel();
         wareQuantity = testWare1.getQuantity();

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material1"});

         if (!baosOut.toString().equals("test:material1 is already as plentiful as possible" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (testWareFields(testWare1, WareMaterial.class, "", wareLevel, 1.0f, wareQuantity)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - equilibrium: raw material");
         wareLevel    = testWare2.getLevel();
         wareQuantity = Config.startQuanBase[testWare2.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare2, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material2"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWare2, WareMaterial.class, "", (byte) (wareLevel - 1), 27.6f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - equilibrium: processed ware");
         wareLevel    = testWareP1.getLevel();
         wareQuantity = Config.startQuanBase[testWareP1.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWareP1, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:processed1"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) (wareLevel - 1), 1.1f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - understocked");
         wareLevel    = testWare3.getLevel();
         wareQuantity = Config.startQuanBase[testWare3.getLevel() - 1];
         testWare3.setQuantity(1); // set stock to be low
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare3, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material3"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) (wareLevel - 1), 4.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - overstocked");
         wareLevel    = testWare4.getLevel();
         wareQuantity = Config.startQuanBase[testWare4.getLevel() - 1] + 10;
         testWare4.setQuantity(wareQuantity); // set stock to be high
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare4, 0, Marketplace.PriceType.CURRENT_BUY) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"minecraft:material4"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) (wareLevel - 1), 8.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         resetTestEnvironment();

         TEST_OUTPUT.println("research() - excessively overstocked");
         wareLevel    = testWare4.getLevel();
         wareQuantity = 999999999;
         testWare4.setQuantity(wareQuantity); // set stock to be excessively high
         money        = playerAccount.getMoney();

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"minecraft:material4"});

         if (!baosOut.toString().equals("material4 is already plentiful" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", wareLevel, 8.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - non-personal account: generating proposal");
         wareLevel    = testWareP1.getLevel();
         wareQuantity = Config.startQuanBase[testWareP1.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWareP1, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         testAccount1.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:processed1", "testAccount1"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) (wareLevel - 1), 1.1f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - non-personal account: accepting proposal");
         wareLevel    = testWare3.getLevel();
         wareQuantity = Config.startQuanBase[testWare3.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare3, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         testAccount1.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material3"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes", "testAccount1"});

         if (testWareFields(testWare3, WareMaterial.class, "mat3", (byte) (wareLevel - 1), 4.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - non-personal account: generating and accepting proposal");
         wareLevel    = testWare2.getLevel();
         wareQuantity = Config.startQuanBase[testWare2.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare2, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         testAccount1.setMoney(money);
         testAccount2.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material2", "testAccount1"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes", "testAccount2"});

         if (testWareFields(testWare2, WareMaterial.class, "", (byte) (wareLevel - 1), 27.6f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount1, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount2, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         resetTestEnvironment();

         TEST_OUTPUT.println("research() - non-personal account: inaccessible");
         money        = 1000000.0f;
         testAccount4.setMoney(money);
         wareLevel    = testWare2.getLevel();
         wareQuantity = testWare2.getQuantity();

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:material2", "testAccount4"});
         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (!baosOut.toString().equals("You don't have permission to access testAccount4" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (testWareFields(testWare2, WareMaterial.class, "", (byte) wareLevel, 27.6f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(testAccount4, money, null)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - maximum price acceptable: insufficient");
         wareLevel    = testWareP1.getLevel();
         wareQuantity = Config.startQuanBase[testWareP1.getLevel()];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWareP1, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"test:processed1", String.valueOf(price - 100.0f)});

         if (!baosOut.toString().contains("test:processed1 research price is " + CommandEconomy.truncatePrice(price) + "; to accept, use /research yes [max_price_acceptable] [account_id]")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) wareLevel, 1.1f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         resetTestEnvironment();

         TEST_OUTPUT.println("research() - maximum price acceptable: sufficient");
         wareLevel    = testWareP1.getLevel();
         wareQuantity = Config.startQuanBase[testWareP1.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWareP1, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"test:processed1", String.valueOf(price + 100.0f)});

         if (testWareFields(testWareP1, WareProcessed.class, "", (byte) (wareLevel - 1), 1.1f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (!baosOut.toString().equals("You don't have any pending research proposals" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         resetTestEnvironment();

         TEST_OUTPUT.println("research() - charging according to market's current average price");
         Config.researchCostIsAMultOfAvgPrice = true;
         Config.researchCostPerHierarchyLevel = 2.0f;
         wareLevel    = testWare4.getLevel();
         wareQuantity = Config.startQuanBase[testWare4.getLevel() - 1];
         price        = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare4, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel * Marketplace.getCurrentPriceAverage();
         price        = CommandEconomy.truncatePrice(price);
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"minecraft:material4"});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) (wareLevel - 1), 8.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         TEST_OUTPUT.println("research() - accepting nonexistent proposal");

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (!baosOut.toString().equals("You don't have any pending research proposals" + System.lineSeparator())) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         resetTestEnvironment();

         TEST_OUTPUT.println("research() - accepting proposal after price lowered");
         wareLevel    = testWare4.getLevel();
         wareQuantity = Config.startQuanBase[testWare4.getLevel() - 1];
         testWare4.setQuantity(Config.quanDeficient[testWare4.getLevel()]); // ensure ware's stock is low so price is high
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"minecraft:material4"});

         if (!baosOut.toString().startsWith("material4 research price is ")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }

         testWare4.setQuantity(wareQuantity - 1);
         price              = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare4, 0, Marketplace.PriceType.CURRENT_BUY) * wareLevel * Config.researchCostPerHierarchyLevel;
         price              = CommandEconomy.truncatePrice(price);

         baosOut.reset(); // clear buffer holding console output
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (!baosOut.toString().contains("material4's commonality and price stability has increased")) {
            TEST_OUTPUT.println("   unexpected console output: " + baosOut.toString());
            errorFound = true;
         }
         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) (wareLevel - 1), 8.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         resetTestEnvironment();

         TEST_OUTPUT.println("research() - accepting proposal after price rose slightly");
         wareLevel    = testWare4.getLevel();
         wareQuantity = Config.startQuanBase[testWare4.getLevel() - 1];
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"minecraft:material4"});

         testWare4.setQuantity((int) (testWare4.getQuantity() * 1.04));
         price              = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare4, 0, Marketplace.PriceType.CURRENT_BUY) * wareLevel * Config.researchCostPerHierarchyLevel;
         price              = CommandEconomy.truncatePrice(price);

         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWare4, WareMaterial.class, "material4", (byte) (wareLevel - 1), 8.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money - price, InterfaceTerminal.playername)) {
            errorFound = true;
         }

         resetTestEnvironment();

         TEST_OUTPUT.println("research() - accepting proposal after price rose greatly");
         wareLevel    = testWare4.getLevel();
         wareQuantity = Config.quanDeficient[testWare4.getLevel()];
         testWare4.setQuantity(Config.startQuanBase[testWare4.getLevel()]); // ensure ware's stock is normal so price is normal
         money        = 1000000.0f;
         playerAccount.setMoney(money);

         InterfaceTerminal.serviceRequestResearch(new String[]{"minecraft:material4"});

         testWare4.setQuantity(wareQuantity);
         price              = Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare4, 0, Marketplace.PriceType.CURRENT_SELL) * wareLevel * Config.researchCostPerHierarchyLevel;
         price              = CommandEconomy.truncatePrice(price);

         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         if (testWareFields(testWare4, WareMaterial.class, "material4", wareLevel, 8.0f, wareQuantity)) {
            errorFound = true;
         }
         if (testAccountFields(playerAccount, money, InterfaceTerminal.playername)) {
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("research() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests adjusting manufactured wares' prices based on components' prices.
    *
    * @return whether Ware.getLinkedPriceMultiplier() and Marketplace.getPrice() passed all test cases
    */
   private static boolean testUnitLinkedPrices() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.shouldComponentsCurrentPricesAffectWholesPrice = true;
      Config.linkedPricesPercent = 0.75f;

      // track changes to variables
      float priceMult;
      float price;
      int   quantityWare;
      int   quantityComponent1;
      int   quantityComponent2;
      int   quantityComponent3;
      Ware  testWare;
      Ware  testComponent1;
      Ware  testComponent2;
      Ware  testComponent3;

      try {
         TEST_OUTPUT.println("linked prices - lowering price at equilibrium");
         priceMult          = 0.625f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 640;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 0.8125f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 56;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - lowering price when above equilibrium");
         priceMult          = 0.625f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] * 2 - 1;
         quantityComponent1 = 640;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 0.8125f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] * 2 - 1;
         quantityComponent1 = 56;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - lowering price when below equilibrium");
         priceMult          = 0.625f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = (int) (Config.quanEquilibrium[testWare.getLevel()] * 0.75f) - 1;
         quantityComponent1 = 640;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 0.8125f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = (int) (Config.quanEquilibrium[testWare.getLevel()] * 0.75f) - 1;
         quantityComponent1 = 56;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - raising price at equilibrium");
         priceMult          = 1.375f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 192;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 1.5625f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 20;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - raising price when above equilibrium");
         priceMult          = 1.375f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] * 2 - 1;
         quantityComponent1 = 192;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) ((price + 0.0001f) * 10000.0f)) / 10000.0f; // truncate and round to match getPrice()
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 1.5625f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] * 2 - 1;
         quantityComponent1 = 20;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - raising price when below equilibrium");
         priceMult          = 1.375f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = (int) (Config.quanEquilibrium[testWare.getLevel()] * 0.75f) - 1;
         quantityComponent1 = 192;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 1.5625f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = (int) (Config.quanEquilibrium[testWare.getLevel()] * 0.75f) - 1;
         quantityComponent1 = 20;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - free components");
         priceMult          = 0.25f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 0.25f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - expensive components");
         priceMult          = 1.75f;
         testWare           = testWareP1;
         testComponent1     = testWare1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 0;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         priceMult          = 1.75f;
         testWare           = testWareC3;
         testComponent1     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 0;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);

         TEST_OUTPUT.println("linked prices - one free component");
         priceMult          = 0.7692308f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 9999;
         quantityComponent3 = Config.quanEquilibrium[testComponent3.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #1): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #1): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 0.53846157f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         quantityComponent3 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #2): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #2): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 0.28712872f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #1): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #1): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         priceMult          = 0.9628713f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 9999;
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #2): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #2): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         TEST_OUTPUT.println("linked prices - one expensive component");
         priceMult          = 1.2307692f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 0;
         quantityComponent3 = Config.quanEquilibrium[testComponent3.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 1.0576923f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 0;
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         quantityComponent3 = Config.quanEquilibrium[testComponent3.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 1.7128713f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 0;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         priceMult          = 1.0371287f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 0;
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + "): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + "): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         TEST_OUTPUT.println("linked prices - equilibrium price smaller than base price");
         Config.priceMult = 0.5f; // lowers all prices
         priceMult          = 0.7692308f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 9999;
         quantityComponent3 = Config.quanEquilibrium[testComponent3.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #1): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #1): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 0.53846157f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         quantityComponent3 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #2): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #2): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 0.28712872f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #1): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #1): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         priceMult          = 0.9628713f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 9999;
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #2): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #2): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         TEST_OUTPUT.println("linked prices - equilibrium price larger than base price");
         Config.priceMult = 2.0f; // raises all prices
         priceMult          = 0.7692308f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 9999;
         quantityComponent3 = Config.quanEquilibrium[testComponent3.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #1): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #1): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 0.53846157f;
         testWare           = testWareP2;
         testComponent1     = testWare1;
         testComponent2     = testWare3;
         testComponent3     = testWare4;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         quantityComponent3 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);
         testComponent3.setQuantity(quantityComponent3);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #2): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #2): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);
         testComponent3.setQuantity(Config.quanEquilibrium[testComponent3.getLevel()]);

         priceMult          = 0.28712872f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = Config.quanEquilibrium[testComponent1.getLevel()];
         quantityComponent2 = 9999;
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #1): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #1): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }

         // prepare for next test
         testWare.setQuantity(Config.quanEquilibrium[testWare.getLevel()]);
         testComponent1.setQuantity(Config.quanEquilibrium[testComponent1.getLevel()]);
         testComponent2.setQuantity(Config.quanEquilibrium[testComponent2.getLevel()]);

         priceMult          = 0.9628713f;
         testWare           = testWareC2;
         testComponent1     = testWare1;
         testComponent2     = testWareC1;
         quantityWare       = Config.quanEquilibrium[testWare.getLevel()] - 1;
         quantityComponent1 = 9999;
         quantityComponent2 = Config.quanEquilibrium[testComponent2.getLevel()];
         testWare.setQuantity(quantityWare);
         price = priceMult * Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL);
         price = ((int) (price * 10000.0f)) / 10000.0f; // truncate to match getPrice()'s truncation
         testComponent1.setQuantity(quantityComponent1);
         testComponent2.setQuantity(quantityComponent2);

         if (testWare.getLinkedPriceMultiplier() != priceMult) {
            TEST_OUTPUT.println("   unexpected linked price multiplier (" + testWare.getWareID() + ", #2): " + testWare.getLinkedPriceMultiplier() + ", should be " + priceMult);
            errorFound = true;
         }
         if (Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) != price) {
            TEST_OUTPUT.println("   unexpected price (" + testWare.getWareID() + ", #2): " + Marketplace.getPrice(InterfaceTerminal.getPlayerIDStatic(InterfaceTerminal.playername), testWare, 1, Marketplace.PriceType.CURRENT_SELL) + ", should be " + price);
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("linked prices - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests purchasing out-of-stock manufactured wares
    * if their components are for sale.
    *
    * @return whether buy() passed all manufacturing contract test cases
    */
   private static boolean testUnitManufacturingContracts() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         // manufactured wares:
         // testWareP1: testWare1
         // testWareP2: testWare1, testWare2, testWare4
         // testWareC1: testWareU1
         // testWareC2: testWare1, testWareC1
         // testWareC3: testWare4

         TEST_OUTPUT.println("manufacturing contracts - when disabled and out-of-stock, untradeable component");
         errorFound |= testerBuyManufacturing(testWareC1, null, null, null, null, 0.0f, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - when disabled and out-of-stock, material component");
         errorFound |= testerBuyManufacturing(testWareC3, testWare4, null, null, null, 0.0f, 0, testWare4.getQuantity(), 0, 0, 0, 0, 0, 0, 1, 0, false);

         // enable manufacturing contracts
         Config.buyingOutOfStockWaresAllowed   = true;
         Config.buyingOutOfStockWaresPriceMult = 1.10f; // explicitly set to known value

         TEST_OUTPUT.println("manufacturing contracts - untradeable ware");
         errorFound |= testerBuyManufacturing(testWareU1, null, null, null, null, 0.0f, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - non-manufactured ware, out-of-stock");
         errorFound |= testerBuyManufacturing(testWare1, null, null, null, null, 0.0f, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock: untradeable component");
         errorFound |= testerBuyManufacturing(testWareC1, testWareU1, null, null, null, 0.0f, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock: material components");
         errorFound |= testerBuyManufacturing(testWareP2, testWare1, testWare3, testWare4, null, 0.0f,
                                              0, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWare3.getLevel()], Config.quanEquilibrium[testWare4.getLevel()],
                                              1, 1, 1, 1, 1, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock: manufactured component");
         errorFound |= testerBuyManufacturing(testWareC2, testWare1, testWareC1, null, null, 0.0f,
                                                 0, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                                 1, 1, 1, 0, 1, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock, varying order sizes: untradeable component");
         errorFound |= testerBuyManufacturing(testWareC1, testWareU1, null, null, null, 0.0f, 0, 0, 0, 0, 32, 32, 0, 0, 32, 1, false);

         InterfaceTerminal.inventory.clear(); // just in case inventory space is insufficient
         errorFound |= testerBuyManufacturing(testWareC1, testWareU1, null, null, null, 0.0f, 0, 0, 0, 0, 192, 192, 0, 0, 192, 2, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock, varying order sizes: material components");
         errorFound |= testerBuyManufacturing(testWareP2, testWare1, testWare3, testWare4, null, 0.0f,
                                              0, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWare3.getLevel()], Config.quanEquilibrium[testWare4.getLevel()],
                                              16, 16, 16, 16, 16, 1, false);

         InterfaceTerminal.inventory.clear(); // just in case inventory space is insufficient
         errorFound |= testerBuyManufacturing(testWareC3, testWare4, null, null, null, 0.0f,
                                              0, Config.quanExcessive[testWare4.getLevel()], 0, 0,
                                              96, 24, 0, 0, 96, 2, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock, varying order sizes: manufactured components");
         errorFound |= testerBuyManufacturing(testWareC2, testWare1, testWareC1, null, null, 0.0f,
                                              0, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              4, 4, 4, 0, 4, 1, false);

         InterfaceTerminal.inventory.clear(); // just in case inventory space is insufficient
         errorFound |= testerBuyManufacturing(testWareC2, testWare1, testWareC1, null, null, 0.0f,
                                              0, Config.quanExcessive[testWare1.getLevel()], Config.quanExcessive[testWareC1.getLevel()], 0,
                                              187, 187, 187, 0, 256, 2, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, low in stock: untradeable component");
         errorFound |= testerBuyManufacturing(testWareC1, testWareU1, null, null, null, 0.0f, 5, 0, 0, 0, 10, 5, 0, 0, 10, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, low in stock: material components");
         errorFound |= testerBuyManufacturing(testWareP2, testWare1, testWare3, testWare4, null, 0.0f,
                                              4, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWare3.getLevel()], Config.quanEquilibrium[testWare4.getLevel()],
                                              16, 12, 12, 12, 16, 1, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, low in stock: manufactured component");
         errorFound |= testerBuyManufacturing(testWareC2, testWare1, testWareC1, null, null, 0.0f,
                                              24, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              32, 8, 8, 0, 32, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock: components low in stock");
         errorFound |= testerBuyManufacturing(testWareP2, testWare1, testWare3, testWare4, null, 0.0f,
                                              0, 8, Config.quanEquilibrium[testWare3.getLevel()], Config.quanEquilibrium[testWare4.getLevel()],
                                              8, 8, 8, 8, 16, 1, false);

         errorFound |= testerBuyManufacturing(testWareC3, testWare4, null, null, null, 0.0f,
                                              0, 10, 0, 0,
                                              40, 10, 0, 0, 80, 2, false);

         // create a new ware with a component whose component has limited stock
         Ware testWareC4 = new WareCrafted(new String[]{"test:crafted3"}, "test:crafted4", "", 32, 2, (byte) 3);
         wares.put("test:crafted4", testWareC4);

         errorFound |= testerBuyManufacturing(testWareC4, testWareC3, null, null, null, 0.0f,
                                              0, 5, 0, 0,
                                              10, 5, 0, 0, 20, 3, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware, out-of-stock: same component used multiple times");
         InterfaceTerminal.inventory.clear(); // just in case inventory space is insufficient
         // create a new ware with a component whose component has limited stock
         Ware testWareC5 = new WareCrafted(new String[]{"test:material1", "test:material1", "test:material1", "test:material2"}, "test:crafted5", "", 32, 4, (byte) 3);
         wares.put("test:crafted5", testWareC5);

         errorFound |= testerBuyManufacturing(testWareC5, testWare1, testWare2, null, null, 0.0f,
                                              0, 30, 20, 0,
                                              40, 30, 10, 0, 80, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - manufactured ware and components out-of-stock");
         errorFound |= testerBuyManufacturing(testWareC2, testWare1, testWareC1, null, null, 0.0f,
                                              0, Config.quanEquilibrium[testWare1.getLevel()], 0, 0,
                                              0, 0, 0, 0, 10, 0, false);

         TEST_OUTPUT.println("manufacturing contracts - buy: minimum args");
         errorFound |= testerBuyManufacturing(testWareC3, testWare4, null, null, null, 0.0f,
                                              0, Config.quanEquilibrium[testWare4.getLevel()], 0, 0,
                                              1, 1, 0, 0, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - buy: no inventory space");
         int inventorySpaceOrig = InterfaceTerminal.inventorySpace;
         InterfaceTerminal.inventorySpace = 0; // maximum inventory space is no inventory
         errorFound |= testerBuyManufacturing(testWareC3, testWare4, null, null, null, 0.0f,
                                              0, Config.quanEquilibrium[testWare4.getLevel()], 0, 0,
                                              1, 1, 0, 0, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - buy: low in inventory space");
         InterfaceTerminal.inventory.clear(); // just in case inventory space is insufficient
         InterfaceTerminal.inventorySpace = 1; // maximum inventory space is 64 items

         errorFound |= testerBuyManufacturing(testWareC3, testWare4, null, null, null, 0.0f,
                                              0, Config.quanExcessive[testWare4.getLevel()], 0, 0,
                                              64, 16, 0, 0, 128, 0, true);

         InterfaceTerminal.inventorySpace = inventorySpaceOrig; // reset maximum inventory space

         TEST_OUTPUT.println("manufacturing contracts - buy: non-manufactured ware");
         errorFound |= testerBuyManufacturing(testWare1, null, null, null, null, 0.0f,
                                              Config.quanEquilibrium[testWare1.getLevel()], 0, 0, 0,
                                              1, 0, 0, 0, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - buy: max price acceptable, too low");
         errorFound |= testerBuyManufacturing(testWareC3, null, null, null, null, 0.01f,
                                              Config.quanEquilibrium[testWareC3.getLevel()], 0, 0, 0,
                                              0, 0, 0, 0, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - buy: max price acceptable, sufficient");
         errorFound |= testerBuyManufacturing(testWareC2, testWare1, testWareC1, null, null, 999999.00f,
                                              0, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              1, 1, 1, 0, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - buy: account ID");
         errorFound |= testerBuyManufacturing(testWareP2, testWare1, testWare3, testWare4, "testAccount1", 0.0f,
                                              0, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWare3.getLevel()], Config.quanEquilibrium[testWare4.getLevel()],
                                              1, 1, 1, 1, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - buy: max price acceptable and account ID");
         errorFound |= testerBuyManufacturing(testWareC1, testWareU1, null, null, "testAccount1", 999999.00f,
                                              0, 0, 0, 0,
                                              1, 1, 0, 0, 1, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - check: non-manufactured ware");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 1, null,
                                   1.0f, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - check: minimum args");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareP1, 1, null,
                                   1.0f, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - check: minimum args, ware out of stock");
         testWareP1.setQuantity(0);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareP1, 0, null,
                                   1.0f, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - check: specified quantity, ware out of stock");
         testWareC1.setQuantity(0);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareC1, 0, null,
                                   1.0f, 0, true);

         TEST_OUTPUT.println("manufacturing contracts - check: specified quantity, ware low in stock");
         testWareC1.setQuantity(5);
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareC1, 10, null,
                                   1.0f, 0, true);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("testUnitManufacturingContracts() - fatal error: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests paying transaction fees when buying, selling, or sending money.
    *
    * @return whether handling transaction fees passed all test cases
    */
   private static boolean testUnitTransactionFees() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.chargeTransactionFees = true;
      FileWriter fileWriter;
      // add a ware with a negative price
      Ware wareWithNegativePrice = new WareMaterial("minecraft:rotten_flesh", "rotten_flesh", -1.0f, 1000, (byte) 0);
      wares.put("minecraft:rotten_flesh", wareWithNegativePrice);
      wareAliasTranslations.put("rotten_flesh", "minecraft:rotten_flesh");

      // track changes to variables
      Account accountFeeCollection;
      Account account1;
      Account account2;

      // ensure fee collection account exists and has sufficient wealth to pay negative fees
      accountFeeCollection = Account.getAccount(Config.transactionFeesAccount);
      if (accountFeeCollection == null)
         accountFeeCollection = Account.makeAccount(Config.transactionFeesAccount, null);
      accountFeeCollection.setMoney(Float.POSITIVE_INFINITY);

      try {
         TEST_OUTPUT.println("transaction fees - buy(): when disabled");
         Config.transactionFeeSelling = 1.10f;
         Config.transactionFeeSending = 0.10f;
         errorFound |= testerBuyTransActFee(testWare1, 16, 0.00f, 0);

         TEST_OUTPUT.println("transaction fees - buy(): when enabled");
         errorFound |= testerBuyTransActFee(testWare2, 8, 1.10f, 0);

         TEST_OUTPUT.println("transaction fees - buy(): zero rates");
         Config.transactionFeeBuyingIsMult = true;
         errorFound |= testerTradeTransActFee(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 0,
                                              16, 0.0f, 17.0f, 1, false, true);

         Config.transactionFeeBuyingIsMult = false;
         errorFound |= testerTradeTransActFee(testWare4, Config.quanEquilibrium[testWare4.getLevel()], 0,
                                              1, 0.0f, testWare4.getBasePrice(), 2, false, true);

         TEST_OUTPUT.println("transaction fees - buy(): flat rates, positive");
         errorFound |= testerBuyTransActFee(testWare2, 48, 10.00f, 1);

         errorFound |= testerBuyTransActFee(testWare3, 4, 100.00f, 2);

         errorFound |= testerBuyTransActFee(testWareC1, 12, 128.00f, 3);

         TEST_OUTPUT.println("transaction fees - buy(): flat rates, negative");
         errorFound |= testerBuyTransActFee(testWareC2, 12, -50.00f, 1);

         errorFound |= testerBuyTransActFee(testWareC2, 24, -124.50f, 2);

         errorFound |= testerBuyTransActFee(testWareP2, 7, -0.86f, 3);

         TEST_OUTPUT.println("transaction fees - buy(): flat rates, positive, negative prices");
         // enable negative prices
         Config.priceFloor         = 1.0f;
         Config.priceFloorAdjusted = 0.0f;

         errorFound |= testerBuyTransActFee(wareWithNegativePrice,  10,  10.00f, 1);

         errorFound |= testerBuyTransActFee(wareWithNegativePrice, 100,   5.00f, 2);

         TEST_OUTPUT.println("transaction fees - buy(): flat rates, negative, negative prices");
         errorFound |= testerBuyTransActFee(wareWithNegativePrice,  10, -10.00f, 1);

         errorFound |= testerBuyTransActFee(wareWithNegativePrice, 100,  -5.00f, 2);

         // disable negative prices
         Config.priceFloor         =  0.0f;
         Config.priceFloorAdjusted =  1.0f;

         TEST_OUTPUT.println("transaction fees - buy(): percent rates, positive");
         Config.transactionFeeBuyingIsMult = true;
         errorFound |= testerBuyTransActFee(testWareP1, 14, 0.10f, 1);

         errorFound |= testerBuyTransActFee(testWare3, 13, 0.50f, 2);

         errorFound |= testerBuyTransActFee(testWare1, 5, 1.00f, 3);

         TEST_OUTPUT.println("transaction fees - buy(): percent rates, negative");
         errorFound |= testerBuyTransActFee(testWareP1, 5, -0.10f, 1);

         errorFound |= testerBuyTransActFee(testWare2, 6, -0.50f, 2);

         errorFound |= testerBuyTransActFee(testWareC1, 74, -0.24f, 3);

         TEST_OUTPUT.println("transaction fees - buy(): percent rates, positive, negative prices");
         // enable negative prices
         Config.priceFloor         = 1.0f;
         Config.priceFloorAdjusted = 0.0f;

         errorFound |= testerBuyTransActFee(wareWithNegativePrice,  10,  1.00f, 1);

         errorFound |= testerBuyTransActFee(wareWithNegativePrice, 100,  0.50f, 2);

         TEST_OUTPUT.println("transaction fees - buy(): percent rates, negative, negative prices");
         errorFound |= testerBuyTransActFee(wareWithNegativePrice,  10, -1.00f, 1);

         errorFound |= testerBuyTransActFee(wareWithNegativePrice, 100, -0.50f, 2);

         // disable negative prices
         Config.priceFloor         =  0.0f;
         Config.priceFloorAdjusted =  1.0f;

         TEST_OUTPUT.println("transaction fees - buy(): funds checking includes fees, positive");
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              10, 1.00f, 0.0f, 1, true, true);

         Config.transactionFeeBuyingIsMult = false;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              10, 10.00f, 0.0f, 2, true, true);

         TEST_OUTPUT.println("transaction fees - buy(): funds checking includes fees, negative");
         Config.transactionFeeBuyingIsMult = true;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              10, -0.10f, 0.0f, 1, true, true);

         Config.transactionFeeBuyingIsMult = false;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              10, -5.00f, 0.0f, 2, true, true);

         TEST_OUTPUT.println("transaction fees - buy(): funds checking includes fees, extremely negative");
         Config.transactionFeeBuyingIsMult = true;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              64, -1.00f, 0.0f, 1, false, true);

         Config.transactionFeeBuyingIsMult = false;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 0,
                                              10, testWareC1.getBasePrice() * -11.0f, 0.0f, 2, false, true);

         TEST_OUTPUT.println("transaction fees - buy(): changing fee applied message");
         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees      = true\n" +
               "transactionFeeBuyingIsMult = true\n" +
               "transactionFeeBuyingMsg    = Sales tax: \n" +
               "disableAutoSaving          = true\n" +
               "crossWorldMarketplace      = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         errorFound |= testerBuyTransActFee(testWareP1, 14, 0.10f, 1);

         errorFound |= testerBuyTransActFee(testWare3, 13, 0.50f, 2);

         errorFound |= testerBuyTransActFee(testWare1, 5, 1.00f, 3);

         TEST_OUTPUT.println("transaction fees - buy(): restoring fee applied message");
         Config.transactionFeeBuyingMsg    = "   Transaction fee applied: ";
         errorFound |= testerBuyTransActFee(testWareP1, 14, 0.10f, 1);

         errorFound |= testerBuyTransActFee(testWare3, 13, 0.50f, 2);

         errorFound |= testerBuyTransActFee(testWare1, 5, 1.00f, 3);

         TEST_OUTPUT.println("transaction fees - buy(): fee account doesn't exist");
         Config.transactionFeesAccount = "transactionFeeCollectionBuy";

         // ensure account does not exist
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account1.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesShouldPutFeesIntoAccount = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerBuyTransActFee(testWare1, 10, 0.10f, 0);

         // check account existence
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account1.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account1.getOwner().toString());
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("transaction fees - buy(): fee account changed");
         Config.transactionFeesShouldPutFeesIntoAccount = true;
         account1 = Account.getAccount(Config.transactionFeesAccount);
         Config.transactionFeesAccount                  = "newTransactionFeeCollectionBuy";
         account2 = Account.getAccount(Config.transactionFeesAccount);

         // ensure account does not exist
         if (account2 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account2.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerBuyTransActFee(testWare1, 10, 0.10f, 0);

         // check account existence
         account2 = Account.getAccount(Config.transactionFeesAccount);
         if (account2 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account2.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account2.getOwner().toString());
               errorFound = true;
            }
         }

         // reset test environment in case
         // reloading configuration made unexpected changes
         resetTestEnvironment();
         Config.chargeTransactionFees                   = true;
         Config.transactionFeesShouldPutFeesIntoAccount = true;

         // ensure fee collection account exists
         accountFeeCollection = Account.getAccount(Config.transactionFeesAccount);
         if (accountFeeCollection == null)
            accountFeeCollection = Account.makeAccount(Config.transactionFeesAccount, null);

         // add a ware with a negative price
         wares.put("minecraft:rotten_flesh", wareWithNegativePrice);
         wareAliasTranslations.put("rotten_flesh", "minecraft:rotten_flesh");

         TEST_OUTPUT.println("transaction fees - buy(): fee account, positive rates");
         errorFound |= testerBuyAccountTransActFee(testWare4,   10, 0.87f,    0.0f, 1, true);

         errorFound |= testerBuyAccountTransActFee(testWare1,   99, 1.00f,  1000.0f, 2, true);

         errorFound |= testerBuyAccountTransActFee(testWare1, 256, 2.14f, 10000.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - buy(): fee account, negative rates");
         errorFound |= testerBuyAccountTransActFee(testWare3,    2, -0.12f,   100.0f, 1, true);

         errorFound |= testerBuyAccountTransActFee(testWare1,   17, -0.23f,   100.0f, 2, true);

         errorFound |= testerBuyAccountTransActFee(testWareC2, 64, -1.50f, 10000.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - buy(): fee account funds too low to pay negative rate");
         errorFound |= testerBuyAccountTransActFee(testWareC1, 10, -0.10f, 10.0f, 1, true);

         Config.transactionFeeBuyingIsMult = false;
         errorFound |= testerBuyAccountTransActFee(testWareC1, 10, -11.0f, 10.0f, 2, true);

         Config.transactionFeesShouldPutFeesIntoAccount = false;


         // ensure fee collection account has sufficient wealth to pay negative fees
         accountFeeCollection.setMoney(Float.POSITIVE_INFINITY);

         TEST_OUTPUT.println("transaction fees - sell(): when disabled");
         Config.transactionFeeSellingIsMult = true;
         Config.transactionFeeBuying        = 0.05f;
         Config.transactionFeeSending       = 0.02f;
         errorFound |= testerSellTransActFee(testWare3, 15, 0.00f, 0);

         TEST_OUTPUT.println("transaction fees - sell(): when enabled");
         errorFound |= testerSellTransActFee(testWare3, 15, 0.75f, 0);

         TEST_OUTPUT.println("transaction fees - sell(): zero rates");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerTradeTransActFee(testWare1, Config.quanEquilibrium[testWare1.getLevel()], Config.quanEquilibrium[testWare1.getLevel()],
                                              16, 0.0f, 0.0f, 1, false, false);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerTradeTransActFee(testWare4, Config.quanEquilibrium[testWare4.getLevel()], Config.quanEquilibrium[testWare4.getLevel()],
                                              Config.quanEquilibrium[testWare4.getLevel()], 0.0f, 0.0f, 2, false, false);

         TEST_OUTPUT.println("transaction fees - sell(): flat rates, positive");
         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerSellTransActFee(testWare2, 48, 10.00f, 1);

         errorFound |= testerSellTransActFee(testWare3, 4, 5.00f, 2);

         errorFound |= testerSellTransActFee(testWareC1, 12, 128.00f, 3);

         TEST_OUTPUT.println("transaction fees - sell(): flat rates, negative");
         errorFound |= testerSellTransActFee(testWareC2, 12, -50.00f, 1);

         errorFound |= testerSellTransActFee(testWareC2, 24, -124.50f, 2);

         errorFound |= testerSellTransActFee(testWareP2, 7, -0.86f, 3);

         TEST_OUTPUT.println("transaction fees - sell(): percent rates, positive");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerSellTransActFee(testWareP1, 14, 0.10f, 1);

         errorFound |= testerSellTransActFee(testWare3, 13, 0.50f, 2);

         errorFound |= testerSellTransActFee(testWare1, 5, 0.95f, 3);

         TEST_OUTPUT.println("transaction fees - sell(): percent rates, negative");
         errorFound |= testerSellTransActFee(testWareP1, 5, -0.10f, 1);

         errorFound |= testerSellTransActFee(testWare2, 6, -0.50f, 2);

         errorFound |= testerSellTransActFee(testWareC1, 74, -0.24f, 3);

         TEST_OUTPUT.println("transaction fees - sell(): percent rates, positive, negative prices");
         // enable negative prices
         Config.priceFloor         = 1.0f;
         Config.priceFloorAdjusted = 0.0f;

         errorFound |= testerSellTransActFee(wareWithNegativePrice,  10,  0.10f, 1);

         errorFound |= testerSellTransActFee(wareWithNegativePrice, 100,  0.50f, 2);

         TEST_OUTPUT.println("transaction fees - sell(): percent rates, negative, negative prices");
         errorFound |= testerSellTransActFee(wareWithNegativePrice,  10, -0.10f, 1);

         errorFound |= testerSellTransActFee(wareWithNegativePrice, 100, -0.50f, 2);

         // disable negative prices
         Config.priceFloor         =  0.0f;
         Config.priceFloorAdjusted =  1.0f;

         TEST_OUTPUT.println("transaction fees - sell(): funds checking includes fees, positive");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()],
                                              Config.quanEquilibrium[testWareC1.getLevel()] / 2,
                                              1.10f, 0.0f, 1, true, false);

         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()],
                                              Config.quanEquilibrium[testWareC1.getLevel()] / 2,
                                              1.00f, 0.0f, 2, true, false);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()],
                                              Config.quanEquilibrium[testWareC1.getLevel()] / 2,
                                              10000.00f, 0.0f, 3, true, false);

         TEST_OUTPUT.println("transaction fees - sell(): funds checking includes fees, negative");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 300, 300,
                                              -0.10f, 0.0f, 1, false, false);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], 300, 300,
                                              -10.00f, 0.0f, 2, false, false);

         TEST_OUTPUT.println("transaction fees - sell(): funds checking includes fees, extremely negative");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()],
                                              64, -1.00f, 0.0f, 1, false, false);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerTradeTransActFee(testWareC1, Config.quanEquilibrium[testWareC1.getLevel()], Config.quanEquilibrium[testWareC1.getLevel()],
                                              64, -(testWareC1.getBasePrice() * 2.0f), 0.0f, 2, false, false);

         TEST_OUTPUT.println("transaction fees - sell(): changing fee applied message");
         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees       = true\n" +
               "transactionFeeSellingIsMult = true\n" +
               "transactionFeeSellingMsg    = Income tax: \n" +
               "disableAutoSaving           = true\n" +
               "crossWorldMarketplace       = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         errorFound |= testerSellTransActFee(testWareP1, 14, 0.10f, 1);

         errorFound |= testerSellTransActFee(testWare3, 13, 0.50f, 2);

         errorFound |= testerSellTransActFee(testWare1, 5, 0.95f, 3);

         TEST_OUTPUT.println("transaction fees - sell(): restoring fee applied message");
         Config.transactionFeeSellingMsg    = "   Transaction fee applied: ";
         errorFound |= testerSellTransActFee(testWareP1, 14, 0.10f, 1);

         errorFound |= testerSellTransActFee(testWare3, 13, 0.50f, 2);

         errorFound |= testerSellTransActFee(testWare1, 5, 0.95f, 3);

         TEST_OUTPUT.println("transaction fees - sell(): fee account doesn't exist");
         Config.transactionFeesAccount = "transactionFeeCollectionSell";

         // ensure account does not exist
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account1.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesShouldPutFeesIntoAccount = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerSellTransActFee(testWare1, 10, 0.10f, 0);

         // check account existence
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account1.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account1.getOwner().toString());
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("transaction fees - sell(): fee account changed");
         Config.transactionFeesShouldPutFeesIntoAccount = true;
         account1 = Account.getAccount(Config.transactionFeesAccount);
         Config.transactionFeesAccount                  = "newTransactionFeeCollectionSell";
         account2 = Account.getAccount(Config.transactionFeesAccount);

         // ensure account does not exist
         if (account2 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account2.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerSellTransActFee(testWare1, 10, 0.10f, 0);

         // check account existence
         account2 = Account.getAccount(Config.transactionFeesAccount);
         if (account2 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account2.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account2.getOwner().toString());
               errorFound = true;
            }
         }

         // reset test environment in case
         // reloading configuration made unexpected changes
         resetTestEnvironment();
         Config.chargeTransactionFees                   = true;
         Config.transactionFeesShouldPutFeesIntoAccount = true;

         // ensure fee collection account exists
         accountFeeCollection = Account.getAccount(Config.transactionFeesAccount);
         if (accountFeeCollection == null)
            accountFeeCollection = Account.makeAccount(Config.transactionFeesAccount, null);

         TEST_OUTPUT.println("transaction fees - sell(): fee account, positive rates");
         errorFound |= testerSellAccountTransActFee(testWare4,   10, 0.87f,    0.0f, 1, true);

         errorFound |= testerSellAccountTransActFee(testWare1,   99, 0.95f,  1000.0f, 2, true);

         errorFound |= testerSellAccountTransActFee(testWare1, 256, 0.14f, 10000.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - sell(): fee account, negative rates");
         errorFound |= testerSellAccountTransActFee(testWare3,    2, -0.12f,   100.0f, 1, true);

         errorFound |= testerSellAccountTransActFee(testWare1,   17, -0.23f,   100.0f, 2, true);

         errorFound |= testerSellAccountTransActFee(testWareC2, 147, -1.50f, 10000.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - sell(): fee account funds too low to pay negative rate");
         errorFound |= testerSellAccountTransActFee(testWareC1, 10, -0.10f, 10.0f, 1, true);

         Config.transactionFeeSellingIsMult= false;
         errorFound |= testerSellAccountTransActFee(testWareC1, 10, -11.0f, 10.0f, 2, true);

         TEST_OUTPUT.println("transaction fees - sell(): $0.00 wares, percent rates, positive rates");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerSellErrorMsgsTransActFee(testWare1,  10000,   1,   1, 0.0f, 0.90f, 1, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareC1, 10000,  10,  10, 0.0f, 1.20f, 2, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareP1, 10000, 100, 100, 0.0f, 0.10f, 3, true);

         TEST_OUTPUT.println("transaction fees - sell(): $0.00 wares, percent rates, negative rates");
         errorFound |= testerSellErrorMsgsTransActFee(testWare1,  10000,   1,   1, 0.0f, -0.90f, 1, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareC1, 10000,  10,  10, 0.0f, -1.20f, 2, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareP1, 10000, 100, 100, 0.0f, -0.10f, 3, true);

         TEST_OUTPUT.println("transaction fees - sell(): $0.00 wares, flat rates, positive rates");
         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerSellErrorMsgsTransActFee(testWare1,  10000,   0,   1, 0.0f, 0.90f, 1, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareC1, 10000,   0,  10, 0.0f, 1.20f, 2, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareP1, 10000,   0, 100, 0.0f, 0.10f, 3, true);

         TEST_OUTPUT.println("transaction fees - sell(): $0.00 wares, flat rates, positive rates, negative acceptable price");
         errorFound |= testerSellErrorMsgsTransActFee(testWare1,  10000,   1,   1, -100.0f, 0.90f, 1, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareC1, 10000,  10,  10, -100.0f, 1.20f, 2, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareP1, 10000, 100, 100, -100.0f, 0.10f, 3, true);

         TEST_OUTPUT.println("transaction fees - sell(): $0.00 wares, flat rates, negative rates");
         errorFound |= testerSellErrorMsgsTransActFee(testWare1,  10000,   1,   1, 0.0f, -0.90f, 1, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareC1, 10000,  10,  10, 0.0f, -1.20f, 2, true);

         errorFound |= testerSellErrorMsgsTransActFee(testWareP1, 10000, 100, 100, 0.0f, -0.10f, 3, true);


         // ensure fee collection account has sufficient wealth to pay negative fees
         accountFeeCollection.setMoney(Float.POSITIVE_INFINITY);

         TEST_OUTPUT.println("transaction fees - sellall(): when disabled");
         Config.transactionFeeSellingIsMult = true;
         Config.transactionFeeBuying        = 0.05f;
         Config.transactionFeeSending       = 0.02f;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                100, 10, 1, 0.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - sellall(): when enabled");
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                100, 10, 1, 0.10f, 0, false);

         TEST_OUTPUT.println("transaction fees - sellall(): zero rates");
         errorFound |= testerSellTransActFeeAll(testWareC1, testWare4, testWareP1,
                                                32, 16, 8, 0.0f, 1, true);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerSellTransActFeeAll(testWareC1, testWare4, testWareP1,
                                                32, 16, 8, 0.0f, 2, true);

         TEST_OUTPUT.println("transaction fees - sellall(): flat rates, positive");
         errorFound |= testerSellTransActFeeAll(testWare2, testWareP2, null,
                                                50, 25, 0, 10.0f, 1, true);

         errorFound |= testerSellTransActFeeAll(testWare1, testWareC2, null,
                                                10, 5, 0, 100.0f, 2, true);

         errorFound |= testerSellTransActFeeAll(testWare4, testWareC1, testWareP2,
                                                4, 2, 1, 5.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - sellall(): flat rates, negative");
         errorFound |= testerSellTransActFeeAll(testWareC3, testWareP1, null,
                                                32, 16, 8, -10.0f, 1, true);

         errorFound |= testerSellTransActFeeAll(testWare1, testWareC2, null,
                                                3, 5, 11, -2.75f, 2, true);

         errorFound |= testerSellTransActFeeAll(testWare4, testWareC1, testWareP2,
                                                20, 10, 40, -123.456f, 3, true);

         TEST_OUTPUT.println("transaction fees - sellall(): percent rates, positive");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerSellTransActFeeAll(testWare2, testWareP2, null,
                                                50, 25, 0, 0.50f, 1, true);

         errorFound |= testerSellTransActFeeAll(testWare1, testWareC2, null,
                                                10, 5, 0, 0.10f, 2, true);

         errorFound |= testerSellTransActFeeAll(testWare4, testWareC1, testWareP2,
                                                4, 2, 1, 0.16f, 3, true);

         TEST_OUTPUT.println("transaction fees - sellall(): percent rates, negative");
         errorFound |= testerSellTransActFeeAll(testWareC3, testWareP1, null,
                                                32, 16, 8, -0.10f, 1, true);

         errorFound |= testerSellTransActFeeAll(testWare1, testWareC2, null,
                                                3, 5, 11, -0.875f, 2, true);

         errorFound |= testerSellTransActFeeAll(testWare4, testWareC1, testWareP2,
                                                20, 10, 40, -0.25f, 3, true);

         TEST_OUTPUT.println("transaction fees - sellall(): funds checking includes fees, positive");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                300, 200, 100, 1.10f, 1, true);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                300, 200, 100, 10.0f, 2, true);

         TEST_OUTPUT.println("transaction fees - sellall(): funds checking includes fees, negative");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                300, 200, 100, -0.10f, 1, true);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                300, 200, 100, -10.0f, 2, true);

         TEST_OUTPUT.println("transaction fees - sellall(): funds checking includes fees, extremely negative");
         Config.transactionFeeSellingIsMult = true;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                300, 200, 100, -1.00f, 1, true);

         Config.transactionFeeSellingIsMult = false;
         errorFound |= testerSellTransActFeeAll(testWare1, testWare2, testWare3,
                                                300, 200, 100, -100.0f, 2, true);

         TEST_OUTPUT.println("transaction fees - sellall(): fee account doesn't exist");
         Config.transactionFeesAccount = "transactionFeeCollectionSellAll";

         // ensure account does not exist
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account1.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesShouldPutFeesIntoAccount = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerSellTransActFeeAllAccount(testWareC1, testWareP2, 10, 1,
                                                     0.10f, 0.0f, 0, false);

         // check account existence
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account1.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account1.getOwner().toString());
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("transaction fees - sellall(): fee account changed");
         account1 = Account.getAccount(Config.transactionFeesAccount);
         Config.transactionFeesAccount = "newTransactionFeeCollectionSellAll";
         account2 = Account.getAccount(Config.transactionFeesAccount);

         // ensure account does not exist
         if (account2 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account2.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesShouldPutFeesIntoAccount = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerSellTransActFeeAllAccount(testWareC2, testWareP1, 10, 10,
                                                     0.10f, 0.0f, 0, false);

         // check account existence
         account2 = Account.getAccount(Config.transactionFeesAccount);
         if (account2 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account2.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account2.getOwner().toString());
               errorFound = true;
            }
         }

         // reset test environment in case
         // reloading configuration made unexpected changes
         resetTestEnvironment();
         Config.chargeTransactionFees                   = true;
         Config.transactionFeesShouldPutFeesIntoAccount = true;

         // ensure fee collection account exists
         accountFeeCollection = Account.getAccount(Config.transactionFeesAccount);
         if (accountFeeCollection == null)
            accountFeeCollection = Account.makeAccount(Config.transactionFeesAccount, null);

         TEST_OUTPUT.println("transaction fees - sellall(): fee account, positive rates");
         errorFound |= testerSellTransActFeeAllAccount(testWareC3, testWare2, 20, 200,
                                                     0.10f, 0.0f, 1, true);

         errorFound |= testerSellTransActFeeAllAccount(testWare1, testWare4, 200, 5,
                                                     0.40f, 100.0f, 2, true);

         errorFound |= testerSellTransActFeeAllAccount(testWare3, testWareP2, 64, 12,
                                                     0.015f, 1.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - sellall(): fee account, negative rates");
         errorFound |= testerSellTransActFeeAllAccount(testWareC1, testWareP2, 8, 12,
                                                     -0.24f, 1000.0f, 1, true);

         errorFound |= testerSellTransActFeeAllAccount(testWare1, testWare2, 114, 36,
                                                     -0.50f, 10000.0f, 2, true);

         errorFound |= testerSellTransActFeeAllAccount(testWareP1, testWare4, 3, 4,
                                                     -0.95f, 100.0f, 3, true);

         TEST_OUTPUT.println("transaction fees - sellall(): fee account funds too low to pay negative rate");
         errorFound |= testerSellTransActFeeAllAccount(testWare1, null, 100, 0,
                                                     -0.10f, 5.0f, 1, true);

         errorFound |= testerSellTransActFeeAllAccount(testWare1, testWare3, 10, 32,
                                                     -0.50f, 60.0f, 2, true);

         errorFound |= testerSellTransActFeeAllAccount(testWareC3, testWareP1, 16, 8,
                                                     -0.50f, 10.0f, 3, true);


         // ensure fee collection account has sufficient wealth to pay negative fees
         accountFeeCollection.setMoney(Float.POSITIVE_INFINITY);

         TEST_OUTPUT.println("transaction fees - sendMoney(): when disabled");
         Config.transactionFeeSendingIsMult = true;
         Config.transactionFeeBuying        = 0.05f;
         Config.transactionFeeSelling       = 1.00f;
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       100.0f, 0.0f, 100.0f, 0.00f, 0);

         TEST_OUTPUT.println("transaction fees - sendMoney(): when enabled");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 0.0f, 100.0f, 0.10f, 0);

         TEST_OUTPUT.println("transaction fees - sendMoney(): zero rates");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       100.0f, 0.0f, 100.0f, 0.0f, 0);

         TEST_OUTPUT.println("transaction fees - sendMoney(): flat rates, positive");
         Config.transactionFeeSendingIsMult = false;
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 0.0f, 100.0f, 100.0f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount2",
                                       128.0f, 0.0f, 64.0f, 32.0f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount3",
                                       20.0f, 0.0f, 10.0f, 1.0f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): flat rates, negative");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 1000.0f, 100.0f, -100.0f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount2",
                                       128.0f, 1000.0f, 64.0f, -32.0f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount3",
                                       20.0f, 1000.0f, 10.0f, -1.0f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): percent rates, positive");
         Config.transactionFeeSendingIsMult = true;
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 0.0f, 100.0f, 1.0f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount2",
                                       128.0f, 0.0f, 64.0f, 0.5f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount3",
                                       20.0f, 0.0f, 10.0f, 0.1f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): percent rates, negative");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 1000.0f, 100.0f, -1.0f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount2",
                                       128.0f, 1000.0f, 64.0f, -0.5f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount3",
                                       20.0f, 1000.0f, 10.0f, -0.1f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): funds checking includes fees, positive");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       100.0f, 0.0f, 100.0f, 0.10f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount2", "testAccount1",
                                       19.0f, 0.0f, 10.0f, 1.00f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       1199.0f, 0.0f, 1000.0f, 0.20f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): funds checking includes fees, negative");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount4",
                                       50.0f, 1000.0f, 100.0f, -1.00f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount2", "testAccount1",
                                       9.0f, 1000.0f, 10.0f, -0.25f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       950.0f, 1000.0f, 1000.0f, -0.20f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): funds checking includes fees, extremely negative");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       0.0f, 10000.0f, 1000.0f, -1.00f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount2", "testAccount3",
                                       0.0f, 10000.0f, 100.0f, -10.00f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", InterfaceTerminal.playername,
                                       10000.0f, 10000.0f, 10.0f, -100.00f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): changing fee applied message");
         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees       = true\n" +
               "transactionFeeSendingIsMult = true\n" +
               "transactionFeeSendingMsg    = Transfer fee: \n" +
               "disableAutoSaving           = true\n" +
               "crossWorldMarketplace       = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 0.0f, 100.0f, 1.0f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount2",
                                       128.0f, 0.0f, 64.0f, 0.5f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount3",
                                       20.0f, 0.0f, 10.0f, 0.1f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): restoring fee applied message");
         Config.transactionFeeSendingMsg    = "   Transaction fee applied: ";

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       1000.0f, 0.0f, 100.0f, 1.0f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount2",
                                       128.0f, 0.0f, 64.0f, 0.5f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount3",
                                       20.0f, 0.0f, 10.0f, 0.1f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): fee account doesn't exist");
         Config.transactionFeesAccount = "transactionFeeCollectionSend";

         // ensure account does not exist
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account1.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesShouldPutFeesIntoAccount = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       100.0f, 0.0f, 10.0f, 1.00f, 0);

         // check account existence
         account1 = Account.getAccount(Config.transactionFeesAccount);
         if (account1 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account1.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account1.getOwner().toString());
               errorFound = true;
            }
         }

         TEST_OUTPUT.println("transaction fees - sendMoney(): fee account changed");
         Config.transactionFeesShouldPutFeesIntoAccount = true;
         account1 = Account.getAccount(Config.transactionFeesAccount);
         Config.transactionFeesAccount                  = "newTransactionFeeCollectionSend";
         account2 = Account.getAccount(Config.transactionFeesAccount);

         // ensure account does not exist
         if (account2 != null)
            Account.deleteAccount(Config.transactionFeesAccount, account2.getOwner());

         // create test config file
         try {
            // open the save file for config, create it if it doesn't exist
            fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);

            // write test wares file
            fileWriter.write(
               "// warning: this file may be cleared and overwritten by the program\n\n" +
               "chargeTransactionFees  = true\n" +
               "transactionFeesAccount = " + Config.transactionFeesAccount + "\n" +
               "accountStartingMoney   = 0.0\n"  +
               "disableAutoSaving      = true\n" +
               "crossWorldMarketplace  = true\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to change test config file");
            e.printStackTrace();
         }

         // reload config
         Config.loadConfig();

         // run test
         errorFound |= testerSendMoney(InterfaceTerminal.playername, InterfaceTerminal.playername, "testAccount1",
                                       100.0f, 0.0f, 10.0f, 1.00f, 0);

         // check account existence
         account2 = Account.getAccount(Config.transactionFeesAccount);
         if (account2 == null) {
            TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should exist when it does not");
            errorFound = true;
         }

         // check account properties
         else {
            if (account2.getOwner() != null) {
               TEST_OUTPUT.println("   account " + Config.transactionFeesAccount + " should be inaccessible, is owned by " + account2.getOwner().toString());
               errorFound = true;
            }
         }

         // reset test environment in case
         // reloading configuration made unexpected changes
         resetTestEnvironment();
         Config.chargeTransactionFees                   = true;
         Config.transactionFeesShouldPutFeesIntoAccount = true;

         // ensure fee collection account exists
         accountFeeCollection = Account.getAccount(Config.transactionFeesAccount);
         if (accountFeeCollection == null)
            accountFeeCollection = Account.makeAccount(Config.transactionFeesAccount, null);

         TEST_OUTPUT.println("transaction fees - sendMoney(): fee account, positive rates");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       1000000.0f, 0.0f, 128.0f, 0.50f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount2", "testAccount1",
                                       1000000.0f, 100.0f, 100.0f, 0.75f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount3",
                                       1000000.0f, 1000.0f, 10.0f, 1.00f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): fee account, negative rates");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       1000000.0f, 128.0f, 128.0f, -0.50f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount2", "testAccount1",
                                       1000000.0f, 80.0f, 100.0f, -0.75f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount3",
                                       1000000.0f, 100.0f, 10.0f, -1.00f, 3);

         TEST_OUTPUT.println("transaction fees - sendMoney(): fee account funds too low to pay negative rate");
         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount2",
                                       1000000.0f, 32.0f, 128.0f, -0.50f, 1);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount2", "testAccount1",
                                       1000000.0f, 74.9f, 100.0f, -0.75f, 2);

         errorFound |= testerSendMoney(InterfaceTerminal.playername, "testAccount1", "testAccount3",
                                       1000000.0f, 9.0f, 10.0f, -1.00f, 3);

         TEST_OUTPUT.println("transaction fees - check(): when disabled, without alias, singular quantity");
         Config.transactionFeeBuyingIsMult  = true;
         Config.transactionFeeSellingIsMult = true;
         Config.transactionFeeSendingIsMult = true;
         Config.transactionFeeSending       = 0.02f;

         errorFound |= testTransActFeeCheck(testWare1, 0, 0.00f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 0.00f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 0.00f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 0.00f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when enabled, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, 0.10f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when enabled, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 0.10f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when enabled, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 0.10f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when enabled, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 0.10f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for buying, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, 0.00f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for buying, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 0.00f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for buying, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 0.00f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for buying, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 0.00f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for selling, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, 0.10f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for selling, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 0.10f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for selling, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 0.10f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): when disabled for selling, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 0.10f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): zero rates, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, 0.0f, 0.0f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): zero rates, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 0.0f, 0.0f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): zero rates, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 0.0f, 0.0f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): zero rates, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 0.0f, 0.0f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, positive, without alias, singular quantity");
         Config.transactionFeeBuyingIsMult  = false;
         Config.transactionFeeSellingIsMult = false;

         errorFound |= testTransActFeeCheck(testWare1, 0, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, positive, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 10.10f, 10.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, positive, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 100.00f, 100.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, positive, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 123.456f, 123.456f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, negative, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, -0.10f, -0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, negative, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, -10.10f, -10.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, negative, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, -100.00f, -100.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, negative, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, -123.456f, -123.456f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, extremely negative, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, -1.00f, -1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, extremely negative, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, -(testWare2.getBasePrice() * 20.0f), -(testWare2.getBasePrice() * 20.0f), 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, extremely negative, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, -100.00f, -100.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): flat rates, extremely negative, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, -655.36f, -655.36f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, positive, without alias, singular quantity");
         Config.transactionFeeBuyingIsMult  = false;
         Config.transactionFeeSellingIsMult = false;

         errorFound |= testTransActFeeCheck(testWare1, 0, 0.10f, 0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, positive, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, 1.28f, 1.28f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, positive, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, positive, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, 10.00f, 10.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, negative, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, -0.10f, -0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, negative, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, -0.128f, -0.128f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, negative, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, -0.10f, -0.10f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, negative, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, -0.50f, -0.50f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, extremely negative, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, -2.56f, -2.56f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, extremely negative, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 10, -1.00f, -1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, extremely negative, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, -2.00f, -2.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): percent rates, extremely negative, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 10, -11.00f, -11.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, no quantity specified, without alias, singular quantity");
         Config.priceBuyUpchargeMult = 2.0f;

         errorFound |= testTransActFeeCheck(testWare1, 0, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, positive rate, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 0, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, negative rate, without alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare2, 1, -0.50f, -0.50f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, positive rate, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare3, 0, 1.00f, 1.00f, 1.00f, 1, true);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, negative rate, with alias, singular quantity");
         errorFound |= testTransActFeeCheck(testWare4, 1, -11.00f, -11.00f, 1.00f, 2, true);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, positive rate, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare1, 10, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, negative rate, without alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare2, 100, -0.50f, -0.50f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, positive rate, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare3, 10, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): buying upcharge, negative rate, with alias, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare4, 100, -11.00f, -11.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, zero rate, singular quantity");
         Config.priceBuyUpchargeMult = 1.0f;
         errorFound |= testTransActFeeCheck(testWareU1, 1, 0.00f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, positive rate, singular quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 1, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, negative rate, singular quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 1, -0.50f, -0.50f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, zero rate, multiple quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 100, 0.00f, 0.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, positive rate, multiple quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 100, 1.00f, 1.00f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, negative rate, multiple quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 100, -0.50f, -0.50f, 1.00f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, damaged, singular quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 1, 1.00f, 1.00f, 0.10f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): untradeable ware, damaged, multiple quantity");
         errorFound |= testTransActFeeCheck(testWareU1, 100, -0.50f, -0.50f, 0.10f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): damaged ware, zero rate, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 1, 0.00f, 0.00f, 0.10f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): damaged ware, positive rate, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 1, 1.00f, 1.00f, 0.50f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): damaged ware, negative rate, singular quantity");
         errorFound |= testTransActFeeCheck(testWare1, 1, -0.50f, -0.50f, 0.50f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): damaged ware, zero rate, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare1, 1000, 0.00f, 0.00f, 0.25f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): damaged ware, positive rate, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare1, 100, 9.00f, 9.00f, 0.01f, 0, false);

         TEST_OUTPUT.println("transaction fees - check(): damaged ware, negative rate, multiple quantity");
         errorFound |= testTransActFeeCheck(testWare1, 10, -11.00f, -11.00f, 0.10f, 0, false);
      }
      catch (Exception e) {
         Config.chargeTransactionFees = false;
         TEST_OUTPUT.println("testUnitTransactionFees() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }
      Config.chargeTransactionFees = false;

      return !errorFound;
   }

   /**
    * Tests events which affect wares' quantities for sale.
    *
    * @return whether random events passed all test cases
    */
   private static boolean testUnitRandomEvents() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.randomEvents          = true;
      Config.randomEventsFrequency = 999999999;
      Config.randomEventsVariance  = 0.0f;
      Config.filenameRandomEvents  = "config" + File.separator + "CommandEconomy" + File.separator + "testRandomEvents.json";
      File       fileRandomEvents  = new File(Config.filenameRandomEvents);
      FileWriter fileWriter;

      // prepare to grab private methods
      Method         randomEventsLoadWares;
      Method         randomEventFire;
      Class<?>       randomEventClass;
      Constructor<?> constructor;

      // prepare to grab internal variables
      Field fRandomEvents;
      Field fTimer;
      Field fTimerTask;
      // for accessing event properties
      Field fDescription;
      Field fChangedWaresIDs;
      Field fChangedMagnitudes;
      Field fChangeMagnitudesCurrent;

      // track changes to variables
      Timer        timerRandomEvents;
      RandomEvents timerTaskRandomEvents;
      Object[]     randomEvents;
      Object       testEvent1;
      Object       testEvent2;
      Object       testEvent3;
      String       descriptionScenario1;
      String       descriptionScenario2;
      String       descriptionScenario3;
      String       descriptionWareChanges1;
      String       descriptionWareChanges2;
      String       descriptionWareChanges3;
      int          quantityWare1;
      int          quantityWare2;
      int          quantityWare3;
      int          quantityWare4;

      // repeatedly-used information
      final RandomEvents.QuantityForSaleChangeMagnitudes[] MAGNITUDES_1 = new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_SMALL, RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_MEDIUM};
      final RandomEvents.QuantityForSaleChangeMagnitudes[] MAGNITUDES_2 = new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.NEGATIVE_SMALL};
      final RandomEvents.QuantityForSaleChangeMagnitudes[] MAGNITUDES_3 = new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_LARGE};
      final RandomEvents.QuantityForSaleChangeMagnitudes[] MAGNITUDES_4 = new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_LARGE, RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_MEDIUM};
      final RandomEvents.QuantityForSaleChangeMagnitudes[] MAGNITUDES_5 = new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.NEGATIVE_SMALL, RandomEvents.QuantityForSaleChangeMagnitudes.NEGATIVE_SMALL};
      final RandomEvents.QuantityForSaleChangeMagnitudes[] MAGNITUDES_6 = new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_SMALL, RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_SMALL};

      // ensure events file doesn't affect next test run
      if (fileRandomEvents.exists())
         fileRandomEvents.delete();

      try {
         // grab references to attributes
         fRandomEvents = RandomEvents.class.getDeclaredField("randomEvents");
         fRandomEvents.setAccessible(true);

         fTimerTask = RandomEvents.class.getDeclaredField("timerTaskRandomEvents");
         fTimerTask.setAccessible(true);

         // first call performs preliminary set up
         RandomEvents.startOrReconfig();


         TEST_OUTPUT.println("random events - handling missing events file");
         // initialize random events
         try {
            RandomEvents.startOrReconfig();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   startOrReconfig() should not throw any exception, but it did while loading missing events file");
            e.printStackTrace();
            errorFound = true;
         }

         // run once to finish configuration
         timerTaskRandomEvents = (RandomEvents) fTimerTask.get(null);
         timerTaskRandomEvents.run();

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents != null) {
            TEST_OUTPUT.println("   randomEvents were loaded when they shouldn't have been");
            randomEvents = null;
            errorFound = true;
         }


         TEST_OUTPUT.println("random events - handling misformatted file");
         // create test events file
         try {
            // open the save file for events, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameRandomEvents);

            // write test events file
            fileWriter.write(
               "The formatting of this file isn't even close to JSON formatting."
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // try to load the test file
         try {
            RandomEvents.load();
            timerTaskRandomEvents.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   loadRandomEvents() should not throw any exception, but it did while loading test events file");
            e.printStackTrace();
            errorFound = true;
         }

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents != null) {
            TEST_OUTPUT.println("   randomEvents were loaded when they shouldn't have been");
            randomEvents = null;
            errorFound = true;
         }


         TEST_OUTPUT.println("random events - handling when no events are loaded");
         // create test events file
         try {
            // open the save file for events, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameRandomEvents);

            // write test events file
            fileWriter.write(
               ""
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // ensure thread reference is current
         RandomEvents.startOrReconfig();
         timerTaskRandomEvents = (RandomEvents) fTimerTask.get(null);

         // try to load the test file
         try {
            RandomEvents.load();
            timerTaskRandomEvents.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   loadRandomEvents() should not throw any exception, but it did while loading test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            errorFound = true;
         }

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents != null) {
            TEST_OUTPUT.println("   randomEvents were loaded when they shouldn't have been");
            randomEvents = null;
            errorFound = true;
         }


         TEST_OUTPUT.println("random events - loading valid and invalid random events");
         // create test events file
         try {
            // open the save file for events, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameRandomEvents);

            // write test events file
            fileWriter.write(
               "[{\n" +
               "\"description\": \"This test event is perfectly fine.\",\n" +
               "\"changedWaresIDs\": [\"test:material3\", \"test:crafted2\"],\n" +
               "\"changeMagnitudes\": [POSITIVE_LARGE, NEGATIVE_MEDIUM]},{\n" +
               "\"description\": \"This test event has too few magnitudes.\",\n" +
               "\"changedWaresIDs\": [\"test:material1\", \"test:material2\"],\n" +
               "\"changeMagnitudes\": [POSITIVE_SMALL]},{\n" +
               "\"description\": \"This test event has too many magnitudes.\",\n" +
               "\"changedWaresIDs\": [\"test:crafted1\"],\n" +
               "\"changeMagnitudes\": [NEGATIVE_SMALL, POSITIVE_MEDIUM]},{\n" +
               "\"description\": \"This test event doesn't have any magnitudes.\",\n" +
               "\"changedWaresIDs\": [\"test:processed2\"]},{\n" +
               "\"description\": \"This test event doesn't affect any wares, yet has magnitudes.\",\n" +
               "\"changeMagnitudes\": [POSITIVE_LARGE, POSITIVE_LARGE]},{\n" +
               "\"description\": \"This test event is also perfectly fine.\",\n" +
               "\"changedWaresIDs\": [\"test:processed1\"],\n" +
               "\"changeMagnitudes\": [NEGATIVE_SMALL]}]\n"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // ensure thread reference is current
         RandomEvents.startOrReconfig();
         timerTaskRandomEvents = (RandomEvents) fTimerTask.get(null);

         // try to load the test file
         try {
            RandomEvents.load();
            timerTaskRandomEvents.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   loadRandomEvents() should not throw any exception, but it did while loading test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            errorFound = true;
         }

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents == null) {
            TEST_OUTPUT.println("   random events should have loaded, but randomEvents is null");
            errorFound = true;
         } else if (randomEvents.length != 2) {
            TEST_OUTPUT.println("   random events loaded: " + randomEvents.length + ", should be 2");
            errorFound = true;
         }


         TEST_OUTPUT.println("random events - loading random events referring to invalid ware IDs");
         // create test events file
         try {
            // open the save file for events, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameRandomEvents);

            // write test events file
            fileWriter.write(
               "[\n" +
               "  {\"description\":\"This is a test event.\",\"changedWaresIDs\":[\"test:material3\",\"test:invalidWareID\"],\"changeMagnitudes\":[POSITIVE_LARGE,NEGATIVE_MEDIUM]},\n" +
               "  {\"description\":\"This is also a test event.\",\"changedWaresIDs\":[\"test:anotherInvalidWareID\"],\"changeMagnitudes\":[NEGATIVE_SMALL]},\n" +
               "  {\"description\":\"This is yet another test event.\",\"changedWaresIDs\":[\"test:yetAnotherInvalidWareID\",\"minecraft:material4\"],\"changeMagnitudes\":[POSITIVE_SMALL,NEGATIVE_MEDIUM]},\n" +
               "  {\"description\":\"This is the third-to-last test event. It makes it so four test events should be loaded, instead of two, like the previous test.\",\"changedWaresIDs\":[\"test:secondToLastInvalidWareID\",\"test:crafted2\"],\"changeMagnitudes\":[POSITIVE_MEDIUM,NEGATIVE_LARGE]},\n" +
               "  {\"description\":\"This test event was included to ensure untradeable wares are being checked for.\",\"changedWaresIDs\":[\"test:untradeable1\"],\"changeMagnitudes\":[POSITIVE_MEDIUM]},\n" +
               "  {\"description\":\"This is the last test event. It makes it so four test events should be loaded, instead of two, like the previous test.\",\"changedWaresIDs\":[\"test:lastInvalidWareID\",\"test:untradeable1\",\"test:material1\"],\"changeMagnitudes\":[POSITIVE_SMALL,NEGATIVE_SMALL,POSITIVE_MEDIUM]}\n" +
               "]"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // try to load the test file
         try {
            RandomEvents.load();
            timerTaskRandomEvents.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   loadRandomEvents() should not throw any exception, but it did while loading test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            errorFound = true;
         }

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents == null) {
            TEST_OUTPUT.println("   random events should have loaded, but randomEvents is null");
            errorFound = true;
         } else if (randomEvents.length != 4) {
            TEST_OUTPUT.println("   random events loaded: " + randomEvents.length + ", should be 4");
            errorFound = true;
         }


         TEST_OUTPUT.println("random events - loading random events with invalid change magnitudes");
         // create test events file
         try {
            // open the save file for events, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameRandomEvents);

            // write test events file
            fileWriter.write(
               "[\n" +
               "  {\"description\":\"This is a test event.\",\"changedWaresIDs\":[\"test:material3\",\"test:invalidWareID\"],\"changeMagnitudes\":[3,-2]},\n" +
               "  {\"description\":\"This is also a test event.\",\"changedWaresIDs\":[\"test:anotherInvalidWareID\"],\"changeMagnitudes\":[-1]},\n" +
               "  {\"description\":\"This is yet another test event.\",\"changedWaresIDs\":[\"test:yetAnotherInvalidWareID\",\"minecraft:material4\"],\"changeMagnitudes\":[1,-2]},\n" +
               "  {\"description\":\"This is the third-to-last test event. It makes it so four test events should be loaded, instead of two, like the previous test.\",\"changedWaresIDs\":[\"test:secondToLastInvalidWareID\",\"test:crafted2\"],\"changeMagnitudes\":[2,-3]},\n" +
               "  {\"description\":\"This test event was included to ensure untradeable wares are being checked for.\",\"changedWaresIDs\":[\"test:untradeable1\"],\"changeMagnitudes\":[2]},\n" +
               "  {\"description\":\"This is the last test event. It makes it so four test events should be loaded, instead of two, like the previous test.\",\"changedWaresIDs\":[\"test:lastInvalidWareID\",\"test:untradeable1\",\"test:material1\"],\"changeMagnitudes\":[1,-1,2]}\n" +
               "]"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // try to load the test file
         try {
            RandomEvents.load();
            timerTaskRandomEvents.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   loadRandomEvents() should not throw any exception, but it did while loading test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            errorFound = true;
         }

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents != null) {
            TEST_OUTPUT.println("   random events should not have been loaded, but randomEvents has " + randomEvents.length + " entries");
            errorFound = true;
         }


         TEST_OUTPUT.println("random events - loading only valid random events");
         // create test events file
         try {
            // open the save file for events, create it if it doesn't exist
            fileWriter = new FileWriter(Config.filenameRandomEvents);

            // write test events file
            fileWriter.write(
               "[\n" +
               "  {\"description\":\"This is the first test event description.\",\"changedWaresIDs\":[\"test:material1\",\"test:material2\"],\"changeMagnitudes\":[POSITIVE_SMALL,POSITIVE_MEDIUM]},\n" +
               "  {\"description\":\"This is the second test event description.\",\"changedWaresIDs\":[\"test:crafted1\"],\"changeMagnitudes\":[NEGATIVE_SMALL]},\n" +
               "  {\"description\":\"This is the third test event description.\",\"changedWaresIDs\":[\"test:processed2\"],\"changeMagnitudes\":[NEGATIVE_LARGE]}\n" +
               "]"
            );

            // close the file
            fileWriter.close();
         } catch (Exception e) {
            TEST_OUTPUT.println("   unable to create test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return false;
         }

         // ensure thread reference is current
         RandomEvents.startOrReconfig();
         timerTaskRandomEvents = (RandomEvents) fTimerTask.get(null);

         // try to load the test file
         try {
            RandomEvents.load();
            timerTaskRandomEvents.run();
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   loadRandomEvents() should not throw any exception, but it did while loading test events file");
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            errorFound = true;
         }

         // check loaded random events
         randomEvents = (Object[]) fRandomEvents.get(null);
         if (randomEvents == null) {
            TEST_OUTPUT.println("   random events should have loaded, but randomEvents is null");
            errorFound = true;
         } else if (randomEvents.length != 3) {
            TEST_OUTPUT.println("   random events loaded: " + randomEvents.length + ", should be 3");
            errorFound = true;
         }

         // set up test environment
         // grab the RandomEvent constructor
         randomEventClass = RandomEvents.class.getDeclaredClasses()[0];
         constructor = randomEventClass.getDeclaredConstructors()[0];
         constructor.setAccessible(true);

         // initialize events
         testEvent1 = constructor.newInstance(timerTaskRandomEvents);
         testEvent2 = constructor.newInstance(timerTaskRandomEvents);
         testEvent3 = constructor.newInstance(timerTaskRandomEvents);

         // access event properties
         fDescription       = randomEventClass.getDeclaredField("description");
         fChangedWaresIDs   = randomEventClass.getDeclaredField("changedWaresIDs");
         fChangedMagnitudes = randomEventClass.getDeclaredField("changeMagnitudes");
         fDescription.setAccessible(true);
         fChangedWaresIDs.setAccessible(true);
         fChangedMagnitudes.setAccessible(true);

         // set event properties
         fDescription.set(testEvent1, "This is the first test event description.");
         fDescription.set(testEvent2, "This is the second test event description.");
         fDescription.set(testEvent3, "This is the third test event description.");
         fChangedWaresIDs.set(testEvent1, new String[]{"test:material1", "test:material2"});
         fChangedWaresIDs.set(testEvent2, new String[]{"test:crafted1"});
         fChangedWaresIDs.set(testEvent3, new String[]{"test:processed2"});
         fChangedMagnitudes.set(testEvent1, MAGNITUDES_1);
         fChangedMagnitudes.set(testEvent2, MAGNITUDES_2);
         fChangedMagnitudes.set(testEvent3, MAGNITUDES_3);

         // grab the random event loader/validator
         randomEventsLoadWares = RandomEvents.class.getDeclaredMethod("loadWaresPrivate");
         randomEventsLoadWares.setAccessible(true);

         // add test events to random events array
         fRandomEvents.set(NULL_OBJECT, Array.newInstance(fRandomEvents.getType().getComponentType(), 3));
         randomEvents    = (Object[]) fRandomEvents.get(null);
         randomEvents[0] = testEvent1;
         randomEvents[1] = testEvent2;
         randomEvents[2] = testEvent3;

         // finish setting up events
         randomEventsLoadWares.invoke(NULL_OBJECT, NULL_OBJECTS);

         // grab the RandomEvent event firer
         randomEventFire = randomEventClass.getDeclaredMethod("fire");
         randomEventFire.setAccessible(true);


         TEST_OUTPUT.println("random events - printing scenarios only");
         Config.randomEventsPrintChanges = false;
         descriptionScenario1 = "This is the first test event description." + System.lineSeparator();
         descriptionScenario2 = "This is the second test event description." + System.lineSeparator();
         descriptionScenario3 = "This is the third test event description." + System.lineSeparator();

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario1)) {
            TEST_OUTPUT.println("    unexpected scenario for test event 1: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario2)) {
            TEST_OUTPUT.println("    unexpected scenario for test event 2: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario3)) {
            TEST_OUTPUT.println("    unexpected scenario for test event 3: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - printing scenarios and ware changes");
         Config.randomEventsPrintChanges = true; // enable printing ware changes

         // grab events from thread
         randomEvents = (Object[]) fRandomEvents.get(null);
         testEvent1 = randomEvents[0];
         testEvent2 = randomEvents[1];
         testEvent3 = randomEvents[2];

         // set event properties
         fDescription.set(testEvent1, "This is the first test event description.");
         fDescription.set(testEvent2, "This is the second test event description.");
         fDescription.set(testEvent3, "This is the third test event description.");
         fChangedWaresIDs.set(testEvent1, new String[]{"test:material1", "test:material2"});
         fChangedWaresIDs.set(testEvent2, new String[]{"test:crafted1", "test:material3"});
         fChangedWaresIDs.set(testEvent3, new String[]{"test:processed2", "minecraft:material4"});
         fChangedMagnitudes.set(testEvent1, MAGNITUDES_4);
         fChangedMagnitudes.set(testEvent2, MAGNITUDES_5);
         fChangedMagnitudes.set(testEvent3, MAGNITUDES_6);

         // generate messages for ware changes
         RandomEvents.loadWares();
         RandomEvents.generateWareChangeDescriptions();
         timerTaskRandomEvents.run();

         //   +++ --> \u001b[1m\u001b[32m
         //    ++ --> \u001b[32m
         //     + --> \u001b[32;1m
         //     - --> \u001b[31;1m
         //    -- --> \u001b[31m
         //   --- --> \u001b[1m\u001b[31m
         // reset --> \u001b[0m\n

         descriptionWareChanges1 = "\u001b[1m\u001b[32m+++test:material1\u001b[0m\n\u001b[32m++test:material2\u001b[0m\n" + System.lineSeparator();
         descriptionWareChanges2 = "\u001b[31;1m-craft1, mat3\u001b[0m\n" + System.lineSeparator();
         descriptionWareChanges3 = "\u001b[32;1m+test:processed2, material4\u001b[0m\n" + System.lineSeparator();

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario1 + descriptionWareChanges1)) {
            TEST_OUTPUT.println("    unexpected descriptions for test event 1: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario2 + descriptionWareChanges2)) {
            TEST_OUTPUT.println("    unexpected descriptions for test event 2: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario3 + descriptionWareChanges3)) {
            TEST_OUTPUT.println("    unexpected descriptions for test event 3: " + baosOut.toString());
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - toggling printing scenarios only by reloading configuration");
         // turn printing changes off
         Config.randomEventsPrintChanges = true;

         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEventsPrintChanges = false\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // grab the new timertask since frequency was changed
         timerTaskRandomEvents = (RandomEvents) fTimerTask.get(null);

         // test printing after disabling printing changes
         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario1)) {
            TEST_OUTPUT.println("    unexpected scenario for test event 1: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario2)) {
            TEST_OUTPUT.println("    unexpected scenario for test event 2: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario3)) {
            TEST_OUTPUT.println("    unexpected scenario for test event 3: " + baosOut.toString());
            errorFound = true;
         }

         // turn printing changes on
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEventsPrintChanges = true\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // test printing after enabling printing changes
         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario1 + descriptionWareChanges1)) {
            TEST_OUTPUT.println("    unexpected descriptions for test event 1: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario2 + descriptionWareChanges2)) {
            TEST_OUTPUT.println("    unexpected descriptions for test event 2: " + baosOut.toString());
            errorFound = true;
         }

         baosOut.reset(); // clear buffer holding console output
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         if (!baosOut.toString().equals(CommandEconomy.MSG_RANDOM_EVENT_OCCURRED + descriptionScenario3 + descriptionWareChanges3)) {
            TEST_OUTPUT.println("    unexpected descriptions for test event 3: " + baosOut.toString());
            errorFound = true;
         }

         // set events to known values
         testEvent1 = constructor.newInstance(timerTaskRandomEvents);
         testEvent2 = constructor.newInstance(timerTaskRandomEvents);
         testEvent3 = constructor.newInstance(timerTaskRandomEvents);

         // set event properties
         fDescription.set(testEvent1, "This is the first test event description.");
         fDescription.set(testEvent2, "This is the second test event description.");
         fDescription.set(testEvent3, "This is the third test event description.");
         fChangedWaresIDs.set(testEvent1, new String[]{"test:material1", "test:material2"});
         fChangedWaresIDs.set(testEvent2, new String[]{"test:crafted1"});
         fChangedWaresIDs.set(testEvent3, new String[]{"test:processed2"});
         fChangedMagnitudes.set(testEvent1, MAGNITUDES_1);
         fChangedMagnitudes.set(testEvent2, MAGNITUDES_2);
         fChangedMagnitudes.set(testEvent3, MAGNITUDES_3);

         // add test events to random events array
         fRandomEvents.set(NULL_OBJECT, Array.newInstance(fRandomEvents.getType().getComponentType(), 3));
         randomEvents    = (Object[]) fRandomEvents.get(null);
         randomEvents[0] = testEvent1;
         randomEvents[1] = testEvent2;
         randomEvents[2] = testEvent3;

         // finish setting up events
         randomEventsLoadWares.invoke(NULL_OBJECT, NULL_OBJECTS);


         TEST_OUTPUT.println("random events - positive flat rate for ware changes");
         // set up ware changes
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEventsAreChangesPercents = false\n" +
            "randomEventsLargeChange = 15\n" +
            "randomEventsMediumChange = 10\n" +
            "randomEventsSmallChange = 5\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         fRandomEvents.set(NULL_OBJECT, null); // prevent firing an event immediately, then hanging while waiting for randomEventsFrequency minutes to pass
         timerTaskRandomEvents.run();

         // set up expected results
         quantityWare1 = testWare1.getQuantity() + (int) (5.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWare1.getLevel()]);
         quantityWare2 = testWare2.getQuantity() + (int) (10.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWare2.getLevel()]);
         quantityWare3 = testWareC1.getQuantity() - (int) (5.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWareC1.getLevel()]);
         quantityWare4 = testWareP2.getQuantity() + (int) (15.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWareP2.getLevel()]);

         // fire events
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // test quantities
         if (testWare1.getQuantity() != quantityWare1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() != quantityWare2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() != quantityWare4) {
            TEST_OUTPUT.println("   unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be " + quantityWare4);
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - negative flat rate for ware changes");
         // set up ware changes
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEventsAreChangesPercents = false\n" +
            "randomEventsLargeChange = -4\n" +
            "randomEventsMediumChange = -8\n" +
            "randomEventsSmallChange = -16\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         timerTaskRandomEvents.run();

         // set up expected results
         quantityWare1 = testWare1.getQuantity() - (int) (16.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWare1.getLevel()]);
         quantityWare2 = testWare2.getQuantity() - (int) (8.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWare2.getLevel()]);
         quantityWare3 = testWareC1.getQuantity() + (int) (16.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWareC1.getLevel()]);
         quantityWare4 = testWareP2.getQuantity() - (int) (4.0f / Config.quanEquilibrium[2] * Config.quanEquilibrium[testWareP2.getLevel()]);

         // fire events
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // test quantities
         if (testWare1.getQuantity() != quantityWare1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() != quantityWare2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() != quantityWare4) {
            TEST_OUTPUT.println("   unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be " + quantityWare4);
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - positive percentage rate for ware changes");
         // set up ware changes
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEventsAreChangesPercents = true\n" +
            "randomEventsLargeChange = 0.30\n" +
            "randomEventsMediumChange = 0.20\n" +
            "randomEventsSmallChange = 0.10\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         timerTaskRandomEvents.run();

         // set up expected results
         quantityWare1 = testWare1.getQuantity() + (int) (Config.quanEquilibrium[testWare1.getLevel()] * 0.10f);
         quantityWare2 = testWare2.getQuantity() + (int) (Config.quanEquilibrium[testWare2.getLevel()] * 0.20f);
         quantityWare3 = testWareC1.getQuantity() - (int) (Config.quanEquilibrium[testWareC1.getLevel()] * 0.10f);
         quantityWare4 = testWareP2.getQuantity() + (int) (Config.quanEquilibrium[testWareP2.getLevel()] * 0.30f);

         // fire events
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // test quantities
         if (testWare1.getQuantity() != quantityWare1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() != quantityWare2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() != quantityWare4) {
            TEST_OUTPUT.println("   unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be " + quantityWare4);
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - negative percentage rate for ware changes");
         // set up ware changes
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEventsAreChangesPercents = true\n" +
            "randomEventsLargeChange = -0.08\n" +
            "randomEventsMediumChange = -0.16\n" +
            "randomEventsSmallChange = -0.32\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         timerTaskRandomEvents.run();

         // set up expected results
         quantityWare1 = testWare1.getQuantity()  - (int) (Config.quanEquilibrium[testWare1.getLevel()]  * 0.32f);
         quantityWare2 = testWare2.getQuantity()  - (int) (Config.quanEquilibrium[testWare2.getLevel()]  * 0.16f);
         quantityWare3 = testWareC1.getQuantity() + (int) (Config.quanEquilibrium[testWareC1.getLevel()] * 0.32);
         quantityWare4 = testWareP2.getQuantity() - (int) (Config.quanEquilibrium[testWareP2.getLevel()] * 0.08f);

         // fire events
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // test quantities
         if (testWare1.getQuantity() != quantityWare1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() != quantityWare2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() != quantityWare4) {
            TEST_OUTPUT.println("   unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be " + quantityWare4);
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - reloading wares");
         // set test wares to equilibrium
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()]);
         testWare2.setQuantity(Config.quanEquilibrium[testWare2.getLevel()]);
         testWareC1.setQuantity(Config.quanEquilibrium[testWareC1.getLevel()]);
         testWareP2.setQuantity(Config.quanEquilibrium[testWareP2.getLevel()]);

         // save wares to write current state to file
         Config.filenameWaresSave = "config" + File.separator + "CommandEconomy" + File.separator + "testWaresSaved.txt"; // don't overwrite user saves
         Marketplace.saveWares();

         // set up expected end results
         quantityWare1 = testWare1.getQuantity()  - (int) (Config.quanEquilibrium[testWare1.getLevel()]  * 0.32f);
         quantityWare2 = testWare2.getQuantity()  - (int) (Config.quanEquilibrium[testWare2.getLevel()]  * 0.16f);
         quantityWare3 = testWareC1.getQuantity() + (int) (Config.quanEquilibrium[testWareC1.getLevel()] * 0.32);
         quantityWare4 = testWareP2.getQuantity() - (int) (Config.quanEquilibrium[testWareP2.getLevel()] * 0.08f);

         // add test events to random events array
         fRandomEvents.set(NULL_OBJECT, Array.newInstance(fRandomEvents.getType().getComponentType(), 3));
         randomEvents    = (Object[]) fRandomEvents.get(null);
         randomEvents[0] = testEvent1;
         randomEvents[1] = testEvent2;
         randomEvents[2] = testEvent3;

         // trigger events twice to greatly change current state
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // ensure ware quantities have changed
         if (testWare1.getQuantity() >= quantityWare1) {
            TEST_OUTPUT.println("   (before reload) unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be less than " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() >= quantityWare2) {
            TEST_OUTPUT.println("   (before reload) unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be less than " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() <= quantityWare3) {
            TEST_OUTPUT.println("   (before reload) unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be greater than " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() >= quantityWare4) {
            TEST_OUTPUT.println("   (before reload) unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be less than " + quantityWare4);
            errorFound = true;
         }

         // prevent any randomly selected random events from messing up data
         fChangedMagnitudes.set(testEvent1, new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.NONE, RandomEvents.QuantityForSaleChangeMagnitudes.NONE});
         fChangedMagnitudes.set(testEvent2, new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.NONE});
         fChangedMagnitudes.set(testEvent3, new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.NONE});

         // reload wares to reset to equilibrium
         Marketplace.loadWares();
         Config.randomEventsFrequency = 1; // prevent hanging
         timerTaskRandomEvents.run();
         Config.randomEventsFrequency = 99999; // prevent thread from interfering

         // restore event magnitudes
         fChangeMagnitudesCurrent = randomEventClass.getDeclaredField("changeMagnitudesCurrent");
         fChangeMagnitudesCurrent.set(testEvent1, MAGNITUDES_1);
         fChangeMagnitudesCurrent.set(testEvent2, MAGNITUDES_2);
         fChangeMagnitudesCurrent.set(testEvent3, MAGNITUDES_3);

         // trigger events once to test relinking
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // relink wares
         testWare1  = wares.get("test:material1");
         testWare2  = wares.get("test:material2");
         testWareC1 = wares.get("test:crafted1");
         testWareP2 = wares.get("test:processed2");

         // check ware quantities
         if (testWare1.getQuantity() != quantityWare1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() != quantityWare2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() != quantityWare4) {
            TEST_OUTPUT.println("   unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be " + quantityWare4);
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - reloading with changed marketplace settings");
         // set up ware changes
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "quanEquilibrium = 192, 96, 48, 24,12, 6\n" +
            "randomEventsAreChangesPercents = true\n" +
            "randomEventsLargeChange = 0.30\n" +
            "randomEventsMediumChange = 0.20\n" +
            "randomEventsSmallChange = 0.10\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 99999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
         Config.randomEventsFrequency = 1; // prevent hanging
         timerTaskRandomEvents.run();
         Config.randomEventsFrequency = 99999; // prevent thread from interfering

         // paranoidly check changing equilibrium quantity
         if (Config.quanEquilibrium[0] != 192 || Config.quanEquilibrium[1] != 96 || Config.quanEquilibrium[2] != 48 ||
            Config.quanEquilibrium[3] != 24 || Config.quanEquilibrium[4] != 12 || Config.quanEquilibrium[5] != 6) {
            TEST_OUTPUT.println("   failed to change configuration settings!");
            errorFound = true;
         }

         // set up expected results
         quantityWare1 = testWare1.getQuantity() + (int) (Config.quanEquilibrium[testWare1.getLevel()] * 0.10f);
         quantityWare2 = testWare2.getQuantity() + (int) (Config.quanEquilibrium[testWare2.getLevel()] * 0.20f);
         quantityWare3 = testWareC1.getQuantity() - (int) (Config.quanEquilibrium[testWareC1.getLevel()] * 0.10f);
         quantityWare4 = testWareP2.getQuantity() + (int) (Config.quanEquilibrium[testWareP2.getLevel()] * 0.30f);

         // fire events
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);
         randomEventFire.invoke(testEvent2, NULL_OBJECTS);
         randomEventFire.invoke(testEvent3, NULL_OBJECTS);

         // test quantities
         if (testWare1.getQuantity() != quantityWare1) {
            TEST_OUTPUT.println("   unexpected quantity for testWare1: " + testWare1.getQuantity() + ", should be " + quantityWare1);
            errorFound = true;
         }
         if (testWare2.getQuantity() != quantityWare2) {
            TEST_OUTPUT.println("   unexpected quantity for testWare2: " + testWare2.getQuantity() + ", should be " + quantityWare2);
            errorFound = true;
         }
         if (testWareC1.getQuantity() != quantityWare3) {
            TEST_OUTPUT.println("   unexpected quantity for testWareC1: " + testWareC1.getQuantity() + ", should be " + quantityWare3);
            errorFound = true;
         }
         if (testWareP2.getQuantity() != quantityWare4) {
            TEST_OUTPUT.println("   unexpected quantity for testWareP2: " + testWareP2.getQuantity() + ", should be " + quantityWare4);
            errorFound = true;
         }

         TEST_OUTPUT.println("random events - toggling feature by reloading configuration");
         fTimer = RandomEvents.class.getDeclaredField("timerRandomEvents");
         fTimer.setAccessible(true);
         timerRandomEvents = (Timer) fTimer.get(null);

         // write to config file to turn off feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEvents = false\n" +
            "randomEventsFrequency = 9999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // attempt to turn off the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // check whether the feature is disabled
         timerRandomEvents = (Timer) fTimer.get(null);
         if (timerRandomEvents != null) {
            TEST_OUTPUT.println("   feature did not turn off when it should have");
            errorFound = true;
         }

         // write to config file to turn on feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEvents = true\n" +
            "randomEventsFrequency = 9999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // attempt to turn on the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // check whether the feature is enabled
         timerRandomEvents = (Timer) fTimer.get(null);
         if (timerRandomEvents == null) {
            TEST_OUTPUT.println("   feature did not turn on when it should have");
            errorFound = true;
         }

         // turn off feature before exiting
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "randomEvents = false\n" +
            "randomEventsFrequency = 9999\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});
      }
      catch (Exception e) {
         TEST_OUTPUT.println("random events - fatal error: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return false;
      }

      return !errorFound;
   }

   /**
    * Tests specific cross-interactions between features.
    *
    * @return whether cross-interactions passed all test cases
    */
   private static boolean testUnitCrossInteractions() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      // prepare to grab private methods
      Method randomEventsLoadWares;
      Method randomEventFire;

      // prepare to grab internal variables
      Field fRandomEvents;
      Field fQuanChangeMedium;
      Field fQuanChangeSmall;

      // track changes to variables
      Object[] randomEvents;

      // set up testing variables
      Account  feeCollectionAccount;

      AI       ai;
      String[] tradeablesIDs; // IDS for wares an AI may buy or sell
      HashMap<Ware, Integer> tradesPending = new HashMap<Ware, Integer>(); // contains AI trade decisions before finalization

      int[]    quanChangeMedium;
      int[]    quanChangeSmall;

      Ware     ware1;
      Ware     ware2;
      int      quantityToTrade;
      int      quantityToOffer;
      int      quantityWare1;
      int      quantityWare2;
      float    price1;
      float    price2;
      float    money;
      byte     level;

      try {
         // enable transaction fees and set to known values
         Config.chargeTransactionFees       = true;
         Config.transactionFeeBuying        = 0.10f;
         Config.transactionFeeSelling       = 0.50f;
         Config.transactionFeeBuyingIsMult  = true;
         Config.transactionFeeSellingIsMult = true;

         // grab the fee collection account
         feeCollectionAccount = Account.getAccount(Config.transactionFeesAccount);
         if (feeCollectionAccount == null)
            feeCollectionAccount = Account.makeAccount(Config.transactionFeesAccount, null);

         TEST_OUTPUT.println("Cross Interactions - Transaction Fees: AI, positive fees");
         // set up test variables
         ware1 = testWare1;
         Config.aiTradeQuantityPercent = 0.10f;
         AI.calcTradeQuantities();

         // enable AI and set to known values
         tradeablesIDs = new String[]{ware1.getWareID()};
         ai = new AI("testAI", tradeablesIDs, null, null);
         quantityToTrade = (int) (Config.aiTradeQuantityPercent * Config.quanEquilibrium[ware1.getLevel()]);

         // set up test conditions
         feeCollectionAccount.setMoney(0.0f);
         ware1.setQuantity(Config.quanEquilibrium[ware1.getLevel()]);

         // set up test oracles
         Config.chargeTransactionFees = false;
         price1 = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY) * Config.transactionFeeBuying;
         Config.chargeTransactionFees = true;

         // perform action
         ai.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         // check fees
         price2 = feeCollectionAccount.getMoney();
         if (price1 + FLOAT_COMPARE_PRECISION < price2 ||
             price2 < price1 - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected fee: " + price2 + ", should be " + price1 +
                                "\n      diff: " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Transaction Fees: AI, negative fees");
         // set up test conditions
         Config.transactionFeeBuying = -0.10f;
         feeCollectionAccount.setMoney(10000.0f);
         ware1.setQuantity(Config.quanEquilibrium[ware1.getLevel()]);

         // set up test oracles
         Config.chargeTransactionFees = false;
         price1 = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY) * Config.transactionFeeBuying;
         Config.chargeTransactionFees = true;

         // perform action
         ai.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         // check fees
         price2 = feeCollectionAccount.getMoney() - 10000.0f;
         if (price1 + FLOAT_COMPARE_PRECISION < price2 ||
             price2 < price1 - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected fee: " + price2 + ", should be " + price1 +
                                "\n      diff: " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Transaction Fees: Manufacturing Contracts, positive fees");
         Config.transactionFeeBuying = 0.10f;
         // enable manufacturing contracts and set to known values
         Config.buyingOutOfStockWaresAllowed   = true;
         Config.buyingOutOfStockWaresPriceMult = 1.10f;

         // set up test conditions
         quantityToTrade = 10;
         ware1           = testWareP1;
         ware2           = testWare1;
         quantityWare1   = 0;
         quantityWare2   = Config.quanEquilibrium[ware2.getLevel()];
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);
         feeCollectionAccount.setMoney(0.0f);
         playerAccount.setMoney(10000.0f);

         // set up test oracles
         Config.chargeTransactionFees = false;
         price1 = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY)
                  * Config.transactionFeeBuying;
         Config.chargeTransactionFees = true;

         // perform action
         Marketplace.buy(PLAYER_ID, null, null, ware1.getWareID(), quantityToTrade, 0.0f, 1.0f);

         // check fee
         price2 = feeCollectionAccount.getMoney();
         if (price1 + FLOAT_COMPARE_PRECISION < price2 ||
             price2 < price1 - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected price: " + price2 + ", should be " + price1 +
                               "\n      diff: " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Transaction Fees: Manufacturing Contracts, negative fees");
         Config.transactionFeeBuying = -0.10f;

         // set up test conditions
         quantityToTrade = 10;
         ware1           = testWareP1;
         ware2           = testWare1;
         quantityWare1   = 0;
         quantityWare2   = Config.quanEquilibrium[ware2.getLevel()];
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);
         feeCollectionAccount.setMoney(10000.0f);
         playerAccount.setMoney(10000.0f);

         // set up test oracles
         Config.chargeTransactionFees = false;
         price1 = feeCollectionAccount.getMoney() + // plus since fee multiplier is negative, so fee collection account should pay out money
                  (Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade, Marketplace.PriceType.CURRENT_BUY)
                   * Config.transactionFeeBuying);
         Config.chargeTransactionFees = true;

         // perform action
         Marketplace.buy(PLAYER_ID, null, null, ware1.getWareID(), quantityToTrade, 0.0f, 1.0f);

         // check fee
         price2 = feeCollectionAccount.getMoney();
         if (price1 + FLOAT_COMPARE_PRECISION < price2 ||
             price2 < price1 - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected price: " + price2 + ", should be " + price1 +
                               "\n      diff: " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Transaction Fees: Research, positive fees");
         Config.transactionFeeBuying      = 0.10f;
         Config.transactionFeeResearching = 0.10f;

         // enable industrial research and set to known values
         Config.researchCostPerHierarchyLevel = 1000.0f;
         Config.researchCostIsAMultOfAvgPrice = false;

         // set up test conditions
         ware1         = testWare2;
         quantityWare1 = Config.quanEquilibrium[ware1.getLevel()];
         ware1.setQuantity(quantityWare1);
         feeCollectionAccount.setMoney(0.0f);
         playerAccount.setMoney(100000.0f);

         // set up test oracles
         price1 = Marketplace.getPrice(PLAYER_ID, ware1, 1, Marketplace.PriceType.CURRENT_SELL)
                  * ware1.getLevel()
                  * Config.researchCostPerHierarchyLevel
                  * Config.transactionFeeResearching;

         // perform action
         InterfaceTerminal.serviceRequestResearch(new String[]{ware1.getWareID()});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         // check fee
         price2 = feeCollectionAccount.getMoney();
         if (price1 + FLOAT_COMPARE_PRECISION < price2 ||
             price2 < price1 - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected fee: " + price2 + ", should be " + price1 +
                               "\n      diff: " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Transaction Fees: Research, negative fees");
         Config.transactionFeeResearching = -0.10f;

         // set up test conditions
         ware1         = testWare2;
         quantityWare1 = Config.quanEquilibrium[ware1.getLevel()];
         ware1.setQuantity(quantityWare1);
         feeCollectionAccount.setMoney(0.0f);
         playerAccount.setMoney(10000.0f);

         // set up test oracles
         price1 = Marketplace.getPrice(PLAYER_ID, ware1, 1, Marketplace.PriceType.CURRENT_SELL)
                  * ware1.getLevel()
                  * Config.researchCostPerHierarchyLevel
                  * Config.transactionFeeResearching;

         // perform action
         InterfaceTerminal.serviceRequestResearch(new String[]{ware1.getWareID()});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         // check fee
         price2 = feeCollectionAccount.getMoney();
         if (price1 + FLOAT_COMPARE_PRECISION < price2 ||
             price2 < price1 - FLOAT_COMPARE_PRECISION) {
            TEST_OUTPUT.println("   unexpected fee: " + price2 + ", should be " + price1 +
                               "\n      diff: " + (price2 - price1));
            errorFound = true;
         }

         // disable transaction fees to avoid possible interference
         Config.chargeTransactionFees = false;

         // enable linked prices and set to known values
         Config.shouldComponentsCurrentPricesAffectWholesPrice = true;
         Config.linkedPricesPercent   = 0.50f;

         TEST_OUTPUT.println("Cross Interactions - Linked Prices: Research, surplus");
         // enable industrial research and set to known values
         Config.researchCostPerHierarchyLevel = 1000.0f;
         Config.researchCostIsAMultOfAvgPrice = false;

         // set up test conditions for equilibrium
         quantityToTrade = 10;
         ware1           = testWareP1;
         ware2           = testWare1;
         quantityWare1   = 0;
         quantityWare2   = Config.quanEquilibrium[ware2.getLevel()];

         money           = 100000.0f;
         level           = ware1.getLevel(); // for resetting the ware's level later on
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);
         playerAccount.setMoney(money);

         // perform action
         InterfaceTerminal.serviceRequestResearch(new String[]{ware1.getWareID()});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         // get the price when component is at equilibrium
         price1 = money - playerAccount.getMoney();

         // set up test conditions for surplus
         quantityWare2 = Config.quanExcessive[ware2.getLevel()];
         ware1.setLevel(level);
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);
         playerAccount.setMoney(money);

         // perform action
         InterfaceTerminal.serviceRequestResearch(new String[]{ware1.getWareID()});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         // get the price when component is in surplus
         price2 = money - playerAccount.getMoney();

         // compare prices
         if (price1 <= price2) {
            TEST_OUTPUT.println("   unexpected price comparison: price during surplus should be lower than price during equilibrium" +
                               "\n      surplus:     " + price2 +
                               "\n      equilibrium: " + price1 +
                               "\n      diff:        " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Linked Prices: Research, scarcity");
         // set up test conditions for scarcity
         quantityWare2 = Config.quanDeficient[ware2.getLevel()];
         ware1.setLevel(level);
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);
         playerAccount.setMoney(money);

         // perform action
         InterfaceTerminal.serviceRequestResearch(new String[]{ware1.getWareID()});
         InterfaceTerminal.serviceRequestResearch(new String[]{"yes"});

         // get the price when component has scarcity
         price2 = money - playerAccount.getMoney();

         // compare prices
         if (price1 >= price2) {
            TEST_OUTPUT.println("   unexpected price comparison: price during scarcity should be higher than price during equilibrium" +
                               "\n      scarcity:    " + price2 +
                               "\n      equilibrium: " + price1 +
                               "\n      diff:        " + (price2 - price1));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Linked Prices: Planned Economy, surplus");
         Config.pricesIgnoreSupplyAndDemand = true; // enable planned economy

         // testWareP1 is made from testWare1
         // set both wares to surplus, then store their price
         testWareP1.setQuantity(Config.quanExcessive[testWareP1.getLevel()] / 2);
         testWare1.setQuantity(Config.quanExcessive[testWare1.getLevel()]);
         price1 = Marketplace.getPrice(PLAYER_ID, testWareP1, 1, Marketplace.PriceType.CURRENT_SELL);

         // test selling some testWareP1 and checking its price
         errorFound |= testPETrade(testWareP1, Config.quanExcessive[testWareP1.getLevel()] / 2, 10,
                                   1.0f, 1.0f, 0, false, true);

         // grab testWareP1's price after selling some
         price2 = Marketplace.getPrice(PLAYER_ID, testWareP1, 1, Marketplace.PriceType.CURRENT_SELL);

         // the prices before the quantity changes should be equal to the prices after
         if (price1 != price2) {
            TEST_OUTPUT.println("   unexpected price after quantity changes: " + price2 + ", should be " + price1);
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - Linked Prices: Planned Economy, scarcity");

         // testWareP1 is made from testWare1
         // set both wares to surplus, then store their price
         testWareP1.setQuantity(Config.quanDeficient[testWareP1.getLevel()]);
         testWare1.setQuantity(Config.quanDeficient[testWare1.getLevel()]);
         price1 = Marketplace.getPrice(PLAYER_ID, testWareP1, 1, Marketplace.PriceType.CURRENT_SELL);

         // test selling some testWareP1 and checking its price
         errorFound |= testPETrade(testWareP1, Config.quanDeficient[testWareP1.getLevel()], 8,
                                   1.0f, 1.0f, 0, false, true);

         // grab testWareP1's price after selling some
         price2 = Marketplace.getPrice(PLAYER_ID, testWareP1, 1, Marketplace.PriceType.CURRENT_SELL);

         // the prices before the quantity changes should be equal to the prices after
         if (price1 != price2) {
            TEST_OUTPUT.println("   unexpected price after quantity changes: " + price2 + ", should be " + price1);
            errorFound = true;
         }

         // disable features to avoid possible interference
         Config.shouldComponentsCurrentPricesAffectWholesPrice = false;
         Config.pricesIgnoreSupplyAndDemand = false;

         // enable no garbage disposing for testing
         Config.noGarbageDisposing = true;

         // set up AI for testing
         tradeablesIDs = new String[]{testWare1.getWareID()};
         ai = new AI("testAI", null, tradeablesIDs, null);

         TEST_OUTPUT.println("Cross Interactions - No Garbage Disposing: AI, selling past price floor");
         // set up test conditions
         ware1           = testWare1;
         quantityToOffer = (int) (Config.aiTradeQuantityPercent * Config.quanEquilibrium[ware1.getLevel()]);
         quantityToTrade = quantityToOffer / 2;
         quantityWare1   = Config.quanExcessive[ware1.getLevel()] - quantityToTrade - 1;
         ware1.setQuantity(quantityWare1);

         // perform action
         ai.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         // check ware properties
         if (ware1.getQuantity() != quantityWare1 + quantityToTrade) {
            TEST_OUTPUT.println("   unexpected quantity: " + ware1.getQuantity() +
                                ", should be " + (quantityWare1 + quantityToTrade) +
                                "\n   quantity traded:  " + (ware1.getQuantity() - quantityWare1) +
                                "\n   trade expected:   " + quantityToTrade +
                                "\n   quantity before trade: " + quantityWare1);
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - No Garbage Disposing: AI, selling at price floor");
         // set up test conditions
         quantityToTrade =  0;
         quantityToOffer = 20;
         quantityWare1   = Config.quanExcessive[ware1.getLevel()] - 1;
         ware1.setQuantity(quantityWare1);

         // perform action
         ai.trade(tradesPending);
         AI.finalizeTrades(tradesPending);

         // check ware properties
         if (ware1.getQuantity() != quantityWare1 + quantityToTrade) {
            TEST_OUTPUT.println("   unexpected quantity: " + ware1.getQuantity() +
                                ", should be " + (quantityWare1 + quantityToTrade) +
                                "\n   quantity traded:  " + (ware1.getQuantity() - quantityWare1) +
                                "\n   trade expected:   " + quantityToTrade +
                                "\n   quantity before trade: " + quantityWare1);
            errorFound = true;
         }

         // set up random events for testing
         // grab the RandomEvent constructor
         Class<?> randomEventClass = RandomEvents.class.getDeclaredClasses()[0];
         Constructor<?> randomEvent = randomEventClass.getDeclaredConstructors()[0];
         randomEvent.setAccessible(true);

         // initialize event
         Object testEvent1 = randomEvent.newInstance(NULL_OBJECT);

         // make event properties accessible
         Field fDescription       = randomEventClass.getDeclaredField("description"); // description is needed to appease event loader/validator
         Field fChangedWaresIDs   = randomEventClass.getDeclaredField("changedWaresIDs");
         Field fChangedMagnitudes = randomEventClass.getDeclaredField("changeMagnitudes");
         fDescription.setAccessible(true);
         fChangedWaresIDs.setAccessible(true);
         fChangedMagnitudes.setAccessible(true);

         // set event properties
         fDescription.set(testEvent1, "This is the first test event description.");
         fChangedWaresIDs.set(testEvent1, new String[]{"test:material1", "test:material2"});
         fChangedMagnitudes.set(testEvent1, new RandomEvents.QuantityForSaleChangeMagnitudes[]{RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_SMALL, RandomEvents.QuantityForSaleChangeMagnitudes.POSITIVE_MEDIUM});

         // grab the random event loader/validator
         randomEventsLoadWares = RandomEvents.class.getDeclaredMethod("loadWaresPrivate");
         randomEventsLoadWares.setAccessible(true);

         // add test events to random events array
         fRandomEvents = RandomEvents.class.getDeclaredField("randomEvents");
         fRandomEvents.setAccessible(true);
         fRandomEvents.set(NULL_OBJECT, Array.newInstance(fRandomEvents.getType().getComponentType(), 1));
         randomEvents    = (Object[]) fRandomEvents.get(null);
         randomEvents[0] = testEvent1;

         // finish setting up event
         randomEventsLoadWares.invoke(NULL_OBJECT, NULL_OBJECTS);

         // grab the RandomEvent event firer
         randomEventFire = randomEventClass.getDeclaredMethod("fire");
         randomEventFire.setAccessible(true);

         // grab references to RandomEvent variables
         fQuanChangeMedium = RandomEvents.class.getDeclaredField("quanChangeMedium");
         fQuanChangeSmall  = RandomEvents.class.getDeclaredField("quanChangeSmall");
         fQuanChangeMedium.setAccessible(true);
         fQuanChangeSmall.setAccessible(true);

         // set up RandomEvent variables
         quanChangeMedium = new int[]{100, 200, 300, 400, 500, 600};
         quanChangeSmall  = new int[]{10, 20, 30, 40, 50, 60};
         fQuanChangeMedium.set(null, quanChangeMedium);
         fQuanChangeSmall.set(null, quanChangeSmall);

         TEST_OUTPUT.println("Cross Interactions - No Garbage Disposing: Random Events, selling past price floor");
         // set up test conditions
         quantityToTrade = 5;
         ware1           = testWare1;
         ware2           = testWare2;
         quantityWare1   = Config.quanExcessive[ware1.getLevel()] - quantityToTrade - 1;
         quantityWare2   = Config.quanEquilibrium[ware2.getLevel()];
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);

         // perform action
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);

         // check ware properties
         if (ware1.getQuantity() != quantityWare1 + quantityToTrade) { // limited to price floor
            TEST_OUTPUT.println("   unexpected quantity: " + ware1.getQuantity() +
                               ", should be " + (quantityWare1 + quantityToTrade));
            errorFound = true;
         }
         if (ware2.getQuantity() != quantityWare2 + quanChangeMedium[ware2.getLevel()]) { // not limited at all
            TEST_OUTPUT.println("   unexpected quantity: " + ware2.getQuantity() +
                               ", should be " + (quantityWare2 + quanChangeMedium[ware2.getLevel()]));
            errorFound = true;
         }

         TEST_OUTPUT.println("Cross Interactions - No Garbage Disposing: Random Events, selling at price floor");
         // set up test conditions
         ware1           = testWare1;
         ware2           = testWare2;
         quantityWare1   = Config.quanEquilibrium[ware1.getLevel()];
         quantityWare2   = Config.quanExcessive[ware2.getLevel()] - 1;
         ware1.setQuantity(quantityWare1);
         ware2.setQuantity(quantityWare2);

         // perform action
         randomEventFire.invoke(testEvent1, NULL_OBJECTS);

         // check ware properties
         if (ware1.getQuantity() != quantityWare1 + quanChangeSmall[ware1.getLevel()]) { // not limited at all
            TEST_OUTPUT.println("   unexpected quantity: " + ware1.getQuantity() +
                               ", should be " + (quantityWare1 + quanChangeSmall[ware1.getLevel()]));
            errorFound = true;
         }
         if (ware2.getQuantity() != quantityWare2) { // prevented from selling anything
            TEST_OUTPUT.println("   unexpected quantity: " + ware2.getQuantity() +
                               ", should be " + quantityWare2);
            errorFound = true;
         }
      }
      catch (Exception e) {
         Config.chargeTransactionFees           = false;
         Config.buyingOutOfStockWaresAllowed    = false;
         Config.researchCostPerHierarchyLevel = 0.0f;
         Config.pricesIgnoreSupplyAndDemand     = false;
         Config.noGarbageDisposing              = false;

         TEST_OUTPUT.println("Cross Interactions - fatal error: " + e);
         e.printStackTrace();
         return false;
      }
      Config.chargeTransactionFees           = false;
      Config.buyingOutOfStockWaresAllowed    = false;
      Config.researchCostPerHierarchyLevel = 0.0f;
      Config.pricesIgnoreSupplyAndDemand     = false;
      Config.noGarbageDisposing              = false;

      return !errorFound;
   }

   /**
    * Predicts how much a ware's quantity available for sale should change if its rebalanced correctly
    * for a typical case when rebalancing the marketplace.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware the ware to be adjusted
    * @return true if an error was discovered
    */
   private static int predictRebalancingWareAdjustment(Ware ware) {
      if (ware == null ||                                        // ware doesn't exist
          ware instanceof WareUntradeable ||                     // ware cannot be traded
          ware.getQuantity() == Config.quanEquilibrium[ware.getLevel()]) // ware is at equilibrium
         return 0;

      // find the maximum amount that quantity may move
      int adjustment = (int) (Config.automaticStockRebalancingPercent * Config.quanEquilibrium[ware.getLevel()]);

      // predict how much quantity should be changed
      // too much quantity
      if (ware.getQuantity() > Config.quanEquilibrium[ware.getLevel()]) {
         // above maximum adjustment
         if (ware.getQuantity() - adjustment >= Config.quanEquilibrium[ware.getLevel()])
            return -adjustment;

         // within range of adjustment
         else
            return Config.quanEquilibrium[ware.getLevel()] - ware.getQuantity();
      }

      // too little quantity
      else {
         // above maximum adjustment
         if (ware.getQuantity() + adjustment <= Config.quanEquilibrium[ware.getLevel()])
            return adjustment;

         // within range of adjustment
         else
            return Config.quanEquilibrium[ware.getLevel()] - ware.getQuantity();
      }
   }

   /**
    * Evaluates whether several wares' quantities available for sale were rebalanced correctly
    * for a typical case when rebalancing the marketplace.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware1                 the first ware to be adjusted
    * @param ware2                 the second ware to be adjusted
    * @param ware3                 the third ware to be adjusted
    * @param ware4                 the fourth ware to be adjusted
    * @param ware5                 the fifth ware to be adjusted
    * @param ware6                 the sixth ware to be adjusted
    * @param quantityWare1         what to set the first ware's quantity available for sale to
    * @param quantityWare2         what to set the second ware's quantity available for sale to
    * @param quantityWare3         what to set the third ware's quantity available for sale to
    * @param quantityWare4         what to set the fourth ware's quantity available for sale to
    * @param quantityWare5         what to set the fifth ware's quantity available for sale to
    * @param quantityWare6         what to set the sixth ware's quantity available for sale to
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testRebalanceMarket(Ware ware1, Ware ware2, Ware ware3, Ware ware4, Ware ware5, Ware ware6,
                                              int quantityWare1, int quantityWare2, int quantityWare3,
                                              int quantityWare4, int quantityWare5, int quantityWare6,
                                              int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      int adjustment1 = 0; // how much ware 1's stock should move
      int adjustment2 = 0; // how much ware 2's stock should move
      int adjustment3 = 0; // how much ware 3's stock should move
      int adjustment4 = 0; // how much ware 4's stock should move
      int adjustment5 = 0; // how much ware 5's stock should move
      int adjustment6 = 0; // how much ware 6's stock should move

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // grab the method to be tested
      if (incrementallyRebalanceMarket == null) {
         try {
            incrementallyRebalanceMarket = MarketRebalancer.class.getDeclaredMethod("incrementallyRebalanceMarket");
            incrementallyRebalanceMarket.setAccessible(true);
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   failed to retrieve rebalancing method: " + e);
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return true;
         }
      }

      // set up test conditions and test oracles
      if (ware1 != null) {
         ware1.setQuantity(quantityWare1);
         adjustment1 = predictRebalancingWareAdjustment(ware1);
      }
      if (ware2 != null) {
         ware2.setQuantity(quantityWare2);
         adjustment2 = predictRebalancingWareAdjustment(ware2);
      }
      if (ware3 != null) {
         ware3.setQuantity(quantityWare3);
         adjustment3 = predictRebalancingWareAdjustment(ware3);
      }
      if (ware4 != null) {
         ware4.setQuantity(quantityWare4);
         adjustment4 = predictRebalancingWareAdjustment(ware4);
      }
      if (ware5 != null) {
         ware5.setQuantity(quantityWare5);
         adjustment5 = predictRebalancingWareAdjustment(ware5);
      }
      if (ware6 != null) {
         ware6.setQuantity(quantityWare6);
         adjustment6 = predictRebalancingWareAdjustment(ware6);
      }

      // test as normal
      try {
         incrementallyRebalanceMarket.invoke(NULL_OBJECT, NULL_OBJECTS);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("   failed to invoke rebalancing method: " + e);
         baosErr.reset();
         e.printStackTrace();
         TEST_OUTPUT.println(baosErr.toString());
         return true;
      }

      // check ware properties
      if (ware1 != null && ware1.getQuantity() != quantityWare1 + adjustment1) {
            TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + " for ware1:   " + ware1.getQuantity() + ", should be " + (quantityWare1 + adjustment1) +
                                "\n   unexpected adjustment" + testIdentifier + " for ware1: " + (ware1.getQuantity() - quantityWare1) + ", should be " + adjustment1 +
                                "\n   equilibrium" + testIdentifier + " for ware1:           " + Config.quanEquilibrium[ware1.getLevel()]);
         errorFound = true;
      }
      if (ware2 != null && ware2.getQuantity() != quantityWare2 + adjustment2) {
            TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + " for ware2: " + ware2.getQuantity() + ", should be " + (quantityWare2 + adjustment2) +
                                "\n   unexpected adjustment" + testIdentifier + " for ware2: " + (ware2.getQuantity() - quantityWare2) + ", should be " + adjustment2);
         errorFound = true;
      }
      if (ware3 != null && ware3.getQuantity() != quantityWare3 + adjustment3) {
            TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + " for ware3: " + ware3.getQuantity() + ", should be " + (quantityWare3 + adjustment3) +
                                "\n   unexpected adjustment" + testIdentifier + " for ware3: " + (ware3.getQuantity() - quantityWare3) + ", should be " + adjustment3);
         errorFound = true;
      }
      if (ware4 != null && ware4.getQuantity() != quantityWare4 + adjustment4) {
            TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + " for ware4: " + ware4.getQuantity() + ", should be " + (quantityWare4 + adjustment4) +
                                "\n   unexpected adjustment" + testIdentifier + " for ware4: " + (ware4.getQuantity() - quantityWare4) + ", should be " + adjustment4);
         errorFound = true;
      }
      if (ware5 != null && ware5.getQuantity() != quantityWare5 + adjustment5) {
            TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + " for ware5: " + ware5.getQuantity() + ", should be " + (quantityWare5 + adjustment5) +
                                "\n   unexpected adjustment" + testIdentifier + " for ware5: " + (ware5.getQuantity() - quantityWare5) + ", should be " + adjustment5);
         errorFound = true;
      }
      if (ware6 != null && ware6.getQuantity() != quantityWare6 + adjustment6) {
            TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + " for ware6: " + ware6.getQuantity() + ", should be " + (quantityWare6 + adjustment6) +
                                "\n   unexpected adjustment" + testIdentifier + " for ware6: " + (ware6.getQuantity() - quantityWare6) + ", should be " + adjustment6);
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether a ware's quantity available for sale was rebalanced correctly
    * for a typical case when rebalancing the marketplace.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be adjusted
    * @param quantityWare          what to set the ware's quantity available for sale to
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testRebalanceMarket(Ware ware, int quantityWare,
                                              int testNumber, boolean printTestNumber) {
      return testRebalanceMarket(ware, null, null, null, null, null,
                                 quantityWare, 0, 0, 0, 0, 0,
                                 testNumber, printTestNumber);
   }

   /**
    * Tests automatically rebalancing quantities for sale within the marketplace.
    *
    * @return whether automatically rebalancing supply and demand passed all test cases
    */
   private static boolean testUnitIncrementallyRebalanceMarket() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.automaticStockRebalancingPercent = 0.10f;
      MarketRebalancer.calcQuantityChanges();
      FileWriter fileWriter;

      try {
         TEST_OUTPUT.println("incrementallyRebalanceMarket() - overstocked");
         errorFound |= testRebalanceMarket(testWare1, Config.quanExcessive[testWare1.getLevel()], 0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - above equilibrium");
         errorFound |= testRebalanceMarket(testWareC1, (int) (Config.quanEquilibrium[testWareC1.getLevel()] * (1.0f + (Config.automaticStockRebalancingPercent / 2.0f))), 0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - equilibrium");
         errorFound |= testRebalanceMarket(testWareP1, Config.quanEquilibrium[testWareP1.getLevel()], 0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - below equilibrium");
         errorFound |= testRebalanceMarket(testWareC2, (int) (Config.quanEquilibrium[testWareC2.getLevel()] * (1.0f - (Config.automaticStockRebalancingPercent / 2.0f))), 0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - understocked");
         errorFound |= testRebalanceMarket(testWare3, Config.quanDeficient[testWare3.getLevel()], 0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - untradeable ware");
         errorFound |= testRebalanceMarket(testWareU1, 10, 0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - scaling based on each level's equilibrium stock: overstocked");
         errorFound |= testRebalanceMarket(testWare1,  // level 0
                             testWare2,  // level 1
                             testWareC1, // level 2
                             testWare4,  // level 3
                             testWareP1, // level 4
                             testWareP2, // level 5
                             Config.quanExcessive[0], Config.quanExcessive[1], Config.quanExcessive[2],
                             Config.quanExcessive[3], Config.quanExcessive[4], Config.quanExcessive[5],
                             0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - scaling based on each level's equilibrium stock: understocked");
         errorFound |= testRebalanceMarket(testWare1,  // level 0
                             testWare2,  // level 1
                             testWareC1, // level 2
                             testWare4,  // level 3
                             testWareP1, // level 4
                             testWareP2, // level 5
                             Config.quanDeficient[0], Config.quanDeficient[1], Config.quanDeficient[2],
                             Config.quanDeficient[3], Config.quanDeficient[4], Config.quanDeficient[5],
                             0, false);

         TEST_OUTPUT.println("incrementallyRebalanceMarket() - toggling feature and changing values by reloading configuration");
         // grab the variables used for rebalancing the marketplace
         Field testTimer = MarketRebalancer.class.getDeclaredField("timerMarketRebalancer");
         testTimer.setAccessible(true);
         Timer timerMarketRebalancer = (Timer) testTimer.get(null);

         // Make sure the feature is off.
         if (timerMarketRebalancer != null) {
            timerMarketRebalancer.cancel();
            timerMarketRebalancer = null;
            TEST_OUTPUT.println("   warning - timer was not null when it should have been");
         }

         // write to config file to turn on feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "automaticStockRebalancing = true\n" +
            "automaticStockRebalancingFrequency = 10\n" +
            "automaticStockRebalancingPercent = 0.05\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // attempt to turn on the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // check whether the feature is enabled
         timerMarketRebalancer = (Timer) testTimer.get(null);
         if (timerMarketRebalancer == null) {
            TEST_OUTPUT.println("   feature did not turn on when it should have");
            errorFound = true;
         }

         // check frequency and percent
         if (Config.automaticStockRebalancingFrequency != 600000) {
            TEST_OUTPUT.println("   unexpected frequency: " + Config.automaticStockRebalancingFrequency + ", should be 600000");
            errorFound = true;
         }

         if (Config.automaticStockRebalancingPercent != 0.05f) {
            TEST_OUTPUT.println("   unexpected percent: " + Config.automaticStockRebalancingPercent + ", should be 0.05");
            errorFound = true;
         }

         // write to config file to turn off feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "automaticStockRebalancing = false\n" +
            "automaticStockRebalancingFrequency = 15\n" +
            "automaticStockRebalancingPercent = 0.30\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // attempt to turn on the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // check whether the feature is disabled
         timerMarketRebalancer = (Timer) testTimer.get(null);
         if (timerMarketRebalancer != null) {
            TEST_OUTPUT.println("   feature did not turn off when it should have");
            errorFound = true;
         }

         // check frequency and percent
         if (Config.automaticStockRebalancingFrequency != 900000) {
            TEST_OUTPUT.println("   unexpected frequency: " + Config.automaticStockRebalancingFrequency + ", should be 900000");
            errorFound = true;
         }

         if (Config.automaticStockRebalancingPercent != 0.30f) {
            TEST_OUTPUT.println("   unexpected percent: " + Config.automaticStockRebalancingPercent + ", should be 0.30");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("incrementallyRebalanceMarket() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Evaluates whether several wares' quantities available for sale were rebalanced correctly
    * for a typical case when rebalancing the marketplace.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param money1                how much money the first account should start with
    * @param money2                how much money the second account should start with
    * @param money3                how much money the third account should start with
    * @param interestRate          the percentage of compound interest to be applied
    * @param numIterations         how many times interest should be applied
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testerAccountInterest(float money1, float money2, float money3,
                                                float interestRate, int numIterations,
                                                int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      float expectedMoney1 = money1; // how much money the account should have after interest is applied
      float expectedMoney2 = money2;
      float expectedMoney3 = money3;

      // grab the method to be tested
      if (applyAccountInterest == null) {
         try {
            applyAccountInterest = AccountInterestApplier.class.getDeclaredMethod("applyAccountInterest");
            applyAccountInterest.setAccessible(true);
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   failed to retrieve interest-applying method: " + e);
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return true;
         }
      }

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      Config.accountPeriodicInterestPercent = interestRate;
      testAccount1.setMoney(money1);
      testAccount2.setMoney(money2);
      testAccount3.setMoney(money3);

      // set up test oracles and test as normal
      for (int i = 0; i < numIterations; i++) {
         expectedMoney1 *= Config.accountPeriodicInterestPercent;
         expectedMoney2 *= Config.accountPeriodicInterestPercent;
         expectedMoney3 *= Config.accountPeriodicInterestPercent;

         // test as normal
         try {
            applyAccountInterest.invoke(NULL_OBJECT, NULL_OBJECTS);
         }
         catch (Exception e) {
            TEST_OUTPUT.println("   failed to invoke interest-applying method: " + e);
            baosErr.reset();
            e.printStackTrace();
            TEST_OUTPUT.println(baosErr.toString());
            return true;
         }
      }

      // if interest should only be applied if players are online,
      // unapply interest where necessary
      if (Config.accountPeriodicInterestOnlyWhenPlaying) {
         if (InterfaceTerminal.playername.equals("possibleID")) {
            expectedMoney1 = money1;
            expectedMoney2 = money2;
         } else
            expectedMoney3 = money3;
      }

      // check account properties
      if (testAccount1.getMoney() != expectedMoney1) {
         TEST_OUTPUT.println("   unexpected funds" + testIdentifier + " for testAccount1: " + testAccount1.getMoney() + ", should be " + expectedMoney1);
         errorFound = true;
      }

      if (testAccount2.getMoney() != expectedMoney2) {
         TEST_OUTPUT.println("   unexpected funds" + testIdentifier + " for testAccount2: " + testAccount2.getMoney() + ", should be " + expectedMoney2);
         errorFound = true;
      }
      if (testAccount3.getMoney() != expectedMoney3) {
         TEST_OUTPUT.println("   unexpected funds" + testIdentifier + " for testAccount3: " + testAccount3.getMoney() + ", should be " + expectedMoney3);
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Tests periodically growing account with compound interest.
    *
    * @return whether growing accounts with interest passed all test cases
    */
   private static boolean testUnitAccountInterest() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.accountPeriodicInterestEnabled   = true;
      Config.accountPeriodicInterestPercent   = 1.015f;
      Config.accountPeriodicInterestFrequency = 7200000; // 2 hours
      FileWriter fileWriter;

      try {
         TEST_OUTPUT.println("applyAccountInterest() - compounding once");
         errorFound |= testerAccountInterest(1000.0f, 100.0f, 10.0f,
                                             1.015f, 1, 0, false);

         TEST_OUTPUT.println("applyAccountInterest() - compounding 5 times");
         errorFound |= testerAccountInterest(1000.0f, 100.0f, 10.0f,
                                             1.015f, 5, 0, false);

         TEST_OUTPUT.println("applyAccountInterest() - compounding 10 times");
         errorFound |= testerAccountInterest(-1000.0f, -100.0f, -10.0f,
                                             1.015f, 1, 0, false);

         TEST_OUTPUT.println("applyAccountInterest() - compounding 25 times");
         errorFound |= testerAccountInterest(2.0f, 4.0f, 8.0f,
                                             1.015f, 1, 0, false);

         TEST_OUTPUT.println("applyAccountInterest() - negative interest");
         errorFound |= testerAccountInterest(-1000.0f, 100.0f, 4.0f,
                                             0.985f, 1, 1, true); // -1.5% interest rate

         errorFound |= testerAccountInterest(-1000.0f, 100.0f, 4.0f,
                                             0.985f, 10, 2, true); // -1.5% interest rate

         TEST_OUTPUT.println("applyAccountInterest() - applying only when online");
         Config.accountPeriodicInterestOnlyWhenPlaying = true;
         errorFound |= testerAccountInterest(100.0f, 100.0f, 100.0f,
                                             1.015f, 1, 0, false);

         String playernameOrig = InterfaceTerminal.playername;
         InterfaceTerminal.playername = "possibleID";

         errorFound |= testerAccountInterest(100.0f, 100.0f, 100.0f,
                                             1.015f, 1, 0, false);

         InterfaceTerminal.playername = playernameOrig;

         TEST_OUTPUT.println("applyAccountInterest() - toggling feature and changing values by reloading configuration");
         // grab the timer for applying account interest
         Field testTimer = AccountInterestApplier.class.getDeclaredField("timerAccountInterestApplier");
         testTimer.setAccessible(true);
         Timer timerAccountInterestApplier = (Timer) testTimer.get(null);

         // Make sure the feature is off.
         if (timerAccountInterestApplier != null) {
            timerAccountInterestApplier.cancel();
            timerAccountInterestApplier = null;
            TEST_OUTPUT.println("   warning - Terminal Interface timer was not null when it should have been");
         }

         // write to config file to turn on feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "accountPeriodicInterestEnabled = true\n" +
            "accountPeriodicInterestFrequency = 15\n" +
            "accountPeriodicInterestPercent = 5.0\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // attempt to turn on the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // check whether the feature is enabled
         timerAccountInterestApplier = (Timer) testTimer.get(null);
         if (timerAccountInterestApplier == null) {
            TEST_OUTPUT.println("   feature did not turn on when it should have");
            errorFound = true;
         }

         // check frequency and percent
         if (Config.accountPeriodicInterestFrequency != 900000) { // 15 minutes * 60000 milliseconds per minute
            TEST_OUTPUT.println("   unexpected frequency: " + Config.accountPeriodicInterestFrequency + ", should be 900000");
            errorFound = true;
         }

         if (Config.accountPeriodicInterestPercent != 1.05f) { // 1 + (5 / 100)
            TEST_OUTPUT.println("   unexpected frequency: " + Config.accountPeriodicInterestPercent + ", should be 1.05");
            errorFound = true;
         }

         // write to config file to turn off feature
         fileWriter = new FileWriter("config" + File.separator + Config.filenameConfig);
         fileWriter.write(
            "// warning: this file may be cleared and overwritten by the program\n\n" +
            "accountPeriodicInterestEnabled = false\n" +
            "accountPeriodicInterestFrequency = 4\n" +
            "accountPeriodicInterestPercent = 85.0\n" +
            "disableAutoSaving = true\n" +
            "crossWorldMarketplace = true\n"
         );
         fileWriter.close();

         // attempt to turn on the feature by reloading config
         InterfaceTerminal.serviceRequestReload(new String[]{"config"});

         // check whether the feature is disabled
         timerAccountInterestApplier = (Timer) testTimer.get(null);
         if (timerAccountInterestApplier != null) {
            TEST_OUTPUT.println("   feature did not turn off when it should have");
            errorFound = true;
         }

         // check frequency and percent
         if (Config.accountPeriodicInterestFrequency != 240000) {
            TEST_OUTPUT.println("   unexpected frequency: " + Config.accountPeriodicInterestFrequency + ", should be 240000");
            errorFound = true;
         }

         if (Config.accountPeriodicInterestPercent != 1.85f) {
            TEST_OUTPUT.println("   unexpected frequency: " + Config.accountPeriodicInterestPercent + ", should be 1.85");
            errorFound = true;
         }
      }
      catch (Exception e) {
         TEST_OUTPUT.println("applyAccountInterest() - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }

   /**
    * Evaluates whether restricting sales at or below the price floor
    * was handled correctly for a typical case of selling something.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be sold
    * @param wareDistFromFloor     how much quantity available for sale away from
                                   lowering price to the floor the ware should be set to
    * @param quantityToOffer       how much of the ware to attempt to sell
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testNGDSell(Ware ware, int wareDistFromFloor, int quantityToOffer,
                                      int testNumber, boolean printTestNumber) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      float   price;           // how much traded wares should cost
      int     quantityToTrade; // how much of the ware should be traded
      int     quantityWare;    // ware's quantity available for sale

      boolean displayErrorFloorBelow   = false; // whether a message about not selling past the price floor should be displayed

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      if (wareDistFromFloor < quantityToOffer) {
         // only sell to the price floor
         if (wareDistFromFloor > 0) {
            quantityToTrade          = wareDistFromFloor - 1;
         }

         // do not sell past the price floor
         else {
            quantityToTrade        = 0;
            displayErrorFloorBelow = true;
         }
      }
      else
         quantityToTrade = quantityToOffer;
      quantityWare       = Config.quanExcessive[ware.getLevel()] - wareDistFromFloor - 1;
      ware.setQuantity(quantityWare);
      playerAccount.setMoney(0.0f);
      InterfaceTerminal.inventory.clear();
      InterfaceTerminal.inventory.put(ware.getWareID(), quantityToTrade);

      // set up test oracles
      if (quantityToTrade <= 0)
         price = 0.0f;
      else
         price = Marketplace.getPrice(PLAYER_ID, ware, quantityToTrade, Marketplace.PriceType.CURRENT_SELL);

      // test as normal
      baosOut.reset(); // clear buffer holding console output
      Marketplace.sell(PLAYER_ID, null, null, ware.getWareID(), quantityToOffer, 0.0f, 1.0f);

      // check ware properties
      if (ware.getQuantity() != quantityWare + quantityToTrade) {
         TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + ": " + ware.getQuantity() +
                            ", should be " + (quantityWare + quantityToTrade));
         errorFound = true;
      }
      // check account funds
      if (price + FLOAT_COMPARE_PRECISION < playerAccount.getMoney() ||
          playerAccount.getMoney() < price - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected price" + testIdentifier + ": " + playerAccount.getMoney() + ", should be " + price +
                            "\n      diff: " + (playerAccount.getMoney() - price));
         errorFound = true;
      }
      // check console output
      /*if (displayErrorFloorReached && !baosOut.toString().contains("Cannot sell at or below the price floor")) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      } else*/ if (displayErrorFloorBelow && !baosOut.toString().contains("Cannot sell at or below the price floor")) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                            "   ware quantity: " + ware.getQuantity());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether restricting sales at or below the price floor
    * was handled correctly for a typical case of selling all of an inventory.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware1                 ware to be sold or null
    * @param ware2                 ware to be sold or null
    * @param ware3                 ware to be sold or null
    * @param quantityToOffer1      how much of the first ware to sell
    * @param quantityToOffer2      how much of the second ware to sell
    * @param quantityToOffer3      how much of the third ware to sell
    * @param wareDistFromFloor1    how much quantity available for sale away from
                                   lowering price to the floor the first ware should be set to
    * @param wareDistFromFloor2    quantity available for sale away from floor for second ware
    * @param wareDistFromFloor3    quantity available for sale away from floor for third ware
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @return true if an error was discovered
    */
   private static boolean testNGDSellAll(Ware ware1, Ware ware2, Ware ware3,
                                         int quantityToOffer1, int quantityToOffer2, int quantityToOffer3,
                                         int wareDistFromFloor1, int wareDistFromFloor2, int wareDistFromFloor3,
                                         int testNumber, boolean printTestNumber) {
      String  testIdentifier;        // can be added to printed errors to differentiate between tests
      boolean errorFound    = false; // assume innocence until proven guilty

      float   price1           = 0.0f;  // profits from each ware
      float   price2           = 0.0f;
      float   price3           = 0.0f;
      int     quantityWare1    = 0;     // wares' quantities available for sale
      int     quantityWare2    = 0;
      int     quantityWare3    = 0;
      int     quantityToTrade1 = 0;     // how much of the ware should be traded
      int     quantityToTrade2 = 0;
      int     quantityToTrade3 = 0;
      boolean sellWare1        = true;  // don't sell if it will not make money
      boolean sellWare2        = true;
      boolean sellWare3        = true;
      float   priceTotal;               // transaction's income

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      playerAccount.setMoney(0.0f);
      InterfaceTerminal.inventory.clear();

      // set up wares
      if (ware1 != null) {
         quantityWare1 = Config.quanExcessive[ware1.getLevel()] - wareDistFromFloor1;
         ware1.setQuantity(quantityWare1);
         InterfaceTerminal.inventory.put(ware1.getWareID(), quantityToOffer1);

         // find quantity to sell
         if (wareDistFromFloor1 < quantityToOffer1) {
            // only sell to the price floor
            if (wareDistFromFloor1 > 0)
               quantityToTrade1 = wareDistFromFloor1 - 1;

            // do not sell past the price floor
            else {
               quantityToTrade1 = 0;
               sellWare1        = false;
            }
         }
         else
            quantityToTrade1 = quantityToOffer1;

         // don't sell if there is no profit to be made
         price1       = Marketplace.getPrice(PLAYER_ID, ware1, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price1 > 0.0001f)
            price1    = Marketplace.getPrice(PLAYER_ID, ware1, quantityToTrade1, Marketplace.PriceType.CURRENT_SELL);
         else {
            price1    = 0.0f;
            sellWare1 = false;
         }
      }
      if (ware2 != null) {
         quantityWare2 = Config.quanExcessive[ware2.getLevel()] - wareDistFromFloor2;
         ware2.setQuantity(quantityWare2);
         InterfaceTerminal.inventory.put(ware2.getWareID(), quantityToOffer2);

         // find quantity to sell
         if (wareDistFromFloor2 < quantityToOffer2) {
            // only sell to the price floor
            if (wareDistFromFloor2 > 0)
               quantityToTrade2 = wareDistFromFloor2 - 1;

            // do not sell past the price floor
            else {
               quantityToTrade2 = 0;
               sellWare2        = false;
            }
         }
         else
            quantityToTrade2 = quantityToOffer2;

         // don't sell if there is no profit to be made
         price2       = Marketplace.getPrice(PLAYER_ID, ware2, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price2 > 0.0001f)
            price2    = Marketplace.getPrice(PLAYER_ID, ware2, quantityToTrade2, Marketplace.PriceType.CURRENT_SELL);
         else {
            price2    = 0.0f;
            sellWare2 = false;
         }
      }
      if (ware3 != null) {
         quantityWare3 = Config.quanExcessive[ware3.getLevel()] - wareDistFromFloor3;
         ware3.setQuantity(quantityWare3);
         InterfaceTerminal.inventory.put(ware3.getWareID(), quantityToOffer3);

         // find quantity to sell
         if (wareDistFromFloor3 < quantityToOffer3) {
            // only sell to the price floor
            if (wareDistFromFloor3 > 0)
               quantityToTrade3 = wareDistFromFloor3 - 1;

            // do not sell past the price floor
            else {
               quantityToTrade3 = 0;
               sellWare3        = false;
            }
         }
         else
            quantityToTrade3 = quantityToOffer3;

         // don't sell if there is no profit to be made
         price3       = Marketplace.getPrice(PLAYER_ID, ware3, 1, Marketplace.PriceType.CURRENT_SELL);
         if (price3 > 0.0001f)
            price3    = Marketplace.getPrice(PLAYER_ID, ware3, quantityToTrade3, Marketplace.PriceType.CURRENT_SELL);
         else {
            price3    = 0.0f;
            sellWare3 = false;
         }
      }

      // set up test oracles
      priceTotal = CommandEconomy.truncatePrice(price1 + price2 + price3); // avoid precision errors using truncation

      // run the test
      baosOut.reset(); // clear buffer holding console output
      Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), null, 1.0f);

      // check ware properties
      if (quantityWare1 != 0) {
         if (sellWare1) {
            if (ware1.getQuantity() != quantityWare1 + quantityToTrade1) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware1.getWareID() + testIdentifier + ": " + ware1.getQuantity() +
                                  ", should be " + (quantityWare1 + quantityToTrade1));
               errorFound = true;
            }
         } else {
            if (ware1.getQuantity() != quantityWare1) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware1.getWareID() + testIdentifier + ": " + ware1.getQuantity() +
                                  ", should be " + quantityWare1);
               errorFound = true;
            }
         }
      }
      if (quantityWare2 != 0) {
         if (sellWare2) {
            if (ware2.getQuantity() != quantityWare2 + quantityToTrade2) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                                  ", should be " + (quantityWare2 + quantityToTrade2));
               errorFound = true;
            }
         } else {
            if (ware2.getQuantity() != quantityWare2) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware2.getWareID() + testIdentifier + ": " + ware2.getQuantity() +
                                  ", should be " + quantityWare2);
               errorFound = true;
            }
         }
      }
      if (quantityWare3 != 0) {
         if (sellWare3) {
            if (ware3.getQuantity() != quantityWare3 + quantityToTrade3) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                                  ", should be " + (quantityWare3 + quantityToTrade3));
               errorFound = true;
            }
         } else {
            if (ware3.getQuantity() != quantityWare3) {
               TEST_OUTPUT.println("   unexpected quantity for " + ware3.getWareID() + testIdentifier + ": " + ware3.getQuantity() +
                                  ", should be " + quantityWare3);
               errorFound = true;
            }
         }
      }
      // check account funds
      if (priceTotal + FLOAT_COMPARE_PRECISION < playerAccount.getMoney() ||
          playerAccount.getMoney() < priceTotal - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected price" + testIdentifier + ": " + playerAccount.getMoney() + ", should be " + priceTotal +
                            "\n      diff: " + (playerAccount.getMoney() - priceTotal));
         errorFound = true;
      }
      // check console output
      if (baosOut.toString().contains("   Sales upon reaching the ware's price floor")) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      } else if (baosOut.toString().contains("   error - unable to sell beyond the ware's price floor")) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString());
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Evaluates whether restricting sales at or below the price floor
    * was handled correctly for cases of selling a linked ware.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                   ware to be sold
    * @param component              ware to be sold's sole component
    * @param yieldOrComponentAmount if positive, ware to be sold's yield per unit of components,
    *                               if negative, ware to be sold's amount of components required per unit
    * @param wareDistFromFloor      how much quantity available for sale away from
    *                               lowering price to the floor the ware should be set to
    * @param quantityToOffer        how much of the ware to attempt to sell
    * @param testNumber             test number to use when printing errors
    * @param printTestNumber        whether to print test numbers
    * @param isNotSellAll           if true, call on Marketplace.sell() rather than Marketplace.sellAll()
    * @return true if an error was discovered
    */
   private static boolean testNGDLinkedWares(WareLinked ware, Ware component, int yieldOrComponentAmount,
                                             int wareDistFromFloor, int quantityToOffer,
                                             int testNumber, boolean printTestNumber, boolean isNotSellAll) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      int     quantityToTrade;         // how much of the ware should be traded
      int     quantityWare;            // ware's quantity available for sale
      int     quantityComponent;       // ware's component's quantity available for sale
      int     yield           = 1;     // how many wares are created per recipe iteration using 1 component unit
      int     componentAmount = 1;     // how many components are needed per recipe iteration creating 1 ware unit
      boolean displayError    = false; // whether a message about not selling at or past at the price floor should be displayed

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions and oracles
      if (wareDistFromFloor < quantityToOffer) {
         // only sell to the price floor
         if (wareDistFromFloor > 0) {
            quantityToTrade = wareDistFromFloor - 1;
         }

         // do not sell past the price floor
         else {
            quantityToTrade = 0;
            displayError    = isNotSellAll;
         }
      }
      else
         quantityToTrade = quantityToOffer;
      quantityComponent  = Config.quanExcessive[component.getLevel()];
      if (yieldOrComponentAmount > 0) {
         yield              = yieldOrComponentAmount;
         quantityComponent -= wareDistFromFloor / yield;           // adjust by wares created per component unit
      }
      else {
         componentAmount    = -yieldOrComponentAmount;
         quantityComponent -= wareDistFromFloor * componentAmount; // adjust by components required to make a ware unit
      }
      quantityComponent -= componentAmount;
      component.setQuantity(quantityComponent);
      quantityWare = ware.getQuantity();
      playerAccount.setMoney(0.0f);
      InterfaceTerminal.inventory.clear();
      InterfaceTerminal.inventory.put(ware.getWareID(), quantityToTrade);

      // test as normal
      baosOut.reset(); // clear buffer holding console output
      if (isNotSellAll)
         Marketplace.sell(PLAYER_ID, null, null, ware.getWareID(), quantityToOffer, 0.0f, 1.0f);
      else
         Marketplace.sellAll(PLAYER_ID, null, getFormattedInventory(), null, 1.0f);

      // check ware properties
      if (ware.getQuantity() != quantityWare + quantityToTrade) {
         TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + ": " + ware.getQuantity() +
                            ", should be " + (quantityWare + quantityToTrade) +
                            "\n      limit:              " + (((Config.quanExcessive[component.getLevel()] - 1) / componentAmount) * yield) +
                            "\n      diff:               " + (ware.getQuantity() - (quantityWare + quantityToTrade)) +
                            "\n      component limit:    " + (Config.quanExcessive[component.getLevel()] - 1) +
                            "\n      component quantity: " + component.getQuantity() +
                            "\n      component expected: " + quantityComponent +
                            "\n      component diff:     " + (component.getQuantity() - (quantityComponent + (quantityToTrade / componentAmount) * yield)));
         errorFound = true;
      }
      // check console output
      if ((displayError && !baosOut.toString().contains("Cannot sell at or below the price floor")) ||
          (!displayError && baosOut.toString().contains("Cannot sell at or below the price floor"))) {
         TEST_OUTPUT.println("   unexpected console output" + testIdentifier + ": " + baosOut.toString() +
                            "\n      limit:              " + (((Config.quanExcessive[component.getLevel()] - 1) / componentAmount) * yield) +
                            "\n      diff:               " + (ware.getQuantity() - (quantityWare + quantityToTrade)) +
                            "\n      component limit:    " + (Config.quanExcessive[component.getLevel()] - 1) +
                            "\n      component quantity: " + component.getQuantity() +
                            "\n      component expected: " + quantityComponent +
                            "\n      component diff:     " + (component.getQuantity() - (quantityComponent + (quantityToTrade / componentAmount) * yield)));
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Tests the configuration option to restrict selling
    * when prices are at or below the price floor.
    *
    * @return whether restricting sales passed all test cases
    */
   private static boolean testUnitNoGarbageDisposing() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.noGarbageDisposing = true;

      // test up test variables
      // linked ware with high yield
      Ware wood = new WareMaterial("minecraft:log", "wood", 0.5f, 10, (byte) 0);
      wares.put("minecraft:log", wood);
      wareAliasTranslations.put("wood", "minecraft:log");
      WareLinked planks = new WareLinked(new String[]{"wood"}, new int[]{1}, "minecraft:planks", "planks", 2);
      wares.put("minecraft:planks", planks);
      wareAliasTranslations.put("planks", "minecraft:planks");

      // linked ware with low yield
      Ware gold_ingot = new WareMaterial("minecraft:gold_ingot", "gold_ingot", 12.0f, 26, (byte) 3);
      wares.put("minecraft:gold_ingot", gold_ingot);
      wareAliasTranslations.put("gold_ingot", "minecraft:gold_ingot");
      WareLinked gold_block = new WareLinked(new String[]{"gold_ingot"}, new int[]{9}, "minecraft:gold_block", "gold_block", 1);
      wares.put("minecraft:gold_block", gold_block);
      wareAliasTranslations.put("gold_block", "minecraft:gold_block");

      // ensure linked wares are not flagged as invalid
      String validationError = planks.validate();
      if (validationError != null && !validationError.isEmpty())
         TEST_OUTPUT.println("failed to set up linked wares - planks: " + validationError);

      validationError = gold_block.validate();
      if (validationError != null && !validationError.isEmpty())
         TEST_OUTPUT.println("failed to set up linked wares - gold_block: " + validationError);

      try {
         TEST_OUTPUT.println("No Garbage Disposing - sell(): above price floor, ending above");
         errorFound |= testNGDSell(testWare1, 100, 10, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): above price floor, ending at floor");
         errorFound |= testNGDSell(testWare1, 100, 99, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): above price floor, ending past");
         errorFound |= testNGDSell(testWare1, 100, 100, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): at price floor");
         errorFound |= testNGDSell(testWare1, 0, 1, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): below price floor");
         errorFound |= testNGDSell(testWare1, -10, 1, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): linked wares, high yield, ending past price floor");
         errorFound |= testNGDLinkedWares(planks, wood, 2,
                                          10, 10, 0, false, true);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): linked wares, high yield, below price floor");
         errorFound |= testNGDLinkedWares(planks, wood, 2,
                                          -1, 10, 0, false, true);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): linked wares, low yield, ending past price floor");
         errorFound |= testNGDLinkedWares(gold_block, gold_ingot, -9,
                                          10, 10, 0, false, true);

         TEST_OUTPUT.println("No Garbage Disposing - sell(): linked wares, low yield, below price floor");
         errorFound |= testNGDLinkedWares(gold_block, gold_ingot, -9,
                                          -1, 10, 0, false, true);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): above price floor, ending above");
         errorFound |= testNGDSellAll(testWare1, testWare2, null, 100, 10, 1,
                                      200, 20, 0, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): above price floor, ending at floor");
         errorFound |= testNGDSellAll(testWare1, testWare2, null, 100, 10, 1,
                                      101, 11, 0, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): above price floor, ending past");
         errorFound |= testNGDSellAll(testWare1, testWare2, null, 100, 10, 1,
                                      10, 1, 0, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): at price floor");
         errorFound |= testNGDSellAll(testWareP1, testWareC2, testWare4, 10, 1, 1,
                                      0, 0, 0, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): below price floor");
         errorFound |= testNGDSellAll(testWareP1, testWareC2, testWare4, 10, 1, 1,
                                      -10, -1, -1, 0, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): linked wares, high yield, ending past price floor");
         errorFound |= testNGDLinkedWares(planks, wood, 2,
                                          10, 10, 0, false, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): linked wares, high yield, below price floor");
         errorFound |= testNGDLinkedWares(planks, wood, 2,
                                          -1, 10, 0, false, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): linked wares, low yield, ending past price floor");
         errorFound |= testNGDLinkedWares(gold_block, gold_ingot, -9,
                                          10, 10, 0, false, false);

         TEST_OUTPUT.println("No Garbage Disposing - sellall(): linked wares, low yield, below price floor");
         errorFound |= testNGDLinkedWares(gold_block, gold_ingot, -9,
                                          -1, 10, 0, false, false);
      }
      catch (Exception e) {
         Config.noGarbageDisposing = false;
         TEST_OUTPUT.println("No Garbage Disposing - fatal error: " + e);
         e.printStackTrace();
         return false;
      }
      Config.noGarbageDisposing = false;

      return !errorFound;
   }

   /**
    * Evaluates whether fixing wares' prices
    * was handled correctly for a typical case of selling something.
    * Prints errors, if found.
    * <p>
    * Complexity: O(1)
    * @param ware                  the ware to be sold
    * @param quantityWare          how much quantity available for sale the ware should be set to
    * @param quantityToTrade       how much of the ware to sell
    * @param priceBuyUpchargeMult  what to set the purchasing upcharge to
    * @param priceMult             what to set the marketplace price multiplier
    * @param testNumber            test number to use when printing errors
    * @param printTestNumber       whether to print test numbers
    * @param isPurchase            whether the transaction should buy wares
    * @return true if an error was discovered
    */
   private static boolean testPETrade(Ware ware, int quantityWare, int quantityToTrade,
                                      float priceBuyUpchargeMult, float priceMult,
                                      int testNumber, boolean printTestNumber, boolean isPurchase) {
      String  testIdentifier;     // can be added to printed errors to differentiate between tests
      boolean errorFound = false; // assume innocence until proven guilty

      float priceUnitBefore; // ware's price per unit before trading
      float priceUnitAfter;  // ware's price per unit after trading

      if (printTestNumber)
         testIdentifier = " (#" + testNumber + ")";
      else
         testIdentifier = "";

      // set up test conditions
      playerAccount.setMoney(10000.0f);
      ware.setQuantity(quantityWare);
      InterfaceTerminal.inventory.clear();
      InterfaceTerminal.inventory.put(ware.getWareID(), quantityToTrade);

      // set up test oracles
      if (isPurchase)
         priceUnitBefore = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_BUY);
      else
         priceUnitBefore = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_SELL);
      if (priceBuyUpchargeMult != Config.priceBuyUpchargeMult && isPurchase) {
         priceUnitBefore /= Config.priceBuyUpchargeMult;
         priceUnitBefore *= priceBuyUpchargeMult;
      }
      if (priceMult != Config.priceMult) {
         priceUnitBefore /= Config.priceMult;
         priceUnitBefore *= priceMult;
      }

      // set up rest of test conditions
      Config.priceBuyUpchargeMult = priceBuyUpchargeMult;
      Config.priceMult            = priceMult;

      // test as normal
      baosOut.reset(); // clear buffer holding console output
      if (isPurchase) {
         Marketplace.buy(PLAYER_ID, null, null, ware.getWareID(), quantityToTrade, 1000.0f, 1.0f);
         priceUnitAfter  = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_BUY);
         quantityToTrade = -quantityToTrade; // to ease checking ware's quantity available for sale
      }
      else {
         Marketplace.sell(PLAYER_ID, null, null, ware.getWareID(), quantityToTrade, 0.0f, 1.0f);
         priceUnitAfter = Marketplace.getPrice(PLAYER_ID, ware, 1, Marketplace.PriceType.CURRENT_SELL);
      }

      // check ware properties
      if (ware.getQuantity() != quantityWare + quantityToTrade) {
         TEST_OUTPUT.println("   unexpected quantity" + testIdentifier + ": " + ware.getQuantity() +
                            ", should be " + (quantityWare + quantityToTrade));
         errorFound = true;
      }
      // check price
      if (priceUnitBefore + FLOAT_COMPARE_PRECISION < priceUnitAfter ||
          priceUnitAfter < priceUnitBefore - FLOAT_COMPARE_PRECISION) {
         TEST_OUTPUT.println("   unexpected price" + testIdentifier + ": " + priceUnitAfter + ", should be " + priceUnitBefore +
                            "\n      diff: " + (priceUnitAfter - priceUnitBefore));
         errorFound = true;
      }

      return errorFound;
   }

   /**
    * Tests the configuration option to fix all prices.
    *
    * @return whether price-setting passed all test cases
    */
   private static boolean testUnitPlannedEconomy() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();
      Config.pricesIgnoreSupplyAndDemand = true;

      try {
         TEST_OUTPUT.println("Planned Economy - buying: excessive surplus");
         errorFound |= testPETrade(testWare1, Config.quanExcessive[testWare1.getLevel()], 10,
                                   1.0f, 1.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: mild surplus");
         errorFound |= testPETrade(testWare1, (int) (Config.quanEquilibrium[testWare1.getLevel()] * 1.1f), 10,
                                   1.0f, 1.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: at equilibrium");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   1.0f, 1.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: mild shortage");
         errorFound |= testPETrade(testWare1, (int) (Config.quanEquilibrium[testWare1.getLevel()] * 0.9f), 10,
                                   1.0f, 1.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: severe shortage");
         errorFound |= testPETrade(testWare1, Config.quanDeficient[testWare1.getLevel()], 10,
                                   1.0f, 1.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: buying upcharge");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   2.0f, 1.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: price multiplier, high");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   1.0f, 2.0f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - buying: price multiplier, low");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   1.0f, 0.5f, 0, false, true);

         TEST_OUTPUT.println("Planned Economy - selling: excessive surplus");
         errorFound |= testPETrade(testWare1, Config.quanExcessive[testWare1.getLevel()], 10,
                                   1.0f, 1.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: mild surplus");
         errorFound |= testPETrade(testWare1, (int) (Config.quanEquilibrium[testWare1.getLevel()] * 1.1f), 10,
                                   1.0f, 1.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: at equilibrium");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   1.0f, 1.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: mild shortage");
         errorFound |= testPETrade(testWare1, (int) (Config.quanEquilibrium[testWare1.getLevel()] * 0.9f), 10,
                                   1.0f, 1.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: severe shortage");
         errorFound |= testPETrade(testWare1, Config.quanDeficient[testWare1.getLevel()], 10,
                                   1.0f, 1.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: buying upcharge");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   2.0f, 1.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: price multiplier, high");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   1.0f, 2.0f, 0, false, false);

         TEST_OUTPUT.println("Planned Economy - selling: price multiplier, low");
         errorFound |= testPETrade(testWare1, Config.quanEquilibrium[testWare1.getLevel()], 10,
                                   1.0f, 0.5f, 0, false, false);
      }
      catch (Exception e) {
         Config.pricesIgnoreSupplyAndDemand = false;
         TEST_OUTPUT.println("Planned Economy - fatal error: " + e);
         e.printStackTrace();
         return false;
      }
      Config.pricesIgnoreSupplyAndDemand = false;

      return !errorFound;
   }

   /**
    * Tests whether passing price multipliers as part of the parameters of trade commands is handled correctly.
    *
    * @return whether the trade command price multiplier parameters passed all test cases
    */
   private static boolean testUnitLocalizedPrices() {
      // use a flag to signal at least one error being found
      boolean errorFound = false;

      // ensure testing environment is properly set up
      resetTestEnvironment();

      try {
         TEST_OUTPUT.println("buy() - no multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - invalid multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - zero multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - 100% multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%1", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - high multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%10.00", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - low multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.10", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - no admin permissions");
         errorFound |= testerTrade("possibleID", null, testWare1, "testAccount3", "%1.00", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - specified quantity");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.10", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 100, 100, 0, false, true, true);

         TEST_OUTPUT.println("buy() - specified unit price");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.75", "1.1", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 61, 100, 0, false, true, true);

         TEST_OUTPUT.println("buy() - specified account");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", "%0.50", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("buy() - specified unit price and account");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount2", "%0.75", "1.1", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 61, 100, 0, false, true, true);

         TEST_OUTPUT.println("sell() - no multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, null, null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - invalid multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 0, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - zero multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - 100% multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%1", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - high multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%10.00", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - low multiplier");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.10", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - no admin permissions");
         errorFound |= testerTrade("possibleID", null, testWare1, "testAccount3", "%1.00", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - no specified quantity");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.10", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 0, 0, false, false, true);

         TEST_OUTPUT.println("sell() - specified quantity");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.10", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, false, true);

         TEST_OUTPUT.println("sell() - specified unit price");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()]);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, null, "%0.75", "0.50", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], Marketplace.getQuantityUntilPrice(testWare1, 0.6666667f, false), 1000, 0, false, false, true);

         TEST_OUTPUT.println("sell() - specified account");
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount1", "%0.10", null, Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], 10, 10, 0, false, true, true);

         TEST_OUTPUT.println("sell() - specified unit price and account");
         testWare1.setQuantity(Config.quanEquilibrium[testWare1.getLevel()]);
         errorFound |= testerTrade(InterfaceTerminal.playername, null, testWare1, "testAccount2", "%0.75", "0.50", Float.NaN,
                                   Config.quanEquilibrium[testWare1.getLevel()], Marketplace.getQuantityUntilPrice(testWare1, 0.6666667f, false), 1000, 0, false, false, true);

         TEST_OUTPUT.println("check() - no multiplier");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 0,
                                   null, 1.0f, 0, true);

         TEST_OUTPUT.println("check() - invalid multiplier");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 0,
                                   "%", 1.0f, 0, true);

         TEST_OUTPUT.println("check() - zero multiplier");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 0,
                                   "%0", 1.0f, 0, true);

         TEST_OUTPUT.println("check() - 100% multiplier");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 0,
                                   "%1.00", 1.0f, 0, true);

         TEST_OUTPUT.println("check() - high multiplier");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWare1, 0,
                                   "%100.00", 1.0f, 0, false);

         TEST_OUTPUT.println("check() - low multiplier");
         errorFound |= testerCheck(InterfaceTerminal.playername, testWareU1, 0,
                                   "%0.01", 1.0f, 0, true);

         TEST_OUTPUT.println("check() - no admin permissions");
         errorFound |= testerCheck("possibleID", testWare1, 0,
                                   "%10.00", 1.0f, 0, true);

         TEST_OUTPUT.println("check() - no specified quantity");
         errorFound |= testerCheck("possibleID", testWare1, 0,
                                   "%10.00", 1.0f, 0, true);

         TEST_OUTPUT.println("check() - specified quantity");
         errorFound |= testerCheck("possibleID", testWare1, 10,
                                   "%10.00", 1.0f, 0, true);

         TEST_OUTPUT.println("sellall() - no multiplier");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, null,
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - invalid multiplier");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, "%",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - zero multiplier");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, "%0",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - 100% multiplier");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, "%1.0",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - high multiplier");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, "%10",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - low multiplier");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, null, "%0.1",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - no admin permissions");
         errorFound |= testerSellAll("possibleID", null, "testAccount3", "%0.1",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);

         TEST_OUTPUT.println("sellall() - specified account");
         errorFound |= testerSellAll(InterfaceTerminal.playername, null, "testAccount1", "%10",
                                     testWare1, testWare3, null,
                                     5, 10, 0, 0, true);
      }
      catch (Exception e) {
         TEST_OUTPUT.println("localized prices - fatal error: " + e);
         e.printStackTrace();
         return false;
      }

      return !errorFound;
   }
}