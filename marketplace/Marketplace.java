package commandeconomy;

import java.util.TreeMap;             // for storing wares
import java.util.Map;                 // for iterating through maps
import java.util.TreeSet;             // for returning all ware aliases and storing IDs of wares changed since last save
import java.util.Set;                 // for returning all ware aliases
import java.util.LinkedList;          // for returning properties of wares found in an inventory
import java.util.List;
import java.util.ArrayDeque;          // for storing ware entries for saving
import java.io.BufferedWriter;        // for faster saving, so fewer file writes are used
import java.io.File;                  // for handling files
import java.util.Scanner;             // for parsing files
import java.io.FileWriter;            // for writing to files
import java.io.FileNotFoundException; // for handling missing file errors
import java.io.IOException;           // for handling miscellaneous file errors
import java.util.UUID;                // for more securely tracking users internally
import java.util.Collection;          // for returning all wares within the marketplace
import java.util.Arrays;              // for sorting arrays when finding medians

/**
 * Manages trading and tracking wares for sale.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-01-29
 */
public final class Marketplace {
   // GLOBAL VARIABLES
   // wares
   /** holds all wares in the market */
   private static TreeMap<String, Ware> wares = new TreeMap<String, Ware>();
   /**
    * looks up ware IDs using unique aliases
    *
    * Individual wares still track their own alias to provide easy, fast reverse lookup
    * when writing wares to a file.
    */
   private static TreeMap<String, String> wareAliasTranslations = new TreeMap<String, String>();

   // prices
   /** used to signal what price should be returned */
   enum PriceType {
      CURRENT_SELL,     // current asking price
      CURRENT_BUY,      // current purchasing price
      EQUILIBRIUM_SELL, // asking price without considering supply and demand
      EQUILIBRIUM_BUY,  // purchasing price without considering supply and demand
      FLOOR_SELL,       // lowest the asking price may be
      FLOOR_BUY,        // lowest the purchasing price may be
      CEILING_SELL,     // highest the asking price may be
      CEILING_BUY       // highest the purchasing price may be
   }

   // saving
   /** holds ware entries which failed to load */
   private static ArrayDeque<String> waresErrored = new ArrayDeque<String>();
   /** holds ware IDs whose entries should be regenerated */
   private static Set<String> waresChangedSinceLastSave = new TreeSet<String>();
   /** maps ware IDs to ware entries, easing regenerating changed wares' entries for saving */
   private static TreeMap<String, StringBuilder> wareEntries = new TreeMap<String, StringBuilder>();
   /** holds ware entries in the order they successfully loaded in; makes reloading faster */
   private static ArrayDeque<StringBuilder> waresLoadOrder = new ArrayDeque<StringBuilder>();
   /** holds alternate ware aliases and tags for saving */
   private static StringBuilder alternateAliasEntries = new StringBuilder(955);

   // miscellaneous
   /** median ware starting quantity */
   private static float startQuanBaseMedian = 0.0f;
   /** median ware base price */
   private static float priceBaseMedian = 0.0f;
   /** average ware base price */
   private static double priceBaseAverage = 0.0;
   /** how many wares should be excluded from statistical calculations */
   private static int numStatisticExcludedWares = 0;
   /** a loose mutex used to avoid synchronization problems with threads rarely adjusting wares' properties */
   private static volatile boolean doNotAdjustWares = false;

   // STRUCTS
   /**
    * Used to track quantities of wares with specific qualities.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2021-05-14
    */
   public static class Stock
   {
      /** original ware ID of the item; for removing the correct item from an inventory */
      public String wareID;
      /** ware or substitute within the marketplace */
      public Ware ware;
      /** amount of the ware */
      public int quantity;
      /** quality of the ware as a percentage of its price */
      public float percentWorth;

      /**
       * Fills fields for several units of a ware with the same value
       * found in the same position of an inventory.
       *
       * @param wareID       unique identifier referring to a ware within an inventory
       * @param ware         ware within the marketplace to be manipulated
       * @param quantity     amount of the ware
       * @param percentWorth quality of the ware as a percentage of its price
       */
      public Stock (final String wareID, final Ware ware, final int quantity, final float percentWorth) {
         this.wareID       = wareID;
         this.ware         = ware;
         this.quantity     = quantity;
         this.percentWorth = percentWorth;
      }
   }

   // FUNCTIONS
   /**
    * Inserts/reloads wares into the marketplace based on the wares file.
    * <p>
    * Complexity: O(n log n)
    */
   public static void loadWares() {
      File fileWares; // has market stock information

      // try to load the save file
      fileWares = new File(Config.filenameWaresSave);
      // if the save file doesn't exist, use the local market-starting file
      if (!fileWares.isFile()) {
         fileWares = new File(Config.filenameWares);
      }
      // if the local file isn't found, use the global file
      if (!Config.crossWorldMarketplace && !fileWares.isFile()) {
         fileWares = new File("config" + File.separator + "CommandEconomy" + File.separator + Config.filenameNoPathWares);
      }

      loadWares(fileWares);
   }

   /**
    * Inserts/reloads wares into the marketplace based on the wares file.
    * <p>
    * Complexity: O(n log n)
    * @param fileWares file containing ware entries
    */
   public static void loadWares(File fileWares) {
      // check file existence
      if (!fileWares.isFile()) {
         // don't throw an exception, print a warning to advise user to reload wares
         Config.userInterface.printToConsole(StringTable.WARN_FILE_MISSING + Config.filenameWares +
            System.lineSeparator() + "To load wares, replace " + Config.filenameWares + " or " + Config.filenameWaresSave +
            "," + System.lineSeparator() + "then use the command \"reload wares\"."
         );
         return;
      }

      // prevents other threads from adjusting the marketplace's wares
      acquireMutex();

      // if there are already wares in the market, remove them
      // useful for reloading
      if (!wares.isEmpty()) {
         wares.clear();
         wareAliasTranslations.clear();
         waresErrored.clear();
         waresLoadOrder.clear();
         alternateAliasEntries.setLength(0);
      }

      // for calculating median starting quantity,
      // track number of wares in each level
      int[] hierarchyLevelTotals = new int[]{0, 0, 0, 0, 0, 0};

      // clear ware base statics
      startQuanBaseMedian = 0.0f;
      priceBaseMedian     = 0.0f;
      priceBaseAverage    = 0.0;

      // The variables below are mainly used for saving converted data.
      // Since properly casting data in Java is as expensive as creating a new object,
      // saving data to the side might reduce resource usage
      // if the same data is used multiple times in a casted format.
      // Also, named variables are more readable than data array indices.

      // set up variables for adding wares
      Ware ware               = null;  // current ware being loaded
      String   wareError      = "";    // current ware's validation error message
      String   wareID         = "";    // current ware's internal name
      String   alias          = "";    // holds ware's alternate name
      // holds alternate ware aliases and tags for processing
      ArrayDeque<String> alternateAliasesToBeProcessed = new ArrayDeque<String>();
      // prepare a place to put wares whose components aren't found
      ArrayDeque<Ware> waresWithUnloadedComponents = new ArrayDeque<Ware>();
      boolean duplicateWare   = false; // flag for preventing repeat entries in the ware load order record
      String wareIDUsingAlias = "";    // holds the ware ID using the current ware's alias

      // open the file
      Scanner fileReader;
      try {
         fileReader = new Scanner(fileWares);
      }
      catch (FileNotFoundException e) {
         Config.userInterface.printToConsole(StringTable.WARN_FILE_MISSED + Config.filenameWares);
         e.printStackTrace();
         releaseMutex();
         // signal threads to reload their wares when possible
         if (Config.enableAI)
            AIHandler.loadWares();
         if (Config.randomEvents)
            RandomEvents.loadWares();
         return;
      }

      // parse the file and add wares
      String line; // line being parsed
      while (fileReader.hasNextLine()) {
         line = fileReader.nextLine(); // grab line to be parsed

         // if the line is a comment or blank, skip it
         if (line.startsWith("//") || line.isEmpty())
           continue;

         // if the ware entry is for an alternate alias,
         // don't treat it like a ware entry
         if (line.startsWith("4,")) {
            // store entry for processing and saving later on
            alternateAliasesToBeProcessed.add(line);
            alternateAliasEntries.append(line).append('\n');

            // skip the alternate alias entry for now
            continue;
         }

         // add new ware to the market
         try {
            ware = Ware.fromJSON(line);

            if (ware == null) {
               Config.userInterface.printToConsole(StringTable.ERROR_WARE_PARSING + line);
               waresErrored.add(line);
               continue;
            }

            // check for and correct errors
            wareError = ware.validate();

            // if there was an uncorrectable error, report it
            if (!wareError.isEmpty()) {
               Config.userInterface.printToConsole(StringTable.ERROR_WARE_ENTRY_INVALID + wareError + ": " + line);
               waresErrored.add(line);
               continue;
            }

            // grab the ware's ID for processing
            wareID = ware.getWareID().intern();

            // Check if the ware exists outside of
            // the marketplace before loading it.
            // If it doesn't exist, print an error and save
            // the line entry to the side in case it is part
            // of a mod which will be added back later.
            if (!Config.userInterface.doesWareExist(wareID) &&
                !(ware instanceof WareUntradeable)) {
               // warn the server
               Config.userInterface.printToConsole(StringTable.WARN_WARE_NONEXISTENT + wareID);

               // store the line entry for later
               waresErrored.add(line);
               continue;
            }

            // Check for duplicate ware IDs, warn if one is found,
            // then overwrite the duplicate ware.
            // Overwriting duplicates may be useful in some situations,
            // such as making quick changes or tests.
            // There is no need to grab and delete duplicate ware objects,
            // since Java's garage collection handles
            // deleting wares not in the marketplace.
            duplicateWare = wares.containsKey(wareID);
            if (duplicateWare)
               Config.userInterface.printToConsole(StringTable.ERROR_WARE_MISSING + wareID);
               // Note: If a new alias is assigned to this duplicate ware ID,
               // then until wares are saved and reloaded,
               // the ware ID will have two aliases: the old one and the new one.

            // if the ware has components, try loading them
            if (ware.hasComponents())
               ware.reloadComponents();

            // add ware to the marketplace
            // or prepare to process it later
            if (!Float.isNaN(ware.getBasePrice())) {
               wares.put(wareID, ware);

               // add the ware entry to the load order record
               // if the ware entry's ID is not a repeat
               if (!duplicateWare) {
                  StringBuilder json = new StringBuilder(line).append('\n');
                  waresLoadOrder.add(json);
                  wareEntries.put(wareID, json);
               }
            } else {
               waresWithUnloadedComponents.add(ware);
               continue;
            }

            // if the current ware's alias is already used,
            // clear the current ware's alias and send a warning
            alias = ware.getAlias();
            if (alias != null && !alias.isEmpty()) {
               wareIDUsingAlias = wareAliasTranslations.get(alias);

               if (wareIDUsingAlias != null &&
                   !wareIDUsingAlias.equals(wareID)) {
                  Config.userInterface.printToConsole(StringTable.WARN_WARE_ALIAS_USED
                     + alias
                     + System.lineSeparator() + "   is used by " + wareIDUsingAlias
                     + System.lineSeparator() + "   failed to assign to " + wareID);
                  ware.setAlias(null);
               } else {
                  wareAliasTranslations.put(alias.intern(), wareID);
               }
            }

            // add ware to averages
            if (ware instanceof WareUntradeable || ware instanceof WareLinked) {
               numStatisticExcludedWares++;
            } else {
               // increment total number of wares in each level
               hierarchyLevelTotals[ware.getLevel()]++;

               // total price bases for later averaging
               priceBaseAverage += ware.getBasePrice();
            }
         } catch (Exception e) {
            Config.userInterface.printToConsole(StringTable.ERROR_WARE_PARSING_EXCEPT + line);
            e.printStackTrace();
         }
      } // end while loop for parsing lines

      // close the file
      fileReader.close();

      // try to load wares whose components were not found
      processWaresWithUnloadedComponents(waresWithUnloadedComponents, hierarchyLevelTotals);

      // check if any wares were added
      if (wares.isEmpty()) {
         // if a blank save file was loaded,
         // try to load a market-starting file instead
         if (fileWares.getPath().equals(Config.filenameWaresSave)) {
            // use the local market-starting file
            fileWares = new File(Config.filenameWares);
            // if the local file isn't found, use the global file
            if (!Config.crossWorldMarketplace && !fileWares.isFile()) {
               fileWares = new File("config" + File.separator + "CommandEconomy" + File.separator + Config.filenameNoPathWares);
            }

            // if the file exists, use it
            // otherwise, warn that no wares were added
            if (fileWares.isFile())
               loadWares(fileWares);
            else
               Config.userInterface.printToConsole(StringTable.WARN_WARE_NONE_LOADED);
         } else {
            Config.userInterface.printToConsole(StringTable.WARN_WARE_NONE_LOADED);
         }
      }
      else { // avoids division-by-zero
         // load wares' additional or alternative aliases
         loadAlternateAliases(alternateAliasesToBeProcessed);

         // calculate median starting quantity
         // sort starting quantities to guarantee numerical ordering
         // create a new array to avoid disrupting settings
         int[][] startQuanBaseSorted = new int[2][Config.startQuanBase.length];
         System.arraycopy(Config.startQuanBase, 0, startQuanBaseSorted[0], 0, Config.startQuanBase.length);

         // record level corresponding to each starting quantity
         // to map sorted array to number of wares in each level
         startQuanBaseSorted[1][0] = 0;
         startQuanBaseSorted[1][1] = 1;
         startQuanBaseSorted[1][2] = 2;
         startQuanBaseSorted[1][3] = 3;
         startQuanBaseSorted[1][4] = 4;
         startQuanBaseSorted[1][5] = 5;

         // sort starting quantities
         Arrays.sort(startQuanBaseSorted[0]);

         // find starting quantity median
         // Since the array is small, walk halfway across it,
         // subtracting the number of wares for each level
         // from a total count, to find where the median lies.
         // If the count becomes negative,
         // the median is in the current level.

         // sum counts of statistic-included wares to account for exclusions
         int totalFrequencies = hierarchyLevelTotals[0] + hierarchyLevelTotals[1] +
                                hierarchyLevelTotals[2] + hierarchyLevelTotals[3] +
                                hierarchyLevelTotals[4] + hierarchyLevelTotals[5];

         // subtract the number of wares in each level
         // from the total count to find median ware's starting quantity
         for(int i = 0; i < Config.startQuanBase.length; i++) {
            totalFrequencies -= hierarchyLevelTotals[startQuanBaseSorted[1][i]] + hierarchyLevelTotals[startQuanBaseSorted[1][i]];

            // check if the median lies in the current index
            if (totalFrequencies < 0) {
               // assume it lies in the previous index and
               // not in the current index or between them
               startQuanBaseMedian = startQuanBaseSorted[0][i];
               break;
            }
            // check if median lies between the current and next index
            else if (totalFrequencies == 0) {
               startQuanBaseMedian = (startQuanBaseSorted[0][i] + startQuanBaseSorted[0][i + 1]) / 2;
               break;
            }
         }

         // prepare to record base prices to find base price median
         float[] priceBases = new float[wares.size()];
         int     index      = 0; // for entering base prices into the array

         // Base prices are recorded when setting start quantities
         // rather than when initially loading wares
         // to obtain greater resource efficiency
         // than using and resizing a list.

         // calculate start quantities
         // if not the starting quantity base median is not greater than zero
         // and at least one ware needs their starting quantity set,
         // something is very wrong
         if (startQuanBaseMedian <= 0.0f) {
            Config.userInterface.printToConsole(StringTable.ERROR_STARTING_QUANTITIES + Float.toString(startQuanBaseMedian));

            // record base prices to find median
            for (Ware wareCurrent : wares.values()) {
               if (!(wareCurrent instanceof WareUntradeable || wareCurrent instanceof WareLinked)) {
                  priceBases[index] = wareCurrent.getBasePrice();
                  index++;
               }
            }
         } else {
            // calculate starting quantities for each level
            float quanCalc = 0.0f;
            for(int level = 0; level < 6; level++) {
               // find effect of spread
               quanCalc = (float) Config.startQuanBase[level]; // get base starting quantity
               if (Config.startQuanSpread != 1.0f && quanCalc != 0.0f) // if spread has any effect, calculate it
                  quanCalc = quanCalc * ((Config.startQuanSpread * (quanCalc - startQuanBaseMedian) / quanCalc) + 1);
               else // if spread has no effect, set its effect to zero
                  quanCalc = 0.0f;

               // startQuan = (base + spreadAdjustment) * multiplier
               quanCalc = (Config.startQuanBase[level] + quanCalc) * Config.startQuanMult;

               // set starting quantity for corresponding level
               Config.startQuan[level] = (int) quanCalc;
            }

            // get all wares whose starting quantities need to be set
            for (Ware wareCurrent : wares.values()) {
               // if the ware is flagged to have its starting quantity set,
               // set its quantity
               if (wareCurrent.getQuantity() == -1)
                  wareCurrent.setQuantity(Config.startQuan[wareCurrent.getLevel()]);

               // record ware's base price
               if (!(wareCurrent instanceof WareUntradeable || wareCurrent instanceof WareLinked)) {
                  priceBases[index] = wareCurrent.getBasePrice();
                  index++;
               }
            }
         }

         // find price base median
         Arrays.sort(priceBases);
         if (priceBases.length % 2 == 1) // odd number of elements -> pick middle element
            priceBaseMedian = priceBases[priceBases.length / 2 + 1];
         else                            // even number of elements -> average middle elements
            priceBaseMedian = (priceBases[priceBases.length / 2 + 1] + priceBases[priceBases.length / 2]) / 2;

         // calculate average base price
         priceBaseAverage /= wares.size() - numStatisticExcludedWares;
         // truncate the price to avoid rounding and multiplication errors
         priceBaseAverage  = (double) PriceFormatter.truncatePrice((float) priceBaseAverage);
      }

      // allow other threads to adjust wares' properties
      releaseMutex();

      // signal threads to reload their wares when possible
      if (Config.enableAI)
         AIHandler.loadWares();
      if (Config.randomEvents)
         RandomEvents.loadWares();
   }

