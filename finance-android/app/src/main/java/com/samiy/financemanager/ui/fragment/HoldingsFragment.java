package com.samiy.financemanager.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.MutualFundTransaction;
import com.samiy.financemanager.data.model.PortfolioSummary;
import com.samiy.financemanager.data.model.SavingsTypeSummary;
import com.samiy.financemanager.ui.adapter.HoldingsAdapter;
import com.samiy.financemanager.ui.view.HoldingsGrowthChartView;
import com.samiy.financemanager.util.DateTimeHelper;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HoldingsFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalInvested, tvMarketValue, tvMarketValueNavDate, tvOneDayChange, tvOneDayChangePercent;
    private TextView tvTotalReturns, tvTotalReturnsRoi, tvPostTaxEstimate, tvActiveSchemes;
    private TextView tvHoldingsHeader;
    private Spinner spHoldingsSort;
    private LinearLayout layoutSavingsTypeBreakdown;
    private RecyclerView rvHoldings;
    private HoldingsAdapter adapter;
    private int currentSortIndex = 0; // 0: Current Value (Default), 1: Invested Amount, 2: Day Change, 3: Name

    // Growth Chart Components
    private HoldingsGrowthChartView holdingsGrowthChartView;
    private TextView btnGrowthRange3M, btnGrowthRange6M, btnGrowthRange1Y, btnGrowthRangeAll;
    private LinearLayout layoutGrowthSavingsTypeChips;
    private TextView tvGrowthScrubDate, tvGrowthScrubInvested, tvGrowthScrubValuation, tvGrowthReturnsBadge;
    private int currentGrowthRangeMode = 3; // 0=3M, 1=6M, 2=1Y, 3=All (default)
    private String selectedGrowthSavingsType = "All";

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_holdings, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshHoldings);
        tvTotalInvested = v.findViewById(R.id.tvTotalInvested);
        tvMarketValue = v.findViewById(R.id.tvMarketValue);
        tvMarketValueNavDate = v.findViewById(R.id.tvMarketValueNavDate);
        tvOneDayChange = v.findViewById(R.id.tvOneDayChange);
        tvOneDayChangePercent = v.findViewById(R.id.tvOneDayChangePercent);
        tvTotalReturns = v.findViewById(R.id.tvTotalReturns);
        tvTotalReturnsRoi = v.findViewById(R.id.tvTotalReturnsRoi);
        tvPostTaxEstimate = v.findViewById(R.id.tvPostTaxEstimate);
        tvActiveSchemes = v.findViewById(R.id.tvActiveSchemes);
        layoutSavingsTypeBreakdown = v.findViewById(R.id.layoutSavingsTypeBreakdown);
        rvHoldings = v.findViewById(R.id.rvHoldings);
        tvHoldingsHeader = v.findViewById(R.id.tvHoldingsHeader);
        spHoldingsSort = v.findViewById(R.id.spHoldingsSort);

        if (spHoldingsSort != null) {
            String[] sortOptions = new String[] {
                    "Current Value",
                    "Invested Amount",
                    "Day Change",
                    "Name",
                    "NAV Date"
            };
            ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, sortOptions);
            sortAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spHoldingsSort.setAdapter(sortAdapter);
            spHoldingsSort.setSelection(currentSortIndex);

            spHoldingsSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(parent);
                    if (currentSortIndex != position) {
                        currentSortIndex = position;
                        applySortingAndSubmit();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        rvHoldings.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HoldingsAdapter();
        rvHoldings.setAdapter(adapter);

        Button btnAddMfTx = v.findViewById(R.id.btnAddMfTx);
        Button btnViewMfTransactions = v.findViewById(R.id.btnViewMfTransactions);
        Button btnRefreshHoldings = v.findViewById(R.id.btnRefreshHoldings);
        Button btnSipTracer = v.findViewById(R.id.btnSipTracer);
        if (btnSipTracer != null) {
            btnSipTracer.setOnClickListener(view -> {
                com.samiy.financemanager.ui.dialog.SipTracerBottomSheet dialog = new com.samiy.financemanager.ui.dialog.SipTracerBottomSheet();
                dialog.show(getParentFragmentManager(), "SipTracerDialog");
            });
        }

        if (btnAddMfTx != null) {
            btnAddMfTx.setOnClickListener(view -> {
                com.samiy.financemanager.ui.dialog.AddMutualFundTransactionBottomSheet dialog = new com.samiy.financemanager.ui.dialog.AddMutualFundTransactionBottomSheet();
                dialog.setListener(this::populateUI);
                dialog.show(getParentFragmentManager(), "AddMfTxDialog");
            });
        }

        if (btnViewMfTransactions != null) {
            btnViewMfTransactions.setOnClickListener(view -> {
                com.samiy.financemanager.ui.dialog.ViewMfTransactionsBottomSheet dialog = com.samiy.financemanager.ui.dialog.ViewMfTransactionsBottomSheet.newInstance(null, null);
                dialog.show(getParentFragmentManager(), "ViewMfTxDialog");
            });
        }

        adapter.setOnHoldingActionListener(new HoldingsAdapter.OnHoldingActionListener() {
            @Override
            public void onViewTransactions(Holding h) {
                if (h != null) {
                    String subCat = (h.savingsType != null && !h.savingsType.trim().isEmpty())
                            ? h.savingsType.trim() : h.subCategory;
                    com.samiy.financemanager.ui.dialog.ViewMfTransactionsBottomSheet dialog =
                            com.samiy.financemanager.ui.dialog.ViewMfTransactionsBottomSheet.newInstance(
                                    h.schemeName, null, subCat, h.schemeId, h.schemeCode);
                    dialog.show(getParentFragmentManager(), "ViewMfTxDialog");
                }
            }

            @Override
            public void onAddTransaction(Holding h) {
                if (h != null) {
                    com.samiy.financemanager.ui.dialog.AddMutualFundTransactionBottomSheet dialog =
                            com.samiy.financemanager.ui.dialog.AddMutualFundTransactionBottomSheet.newInstanceForHolding(h);
                    dialog.setListener(HoldingsFragment.this::populateUI);
                    dialog.show(getParentFragmentManager(), "AddMfTxDialog");
                }
            }
        });

        if (btnRefreshHoldings != null) {
            btnRefreshHoldings.setOnClickListener(view -> refreshData());
        }

        // Growth Chart Components
        holdingsGrowthChartView = v.findViewById(R.id.holdingsGrowthChartView);
        btnGrowthRange3M = v.findViewById(R.id.btnGrowthRange3M);
        btnGrowthRange6M = v.findViewById(R.id.btnGrowthRange6M);
        btnGrowthRange1Y = v.findViewById(R.id.btnGrowthRange1Y);
        btnGrowthRangeAll = v.findViewById(R.id.btnGrowthRangeAll);
        layoutGrowthSavingsTypeChips = v.findViewById(R.id.layoutGrowthSavingsTypeChips);
        tvGrowthScrubDate = v.findViewById(R.id.tvGrowthScrubDate);
        tvGrowthScrubInvested = v.findViewById(R.id.tvGrowthScrubInvested);
        tvGrowthScrubValuation = v.findViewById(R.id.tvGrowthScrubValuation);
        tvGrowthReturnsBadge = v.findViewById(R.id.tvGrowthReturnsBadge);

        setupGrowthChartControls();

        swipeRefresh.setOnRefreshListener(this::refreshData);

        populateUI();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateUI();
    }

    public void populateUI() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null) return;

        Context context = requireContext();
        PortfolioSummary ps = db.portfolioSummary != null ? db.portfolioSummary : new PortfolioSummary();

        tvTotalInvested.setText(formatCurrency(ps.totalInvested));
        tvMarketValue.setText(formatCurrency(ps.currentValuation));

        // Latest NAV date
        String latestNavDate = (ps.latestNavDate != null && !ps.latestNavDate.isEmpty())
                ? ps.latestNavDate : getLatestNavDate(db.holdings);
        if (tvMarketValueNavDate != null) {
            if (latestNavDate != null && !latestNavDate.isEmpty()) {
                tvMarketValueNavDate.setText("Latest NAV: " + latestNavDate);
                tvMarketValueNavDate.setVisibility(View.VISIBLE);
            } else {
                tvMarketValueNavDate.setVisibility(View.GONE);
            }
        }

        // Market Value: show red if portfolio is in loss
        if (ps.totalReturns < 0 || (ps.totalInvested > 0 && ps.currentValuation < ps.totalInvested)) {
            tvMarketValue.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
        } else {
            tvMarketValue.setTextColor(ContextCompat.getColor(context, R.color.green_mint));
        }

        // 1-Day Market Change (Red if negative/loss, Green if positive/gain, Neutral if zero)
        if (ps.oneDayGain < 0) {
            tvOneDayChange.setText("-" + formatCurrency(Math.abs(ps.oneDayGain)));
            tvOneDayChange.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
            tvOneDayChangePercent.setText(String.format(Locale.US, "%.2f", ps.oneDayGainPercent) + "% today");
            tvOneDayChangePercent.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
        } else if (ps.oneDayGain > 0) {
            tvOneDayChange.setText("+" + formatCurrency(ps.oneDayGain));
            tvOneDayChange.setTextColor(ContextCompat.getColor(context, R.color.green_mint));
            tvOneDayChangePercent.setText("+" + String.format(Locale.US, "%.2f", ps.oneDayGainPercent) + "% today");
            tvOneDayChangePercent.setTextColor(ContextCompat.getColor(context, R.color.green_emerald));
        } else {
            tvOneDayChange.setText(formatCurrency(0));
            tvOneDayChange.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            tvOneDayChangePercent.setText("0.00% today");
            tvOneDayChangePercent.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Total Returns (ROI) (Red if negative/loss, Green if positive/gain, Neutral if zero)
        if (ps.totalReturns < 0 || ps.totalReturnsRoi < 0) {
            tvTotalReturns.setText("-" + formatCurrency(Math.abs(ps.totalReturns)));
            tvTotalReturns.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
            tvTotalReturnsRoi.setText(String.format(Locale.US, "%.2f", ps.totalReturnsRoi) + "% ROI");
            tvTotalReturnsRoi.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
        } else if (ps.totalReturns > 0 || ps.totalReturnsRoi > 0) {
            tvTotalReturns.setText("+" + formatCurrency(ps.totalReturns));
            tvTotalReturns.setTextColor(ContextCompat.getColor(context, R.color.green_mint));
            tvTotalReturnsRoi.setText("+" + String.format(Locale.US, "%.2f", ps.totalReturnsRoi) + "% ROI");
            tvTotalReturnsRoi.setTextColor(ContextCompat.getColor(context, R.color.green_emerald));
        } else {
            tvTotalReturns.setText(formatCurrency(0));
            tvTotalReturns.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            tvTotalReturnsRoi.setText("0.00% ROI");
            tvTotalReturnsRoi.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Net Post-Tax Estimate
        if (ps.afterTaxApprox < 0) {
            tvPostTaxEstimate.setText("-" + formatCurrency(Math.abs(ps.afterTaxApprox)));
            tvPostTaxEstimate.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
        } else {
            tvPostTaxEstimate.setText("+" + formatCurrency(ps.afterTaxApprox));
            tvPostTaxEstimate.setTextColor(ContextCompat.getColor(context, R.color.blue_sky));
        }
        tvActiveSchemes.setText(ps.totalAssetsCount + " Assets");

        // Savings Type Breakdown
        layoutSavingsTypeBreakdown.removeAllViews();
        if (ps.savingsTypeBreakdown != null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            for (SavingsTypeSummary sts : ps.savingsTypeBreakdown) {
                View itemView = inflater.inflate(R.layout.item_savings_type, layoutSavingsTypeBreakdown, false);
                TextView tvName = itemView.findViewById(R.id.tvSavingsTypeName);
                TextView tvCount = itemView.findViewById(R.id.tvSavingsTypeHoldingsCount);
                TextView tv1D = itemView.findViewById(R.id.tvSavingsType1DChange);
                TextView tvVal = itemView.findViewById(R.id.tvSavingsTypeValuation);

                tvName.setText(sts.name);
                tvCount.setText(sts.holdingsCount + " holdings");

                if (sts.oneDayChange < 0) {
                    tv1D.setText("-" + formatCurrency(Math.abs(sts.oneDayChange)));
                    tv1D.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
                } else if (sts.oneDayChange > 0) {
                    tv1D.setText("+" + formatCurrency(sts.oneDayChange));
                    tv1D.setTextColor(ContextCompat.getColor(context, R.color.green_mint));
                } else {
                    tv1D.setText(formatCurrency(0));
                    tv1D.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                }
                tvVal.setText(formatCurrency(sts.currentValuation));

                layoutSavingsTypeBreakdown.addView(itemView);
            }
        }

        // Holdings List
        if (db.holdings != null) {
            if (tvHoldingsHeader != null) {
                if (latestNavDate != null && !latestNavDate.isEmpty()) {
                    tvHoldingsHeader.setText("Holdings Portfolio (" + db.holdings.size() + ") • " + latestNavDate);
                } else {
                    tvHoldingsHeader.setText("Holdings Portfolio (" + db.holdings.size() + ")");
                }
            }
            applySortingAndSubmit();
            adapter.setMutualFundTransactions(db.mutualFundTransactions);
        }

        // Populate Portfolio Growth Chart
        populateGrowthSavingsTypeChips(db);
        populateGrowthChart();
    }

    private void applySortingAndSubmit() {
        if (!isAdded()) return;
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null || db.holdings == null) return;

        List<Holding> sortedList = new ArrayList<>(db.holdings);
        sortedList.sort((h1, h2) -> {
            int cmp = 0;
            switch (currentSortIndex) {
                case 0: // Current Value (High to Low)
                    cmp = Double.compare(h2.currentValue, h1.currentValue);
                    break;
                case 1: // Invested Amount (High to Low)
                    cmp = Double.compare(h2.investedValue, h1.investedValue);
                    break;
                case 2: // Day Change (High to Low)
                    cmp = Double.compare(h2.oneDayChange, h1.oneDayChange);
                    break;
                case 3: // Name (A to Z)
                    String name1 = h1.schemeName != null ? h1.schemeName.trim() : "";
                    String name2 = h2.schemeName != null ? h2.schemeName.trim() : "";
                    cmp = name1.compareToIgnoreCase(name2);
                    break;
                case 4: // NAV Date (Newest to Oldest)
                    long d1 = h1.getNavEpochDay();
                    long d2 = h2.getNavEpochDay();
                    cmp = Long.compare(d2, d1);
                    break;
            }
            if (cmp == 0 && currentSortIndex != 3) {
                String name1 = h1.schemeName != null ? h1.schemeName.trim() : "";
                String name2 = h2.schemeName != null ? h2.schemeName.trim() : "";
                cmp = name1.compareToIgnoreCase(name2);
            }
            return cmp;
        });

        adapter.setItems(sortedList);
    }

    public static String getLatestNavDate(List<Holding> holdings) {
        if (holdings == null || holdings.isEmpty()) return "";
        String latestDate = "";
        long maxEpoch = 0L;
        for (Holding h : holdings) {
            if (h != null) {
                long epoch = h.getNavEpochDay();
                if (epoch > maxEpoch) {
                    maxEpoch = epoch;
                    latestDate = h.getFormattedNavDate();
                }
            }
        }
        return latestDate;
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
        String formatted = currencyFormat.format(amount).replace("INR", "₹").replace(".00", "").trim();
        while (formatted.startsWith("₹₹")) {
            formatted = formatted.substring(1);
        }
        if (!formatted.startsWith("₹") && !formatted.startsWith("-₹") && !formatted.startsWith("+₹")) {
            formatted = "₹" + formatted;
        }
        return formatted;
    }

    private void setupGrowthChartControls() {
        if (holdingsGrowthChartView == null) return;
        updateGrowthRangeButtons();

        if (btnGrowthRange3M != null) {
            btnGrowthRange3M.setOnClickListener(v -> {
                currentGrowthRangeMode = 0;
                updateGrowthRangeButtons();
                populateGrowthChart();
            });
        }

        if (btnGrowthRange6M != null) {
            btnGrowthRange6M.setOnClickListener(v -> {
                currentGrowthRangeMode = 1;
                updateGrowthRangeButtons();
                populateGrowthChart();
            });
        }

        if (btnGrowthRange1Y != null) {
            btnGrowthRange1Y.setOnClickListener(v -> {
                currentGrowthRangeMode = 2;
                updateGrowthRangeButtons();
                populateGrowthChart();
            });
        }

        if (btnGrowthRangeAll != null) {
            btnGrowthRangeAll.setOnClickListener(v -> {
                currentGrowthRangeMode = 3;
                updateGrowthRangeButtons();
                populateGrowthChart();
            });
        }

        holdingsGrowthChartView.setOnGrowthPointSelectedListener(point -> {
            if (point == null || !isAdded()) return;
            tvGrowthScrubDate.setText(point.fullDate != null && !point.fullDate.isEmpty() ? point.fullDate : point.dateLabel);
            tvGrowthScrubInvested.setText("Inv: " + formatCurrency(point.invested));
            tvGrowthScrubValuation.setText("Val: " + formatCurrency(point.valuation));

            double ret = point.returns;
            double pct = point.returnsPct;
            String sign = ret >= 0 ? "+" : "-";
            tvGrowthReturnsBadge.setText(sign + formatCurrency(Math.abs(ret)) + " (" + String.format(Locale.US, "%.2f", pct) + "%)");
            tvGrowthReturnsBadge.setTextColor(ContextCompat.getColor(requireContext(), ret >= 0 ? R.color.green_mint : R.color.red_rose));
        });
    }

    private void updateGrowthRangeButtons() {
        if (!isAdded() || btnGrowthRange3M == null) return;
        btnGrowthRange3M.setBackgroundResource(currentGrowthRangeMode == 0 ? R.drawable.bg_pill_emerald : 0);
        btnGrowthRange3M.setTextColor(ContextCompat.getColor(requireContext(), currentGrowthRangeMode == 0 ? R.color.text_on_primary : R.color.text_secondary));

        btnGrowthRange6M.setBackgroundResource(currentGrowthRangeMode == 1 ? R.drawable.bg_pill_emerald : 0);
        btnGrowthRange6M.setTextColor(ContextCompat.getColor(requireContext(), currentGrowthRangeMode == 1 ? R.color.text_on_primary : R.color.text_secondary));

        btnGrowthRange1Y.setBackgroundResource(currentGrowthRangeMode == 2 ? R.drawable.bg_pill_emerald : 0);
        btnGrowthRange1Y.setTextColor(ContextCompat.getColor(requireContext(), currentGrowthRangeMode == 2 ? R.color.text_on_primary : R.color.text_secondary));

        btnGrowthRangeAll.setBackgroundResource(currentGrowthRangeMode == 3 ? R.drawable.bg_pill_emerald : 0);
        btnGrowthRangeAll.setTextColor(ContextCompat.getColor(requireContext(), currentGrowthRangeMode == 3 ? R.color.text_on_primary : R.color.text_secondary));
    }

    private void populateGrowthSavingsTypeChips(FinanceDatabase db) {
        if (!isAdded() || layoutGrowthSavingsTypeChips == null || db == null) return;
        layoutGrowthSavingsTypeChips.removeAllViews();

        Set<String> categories = new LinkedHashSet<>();
        categories.add("All");

        // Add from portfolio summary breakdown
        if (db.portfolioSummary != null && db.portfolioSummary.savingsTypeBreakdown != null) {
            for (SavingsTypeSummary sts : db.portfolioSummary.savingsTypeBreakdown) {
                if (sts.name != null && !sts.name.trim().isEmpty()) {
                    categories.add(sts.name.trim());
                }
            }
        }

        // Add from holdings
        if (db.holdings != null) {
            for (Holding h : db.holdings) {
                String st = (h.savingsType != null && !h.savingsType.trim().isEmpty()) ? h.savingsType.trim() : h.subCategory;
                if (st != null && !st.trim().isEmpty()) {
                    categories.add(st.trim());
                }
            }
        }

        // Add from mutual fund transactions
        if (db.mutualFundTransactions != null) {
            for (MutualFundTransaction tx : db.mutualFundTransactions) {
                if (tx.subCategory != null && !tx.subCategory.trim().isEmpty()) {
                    categories.add(tx.subCategory.trim());
                }
            }
        }

        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);
        int dp12 = (int) (12 * getResources().getDisplayMetrics().density);
        int dp4 = (int) (4 * getResources().getDisplayMetrics().density);

        for (String cat : categories) {
            TextView chip = new TextView(getContext());
            chip.setText(cat);
            chip.setTextSize(11f);
            chip.setPadding(dp12, dp4, dp12, dp4);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp8, 0);
            chip.setLayoutParams(lp);

            boolean isSelected = cat.equalsIgnoreCase(selectedGrowthSavingsType);
            chip.setBackgroundResource(isSelected ? R.drawable.bg_pill_emerald : R.drawable.bg_pill_dark);
            chip.setTextColor(ContextCompat.getColor(requireContext(), isSelected ? R.color.text_on_primary : R.color.text_secondary));
            if (isSelected) chip.setTypeface(null, android.graphics.Typeface.BOLD);

            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                selectedGrowthSavingsType = cat;
                populateGrowthSavingsTypeChips(db);
                populateGrowthChart();
            });

            layoutGrowthSavingsTypeChips.addView(chip);
        }
    }

    private void populateGrowthChart() {
        if (!isAdded() || holdingsGrowthChartView == null) return;
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null || db.mutualFundTransactions == null || db.mutualFundTransactions.isEmpty()) return;

        boolean isAll = "All".equalsIgnoreCase(selectedGrowthSavingsType);

        List<MutualFundTransaction> filteredTx = new ArrayList<>();
        for (MutualFundTransaction tx : db.mutualFundTransactions) {
            if (isAll) {
                filteredTx.add(tx);
            } else if (tx.subCategory != null && tx.subCategory.equalsIgnoreCase(selectedGrowthSavingsType)) {
                filteredTx.add(tx);
            }
        }

        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        SimpleDateFormat sdfLabel = new SimpleDateFormat("MMM ''yy", Locale.US);

        // Sort chronologically ascending
        filteredTx.sort((t1, t2) -> {
            long ts1 = parseTxTimestamp(t1.date);
            long ts2 = parseTxTimestamp(t2.date);
            return Long.compare(ts1, ts2);
        });

        // Group transactions by date
        Map<String, List<MutualFundTransaction>> dateGroupMap = new LinkedHashMap<>();
        for (MutualFundTransaction tx : filteredTx) {
            String cleanDate = DateTimeHelper.formatDisplayDate(tx.date);
            if (cleanDate == null || cleanDate.isEmpty()) continue;
            if (!dateGroupMap.containsKey(cleanDate)) {
                dateGroupMap.put(cleanDate, new ArrayList<>());
            }
            dateGroupMap.get(cleanDate).add(tx);
        }

        List<HoldingsGrowthChartView.GrowthDataPoint> allPoints = new ArrayList<>();
        Map<String, Double> unitsByScheme = new HashMap<>();
        Map<String, Double> investedByScheme = new HashMap<>();
        Map<String, Double> navByScheme = new HashMap<>();

        for (Map.Entry<String, List<MutualFundTransaction>> entry : dateGroupMap.entrySet()) {
            String dateStr = entry.getKey();
            List<MutualFundTransaction> dayTxs = entry.getValue();

            for (MutualFundTransaction tx : dayTxs) {
                String scheme = (tx.schemeName != null && !tx.schemeName.trim().isEmpty()) ? tx.schemeName.trim() : "Unknown";
                unitsByScheme.put(scheme, unitsByScheme.getOrDefault(scheme, 0.0) + tx.units);

                double inv = tx.actualInvested > 0 ? tx.actualInvested : (tx.amount > 0 ? tx.amount : 0.0);
                if (tx.isRedeem()) {
                    double curInv = investedByScheme.getOrDefault(scheme, 0.0);
                    investedByScheme.put(scheme, Math.max(0.0, curInv - Math.abs(tx.amount)));
                } else {
                    investedByScheme.put(scheme, investedByScheme.getOrDefault(scheme, 0.0) + inv);
                }

                if (tx.nav > 0) {
                    navByScheme.put(scheme, tx.nav);
                } else if (tx.currentNav > 0) {
                    navByScheme.put(scheme, tx.currentNav);
                }
            }

            double totalInv = 0.0;
            double totalVal = 0.0;
            for (Map.Entry<String, Double> uEntry : unitsByScheme.entrySet()) {
                double u = uEntry.getValue();
                if (u > 0.0001) {
                    totalInv += investedByScheme.getOrDefault(uEntry.getKey(), 0.0);
                    totalVal += u * navByScheme.getOrDefault(uEntry.getKey(), 1.0);
                }
            }

            long ts = parseTxTimestamp(dateStr);
            String label = dateStr;
            try {
                Date d = sdfDate.parse(dateStr);
                if (d != null) label = sdfLabel.format(d);
            } catch (Exception ignored) {}

            allPoints.add(new HoldingsGrowthChartView.GrowthDataPoint(
                    ts,
                    label,
                    dateStr,
                    Math.round(totalInv),
                    Math.round(totalVal)
            ));
        }

        // Anchor final point to current live valuation and invested value
        double currentVal = 0.0;
        double currentInv = 0.0;
        if (!isAll) {
            if (db.portfolioSummary != null && db.portfolioSummary.savingsTypeBreakdown != null) {
                for (SavingsTypeSummary sts : db.portfolioSummary.savingsTypeBreakdown) {
                    if (sts.name != null && sts.name.equalsIgnoreCase(selectedGrowthSavingsType)) {
                        currentVal = sts.currentValuation;
                        currentInv = sts.investedValue;
                        break;
                    }
                }
            }
            if (currentVal == 0.0 && db.holdings != null) {
                for (Holding h : db.holdings) {
                    String st = (h.savingsType != null && !h.savingsType.trim().isEmpty()) ? h.savingsType.trim() : h.subCategory;
                    if (st != null && st.equalsIgnoreCase(selectedGrowthSavingsType)) {
                        currentVal += h.currentValue;
                        currentInv += h.investedValue;
                    }
                }
            }
        } else if (db.portfolioSummary != null) {
            currentVal = db.portfolioSummary.currentValuation;
            currentInv = db.portfolioSummary.totalInvested;
        }

        long nowTs = System.currentTimeMillis();
        String todayStr = sdfDate.format(new Date(nowTs));
        String todayLabel = sdfLabel.format(new Date(nowTs));

        if (currentVal > 0 || currentInv > 0) {
            allPoints.add(new HoldingsGrowthChartView.GrowthDataPoint(
                    nowTs,
                    todayLabel,
                    todayStr + " (Live)",
                    Math.round(currentInv),
                    Math.round(currentVal)
            ));
        }

        if (allPoints.isEmpty()) {
            holdingsGrowthChartView.setData(Collections.emptyList());
            return;
        }

        // Apply Time Range Filter: 0=3M (90d), 1=6M (180d), 2=1Y (365d), 3=All
        long maxTs = allPoints.get(allPoints.size() - 1).timestamp;
        long cutoffTs = 0;
        if (currentGrowthRangeMode == 0) {
            cutoffTs = maxTs - (90L * 24L * 3600L * 1000L);
        } else if (currentGrowthRangeMode == 1) {
            cutoffTs = maxTs - (180L * 24L * 3600L * 1000L);
        } else if (currentGrowthRangeMode == 2) {
            cutoffTs = maxTs - (365L * 24L * 3600L * 1000L);
        }

        List<HoldingsGrowthChartView.GrowthDataPoint> filteredPoints = new ArrayList<>();
        if (cutoffTs > 0) {
            for (HoldingsGrowthChartView.GrowthDataPoint p : allPoints) {
                if (p.timestamp >= cutoffTs) {
                    filteredPoints.add(p);
                }
            }
            // Ensure at least 2 points for a curve
            if (filteredPoints.size() < 2 && allPoints.size() >= 2) {
                filteredPoints.clear();
                filteredPoints.add(allPoints.get(allPoints.size() - 2));
                filteredPoints.add(allPoints.get(allPoints.size() - 1));
            }
        } else {
            filteredPoints.addAll(allPoints);
        }

        holdingsGrowthChartView.setData(filteredPoints);
    }

    private long parseTxTimestamp(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return 0L;
        String clean = DateTimeHelper.formatDisplayDate(dateStr);
        if (clean != null && clean.contains("/")) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                Date d = sdf.parse(clean);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {}
        }
        return 0L;
    }
}