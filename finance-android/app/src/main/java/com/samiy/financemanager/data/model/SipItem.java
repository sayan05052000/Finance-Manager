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
}
