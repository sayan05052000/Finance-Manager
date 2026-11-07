package com.samiy.financemanager.util;

import com.samiy.financemanager.ui.dialog.CustomCalendarDialog;
import android.content.Context;
import android.widget.EditText;
import com.samiy.financemanager.R;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeHelper {
    private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
    private static final TimeZone IST_TZ = TimeZone.getTimeZone("Asia/Kolkata");

    /**
     * Formats any date string (ISO 8601 with timezone, or raw date string) into "dd/MM/yyyy" in Asia/Kolkata.
     */
    public static String formatDisplayDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "";
        String trimmed = dateStr.trim();

        if (trimmed.contains("T")) {
            // ISO 8601 string, e.g. "2026-11-05T18:30:00.000Z" or "2026-11-03T18:30:00.000Z"
            try {
                SimpleDateFormat isoParser;
                if (trimmed.contains(".")) {
                    isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                } else if (trimmed.endsWith("Z")) {
                    isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                } else {
                    isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                }
                isoParser.setTimeZone(UTC_TZ);
                Date date = isoParser.parse(trimmed);
                if (date != null) {
                    SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                    outFormat.setTimeZone(IST_TZ);
                    return outFormat.format(date);
                }
            } catch (Exception ignored) {
                try {
                    // Truncate to 19 characters (yyyy-MM-ddTHH:mm:ss) and parse
                    if (trimmed.length() >= 19) {
                        SimpleDateFormat isoParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                        isoParser.setTimeZone(UTC_TZ);
                        Date date = isoParser.parse(trimmed.substring(0, 19));
                        if (date != null) {
                            SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                            outFormat.setTimeZone(IST_TZ);
                            return outFormat.format(date);
                        }
                    }
                } catch (Exception ignored2) {}
            }
        }

        // If it's already in format dd/MM/yyyy or d/M/yyyy
        if (trimmed.contains("/")) {
            String[] parts = trimmed.split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim());
                    int y = Integer.parseInt(parts[2].trim());
                    return String.format(Locale.US, "%02d/%02d/%04d", d, m, y);
                } catch (Exception ignored) {}
            }
            return trimmed;
        }

        // If in yyyy-MM-dd
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length == 3) {
                if (parts[0].length() == 4) {
                    return parts[2] + "/" + parts[1] + "/" + parts[0];
                }
            }
        }

        return trimmed;
    }

    public static int extractYear(String dateStr) {
        String clean = formatDisplayDate(dateStr);
        if (clean != null && clean.contains("/")) {
            String[] parts = clean.split("/");
            if (parts.length == 3) {
                try {
                    return Integer.parseInt(parts[2].trim());
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    public static int extractMonth(String dateStr) {
        String clean = formatDisplayDate(dateStr);
        if (clean != null && clean.contains("/")) {
            String[] parts = clean.split("/");
            if (parts.length == 3) {
                try {
                    return Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    public static void showDatePickerDialog(Context context, EditText targetEditText) {
        if (context == null || targetEditText == null) return;
        String currentText = targetEditText.getText().toString().trim();
        CustomCalendarDialog dialog = new CustomCalendarDialog(context, currentText, (day, month, year, formattedDate) -> {
            targetEditText.setText(formattedDate);
        });
        dialog.show();
    }

    public static void setupDatePicker(EditText editText) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setCursorVisible(false);
        editText.setOnClickListener(v -> showDatePickerDialog(v.getContext(), editText));
    }
}
