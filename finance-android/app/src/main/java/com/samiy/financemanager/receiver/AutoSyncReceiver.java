package com.samiy.financemanager.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.util.BackgroundSyncManager;
import com.samiy.financemanager.util.ReminderNotificationManager;

public class AutoSyncReceiver extends BroadcastReceiver {
    private static final String TAG = "AutoSyncReceiver";
    public static final String ACTION_AUTO_SYNC = "com.samiy.financemanager.ACTION_AUTO_SYNC";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;

        // Always schedule the next 10-minute sync first to maintain cadence
        BackgroundSyncManager.scheduleNextSync(context);

        String action = intent != null ? intent.getAction() : "";
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Device rebooted; 10-minute auto-sync rescheduled.");
            return;
        }

        // Only perform background sync if authorized
        GoogleAuthManager authManager = GoogleAuthManager.getInstance(context);
        if (!authManager.isAuthorized()) {
            Log.d(TAG, "User not authorized, skipping background sync.");
            return;
        }

        Log.d(TAG, "Executing 10-minute background sync with Google Sheets...");
        FinanceRepository repository = FinanceRepository.getInstance(context);
        repository.syncWithGoogleSheets(new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase database) {
                Log.d(TAG, "10-minute background sync completed successfully.");
                if (database != null) {
                    ReminderNotificationManager.checkAndSendReminders(context, database);
                }
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "10-minute background sync failed: " + error);
            }
        });
    }
}
