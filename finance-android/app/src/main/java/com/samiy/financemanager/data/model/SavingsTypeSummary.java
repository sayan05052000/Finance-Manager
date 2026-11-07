package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class SavingsTypeSummary implements Serializable {
    @SerializedName("name")
    public String name = "";

    @SerializedName("holdingsCount")
    public int holdingsCount;

    @SerializedName("currentValuation")
    public double currentValuation;

    @SerializedName("investedValue")
    public double investedValue;

    @SerializedName("oneDayChange")
    public double oneDayChange;

    @SerializedName("oneDayChangePercent")
    public double oneDayChangePercent;

    public SavingsTypeSummary() {}
}
