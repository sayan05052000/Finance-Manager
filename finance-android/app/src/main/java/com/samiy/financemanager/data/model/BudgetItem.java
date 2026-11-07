package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class BudgetItem implements Serializable {
    @SerializedName("year")
    public int year;

    @SerializedName("month")
    public String month = "";

    @SerializedName("monthNumber")
    public int monthNumber;

    @SerializedName("budget")
    public double budget;

    @SerializedName("spend")
    public double spend;

    @SerializedName("remainder")
    public double remainder;

    @SerializedName("canSpendPerDay")
    public double canSpendPerDay;

    @SerializedName("investment")
    public double investment;

    public BudgetItem() {}
}
