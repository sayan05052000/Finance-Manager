package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.samiy.financemanager.R;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.CalculationEngine;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.GoalItem;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.ui.adapter.AllocationSourceAdapter;
import com.samiy.financemanager.util.FinMngrToast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddGoalBottomSheet extends BaseBottomSheetDialogFragment {

    public interface OnGoalAddedListener {
        void onGoalAdded(GoalItem newGoal);
    }

    private OnGoalAddedListener listener;

    private List<AllocationSourceAdapter.SourceItem> baseAccountItems = new ArrayList<>();
    private List<AllocationSourceAdapter.SourceItem> baseMfItems = new ArrayList<>();

    public static AddGoalBottomSheet newInstance() {
        return new AddGoalBottomSheet();
    }

    public void setOnGoalAddedListener(OnGoalAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.show(getContext(), "You are not authorized", Toast.LENGTH_SHORT);
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_add_goal, container, false);

        EditText etGoalName = v.findViewById(R.id.etGoalName);
        EditText etGoalTargetAmount = v.findViewById(R.id.etGoalTargetAmount);
        EditText etGoalCurrentAmount = v.findViewById(R.id.etGoalCurrentAmount);

        RadioGroup rgGoalLinkType = v.findViewById(R.id.rgGoalLinkType);
        RadioButton rbLinkNone = v.findViewById(R.id.rbLinkNone);
        RadioButton rbLinkAssets = v.findViewById(R.id.rbLinkAssets);

        LinearLayout layoutLinkedAssetsContainer = v.findViewById(R.id.layoutLinkedAssetsContainer);
        LinearLayout layoutAccountsContainer = v.findViewById(R.id.layoutAccountsContainer);
        TextView tvEmptyAccounts = v.findViewById(R.id.tvEmptyAccounts);
        TextView btnAddAccountRow = v.findViewById(R.id.btnAddAccountRow);

        LinearLayout layoutMfContainer = v.findViewById(R.id.layoutMfContainer);
        TextView tvEmptyMf = v.findViewById(R.id.tvEmptyMf);
        TextView btnAddMfRow = v.findViewById(R.id.btnAddMfRow);

        Button btnCancel = v.findViewById(R.id.btnCancelGoal);
        Button btnSubmit = v.findViewById(R.id.btnSubmitGoal);

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();

        // 1. Prepare Base Account Items
        baseAccountItems.clear();
        List<String> rawAccs = new ArrayList<>();
        if (db != null && db.accounts != null) {
            for (Account a : db.accounts) {
                if (a != null && a.name != null && !a.name.trim().isEmpty() && !rawAccs.contains(a.name.trim())) {
                    rawAccs.add(a.name.trim());
                }
            }
        }
        if (rawAccs.isEmpty()) {
            rawAccs.add("PNB");
            rawAccs.add("HDFC SAVINGS");
            rawAccs.add("SBI");
            rawAccs.add("PURSE");
        }
        for (String accName : rawAccs) {
            double alreadyUsed = CalculationEngine.getAccountAllocatedPercentage(accName, db != null ? db.goals : null, null);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            baseAccountItems.add(new AllocationSourceAdapter.SourceItem(accName, available, alreadyUsed));
        }

        // 2. Prepare Base Mutual Fund Items
        baseMfItems.clear();
        List<String> rawMfTypes = new ArrayList<>();
        if (db != null && db.holdings != null) {
            for (Holding h : db.holdings) {
                if (h != null) {
                    if (h.savingsType != null && !h.savingsType.trim().isEmpty() && !rawMfTypes.contains(h.savingsType.trim())) {
                        rawMfTypes.add(h.savingsType.trim());
                    }
                    if (h.savingsCategory != null && !h.savingsCategory.trim().isEmpty() && !rawMfTypes.contains(h.savingsCategory.trim())) {
                        rawMfTypes.add(h.savingsCategory.trim());
                    }
                }
            }
        }
        if (rawMfTypes.isEmpty()) {
            rawMfTypes.add("Mutual Fund");
            rawMfTypes.add("Emergency Mutual Fund");
            rawMfTypes.add("Family Savings");
            rawMfTypes.add("Kuvera Surplus(Family)");
        }
        for (String mfName : rawMfTypes) {
            double alreadyUsed = CalculationEngine.getMutualFundAllocatedPercentage(mfName, db != null ? db.goals : null, null);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            baseMfItems.add(new AllocationSourceAdapter.SourceItem(mfName, available, alreadyUsed));
        }

        // Helper: Check and update empty state text views
        Runnable updateEmptyStates = () -> {
            if (tvEmptyAccounts != null) {
                tvEmptyAccounts.setVisibility(layoutAccountsContainer.getChildCount() == 0 ? View.VISIBLE : View.GONE);
            }
            if (tvEmptyMf != null) {
                tvEmptyMf.setVisibility(layoutMfContainer.getChildCount() == 0 ? View.VISIBLE : View.GONE);
            }
        };

        // Helper: Add an Account Row
        AccountRowAdder addAccountRow = (selectedAccountName, initialPct) -> {
            View rowView = inflater.inflate(R.layout.item_goal_account_row, layoutAccountsContainer, false);
            Spinner sp = rowView.findViewById(R.id.spRowAccount);
            EditText etPct = rowView.findViewById(R.id.etRowAccountPercent);
            TextView tvInfo = rowView.findViewById(R.id.tvRowAccountInfo);
            ImageView btnRemove = rowView.findViewById(R.id.btnRemoveAccountRow);

            AllocationSourceAdapter adapter = new AllocationSourceAdapter(requireContext(), new ArrayList<>(baseAccountItems));
            sp.setAdapter(adapter);

            int selPos = 0;
            if (selectedAccountName != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    AllocationSourceAdapter.SourceItem it = adapter.getItem(i);
                    if (it != null && it.name.equalsIgnoreCase(selectedAccountName.trim())) {
                        selPos = i;
                        break;
                    }
                }
            } else {
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i).isSelectable()) {
                        selPos = i;
                        break;
                    }
                }
            }
            sp.setSelection(selPos);
            etPct.setText(String.format(Locale.US, "%d", (int) Math.round(initialPct)));

            Runnable refreshRowInfo = () -> {
                AllocationSourceAdapter.SourceItem item = (AllocationSourceAdapter.SourceItem) sp.getSelectedItem();
                if (item == null) return;
                if (!item.isSelectable()) {
                    tvInfo.setText("⚠️ Fully Allocated: " + String.format(Locale.US, "%.1f", item.consumedPercent) + "% used by other goals (0% remaining)");
                    tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                    etPct.setEnabled(false);
                } else {
                    tvInfo.setText("Available to allocate: " + String.format(Locale.US, "%.1f", item.availablePercent) + "% (" + String.format(Locale.US, "%.1f", item.consumedPercent) + "% used by other goals)");
                    tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
                    etPct.setEnabled(true);
                }
            };

            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    refreshRowInfo.run();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            btnRemove.setOnClickListener(vRow -> {
                layoutAccountsContainer.removeView(rowView);
                updateEmptyStates.run();
            });

            refreshRowInfo.run();
            layoutAccountsContainer.addView(rowView);
            updateEmptyStates.run();
        };

        // Helper: Add a Mutual Fund Row
        MfRowAdder addMfRow = (selectedMfName, initialPct) -> {
            View rowView = inflater.inflate(R.layout.item_goal_mf_row, layoutMfContainer, false);
            Spinner sp = rowView.findViewById(R.id.spRowMf);
            EditText etPct = rowView.findViewById(R.id.etRowMfPercent);
            TextView tvInfo = rowView.findViewById(R.id.tvRowMfInfo);
            ImageView btnRemove = rowView.findViewById(R.id.btnRemoveMfRow);

            AllocationSourceAdapter adapter = new AllocationSourceAdapter(requireContext(), new ArrayList<>(baseMfItems));
            sp.setAdapter(adapter);

            int selPos = 0;
            if (selectedMfName != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    AllocationSourceAdapter.SourceItem it = adapter.getItem(i);
                    if (it != null && it.name.equalsIgnoreCase(selectedMfName.trim())) {
                        selPos = i;
                        break;
                    }
                }
            } else {
                for (int i = 0; i < adapter.getCount(); i++) {
                    if (adapter.getItem(i).isSelectable()) {
                        selPos = i;
                        break;
                    }
                }
            }
            sp.setSelection(selPos);
            etPct.setText(String.format(Locale.US, "%d", (int) Math.round(initialPct)));

            Runnable refreshRowInfo = () -> {
                AllocationSourceAdapter.SourceItem item = (AllocationSourceAdapter.SourceItem) sp.getSelectedItem();
                if (item == null) return;
                if (!item.isSelectable()) {
                    tvInfo.setText("⚠️ Fully Allocated: " + String.format(Locale.US, "%.1f", item.consumedPercent) + "% used by other goals (0% remaining)");
                    tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                    etPct.setEnabled(false);
                } else {
                    tvInfo.setText("Available to allocate: " + String.format(Locale.US, "%.1f", item.availablePercent) + "% (" + String.format(Locale.US, "%.1f", item.consumedPercent) + "% used by other goals)");
                    tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
                    etPct.setEnabled(true);
                }
            };

            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    refreshRowInfo.run();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            btnRemove.setOnClickListener(vRow -> {
                layoutMfContainer.removeView(rowView);
                updateEmptyStates.run();
            });

            refreshRowInfo.run();
            layoutMfContainer.addView(rowView);
            updateEmptyStates.run();
        };

        // Wire up "Add" buttons
        btnAddAccountRow.setOnClickListener(vBtn -> addAccountRow.add(null, 100.0));
        btnAddMfRow.setOnClickListener(vBtn -> addMfRow.add(null, 100.0));

        // Initial default: Manual Tracking
        rbLinkNone.setChecked(true);
        layoutLinkedAssetsContainer.setVisibility(View.GONE);
        etGoalCurrentAmount.setEnabled(true);
        updateEmptyStates.run();

        // Toggle Tracking Modes (Mutual Exclusivity)
        rgGoalLinkType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLinkNone) {
                // Manual Tracking ON -> Disable and hide linked assets
                layoutLinkedAssetsContainer.setVisibility(View.GONE);
                etGoalCurrentAmount.setEnabled(true);
            } else if (checkedId == R.id.rbLinkAssets) {
                // Linked Assets ON -> Enable linked assets, disable manual amount input
                layoutLinkedAssetsContainer.setVisibility(View.VISIBLE);
                etGoalCurrentAmount.setEnabled(false);

                // If no rows exist yet, add one default account row
                if (layoutAccountsContainer.getChildCount() == 0 && layoutMfContainer.getChildCount() == 0) {
                    addAccountRow.add(null, 100.0);
                }
            }
        });

        // Cancel Button
        btnCancel.setOnClickListener(vBtn -> dismiss());

        // Submit Button
        btnSubmit.setOnClickListener(vBtn -> {
            String goalName = etGoalName.getText().toString().trim();
            if (goalName.isEmpty()) {
                FinMngrToast.showError(getContext(), "Goal Name cannot be empty.");
                return;
            }

            // Check if goal with same name exists
            if (db != null && db.goals != null) {
                for (GoalItem existing : db.goals) {
                    if (existing != null && existing.name != null && existing.name.trim().equalsIgnoreCase(goalName)) {
                        FinMngrToast.showError(getContext(), "A goal named '" + goalName + "' already exists.");
                        return;
                    }
                }
            }

            String targetStr = etGoalTargetAmount.getText().toString().trim();
            double targetAmount;
            try {
                targetAmount = Double.parseDouble(targetStr);
                if (targetAmount <= 0) {
                    FinMngrToast.showError(getContext(), "Target Goal Amount must be greater than 0.");
                    return;
                }
            } catch (Exception e) {
                FinMngrToast.showError(getContext(), "Please enter a valid numeric target amount.");
                return;
            }

            double currentAmount = 0.0;
            String contributionJson = "{}";

            if (rbLinkNone.isChecked()) {
                // Manual Tracking
                String curStr = etGoalCurrentAmount.getText().toString().trim();
                if (!curStr.isEmpty()) {
                    try {
                        currentAmount = Double.parseDouble(curStr);
                    } catch (Exception ignored) {}
                }
                contributionJson = "{}";
            } else {
                // Linked Assets: Parse all Account Rows and Mutual Fund Rows
                JsonObject contribObj = new JsonObject();
                JsonObject accountsObj = new JsonObject();
                JsonArray mfArray = new JsonArray();

                // Validate Account Rows
                List<String> seenAccounts = new ArrayList<>();
                for (int i = 0; i < layoutAccountsContainer.getChildCount(); i++) {
                    View rowView = layoutAccountsContainer.getChildAt(i);
                    Spinner sp = rowView.findViewById(R.id.spRowAccount);
                    EditText etPct = rowView.findViewById(R.id.etRowAccountPercent);

                    AllocationSourceAdapter.SourceItem item = (AllocationSourceAdapter.SourceItem) sp.getSelectedItem();
                    if (item == null) continue;
                    String accName = item.name.trim();

                    if (seenAccounts.contains(accName.toUpperCase(Locale.US))) {
                        FinMngrToast.showError(getContext(), "Bank Account '" + accName + "' is selected multiple times. Please adjust its percentage in a single row.");
                        return;
                    }
                    seenAccounts.add(accName.toUpperCase(Locale.US));

                    String pctStr = etPct.getText().toString().trim();
                    double pct;
                    try {
                        pct = Double.parseDouble(pctStr);
                    } catch (Exception e) {
                        FinMngrToast.showError(getContext(), "Please enter a valid allocation percentage for account '" + accName + "'.");
                        return;
                    }

                    if (pct <= 0 || pct > 100) {
                        FinMngrToast.showError(getContext(), "Allocation percentage for account '" + accName + "' must be between 1% and 100%.");
                        return;
                    }

                    if (pct > item.availablePercent + 0.001) {
                        FinMngrToast.showError(getContext(), "Allocation for account '" + accName + "' (" + pct + "%) exceeds available capacity (" + String.format(Locale.US, "%.1f", item.availablePercent) + "%).");
                        return;
                    }

                    accountsObj.addProperty(accName, pct / 100.0);
                }

                // Validate Mutual Fund Rows
                List<String> seenMfs = new ArrayList<>();
                for (int i = 0; i < layoutMfContainer.getChildCount(); i++) {
                    View rowView = layoutMfContainer.getChildAt(i);
                    Spinner sp = rowView.findViewById(R.id.spRowMf);
                    EditText etPct = rowView.findViewById(R.id.etRowMfPercent);

                    AllocationSourceAdapter.SourceItem item = (AllocationSourceAdapter.SourceItem) sp.getSelectedItem();
                    if (item == null) continue;
                    String mfName = item.name.trim();

                    if (seenMfs.contains(mfName.toLowerCase(Locale.US))) {
                        FinMngrToast.showError(getContext(), "Holding '" + mfName + "' is selected multiple times. Please adjust its percentage in a single row.");
                        return;
                    }
                    seenMfs.add(mfName.toLowerCase(Locale.US));

                    String pctStr = etPct.getText().toString().trim();
                    double pct;
                    try {
                        pct = Double.parseDouble(pctStr);
                    } catch (Exception e) {
                        FinMngrToast.showError(getContext(), "Please enter a valid allocation percentage for holding '" + mfName + "'.");
                        return;
                    }

                    if (pct <= 0 || pct > 100) {
                        FinMngrToast.showError(getContext(), "Allocation percentage for holding '" + mfName + "' must be between 1% and 100%.");
                        return;
                    }

                    if (pct > item.availablePercent + 0.001) {
                        FinMngrToast.showError(getContext(), "Allocation for holding '" + mfName + "' (" + pct + "%) exceeds available capacity (" + String.format(Locale.US, "%.1f", item.availablePercent) + "%).");
                        return;
                    }

                    JsonObject rule = new JsonObject();
                    rule.addProperty("column", "catgory");
                    rule.addProperty("value", mfName);
                    rule.addProperty("percentage", pct / 100.0);
                    mfArray.add(rule);
                }

                if (accountsObj.keySet().isEmpty() && mfArray.size() == 0) {
                    FinMngrToast.showError(getContext(), "Please link at least one bank account or mutual fund, or switch to Manual Tracking.");
                    return;
                }

                if (!accountsObj.keySet().isEmpty()) {
                    contribObj.add("accounts", accountsObj);
                }
                if (mfArray.size() > 0) {
                    contribObj.add("mutual_funds", mfArray);
                }
                contributionJson = contribObj.toString();
            }

            double achievedPercent = targetAmount > 0 ? (currentAmount / targetAmount) * 100.0 : 0.0;
            GoalItem newGoal = new GoalItem(goalName, targetAmount, currentAmount, achievedPercent, 0.0, contributionJson);

            // If linked assets are present, compute real-time balance
            if (!contributionJson.equals("{}") && db != null) {
                newGoal.currentAmount = CalculationEngine.calculateGoalBalance(newGoal, db);
                newGoal.achievedPercent = newGoal.targetAmount > 0 ? (newGoal.currentAmount / newGoal.targetAmount) * 100.0 : 0.0;
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Adding Goal...");

            FinanceRepository.getInstance(requireContext()).addGoal(newGoal, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        FinMngrToast.showSuccess(getContext(), "Goal '" + goalName + "' added successfully!");
                        if (listener != null) listener.onGoalAdded(newGoal);
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("＋ Add Goal");
                        FinMngrToast.showError(getContext(), "Failed to add goal: " + error);
                    }
                }
            });
        });

        return v;
    }

    private interface AccountRowAdder {
        void add(String selectedAccountName, double initialPct);
    }

    private interface MfRowAdder {
        void add(String selectedMfName, double initialPct);
    }
}
