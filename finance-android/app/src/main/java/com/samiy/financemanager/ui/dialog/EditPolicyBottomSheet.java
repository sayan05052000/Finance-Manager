package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.InsurancePolicy;
import com.samiy.financemanager.util.DateTimeHelper;

import android.widget.CheckBox;
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
            Toast.makeText(getContext(), "You are not authorized to update policies.", Toast.LENGTH_SHORT).show();
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

        EditText etDueDate = v.findViewById(R.id.etEditPolicyDueDate);
        EditText etLastPaid = v.findViewById(R.id.etEditPolicyLastPaid);
        Button btnUpdate = v.findViewById(R.id.btnUpdatePolicy);

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        tvSubCategory.setText(policy.subCategory);
        tvPolicyNumber.setText(policy.policyNumber);
        tvPremium.setText(nf.format(policy.premiumAmount).replace("INR", "₹").replace(".00", ""));

        final String originalDue = policy.dueDate != null ? policy.dueDate : "";
        final String originalLastPaid = policy.lastPaid != null ? policy.lastPaid : "";

        etDueDate.setText(originalDue);
        etLastPaid.setText(originalLastPaid);

        // Setup calendar date pickers for Due Date and Last Paid Date
        DateTimeHelper.setupDatePicker(etDueDate);
        DateTimeHelper.setupDatePicker(etLastPaid);

        // Mark as Paid quick option
        View cardMarkPaid = v.findViewById(R.id.cardMarkPolicyPaid);
        CheckBox cbMarkPaid = v.findViewById(R.id.cbMarkPolicyPaid);
        TextView tvMarkPaidSub = v.findViewById(R.id.tvMarkPaidSub);

        SimpleDateFormat todaySdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        String todayStr = todaySdf.format(new Date());

        Runnable applyMarkAsPaid = () -> {
            cbMarkPaid.setChecked(true);
            etLastPaid.setText(todayStr);
            String shiftedDue = shiftDateByOneYear(originalDue);
            etDueDate.setText(shiftedDue);
            tvMarkPaidSub.setText("Payment date set to Today (" + todayStr + ") • Next due shifted to " + shiftedDue);
            btnUpdate.setText("Mark Paid & Update Dates");
        };

        Runnable revertMarkAsPaid = () -> {
            cbMarkPaid.setChecked(false);
            etDueDate.setText(originalDue);
            etLastPaid.setText(originalLastPaid);
            tvMarkPaidSub.setText("Sets payment date to Today and shifts Due Date by +1 year");
            btnUpdate.setText("Update Policy Dates");
        };

        cardMarkPaid.setOnClickListener(view -> {
            if (!cbMarkPaid.isChecked()) {
                applyMarkAsPaid.run();
                Toast.makeText(getContext(), "Payment date set to Today! Next due date shifted +1 year.", Toast.LENGTH_SHORT).show();
            } else {
                revertMarkAsPaid.run();
            }
        });

        boolean autoMarkPaid = getArguments() != null && getArguments().getBoolean(ARG_AUTO_MARK_PAID, false);
        if (autoMarkPaid) {
            applyMarkAsPaid.run();
        }

        btnUpdate.setOnClickListener(view -> {
            String newDue = etDueDate.getText().toString().trim();
            String newLastPaid = etLastPaid.getText().toString().trim();

            if (newDue.isEmpty()) {
                Toast.makeText(getContext(), "Please select a due date", Toast.LENGTH_SHORT).show();
                return;
            }

            policy.dueDate = newDue;
            policy.lastPaid = newLastPaid;

            btnUpdate.setEnabled(false);
            btnUpdate.setText("Updating in Google Sheet...");

            FinanceRepository.getInstance(requireContext()).updatePolicy(policy, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Policy dates updated successfully!", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onPolicyUpdated();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Update Policy Dates");
                        Toast.makeText(getContext(), "Failed to update: " + error, Toast.LENGTH_LONG).show();
                    }
                }
            });
        });

        return v;
    }

    private String shiftDateByOneYear(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.YEAR, 1);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(c.getTime());
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            Date d = sdf.parse(dateStr.trim());
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