   /**
    * Tries to load wares which failed loading after pulling from a file,
    * presumably because at least one component was missing.
    * If the component was found later on or while parsing the unloaded wares,
    * then the ware might load successfully. Otherwise, prints an error.
    * <p>
    * Complexity: Worst-Case - O(n!)
    * @param unloadedWares        wares which failed to load previously
    * @param hierarchyLevelTotals continuing counts for number of wares in each level
    */
   private static void processWaresWithUnloadedComponents(ArrayDeque<Ware> unloadedWares, int[] hierarchyLevelTotals) {
      // if there's nothing to do, do nothing
      if (unloadedWares.size() == 0)
         return;

      // set up variables
      int    depth              = 0;    // counter for repeatedly trying to load wares
      String wareID;                    // ID for ware currently being processed
      String missingComponent;          // tracks which of the current ware's components is missing, if any
      // prepare a place to put wares who loaded successfully
      ArrayDeque<Ware> waresSuccessfullyLoaded = new ArrayDeque<Ware>();

      // check if the unloaded wares' components have been loaded
      // if possible, try to load the ware
      // loop multiple times in case an unloaded ware's missing component is an unloaded ware
      for (; depth < Config.maxCraftingDepth; depth++) {
         for (Ware ware : unloadedWares) {
            wareID = ware.getWareID();

            // reload ware's components
            missingComponent = ware.reloadComponents();

            // don't continue loading an invalid ware
            if (!missingComponent.isEmpty())
               continue;

            // if the ware is valid, add it to the marketplace
            // if there are any wares using the given alias, handle it
            if (ware.getAlias() != null && wareAliasTranslations.containsKey(ware.getAlias())) {
               Config.userInterface.printToConsole(StringTable.ERROR_WARE_ALIAS
                  + wareID + ", AKA " + ware.getAlias());
               ware.setAlias(null);
            }

            // add the ware entry to the load order record
            // before moving the ware to the marketplace
            // to ease checking for duplicates
            if (!wares.containsKey(wareID)) {
               StringBuilder json = new StringBuilder(ware.toJSON() + '\n');
               waresLoadOrder.add(json);
               wareEntries.put(wareID, json);
            }

            // move the ware to the marketplace
            wares.put(wareID, ware);
            waresSuccessfullyLoaded.add(ware);

            // if the current ware's alias is unique,
            // then record the ware's alias in the translation table
            if (ware.getAlias() != null)
               wareAliasTranslations.put(ware.getAlias(), wareID);

            // if the ware is not untradeable or represents other wares,
            // use it for calculating ware statistics
            if (ware instanceof WareUntradeable || ware instanceof WareLinked) {
               numStatisticExcludedWares++;
            } else {
               // increment total number of wares in each level
               hierarchyLevelTotals[ware.getLevel()]++;

               // total price bases for later averaging
               priceBaseAverage += ware.getBasePrice();
            }
         } // end of unloaded-ware-traversing for loop

         // remove wares which loaded successfully
         // from the table of unloaded wares
         for (Ware loadedWare : waresSuccessfullyLoaded) {
            unloadedWares.remove(loadedWare);
         }
         waresSuccessfullyLoaded.clear();
      } // end of maximum crafting depth for loop

      // report errors for any wares which still have not loaded
      for (Ware ware : unloadedWares) {
         wareID = ware.getWareID();

         // tell the console which ware could not be loaded
         Config.userInterface.printToConsole(StringTable.ERROR_WARE_PARSING_ID + wareID + StringTable.WARN_FILE_WARE_INVALID + Config.filenameWaresSave);

         // reload ware's components
         missingComponent = ware.reloadComponents();

         // check whether the ware just loaded its components correctly
         // report an error accordingly
         if (missingComponent.isEmpty()) {
            // if no component seems to be missing,
            // suggest considering adjusting the setting for maximum crafting depth
            Config.userInterface.printToConsole(StringTable.WARN_CRAFTING_DEPTH);

            // Even though the ware could probably be loaded at this point, it is not since
            // it exceeds the maximum crafting depth and
            // to ease debugging in case this ware is a component of earlier wares.
         } else {
            // to ease debugging, print the first missing component
            Config.userInterface.printToConsole(StringTable.WARN_COMPONENT_MISSING + missingComponent);
         }

         // record ware entry
         waresErrored.add(ware.toJSON() + '\n');
      }
   }

   /**
    * Loads additional aliases and tags for wares.
    * Assumes all wares which are going to be loaded have been loaded.
    * <p>
    * Complexity: O(n)
    * @param alternateAliasesToBeProcessed stack of line entries to be parsed
    */
   private static void loadAlternateAliases(ArrayDeque<String> alternateAliasesToBeProcessed) {
      if (alternateAliasesToBeProcessed.size() == 0)
         return;

      // initialize variables
      String[] data;                     // holds data being parsed
      byte     type             = 0;
      String   alias            = "";    // holds ware's additional alternate name
      String[] wareIDs          = null;  // holds possible ware IDs to assign the alias to
      String   wareID           = "";    // holds ware ID to be assigned the alias
      int      i                = 0;     // for parsing possible ware IDs
      String   wareIDUsingAlias = "";    // holds the ware ID using the current ware's alias

      // process all entries marked as alternate aliases
      for (String entry : alternateAliasesToBeProcessed) {
         // tag: 4,name,mostPreferredModelWareID,nextPreferredModelWareID,...,lastPreferredModelWareID
         // alternate alias: 4,alternateAlias,wareID

         data = entry.split(",", 0); // split line using commas

         // check if there is enough data to create an alias
         if (data.length < 3) {
            Config.userInterface.printToConsole(StringTable.ERROR_ALT_ALIAS_ENTRY + "missing data: " + entry);
            continue;
         }

         try {
            // double-check entry type
            type = Byte.parseByte(data[0]);
            if (type != 4) {
               Config.userInterface.printToConsole(StringTable.ERROR_ALT_ALIAS_ENTRY + "invalid type: " + entry);
               continue;
            }

            // double-check that an alias exists
            if (data[1] == null || data[1].isEmpty()) {
               Config.userInterface.printToConsole(StringTable.ERROR_ALT_ALIAS_ENTRY + "missing alias: " + entry);
               continue;
            }
            alias = data[1];

            // check whether it is a tag and whether that tag exists
            if (Config.wareTagsReportInvalid && alias.startsWith("#") && !Config.userInterface.doesOreDictionaryNameExist(alias.substring(1, alias.length()))) {
               Config.userInterface.printToConsole(StringTable.WARN_ORE_NAME_NONEXISTENT + alias.substring(1, alias.length()));
               continue;
            }

            // prepare to process possible ware IDs
            wareIDs = new String[data.length - 2];
            System.arraycopy(data, 2, wareIDs, 0, data.length - 2);

            // check the existence of each ware before assigning an alias
            wareID = null; // clear last used ware ID
            for (i = 0; i < wareIDs.length; i++) {
               // if the current ware exists, use it
               if (wares.containsKey(wareIDs[i])) {
                  wareID = wareIDs[i];
                  i = wareIDs.length; // break out of the current for loop
               }
               // search among ware aliases
               else {
                  wareID = wareAliasTranslations.get(wareIDs[i]);
                  if (wareID != null)
                     i = wareIDs.length; // break out of the current for loop
               }
            }

            // check if any loaded ware was found
            if (wareID == null) {
               Config.userInterface.printToConsole(StringTable.WARN_ALT_ALIAS_UNUSED + alias);
               continue;
            }

            // if the alias is already used, send a warning
            wareIDUsingAlias = wareAliasTranslations.get(alias);
            if (wareIDUsingAlias != null &&
                !wareIDUsingAlias.equals(wareID)) {
               Config.userInterface.printToConsole(StringTable.WARN_WARE_ALIAS_USED
                  + alias
                  + System.lineSeparator() + "   is now used by " + wareIDUsingAlias
                  + System.lineSeparator() + "   was assigned to " + wareID);
            }

            // assign the alias
            wareAliasTranslations.put(alias, wareID);
         }
         // if parsing fails, report the error
         // but continue loading other aliases
         catch (Exception e) {
            Config.userInterface.printToConsole(StringTable.ERROR_ALT_ALIAS_PARSING + entry);
            e.printStackTrace();
         }
      }
   }

