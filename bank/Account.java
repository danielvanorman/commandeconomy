package commandeconomy;

import java.util.TreeMap;             // for storing accounts
import java.util.HashMap;             // for storing account properties
import java.util.Set;                 // for returning all account names
import java.util.ArrayDeque;          // for storing players with access to a specific account
import java.io.File;                  // for handling files
import java.util.Scanner;             // for parsing files
import java.io.FileWriter;            // for writing to files
import java.io.FileNotFoundException; // for handling missing file errors
import java.io.IOException;           // for handling miscellaneous file errors
import java.util.UUID;                // for more securely tracking users internally
import java.util.HashSet;             // for faster saving, by storing accounts changed since last save
import java.io.BufferedWriter;        // for faster saving, so fewer file writes are used
import java.util.Map;                 // for iterating through maps
import java.util.Collection;          // for returning all accounts usable within the marketplace

/**
 * Manages financial accounts usable within the market.
 * <p>
 * Accounts were implemented as objects (rather than structs or another data structure)
 * since doing so makes code more readable and maintainable.
 *
 * @author  Daniel Van Orman
 * @version %I%, %G%
 * @since   2021-03-31
 */
public final class Account {
   // GLOBAL VARIABLES
   // account information
   /** holds all accounts usable in the market */
   private static TreeMap<String, Account> accounts = new TreeMap<String, Account>();
   /** tracks how many accounts each user has created */
   private static Map<UUID, Integer> accountsCreatedPerUser = new HashMap<UUID, Integer>();
   /** maps players to accounts they specified should be used by default for their transactions */
   private static Map<UUID, Account> defaultAccounts = new HashMap<UUID, Account>();

   // file-handling
   /** maps UUIDs to account creation records, easing regenerating changed those records for saving */
   private static StringBuilder accountCreationRecords = new StringBuilder();
   /** holds account entries which failed to load */
   private static StringBuilder accountsErrored = new StringBuilder();
   /** holds account IDs whose entries should be regenerated */
   private static Set<Account> accountsChangedSinceLastSave = new HashSet<Account>();
   /** maps account IDs to account entries, easing regenerating changed accounts' entries for saving */
   private static TreeMap<String, StringBuilder> accountEntries = new TreeMap<String, StringBuilder>();
   /** whether or not to remove entries for mapping players to default accounts */
   private static boolean regenerateDefaultAccountEntries = false;
   /** holds entries mapping players to default accounts */
   private static StringBuilder defaultAccountEntries = new StringBuilder();

   // miscellaneous
   /** a loose mutex used to avoid synchronization problems with threads rarely adjusting accounts' properties */
   private static volatile boolean doNotAdjustAccounts = false;

   // INSTANCE VARIABLES
   /** funds held by account */
   private float money;
   /** players who may view and withdraw from account */
   private ArrayDeque<UUID> accountUsers;

   // FUNCTIONS
   /**
    * Creates an account with specified amount of starting money.
    * This constructor is private to allow for not making an account
    * to handle spam attacks and to condense code.
    *
    * @param accountID     internal name of account
    * @param accountOwner  player who should have access to the account
    * @param startingMoney how much funds the account should have
    * @return a newly opened account or null if the account could not be created
    */
   public static Account makeAccount(final String accountID, final UUID accountOwner, final float startingMoney) {
      // grab the account owner's name once
      String accountOwnerName = Config.userInterface.getDisplayName(accountOwner);

      // avoid creating an account whose ID matches an existing player's name
      // if the player isn't the one creating the account
      if (Config.userInterface.doesPlayerExist(accountID) && // if the account ID is a player's name and
         !accountOwnerName.equals(accountID))            // if the player isn't the account owner
         return null;

      // avoid creating an account whose ID is a number
      try {
         float number = Float.parseFloat(accountID);
         Config.userInterface.printErrorToUser(accountOwner, StringTable.MSG_ACCOUNT_NUMERICAL_ID);
         return null;
      } catch (Exception e) {}

      // try to grab the account in case it is a duplicate
      Account account = null;
      // TreeMap throws an exception if passed null
      if (accountID != null)
         account = accounts.get(accountID);

      // avoid overwriting an existing account
      // unless a personal account is being made
      if (account != null) {
         // if a new personal account is being made,
         // delete the non-personal account and send its money to its owner
         if (!account.getOwner().equals(accountOwner) &&
            accountID.equals(accountOwnerName)) {
            // send non-personal account's money to its owner
            UUID nonpersonalAccountOwner = account.getOwner();
            // if the non-personal account owner doesn't
            // have a personal account, open one for them
            if (!accounts.containsKey(Config.userInterface.getDisplayName(nonpersonalAccountOwner))) {
               makeAccount(Config.userInterface.getDisplayName(nonpersonalAccountOwner), nonpersonalAccountOwner,
                  account.getMoney() + Config.accountStartingMoney);
            } else {
               accounts.get(Config.userInterface.getDisplayName(nonpersonalAccountOwner)).addMoney(account.getMoney());
            }

            // delete the non-personal account
            waitForMutex(); // check if another thread is adjusting accounts' properties
            accounts.remove(accountID);

            // tell the non-personal account owner about
            // the non-personal account being deleted
            Config.userInterface.printErrorToUser(nonpersonalAccountOwner, accountID + "'s funds have been sent to your personal account." + System.lineSeparator() +
               accountID + " is now another player's personal account.");
         }
         // non-personal accounts cannot take the IDs of existing accounts
         // additionally, accounts cannot be reset by attempting to recreate them
         else {
            return null;
         }
      }

      // if there is no player creating the account or
      // if the account is personal (account ID == player's name),
      // don't check account creation limit and
      // don't increment per-player account creation count
      if (accountOwner != null &&
         !(accountID != null && !accountID.isEmpty() && accountID.equals(accountOwnerName))) {
         // if the account isn't personal,
         // check account creation limit
         if (Config.accountMaxCreatedByIndividual != -1 &&
            Config.accountMaxCreatedByIndividual <= Account.getNumAccountsCreatedByUser(accountOwner)) {
            Config.userInterface.printErrorToUser(accountOwner, StringTable.MSG_ACCOUNT_TOO_MANY);
            return null;
         }

         // if the account creation limit isn't reached,
         // increment the per-player account creation count
         else {
            // increment account creation count
            accountsCreatedPerUser.put(accountOwner, accountsCreatedPerUser.getOrDefault(accountOwner, 0) + 1);

            // generate record to ease saving later
            // format: #,playername,count
            accountCreationRecords.append("#," + accountOwner.toString() + ',' + accountsCreatedPerUser.get(accountOwner) + '\n');
         }
      }

      // pass parameters to constructor
      waitForMutex(); // check if another thread is adjusting accounts' properties
      return new Account(accountID, accountOwner, startingMoney);
   }

