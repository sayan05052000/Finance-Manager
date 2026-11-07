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

import java.text.NumberFormat;
import java.util.Locale;

public class EditPolicyBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_POLICY = "policy";
    private InsurancePolicy policy;

    public interface OnPolicyUpdatedListener {
        void onPolicyUpdated();
    }

    private OnPolicyUpdatedListener listener;

    public static EditPolicyBottomSheet newInstance(InsurancePolicy policy) {
        EditPolicyBottomSheet fragment = new EditPolicyBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_POLICY, policy);
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

        etDueDate.setText(policy.dueDate != null ? policy.dueDate : "");
        etLastPaid.setText(policy.lastPaid != null ? policy.lastPaid : "");

        // Setup calendar date pickers for Due Date and Last Paid Date
        DateTimeHelper.setupDatePicker(etDueDate);
        DateTimeHelper.setupDatePicker(etLastPaid);

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
}
