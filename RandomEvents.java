package commandeconomy;

import com.google.gson.Gson;                    // for saving and loading
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;                            // for handling files
import java.io.FileReader;
import com.google.gson.JsonSyntaxException;     // for more specific error messages when parsing files
import java.util.Timer;                         // for triggering events periodically and on a separate thread
import java.util.TimerTask;                     // for disabling random events mid-execution
import java.util.Collections;                   // for communication between the main thread and the thread handling random events
import java.util.Set;
import java.util.EnumSet;                       // EnumSets are faster than HashSets, so they are used for inter-thread communication
import java.lang.StringBuilder;                 // for generating ware change descriptions and reporting invalid ware IDs
import java.util.concurrent.ThreadLocalRandom;  // for randomizing event frequency and decisions

/**
 * Creating an instance of this class initializes
 * a singleton handler for random events.
 * <p>
 * This class is meant to handle random events using an independent thread.
 * Public methods leave requests on a non-blocking queue.
 * Requests are serviced just before the next random event is triggered.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2022-06-11
 */
@SuppressWarnings("deprecation") // unfortunately, Minecraft 1.12.2's gson requires a JSONParser object
public class RandomEvents extends TimerTask {
   // STATIC VARIABLES
   // events
   /** events which might occur */
   static RandomEvent[] randomEvents = null;
   /** how much to adjust when an event greatly affects a ware */
   static int[] quanChangeLarge  = null;
   /** how much to adjust when an event moderately affects a ware */
   static int[] quanChangeMedium = null;
   /** how much to adjust when an event slightly affects a ware */
   static int[] quanChangeSmall  = null;
   /** valid flags for how much to change wares' quantities available for sale */
   enum QuantityForSaleChangeMagnitudes {
      NEGATIVE_LARGE,
      NEGATIVE_MEDIUM,
      NEGATIVE_SMALL,
      NONE,
      POSITIVE_SMALL,
      POSITIVE_MEDIUM,
      POSITIVE_LARGE
   }

   // thread management
   /** manages thread triggering random events */
   static Timer timerRandomEvents = null;
   /** handles random events */
   static RandomEvents timerTaskRandomEvents = null;
   /** used to monitor a timer interval for configuration changes */
   static long oldFrequency = 0L;
   /** used to monitor changes in quantity change percentages for configuration changes */
   static float[] oldQuantityChangePercents = null;
   /** used to signal what should be reloaded or recalculated */
   enum QueueCommands {
      LOAD,
      CALC_QUANTITY_CHANGES,
      LOAD_WARES,
      GEN_WARE_DESCS
   }
   /** used to signal thread to reload or recalculate variables */
   static Set<QueueCommands> queue = null;

   // INSTANCE VARIABLES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;

