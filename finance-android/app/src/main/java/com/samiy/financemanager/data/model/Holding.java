package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Holding implements Serializable {
    @SerializedName("schemeName")
    public String schemeName = "";

    @SerializedName("schemeCode")
    public String schemeCode = "";

    @SerializedName("schemeId")
    public String schemeId = "";

    @SerializedName("category")
    public String category = "";

    @SerializedName("subCategory")
    public String subCategory = "";

    @SerializedName("savingsType")
    public String savingsType = "";

    @SerializedName("savingsCategory")
    public String savingsCategory = "";

    @SerializedName("source")
    public String source = "";

    @SerializedName("units")
    public double units;

    @SerializedName("avgBuyingNav")
    public double avgBuyingNav;

    @SerializedName("investedValue")
    public double investedValue;

    @SerializedName("nav")
    public double nav;

    @SerializedName("navDate")
    public String navDate = "";

    @SerializedName("currentValue")
    public double currentValue;

    @SerializedName("returns")
    public double returns;

    @SerializedName("absReturn")
    public double absReturn;

    @SerializedName("afterTaxReturn")
    public double afterTaxReturn;

    @SerializedName("previousValue")
    public double previousValue;

    @SerializedName("oneDayChange")
    public double oneDayChange;

    @SerializedName("oneDayChangePercent")
    public double oneDayChangePercent;

    public Holding() {}

    public String getFormattedNavDate() {
        if (navDate == null || navDate.trim().isEmpty()) return "";
        return com.samiy.financemanager.util.DateTimeHelper.formatDisplayDate(navDate);
    }

    public long getNavEpochDay() {
        String clean = getFormattedNavDate();
        if (clean != null && clean.contains("/")) {
            String[] parts = clean.split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim());
                    int y = Integer.parseInt(parts[2].trim());
                    return (long) y * 10000L + m * 100L + d;
                } catch (Exception ignored) {}
            }
        }
        return 0L;
    }

    public String getFormattedNav() {
        if (nav <= 0) return "";
        java.text.DecimalFormat df = new java.text.DecimalFormat("₹#,##0.00##");
        return df.format(nav);
    }
}
