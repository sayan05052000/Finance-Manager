package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class SipItem implements Serializable {
    @SerializedName("fundName")
    public String fundName = "";

    @SerializedName("role")
    public String role = "";

    @SerializedName("currentSip")
    public double currentSip;

    @SerializedName("currentMonth")
    public String currentMonth = "";

    @SerializedName("currentStatus")
    public String currentStatus = "Not Paid";

    @SerializedName("nextSip")
    public double nextSip;

    @SerializedName("nextMonth")
    public String nextMonth = "";

    @SerializedName("nextStatus")
    public String nextStatus = "Not Paid";

    @SerializedName("bankAccount")
    public String bankAccount = "";

    @SerializedName("platform")
    public String platform = "";

    public SipItem() {}

    public SipItem copy() {
        SipItem copy = new SipItem();
        copy.fundName = this.fundName;
        copy.role = this.role;
        copy.currentSip = this.currentSip;
        copy.currentMonth = this.currentMonth;
        copy.currentStatus = this.currentStatus;
        copy.nextSip = this.nextSip;
        copy.nextMonth = this.nextMonth;
        copy.nextStatus = this.nextStatus;
        copy.bankAccount = this.bankAccount;
        copy.platform = this.platform;
        return copy;
    }
}
