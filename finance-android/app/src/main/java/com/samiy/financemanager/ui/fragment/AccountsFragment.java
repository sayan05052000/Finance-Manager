package com.samiy.financemanager.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.GoalItem;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.data.model.Loan;
import com.samiy.financemanager.ui.dialog.AddPolicyBottomSheet;
import com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet;

import java.text.NumberFormat;
import java.util.Locale;

public class AccountsFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;

    // Filter Section Pills
    private TextView btnSectionFilterAll, btnSectionFilterBanks, btnSectionFilterInsurance, btnSectionFilterGoals;
    private View layoutSectionBanksAndLoans, layoutSectionInsurance, layoutSectionGoals;
    private String currentSection = "ALL";

    // Bank & Loan Section
    private TextView tvTotalBankBalance, tvTotalLoanDebt;
    private LinearLayout layoutBankAccountsList, layoutLoanAccountsList;
    private View btnToggleNonZero;
    private TextView tvToggleNonZeroIcon, tvToggleNonZeroText;
    private boolean isNonZeroOnly = false;

    // Insurance Financing Card
    private TextView tvFinancingPercentBadge, tvTotalInsurancePremiums, tvGoalFundedAmount, tvGoalTargetAmount, tvFinancingRemainingNote;
    private ProgressBar pbInsuranceFinancing;

    // Policies List
    private TextView btnAddPolicy;
    private LinearLayout layoutInsurancePoliciesList;

    // Goals List
    private LinearLayout layoutGoalsList;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_accounts, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshAccounts);

        // Section Filters & Containers
        btnSectionFilterAll = v.findViewById(R.id.btnSectionFilterAll);
        btnSectionFilterBanks = v.findViewById(R.id.btnSectionFilterBanks);
        btnSectionFilterInsurance = v.findViewById(R.id.btnSectionFilterInsurance);
        btnSectionFilterGoals = v.findViewById(R.id.btnSectionFilterGoals);

        layoutSectionBanksAndLoans = v.findViewById(R.id.layoutSectionBanksAndLoans);
        layoutSectionInsurance = v.findViewById(R.id.layoutSectionInsurance);
        layoutSectionGoals = v.findViewById(R.id.layoutSectionGoals);

        // Banks & Loans Views
        tvTotalBankBalance = v.findViewById(R.id.tvTotalBankBalance);
        tvTotalLoanDebt = v.findViewById(R.id.tvTotalLoanDebt);
        layoutBankAccountsList = v.findViewById(R.id.layoutBankAccountsList);
        layoutLoanAccountsList = v.findViewById(R.id.layoutLoanAccountsList);

        btnToggleNonZero = v.findViewById(R.id.btnToggleNonZero);
        tvToggleNonZeroIcon = v.findViewById(R.id.tvToggleNonZeroIcon);
        tvToggleNonZeroText = v.findViewById(R.id.tvToggleNonZeroText);

        // Insurance Financing Views
        tvFinancingPercentBadge = v.findViewById(R.id.tvFinancingPercentBadge);
        tvTotalInsurancePremiums = v.findViewById(R.id.tvTotalInsurancePremiums);
        tvGoalFundedAmount = v.findViewById(R.id.tvGoalFundedAmount);
        tvGoalTargetAmount = v.findViewById(R.id.tvGoalTargetAmount);
        tvFinancingRemainingNote = v.findViewById(R.id.tvFinancingRemainingNote);
        pbInsuranceFinancing = v.findViewById(R.id.pbInsuranceFinancing);

        btnAddPolicy = v.findViewById(R.id.btnAddPolicy);
        layoutInsurancePoliciesList = v.findViewById(R.id.layoutInsurancePoliciesList);

        // Goals View
        layoutGoalsList = v.findViewById(R.id.layoutGoalsList);

        // Saved Preferences
        isNonZeroOnly = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("accounts_non_zero_only", false);

        updateToggleUI();

        if (btnToggleNonZero != null) {
            btnToggleNonZero.setOnClickListener(view -> {
                isNonZeroOnly = !isNonZeroOnly;
                requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("accounts_non_zero_only", isNonZeroOnly).apply();
                updateToggleUI();
                populateBankAndLoanAccounts();
            });
        }

        // Section Filter Listeners
        if (btnSectionFilterAll != null) btnSectionFilterAll.setOnClickListener(view -> setSectionFilter("ALL"));
        if (btnSectionFilterBanks != null) btnSectionFilterBanks.setOnClickListener(view -> setSectionFilter("BANKS"));
        if (btnSectionFilterInsurance != null) btnSectionFilterInsurance.setOnClickListener(view -> setSectionFilter("INSURANCE"));
        if (btnSectionFilterGoals != null) btnSectionFilterGoals.setOnClickListener(view -> setSectionFilter("GOALS"));

        // Add Policy Button Listener
        if (btnAddPolicy != null) {
            btnAddPolicy.setOnClickListener(view -> {
                if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                    Toast.makeText(getContext(), "You are not authorized to add policies.", Toast.LENGTH_SHORT).show();
                    return;
                }
                AddPolicyBottomSheet bottomSheet = AddPolicyBottomSheet.newInstance();
                bottomSheet.setOnPolicyAddedListener(this::refreshData);
                bottomSheet.show(getChildFragmentManager(), "add_policy");
            });
        }

        swipeRefresh.setOnRefreshListener(this::refreshData);

        applySectionFilterUI();
        populateUI();

        return v;
    }

    private void setSectionFilter(String section) {
        currentSection = section;
        applySectionFilterUI();
    }

    private void applySectionFilterUI() {
        if (!isAdded()) return;

        // Reset all pills
        resetPill(btnSectionFilterAll);
        resetPill(btnSectionFilterBanks);
        resetPill(btnSectionFilterInsurance);
        resetPill(btnSectionFilterGoals);

        switch (currentSection) {
            case "BANKS":
                highlightPill(btnSectionFilterBanks);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.VISIBLE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.GONE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.GONE);
                break;
            case "INSURANCE":
                highlightPill(btnSectionFilterInsurance);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.GONE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.VISIBLE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.GONE);
                break;
            case "GOALS":
                highlightPill(btnSectionFilterGoals);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.GONE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.GONE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.VISIBLE);
                break;
            case "ALL":
            default:
                highlightPill(btnSectionFilterAll);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.VISIBLE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.VISIBLE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void resetPill(TextView pill) {
        if (pill == null) return;
        pill.setBackground(null);
        pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
    }

    private void highlightPill(TextView pill) {
        if (pill == null) return;
        pill.setBackgroundResource(R.drawable.bg_pill_emerald);
        pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
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

        populateBankAndLoanAccounts();
        populateInsuranceFinancing(db);
        populateInsurancePolicies(db);
        populateGoals(db);
    }

    private void populateBankAndLoanAccounts() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null) return;

        if (tvTotalBankBalance != null) tvTotalBankBalance.setText(formatCurrency(db.getTotalBankBalance()));
        if (tvTotalLoanDebt != null) tvTotalLoanDebt.setText(formatCurrency(db.getTotalLoanDebt()));

        // Bank Accounts
        if (layoutBankAccountsList != null) {
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
        }

        // Loan Accounts
        if (layoutLoanAccountsList != null) {
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
    }

    private void populateInsuranceFinancing(FinanceDatabase db) {
        if (tvTotalInsurancePremiums == null) return;

        double totalPremiums = db.getTotalAnnualInsurancePremiums();
        GoalItem healthGoal = db.getHealthAndLifeInsuranceGoal();
        double goalFunded = healthGoal != null ? healthGoal.currentAmount : 0.0;
        double goalTarget = healthGoal != null ? healthGoal.targetAmount : 0.0;

        tvTotalInsurancePremiums.setText(formatCurrency(totalPremiums));
        tvGoalFundedAmount.setText(formatCurrency(goalFunded));
        tvGoalTargetAmount.setText(formatCurrency(goalTarget));

        double pctFunded = totalPremiums > 0 ? (goalFunded / totalPremiums) * 100.0 : 0.0;
        int progressInt = (int) Math.min(100, Math.round(pctFunded));
        pbInsuranceFinancing.setProgress(progressInt);

        tvFinancingPercentBadge.setText(String.format(Locale.US, "%.1f%% Funded", pctFunded));

        if (pctFunded >= 100.0) {
            tvFinancingPercentBadge.setBackgroundResource(R.drawable.bg_badge_good);
            tvFinancingPercentBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            tvFinancingRemainingNote.setText("Annual insurance premiums are 100% funded by your Health & Life Goal!");
            tvFinancingRemainingNote.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
        } else {
            tvFinancingPercentBadge.setBackgroundResource(R.drawable.bg_badge_amber);
            tvFinancingPercentBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
            double remaining = Math.max(0, totalPremiums - goalFunded);
            tvFinancingRemainingNote.setText(formatCurrency(remaining) + " remaining in goal to fully fund annual premiums");
            tvFinancingRemainingNote.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        }
    }

    private void populateInsurancePolicies(FinanceDatabase db) {
        if (layoutInsurancePoliciesList == null) return;
        layoutInsurancePoliciesList.removeAllViews();

        if (db.insurance == null || db.insurance.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("No insurance policies recorded.");
            tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            tvEmpty.setTextSize(12);
            tvEmpty.setPadding(0, 16, 0, 16);
            layoutInsurancePoliciesList.addView(tvEmpty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (InsurancePolicy p : db.insurance) {
            View itemView = inflater.inflate(R.layout.item_policy, layoutInsurancePoliciesList, false);

            TextView tvSubCategory = itemView.findViewById(R.id.tvPolicySubCategory);
            TextView tvPolicyNumber = itemView.findViewById(R.id.tvPolicyNumber);
            TextView tvCategory = itemView.findViewById(R.id.tvPolicyCategory);
            TextView tvPremium = itemView.findViewById(R.id.tvPolicyPremium);
            TextView tvDueDate = itemView.findViewById(R.id.tvPolicyDueDate);
            TextView tvLastPaid = itemView.findViewById(R.id.tvPolicyLastPaid);
            TextView tvSumAssured = itemView.findViewById(R.id.tvPolicySumAssured);
            TextView tvInstallments = itemView.findViewById(R.id.tvPolicyInstallments);
            TextView tvNotes = itemView.findViewById(R.id.tvPolicyNotes);
            View btnEditDates = itemView.findViewById(R.id.btnEditPolicyDates);

            // Provider SubCategory
            String subCat = p.subCategory != null ? p.subCategory : "";
            if (subCat.toUpperCase().contains("TATA")) {
                tvSubCategory.setText("TATA AIA");
                tvSubCategory.setBackgroundResource(R.drawable.bg_chip_blue);
                tvSubCategory.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_sky));
            } else {
                tvSubCategory.setText("LIC");
                tvSubCategory.setBackgroundResource(R.drawable.bg_badge_amber);
                tvSubCategory.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
            }

            // Policy Number & Category
            tvPolicyNumber.setText(p.policyNumber != null ? p.policyNumber : "Unknown Policy");
            tvCategory.setText(p.category != null ? p.category : "Insurance");

            // Premium Amount
            tvPremium.setText(formatCurrency(p.premiumAmount));

            // Due Date
            if (p.dueDate != null && !p.dueDate.trim().isEmpty()) {
                tvDueDate.setText(p.dueDate);
                tvDueDate.setVisibility(View.VISIBLE);
            } else {
                tvDueDate.setText("No Due Date");
            }

            // Last Paid Date
            if (p.lastPaid != null && !p.lastPaid.trim().isEmpty()) {
                tvLastPaid.setText("Last Paid: " + p.lastPaid);
            } else {
                tvLastPaid.setText("Last Paid: N/A");
            }

            // Sum Assured
            if (p.sumAssured > 0) {
                tvSumAssured.setText("Sum Assured: " + formatCurrency(p.sumAssured));
                tvSumAssured.setVisibility(View.VISIBLE);
            } else {
                tvSumAssured.setVisibility(View.GONE);
            }

            // Installments & Maturity
            if (p.lastDate != null && !p.lastDate.trim().isEmpty()) {
                String extra = "Maturity: " + p.lastDate;
                if (p.installmentsRemaining != null && !p.installmentsRemaining.trim().isEmpty()) {
                    extra += " • Rem: " + p.installmentsRemaining;
                }
                tvInstallments.setText(extra);
                tvInstallments.setVisibility(View.VISIBLE);
            } else if (p.installmentsRemaining != null && !p.installmentsRemaining.trim().isEmpty()) {
                tvInstallments.setText("Installments Remaining: " + p.installmentsRemaining);
                tvInstallments.setVisibility(View.VISIBLE);
            } else {
                tvInstallments.setVisibility(View.GONE);
            }

            // Notes
            if (p.notes != null && !p.notes.trim().isEmpty()) {
                tvNotes.setText("Note: " + p.notes);
                tvNotes.setVisibility(View.VISIBLE);
            } else {
                tvNotes.setVisibility(View.GONE);
            }

            // Update Dates Button Click: Only Last Paid and Due Date can be modified!
            btnEditDates.setOnClickListener(view -> {
                if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                    Toast.makeText(getContext(), "You are not authorized to update policies.", Toast.LENGTH_SHORT).show();
                    return;
                }
                EditPolicyBottomSheet bottomSheet = EditPolicyBottomSheet.newInstance(p);
                bottomSheet.setOnPolicyUpdatedListener(this::refreshData);
                bottomSheet.show(getChildFragmentManager(), "edit_policy_" + p.policyNumber);
            });

            layoutInsurancePoliciesList.addView(itemView);
        }
    }

    private void populateGoals(FinanceDatabase db) {
        if (layoutGoalsList == null) return;
        layoutGoalsList.removeAllViews();

        if (db.goals == null || db.goals.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("No financial goals configured.");
            tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            tvEmpty.setTextSize(12);
            tvEmpty.setPadding(0, 16, 0, 16);
            layoutGoalsList.addView(tvEmpty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (GoalItem g : db.goals) {
            View itemView = inflater.inflate(R.layout.item_goal, layoutGoalsList, false);

            TextView tvGoalName = itemView.findViewById(R.id.tvGoalName);
            TextView tvCurrentAmount = itemView.findViewById(R.id.tvGoalCurrentAmount);
            TextView tvTargetAmount = itemView.findViewById(R.id.tvGoalTargetAmount);
            ProgressBar pbProgress = itemView.findViewById(R.id.pbGoalProgress);
            TextView tvAchievedPercent = itemView.findViewById(R.id.tvGoalAchievedPercent);
            TextView tvSavingAllocation = itemView.findViewById(R.id.tvGoalSavingAllocation);

            tvGoalName.setText(g.name != null ? g.name : "Financial Goal");
            tvCurrentAmount.setText(formatCurrency(g.currentAmount));
            tvTargetAmount.setText(formatCurrency(g.targetAmount));

            int progressInt = (int) Math.min(100, Math.round(g.achievedPercent));
            pbProgress.setProgress(progressInt);

            tvAchievedPercent.setText(String.format(Locale.US, "%.1f%% Achieved", g.achievedPercent));
            tvSavingAllocation.setText(String.format(Locale.US, "Allocation: %.0f%%", g.savingAllocationPercent));

            // Goals are strictly VIEW-ONLY per explicit requirement. No edit or click listeners attached.

            layoutGoalsList.addView(itemView);
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
