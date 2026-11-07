package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CurrentCycle implements Serializable {
    @SerializedName("cycleName")
    public String cycleName = "November 2026 ★ (Active)";

    @SerializedName("cycleMonth")
    public String cycleMonth = "November 2026";

    @SerializedName("cycleYear")
    public int cycleYear = 2026;

    @SerializedName("cycleMonthNumber")
    public int cycleMonthNumber = 11;

    @SerializedName("cycleStart")
    public String cycleStart = "06/11/2026";

    @SerializedName("cycleEnd")
    public String cycleEnd = "05/12/2026";

    @SerializedName("daysLeft")
    public int daysLeft = 30;

    @SerializedName("budget")
    public double budget = 54550.0;

    @SerializedName("totalSpend")
    public double totalSpend = 24340.28;

    @SerializedName("remainder")
    public double remainder = 30209.72;

    @SerializedName("canSpendPerDay")
    public double canSpendPerDay = 1006.99;

    @SerializedName("interestThisCycle")
    public double interestThisCycle = 26.37;

    @SerializedName("cashbackRefunds")
    public double cashbackRefunds = 0.0;

    @SerializedName("totalReceivables")
    public double totalReceivables = 1230294.23;

    @SerializedName("subCategories")
    public List<SubCategorySpend> subCategories = new ArrayList<>();

    @SerializedName("receivables")
    public List<ReceivableItem> receivables = new ArrayList<>();

    public static class SubCategorySpend implements Serializable {
        @SerializedName("name")
        public String name = "";

        @SerializedName("amount")
        public double amount;

        @SerializedName("percentage")
        public double percentage;

        public SubCategorySpend() {}
        public SubCategorySpend(String name, double amount, double percentage) {
            this.name = name;
            this.amount = amount;
            this.percentage = percentage;
        }
    }

    public static class ReceivableItem implements Serializable {
        @SerializedName("source")
        public String source = "";

        @SerializedName("amount")
        public double amount;

        @SerializedName("isAdvance")
        public boolean isAdvance;

        @SerializedName("tag")
        public String tag = "";

        @SerializedName("note")
        public String note = "";

        public ReceivableItem() {}
    }

    public CurrentCycle() {}
}
