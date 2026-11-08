package com.samiy.financemanager.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.samiy.financemanager.MainActivity;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.model.CreditCard;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderNotificationManager {
    private static final String TAG = "ReminderNotifMgr";
    public static final String CHANNEL_ID = "due_date_reminders_channel";
    private static final String PREF_NAME = "due_date_notifications_tracker";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Due Date Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Daily reminders for upcoming LIC/Tata AIA policies and Credit Card bills");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Checks all policies and credit cards. If due within upcoming 14 days,
     * sends exactly 1 notification per item per day.
     */
    public static void checkAndSendReminders(Context context, FinanceDatabase db) {
        if (context == null || db == null) return;

        createNotificationChannel(context);

        // Check permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission not granted, skipping notification posting.");
                return;
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // 1. Check LIC & Tata AIA Policies
        if (db.insurance != null) {
            for (InsurancePolicy p : db.insurance) {
                if (p == null || !p.isLicOrTataAia()) continue;
                if (EditPolicyBottomSheet.isPolicyFullyPaidOrMatured(p)) continue;

                Integer days = p.getDaysRemaining();
                if (days != null && days >= 0 && days <= 14) {
                    String itemKey = "policy_" + (p.policyNumber != null ? p.policyNumber.trim() : "unknown");
                    String lastNotified = prefs.getString("last_notified_" + itemKey, "");

                    if (!todayStr.equals(lastNotified)) {
                        String daysText;
                        if (days == 0) {
                            daysText = "🚨 Due TODAY!";
                        } else if (days == 1) {
                            daysText = "⚠️ Due TOMORROW (1 day left)";
                        } else {
                            daysText = "⏳ Due in " + days + " days (" + p.dueDate + ")";
                        }

                        String amountStr = currencyFormat.format(p.premiumAmount).replace("INR", "₹").replace(".00", "");
                        String title = "🛡️ Policy Premium Reminder: " + (p.subCategory != null ? p.subCategory : "Policy");
                        String body = "Policy " + p.policyNumber + " • " + amountStr + " • " + daysText;

                        sendNotification(context, notificationManager, itemKey.hashCode(), title, body);
                        prefs.edit().putString("last_notified_" + itemKey, todayStr).apply();
                    }
                }
            }
        }

        // 2. Check Credit Cards
        if (db.creditCards != null) {
            for (CreditCard c : db.creditCards) {
                if (c == null || c.billedAmount <= 0) continue;

                Integer days = c.getDaysRemaining();
                if (days != null && days >= 0 && days <= 14) {
                    String itemKey = "card_" + (c.cardName != null ? c.cardName.trim() : "unknown");
                    String lastNotified = prefs.getString("last_notified_" + itemKey, "");

                    if (!todayStr.equals(lastNotified)) {
                        String daysText;
                        if (days == 0) {
                            daysText = "🚨 Due TODAY!";
                        } else if (days == 1) {
                            daysText = "⚠️ Due TOMORROW (1 day left)";
                        } else {
                            daysText = "⏳ Due in " + days + " days (" + c.dueDate + ")";
                        }

                        String amountStr = currencyFormat.format(c.billedAmount).replace("INR", "₹").replace(".00", "");
                        String title = "💳 Credit Card Bill Due: " + c.cardName;
                        String body = amountStr + " bill • " + daysText;

                        sendNotification(context, notificationManager, itemKey.hashCode(), title, body);
                        prefs.edit().putString("last_notified_" + itemKey, todayStr).apply();
                    }
                }
            }
        }
    }

    private static void sendNotification(Context context, NotificationManagerCompat manager, int notificationId, String title, String body) {
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            manager.notify(notificationId, builder.build());
        } catch (SecurityException se) {
            Log.w(TAG, "SecurityException while posting notification: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error posting notification", e);
        }
    }
}
