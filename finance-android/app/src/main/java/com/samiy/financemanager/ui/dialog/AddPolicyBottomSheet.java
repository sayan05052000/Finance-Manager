package com.samiy.financemanager.ui.dialog;

import android.app.AlertDialog;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AddPolicyBottomSheet extends BottomSheetDialogFragment {

    public interface OnPolicyAddedListener {
        void onPolicyAdded();
    }

    private OnPolicyAddedListener listener;

    public static AddPolicyBottomSheet newInstance() {
        return new AddPolicyBottomSheet();
    }

    public void setOnPolicyAddedListener(OnPolicyAddedListener listener) {
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
            Toast.makeText(getContext(), "You are not authorized to add policies.", Toast.LENGTH_SHORT).show();
            dismiss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_add_policy, container, false);

        EditText etPolicyNumber = v.findViewById(R.id.etAddPolicyNumber);
        View btnSelectSubCategory = v.findViewById(R.id.btnSelectPolicySubCategory);
        TextView tvSubCategory = v.findViewById(R.id.tvAddPolicySubCategory);
        EditText etAmount = v.findViewById(R.id.etAddPolicyAmount);
        EditText etSumAssured = v.findViewById(R.id.etAddPolicySumAssured);
        EditText etDueDate = v.findViewById(R.id.etAddPolicyDueDate);
        EditText etLastPaid = v.findViewById(R.id.etAddPolicyLastPaid);
        EditText etInstallments = v.findViewById(R.id.etAddPolicyInstallments);
        EditText etNotes = v.findViewById(R.id.etAddPolicyNotes);
        Button btnSubmit = v.findViewById(R.id.btnAddPolicySubmit);

        // Pre-fill today's date for Last Paid
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        etLastPaid.setText(sdf.format(new Date()));

        // Wire calendar date pickers
        DateTimeHelper.setupDatePicker(etDueDate);
        DateTimeHelper.setupDatePicker(etLastPaid);

        // Subcategory modal selector
        String[] subCategoryOptions = {
                "LIC Policies",
                "Tata AIA Policy",
                "Health Insurance",
                "Term Insurance",
                "Brother LIC Policies",
                "Sister LIC Policies"
        };
        btnSelectSubCategory.setOnClickListener(view -> {
            new AlertDialog.Builder(requireContext(), R.style.CustomDarkDialog)
                    .setTitle("Select Policy Provider / Sub-Category")
                    .setItems(subCategoryOptions, (dialog, which) -> {
                        tvSubCategory.setText(subCategoryOptions[which]);
                    })
                    .show();
        });

        btnSubmit.setOnClickListener(view -> {
            String policyNum = etPolicyNumber.getText().toString().trim();
            String amountStr = etAmount.getText().toString().trim();
            String due = etDueDate.getText().toString().trim();
            String lastPaid = etLastPaid.getText().toString().trim();
            String subCat = tvSubCategory.getText().toString().trim();
            String sumAssuredStr = etSumAssured.getText().toString().trim();
            String installments = etInstallments.getText().toString().trim();
            String notes = etNotes.getText().toString().trim();

            if (policyNum.isEmpty()) {
                Toast.makeText(getContext(), "Please enter policy number", Toast.LENGTH_SHORT).show();
                return;
            }
            if (amountStr.isEmpty()) {
                Toast.makeText(getContext(), "Please enter premium amount", Toast.LENGTH_SHORT).show();
                return;
            }
            if (due.isEmpty()) {
                Toast.makeText(getContext(), "Please select due date from calendar", Toast.LENGTH_SHORT).show();
                return;
            }

            double premium = 0.0;
            try {
                premium = Double.parseDouble(amountStr);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Invalid premium amount", Toast.LENGTH_SHORT).show();
                return;
            }

            double sumAssured = 0.0;
            if (!sumAssuredStr.isEmpty()) {
                try {
                    sumAssured = Double.parseDouble(sumAssuredStr);
                } catch (Exception ignored) {}
            }

            String category = "Savings";
            if (subCat.contains("Brother")) category = "Brother Savings";
            else if (subCat.contains("Sister")) category = "Sister Savings";
            else if (subCat.contains("Insurance")) category = "Insurance";

            InsurancePolicy p = new InsurancePolicy(
                    policyNum,
                    premium,
                    lastPaid,
                    due,
                    installments,
                    subCat,
                    category,
                    sumAssured,
                    notes
            );

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Adding to Google Sheet...");

            FinanceRepository.getInstance(requireContext()).addPolicy(p, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Policy added successfully!", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onPolicyAdded();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Add Policy to Google Sheet");
                        Toast.makeText(getContext(), "Failed to add policy: " + error, Toast.LENGTH_LONG).show();
                    }
                }
            });
        });

        return v;
    }
}
