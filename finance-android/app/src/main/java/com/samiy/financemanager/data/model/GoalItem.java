package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class GoalItem implements Serializable {
    @SerializedName("name")
    public String name = "";

    @SerializedName("targetAmount")
    public double targetAmount;

    @SerializedName("currentAmount")
    public double currentAmount;

    @SerializedName("achievedPercent")
    public double achievedPercent;

    @SerializedName("savingAllocationPercent")
    public double savingAllocationPercent;

    public GoalItem() {}

    public GoalItem(String name, double targetAmount, double currentAmount, double achievedPercent, double savingAllocationPercent) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.achievedPercent = achievedPercent;
        this.savingAllocationPercent = savingAllocationPercent;
    }
}
