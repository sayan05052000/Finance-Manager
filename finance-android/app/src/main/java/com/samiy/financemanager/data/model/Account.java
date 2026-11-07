package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Account implements Serializable {
    @SerializedName("name")
    public String name = "";

    @SerializedName("currentBalance")
    public double currentBalance;

    public Account() {}
}
