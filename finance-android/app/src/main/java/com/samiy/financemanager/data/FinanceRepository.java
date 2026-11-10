package com.samiy.financemanager.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Type;
import com.google.gson.JsonDeserializationContext;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.model.CategoryItem;
import com.samiy.financemanager.data.model.CurrentCycle;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.CreditCard;
import com.samiy.financemanager.data.model.GoalItem;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.data.model.Loan;
import com.samiy.financemanager.data.model.MutualFundTransaction;
import com.samiy.financemanager.data.model.SipItem;
import com.samiy.financemanager.data.model.Transaction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FinanceRepository {
    private static final String TAG = "FinanceRepository";
    public static final String GAS_URL = "https://script.google.com/macros/s/AKfycbxf7IJ4H37TBhZBl4Jids7zZnZ-lJYW2gfu1fRH4nwgTdHvamO2uKklCbw75dzaMXQq/exec";

    private static FinanceRepository instance;
    private final Context context;
    private static final JsonDeserializer<Integer> SAFE_INT_DESERIALIZER = (jsonEl, typeOfT, ctx) -> {
        try {
            if (jsonEl.isJsonPrimitive()) {
                String s = jsonEl.getAsString().trim().replace(",", "");
                if (s.isEmpty()) return 0;
                return (int) Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return 0;
    };

    private static final JsonDeserializer<Double> SAFE_DOUBLE_DESERIALIZER = (jsonEl, typeOfT, ctx) -> {
        try {
            if (jsonEl.isJsonPrimitive()) {
                String s = jsonEl.getAsString().trim().replace(",", "");
                if (s.isEmpty()) return 0.0;
                return Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return 0.0;
    };

    private static final JsonDeserializer<Long> SAFE_LONG_DESERIALIZER = (jsonEl, typeOfT, ctx) -> {
        try {
            if (jsonEl.isJsonPrimitive()) {
                String s = jsonEl.getAsString().trim().replace(",", "");
                if (s.isEmpty()) return 0L;
                return (long) Double.parseDouble(s);
            }
        } catch (Exception ignored) {}
        return 0L;
    };

    private final Gson gson = new GsonBuilder()
            .setLenient()
            .registerTypeAdapter(int.class, SAFE_INT_DESERIALIZER)
            .registerTypeAdapter(Integer.class, SAFE_INT_DESERIALIZER)
            .registerTypeAdapter(double.class, SAFE_DOUBLE_DESERIALIZER)
            .registerTypeAdapter(Double.class, SAFE_DOUBLE_DESERIALIZER)
            .registerTypeAdapter(long.class, SAFE_LONG_DESERIALIZER)
            .registerTypeAdapter(Long.class, SAFE_LONG_DESERIALIZER)
            .registerTypeAdapter(CategoryItem.class, (JsonDeserializer<CategoryItem>) (json, typeOfT, ctx) -> {
                CategoryItem item = new CategoryItem();
                if (json.isJsonPrimitive()) {
                    item.name = json.getAsString();
                } else if (json.isJsonObject()) {
                    JsonObject obj = json.getAsJsonObject();
                    if (obj.has("name") && !obj.get("name").isJsonNull()) {
                        item.name = obj.get("name").getAsString();
                    }
                    if (obj.has("subCategories") && obj.get("subCategories").isJsonArray()) {
                        for (JsonElement e : obj.getAsJsonArray("subCategories")) {
                            if (!e.isJsonNull()) {
                                item.subCategories.add(e.getAsString());
                            }
                        }
                    }
                }
                return item;
            })
            .create();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FinanceDatabase database;

    public interface DataCallback {
        void onLoaded(FinanceDatabase db);
        void onError(String error);
    }

    public static synchronized FinanceRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FinanceRepository(context.getApplicationContext());
        }
        return instance;
    }

    private FinanceRepository(Context context) {
        this.context = context;
        loadLocalCache();
    }

    public FinanceDatabase getDatabase() {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            return null;
        }
        return database;
    }

    private void loadLocalCache() {
        try {
            // First try reading from internal cache file
            File cacheFile = new File(context.getFilesDir(), "finance_cache.json");
            if (cacheFile.exists()) {
                try (InputStream is = new FileInputStream(cacheFile);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    database = gson.fromJson(reader, FinanceDatabase.class);
                } catch (Exception e) {
                    Log.w(TAG, "Failed reading cache file, fallback to asset", e);
                }
            }

            // Fallback to assets/finance-db.json if database is null or has no accounts
            if (database == null || database.accounts == null || database.accounts.isEmpty()) {
                try (InputStream is = context.getAssets().open("finance-db.json");
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    database = gson.fromJson(reader, FinanceDatabase.class);
                }
            }

            if (database != null) {
                CalculationEngine.recalculate(database);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading local cache", e);
            database = new FinanceDatabase();
        }
    }

    private void saveLocalCache() {
        try {
            if (database == null) return;
            File cacheFile = new File(context.getFilesDir(), "finance_cache.json");
            String json = gson.toJson(database);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving local cache", e);
        }
    }

    public interface SyncListener {
        void onDatabaseSynced(FinanceDatabase db);
    }

    public interface SyncStatusListener {
        void onSyncStarted(String message);
        void onSyncFinished(String message, boolean isError);
    }

    private final java.util.List<SyncListener> syncListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<SyncStatusListener> syncStatusListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addSyncListener(SyncListener listener) {
        if (listener != null && !syncListeners.contains(listener)) {
            syncListeners.add(listener);
        }
    }

    public void removeSyncListener(SyncListener listener) {
        if (listener != null) {
            syncListeners.remove(listener);
        }
    }

    public void addSyncStatusListener(SyncStatusListener listener) {
        if (listener != null && !syncStatusListeners.contains(listener)) {
            syncStatusListeners.add(listener);
        }
    }

    public void removeSyncStatusListener(SyncStatusListener listener) {
        if (listener != null) {
            syncStatusListeners.remove(listener);
        }
    }

    public void notifySyncStarted(String message) {
        mainHandler.post(() -> {
            for (SyncStatusListener l : syncStatusListeners) {
                try {
                    l.onSyncStarted(message);
                } catch (Exception ignored) {}
            }
        });
    }

    public void notifySyncFinished(String message, boolean isError) {
        mainHandler.post(() -> {
            for (SyncStatusListener l : syncStatusListeners) {
                try {
                    l.onSyncFinished(message, isError);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifySyncListeners() {
        mainHandler.post(() -> {
            for (SyncListener l : syncListeners) {
                try {
                    l.onDatabaseSynced(this.database);
                } catch (Exception ignored) {}
            }
        });
    }

    public FinanceDatabase applyFreshDatabase(FinanceDatabase freshDb) {
        if (freshDb != null) {
            if (this.database != null) {
                if ((freshDb.budget == null || freshDb.budget.isEmpty()) && this.database.budget != null) {
                    freshDb.budget = this.database.budget;
                }
                if (this.database.currentCycle != null) {
                    if (freshDb.currentCycle == null) freshDb.currentCycle = new CurrentCycle();
                    if (this.database.currentCycle.receivables != null && (freshDb.currentCycle.receivables == null || freshDb.currentCycle.receivables.isEmpty())) {
                        freshDb.currentCycle.receivables = this.database.currentCycle.receivables;
                        freshDb.currentCycle.totalReceivables = this.database.currentCycle.totalReceivables;
                    }
                }
                mergeRecentLocalTransactions(this.database, freshDb);
            }
            CalculationEngine.recalculate(freshDb);
            this.database = freshDb;
            saveLocalCache();
            notifySyncListeners();
            return this.database;
        }
        return this.database;
    }

    /**
     * Executes internal full sync with Google Sheets on the current background thread.
     * Updates this.database, recalculates with CalculationEngine, saves to local cache,
     * and notifies listeners.
     */
    public FinanceDatabase performSyncInternal() throws Exception {
        long ts = System.currentTimeMillis();

        String nonce = UUID.randomUUID().toString().substring(0, 8);

        String url = GAS_URL + (GAS_URL.contains("?") ? "&" : "?") + "action=all&_t=" + ts + "&_nonce=" + nonce;

        String resultJson = executeHttpGetWithRedirect(url);

        if (resultJson != null && !resultJson.isEmpty()) {
            JsonObject root = JsonParser.parseString(resultJson).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject dataObj = root.getAsJsonObject("data");
                FinanceDatabase freshDb = gson.fromJson(dataObj, FinanceDatabase.class);
                if (freshDb != null) {
                    return applyFreshDatabase(freshDb);
                }
            } else if (root.has("status") && "error".equalsIgnoreCase(root.get("status").getAsString())) {
                String msg = root.has("message") ? root.get("message").getAsString() : "Error from Google Sheets";
                throw new Exception(msg);
            }
        }
        return this.database;
    }

    /**
     * Executes a POST mutation, checks for errors, extracts and applies fresh data if present,
     * performs full sync from Google Sheets to ensure 100% synchronization across all sheets,
     * and returns the synced database.
     */
    private FinanceDatabase executePostAndSync(String jsonBody) throws Exception {
        String resp = executeHttpPost(GAS_URL, jsonBody);
        checkJsonResponseForError(resp);
        if (resp != null && !resp.trim().isEmpty()) {
            try {
                JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
                if (root.has("data") && root.get("data").isJsonObject()) {
                    JsonObject dataObj = root.getAsJsonObject("data");
                    FinanceDatabase freshDb = gson.fromJson(dataObj, FinanceDatabase.class);
                    if (freshDb != null) {
                        applyFreshDatabase(freshDb);
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not extract full database directly from POST response: " + e.getMessage());
            }
        }
        return performSyncInternal();
    }

    public void syncWithGoogleSheets(DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        notifySyncStarted("Syncing with live Google Sheet...");
        executor.execute(() -> {
            try {
                FinanceDatabase fresh = performSyncInternal();
                notifySyncFinished("Google Sheet sync complete!", false);
                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(fresh != null ? fresh : this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error syncing with Google Sheets", e);
                notifySyncFinished("Sync error: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public void syncSips(DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        notifySyncStarted("Syncing SIPs with Google Sheets...");
        executor.execute(() -> {
            try {
                long ts = System.currentTimeMillis();
                String nonce = UUID.randomUUID().toString().substring(0, 8);
                String url = GAS_URL + (GAS_URL.contains("?") ? "&" : "?") + "action=sips&_t=" + ts + "&_nonce=" + nonce;
                String resultJson = executeHttpGetWithRedirect(url);
                if (resultJson != null && !resultJson.isEmpty()) {
                    JsonObject root = JsonParser.parseString(resultJson).getAsJsonObject();
                    if (root.has("data") && root.get("data").isJsonArray()) {
                        Type sipListType = new com.google.gson.reflect.TypeToken<java.util.List<SipItem>>(){}.getType();
                        java.util.List<SipItem> freshSips = gson.fromJson(root.get("data"), sipListType);
                        if (freshSips != null) {
                            if (this.database == null) {
                                loadLocalCache();
                                if (this.database == null) this.database = new FinanceDatabase();
                            }
                            this.database.sips = freshSips;
                            CalculationEngine.recalculate(this.database);
                            saveLocalCache();
                            notifySyncListeners();
                            notifySyncFinished("SIPs synced successfully!", false);
                            mainHandler.post(() -> {
                                if (callback != null) callback.onLoaded(this.database);
                            });
                            return;
                        }
                    }
                }
                FinanceDatabase fresh = performSyncInternal();
                notifySyncFinished("SIPs synced successfully!", false);
                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(fresh != null ? fresh : this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error syncing SIPs", e);
                notifySyncFinished("Failed to sync SIPs: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    /**
     * Interface representing an atomic mutation that executes sequentially in a queue.
     * Before execution, it is revalidated against the freshly synced in-memory database.
     */
    public interface QueuedMutation {
        String getActionName();

        /**
         * Revalidates this operation against the fresh database state (after any previous
         * queued mutations have completed and resynced).
         *
         * @param currentDb the latest database state in memory.
         * @return null if validation succeeds, or a user-facing error message if validation fails.
         */
        String revalidate(FinanceDatabase currentDb);

        /**
         * Prepares the mutation using the latest database state, posts to Google Apps Script,
         * triggers sheet recalculation, flushes, and performs a complete resync.
         *
         * @param currentDb the latest database state in memory.
         * @return the newly resynced FinanceDatabase.
         */
        FinanceDatabase execute(FinanceDatabase currentDb) throws Exception;

        void onSuccess(FinanceDatabase syncedDb);

        void onError(String errorMessage);
    }

    /**
     * Enqueues a mutation onto the single-thread background worker queue.
     * Ensures strict FIFO execution: each mutation waits for the preceding mutation
     * to completely execute, trigger Google Sheets calculation, and resync the entire database.
     * Before executing, the mutation revalidates against the updated database state.
     *
     * NOTE: notifySyncStarted is called inside the executor task so it fires only when
     * this mutation actually begins running — not when it is merely enqueued. This ensures
     * that if two mutations are submitted back-to-back the UI progress indicator reflects
     * whichever mutation is currently executing, and no mutation is skipped or cancelled.
     */
    private void enqueueMutation(String startMessage, QueuedMutation mutation) {
        executor.execute(() -> {
            // Notify AFTER any preceding queued task has finished, so the UI
            // accurately reflects the mutation that is actually running now.
            notifySyncStarted(startMessage);
            try {
                // Ensure in-memory database is initialized
                if (this.database == null) {
                    loadLocalCache();
                    if (this.database == null) {
                        performSyncInternal();
                    }
                }
                FinanceDatabase currentDb = this.database;

                // 1. REVALIDATION STEP against the latest database
                String validationError = mutation.revalidate(currentDb);
                if (validationError != null && !validationError.trim().isEmpty()) {
                    Log.w(TAG, "Revalidation failed for " + mutation.getActionName() + ": " + validationError);
                    notifySyncFinished(validationError, true);
                    mainHandler.post(() -> mutation.onError(validationError));
                    return;
                }

                // 2. EXECUTE MUTATION & RESYNC
                FinanceDatabase syncedDb = mutation.execute(currentDb);

                // 3. SUCCESS NOTIFICATION
                notifySyncFinished(mutation.getActionName() + " completed successfully", false);
                mainHandler.post(() -> mutation.onSuccess(syncedDb != null ? syncedDb : this.database));
            } catch (Exception e) {
                Log.e(TAG, "Error executing queued mutation: " + mutation.getActionName(), e);
                notifySyncFinished("Failed to " + mutation.getActionName() + ": " + e.getMessage(), true);
                mainHandler.post(() -> mutation.onError(e.getMessage() != null ? e.getMessage() : "Unknown error"));
            }
        });
    }

    public void updateEntityBalance(String entityName, double delta) {
        updateEntityBalance(this.database, entityName, delta);
    }

    public void updateEntityBalance(FinanceDatabase targetDb, String entityName, double delta) {
        if (targetDb == null || entityName == null || entityName.trim().isEmpty() || delta == 0) return;
        String name = entityName.trim();
        if (targetDb.accounts != null) {
            for (Account a : targetDb.accounts) {
                if (a != null && a.name != null && a.name.trim().equalsIgnoreCase(name)) {
                    a.currentBalance = Math.round((a.currentBalance + delta) * 100.0) / 100.0;
                    return;
                }
            }
        }
        if (targetDb.loans != null) {
            for (Loan l : targetDb.loans) {
                if (l != null && l.name != null && l.name.trim().equalsIgnoreCase(name)) {
                    l.outstanding = Math.round((l.outstanding + delta) * 100.0) / 100.0;
                    return;
                }
            }
        }
    }

    public void addTransaction(Transaction tx, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Adding transaction to Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "add transaction";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (tx == null) {
                    return "Transaction cannot be empty";
                }
                if (tx.date == null || tx.date.trim().isEmpty()) {
                    return "Transaction date is required";
                }
                double netVal = (tx.credit > 0 ? tx.credit : 0) +
                        (tx.debit != 0 ? tx.debit : (tx.debitWithoutSign > 0 ? -tx.debitWithoutSign : 0));
                if (netVal == 0 && tx.credit == 0 && tx.debit == 0 && tx.debitWithoutSign == 0) {
                    return "Transaction amount must be greater than zero";
                }
                if (tx.taggedCreditCard != null && !tx.taggedCreditCard.trim().isEmpty() && currentDb != null && currentDb.creditCards != null) {
                    boolean foundCard = false;
                    for (CreditCard cc : currentDb.creditCards) {
                        if (cc != null && cc.cardName != null && cc.cardName.trim().equalsIgnoreCase(tx.taggedCreditCard.trim())) {
                            foundCard = true;
                            break;
                        }
                    }
                    if (!foundCard) {
                        return "Tagged credit card '" + tx.taggedCreditCard + "' does not exist";
                    }
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                // Ensure net is accurately computed
                double netVal = (tx.credit > 0 ? tx.credit : 0) +
                        (tx.debit != 0 ? tx.debit : (tx.debitWithoutSign > 0 ? -tx.debitWithoutSign : 0));
                tx.net = netVal;

                if (tx.year == 0 || tx.month == 0) {
                    if (tx.date != null && tx.date.contains("/")) {
                        String[] p = tx.date.trim().split("/");
                        if (p.length == 3) {
                            try {
                                int d = Integer.parseInt(p[0].trim());
                                int m = Integer.parseInt(p[1].trim());
                                int y = Integer.parseInt(p[2].trim());
                                if (d < 6) {
                                    if (m == 1) { m = 12; y = y - 1; }
                                    else { m = m - 1; }
                                }
                                if (tx.year == 0) tx.year = y;
                                if (tx.month == 0) tx.month = m;
                            } catch (Exception ignored) {}
                        }
                    }
                }

                // Calculate running remainder for the entity against currentDb
                String entity = tx.account != null && !tx.account.trim().isEmpty() ? tx.account.trim() : (tx.loans != null ? tx.loans.trim() : "");
                if (!entity.isEmpty() && currentDb != null) {
                    double prevRem = 0;
                    boolean foundPrev = false;
                    if (currentDb.transactions != null) {
                        for (Transaction t : currentDb.transactions) {
                            String tEntity = t.account != null && !t.account.trim().isEmpty() ? t.account.trim() : (t.loans != null ? t.loans.trim() : "");
                            if (tEntity.equalsIgnoreCase(entity)) {
                                prevRem = t.remainder;
                                foundPrev = true;
                                break;
                            }
                        }
                    }
                    if (!foundPrev) {
                        if (currentDb.accounts != null) {
                            for (Account a : currentDb.accounts) {
                                if (a != null && a.name != null && a.name.trim().equalsIgnoreCase(entity)) {
                                    prevRem = a.currentBalance;
                                    break;
                                }
                            }
                        }
                        if (currentDb.loans != null) {
                            for (Loan l : currentDb.loans) {
                                if (l != null && l.name != null && l.name.trim().equalsIgnoreCase(entity)) {
                                    prevRem = l.outstanding;
                                    break;
                                }
                            }
                        }
                    }
                    tx.remainder = Math.round((prevRem + netVal) * 100.0) / 100.0;
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("action", "addTransaction");
                JsonObject txObj = new JsonObject();
                txObj.addProperty("date", tx.date);
                if (tx.credit != 0) txObj.addProperty("credit", tx.credit);
                else txObj.addProperty("credit", "");

                if (tx.debitWithoutSign != 0) txObj.addProperty("debitWithoutSign", tx.debitWithoutSign);
                else txObj.addProperty("debitWithoutSign", "");

                if (tx.debit != 0) txObj.addProperty("debit", tx.debit);
                else if (tx.debitWithoutSign != 0) txObj.addProperty("debit", -Math.abs(tx.debitWithoutSign));
                else txObj.addProperty("debit", "");

                if (tx.net != 0) txObj.addProperty("net", tx.net);
                else txObj.addProperty("net", "");

                if (tx.remainder != 0) txObj.addProperty("remainder", tx.remainder);
                else txObj.addProperty("remainder", "");

                txObj.addProperty("type", tx.type);
                txObj.addProperty("category", ""); // automatically decided inside sheet
                txObj.addProperty("subCategory", tx.subCategory);
                txObj.addProperty("account", tx.account);
                txObj.addProperty("loans", tx.loans);
                txObj.addProperty("year", tx.year);
                txObj.addProperty("month", tx.month);
                if (tx.taggedCreditCard != null && !tx.taggedCreditCard.trim().isEmpty()) {
                    txObj.addProperty("taggedCreditCard", tx.taggedCreditCard.trim());
                }
                payload.add("transaction", txObj);

                String postBody = gson.toJson(payload);
                return executePostAndSync(postBody);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void deleteTransaction(long transactionId, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Deleting transaction from Google Sheets...", new QueuedMutation() {
            private Transaction toRemove = null;
            private String cardNameForReversal = "";

            @Override
            public String getActionName() {
                return "delete transaction";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (currentDb == null || currentDb.transactions == null) {
                    return "No transactions found in current database";
                }
                toRemove = null;
                for (Transaction t : currentDb.transactions) {
                    if (t.id == transactionId) {
                        toRemove = t;
                        break;
                    }
                }
                if (toRemove == null) {
                    return "Transaction #" + transactionId + " does not exist or was already deleted";
                }

                String targetCard = toRemove.taggedCreditCard != null ? toRemove.taggedCreditCard.trim() : "";
                if (targetCard.isEmpty() && "CREDIT CARD".equalsIgnoreCase(toRemove.loans != null ? toRemove.loans.trim() : "")) {
                    if (currentDb.creditCards != null && currentDb.creditCards.size() == 1) {
                        targetCard = currentDb.creditCards.get(0).cardName;
                    }
                }
                cardNameForReversal = targetCard;
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject payload = new JsonObject();
                payload.addProperty("action", "deleteTransaction");
                payload.addProperty("id", transactionId);

                if (toRemove != null) {
                    JsonObject txObj = new JsonObject();
                    txObj.addProperty("id", transactionId);
                    txObj.addProperty("date", toRemove.date);
                    txObj.addProperty("subCategory", toRemove.subCategory);
                    txObj.addProperty("loans", toRemove.loans);
                    txObj.addProperty("account", toRemove.account);
                    txObj.addProperty("debitWithoutSign", toRemove.debitWithoutSign);
                    txObj.addProperty("credit", toRemove.credit);
                    txObj.addProperty("debit", toRemove.debit);
                    txObj.addProperty("net", toRemove.net);
                    if (!cardNameForReversal.isEmpty()) {
                        txObj.addProperty("taggedCreditCard", cardNameForReversal);
                    }
                    payload.add("transaction", txObj);
                }

                String postBody = gson.toJson(payload);
                return executePostAndSync(postBody);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void updateSipStatus(String fundName, String targetMonth, String status, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Updating SIP status in Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "update SIP status";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (fundName == null || fundName.trim().isEmpty()) {
                    return "Fund name cannot be empty";
                }
                if (currentDb == null || currentDb.sips == null) {
                    return "No SIPs found in database";
                }
                SipItem found = null;
                for (SipItem s : currentDb.sips) {
                    if (s != null && s.fundName != null && s.fundName.trim().equalsIgnoreCase(fundName.trim())) {
                        found = s;
                        break;
                    }
                }
                if (found == null) {
                    return "SIP for fund '" + fundName + "' not found in current database";
                }
                boolean isNext = "next".equalsIgnoreCase(targetMonth);
                String currentStatus = isNext ? found.nextStatus : found.currentStatus;
                if (status != null && status.equalsIgnoreCase(currentStatus)) {
                    return "SIP for " + fundName + " (" + (isNext ? "next" : "current") + " month) is already " + status;
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updateSipStatus");
                postBody.addProperty("fundName", fundName);
                postBody.addProperty("targetMonth", targetMonth);
                postBody.addProperty("status", status);
                return executePostAndSync(postBody.toString());
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void updateSip(SipItem updatedSip, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Updating SIP details in Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "update SIP";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (updatedSip == null || updatedSip.fundName == null || updatedSip.fundName.trim().isEmpty()) {
                    return "SIP fund name is required";
                }
                if (currentDb == null || currentDb.sips == null) {
                    return "No SIPs found in database";
                }
                boolean found = false;
                for (SipItem s : currentDb.sips) {
                    if (s != null && s.fundName != null && s.fundName.trim().equalsIgnoreCase(updatedSip.fundName.trim())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return "SIP for '" + updatedSip.fundName + "' does not exist";
                }
                if (updatedSip.currentSip < 0 || updatedSip.nextSip < 0) {
                    return "SIP amount cannot be negative";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updateSip");

                JsonObject sipObj = new JsonObject();
                sipObj.addProperty("fundName", updatedSip.fundName);
                sipObj.addProperty("currentSip", updatedSip.currentSip);
                sipObj.addProperty("currentMonth", updatedSip.currentMonth);
                sipObj.addProperty("currentStatus", updatedSip.currentStatus);
                sipObj.addProperty("nextSip", updatedSip.nextSip);
                sipObj.addProperty("nextMonth", updatedSip.nextMonth);
                sipObj.addProperty("nextStatus", updatedSip.nextStatus);
                sipObj.addProperty("bankAccount", updatedSip.bankAccount);
                sipObj.addProperty("platform", updatedSip.platform);
                postBody.add("sip", sipObj);

                return executePostAndSync(postBody.toString());
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void addSip(SipItem newSip, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Adding SIP to Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "add SIP";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (newSip == null || newSip.fundName == null || newSip.fundName.trim().isEmpty()) {
                    return "SIP fund name is required";
                }
                if (currentDb != null && currentDb.sips != null) {
                    for (SipItem s : currentDb.sips) {
                        if (s != null && s.fundName != null && s.fundName.trim().equalsIgnoreCase(newSip.fundName.trim())) {
                            return "A SIP for fund '" + newSip.fundName + "' already exists";
                        }
                    }
                }
                if (newSip.currentSip < 0) {
                    return "SIP amount cannot be negative";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "addSip");

                JsonObject sipObj = new JsonObject();
                sipObj.addProperty("fundName", newSip.fundName);
                sipObj.addProperty("role", newSip.role);
                sipObj.addProperty("currentSip", newSip.currentSip);
                sipObj.addProperty("currentMonth", newSip.currentMonth);
                sipObj.addProperty("currentStatus", newSip.currentStatus);
                sipObj.addProperty("nextSip", newSip.nextSip);
                sipObj.addProperty("nextMonth", newSip.nextMonth);
                sipObj.addProperty("nextStatus", newSip.nextStatus);
                sipObj.addProperty("bankAccount", newSip.bankAccount);
                sipObj.addProperty("platform", newSip.platform);
                postBody.add("sip", sipObj);

                return executePostAndSync(postBody.toString());
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void deleteSip(String fundName, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Deleting SIP from Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "delete SIP";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (fundName == null || fundName.trim().isEmpty()) {
                    return "SIP fund name is required";
                }
                if (currentDb == null || currentDb.sips == null) {
                    return "No SIPs found in database";
                }
                boolean found = false;
                for (SipItem s : currentDb.sips) {
                    if (s != null && s.fundName != null && s.fundName.trim().equalsIgnoreCase(fundName.trim())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return "SIP for '" + fundName + "' does not exist or was already deleted";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "deleteSip");
                postBody.addProperty("fundName", fundName);
                return executePostAndSync(postBody.toString());
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void addMutualFundTransaction(MutualFundTransaction mfTx, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Recording mutual fund transaction in Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "record mutual fund transaction";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (mfTx == null || mfTx.schemeName == null || mfTx.schemeName.trim().isEmpty()) {
                    return "Mutual fund scheme name is required";
                }
                if (mfTx.units == 0) {
                    return "Units must not be zero";
                }
                if (mfTx.date == null || mfTx.date.trim().isEmpty()) {
                    return "Transaction date is required";
                }
                if (mfTx.units < 0 && currentDb != null && currentDb.holdings != null) {
                    for (Holding h : currentDb.holdings) {
                        if (h != null && (h.schemeName.equalsIgnoreCase(mfTx.schemeName) ||
                                (mfTx.schemeCode != null && !mfTx.schemeCode.isEmpty() && mfTx.schemeCode.equals(h.schemeCode)))) {
                            if (h.units < Math.abs(mfTx.units) - 0.0001) {
                                return "Insufficient units to redeem: holding has " + h.units + " units, cannot redeem " + Math.abs(mfTx.units);
                            }
                            break;
                        }
                    }
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject payload = new JsonObject();
                payload.addProperty("action", "addMutualFundTransaction");
                JsonObject txObj = new JsonObject();
                txObj.addProperty("schemeName", mfTx.schemeName != null ? mfTx.schemeName : "");
                txObj.addProperty("schemeId", mfTx.schemeId != null ? mfTx.schemeId : "");
                txObj.addProperty("schemeCode", mfTx.schemeCode != null ? mfTx.schemeCode : "");
                txObj.addProperty("date", mfTx.date);
                txObj.addProperty("fundCategory", mfTx.fundCategory != null ? mfTx.fundCategory : "Equity");
                txObj.addProperty("fundSubCategory", mfTx.fundSubCategory != null ? mfTx.fundSubCategory : "Small Cap");
                txObj.addProperty("platform", mfTx.platform != null ? mfTx.platform : "Groww");
                txObj.addProperty("subCategory", mfTx.subCategory != null ? mfTx.subCategory : "Mutual Fund");
                txObj.addProperty("folioNumber", mfTx.folioNumber != null ? mfTx.folioNumber : "");
                boolean isRedeem = mfTx.units < 0;
                txObj.addProperty("isRedeem", isRedeem);
                if (isRedeem) {
                    txObj.addProperty("amount", "");
                    txObj.addProperty("actualInvested", "");
                } else {
                    if (mfTx.amount != 0) {
                        txObj.addProperty("amount", mfTx.amount);
                    } else {
                        txObj.addProperty("amount", "");
                    }
                    if (mfTx.actualInvested != 0) {
                        txObj.addProperty("actualInvested", mfTx.actualInvested);
                    } else if (mfTx.amount != 0) {
                        txObj.addProperty("actualInvested", mfTx.amount);
                    } else {
                        txObj.addProperty("actualInvested", "");
                    }
                }
                txObj.addProperty("units", mfTx.units);
                txObj.addProperty("nav", mfTx.nav);
                txObj.addProperty("investedRedeemedAmount", "");
                payload.add("transaction", txObj);

                String postBody = gson.toJson(payload);
                return executePostAndSync(postBody);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void updatePolicy(InsurancePolicy updatedPolicy, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Updating policy in Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "update policy";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (updatedPolicy == null || updatedPolicy.policyNumber == null || updatedPolicy.policyNumber.trim().isEmpty()) {
                    return "Policy number is required";
                }
                if (currentDb == null || currentDb.insurance == null) {
                    return "No policies found in database";
                }
                boolean found = false;
                for (InsurancePolicy p : currentDb.insurance) {
                    if (p != null && p.policyNumber != null && p.policyNumber.equalsIgnoreCase(updatedPolicy.policyNumber.trim())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return "Policy '" + updatedPolicy.policyNumber + "' does not exist in database";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updatePolicy");

                JsonObject policyObj = new JsonObject();
                policyObj.addProperty("policyNumber", updatedPolicy.policyNumber);
                policyObj.addProperty("lastPaid", updatedPolicy.lastPaid);
                policyObj.addProperty("dueDate", updatedPolicy.dueDate);

                postBody.add("policy", policyObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void addPolicy(InsurancePolicy newPolicy, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Adding policy to Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "add policy";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (newPolicy == null || newPolicy.policyNumber == null || newPolicy.policyNumber.trim().isEmpty()) {
                    return "Policy number is required";
                }
                if (currentDb != null && currentDb.insurance != null) {
                    for (InsurancePolicy p : currentDb.insurance) {
                        if (p != null && p.policyNumber != null && p.policyNumber.equalsIgnoreCase(newPolicy.policyNumber.trim())) {
                            return "A policy with number '" + newPolicy.policyNumber + "' already exists";
                        }
                    }
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "addPolicy");

                JsonObject policyObj = new JsonObject();
                policyObj.addProperty("policyNumber", newPolicy.policyNumber);
                policyObj.addProperty("birthDate", newPolicy.birthDate != null ? newPolicy.birthDate : "05/05/2000");
                policyObj.addProperty("premiumAmount", newPolicy.premiumAmount);
                policyObj.addProperty("lastPaid", newPolicy.lastPaid);
                policyObj.addProperty("dueDate", newPolicy.dueDate);
                policyObj.addProperty("lastDate", newPolicy.lastDate != null ? newPolicy.lastDate : "");
                policyObj.addProperty("installmentsRemaining", newPolicy.installmentsRemaining != null ? newPolicy.installmentsRemaining : "");
                policyObj.addProperty("subCategory", newPolicy.subCategory != null ? newPolicy.subCategory : "LIC Policies");
                policyObj.addProperty("category", newPolicy.category != null ? newPolicy.category : "Savings");
                policyObj.addProperty("id", newPolicy.id != null ? newPolicy.id : "");
                policyObj.addProperty("password", newPolicy.password != null ? newPolicy.password : "");
                policyObj.addProperty("sumAssured", newPolicy.sumAssured);
                policyObj.addProperty("notes", newPolicy.notes != null ? newPolicy.notes : "");

                postBody.add("policy", policyObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void addCreditCard(CreditCard newCard, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Adding credit card to Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "add credit card";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (newCard == null || newCard.cardName == null || newCard.cardName.trim().isEmpty()) {
                    return "Credit card name is required";
                }
                if (currentDb != null && currentDb.creditCards != null) {
                    for (CreditCard c : currentDb.creditCards) {
                        if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(newCard.cardName.trim())) {
                            return "A credit card named '" + newCard.cardName + "' already exists";
                        }
                    }
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "addCreditCard");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", newCard.cardName);
                cardObj.addProperty("dueDate", newCard.dueDate);
                cardObj.addProperty("billedAmount", newCard.billedAmount);

                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void updateCreditCard(CreditCard updatedCard, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Updating credit card in Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "update credit card";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (updatedCard == null || updatedCard.cardName == null || updatedCard.cardName.trim().isEmpty()) {
                    return "Credit card name is required";
                }
                if (currentDb == null || currentDb.creditCards == null) {
                    return "No credit cards found in database";
                }
                boolean found = false;
                for (CreditCard c : currentDb.creditCards) {
                    if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(updatedCard.cardName.trim())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return "Credit card '" + updatedCard.cardName + "' does not exist in database";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updateCreditCard");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", updatedCard.cardName);
                cardObj.addProperty("dueDate", updatedCard.dueDate);
                cardObj.addProperty("billedAmount", updatedCard.billedAmount);

                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void markCreditCardBillGenerated(CreditCard card, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Generating bill for " + (card != null ? card.cardName : "") + " in Google Sheets...", new QueuedMutation() {
            private double unbilledToBill = 0.0;

            @Override
            public String getActionName() {
                return "generate credit card bill";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (card == null || card.cardName == null || card.cardName.trim().isEmpty()) {
                    return "Credit card name is required";
                }
                if (currentDb == null || currentDb.creditCards == null) {
                    return "No credit cards found in database";
                }
                CreditCard found = null;
                for (CreditCard c : currentDb.creditCards) {
                    if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(card.cardName.trim())) {
                        found = c;
                        break;
                    }
                }
                if (found == null) {
                    return "Credit card '" + card.cardName + "' does not exist in database";
                }
                if (found.unbilledAmount <= 0) {
                    return "Credit card '" + card.cardName + "' has no unbilled amount (current unbilled: 0)";
                }
                unbilledToBill = found.unbilledAmount;
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "markCreditCardBillGenerated");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", card.cardName);
                cardObj.addProperty("unbilledAmount", unbilledToBill);
                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void markCreditCardPaid(CreditCard cardToPay, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Recording payment for " + (cardToPay != null ? cardToPay.cardName : "") + " in Google Sheets...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "mark credit card paid";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (cardToPay == null || cardToPay.cardName == null || cardToPay.cardName.trim().isEmpty()) {
                    return "Credit card name is required";
                }
                if (currentDb == null || currentDb.creditCards == null) {
                    return "No credit cards found in database";
                }
                CreditCard found = null;
                for (CreditCard c : currentDb.creditCards) {
                    if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(cardToPay.cardName.trim())) {
                        found = c;
                        break;
                    }
                }
                if (found == null) {
                    return "Credit card '" + cardToPay.cardName + "' does not exist in database";
                }
                if (found.billedAmount <= 0) {
                    return "Credit card '" + cardToPay.cardName + "' is already paid (billed amount is 0)";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "markCreditCardPaid");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", cardToPay.cardName);
                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void addGoal(GoalItem newGoal, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Adding goal '" + (newGoal != null ? newGoal.name : "") + "'...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "add goal";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (newGoal == null || newGoal.name == null || newGoal.name.trim().isEmpty()) {
                    return "Goal name is required";
                }
                if (currentDb != null && currentDb.goals != null) {
                    for (GoalItem existing : currentDb.goals) {
                        if (existing != null && existing.name != null && existing.name.trim().equalsIgnoreCase(newGoal.name.trim())) {
                            return "A goal named '" + newGoal.name + "' already exists";
                        }
                    }
                    String allocError = CalculationEngine.validateGoalAllocations(newGoal, currentDb.goals);
                    if (allocError != null) {
                        return allocError;
                    }
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "addGoal");

                JsonObject goalObj = new JsonObject();
                goalObj.addProperty("name", newGoal.name);
                goalObj.addProperty("targetAmount", newGoal.targetAmount);
                goalObj.addProperty("currentAmount", newGoal.currentAmount);
                goalObj.addProperty("contributionJson", newGoal.contributionJson != null ? newGoal.contributionJson : "{}");

                postBody.add("goal", goalObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void updateGoal(String oldGoalName, GoalItem updatedGoal, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Updating goal '" + (updatedGoal != null ? updatedGoal.name : "") + "'...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "update goal";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (updatedGoal == null || updatedGoal.name == null || updatedGoal.name.trim().isEmpty()) {
                    return "Goal name is required";
                }
                if (oldGoalName == null || oldGoalName.trim().isEmpty()) {
                    return "Original goal name is required";
                }
                if (currentDb == null || currentDb.goals == null) {
                    return "No goals found in database";
                }
                boolean foundOld = false;
                java.util.List<GoalItem> otherGoals = new java.util.ArrayList<>();
                for (GoalItem existing : currentDb.goals) {
                    if (existing != null && existing.name != null) {
                        if (existing.name.trim().equalsIgnoreCase(oldGoalName.trim())) {
                            foundOld = true;
                        } else {
                            otherGoals.add(existing);
                            if (existing.name.trim().equalsIgnoreCase(updatedGoal.name.trim())) {
                                return "A goal named '" + updatedGoal.name + "' already exists";
                            }
                        }
                    }
                }
                if (!foundOld) {
                    return "Goal '" + oldGoalName + "' does not exist in database";
                }
                String allocError = CalculationEngine.validateGoalAllocations(updatedGoal, otherGoals);
                if (allocError != null) {
                    return allocError;
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updateGoal");
                postBody.addProperty("oldName", oldGoalName);

                JsonObject goalObj = new JsonObject();
                goalObj.addProperty("name", updatedGoal.name);
                goalObj.addProperty("targetAmount", updatedGoal.targetAmount);
                goalObj.addProperty("currentAmount", updatedGoal.currentAmount);
                goalObj.addProperty("contributionJson", updatedGoal.contributionJson != null ? updatedGoal.contributionJson : "{}");

                postBody.add("goal", goalObj);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    public void deleteGoal(String goalName, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        enqueueMutation("Deleting goal '" + goalName + "'...", new QueuedMutation() {
            @Override
            public String getActionName() {
                return "delete goal";
            }

            @Override
            public String revalidate(FinanceDatabase currentDb) {
                if (goalName == null || goalName.trim().isEmpty()) {
                    return "Goal name is required";
                }
                if (currentDb == null || currentDb.goals == null) {
                    return "No goals found in database";
                }
                boolean found = false;
                for (GoalItem g : currentDb.goals) {
                    if (g != null && g.name != null && g.name.trim().equalsIgnoreCase(goalName.trim())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return "Goal '" + goalName + "' does not exist or was already deleted";
                }
                return null;
            }

            @Override
            public FinanceDatabase execute(FinanceDatabase currentDb) throws Exception {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "deleteGoal");
                postBody.addProperty("name", goalName);

                String jsonStr = gson.toJson(postBody);
                return executePostAndSync(jsonStr);
            }

            @Override
            public void onSuccess(FinanceDatabase syncedDb) {
                if (callback != null) callback.onLoaded(syncedDb);
            }

            @Override
            public void onError(String errorMessage) {
                if (callback != null) callback.onError(errorMessage);
            }
        });
    }

    private void checkJsonResponseForError(String jsonStr) throws Exception {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return;
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(jsonStr);
            if (el != null && el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("status") && "error".equalsIgnoreCase(obj.get("status").getAsString())) {
                    String msg = obj.has("message") ? obj.get("message").getAsString() : "Error from Google Sheets";
                    throw new Exception(msg);
                }
            }
        } catch (com.google.gson.JsonSyntaxException ignored) {}
    }

    private void mergeRecentLocalTransactions(FinanceDatabase oldDb, FinanceDatabase newDb) {
        if (oldDb == null || oldDb.transactions == null || newDb == null || newDb.transactions == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Transaction oldTx : oldDb.transactions) {
            if (oldTx.id > 1000000000000L && (now - oldTx.id) < 180000) {
                boolean foundInNew = false;
                for (Transaction newTx : newDb.transactions) {
                    if (isSameTransaction(oldTx, newTx)) {
                        foundInNew = true;
                        if (newTx.net == 0 && oldTx.net != 0) {
                            newTx.net = oldTx.net;
                        }
                        if (newTx.remainder == 0 && oldTx.remainder != 0) {
                            newTx.remainder = oldTx.remainder;
                        }
                        if ((newTx.taggedCreditCard == null || newTx.taggedCreditCard.trim().isEmpty()) &&
                                oldTx.taggedCreditCard != null && !oldTx.taggedCreditCard.trim().isEmpty()) {
                            newTx.taggedCreditCard = oldTx.taggedCreditCard;
                        }
                        break;
                    }
                }
                if (!foundInNew) {
                    newDb.transactions.add(0, oldTx);
                }
            }
        }
    }

    private boolean isSameTransaction(Transaction a, Transaction b) {
        if (a == null || b == null) return false;
        boolean sameDate = isSameString(a.date, b.date);
        boolean sameCredit = Math.abs(a.credit - b.credit) < 0.01;
        double aDebit = a.debit != 0 ? a.debit : (a.debitWithoutSign > 0 ? -a.debitWithoutSign : 0);
        double bDebit = b.debit != 0 ? b.debit : (b.debitWithoutSign > 0 ? -b.debitWithoutSign : 0);
        boolean sameDebit = Math.abs(aDebit - bDebit) < 0.01;
        boolean sameSubCat = isSameString(a.subCategory, b.subCategory);
        boolean sameAccount = isSameString(a.account, b.account);
        boolean sameLoans = isSameString(a.loans, b.loans);
        return sameDate && sameCredit && sameDebit && sameSubCat && sameAccount && sameLoans;
    }

    private boolean isSameString(String s1, String s2) {
        String str1 = s1 != null ? s1.trim() : "";
        String str2 = s2 != null ? s2.trim() : "";
        return str1.equalsIgnoreCase(str2);
    }

    private String executeHttpGetWithRedirect(String urlStr) throws Exception {

        URL url = new URL(urlStr);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");

        conn.setInstanceFollowRedirects(true);

        conn.setUseCaches(false);

        conn.setDefaultUseCaches(false);

        conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");

        conn.setRequestProperty("Pragma", "no-cache");

        conn.setRequestProperty("Expires", "0");

        conn.setConnectTimeout(30000);

        conn.setReadTimeout(90000);



        int status = conn.getResponseCode();

        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {

            String redirectUrl = conn.getHeaderField("Location");

            conn.disconnect();

            if (redirectUrl != null) {

                return executeHttpGetWithRedirect(redirectUrl);

            }

        }



        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();

            String inputLine;

            while ((inputLine = in.readLine()) != null) {

                response.append(inputLine);

            }

            return response.toString();

        } finally {

            conn.disconnect();

        }

    }



    private String executeHttpPost(String urlStr, String jsonBody) throws Exception {

        URL url = new URL(urlStr);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");

        conn.setDoOutput(true);

        conn.setUseCaches(false);

        conn.setDefaultUseCaches(false);

        conn.setConnectTimeout(30000);

        conn.setReadTimeout(90000);

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");

        conn.setRequestProperty("Pragma", "no-cache");

        conn.setRequestProperty("Expires", "0");



        try (OutputStream os = conn.getOutputStream()) {

            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);

            os.write(input, 0, input.length);

        }



        int status = conn.getResponseCode();

        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {

            String redirectUrl = conn.getHeaderField("Location");

            conn.disconnect();

            if (redirectUrl != null) {

                String resp = executeHttpGetWithRedirect(redirectUrl);

                checkJsonResponseForError(resp);

                return resp;

            }

        }



        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();

            String inputLine;

            while ((inputLine = in.readLine()) != null) {

                response.append(inputLine);

            }

            String resp = response.toString();

            checkJsonResponseForError(resp);

            return resp;

        } finally {

            conn.disconnect();

        }

    }

}

