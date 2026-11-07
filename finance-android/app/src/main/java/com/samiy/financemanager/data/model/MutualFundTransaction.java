package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import com.samiy.financemanager.util.DateTimeHelper;
import java.io.Serializable;

public class MutualFundTransaction implements Serializable {
    @SerializedName("id")
    public long id;

    @SerializedName("schemeName")
    public String schemeName = "";

    @SerializedName("schemeId")
    public String schemeId = "";

    @SerializedName("schemeCode")
    public String schemeCode = "";

    @SerializedName("date")
    public String date = "";

    @SerializedName("fundCategory")
    public String fundCategory = "Equity";

    @SerializedName("fundSubCategory")
    public String fundSubCategory = "Small Cap";

    @SerializedName("platform")
    public String platform = "Groww";

    @SerializedName("subCategory")
    public String subCategory = "Mutual Fund";

    @SerializedName("folioNumber")
    public String folioNumber = "";

    @SerializedName("amount")
    public double amount;

    @SerializedName("actualInvested")
    public double actualInvested;

    @SerializedName("investedRedeemedAmount")
    public String investedRedeemedAmount = "";

    @SerializedName("units")
    public double units;

    @SerializedName("nav")
    public double nav;

    @SerializedName("currentNav")
    public double currentNav;

    public MutualFundTransaction() {}

    public boolean isRedeem() {
        return units < 0;
    }

    public String getDisplayType() {
        return isRedeem() ? "Redeem" : "Buy / SIP";
    }

    public double getDisplayAmount() {
        if (amount > 0) return amount;
        if (actualInvested > 0) return actualInvested;
        return Math.abs(units * nav);
    }

    public String getFormattedDate() {
        return DateTimeHelper.formatDisplayDate(date);
    }
}
