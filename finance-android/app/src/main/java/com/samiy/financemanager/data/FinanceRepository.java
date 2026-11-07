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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FinanceRepository {
    private static final String TAG = "FinanceRepository";
    public static final String GAS_URL = "https://script.google.com/macros/s/AKfycbxf7IJ4H37TBhZBl4Jids7zZnZ-lJYW2gfu1fRH4nwgTdHvamO2uKklCbw75dzaMXQq/exec";

    private static FinanceRepository instance;
    private final Context context;
    private final Gson gson = new GsonBuilder()
            .setLenient()
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

    public void syncWithGoogleSheets(DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        executor.execute(() -> {
            try {
                String resultJson = executeHttpGetWithRedirect(GAS_URL + "?action=all");
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
                            CalculationEngine.recalculate(freshDb);
                            this.database = freshDb;
                            saveLocalCache();
                            mainHandler.post(() -> {
                                if (callback != null) callback.onLoaded(this.database);
                            });
                            return;
                        }
                    }
                }
                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error syncing with Google Sheets", e);
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

        // Pre-append to local database for immediate UI responsiveness
        if (database != null) {
            if (database.transactions == null) {
                database.transactions = new java.util.ArrayList<>();
            }
            tx.id = System.currentTimeMillis();
            database.transactions.add(0, tx);
            CalculationEngine.recalculate(database);
            saveLocalCache();
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
                txObj.addProperty("net", "");
                txObj.addProperty("remainder", "");
                txObj.addProperty("type", tx.type);
                txObj.addProperty("category", ""); // automatically decided inside sheet
                txObj.addProperty("subCategory", tx.subCategory);
                txObj.addProperty("account", tx.account);
                txObj.addProperty("loans", tx.loans);
                txObj.addProperty("year", tx.year);
                txObj.addProperty("month", tx.month);
                payload.add("transaction", txObj);

                String postBody = gson.toJson(payload);
                executeHttpPost(GAS_URL, postBody.toString());

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error posting transaction to Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to sync to sheet: " + e.getMessage());
                });
            }
        });
    }

    public void updateTransaction(Transaction tx, DataCallback callback) {
        if (!GoogleAuthManager.getInstance(context).isAuthorized()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("You are not authorized. Log in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            });
            return;
        }

        // Update in local database immediately
        if (database != null && database.transactions != null) {
            for (int i = 0; i < database.transactions.size(); i++) {
                if (database.transactions.get(i).id == tx.id) {
                    database.transactions.set(i, tx);
                    break;
                }
            }
            CalculationEngine.recalculate(database);
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("action", "updateTransaction");
                JsonObject txObj = new JsonObject();
                txObj.addProperty("id", tx.id);
                txObj.addProperty("date", tx.date);
                if (tx.credit != 0) txObj.addProperty("credit", tx.credit);
                else txObj.addProperty("credit", "");

                if (tx.debitWithoutSign != 0) txObj.addProperty("debitWithoutSign", tx.debitWithoutSign);
                else txObj.addProperty("debitWithoutSign", "");

                if (tx.debit != 0) txObj.addProperty("debit", tx.debit);
                else if (tx.debitWithoutSign != 0) txObj.addProperty("debit", -Math.abs(tx.debitWithoutSign));
                else txObj.addProperty("debit", "");

                txObj.addProperty("type", tx.type);
                txObj.addProperty("category", tx.category != null ? tx.category : "");
                txObj.addProperty("subCategory", tx.subCategory != null ? tx.subCategory : "");
                txObj.addProperty("account", tx.account != null ? tx.account : "");
                txObj.addProperty("loans", tx.loans != null ? tx.loans : "");
                payload.add("transaction", txObj);

                String postBody = gson.toJson(payload);
                executeHttpPost(GAS_URL, postBody.toString());

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating transaction in Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to sync to sheet: " + e.getMessage());
                });
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

        // Remove from local database immediately
        if (database != null && database.transactions != null) {
            for (int i = 0; i < database.transactions.size(); i++) {
                if (database.transactions.get(i).id == transactionId) {
                    database.transactions.remove(i);
                    break;
                }
            }
            CalculationEngine.recalculate(database);
            saveLocalCache();
        }

        executor.execute(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("action", "deleteTransaction");
                payload.addProperty("id", transactionId);

                String postBody = gson.toJson(payload);
                executeHttpPost(GAS_URL, postBody.toString());

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(database);
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

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(this.database);
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

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(this.database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating SIP", e);
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

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(database);
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

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error updating policy in Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to update policy: " + e.getMessage());
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

                mainHandler.post(() -> {
                    if (callback != null) callback.onLoaded(database);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error adding policy in Google Sheets", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Failed to add policy: " + e.getMessage());
                });
            }
        });
    }

    private String executeHttpGetWithRedirect(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String redirectUrl = conn.getHeaderField("Location");
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
        }
    }

    private String executeHttpPost(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String redirectUrl = conn.getHeaderField("Location");
            if (redirectUrl != null) {
                return executeHttpPost(redirectUrl, jsonBody);
            }
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }
}
