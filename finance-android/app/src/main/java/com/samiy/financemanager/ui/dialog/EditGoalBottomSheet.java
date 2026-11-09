package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

public class EditGoalBottomSheet extends BaseBottomSheetDialogFragment {

    public interface OnGoalUpdatedListener {
        void onGoalUpdated(GoalItem updatedGoal);
    }

    private static final String ARG_GOAL_NAME = "arg_goal_name";
    private static final String ARG_TARGET_AMOUNT = "arg_target_amount";
    private static final String ARG_CURRENT_AMOUNT = "arg_current_amount";
    private static final String ARG_CONTRIB_JSON = "arg_contrib_json";

    private OnGoalUpdatedListener listener;
    private String initialGoalName;
    private double initialTargetAmount;
    private double initialCurrentAmount;
    private String initialContribJson;

    public static EditGoalBottomSheet newInstance(GoalItem goal) {
        EditGoalBottomSheet sheet = new EditGoalBottomSheet();
        Bundle args = new Bundle();
        if (goal != null) {
            args.putString(ARG_GOAL_NAME, goal.name != null ? goal.name : "");
            args.putDouble(ARG_TARGET_AMOUNT, goal.targetAmount);
            args.putDouble(ARG_CURRENT_AMOUNT, goal.currentAmount);
            args.putString(ARG_CONTRIB_JSON, goal.contributionJson != null ? goal.contributionJson : "{}");
        }
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnGoalUpdatedListener(OnGoalUpdatedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            initialGoalName = getArguments().getString(ARG_GOAL_NAME, "");
            initialTargetAmount = getArguments().getDouble(ARG_TARGET_AMOUNT, 0.0);
            initialCurrentAmount = getArguments().getDouble(ARG_CURRENT_AMOUNT, 0.0);
            initialContribJson = getArguments().getString(ARG_CONTRIB_JSON, "{}");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.show(getContext(), "You are not authorized", Toast.LENGTH_SHORT);
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_edit_goal, container, false);

        EditText etGoalName = v.findViewById(R.id.etGoalName);
        EditText etGoalTargetAmount = v.findViewById(R.id.etGoalTargetAmount);
        EditText etGoalCurrentAmount = v.findViewById(R.id.etGoalCurrentAmount);

        RadioGroup rgGoalLinkType = v.findViewById(R.id.rgGoalLinkType);
        RadioButton rbLinkNone = v.findViewById(R.id.rbLinkNone);
        RadioButton rbLinkAccount = v.findViewById(R.id.rbLinkAccount);
        RadioButton rbLinkMutualFund = v.findViewById(R.id.rbLinkMutualFund);

        LinearLayout layoutLinkAccount = v.findViewById(R.id.layoutLinkAccount);
        Spinner spGoalAccount = v.findViewById(R.id.spGoalAccount);
        EditText etGoalAccountPercent = v.findViewById(R.id.etGoalAccountPercent);
        TextView tvAccountAllocationInfo = v.findViewById(R.id.tvAccountAllocationInfo);

        LinearLayout layoutLinkMutualFund = v.findViewById(R.id.layoutLinkMutualFund);
        Spinner spGoalMfType = v.findViewById(R.id.spGoalMfType);
        EditText etGoalMfPercent = v.findViewById(R.id.etGoalMfPercent);
        TextView tvMfAllocationInfo = v.findViewById(R.id.tvMfAllocationInfo);

        Button btnCancel = v.findViewById(R.id.btnCancelGoal);
        Button btnSubmit = v.findViewById(R.id.btnSubmitGoal);

        // Pre-fill basic fields
        etGoalName.setText(initialGoalName);
        etGoalTargetAmount.setText(initialTargetAmount > 0 ? String.format(Locale.US, "%.2f", initialTargetAmount) : "");
        etGoalCurrentAmount.setText(initialCurrentAmount > 0 ? String.format(Locale.US, "%.2f", initialCurrentAmount) : "");

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();

        // Parse initial contribution JSON
        String initialLinkedAccount = null;
        double initialAccountPct = 100.0;
        String initialLinkedMf = null;
        double initialMfPct = 100.0;
        boolean hasLinkedAccount = false;
        boolean hasLinkedMf = false;

        try {
            JsonObject obj = JsonParser.parseString(initialContribJson).getAsJsonObject();
            if (obj.has("accounts") && obj.get("accounts").isJsonObject()) {
                JsonObject accs = obj.getAsJsonObject("accounts");
                for (String key : accs.keySet()) {
                    initialLinkedAccount = key.trim();
                    hasLinkedAccount = true;
                    double p = accs.get(key).getAsDouble();
                    initialAccountPct = (p <= 1.0) ? p * 100.0 : p;
                    break;
                }
            }
            if (!hasLinkedAccount && obj.has("mutual_funds") && obj.get("mutual_funds").isJsonArray()) {
                JsonArray mfs = obj.getAsJsonArray("mutual_funds");
                for (JsonElement el : mfs) {
                    if (!el.isJsonObject()) continue;
                    JsonObject rule = el.getAsJsonObject();
                    initialLinkedMf = rule.has("value") ? rule.get("value").getAsString().trim() : "";
                    if (!initialLinkedMf.isEmpty()) {
                        hasLinkedMf = true;
                        double p = rule.has("percentage") ? rule.get("percentage").getAsDouble() : 1.0;
                        initialMfPct = (p <= 1.0) ? p * 100.0 : p;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        // Populate accounts using AllocationSourceAdapter (excluding this goal so its own allocation is available)
        List<AllocationSourceAdapter.SourceItem> accItems = new ArrayList<>();
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
        }
        for (String accName : rawAccs) {
            double alreadyUsed = CalculationEngine.getAccountAllocatedPercentage(accName, db != null ? db.goals : null, initialGoalName);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            accItems.add(new AllocationSourceAdapter.SourceItem(accName, available, alreadyUsed));
        }
        AllocationSourceAdapter accAdapter = new AllocationSourceAdapter(requireContext(), accItems);
        spGoalAccount.setAdapter(accAdapter);

        // Pre-select account
        int accInitialPos = 0;
        if (initialLinkedAccount != null) {
            for (int i = 0; i < accItems.size(); i++) {
                if (accItems.get(i).name.equalsIgnoreCase(initialLinkedAccount)) {
                    accInitialPos = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < accItems.size(); i++) {
                if (accItems.get(i).isSelectable()) {
                    accInitialPos = i;
                    break;
                }
            }
        }
        spGoalAccount.setSelection(accInitialPos);
        etGoalAccountPercent.setText(String.format(Locale.US, "%d", (int) Math.round(initialAccountPct)));

        // Populate mutual fund savings types using AllocationSourceAdapter (excluding this goal)
        List<AllocationSourceAdapter.SourceItem> mfItems = new ArrayList<>();
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
            double alreadyUsed = CalculationEngine.getMutualFundAllocatedPercentage(mfName, db != null ? db.goals : null, initialGoalName);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            mfItems.add(new AllocationSourceAdapter.SourceItem(mfName, available, alreadyUsed));
        }
        AllocationSourceAdapter mfAdapter = new AllocationSourceAdapter(requireContext(), mfItems);
        spGoalMfType.setAdapter(mfAdapter);

        // Pre-select MF
        int mfInitialPos = 0;
        if (initialLinkedMf != null) {
            for (int i = 0; i < mfItems.size(); i++) {
                if (mfItems.get(i).name.equalsIgnoreCase(initialLinkedMf)) {
                    mfInitialPos = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < mfItems.size(); i++) {
                if (mfItems.get(i).isSelectable()) {
                    mfInitialPos = i;
                    break;
                }
            }
        }
        spGoalMfType.setSelection(mfInitialPos);
        etGoalMfPercent.setText(String.format(Locale.US, "%d", (int) Math.round(initialMfPct)));

        // Dynamic ceiling info runners
        Runnable updateAccountAllocationInfo = () -> {
            AllocationSourceAdapter.SourceItem selected = (AllocationSourceAdapter.SourceItem) spGoalAccount.getSelectedItem();
            if (selected == null) return;
            if (tvAccountAllocationInfo != null) {
                if (!selected.isSelectable()) {
                    tvAccountAllocationInfo.setText("⚠️ Fully Allocated: " + String.format(Locale.US, "%.1f", selected.consumedPercent) + "% used by other goals (0% remaining)");
                    tvAccountAllocationInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                    etGoalAccountPercent.setText("0");
                    etGoalAccountPercent.setEnabled(false);
                } else {
                    tvAccountAllocationInfo.setText("Available to allocate: " + String.format(Locale.US, "%.1f", selected.availablePercent) + "% (" + String.format(Locale.US, "%.1f", selected.consumedPercent) + "% used by other goals)");
                    tvAccountAllocationInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
                    etGoalAccountPercent.setEnabled(true);
                }
            }
        };

        Runnable updateMfAllocationInfo = () -> {
            AllocationSourceAdapter.SourceItem selected = (AllocationSourceAdapter.SourceItem) spGoalMfType.getSelectedItem();
            if (selected == null) return;
            if (tvMfAllocationInfo != null) {
                if (!selected.isSelectable()) {
                    tvMfAllocationInfo.setText("⚠️ Fully Allocated: " + String.format(Locale.US, "%.1f", selected.consumedPercent) + "% used by other goals (0% remaining)");
                    tvMfAllocationInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                    etGoalMfPercent.setText("0");
                    etGoalMfPercent.setEnabled(false);
                } else {
                    tvMfAllocationInfo.setText("Available to allocate: " + String.format(Locale.US, "%.1f", selected.availablePercent) + "% (" + String.format(Locale.US, "%.1f", selected.consumedPercent) + "% used by other goals)");
                    tvMfAllocationInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
                    etGoalMfPercent.setEnabled(true);
                }
            }
        };

        spGoalAccount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AllocationSourceAdapter.SourceItem item = accAdapter.getItem(position);
                if (item != null && !item.isSelectable()) {
                    for (int i = 0; i < accAdapter.getCount(); i++) {
                        if (accAdapter.getItem(i).isSelectable()) {
                            spGoalAccount.setSelection(i);
                            return;
                        }
                    }
                }
                updateAccountAllocationInfo.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spGoalMfType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AllocationSourceAdapter.SourceItem item = mfAdapter.getItem(position);
                if (item != null && !item.isSelectable()) {
                    for (int i = 0; i < mfAdapter.getCount(); i++) {
                        if (mfAdapter.getItem(i).isSelectable()) {
                            spGoalMfType.setSelection(i);
                            return;
                        }
                    }
                }
                updateMfAllocationInfo.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set initial radio state
        if (hasLinkedAccount) {
            rbLinkAccount.setChecked(true);
            layoutLinkAccount.setVisibility(View.VISIBLE);
            layoutLinkMutualFund.setVisibility(View.GONE);
            updateAccountAllocationInfo.run();
        } else if (hasLinkedMf) {
            rbLinkMutualFund.setChecked(true);
            layoutLinkAccount.setVisibility(View.GONE);
            layoutLinkMutualFund.setVisibility(View.VISIBLE);
            updateMfAllocationInfo.run();
        } else {
            rbLinkNone.setChecked(true);
            layoutLinkAccount.setVisibility(View.GONE);
            layoutLinkMutualFund.setVisibility(View.GONE);
        }

        // Toggle linking visibility
        rgGoalLinkType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbLinkAccount) {
                layoutLinkAccount.setVisibility(View.VISIBLE);
                layoutLinkMutualFund.setVisibility(View.GONE);
                updateAccountAllocationInfo.run();
            } else if (checkedId == R.id.rbLinkMutualFund) {
                layoutLinkAccount.setVisibility(View.GONE);
                layoutLinkMutualFund.setVisibility(View.VISIBLE);
                updateMfAllocationInfo.run();
            } else {
                layoutLinkAccount.setVisibility(View.GONE);
                layoutLinkMutualFund.setVisibility(View.GONE);
            }
        });

        btnCancel.setOnClickListener(view -> dismiss());

        btnSubmit.setOnClickListener(view -> {
            if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                FinMngrToast.show(getContext(), "You are not authorized", Toast.LENGTH_SHORT);
                return;
            }

            String goalName = etGoalName.getText().toString().trim();
            if (goalName.isEmpty()) {
                etGoalName.setError("Goal name is required");
                etGoalName.requestFocus();
                return;
            }

            // Uniqueness check if name was changed
            if (!goalName.equalsIgnoreCase(initialGoalName) && db != null && db.goals != null) {
                for (GoalItem existing : db.goals) {
                    if (existing != null && existing.name != null && existing.name.trim().equalsIgnoreCase(goalName)) {
                        etGoalName.setError("A goal named '" + goalName + "' already exists");
                        etGoalName.requestFocus();
                        FinMngrToast.show(getContext(), "Goal '" + goalName + "' already exists!", Toast.LENGTH_LONG);
                        return;
                    }
                }
            }

            String targetStr = etGoalTargetAmount.getText().toString().trim();
            if (targetStr.isEmpty()) {
                etGoalTargetAmount.setError("Target amount is required");
                etGoalTargetAmount.requestFocus();
                return;
            }
            double targetAmount;
            try {
                targetAmount = Double.parseDouble(targetStr);
                if (targetAmount <= 0) {
                    etGoalTargetAmount.setError("Target must be greater than 0");
                    etGoalTargetAmount.requestFocus();
                    return;
                }
            } catch (Exception e) {
                etGoalTargetAmount.setError("Invalid target amount");
                etGoalTargetAmount.requestFocus();
                return;
            }

            String currStr = etGoalCurrentAmount.getText().toString().trim();
            double currentAmount = 0.0;
            if (!currStr.isEmpty()) {
                try {
                    currentAmount = Double.parseDouble(currStr);
                } catch (Exception ignored) {}
            }

            // Validate source allocations (sum <= 100%)
            if (rbLinkAccount.isChecked()) {
                AllocationSourceAdapter.SourceItem selectedAccItem = (AllocationSourceAdapter.SourceItem) spGoalAccount.getSelectedItem();
                String selectedAcc = selectedAccItem != null ? selectedAccItem.name : "";
                if (selectedAccItem != null && !selectedAccItem.isSelectable()) {
                    FinMngrToast.showError(getContext(), "Bank account '" + selectedAcc + "' is 100% allocated. Cannot be selected!");
                    return;
                }
                String pctStr = etGoalAccountPercent.getText().toString().trim();
                double requestedPct = 100.0;
                if (!pctStr.isEmpty()) {
                    try {
                        requestedPct = Double.parseDouble(pctStr);
                    } catch (Exception e) {
                        etGoalAccountPercent.setError("Invalid percentage");
                        etGoalAccountPercent.requestFocus();
                        return;
                    }
                }
                if (requestedPct <= 0) {
                    etGoalAccountPercent.setError("Percentage must be greater than 0%");
                    etGoalAccountPercent.requestFocus();
                    return;
                }
                double alreadyUsed = CalculationEngine.getAccountAllocatedPercentage(selectedAcc, db != null ? db.goals : null, initialGoalName);
                if (alreadyUsed + requestedPct > 100.0001) {
                    double maxAllowed = Math.max(0.0, 100.0 - alreadyUsed);
                    etGoalAccountPercent.setError("Total allocation cannot exceed 100%. Max available: " + String.format(Locale.US, "%.1f", maxAllowed) + "%");
                    etGoalAccountPercent.requestFocus();
                    FinMngrToast.showError(getContext(), "Total allocation for " + selectedAcc + " would be " + String.format(Locale.US, "%.1f", (alreadyUsed + requestedPct)) + "%. Must be <= 100%!");
                    return;
                }
            } else if (rbLinkMutualFund.isChecked()) {
                AllocationSourceAdapter.SourceItem selectedMfItem = (AllocationSourceAdapter.SourceItem) spGoalMfType.getSelectedItem();
                String selectedMfType = selectedMfItem != null ? selectedMfItem.name : "";
                if (selectedMfItem != null && !selectedMfItem.isSelectable()) {
                    FinMngrToast.showError(getContext(), "Mutual Fund '" + selectedMfType + "' is 100% allocated. Cannot be selected!");
                    return;
                }
                String pctStr = etGoalMfPercent.getText().toString().trim();
                double requestedPct = 100.0;
                if (!pctStr.isEmpty()) {
                    try {
                        requestedPct = Double.parseDouble(pctStr);
                    } catch (Exception e) {
                        etGoalMfPercent.setError("Invalid percentage");
                        etGoalMfPercent.requestFocus();
                        return;
                    }
                }
                if (requestedPct <= 0) {
                    etGoalMfPercent.setError("Percentage must be greater than 0%");
                    etGoalMfPercent.requestFocus();
                    return;
                }
                double alreadyUsed = CalculationEngine.getMutualFundAllocatedPercentage(selectedMfType, db != null ? db.goals : null, initialGoalName);
                if (alreadyUsed + requestedPct > 100.0001) {
                    double maxAllowed = Math.max(0.0, 100.0 - alreadyUsed);
                    etGoalMfPercent.setError("Total allocation cannot exceed 100%. Max available: " + String.format(Locale.US, "%.1f", maxAllowed) + "%");
                    etGoalMfPercent.requestFocus();
                    FinMngrToast.showError(getContext(), "Total allocation for " + selectedMfType + " would be " + String.format(Locale.US, "%.1f", (alreadyUsed + requestedPct)) + "%. Must be <= 100%!");
                    return;
                }
            }

            // Build Contribution JSON
            JsonObject contribObj = new JsonObject();
            JsonObject accountsObj = new JsonObject();
            JsonArray mfArray = new JsonArray();

            if (rbLinkAccount.isChecked()) {
                AllocationSourceAdapter.SourceItem selectedAccItem = (AllocationSourceAdapter.SourceItem) spGoalAccount.getSelectedItem();
                String selectedAcc = selectedAccItem != null ? selectedAccItem.name : "";
                String pctStr = etGoalAccountPercent.getText().toString().trim();
                double pct = 1.0;
                if (!pctStr.isEmpty()) {
                    try {
                        pct = Double.parseDouble(pctStr) / 100.0;
                    } catch (Exception ignored) {}
                }
                if (!selectedAcc.isEmpty()) {
                    accountsObj.addProperty(selectedAcc, pct);
                }
            } else if (rbLinkMutualFund.isChecked()) {
                AllocationSourceAdapter.SourceItem selectedMfItem = (AllocationSourceAdapter.SourceItem) spGoalMfType.getSelectedItem();
                String selectedMfType = selectedMfItem != null ? selectedMfItem.name : "";
                String pctStr = etGoalMfPercent.getText().toString().trim();
                double pct = 1.0;
                if (!pctStr.isEmpty()) {
                    try {
                        pct = Double.parseDouble(pctStr) / 100.0;
                    } catch (Exception ignored) {}
                }
                if (!selectedMfType.isEmpty()) {
                    JsonObject mfRule = new JsonObject();
                    mfRule.addProperty("column", "Savings Type");
                    mfRule.addProperty("value", selectedMfType);
                    mfRule.addProperty("percentage", pct);
                    mfArray.add(mfRule);
                }
            }

            contribObj.add("accounts", accountsObj);
            contribObj.add("mutual_funds", mfArray);
            String contributionJson = contribObj.toString();

            double achievedPercent = targetAmount > 0 ? (currentAmount / targetAmount) * 100.0 : 0.0;

            GoalItem updatedGoal = new GoalItem(goalName, targetAmount, currentAmount, achievedPercent, 0.0, contributionJson);

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Saving Changes...");

            FinanceRepository.getInstance(requireContext()).updateGoal(initialGoalName, updatedGoal, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        FinMngrToast.showSuccess(getContext(), "Goal '" + goalName + "' updated successfully!");
                        if (listener != null) listener.onGoalUpdated(updatedGoal);
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        FinMngrToast.showError(getContext(), error);
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("💾 Save Changes");
                    }
                }
            });
        });

        return v;
    }
}