   /**
    * Reloads components and recalculates prices for all wares created from other wares.
    * <p>
    * Complexity: O(n), where n is the number of wares within the marketplace
    */
   protected static void reloadAllComponents() {
      for (Ware ware : wares.values()) {
         // only recalculates price if ware has components
         // recalculate all wares with components in case
         // they use each other as components
         ware.reloadComponents();

         // Reloading components like this should give the latest prices
         // if wares use Ware.getBasePrice() to calculate their prices and
         // use price multipliers in Ware.getBasePrice() rather than
         // set their base price using their multiplier.
      }
   }

   /**
    * Marks a ware as changed since the last save so the next save with store its changes.
    * <p>
    * Complexity: O(1)
    * @param ware the ware to be saved
    */
   public static void markAsChanged(Ware ware) {
      if (ware.getWareID() == null)
         return;

      // add to list of wares to be saved
      if (wares.containsKey(ware.getWareID()))
         waresChangedSinceLastSave.add(ware.getWareID());

      // recalculate any saved values based on outdated information
      ware.removeSavedCalculations();
   }

   /**
    * Writes information for regenerating current wares within the marketplace.
    * <p>
    * Complexity: O(n), where n is loaded and errored wares
    */
   public static void saveWares() {
      // if there is nothing to save, do nothing
      if (waresChangedSinceLastSave.isEmpty())
         return;

      StringBuilder lineEntry = new StringBuilder();
      StringBuilder json;

      // check if another thread is adjusting wares' properties
      acquireMutex();

      // ensure the save file and directory exist
      try {
         File fileWaresSave = new File (Config.filenameWaresSave);
         if (!fileWaresSave.exists()) {
            fileWaresSave.getParentFile().mkdirs();
            fileWaresSave.createNewFile();
         }
      } catch (IOException e) {
         Config.userInterface.printToConsole(StringTable.ERROR_FILE_CREATE_SAVE_WARES);
         e.printStackTrace();
         releaseMutex();
         return;
      }

      // regenerate entries for changed wares
      for (String wareID : waresChangedSinceLastSave) {
         json = wareEntries.get(wareID);
         json.setLength(0); // clear the entry without losing the reference nor reallocating memory
         json.append(wares.get(wareID).toJSON()).append('\n'); // save updated entry
      }
      waresChangedSinceLastSave.clear();

      BufferedWriter fileWriter = null; // use a handle to ensure the file gets closed
      try {
         // open the save file for wares, create it if it doesn't exist
         fileWriter = new BufferedWriter(new FileWriter(Config.filenameWaresSave, false));

         // warn users file may be overwritten
         fileWriter.write(StringTable.WARN_FILE_OVERWRITE);

         // save the wares in the order they successfully loaded in
         // so they will be loaded more smoothly next time
         for (StringBuilder wareEntry : waresLoadOrder) {
            // write ware
            fileWriter.write(wareEntry.toString());
         }

         // write wares which failed to load,
         // they might be nonexistent until the mod they are from is loaded
         // or otherwise fixed by a server administrator
         if (!waresErrored.isEmpty()) {
            fileWriter.write(StringTable.WARN_FILE_WARES_INVALID);
            for (String nonexistentWare : waresErrored) {
               lineEntry.append(nonexistentWare).append('\n');
            }
            fileWriter.write(lineEntry.toString());
            lineEntry.setLength(0);
         }

         // write alternate aliases last so all wares will be loaded
         // before the aliases are loaded
         if (alternateAliasEntries.length() != 0) {
            // warn users file may be overwritten
            fileWriter.write(StringTable.FILE_HEADER_ALT_ALIASES);
            fileWriter.write(alternateAliasEntries.toString());
         }
      } catch (IOException e) {
         Config.userInterface.printToConsole(StringTable.ERROR_FILE_SAVE_WARES);
         e.printStackTrace();
      }

      // ensure the file is closed
      try {
         if (fileWriter != null)
            fileWriter.close();
      } catch (Exception e) { }

      releaseMutex();
   }

   /**
    * Writes information for tradeable wares within the marketplace.
    * <p>
    * Complexity: O(n), where n is loaded wares
    */
   public static void printMarket() {
      StringBuilder lineEntry = new StringBuilder();

      BufferedWriter fileWriter = null; // use a handle to ensure the file gets closed
      try {
         // open the file for printing wares, create it if it doesn't exist
         fileWriter = new BufferedWriter(new FileWriter(Config.filenameMarket, false));

         // warn users file may be overwritten and print the header
         // tabs are used to allow easy pasting into Microsoft Excel spreadsheets
         fileWriter.write(StringTable.WARN_FILE_OVERWRITE + StringTable.FILE_HEADER_PRINT_MARKET);

         // loop through wares and write data to file
         String alias;
         String wareID; // ID of ware being printed
         Ware   ware;   // ware being printed
         for (Map.Entry<String, Ware> entry : wares.entrySet()) {
            // prevents a null pointer exception if wares are being reloaded
            if (entry == null) {
               fileWriter.close();
               return;
            }

            wareID = entry.getKey();
            ware   = entry.getValue();

            // for paranoia's sake and
            // don't print untradeable and linked wares
            if (ware == null || ware instanceof WareUntradeable || ware instanceof WareLinked)
               continue;

            // only print alias if ware has one
            alias = ware.getAlias();
            if (alias != null && !alias.isEmpty())
               lineEntry.append(wareID).append('\t').append(alias).append('\t').append(getPrice(null, ware, 1, PriceType.CURRENT_SELL)).append('\t').append(ware.getQuantity()).append('\t').append(ware.getLevel()).append('\n');
            else
               lineEntry.append(wareID).append("\t\t").append(getPrice(null, ware, 1, PriceType.CURRENT_SELL)).append('\t').append(ware.getQuantity()).append('\t').append(ware.getLevel()).append('\n');

            // write to file
            fileWriter.write(lineEntry.toString());
            lineEntry.setLength(0);
         }
      } catch (IOException e) {
         Config.userInterface.printToConsole(StringTable.ERROR_FILE_PRINT_MARKET);
         e.printStackTrace();
      }

      // ensure the file is closed
      try {
         if (fileWriter != null)
            fileWriter.close();
      } catch (Exception e) { }

      Config.userInterface.printToConsole(StringTable.MSG_PRINT_MARKET);
   }

   /**
    * Returns a ware's price, either for selling, buying,
    * without considering supply and demand, or floor.
    * <p>
    * Complexity: O(1)
    * @param playerID          who to send any error messages to
    * @param ware              ware whose price should be calculated
    * @param quanToTrade       how much to buy or sell; used for price sliding
    * @param priceType         which price should be returned
    * @return ware's price
    */
   public static float getPrice(UUID playerID, Ware ware, int quanToTrade, PriceType priceType) {
      // check if no ware is given
      if (ware == null)
         return Float.NaN;

      // if the quantity specified is invalid, set it to 1
        if (quanToTrade <= 0)
            quanToTrade = 1;

      // check whether the price should be fixed
      // or handled in as a special case
      if (priceType == PriceType.CURRENT_SELL ||
          priceType == PriceType.CURRENT_BUY) {
         // if ware cannot be bought or sold in its current form
         if ((ware instanceof WareUntradeable))
            priceType = PriceType.EQUILIBRIUM_BUY;

         // if all prices should be fixed
         else if (Config.pricesIgnoreSupplyAndDemand) {
            if (priceType == PriceType.CURRENT_BUY)
               priceType = PriceType.EQUILIBRIUM_BUY;
            else
               priceType = PriceType.EQUILIBRIUM_SELL;
         }

         // check whether price should be handled by
         // linked ware's method
         else if (ware instanceof WareLinked)
            return ((WareLinked) ware).getCurrentPrice(quanToTrade, priceType == PriceType.CURRENT_BUY);
      }

      // initialize variables
      final float PRICE_BASE = ware.getBasePrice();
      float spreadAdjustment = 0.0f; // spread's effect on price
      float priceNoQuantityEffect;   // ware's price without considering supply and demand

      // if spread is normal or base is 0, make no adjustment
      if (Config.priceSpread != 1.0f && PRICE_BASE != 0.0f)
         // spreadAdjustment = distance from median * distance multiplier
         spreadAdjustment = ((float) priceBaseMedian - PRICE_BASE) * (1.0f - Config.priceSpread);

      // check if purchasing upcharge should be applied
      if (Config.priceBuyUpchargeMult != 1.0f &&
          (priceType == PriceType.CURRENT_BUY || priceType == PriceType.EQUILIBRIUM_BUY || priceType == PriceType.FLOOR_BUY || priceType == PriceType.CEILING_BUY))
         // calculate price with upcharge multiplier
         priceNoQuantityEffect = (PRICE_BASE + spreadAdjustment) * Config.priceMult * Config.priceBuyUpchargeMult;
      else
         // calculate price without upcharge multiplier
         priceNoQuantityEffect = (PRICE_BASE + spreadAdjustment) * Config.priceMult;

      // factor in components' prices affecting manufactured prices
      if (Config.shouldComponentsCurrentPricesAffectWholesPrice && ware.hasComponents() && !Config.pricesIgnoreSupplyAndDemand)
         priceNoQuantityEffect *= ware.getLinkedPriceMultiplier();

      // check whether price should be returned
      // without considering supply and demand
      if (priceType == PriceType.EQUILIBRIUM_SELL || priceType == PriceType.EQUILIBRIUM_BUY)
         return PriceFormatter.truncatePrice(quanToTrade * priceNoQuantityEffect);

      // check whether price ceiling should be returned
      else if (priceType == PriceType.CEILING_BUY || priceType == PriceType.CEILING_SELL)
         return PriceFormatter.truncatePrice(quanToTrade * priceNoQuantityEffect * Config.priceCeiling);

      // find price floor to be enforced for this purchase
      final float PRICE_MIN = PriceFormatter.truncatePrice(quanToTrade * priceNoQuantityEffect * Config.priceFloor);

      // check whether price floor should be returned
      if (priceType == PriceType.FLOOR_BUY || priceType == PriceType.FLOOR_SELL)
         return PRICE_MIN;

      // prepare to calculate current price
      final int   QUAN_CEILING                  = Config.quanExcessive[ware.getLevel()];
      final int   QUAN_FLOOR                    = Config.quanDeficient[ware.getLevel()];
      final int   QUAN_EQUILIBRIUM              = Config.quanEquilibrium[ware.getLevel()];
      final float QUAN_FLOOR_TO_EQUILIBRIUM     = (float) (QUAN_EQUILIBRIUM - QUAN_FLOOR);   // how much quantity is between the price floor stock and equilibrium stock
      final float QUAN_CEILING_FROM_EQUILIBRIUM = (float) (QUAN_CEILING - QUAN_EQUILIBRIUM); // cast as a float now since it will only be used as a float
            int   quanOnMarket                  = ware.getQuantity();
            float priceTotal                    = 0.0f; // total price of quantity traded
            int   quanPartialTrade              = 0;    // quantity to be traded in a particular price quadrant

      // find the total price
      // if buying, adjust the price first
      // so buying and selling are reciprocal
      if (priceType == PriceType.CURRENT_BUY) {
         quanOnMarket -= quanToTrade;

         // if manufacturing wares should be included and
         // buying out quantity available for sale,
         // factor in manufacturing costs
         if (Config.buyingOutOfStockWaresAllowed && quanOnMarket < 0) {
            // find price and quantity from manufacturing
            float[] manufacturedWares = ware.getManufacturingPrice(-quanOnMarket);

            // only factor in manufacturing if the ware is manufactureable
            if (manufacturedWares != null) {
               // factor in manufacturing costs and quantity
               priceTotal   = manufacturedWares[0];
               quanToTrade -= (int) manufacturedWares[1];

               // alter quantity available for sale
               // to prevent double-counting manufactured quantity
               quanOnMarket += (int) manufacturedWares[1];
            }
         }
      }

      // if understocked, enforce a price ceiling
      if (quanToTrade > 0 && quanOnMarket < QUAN_FLOOR) {
         // figure how how much should be sold in this price quadrant
         if ((quanOnMarket + quanToTrade) <= QUAN_FLOOR)
            quanPartialTrade += quanToTrade;
         else
            quanPartialTrade += QUAN_FLOOR - quanOnMarket;

         // trade within the price quadrant
         priceTotal   += quanPartialTrade * priceNoQuantityEffect * Config.priceCeiling;
         quanOnMarket += quanPartialTrade;
         quanToTrade  -= quanPartialTrade;
      }

      // if below equilibrium, raise the price
      if (quanToTrade > 0 && quanOnMarket < QUAN_EQUILIBRIUM) {
         // figure how how much should be sold in this price quadrant
         if ((quanOnMarket + quanToTrade) <= QUAN_EQUILIBRIUM)
            quanPartialTrade = quanToTrade;
         else
            quanPartialTrade = QUAN_EQUILIBRIUM - quanOnMarket;

         // trade within the price quadrant
         // price in a price quadrant = (cost of first unit to trade + cost of last unit to trade) / 2 * quantity sold
         // scarcity price rise percent = price ceiling multiplier - percent distance away from equilibrium toward stock floor
         priceTotal   += quanPartialTrade * priceNoQuantityEffect * (1.0f + Config.priceCeilingAdjusted * (((quanOnMarket + ((float) (quanPartialTrade + 1) / 2) - QUAN_EQUILIBRIUM)) / QUAN_FLOOR_TO_EQUILIBRIUM));
         quanOnMarket += quanPartialTrade;
         quanToTrade  -= quanPartialTrade;
      }

      // if at equilibrium, use balanced price
      if (quanToTrade > 0 && quanOnMarket == QUAN_EQUILIBRIUM) {
         priceTotal += priceNoQuantityEffect;
         quanOnMarket++;
         quanToTrade--;
      }

      // if above equilibrium, lower the price
      if (quanToTrade > 0 && QUAN_CEILING > quanOnMarket && quanOnMarket > QUAN_EQUILIBRIUM) {
         // figure how how much should be sold in this price quadrant
         if ((quanOnMarket + quanToTrade) <= QUAN_CEILING)
            quanPartialTrade = quanToTrade;
         else
            quanPartialTrade = QUAN_CEILING - quanOnMarket;

         // trade within the price quadrant
         // price in a price quadrant = (cost of first unit to trade + cost of last unit to trade) / 2 * quantity sold
         // saturation price drop percent = price floor multiplier - percent distance away from equilibrium toward overstocked
         priceTotal   += quanPartialTrade * priceNoQuantityEffect * (1.0f - Config.priceFloorAdjusted * (((quanOnMarket + ((float) (quanPartialTrade + 1) / 2) - QUAN_EQUILIBRIUM)) / QUAN_CEILING_FROM_EQUILIBRIUM));
         quanOnMarket += quanPartialTrade;
         quanToTrade  -= quanPartialTrade;
      }

      // if overstocked, enforce a price floor
      if (quanToTrade > 0 && quanOnMarket >= QUAN_CEILING) {
         priceTotal   += quanToTrade * priceNoQuantityEffect * Config.priceFloor;
         quanOnMarket += quanToTrade;
      }

      // enforce a price floor
      if (priceTotal >= PRICE_MIN)
         // truncate the price to avoid rounding and multiplication errors
         return PriceFormatter.truncatePrice(priceTotal);
      else
         return PRICE_MIN;
   }

