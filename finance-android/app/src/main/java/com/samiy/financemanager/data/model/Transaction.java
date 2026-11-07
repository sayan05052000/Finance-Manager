package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import com.samiy.financemanager.util.DateTimeHelper;
import java.io.Serializable;
import java.util.Locale;

public class Transaction implements Serializable {
    @SerializedName("id")
    public long id;

    @SerializedName("date")
    public String date = "";

    @SerializedName("credit")
    public double credit;

    @SerializedName("debit")
    public double debit;

    @SerializedName("net")
    public double net;

    @SerializedName("remainder")
    public double remainder;

    @SerializedName("subCategory")
    public String subCategory = "";

    @SerializedName("type")
    public String type = "Expense";

    @SerializedName("loans")
    public String loans = "";

    @SerializedName("account")
    public String account = "";

    @SerializedName("category")
    public String category = "";

    @SerializedName("year")
    public int year;

    @SerializedName("month")
    public int month;

    @SerializedName("debitWithoutSign")
    public double debitWithoutSign;

    @SerializedName("description")
    public String description = "";

    public Transaction() {}

    public String getDisplaySource() {
        if (loans != null && !loans.trim().isEmpty()) {
            return loans;
        }
        if (account != null && !account.trim().isEmpty()) {
            return account;
        }
        return "General";
    }

    /**
     * A transaction is an expense ONLY if it is explicitly marked as "Expense".
     * A value in debit does not make it an expense (e.g. "Do not Consider", "Transfer", "Income").
     */
    public boolean isExpense() {
        return type != null && "Expense".equalsIgnoreCase(type.trim());
    }

    /**
     * If an expense has a positive debit, it means it is balancing out (refund, cashback, reversal).
     */
    public boolean isBalancingOut() {
        return isExpense() && debit > 0;
    }

    /**
     * Net budget spend contribution:
     * - Returns 0 for non-expenses (e.g. transfers, "Do not Consider").
     * - Returns positive amount for normal expenses (debit < 0 -> -debit).
     * - Returns negative amount for balancing out expenses (debit > 0 -> -debit).
     */
    public double getExpenseContribution() {
        if (!isExpense()) {
            return 0.0;
        }
        if (debit != 0) {
            return -debit;
        }
        if (debitWithoutSign != 0) {
            return debitWithoutSign;
        }
        return 0.0;
    }

    public String getFormattedDate() {
        String formatted = DateTimeHelper.formatDisplayDate(date);
        // Correct date if timezone shift occurred in Google Apps Script export:
        // A transaction in cycle M runs from 06/M to 05/(M+1).
        // If date was 05/M and cycle month is M, it was 06/M shifted back by 1 day due to UTC export.
        if (month > 0 && formatted != null && formatted.contains("/")) {
            String[] parts = formatted.split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim());
                    int y = Integer.parseInt(parts[2].trim());
                    if (d == 5 && m == month && (year == 0 || y == year)) {
                        return String.format(Locale.US, "06/%02d/%04d", m, y);
                    }
                } catch (Exception ignored) {}
            }
        }
        return formatted;
    }

    public int getCycleMonth() {
        if (month > 0) return month;
        String clean = getFormattedDate();
        if (clean != null && clean.contains("/")) {
            String[] parts = clean.split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim());
                    if (d < 6) {
                        return (m == 1) ? 12 : (m - 1);
                    }
                    return m;
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    public int getCycleYear() {
        if (year > 0) return year;
        String clean = getFormattedDate();
        if (clean != null && clean.contains("/")) {
            String[] parts = clean.split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim());
                    int y = Integer.parseInt(parts[2].trim());
                    if (d < 6 && m == 1) {
                        return y - 1;
                    }
                    return y;
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    public boolean isInCycle(int cYear, int cMonth) {
        return getCycleYear() == cYear && getCycleMonth() == cMonth;
    }

    public int getResolvedYear() {
        return getCycleYear();
    }

    public int getResolvedMonth() {
        return getCycleMonth();
    }

    public String getCycleLabel() {
        int m = getCycleMonth();
        int y = getCycleYear();
        if (m >= 1 && m <= 12 && y > 0) {
            String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
            return monthNames[m - 1] + " " + y;
        }
        return "";
    }
}
