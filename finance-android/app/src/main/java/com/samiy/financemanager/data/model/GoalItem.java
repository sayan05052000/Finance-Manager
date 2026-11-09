package com.samiy.financemanager.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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

    @SerializedName("contributionJson")
    public String contributionJson = "{}";

    public GoalItem() {}

    public GoalItem(String name, double targetAmount, double currentAmount, double achievedPercent, double savingAllocationPercent) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.achievedPercent = achievedPercent;
        this.savingAllocationPercent = savingAllocationPercent;
        this.contributionJson = "{}";
    }

    public GoalItem(String name, double targetAmount, double currentAmount, double achievedPercent, double savingAllocationPercent, String contributionJson) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.achievedPercent = achievedPercent;
        this.savingAllocationPercent = savingAllocationPercent;
        this.contributionJson = contributionJson != null ? contributionJson : "{}";
    }

    public String getContributionSummary() {
        if (contributionJson == null || contributionJson.trim().isEmpty() || contributionJson.trim().equals("{}")) {
            if (savingAllocationPercent > 0) {
                return String.format(java.util.Locale.US, "Allocation: %.0f%%", savingAllocationPercent);
            }
            return "Manual Tracking";
        }
        try {
            JsonObject obj = JsonParser.parseString(contributionJson).getAsJsonObject();
            List<String> parts = new ArrayList<>();
            if (obj.has("accounts") && obj.get("accounts").isJsonObject()) {
                JsonObject accs = obj.getAsJsonObject("accounts");
                for (String acc : accs.keySet()) {
                    double pct = 1.0;
                    try {
                        pct = accs.get(acc).getAsDouble();
                    } catch (Exception ignored) {}
                    if (pct >= 0.99) {
                        parts.add(acc);
                    } else {
                        parts.add(acc + " (" + (int) Math.round(pct * 100) + "%)");
                    }
                }
            }
            if (obj.has("mutual_funds") && obj.get("mutual_funds").isJsonArray()) {
                JsonArray mfs = obj.getAsJsonArray("mutual_funds");
                for (JsonElement e : mfs) {
                    if (e.isJsonObject()) {
                        JsonObject mf = e.getAsJsonObject();
                        String val = mf.has("value") ? mf.get("value").getAsString() : "";
                        double pct = 1.0;
                        if (mf.has("percentage")) {
                            try {
                                pct = mf.get("percentage").getAsDouble();
                            } catch (Exception ignored) {}
                        }
                        if (pct >= 0.99) {
                            parts.add(val);
                        } else {
                            parts.add(val + " (" + (int) Math.round(pct * 100) + "%)");
                        }
                    }
                }
            }
            if (parts.isEmpty()) {
                return "Manual Tracking";
            }
            return "Linked: " + String.join(", ", parts);
        } catch (Exception e) {
            return "Custom Linked";
        }
    }
}