   /**
    * Creates an account with default starting money.
    * This constructor is private to allow for not making an account
    * to handle spam attacks and to condense code.
    *
    * @param accountID    internal name of account
    * @param accountOwner player who should have access to the account
    * @return a newly opened account or null if the account could not be created
    */
   public static Account makeAccount(final String accountID, final UUID accountOwner) {
      return makeAccount(accountID, accountOwner, Config.accountStartingMoney);
   }

   /**
    * Removes the specified account from the marketplace.
    *
    * @param accountID     internal name of account
    * @param accountOwner  player who should have access to the account
    */
   public static void deleteAccount(final String accountID, final UUID accountOwner) {
      // check whether the account exists
      if (accountID == null || accountID.isEmpty() || !accounts.containsKey(accountID))
         return;

      // check account permissions
      Account account   = accounts.get(accountID);
      UUID    realOwner = account.getOwner();
      // only the account's owner and server administrators may delete the account
      if ((realOwner == null || !realOwner.equals(accountOwner)) && !Config.userInterface.isAnOp(accountOwner)) {
         Config.userInterface.printErrorToUser(accountOwner, StringTable.MSG_ACCOUNT_DENIED_DELETE + accountID);
         return;
      }

      // if the account is personal, it may not be deleted
      String playername = Config.userInterface.getDisplayName(realOwner);
      if (accountID.equals(playername)) {
         Config.userInterface.printErrorToUser(accountOwner, StringTable.MSG_ACCOUNT_DENIED_DELETE_PERSONAL);
         return;
      }

      // transfer any positive funds to a personal account
      if (account.getMoney() > 0.0f && !playername.isEmpty()) {
         // check if account owner's personal account exists
         Account accountRecipient = accounts.get(playername);
         if (accountRecipient == null) {
            // if sending to a nonexistent personal account,
            // open the personal account
            if (Config.userInterface.doesPlayerExist(playername)) {
               accountRecipient = makeAccount(playername, realOwner);
            }
         }

         // if possible, send the money
         if (accountRecipient != null) {
            // transfer the money
            accountRecipient.addMoney(account.getMoney());

            // report the transfer
            Config.userInterface.printToUser(realOwner, "You received $" + Float.toString(account.getMoney()) + " from " + accountID);
         }
      }

      // delete the account
      waitForMutex(); // check if another thread is adjusting accounts' properties
      accounts.remove(accountID);
      accountsChangedSinceLastSave.remove(account);
      accountEntries.remove(accountID);
      // account creation records are not adjusted to prevent spamming

      // remove any default accounts pointing to the deleted account
      UUID    playerID;
      Account defaultAccount;
      for (Map.Entry<UUID, Account> entry : defaultAccounts.entrySet()) {
         playerID       = entry.getKey();
         defaultAccount = entry.getValue();

         if (account == defaultAccount)
            defaultAccounts.remove(playerID);
      }
   }

   /**
    * Returns the account with the specified ID or
    * returns null if the account is not found.
    * <p>
    * Complexity: O(1)
    * @param accountID internal name of account to be returned if found
    * @return account or null
    */
   public static Account getAccount(final String accountID) {
      // TreeMap throws an exception if given null
      if (accountID != null)
         return accounts.get(accountID);
      else
         return null;
   }

   /**
    * Returns the account with the specified ID.
    * If the given ID is null or empty, the player's personal account is used or opened.
    * If the account is not found, returns null.
    *
    * @param accountID   internal name of account to be returned if found
    * @param playername  name of player wishing to access the account
    * @param accountUser ID of player wishing to access the account
    * @return the account if it exists or null
    */
   public static Account grabPlayerAccount(final String accountID, String playername, UUID accountUser) {
      // validate parameters
      if (playername == null && accountUser == null)
         return null;
      else if (playername == null)
         playername = Config.userInterface.getDisplayName(accountUser);
      else if (accountUser == null)
         accountUser = Config.userInterface.getPlayerID(playername);

      // account to be returned
      Account account = null;

      // if no account is given, use the player's personal account or default account
      // if the player's personal account is used, make sure it exists
      if (accountID == null || accountID.isEmpty() ||
          (Config.userInterface.doesPlayerExist(accountID) &&
          accountID.equals(playername))) {
         // if the player set a default account, grab it
         account = defaultAccounts.get(accountUser);

         // if no default account is set, use a personal account
         if (account == null) {
            // if a default account was deleted, remove the default account entry
            if (defaultAccounts.containsKey(accountUser))
               defaultAccounts.remove(accountUser);

            // if the player doesn't have a personal account, make one
            if (playername != null) {
               account = accounts.get(playername);
               if (account == null)
                  account = makeAccount(playername, accountUser);
            }
            else
               account = null;
         }
      }

      // if account is non-personal, try to grab directly
      if (account == null && accountID != null)
         account = accounts.get(accountID);

      return account;
   }

