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
import androidx.core.widget.NestedScrollView;

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
import com.samiy.financemanager.util.KeyboardHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    private List<AllocationSourceAdapter.SourceItem> baseAccountItems = new ArrayList<>();
    private List<AllocationSourceAdapter.SourceItem> baseMfItems = new ArrayList<>();

    private static class AccountRowHolder {
        View rowView;
        Spinner spinner;
        EditText etPercent;
        TextView tvInfo;
        ImageView btnRemove;
        String selectedAccount;
        boolean isUpdatingAdapter = false;
    }

    private static class MfRowHolder {
        View rowView;
        Spinner spinner;
        EditText etPercent;
        TextView tvInfo;
        ImageView btnRemove;
        String selectedMf;
        boolean isUpdatingAdapter = false;
    }

    private final List<AccountRowHolder> accountRowHolders = new ArrayList<>();
    private final List<MfRowHolder> mfRowHolders = new ArrayList<>();

    private LinearLayout layoutAccountsContainer;
    private TextView tvEmptyAccounts;
    private TextView btnAddAccountRow;

    private LinearLayout layoutMfContainer;
    private TextView tvEmptyMf;
    private TextView btnAddMfRow;

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

        if (v instanceof NestedScrollView) {
            ((NestedScrollView) v).setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (vScroll, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                KeyboardHelper.hideKeyboard(vScroll);
            });
        }

        EditText etGoalName = v.findViewById(R.id.etGoalName);
        EditText etGoalTargetAmount = v.findViewById(R.id.etGoalTargetAmount);
        EditText etGoalCurrentAmount = v.findViewById(R.id.etGoalCurrentAmount);

        RadioGroup rgGoalLinkType = v.findViewById(R.id.rgGoalLinkType);
        RadioButton rbLinkNone = v.findViewById(R.id.rbLinkNone);
        RadioButton rbLinkAssets = v.findViewById(R.id.rbLinkAssets);

        LinearLayout layoutLinkedAssetsContainer = v.findViewById(R.id.layoutLinkedAssetsContainer);
        layoutAccountsContainer = v.findViewById(R.id.layoutAccountsContainer);
        tvEmptyAccounts = v.findViewById(R.id.tvEmptyAccounts);
        btnAddAccountRow = v.findViewById(R.id.btnAddAccountRow);

        layoutMfContainer = v.findViewById(R.id.layoutMfContainer);
        tvEmptyMf = v.findViewById(R.id.tvEmptyMf);
        btnAddMfRow = v.findViewById(R.id.btnAddMfRow);

        Button btnCancel = v.findViewById(R.id.btnCancelGoal);
        Button btnSubmit = v.findViewById(R.id.btnSubmitGoal);

        KeyboardHelper.setupDoneAction(etGoalName, etGoalTargetAmount, etGoalCurrentAmount);

        // Pre-fill basic fields
        etGoalName.setText(initialGoalName);
        etGoalTargetAmount.setText(initialTargetAmount > 0 ? String.format(Locale.US, "%.2f", initialTargetAmount) : "");
        etGoalCurrentAmount.setText(initialCurrentAmount > 0 ? String.format(Locale.US, "%.2f", initialCurrentAmount) : "");

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();

        // 1. Prepare Base Account Items (exempting this goal's own allocation)
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
            double alreadyUsed = CalculationEngine.getAccountAllocatedPercentage(accName, db != null ? db.goals : null, initialGoalName);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            baseAccountItems.add(new AllocationSourceAdapter.SourceItem(accName, available, alreadyUsed));
        }

        // 2. Prepare Base Mutual Fund Items (exempting this goal's own allocation)
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
            double alreadyUsed = CalculationEngine.getMutualFundAllocatedPercentage(mfName, db != null ? db.goals : null, initialGoalName);
            double available = Math.max(0.0, 100.0 - alreadyUsed);
            baseMfItems.add(new AllocationSourceAdapter.SourceItem(mfName, available, alreadyUsed));
        }

        // Wire up "Add" buttons
        btnAddAccountRow.setOnClickListener(vBtn -> {
            KeyboardHelper.hideKeyboard(vBtn);
            addAccountRow(null, 100.0);
        });
        btnAddMfRow.setOnClickListener(vBtn -> {
            KeyboardHelper.hideKeyboard(vBtn);
            addMfRow(null, 100.0);
        });

        // 3. Auto-populate from initialContribJson
        Map<String, Double> initialAccounts = new HashMap<>();
        Map<String, Double> initialMfs = new HashMap<>();
        try {
            JsonObject obj = JsonParser.parseString(initialContribJson).getAsJsonObject();
            if (obj.has("accounts") && obj.get("accounts").isJsonObject()) {
                JsonObject accs = obj.getAsJsonObject("accounts");
                for (String key : accs.keySet()) {
                    double p = accs.get(key).getAsDouble();
                    initialAccounts.put(key.trim(), (p <= 1.0) ? p * 100.0 : p);
                }
            }
            if (obj.has("mutual_funds") && obj.get("mutual_funds").isJsonArray()) {
                JsonArray mfs = obj.getAsJsonArray("mutual_funds");
                for (JsonElement el : mfs) {
                    if (!el.isJsonObject()) continue;
                    JsonObject rule = el.getAsJsonObject();
                    String val = rule.has("value") ? rule.get("value").getAsString().trim() : "";
                    if (!val.isEmpty()) {
                        double p = rule.has("percentage") ? rule.get("percentage").getAsDouble() : 1.0;
                        initialMfs.put(val, (p <= 1.0) ? p * 100.0 : p);
                    }
                }
            }
        } catch (Exception ignored) {}

        accountRowHolders.clear();
        mfRowHolders.clear();

        boolean hasLinkedAssets = !initialAccounts.isEmpty() || !initialMfs.isEmpty();
        if (hasLinkedAssets) {
            rbLinkAssets.setChecked(true);
            layoutLinkedAssetsContainer.setVisibility(View.VISIBLE);
            etGoalCurrentAmount.setEnabled(false);

            for (Map.Entry<String, Double> entry : initialAccounts.entrySet()) {
                addAccountRow(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Double> entry : initialMfs.entrySet()) {
                addMfRow(entry.getKey(), entry.getValue());
            }
        } else {
            rbLinkNone.setChecked(true);
            layoutLinkedAssetsContainer.setVisibility(View.GONE);
            etGoalCurrentAmount.setEnabled(true);
        }

        refreshAllAccountAdapters();
        refreshAllMfAdapters();

        // 4. Toggle Tracking Modes (Mutual Exclusivity)
        rgGoalLinkType.setOnCheckedChangeListener((group, checkedId) -> {
            KeyboardHelper.hideKeyboard(group);
            if (checkedId == R.id.rbLinkNone) {
                // Manual Tracking ON -> Disable and hide linked assets
                layoutLinkedAssetsContainer.setVisibility(View.GONE);
                etGoalCurrentAmount.setEnabled(true);
            } else if (checkedId == R.id.rbLinkAssets) {
                // Linked Assets ON -> Enable linked assets, disable manual amount input
                layoutLinkedAssetsContainer.setVisibility(View.VISIBLE);
                etGoalCurrentAmount.setEnabled(false);

                // If no rows exist yet, add one default account row
                if (accountRowHolders.isEmpty() && mfRowHolders.isEmpty()) {
                    addAccountRow(null, 100.0);
                }
            }
        });

        // 5. Cancel Button
        btnCancel.setOnClickListener(vBtn -> {
            KeyboardHelper.hideKeyboard(vBtn);
            dismiss();
        });

        // 6. Submit Changes
        btnSubmit.setOnClickListener(vBtn -> {
            KeyboardHelper.hideKeyboard(vBtn);
            String goalName = etGoalName.getText().toString().trim();
            if (goalName.isEmpty()) {
                FinMngrToast.showError(getContext(), "Goal Name cannot be empty.");
                return;
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
                for (AccountRowHolder holder : accountRowHolders) {
                    String accName = holder.selectedAccount != null ? holder.selectedAccount.trim() : "";
                    if (accName.isEmpty()) continue;

                    if (seenAccounts.contains(accName.toUpperCase(Locale.US))) {
                        FinMngrToast.showError(getContext(), "Bank Account '" + accName + "' is selected multiple times. Please adjust its percentage in a single row.");
                        return;
                    }
                    seenAccounts.add(accName.toUpperCase(Locale.US));

                    String pctStr = holder.etPercent.getText().toString().trim();
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

                    AllocationSourceAdapter.SourceItem baseItem = findBaseAccountItem(accName);
                    double available = baseItem != null ? baseItem.availablePercent : 100.0;
                    if (pct > available + 0.001) {
                        FinMngrToast.showError(getContext(), "Allocation for account '" + accName + "' (" + pct + "%) exceeds available capacity (" + String.format(Locale.US, "%.1f", available) + "%).");
                        return;
                    }

                    accountsObj.addProperty(accName, pct / 100.0);
                }

                // Validate Mutual Fund Rows
                List<String> seenMfs = new ArrayList<>();
                for (MfRowHolder holder : mfRowHolders) {
                    String mfName = holder.selectedMf != null ? holder.selectedMf.trim() : "";
                    if (mfName.isEmpty()) continue;

                    if (seenMfs.contains(mfName.toLowerCase(Locale.US))) {
                        FinMngrToast.showError(getContext(), "Holding '" + mfName + "' is selected multiple times. Please adjust its percentage in a single row.");
                        return;
                    }
                    seenMfs.add(mfName.toLowerCase(Locale.US));

                    String pctStr = holder.etPercent.getText().toString().trim();
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

                    AllocationSourceAdapter.SourceItem baseItem = findBaseMfItem(mfName);
                    double available = baseItem != null ? baseItem.availablePercent : 100.0;
                    if (pct > available + 0.001) {
                        FinMngrToast.showError(getContext(), "Allocation for holding '" + mfName + "' (" + pct + "%) exceeds available capacity (" + String.format(Locale.US, "%.1f", available) + "%).");
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
            GoalItem updatedGoal = new GoalItem(goalName, targetAmount, currentAmount, achievedPercent, 0.0, contributionJson);

            // If linked assets are present, compute real-time balance
            if (!contributionJson.equals("{}") && db != null) {
                updatedGoal.currentAmount = CalculationEngine.calculateGoalBalance(updatedGoal, db);
                updatedGoal.achievedPercent = updatedGoal.targetAmount > 0 ? (updatedGoal.currentAmount / updatedGoal.targetAmount) * 100.0 : 0.0;
            }

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
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("💾 Save Changes");
                        FinMngrToast.showError(getContext(), "Failed to update goal: " + error);
                    }
                }
            });
        });

        return v;
    }

    private AllocationSourceAdapter.SourceItem findBaseAccountItem(String name) {
        for (AllocationSourceAdapter.SourceItem it : baseAccountItems) {
            if (it.name.equalsIgnoreCase(name.trim())) return it;
        }
        return null;
    }

    private AllocationSourceAdapter.SourceItem findBaseMfItem(String name) {
        for (AllocationSourceAdapter.SourceItem it : baseMfItems) {
            if (it.name.equalsIgnoreCase(name.trim())) return it;
        }
        return null;
    }

    private void addAccountRow(String preferredAccountName, double initialPct) {
        // Collect currently selected accounts
        Set<String> selected = new HashSet<>();
        for (AccountRowHolder h : accountRowHolders) {
            if (h.selectedAccount != null && !h.selectedAccount.trim().isEmpty()) {
                selected.add(h.selectedAccount.trim().toUpperCase(Locale.US));
            }
        }

        String accountToPick = null;
        if (preferredAccountName != null && !preferredAccountName.trim().isEmpty()) {
            accountToPick = preferredAccountName.trim();
        } else {
            // Pick first unselected and selectable account
            for (AllocationSourceAdapter.SourceItem base : baseAccountItems) {
                if (!selected.contains(base.name.trim().toUpperCase(Locale.US)) && base.isSelectable()) {
                    accountToPick = base.name.trim();
                    break;
                }
            }
            if (accountToPick == null) {
                for (AllocationSourceAdapter.SourceItem base : baseAccountItems) {
                    if (!selected.contains(base.name.trim().toUpperCase(Locale.US))) {
                        accountToPick = base.name.trim();
                        break;
                    }
                }
            }
        }

        if (accountToPick == null) {
            FinMngrToast.show(getContext(), "All available bank accounts have already been added.", Toast.LENGTH_SHORT);
            return;
        }

        View rowView = LayoutInflater.from(getContext()).inflate(R.layout.item_goal_account_row, layoutAccountsContainer, false);
        AccountRowHolder holder = new AccountRowHolder();
        holder.rowView = rowView;
        holder.spinner = rowView.findViewById(R.id.spRowAccount);
        holder.etPercent = rowView.findViewById(R.id.etRowAccountPercent);
        holder.tvInfo = rowView.findViewById(R.id.tvRowAccountInfo);
        holder.btnRemove = rowView.findViewById(R.id.btnRemoveAccountRow);
        holder.selectedAccount = accountToPick;

        holder.etPercent.setText(String.format(Locale.US, "%d", (int) Math.round(initialPct)));
        KeyboardHelper.setupDoneAction(holder.etPercent);

        holder.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(parent);
                if (holder.isUpdatingAdapter) return;
                AllocationSourceAdapter.SourceItem it = (AllocationSourceAdapter.SourceItem) holder.spinner.getItemAtPosition(position);
                if (it != null && !it.name.equalsIgnoreCase(holder.selectedAccount)) {
                    holder.selectedAccount = it.name;
                    refreshAllAccountAdapters();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        holder.btnRemove.setOnClickListener(vRow -> {
            KeyboardHelper.hideKeyboard(vRow);
            accountRowHolders.remove(holder);
            layoutAccountsContainer.removeView(rowView);
            refreshAllAccountAdapters();
        });

        accountRowHolders.add(holder);
        layoutAccountsContainer.addView(rowView);
        refreshAllAccountAdapters();
    }

    private void addMfRow(String preferredMfName, double initialPct) {
        // Collect currently selected MFs
        Set<String> selected = new HashSet<>();
        for (MfRowHolder h : mfRowHolders) {
            if (h.selectedMf != null && !h.selectedMf.trim().isEmpty()) {
                selected.add(h.selectedMf.trim().toLowerCase(Locale.US));
            }
        }

        String mfToPick = null;
        if (preferredMfName != null && !preferredMfName.trim().isEmpty()) {
            mfToPick = preferredMfName.trim();
        } else {
            // Pick first unselected and selectable MF
            for (AllocationSourceAdapter.SourceItem base : baseMfItems) {
                if (!selected.contains(base.name.trim().toLowerCase(Locale.US)) && base.isSelectable()) {
                    mfToPick = base.name.trim();
                    break;
                }
            }
            if (mfToPick == null) {
                for (AllocationSourceAdapter.SourceItem base : baseMfItems) {
                    if (!selected.contains(base.name.trim().toLowerCase(Locale.US))) {
                        mfToPick = base.name.trim();
                        break;
                    }
                }
            }
        }

        if (mfToPick == null) {
            FinMngrToast.show(getContext(), "All available holdings have already been added.", Toast.LENGTH_SHORT);
            return;
        }

        View rowView = LayoutInflater.from(getContext()).inflate(R.layout.item_goal_mf_row, layoutMfContainer, false);
        MfRowHolder holder = new MfRowHolder();
        holder.rowView = rowView;
        holder.spinner = rowView.findViewById(R.id.spRowMf);
        holder.etPercent = rowView.findViewById(R.id.etRowMfPercent);
        holder.tvInfo = rowView.findViewById(R.id.tvRowMfInfo);
        holder.btnRemove = rowView.findViewById(R.id.btnRemoveMfRow);
        holder.selectedMf = mfToPick;

        holder.etPercent.setText(String.format(Locale.US, "%d", (int) Math.round(initialPct)));
        KeyboardHelper.setupDoneAction(holder.etPercent);

        holder.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(parent);
                if (holder.isUpdatingAdapter) return;
                AllocationSourceAdapter.SourceItem it = (AllocationSourceAdapter.SourceItem) holder.spinner.getItemAtPosition(position);
                if (it != null && !it.name.equalsIgnoreCase(holder.selectedMf)) {
                    holder.selectedMf = it.name;
                    refreshAllMfAdapters();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        holder.btnRemove.setOnClickListener(vRow -> {
            KeyboardHelper.hideKeyboard(vRow);
            mfRowHolders.remove(holder);
            layoutMfContainer.removeView(rowView);
            refreshAllMfAdapters();
        });

        mfRowHolders.add(holder);
        layoutMfContainer.addView(rowView);
        refreshAllMfAdapters();
    }

    private void refreshAllAccountAdapters() {
        Set<String> selectedAccounts = new HashSet<>();
        for (AccountRowHolder holder : accountRowHolders) {
            if (holder.selectedAccount != null && !holder.selectedAccount.trim().isEmpty()) {
                selectedAccounts.add(holder.selectedAccount.trim().toUpperCase(Locale.US));
            }
        }

        for (AccountRowHolder holder : accountRowHolders) {
            List<AllocationSourceAdapter.SourceItem> availableForThisRow = new ArrayList<>();
            int selectedIndex = 0;

            for (AllocationSourceAdapter.SourceItem base : baseAccountItems) {
                boolean isCurrentRowAccount = holder.selectedAccount != null &&
                        base.name.trim().equalsIgnoreCase(holder.selectedAccount.trim());
                boolean isUsedByOtherRow = selectedAccounts.contains(base.name.trim().toUpperCase(Locale.US)) && !isCurrentRowAccount;

                if (!isUsedByOtherRow) {
                    if (isCurrentRowAccount) {
                        selectedIndex = availableForThisRow.size();
                    }
                    availableForThisRow.add(base);
                }
            }

            holder.isUpdatingAdapter = true;
            AllocationSourceAdapter adapter = new AllocationSourceAdapter(requireContext(), availableForThisRow);
            holder.spinner.setAdapter(adapter);
            if (!availableForThisRow.isEmpty()) {
                holder.spinner.setSelection(selectedIndex);
            }
            holder.isUpdatingAdapter = false;

            updateAccountRowInfo(holder);
        }

        int unselectedCount = baseAccountItems.size() - selectedAccounts.size();
        if (btnAddAccountRow != null) {
            btnAddAccountRow.setAlpha(unselectedCount > 0 ? 1.0f : 0.45f);
        }

        if (tvEmptyAccounts != null) {
            tvEmptyAccounts.setVisibility(accountRowHolders.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void updateAccountRowInfo(AccountRowHolder holder) {
        if (holder == null || holder.tvInfo == null) return;
        AllocationSourceAdapter.SourceItem it = findBaseAccountItem(holder.selectedAccount != null ? holder.selectedAccount : "");
        if (it == null) return;

        if (!it.isSelectable()) {
            holder.tvInfo.setText("⚠️ Fully Allocated: " + String.format(Locale.US, "%.1f", it.consumedPercent) + "% used by other goals (0% remaining)");
            holder.tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
            holder.etPercent.setEnabled(false);
        } else {
            holder.tvInfo.setText("Available to allocate: " + String.format(Locale.US, "%.1f", it.availablePercent) + "% (" + String.format(Locale.US, "%.1f", it.consumedPercent) + "% used by other goals)");
            holder.tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            holder.etPercent.setEnabled(true);
        }
    }

    private void refreshAllMfAdapters() {
        Set<String> selectedMfs = new HashSet<>();
        for (MfRowHolder holder : mfRowHolders) {
            if (holder.selectedMf != null && !holder.selectedMf.trim().isEmpty()) {
                selectedMfs.add(holder.selectedMf.trim().toLowerCase(Locale.US));
            }
        }

        for (MfRowHolder holder : mfRowHolders) {
            List<AllocationSourceAdapter.SourceItem> availableForThisRow = new ArrayList<>();
            int selectedIndex = 0;

            for (AllocationSourceAdapter.SourceItem base : baseMfItems) {
                boolean isCurrentRowMf = holder.selectedMf != null &&
                        base.name.trim().equalsIgnoreCase(holder.selectedMf.trim());
                boolean isUsedByOtherRow = selectedMfs.contains(base.name.trim().toLowerCase(Locale.US)) && !isCurrentRowMf;

                if (!isUsedByOtherRow) {
                    if (isCurrentRowMf) {
                        selectedIndex = availableForThisRow.size();
                    }
                    availableForThisRow.add(base);
                }
            }

            holder.isUpdatingAdapter = true;
            AllocationSourceAdapter adapter = new AllocationSourceAdapter(requireContext(), availableForThisRow);
            holder.spinner.setAdapter(adapter);
            if (!availableForThisRow.isEmpty()) {
                holder.spinner.setSelection(selectedIndex);
            }
            holder.isUpdatingAdapter = false;

            updateMfRowInfo(holder);
        }

        int unselectedCount = baseMfItems.size() - selectedMfs.size();
        if (btnAddMfRow != null) {
            btnAddMfRow.setAlpha(unselectedCount > 0 ? 1.0f : 0.45f);
        }

        if (tvEmptyMf != null) {
            tvEmptyMf.setVisibility(mfRowHolders.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void updateMfRowInfo(MfRowHolder holder) {
        if (holder == null || holder.tvInfo == null) return;
        AllocationSourceAdapter.SourceItem it = findBaseMfItem(holder.selectedMf != null ? holder.selectedMf : "");
        if (it == null) return;

        if (!it.isSelectable()) {
            holder.tvInfo.setText("⚠️ Fully Allocated: " + String.format(Locale.US, "%.1f", it.consumedPercent) + "% used by other goals (0% remaining)");
            holder.tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
            holder.etPercent.setEnabled(false);
        } else {
            holder.tvInfo.setText("Available to allocate: " + String.format(Locale.US, "%.1f", it.availablePercent) + "% (" + String.format(Locale.US, "%.1f", it.consumedPercent) + "% used by other goals)");
            holder.tvInfo.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            holder.etPercent.setEnabled(true);
        }
    }
}
