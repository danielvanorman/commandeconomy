package commandeconomy;

import com.google.gson.Gson;                    // for loading
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;
import java.io.File;                            // for handling files
import java.io.FileReader;
import com.google.gson.JsonSyntaxException;     // for more specific error messages when parsing files
import java.util.Timer;                         // for triggering AI trades periodically and on a separate thread
import java.util.TimerTask;                     // for disabling AI mid-execution
import java.util.Collections;                   // for communication between the main thread and the thread handling AI
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;  // for randomizing trade frequency and decisions
import java.util.HashMap;                       // for storing AI professions and trade decisions
import java.util.Map;                           // for iterating through hashmaps
import java.util.HashSet;                      // for storing AI before their activation

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
   static long oldFrequency = 0L;
   /** used to monitor changes in a trade quantity percentage for configuration changes */
   static float oldTradeQuantityPercent = 0.0f;
   /** used to signal what should be reloaded or recalculated */
   enum QueueCommands {
      LOAD,
      CALC_TRADE_QUANTITIES,
      RELOAD_WARES
   }
   /** used to signal thread to reload or recalculate variables */
   static Set<QueueCommands> queue = null;

   // INSTANCE ATTRIBUTES
   /** whether the task should continue running */
   public transient volatile boolean stop = false;
   /** unfinalized AI trade decisions; used to condense marketplace changes into one critical section */
   private transient HashMap<Ware, Integer> tradesPending = null;

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
   public static void startOrReconfig() {
      // calculate frequency using configuration settings
      long newFrequency = ((long) Config.aiTradeFrequency) * 60000L; // 60000 ms per min.
      // enforce a positive floor
      if (newFrequency <= 0.0)
         newFrequency = 60000L; // 60000 ms per min.

      // if necessary, start, reload, or stop AI
      if (Config.enableAI && Config.aiTradeFrequency > 0) {
         // set up AI if they haven't been already
         if (queue == null) {
            // set up queue
            queue = Collections.synchronizedSet(EnumSet.noneOf(QueueCommands.class));

            // leave requests to set up AI
            queue.add(QueueCommands.LOAD);
            queue.add(QueueCommands.CALC_TRADE_QUANTITIES);
         }

         // if necessary, recalculate trade quantities
         if (oldTradeQuantityPercent != Config.aiTradeQuantityPercent)
            queue.add(QueueCommands.CALC_TRADE_QUANTITIES);

         // start AI
         if (timerAITrades == null ) {
            // initialize timer objects
            timerAITrades     = new Timer(true);
            timerTaskAITrades = new AIHandler();

            // initialize AI
            timerAITrades.scheduleAtFixedRate(timerTaskAITrades, (long) 0, newFrequency);
         }

         // reload AI trading frequency
         else {
            // reload AI professions file
            queue.add(QueueCommands.LOAD);

            if (oldFrequency != newFrequency) {
               // There's no way to change a task's period.
               // Therefore, it is necessary to stop the current task
               // and schedule a new one.
               timerTaskAITrades.stop = true;
               timerTaskAITrades.cancel();

               // initialize timertask object
               timerTaskAITrades = new AIHandler();

               // initialize AI
               timerAITrades.scheduleAtFixedRate(timerTaskAITrades, newFrequency, newFrequency);
            }
         }
      }

      // stop AI
      else if (timerAITrades != null && (!Config.enableAI || Config.aiTradeFrequency <= 0))
         endAI();

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
      if (timerAITrades != null) {
         timerTaskAITrades.stop = true;
         timerTaskAITrades = null;
         timerAITrades.cancel();
         timerAITrades = null;

         // deallocate memory
         professions = null;
         activeAI    = null;
         queue       = null;
      }
   }

   /**
    * Prepares for using AI.
    * <p>
    * Complexity: O(n), where n is the number of AI professions to load
    */
   private static void loadPrivate() {
      // if the marketplace is uninitialized,
      // there are no wares for AI to trade
      if (Marketplace.getAllWares().size() == 0)
         return;

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
            System.lineSeparator() + "To use AI, replace " + Config.filenameAIProfessions +
            "," + System.lineSeparator() + "then use the command \"reload config\"."
         );
         return;
      }

      // prepare to read the JSON file
      Gson gson = new Gson();
      FileReader fileReader = null; // use a handle to ensure the file gets closed

      // attempt to read file
      try {
         fileReader = new FileReader(fileAIProfessions);
         Type typeProfessions = new TypeToken<HashMap<String, AI>>(){}.getType(); // use TypeToken since the profession object may not have been initialized yet
         professions = gson.fromJson(fileReader, typeProfessions);
         fileReader.close();
      }
      catch (JsonSyntaxException e) {
         professions = null; // disable AI
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_AI_MISFORMAT + Config.filenameAIProfessions);
      }
      catch (Exception e) {
         professions = null; // disable AI
         Config.commandInterface.printToConsole(CommandEconomy.ERROR_AI_PARSING + Config.filenameAIProfessions);
         e.printStackTrace();
      }

      // check whether any AI professions were loaded
      if (professions == null || professions.size() <= 0) {
         professions = null; // disable AI

         // ensure the file is closed
         try {
            if (fileReader != null)
               fileReader.close();
         } catch (Exception e) { }

         Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_NONE_LOADED);
         return;
      }

      // validate AI professions
      String professionName;
      AI     professionAI;
      for (Map.Entry<String, AI> entry : professions.entrySet()) {
         professionName = entry.getKey();
         professionAI   = entry.getValue();

         // for paranoia's sake
         if (professionAI == null)
            continue;

         // try to load the AI profession
         professionAI.load(professionName); // if loading fails, an error message has already been printed
      }

      // check whether any AI professions are valid
      if (professions.size() <= 0) {
         professions = null; // disable AI
         Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_INVALID);
         return;
      }

      // activate AI
      HashSet<AI> aiToActivate = null;
      if (Config.activeAI != null) {
         aiToActivate = new HashSet<AI>(); // store AI to be activated to handle repeats
         AI ai; // AI currently being processed

         for (String aiProfession : Config.activeAI) {
            // try to grab the AI profession
            ai = professions.get(aiProfession);

            // if the AI profession exists,
            // add it to active AI or increment its trade decisions
            if (ai != null) {
               // if the AI is a repeat, increment its trade decisions
               if (aiToActivate.contains(aiProfession))
                  ai.incrementDecisionsPerTradeEvent();
               // otherwise, set it to be activated
               else
                  aiToActivate.add(ai);
            }

            // if the AI profession doesn't exist,
            // warn the server
            else
               Config.commandInterface.printToConsole(CommandEconomy.ERROR_AI_MISSING + aiProfession);
         }

         // if no AI were found
         if (aiToActivate.size() == 0) {
            Config.commandInterface.printToConsole(CommandEconomy.WARN_AI_NONE_LOADED);
            endAI();
         }

         // create active AI array
         else {
            // initialize array
            activeAI = new AI[aiToActivate.size()];

            // fill array
            aiToActivate.toArray(activeAI);
         }
      }

      // if no AI should be run
      else
         endAI();
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
      String professionName;
      AI     professionAI;
      for (Map.Entry<String, AI> entry : professions.entrySet()) {
         professionName = entry.getKey();
         professionAI   = entry.getValue();

         // for paranoia's sake
         if (professionAI == null)
            continue;

         // tell the AI to relink its wares
         professionAI.reload(professionName);
      }
   }

   // INSTANCE METHODS
   /**
    * Constructor: Initializes the AI handler.
    */
   public AIHandler() { }

   /**
    * Calls on the appropriate function for
    * handling AI trade events.
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
      if (queue.size() > 0) {
         // check flag for stopping
         if (stop)
            return;

         // load AI
         if (queue.contains(QueueCommands.LOAD)) {
            queue.remove(QueueCommands.LOAD);
            loadPrivate();
            return;
         }

         // recalc quantities
         if (queue.contains(QueueCommands.CALC_TRADE_QUANTITIES) &&
             oldTradeQuantityPercent != Config.aiTradeQuantityPercent) {
            queue.remove(QueueCommands.CALC_TRADE_QUANTITIES);
            oldTradeQuantityPercent = Config.aiTradeQuantityPercent; // record to monitor for changes
            AI.calcTradeQuantities();
         }

         // reload wares
         if (queue.contains(QueueCommands.RELOAD_WARES)) {
            queue.remove(QueueCommands.RELOAD_WARES);
            reloadWaresPrivate();
         }
      }

      // check flag for stopping
      if (stop)
         return;

      // check whether there are any AI running
      if (activeAI == null)
         return;

      // prepare place to store AI trade decisions
      // so all decisions may be processed within a single critical section
      if (tradesPending == null)
         tradesPending = new HashMap<Ware, Integer>();

      // initiate AI trades
      for (AI ai : activeAI)
         ai.trade(tradesPending);

      // finalize trades
      AI.finalizeTrades(tradesPending);
   }
};