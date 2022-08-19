package commandeconomy;

import java.io.File;                  // for handling files
import java.util.Scanner;             // for parsing files
import java.io.FileWriter;            // for writing to files
import java.io.FileNotFoundException; // for handling missing file errors
import java.io.IOException;           // for handling miscellaneous file errors

/**
 * Holds configuration settings for the marketplace.
 * <p>
 * Configuration settings are held as fields rather than a hashtable
 * to prevent potential errors if configuration hasn't been loaded and
 * to quickly catch any typos or mistakes in accessing settings. 
 * <p>
 * Configuration is implemented using static variables rather than a
 * class with member variables since static variables seems to simplify code
 * and ensure all classes are running with the same configuration settings.
 * Although it is common to initialize and pass a configuration object,
 * the benefits of doing so primarily seem to be for testing. 
 * Command Economy's test suite would probably not benefit from
 * a configuration object. It seems simpler to run a function resetting configuration
 * to test suite standards and having each unit test explicitly change the few settings
 * they interact with than creating multiple configuration objects per test and then
 * ensuring the right settings are changed after creating the new object.
 * If the test suite were multithreaded, it would make sense to use a
 * configuration object. Right now, the complexities and risks of multithreading
 * far outweight any performance benefits the small test suite might gain.
 * It appears better to use static variables for configuration
 * until there might be a need to multithread testing or otherwise
 * reorganize the configuration class.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-04-08
 */
public class Config
{
   // interface
   /** used to access the interface in use */
   protected static InterfaceCommand commandInterface = null;

   // files
   /** contains wares to be tradeable within the marketplace */
   public    static String filenameNoPathWares         = "wares.txt";
   /** save file containing tradeable wares within the marketplace */
   private   static String filenameNoPathWaresSave     = "waresSaved.txt";
   /** contains accounts usable within the marketplace */
   private   static String filenameNoPathAccounts      = "accounts.txt";
   /** contains possible AI, the wares they may trade, and their preferences */
   protected static String filenameNoPathAIProfessions = "aiProfessions.json";
   /** contains potential events affecting the marketplace */
   protected static String filenameNoPathRandomEvents  = "randomEvents.json";

   /** contains settings for customizing the marketplace */
   public static String  filenameConfig        = "CommandEconomy" + File.separator + "config.txt";
   /** contains wares to be tradeable within the marketplace */
   public static String  filenameWares         = "config" + File.separator + "CommandEconomy" + File.separator + "wares.txt";
   /** save file containing tradeable wares within the marketplace */
   public static String  filenameWaresSave     = "config" + File.separator + "CommandEconomy" + File.separator + "waresSaved.txt";
   /** contains accounts usable within the marketplace */
   public static String  filenameAccounts      = "config" + File.separator + "CommandEconomy" + File.separator + "accounts.txt";
   /** output file for printing wares within the marketplace */
   public static String  filenameMarket        = "config" + File.separator + "CommandEconomy" + File.separator + "market.txt";
   /** contains possible AI, the wares they may trade, and their preferences */
   public static String  filenameAIProfessions = "config" + File.separator + "CommandEconomy" + File.separator + "aiProfessions.json";
   /** contains potential events affecting the marketplace */
   public static String  filenameRandomEvents  = "config" + File.separator + "CommandEconomy" + File.separator + "randomEvents.json";
   /** if true, load global save files instead of local */
   public static boolean crossWorldMarketplace = true;

   // hierarchy
   /** starting stock for each level */
   protected static int[] startQuanBase   = {16384,  9216,  5120,  3072, 2048, 1024};
   /** quantity &gt; this is considered saturated */
   public    static int[] quanExcessive   = {65536, 43008, 14336, 10240, 6144, 3072};
   /** quantity = this is considered balanced */
   public    static int[] quanEquilibrium = {16384,  9216,  5120,  3072, 2048, 1024};
   /** quantity &lt; this is considered scarce */
   public    static int[] quanDeficient   = { 4096,  2048,  1536,  1024,  768,  512};

   // starting quantity
   /** scales market stock linearly */
   protected static float startQuanMult = 1.0f;
   /** pushes stock levels closer together or farther apart */
   protected static float startQuanSpread = 1.0f;
   /** starting quantities for each level */
   protected static int[] startQuan = {-1, -1, -1, -1, -1, -1};

   // prices
   /** scales prices linearly */
   protected static float priceMult = 1.0f;
   /** pushes prices closer together or farther apart */
   protected static float priceSpread = 1.0f;
   /** the highest ware price may increase based on quantity, 2.0 = 2x after falling below quanDeficient */
   protected static float priceCeiling = 2.0f;
   /** the lowest ware price may decrease based on quantity, 0.0 = free after surpassing quanExcessive */
   protected static float priceFloor = 0.0f;
   /** processed wares (ex: charcoal) have prices adjusted by this multiplier */
   protected static float priceProcessed = 1.1f;
   /** crafted wares (ex: piston) have prices adjusted by this multiplier */
   protected static float priceCrafted = 1.2f;
   /** changes the cost of purchasing a ware, but keeps selling the same; 1.2 == 20% higher price when buying than selling */
   public    static float priceBuyUpchargeMult = 1.0f;

   // miscellaneous
   /** how much money an account should start with */
   protected static float accountStartingMoney = 0.0f;
   /** how many accounts a single player is allowed to create */
   protected static int accountMaxCreatedByIndividual = 3;
   /**
    * max tolerance for how long a crafting chain may go, where a crafted item is crafted using another crafted item, which is crafted using another crafted item, and so forth
    * Ex: 5 means allows loading Item6, where Item6 is crafted using Item5, which uses Item4, which uses Item3, which uses Item2, which uses Item1. However, 5 would flag an error for Item7 since Item7's crafting chain would be too long.
    */
   protected static int maxCraftingDepth = 10;
   /** disables automatically saving wares and accounts when the world is saved */
   public static boolean disableAutoSaving = false;
   /** whether or not to only check base ware IDs when checking ware existence outside the market */
   public static boolean itemExistenceCheckIgnoresMeta = false;
   /** whether wares which are not in the market may be sold using a Forge OreDictionary name it shares with a ware in the market */
   public static boolean allowOreDictionarySubstitution = true;
   /** whether to print warnings for not finding Forge OreDictionary names used by alternative aliases */
   public static boolean oreDictionaryReportInvalid = false;

