package com.samiy.financemanager.util;

import android.content.Context;
import android.widget.Toast;

import com.samiy.financemanager.MainActivity;

public class FinMngrToast {

    public enum Type {
        UPDATING,
        SUCCESS,
        ERROR,
        INFO
    }

    public static void show(Context context, String message) {
        show(context, message, Type.INFO);
    }

    public static void show(Context context, String message, int duration) {
        show(context, message, Type.INFO);
    }

    public static void showUpdating(Context context, String message) {
        show(context, message, Type.UPDATING);
    }

    public static void showSuccess(Context context, String message) {
        show(context, message, Type.SUCCESS);
    }

    public static void showError(Context context, String message) {
        show(context, message, Type.ERROR);
    }

    public static void showInfo(Context context, String message) {
        show(context, message, Type.INFO);
    }

    public static void show(Context context, String message, Type type) {
        if (message == null || message.trim().isEmpty()) return;

        MainActivity mainActivity = MainActivity.getInstance();
        if (mainActivity != null && !mainActivity.isFinishing() && !mainActivity.isDestroyed()) {
            mainActivity.showInAppToast(message, type);
            return;
        }

        if (context != null) {
            int duration = (type == Type.ERROR) ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
            Toast.makeText(context.getApplicationContext(), message, duration).show();
        }
    }
}