   // STATIC METHODS
   /**
    * Spawns and handles a thread for handling random events.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfig() {
      // calculate frequency using settings for frequency and variance
      long newFrequency = ((long) (Config.randomEventsFrequency * (1.0f - Config.randomEventsVariance)) * 60000L); // 60000 ms per min.
      // enforce a positive floor
      if (newFrequency <= 0.0)
         newFrequency = 60000L; // 60000 ms per min.

      // if necessary, start, reload, or stop random events
      if (Config.randomEvents && Config.randomEventsFrequency > 0) {
         // set up random events if they haven't been already
         if (queue == null) {
            // set up queue
            queue = Collections.synchronizedSet(EnumSet.noneOf(QueueCommands.class));

            // leave requests to set up random events
            queue.add(QueueCommands.LOAD);
            queue.add(QueueCommands.CALC_QUANTITY_CHANGES);

            // record change amounts for quantities for the first time
            if (oldQuantityChangePercents == null) {
               oldQuantityChangePercents    = new float[3];
               oldQuantityChangePercents[0] = Config.randomEventsSmallChange;
               oldQuantityChangePercents[1] = Config.randomEventsMediumChange;
               oldQuantityChangePercents[2] = Config.randomEventsLargeChange;
            }
         }

         // if necessary, recalculate change amounts for quantities
         if (oldQuantityChangePercents != null &&
             (oldQuantityChangePercents[0] != Config.randomEventsSmallChange  ||
              oldQuantityChangePercents[1] != Config.randomEventsMediumChange ||
              oldQuantityChangePercents[2] != Config.randomEventsLargeChange))
            calcQuantityChanges();

         // start random events
         if (timerRandomEvents == null) {
            // initialize timer objects
            timerRandomEvents     = new Timer(true);
            timerTaskRandomEvents = new RandomEvents();

            // initialize random events
            timerRandomEvents.scheduleAtFixedRate(timerTaskRandomEvents, 0L, newFrequency);
         }

         // reload random events
         else if (oldFrequency != newFrequency) {
            // There's no way to change a task's period.
            // Therefore, it is necessary to stop the current task
            // and schedule a new one.
            timerTaskRandomEvents.stop = true;
            timerTaskRandomEvents.cancel();

            // initialize timertask object
            timerTaskRandomEvents = new RandomEvents();

            // initialize random events
            timerRandomEvents.scheduleAtFixedRate(timerTaskRandomEvents, newFrequency, newFrequency);
         }
      }

      // stop random events
      else if (timerRandomEvents != null &&
               (!Config.randomEvents || Config.randomEventsFrequency <= 0))
         end();

      // record timer interval to monitor for changes
      oldFrequency = newFrequency;
   }

   /**
    * Closes the thread handling random events.
    * <p>
    * Complexity: O(1)
    */
   public static void end() {
      // if necessary, stop random events
      if (timerRandomEvents != null) {
         timerTaskRandomEvents.stop = true;
         timerTaskRandomEvents = null;
         timerRandomEvents.cancel();
         timerRandomEvents = null;

         // deallocate memory
         randomEvents              = null;
         quanChangeLarge           = null;
         quanChangeMedium          = null;
         quanChangeSmall           = null;
         oldQuantityChangePercents = null;
         queue                     = null;
      }
   }

   /**
    * Prepares for using random events.
    * <p>
    * This method sends a request to the thread handling random events
    * to perform a given task before firing the next random event.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(n), where n is events to load
    */
   public static void load() {
      if (queue == null)
         return;

      queue.add(QueueCommands.LOAD);
   }

   /**
    * Recalculates events' effects on quantities available for sale
    * according to configuration settings.
    * <p>
    * This method sends a request to the thread handling random events
    * to perform a given task before firing the next random event.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(1)
    */
   public static void calcQuantityChanges() {
      if (queue == null)
         return;

      queue.add(QueueCommands.CALC_QUANTITY_CHANGES);

      // record quantity change amounts to monitor for changes
      oldQuantityChangePercents    = new float[3];
      oldQuantityChangePercents[0] = Config.randomEventsSmallChange;
      oldQuantityChangePercents[1] = Config.randomEventsMediumChange;
      oldQuantityChangePercents[2] = Config.randomEventsLargeChange;
   }

