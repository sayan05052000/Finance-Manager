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
import com.samiy.financemanager.ui.dialog.FinMngrDialog;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.GoalItem;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.data.model.Loan;
import com.samiy.financemanager.ui.dialog.AddPolicyBottomSheet;
import com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet;
import com.samiy.financemanager.MainActivity;
import com.samiy.financemanager.data.model.Transaction;
import com.samiy.financemanager.ui.dialog.AccountTransactionsBottomSheet;
import com.samiy.financemanager.ui.dialog.AddTransactionBottomSheet;

import java.text.NumberFormat;
import java.util.Locale;

public class AccountsFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;

    // Filter Section Pills
    private TextView btnSectionFilterAll, btnSectionFilterBanks, btnSectionFilterInsurance, btnSectionFilterCreditCards, btnSectionFilterGoals;
    private View layoutSectionBanksAndLoans, layoutSectionInsurance, layoutSectionCreditCards, layoutSectionGoals;
    private String currentSection = "ALL";

    // Credit Cards Views
    private TextView tvCreditCardsSummary, btnAddCreditCard;
    private LinearLayout layoutCreditCardsList;

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
        btnSectionFilterCreditCards = v.findViewById(R.id.btnSectionFilterCreditCards);
        btnSectionFilterGoals = v.findViewById(R.id.btnSectionFilterGoals);

        layoutSectionBanksAndLoans = v.findViewById(R.id.layoutSectionBanksAndLoans);
        layoutSectionInsurance = v.findViewById(R.id.layoutSectionInsurance);
        layoutSectionCreditCards = v.findViewById(R.id.layoutSectionCreditCards);
        layoutSectionGoals = v.findViewById(R.id.layoutSectionGoals);

        tvCreditCardsSummary = v.findViewById(R.id.tvCreditCardsSummary);
        layoutCreditCardsList = v.findViewById(R.id.layoutCreditCardsList);

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
        btnAddCreditCard = v.findViewById(R.id.btnAddCreditCard);
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
        if (btnSectionFilterCreditCards != null) btnSectionFilterCreditCards.setOnClickListener(view -> setSectionFilter("CREDIT_CARDS"));
        if (btnSectionFilterGoals != null) btnSectionFilterGoals.setOnClickListener(view -> setSectionFilter("GOALS"));

        // Add Credit Card Button Listener
        if (btnAddCreditCard != null) {
            btnAddCreditCard.setOnClickListener(view -> {
                if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                    Toast.makeText(getContext(), "You are not authorized to add credit cards.", Toast.LENGTH_SHORT).show();
                    return;
                }
                com.samiy.financemanager.ui.dialog.AddCreditCardBottomSheet sheet =
                        com.samiy.financemanager.ui.dialog.AddCreditCardBottomSheet.newInstance();
                sheet.setOnCreditCardAddedListener(newCard -> refreshData());
                sheet.show(getChildFragmentManager(), "add_credit_card");
            });
        }

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

        // Add Goal Button Listener
        View btnAddGoal = v.findViewById(R.id.btnAddGoal);
        if (btnAddGoal != null) {
            btnAddGoal.setOnClickListener(view -> {
                if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                    Toast.makeText(getContext(), "You are not authorized to add goals.", Toast.LENGTH_SHORT).show();
                    return;
                }
                com.samiy.financemanager.ui.dialog.AddGoalBottomSheet bottomSheet =
                        com.samiy.financemanager.ui.dialog.AddGoalBottomSheet.newInstance();
                bottomSheet.setOnGoalAddedListener(newGoal -> refreshData());
                bottomSheet.show(getChildFragmentManager(), "add_goal");
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
        resetPill(btnSectionFilterCreditCards);
        resetPill(btnSectionFilterGoals);

        switch (currentSection) {
            case "BANKS":
                highlightPill(btnSectionFilterBanks);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.VISIBLE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.GONE);
                if (layoutSectionCreditCards != null) layoutSectionCreditCards.setVisibility(View.GONE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.GONE);
                break;
            case "INSURANCE":
                highlightPill(btnSectionFilterInsurance);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.GONE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.VISIBLE);
                if (layoutSectionCreditCards != null) layoutSectionCreditCards.setVisibility(View.GONE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.GONE);
                break;
            case "CREDIT_CARDS":
                highlightPill(btnSectionFilterCreditCards);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.GONE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.GONE);
                if (layoutSectionCreditCards != null) layoutSectionCreditCards.setVisibility(View.VISIBLE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.GONE);
                break;
            case "GOALS":
                highlightPill(btnSectionFilterGoals);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.GONE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.GONE);
                if (layoutSectionCreditCards != null) layoutSectionCreditCards.setVisibility(View.GONE);
                if (layoutSectionGoals != null) layoutSectionGoals.setVisibility(View.VISIBLE);
                break;
            case "ALL":
            default:
                highlightPill(btnSectionFilterAll);
                if (layoutSectionBanksAndLoans != null) layoutSectionBanksAndLoans.setVisibility(View.VISIBLE);
                if (layoutSectionInsurance != null) layoutSectionInsurance.setVisibility(View.VISIBLE);
                if (layoutSectionCreditCards != null) layoutSectionCreditCards.setVisibility(View.VISIBLE);
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
        pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary));
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
        populateCreditCards(db);
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
                    TextView tvTxCount = itemView.findViewById(R.id.tvAccountTxCount);
                    View btnViewTxs = itemView.findViewById(R.id.btnViewAccountTransactions);
                    View btnAddTx = itemView.findViewById(R.id.btnAddAccountTransaction);

                    tvName.setText(a.name);
                    tvBalance.setText(formatCurrency(a.currentBalance));

                    int txCount = 0;
                    if (db.transactions != null) {
                        for (Transaction t : db.transactions) {
                            if (t != null && t.account != null && t.account.trim().equalsIgnoreCase(a.name.trim())) {
                                txCount++;
                            }
                        }
                    }
                    if (tvTxCount != null) {
                        tvTxCount.setText(txCount + " txs");
                    }

                    if (btnViewTxs != null) {
                        btnViewTxs.setOnClickListener(v -> {
                            AccountTransactionsBottomSheet sheet = AccountTransactionsBottomSheet.newInstanceForAccount(a.name);
                            sheet.show(getChildFragmentManager(), "txs_acc_" + a.name);
                        });
                    }

                    if (btnAddTx != null) {
                        btnAddTx.setOnClickListener(v -> {
                            if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                                FinMngrToast.showError(getContext(), "You are not authorized");
                                return;
                            }
                            AddTransactionBottomSheet addDialog = AddTransactionBottomSheet.newInstanceForAccount(a.name);
                            addDialog.setListener(() -> {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).updateHeaderInfo();
                                }
                                refreshData();
                            });
                            addDialog.show(getChildFragmentManager(), "add_tx_acc_" + a.name);
                        });
                    }

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
                    TextView tvTxCount = itemView.findViewById(R.id.tvLoanTxCount);
                    View btnViewTxs = itemView.findViewById(R.id.btnViewLoanTransactions);
                    View btnAddTx = itemView.findViewById(R.id.btnAddLoanTransaction);

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

                    int txCount = 0;
                    if (db.transactions != null) {
                        for (Transaction t : db.transactions) {
                            if (t != null && t.loans != null && t.loans.trim().equalsIgnoreCase(l.name.trim())) {
                                txCount++;
                            }
                        }
                    }
                    if (tvTxCount != null) {
                        tvTxCount.setText(txCount + " txs");
                    }

                    if (btnViewTxs != null) {
                        btnViewTxs.setOnClickListener(v -> {
                            AccountTransactionsBottomSheet sheet = AccountTransactionsBottomSheet.newInstanceForLoan(l.name);
                            sheet.show(getChildFragmentManager(), "txs_loan_" + l.name);
                        });
                    }

                    if (btnAddTx != null) {
                        btnAddTx.setOnClickListener(v -> {
                            if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                                FinMngrToast.showError(getContext(), "You are not authorized");
                                return;
                            }
                            AddTransactionBottomSheet addDialog = AddTransactionBottomSheet.newInstanceForLoan(l.name);
                            addDialog.setListener(() -> {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).updateHeaderInfo();
                                }
                                refreshData();
                            });
                            addDialog.show(getChildFragmentManager(), "add_tx_loan_" + l.name);
                        });
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

            // SubCategory as Badge (e.g. Health Insurance, Brother LIC Policies, Tata AIA Policy, Term Insurance)
            String subCat = p.subCategory != null ? p.subCategory.trim() : "";
            if (subCat.isEmpty()) {
                subCat = "Insurance";
            }
            tvSubCategory.setText(subCat);

            String subCatUpper = subCat.toUpperCase();
            if (subCatUpper.contains("HEALTH")) {
                tvSubCategory.setBackgroundResource(R.drawable.bg_chip_green);
                tvSubCategory.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            } else if (subCatUpper.contains("TATA") || subCatUpper.contains("TERM")) {
                tvSubCategory.setBackgroundResource(R.drawable.bg_chip_blue);
                tvSubCategory.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_sky));
            } else {
                // LIC Policies, Brother LIC Policies, Sister LIC Policies, etc.
                tvSubCategory.setBackgroundResource(R.drawable.bg_badge_amber);
                tvSubCategory.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
            }

            // Policy Number
            tvPolicyNumber.setText(p.policyNumber != null ? p.policyNumber : "Unknown Policy");

            // Category tag (only show if not redundant with Sub-Category)
            String cat = p.category != null ? p.category.trim() : "";
            if (!cat.isEmpty() && !cat.equalsIgnoreCase("Insurance") && !cat.equalsIgnoreCase(subCat)) {
                tvCategory.setText(cat);
                tvCategory.setVisibility(View.VISIBLE);
            } else {
                tvCategory.setVisibility(View.GONE);
            }

            // Premium Amount
            tvPremium.setText(formatCurrency(p.premiumAmount));

            // Check if policy is matured / stopped / fully paid
            boolean isMatured = com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet.isPolicyFullyPaidOrMatured(p);
            TextView tvDueDateLabel = itemView.findViewById(R.id.tvPolicyDueDateLabel);

            // Due Date / Policy Status
            if (isMatured) {
                if (tvDueDateLabel != null) tvDueDateLabel.setText("Policy Status");
                tvDueDate.setText("Stopped / Fully Paid");
                tvDueDate.setBackgroundResource(R.drawable.bg_badge_good);
                tvDueDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
                tvDueDate.setVisibility(View.VISIBLE);
            } else {
                if (tvDueDateLabel != null) tvDueDateLabel.setText("Next Due Date");
                if (p.dueDate != null && !p.dueDate.trim().isEmpty()) {
                    tvDueDate.setText(p.dueDate.trim());
                    tvDueDate.setBackgroundResource(R.drawable.bg_badge_amber);
                    tvDueDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    tvDueDate.setVisibility(View.VISIBLE);
                } else {
                    tvDueDate.setText("No Due Date");
                    tvDueDate.setBackgroundResource(R.drawable.bg_pill_dark);
                    tvDueDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                    tvDueDate.setVisibility(View.VISIBLE);
                }
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

            // Quick Mark as Paid Button or Matured Badge
            View btnMarkPaidQuick = itemView.findViewById(R.id.btnMarkPolicyPaidQuick);
            View tvPolicyMaturedBadge = itemView.findViewById(R.id.tvPolicyMaturedBadge);

            if (isMatured) {
                if (btnMarkPaidQuick != null) btnMarkPaidQuick.setVisibility(View.GONE);
                if (tvPolicyMaturedBadge != null) tvPolicyMaturedBadge.setVisibility(View.VISIBLE);
            } else {
                if (tvPolicyMaturedBadge != null) tvPolicyMaturedBadge.setVisibility(View.GONE);
                if (btnMarkPaidQuick != null) {
                    btnMarkPaidQuick.setVisibility(View.VISIBLE);
                    btnMarkPaidQuick.setOnClickListener(view -> {
                        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                            Toast.makeText(getContext(), "You are not authorized to update policies.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        EditPolicyBottomSheet bottomSheet = EditPolicyBottomSheet.newInstance(p, true);
                        bottomSheet.setOnPolicyUpdatedListener(this::refreshData);
                        bottomSheet.show(getChildFragmentManager(), "mark_paid_" + p.policyNumber);
                    });
                }
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

    private void populateCreditCards(FinanceDatabase db) {
        if (layoutCreditCardsList == null) return;
        layoutCreditCardsList.removeAllViews();

        if (db.creditCards == null || db.creditCards.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("No credit cards recorded.");
            tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            tvEmpty.setTextSize(12);
            tvEmpty.setPadding(0, 16, 0, 16);
            layoutCreditCardsList.addView(tvEmpty);
            return;
        }

        if (tvCreditCardsSummary != null) {
            double totalBilled = db.getTotalCreditCardBilledAmount();
            double totalUnbilled = db.getTotalCreditCardUnbilledAmount();
            tvCreditCardsSummary.setText("Total Billed: " + formatCurrency(totalBilled) + " • Total Unbilled: " + formatCurrency(totalUnbilled));
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (com.samiy.financemanager.data.model.CreditCard card : db.creditCards) {
            if (card == null) continue;

            View itemView = inflater.inflate(R.layout.item_credit_card, layoutCreditCardsList, false);

            TextView tvName = itemView.findViewById(R.id.tvCreditCardName);
            TextView tvStatusBadge = itemView.findViewById(R.id.tvCreditCardStatusBadge);
            TextView tvBilled = itemView.findViewById(R.id.tvCreditCardBilledAmount);
            TextView tvUnbilled = itemView.findViewById(R.id.tvCreditCardUnbilledAmount);
            TextView tvDueDate = itemView.findViewById(R.id.tvCreditCardDueDate);
            TextView tvLastPaid = itemView.findViewById(R.id.tvCreditCardLastPaid);
            View btnMarkPaidQuick = itemView.findViewById(R.id.btnMarkCreditCardPaidQuick);
            View btnMarkBillGenerated = itemView.findViewById(R.id.btnMarkBillGenerated);
            View btnEditCard = itemView.findViewById(R.id.btnEditCreditCard);
            View btnViewCardTxs = itemView.findViewById(R.id.btnViewCardTransactions);
            View btnAddCardTx = itemView.findViewById(R.id.btnAddCardTransaction);

            int cardTxCount = 0;
            if (db.transactions != null) {
                for (Transaction t : db.transactions) {
                    if (t != null && t.taggedCreditCard != null && t.taggedCreditCard.trim().equalsIgnoreCase(card.cardName.trim())) {
                        cardTxCount++;
                    }
                }
            }

            if (btnViewCardTxs instanceof TextView) {
                ((TextView) btnViewCardTxs).setText("📜 Tagged Txs (" + cardTxCount + ")");
            }

            if (btnViewCardTxs != null) {
                btnViewCardTxs.setOnClickListener(v -> {
                    AccountTransactionsBottomSheet sheet = AccountTransactionsBottomSheet.newInstanceForCreditCard(card.cardName);
                    sheet.show(getChildFragmentManager(), "txs_card_" + card.cardName);
                });
            }

            if (btnAddCardTx != null) {
                btnAddCardTx.setOnClickListener(v -> {
                    if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                        FinMngrToast.showError(getContext(), "You are not authorized");
                        return;
                    }
                    AddTransactionBottomSheet addDialog = AddTransactionBottomSheet.newInstanceForCreditCard(card.cardName);
                    addDialog.setListener(() -> {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).updateHeaderInfo();
                        }
                        refreshData();
                    });
                    addDialog.show(getChildFragmentManager(), "add_tx_card_" + card.cardName);
                });
            }

            tvName.setText(card.cardName != null ? card.cardName : "Credit Card");
            tvBilled.setText(formatCurrency(card.billedAmount));
            tvUnbilled.setText(formatCurrency(card.unbilledAmount));

            if (card.dueDate != null && !card.dueDate.trim().isEmpty()) {
                tvDueDate.setText("Due Date: " + card.dueDate.trim());
            } else {
                tvDueDate.setText("Due Date: N/A");
            }

            if (card.lastPaid != null && !card.lastPaid.trim().isEmpty()) {
                tvLastPaid.setText("Last Paid: " + card.lastPaid.trim());
            } else {
                tvLastPaid.setText("Last Paid: N/A");
            }

            // Status Badge & Mark as Paid visibility
            if (card.billedAmount > 0) {
                Integer days = card.getDaysRemaining();
                if (days != null) {
                    if (days == 0) {
                        tvStatusBadge.setText("🚨 Due TODAY");
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red);
                        tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                    } else if (days == 1) {
                        tvStatusBadge.setText("⚠️ Due Tomorrow (1d)");
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    } else if (days > 1 && days <= 14) {
                        tvStatusBadge.setText("⏳ Due in " + days + "d");
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    } else if (days < 0) {
                        tvStatusBadge.setText("🚨 Overdue (" + Math.abs(days) + "d)");
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red);
                        tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                    } else {
                        tvStatusBadge.setText("Unpaid (" + days + "d)");
                        tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_dark);
                        tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                    }
                } else {
                    tvStatusBadge.setText("Unpaid");
                    tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_dark);
                    tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                }

                if (btnMarkPaidQuick != null) {
                    btnMarkPaidQuick.setVisibility(View.VISIBLE);
                    btnMarkPaidQuick.setOnClickListener(view -> {
                        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                            FinMngrToast.showError(getContext(), "You are not authorized");
                            return;
                        }

                        final double billedPaid = card.billedAmount;
                        FinMngrDialog.showConfirmation(requireContext(),
                                "Mark " + card.cardName + " as Paid?",
                                "This will set the billed amount (" + formatCurrency(billedPaid) + ") to ₹0, automatically advance the due date by 1 month, and record today as the paid date in Google Sheets.",
                                "Yes, Mark Paid",
                                () -> {
                                    FinanceRepository.getInstance(requireContext()).markCreditCardPaid(card, new FinanceRepository.DataCallback() {
                                        @Override
                                        public void onLoaded(FinanceDatabase updatedDb) {
                                            if (isAdded()) {
                                                refreshData();
                                            }
                                        }

                                        @Override
                                        public void onError(String error) {
                                            if (isAdded()) {
                                                refreshData();
                                            }
                                        }
                                    });
                                });
                    });
                }
            } else {
                tvStatusBadge.setText("Paid");
                tvStatusBadge.setBackgroundResource(R.drawable.bg_chip_green);
                tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));

                if (btnMarkPaidQuick != null) {
                    btnMarkPaidQuick.setVisibility(View.GONE);
                }
            }

            // Mark as Bill Generated Button:
            // "on marking that the bill amount will be the unbilled amount and then the unbillled amount will be 0"
            if (btnMarkBillGenerated != null) {
                if (card.unbilledAmount > 0) {
                    btnMarkBillGenerated.setVisibility(View.VISIBLE);
                    btnMarkBillGenerated.setOnClickListener(view -> {
                        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                            FinMngrToast.showError(getContext(), "You are not authorized");
                            return;
                        }

                        final double amountToBill = card.unbilledAmount;
                        FinMngrDialog.showConfirmation(requireContext(),
                                "Mark Bill Generated for " + card.cardName + "?",
                                "This will set the Billed Amount to " + formatCurrency(amountToBill) +
                                        " (the current unbilled spending), and reset Unbilled Spends to ₹0.",
                                "Yes, Generate Bill",
                                () -> {
                                    FinanceRepository.getInstance(requireContext()).markCreditCardBillGenerated(card, new FinanceRepository.DataCallback() {
                                        @Override
                                        public void onLoaded(FinanceDatabase updatedDb) {
                                            if (isAdded()) {
                                                refreshData();
                                            }
                                        }

                                        @Override
                                        public void onError(String error) {
                                            if (isAdded()) {
                                                refreshData();
                                            }
                                        }
                                    });
                                });
                    });
                } else {
                    btnMarkBillGenerated.setVisibility(View.GONE);
                }
            }

            // Edit Due / Billed Amount Button (Explicit constraint: user CANNOT mark as paid from update option)
            if (btnEditCard != null) {
                btnEditCard.setOnClickListener(view -> {
                    if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                        FinMngrToast.showError(getContext(), "You are not authorized");
                        return;
                    }
                    com.samiy.financemanager.ui.dialog.EditCreditCardBottomSheet sheet =
                            com.samiy.financemanager.ui.dialog.EditCreditCardBottomSheet.newInstance(card);
                    sheet.setOnCreditCardUpdatedListener(this::refreshData);
                    sheet.show(getChildFragmentManager(), "edit_card_" + card.cardName);
                });
            }

            layoutCreditCardsList.addView(itemView);
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
            View btnEditGoal = itemView.findViewById(R.id.btnEditGoal);
            View btnRemoveGoal = itemView.findViewById(R.id.btnRemoveGoal);

            if (btnEditGoal != null) {
                btnEditGoal.setOnClickListener(vItem -> {
                    com.samiy.financemanager.ui.dialog.EditGoalBottomSheet sheet = com.samiy.financemanager.ui.dialog.EditGoalBottomSheet.newInstance(g);
                    sheet.setOnGoalUpdatedListener(updated -> {
                        populateUI();
                    });
                    sheet.show(getChildFragmentManager(), "edit_goal_" + g.name);
                });
            }

            tvGoalName.setText(g.name != null ? g.name : "Financial Goal");
            tvCurrentAmount.setText(formatCurrency(g.currentAmount));
            tvTargetAmount.setText(formatCurrency(g.targetAmount));

            int progressInt = (int) Math.min(100, Math.round(g.achievedPercent));
            pbProgress.setProgress(progressInt);

            tvAchievedPercent.setText(String.format(Locale.US, "%.1f%% Achieved", g.achievedPercent));
            tvSavingAllocation.setText(g.getContributionSummary());

            if (btnRemoveGoal != null) {
                btnRemoveGoal.setOnClickListener(vItem -> {
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Remove Goal")
                            .setMessage("Are you sure you want to remove goal \"" + g.name + "\"? This will delete this goal from your Goals tracker and Google Sheets.")
                            .setPositiveButton("Remove", (dialog, which) -> {
                                FinanceRepository.getInstance(requireContext()).deleteGoal(g.name, new FinanceRepository.DataCallback() {
                                    @Override
                                    public void onLoaded(FinanceDatabase updatedDb) {
                                        if (isAdded()) {
                                            populateUI();
                                            FinMngrToast.showSuccess(getContext(), "Goal \"" + g.name + "\" removed successfully");
                                        }
                                    }

                                    @Override
                                    public void onError(String error) {
                                        if (isAdded()) {
                                            populateUI();
                                            FinMngrToast.showError(getContext(), error);
                                        }
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

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
