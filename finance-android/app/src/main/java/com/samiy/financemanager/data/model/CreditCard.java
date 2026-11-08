package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import com.samiy.financemanager.util.DateTimeHelper;

import java.io.Serializable;

public class CreditCard implements Serializable {
    @SerializedName("id")
    public int id;

    @SerializedName("cardName")
    public String cardName = "";

    @SerializedName("billedAmount")
    public double billedAmount;

    @SerializedName("unbilledAmount")
    public double unbilledAmount;

    @SerializedName("dueDate")
    public String dueDate = "";

    @SerializedName("lastPaid")
    public String lastPaid = "";

    @SerializedName("status")
    public String status = "Unpaid";

    public CreditCard() {}

    public CreditCard(int id, String cardName, double billedAmount, double unbilledAmount, String dueDate, String lastPaid, String status) {
        this.id = id;
        this.cardName = cardName;
        this.billedAmount = billedAmount;
        this.unbilledAmount = unbilledAmount;
        this.dueDate = dueDate;
        this.lastPaid = lastPaid;
        this.status = status;
    }

    public Integer getDaysRemaining() {
        return DateTimeHelper.getDaysRemaining(dueDate);
    }

    /**
     * True if billed amount > 0 and due date is within upcoming 14 days (0 <= daysLeft <= 14).
     */
    public boolean isDueSoon() {
        Integer days = getDaysRemaining();
        return billedAmount > 0 && days != null && days >= 0 && days <= 14;
    }

    /**
     * True if billed amount > 0 and due date is in the past (daysLeft < 0).
     */
    public boolean isOverdue() {
        Integer days = getDaysRemaining();
        return billedAmount > 0 && days != null && days < 0;
    }

    public boolean isPaid() {
        return billedAmount <= 0 || "Paid".equalsIgnoreCase(status);
    }
}
