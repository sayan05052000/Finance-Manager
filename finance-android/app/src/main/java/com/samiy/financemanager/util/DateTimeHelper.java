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

    public static void setupPastDatePicker(EditText editText) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setCursorVisible(false);
        editText.setOnClickListener(v -> {
            String currentText = editText.getText().toString().trim();
            CustomCalendarDialog dialog = new CustomCalendarDialog(v.getContext(), currentText, (day, month, year, formattedDate) -> {
                editText.setText(formattedDate);
            });
            Calendar today = Calendar.getInstance();
            dialog.setMaxDate(today); // Cannot select future date!
            dialog.show();
        });
    }

    public static void setupFutureDatePicker(EditText editText) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setCursorVisible(false);
        editText.setOnClickListener(v -> {
            String currentText = editText.getText().toString().trim();
            CustomCalendarDialog dialog = new CustomCalendarDialog(v.getContext(), currentText, (day, month, year, formattedDate) -> {
                editText.setText(formattedDate);
            });
            Calendar today = Calendar.getInstance();
            dialog.setMinDate(today); // Cannot select past date!
            dialog.show();
        });
    }

    public static void setupDueDatePicker(EditText editText, String maturityDateStr) {
        if (editText == null) return;
        editText.setFocusable(false);
        editText.setClickable(true);
        editText.setCursorVisible(false);
        editText.setOnClickListener(v -> {
            String currentText = editText.getText().toString().trim();
            CustomCalendarDialog dialog = new CustomCalendarDialog(v.getContext(), currentText, (day, month, year, formattedDate) -> {
                editText.setText(formattedDate);
            });
            Calendar today = Calendar.getInstance();
            dialog.setMinDate(today); // Cannot select past date for Due Date

            if (maturityDateStr != null && !maturityDateStr.trim().isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                    Date matDate = sdf.parse(maturityDateStr.trim());
                    if (matDate != null) {
                        Calendar matCal = Calendar.getInstance();
                        matCal.setTime(matDate);
                        dialog.setMaxDate(matCal); // Due date can NEVER be greater than maturity date
                    }
                } catch (Exception ignored) {}
            }
            dialog.show();
        });
    }

    /**
     * Calculates days remaining from today (at midnight) to the specified dateStr (at midnight).
     * Returns:
     *   null if dateStr is invalid/empty,
     *   0 if due today,
     *   > 0 if due in the future,
     *   < 0 if overdue.
     */
    public static Integer getDaysRemaining(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String clean = formatDisplayDate(dateStr);
        if (clean == null || !clean.contains("/")) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date targetDate = sdf.parse(clean);
            if (targetDate == null) return null;

            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);

            Calendar targetCal = Calendar.getInstance();
            targetCal.setTime(targetDate);
            targetCal.set(Calendar.HOUR_OF_DAY, 0);
            targetCal.set(Calendar.MINUTE, 0);
            targetCal.set(Calendar.SECOND, 0);
            targetCal.set(Calendar.MILLISECOND, 0);

            long diffMillis = targetCal.getTimeInMillis() - todayCal.getTimeInMillis();
            return (int) Math.round((double) diffMillis / (1000 * 60 * 60 * 24));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Advances a "dd/MM/yyyy" date by exactly 1 month.
     * Preserves day-of-month and handles month rollover (Dec -> Jan of next year).
     */
    public static String advanceDateByOneMonth(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "";
        String clean = formatDisplayDate(dateStr);
        if (clean == null || !clean.contains("/")) return dateStr;
        try {
            String[] parts = clean.split("/");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int year = Integer.parseInt(parts[2].trim());

                month += 1;
                if (month > 12) {
                    month = 1;
                    year += 1;
                }

                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month - 1);
                int maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                if (day > maxDays) {
                    day = maxDays;
                }

                return String.format(Locale.US, "%02d/%02d/%04d", day, month, year);
            }
        } catch (Exception ignored) {}
        return dateStr;
    }
}