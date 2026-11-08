package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.util.DateTimeHelper;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditPolicyBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POLICY = "policy";
    private static final String ARG_AUTO_MARK_PAID = "auto_mark_paid";
    private InsurancePolicy policy;

    public interface OnPolicyUpdatedListener {
        void onPolicyUpdated();
    }

    private OnPolicyUpdatedListener listener;

    public static EditPolicyBottomSheet newInstance(InsurancePolicy policy) {
        return newInstance(policy, false);
    }

    public static EditPolicyBottomSheet newInstance(InsurancePolicy policy, boolean autoMarkPaid) {
        EditPolicyBottomSheet fragment = new EditPolicyBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_POLICY, policy);
        args.putBoolean(ARG_AUTO_MARK_PAID, autoMarkPaid);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnPolicyUpdatedListener(OnPolicyUpdatedListener listener) {
        this.listener = listener;
    }

    public static boolean isPolicyFullyPaidOrMatured(InsurancePolicy policy) {
        if (policy == null) return false;

        // 1. Installments Remaining check
        if (policy.installmentsRemaining != null && !policy.installmentsRemaining.trim().isEmpty()) {
            String rem = policy.installmentsRemaining.trim();
            try {
                double remVal = Double.parseDouble(rem);
                if (remVal <= 0) return true;
            } catch (Exception ignored) {}
            if (rem.equalsIgnoreCase("0") || rem.equalsIgnoreCase("0.0") ||
                    rem.equalsIgnoreCase("matured") || rem.equalsIgnoreCase("completed")) {
                return true;
            }
        }

        // 2. Notes check
        if (policy.notes != null) {
            String n = policy.notes.toLowerCase();
            if (n.contains("fully paid") || n.contains("matured") || n.contains("surrender pending") || n.contains("stopped")) {
                return true;
            }
        }

        // 3. Last Paid year reaches or exceeds Maturity year
        if (policy.lastDate != null && !policy.lastDate.trim().isEmpty() &&
                policy.lastPaid != null && !policy.lastPaid.trim().isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                Date mat = sdf.parse(DateTimeHelper.formatDisplayDate(policy.lastDate));
                Date paid = sdf.parse(DateTimeHelper.formatDisplayDate(policy.lastPaid));
                if (mat != null && paid != null) {
                    Calendar matCal = Calendar.getInstance();
                    matCal.setTime(mat);
                    Calendar paidCal = Calendar.getInstance();
                    paidCal.setTime(paid);
                    if (paidCal.get(Calendar.YEAR) >= matCal.get(Calendar.YEAR)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Due Date reached or exceeded Maturity Date AND lastPaid is in the same year or after
        if (policy.lastDate != null && !policy.lastDate.trim().isEmpty() &&
                policy.dueDate != null && !policy.dueDate.trim().isEmpty() &&
                policy.lastPaid != null && !policy.lastPaid.trim().isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                Date mat = sdf.parse(DateTimeHelper.formatDisplayDate(policy.lastDate));
                Date due = sdf.parse(DateTimeHelper.formatDisplayDate(policy.dueDate));
                Date paid = sdf.parse(DateTimeHelper.formatDisplayDate(policy.lastPaid));
                if (mat != null && due != null && paid != null) {
                    if (due.compareTo(mat) >= 0 && (paid.compareTo(due) >= 0 || paid.compareTo(mat) >= 0)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.show(getContext(), "You are not authorized to update policies.", Toast.LENGTH_SHORT);
            dismiss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_edit_policy, container, false);

        if (getArguments() != null) {
            policy = (InsurancePolicy) getArguments().getSerializable(ARG_POLICY);
        }

        if (policy == null) {
            dismiss();
            return null;
        }

        TextView tvSubCategory = v.findViewById(R.id.tvEditPolicySubCategory);
        TextView tvPolicyNumber = v.findViewById(R.id.tvEditPolicyNumber);
        TextView tvPremium = v.findViewById(R.id.tvEditPolicyPremium);

        View cardMaturedNotice = v.findViewById(R.id.cardPolicyMaturedNotice);
        View cardMarkPaid = v.findViewById(R.id.cardMarkPolicyPaid);
        CheckBox cbMarkPaid = v.findViewById(R.id.cbMarkPolicyPaid);
        TextView tvMarkPaidSub = v.findViewById(R.id.tvMarkPaidSub);

        TextView tvDueDateLockedTag = v.findViewById(R.id.tvDueDateLockedTag);
        TextView tvDueDateHelper = v.findViewById(R.id.tvDueDateHelper);
        EditText etDueDate = v.findViewById(R.id.etEditPolicyDueDate);
        EditText etLastPaid = v.findViewById(R.id.etEditPolicyLastPaid);
        Button btnUpdate = v.findViewById(R.id.btnUpdatePolicy);

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        String subCat = policy.subCategory != null ? policy.subCategory.trim() : "";
        tvSubCategory.setText(subCat.isEmpty() ? "Insurance Policy" : subCat);
        String subCatUpper = subCat.toUpperCase();
        if (subCatUpper.contains("HEALTH")) {
            tvSubCategory.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.green_mint));
        } else if (subCatUpper.contains("TATA") || subCatUpper.contains("TERM")) {
            tvSubCategory.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.blue_sky));
        } else {
            tvSubCategory.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.amber_gold));
        }
        tvPolicyNumber.setText(policy.policyNumber);
        tvPremium.setText(nf.format(policy.premiumAmount).replace("INR", "₹").replace(".00", ""));

        final String originalDue = policy.dueDate != null ? policy.dueDate : "";
        final String originalLastPaid = policy.lastPaid != null ? policy.lastPaid : "";

        etDueDate.setText(originalDue);
        etLastPaid.setText(originalLastPaid);

        boolean isMatured = isPolicyFullyPaidOrMatured(policy);

        SimpleDateFormat todaySdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        String todayStr = todaySdf.format(new Date());

        if (isMatured) {
            // Policy already fully paid or reached maturity: Remove option to mark as paid
            if (cardMarkPaid != null) cardMarkPaid.setVisibility(View.GONE);
            if (cardMaturedNotice != null) cardMaturedNotice.setVisibility(View.VISIBLE);
            if (tvDueDateLockedTag != null) tvDueDateLockedTag.setVisibility(View.GONE);
            if (tvDueDateHelper != null) tvDueDateHelper.setVisibility(View.GONE);

            // In matured state, user can only do manual date adjustments if necessary
            DateTimeHelper.setupDueDatePicker(etDueDate, policy.lastDate);
            DateTimeHelper.setupPastDatePicker(etLastPaid);
        } else {
            if (cardMaturedNotice != null) cardMaturedNotice.setVisibility(View.GONE);
            if (cardMarkPaid != null) cardMarkPaid.setVisibility(View.VISIBLE);

            final String calculatedDue = calculateNextDueDate(originalDue, policy.lastDate);

            Runnable applyMarkAsPaid = () -> {
                cbMarkPaid.setChecked(true);
                etLastPaid.setText(todayStr);

                // Payment date can NEVER be in the future
                DateTimeHelper.setupPastDatePicker(etLastPaid);

                // During marking as paid, NO option to choose due date: locked & auto-calculated
                etDueDate.setText(calculatedDue);
                etDueDate.setOnClickListener(null);
                etDueDate.setClickable(false);
                etDueDate.setFocusable(false);
                if (tvDueDateLockedTag != null) tvDueDateLockedTag.setVisibility(View.VISIBLE);
                if (tvDueDateHelper != null) tvDueDateHelper.setVisibility(View.VISIBLE);

                tvMarkPaidSub.setText("Payment set to Today (" + todayStr + ") • Due auto-shifted to " + calculatedDue);
                btnUpdate.setText("Mark Paid & Update Dates");
            };

            Runnable revertMarkAsPaid = () -> {
                cbMarkPaid.setChecked(false);
                etDueDate.setText(originalDue);
                etLastPaid.setText(originalLastPaid);

                // Re-enable manual due date calendar (cannot select past date, cannot exceed maturity date)
                DateTimeHelper.setupDueDatePicker(etDueDate, policy.lastDate);
                DateTimeHelper.setupPastDatePicker(etLastPaid);

                if (tvDueDateLockedTag != null) tvDueDateLockedTag.setVisibility(View.GONE);
                if (tvDueDateHelper != null) tvDueDateHelper.setVisibility(View.GONE);

                tvMarkPaidSub.setText("Defaults to Today • Auto-shifts Due Date +1 year");
                btnUpdate.setText("Update Policy Dates");
            };

            cardMarkPaid.setOnClickListener(view -> {
                if (!cbMarkPaid.isChecked()) {
                    applyMarkAsPaid.run();
                    FinMngrToast.show(getContext(), "Payment date set to Today! Due date auto-shifted to " + calculatedDue, Toast.LENGTH_SHORT);
                } else {
                    revertMarkAsPaid.run();
                }
            });

            boolean autoMarkPaid = getArguments() != null && getArguments().getBoolean(ARG_AUTO_MARK_PAID, false);
            if (autoMarkPaid) {
                applyMarkAsPaid.run();
            } else {
                revertMarkAsPaid.run();
            }
        }

        btnUpdate.setOnClickListener(view -> {
            String newDue = etDueDate.getText().toString().trim();
            String newLastPaid = etLastPaid.getText().toString().trim();

            if (newDue.isEmpty()) {
                FinMngrToast.show(getContext(), "Please select a due date", Toast.LENGTH_SHORT);
                return;
            }

            if (cbMarkPaid.isChecked()) {
                // If marking as paid, check if this is the final installment
                if (policy.lastDate != null && !policy.lastDate.trim().isEmpty()) {
                    String matClean = DateTimeHelper.formatDisplayDate(policy.lastDate);
                    String dueClean = DateTimeHelper.formatDisplayDate(newDue);
                    String origDueClean = DateTimeHelper.formatDisplayDate(originalDue);
                    if (dueClean.equals(matClean) || origDueClean.equals(matClean)) {
                        policy.installmentsRemaining = "0";
                    }
                }
            }

            policy.dueDate = newDue;
            policy.lastPaid = newLastPaid;

            btnUpdate.setEnabled(false);
            btnUpdate.setText("Updating in Google Sheet...");

            FinanceRepository.getInstance(requireContext()).updatePolicy(policy, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), "Policy dates updated successfully!", Toast.LENGTH_SHORT);
                        if (listener != null) listener.onPolicyUpdated();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Update Policy Dates");
                        FinMngrToast.show(getContext(), "Failed to update: " + error, Toast.LENGTH_LONG);
                    }
                }
            });
        });

        return v;
    }

    private String calculateNextDueDate(String currentDueStr, String maturityDateStr) {
        String shifted = shiftDateByOneYear(currentDueStr);
        if (maturityDateStr != null && !maturityDateStr.trim().isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                Date shiftedDate = sdf.parse(DateTimeHelper.formatDisplayDate(shifted));
                Date matDate = sdf.parse(DateTimeHelper.formatDisplayDate(maturityDateStr));
                if (shiftedDate != null && matDate != null) {
                    if (shiftedDate.after(matDate)) {
                        // Due date can NEVER be greater than maturity date: cap it at maturity date!
                        return DateTimeHelper.formatDisplayDate(maturityDateStr);
                    }
                }
            } catch (Exception ignored) {}
        }
        return shifted;
    }

    private String shiftDateByOneYear(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.YEAR, 1);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.getTime());
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date d = sdf.parse(DateTimeHelper.formatDisplayDate(dateStr));
            if (d != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                c.add(Calendar.YEAR, 1);
                return sdf.format(c.getTime());
            }
        } catch (Exception ignored) {}

        String[] parts = dateStr.trim().split("/");
        if (parts.length == 3) {
            try {
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int year = Integer.parseInt(parts[2].trim()) + 1;
                return String.format(Locale.US, "%02d/%02d/%04d", day, month, year);
            } catch (Exception ignored) {}
        }

        Calendar c = Calendar.getInstance();
        c.add(Calendar.YEAR, 1);
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.getTime());
    }
}