   /**
    * Returns how much of a ware may be afforded with a given budget.
    * <p>
    * Complexity: O(1)
    * @param ware           tradeable ware to be purchased
    * @param moneyAvailable maximum amount of money to spend
    * @return amount which can be bought
    * @see    Ware
    */
   public static int getPurchasableQuantity(Ware ware, float moneyAvailable) {
      // get ware information
      final float PRICE_BASE                    = ware.getBasePrice();
      final int   QUAN_CEILING                  = Config.quanExcessive[ware.getLevel()];
      final int   QUAN_FLOOR                    = Config.quanDeficient[ware.getLevel()];
      final int   QUAN_EQUILIBRIUM              = Config.quanEquilibrium[ware.getLevel()];
      final float QUAN_FLOOR_TO_EQUILIBRIUM     = (float) (QUAN_EQUILIBRIUM - QUAN_FLOOR);   // how much quantity is between the price floor stock and equilibrium stock
      final float QUAN_CEILING_FROM_EQUILIBRIUM = (float) (QUAN_CEILING - QUAN_EQUILIBRIUM); // cast as a float now since it will only be used as a float
            int   quanOnMarket                  = ware.getQuantity();

      // initialize variables
      float spreadAdjustment   = 0.0f; // spread's effect on price
      float priceNoQuantityEffect;     // ware's price without considering supply and demand
      float priceUnit;                 // multiplier for how much supply and demand are affecting price
      int   quanPartialTrade;          // quantity to be traded in a particular price quadrant
      int   purchasableQuantity = 0;   // how much quantity many be purchased

      // prepare to use quadratic formula to solve for purchasable quantity
      float quadraticFormulaA;
      float quadraticFormulaB;
      float quadraticFormulaC;

      // if spread is normal or base is 0, make no adjustment
      if (Config.priceSpread != 1.0f && PRICE_BASE != 0.0f)
         // spreadAdjustment = distance from median * distance multiplier
         spreadAdjustment = ((float) priceBaseMedian - PRICE_BASE) * (1.0f - Config.priceSpread);

      // calculate price when unaffected by supply and demand
      priceNoQuantityEffect = (PRICE_BASE + spreadAdjustment) * Config.priceBuyUpchargeMult * Config.priceMult;

      // find price quadrant and grab values
      // (quad 1) if at or below price floor
      if (quanOnMarket > QUAN_CEILING) {
         quanPartialTrade = quanOnMarket - QUAN_CEILING;
         priceUnit        = priceNoQuantityEffect * Config.priceFloor;

         // find how much quantity until crossing
         if (priceUnit * quanPartialTrade >= moneyAvailable) {
            // if not crossing, divide money by constant price
            purchasableQuantity = (int) (moneyAvailable / priceUnit);

            // zero out money to avoid checking next price quadrants
            moneyAvailable = 0.0f;
            quanOnMarket -= quanPartialTrade;
         } else {
            // if crossing, multiple constant price by total quantity before crossing
            purchasableQuantity  = quanPartialTrade;
            moneyAvailable      -= priceUnit * quanPartialTrade;

            // remove purchased quantity to cross into next price quadrant
            quanOnMarket -= quanPartialTrade;
         }
      }

      // (quad 2) if above equilibrium
      if (moneyAvailable > 0.0f  && quanOnMarket > QUAN_EQUILIBRIUM) {
         // find the average price of price quadrant's remaining quantity until crossing
         quanPartialTrade = quanOnMarket - QUAN_EQUILIBRIUM - 1;
         priceUnit        = priceNoQuantityEffect * (1.0f - Config.priceFloorAdjusted * (((quanOnMarket - ((float) quanPartialTrade / 2) - QUAN_EQUILIBRIUM)) / QUAN_CEILING_FROM_EQUILIBRIUM));

         // find how much quantity until crossing
         if (priceUnit * quanPartialTrade > moneyAvailable) {
            // if not crossing, use quadratic formula to solve for purchasable quantity
            quadraticFormulaA = 0.5f * Config.priceFloorAdjusted;
            quadraticFormulaB = QUAN_CEILING_FROM_EQUILIBRIUM + Config.priceFloorAdjusted * (QUAN_EQUILIBRIUM - quanOnMarket - 0.5f);
            quadraticFormulaC = -moneyAvailable * QUAN_CEILING_FROM_EQUILIBRIUM / priceNoQuantityEffect;
            purchasableQuantity += (int) ((-quadraticFormulaB + Math.sqrt(Math.pow(quadraticFormulaB, 2) - (4 * quadraticFormulaA * quadraticFormulaC))) / (2 * quadraticFormulaA));

            // zero out money to avoid checking next price quadrants
            moneyAvailable = 0.0f;
         } else {
            // if crossing, multiple average price by total quantity before crossing
            purchasableQuantity += quanPartialTrade;
            moneyAvailable      -= priceUnit * quanPartialTrade;

            // remove purchased quantity to cross into next price quadrant
            quanOnMarket -= quanPartialTrade;
         }
      }

      // (quad3) if below equilibrium
      if (moneyAvailable > 0.0f && QUAN_FLOOR < quanOnMarket) {
         // find the average price of price quadrant's remaining quantity until crossing
         quanPartialTrade = quanOnMarket - QUAN_FLOOR + 1;
         priceUnit        = priceNoQuantityEffect * (1.0f + Config.priceCeilingAdjusted * (((quanOnMarket - ((float) quanPartialTrade / 2) - QUAN_EQUILIBRIUM)) / QUAN_FLOOR_TO_EQUILIBRIUM));

         // find how much quantity until crossing
         if (priceUnit * quanPartialTrade > moneyAvailable) {
            // if not crossing, use quadratic formula to solve for purchasable quantity
            quadraticFormulaA = -0.5f * Config.priceCeilingAdjusted;
            quadraticFormulaB = QUAN_FLOOR_TO_EQUILIBRIUM + Config.priceCeilingAdjusted * (quanOnMarket - QUAN_EQUILIBRIUM + 0.5f);
            quadraticFormulaC = -moneyAvailable * QUAN_FLOOR_TO_EQUILIBRIUM / priceNoQuantityEffect;
            purchasableQuantity += (int) ((-quadraticFormulaB + Math.sqrt(Math.pow(quadraticFormulaB, 2) - (4 * quadraticFormulaA * quadraticFormulaC))) / (2 * quadraticFormulaA));

            // zero out money to avoid checking next price quadrants
            moneyAvailable = 0.0f;
         } else {
            // if crossing, multiple average price by total quantity before crossing
            purchasableQuantity += quanPartialTrade;
            moneyAvailable      -= priceUnit * quanPartialTrade;

            // remove purchased quantity to cross into next price quadrant
            quanOnMarket -= quanPartialTrade;
         }
      }

      // (quad 4) if at price ceiling
      if (moneyAvailable > 0.0f && quanOnMarket <= QUAN_FLOOR + 1) {
         // divide money by constant price
         purchasableQuantity += (int) ((moneyAvailable / (priceNoQuantityEffect * Config.priceCeiling)) + 0.5f);
      }

      // validate purchasable quantity
      if (purchasableQuantity > 0)
         return purchasableQuantity;
      else
         return 0;
   }

   /**
    * Returns how much change in quantity for a ware
    * may result in reaching a given unit price.
    * <p>
    * Complexity: O(1)
    * @param ware         tradeable ware to be analyzed
    * @param priceUnit    unit price to be sought
    * @param isPurchase   <code>true</code> if the price should reflect buying the ware
    *                     <code>false</code> if the price should reflect selling the ware
    * @return units until given price is reached
    */
   public static int getQuantityUntilPrice(Ware ware, float priceUnit, boolean isPurchase) {
      // get ware information
      final float PRICE_BASE       = ware.getBasePrice();
      final int   QUAN_CEILING                  = Config.quanExcessive[ware.getLevel()];
      final int   QUAN_FLOOR                    = Config.quanDeficient[ware.getLevel()];
      final int   QUAN_EQUILIBRIUM              = Config.quanEquilibrium[ware.getLevel()];
      final float QUAN_FLOOR_TO_EQUILIBRIUM     = (float) (QUAN_EQUILIBRIUM - QUAN_FLOOR);   // how much quantity is between the price floor stock and equilibrium stock
      final float QUAN_CEILING_FROM_EQUILIBRIUM = (float) (QUAN_CEILING - QUAN_EQUILIBRIUM); // cast as a float now since it will only be used as a float
      final int   QUAN_ON_MARKET    = ware.getQuantity();

      // initialize variables
      float spreadAdjustment = 0.0f; // spread's effect on price
      float priceNoQuantityEffect;   // ware's price without considering supply and demand
      int quantityUntilPrice;        // units until given price is reached

      // if spread is normal or base is 0, make no adjustment
      if (Config.priceSpread != 1.0f && PRICE_BASE != 0.0f)
         // spreadAdjustment = distance from median * distance multiplier
         spreadAdjustment = ((float) priceBaseMedian - PRICE_BASE) * (1.0f - Config.priceSpread);

      // calculate price when unaffected by supply and demand
      priceNoQuantityEffect = (PRICE_BASE + spreadAdjustment) * Config.priceBuyUpchargeMult * Config.priceMult;

      // quad1/quad2, if acceptable price is above base price
      if (priceUnit > PRICE_BASE) {
         // quad1, if price is above ceiling
         if (priceUnit >= PRICE_BASE * Config.priceCeiling) {
            // if the highest possible price is acceptable,
            // everything is acceptable
            return QUAN_ON_MARKET;
         }
         // quad2, if price is below ceiling and above equilibrium
         else {
            // find quantity at acceptable price
            quantityUntilPrice = (int) -((((-priceUnit / priceNoQuantityEffect + 1.0f) / Config.priceCeilingAdjusted) * QUAN_FLOOR_TO_EQUILIBRIUM) + 1 - QUAN_EQUILIBRIUM);

            // find delta between current quantity and acceptable quantity
            if (isPurchase)
               quantityUntilPrice = QUAN_ON_MARKET - quantityUntilPrice;
            else
               quantityUntilPrice -= QUAN_ON_MARKET;
         }
      }
      // quad3/quad4, if acceptable price is below base price
      else {
         // quad4, if price is below price floor
         if (priceUnit <= PRICE_BASE * Config.priceFloor) {
            if (isPurchase)
               // if the lowest possible price is unacceptable,
               // nothing is acceptable
               return 0;
            else
               // if the lowest possible price is acceptable,
               // everything is acceptable
               return QUAN_ON_MARKET;
         }
         // quad3, if price is above floor and below equilibrium
         else {
            // find quantity at acceptable price
            quantityUntilPrice = (int) (((-priceUnit / priceNoQuantityEffect + 1.0f) / Config.priceFloorAdjusted * QUAN_CEILING_FROM_EQUILIBRIUM) - 1 + QUAN_EQUILIBRIUM);

            // find delta between current quantity and acceptable quantity
            if (isPurchase)
               quantityUntilPrice = QUAN_ON_MARKET - quantityUntilPrice;
            else
               quantityUntilPrice -= QUAN_ON_MARKET;
         }
      }

      // if quantity until the price is negative,
      // then the current price is unacceptable
      if (quantityUntilPrice < 0)
         return 0;
      else
         return quantityUntilPrice;
   }

