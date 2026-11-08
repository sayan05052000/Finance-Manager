package com.samiy.financemanager.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.samiy.financemanager.receiver.AutoSyncReceiver;

public class BackgroundSyncManager {
    private static final String TAG = "BackgroundSyncManager";
    public static final long SYNC_INTERVAL_MS = 10 * 60 * 1000L; // 10 minutes

    /**
     * Schedules the next periodic background sync to occur in 10 minutes.
     */
    public static void scheduleNextSync(Context context) {
        if (context == null) return;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AutoSyncReceiver.class);
            intent.setAction(AutoSyncReceiver.ACTION_AUTO_SYNC);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(context, 1002, intent, flags);

            long triggerAt = System.currentTimeMillis() + SYNC_INTERVAL_MS;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
            Log.d(TAG, "Scheduled next 10-minute background sync at " + triggerAt);
        } catch (SecurityException se) {
            try {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    Intent intent = new Intent(context, AutoSyncReceiver.class);
                    intent.setAction(AutoSyncReceiver.ACTION_AUTO_SYNC);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        flags |= PendingIntent.FLAG_IMMUTABLE;
                    }
                    PendingIntent pi = PendingIntent.getBroadcast(context, 1002, intent, flags);
                    am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + SYNC_INTERVAL_MS, pi);
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.w(TAG, "Failed to schedule background sync: " + e.getMessage());
        }
    }

    /**
     * Cancels pending background sync alarms.
     */
    public static void cancelSync(Context context) {
        if (context == null) return;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent intent = new Intent(context, AutoSyncReceiver.class);
            intent.setAction(AutoSyncReceiver.ACTION_AUTO_SYNC);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(context, 1002, intent, flags);
            am.cancel(pi);
        } catch (Exception ignored) {}
    }
}
