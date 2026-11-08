package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class InsurancePolicy implements Serializable {
    @SerializedName("policyNumber")
    public String policyNumber = "";

    @SerializedName("birthDate")
    public String birthDate = "";

    @SerializedName("premiumAmount")
    public double premiumAmount;

    @SerializedName("lastPaid")
    public String lastPaid = "";

    @SerializedName("dueDate")
    public String dueDate = "";

    @SerializedName("lastDate")
    public String lastDate = "";

    @SerializedName("installmentsRemaining")
    public String installmentsRemaining = "";

    @SerializedName("subCategory")
    public String subCategory = "";

    @SerializedName("category")
    public String category = "";

    @SerializedName("id")
    public String id = "";

    @SerializedName("password")
    public String password = "";

    @SerializedName("sumAssured")
    public double sumAssured;

    @SerializedName("notes")
    public String notes = "";

    public InsurancePolicy() {}

    public InsurancePolicy(String policyNumber, double premiumAmount, String lastPaid, String dueDate, String installmentsRemaining, String subCategory, String category, double sumAssured, String notes) {
        this(policyNumber, "", premiumAmount, lastPaid, dueDate, "", installmentsRemaining, subCategory, category, sumAssured, notes);
    }

    public InsurancePolicy(String policyNumber, String birthDate, double premiumAmount, String lastPaid, String dueDate, String lastDate, String installmentsRemaining, String subCategory, String category, double sumAssured, String notes) {
        this.policyNumber = policyNumber;
        this.birthDate = birthDate;
        this.premiumAmount = premiumAmount;
        this.lastPaid = lastPaid;
        this.dueDate = dueDate;
        this.lastDate = lastDate;
        this.installmentsRemaining = installmentsRemaining;
        this.subCategory = subCategory;
        this.category = category;
        this.sumAssured = sumAssured;
        this.notes = notes;
    }

    public Integer getDaysRemaining() {
        return com.samiy.financemanager.util.DateTimeHelper.getDaysRemaining(dueDate);
    }

    public boolean isLicOrTataAia() {
        String sub = (subCategory != null ? subCategory : "").toUpperCase();
        String cat = (category != null ? category : "").toUpperCase();
        String n = (notes != null ? notes : "").toUpperCase();
        return sub.contains("LIC") || sub.contains("TATA") || cat.contains("LIC") || cat.contains("TATA") || n.contains("LIC") || n.contains("TATA");
    }

    public boolean isDueSoon() {
        Integer days = getDaysRemaining();
        return isLicOrTataAia() && days != null && days >= 0 && days <= 14;
    }

    public boolean isOverdue() {
        Integer days = getDaysRemaining();
        return isLicOrTataAia() && days != null && days < 0;
    }
}