   // repeatedly-used constants
   /** adjusted multiplier for price floor */
   protected static float priceFloorAdjusted   = 1.0f - priceFloor;
   /** adjusted multiplier for price ceiling */
   protected static float priceCeilingAdjusted = 1.0f - priceCeiling;

   // AI
   /** whether AI should be used */
   public static boolean enableAI = false;
   /** which AI professions should be used */
   public static String[] activeAI = null;
   /** how often AI should trade, in minutes */
   public static int aiTradeFrequency = 3600000; // 60 minutes
   /** how many units AI should buy or sell per trade, in percentage of equilibrium stock */
   public static float aiTradeQuantityPercent = 0.05f;
   /** how randomized AI trade decisions should be */
   public static float aiRandomness = 0.05f;
   /** whether to print warnings for AI professions' ware IDs or aliases not found within the marketplace */
   public static boolean aiReportInvalidWares = false;
   /** if true, AI pay transaction fees just like players do */
   public static boolean aiShouldPayTransactionFees = true;

   // industrial research
   /** decreasing a ware's hierarchy level by 1 costs this much, depending on the ware's level */
   public static float researchCostPerHierarchyLevel = 185.0f;
   /** if true, the cost of researching a ware is a multiplier applied to the market's current price average */
   public static boolean researchCostIsAMultOfAvgPrice = true;

   // linked prices
   /** if true, components' current prices affect created wares' current prices */
   public static boolean shouldComponentsCurrentPricesAffectWholesPrice = true;
   /** how much components' prices may affect a created ware's price */
   public static float linkedPricesPercent = 0.75f;

   // manufacturing contracts
   /** if true, out-of-stock processed/crafted wares may be purchased
    *  if their components have enough available stock on the market */
   protected static boolean buyingOutOfStockWaresAllowed = true;
   /** how much to charge for purchasing out-of-stock processed/crafted wares */
   protected static float buyingOutOfStockWaresPriceMult = 1.10f;

   // transaction fees
   /** whether or not to charge for buying, selling, and possibly sending */
   protected static boolean chargeTransactionFees = false;
   /** what to say when telling users a fee for purchasing has been applied */
   protected static String transactionFeeBuyingMsg      = CommandEconomy.MSG_TRANSACT_FEE;
   /** what to say when telling users a fee for selling has been applied */
   protected static String transactionFeeSellingMsg     = CommandEconomy.MSG_TRANSACT_FEE;
   /** what to say when telling users a fee for transferring has been applied */
   protected static String transactionFeeSendingMsg     = CommandEconomy.MSG_TRANSACT_FEE;
   /** what to say when telling users a fee for investing has been applied */
   protected static String transactionFeeResearchingMsg = CommandEconomy.MSG_TRANSACT_FEE;
   /** how much to charge per transaction for buying */
   protected static float transactionFeeBuying     = 0.05f;
   /** how much to charge per transaction for selling */
   protected static float transactionFeeSelling     = 0.00f;
   /** how much to charge per transaction for sending */
   protected static float transactionFeeSending     = 0.02f;
   /** how much to charge per transaction for researching */
   protected static float transactionFeeResearching = 0.015f;
   /** if true, transactionFeeBuying is treated as a multiplier,
    *  charging based off of purchases' total prices */
   protected static boolean transactionFeeBuyingIsMult      = true;
   /** if true, transactionFeeSelling is treated as a multiplier,
    *  charging based off of sales' total prices */
   protected static boolean transactionFeeSellingIsMult     = true;
   /** if true, transactionFeeSending is treated as a multiplier,
    *  charging based off of total funds transferred */
   protected static boolean transactionFeeSendingIsMult     = true;
   /** if true, transactionFeeResearching is treated as a multiplier,
    *  charging based off of total funds transferred */
   protected static boolean transactionFeeResearchingIsMult = true;
   /** the account which transaction fees are paid to */
   protected static String transactionFeesAccount = CommandEconomy.TRANSACT_FEE_COLLECTION;
   /** if true, money from paid fees is put into transactionFeeAccount */
   protected static boolean transactionFeesShouldPutFeesIntoAccount = true;

   // random events
   /** if true, periodic events will summon or destroy wares' quantities for sale */
   public static boolean randomEvents = false;
   /** how often random events should occur on average */
   public static int randomEventsFrequency = 10800000; // 180 minutes
   /** percentage for how much events' occurrences may drift from the average */
   public static float randomEventsVariance = 0.25f;
   /** how much a small change in a ware's quantity for sale should be */
   public static float randomEventsSmallChange  = 0.05f;
   /** how much a medium change in a ware's quantity for sale should be */
   public static float randomEventsMediumChange = 0.10f;
   /** how much a large change in a ware's quantity for sale should be */
   public static float randomEventsLargeChange  = 0.15f;
   /** if true, random events' changes in quantities for sale are percentages of equilibrium quantity */
   public static boolean randomEventsAreChangesPercents = true;
   /** if true, random events display ware IDs affected by a recent event */
   public static boolean randomEventsPrintChanges = false;
   /** whether to print warnings for events' ware IDs or aliases not found within the marketplace */
   public static boolean randomEventsReportInvalidWares = false;

   // automatic market rebalancing
   /** whether stock levels should bring themselves to equilibrium by periodically increasing or decreasing */
   public static boolean automaticStockRebalancing = false;
   /** how often stock levels should change to rebalance themselves; measured in minutes */
   public static int automaticStockRebalancingFrequency = 2700000; // 45 minutes
   /** what percentage of equilibrium stock levels should change per rebalancing event */
   public static float automaticStockRebalancingPercent = 0.005f;

