package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FinanceDatabase implements Serializable {
    @SerializedName("currentCycle")
    public CurrentCycle currentCycle = new CurrentCycle();

    @SerializedName("portfolioSummary")
    public PortfolioSummary portfolioSummary = new PortfolioSummary();

    @SerializedName("accounts")
    public List<Account> accounts = new ArrayList<>();

    @SerializedName("loans")
    public List<Loan> loans = new ArrayList<>();

    @SerializedName("holdings")
    public List<Holding> holdings = new ArrayList<>();

    @SerializedName("transactions")
    public List<Transaction> transactions = new ArrayList<>();

    @SerializedName("mutualFundTransactions")
    public List<MutualFundTransaction> mutualFundTransactions = new ArrayList<>();

    @SerializedName("categories")
    public List<CategoryItem> categories = new ArrayList<>();

    @SerializedName("budget")
    public List<BudgetItem> budget = new ArrayList<>();

    public FinanceDatabase() {}

    public double getTotalBankBalance() {
        double sum = 0.0;
        if (accounts != null) {
            for (Account a : accounts) {
                if (a != null) sum += a.currentBalance;
            }
        }
        return sum;
    }

    public double getTotalLoanDebt() {
        double sum = 0.0;
        if (loans != null) {
            for (Loan l : loans) {
                if (l != null && l.isDebt) {
                    sum += Math.abs(l.outstanding);
                }
            }
        }
        return sum;
    }

    public double getCreditCardDueAmount() {
        if (loans != null) {
            for (Loan l : loans) {
                if (l != null && l.name != null && l.name.equalsIgnoreCase("CREDIT CARD")) {
                    return Math.abs(l.outstanding);
                }
            }
        }
        return 0.0;
    }

    public double getCreditCardPaymentBalance() {
        if (loans != null) {
            for (Loan l : loans) {
                if (l != null && l.name != null && l.name.equalsIgnoreCase("PAYMENT")) {
                    return Math.abs(l.outstanding);
                }
            }
        }
        if (currentCycle != null && currentCycle.receivables != null) {
            for (CurrentCycle.ReceivableItem r : currentCycle.receivables) {
                if (r != null && r.source != null && r.source.equalsIgnoreCase("PAYMENT")) {
                    return r.amount;
                }
            }
        }
        return 0.0;
    }

    public double getEstimatedNetWorth() {
        double bank = getTotalBankBalance();
        double holdingsVal = portfolioSummary != null ? portfolioSummary.currentValuation : 0.0;
        double debt = getTotalLoanDebt();
        return (bank + holdingsVal) - debt;
    }

    public List<String> getCategoryNames() {
        List<String> names = new ArrayList<>();
        if (categories != null) {
            for (CategoryItem c : categories) {
                if (c != null && c.name != null && !c.name.trim().isEmpty()) {
                    names.add(c.name.trim());
                }
            }
        }
        return names;
    }

    public List<String> getAllSubCategories() {
        List<String> list = new ArrayList<>();
        if (categories != null) {
            for (CategoryItem c : categories) {
                if (c != null && c.subCategories != null) {
                    for (String sc : c.subCategories) {
                        if (sc != null && !sc.trim().isEmpty() && !list.contains(sc.trim())) {
                            list.add(sc.trim());
                        }
                    }
                }
            }
        }
        if (list.isEmpty()) {
            String[] defaults = {
                "Mutual Fund", "Credit Mutual Fund", "Family Mutual Fund", "Emergency Mutual Fund",
                "Kuvera Surplus(Family)", "Transit", "Food", "Electricity", "Water bill", "Groceries",
                "Mobile Bill", "Shopping", "House Rent", "CashBack", "Refund", "Salary", "Interest",
                "Credit Card", "Family Contribution ( Baba )", "Family Contribution ( Maa )",
                "Escess Spending From Past Month", "Health Insurance", "Term Insurance"
            };
            for (String s : defaults) list.add(s);
        }
        return list;
    }
}
