package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Loan implements Serializable {
    @SerializedName("name")
    public String name = "";

    @SerializedName("outstanding")
    public double outstanding;

    @SerializedName("interestRate")
    public double interestRate;

    @SerializedName("isDebt")
    public boolean isDebt = true;

    public Loan() {}
}
