package commandeconomy;

import com.google.gson.Gson;                    // for saving and loading
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;                            // for handling files
import java.io.FileReader;
import com.google.gson.JsonSyntaxException;     // for more specific error messages when parsing files
import java.util.Timer;                         // for triggering AI trades periodically and on a separate thread
import java.util.TimerTask;                     // for disabling AI mid-execution
import java.util.concurrent.ArrayBlockingQueue; // for communication between the main thread and the thread handling AI
import java.util.concurrent.ThreadLocalRandom;  // for randomizing trade frequency and decisions
import java.util.HashMap;                       // for storing AI professions
import java.util.Map;                           // for iterating through hashmaps

/**
 * Creating an instance of this class initializes
 * a singleton handler for AI.
 * <p>
 * This class is meant to handle AI using an independent thread.
 * Public methods leave requests on a non-blocking queue.
 * Requests are serviced just before the next AI trade is triggered.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-4-23
 */
@SuppressWarnings("deprecation") // unfortunately, Minecraft 1.12.2's gson requires a JSONParser object
public class AIHandler extends TimerTask {
   // STATIC ATTRIBUTES
   // AI management
   /** AI configured to mimic a particular occupation */
   static HashMap<String, AI> professions = null;
   /** AI who regularly engage in trade */
   static AI[] activeAI = null;

   // thread management
   /** manages thread triggering AI trading */
   static Timer timerAITrades = null;
   /** handles AI trading */
   static AIHandler timerTaskAITrades = null;
   /** used to monitor a timer interval for configuration changes */
   static long oldFrequency = (long) 0.0;
   /** used to signal what should be reloaded or recalculated */
   enum QueueCommands {
      LOAD,
      CALC_TRADE_QUANTITIES,
      RELOAD_WARES
   }
   /** used to signal thread to reload or recalculate variables */
   static ArrayBlockingQueue<QueueCommands> queue = null;

   // INSTANCE ATTRIBUTES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;

   // STATIC METHODS
   /**
    * Prepares for using AI.
    * <p>
    * This method sends a request to the thread handling AI
    * to perform a given task before starting the next AI trades.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(n), where n is the number of AI professions to load
    */
   public static void load() {
      if (queue == null)
         return;

      queue.add(QueueCommands.LOAD);
   }

   /**
    * Recalculates how much to buy or sell for each trade
    * according to configuration settings.
    * <p>
    * This method sends a request to the thread handling AI
    * to perform a given task before starting the next AI trades.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(1)
    */
   public static void calcTradeQuantities() {
      if (queue == null)
         return;

      queue.add(QueueCommands.CALC_TRADE_QUANTITIES);
   }

   /**
    * Relinks AI to the wares they affect.
    * This is necessary if wares are reloaded.
    * <p>
    * This method sends a request to the thread handling AI
    * to perform a given task before starting the next AI trades.
    * There is no guarantee for when the task will be completed.
    * <p>
    * Complexity: O(n*m)<br>
    * where n is the number of active AI<br>
    * where m is the number of affected wares
    */
   public static void reloadWares() {
      if (queue == null)
         return;

      queue.add(QueueCommands.RELOAD_WARES);
   }

   /**
    * Spawns and handles a thread for handling AI.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfigAI() {
      // calculate frequency using configuration settings
      long newFrequency = (long) (Config.aiTradeFrequency);
      // enforce a positive floor
      if (newFrequency <= 0.0)
         newFrequency = (long) 60000.0; // 60000 ms per min.

      // if necessary, start, reload, or stop AI
      if (Config.enableAI && Config.aiTradeFrequency > 0) {
         // set up AI if they haven't been already
            // set up queue
            // leave requests to set up AI

         // start AI

         // reload AI trading frequency
      }

      // stop AI

      // record timer interval to monitor for changes
      oldFrequency = newFrequency;
   }

   /**
    * Closes the thread handling AI.
    * <p>
    * Complexity: O(1)
    */
   public static void endAI() {
      // if necessary, stop AI thread
   }

   /**
    * Prepares for using AI.
    * <p>
    * Complexity: O(n), where n is the number of AI professions to load
    */
   private static void loadPrivate() {
      // try to load the professions file
      File fileAIProfessions = new File(Config.filenameAIProfessions);  // contains AI objects
      // if the local file isn't found, use the global file
      if (!Config.crossWorldMarketplace && !fileAIProfessions.isFile()) {
         fileAIProfessions = new File("config" + File.separator + "CommandEconomy" + File.separator + Config.filenameNoPathAIProfessions);
      }

      // check file existence
      if (!fileAIProfessions.isFile()) {
         // don't throw an exception, print a warning to advise user to reload AI
         Config.commandInterface.printToConsole(CommandEconomy.WARN_FILE_MISSING + Config.filenameAIProfessions +
            "\nTo use AI, replace " + Config.filenameAIProfessions +
            ",\nthen use the command \"reload config\"."
         );
         return;
      }

      // prepare to read the JSON file
      Gson gson = new Gson();
      FileReader fileReader = null; // use a handle to ensure the file gets closed

      // attempt to read file

      // check whether any AI professions were loaded

      // validate AI professions
      String professionName;
      AI     professionAI;
      for (Map.Entry<String, AI> entry : professions.entrySet()) {
         professionName = entry.getKey();
         professionAI   = entry.getValue();

         // for paranoia's sake
         if (professionAI == null)
            continue;

         // if the AI fails to load,
         // remove the entry
         if (professionAI.load())
            continue; // To-Do: Code goes here
            // an error message has already been printed
      }

      // check whether any AI professions are valid
      if (professions.size() <= 0) {
         professions = null; // disable random events
         Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID);
         return;
      }

      // activate AI
      for (String aiProfession : Config.activeAI) {
         // try to grab the AI profession
         // if the AI profession exists,
         // add it to active AI and increment its trade decisions
            // ai.incrementDecisionsPerTradeEvent();

         // if the AI profession doesn't exist,
         // warn the server
      }

      // create active AI array
   }

   /**
    * Relinks AI to wares they affect.
    * Necessary if wares are reloaded.
    * <p>
    * Complexity: O(n*m)<br>
    * where n is the number of active AI<br>
    * where m is the number of affected wares
    */
   private static void reloadWaresPrivate() {
      if (activeAI == null)
         return;

      // reload wares for each AI
   }

   // INSTANCE METHODS
   /**
    * Constructor: Initializes the AI handler.
    */
   public AIHandler() { }

   /**
    * Calls on the appropriate function for
    * periodically saving the marketplace.
    */
   public void run() {
      // don't allow more than one singleton
      if (timerTaskAITrades != this) {
         cancel();
         return;
      }

      // don't run if AI aren't set up
      if (queue == null)
         return;

      // check the queue
      while (queue.size() > 0) {
         // check flag for stopping
         if (stop)
            return;

         // grab request
         switch (queue.poll()) { // take from head of queue, in order of requests made
            // load AI
            case LOAD:
               loadPrivate();
               return;

            // recalc quantities
            case CALC_TRADE_QUANTITIES:
               AI.calcTradeQuantities();
               break;

            // reload wares
            case RELOAD_WARES:
               reloadWaresPrivate();
               break;
         }
      }

      // check flag for stopping
      if (stop)
         return;

      // check whether there are any AI running
      if (activeAI == null)
         return;

      // initiate AI trades
   }
};