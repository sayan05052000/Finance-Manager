package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ExpenseTrackerItem implements Serializable {
    @SerializedName("expenseType")
    public String expenseType = ""; // e.g., "Fixed Savings", "Fixed Expense", "Variable Expense", "Fixed Family Savings"

    @SerializedName("subCategory")
    public String subCategory = "";

    @SerializedName("amountToBePaid")
    public double amountToBePaid; // Budget / Target

    @SerializedName("amount")
    public double amount;

    @SerializedName("year")
    public int year;

    @SerializedName("month")
    public int month;

    @SerializedName("percent")
    public double percent;

    @SerializedName("previousMonthCost")
    public double previousMonthCost; // Past cycle cost

    @SerializedName("deviation")
    public double deviation;

    @SerializedName("currentMonthCost")
    public double currentMonthCost; // Current cycle cost

    public ExpenseTrackerItem() {}

    public ExpenseTrackerItem(String expenseType, String subCategory, double amountToBePaid, double previousMonthCost, double currentMonthCost) {
        this.expenseType = expenseType;
        this.subCategory = subCategory;
        this.amountToBePaid = amountToBePaid;
        this.previousMonthCost = previousMonthCost;
        this.currentMonthCost = currentMonthCost;
        this.deviation = Math.abs(currentMonthCost - previousMonthCost);
    }

    public boolean isSaving() {
        if (expenseType != null && expenseType.toLowerCase().contains("saving")) {
            return true;
        }
        if (subCategory != null) {
            String sc = subCategory.toLowerCase();
            return sc.contains("mutual fund") || sc.contains("stock") || sc.contains("nps")
                    || sc.contains("ppf") || sc.contains("lic") || sc.contains("policy") || sc.contains("saving");
        }
        return false;
    }

    /**
     * User Rule:
     * - If category of saving: if current cycle is bigger (currentMonthCost >= previousMonthCost), then it is good.
     * - For others (expenses): if current cycle is less (currentMonthCost <= previousMonthCost), it is better.
     */
    public boolean isGood() {
        if (isSaving()) {
            return currentMonthCost >= previousMonthCost;
        } else {
            return currentMonthCost <= previousMonthCost;
        }
    }

    public double getCostDifference() {
        return currentMonthCost - previousMonthCost;
    }
}
