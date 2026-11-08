package com.samiy.financemanager.ui.fragment;

import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.MainActivity;
import com.samiy.financemanager.ui.dialog.AddTransactionBottomSheet;
import com.samiy.financemanager.ui.dialog.AddMutualFundTransactionBottomSheet;
import android.widget.Toast;
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
import com.samiy.financemanager.data.model.CurrentCycle;
import com.samiy.financemanager.data.model.FinanceDatabase;

import java.text.NumberFormat;
import java.util.Locale;

public class OverviewFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvNetWorthValue, tvBankBalancesValue, tvBankAccountsCount;
    private TextView tvAssetsValue, tvHoldingsCountText, tvLoanDebtValue;
    private TextView tvBudgetRemainderValue, tvBudgetSpentText, tvDailyLimitValue;
    private TextView tvCycleHeaderTitle, tvCycleDates, tvCycleRemainder, tvCycleDaily;
    private TextView tvCycleInterest, tvCycleReceivables;
    private LinearLayout layoutSubCategoriesContainer;

    // Upcoming Reminders Container & Views
    private LinearLayout layoutDueRemindersContainer, layoutDueRemindersList;
    private TextView tvRemindersHeaderTitle, tvRemindersCountBadge;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_overview, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefresh);
        tvNetWorthValue = v.findViewById(R.id.tvNetWorthValue);
        tvBankBalancesValue = v.findViewById(R.id.tvBankBalancesValue);
        tvBankAccountsCount = v.findViewById(R.id.tvBankAccountsCount);
        tvAssetsValue = v.findViewById(R.id.tvAssetsValue);
        tvHoldingsCountText = v.findViewById(R.id.tvHoldingsCountText);
        tvLoanDebtValue = v.findViewById(R.id.tvLoanDebtValue);
        // tvBudgetRemainderValue removed as duplicate
        tvBudgetSpentText = v.findViewById(R.id.tvBudgetSpentText);
        // tvDailyLimitValue removed as duplicate

        tvCycleHeaderTitle = v.findViewById(R.id.tvCycleHeaderTitle);
        tvCycleDates = v.findViewById(R.id.tvCycleDates);
        tvCycleRemainder = v.findViewById(R.id.tvCycleRemainder);
        tvCycleDaily = v.findViewById(R.id.tvCycleDaily);
        tvCycleInterest = v.findViewById(R.id.tvCycleInterest);
        tvCycleReceivables = v.findViewById(R.id.tvCycleReceivables);
        layoutSubCategoriesContainer = v.findViewById(R.id.layoutSubCategoriesContainer);

        layoutDueRemindersContainer = v.findViewById(R.id.layoutDueRemindersContainer);
        layoutDueRemindersList = v.findViewById(R.id.layoutDueRemindersList);
        tvRemindersHeaderTitle = v.findViewById(R.id.tvRemindersHeaderTitle);
        tvRemindersCountBadge = v.findViewById(R.id.tvRemindersCountBadge);

        View btnAddTx = v.findViewById(R.id.btnOverviewAddTx);
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

        View btnAddMf = v.findViewById(R.id.btnOverviewAddMfTx);
        if (btnAddMf != null) {
            btnAddMf.setOnClickListener(view -> {
                if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                    Toast.makeText(getContext(), "You are not authorized", Toast.LENGTH_SHORT).show();
                    return;
                }
                AddMutualFundTransactionBottomSheet dialog = new AddMutualFundTransactionBottomSheet();
                dialog.setListener(() -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).updateHeaderInfo();
                    }
                    refreshData();
                });
                dialog.show(getChildFragmentManager(), "AddMfTxDialog");
            });
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

        double netWorth = db.getEstimatedNetWorth();
        double bankBalances = db.getTotalBankBalance();
        double assets = db.portfolioSummary != null ? db.portfolioSummary.currentValuation : 0.0;
        double loanDebt = db.getTotalLoanDebt();

        tvNetWorthValue.setText(formatCurrency(netWorth));
        tvBankBalancesValue.setText(formatCurrency(bankBalances));
        tvBankAccountsCount.setText(db.accounts != null ? db.accounts.size() + " Accounts (Loans Excluded)" : "0 Accounts");

        tvAssetsValue.setText(formatCurrency(assets));
        int holdingsCount = db.holdings != null ? db.holdings.size() : 13;
        tvHoldingsCountText.setText(holdingsCount + " Active Holdings");

        tvLoanDebtValue.setText(formatCurrency(loanDebt));

        CurrentCycle cycle = db.currentCycle != null ? db.currentCycle : new CurrentCycle();
        // tvBudgetRemainderValue removed
        tvBudgetSpentText.setText("Budget: " + formatCurrency(cycle.budget) + " – Spent: " + formatCurrency(cycle.totalSpend));
        // tvDailyLimitValue removed

        tvCycleHeaderTitle.setText("✨ Cycle Snapshot Metrics (" + cycle.cycleMonth + ")");
        tvCycleDates.setText("Active cycle: " + cycle.cycleStart + " – " + cycle.cycleEnd + " • " + cycle.daysLeft + " Days Remaining");
        tvCycleRemainder.setText(formatCurrency(cycle.remainder));
        tvCycleDaily.setText(formatCurrency(cycle.canSpendPerDay) + "/day");
        tvCycleInterest.setText(formatCurrency(cycle.interestThisCycle));
        tvCycleReceivables.setText(formatCurrency(cycle.totalReceivables));

        // Populate Upcoming Due Reminders (LIC / Tata AIA & Credit Cards within 14 days)
        populateDueReminders(db);

        // Subcategories
        layoutSubCategoriesContainer.removeAllViews();
        if (cycle.subCategories != null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            for (CurrentCycle.SubCategorySpend sc : cycle.subCategories) {
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
                layoutSubCategoriesContainer.addView(item);
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

    private void populateDueReminders(FinanceDatabase db) {
        if (layoutDueRemindersContainer == null || layoutDueRemindersList == null) return;
        layoutDueRemindersList.removeAllViews();

        int reminderCount = 0;
        LayoutInflater inflater = LayoutInflater.from(getContext());

        // 1. Check LIC / Tata AIA Policies
        if (db.insurance != null) {
            for (com.samiy.financemanager.data.model.InsurancePolicy p : db.insurance) {
                if (p == null || !p.isLicOrTataAia()) continue;
                if (com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet.isPolicyFullyPaidOrMatured(p)) continue;

                Integer days = p.getDaysRemaining();
                if (days != null && days >= 0 && days <= 14) {
                    reminderCount++;
                    View item = inflater.inflate(R.layout.item_due_reminder, layoutDueRemindersList, false);

                    TextView tvType = item.findViewById(R.id.tvReminderTypeBadge);
                    TextView tvDays = item.findViewById(R.id.tvReminderDaysCounter);
                    TextView tvTitle = item.findViewById(R.id.tvReminderTitle);
                    TextView tvSubtitle = item.findViewById(R.id.tvReminderSubtitle);
                    TextView tvAmount = item.findViewById(R.id.tvReminderAmount);

                    tvType.setText("🛡️ POLICY PREMIUM");
                    tvType.setBackgroundResource(R.drawable.bg_chip_blue);
                    tvType.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.blue_sky));

                    String daysText;
                    if (days == 0) {
                        daysText = "🚨 DUE TODAY!";
                        tvDays.setBackgroundResource(R.drawable.bg_badge_red);
                        tvDays.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.red_rose));
                    } else if (days == 1) {
                        daysText = "⚠️ DUE TOMORROW (1 day left)";
                        tvDays.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvDays.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    } else {
                        daysText = "⏳ " + days + " days remaining";
                        tvDays.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvDays.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    }
                    tvDays.setText(daysText);

                    String name = (p.subCategory != null && !p.subCategory.trim().isEmpty()) ? p.subCategory.trim() : "Insurance Policy";
                    tvTitle.setText(name + " (" + p.policyNumber + ")");
                    tvSubtitle.setText("Due Date: " + p.dueDate + (p.lastDate != null && !p.lastDate.isEmpty() ? " • Maturity: " + p.lastDate : ""));
                    tvAmount.setText(formatCurrency(p.premiumAmount));

                    item.setOnClickListener(view -> {
                        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                            Toast.makeText(getContext(), "You are not authorized", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet dialog = com.samiy.financemanager.ui.dialog.EditPolicyBottomSheet.newInstance(p);
                        dialog.setOnPolicyUpdatedListener(this::refreshData);
                        dialog.show(getChildFragmentManager(), "reminder_policy_" + p.policyNumber);
                    });

                    layoutDueRemindersList.addView(item);
                }
            }
        }

        // 2. Check Credit Cards
        if (db.creditCards != null) {
            for (com.samiy.financemanager.data.model.CreditCard c : db.creditCards) {
                if (c == null || c.billedAmount <= 0) continue;

                Integer days = c.getDaysRemaining();
                if (days != null && days >= 0 && days <= 14) {
                    reminderCount++;
                    View item = inflater.inflate(R.layout.item_due_reminder, layoutDueRemindersList, false);

                    TextView tvType = item.findViewById(R.id.tvReminderTypeBadge);
                    TextView tvDays = item.findViewById(R.id.tvReminderDaysCounter);
                    TextView tvTitle = item.findViewById(R.id.tvReminderTitle);
                    TextView tvSubtitle = item.findViewById(R.id.tvReminderSubtitle);
                    TextView tvAmount = item.findViewById(R.id.tvReminderAmount);

                    tvType.setText("💳 CREDIT CARD BILL");
                    tvType.setBackgroundResource(R.drawable.bg_chip_green);
                    tvType.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_mint));

                    String daysText;
                    if (days == 0) {
                        daysText = "🚨 DUE TODAY!";
                        tvDays.setBackgroundResource(R.drawable.bg_badge_red);
                        tvDays.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.red_rose));
                    } else if (days == 1) {
                        daysText = "⚠️ DUE TOMORROW (1 day left)";
                        tvDays.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvDays.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    } else {
                        daysText = "⏳ " + days + " days remaining";
                        tvDays.setBackgroundResource(R.drawable.bg_badge_amber);
                        tvDays.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.amber_gold));
                    }
                    tvDays.setText(daysText);

                    tvTitle.setText(c.cardName);
                    tvSubtitle.setText("Due Date: " + c.dueDate + " • Unbilled: " + formatCurrency(c.unbilledAmount));
                    tvAmount.setText(formatCurrency(c.billedAmount));

                    item.setOnClickListener(view -> {
                        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                            Toast.makeText(getContext(), "You are not authorized", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        com.samiy.financemanager.ui.dialog.EditCreditCardBottomSheet dialog = com.samiy.financemanager.ui.dialog.EditCreditCardBottomSheet.newInstance(c);
                        dialog.setOnCreditCardUpdatedListener(this::refreshData);
                        dialog.show(getChildFragmentManager(), "reminder_card_" + c.cardName);
                    });

                    layoutDueRemindersList.addView(item);
                }
            }
        }

        if (reminderCount > 0) {
            layoutDueRemindersContainer.setVisibility(View.VISIBLE);
            if (tvRemindersCountBadge != null) {
                tvRemindersCountBadge.setText(reminderCount + (reminderCount == 1 ? " Due" : " Dues"));
            }
        } else {
            layoutDueRemindersContainer.setVisibility(View.GONE);
        }

        // Trigger daily notification check
        com.samiy.financemanager.util.ReminderNotificationManager.checkAndSendReminders(requireContext(), db);
    }

}
