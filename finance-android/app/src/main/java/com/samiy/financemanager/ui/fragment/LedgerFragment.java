package com.samiy.financemanager.ui.fragment;

import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.MainActivity;
import com.samiy.financemanager.ui.dialog.AddTransactionBottomSheet;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Loan;
import com.samiy.financemanager.data.model.Transaction;
import com.samiy.financemanager.ui.adapter.TransactionsAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LedgerFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private Spinner spCycleFilter, spTypeFilter, spAccountFilter, spLoanFilter, spCategoryFilter, spYearFilter;
    private TextView tvMatchingEntries, tvTotalDebit, tvTotalCredit, tvNetInflow;
    private TextView tvCycleBudgetRemainder, tvAccountLoanRemainder;
    private RecyclerView rvLedger;
    private TransactionsAdapter adapter;

    // Preserved user-selected filters so background resync never wipes active filters
    private String savedSelectedCycle = "All Cycles";
    private String savedSelectedType = "All Types";
    private String savedSelectedAccount = "All Accounts";
    private String savedSelectedLoan = "All Loan Accounts";
    private String savedSelectedCategory = "All Categories";
    private String savedSelectedYear = "All Years";

    private List<Transaction> allTransactions = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_ledger, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshLedger);
        spCycleFilter = v.findViewById(R.id.spCycleFilter);
        spTypeFilter = v.findViewById(R.id.spTypeFilter);
        spAccountFilter = v.findViewById(R.id.spAccountFilter);
        spLoanFilter = v.findViewById(R.id.spLoanFilter);
        spCategoryFilter = v.findViewById(R.id.spCategoryFilter);
        spYearFilter = v.findViewById(R.id.spYearFilter);

        tvMatchingEntries = v.findViewById(R.id.tvMatchingEntries);
        tvTotalDebit = v.findViewById(R.id.tvTotalDebit);
        tvTotalCredit = v.findViewById(R.id.tvTotalCredit);
        tvNetInflow = v.findViewById(R.id.tvNetInflow);
        tvCycleBudgetRemainder = v.findViewById(R.id.tvCycleBudgetRemainder);
        tvAccountLoanRemainder = v.findViewById(R.id.tvAccountLoanRemainder);

        rvLedger = v.findViewById(R.id.rvLedger);
        rvLedger.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TransactionsAdapter();
        rvLedger.setAdapter(adapter);

        View btnAddTx = v.findViewById(R.id.btnAddTransactionLedger);
        if (btnAddTx != null) {
            btnAddTx.setOnClickListener(view -> {
                if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                    Toast.makeText(getContext(), "You are not authorized", Toast.LENGTH_SHORT).show();
                    return;
                }
                AddTransactionBottomSheet dialog = new AddTransactionBottomSheet();
                dialog.setListener(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateHeaderInfo();
                    }
                    refreshData();
                });
                dialog.show(getChildFragmentManager(), "AddTxDialog");
            });
        }

        setupFilters();
        swipeRefresh.setOnRefreshListener(this::refreshData);

        populateUI();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateUI();
    }

    private void setupFilters() {
        // Types: All Types, Expense, Income, Do not Consider
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                R.layout.item_spinner_selected,
                new String[]{"All Types", "Expense", "Income", "Do not Consider"});
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spTypeFilter.setAdapter(typeAdapter);

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent == spCycleFilter && parent.getSelectedItem() != null) {
                    savedSelectedCycle = parent.getSelectedItem().toString();
                } else if (parent == spTypeFilter && parent.getSelectedItem() != null) {
                    savedSelectedType = parent.getSelectedItem().toString();
                } else if (parent == spAccountFilter && parent.getSelectedItem() != null) {
                    savedSelectedAccount = parent.getSelectedItem().toString();
                } else if (parent == spLoanFilter && parent.getSelectedItem() != null) {
                    savedSelectedLoan = parent.getSelectedItem().toString();
                } else if (parent == spCategoryFilter && parent.getSelectedItem() != null) {
                    savedSelectedCategory = parent.getSelectedItem().toString();
                } else if (parent == spYearFilter && parent.getSelectedItem() != null) {
                    savedSelectedYear = parent.getSelectedItem().toString();
                }
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spCycleFilter.setOnItemSelectedListener(filterListener);
        spTypeFilter.setOnItemSelectedListener(filterListener);
        spAccountFilter.setOnItemSelectedListener(filterListener);
        spLoanFilter.setOnItemSelectedListener(filterListener);
        spCategoryFilter.setOnItemSelectedListener(filterListener);
        spYearFilter.setOnItemSelectedListener(filterListener);
    }

    private void updateSpinnerSafely(Spinner spinner, List<String> newItems, String targetSelection) {
        if (spinner == null || newItems == null || !isAdded()) return;

        SpinnerAdapter currentAdapter = spinner.getAdapter();
        boolean needsNewAdapter = true;

        if (currentAdapter instanceof ArrayAdapter) {
            ArrayAdapter<?> arrAdapter = (ArrayAdapter<?>) currentAdapter;
            if (arrAdapter.getCount() == newItems.size()) {
                boolean allMatch = true;
                for (int i = 0; i < newItems.size(); i++) {
                    Object item = arrAdapter.getItem(i);
                    if (item == null || !item.toString().equals(newItems.get(i))) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    needsNewAdapter = false;
                }
            }
        }

        if (needsNewAdapter) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, newItems);
            adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
            spinner.setAdapter(adapter);
        }

        // Restore target selection if available without triggering unexpected jumps
        if (targetSelection != null) {
            for (int i = 0; i < newItems.size(); i++) {
                if (newItems.get(i).equalsIgnoreCase(targetSelection)) {
                    if (spinner.getSelectedItemPosition() != i) {
                        spinner.setSelection(i, false);
                    }
                    break;
                }
            }
        }
    }

    public void populateUI() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null) return;

        allTransactions = db.transactions != null ? db.transactions : new ArrayList<>();

        // 1. Populate Cycle Filter
        List<String> cycles = new ArrayList<>();
        cycles.add("All Cycles");
        String activeCycleMonth = (db.currentCycle != null && db.currentCycle.cycleMonth != null && !db.currentCycle.cycleMonth.isEmpty())
                ? db.currentCycle.cycleMonth : "November 2026";
        cycles.add(activeCycleMonth + " (Active)");

        java.util.LinkedHashSet<String> pastCycles = new java.util.LinkedHashSet<>();
        for (Transaction t : allTransactions) {
            String label = t.getCycleLabel();
            if (label != null && !label.isEmpty() && !label.equalsIgnoreCase(activeCycleMonth)) {
                pastCycles.add(label);
            }
        }
        cycles.addAll(pastCycles);

        if (savedSelectedCycle == null && cycles.size() > 1) {
            savedSelectedCycle = cycles.get(1);
        }
        updateSpinnerSafely(spCycleFilter, cycles, savedSelectedCycle);

        // 2. Populate Account Filter
        List<String> accounts = new ArrayList<>();
        accounts.add("All Accounts");
        if (db.accounts != null) {
            for (Account a : db.accounts) {
                if (a != null && a.name != null && !a.name.isEmpty()) accounts.add(a.name);
            }
        }
        updateSpinnerSafely(spAccountFilter, accounts, savedSelectedAccount);

        // 3. Populate Loan Filter
        List<String> loans = new ArrayList<>();
        loans.add("All Loan Accounts");
        if (db.loans != null) {
            for (Loan l : db.loans) {
                if (l != null && l.name != null && !l.name.isEmpty()) loans.add(l.name);
            }
        }
        updateSpinnerSafely(spLoanFilter, loans, savedSelectedLoan);

        // 4. Categories & Years
        Set<String> categoriesSet = new HashSet<>();
        Set<String> yearsSet = new HashSet<>();
        for (Transaction t : allTransactions) {
            if (t.category != null && !t.category.trim().isEmpty()) categoriesSet.add(t.category.trim());
            int y = t.getResolvedYear();
            if (y > 0) yearsSet.add(String.valueOf(y));
        }

        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        categories.addAll(categoriesSet);
        updateSpinnerSafely(spCategoryFilter, categories, savedSelectedCategory);

        List<String> years = new ArrayList<>();
        years.add("All Years");
        years.addAll(yearsSet);
        updateSpinnerSafely(spYearFilter, years, savedSelectedYear);

        applyFilters();
    }

    private void applyFilters() {
        String selectedCycle = spCycleFilter.getSelectedItem() != null ? spCycleFilter.getSelectedItem().toString() : savedSelectedCycle;
        String selectedType = spTypeFilter.getSelectedItem() != null ? spTypeFilter.getSelectedItem().toString() : savedSelectedType;
        String selectedAccount = spAccountFilter.getSelectedItem() != null ? spAccountFilter.getSelectedItem().toString() : savedSelectedAccount;
        String selectedLoan = spLoanFilter.getSelectedItem() != null ? spLoanFilter.getSelectedItem().toString() : savedSelectedLoan;
        String selectedCategory = spCategoryFilter.getSelectedItem() != null ? spCategoryFilter.getSelectedItem().toString() : savedSelectedCategory;
        String selectedYear = spYearFilter.getSelectedItem() != null ? spYearFilter.getSelectedItem().toString() : savedSelectedYear;

        Parcelable scrollState = rvLedger.getLayoutManager() != null ? rvLedger.getLayoutManager().onSaveInstanceState() : null;

        List<Transaction> filtered = new ArrayList<>();
        double totalDebit = 0.0;
        double totalCredit = 0.0;

        for (Transaction t : allTransactions) {
            // 1. Cycle Filter
            if (!"All Cycles".equals(selectedCycle)) {
                if (selectedCycle.contains("Active")) {
                    FinanceDatabase currentDb = FinanceRepository.getInstance(requireContext()).getDatabase();
                    int cYear = (currentDb != null && currentDb.currentCycle != null) ? currentDb.currentCycle.cycleYear : 2026;
                    int cMonth = (currentDb != null && currentDb.currentCycle != null) ? currentDb.currentCycle.cycleMonthNumber : 11;
                    if (!t.isInCycle(cYear, cMonth)) continue;
                } else {
                    String label = t.getCycleLabel();
                    if (!selectedCycle.equalsIgnoreCase(label)) continue;
                }
            }

            // 2. Type Filter
            if (!"All Types".equals(selectedType) && !selectedType.equalsIgnoreCase(t.type)) {
                continue;
            }

            // 3. Account Filter
            if (!"All Accounts".equals(selectedAccount) && !selectedAccount.equalsIgnoreCase(t.account)) {
                continue;
            }

            // 4. Loan Filter
            if (!"All Loan Accounts".equals(selectedLoan) && !selectedLoan.equalsIgnoreCase(t.loans)) {
                continue;
            }

            // 5. Category Filter
            if (!"All Categories".equals(selectedCategory) && !selectedCategory.equalsIgnoreCase(t.category)) {
                continue;
            }

            // 6. Year Filter
            if (!"All Years".equals(selectedYear) && !selectedYear.equals(String.valueOf(t.getResolvedYear()))) {
                continue;
            }

            filtered.add(t);

            double debitAmount = t.debit != 0 ? t.debit : (t.debitWithoutSign != 0 ? -t.debitWithoutSign : 0);
            if (debitAmount < 0) {
                totalDebit += Math.abs(debitAmount);
            } else if (debitAmount > 0 && t.isExpense()) {
                // A positive debit in an expense balances out (reduces total debit)
                totalDebit -= debitAmount;
            }
            totalCredit += t.credit;
        }

        tvMatchingEntries.setText(String.valueOf(filtered.size()));
        tvTotalDebit.setText("-" + formatCurrency(totalDebit));
        tvTotalCredit.setText("+" + formatCurrency(totalCredit));

        double netInflow = totalCredit - totalDebit;
        String netSign = netInflow >= 0 ? "+" : "";
        tvNetInflow.setText(netSign + formatCurrency(netInflow));

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        double cycleRemainder = db != null && db.currentCycle != null ? db.currentCycle.remainder : 30209.72;
        tvCycleBudgetRemainder.setText(formatCurrency(cycleRemainder));
        tvAccountLoanRemainder.setText(formatCurrency(cycleRemainder));

        adapter.setItems(filtered);

        if (scrollState != null && rvLedger.getLayoutManager() != null) {
            rvLedger.getLayoutManager().onRestoreInstanceState(scrollState);
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
