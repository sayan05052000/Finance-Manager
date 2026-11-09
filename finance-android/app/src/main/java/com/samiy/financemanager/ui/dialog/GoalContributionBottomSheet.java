package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.CalculationEngine;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.GoalItem;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class GoalContributionBottomSheet extends BaseBottomSheetDialogFragment {

    private static final String ARG_GOAL_NAME = "arg_goal_name";

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public static GoalContributionBottomSheet newInstance(GoalItem goal) {
        GoalContributionBottomSheet sheet = new GoalContributionBottomSheet();
        Bundle args = new Bundle();
        if (goal != null) {
            args.putString(ARG_GOAL_NAME, goal.name != null ? goal.name : "");
        }
        sheet.setArguments(args);
        return sheet;
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_goal_contribution, container, false);

        String goalName = getArguments() != null ? getArguments().getString(ARG_GOAL_NAME, "") : "";

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        GoalItem currentGoal = null;
        if (db != null && db.goals != null) {
            for (GoalItem g : db.goals) {
                if (g != null && g.name != null && g.name.trim().equalsIgnoreCase(goalName.trim())) {
                    currentGoal = g;
                    break;
                }
            }
        }

        if (currentGoal == null) {
            currentGoal = new GoalItem(goalName, 0, 0, 0, 0, "{}");
        }

        TextView tvTitle = v.findViewById(R.id.tvGoalContribTitle);
        TextView tvSubtitle = v.findViewById(R.id.tvGoalContribSubtitle);
        ImageView btnClose = v.findViewById(R.id.btnCloseContribSheet);
        TextView tvAccumulated = v.findViewById(R.id.tvGoalContribAccumulated);
        TextView tvPercent = v.findViewById(R.id.tvGoalContribPercent);
        ProgressBar pbProgress = v.findViewById(R.id.pbGoalContribProgress);
        TextView tvRemaining = v.findViewById(R.id.tvGoalContribRemaining);
        LinearLayout layoutAssets = v.findViewById(R.id.layoutContributingAssets);
        MaterialButton btnEdit = v.findViewById(R.id.btnEditGoalFromContrib);
        MaterialButton btnDone = v.findViewById(R.id.btnCloseGoalContrib);

        if (btnClose != null) btnClose.setOnClickListener(view -> dismiss());
        if (btnDone != null) btnDone.setOnClickListener(view -> dismiss());

        tvTitle.setText("🎯 " + currentGoal.name);
        tvSubtitle.setText("Target: " + formatCurrency(currentGoal.targetAmount));
        tvAccumulated.setText(formatCurrency(currentGoal.currentAmount));

        double achieved = currentGoal.achievedPercent;
        tvPercent.setText(String.format(Locale.US, "%.1f%% Achieved", achieved));
        pbProgress.setProgress((int) Math.min(100, Math.round(achieved)));

        double remainingToTarget = Math.max(0.0, currentGoal.targetAmount - currentGoal.currentAmount);
        if (remainingToTarget <= 0.0001 && currentGoal.targetAmount > 0) {
            tvRemaining.setText("🎉 Target Goal Fully Reached!");
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
        } else {
            tvRemaining.setText(formatCurrency(remainingToTarget) + " remaining to reach target");
            tvRemaining.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_gold));
        }

        final GoalItem goalForEdit = currentGoal;
        if (btnEdit != null) {
            btnEdit.setOnClickListener(view -> {
                dismiss();
                if (getParentFragmentManager() != null) {
                    EditGoalBottomSheet editSheet = EditGoalBottomSheet.newInstance(goalForEdit);
                    editSheet.show(getParentFragmentManager(), "edit_goal_" + goalForEdit.name);
                }
            });
        }

        // Render detailed contributing breakdown
        List<CalculationEngine.GoalContributionItem> breakdown = CalculationEngine.getGoalContributionBreakdown(currentGoal, db);
        if (layoutAssets != null) {
            layoutAssets.removeAllViews();
            if (breakdown.isEmpty()) {
                TextView tvEmpty = new TextView(getContext());
                tvEmpty.setText("No active contributing assets linked to this goal. Tap 'Edit Goal' to link an account or mutual fund.");
                tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
                tvEmpty.setTextSize(12f);
                tvEmpty.setPadding(0, 16, 0, 16);
                layoutAssets.addView(tvEmpty);
            } else {
                for (CalculationEngine.GoalContributionItem item : breakdown) {
                    View card = inflater.inflate(R.layout.item_policy, layoutAssets, false); // reuse structure or create view
                    // Create clean dynamic card
                    LinearLayout itemCard = new LinearLayout(getContext());
                    itemCard.setOrientation(LinearLayout.VERTICAL);
                    itemCard.setBackgroundResource(R.drawable.bg_card);
                    itemCard.setPadding(32, 28, 32, 28);
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp.setMargins(0, 0, 0, 18);
                    itemCard.setLayoutParams(cardLp);

                    // Row 1: Source Name + Contributed Rupee Amount
                    LinearLayout row1 = new LinearLayout(getContext());
                    row1.setOrientation(LinearLayout.HORIZONTAL);
                    row1.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    LinearLayout colLeft = new LinearLayout(getContext());
                    colLeft.setOrientation(LinearLayout.VERTICAL);
                    colLeft.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                    TextView tvSrcName = new TextView(getContext());
                    tvSrcName.setText(item.sourceName);
                    tvSrcName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                    tvSrcName.setTextSize(14f);
                    tvSrcName.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvSrcType = new TextView(getContext());
                    tvSrcType.setText(item.sourceType + " • " + String.format(Locale.US, "%.0f%% Allocated", item.allocationPercentage));
                    tvSrcType.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_sky));
                    tvSrcType.setTextSize(11f);
                    tvSrcType.setPadding(0, 4, 0, 0);

                    colLeft.addView(tvSrcName);
                    colLeft.addView(tvSrcType);

                    LinearLayout colRight = new LinearLayout(getContext());
                    colRight.setOrientation(LinearLayout.VERTICAL);
                    colRight.setGravity(android.view.Gravity.END);

                    TextView tvContributed = new TextView(getContext());
                    tvContributed.setText("+" + formatCurrency(item.contributedAmount));
                    tvContributed.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
                    tvContributed.setTextSize(15f);
                    tvContributed.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvBaseVal = new TextView(getContext());
                    tvBaseVal.setText("of " + formatCurrency(item.sourceTotal));
                    tvBaseVal.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
                    tvBaseVal.setTextSize(10.5f);

                    colRight.addView(tvContributed);
                    colRight.addView(tvBaseVal);

                    row1.addView(colLeft);
                    row1.addView(colRight);
                    itemCard.addView(row1);

                    // If mutual fund holdings exist, show nested holdings breakdown
                    if (item.holdingDetails != null && !item.holdingDetails.isEmpty()) {
                        View divider = new View(getContext());
                        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.border_dark));
                        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                        divLp.setMargins(0, 18, 0, 14);
                        divider.setLayoutParams(divLp);
                        itemCard.addView(divider);

                        TextView tvHoldingsHeader = new TextView(getContext());
                        tvHoldingsHeader.setText("Underlying Holdings (" + item.holdingDetails.size() + "):");
                        tvHoldingsHeader.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                        tvHoldingsHeader.setTextSize(10.5f);
                        tvHoldingsHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                        itemCard.addView(tvHoldingsHeader);

                        for (CalculationEngine.HoldingContributionDetail hDetail : item.holdingDetails) {
                            LinearLayout hRow = new LinearLayout(getContext());
                            hRow.setOrientation(LinearLayout.HORIZONTAL);
                            hRow.setPadding(0, 6, 0, 2);

                            TextView tvScheme = new TextView(getContext());
                            tvScheme.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                            tvScheme.setText("• " + hDetail.schemeName);
                            tvScheme.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                            tvScheme.setTextSize(11f);

                            TextView tvHVal = new TextView(getContext());
                            tvHVal.setText(formatCurrency(hDetail.contributedValue));
                            tvHVal.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                            tvHVal.setTextSize(11f);
                            tvHVal.setTypeface(null, android.graphics.Typeface.BOLD);

                            hRow.addView(tvScheme);
                            hRow.addView(tvHVal);
                            itemCard.addView(hRow);
                        }
                    }

                    layoutAssets.addView(itemCard);
                }
            }
        }

        return v;
    }
}
