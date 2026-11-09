package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.samiy.financemanager.R;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.GoalItem;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.util.FinMngrToast;

import java.util.ArrayList;
import java.util.List;

public class AddGoalBottomSheet extends BaseBottomSheetDialogFragment {
    public interface OnGoalAddedListener {
        void onGoalAdded(GoalItem newGoal);
    }

    private OnGoalAddedListener listener;

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
        RadioButton rbLinkAccount = v.findViewById(R.id.rbLinkAccount);
        RadioButton rbLinkMutualFund = v.findViewById(R.id.rbLinkMutualFund);

        LinearLayout layoutLinkAccount = v.findViewById(R.id.layoutLinkAccount);
        Spinner spGoalAccount = v.findViewById(R.id.spGoalAccount);
        EditText etGoalAccountPercent = v.findViewById(R.id.etGoalAccountPercent);
        android.widget.TextView tvAccountAllocationInfo = v.findViewById(R.id.tvAccountAllocationInfo);

        LinearLayout layoutLinkMutualFund = v.findViewById(R.id.layoutLinkMutualFund);
        Spinner spGoalMfType = v.findViewById(R.id.spGoalMfType);
        EditText etGoalMfPercent = v.findViewById(R.id.etGoalMfPercent);
        android.widget.TextView tvMfAllocationInfo = v.findViewById(R.id.tvMfAllocationInfo);

        Button btnCancel = v.findViewById(R.id.btnCancelGoal);
        Button btnSubmit = v.findViewById(R.id.btnSubmitGoal);

