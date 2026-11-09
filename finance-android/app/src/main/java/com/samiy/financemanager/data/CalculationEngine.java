package com.samiy.financemanager.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.BudgetItem;
import com.samiy.financemanager.data.model.CurrentCycle;
import com.samiy.financemanager.data.model.ExpenseTrackerItem;
import com.samiy.financemanager.data.model.GoalItem;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.PortfolioSummary;
import com.samiy.financemanager.data.model.SavingsTypeSummary;
import com.samiy.financemanager.data.model.Transaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class CalculationEngine {

    public static void recalculate(FinanceDatabase db) {
        if (db == null) return;

        // 1. Recalculate Portfolio Summary from raw holdings
        if (db.holdings != null && !db.holdings.isEmpty()) {
            double totalInvested = 0.0;
            double currentValuation = 0.0;
            double oneDayGain = 0.0;
            double totalUnits = 0.0;

            Map<String, List<Holding>> bySavingsType = new HashMap<>();

            for (Holding h : db.holdings) {
                totalInvested += h.investedValue;
                currentValuation += h.currentValue;
                oneDayGain += h.oneDayChange;
                totalUnits += h.units;

                String type = h.savingsType != null && !h.savingsType.trim().isEmpty() ? h.savingsType.trim() : "Other";
                if (!bySavingsType.containsKey(type)) {
                    bySavingsType.put(type, new ArrayList<>());
                }
                bySavingsType.get(type).add(h);
            }

            if (db.portfolioSummary == null) {
                db.portfolioSummary = new PortfolioSummary();
            }

            db.portfolioSummary.totalInvested = totalInvested;
            db.portfolioSummary.currentValuation = currentValuation;
            db.portfolioSummary.oneDayGain = oneDayGain;
            db.portfolioSummary.oneDayGainPercent = currentValuation > 0 ? (oneDayGain / (currentValuation - oneDayGain)) * 100.0 : 0.0;
            db.portfolioSummary.totalReturns = currentValuation - totalInvested;
            db.portfolioSummary.totalReturnsRoi = totalInvested > 0 ? ((currentValuation - totalInvested) / totalInvested) * 100.0 : 0.0;
            db.portfolioSummary.afterTaxApprox = Math.max(0, (currentValuation - totalInvested) * 0.875); // ~12.5% LTCG estimate
            db.portfolioSummary.totalAssetsCount = db.holdings.size();
            db.portfolioSummary.totalUnits = totalUnits;

            // Build SavingsType breakdown
            List<SavingsTypeSummary> breakdown = new ArrayList<>();
            for (Map.Entry<String, List<Holding>> entry : bySavingsType.entrySet()) {
                SavingsTypeSummary sts = new SavingsTypeSummary();
                sts.name = entry.getKey();
                sts.holdingsCount = entry.getValue().size();
                double sCurrent = 0.0;
                double sInvested = 0.0;
                double s1D = 0.0;
                for (Holding sh : entry.getValue()) {
                    sCurrent += sh.currentValue;
                    sInvested += sh.investedValue;
                    s1D += sh.oneDayChange;
                }
                sts.currentValuation = sCurrent;
                sts.investedValue = sInvested;
                sts.oneDayChange = s1D;
                sts.oneDayChangePercent = sCurrent > 0 ? (s1D / Math.max(1, sCurrent - s1D)) * 100.0 : 0.0;
                breakdown.add(sts);
            }
            db.portfolioSummary.savingsTypeBreakdown = breakdown;
        }

        // 2. Recalculate Current Cycle Budget, Spend, Days Left & Interest dynamically along with date
        updateCurrentCycle(db);
    }

    public static void updateCurrentCycle(FinanceDatabase db) {
        if (db == null) return;
        if (db.currentCycle == null) {
            db.currentCycle = new CurrentCycle();
        }

        // Determine current date in Asia/Kolkata timezone
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));

        // Check latest transaction date to anchor properly
        int latestTxYear = 0;
        int latestTxMonth = 0;
        int latestTxDay = 0;
        if (db.transactions != null && !db.transactions.isEmpty()) {
            for (Transaction t : db.transactions) {
                if (t.date != null && t.date.contains("/")) {
                    String[] parts = t.date.trim().split("/");
                    if (parts.length == 3) {
                        try {
                            int d = Integer.parseInt(parts[0].trim());
                            int m = Integer.parseInt(parts[1].trim());
                            int y = Integer.parseInt(parts[2].trim());
                            if (y > latestTxYear || (y == latestTxYear && m > latestTxMonth) || (y == latestTxYear && m == latestTxMonth && d > latestTxDay)) {
                                latestTxYear = y;
                                latestTxMonth = m;
                                latestTxDay = d;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        int currYear = now.get(Calendar.YEAR);
        int currMonth = now.get(Calendar.MONTH) + 1;
        int currDay = now.get(Calendar.DAY_OF_MONTH);

        if (latestTxYear > currYear || (latestTxYear == currYear && latestTxMonth > currMonth)) {
            currYear = latestTxYear;
            currMonth = latestTxMonth;
            currDay = latestTxDay;
        }

        // Cycle rule: starts on 6th of month and ends on 5th of next month
        int cMonth, cYear;
        if (currDay >= 6) {
            cMonth = currMonth;
            cYear = currYear;
        } else {
            if (currMonth == 1) {
                cMonth = 12;
                cYear = currYear - 1;
            } else {
                cMonth = currMonth - 1;
                cYear = currYear;
            }
        }

        int nextMonth = (cMonth == 12) ? 1 : cMonth + 1;
        int nextYear = (cMonth == 12) ? cYear + 1 : cYear;

        String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        String cycleMonthName = monthNames[cMonth - 1] + " " + cYear;

        String cycleStartStr = String.format(Locale.US, "06/%02d/%04d", cMonth, cYear);
        String cycleEndStr = String.format(Locale.US, "05/%02d/%04d", nextMonth, nextYear);

        // Calculate days left in cycle dynamically based on date
        Calendar endCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        endCal.set(Calendar.YEAR, nextYear);
        endCal.set(Calendar.MONTH, nextMonth - 1);
        endCal.set(Calendar.DAY_OF_MONTH, 5);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);

        Calendar anchorCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        anchorCal.set(Calendar.YEAR, currYear);
        anchorCal.set(Calendar.MONTH, currMonth - 1);
        anchorCal.set(Calendar.DAY_OF_MONTH, currDay);
        anchorCal.set(Calendar.HOUR_OF_DAY, 0);
        anchorCal.set(Calendar.MINUTE, 0);
        anchorCal.set(Calendar.SECOND, 0);

        long diffMillis = endCal.getTimeInMillis() - anchorCal.getTimeInMillis();
        int daysLeft = (int) Math.max(1, Math.ceil(diffMillis / (1000.0 * 60 * 60 * 24)));

        db.currentCycle.cycleYear = cYear;
        db.currentCycle.cycleMonthNumber = cMonth;
        db.currentCycle.cycleMonth = cycleMonthName;
        db.currentCycle.cycleName = cycleMonthName + " ★ (Active)";
        db.currentCycle.cycleStart = cycleStartStr;
        db.currentCycle.cycleEnd = cycleEndStr;
        db.currentCycle.daysLeft = daysLeft;

        // Dynamic budget lookup for active cycle
        double budgetAmount = 54550.0;
        if (db.budget != null && !db.budget.isEmpty()) {
            for (BudgetItem b : db.budget) {
                if (b.year == cYear && b.monthNumber == cMonth && b.budget > 0) {
                    budgetAmount = b.budget;
                    break;
                }
            }
        } else if (db.currentCycle.budget > 0) {
            budgetAmount = db.currentCycle.budget;
        }
        db.currentCycle.budget = budgetAmount;

        // Dynamic spend & dynamic interest occurred in accounts (not loans)
        double totalSpend = 0.0;
        double interestOccurredAccounts = 0.0;
        Map<String, Double> subCatSpend = new HashMap<>();

        if (db.transactions != null) {
            for (Transaction t : db.transactions) {
                if (t.isInCycle(cYear, cMonth)) {
                    // 1. Spend Contribution for expenses
                    if (t.isExpense()) {
                        double contribution = t.getExpenseContribution();
                        totalSpend += contribution;
                        String subCat = t.subCategory != null && !t.subCategory.trim().isEmpty() ? t.subCategory.trim() : "Other";
                        subCatSpend.put(subCat, subCatSpend.getOrDefault(subCat, 0.0) + contribution);
                    }

                    // 2. Interest Occurred in Bank Accounts (NOT in loans)
                    String subCat = t.subCategory != null ? t.subCategory.trim().toLowerCase(Locale.ROOT) : "";
                    String cat = t.category != null ? t.category.trim().toLowerCase(Locale.ROOT) : "";
                    if (subCat.contains("interest") || cat.contains("interest")) {
                        boolean hasAccount = t.account != null && !t.account.trim().isEmpty()
                                && !t.account.trim().equalsIgnoreCase("none") && !t.account.trim().equals("-");
                        boolean hasLoan = t.loans != null && !t.loans.trim().isEmpty()
                                && !t.loans.trim().equalsIgnoreCase("none") && !t.loans.trim().equals("-");
                        if (hasAccount && !hasLoan) {
                            double amt = t.credit > 0 ? t.credit : (t.net > 0 ? t.net : (t.debit > 0 ? t.debit : 0));
                            interestOccurredAccounts += amt;
                        }
                    }
                }
            }
        }

        if (totalSpend > 0) {
            db.currentCycle.totalSpend = Math.round(totalSpend * 100.0) / 100.0;
        } else if (db.currentCycle.totalSpend <= 0) {
            db.currentCycle.totalSpend = 24340.28;
        }

        db.currentCycle.remainder = Math.round((db.currentCycle.budget - db.currentCycle.totalSpend) * 100.0) / 100.0;
        db.currentCycle.canSpendPerDay = daysLeft > 0 ? Math.round((db.currentCycle.remainder / daysLeft) * 100.0) / 100.0 : 0.0;

        // Interest occurred in accounts (dynamic calculation from active cycle account interest transactions)
        if (interestOccurredAccounts > 0 || db.currentCycle.interestThisCycle == 0) {
            db.currentCycle.interestThisCycle = Math.round(interestOccurredAccounts * 100.0) / 100.0;
        }

        // Past cycle spend calculation per subcategory
        int pastMonth = (cMonth == 1) ? 12 : cMonth - 1;
        int pastYear = (cMonth == 1) ? cYear - 1 : cYear;
        Map<String, Double> pastSubCatSpend = new HashMap<>();

        if (db.transactions != null) {
            for (Transaction t : db.transactions) {
                if (t.isInCycle(pastYear, pastMonth) && t.isExpense()) {
                    double contribution = t.getExpenseContribution();
                    String subCat = t.subCategory != null && !t.subCategory.trim().isEmpty() ? t.subCategory.trim() : "Other";
                    pastSubCatSpend.put(subCat, pastSubCatSpend.getOrDefault(subCat, 0.0) + contribution);
                }
            }
        }

        // Synchronize and populate Expense Tracker (Past vs Current Cycle comparison)
        if (db.expenseTracker == null || db.expenseTracker.isEmpty()) {
            db.expenseTracker = createDefaultExpenseTrackerItems();
        }

        Set<String> trackedSubCats = new HashSet<>();
        for (ExpenseTrackerItem item : db.expenseTracker) {
            if (item.subCategory != null) {
                trackedSubCats.add(item.subCategory.trim().toLowerCase(Locale.ROOT));
                if (subCatSpend.containsKey(item.subCategory)) {
                    item.currentMonthCost = subCatSpend.get(item.subCategory);
                }
                if (pastSubCatSpend.containsKey(item.subCategory) && item.previousMonthCost == 0) {
                    item.previousMonthCost = pastSubCatSpend.get(item.subCategory);
                }
                item.deviation = Math.abs(item.currentMonthCost - item.previousMonthCost);
            }
        }

        for (Map.Entry<String, Double> entry : subCatSpend.entrySet()) {
            String sc = entry.getKey();
            if (!trackedSubCats.contains(sc.trim().toLowerCase(Locale.ROOT))) {
                String expenseType = "Variable Expense";
                String lower = sc.toLowerCase(Locale.ROOT);
                if (lower.contains("mutual fund") || lower.contains("saving")) {
                    expenseType = "Fixed Savings";
                } else if (lower.contains("family")) {
                    expenseType = "Fixed Family Expense";
                }
                double curr = entry.getValue();
                double prev = pastSubCatSpend.getOrDefault(sc, 0.0);
                ExpenseTrackerItem newItem = new ExpenseTrackerItem(expenseType, sc, curr, prev, curr);
                db.expenseTracker.add(newItem);
                trackedSubCats.add(lower);
            }
        }

        if (db.goals == null || db.goals.isEmpty()) {
            db.goals = createDefaultGoals();
        } else {
            for (GoalItem g : db.goals) {
                if (g != null && g.contributionJson != null && !g.contributionJson.trim().isEmpty() && !g.contributionJson.trim().equals("{}")) {
                    double calcBal = calculateGoalBalance(g, db);
                    if (calcBal > 0) {
                        g.currentAmount = calcBal;
                    }
                    if (g.targetAmount > 0) {
                        g.achievedPercent = Math.round((g.currentAmount / g.targetAmount) * 1000.0) / 10.0;
                    }
                }
            }
        }
        if (db.insurance == null || db.insurance.isEmpty()) {
            db.insurance = createDefaultInsurance();
        }

        // SubCategories breakdown
        if (!subCatSpend.isEmpty()) {
            List<CurrentCycle.SubCategorySpend> scList = new ArrayList<>();
            for (Map.Entry<String, Double> e : subCatSpend.entrySet()) {
                double pct = db.currentCycle.totalSpend > 0 ? (e.getValue() / db.currentCycle.totalSpend) * 100.0 : 0.0;
                scList.add(new CurrentCycle.SubCategorySpend(e.getKey(), Math.round(e.getValue() * 100.0) / 100.0, Math.round(pct * 10.0) / 10.0));
            }
            scList.sort((a, b) -> Double.compare(b.amount, a.amount));
            db.currentCycle.subCategories = scList;
        }
    }

    private static List<ExpenseTrackerItem> createDefaultExpenseTrackerItems() {
        List<ExpenseTrackerItem> list = new ArrayList<>();
        list.add(new ExpenseTrackerItem("Fixed Savings", "Mutual Fund", 11250.0, 17920.0, 1000.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "Emergency Mutual Fund", 2000.0, 2250.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "US Stocks", 5000.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Expense", "House Rent", 6000.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "NPS", 500.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "PPF", 500.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "LIC Policies", 0.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "Tata AIA Policy", 0.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Family Savings", "Brother LIC Policies", 0.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Family Savings", "Sister LIC Policies", 0.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Family Savings", "Family Mutual Fund", 0.0, 4750.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Savings", "Ishita Loan Repayment", 30000.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Expense", "Savings Account", 1000.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Expense", "Credit Card", 855.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Fixed Expense", "Mobile Bill", 899.0, 0.0, 0.0));
        list.add(new ExpenseTrackerItem("Variable Family Expense", "Family Mobile Bill", 0.0, 902.0, 0.0));
        list.add(new ExpenseTrackerItem("Variable Expense", "Electricity", 4267.75, 4773.0, 4267.75));
        list.add(new ExpenseTrackerItem("Variable Expense", "Food", 500.0, 2098.17, 0.0));
        list.add(new ExpenseTrackerItem("Variable Expense", "Transit", 900.0, 825.0, 0.0));
        return list;
    }

    public static double calculateGoalBalance(GoalItem g, FinanceDatabase db) {
        if (db == null || g == null) return 0.0;
        if (g.contributionJson == null || g.contributionJson.trim().isEmpty() || g.contributionJson.trim().equals("{}")) {
            return g.currentAmount;
        }
        try {
            JsonObject obj = JsonParser.parseString(g.contributionJson).getAsJsonObject();
            double total = 0.0;

            // 1. Accounts
            if (obj.has("accounts") && obj.get("accounts").isJsonObject() && db.accounts != null) {
                JsonObject accs = obj.getAsJsonObject("accounts");
                Map<String, Double> accBalances = new HashMap<>();
                for (Account a : db.accounts) {
                    if (a != null && a.name != null) {
                        accBalances.put(a.name.trim().toUpperCase(Locale.US), a.currentBalance);
                    }
                }
                for (String accName : accs.keySet()) {
                    double pct = 1.0;
                    try {
                        pct = accs.get(accName).getAsDouble();
                    } catch (Exception ignored) {}
                    Double bal = accBalances.get(accName.trim().toUpperCase(Locale.US));
                    if (bal != null) {
                        total += bal * pct;
                    }
                }
            }

            // 2. Mutual Funds
            if (obj.has("mutual_funds") && obj.get("mutual_funds").isJsonArray() && db.holdings != null) {
                JsonArray mfs = obj.getAsJsonArray("mutual_funds");
                for (JsonElement elem : mfs) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject rule = elem.getAsJsonObject();
                    String col = rule.has("column") ? rule.get("column").getAsString().trim().toLowerCase(Locale.US) : "";
                    String val = rule.has("value") ? rule.get("value").getAsString().trim().toLowerCase(Locale.US) : "";
                    double pct = 1.0;
                    if (rule.has("percentage")) {
                        try {
                            pct = rule.get("percentage").getAsDouble();
                        } catch (Exception ignored) {}
                    }

                    for (Holding h : db.holdings) {
                        if (h == null) continue;
                        boolean match = false;
                        if (col.contains("catgory") || col.contains("category")) {
                            if (h.savingsCategory != null && h.savingsCategory.trim().equalsIgnoreCase(val)) match = true;
                        } else if (col.contains("type")) {
                            if (h.savingsType != null && h.savingsType.trim().equalsIgnoreCase(val)) match = true;
                        } else {
                            if ((h.savingsType != null && h.savingsType.trim().equalsIgnoreCase(val)) ||
                                (h.savingsCategory != null && h.savingsCategory.trim().equalsIgnoreCase(val))) {
                                match = true;
                            }
                        }

                        if (match) {
                            total += h.currentValue * pct;
                        }
                    }
                }
            }

            return Math.round(total * 100.0) / 100.0;
        } catch (Exception e) {
            return g.currentAmount;
        }
    }

    /**
     * Calculates the total percentage (0 to 100) allocated to a specific bank account across all existing goals.
     */
    public static double getAccountAllocatedPercentage(String accountName, List<GoalItem> goals, String excludeGoalName) {
        if (accountName == null || goals == null) return 0.0;
        String cleanName = accountName.trim().toUpperCase(Locale.US);
        double totalPct = 0.0;
        for (GoalItem g : goals) {
            if (g == null || g.contributionJson == null || g.contributionJson.trim().isEmpty()) continue;
            if (excludeGoalName != null && g.name != null && g.name.trim().equalsIgnoreCase(excludeGoalName.trim())) {
                continue;
            }
            try {
                JsonObject obj = JsonParser.parseString(g.contributionJson).getAsJsonObject();
                if (obj.has("accounts") && obj.get("accounts").isJsonObject()) {
                    JsonObject accs = obj.getAsJsonObject("accounts");
                    for (String key : accs.keySet()) {
                        if (key.trim().toUpperCase(Locale.US).equals(cleanName)) {
                            double pct = accs.get(key).getAsDouble();
                            if (pct <= 1.0) {
                                totalPct += pct * 100.0;
                            } else {
                                totalPct += pct;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return Math.round(totalPct * 10.0) / 10.0;
    }

    /**
     * Calculates the total percentage (0 to 100) allocated to a specific mutual fund category/type across all existing goals.
     */
    public static double getMutualFundAllocatedPercentage(String mfType, List<GoalItem> goals, String excludeGoalName) {
        if (mfType == null || goals == null) return 0.0;
        String cleanType = mfType.trim().toLowerCase(Locale.US);
        double totalPct = 0.0;
        for (GoalItem g : goals) {
            if (g == null || g.contributionJson == null || g.contributionJson.trim().isEmpty()) continue;
            if (excludeGoalName != null && g.name != null && g.name.trim().equalsIgnoreCase(excludeGoalName.trim())) {
                continue;
            }
            try {
                JsonObject obj = JsonParser.parseString(g.contributionJson).getAsJsonObject();
                if (obj.has("mutual_funds") && obj.get("mutual_funds").isJsonArray()) {
                    JsonArray mfs = obj.getAsJsonArray("mutual_funds");
                    for (JsonElement el : mfs) {
                        if (!el.isJsonObject()) continue;
                        JsonObject rule = el.getAsJsonObject();
                        String val = rule.has("value") ? rule.get("value").getAsString().trim().toLowerCase(Locale.US) : "";
                        if (val.equals(cleanType)) {
                            double pct = 1.0;
                            if (rule.has("percentage")) {
                                try {
                                    pct = rule.get("percentage").getAsDouble();
                                } catch (Exception ignored) {}
                            }
                            if (pct <= 1.0) {
                                totalPct += pct * 100.0;
                            } else {
                                totalPct += pct;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return Math.round(totalPct * 10.0) / 10.0;
    }

    /**
     * Validates that no single source (bank account or mutual fund) will exceed 100% total allocation
     * when newGoal is added to existingGoals.
     * @return null if valid, or an error message detailing which source exceeds 100%.
     */
    public static String validateGoalAllocations(GoalItem newGoal, List<GoalItem> existingGoals) {
        if (newGoal == null || newGoal.contributionJson == null || newGoal.contributionJson.trim().isEmpty()) {
            return null;
        }
        try {
            JsonObject obj = JsonParser.parseString(newGoal.contributionJson).getAsJsonObject();

            // Validate Accounts
            if (obj.has("accounts") && obj.get("accounts").isJsonObject()) {
                JsonObject accs = obj.getAsJsonObject("accounts");
                for (String key : accs.keySet()) {
                    double incomingPct = accs.get(key).getAsDouble();
                    if (incomingPct <= 1.0) incomingPct *= 100.0;
                    double existingPct = getAccountAllocatedPercentage(key, existingGoals, newGoal.name);
                    if (existingPct + incomingPct > 100.0001) {
                        return "Total allocation for bank account '" + key + "' would be " +
                                String.format(Locale.US, "%.1f", (existingPct + incomingPct)) + "% (already " +
                                String.format(Locale.US, "%.1f", existingPct) + "% allocated across goals). Allocation must be <= 100%!";
                    }
                }
            }

            // Validate Mutual Funds
            if (obj.has("mutual_funds") && obj.get("mutual_funds").isJsonArray()) {
                JsonArray mfs = obj.getAsJsonArray("mutual_funds");
                for (JsonElement el : mfs) {
                    if (!el.isJsonObject()) continue;
                    JsonObject rule = el.getAsJsonObject();
                    String val = rule.has("value") ? rule.get("value").getAsString().trim() : "";
                    double incomingPct = 100.0;
                    if (rule.has("percentage")) {
                        try {
                            incomingPct = rule.get("percentage").getAsDouble();
                            if (incomingPct <= 1.0) incomingPct *= 100.0;
                        } catch (Exception ignored) {}
                    }
                    double existingPct = getMutualFundAllocatedPercentage(val, existingGoals, newGoal.name);
                    if (existingPct + incomingPct > 100.0001) {
                        return "Total allocation for mutual fund '" + val + "' would be " +
                                String.format(Locale.US, "%.1f", (existingPct + incomingPct)) + "% (already " +
                                String.format(Locale.US, "%.1f", existingPct) + "% allocated across goals). Allocation must be <= 100%!";
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static List<GoalItem> createDefaultGoals() {
        List<GoalItem> list = new ArrayList<>();
        list.add(new GoalItem("Health And Life Insurance", 60000.0, 15193.01, 25.3, 0.0,
                "{\n  \"accounts\": {},\n  \"mutual_funds\": [\n    {\n      \"column\": \"Savings Catgory\",\n      \"value\": \"Family Savings\",\n      \"percentage\": 1.0\n    }\n  ]\n}"));
        list.add(new GoalItem("Emergency Fund", 200000.0, 29008.74, 14.5, 0.0,
                "{\n  \"accounts\": {\n    \"HDFC SAVINGS\": 1.0,\n    \"PNB SAVINGS FROM PREVIOUS MONTH\": 1.0,\n    \"PNB\": 1.0,\n    \"SBI\": 1.0\n  },\n  \"mutual_funds\": [\n    {\n      \"column\": \"Savings Type\",\n      \"value\": \"Emergency Mutual Fund\",\n      \"percentage\": 1.0\n    }\n  ]\n}"));
        list.add(new GoalItem("Bike", 200000.0, 41407.97, 20.7, 0.0,
                "{\n  \"accounts\": {},\n  \"mutual_funds\": [\n    {\n      \"column\": \"Savings Type\",\n      \"value\": \"Mutual Fund\",\n      \"percentage\": \"0.3\"\n    }\n  ]\n}"));
        return list;
    }

    private static List<InsurancePolicy> createDefaultInsurance() {
        List<InsurancePolicy> list = new ArrayList<>();
        list.add(new InsurancePolicy("438739536", 5230.0, "15/03/2026", "26/03/2027", "1", "Brother LIC Policies", "Brother Savings", 100000.0, ""));
        list.add(new InsurancePolicy("403779399", 4594.0, "14/12/2025", "14/12/2026", "23", "Brother LIC Policies", "Brother Savings", 150000.0, ""));
        list.add(new InsurancePolicy("2856101499307900", 9168.0, "17/10/2026", "17/10/2026", "22", "Health Insurance", "Insurance", 500000.0, ""));
        list.add(new InsurancePolicy("438739535", 5047.0, "15/03/2026", "26/03/2027", "1", "LIC Policies", "Savings", 100000.0, ""));
        list.add(new InsurancePolicy("499966351", 5423.0, "17/03/2026", "17/03/2027", "2", "Sister LIC Policies", "Sister Savings", 100000.0, ""));
        list.add(new InsurancePolicy("403779400", 4586.0, "14/12/2025", "14/12/2026", "23", "Sister LIC Policies", "Sister Savings", 150000.0, ""));
        list.add(new InsurancePolicy("403779398", 4803.0, "14/12/2025", "14/12/2026", "23", "LIC Policies", "Savings", 150000.0, "Stopped. No further premiums from 14/12/2026."));
        list.add(new InsurancePolicy("C202635066", 6545.0, "15/03/2026", "31/03/2027", "1", "Tata AIA Policy", "Savings", 100000.0, ""));
        list.add(new InsurancePolicy("C321850816", 8298.0, "04/05/2026", "04/05/2027", "35", "Term Insurance", "Insurance", 10000000.0, ""));
        return list;
    }
}
