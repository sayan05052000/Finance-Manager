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
import com.samiy.financemanager.data.model.Transaction;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.data.model.GoalItem;

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

    /**
     * Executes internal full sync with Google Sheets on the current background thread.
     * Updates this.database, recalculates with CalculationEngine, saves to local cache,
     * and notifies listeners.
     */
    public FinanceDatabase performSyncInternal() {
        try {
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
                        }
                        if (this.database != null) {

                            mergeRecentLocalTransactions(this.database, freshDb);

                        }

                        CalculationEngine.recalculate(freshDb);
                        this.database = freshDb;
                        saveLocalCache();
                        notifySyncListeners();
                        return this.database;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in performSyncInternal", e);
        }
        return this.database;
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

    public void addTransaction(Transaction tx, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

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

        // Pre-append to local database for immediate UI responsiveness
        if (database != null) {
            if (database.transactions == null) {
                database.transactions = new java.util.ArrayList<>();
            }
            tx.id = System.currentTimeMillis();

            // Calculate running remainder for the entity (Account or Loan)
            String entity = tx.account != null && !tx.account.trim().isEmpty() ? tx.account.trim() : (tx.loans != null ? tx.loans.trim() : "");
            if (!entity.isEmpty()) {
                double prevRem = 0;
                boolean foundPrev = false;
                for (Transaction t : database.transactions) {
                    String tEntity = t.account != null && !t.account.trim().isEmpty() ? t.account.trim() : (t.loans != null ? t.loans.trim() : "");
                    if (tEntity.equalsIgnoreCase(entity)) {
                        prevRem = t.remainder;
                        foundPrev = true;
                        break;
                    }
                }
                if (!foundPrev) {
                    if (database.accounts != null) {
                        for (com.samiy.financemanager.data.model.Account a : database.accounts) {
                            if (a != null && a.name != null && a.name.trim().equalsIgnoreCase(entity)) {
                                prevRem = a.currentBalance;
                                break;
                            }
                        }
                    }
                    if (database.loans != null) {
                        for (com.samiy.financemanager.data.model.Loan l : database.loans) {
                            if (l != null && l.name != null && l.name.trim().equalsIgnoreCase(entity)) {
                                prevRem = l.outstanding;
                                break;
                            }
                        }
                    }
                }
                tx.remainder = Math.round((prevRem + netVal) * 100.0) / 100.0;
                updateEntityBalance(entity, netVal);
            }

            // If tagged to a credit card, update its local unbilled amount
            if (tx.taggedCreditCard != null && !tx.taggedCreditCard.trim().isEmpty() && database.creditCards != null) {
                for (com.samiy.financemanager.data.model.CreditCard cc : database.creditCards) {
                    if (cc != null && cc.cardName != null && cc.cardName.trim().equalsIgnoreCase(tx.taggedCreditCard.trim())) {
                        double delta = (tx.debitWithoutSign > 0) ? (tx.debitWithoutSign - tx.credit) : -tx.credit;
                        if (delta == 0 && tx.net != 0) delta = Math.abs(tx.net);
                        cc.unbilledAmount = Math.max(0, Math.round((cc.unbilledAmount + delta) * 100.0) / 100.0);
                        break;
                    }
                }
            }

            database.transactions.add(0, tx);
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        }


        executor.execute(() -> {
            try {
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
                executeHttpPost(GAS_URL, postBody.toString());

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error posting transaction to Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to sync to sheet: " + e.getMessage());
                });
            }
        });
    }

    private void updateEntityBalance(String entityName, double delta) {
        if (database == null || entityName == null || entityName.trim().isEmpty() || delta == 0) return;
        String name = entityName.trim();
        if (database.accounts != null) {
            for (com.samiy.financemanager.data.model.Account a : database.accounts) {
                if (a != null && a.name != null && a.name.trim().equalsIgnoreCase(name)) {
                    a.currentBalance = Math.round((a.currentBalance + delta) * 100.0) / 100.0;
                    return;
                }
            }
        }
        if (database.loans != null) {
            for (com.samiy.financemanager.data.model.Loan l : database.loans) {
                if (l != null && l.name != null && l.name.trim().equalsIgnoreCase(name)) {
                    l.outstanding = Math.round((l.outstanding + delta) * 100.0) / 100.0;
                    return;
                }
            }
        }
    }

    public void deleteTransaction(long transactionId, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        // Remove from local database immediately and adjust remainders
        final Transaction removedTx;
        final String cardNameForReversal;
        if (database != null && database.transactions != null) {
            Transaction toRemove = null;
            int foundIdx = -1;
            for (int i = 0; i < database.transactions.size(); i++) {
                if (database.transactions.get(i).id == transactionId) {
                    toRemove = database.transactions.get(i);
                    foundIdx = i;
                    break;
                }
            }

            if (foundIdx != -1 && toRemove != null) {
                removedTx = toRemove;
                double net = (toRemove.credit > 0 ? toRemove.credit : 0) + 
                        (toRemove.debit != 0 ? toRemove.debit : (toRemove.debitWithoutSign > 0 ? -toRemove.debitWithoutSign : 0));
                String entity = toRemove.account != null && !toRemove.account.trim().isEmpty() 
                        ? toRemove.account.trim() : (toRemove.loans != null ? toRemove.loans.trim() : "");
                if (!entity.isEmpty()) {
                    for (Transaction t : database.transactions) {
                        String tEntity = t.account != null && !t.account.trim().isEmpty() ? t.account.trim() : (t.loans != null ? t.loans.trim() : "");
                        if (tEntity.equalsIgnoreCase(entity) && t.id > toRemove.id) {
                            t.remainder = Math.round((t.remainder - net) * 100.0) / 100.0;
                        }
                    }
                    updateEntityBalance(entity, -net);
                }

                // Reverse credit card unbilled amount if tagged
                String targetCard = toRemove.taggedCreditCard != null ? toRemove.taggedCreditCard.trim() : "";
                if (targetCard.isEmpty() && "CREDIT CARD".equalsIgnoreCase(toRemove.loans != null ? toRemove.loans.trim() : "")) {
                    if (database.creditCards != null && database.creditCards.size() == 1) {
                        targetCard = database.creditCards.get(0).cardName;
                    }
                }
                cardNameForReversal = targetCard;

                if (!targetCard.isEmpty() && database.creditCards != null) {
                    double delta = (toRemove.debitWithoutSign > 0) ? (toRemove.debitWithoutSign - toRemove.credit) : -toRemove.credit;
                    if (delta == 0 && toRemove.net != 0) delta = Math.abs(toRemove.net);
                    for (com.samiy.financemanager.data.model.CreditCard cc : database.creditCards) {
                        if (cc != null && cc.cardName != null && cc.cardName.trim().equalsIgnoreCase(targetCard)) {
                            cc.unbilledAmount = Math.max(0, Math.round((cc.unbilledAmount - delta) * 100.0) / 100.0);
                            break;
                        }
                    }
                }

                database.transactions.remove(foundIdx);
            } else {
                removedTx = null;
                cardNameForReversal = "";
            }
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        } else {
            removedTx = null;
            cardNameForReversal = "";
        }

        executor.execute(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("action", "deleteTransaction");
                payload.addProperty("id", transactionId);

                if (removedTx != null) {
                    JsonObject txObj = new JsonObject();
                    txObj.addProperty("id", transactionId);
                    txObj.addProperty("date", removedTx.date);
                    txObj.addProperty("subCategory", removedTx.subCategory);
                    txObj.addProperty("loans", removedTx.loans);
                    txObj.addProperty("account", removedTx.account);
                    txObj.addProperty("debitWithoutSign", removedTx.debitWithoutSign);
                    txObj.addProperty("credit", removedTx.credit);
                    txObj.addProperty("debit", removedTx.debit);
                    txObj.addProperty("net", removedTx.net);
                    if (!cardNameForReversal.isEmpty()) {
                        txObj.addProperty("taggedCreditCard", cardNameForReversal);
                    }
                    payload.add("transaction", txObj);
                }

                String postBody = gson.toJson(payload);
                executeHttpPost(GAS_URL, postBody.toString());

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting transaction from Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to delete from sheet: " + e.getMessage());
                });
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

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updateSipStatus");
                postBody.addProperty("fundName", fundName);
                postBody.addProperty("targetMonth", targetMonth);
                postBody.addProperty("status", status);

                executeHttpPost(GAS_URL, postBody.toString());

                // Update locally
                if (database != null && database.sips != null) {
                    for (com.samiy.financemanager.data.model.SipItem sip : database.sips) {
                        if (sip.fundName != null && sip.fundName.trim().equalsIgnoreCase(fundName.trim())) {
                            if ("next".equalsIgnoreCase(targetMonth)) {
                                sip.nextStatus = status;
                            } else {
                                sip.currentStatus = status;
                            }
                            break;
                        }
                    }
                    saveLocalCache();
                }

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating SIP status", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public void updateSip(com.samiy.financemanager.data.model.SipItem updatedSip, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        executor.execute(() -> {
            try {
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

                executeHttpPost(GAS_URL, postBody.toString());

                // Update locally
                if (database != null && database.sips != null) {
                    for (int i = 0; i < database.sips.size(); i++) {
                        com.samiy.financemanager.data.model.SipItem s = database.sips.get(i);
                        if (s.fundName != null && s.fundName.trim().equalsIgnoreCase(updatedSip.fundName.trim())) {
                            database.sips.set(i, updatedSip);
                            break;
                        }
                    }
                    saveLocalCache();
                }

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating SIP", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public void addSip(com.samiy.financemanager.data.model.SipItem newSip, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        executor.execute(() -> {
            try {
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

                executeHttpPost(GAS_URL, postBody.toString());

                // Update locally
                if (database != null) {
                    if (database.sips == null) {
                        database.sips = new java.util.ArrayList<>();
                    }
                    database.sips.add(newSip);
                    saveLocalCache();
                }

                // Auto-sync from Google Sheets after addition
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error adding SIP", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
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

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "deleteSip");
                postBody.addProperty("fundName", fundName);

                executeHttpPost(GAS_URL, postBody.toString());

                // Update locally
                if (database != null && database.sips != null) {
                    for (int i = 0; i < database.sips.size(); i++) {
                        com.samiy.financemanager.data.model.SipItem s = database.sips.get(i);
                        if (s.fundName != null && s.fundName.trim().equalsIgnoreCase(fundName.trim())) {
                            database.sips.remove(i);
                            break;
                        }
                    }
                    saveLocalCache();
                }

                // Auto-sync from Google Sheets after deletion
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting SIP", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public void addMutualFundTransaction(com.samiy.financemanager.data.model.MutualFundTransaction mfTx, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        if (database != null) {
            if (database.mutualFundTransactions == null) {
                database.mutualFundTransactions = new java.util.ArrayList<>();
            }
            mfTx.id = System.currentTimeMillis();
            database.mutualFundTransactions.add(0, mfTx);

            if (database.holdings != null) {
                for (com.samiy.financemanager.data.model.Holding h : database.holdings) {
                    if (h.schemeName.equalsIgnoreCase(mfTx.schemeName) ||
                            (mfTx.schemeCode != null && !mfTx.schemeCode.isEmpty() && mfTx.schemeCode.equals(h.schemeCode))) {
                        h.units += mfTx.units;
                        h.investedValue += mfTx.amount;
                        if (mfTx.nav > 0) h.nav = mfTx.nav;
                        h.currentValue = h.units * (h.nav > 0 ? h.nav : h.avgBuyingNav);
                        h.returns = h.currentValue - h.investedValue;
                        break;
                    }
                }
            }

            CalculationEngine.recalculate(database);
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
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
                executeHttpPost(GAS_URL, postBody);

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error posting mutual fund transaction to Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to sync MF transaction: " + e.getMessage());
                });
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

        // Update in-memory database immediately
        if (database != null && database.insurance != null) {
            for (InsurancePolicy p : database.insurance) {
                if (p.policyNumber != null && p.policyNumber.equalsIgnoreCase(updatedPolicy.policyNumber)) {
                    p.lastPaid = updatedPolicy.lastPaid;
                    p.dueDate = updatedPolicy.dueDate;
                    break;
                }
            }
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updatePolicy");

                JsonObject policyObj = new JsonObject();
                policyObj.addProperty("policyNumber", updatedPolicy.policyNumber);
                policyObj.addProperty("lastPaid", updatedPolicy.lastPaid);
                policyObj.addProperty("dueDate", updatedPolicy.dueDate);

                postBody.add("policy", policyObj);

                String jsonStr = gson.toJson(postBody);
                executeHttpPost(GAS_URL, jsonStr);

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating policy in Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to update policy: " + e.getMessage());
                });
            }
        });
    }

    public void addCreditCard(com.samiy.financemanager.data.model.CreditCard newCard, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        // Uniqueness check
        if (database != null && database.creditCards != null) {
            for (com.samiy.financemanager.data.model.CreditCard c : database.creditCards) {
                if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(newCard.cardName.trim())) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("A credit card named '" + newCard.cardName + "' already exists");
                    });
                    return;
                }
            }
            // Pre-append to in-memory list
            newCard.id = database.creditCards.size() + 1;
            database.creditCards.add(newCard);
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "addCreditCard");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", newCard.cardName);
                cardObj.addProperty("dueDate", newCard.dueDate);
                cardObj.addProperty("billedAmount", newCard.billedAmount);

                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                executeHttpPost(GAS_URL, jsonStr);

                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error adding credit card to Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to add credit card: " + e.getMessage());
                });
            }
        });
    }

    public void updateCreditCard(com.samiy.financemanager.data.model.CreditCard updatedCard, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        // Update in-memory database immediately
        if (database != null && database.creditCards != null) {
            for (com.samiy.financemanager.data.model.CreditCard c : database.creditCards) {
                if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(updatedCard.cardName.trim())) {
                    c.dueDate = updatedCard.dueDate;
                    c.billedAmount = updatedCard.billedAmount;
                    if (c.billedAmount > 0) {
                        c.status = "Unpaid";
                    }
                    break;
                }
            }
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "updateCreditCard");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", updatedCard.cardName);
                cardObj.addProperty("dueDate", updatedCard.dueDate);
                cardObj.addProperty("billedAmount", updatedCard.billedAmount);

                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                executeHttpPost(GAS_URL, jsonStr);

                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating credit card in Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to update credit card: " + e.getMessage());
                });
            }
        });
    }

    public void markCreditCardBillGenerated(com.samiy.financemanager.data.model.CreditCard card, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        final double billedAmountCaptured = card.unbilledAmount;
        // Update in-memory database immediately:
        // billedAmount = unbilledAmount, unbilledAmount = 0
        if (database != null && database.creditCards != null) {
            for (com.samiy.financemanager.data.model.CreditCard c : database.creditCards) {
                if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(card.cardName.trim())) {
                    c.billedAmount = c.unbilledAmount;
                    c.unbilledAmount = 0.0;
                    if (c.billedAmount > 0) {
                        c.status = "Unpaid";
                    }
                    break;
                }
            }
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        }

        notifySyncStarted("Generating bill for " + card.cardName + " in Google Sheets...");

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "markCreditCardBillGenerated");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", card.cardName);
                cardObj.addProperty("unbilledAmount", billedAmountCaptured);
                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                executeHttpPost(GAS_URL, jsonStr);

                FinanceDatabase syncedDb = performSyncInternal();
                notifySyncFinished(card.cardName + " bill generated successfully!", false);

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error generating credit card bill in Google Sheets", e);
                notifySyncFinished("Failed to generate bill: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to generate bill: " + e.getMessage());
                });
            }
        });
    }

    public void markCreditCardPaid(com.samiy.financemanager.data.model.CreditCard cardToPay, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        final String todayStr = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US).format(new java.util.Date());
        String advancedDueDate = "";

        // Update in-memory database immediately:
        // Set billedAmount = 0, advance dueDate by 1 month, lastPaid = today, status = Paid
        if (database != null && database.creditCards != null) {
            for (com.samiy.financemanager.data.model.CreditCard c : database.creditCards) {
                if (c != null && c.cardName != null && c.cardName.trim().equalsIgnoreCase(cardToPay.cardName.trim())) {
                    c.billedAmount = 0;
                    c.dueDate = com.samiy.financemanager.util.DateTimeHelper.advanceDateByOneMonth(c.dueDate);
                    advancedDueDate = c.dueDate;
                    c.lastPaid = todayStr;
                    c.status = "Paid";
                    break;
                }
            }
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        }

        notifySyncStarted("Recording payment for " + cardToPay.cardName + " in Google Sheets...");
        final String finalNextDueDate = advancedDueDate;

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "markCreditCardPaid");

                JsonObject cardObj = new JsonObject();
                cardObj.addProperty("cardName", cardToPay.cardName);
                postBody.add("card", cardObj);

                String jsonStr = gson.toJson(postBody);
                executeHttpPost(GAS_URL, jsonStr);

                FinanceDatabase syncedDb = performSyncInternal();
                notifySyncFinished(cardToPay.cardName + " marked as paid!", false);

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error marking credit card paid in Google Sheets", e);
                notifySyncFinished("Failed to mark card paid: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to mark card paid: " + e.getMessage());
                });
            }
        });
    }

    public void addGoal(com.samiy.financemanager.data.model.GoalItem newGoal, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        // Uniqueness check & Pre-append in-memory
        if (database != null) {
            if (database.goals == null) database.goals = new java.util.ArrayList<>();
            for (com.samiy.financemanager.data.model.GoalItem existing : database.goals) {
                if (existing != null && existing.name != null && existing.name.trim().equalsIgnoreCase(newGoal.name.trim())) {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("A goal named '" + newGoal.name + "' already exists");
                    });
                    return;
                }
            }

            // Allocation <= 100% check across existing goals
            String allocError = CalculationEngine.validateGoalAllocations(newGoal, database.goals);
            if (allocError != null) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(allocError);
                });
                return;
            }

            database.goals.add(newGoal);
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        }

        notifySyncStarted("Adding goal '" + newGoal.name + "'...");

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "addGoal");

                JsonObject goalObj = new JsonObject();
                goalObj.addProperty("name", newGoal.name);
                goalObj.addProperty("targetAmount", newGoal.targetAmount);
                goalObj.addProperty("currentAmount", newGoal.currentAmount);
                goalObj.addProperty("contributionJson", newGoal.contributionJson != null ? newGoal.contributionJson : "{}");

                postBody.add("goal", goalObj);

                String jsonStr = gson.toJson(postBody);
                String resp = executeHttpPost(GAS_URL, jsonStr);
                checkJsonResponseForError(resp);

                FinanceDatabase syncedDb = performSyncInternal();
                notifySyncFinished("Goal added successfully", false);

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error adding goal in Google Sheets", e);
                notifySyncFinished("Failed to add goal: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to add goal: " + e.getMessage());
                });
            }
        });
    }

    public void updateGoal(String oldGoalName, com.samiy.financemanager.data.model.GoalItem updatedGoal, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        if (database != null) {
            if (database.goals == null) database.goals = new java.util.ArrayList<>();

            // If name changed, check uniqueness
            if (!oldGoalName.trim().equalsIgnoreCase(updatedGoal.name.trim())) {
                for (com.samiy.financemanager.data.model.GoalItem existing : database.goals) {
                    if (existing != null && existing.name != null && existing.name.trim().equalsIgnoreCase(updatedGoal.name.trim())) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError("A goal named '" + updatedGoal.name + "' already exists");
                        });
                        return;
                    }
                }
            }

            // Allocation <= 100% check across existing goals (excluding oldGoalName)
            String allocError = CalculationEngine.validateGoalAllocations(updatedGoal, database.goals);
            if (allocError != null) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(allocError);
                });
                return;
            }

            // Optimistic in-memory update
            boolean found = false;
            for (int i = 0; i < database.goals.size(); i++) {
                com.samiy.financemanager.data.model.GoalItem g = database.goals.get(i);
                if (g != null && g.name != null && g.name.trim().equalsIgnoreCase(oldGoalName.trim())) {
                    database.goals.set(i, updatedGoal);
                    found = true;
                    break;
                }
            }
            if (!found) {
                database.goals.add(updatedGoal);
            }
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        }

        notifySyncStarted("Updating goal '" + updatedGoal.name + "'...");

        executor.execute(() -> {
            try {
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
                String resp = executeHttpPost(GAS_URL, jsonStr);
                checkJsonResponseForError(resp);

                FinanceDatabase syncedDb = performSyncInternal();
                notifySyncFinished("Goal updated successfully", false);

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating goal in Google Sheets", e);
                notifySyncFinished("Failed to update goal: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to update goal: " + e.getMessage());
                });
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

        // Optimistically remove from in-memory database
        if (database != null && database.goals != null) {
            database.goals.removeIf(g -> g != null && g.name != null && g.name.trim().equalsIgnoreCase(goalName.trim()));
            CalculationEngine.recalculate(database);
            saveLocalCache();
            notifySyncListeners();
        }

        notifySyncStarted("Deleting goal '" + goalName + "'...");

        executor.execute(() -> {
            try {
                JsonObject postBody = new JsonObject();
                postBody.addProperty("action", "deleteGoal");
                postBody.addProperty("name", goalName);

                String jsonStr = gson.toJson(postBody);
                String resp = executeHttpPost(GAS_URL, jsonStr);
                checkJsonResponseForError(resp);

                FinanceDatabase syncedDb = performSyncInternal();
                notifySyncFinished("Goal removed successfully", false);

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error deleting goal in Google Sheets", e);
                notifySyncFinished("Failed to delete goal: " + e.getMessage(), true);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to delete goal: " + e.getMessage());
                });
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

        // Pre-append to in-memory database
        if (database != null) {
            if (database.insurance == null) database.insurance = new java.util.ArrayList<>();
            database.insurance.add(newPolicy);
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
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
                executeHttpPost(GAS_URL, jsonStr);

                // Auto-sync from Google Sheets after update
                FinanceDatabase syncedDb = performSyncInternal();

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(syncedDb != null ? syncedDb : database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error adding policy in Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to add policy: " + e.getMessage());
                });
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

        conn.setConnectTimeout(20000);

        conn.setReadTimeout(20000);



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

        conn.setConnectTimeout(20000);

        conn.setReadTimeout(20000);

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