   /**
    * Returns the average price loaded from the wares configuration file,
    * before adjusting for configuration settings and market influences.
    * <p>
    * Complexity: O(1)
    * @return average base price of all wares
    */
   public static float getBasePriceAverage() { return (float) priceBaseAverage; }

   /**
    * Verifies whether a given ware ID is used within the marketplace
    * and returns either the given ID, its translation, or a blank string.
    * <p>
    * Complexity: O(1)
    * @param wareID  ID to be searched for and/or translated
    * @return the given ID, its translation, or an empty string
    */
   public static String translateWareID(final String wareID) {
      // check whether anything was given
      if (wareID == null || wareID.isEmpty())
         return "";

      // if ware is in the market, just return it
      if (wares.containsKey(wareID))
         return wareID;

      // check if the ware is an alias
      String result = wareAliasTranslations.get(wareID);
      if (result != null)
         return result;

      // If the ware ID is neither literal nor an alias,
      // it might be a variant of a known ware.

      // set up variables for checking variation
      String baseID         = ""; // holds base ware ID if given ware is a variant of a known ware
      int ampersandPosition = wareID.indexOf('&'); // find if and where variation begins

      // check if the given ID is a variant
      if (ampersandPosition != -1) {
         baseID = wareID.substring(0, ampersandPosition);

         // if the base ID is found, use it
         // otherwise, check if it is an alias
         if (wares.containsKey(baseID))
            return baseID;
         else {
            result = wareAliasTranslations.get(baseID);
            if (result != null)
               return result;
         }
      }

      return "";
   }

   /**
    * Verifies whether a given ware ID is used within the marketplace
    * and returns the corresponding ware or null.
    * <p>
    * Complexity: O(4)
    * @param wareID ID to be searched for and translated if needed
    * @return the corresponding ware or null
    */
   public static Ware translateAndGrab(final String wareID) {
      // check whether anything was given
      if (wareID == null || wareID.isEmpty())
         return null;

      // if ware is in the market, just return it
      Ware ware = wares.get(wareID);
      if (ware != null)
         return ware;

      // check if the ware is an alias
      if (wareAliasTranslations.get(wareID) != null) {
         ware = wares.get(wareAliasTranslations.get(wareID));
         if (ware != null)
            return ware;
      }

      // If the ware ID is neither literal nor an alias,
      // it might be a variant of a known ware.

      // check if the given ID is a variant
      int ampersandPosition = wareID.indexOf('&');
      if (ampersandPosition != -1) {
         String baseID = wareID.substring(0, ampersandPosition);

         // if the base ID is found, use it
         // otherwise, check if it is an alias
         ware = wares.get(baseID);
         if (ware != null)
            return ware;
         else if (wareAliasTranslations.get(baseID) != null) {
            ware = wares.get(wareAliasTranslations.get(baseID));
            if (ware != null)
               return ware;
         }
      }

      return null;
   }

   /**
    * Returns the ware ID corresponding to the given alias or null.
    * <p>
    * Complexity: O(1)
    * @param alias ware alias to be converted
    * @return ware ID or null
    */
   public static String translateAlias(final String alias) {
      // TreeMap throws an exception if passed null
      if (alias != null)
         return wareAliasTranslations.get(alias);
      else
         return null;
   }

   /**
    * Returns a set of all wares currently available within the marketplace.
    * <p>
    * Complexity: O(n), where n is the number of wares to be returned
    * @return a set of all wares tradeable within the marketplace
    */
   public static Collection<Ware> getAllWares() { return wares.values(); }

   /**
    * Returns a set of all aliases currently being used.
    * Useful for autocompletion.
    * <p>
    * Complexity: O(1)
    * @return all aliases used within the market
    */
   public static Set<String> getAllWareAliases() { return wareAliasTranslations.keySet(); }

