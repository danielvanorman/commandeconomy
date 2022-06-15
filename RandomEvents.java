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
   /** how much to adjust when an event greatly affects a ware */
   private static int[] quanChangeLarge;
   /** how much to adjust when an event moderately affects a ware */
   private static int[] quanChangeMedium;
   /** how much to adjust when an event slightly affects a ware */
   private static int[] quanChangeSmall;
   /** events which might occur */
   private static RandomEvent[] randomEvents;

   /** manages thread triggering random events */
   private static Timer timerRandomEvents;
   /** triggers random events */
   private static RandomEvents timerTaskRandomEvents;
   /** used to monitor a timer interval for configuration changes */
   private static long oldFrequency = 0L;
   /** used to signal what should be reloaded or recalculated */
   enum QueueCommands {
      LOAD,
      CALC_TRADE_QUANTITIES,
      RELOAD_WARES
   }
   /** used to signal thread to reload or recalculate variables */
   private static ArrayBlockingQueue<QueueCommands> queue = null;

   /** color code for positive, large changes in ware's quantities */
   private static final String PREFIX_POS_LARGE  = "\u001b[1m\u001b[32m+++";
   /** color code for positive, medium changes in ware's quantities */
   private static final String PREFIX_POS_MEDIUM = "\u001b[32m++";
   /** color code for positive, small changes in ware's quantities */
   private static final String PREFIX_POS_SMALL  = "\u001b[32;1m+";
   /** color code for negative, large changes in ware's quantities */
   private static final String PREFIX_NEG_LARGE  = "\u001b[1m\u001b[31m---";
   /** color code for negative, medium changes in ware's quantities */
   private static final String PREFIX_NEG_MEDIUM = "\u001b[31m--";
   /** color code for negative, small changes in ware's quantities */
   private static final String PREFIX_NEG_SMALL  = "\u001b[31;1m-";
   /** code for resetting color codes */
   private static final String POSTFIX           = "\u001b[0m\n";

   // INSTANCE VARIABLES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;

   /** what to print when the event occurs */
   String description = "";
   /** announcement for which wares were affected */
   transient String changedWaresDescriptions = "";
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

   // STATIC METHODS
   /**
    * Prepares for using random events.
    * <p>
    * Complexity: O(n), where n is events to load
    */
   public static void loadRandomEvents() { return; }

   /**
    * Recalculates events' effects on quantities available for sale
    * according to configuration settings.
    * <p>
    * Complexity: O(1)
    */
   public static void calcQuantityChanges() { return; }

   /**
    * Relinks random events to wares they affect.
    * Necessary if wares are reloaded.
    * <p>
    * Complexity: O(n*m)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   public static void reloadWares() { return; }

   /**
    * Generates descriptions for affected wares if they haven't been generated already.
    * Necessary if printing wares' changes was initially disabled,
    * then was enabled by reloading configuration.
    * <p>
    * Complexity: O(n*m) or O(1)<br>
    * where n is the number of random events<br>
    * where m is the number of affected wares
    */
   public static void generateWareChangeDescriptions() { return; }

   /**
    * Randomly selects an event and makes it happen.
    * <p>
    * Complexity: O(1)
    */
   public static void fireEvent() { return; }

   /**
    * Makes random events start occurring.
    * <p>
    * Complexity: O(1)
    */
   public static void enableRandomEvents() { return; }

   /**
    * Makes random events stop occurring.
    * <p>
    * Complexity: O(1)
    */
   public static void disableRandomEvents() { return; }

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
         return;
      }

      // validate random events

      // check whether any events were loaded
      if (randomEvents.length <= 0) {
         randomEvents = null; // disable random events
         Config.commandInterface.printToConsole(CommandEconomy.WARN_RANDOM_EVENTS_NONE_LOADED);
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
      return;
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

      return;
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

      return;
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
         }
      }

      // check flag for stopping
      if (stop)
         return;

      // check whether there are any random events
      if (randomEvents == null)
         return;

      return;
   }

   /**
    * A possible event affecting wares' quantities for sale.
    *
    * @author  Daniel Van Orman
    * @version %I%, %G%
    * @since   2022-06-11
    */
   protected class RandomEvent
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
       */
      private void load() { return; }

      /**
       * Prints the event's description and adjusts wares' quantities for sale.
       * <p>
       * Complexity: O(n^2), where n is characters in the event's description
       */
      private void fire() { return; }

      /**
       * Relinks affected wares with the marketplace's wares.
       * <p>
       * Complexity: O(n), where n is wares affected by the event
       */
      private void reloadWares() { return; }
   };
};