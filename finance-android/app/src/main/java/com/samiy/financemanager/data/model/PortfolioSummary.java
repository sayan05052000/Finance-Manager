package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PortfolioSummary implements Serializable {
    @SerializedName("totalInvested")
    public double totalInvested = 383027.82;

    @SerializedName("currentValuation")
    public double currentValuation = 385139.11;

    @SerializedName("oneDayGain")
    public double oneDayGain = 7303.16;

    @SerializedName("oneDayGainPercent")
    public double oneDayGainPercent = 1.90;

    @SerializedName("totalReturns")
    public double totalReturns = 2111.29;

    @SerializedName("totalReturnsRoi")
    public double totalReturnsRoi = 0.55;

    @SerializedName("afterTaxApprox")
    public double afterTaxApprox = 1583.72;

    @SerializedName("totalAssetsCount")
    public int totalAssetsCount = 13;

    @SerializedName("totalUnits")
    public double totalUnits;

    @SerializedName("savingsTypeBreakdown")
    public List<SavingsTypeSummary> savingsTypeBreakdown = new ArrayList<>();

    public PortfolioSummary() {}
}
