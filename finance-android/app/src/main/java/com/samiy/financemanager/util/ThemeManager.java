package com.samiy.financemanager.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String PREF_NAME = "finmngr_theme_pref";
    private static final String KEY_THEME = "app_theme_mode";

    public static final String THEME_DARK_PASTEL = "dark_pastel";
    public static final String THEME_LIGHT_PASTEL = "light_pastel";

    private static boolean pendingThemeToast = false;
    private static boolean pendingThemeIsLight = false;

    /**
     * Applies the user's saved theme mode (defaults to Dark Pastel).
     */
    public static void applyTheme(Context context) {
        String theme = getSelectedTheme(context);
        if (THEME_LIGHT_PASTEL.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    /**
     * Returns true if current configured theme is Light Pastel.
     */
    public static boolean isLightPastel(Context context) {
        return THEME_LIGHT_PASTEL.equals(getSelectedTheme(context));
    }

    /**
     * Retrieves the stored theme preference string.
     */
    public static String getSelectedTheme(Context context) {
        if (context == null) return THEME_DARK_PASTEL;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_THEME, THEME_DARK_PASTEL);
    }

    /**
     * Toggles between Dark Pastel and Light Pastel, synchronously commits to preferences, and applies.
     * @return true if new theme is Light Pastel, false if Dark Pastel.
     */
    public static boolean toggleTheme(Context context) {
        if (context == null) return false;
        boolean wasLight = isLightPastel(context);
        String newTheme = wasLight ? THEME_DARK_PASTEL : THEME_LIGHT_PASTEL;
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_THEME, newTheme).commit();
        
        applyTheme(context);
        boolean isNowLight = !wasLight;
        pendingThemeToast = true;
        pendingThemeIsLight = isNowLight;
        return isNowLight;
    }

    public static boolean hasPendingThemeToast() {
        return pendingThemeToast;
    }

    public static boolean consumePendingThemeToast() {
        pendingThemeToast = false;
        return pendingThemeIsLight;
    }
}
