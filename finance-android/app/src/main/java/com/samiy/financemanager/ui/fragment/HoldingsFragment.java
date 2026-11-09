package com.samiy.financemanager.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.PortfolioSummary;
import com.samiy.financemanager.data.model.SavingsTypeSummary;
import com.samiy.financemanager.ui.adapter.HoldingsAdapter;

import java.text.NumberFormat;
import java.util.Locale;

public class HoldingsFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalInvested, tvMarketValue, tvOneDayChange, tvOneDayChangePercent;
    private TextView tvTotalReturns, tvTotalReturnsRoi, tvPostTaxEstimate, tvActiveSchemes;
    private LinearLayout layoutSavingsTypeBreakdown;
    private RecyclerView rvHoldings;
    private HoldingsAdapter adapter;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_holdings, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshHoldings);
        tvTotalInvested = v.findViewById(R.id.tvTotalInvested);
        tvMarketValue = v.findViewById(R.id.tvMarketValue);
        tvOneDayChange = v.findViewById(R.id.tvOneDayChange);
        tvOneDayChangePercent = v.findViewById(R.id.tvOneDayChangePercent);
        tvTotalReturns = v.findViewById(R.id.tvTotalReturns);
        tvTotalReturnsRoi = v.findViewById(R.id.tvTotalReturnsRoi);
        tvPostTaxEstimate = v.findViewById(R.id.tvPostTaxEstimate);
        tvActiveSchemes = v.findViewById(R.id.tvActiveSchemes);
        layoutSavingsTypeBreakdown = v.findViewById(R.id.layoutSavingsTypeBreakdown);
        rvHoldings = v.findViewById(R.id.rvHoldings);

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

        PortfolioSummary ps = db.portfolioSummary != null ? db.portfolioSummary : new PortfolioSummary();

        tvTotalInvested.setText(formatCurrency(ps.totalInvested));
        tvMarketValue.setText(formatCurrency(ps.currentValuation));

        String changeSign = ps.oneDayGain >= 0 ? "+" : "";
        tvOneDayChange.setText(changeSign + formatCurrency(ps.oneDayGain));
        tvOneDayChangePercent.setText(changeSign + String.format(Locale.US, "%.2f", ps.oneDayGainPercent) + "% today");

        String retSign = ps.totalReturns >= 0 ? "+" : "";
        tvTotalReturns.setText(retSign + formatCurrency(ps.totalReturns));
        tvTotalReturnsRoi.setText(retSign + String.format(Locale.US, "%.2f", ps.totalReturnsRoi) + "% ROI");

        tvPostTaxEstimate.setText(retSign + formatCurrency(ps.afterTaxApprox));
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

                String sSign = sts.oneDayChange >= 0 ? "+" : "";
                tv1D.setText(sSign + formatCurrency(sts.oneDayChange));
                tvVal.setText(formatCurrency(sts.currentValuation));

                layoutSavingsTypeBreakdown.addView(itemView);
            }
        }

        // Holdings List
        if (db.holdings != null) {
            adapter.setItems(db.holdings);
            adapter.setMutualFundTransactions(db.mutualFundTransactions);
        }
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