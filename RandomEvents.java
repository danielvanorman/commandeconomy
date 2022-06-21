package commandeconomy;

import com.google.gson.Gson;                    // for saving and loading
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;                            // for handling files
import java.io.FileReader;
import com.google.gson.JsonSyntaxException;     // for more specific error messages when parsing files
import java.util.Timer;                         // for triggering events periodically and on a separate thread
import java.util.TimerTask;                     // for disabling random events mid-execution
import java.util.concurrent.ArrayBlockingQueue; // for communication between the main thread and the thread handling random events
import java.lang.StringBuilder;                 // for generating ware change descriptions
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
   /** how much to adjust when an event greatly affects a ware */
   static int[] quanChangeLarge  = null;
   /** how much to adjust when an event moderately affects a ware */
   static int[] quanChangeMedium = null;
   /** how much to adjust when an event slightly affects a ware */
   static int[] quanChangeSmall  = null;
   /** events which might occur */
   static RandomEvent[] randomEvents = null;

   // thread management
   /** manages thread triggering random events */
   static Timer timerRandomEvents = null;
   /** handles random events */
   static RandomEvents timerTaskRandomEvents = null;
   /** used to monitor a timer interval for configuration changes */
   static long oldFrequency = 0L;
   /** used to monitor changes in quantity change percentages for configuration changes */
   static float[] oldQuantityChangePercents =null;
   /** used to signal what should be reloaded or recalculated */
   enum QueueCommands {
      LOAD,
      CALC_TRADE_QUANTITIES,
      RELOAD_WARES,
      GEN_WARE_DESCS
   }
   /** used to signal thread to reload or recalculate variables */
   static ArrayBlockingQueue<QueueCommands> queue = null;

   // INSTANCE VARIABLES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;

   // STATIC METHODS
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

      queue.add(QueueCommands.CALC_TRADE_QUANTITIES);
   }

   /**
    * Relinks random events to wares they affect.
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
   public static void reloadWares() {
      if (queue == null)
         return;

      queue.add(QueueCommands.RELOAD_WARES);
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
    * Spawns and handles a thread for handling random events.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfigRandomEvents() {
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
            queue = new ArrayBlockingQueue<QueueCommands>(15);

            // leave requests to set up random events
            queue.add(QueueCommands.LOAD);
            queue.add(QueueCommands.CALC_TRADE_QUANTITIES);

            // exit to delay setting up until the marketplace is set up
            return;
         }

         // if necessary, recalculate change amounts for quantities
         // float[] oldQuantityChangePercents

         // start random events
         if (timerRandomEvents == null ) {
            // initialize timer objects
            timerRandomEvents     = new Timer(true);
            timerTaskRandomEvents = new RandomEvents();

            // initialize random events
            timerRandomEvents.scheduleAtFixedRate(timerTaskRandomEvents, (long) 0, newFrequency);
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
      else if (timerRandomEvents != null && (!Config.randomEvents || Config.randomEventsFrequency <= 0))
         endRandomEvents();

      // record timer interval to monitor for changes
      oldFrequency = newFrequency;
   }

   /**
    * Closes the thread handling random events.
    * <p>
    * Complexity: O(1)
    */
   public static void endRandomEvents() {
      // if necessary, stop random events
      if (timerRandomEvents != null) {
         timerTaskRandomEvents.stop = true;
         timerTaskRandomEvents = null;
         timerRandomEvents.cancel();
         timerRandomEvents = null;
      }
   }

   /**
    * Prepares for using random events.
    * <p>
    * Complexity: O(n), where n is events to load
    */
   private static void loadRandomEventsPrivate() {
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
         // To-Do: Read the file to fill randomEvents
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
         endRandomEvents();
         return;
      }

      // validate random events
         // if the random event fails to load,
         // remove the entry

      // check whether any events were loaded
      if (randomEvents.length <= 0) {
         randomEvents = null; // disable random events
         Config.commandInterface.printToConsole(CommandEconomy.WARN_RANDOM_EVENTS_NONE_LOADED);
         endRandomEvents();
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

      // calculate change amounts
   }

   /**
    * Relinks random events to wares they affect.
    * Necessary if wares are reloaded.
    * <p>
    * Complexity: O(n*m)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   private static void reloadWaresPrivate() {
      if (randomEvents == null)
         return;

      // reload wares for each random event
         // if an error is found, don't use that random event
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

      // generate descriptions
      for (RandomEvent randomEvent : randomEvents) {
         randomEvent.generateWareChangeDescriptions();
      }
   }

   // INSTANCE METHODS
   /**
    * Constructor: Initializes random events.
    */
   public RandomEvents() { }

   /**
    * Calls on the appropriate function for
    * periodically saving the marketplace.
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
      while (queue.size() > 0) {
         // check flag for stopping
         if (stop)
            return;

         // grab request
         switch (queue.poll()) { // take from head of queue, in order of requests made
            // load random events
            case LOAD:
               loadRandomEventsPrivate();
               return;

            // reload wares
            case CALC_TRADE_QUANTITIES:
               reloadWaresPrivate();
               break;

            // recalc quantities
            case RELOAD_WARES:
               calcQuantityChangesPrivate();
               break;

            // generate ware descriptions
            case GEN_WARE_DESCS:
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

      // determine how long to wait until the next event
      if (Config.randomEventsVariance != 0.0f) {
         // randomize wait time based on configured variance

         // wait a random amount of time before the next event
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
      // STATIC VARIABLES
      // +++ --> Dark Green
      //  ++ --> Green
      //   + --> Light Green or Italicized Green
      // --- --> Dark Red
      //  -- --> Red
      //   - --> Light Red or Italicized Red

      // Terminal Interface
      /** color code for positive, large changes in ware's quantities */
      private static final String PREFIX_POS_LARGE  = "\033[1m\033[32m+++";
      /** color code for positive, medium changes in ware's quantities */
      private static final String PREFIX_POS_MEDIUM = "\033[32m++";
      /** color code for positive, small changes in ware's quantities */
      private static final String PREFIX_POS_SMALL  = "\033[32;1m+";
      /** color code for negative, large changes in ware's quantities */
      private static final String PREFIX_NEG_LARGE  = "\033[1m\033[31m---";
      /** color code for negative, medium changes in ware's quantities */
      private static final String PREFIX_NEG_MEDIUM = "\033[31m--";
      /** color code for negative, small changes in ware's quantities */
      private static final String PREFIX_NEG_SMALL  = "\033[31;1m-";
      /** code for resetting color codes */
      private static final String POSTFIX           = "\033[0m\n";

      // Minecraft Interface
      /** color code for positive, large changes in ware's quantities */
      //private static final String PREFIX_POS_LARGE  = "§2+++";
      /** color code for positive, medium changes in ware's quantities */
      //private static final String PREFIX_POS_MEDIUM = "§a++";
      /** color code for positive, small changes in ware's quantities */
      //private static final String PREFIX_POS_SMALL  = "§a§o+";
      /** color code for negative, large changes in ware's quantities */
      //private static final String PREFIX_NEG_LARGE  = "§4---";
      /** color code for negative, medium changes in ware's quantities */
      //private static final String PREFIX_NEG_MEDIUM = "§c--";
      /** color code for negative, small changes in ware's quantities */
      //private static final String PREFIX_NEG_SMALL  = "§c§o-";
      /** code for resetting color codes */
      //private static final String POSTFIX           = "§r\n";

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
      /**
       * How much each ware is affected.
       * Valid entries are:<br>
       * +/-3: Positive/negative large change<br>
       * +/-2: Positive/negative medium change<br>
       * +/-1: Positive/negative small change
       */
      int[] changeMagnitudes = null;
      /** How much each ware is affected; corresponds to changedWares and
        * allows loading in new wares without loading magnitudes from file. */
      transient int[] changeMagnitudesCurrent = null;

      // INSTANCE METHODS
      /**
       * Creates a random event for GSON.
       * <p>
       * Complexity: O(1)
       */
      public RandomEvent() { }

      /**
       * Prepares and checks the event's data, correcting errors where possible.
       * <p>
       * Complexity: O(n), where n is wares affected by the event
       * @return <code>true</code> if an error was detected and loading failed
       */
      public boolean load() {
         // validate description
         if (description == null || description.isEmpty()) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_DESC_MISSING);
            return true;
         }

         // validate effects
         // check whether magnitudes were loaded
         if (changeMagnitudes == null) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_MAGNITUDES_MISSING + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
         }

         // check whether magnitudes entries were loaded
         int sizeChangeMagnitudes = changeMagnitudes.length;
         if (sizeChangeMagnitudes == 0) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_MAGNITUDES_BLANK + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
         }

         // check whether magnitude entries are valid
         for (int i = 0; i < sizeChangeMagnitudes; i++) {
            if (changeMagnitudes[i] < -3 || changeMagnitudes[i] == 0 || changeMagnitudes[i] > 3) {
               Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_MAGNITUDES_INVALID + changeMagnitudes[i] + CommandEconomy.MSG_RANDOM_EVENTS_CHANGES + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
               return true;
            }
         }

         // validate ware IDs
         // check whether ware IDs were loaded
         if (changedWaresIDs == null) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_WARES_MISSING + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
         }

         // check whether ware ID entries were loaded
         int sizeChangedWaresIDs = changedWaresIDs.length;
         if (sizeChangedWaresIDs == 0) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_WARES_BLANK + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
         }

         // check whether the is a magnitude for every ware ID
         if (sizeChangedWaresIDs != sizeChangeMagnitudes) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_CHANGES_MISMATCH + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
         }

         // load wares
         return reloadWares();
      }

      /**
       * Prints the event's description and adjusts wares' quantities for sale.
       * <p>
       * Complexity: O(n^2), where n is characters in the event's description
       */
      public void fire() {
         // change wares' quantities for sale

         // print scenario description
         // Config.commandInterface.printToAllUsers(description);

         // print effects on wares
      }

      /**
       * Relinks affected wares with the marketplace's wares.
       * <p>
       * Complexity: O(n), where n is wares affected by the event
       * @return <code>true</code> if no wares were loaded
       */
      public boolean reloadWares() {
         // if wares cannot be loaded, flag an error
         if (changedWaresIDs == null || changedWaresIDs.length == 0) {
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_CHANGES_MISSING + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
         }

         // try to grab each ware
            // ware = Marketplace.translateAndGrab(changedWaresIDs[i]);

            // if the ware is not fine, record it
            /*
               // if enabled, print which scenario is using an invalid ware ID
               if (Config.randomEventsReportInvalidWares && !foundInvalidEntry)
                  System.err.print(CommandEconomy.ERROR_RANDOM_EVENT_WARES_INVALID +
                                   CommandEconomy.MSG_RANDOM_EVENT_DESC + description + CommandEconomy.ERROR_RANDOM_EVENT_WARES_INVALID_LIST);

               // if enabled, report invalid IDs
               if (Config.randomEventsReportInvalidWares)
                  System.err.print(changedWaresIDs[i] + ", ");
            */

         // check whether any wares were loaded
         /*
            Config.commandInterface.printToConsole(CommandEconomy.ERROR_RANDOM_EVENT_WARES_NO_VALID + CommandEconomy.MSG_RANDOM_EVENT_DESC + description);
            return true;
            */

         // report that no errors were found
         return false;
      }

      /**
       * Generates descriptions stating which wares are affected by events.
       * <p>
       * Complexity: O(n),where n is the number of affected wares
       */
      public void generateWareChangeDescriptions() {
         // if wares cannot be parsed, there is nothing to do
         if (changedWares == null || changedWares.length == 0 ||
             changeMagnitudesCurrent == null || changeMagnitudesCurrent.length == 0 ||
             changedWares.length != changeMagnitudesCurrent.length)
            return;

         // prepare a buffer for each change order of magnitude
         // eliminates the needs for ware changes to be sorted
         StringBuilder descriptionPosLarge  = new StringBuilder(PREFIX_POS_LARGE);
         StringBuilder descriptionPosMedium = new StringBuilder(PREFIX_POS_MEDIUM);
         StringBuilder descriptionPosSmall  = new StringBuilder(PREFIX_POS_SMALL);
         StringBuilder descriptionNegLarge  = new StringBuilder(PREFIX_NEG_LARGE);
         StringBuilder descriptionNegMedium = new StringBuilder(PREFIX_NEG_MEDIUM);
         StringBuilder descriptionNegSmall  = new StringBuilder(PREFIX_NEG_SMALL);

         // use changes' magnitudes to
         // determine how to format descriptions
            // add to buffer corresponding to change's magnitude

         // generate overall description by combining buffers
         // add each buffer if it has any entries
         // also, remove trailing comma and space

         // reduce size to as much as is needed
      }
   };
};