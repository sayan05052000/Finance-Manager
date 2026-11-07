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
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.BudgetItem;
import com.samiy.financemanager.data.model.CurrentCycle;
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
                    Toast.makeText(getContext(), "Synced with Google Sheet", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(getContext(), "Sync failed: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
