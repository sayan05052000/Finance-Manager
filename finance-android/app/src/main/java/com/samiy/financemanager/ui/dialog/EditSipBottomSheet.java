package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.SipItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EditSipBottomSheet extends BottomSheetDialogFragment {
    public interface OnSipUpdatedListener {
        void onSipUpdated(SipItem updatedSip);
    }

    private static final String ARG_SIP = "arg_sip";

    private SipItem originalSip;
    private OnSipUpdatedListener listener;

    private TextView tvFundName, tvRole;
    private EditText etCurrentAmount, etNextAmount;
    private Spinner spCurrentMonth, spCurrentStatus;
    private Spinner spNextMonth, spNextStatus;
    private Spinner spPlatform, spBankAccount;
    private Button btnSave, btnCancel;

    public static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    public static final String[] STATUSES = {
            "Not Paid", "Paid"
    };

    public static final String[] DEFAULT_PLATFORMS = {
            "amc/upiauto", "indmoney", "amc/enach", "groww", "NA"
    };

    public static final String[] DEFAULT_BANKS = {
            "Slice", "sbi", "Pnb", "Hdfc"
    };

    public static EditSipBottomSheet newInstance(SipItem sip) {
        EditSipBottomSheet fragment = new EditSipBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SIP, sip);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnSipUpdatedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            originalSip = (SipItem) getArguments().getSerializable(ARG_SIP);
        }
        if (originalSip == null) {
            originalSip = new SipItem();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_edit_sip, container, false);

        tvFundName = v.findViewById(R.id.tvEditSipFundName);
        tvRole = v.findViewById(R.id.tvEditSipRole);

        etCurrentAmount = v.findViewById(R.id.etSipCurrentAmount);
        spCurrentMonth = v.findViewById(R.id.spSipCurrentMonth);
        spCurrentStatus = v.findViewById(R.id.spSipCurrentStatus);

        etNextAmount = v.findViewById(R.id.etSipNextAmount);
        spNextMonth = v.findViewById(R.id.spSipNextMonth);
        spNextStatus = v.findViewById(R.id.spSipNextStatus);

        spPlatform = v.findViewById(R.id.spSipPlatform);
        spBankAccount = v.findViewById(R.id.spSipBankAccount);

        btnSave = v.findViewById(R.id.btnSipSave);
        btnCancel = v.findViewById(R.id.btnSipCancel);
        View btnClose = v.findViewById(R.id.btnEditSipClose);

        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dismiss());
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(view -> dismiss());
        }

        setupSpinners();
        populateData();

        btnSave.setOnClickListener(view -> saveSip());

        return v;
    }

    private void setupSpinners() {
        // Month Spinners
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, MONTHS);
        monthAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spCurrentMonth.setAdapter(monthAdapter);
        spNextMonth.setAdapter(monthAdapter);

        // Status Spinners
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, STATUSES);
        statusAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spCurrentStatus.setAdapter(statusAdapter);
        spNextStatus.setAdapter(statusAdapter);

        // Platform Spinner
        List<String> platformList = new ArrayList<>(Arrays.asList(DEFAULT_PLATFORMS));
        if (originalSip.platform != null && !originalSip.platform.trim().isEmpty() && !platformList.contains(originalSip.platform.trim())) {
            platformList.add(originalSip.platform.trim());
        }
        ArrayAdapter<String> platformAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, platformList);
        platformAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spPlatform.setAdapter(platformAdapter);

        // Bank Account Spinner
        List<String> bankList = new ArrayList<>(Arrays.asList(DEFAULT_BANKS));
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db != null && db.accounts != null) {
            for (Account a : db.accounts) {
                if (a.name != null && !a.name.trim().isEmpty() && !bankList.contains(a.name.trim())) {
                    bankList.add(a.name.trim());
                }
            }
        }
        if (originalSip.bankAccount != null && !originalSip.bankAccount.trim().isEmpty() && !bankList.contains(originalSip.bankAccount.trim())) {
            bankList.add(originalSip.bankAccount.trim());
        }
        ArrayAdapter<String> bankAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, bankList);
        bankAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spBankAccount.setAdapter(bankAdapter);
    }

    private void populateData() {
        tvFundName.setText(originalSip.fundName);
        tvRole.setText(originalSip.role != null && !originalSip.role.trim().isEmpty() ? originalSip.role.trim() : "SIP Fund");

        // Current Amount
        if (originalSip.currentSip > 0) {
            if (originalSip.currentSip == Math.floor(originalSip.currentSip)) {
                etCurrentAmount.setText(String.valueOf((long) originalSip.currentSip));
            } else {
                etCurrentAmount.setText(String.valueOf(originalSip.currentSip));
            }
        } else {
            etCurrentAmount.setText("0");
        }

        // Next Amount
        if (originalSip.nextSip > 0) {
            if (originalSip.nextSip == Math.floor(originalSip.nextSip)) {
                etNextAmount.setText(String.valueOf((long) originalSip.nextSip));
            } else {
                etNextAmount.setText(String.valueOf(originalSip.nextSip));
            }
        } else {
            etNextAmount.setText("0");
        }

        // Current Month selection
        selectSpinnerItem(spCurrentMonth, originalSip.currentMonth, "Nov");
        selectSpinnerItem(spNextMonth, originalSip.nextMonth, "Dec");

        // Status selection
        selectSpinnerItem(spCurrentStatus, originalSip.currentStatus, "Not Paid");
        selectSpinnerItem(spNextStatus, originalSip.nextStatus, "Not Paid");

        // Platform selection
        selectSpinnerItem(spPlatform, originalSip.platform, "amc/upiauto");

        // Bank selection
        selectSpinnerItem(spBankAccount, originalSip.bankAccount, "Slice");
    }

    private void selectSpinnerItem(Spinner spinner, String value, String defaultValue) {
        String target = (value != null && !value.trim().isEmpty()) ? value.trim() : defaultValue;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(target)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void saveSip() {
        String curAmtStr = etCurrentAmount.getText().toString().trim();
        String nextAmtStr = etNextAmount.getText().toString().trim();

        double curAmt = 0.0;
        double nextAmt = 0.0;

        try {
            if (!curAmtStr.isEmpty()) curAmt = Double.parseDouble(curAmtStr);
        } catch (NumberFormatException e) {
            etCurrentAmount.setError("Invalid amount");
            return;
        }

        try {
            if (!nextAmtStr.isEmpty()) nextAmt = Double.parseDouble(nextAmtStr);
        } catch (NumberFormatException e) {
            etNextAmount.setError("Invalid amount");
            return;
        }

        SipItem updated = originalSip.copy();
        updated.currentSip = curAmt;
        updated.nextSip = nextAmt;
        updated.currentMonth = spCurrentMonth.getSelectedItem() != null ? spCurrentMonth.getSelectedItem().toString() : "Nov";
        updated.nextMonth = spNextMonth.getSelectedItem() != null ? spNextMonth.getSelectedItem().toString() : "Dec";
        updated.currentStatus = spCurrentStatus.getSelectedItem() != null ? spCurrentStatus.getSelectedItem().toString() : "Not Paid";
        updated.nextStatus = spNextStatus.getSelectedItem() != null ? spNextStatus.getSelectedItem().toString() : "Not Paid";
        updated.platform = spPlatform.getSelectedItem() != null ? spPlatform.getSelectedItem().toString() : "amc/upiauto";
        updated.bankAccount = spBankAccount.getSelectedItem() != null ? spBankAccount.getSelectedItem().toString() : "Slice";

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        FinanceRepository.getInstance(requireContext()).updateSip(updated, new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase db) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "SIP updated successfully", Toast.LENGTH_SHORT).show();
                    if (listener != null) {
                        listener.onSipUpdated(updated);
                    }
                    dismiss();
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Changes");
                    Toast.makeText(getContext(), "Failed to save: " + error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
