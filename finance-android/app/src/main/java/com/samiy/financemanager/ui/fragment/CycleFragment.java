package com.samiy.financemanager.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.data.CalculationEngine;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.BudgetItem;
import com.samiy.financemanager.data.model.CurrentCycle;
import com.samiy.financemanager.data.model.ExpenseTrackerItem;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.ui.view.CycleChartView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CycleFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvCycleDetailTitle, tvCycleDetailPeriod;
    private TextView tvAllocatedBudget, tvTotalCycleSpent, tvCycleRemainderVal;
    private TextView tvDailyLimitValueCycle;
    private LinearLayout layoutCycleSubCats, layoutCycleReceivables;

    // Interactive Chart & Detail Inspector Views
    private CycleChartView cycleChartView;
    private TextView btnRange6M, btnRange1Y, btnRangeAll;
    private View chipFilterBudget, chipFilterExpense, chipFilterInvestment;
    private TextView tvChipBudget, tvChipExpense, tvChipInvestment;
    private TextView tvChartSelectedTitle, tvChartSelectedStatus;
    private TextView tvChartDetailBudget, tvChartDetailExpense, tvChartDetailInvestment, tvChartDetailSurplus;

    // Expense Tracker Views
    private TextView tvTrackerGoodCount, tvTrackerAlertCount;
    private TextView btnTrackerFilterAll, btnTrackerFilterSavings, btnTrackerFilterExpenses;
    private LinearLayout layoutExpenseTrackerItems;
    private int currentTrackerFilter = 0; // 0=All, 1=Savings, 2=Expenses

    private int currentRangeMode = 0; // 0=6M, 1=1Y, 2=All
    private boolean isBudgetVisible = true;
    private boolean isExpenseVisible = true;
    private boolean isInvestmentVisible = true;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_cycle, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshCycle);
        tvCycleDetailTitle = v.findViewById(R.id.tvCycleDetailTitle);
        tvCycleDetailPeriod = v.findViewById(R.id.tvCycleDetailPeriod);
        tvAllocatedBudget = v.findViewById(R.id.tvAllocatedBudget);
        tvTotalCycleSpent = v.findViewById(R.id.tvTotalCycleSpent);
        tvCycleRemainderVal = v.findViewById(R.id.tvCycleRemainderVal);
        tvDailyLimitValueCycle = v.findViewById(R.id.tvDailyLimitValueCycle);
        layoutCycleSubCats = v.findViewById(R.id.layoutCycleSubCats);
        layoutCycleReceivables = v.findViewById(R.id.layoutCycleReceivables);

        // Chart components
        cycleChartView = v.findViewById(R.id.cycleChartView);
        btnRange6M = v.findViewById(R.id.btnRange6M);
        btnRange1Y = v.findViewById(R.id.btnRange1Y);
        btnRangeAll = v.findViewById(R.id.btnRangeAll);

        chipFilterBudget = v.findViewById(R.id.chipFilterBudget);
        chipFilterExpense = v.findViewById(R.id.chipFilterExpense);
        chipFilterInvestment = v.findViewById(R.id.chipFilterInvestment);
        tvChipBudget = v.findViewById(R.id.tvChipBudget);
        tvChipExpense = v.findViewById(R.id.tvChipExpense);
        tvChipInvestment = v.findViewById(R.id.tvChipInvestment);

        tvChartSelectedTitle = v.findViewById(R.id.tvChartSelectedTitle);
        tvChartSelectedStatus = v.findViewById(R.id.tvChartSelectedStatus);
        tvChartDetailBudget = v.findViewById(R.id.tvChartDetailBudget);
        tvChartDetailExpense = v.findViewById(R.id.tvChartDetailExpense);
        tvChartDetailInvestment = v.findViewById(R.id.tvChartDetailInvestment);
        tvChartDetailSurplus = v.findViewById(R.id.tvChartDetailSurplus);

        setupChartControls();

        // Expense Tracker components
        tvTrackerGoodCount = v.findViewById(R.id.tvTrackerGoodCount);
        tvTrackerAlertCount = v.findViewById(R.id.tvTrackerAlertCount);
        btnTrackerFilterAll = v.findViewById(R.id.btnTrackerFilterAll);
        btnTrackerFilterSavings = v.findViewById(R.id.btnTrackerFilterSavings);
        btnTrackerFilterExpenses = v.findViewById(R.id.btnTrackerFilterExpenses);
        layoutExpenseTrackerItems = v.findViewById(R.id.layoutExpenseTrackerItems);

        setupTrackerFilterControls();

        swipeRefresh.setOnRefreshListener(this::refreshData);

        populateUI();

        return v;
    }

    private void setupChartControls() {
        // Range Filters
        btnRange6M.setOnClickListener(v -> {
            currentRangeMode = 0;
            updateRangeButtons();
            populateChart();
        });

        btnRange1Y.setOnClickListener(v -> {
            currentRangeMode = 1;
            updateRangeButtons();
            populateChart();
        });

        btnRangeAll.setOnClickListener(v -> {
            currentRangeMode = 2;
            updateRangeButtons();
            populateChart();
        });

        // Metric Series Filters
        chipFilterBudget.setOnClickListener(v -> {
            isBudgetVisible = !isBudgetVisible;
            chipFilterBudget.setAlpha(isBudgetVisible ? 1.0f : 0.35f);
            cycleChartView.setSeriesVisibility(isBudgetVisible, isExpenseVisible, isInvestmentVisible);
        });

        chipFilterExpense.setOnClickListener(v -> {
            isExpenseVisible = !isExpenseVisible;
            chipFilterExpense.setAlpha(isExpenseVisible ? 1.0f : 0.35f);
            cycleChartView.setSeriesVisibility(isBudgetVisible, isExpenseVisible, isInvestmentVisible);
        });

        chipFilterInvestment.setOnClickListener(v -> {
            isInvestmentVisible = !isInvestmentVisible;
            chipFilterInvestment.setAlpha(isInvestmentVisible ? 1.0f : 0.35f);
            cycleChartView.setSeriesVisibility(isBudgetVisible, isExpenseVisible, isInvestmentVisible);
        });

        // Selection Listener for Detail Card
        cycleChartView.setOnCycleSelectedListener(this::updateSelectedCycleCard);
    }

    private void updateRangeButtons() {
        btnRange6M.setBackgroundResource(currentRangeMode == 0 ? R.drawable.bg_pill_emerald : 0);
        btnRange6M.setTextColor(currentRangeMode == 0 ? 0xFFFFFFFF : getResources().getColor(R.color.text_secondary));

        btnRange1Y.setBackgroundResource(currentRangeMode == 1 ? R.drawable.bg_pill_emerald : 0);
        btnRange1Y.setTextColor(currentRangeMode == 1 ? 0xFFFFFFFF : getResources().getColor(R.color.text_secondary));

        btnRangeAll.setBackgroundResource(currentRangeMode == 2 ? R.drawable.bg_pill_emerald : 0);
        btnRangeAll.setTextColor(currentRangeMode == 2 ? 0xFFFFFFFF : getResources().getColor(R.color.text_secondary));
    }

    private void updateSelectedCycleCard(CycleChartView.CycleBarItem item) {
        if (item == null) return;
        tvChartSelectedTitle.setText(item.fullCycle);
        tvChartSelectedStatus.setText(item.isActiveCycle ? "Active Cycle ★" : "Completed Cycle");
        tvChartSelectedStatus.setTextColor(item.isActiveCycle ? getResources().getColor(R.color.green_mint) : getResources().getColor(R.color.text_secondary));

        tvChartDetailBudget.setText(formatCurrency(item.budget));
        tvChartDetailExpense.setText(formatCurrency(item.expense));
        tvChartDetailInvestment.setText(formatCurrency(item.investment));

        double surplus = item.budget - item.expense;
        if (surplus >= 0) {
            tvChartDetailSurplus.setText("+" + formatCurrency(surplus));
            tvChartDetailSurplus.setTextColor(getResources().getColor(R.color.green_mint));
        } else {
            tvChartDetailSurplus.setText("-" + formatCurrency(Math.abs(surplus)));
            tvChartDetailSurplus.setTextColor(getResources().getColor(R.color.red_rose));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        populateUI();
    }

    public void populateUI() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null || db.currentCycle == null) return;

        CurrentCycle c = db.currentCycle;
        tvCycleDetailTitle.setText(c.cycleName);
        tvCycleDetailPeriod.setText(c.cycleStart + " – " + c.cycleEnd + " • " + c.daysLeft + " days left");

        tvAllocatedBudget.setText(formatCurrency(c.budget));
        tvTotalCycleSpent.setText(formatCurrency(c.totalSpend));
        tvCycleRemainderVal.setText(formatCurrency(c.remainder));
        tvDailyLimitValueCycle.setText(formatCurrency(c.canSpendPerDay) + " / day");

        // Populate Interactive Chart
        populateChart();

        // Populate Expense Tracker initially and on every data change
        populateExpenseTracker();

        // Subcategories
        layoutCycleSubCats.removeAllViews();
        if (c.subCategories != null) {
            for (CurrentCycle.SubCategorySpend sc : c.subCategories) {
                LinearLayout item = new LinearLayout(getContext());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackgroundResource(R.drawable.bg_card);
                item.setPadding(32, 24, 32, 24);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                item.setLayoutParams(lp);

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);

                TextView name = new TextView(getContext());
                name.setText(sc.name);
                name.setTextColor(getResources().getColor(R.color.text_primary));
                name.setTextSize(13);
                name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView amt = new TextView(getContext());
                amt.setText(formatCurrency(sc.amount) + " (" + String.format(Locale.US, "%.1f", sc.percentage) + "%)");
                amt.setTextColor(getResources().getColor(R.color.text_secondary));
                amt.setTextSize(13);

                row.addView(name);
                row.addView(amt);
                item.addView(row);
                layoutCycleSubCats.addView(item);
            }
        }

        // Receivables
        layoutCycleReceivables.removeAllViews();
        if (c.receivables != null) {
            for (CurrentCycle.ReceivableItem r : c.receivables) {
                LinearLayout item = new LinearLayout(getContext());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackgroundResource(R.drawable.bg_card);
                item.setPadding(32, 24, 32, 24);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                item.setLayoutParams(lp);

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);

                TextView src = new TextView(getContext());
                src.setText(r.source);
                src.setTextColor(getResources().getColor(R.color.text_primary));
                src.setTextSize(13);
                src.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView amt = new TextView(getContext());
                amt.setText(formatCurrency(r.amount));
                amt.setTextColor(getResources().getColor(R.color.blue_sky));
                amt.setTextSize(13);

                row.addView(src);
                row.addView(amt);
                item.addView(row);

                if (r.note != null && !r.note.isEmpty()) {
                    TextView note = new TextView(getContext());
                    note.setText(r.note);
                    note.setTextColor(getResources().getColor(R.color.text_muted));
                    note.setTextSize(11);
                    note.setPadding(0, 8, 0, 0);
                    item.addView(note);
                }

                layoutCycleReceivables.addView(item);
            }
        }
    }


    private void setupTrackerFilterControls() {
        updateTrackerFilterButtons();
        btnTrackerFilterAll.setOnClickListener(v -> {
            currentTrackerFilter = 0;
            updateTrackerFilterButtons();
            populateExpenseTracker();
        });
        btnTrackerFilterSavings.setOnClickListener(v -> {
            currentTrackerFilter = 1;
            updateTrackerFilterButtons();
            populateExpenseTracker();
        });
        btnTrackerFilterExpenses.setOnClickListener(v -> {
            currentTrackerFilter = 2;
            updateTrackerFilterButtons();
            populateExpenseTracker();
        });
    }

    private void updateTrackerFilterButtons() {
        btnTrackerFilterAll.setBackgroundResource(currentTrackerFilter == 0 ? R.drawable.bg_pill_emerald : 0);
        btnTrackerFilterAll.setTextColor(currentTrackerFilter == 0 ? 0xFFFFFFFF : getResources().getColor(R.color.text_secondary));

        btnTrackerFilterSavings.setBackgroundResource(currentTrackerFilter == 1 ? R.drawable.bg_pill_emerald : 0);
        btnTrackerFilterSavings.setTextColor(currentTrackerFilter == 1 ? 0xFFFFFFFF : getResources().getColor(R.color.text_secondary));

        btnTrackerFilterExpenses.setBackgroundResource(currentTrackerFilter == 2 ? R.drawable.bg_pill_emerald : 0);
        btnTrackerFilterExpenses.setTextColor(currentTrackerFilter == 2 ? 0xFFFFFFFF : getResources().getColor(R.color.text_secondary));
    }

    private void populateExpenseTracker() {
        if (!isAdded()) return;
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null) return;
        if (db.expenseTracker == null || db.expenseTracker.isEmpty()) {
            CalculationEngine.recalculate(db);
        }
        if (db.expenseTracker == null || db.expenseTracker.isEmpty()) return;

        List<ExpenseTrackerItem> allItems = db.expenseTracker;
        int goodCount = 0;
        int alertCount = 0;
        int savingsCount = 0;
        int expensesCount = 0;

        for (ExpenseTrackerItem item : allItems) {
            if (item.isSaving()) savingsCount++;
            else expensesCount++;

            if (item.isGood()) goodCount++;
            else alertCount++;
        }

        btnTrackerFilterAll.setText("All (" + allItems.size() + ")");
        btnTrackerFilterSavings.setText("Savings (" + savingsCount + ")");
        btnTrackerFilterExpenses.setText("Expenses (" + expensesCount + ")");

        tvTrackerGoodCount.setText(goodCount + " Good");
        tvTrackerAlertCount.setText(alertCount + " Alert");

        layoutExpenseTrackerItems.removeAllViews();

        for (ExpenseTrackerItem item : allItems) {
            // Apply filter
            if (currentTrackerFilter == 1 && !item.isSaving()) continue;
            if (currentTrackerFilter == 2 && item.isSaving()) continue;

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_pill_dark);
            card.setPadding(32, 28, 32, 28);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            card.setLayoutParams(lp);

            // Row 1: SubCategory Name + Type + Status Badge
            LinearLayout headerRow = new LinearLayout(getContext());
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            LinearLayout titleContainer = new LinearLayout(getContext());
            titleContainer.setOrientation(LinearLayout.VERTICAL);
            titleContainer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView tvName = new TextView(getContext());
            tvName.setText(item.subCategory);
            tvName.setTextColor(0xFFFFFFFF);
            tvName.setTextSize(13.5f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView tvType = new TextView(getContext());
            tvType.setText(item.expenseType != null && !item.expenseType.isEmpty() ? item.expenseType : (item.isSaving() ? "Savings" : "Expense"));
            tvType.setTextColor(getResources().getColor(item.isSaving() ? R.color.green_mint : R.color.blue_sky));
            tvType.setTextSize(10.5f);

            titleContainer.addView(tvName);
            titleContainer.addView(tvType);

            // Status Badge based on user rule:
            // If Saving: current >= past -> good (Saved More); current < past -> alert (Less Saved)
            // If Expense: current <= past -> good/better (Reduced); current > past -> alert (Higher Expense)
            TextView tvBadge = new TextView(getContext());
            tvBadge.setTextSize(10.5f);
            tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            tvBadge.setPadding(20, 8, 20, 8);

            double diff = item.getCostDifference();
            String diffStr = formatCurrency(Math.abs(diff));

            if (item.isSaving()) {
                if (item.currentMonthCost >= item.previousMonthCost) {
                    tvBadge.setText(diff > 0 ? "▲ +" + diffStr + " (Good)" : "✓ On Track");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_good);
                    tvBadge.setTextColor(getResources().getColor(R.color.green_mint));
                } else {
                    tvBadge.setText("▼ -" + diffStr + " (Less Saved)");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                    tvBadge.setTextColor(getResources().getColor(R.color.amber_gold));
                }
            } else {
                if (item.currentMonthCost <= item.previousMonthCost) {
                    tvBadge.setText(diff < 0 ? "▼ -" + diffStr + " (Better)" : "✓ On Track");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_good);
                    tvBadge.setTextColor(getResources().getColor(R.color.green_mint));
                } else {
                    tvBadge.setText("▲ +" + diffStr + " (Higher)");
                    tvBadge.setBackgroundResource(R.drawable.bg_badge_warning);
                    tvBadge.setTextColor(getResources().getColor(R.color.red_rose));
                }
            }

            headerRow.addView(titleContainer);
            headerRow.addView(tvBadge);
            card.addView(headerRow);

            // Row 2: 3 Values Columns (Current Cycle, Past Cycle, Budget / Target)
            LinearLayout valuesRow = new LinearLayout(getContext());
            valuesRow.setOrientation(LinearLayout.HORIZONTAL);
            valuesRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            valuesRow.setPadding(0, 18, 0, 8);

            // Col 1: Current Cycle
            LinearLayout colCurrent = new LinearLayout(getContext());
            colCurrent.setOrientation(LinearLayout.VERTICAL);
            colCurrent.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView lblCurrent = new TextView(getContext());
            lblCurrent.setText("Current Cycle");
            lblCurrent.setTextSize(10f);
            lblCurrent.setTextColor(getResources().getColor(R.color.text_secondary));

            TextView valCurrent = new TextView(getContext());
            valCurrent.setText(formatCurrency(item.currentMonthCost));
            valCurrent.setTextSize(13f);
            valCurrent.setTypeface(null, android.graphics.Typeface.BOLD);
            int curColor = item.isGood() ? getResources().getColor(R.color.green_mint)
                    : (item.isSaving() ? getResources().getColor(R.color.amber_gold) : getResources().getColor(R.color.red_rose));
            valCurrent.setTextColor(curColor);

            colCurrent.addView(lblCurrent);
            colCurrent.addView(valCurrent);

            // Col 2: Past Cycle
            LinearLayout colPast = new LinearLayout(getContext());
            colPast.setOrientation(LinearLayout.VERTICAL);
            colPast.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView lblPast = new TextView(getContext());
            lblPast.setText("Past Cycle");
            lblPast.setTextSize(10f);
            lblPast.setTextColor(getResources().getColor(R.color.text_secondary));

            TextView valPast = new TextView(getContext());
            valPast.setText(formatCurrency(item.previousMonthCost));
            valPast.setTextSize(13f);
            valPast.setTypeface(null, android.graphics.Typeface.BOLD);
            valPast.setTextColor(getResources().getColor(R.color.text_primary));

            colPast.addView(lblPast);
            colPast.addView(valPast);

            // Col 3: Budget / Target
            LinearLayout colTarget = new LinearLayout(getContext());
            colTarget.setOrientation(LinearLayout.VERTICAL);
            colTarget.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            TextView lblTarget = new TextView(getContext());
            lblTarget.setText("Target / Budget");
            lblTarget.setTextSize(10f);
            lblTarget.setTextColor(getResources().getColor(R.color.text_secondary));

            TextView valTarget = new TextView(getContext());
            valTarget.setText(formatCurrency(item.amountToBePaid));
            valTarget.setTextSize(13f);
            valTarget.setTypeface(null, android.graphics.Typeface.BOLD);
            valTarget.setTextColor(getResources().getColor(R.color.blue_sky));

            colTarget.addView(lblTarget);
            colTarget.addView(valTarget);

            valuesRow.addView(colCurrent);
            valuesRow.addView(colPast);
            valuesRow.addView(colTarget);
            card.addView(valuesRow);

            layoutExpenseTrackerItems.addView(card);
        }
    }

    private void populateChart() {
        if (!isAdded()) return;
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null || db.budget == null || db.budget.isEmpty()) return;

        List<CycleChartView.CycleBarItem> allBars = new ArrayList<>();
        CurrentCycle activeCycle = db.currentCycle != null ? db.currentCycle : new CurrentCycle();

        for (BudgetItem b : db.budget) {
            String shortMonth = b.month != null && b.month.length() >= 3 ? b.month.substring(0, 3) : "Mo";
            boolean isActive = (b.year == activeCycle.cycleYear && b.monthNumber == activeCycle.cycleMonthNumber);
            String fullTitle = (b.month != null ? b.month : "") + " " + b.year;

            double exp = b.spend;
            double bud = b.budget;
            double inv = b.investment;

            if (isActive) {
                if (activeCycle.totalSpend > 0) exp = activeCycle.totalSpend;
                if (activeCycle.budget > 0) bud = activeCycle.budget;
            }

            allBars.add(new CycleChartView.CycleBarItem(
                    shortMonth,
                    fullTitle,
                    bud,
                    exp,
                    inv,
                    b.year,
                    b.monthNumber,
                    isActive
            ));
        }

        // Apply Range Filter
        List<CycleChartView.CycleBarItem> filtered = new ArrayList<>();
        int count = allBars.size();
        if (currentRangeMode == 0) {
            // 6 Months
            int start = Math.max(0, count - 6);
            filtered.addAll(allBars.subList(start, count));
        } else if (currentRangeMode == 1) {
            // 1 Year (12 Months)
            int start = Math.max(0, count - 12);
            filtered.addAll(allBars.subList(start, count));
        } else {
            // All
            filtered.addAll(allBars);
        }

        cycleChartView.setData(filtered);
        cycleChartView.setSeriesVisibility(isBudgetVisible, isExpenseVisible, isInvestmentVisible);
    }

    private void refreshData() {
        FinanceRepository.getInstance(requireContext()).syncWithGoogleSheets(new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase db) {
                if (isAdded()) {
                    swipeRefresh.setRefreshing(false);
                    populateUI();
                    FinMngrToast.showSuccess(getContext(), "Synced with Google Sheet");
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    swipeRefresh.setRefreshing(false);
                    FinMngrToast.showError(getContext(), "Sync failed: " + error);
                }
            }
        });
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
