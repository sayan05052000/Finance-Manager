package com.samiy.financemanager.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Loan;

import java.text.NumberFormat;
import java.util.Locale;

public class AccountsFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvTotalBankBalance, tvTotalLoanDebt;
    private LinearLayout layoutBankAccountsList, layoutLoanAccountsList;

    private View btnToggleNonZero;
    private TextView tvToggleNonZeroIcon, tvToggleNonZeroText;
    private boolean isNonZeroOnly = false;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_accounts, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshAccounts);
        tvTotalBankBalance = v.findViewById(R.id.tvTotalBankBalance);
        tvTotalLoanDebt = v.findViewById(R.id.tvTotalLoanDebt);
        layoutBankAccountsList = v.findViewById(R.id.layoutBankAccountsList);
        layoutLoanAccountsList = v.findViewById(R.id.layoutLoanAccountsList);

        btnToggleNonZero = v.findViewById(R.id.btnToggleNonZero);
        tvToggleNonZeroIcon = v.findViewById(R.id.tvToggleNonZeroIcon);
        tvToggleNonZeroText = v.findViewById(R.id.tvToggleNonZeroText);

        isNonZeroOnly = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("accounts_non_zero_only", false);

        updateToggleUI();

        if (btnToggleNonZero != null) {
            btnToggleNonZero.setOnClickListener(view -> {
                isNonZeroOnly = !isNonZeroOnly;
                requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("accounts_non_zero_only", isNonZeroOnly).apply();
                updateToggleUI();
                populateUI();
            });
        }

        swipeRefresh.setOnRefreshListener(this::refreshData);

        populateUI();

        return v;
    }

    private void updateToggleUI() {
        if (btnToggleNonZero == null || !isAdded()) return;
        if (isNonZeroOnly) {
            btnToggleNonZero.setBackgroundResource(R.drawable.bg_chip_green);
            if (tvToggleNonZeroIcon != null) {
                tvToggleNonZeroIcon.setText("● ");
                tvToggleNonZeroIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            }
            if (tvToggleNonZeroText != null) {
                tvToggleNonZeroText.setText("Showing Non-Zero Only");
                tvToggleNonZeroText.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            }
        } else {
            btnToggleNonZero.setBackgroundResource(R.drawable.bg_pill_dark);
            if (tvToggleNonZeroIcon != null) {
                tvToggleNonZeroIcon.setText("○ ");
                tvToggleNonZeroIcon.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }
            if (tvToggleNonZeroText != null) {
                tvToggleNonZeroText.setText("Hide ₹0 Balances");
                tvToggleNonZeroText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        populateUI();
    }

    public void populateUI() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null) return;

        tvTotalBankBalance.setText(formatCurrency(db.getTotalBankBalance()));
        tvTotalLoanDebt.setText(formatCurrency(db.getTotalLoanDebt()));

        // Bank Accounts
        layoutBankAccountsList.removeAllViews();
        if (db.accounts != null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            int visibleCount = 0;
            for (Account a : db.accounts) {
                if (isNonZeroOnly && Math.abs(a.currentBalance) < 0.01) {
                    continue;
                }
                visibleCount++;
                View itemView = inflater.inflate(R.layout.item_account, layoutBankAccountsList, false);
                TextView tvName = itemView.findViewById(R.id.tvAccountName);
                TextView tvBalance = itemView.findViewById(R.id.tvAccountBalance);

                tvName.setText(a.name);
                tvBalance.setText(formatCurrency(a.currentBalance));
                layoutBankAccountsList.addView(itemView);
            }

            if (visibleCount == 0 && isNonZeroOnly) {
                TextView tvEmpty = new TextView(getContext());
                tvEmpty.setText("No bank accounts with non-zero balances.");
                tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
                tvEmpty.setTextSize(12);
                tvEmpty.setPadding(0, 16, 0, 16);
                layoutBankAccountsList.addView(tvEmpty);
            }
        }

        // Loan Accounts
        layoutLoanAccountsList.removeAllViews();
        if (db.loans != null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            int visibleCount = 0;
            for (Loan l : db.loans) {
                if (isNonZeroOnly && Math.abs(l.outstanding) < 0.01) {
                    continue;
                }
                visibleCount++;
                View itemView = inflater.inflate(R.layout.item_loan, layoutLoanAccountsList, false);
                TextView tvName = itemView.findViewById(R.id.tvLoanName);
                TextView tvBadge = itemView.findViewById(R.id.tvLoanBadge);
                TextView tvOutstanding = itemView.findViewById(R.id.tvLoanOutstanding);

                tvName.setText(l.name);
                if ("CREDIT CARD".equalsIgnoreCase(l.name.trim())) {
                    double due = Math.abs(l.outstanding);
                    double ccPayBal = db.getCreditCardPaymentBalance();
                    tvBadge.setText("Payment Bal: " + formatCurrency(ccPayBal) + " • Due: " + formatCurrency(due));
                    tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    tvOutstanding.setText(formatCurrency(due));
                } else {
                    tvBadge.setText(l.isDebt ? "isDebt: TRUE" : "Tracking");
                    tvBadge.setTextColor(ContextCompat.getColor(requireContext(), l.isDebt ? R.color.red_rose : R.color.green_mint));
                    tvOutstanding.setText(formatCurrency(Math.abs(l.outstanding)));
                }
                layoutLoanAccountsList.addView(itemView);
            }

            if (visibleCount == 0 && isNonZeroOnly) {
                TextView tvEmpty = new TextView(getContext());
                tvEmpty.setText("No loan accounts with non-zero balances.");
                tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
                tvEmpty.setTextSize(12);
                tvEmpty.setPadding(0, 16, 0, 16);
                layoutLoanAccountsList.addView(tvEmpty);
            }
        }
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