   /**
    * Purchases a ware from the market for a player.
    * <p>
    * Complexity: O(log n)
    * @param playerID     user responsible for the trading
    * @param coordinates  where wares may be found
    * @param accountID    key used to retrieve account information
    * @param wareID       key used to retrieve ware information
    * @param quantity     how much of the ware should be purchased
    * @param maxUnitPrice stop buying if unit price is above this amount
    * @param pricePercent percentage multiplier for ware's price
    */
   public static void buy(UUID playerID, UserInterface.Coordinates coordinates,
                          String accountID, String wareID, int quantity, float maxUnitPrice, float pricePercent) {
      if (quantity <= 0              || // if nothing should be bought, stop
         playerID == null)             // if no player was given, there is no party responsible for the purchase
         return;

      // if price multiplier is invalid, set it have no effect
      if (Float.isNaN(pricePercent))
         pricePercent = 1.0f;

      // ---Analyze Ware:---
      // grab the ware to be used
      Ware ware = translateAndGrab(wareID);
      // if ware is not in the market, stop
      if (ware == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_MISSING + wareID);
         return;
      }
      // if ware is invalid, stop
      if (Float.isNaN(ware.getBasePrice())) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_INVALID + wareID);
         Config.userInterface.printToConsole(StringTable.ERROR_WARE_INVALID + ware.getWareID() + StringTable.WARN_FILE_WARE_INVALID + Config.filenameWaresSave);
         waresErrored.add(ware.toJSON() + '\n');
         wares.remove(ware.getWareID());
         return;
      }
      wareID = ware.getWareID();

      // if ware is untradeable, stop
      if (ware instanceof WareUntradeable) {
         Config.userInterface.printErrorToUser(playerID, wareID + StringTable.MSG_BUY_UNTRADEABLE);
         return;
      }

      // if ware has no quantity in the market, stop
      // unless the ware should be manufactured if possible
      if (ware.getQuantity() <= 0 &&
          !(Config.buyingOutOfStockWaresAllowed && ware.hasComponents())) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_BUY_OUT_OF_STOCK + wareID);
         return;
      }

      // ---Analyze Player's Inventory:---
      // check whether it is possible to give the ware to the player
      if (!Config.userInterface.doesWareExist(wareID)) {
         Config.userInterface.printErrorToUser(playerID, "error - " + wareID + " does not appear to exist outside of the marketplace");
         Config.userInterface.printToConsole("commandeconomy - Marketplace.buy(), error - " + wareID + " does not appear to exist outside of the marketplace");
         return;
      }

      // if player can't hold any more wares, stop
      int inventorySpaceAvailable = Config.userInterface.getInventorySpaceAvailable(playerID, coordinates);
      // check whether a player inventory or chest inventory should be used
      if (inventorySpaceAvailable == 0) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_INVENTORY_NO_SPACE);
         return;
      }
      // check whether an inventory was found
      if (inventorySpaceAvailable == -1) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_INVENTORY_MISSING);
         return;
      }

      // ---Analyze Account:---
      // if no account ID was given, use the player's personal account
      if (accountID == null || accountID.isEmpty())
         accountID = Config.userInterface.getDisplayName(playerID);

      // grab the account to be used
      Account account = Account.grabAndCheckAccount(accountID, playerID);

      // if something's wrong with the account, stop
      if (account == null)
         return; // an error message has already been sent to the player

      // ---Determine Order Quantity:---
      // variable for finding how much to buy
      int quantityToBuy = quantity;

      // only buy until the acceptable price is reached
      if (!Float.isNaN(maxUnitPrice)) {
         // find out how much quantity is for sale until the given price
         quantityToBuy = getQuantityUntilPrice(ware, maxUnitPrice / pricePercent, true);

         // if nothing can be bought, buy nothing
         if (quantityToBuy == 0 &&
             !(Config.buyingOutOfStockWaresAllowed))
            return; // no message to avoid needless messaging and ease autobuying

         // if an unacceptable price will not be reached,
         // then do not limit order
         if (quantityToBuy > quantity)
            quantityToBuy = quantity;
      }

      // only buy up to quantity available in the market
      if (quantityToBuy > ware.getQuantity())
         quantityToBuy = ware.getQuantity();

      // if quantity exceeds inventory space, only buy enough to fill inventory
      inventorySpaceAvailable *= Config.userInterface.getStackSize(wareID); // how many more items may be held
      if (quantityToBuy > inventorySpaceAvailable)
         quantityToBuy = inventorySpaceAvailable;

      // get ware's price and player's funds to figure out how much is affordable
      float price          = getPrice(playerID, ware, quantityToBuy, PriceType.CURRENT_BUY) * pricePercent;
      float moneyAvailable = account.getMoney();

      // if there are transaction fees,
      // adjust available funds to account for them
      if (Config.chargeTransactionFees &&
          Config.transactionFeeBuying != 0.0f) {
         // if transaction fee is a multiplier,
         // adjust available funds, assuming all will be spent
         if (Config.transactionFeeBuyingIsMult)
            moneyAvailable /= (1.0f + Config.transactionFeeBuying);
         else
            moneyAvailable -= Config.transactionFeeBuying;

         // if the fee is negative, only adjust as much as
         // the fee collection account will cover
         if (Config.transactionFeeBuying < 0.0f &&
             Account.canNegativeFeeBePaid(moneyAvailable - account.getMoney()) != 0.0f) {
            // if the negative fee is a percentage,
            // find how much cost could be covered
            if (Config.transactionFeeBuyingIsMult) {
               // validate fee collection account's ID
               if (Config.transactionFeesAccount == null || Config.transactionFeesAccount.isEmpty())
                  Config.transactionFeesAccount = StringTable.TRANSACT_FEE_COLLECTION;

               // grab fee collection account
               Account feeCollectionAccount = Account.getAccount(Config.transactionFeesAccount);

               // if nonexistent, create the fee collection account
               if (feeCollectionAccount == null)
                  feeCollectionAccount = Account.makeAccount(Config.transactionFeesAccount, null);

               // adjust player's funds by
               // fee collection account's maximum coverage
               moneyAvailable = account.getMoney() +
                                (feeCollectionAccount.getMoney() *
                                 (1.0f + Config.transactionFeeBuying));
            }

            // if the negative fee is flat,
            // simply reset the player's funds
            else
               moneyAvailable = account.getMoney();
         }
      }

      // if the ware isn't free, figure out how much is affordable
      if (price > moneyAvailable && price > 0.0f) {
         quantityToBuy = getPurchasableQuantity(ware, moneyAvailable / pricePercent);
         price         = getPrice(playerID, ware, quantityToBuy, PriceType.CURRENT_BUY) * pricePercent;

         // if not enough money to buy one ware, stop
         if (quantityToBuy <= 0) {
            Config.userInterface.printErrorToUser(playerID, StringTable.MSG_BUY_NO_MONEY);
            return;
         }
      }

      // ---Manufacturing:---
      // if no quantity is set to be purchased,
      // reset the amount to charge
      if (quantityToBuy <= 0)
         price = 0.0f;

      // prepare a spot to hold manufacturing results
      float[] manufacturedWares = null;

      // if possible, ordered, and necessary,
      // attempt to manufacture the ware
      if (Config.buyingOutOfStockWaresAllowed &&
          quantityToBuy < quantity) {
         // unless the unit price is too high,
         // purchase components and create wares
         if (Float.isNaN(maxUnitPrice) || maxUnitPrice >= ware.getBasePrice() * Config.priceCeiling * Config.buyingOutOfStockWaresPriceMult)
            manufacturedWares = ware.manufacture(playerID, quantity - quantityToBuy,
                                                 moneyAvailable - price,  inventorySpaceAvailable - quantityToBuy);
         else
            manufacturedWares = null;

         // if ware has no quantity in the market and
         // failed to be manufactured, stop
         if (ware.getQuantity() <= 0 &&
             (manufacturedWares == null || manufacturedWares[1] == 0.0f)) {
            // if the quantity manufactured is null,
            // the ware couldn't be manufactured
            if (manufacturedWares == null)
               Config.userInterface.printErrorToUser(playerID, StringTable.MSG_BUY_OUT_OF_STOCK + wareID);
            return;
         }

         // add manufacturing costs and quantity to order
         if (manufacturedWares != null) {
            price         += manufacturedWares[0] * pricePercent;
            quantityToBuy += manufacturedWares[1];

            // truncate price for neatness and avoiding problematic rounding
            price = PriceFormatter.truncatePrice(price);
         }
      }

      // if nothing can be bought, buy nothing
      if (quantityToBuy == 0)
         return; // no message to avoid needless messaging and ease autobuying

      // ---Complete Transaction:---
      // buy the ware
      // take the money
      account.subtractMoney(price);

      // give the ware to the player
      Config.userInterface.addToInventory(playerID, coordinates, wareID, quantityToBuy);

      // subtract from market's quantity
      waitForMutex(); // check if another thread is adjusting wares' properties
      // don't double-count manufactured quantity from marketplace
      if (manufacturedWares == null)
         ware.subtractQuantity(quantityToBuy);
      else
         ware.subtractQuantity(quantityToBuy - (int) manufacturedWares[1]);

      // report success
      // if the ware has an alias, use it
      if (ware.getAlias() == null || ware.getAlias().isEmpty()) {
         /* Although the code below could be condensed by using two String variables,
          * this would slow execution for a highly used section of code.
          * Unfortunately, Java's Strings can be performance-intensive
          * since immutability is enforced and overhead is poorly optimized.
          *
          * If reporting transactions becomes any more complicated,
          * then the two String variables used could be:
          * String wareName    = wareID or ware.getAlias();
          * String accountName = ""     or " taken from " + accountID;
          *
          * Printing could be accomplished with:
          * Config.userInterface.printToUser(playerID, "Bought " + quantityToBuy + " " + wareName + " for " + PriceFormatter.PRICE_FORMAT.format(price) + accountName);
          */

         // print the name of the account used if it isn't a personal or default account
         if (accountID.equals(Config.userInterface.getDisplayName(account.getOwner())) ||
             accountID.equals(Config.userInterface.getDisplayName(playerID)))
            Config.userInterface.printToUser(playerID, "Bought " + Integer.toString(quantityToBuy) + " " + wareID + " for " + PriceFormatter.PRICE_FORMAT.format(price));
         else
            Config.userInterface.printToUser(playerID, "Bought " + Integer.toString(quantityToBuy) + " " + wareID + " for " + PriceFormatter.PRICE_FORMAT.format(price) + " taken from " + accountID);
      }
      else {
         // print the name of the account used if it isn't a personal or default account
         if (accountID.equals(Config.userInterface.getDisplayName(account.getOwner())) ||
             accountID.equals(Config.userInterface.getDisplayName(playerID)))
            Config.userInterface.printToUser(playerID, "Bought " + Integer.toString(quantityToBuy) + " " + ware.getAlias() + " for " + PriceFormatter.PRICE_FORMAT.format(price));
         else
            Config.userInterface.printToUser(playerID, "Bought " + Integer.toString(quantityToBuy) + " " + ware.getAlias() + " for " + PriceFormatter.PRICE_FORMAT.format(price) + " taken from " + accountID);
      }

      // pay the transaction fee
      if (Config.chargeTransactionFees &&
          Config.transactionFeeBuying != 0.0f) {
         // find fee's charge
         float fee = calcTransactionFeeBuying(price);

         // stop if the fee is zero
         if (fee == 0.0f)
            return;

         // check whether a fee collection account should be used
         if (Config.transactionFeesShouldPutFeesIntoAccount)
            // if the fee is negative and unaffordable, don't pay it
            if (Account.depositTransactionFee(fee))
               return;

         // pay the fee
         account.subtractMoney(fee);

         // report fee payment
         Config.userInterface.printToUser(playerID, Config.transactionFeeBuyingMsg + PriceFormatter.PRICE_FORMAT.format(fee));
      }
   }

   /**
    * Sells a ware to the market for a player.
    *<p>
    * Complexity: O(n)
    * @param playerID      user responsible for the trading
    * @param coordinates   where wares may be found
    * @param accountID     key used to retrieve account information
    * @param wareID        key used to retrieve ware information
    * @param inventorySlot where to begin selling within a container
    * @param quantity      how much of the ware should be sold; 0 means sell everything
    * @param minUnitPrice  stop selling if unit price is below this amount
    * @param pricePercent  percentage multiplier for ware's price
    */
   public static void sell(UUID playerID, UserInterface.Coordinates coordinates,
                           String accountID, String wareID, int inventorySlot, int quantity,
                           float minUnitPrice, float pricePercent) {
      if (quantity <  0              || // if nothing should be sold, stop; 0 quantity means sell everything
         playerID == null)             // if no player was given, there is no party responsible for the purchase
         return;

      // if price multiplier is invalid, set it have no effect
      if (Float.isNaN(pricePercent))
         pricePercent = 1.0f;

      // grab the ware to be used
      Ware ware = translateAndGrab(wareID);
      // if ware is not in the market, check for a substitute
      if (ware == null && Config.allowWareTagSubstitution)
         ware = Config.userInterface.getOreDictionarySubstitution(wareID);
      // if no substitute exists or should be used, stop
      if (ware == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_MISSING + wareID);
         return;
      }
      String translatedID = ware.getWareID();

      // if ware is invalid, stop
      if (Float.isNaN(ware.getBasePrice())) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_INVALID + wareID);
         Config.userInterface.printToConsole(StringTable.ERROR_WARE_INVALID + translatedID + StringTable.WARN_FILE_WARE_INVALID + Config.filenameWaresSave);
         waresErrored.add(ware.toJSON() + '\n');
         wares.remove(translatedID);
         return;
      }

      // if selling at or past the price floor is prohibited,
      // then stop and warn players if they are about to
      if (Config.noGarbageDisposing && hasReachedPriceFloor(ware)) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_SELL_NO_GARBAGE_DISPOSING);
         return;
      }

      // get the quality and quantity of wares the player has
      List<Stock> waresFound;
      // if the given ware ID is an alias,
      // use the translated ID
      if (!wareID.startsWith("#") && // not a tag
          !wareID.contains(":"))     // not a base ID
         waresFound = Config.userInterface.checkInventory(playerID, coordinates, translatedID);
      else
         waresFound = Config.userInterface.checkInventory(playerID, coordinates, wareID);

      // if player doesn't have the ware, stop
      if (waresFound.isEmpty())
         return;

      // check whether an inventory was found
      if (waresFound.get(0).quantity == -1) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_INVENTORY_MISSING);
         return;
      }

      // if no account ID was given, use the player's personal account
      if (accountID == null || accountID.isEmpty())
         accountID = Config.userInterface.getDisplayName(playerID);

      // grab the account to be used
      Account account = Account.grabAndCheckAccount(accountID, playerID);

      // if something's wrong with the account, stop
      if (account == null)
         return; // an error message has already been sent to the player

      // variable for finding how much to sell
      int quantityToSell = quantity;

      // only sell until the acceptable price is reached
      if (!Float.isNaN(minUnitPrice)) {
         // find out how much quantity is for sale until the given price
         quantityToSell = getQuantityUntilPrice(ware, minUnitPrice / pricePercent, false);

         // if nothing can be sold, sell nothing
         if (quantityToSell == 0)
            return; // no message to avoid needless messaging and ease autoselling

         // if an unacceptable unit price will not be reached,
         // then do not limit the order
         if (quantityToSell > quantity)
            quantityToSell = quantity;
      }

      // if transaction fees are used,
      // check whether profit will be made
      if (Config.chargeTransactionFees &&
          Config.transactionFeeSelling != 0.0f &&
          (Float.isNaN(minUnitPrice) || minUnitPrice > 0.0f)) {
         // calculate potential income and fee
         float price = getPrice(null, ware, quantityToSell, PriceType.CURRENT_SELL) * pricePercent;
         float fee   = calcTransactionFeeSelling(price);

         // if selling is guaranteed to lose money, don't sell
         if (fee != 0.0f && // if the fee is zero, it cannot incur costs
             price - fee + Account.canNegativeFeeBePaid(fee) <= 0.0f) {
            Config.userInterface.printErrorToUser(playerID, StringTable.MSG_TRANSACT_FEE_SALES_LOSS);
            return;
         }
      }

      // sell the ware
      float[] salesResults = sellStock(playerID, coordinates, waresFound, inventorySlot, quantityToSell, minUnitPrice, pricePercent);
      int quantitySold = (int) salesResults[1];

      // give money to the player
      account.addMoney(salesResults[0]);

      // report success
      // if the ware has an alias, use it
      if (ware.getAlias() == null || ware.getAlias().isEmpty()) {
         // print the name of the account used if it isn't a personal or default account
         if (accountID.equals(Config.userInterface.getDisplayName(account.getOwner())) ||
             accountID.equals(Config.userInterface.getDisplayName(playerID)))
            Config.userInterface.printToUser(playerID, "Sold " + Integer.toString(quantitySold) + " " + translatedID + " for " + PriceFormatter.PRICE_FORMAT.format(salesResults[0]));
         else
            Config.userInterface.printToUser(playerID, "Sold " + Integer.toString(quantitySold) + " " + translatedID + " for " + PriceFormatter.PRICE_FORMAT.format(salesResults[0]) + ", sent money to " + accountID);
      }
      else {
         // print the name of the account used if it isn't a personal account
         if (accountID.equals(Config.userInterface.getDisplayName(account.getOwner())) ||
             accountID.equals(Config.userInterface.getDisplayName(playerID)))
            Config.userInterface.printToUser(playerID, "Sold " + Integer.toString(quantitySold) + " " + ware.getAlias() + " for " + PriceFormatter.PRICE_FORMAT.format(salesResults[0]));
         else
            Config.userInterface.printToUser(playerID, "Sold " + Integer.toString(quantitySold) + " " + ware.getAlias() + " for " + PriceFormatter.PRICE_FORMAT.format(salesResults[0]) + ", sent money to " + accountID);
      }

      // pay the transaction fee
      if (Config.chargeTransactionFees &&
          Config.transactionFeeSelling != 0.0f) {
         // find fee's charge
         float fee = calcTransactionFeeSelling(salesResults[0]);

         // stop if the fee is zero
         if (fee == 0.0f)
            return;

         // check whether a fee collection account should be used
         if (Config.transactionFeesShouldPutFeesIntoAccount)
            // if the fee is negative and unaffordable, don't pay it
            if (Account.depositTransactionFee(fee))
               return;

         // pay the fee
         account.subtractMoney(fee);

         // report fee payment
         Config.userInterface.printToUser(playerID, Config.transactionFeeSellingMsg + PriceFormatter.PRICE_FORMAT.format(fee));
      }
   }

   /**
    * Sells all wares within a given inventory.
    *<p>
    * Complexity: O(n)
    * @param playerID     user responsible for the trading
    * @param coordinates  where wares may be found
    * @param inventory    wares to be sold and their information
    * @param accountID    key used to retrieve account information
    * @param pricePercent percentage multiplier for ware's price
    */
   public static void sellAll(UUID playerID, UserInterface.Coordinates coordinates,
      List<Stock> inventory, String accountID, float pricePercent) {
      if ((coordinates == null &&                            // if the given inventory is empty and no coordinates are given,
          (inventory   == null || inventory.isEmpty())) || // there is nothing to sell
          playerID     == null)                              // if no player was given, there is no party responsible for the purchase
         return;

      // if price multiplier is invalid, set it have no effect
      if (Float.isNaN(pricePercent))
         pricePercent = 1.0f;

      // if no account ID was given, use the player's personal account
      if (accountID == null || accountID.isEmpty())
         accountID = Config.userInterface.getDisplayName(playerID);

      // grab the account to be used
      Account account = Account.grabAndCheckAccount(accountID, playerID);

      // if something's wrong with the account, stop
      if (account == null)
         return; // an error message has already been sent to the player

      // validate coordinates
      if (coordinates != null) {
         if (Config.userInterface.getInventorySpaceAvailable(playerID, coordinates) == -1) {
            Config.userInterface.printErrorToUser(playerID, StringTable.MSG_INVENTORY_MISSING);
            return;
         }
      }

      // if transaction fees are used,
      // check whether profit is possible
      if (Config.chargeTransactionFees &&
          Config.transactionFeeSellingIsMult &&
          Config.transactionFeeSelling >= 1.0f) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_TRANSACT_FEE_SALES_LOSS);
         return;
      }

      // sell everything sellable
      float[] salesResults = sellStock(playerID, coordinates, inventory, 0, 0, 0.0001f, pricePercent);

      // deliver funds to account
      account.addMoney(salesResults[0]);

      // if nothing was sold, don't print anything
      if (salesResults[1] == 0.0f)
         return;

      // report success
      // print the name of the account used if it isn't a personal or default account
      if (accountID.equals(Config.userInterface.getDisplayName(account.getOwner())) ||
          accountID.equals(Config.userInterface.getDisplayName(playerID)))
         Config.userInterface.printToUser(playerID, "Sold " + Integer.toString((int) salesResults[1]) + " items for " + PriceFormatter.PRICE_FORMAT.format(salesResults[0]));
      else
         Config.userInterface.printToUser(playerID, "Sold " + Integer.toString((int) salesResults[1]) + " items for " + PriceFormatter.PRICE_FORMAT.format(salesResults[0]) + ", sent money to " + accountID);

      // pay the transaction fee
      if (Config.chargeTransactionFees &&
          Config.transactionFeeSelling != 0.0f) {
         // find fee's charge
         float fee = calcTransactionFeeSelling(salesResults[0]);

         // check whether a fee collection account should be used
         if (Config.transactionFeesShouldPutFeesIntoAccount)
            // if the fee is negative and unaffordable, don't pay it
            if (Account.depositTransactionFee(fee))
               return;

         // pay the fee
         account.subtractMoney(fee);

         // report fee payment
         Config.userInterface.printToUser(playerID, Config.transactionFeeSellingMsg + PriceFormatter.PRICE_FORMAT.format(fee));
      }
   }

   /**
    * Removes wares from a player's inventory and
    * tallies up money gained from selling those wares
    * as well as the total quantity of wares sold.
    * <p>
    * Complexity:<br>
    * O(n) without flat selling transaction fee<br>
    * O(n^2) with flat selling transaction fee
    * @param playerID      user responsible for the trade
    * @param coordinates   where wares may be found
    * @param stocks        wares to be sold and their information
    * @param inventorySlot where to begin selling within a container
    * @param quantity      how much wares should be sold; 0 means sell everything
    * @param minUnitPrice  stop selling if unit price is below this amount
    * @param pricePercent  percentage multiplier for ware's price
    * @return total money from selling wares and the quantity sold
    */
   private static float[] sellStock(UUID playerID, UserInterface.Coordinates coordinates,
                                    List<Stock> stocks, int inventorySlot, int quantity,
                                    float minUnitPrice, float pricePercent) {
      // if nothing should be sold, stop; 0 quantity means sell everything
      if (quantity < 0)
         return null;

      // if price multiplier is invalid, set it have no effect
      if (Float.isNaN(pricePercent))
         pricePercent = 1.0f;

      // set up variables
      LinkedList<Stock> unsoldStocks = null; // holds wares to be sold if the transaction turns out to be profitable despite paying a flat fee
      float   totalEarnings    = 0.0f;
      float   price;                         // value of the ware being processed
      int     quantityToSell   = 0;          // how much should be sold from the current stack
      int     quantitySold     = 0;          // how much quantity has been sold
      int     quantityDistFromFloor;         // how much quantity may be sold before reaching the price floor
      boolean isProfitable     = true;       // whether the transaction is profitable despite paying a flat fee

      // if a flat fee should be used,
      // store wares until it is known
      // whether the transaction is profitable
      if (Config.chargeTransactionFees &&
          !Config.transactionFeeSellingIsMult &&
          Config.transactionFeeSelling > 0.0f) {
         isProfitable = false;
         unsoldStocks = new LinkedList<Stock>();
      }

      // prevents other threads from adjusting the marketplace's wares
      acquireMutex();

      // loop through wares owned and get prices according to quality
      for (Stock stock : stocks) {
         // if ware is not in the market, stop
         if (stock.ware == null)
            continue;

         // if the price isn't high enough, stop
         price = PriceFormatter.truncatePrice(getPrice(playerID, stock.ware, 1, PriceType.CURRENT_SELL) * stock.percentWorth * pricePercent);
         if (price < minUnitPrice)
            continue;

         // check whether stock should not be sold past the price floor
         if (Config.noGarbageDisposing) {
            // find out if the ware can be sold
            if (hasReachedPriceFloor(stock.ware))
               continue; // if nothing may be sold, skip this ware

            // find how much may be sold
            if (stock.ware instanceof WareLinked)
               quantityDistFromFloor = ((WareLinked) stock.ware).getQuanWhenReachedPriceFloor() - stock.ware.getQuantity();
            else
               quantityDistFromFloor = getQuantityUntilPrice(stock.ware, getPrice(null, stock.ware, 1, PriceType.FLOOR_SELL) + 0.0001f, false) + 1;

            // if nothing may be sold, skip this ware
            if (quantityDistFromFloor <= 0)
               continue;

            // otherwise, sell the most possible
            if (quantityDistFromFloor < stock.quantity)
               stock.quantity = quantityDistFromFloor;
         }

         // try to sell the ware
         try {
            // figure how much to sell from the current stack
            if (quantity == 0 || quantity > quantitySold + stock.quantity)
               quantityToSell = stock.quantity;
            else
               quantityToSell = quantity - quantitySold;

            // get the money
            totalEarnings += getPrice(playerID, stock.ware, quantityToSell, PriceType.CURRENT_SELL) * stock.percentWorth;

            // if a flat fee should be used,
            // check whether the transaction became profitable
            isProfitable = isProfitable || totalEarnings * pricePercent > Config.transactionFeeSelling || minUnitPrice < 0.0f;

            // if the transaction is profitable, sell the ware
            if (isProfitable) {
               // take the ware
               Config.userInterface.removeFromInventory(playerID, coordinates, stock.wareID, inventorySlot, quantityToSell);
               quantitySold += quantityToSell;

               // add quantity sold to the marketplace
               stock.ware.addQuantity(quantityToSell);
            }

            // if a flat fee should be used,
            // hold off taking the ware until it is known
            // whether the transaction is profitable
            else
               unsoldStocks.add(stock);

            // if enough quantity has been sold,
            // stop searching for more
            if (quantity == quantitySold)
               break;
         } catch (Exception e) {
            Config.userInterface.printToConsole(StringTable.MSG_SELLALL + stock.wareID);
            e.printStackTrace();
            // don't return, keep trying to sell wares and pay the player
         }
      }

      // if a flat fee should be used and
      // the transaction is not profitable,
      // don't process it
      if (!isProfitable) {
         releaseMutex(); // allow other threads to adjust wares' properties
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_TRANSACT_FEE_SALES_LOSS);
         return new float[]{0.0f, 0.0f};
      }

      // if a flat fee should be used,
      // check whether any remaining goods should be sold
      else if (unsoldStocks != null && !unsoldStocks.isEmpty()) {
         // sell each stack of unsold wares
         for (Stock stock : unsoldStocks) {
            try {
               // take the ware
               Config.userInterface.removeFromInventory(playerID, coordinates, stock.wareID, inventorySlot, stock.quantity);
               quantitySold += stock.quantity;

               // add quantity sold to the marketplace
               stock.ware.addQuantity(stock.quantity);
            } catch (Exception e) {
               Config.userInterface.printToConsole(StringTable.MSG_SELLALL + stock.wareID);
               e.printStackTrace();
               // don't return, keep trying to sell wares and pay the player
            }
         }
      }

      // allow other threads to adjust wares' properties
      releaseMutex();

      // truncate to reduce error
      totalEarnings = PriceFormatter.truncatePrice(totalEarnings * pricePercent);

      // return total money gained and total quantity sold
      return new float[]{totalEarnings, (float) quantitySold};
   }

   /**
    * Displays ware price and quantity for a player.
    * <p>
    * Complexity: O(n^2)
    * @param playerID     user responsible for the trading
    * @param wareID       key used to retrieve ware information
    * @param quantity     how much would be traded
    * @param pricePercent percentage multiplier for ware's price
    */
   public static void check(UUID playerID, String wareID, int quantity, float pricePercent) {
      // if no player was given, there is no one to send messages to
      if (playerID == null)
         return;

      // check if ware id is empty
      if (wareID == null || wareID.isEmpty()) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_ID);
         return;
      }

      // grab the ware to be used
      Ware ware = translateAndGrab(wareID);
      // if ware is not in the market, check for a substitute
      if (ware == null && Config.allowWareTagSubstitution)
         ware = Config.userInterface.getOreDictionarySubstitution(wareID);
      // if no substitute exists or should be used, stop
      if (ware == null) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_MISSING + wareID);
         return;
      }
      wareID = ware.getWareID();

      // if ware is invalid, stop
      if (Float.isNaN(ware.getBasePrice())) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_WARE_INVALID + wareID);
         Config.userInterface.printToConsole(StringTable.ERROR_WARE_INVALID + wareID + StringTable.WARN_FILE_WARE_INVALID + Config.filenameWaresSave);
         waresErrored.add(ware.toJSON() + '\n');
         wares.remove(wareID);
         return;
      }

      // if price multiplier is invalid, set it have no effect
      if (Float.isNaN(pricePercent))
         pricePercent = 1.0f;

      // get ware's alias for printing
      final String ALIAS = ware.getAlias();

      // prepare to find unit and total prices
      float priceBuy = 0.0f;
      float priceSell;

      // if ware is untradeable, stop
      if (ware instanceof WareUntradeable) {
         // find unit price for buying
         priceBuy = getPrice(playerID, ware, 1, PriceType.CURRENT_BUY) * pricePercent;

         // if necessary, factor in transaction fee
         if (Config.chargeTransactionFees && Config.transactionFeeBuying != 0.00f)
            priceBuy += calcTransactionFeeBuying(priceBuy);

         // print the untradeable ware's price
         if (ALIAS != null &&
             !ALIAS.isEmpty()) {
            Config.userInterface.printToUser(playerID, ALIAS + " (" + wareID
               + "): " + PriceFormatter.PRICE_FORMAT.format(priceBuy));
         } else {
            Config.userInterface.printToUser(playerID, wareID
               + ": " + PriceFormatter.PRICE_FORMAT.format(priceBuy));
         }
         return;
      }

      // prepare to print prices
      final boolean PRINT_BOTH_PRICES = Config.priceBuyUpchargeMult != 1.0f ||
                                        (Config.chargeTransactionFees && (Config.transactionFeeBuying != 0.00f || Config.transactionFeeSelling != 0.00f)) ||
                                        (Config.buyingOutOfStockWaresAllowed && ware.getQuantity() == 0);
      priceSell = getPrice(playerID, ware, 1, PriceType.CURRENT_SELL) * pricePercent;
      if (PRINT_BOTH_PRICES) {
         priceBuy = getPrice(playerID, ware, 1, PriceType.CURRENT_BUY) * pricePercent;

         // if necessary, factor in transaction fee
         if (Config.chargeTransactionFees && Config.transactionFeeBuying != 0.00f)
            priceBuy += calcTransactionFeeBuying(priceBuy);
      }

      // if necessary, factor in selling transaction fee
      if (Config.chargeTransactionFees && Config.transactionFeeSelling != 0.00f)
         priceSell -= calcTransactionFeeSelling(priceSell);

      // if the ware has an alias, use it
      if (ALIAS != null &&
          !ALIAS.isEmpty()) {
         // print prices for buying and selling
         if (PRINT_BOTH_PRICES) {
            Config.userInterface.printToUser(playerID, ALIAS + " (" + wareID
               + "): Buy - " + PriceFormatter.PRICE_FORMAT.format(priceBuy)
               + " | Sell - " + PriceFormatter.PRICE_FORMAT.format(priceSell)
               + ", " + Integer.toString(ware.getQuantity()));
         }
         // only print one price
         else
            Config.userInterface.printToUser(playerID, ALIAS + " (" + wareID
               + "): " + PriceFormatter.PRICE_FORMAT.format(priceSell)
               + ", " + Integer.toString(ware.getQuantity()));
      }
      // if the ware doesn't have an alias,
      // don't try to print an alias
      else {
         // print prices for buying and selling
         if (PRINT_BOTH_PRICES) {
            Config.userInterface.printToUser(playerID, wareID
               + ": Buy - " + PriceFormatter.PRICE_FORMAT.format(priceBuy)
               + " | Sell - " + PriceFormatter.PRICE_FORMAT.format(priceSell)
               + ", " + Integer.toString(ware.getQuantity()));
         }
         // only print one price
         else
            Config.userInterface.printToUser(playerID, wareID
               + ": " + PriceFormatter.PRICE_FORMAT.format(priceSell)
               + ", " + Integer.toString(ware.getQuantity()));
      }

      // if there is no need to print a specific quantity, stop
      if (quantity < 2)
         return;

      // if a specific quantity is specified,
      // print prices for buying and selling
      else {
         priceBuy  = getPrice(playerID, ware, quantity, PriceType.CURRENT_BUY)  * pricePercent;
         priceSell = getPrice(playerID, ware, quantity, PriceType.CURRENT_SELL) * pricePercent;

         // if necessary, include transaction fees
         if (Config.chargeTransactionFees) {
            if (Config.transactionFeeBuying != 0.00f)
               priceBuy += calcTransactionFeeBuying(priceBuy);
            if (Config.transactionFeeSelling != 0.00f)
               priceSell -= calcTransactionFeeSelling(priceSell);
         }

         // report prices
         Config.userInterface.printToUser(playerID, "   for " + Integer.toString(quantity)
            + ": Buy - " + PriceFormatter.PRICE_FORMAT.format(priceBuy)
            + " | Sell - " + PriceFormatter.PRICE_FORMAT.format(priceSell));
      }
   }

   /**
    * Displays ware price and quantity of a damageable ware for a player.
    * <p>
    * Since checking damaged wares' prices is a rare occasion,
    * the functionality was implemented using this small, overloaded function
    * to avoid adding a rarely-used parameter and extra branch
    * to the commonly-used price checking function.
    * <p>
    * Complexity: O(n^2)
    * @param playerID     user responsible for the trading
    * @param wareID       key used to retrieve ware information
    * @param quantity     how much would be traded
    * @param percentWorth ware's damage as a percentage
    * @param pricePercent percentage multiplier for ware's price
    */
   public static void check(UUID playerID, String wareID, int quantity,
                            float percentWorth, float pricePercent) {
      check(playerID, wareID, quantity, pricePercent);

      // check whether anything could be printed
      if (playerID == null || wareID == null || wareID.isEmpty())
         return;

      // grab the ware to be used
      Ware ware = translateAndGrab(wareID);
      // if ware is not in the market, check for a substitute
      if (ware == null && Config.allowWareTagSubstitution)
         ware = Config.userInterface.getOreDictionarySubstitution(wareID);
      // if no substitute exists or should be used, stop
      if (ware == null || Float.isNaN(ware.getBasePrice()) || ware instanceof WareUntradeable)
         return;

      // if price multiplier is invalid, set it have no effect
      if (Float.isNaN(pricePercent))
         pricePercent = 1.0f;

      // use percent worth to give price if player sells
      if (percentWorth != 1.0f) {
         float priceSell;

         if (quantity < 2) {
            // find selling price
            priceSell = getPrice(playerID, ware, 1, PriceType.CURRENT_SELL) * percentWorth * pricePercent;

            // if necessary, include transaction fee
            if (Config.chargeTransactionFees) {
               // add on fee
               if (Config.transactionFeeSelling != 0.00f)
                  priceSell -= calcTransactionFeeSelling(priceSell);
            }

            // report price
            Config.userInterface.printToUser(playerID, "   for held inventory: Sell - " + PriceFormatter.PRICE_FORMAT.format(priceSell));
         }
         else {
            // find selling price
            priceSell = getPrice(playerID, ware, quantity, PriceType.CURRENT_SELL) * percentWorth * pricePercent;

            // if necessary, include transaction fee
            if (Config.chargeTransactionFees) {
               // add on fee
               if (Config.transactionFeeSelling != 0.00f)
                  priceSell -= calcTransactionFeeSelling(priceSell);
            }

            // report price
            Config.userInterface.printToUser(playerID, "   for " + Integer.toString(quantity)
            + " of held inventory: Sell - " + PriceFormatter.PRICE_FORMAT.format(priceSell));
         }
      }
   }

   /**
    * Prevents other threads from adjusting the marketplace's wares.
    * If another thread is already adjusting wares, then waits for that thread to finish.
    * <p>
    * Complexity: O(1)
    */
   public static void acquireMutex() {
      // wait for permission to adjust ware's properties
      // check if another thread is adjusting wares' properties
      if (doNotAdjustWares) {
         // sleep() may throw an exception
         try {
            while (doNotAdjustWares) {
               Thread.sleep(10); // 10 ms wait for mutex to become available
            }
         } catch(Exception ex) {
            Thread.currentThread().interrupt();
         }
      }

      // prevent other threads from adjusting wares' properties
      doNotAdjustWares = true;
      // wait until any other threads finish execution since mutex is loose
      try {
         Thread.sleep(5);
      } catch(Exception ex) {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Checks whether other threads are adjusting the marketplace's wares
    * and waits for them to finish if they are.
    * <p>
    * Complexity: O(1)
    */
   private static void waitForMutex() {
      // check if another thread is adjusting wares' properties
      if (doNotAdjustWares) {
         // sleep() may throw an exception
         try {
            while (doNotAdjustWares) {
               Thread.sleep(10); // 10 ms wait for mutex to become available
            }
         } catch(Exception ex) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * Allows other threads to adjust wares' properties.
    * <p>
    * Complexity: O(1)
    */
   public static void releaseMutex() {
      doNotAdjustWares = false;
   }

   /**
    * Spawns and handles threads for features.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfigPeriodicEvents() {
      AIHandler.startOrReconfig();        // if necessary, start, reload, or stop AI
      RandomEvents.startOrReconfig();     // if necessary, start, reload, or stop random events
      MarketRebalancer.startOrReconfig(); // if necessary, start, reload, or stop automatic market rebalancing
   }

   /**
    * Closes threads for features.
    * <p>
    * Complexity: O(1)
    */
   public static void endPeriodicEvents() {
      AIHandler.end();        // if necessary, stop AI
      RandomEvents.end();     // if necessary, stop random events
      MarketRebalancer.end(); // if necessary, stop automatic marketplace rebalancing
   }

   /**
    * Averages current prices of all wares available within the marketplace.
    * <p>
    * Complexity: O(n), where n is the number of wares in the market
    * @return average price of all wares for sale on the market
    */
   public static float getCurrentPriceAverage() {
      // initialize variables
      float currentPriceAverage = 0.0f;

      float spreadAdjustment = 0.0f; // spread's effect on price
      float priceCurrent     = 0.0f; // ware's price at this moment
      float priceBase        = 0.0f; // ware's price at equilibrium

      int   quanCeiling      = 0;
      int   quanFloor        = 0;
      int   quanEquilibrium  = 0;
      int   quanOnMarket     = 0;
      float quanFloorFromEquilibrium   = 0.0f; // how much quantity is between the price floor stock and equilibrium stock
      float quanCeilingFromEquilibrium = 0.0f; // cast as a float now since it will only be used as a float

      float priceMinimum; // price floor to be enforced

      // precalculate repeatedly used information
      float spreadMult       = 1.0f - Config.priceSpread;
      boolean usePriceSpread = Config.priceSpread != 1.0f;
      boolean usePriceBuyUpchargeMult = Config.priceBuyUpchargeMult != 1.0f;

      // loop through all wares to get their prices
      for (Ware ware : wares.values()) {
         // prevents a null pointer exception if wares are being reloaded
         if (ware == null)
            continue;

         // if the ware is untradeable or simply a grouping of other wares, skip it
         if (ware instanceof WareUntradeable || ware instanceof WareLinked)
            continue;

         // find the ware's current price
         // get ware information
         priceBase       = ware.getBasePrice();
         quanCeiling     = Config.quanExcessive[ware.getLevel()];
         quanFloor       = Config.quanDeficient[ware.getLevel()];
         quanEquilibrium = Config.quanEquilibrium[ware.getLevel()];
         quanOnMarket    = ware.getQuantity();
         quanFloorFromEquilibrium   = (float) (quanEquilibrium - quanFloor);
         quanCeilingFromEquilibrium = (float) (quanCeiling - quanEquilibrium);

         // if spread is normal or base is 0, make no adjustment
         if (usePriceSpread && priceBase != 0.0f)
            // spreadAdjustment = distance from average * distance multiplier
            spreadAdjustment = ((float) priceBaseMedian - priceBase) * spreadMult;

         // check if purchasing upcharge should be applied
         if (usePriceBuyUpchargeMult)
            // calculate price with upcharge multiplier
            priceCurrent = (priceBase + spreadAdjustment) * Config.priceBuyUpchargeMult * Config.priceMult;
         else
            // calculate price without upcharge multiplier
            priceCurrent = (priceBase + spreadAdjustment) * Config.priceMult;

         // factor in components' prices affecting manufactured prices
         if (Config.shouldComponentsCurrentPricesAffectWholesPrice && ware.hasComponents() && !Config.pricesIgnoreSupplyAndDemand)
            priceCurrent *= ware.getLinkedPriceMultiplier();

         // find price floor to be enforced
         priceMinimum = priceCurrent * Config.priceFloor;

         // calculate scarcity's effect on price
         // if above equilibrium, lower the price
         // if below, raise
         if (quanOnMarket > quanEquilibrium) {
            // enforce a non-zero price floor
            if (quanOnMarket >= quanCeiling)
               priceCurrent *= Config.priceFloor;
            else
               // saturation price drop percent = price floor multiplier - percent distance away from equilibrium toward overstocked
               priceCurrent *= Config.priceFloorAdjusted - (((float) (quanOnMarket - quanEquilibrium)) / quanCeilingFromEquilibrium);
         }
         else if (quanOnMarket < quanEquilibrium) {
            if (quanOnMarket <= quanFloor)
               priceCurrent *= Config.priceCeiling;
            else
               // scarcity price rise percent = price ceiling multiplier - percent distance away from equilibrium toward stock floor
               priceCurrent *= Config.priceCeilingAdjusted - (((float) (quanOnMarket - quanEquilibrium)) / quanFloorFromEquilibrium);
         }

         // enforce a price floor
         if (priceCurrent >= priceMinimum)
            // add ware's current price to the average
            currentPriceAverage += priceCurrent;
         else
            currentPriceAverage += priceMinimum;
      }

      // average the prices
      currentPriceAverage /= wares.size() - numStatisticExcludedWares;

      // truncate the price to avoid rounding and multiplication errors
      return PriceFormatter.truncatePrice(currentPriceAverage);
   }

   /**
    * Returns the fee to be paid based on a given price.
    * <p>
    * Complexity: O(1)
    * @param price transaction's total cost
    * @return purchasing fee
    */
   public static float calcTransactionFeeBuying(float price) {
      // if the fee is a percentage of the price, calculate it
      if (Config.transactionFeeBuyingIsMult) {
         price *= Config.transactionFeeBuying;

         // if the price and fee percentage have opposite signs, flip the fee's sign
         // so positive rates don't pay out anything and negative rates don't take anything
         if ((price < 0.0f && Config.transactionFeeBuying > 0.0f) ||
             (Config.transactionFeeBuying < 0.0f && price > 0.0f))
            price = -price;

         return price;
      }

      // otherwise, return a flat rate
      else
         return Config.transactionFeeBuying;
   }

   /**
    * Returns the fee to be paid based on a given price.
    * <p>
    * Complexity: O(1)
    * @param price transaction's total cost
    * @return offering fee
    */
   public static float calcTransactionFeeSelling(float price) {
      // if the fee is a percentage of the price, calculate it
      if (Config.transactionFeeSellingIsMult) {
         price *= Config.transactionFeeSelling;

         // if the price and fee percentage have opposite signs, flip the fee's sign
         // so positive rates don't pay out anything and negative rates don't take anything
         if ((price < 0.0f && Config.transactionFeeSelling > 0.0f) ||
             (Config.transactionFeeSelling < 0.0f && price > 0.0f))
            price = -price;

         return price;
      }

      // otherwise, return a flat rate
      else
         return Config.transactionFeeSelling;
   }

   /**
    * Determines whether a ware is currently at or past its price floor.
    * <p>
    * Complexity:<br>
    * Average-Case: O(1)<br>
    * Worst-Case: O(2n), whether n is the number of the ware's components
    * @param ware tradeable item whose status regarding its price floor should be determined
    * @return <code>true</code> if the ware's current price is at or past its price floor
    *         <code>false</code> if the ware's current price is below its price floor
    */
   public static boolean hasReachedPriceFloor(final Ware ware) {
      return getPrice(null, ware, 1, PriceType.CURRENT_SELL) <= getPrice(null, ware, 1, PriceType.FLOOR_SELL);
   }
}