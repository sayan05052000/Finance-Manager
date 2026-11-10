package com.samiy.financemanager.util;

import com.samiy.financemanager.data.model.SipItem;

public class SipHelper {

    public static final String[] SHORT_MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    public static final String[] FULL_MONTHS = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    /**
     * Calculates the subsequent month name for standard abbreviated ("Nov" -> "Dec", "Dec" -> "Jan"),
     * full names ("November" -> "December"), or year-tagged strings ("Dec 2026" -> "Jan 2027").
     */
    public static String getNextMonthName(String month) {
        if (month == null || month.trim().isEmpty()) {
            return "Jan";
        }
        String trimmed = month.trim();
        String[] parts = trimmed.split("\\s+");
        String monthPart = parts[0];
        Integer yearPart = null;
        if (parts.length > 1) {
            try {
                yearPart = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }

        int index = -1;
        boolean isFull = false;

        for (int i = 0; i < 12; i++) {
            if (SHORT_MONTHS[i].equalsIgnoreCase(monthPart)) {
                index = i;
                isFull = false;
                break;
            }
            if (FULL_MONTHS[i].equalsIgnoreCase(monthPart)) {
                index = i;
                isFull = true;
                break;
            }
        }

        if (index == -1) {
            for (int i = 0; i < 12; i++) {
                if (monthPart.toLowerCase().startsWith(SHORT_MONTHS[i].toLowerCase())) {
                    index = i;
                    break;
                }
            }
        }

        if (index == -1) {
            return "Jan";
        }

        int nextIndex = (index + 1) % 12;
        if (yearPart != null) {
            if (nextIndex == 0) {
                yearPart += 1;
            }
            return (isFull ? FULL_MONTHS[nextIndex] : SHORT_MONTHS[nextIndex]) + " " + yearPart;
        } else {
            return isFull ? FULL_MONTHS[nextIndex] : SHORT_MONTHS[nextIndex];
        }
    }

    /**
     * Advances the SIP cycle when marked as Paid:
     * - Next Month value becomes Current Month value
     * - Month after Next Month becomes Next Month value
     * - Both current and next statuses are set to "Not Paid"
     */
    public static SipItem advanceSipCycle(SipItem source) {
        if (source == null) return null;
        SipItem copy = source.copy();
        copy.currentMonth = (source.nextMonth != null && !source.nextMonth.trim().isEmpty())
                ? source.nextMonth.trim()
                : getNextMonthName(source.currentMonth);
        copy.currentSip = source.nextSip > 0 ? source.nextSip : source.currentSip;
        copy.currentStatus = "Not Paid";

        copy.nextMonth = getNextMonthName(copy.currentMonth);
        copy.nextSip = copy.currentSip;
        copy.nextStatus = "Not Paid";
        return copy;
    }
}