   /**
    * Grabs the account with the specified ID and checks the given player's permissions.
    * If the given ID is null or empty, the player's personal account is used or opened.
    * If the account is not found or fails a check, the player is sent
    * a corresponding error message and the function returns null.
    *
    * @param accountID   internal name of account to be returned if found
    * @param accountUser player wishing to access the account
    * @return the account if it exists and passes all checks or null
    */
   public static Account grabAndCheckAccount(final String accountID, final UUID accountUser) {
      // if no account is given, use the player's personal account or default account
      // if the player's personal account is used, make sure it exists
      Account account = grabPlayerAccount(accountID, null, accountUser);

      // if given account doesn't exist, stop
      if (account == null) {
         Config.userInterface.printErrorToUser(accountUser, StringTable.ERROR_ACCOUNT_MISSING + accountID);
         return null;
      }

      // if the player doesn't have access to the account, stop
      if (!account.hasAccess(accountUser)) {
         // server administrators should have access to the admin account
         if (!accountID.equals(StringTable.ACCOUNT_ADMIN) || !Config.userInterface.isAnOp(accountUser)) {
           Config.userInterface.printErrorToUser(accountUser, StringTable.MSG_ACCOUNT_DENIED_ACCESS + accountID);
           return null;
         }
      }

      // if the account exists and has passed all checks
      return account;
   }

   /**
    * Grabs the account with the specified ID, checks the given player's permissions,
    * and checks whether the account's funds is at least the specified amount.
    * If the given ID is null or empty, the player's personal account is used or opened.
    * If the specified amount is NaN, the funds check is skipped.
    * If the account is not found or fails a check, the player is sent
    * a corresponding error message and the function returns null.
    * <p>
    * Complexity: O(1)
    * @param accountID    internal name of account to be returned if found
    * @param accountUser  player wishing to access the account
    * @param minimumFunds the lowest amount of money the account should have
    * @return the account if it exists and passes all checks or null
    */
   public static Account grabAndCheckAccount(final String accountID, final UUID accountUser, final float minimumFunds) {
      Account account = grabAndCheckAccount(accountID, accountUser);

      // if given account doesn't exist, stop
      if (account == null)
         return null;

      // check if account has enough money
      if (!Float.isNaN(minimumFunds) &&
          account.getMoney() < minimumFunds) {
         Config.userInterface.printErrorToUser(accountUser, StringTable.MSG_ACCOUNT_NO_MONEY);
         return null;
      }

      // if the account exists and has passed all checks
      return account;
   }

   /**
    * Returns a set of all account names currently being used.
    * Useful for autocompletion.
    * <p>
    * Complexity: O(1)
    * @return all account names in use
    */
   public static Set<String> getAllAccountNames() { return accounts.keySet(); }

   /**
    * Returns a set of all accounts currently being used within the marketplace.
    * <p>
    * Complexity: O(n), where n is the number of accounts to be returned
    * @return a set of all accounts usable within the marketplace
    */
   public static Collection<Account> getAllAccounts() { return accounts.values(); }

   /**
    * Creates an account.
    * This constructor is private to allow for not making an account
    * to handle spam attacks and to condense code.
    * <p>
    * Does not use Account's loose mutex; concurrent modification is not protected against.
    * <p>
    * Complexity: O(1)
    * @param accountID     internal name of account
    * @param accountOwner  player who should have access to the account
    * @param startingMoney how much money the account should start with
    */
   private Account(final String accountID, final UUID accountOwner, float startingMoney) {
      // account IDs and player IDs may be null or empty
      // no account ID == account is not placed into Account.accounts
      // no player  ID == account is inaccessible to all players

      // if the given starting amount happens to be NaN,
      // set money as 0 to avoid errors when using the account
      if (Float.isNaN(startingMoney))
         money = 0.0f;
      else
         money = startingMoney;

      // note: if no player name was given,
      // an inaccessible account is made
      if (accountOwner == null) {
         accountUsers = null;
      } else {
         accountUsers = new ArrayDeque<UUID>();
         accountUsers.add(accountOwner);
      }

      // if a ID was given, use it to insert
      // the new account into the accounts table
      if (accountID != null && !accountID.isEmpty()) {
         accounts.put(accountID.intern(), this);

         // add account to the save list
         accountEntries.put(accountID.intern(), new StringBuilder());

         // mark the new account as needing to be saved
         accountsChangedSinceLastSave.add(this);
      }
   }

   /**
    * Retrieves the amount of money in the account.
    * <p>
    * Complexity: O(1)
    * @return how much wealth the account holds
    */
   public float getMoney() {
      return money;
   }

   /**
    * Increases an account's funds.
    * <p>
    * Complexity: O(1)
    * @param quantity amount to add to account
    */
   public void addMoney(float quantity) {
      // if quantity is 0, do nothing
      if (quantity == 0.0f || Float.isNaN(quantity))
         return;

      // add funds to the account
      waitForMutex(); // check if another thread is adjusting accounts' properties
      money += quantity;

      // mark the new account as needing to be saved
      accountsChangedSinceLastSave.add(this);
   }