   // account interest
   /** if true, account funds experience compound interest */
   public static boolean accountPeriodicInterestEnabled = false;
   /** interest rate accounts experience compound interest at */
   public static float accountPeriodicInterestPercent = 1.015f;
   /** how often compound interest is compounded */
   public static int accountPeriodicInterestFrequency = 7200000; // 120 minutes
   /** if true, interest is only applied when account owners are logged onto the server */
   public static boolean accountPeriodicInterestOnlyWhenPlaying = false;

   // no garbage disposing
   /** if true, wares with prices at or below the price floor cannot be sold */
   public static boolean noGarbageDisposing = false;

   // planned economy
   /** true means it is truly a command economy */
   public static boolean pricesIgnoreSupplyAndDemand = false;

   /**
    * Sets a config option to a given value or prints an error.
    * <p>
    * Complexity: O(1)
    * @param configOption field name of the config option to set
    * @param value        number to set config option to
    */
   public static void setConfig(String configOption, float value) {
      if (Float.isNaN(value))
         return;

      switch (configOption) {
         case "startQuanMult":
            startQuanMult = value;
            break;
         case "startQuanSpread":
            startQuanSpread = value;
            break;
         case "priceMult":
            priceMult = value;
            break;
         case "priceSpread":
            priceSpread = value;
            break;
         case "priceCeiling":
            priceCeiling     = value;
            priceCeilingAdjusted = 1.0f - value;
            break;
         case "priceFloor":
            priceFloor     = value;
            priceFloorAdjusted = 1.0f - value;
            break;
         case "priceProcessed":
            priceProcessed = value;
            break;
         case "priceCrafted":
            priceCrafted = value;
            break;
         case "priceBuyUpchargeMult":
            priceBuyUpchargeMult = value;
            break;
         case "accountStartingMoney":
            accountStartingMoney = value;
            break;
         case "accountMaxCreatedByIndividual":
            accountMaxCreatedByIndividual = (int) value;
            break;
         case "maxCraftingDepth":
            maxCraftingDepth = (int) value;
            break;

         case "aiTradeFrequency":
            if (value < 0.0f)
               value = -value;
            else if (value > 35791.0f)
               value = 35791;
            aiTradeFrequency = (int) value * 60000;  // 60000 milliseconds per minute
            break;
         case "aiTradeQuantityPercent":
            aiTradeQuantityPercent = value;
            break;
         case "aiRandomness":
            aiRandomness = value;
            break;

         case "researchCostPerHierarchyLevel":
            researchCostPerHierarchyLevel = value;
            break;

         case "linkedPricesPercent":
            linkedPricesPercent = value;
            break;

         case "buyingOutOfStockWaresPriceMult":
            buyingOutOfStockWaresPriceMult = value;
            break;

         case "transactionFeeBuying":
            transactionFeeBuying = value;
            break;
         case "transactionFeeSelling":
            transactionFeeSelling = value;
            break;
         case "transactionFeeSending":
            transactionFeeSending = value;
            break;
         case "transactionFeeResearching":
            transactionFeeResearching = value;
            break;

         case "randomEventsFrequency":
            if (value < 0.0f)
               value = -value;
            else if (value > 35791.0f)
               value = 35791;
            randomEventsFrequency = (int) value * 60000;  // 60000 milliseconds per minute
            break;
         case "randomEventsVariance":
            if (value < 0.0f)
               value = -value;
            if (value >  1.0f)
               randomEventsVariance = 1.0f;
            else
               randomEventsVariance = value;
            break;
         case "randomEventsSmallChange":
            randomEventsSmallChange = value;
            break;
         case "randomEventsMediumChange":
            randomEventsMediumChange = value;
            break;
         case "randomEventsLargeChange":
            randomEventsLargeChange = value;
            break;

         case "automaticStockRebalancingPercent":
            automaticStockRebalancingPercent = value;
            break;
         case "automaticStockRebalancingFrequency":
            if (value < 0.0f)
               value = -value;
            else if (value > 35791.0f)
               value = 35791;
            automaticStockRebalancingFrequency = (int) value * 60000;  // 60000 milliseconds per minute
            break;

         case "accountPeriodicInterestPercent":
            accountPeriodicInterestPercent = 1.0f + (value / 100.0f);
            break;
         case "accountPeriodicInterestFrequency":
            if (value < 0.0f)
               value = -value;
            else if (value > 35791.0f)
               value = 35791;
            accountPeriodicInterestFrequency = (int) value * 60000;  // 60000 milliseconds per minute
            break;

         default:
            commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_SET + configOption +
                                            CommandEconomy.ERROR_CONFIG_OPTION_VALUE + value);
      }
   }

   /**
    * Sets a config option to a given value or prints an error.
    * <p>
    * Complexity: O(1)
    * @param configOption field name of the config option to set
    * @param values       array of numbers to set config option to
    */
   public static void setConfig(String configOption, int[] values) {
      switch (configOption) {
         case "startQuanBase":
            startQuanBase = values;
            break;
         case "quanExcessive":
            quanExcessive = values;
            break;
         case "quanDeficient":
            quanDeficient = values;
            break;
         case "quanEquilibrium":
            quanEquilibrium = values;
            break;

         default:
            commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_SET + configOption);
      }
   }

   /**
    * Sets a config option to a given value or prints an error.
    * <p>
    * Complexity: O(1)
    * @param configOption field name of the config option to set
    * @param value        true or false to set config option to
    */
   public static void setConfig(String configOption, boolean value) {
      switch (configOption) {
         case "itemExistenceCheckIgnoresMeta":
            itemExistenceCheckIgnoresMeta = value;
            break;
         case "disableAutoSaving":
            disableAutoSaving = value;
            break;
         case "allowOreDictionarySubstitution":
            allowOreDictionarySubstitution = value;
            break;
         case "oreDictionaryReportInvalid":
            oreDictionaryReportInvalid = value;
            break;
         case "crossWorldMarketplace":
            crossWorldMarketplace = value;
            break;

         case "enableAI":
            enableAI = value;
            break;
         case "aiReportInvalidWares":
            aiReportInvalidWares = value;
            break;
         case "aiShouldPayTransactionFees":
            aiShouldPayTransactionFees = value;
            break;

         case "researchCostIsAMultOfAvgPrice":
            researchCostIsAMultOfAvgPrice = value;
            break;

         case "shouldComponentsCurrentPricesAffectWholesPrice":
            shouldComponentsCurrentPricesAffectWholesPrice = value;
            break;

         case "buyingOutOfStockWaresAllowed":
            buyingOutOfStockWaresAllowed = value;
            break;

         case "chargeTransactionFees":
            chargeTransactionFees = value;
            break;
         case "transactionFeeBuyingIsMult":
            transactionFeeBuyingIsMult = value;
            break;
         case "transactionFeeSellingIsMult":
            transactionFeeSellingIsMult = value;
            break;
         case "transactionFeeSendingIsMult":
            transactionFeeSendingIsMult = value;
            break;
         case "transactionFeeResearchingIsMult":
            transactionFeeResearchingIsMult = value;
            break;
         case "transactionFeesShouldPutFeesIntoAccount":
            transactionFeesShouldPutFeesIntoAccount = value;
            break;

         case "randomEvents":
            randomEvents = value;
            break;
         case "randomEventsAreChangesPercents":
            randomEventsAreChangesPercents = value;
            break;
         case "randomEventsPrintChanges":
            randomEventsPrintChanges = value;
            break;
         case "randomEventsReportInvalidWares":
            randomEventsReportInvalidWares = value;
            break;

         case "automaticStockRebalancing":
            automaticStockRebalancing = value;
            break;

         case "accountPeriodicInterestEnabled":
            accountPeriodicInterestEnabled = value;
            break;
         case "accountPeriodicInterestOnlyWhenPlaying":
            accountPeriodicInterestOnlyWhenPlaying = value;
            break;

         case "noGarbageDisposing":
            noGarbageDisposing = value;
            break;

         case "pricesIgnoreSupplyAndDemand":
            pricesIgnoreSupplyAndDemand = value;
            break;

         default:
            commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_SET + configOption);
      }
   }

   /**
    * Sets a config option to a given value or prints an error.
    * <p>
    * Complexity: O(1)
    * @param configOption field name of the config option to set
    * @param value        text to set config option to
    */
   public static void setConfig(String configOption, String value) {
      switch (configOption) {
         case "filenameWares":
            filenameNoPathWares     = value;
            break;
         case "filenameWaresSave":
            filenameNoPathWaresSave = value;
            break;
         case "filenameAccounts":
            filenameNoPathAccounts  = value;
            break;
         case "filenameMarket":
            filenameMarket          = "config" + File.separator + "CommandEconomy" + File.separator + value;
            break;

         case "filenameAIProfessions":
            filenameNoPathAIProfessions = value;
            break;

         case "transactionFeeBuyingMsg":
            transactionFeeBuyingMsg  = "   " + value;
            break;
         case "transactionFeeSellingMsg":
            transactionFeeSellingMsg = "   " + value;
            break;
         case "transactionFeeSendingMsg":
            transactionFeeSendingMsg = "   " + value;
            break;
         case "transactionFeeResearchingMsg":
            transactionFeeResearchingMsg = "   " + value;
            break;
         case "transactionFeesAccount":
            transactionFeesAccount   = value;
            break;

         case "filenameRandomEvents":
            filenameNoPathRandomEvents = value;
            break;

         default:
            commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_SET + configOption
                                            + CommandEconomy.ERROR_CONFIG_OPTION_VALUE + value);
      }
   }

   /**
    * Sets global variables based on settings in the config file. If no file is found, default settings are used.
    * <p>
    * Complexity: O(n)
    */
   public static void loadConfig() {
      // set up Command Economy configuration if they do not exist
      createConfig();

      // try to load the local config file
      File fileConfig = new File(commandInterface.getSaveDirectory() + File.separator + filenameConfig);

      // check file existence
      if (!fileConfig.isFile()) {
         // if the local file doesn't exist,
         // try to load the global file
         fileConfig = new File("config" + File.separator + filenameConfig);
      }

      // check file existence
      if (!fileConfig.isFile()) {
         // don't throw an exception, print a warning to advise user to reload config
         commandInterface.printToConsole(CommandEconomy.WARN_FILE_MISSING + filenameConfig
                              + System.lineSeparator() + "To load custom settings, replace " + filenameConfig
                              + "," + System.lineSeparator() + "then use the command \"reload config\"."
         );
         return;
      }

      // track changes to filenames to potentially avoid regenerating them
      boolean oldCrossWorldMarketplace       = crossWorldMarketplace;
      String  oldFilenameNoPathWares         = filenameNoPathWares;
      String  oldFilenameNoPathWaresSave     = filenameNoPathWaresSave;
      String  oldFilenameNoPathAccounts      = filenameNoPathAccounts;
      String  oldFilenameNoPathAIProfessions = filenameNoPathAIProfessions;
      String  oldFilenameNoPathRandomEvents  = filenameNoPathRandomEvents;

      // track changes to equilibrium quantities
      // for features depending on equilibriums
      int[] oldQuanEquilibrium = new int[quanEquilibrium.length];
      System.arraycopy(quanEquilibrium, 0, oldQuanEquilibrium, 0, oldQuanEquilibrium.length);

     // save ware price multipliers to the side
     // to track whether ware prices need to be recalculated
     float priceProcessedOld = priceProcessed;
     float priceCraftedOld   = priceCrafted;

     // open the file
     Scanner fileReader;
     try {
        fileReader = new Scanner(fileConfig);
     }
     catch (FileNotFoundException e) {
        commandInterface.printToConsole(CommandEconomy.WARN_FILE_MISSED + filenameConfig);
        e.printStackTrace();
        return;
     }

     // load default configuration
     resetConfig();

     // set up variables for parsing config options
     float input       = 0.0f;  // for individual values
     String[] inputArray; // for arrays of values

     // parse through the file line-by-line
     String[] data = {""}; // holds pointer to line being parsed
     while (fileReader.hasNextLine()) {
         // grab line to be parsed
         data[0] = fileReader.nextLine();

        // if the line is blank, skip it
        if (data[0] == null || data[0].isEmpty())
           continue;

        // if the line is a comment, skip it
        if (data[0].startsWith("//"))
           continue;

         // split line using equals sign
         data = data[0].split("=", 0);

         // if line has no or multiple equal signs, something is wrong
         if (data.length != 2) {
            commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_FORMAT + data[0]);
            continue;
         }

         // remove whitespace
         data[0] = data[0].trim();                  // remove trailing and leading whitespace from config option to be set
         data[1] = data[1].replaceFirst("\\s+",""); // remove leading whitespace from intended config setting
                                                    // only removing leading whitespace allows for configuring trailing whitespace when setting strings

         // attempt to set the appropriate config variable
        try {
           // if option being set is an array representing quantities,
           // load it accordingly
           if (!data[0].equals("startQuanMult") && !data[0].equals("startQuanSpread")
              && (data[0].startsWith("quan")
                  || data[0].startsWith("startQuan"))) {
               // if the data is an array of values, remove all whitespace to ease splitting
               data[1] = data[1].replaceAll("\\s+","");

              // split the array in separate values
              inputArray = data[1].split(",", 0);

              // if the array's size does not match config arrays,
              // report an error and move on
              if (inputArray.length != 6) {
                 commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_LOAD + data[0] + CommandEconomy.ERROR_CONFIG_OPTION_ARRAY + inputArray.length);
                 continue;
              }

              // convert given quantities into usable integers
              int[] inputQuantities = {0, 0, 0, 0, 0, 0}; // array must be allocated here to ensure the pointer to the array isn't reused
              inputQuantities[0] = Integer.parseInt(inputArray[0]);
              inputQuantities[1] = Integer.parseInt(inputArray[1]);
              inputQuantities[2] = Integer.parseInt(inputArray[2]);
              inputQuantities[3] = Integer.parseInt(inputArray[3]);
              inputQuantities[4] = Integer.parseInt(inputArray[4]);
              inputQuantities[5] = Integer.parseInt(inputArray[5]);

              // grab and set the corresponding config option
              setConfig(data[0], inputQuantities);
            }
            // for AI professions
            else if (data[0].equals("activeAI")) {
               // if the data is an array of values, remove all whitespace to ease splitting
               data[1] = data[1].replaceAll("\\s+","");

              // split the array in separate values and set the corresponding config option
              activeAI = data[1].split(",", 0);
            }
           // for individual values
           else {
              // if the value is not a float, it's probably meant to be a boolean or a string
              try {
                 // if the value is a float
                 input = Float.parseFloat(data[1]); // if value is not a float, exception will be thrown here
                 setConfig(data[0], input);
              } catch (Exception e) {
                  // if the value is not a float
                  if (data[1].equalsIgnoreCase(CommandEconomy.TRUE) || data[1].equalsIgnoreCase(CommandEconomy.FALSE))
                     setConfig(data[0], Boolean.parseBoolean(data[1]));
                  else
                     setConfig(data[0], data[1]);
              }
           }
        } catch (NumberFormatException e) {
           commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_LOAD + data[0] + CommandEconomy.ERROR_CONFIG_OPTION_PARSING + data[1]);
           continue;
        } catch (Exception e) {
           commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_OPTION_LOAD + data[0]);
           continue;
        }
     }

      // close the file
      fileReader.close();

      // ensure file paths are correct
      String path;
      boolean regenWares         = !oldFilenameNoPathWares.equals(filenameNoPathWares);
      boolean regenWaresSave     = !oldFilenameNoPathWaresSave.equals(filenameNoPathWaresSave);
      boolean regenAccounts      = !oldFilenameNoPathAccounts.equals(filenameNoPathAccounts);
      boolean regenAIProfessions = !oldFilenameNoPathAIProfessions.equals(filenameNoPathAIProfessions);
      boolean regenRandomEvents  = !oldFilenameNoPathRandomEvents.equals(filenameNoPathRandomEvents);
      // check whether file paths need to be regenerated
      if (regenWares || regenWaresSave || regenAccounts || regenAIProfessions || regenRandomEvents ||
          oldCrossWorldMarketplace != crossWorldMarketplace) {
         if (crossWorldMarketplace)
            path = "config" + File.separator + "CommandEconomy" + File.separator;
         else
            path = commandInterface.getSaveDirectory() + File.separator + "CommandEconomy" + File.separator;

         if (regenWares         || oldCrossWorldMarketplace != crossWorldMarketplace)
            filenameWares         = path + filenameNoPathWares;
         if (regenWaresSave     || oldCrossWorldMarketplace != crossWorldMarketplace)
            filenameWaresSave     = path + filenameNoPathWaresSave;
         if (regenAccounts      || oldCrossWorldMarketplace != crossWorldMarketplace)
            filenameAccounts      = path + filenameNoPathAccounts;
         if (regenAIProfessions || oldCrossWorldMarketplace != crossWorldMarketplace)
            filenameAIProfessions = path + filenameNoPathAIProfessions;
         if (regenRandomEvents  || oldCrossWorldMarketplace != crossWorldMarketplace)
            filenameRandomEvents  = path + filenameNoPathRandomEvents;
      }

      // check for changes to equilibrium quantities
      boolean equilibriumChanged = false;
      for (int i = 0; i < 6; i++) {
         if (oldQuanEquilibrium[i] != quanEquilibrium[i]) {
            equilibriumChanged = true;
            i = 6; // exit loop
         }
      }

      // if equilibrium quantities have changes,
      // let features depending on them know
      if (equilibriumChanged) {
         // if AI is enabled,
         // calculate trade amounts
         if (enableAI)
            AIHandler.calcQuantityChanges();

         // if random events are enabled,
         // calculate changes for wares' quantities for sale
         if (randomEvents)
             RandomEvents.calcQuantityChanges();

         // if periodically moving wares toward equilibrium is enabled,
         // calculate changes for wares' quantities for sale
         if (automaticStockRebalancing)
            MarketRebalancer.calcQuantityChanges();
      }

      // if the price floor is higher than the price ceiling, swap them
      if (priceFloor > priceCeiling) {
         priceCeilingAdjusted = priceFloor;
         priceFloor           = priceCeiling;
         priceCeiling         = priceCeilingAdjusted;
         priceCeilingAdjusted = 1.0f - priceCeiling;
         priceFloorAdjusted   = 1.0f - priceFloor;
      }

     // recalculate ware prices if necessary
     if (priceCrafted != priceCraftedOld || priceProcessed != priceProcessedOld) {
         Marketplace.reloadAllComponents();
     }

      // change or close any threads needed for features based on configuration settings
      Marketplace.startOrReconfigPeriodicEvents();
      Account.startOrReconfigPeriodicEvents();
   }

   /**
    * Sets configuration to default values.
    * <p>
    * Complexity: O(1)
    */
   public static void resetConfig() {
      // miscellaneous
      itemExistenceCheckIgnoresMeta = false;
      accountStartingMoney          = 0.0f;
      accountMaxCreatedByIndividual = 3;
      crossWorldMarketplace         = false;

      // files
      filenameNoPathWares         = "wares.txt";
      filenameNoPathWaresSave     = "waresSaved.txt";
      filenameNoPathAccounts      = "accounts.txt";
      filenameNoPathAIProfessions = "aiProfessions.json";
      filenameNoPathRandomEvents  = "randomEvents.json";

      // prices
      priceMult            =  1.0f;
      priceSpread          =  1.0f;
      priceBuyUpchargeMult =  1.0f;
      priceCeiling         =  2.0f;
      priceCeilingAdjusted = -1.0f;
      priceFloor           =  0.0f;
      priceFloorAdjusted   =  1.0f;
      priceProcessed       =  1.1f;
      priceCrafted         =  1.2f;

      // hierarchy
      startQuanBase   = new int[]{16384,  9216,  5120,  3072, 2048, 1024};
      quanExcessive   = new int[]{65536, 43008, 14336, 10240, 6144, 3072};
      quanEquilibrium = new int[]{16384,  9216,  5120,  3072, 2048, 1024};
      quanDeficient   = new int[]{ 4096,  2048,  1536,  1024,  768,  512};

      // starting quantity
      startQuanMult   = 1.0f;
      startQuanSpread = 1.0f;
      startQuan       = new int[]{-1, -1, -1, -1, -1, -1};

      // miscellaneous
      disableAutoSaving = false;
      allowOreDictionarySubstitution = true;
      oreDictionaryReportInvalid = false;
      maxCraftingDepth  = 10;

      // AI
      enableAI                   = false;
      activeAI                   = null;
      aiTradeFrequency           = 3600000;
      aiTradeQuantityPercent     = 0.05f;
      aiRandomness               = 0.05f;
      aiReportInvalidWares       = false;
      aiShouldPayTransactionFees = true;

      // research
      researchCostPerHierarchyLevel = 185.0f;
      researchCostIsAMultOfAvgPrice = true;

      // linked prices
      shouldComponentsCurrentPricesAffectWholesPrice = true;
      linkedPricesPercent   = 0.75f;

      // manufacturing contracts
      buyingOutOfStockWaresAllowed   = true;
      buyingOutOfStockWaresPriceMult = 0.10f;

      // transaction fees
      chargeTransactionFees           = false;
      transactionFeeBuyingMsg         = CommandEconomy.MSG_TRANSACT_FEE;
      transactionFeeSellingMsg        = CommandEconomy.MSG_TRANSACT_FEE;
      transactionFeeSendingMsg        = CommandEconomy.MSG_TRANSACT_FEE;
      transactionFeeResearchingMsg    = CommandEconomy.MSG_TRANSACT_FEE;
      transactionFeeBuying            = 0.05f;
      transactionFeeSelling           = 0.00f;
      transactionFeeSending           = 0.02f;
      transactionFeeResearching       = 0.015f;
      transactionFeeBuyingIsMult      = true;
      transactionFeeSellingIsMult     = true;
      transactionFeeSendingIsMult     = true;
      transactionFeeResearchingIsMult = true;
      transactionFeesAccount          = CommandEconomy.TRANSACT_FEE_COLLECTION;
      transactionFeesShouldPutFeesIntoAccount = true;

      // random events
      randomEvents                   = false;
      randomEventsFrequency          = 10800000;
      randomEventsVariance           = 0.25f;
      randomEventsSmallChange        = 0.05f;
      randomEventsMediumChange       = 0.10f;
      randomEventsLargeChange        = 0.15f;
      randomEventsAreChangesPercents = true;
      randomEventsPrintChanges       = false;
      randomEventsReportInvalidWares = false;

      // automatic market rebalancing
      automaticStockRebalancing          = false;
      automaticStockRebalancingFrequency = 2700000;
      automaticStockRebalancingPercent   = 0.005f;

      // account interest
      accountPeriodicInterestEnabled         = false;
      accountPeriodicInterestPercent         = 1.015f;
      accountPeriodicInterestFrequency       = 7200000;
      accountPeriodicInterestOnlyWhenPlaying = false;

      // no garbage disposing
      noGarbageDisposing = false;

      // planned economy
      pricesIgnoreSupplyAndDemand = false;
   }

   /**
    * If necessary, creates configuration folders and files for Command Economy
    * <p>
    * Complexity: O(1)
    */
   public static void createConfig() {
      // check existence of Command Economy main config folder
      // create it if it doesn't exist
      File directoryGlobal = new File("config" + File.separator + "CommandEconomy");
      if (!directoryGlobal.exists()){
         directoryGlobal.mkdirs();
      }

      // check existence of main config file
      // create it if it doesn't exist
      File fileConfig = new File("config" + File.separator + filenameConfig);
      if (!fileConfig.exists()){
         // try to fill the file with default values
         try {
            FileWriter fileWriter = new FileWriter("config" + File.separator + filenameConfig, false);
            fileWriter.write("// ===Prices:===\n// =Global:=\n// scales prices linearly\npriceMult = 1.0\n// pushes prices closer together or farther apart\npriceSpread = 1.0\n\n// the highest a ware's price may increase based on stock quantity\n// 2.0 == 2x price base after stock falls below quanDeficient\npriceCeiling = 2.0\n// the lowest a ware's price may decrease based on stock quantity\n// 0.0 == free after stock surpasses quanExcessive\npriceFloor = 0.0\n\n// =Targeted Multipliers:=\n// processed wares' prices are adjusted by this multiplier\n// ex: charcoal's price == wood's price * priceProcessed\npriceProcessed = 1.5\n// crafted wares' prices are adjusted by this multiplier\n// ex: piston's price == sum of piston's components' prices * priceCrafted\npriceCrafted = 1.2\n\n// multiplies the cost of purchasing a ware, but keeps selling the same\n// 1.2 == 20% higher price when buying than selling\npriceBuyUpchargeMult = 1.0\n\n// if true, out-of-stock processed/crafted wares may be purchased\n// if their components have enough available stock on the market\nbuyingOutOfStockWaresAllowed = true\n\n// how much to charge for purchasing out-of-stock processed/crafted wares\n// this charge is in addition to processed/crafted price multipliers\n// 1.10 == +10% out-of-stock price\nbuyingOutOfStockWaresPriceMult = 1.10\n\n// =Linked Prices:=\n// if true, current prices of components used to create a ware\n// affect the current price of the created ware,\n// even if the ware cannot be reverted into its components\n// ex: if wood is scarce, charcoal automatically costs more\nshouldComponentsCurrentPricesAffectWholesPrice = true\n\n// the most components' prices may affect a created ware's price\n// ex: 0.75 == can lower created's price as much as 75% and\n// can raise created's price by 75% of components' prices\nlinkedPricesPercent = 0.75\n\n// =Transaction Fees:=\n// whether or not to charge for buying, selling, or sending\nchargeTransactionFees = false\n\n// how much to charge per transaction for buying/etc.\n// 0.05 == fee is 5% of total price or $0.05\ntransactionFeeBuying    = 0.05\ntransactionFeeSelling   = 0.00\ntransactionFeeSending   = 0.02\ntransactionFeeResearching = 0.015\n\n// if true, transactionFee is treated as a multiplier,\n// charging based off of purchases' total prices \n// if false, transactionFee is treated as a flat rate\ntransactionFeeBuyingIsMult    = true\ntransactionFeeSellingIsMult   = true\ntransactionFeeSendingIsMult   = true\ntransactionFeeResearchingIsMult = true\n\n// what to say when telling users a fee for\n// purchasing/selling/transferring has been applied\ntransactionFeeBuyingMsg    = Sales tax paid: \ntransactionFeeSellingMsg   = Income tax paid: \ntransactionFeeSendingMsg   = Transfer fee applied: \ntransactionFeeResearchingMsg = Brokerage fee applied: \n\n// if true, money from fees is put into transactionFeeAccount\ntransactionFeesShouldPutFeesIntoAccount = true\n\n// the account which transaction fees are paid to\n// if this account doesn't exist,\n// an inaccessible account is made\ntransactionFeesAccount = cumulativeTransactionFees\n\n// ===Wares' Quantities for Sale:===\n// =Supply and Demand:=\n// quantity > this is considered saturated\nquanExcessive   = 65536, 43008, 14336, 10240, 6144, 3072\n// quantity = this is considered balanced\nquanEquilibrium = 16384,  9216,  5120,  3072, 2048, 1024\n// quantity < this is considered scarce\nquanDeficient   =  4096,  2048,  1536,  1024,  768,  512\n\n// true means it is truly a command economy\npricesIgnoreSupplyAndDemand = false\n\n// if true, wares with prices at or below\n// the price floor cannot be sold\nnoGarbageDisposing = false\n\n// =Starting Quantities:=\n// starting stock for each level\nstartQuanBase = 16384, 9216, 5120, 3072, 2048, 1024\n// scales starting stock linearly\nstartQuanMult = 1.0\n// pushes starting stock levels closer together or farther apart\nstartQuanSpread = 1.0\n\n// =Investment:=\n// Investments into industrial research and manufacturing\n// increase a ware's supply and demand, reduces price fluctuations,\n// and resets quantity available for sale to equilibrium.\n\n// investing in a ware costs increases this much\n// per ware hierarchy level (represents rarity)\n// set to 0 to disable this feature\nresearchCostPerHierarchyLevel = 185.0\n\n// if true, the cost of investing in a ware is a multiplier\n// applied to the market's current price average\nresearchCostIsAMultOfAvgPrice = true\n\n// =Automatic Market Rebalancing:=\n// whether stock levels should bring themselves\n// to equilibrium by periodically increasing or decreasing\nautomaticStockRebalancing = false\n\n// how often quantities for sale should change to rebalance themselves\n// 45 == change every 45 minutes\nautomaticStockRebalancingFrequency = 45\n\n// how much quantities for sale should change per rebalancing event\n// 0.005 == 0.5% of equilibrium quantity\nautomaticStockRebalancingPercent = 0.005\n\n// ===Ware-Handling:===\n// contains wares to be tradeable within the marketplace\nfilenameWares = wares.txt\n\n// save file containing tradeable wares within the marketplace\n// if this file exists, it is loaded instead of filenameWares\nfilenameWaresSave = waresSaved.txt\n\n// if true, checking ware IDs for corresponding items\n// existing within Minecraft does not check metadata\n// useful for mods which do not register items properly\n// bad for validating ware entries since it may\n// allow loading wares which don't exist\nitemExistenceCheckIgnoresMeta = false\n\n// whether wares which are not in the market\n// may be sold using a Forge OreDictionary name\n// it shares with a ware in the market\n// ex: sell different copper ingots from multiple mods,\n// pretending they are all from the mod\n// whose copper ingot is in the market\nallowOreDictionarySubstitution = true\n\n// whether to print warnings for not finding\n// Forge OreDictionary names used by alternative aliases\noreDictionaryReportInvalid = false\n\n// max tolerance for how long a crafting chain may go, where a crafted item is crafted using another crafted item, which is crafted using another crafted item, and so forth\n// Ex: 5 means allows loading Item6, where Item6 is crafted using Item5, which uses Item4, which uses Item3, which uses Item2, which uses Item1. However, 5 would flag an error for Item7 since Item7's crafting chain would be too long.\nmaxCraftingDepth = 10\n\n// ===Accounts:===\n// how much money an account should start with\naccountStartingMoney = 0.0\n\n// how many accounts a single player is allowed to create\n// 0 == no new accounts except default, personal ones\n// -1 == no restriction or infinity accounts\naccountMaxCreatedByIndividual = 3\n\n// contains accounts usable within the marketplace\nfilenameAccounts = accounts.txt\n\n// =Interest:=\n// if true, account funds experience compound interest\naccountPeriodicInterestEnabled = false\n\n// interest rate at which account funds are compounded\n// 1.5 == 1.5%; accurate to 0.01\naccountPeriodicInterestPercent = 1.5\n\n// how often compound interest is applied\n// dedicated server recommended: 120 == 2 hours\n// singleplayer recommended: 15 == 15 minutes\naccountPeriodicInterestFrequency = 120\n\n// if true, interest is only applied when\n// account owners are logged onto the server\naccountPeriodicInterestOnlyWhenPlaying = false\n\n// ===Administrative:===\n// All files except filenameMarket can be saved\n// in a world's directory in ../CommandEconomy/\n// or the Minecraft game directory\n// in ../config/CommandEconomy/.\n// The world's directory is checked for files first\n// unless crossWorldMarketplace is true.\n\n// If true, ware and account save files\n// will be shared across all worlds.\n// To exclude a world while set to true,\n// go to the world's Command Economy directory\n// and create a local config file named \"config.txt\".\n// As long as this config file exists,\n// that world will load it\n// instead of the main config file.\ncrossWorldMarketplace = false\n\n// output file for printing wares within the marketplace\n// \"/printMarket\" to print to this file\nfilenameMarket = market.txt\n\n// disables automatically saving wares and accounts when the world is saved\ndisableAutoSaving = false\n\n// ===Additional Factors:===\n// =AI:=\n// whether AI should be used\nenableAI = false\n\n// which AI professions should be used\n// repeats increase the number of times a profession trades per event\nactiveAI = armorer, cleric, farmer, farmer, fletcher, librarian\n\n// how often AI should trade, in minutes\naiTradeFrequency = 60\n\n// how many units AI should buy or sell per trade\n// in percentage of equilibrium stock\n// ex: ware's quanEquilibrium = 100 and aiTradeQuantityPercent = 0.05\n// means AI will buy or sell 5 units of the ware at a time\naiTradeQuantityPercent = 0.05\n\n// how randomized AI trade decisions should be\n// 0.0 == trade according to wares' supply and demand\n// 1.0 == trades are mostly unpredictable\naiRandomness = 0.05\n\n// contains possible AI, the wares they may trade,\n// and their preferences\nfilenameAIProfessions = aiProfessions.json\n\n// =Random Events:=\n// whether or not to periodically trigger events\n// summoning or destroying wares' quantities for sale\nrandomEvents = false\n\n// on average, an event should occur every X minutes\nrandomEventsFrequency = 180\n\n// events may occur anywhere from\n// frequency *  (1 - variance) to frequency * variance\n// so if frequency is 180 minutes and variance is 0.25,\n// events could occur anywhere from every 135 minutes to 225 minutes\nrandomEventsVariance = 0.25\n\n// if true, random events display which wares\n// have been affected when an event fires\nrandomEventsPrintChanges = false\n\n// if true, the changes in quantities for sale listed below\n// are considered percentages of equilibrium quantity\n// if false, they are considered flat values\nrandomEventsAreChangesPercents = true\n\n// each random event may affect stock levels\n// up to the amounts listed below\n// 0.15 == 15% of equilibrium or 0 change\n// 15 == 1500% or +/-15 stock for hierarchy level 2 (iron),\n// flat rates scale according to equilibrium stock for other levels\nrandomEventsLargeChange  = 0.15\nrandomEventsMediumChange = 0.10\nrandomEventsSmallChange  = 0.05\n\n// contains possible events, their descriptions,\n// the wares they may affect and how much each ware is affected\nfilenameRandomEvents = randomEvents.json");
            fileWriter.close();
         } catch (IOException e) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_CONFIG_FILE_CREATE + filenameConfig);
            e.printStackTrace();
         }
      }

      // check existence of Command Economy local save folder
      // create it if it doesn't exist
      File directoryLocal = new File(commandInterface.getSaveDirectory() + File.separator + "CommandEconomy");
      if (!directoryLocal.exists()){
         directoryLocal.mkdir();
      }
   }
}