   /**
    * Links random events to wares they affect.
    * Necessary if wares are reloaded.
    * <p>
    * This method sends a request to the thread handling random events
    * to perform a given task before firing the next random event.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(n*m)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   public static void loadWares() {
      if (queue == null)
         return;

      queue.add(QueueCommands.LOAD_WARES);
   }

   /**
    * Generates descriptions stating which wares are affected by events,
    * if they haven't been generated already.
    * This is necessary if printing wares' changes was initially disabled,
    * then was enabled by reloading configuration.
    * <p>
    * This method sends a request to the thread handling random events
    * to perform a given task before firing the next random event.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(n*m) or O(1)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   public static void generateWareChangeDescriptions() {
      if (queue == null)
         return;

      queue.add(QueueCommands.GEN_WARE_DESCS);
   }

   /**
    * Prepares for using random events.
    * <p>
    * Complexity: O(n^2), where n is events to load
    */
   private static void loadPrivate() {
      // if the marketplace is uninitialized,
      // there are no wares for AI to trade
      if (Marketplace.getAllWares().size() == 0)
         return;

      // try to load the events file
      File fileRandomEvents = new File(Config.filenameRandomEvents); // contains RandomEvent objects
      // if the local file isn't found, use the global file
      if (!Config.crossWorldMarketplace && !fileRandomEvents.isFile()) {
         fileRandomEvents = new File("config" + File.separator + "CommandEconomy" + File.separator + Config.filenameNoPathRandomEvents);
      }

      // check file existence
      if (!fileRandomEvents.isFile()) {
         // don't throw an exception, print a warning to advise user to reload wares
         Config.commandInterface.printToConsole(CommandEconomy.WARN_FILE_MISSING + Config.filenameRandomEvents +
            System.lineSeparator() + "To use random events, replace " + Config.filenameRandomEvents +
            "," + System.lineSeparator() + "then use the command \"reload config\"."
         );
         return;
      }

      // prepare to read the JSON file
      Gson gson = new Gson();
      FileReader fileReader = null; // use a handle to ensure the file gets closed

      // attempt to read file
      try {
         fileReader = new FileReader(fileRandomEvents);
         randomEvents = gson.fromJson(fileReader, RandomEvent[].class);
         fileReader.close();
      }
      catch (JsonSyntaxException e) {
         randomEvents = null; // disable random events
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_FILE_RANDOM_EVENTS_INVALID + Config.filenameRandomEvents);
      }
      catch (Exception e) {
         randomEvents = null; // disable random events
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_FILE_RANDOM_EVENTS_PARSING + Config.filenameRandomEvents);
         e.printStackTrace();
      }

      // check whether any events were loaded
      if (randomEvents == null || randomEvents.length <= 0) {
         randomEvents = null; // disable random events

         // ensure the file is closed
         try {
            if (fileReader != null)
               fileReader.close();
         } catch (Exception e) { }

         Config.commandInterface.printToConsole(CommandEconomy.WARN_RANDOM_EVENTS_NONE_LOADED);
         end();
         return;
      }

      // validate random events
      int           size                   = randomEvents.length;   // how many random events need to be parsed
      RandomEvent[] compressedRandomEvents = new RandomEvent[size]; // eases copying events to an array without any null entries
      RandomEvent   randomEvent            = null;                  // current random event being parsed
      StringBuilder loadingErrors          = new StringBuilder();   // if any events fail to load, buffer their error messages before printing them
      int           invalidEntries         = 0;                     // how many random events failed to load
      int           compressedIndex        = 0;                     // index into array containing only valid random events

      for (int i = 0; i < size; i++) {
         randomEvent = randomEvents[i];

         // validate description
         if (randomEvent.description == null || randomEvent.description.isEmpty()) {
            loadingErrors.append(CommandEconomy.ERROR_RANDOM_EVENT_DESC_MISSING);
            invalidEntries++; // mark how many random events fail to load
            continue;
         }

         // validate effects
         // check whether magnitudes were loaded
         if (randomEvent.changeMagnitudes == null) {
            loadingErrors.append(CommandEconomy.ERROR_RANDOM_EVENT_MAGNITUDES_MISSING + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description + System.lineSeparator());
            invalidEntries++;
            continue;
         }

         // check whether magnitudes entries were loaded
         if (randomEvent.changeMagnitudes.length == 0) {
            loadingErrors.append(CommandEconomy.ERROR_RANDOM_EVENT_MAGNITUDES_BLANK + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description + System.lineSeparator());
            invalidEntries++;
            continue;
         }

         // validate ware IDs
         // check whether ware IDs were loaded
         if (randomEvent.changedWaresIDs == null) {
            loadingErrors.append(CommandEconomy.ERROR_RANDOM_EVENT_WARES_MISSING + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description + System.lineSeparator());
            invalidEntries++;
            continue;
         }

         // check whether ware ID entries were loaded
         if (randomEvent.changedWaresIDs.length == 0) {
            loadingErrors.append(CommandEconomy.ERROR_RANDOM_EVENT_WARES_BLANK + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description + System.lineSeparator());
            invalidEntries++;
            continue;
         }

         // validate effects and ware IDs
         // check whether the is a magnitude for every ware ID
         if (randomEvent.changedWaresIDs.length != randomEvent.changeMagnitudes.length) {
            loadingErrors.append(CommandEconomy.ERROR_RANDOM_EVENT_CHANGES_MISMATCH + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description + System.lineSeparator());
            invalidEntries++;
            continue;
         }

         // if the entry is valid,
         // save it in the valid-events array's next available entry
         compressedRandomEvents[compressedIndex] = randomEvent;
         compressedIndex++;
      }

      // if necessary, resize the random events array
      if (invalidEntries > 0) {
         // change reference to prevent allocating a third array
         randomEvents = compressedRandomEvents;

         // copy event references to appropriately-sized array
         compressedRandomEvents = new RandomEvent[compressedIndex];
         System.arraycopy(randomEvents, 0, compressedRandomEvents, 0, compressedIndex);

         // replace old array with new
         randomEvents = compressedRandomEvents;

         // print error messages
         Config.commandInterface.printToConsole(loadingErrors.substring(0, loadingErrors.length() - System.lineSeparator().length())); // remove the trailing new line
         loadingErrors = null; // deallocate memory sooner rather than later
      }

      // attempt to load the random events' wares
      loadWaresPrivate();

      // check whether any events were loaded
      if (randomEvents.length <= 0) {
         randomEvents = null; // disable random events
         Config.commandInterface.printToConsole(CommandEconomy.WARN_RANDOM_EVENTS_NONE_LOADED);
         end();
         return;
      }

      // if random events loaded successfully,
      // see if wares' quantities for sale should be generated
      generateWareChangeDescriptionsPrivate();
   }

   /**
    * Recalculates events' effects on quantities available for sale
    * according to configuration settings.
    * <p>
    * Complexity: O(1)
    */
   private static void calcQuantityChangesPrivate() {
      // if necessary, initialize arrays
      if (quanChangeLarge == null) {
         quanChangeLarge  = new int[6];
         quanChangeMedium = new int[6];
         quanChangeSmall  = new int[6];
      }

      // initialize percentage variables
      float changePercentLarge  = Config.randomEventsLargeChange;
      float changePercentMedium = Config.randomEventsMediumChange;
      float changePercentSmall  = Config.randomEventsSmallChange;

      // if necessary, convert flat rate to percentage
      if (!Config.randomEventsAreChangesPercents) {
         changePercentLarge  /= Config.quanMid[2];
         changePercentMedium /= Config.quanMid[2];
         changePercentSmall  /= Config.quanMid[2];
      }

      // calculate change amounts
      for (int i = 0; i < 6; i++) {
         quanChangeLarge[i]  = (int) (changePercentLarge  * Config.quanMid[i]);
         quanChangeMedium[i] = (int) (changePercentMedium * Config.quanMid[i]);
         quanChangeSmall[i]  = (int) (changePercentSmall  * Config.quanMid[i]);
      }
   }

   /**
    * Links random events to wares they affect.
    * Necessary if wares are reloaded.
    * <p>
    * Complexity: O(n*m)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   private static void loadWaresPrivate() {
      if (randomEvents == null)
         return;


      // prepare to load wares
      StringBuilder invalidWareIDs       = null;  // if reporting is enabled, prepares invalid ware IDs and aliases before printing them
      Ware[]        newChangedWares      = null;  // holds a reference to a newly allocated array of wares when resizing an array of affected wares
      QuantityForSaleChangeMagnitudes[] newChangeMagnitudes = null; // holds a reference to a newly allocated array of change magnitudes when resizing an array of change magnitudes affecting wares
      Ware          changedWares         = null;
      Ware          ware;                         // holds ware reference before adding to array
      int           invalidEvents        = 0;     // track whether any events failed to reload
      int           size                 = 0;     // how many ware IDs should be processed
      int           randomEventsIndex    = 0;     // tracks which event should be marked as invalid
      int           changedWaresIDsIndex = 0;     // eases parsing IDs of possibly affected wares
      int           changedWaresIndex    = 0;     // eases shrinking the array holding ware references
      boolean       foundInvalidEntry    = false; // whether a ware ID is unusable

      // reload wares for each random event
      for (RandomEvent randomEvent : randomEvents) {
         // if wares cannot be loaded, flag an error
         if (randomEvent.changedWaresIDs == null ||
             randomEvent.changedWaresIDs.length == 0) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_WARES_BLANK + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description);
            randomEvents[randomEventsIndex] = null;
            invalidEvents++;
            randomEventsIndex++;
            continue;
         }

         // initialize variables
         size                     = randomEvent.changedWaresIDs.length;
         randomEvent.changedWares = new Ware[size];                                       // holds affected ware references
         changedWaresIndex        = 0;
         randomEvent.changeMagnitudesCurrent = new QuantityForSaleChangeMagnitudes[size]; // holds how much each ware should be changed
         foundInvalidEntry        = false;

         // grab each ware's latest reference
         for (changedWaresIDsIndex = 0; changedWaresIDsIndex < size; changedWaresIDsIndex++) {
            ware = Marketplace.translateAndGrab(randomEvent.changedWaresIDs[changedWaresIDsIndex]);

            // if the ware is fine, use it
            if (ware != null && !(ware instanceof WareUntradeable)) {
               randomEvent.changedWares[changedWaresIndex]            = ware;                                               // move ware reference into correct location
               randomEvent.changeMagnitudesCurrent[changedWaresIndex] = randomEvent.changeMagnitudes[changedWaresIDsIndex]; // move change magnitude into corresponding location
               changedWaresIndex++;
            }
            // if the ware is not fine, record it
            else {
               // if enabled, print which scenario is using an invalid ware ID
               if (Config.randomEventsReportInvalidWares && !foundInvalidEntry) {
                  // allocate space for holding invalid ware IDs before printing
                  if (invalidWareIDs == null)
                     invalidWareIDs = new StringBuilder();

                  // prepare front matter for reporting the event's invalid IDs
                  invalidWareIDs.append(CommandEconomy.ERROR_RANDOM_EVENT_WARES_INVALID +
                                        CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description +
                                        CommandEconomy.ERROR_RANDOM_EVENT_WARES_INVALID_LIST);
               }

               // flag that at least one invalid entry has been found
               foundInvalidEntry = true;

               // if enabled, report invalid IDs
               if (Config.randomEventsReportInvalidWares)
                  invalidWareIDs.append(randomEvent.changedWaresIDs[changedWaresIDsIndex] + ", ");
            }
         }

         // check whether any wares were loaded
         if (changedWaresIndex == 0) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_WARES_NO_VALID + CommandEconomy.MSG_RANDOM_EVENT_DESC + randomEvent.description);
            randomEvents[randomEventsIndex] = null;
            invalidEvents++;
            randomEventsIndex++;
            continue;
         }

         // check whether the arrays holding ware data should be resized
         // and whether invalid ware IDs should be printed
         if (foundInvalidEntry) {
            newChangedWares     = new Ware[changedWaresIndex];
            newChangeMagnitudes = new QuantityForSaleChangeMagnitudes[changedWaresIndex];
            System.arraycopy(randomEvent.changedWares, 0, newChangedWares, 0, changedWaresIndex);
            System.arraycopy(randomEvent.changeMagnitudesCurrent, 0, newChangeMagnitudes, 0, changedWaresIndex);
            randomEvent.changedWares            = newChangedWares;
            randomEvent.changeMagnitudesCurrent = newChangeMagnitudes;

            // check whether invalid ware IDs should be printed
            if (Config.randomEventsReportInvalidWares) {
               Config.commandInterface.printToConsole(invalidWareIDs.substring(0, invalidWareIDs.length() - 2)); // remove the trailing comma and space
               invalidWareIDs.setLength(0); // clear buffer
            }
         }

         randomEventsIndex++;
      }

      // check whether the array holding random event references should be resized
      if (invalidEvents > 0) {
         RandomEvent[] newRandomEvents = new RandomEvent[randomEvents.length - invalidEvents];

         changedWaresIndex = 0;
         for (RandomEvent randomEvent : randomEvents) {
            if (randomEvent != null) {
               newRandomEvents[changedWaresIndex] = randomEvent;
               changedWaresIndex++;
            }
         }

         randomEvents = newRandomEvents;
      }
   }

   /**
    * Generates descriptions stating which wares are affected by events,
    * if they haven't been generated already.
    * This is necessary if printing wares' changes was initially disabled,
    * then was enabled by reloading configuration.
    * <p>
    * Complexity: O(n*m) or O(1)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   private static void generateWareChangeDescriptionsPrivate() {
      // check whether descriptions should be generated
      if (!Config.randomEventsPrintChanges ||
          randomEvents == null)
         return;

      // prepare to generate descriptions
      Ware changedWare      = null; // holds current change's affected ware
      int  changedWareIndex = 0;    // tracks which ware is currently being parsed
      int  size             = 0;    // holds how many wares should be parsed

      // prepare a buffer for each change order of magnitude
      // to eliminate the needs for ware changes to be sorted
      StringBuilder descriptionPosLarge  = new StringBuilder(PlatformStrings.PREFIX_POS_LARGE);
      StringBuilder descriptionPosMedium = new StringBuilder(PlatformStrings.PREFIX_POS_MEDIUM);
      StringBuilder descriptionPosSmall  = new StringBuilder(PlatformStrings.PREFIX_POS_SMALL);
      StringBuilder descriptionNegLarge  = new StringBuilder(PlatformStrings.PREFIX_NEG_LARGE);
      StringBuilder descriptionNegMedium = new StringBuilder(PlatformStrings.PREFIX_NEG_MEDIUM);
      StringBuilder descriptionNegSmall  = new StringBuilder(PlatformStrings.PREFIX_NEG_SMALL);

      // generate descriptions
      for (RandomEvent randomEvent : randomEvents) {
         // use changes' magnitudes to
         // determine how to format descriptions
         for (changedWareIndex = 0; changedWareIndex < randomEvent.changedWares.length; changedWareIndex++) {
            // grab reference to changed ware
            changedWare = randomEvent.changedWares[changedWareIndex];

            // add to buffer corresponding to change's magnitude
            switch(randomEvent.changeMagnitudesCurrent[changedWareIndex]) {
               case POSITIVE_LARGE:
                  if (changedWare.getAlias() != null)
                     descriptionPosLarge.append(changedWare.getAlias()).append(", ");
                  else
                     descriptionPosLarge.append(changedWare.getWareID()).append(", ");
                  break;

               case POSITIVE_MEDIUM:
                  if (changedWare.getAlias() != null)
                     descriptionPosMedium.append(changedWare.getAlias()).append(", ");
                  else
                     descriptionPosMedium.append(changedWare.getWareID()).append(", ");
                  break;

               case POSITIVE_SMALL:
                  if (changedWare.getAlias() != null)
                     descriptionPosSmall.append(changedWare.getAlias()).append(", ");
                  else
                     descriptionPosSmall.append(changedWare.getWareID()).append(", ");
                  break;

               case NEGATIVE_LARGE:
                  if (changedWare.getAlias() != null)
                     descriptionNegLarge.append(changedWare.getAlias()).append(", ");
                  else
                     descriptionNegLarge.append(changedWare.getWareID()).append(", ");
                  break;

               case NEGATIVE_MEDIUM:
                  if (changedWare.getAlias() != null)
                     descriptionNegMedium.append(changedWare.getAlias()).append(", ");
                  else
                     descriptionNegMedium.append(changedWare.getWareID()).append(", ");
                  break;

               case NEGATIVE_SMALL:
                  if (changedWare.getAlias() != null)
                     descriptionNegSmall.append(changedWare.getAlias()).append(", ");
                  else
                     descriptionNegSmall.append(changedWare.getWareID()).append(", ");
                  break;

               default:
                  // validation function should catch and handle invalid magnitudes
                  break;
            }
         }

         // allocate memory for overall description
         if (randomEvent.descriptionChangedWares == null) {
            randomEvent.descriptionChangedWares = new StringBuilder(
               descriptionPosLarge.length()  + descriptionPosMedium.length() +
               descriptionPosSmall.length()  + descriptionNegLarge.length()  +
               descriptionNegMedium.length() + descriptionNegSmall.length());
         } else {
            randomEvent.descriptionChangedWares.setLength(0); // clear contents
            randomEvent.descriptionChangedWares.ensureCapacity(
               descriptionPosLarge.length()  + descriptionPosMedium.length() +
               descriptionPosSmall.length()  + descriptionNegLarge.length()  +
               descriptionNegMedium.length() + descriptionNegSmall.length());
         }

         // generate overall description by combining buffers
         // add each buffer if it has any entries
         // also, remove trailing comma and space
         if (descriptionPosLarge.length() != PlatformStrings.PREFIX_POS_LARGE.length()) {
            descriptionPosLarge.setLength(descriptionPosLarge.length() - 2); // remove trailing comma and space
            randomEvent.descriptionChangedWares.append(descriptionPosLarge).append(PlatformStrings.POSTFIX);
            descriptionPosLarge.setLength(PlatformStrings.PREFIX_POS_LARGE.length()); // clear buffer
         }
         if (descriptionPosMedium.length() != PlatformStrings.PREFIX_POS_MEDIUM.length()) {
            descriptionPosMedium.setLength(descriptionPosMedium.length() - 2);
            randomEvent.descriptionChangedWares.append(descriptionPosMedium).append(PlatformStrings.POSTFIX);
            descriptionPosMedium.setLength(PlatformStrings.PREFIX_POS_MEDIUM.length()); // clear buffer
         }
         if (descriptionPosSmall.length() != PlatformStrings.PREFIX_POS_SMALL.length()) {
            descriptionPosSmall.setLength(descriptionPosSmall.length() - 2);
            randomEvent.descriptionChangedWares.append(descriptionPosSmall).append(PlatformStrings.POSTFIX);
            descriptionPosSmall.setLength(PlatformStrings.PREFIX_POS_SMALL.length()); // clear buffer
         }
         if (descriptionNegLarge.length() != PlatformStrings.PREFIX_NEG_LARGE.length()) {
            descriptionNegLarge.setLength(descriptionNegLarge.length() - 2);
            randomEvent.descriptionChangedWares.append(descriptionNegLarge).append(PlatformStrings.POSTFIX);
            descriptionNegLarge.setLength(PlatformStrings.PREFIX_NEG_LARGE.length()); // clear buffer
         }
         if (descriptionNegMedium.length() != PlatformStrings.PREFIX_NEG_MEDIUM.length()) {
            descriptionNegMedium.setLength(descriptionNegMedium.length() - 2);
            randomEvent.descriptionChangedWares.append(descriptionNegMedium).append(PlatformStrings.POSTFIX);
            descriptionNegMedium.setLength(PlatformStrings.PREFIX_NEG_MEDIUM.length()); // clear buffer
         }
         if (descriptionNegSmall.length() != PlatformStrings.PREFIX_NEG_SMALL.length()) {
            descriptionNegSmall.setLength(descriptionNegSmall.length() - 2);
            randomEvent.descriptionChangedWares.append(descriptionNegSmall).append(PlatformStrings.POSTFIX);
            descriptionNegSmall.setLength(PlatformStrings.PREFIX_NEG_SMALL.length()); // clear buffer
         }

         // reduce size to as much as is needed
         randomEvent.descriptionChangedWares.trimToSize();
      }
   }

   // INSTANCE METHODS
   /**
    * Constructor: Initializes random events.
    */
   public RandomEvents() { }

   /**
    * Calls on the appropriate function for
    * periodically triggering random events.
    */
   public void run() {
      // don't allow more than one singleton
      if (timerTaskRandomEvents != this) {
         cancel();
         return;
      }

      // don't run if random events aren't set up
      if (queue == null)
         return;

      // check the queue
      if (queue.size() > 0) {
         // check flag for stopping
         if (stop)
            return;

         // load random events from file
         if (queue.contains(QueueCommands.LOAD)) {
            queue.remove(QueueCommands.LOAD);
            loadPrivate();
            return;
         }

         // recalculate values for adjusting wares' quantities for sale
         if (queue.contains(QueueCommands.CALC_QUANTITY_CHANGES)) {
            queue.remove(QueueCommands.CALC_QUANTITY_CHANGES);
            calcQuantityChangesPrivate();
         }

         // reload ware references
         if (queue.contains(QueueCommands.LOAD_WARES)) {
            queue.remove(QueueCommands.LOAD_WARES);
            loadWaresPrivate();
         }

         // generate descriptions of events' effects
         if (queue.contains(QueueCommands.GEN_WARE_DESCS)) {
            queue.remove(QueueCommands.GEN_WARE_DESCS);
            generateWareChangeDescriptionsPrivate();
         }
      }

      // check flag for stopping
      if (stop)
         return;

      // check whether there are any random events
      if (randomEvents == null)
         return;

      // randomly select an event and make it happen
      randomEvents[ThreadLocalRandom.current().nextInt(randomEvents.length)].fire();

      // determine how long to wait until the next event
      if (Config.randomEventsVariance != 0.0f) {
         // randomize wait time based on configured variance
         long additionalWaitTime = ThreadLocalRandom.current().nextLong(
            (long) (Config.randomEventsFrequency * (1.0f - Config.randomEventsVariance)),
            (long) (Config.randomEventsFrequency * (1.0f + Config.randomEventsVariance)));

         // wait a random amount of time before the next event
         try {
            Thread.sleep(additionalWaitTime);
         } catch (Exception e) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENTS_SLEEP + e);
         }
      }
   }

   /**
    * A possible event affecting wares' quantities for sale.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2022-06-11
    */
   private class RandomEvent
   {
      // INSTANCE VARIABLES
      /**
       * What to print when the event occurs.
       * This variable is labeled "description"
       * instead of "descriptionScenario" to
       * ease understanding events' JSON files.
       */
      String description = "";
      /** announcement for which wares were affected */
      transient StringBuilder descriptionChangedWares = null;
      /** which ids of wares are affected */
      String[] changedWaresIDs = null;
      /** which wares are affected */
      transient Ware[] changedWares = null;
      /** how much each ware's quantity available for sale is affected */
      QuantityForSaleChangeMagnitudes[] changeMagnitudes = null;
      /** How much each ware is affected; corresponds to changedWares and
        * allows loading in new wares without loading magnitudes from file. */
      transient QuantityForSaleChangeMagnitudes[] changeMagnitudesCurrent = null;

      // INSTANCE METHODS
      /**
       * Creates a random event for GSON.
       * <p>
       * Complexity: O(1)
       */
      public RandomEvent() { }

      /**
       * Prints the event's description and adjusts wares' quantities for sale.
       * <p>
       * Complexity: O(n^2), where n is characters in the event's description
       */
      public void fire() {
         int  size = changedWares.length; // how many wares should be affected
         int  quanChange;                 // how much to alter the ware's quantity available for sale
         int  quantityDistFromFloor = 0;  // how much quantity may be sold before reaching the price floor
         Ware ware;                       // current ware being affected

         // change wares' quantities for sale
         Marketplace.acquireMutex(); // check if another thread is adjusting wares' properties
         for (int i = 0; i < size; i++) {
            ware = changedWares[i];

            // check whether stock should not be sold past the price floor
            if (Config.noGarbageDisposing) {
               // find out if the ware can be sold
               if (Marketplace.hasReachedPriceFloor(ware))
                  continue; // if nothing may be sold, skip this ware

               // find how much may be sold
               if (ware instanceof WareLinked)
                  quantityDistFromFloor = ((WareLinked) ware).getQuanWhenReachedPriceFloor() - ware.getQuantity();
               else
                  quantityDistFromFloor = Marketplace.getQuantityUntilPrice(ware, Marketplace.getPrice(null, ware, 1, false, Marketplace.PriceType.FLOOR_SELL) + 0.0001f, false) + 1;

               // if nothing may be sold, skip this ware
               if (quantityDistFromFloor <= 0)
                  continue;
            }

            switch (changeMagnitudesCurrent[i]) {
               case POSITIVE_LARGE:
                  quanChange =  quanChangeLarge[ware.getLevel()];
                  break;

               case POSITIVE_MEDIUM:
                  quanChange =  quanChangeMedium[ware.getLevel()];
                  break;

               case POSITIVE_SMALL:
                  quanChange =  quanChangeSmall[ware.getLevel()];
                  break;

               case NEGATIVE_LARGE:
                  quanChange = -quanChangeLarge[ware.getLevel()];
                  break;

               case NEGATIVE_MEDIUM:
                  quanChange = -quanChangeMedium[ware.getLevel()];
                  break;

               case NEGATIVE_SMALL:
                  quanChange = -quanChangeSmall[ware.getLevel()];
                  break;

               default:           // this line should never be reached
                  quanChange = 0; // don't make any changes if it is reached
            }

            // if necessary, ensure selling does not exceed the ware's quantity ceiling
            if (Config.noGarbageDisposing &&                    // if quantity ceilings are enforced,
                quanChange > 0            &&                    // the event is adding quantity,
                quantityDistFromFloor < quanChange)             // and that quantity is too much,
               quanChange = quantityDistFromFloor;              // reduce quantity to an acceptable value

            // increase or reduce the ware's quantity available for sale
            ware.addQuantity(quanChange);
         }

         // allow other threads to adjust wares' properties
         Marketplace.releaseMutex();

         // print scenario description
         Config.commandInterface.printToAllUsers(description);

         // print effects on wares
         if (Config.randomEventsPrintChanges && descriptionChangedWares != null)
            Config.commandInterface.printToAllUsers(descriptionChangedWares.toString());
      }
   };
};