   /**
    * Decreases an account's funds.
    * <p>
    * Complexity: O(1)
    * @param quantity amount to subtract from account
    */
   public void subtractMoney(float quantity) {
      // if quantity is 0, do nothing
      if (quantity == 0.0f || Float.isNaN(quantity))
         return;

      // add funds to the account
      waitForMutex(); // check if another thread is adjusting accounts' properties
      money -= quantity;

      // mark the new account as needing to be saved
      accountsChangedSinceLastSave.add(this);
   }

   /**
    * Forces an account's money to be a given value.
    * <p>
    * Does not use Account's loose mutex; concurrent modification is not protected against.
    * <p>
    * Complexity: O(1)
    * @param quantity amount to set money to
    */
   public void setMoney(float quantity) {
      // if quantity is not a number,
      // don't do anything
      if (Float.isNaN(quantity))
         return;

      // set account funds
      money = quantity;

      // mark the new account as needing to be saved
      accountsChangedSinceLastSave.add(this);
   }

   /**
    * Check a player's access to the account, 
    * whether they may view and withdraw from the account.
    * <p>
    * Complexity: O(n), where n is the amount of players permitted to access the account
    * @param playerID player id whose access
    * @return whether the player has access to the account
    */
   public Boolean hasAccess(UUID playerID) {
      // if no id was given, there is nothing to do
      if (playerID == null)
         return false;

      // if no players are permitted, then none are permitted
      if (accountUsers == null)
         return false;

      return accountUsers.contains(playerID);
   }