        // Populate accounts
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        List<String> accountNames = new ArrayList<>();
        if (db != null && db.accounts != null) {
            for (Account a : db.accounts) {
                if (a != null && a.name != null && !a.name.trim().isEmpty()) {
                    accountNames.add(a.name.trim());
                }
            }
        }
        if (accountNames.isEmpty()) {
            accountNames.add("PNB");
            accountNames.add("HDFC SAVINGS");
            accountNames.add("SBI");
        }
        ArrayAdapter<String> accAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, accountNames);
        accAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spGoalAccount.setAdapter(accAdapter);

        // Populate mutual fund savings types
        List<String> mfTypes = new ArrayList<>();
        if (db != null && db.holdings != null) {
            for (Holding h : db.holdings) {
                if (h != null) {
                    if (h.savingsType != null && !h.savingsType.trim().isEmpty() && !mfTypes.contains(h.savingsType.trim())) {
                        mfTypes.add(h.savingsType.trim());
                    }
                    if (h.savingsCategory != null && !h.savingsCategory.trim().isEmpty() && !mfTypes.contains(h.savingsCategory.trim())) {
                        mfTypes.add(h.savingsCategory.trim());
                    }
                }
            }
        }
        if (mfTypes.isEmpty()) {
            mfTypes.add("Mutual Fund");
            mfTypes.add("Emergency Mutual Fund");
            mfTypes.add("Family Savings");
            mfTypes.add("Kuvera Surplus(Family)");
        }
        ArrayAdapter<String> mfAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, mfTypes);
        mfAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spGoalMfType.setAdapter(mfAdapter);

        Runnable updateAccountAllocationInfo = () -> {
            String selectedAcc = spGoalAccount.getSelectedItem() != null ? spGoalAccount.getSelectedItem().toString() : "";
            if (selectedAcc.isEmpty() || db == null || db.goals == null) {
                if (tvAccountAllocationInfo != null) {
                    tvAccountAllocationInfo.setText("Available to allocate: 100%");
                    tvAccountAllocationInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_mint));
                }
                etGoalAccountPercent.setEnabled(true);
                return;
            }
            double alreadyUsed = com.samiy.financemanager.data.CalculationEngine.getAccountAllocatedPercentage(selectedAcc, db.goals, null);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            if (tvAccountAllocationInfo != null) {
                if (alreadyUsed >= 100.0) {
                    tvAccountAllocationInfo.setText("⚠️ Fully Allocated: " + String.format(java.util.Locale.US, "%.1f", alreadyUsed) + "% already allocated across goals (0% remaining)");
                    tvAccountAllocationInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.red_rose));
                    etGoalAccountPercent.setText("0");
                    etGoalAccountPercent.setEnabled(false);
                } else {
                    tvAccountAllocationInfo.setText("Available to allocate: " + String.format(java.util.Locale.US, "%.1f", available) + "% (" + String.format(java.util.Locale.US, "%.1f", alreadyUsed) + "% allocated across other goals)");
                    tvAccountAllocationInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_mint));
                    etGoalAccountPercent.setEnabled(true);
                    etGoalAccountPercent.setText(String.format(java.util.Locale.US, "%d", (int) Math.floor(available)));
                }
            }
        };

        Runnable updateMfAllocationInfo = () -> {
            String selectedMf = spGoalMfType.getSelectedItem() != null ? spGoalMfType.getSelectedItem().toString() : "";
            if (selectedMf.isEmpty() || db == null || db.goals == null) {
                if (tvMfAllocationInfo != null) {
                    tvMfAllocationInfo.setText("Available to allocate: 100%");
                    tvMfAllocationInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_mint));
                }
                etGoalMfPercent.setEnabled(true);
                return;
            }
            double alreadyUsed = com.samiy.financemanager.data.CalculationEngine.getMutualFundAllocatedPercentage(selectedMf, db.goals, null);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            if (tvMfAllocationInfo != null) {
                if (alreadyUsed >= 100.0) {
                    tvMfAllocationInfo.setText("⚠️ Fully Allocated: " + String.format(java.util.Locale.US, "%.1f", alreadyUsed) + "% already allocated across goals (0% remaining)");
                    tvMfAllocationInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.red_rose));
                    etGoalMfPercent.setText("0");
                    etGoalMfPercent.setEnabled(false);
                } else {
                    tvMfAllocationInfo.setText("Available to allocate: " + String.format(java.util.Locale.US, "%.1f", available) + "% (" + String.format(java.util.Locale.US, "%.1f", alreadyUsed) + "% allocated across other goals)");
                    tvMfAllocationInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_mint));
                    etGoalMfPercent.setEnabled(true);
                    etGoalMfPercent.setText(String.format(java.util.Locale.US, "%d", (int) Math.floor(available)));
                }
            }
        };

        spGoalAccount.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateAccountAllocationInfo.run();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        spGoalMfType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateMfAllocationInfo.run();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

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

            // Uniqueness check
            if (db != null && db.goals != null) {
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
                String selectedAcc = spGoalAccount.getSelectedItem() != null ? spGoalAccount.getSelectedItem().toString() : "";
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
                double alreadyUsed = com.samiy.financemanager.data.CalculationEngine.getAccountAllocatedPercentage(selectedAcc, db != null ? db.goals : null, null);
                if (alreadyUsed + requestedPct > 100.0001) {
                    double maxAllowed = Math.max(0.0, 100.0 - alreadyUsed);
                    etGoalAccountPercent.setError("Total allocation cannot exceed 100%. Max available: " + String.format(java.util.Locale.US, "%.1f", maxAllowed) + "%");
                    etGoalAccountPercent.requestFocus();
                    FinMngrToast.showError(getContext(), "Total allocation for " + selectedAcc + " would be " + String.format(java.util.Locale.US, "%.1f", (alreadyUsed + requestedPct)) + "%. Must be <= 100%!");
                    return;
                }
            } else if (rbLinkMutualFund.isChecked()) {
                String selectedMfType = spGoalMfType.getSelectedItem() != null ? spGoalMfType.getSelectedItem().toString() : "";
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
                double alreadyUsed = com.samiy.financemanager.data.CalculationEngine.getMutualFundAllocatedPercentage(selectedMfType, db != null ? db.goals : null, null);
                if (alreadyUsed + requestedPct > 100.0001) {
                    double maxAllowed = Math.max(0.0, 100.0 - alreadyUsed);
                    etGoalMfPercent.setError("Total allocation cannot exceed 100%. Max available: " + String.format(java.util.Locale.US, "%.1f", maxAllowed) + "%");
                    etGoalMfPercent.requestFocus();
                    FinMngrToast.showError(getContext(), "Total allocation for " + selectedMfType + " would be " + String.format(java.util.Locale.US, "%.1f", (alreadyUsed + requestedPct)) + "%. Must be <= 100%!");
                    return;
                }
            }

            // Build Contribution JSON
            JsonObject contribObj = new JsonObject();
            JsonObject accountsObj = new JsonObject();
            JsonArray mfArray = new JsonArray();

            if (rbLinkAccount.isChecked()) {
                String selectedAcc = spGoalAccount.getSelectedItem() != null ? spGoalAccount.getSelectedItem().toString() : "";
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
                String selectedMfType = spGoalMfType.getSelectedItem() != null ? spGoalMfType.getSelectedItem().toString() : "";
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

            GoalItem newGoal = new GoalItem(goalName, targetAmount, currentAmount, achievedPercent, 0.0, contributionJson);

            // Cross-check all allocations across existing goals
            if (db != null && db.goals != null) {
                String allocErr = com.samiy.financemanager.data.CalculationEngine.validateGoalAllocations(newGoal, db.goals);
                if (allocErr != null) {
                    FinMngrToast.showError(getContext(), allocErr);
                    return;
                }
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
                        FinMngrToast.showError(getContext(), error);
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("＋ Add Goal");
                    }
                }
            });
        });

        return v;
    }
}