   /**
    * Gives a player account access, so they may view and withdraw from the account.
    *
    * @param playerID            command-issuing user
    * @param playerToAllowAccess player id allowed access
    * @param accountID           what to call the account when messaging players
    */
   public void grantAccess(UUID playerID, UUID playerToAllowAccess, String accountID) {
      // allow playerID to be null so test suite make force allowing access

      // if no id was given, there is nothing to do
      if (playerToAllowAccess == null)
         return;

      // check whether player has permission to use the account
      if (playerID != null && !hasAccess(playerID)) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_ACCOUNT_DENIED_ACCESS + accountID);
         return;
      }

      // check if another thread is adjusting accounts' properties
      waitForMutex();

      // if no players are permitted, prepare a container to hold permitted players
      if (accountUsers == null)
         accountUsers = new ArrayDeque<UUID>();

      // if player to have access granted already has access, say so
      if (accountUsers.contains(playerToAllowAccess)) {
         if (playerID != null)
            Config.userInterface.printToUser(playerID, Config.userInterface.getDisplayName(playerToAllowAccess) + " already may access " + accountID);
         return;
      }

      // add the player to the permitted list
      accountUsers.add(playerToAllowAccess);
      if (accountID != null && !accountID.isEmpty()) {
         if (playerID != null)
            Config.userInterface.printToUser(playerID, Config.userInterface.getDisplayName(playerToAllowAccess) + " may now access " + accountID);
         Config.userInterface.printToUser(playerToAllowAccess, StringTable.MSG_ACCOUNT_ACCESS_GRANTED + accountID);
      }

      // mark the new account as needing to be saved
      accountsChangedSinceLastSave.add(this);
   }

   /**
    * Removes a player's access, so they no longer may view and withdraw from the account.
    *
    * @param playerID               command-issuing user
    * @param playerToDisallowAccess player whose access should be revoked
    * @param accountID              what to call the account when messaging players
    */
   public void revokeAccess(UUID playerID, UUID playerToDisallowAccess, String accountID) {
      // if no id was given, there is nothing to do
      if (playerID == null || playerToDisallowAccess == null)
         return;

      // check whether player has permission to use the account
      if (playerID != null && !hasAccess(playerID)) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_ACCOUNT_DENIED_ACCESS + accountID);
         return;
      }

      // if no players are permitted, do nothing
      // if player to have access revoked doesn't have access, do nothing
      if (accountUsers == null || !accountUsers.contains(playerToDisallowAccess)) {
         if (playerID != null)
            Config.userInterface.printToUser(playerID, Config.userInterface.getDisplayName(playerToDisallowAccess) + " already cannot access " + accountID + ".");
         return;
      }

      // check if another thread is adjusting accounts' properties
      waitForMutex();

      // remove player to have access revoked
      accountUsers.remove(playerToDisallowAccess);
      if (accountID != null && !accountID.isEmpty()) {
         if (playerID != null)
            Config.userInterface.printToUser(playerID, Config.userInterface.getDisplayName(playerToDisallowAccess) + " may no longer access " + accountID);
         Config.userInterface.printToUser(playerToDisallowAccess, StringTable.MSG_ACCOUNT_ACCESS_REVOKED + accountID);
      }

      // check whether the account was the player's default account
      Account defaultAccount = defaultAccounts.get(playerID);
      if (defaultAccount != null && defaultAccount == this) {
         defaultAccounts.remove(playerID);
         regenerateDefaultAccountEntries = true;
      }

      // mark the new account as needing to be saved
      accountsChangedSinceLastSave.add(this);
   }

   /**
    * Displays the amount of money in an account.
    * <p>
    * Complexity: O(1)
    * @param playerID  command-issuing user
    * @param accountID what to call this account when messaging players
    */
   public void check(UUID playerID, String accountID) {
      // if no id was given, there is nothing to do
      if (playerID == null || accountID == null || accountID.isEmpty())
         return;

      // check whether player has permission to use the account
      if (!hasAccess(playerID)) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_ACCOUNT_DENIED_ACCESS + accountID);
         return;
      }

      // print the quantity in the given account
      Config.userInterface.printToUser(playerID, accountID + ": " +
         PriceFormatter.PRICE_FORMAT.format(money));
   }

   /**
    * Sends money from the current account to another account.
    * <p>
    * Complexity: O(1)
    * @param playerID    command-issuing user
    * @param quantity    amount to move between accounts
    * @param senderID    what to tell the recipient about where the money came from
    * @param recipientID account id to transfer money to
    */
   public void sendMoney(UUID playerID, float quantity, String senderID, String recipientID) {
      // if no id was given, there is nothing to do
      if (playerID == null)
         return;

      // check whether player has permission to use this account
      if (!hasAccess(playerID)) {
         Config.userInterface.printErrorToUser(playerID, StringTable.MSG_ACCOUNT_DENIED_TRANSFER);
         return;
      }

      // check if quantity is valid
      if (quantity < 0.0f) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_QUANTITY);
         return;
      }
      if (quantity == 0.0f)
         // don't print an error for trying to transfer $0, just ignore it
         return;

      // check if there is enough money to
      // send the desired quantity and pay the transaction fee
      float fee = 0.0f;
      if (Config.chargeTransactionFees &&
          Config.transactionFeeSending != 0.0f) {
         // find fee's charge
         fee = Config.transactionFeeSending;
         if (Config.transactionFeeSendingIsMult) {
            fee *= quantity;

            // if the price and fee percentage have opposite signs, flip the fee's sign
            // so positive rates don't pay out anything and negative rates don't take anything
            if ((fee < 0.0f && Config.transactionFeeSending > 0.0f) ||
                (Config.transactionFeeSending < 0.0f && fee > 0.0f))
               fee = -fee;
         }

         // stop if there isn't enough money
         if (money + canNegativeFeeBePaid(fee) < quantity + fee) {
            Config.userInterface.printErrorToUser(playerID, StringTable.MSG_ACCOUNT_NO_MONEY_FEE);
            return;
         }
      } else {
         // check if sender is able to send the desired quantity
         if (money < quantity) {
            Config.userInterface.printErrorToUser(playerID, StringTable.MSG_ACCOUNT_NO_MONEY);
            return;
         }
      }

      // check if recipient id is empty
      if (recipientID == null || recipientID.isEmpty()) {
         Config.userInterface.printErrorToUser(playerID, StringTable.ERROR_ACCOUNT_ID_INVALID);
         return;
      }

      // if the recipient account ID is a playername, grab their default account or personal account
      Account accountRecipient = grabPlayerAccount(recipientID, recipientID, null);

      // check if recipient account exists
      if (accountRecipient == null) {
         Config.userInterface.printErrorToUser(playerID,StringTable.ERROR_ACCOUNT_MISSING + recipientID);
         return;
      }

      // Note: the sending account might be the recipient account.
      // In this case, the transfer is allowed, but shouldn't do much.

      // transfer the money
      waitForMutex(); // check if another thread is adjusting accounts' properties
      money -= quantity;
      accountRecipient.addMoney(quantity);

      // report the transfer
      Config.userInterface.printToUser(playerID, "Successfully transferred " + PriceFormatter.PRICE_FORMAT.format(quantity) + " to " + recipientID);

      // if the owner of the recipient account can be found,
      // tell them about the transfer
      if (accountRecipient.accountUsers != null &&
         accountRecipient.accountUsers.size() != 0) {
         if (senderID == null || senderID.isEmpty()) {
            // use the name of the account transferred into if it isn't a personal account
            if (recipientID.equals(accountRecipient.accountUsers.getFirst()))
               Config.userInterface.printToUser(accountRecipient.accountUsers.getFirst(), "Received " + PriceFormatter.PRICE_FORMAT.format(quantity) + " from an anonymous party");
            else
               Config.userInterface.printToUser(accountRecipient.accountUsers.getFirst(), "Received " + PriceFormatter.PRICE_FORMAT.format(quantity) + " in " + recipientID + " from an anonymous party");
         } else {
            // use the name of the account transferred into if it isn't a personal account
            if (recipientID.equals(accountRecipient.accountUsers.getFirst()))
               Config.userInterface.printToUser(accountRecipient.accountUsers.getFirst(), "Received " + PriceFormatter.PRICE_FORMAT.format(quantity) + " from " + senderID);
            else
               Config.userInterface.printToUser(accountRecipient.accountUsers.getFirst(), "Received " + PriceFormatter.PRICE_FORMAT.format(quantity) + " in " + recipientID + " from " + senderID);
         }
      }

      // pay the transaction fee
      if (Config.chargeTransactionFees &&
          Config.transactionFeeSending != 0.0f) {
         // check whether a fee collection account should be used
         if (Config.transactionFeesShouldPutFeesIntoAccount)
            // if the fee is negative and unaffordable, don't pay it
            if (depositTransactionFee(fee))
               return;

         // pay the fee
         waitForMutex(); // check if another thread is adjusting accounts' properties
         money -= fee;

         // report fee payment
         Config.userInterface.printToUser(playerID, Config.transactionFeeSendingMsg + PriceFormatter.PRICE_FORMAT.format(fee));
      }

      // mark the new account as needing to be saved
      accountsChangedSinceLastSave.add(this);
   }

   /**
    * Returns account information writable to a file.
    * <p>
    * Complexity: O(log n)
    * @return account ID-less account information formatted as a single-line file entry
    */
   protected String getWrittenState() {
      // format: accountID,money,accountOwner,accountOwner,...,accountOwner

      // grab account funds
      StringBuilder result = new StringBuilder();
      result.append(money).append(',');

      // if there are any permitted players, write them
      if (accountUsers != null) {
         for (UUID id : accountUsers)
            result.append(id.toString()).append(',');
      }

      // remove trailing comma and
      // end the line to signify end of account information
      result.setLength(result.length() - 1);
      result.append('\n');

      return result.toString();
   }

   /**
    * Returns the ID of the player responsible for the account.
    * <p>
    * Complexity: O(1)
    * @return first player ID in the list of account users
    */
   public UUID getOwner() {
      if (accountUsers == null)
         return null;
      else
         return accountUsers.getFirst();
   }

   /**
    * Returns how many accounts a player has created.
    * <p>
    * Complexity: O(1)
    * @param playerID player whose the number of accounts needs to be checked
    * @return numbers of times user has created an account
    */
   public static int getNumAccountsCreatedByUser(final UUID playerID) {
      // if no id was given, then don't allow an account to be made
      if (playerID == null)
         return 2147483647;

      // if there is no record of the user creating the accounts,
      // they probably have not created any
      if (!accountsCreatedPerUser.containsKey(playerID))
         return 0;

      return accountsCreatedPerUser.get(playerID);
   }

   /**
    * Creates/reloads accounts usable in the marketplace based on the accounts file.
    * <p>
    * Complexity: O(n log n)
    */
   public static void loadAccounts() {
      File fileAccounts; // has market account information

      // try to load the accounts file
      fileAccounts = new File(Config.filenameAccounts);
      // check file existence
      if (!fileAccounts.isFile()) {
         // don't throw an exception, print a warning to advise user to reload accounts 
         Config.userInterface.printToConsole(StringTable.WARN_FILE_MISSING + Config.filenameAccounts +
            System.lineSeparator() + "To load accounts, replace " + Config.filenameAccounts +
            ", " + System.lineSeparator() + "then use the command \"reload accounts\"."
         );
         return;
      }

      acquireMutex();

      // if there are already accounts usable in the market, remove them
      // this is useful for reloading
      if (!accounts.isEmpty()) {
         accounts.clear();
         accountEntries.clear();
         accountsCreatedPerUser.clear();
         accountCreationRecords.setLength(0);
         accountsErrored.setLength(0);
         defaultAccounts.clear();
      }

      // create a generic admin account
      new Account(StringTable.ACCOUNT_ADMIN, UUID.nameUUIDFromBytes((StringTable.ACCOUNT_ADMIN).getBytes()), Float.POSITIVE_INFINITY);

      // open the file
      Scanner fileReader;
      try {
         fileReader = new Scanner(fileAccounts);
      } catch (FileNotFoundException e) {
         Config.userInterface.printToConsole(StringTable.WARN_FILE_MISSED + Config.filenameAccounts);
         e.printStackTrace();

         // allow other threads to adjust accounts' properties
         releaseMutex();
         return;
      }

      // set up variables for account creation
      Account account; // holds account being created, useful when adding permissions
      float money;     // useful for preventing crashes when loading invalid entries
      UUID playerID;   // for loading default account entries

      // parse the file and add accounts
      String[] data; // holds pointer to line being parsed
      while (fileReader.hasNextLine()) { // grab line to be parsed
         data = fileReader.nextLine().split(",", 0); // split line using commas

         // check if there is enough data to create an account
         if (data.length < 2)
            continue;

         // if the line is a comment, skip it
         if (data[0].startsWith("//"))
            continue;

         // if the line is an account creation count, process it accordingly
         if (data[0].equals("#")) {
            try {
               accountsCreatedPerUser.put(UUID.fromString(data[1]), Integer.parseInt(data[2]));
               accountCreationRecords.append(String.join(",", data) + '\n');
            } catch (Exception e) {
               String erroredAccount = String.join(",", data);
               Config.userInterface.printToConsole(StringTable.WARN_ACCOUNT_CREATION + erroredAccount);

               // store the line entry for later
               accountsErrored.append(erroredAccount);
            }

            continue; // skip to the next entry
         }

         // if the line maps a player ID to their default account, process it accordingly
         if (data[0].equals("*")) {
            try {
               // check whether the account exists
               if (data[2] != null)
                  account = accounts.get(data[2]);
               else
                  account = null;
               if (account == null) {
                  String erroredAccount = String.join(",", data);
                  Config.userInterface.printToConsole(StringTable.WARN_ACCOUNT_NONEXISTENT + erroredAccount);
                  continue; // skip to the next entry
               }

               // check whether the player ID is valid
               playerID = UUID.fromString(data[1]);
               if (playerID == null) {
                  String erroredAccount = String.join(",", data);
                  Config.userInterface.printToConsole(StringTable.WARN_ACCOUNT_UUID_DEFAULT + erroredAccount);
                  continue; // skip to the next entry
               }

               // check whether the player still has permission to use the account
               if (!account.hasAccess(playerID))
                  continue; // ignore the invalid entry

               // if everything's fine, use the entry
               defaultAccounts.put(playerID, account);
            } catch (Exception e) {
               String erroredAccount = String.join(",", data);
               Config.userInterface.printToConsole(StringTable.WARN_ACCOUNT_DEFAULT + erroredAccount);

               // store the line entry for later
               accountsErrored.append(erroredAccount);
            }

            continue; // skip to the next entry
         }

         // add new account to the market
         try {
            // format: accountID,money,accountOwner,accountOwner,...,accountOwner

            // try to parse account funds
            try {
               money = Float.parseFloat(data[1]);
            } catch (Exception ef) {
               Config.userInterface.printToConsole(StringTable.ERROR_ACCOUNT_PARSING + data[0]);

               // store the line entry for later
               accountsErrored.append(String.join(",", data));

               continue; // skip the invalid entry and try to load the rest of the entries
            }

            // create the account
            // if the account is inaccessible, don't try to load a permitted player
            if (data.length == 2) {
               account = new Account(data[0], null, money);
            } else {
               // try to parse account owner UUID
               try {
                  account = new Account(data[0], UUID.fromString(data[2]), money);
               } catch (Exception eo) {
                  Config.userInterface.printToConsole("warning - could not parse account owner UUID " + data[2] + " for account " + data[0]);

                  // store the line entry for later
                  accountsErrored.append(String.join(",", data));

                  continue; // skip the invalid entry and try to load the rest of the entries
               }

               // if several players should have access to the account, give them access
               if (data.length > 3) {
                  for (int i = 3; i < data.length; i++) {
                     // try to parse account user UUIDs individually
                     try {
                        account.accountUsers.add(UUID.fromString(data[i]));
                     } catch (Exception eu) {
                        Config.userInterface.printToConsole("warning - could not parse account user UUID " + data[i] + " for account " + data[0]);
                     }
                  }
               }
            }
         } catch (Exception e) {
            Config.userInterface.printToConsole(StringTable.ERROR_FILE_LOAD_ACCOUNTS);
            e.printStackTrace();
         }
      }

      // close the file
      fileReader.close();

      // mark default account entries for saving
      // to be regenerated using any new data
      regenerateDefaultAccountEntries = true;

      // allow other threads to adjust accounts' properties
      releaseMutex();
   }

   /**
    * Writes information for regenerating
    * current accounts usable in the marketplace.
    * <p>
    * Complexity: O(n log n)
    */
   public static void saveAccounts() {
      // if there is nothing to save, do nothing
      if (accountsChangedSinceLastSave.isEmpty() && !regenerateDefaultAccountEntries)
         return;

      String  accountID;  // ID for the account currently being written
      Account account;    // the account currently being written
      UUID    playerID;   // ID for the player whose account creation count is currently being written
      StringBuilder json; // for regenerating account written states

      // loop through accounts and regenerate written states as needed
      for (Map.Entry<String, Account> entry : accounts.entrySet()) {
         // prevents a null pointer exception if an account was just deleted
         if (entry == null)
            continue;

         accountID = entry.getKey();
         account   = entry.getValue();

         // regenerate written states as needed
         if (accountsChangedSinceLastSave.contains(account)) {
            // format: accountID,money,accountOwner,accountOwner,...,accountOwner
            json = accountEntries.get(accountID);
            json.setLength(0); // clear the entry without losing the reference nor reallocating memory
            // prevents a null pointer exception if an account was just deleted
            if (account == null)
               continue;
            json.append(accountID).append(',').append(account.getWrittenState()); // save updated entry
         }
      }
      accountsChangedSinceLastSave.clear();

      // if necessary, regenerate entries mapping players to default accounts
      if (regenerateDefaultAccountEntries) {
         defaultAccountEntries.setLength(0);
         for (Map.Entry<UUID, Account> entry : defaultAccounts.entrySet()) {
            // prevents a null pointer exception if an entry was just deleted
            if (entry == null)
               continue;

            playerID = entry.getKey();
            account  = entry.getValue();

            // grab account ID using Stream
            final Account accountRef = account;
            accountID = accounts.keySet().stream().filter(key -> accountRef.equals(accounts.get(key))).findFirst().get();
            if (accountID == null) // if the account ID is not found, skip it
               continue;

            // format: *,playerID,accountID
            defaultAccountEntries.append("*,").append(playerID.toString()).append(',').append(accountID).append('\n');
         }
      }
      regenerateDefaultAccountEntries = false;

      // ensure the save file and directory exist
      try {
         File fileAccounts = new File (Config.filenameAccounts);
         if (!fileAccounts.exists()) {
            fileAccounts.getParentFile().mkdirs();
            fileAccounts.createNewFile();
         }
      } catch (IOException e) {
         Config.userInterface.printToConsole(StringTable.ERROR_FILE_CREATE_SAVE_ACCOUNTS);
         e.printStackTrace();
         return;
      }

      BufferedWriter fileWriter = null; // use a handle to ensure the file gets closed
      try {
         // open the accounts file, create it if it doesn't exist
         fileWriter = new BufferedWriter(new FileWriter(Config.filenameAccounts, false));

         // warn users file may be overwritten
         fileWriter.write(StringTable.WARN_FILE_OVERWRITE);

         // loop through accounts and write to file
         for (StringBuilder writtenState : accountEntries.values()) {
            // prevents a null pointer exception if an account was just deleted
            if (writtenState == null)
               continue;

            fileWriter.write(writtenState.toString());
         }

         // write account creation counts to file
         // format: #,playerID,count
         if (accountCreationRecords.length() > 0)
            fileWriter.write('\n' + accountCreationRecords.toString());

         // write entries mapping players to default accounts
         // format: *,playerID,accountID
         if (defaultAccountEntries.length() > 0)
            fileWriter.write('\n' + defaultAccountEntries.toString());

         // write accounts which failed to load,
         // they might be nonexistent until fixed by a server administrator
         if (accountsErrored.length() > 0)
            fileWriter.write(StringTable.WARN_FILE_WARES_INVALID + accountsErrored + '\n');
      } catch (IOException e) {
         Config.userInterface.printToConsole(StringTable.ERROR_FILE_SAVE_ACCOUNTS);
         e.printStackTrace();
      }

      // ensure the file is closed
      try {
         if (fileWriter != null)
            fileWriter.close();
      } catch (Exception e) { }
   }

   /**
    * Marks an account to be the one to be used by default for a player's transactions.
    * <p>
    * Complexity: O(1)
    * @param playerID  player setting their default account
    * @param accountID account to be used by default in player's transactions
    * @return whether the account was marked as the player's default account
    */
   public static boolean setDefaultAccount(UUID playerID, String accountID) {
      // if something is wrong with the parameters, stop
      if (playerID == null || accountID == null || accountID.isEmpty())
         return false;

      // check the account and the player's permissions
      Account account = grabAndCheckAccount(accountID, playerID);
      if (account == null)
         return false; // if there was a problem, an error has already been printed

      // grab the player's name to check if the account is personal
      String playername = Config.userInterface.getDisplayName(playerID);

      // check if another thread is adjusting accounts' properties
      waitForMutex();

      // if the account is the player's personal account,
      // remove any default accounts set for them
      if (accountID.equals(playername)) {
         defaultAccounts.remove(playerID);
         regenerateDefaultAccountEntries = true;
         return true;
      }

      // if player has permissions, set the non-personal account as their default
      defaultAccounts.put(playerID, account);
      regenerateDefaultAccountEntries = true;
      return true;
   }

   /**
    * Prevents other threads from adjusting the marketplace's wares.
    * If another thread is already adjusting accounts, then waits for that thread to finish.
    * <p>
    * Complexity: O(1)
    */
   public static void acquireMutex() {
      // wait for permission to adjust accounts' properties
      if (doNotAdjustAccounts) {
         // sleep() may throw an exception
         try {
            while (doNotAdjustAccounts) {
               Thread.sleep(10); // 10 ms wait for mutex to become available
            }
         } catch(Exception ex) {
            Thread.currentThread().interrupt();
         }
      }

      // prevent other threads from adjusting accounts' properties
      doNotAdjustAccounts = true;
      // wait until any other threads finish execution since mutex is loose
      try {
         Thread.sleep(5);
      } catch(Exception ex) {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Checks whether other threads are adjusting accounts' properties
    * and waits for them to finish if they are.
    * <p>
    * Complexity: O(1)
    */
   private static void waitForMutex() {
      // check if another thread is adjusting accounts' properties
      if (doNotAdjustAccounts) {
         // sleep() may throw an exception
         try {
            while (doNotAdjustAccounts) {
               Thread.sleep(10); // 10 ms wait for mutex to become available
            }
         } catch(Exception ex) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * Allows other threads to adjust accounts' properties.
    * <p>
    * Complexity: O(1)
    */
   public static void releaseMutex() {
      doNotAdjustAccounts = false;
   }

   /**
    * Spawns and handles threads for features.
    * <p>
    * Complexity: O(1)
    */
   public static void startOrReconfigPeriodicEvents() {
      AccountInterestApplier.startOrReconfig(); // if necessary, start, reload, or stop applying account interest
   }

   /**
    * Closes threads for features.
    * <p>
    * Complexity: O(1)
    */
   public static void endPeriodicEvents() {
      AccountInterestApplier.end(); // if necessary, stop applying account interest
   }

   /**
    * When negative fees are used, returns the amount of a negative fee
    * the fee collection account will pay not out due to constrained funds.
    * <p>
    * Complexity: O(1)
    * @param fee transaction fee to be paid
    * @return if negative fee can be paid, returns 0; if unpayable, returns the input
    */
   public static float canNegativeFeeBePaid(float fee) {
      // if the fee isn't negative, nothing has to be paid out
      if (fee >= 0.0f)
         return 0.0f;

      // validate fee collection account's ID
      if (Config.transactionFeesAccount == null || Config.transactionFeesAccount.isEmpty())
         Config.transactionFeesAccount = StringTable.TRANSACT_FEE_COLLECTION;

      // grab fee collection account
      Account feeCollectionAccount = Account.getAccount(Config.transactionFeesAccount);

      // if nonexistent, create the fee collection account
      if (feeCollectionAccount == null)
         feeCollectionAccount = Account.makeAccount(Config.transactionFeesAccount, null);

      // check whether the fee is affordable
      if (feeCollectionAccount.getMoney() + fee < 0.0f)
         return fee;
      else
         return 0.0f;
   }

   /**
    * Places a transaction fee into the appropriate fee collection account.
    * <p>
    * Complexity: O(1)
    * @param fee transaction fee to be paid
    * @return whether the fee was deposited unsuccessfully; useful if a negative fee is unpaid
    */
   public static boolean depositTransactionFee(float fee) {
      // validate fee collection account's ID
      if (Config.transactionFeesAccount == null || Config.transactionFeesAccount.isEmpty())
         Config.transactionFeesAccount = StringTable.TRANSACT_FEE_COLLECTION;

      // grab fee collection account
      Account feeCollectionAccount = Account.getAccount(Config.transactionFeesAccount);

      // if nonexistent, create the fee collection account
      if (feeCollectionAccount == null)
         feeCollectionAccount = Account.makeAccount(Config.transactionFeesAccount, null);

      // if the fee is negative,
      // only paid it if it is affordable
      if (fee < 0.0f)
         if (feeCollectionAccount.getMoney() + fee < 0.0f)
            return true;

      // deposit the transaction fee
      feeCollectionAccount.addMoney(fee);
      return false;
   }
}