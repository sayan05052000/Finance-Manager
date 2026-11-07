package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
    private TextView tvCurrentMonth, tvNextMonth;
    private TextView btnToggleCurrentNotPaid, btnToggleCurrentPaid;
    private TextView btnToggleNextNotPaid, btnToggleNextPaid;
    private TextView tvSelectedPlatform, tvSelectedBankAccount;
    private Button btnSave, btnCancel;

    private String currentStatus = "Not Paid";
    private String nextStatus = "Not Paid";

    public static final String[] MONTHS = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
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

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
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
        if (!com.samiy.financemanager.auth.GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            android.widget.Toast.makeText(requireContext(), "You are not authorized", android.widget.Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_edit_sip, container, false);

        tvFundName = v.findViewById(R.id.tvEditSipFundName);
        tvRole = v.findViewById(R.id.tvEditSipRole);

        etCurrentAmount = v.findViewById(R.id.etSipCurrentAmount);
        etNextAmount = v.findViewById(R.id.etSipNextAmount);
        com.samiy.financemanager.util.KeyboardHelper.setupDoneAction(etCurrentAmount, etNextAmount);

        tvCurrentMonth = v.findViewById(R.id.tvCurrentMonth);
        tvNextMonth = v.findViewById(R.id.tvNextMonth);

        btnToggleCurrentNotPaid = v.findViewById(R.id.btnToggleCurrentNotPaid);
        btnToggleCurrentPaid = v.findViewById(R.id.btnToggleCurrentPaid);
        btnToggleNextNotPaid = v.findViewById(R.id.btnToggleNextNotPaid);
        btnToggleNextPaid = v.findViewById(R.id.btnToggleNextPaid);

        tvSelectedPlatform = v.findViewById(R.id.tvSelectedPlatform);
        tvSelectedBankAccount = v.findViewById(R.id.tvSelectedBankAccount);

        btnSave = v.findViewById(R.id.btnSipSave);
        btnCancel = v.findViewById(R.id.btnSipCancel);
        View btnClose = v.findViewById(R.id.btnEditSipClose);

        if (btnClose != null) btnClose.setOnClickListener(view -> dismiss());
        if (btnCancel != null) btnCancel.setOnClickListener(view -> dismiss());

        // Month Picker Click Listeners
        View btnSelectCurrentMonth = v.findViewById(R.id.btnSelectCurrentMonth);
        if (btnSelectCurrentMonth != null) {
            btnSelectCurrentMonth.setOnClickListener(view -> {
                hideKeyboard();
                showSingleChoiceDialog("Select Current Month", MONTHS, tvCurrentMonth.getText().toString(), selected -> {
                    tvCurrentMonth.setText(selected);
                });
            });
        }

        View btnSelectNextMonth = v.findViewById(R.id.btnSelectNextMonth);
        if (btnSelectNextMonth != null) {
            btnSelectNextMonth.setOnClickListener(view -> {
                hideKeyboard();
                showSingleChoiceDialog("Select Next Month", MONTHS, tvNextMonth.getText().toString(), selected -> {
                    tvNextMonth.setText(selected);
                });
            });
        }

        // Platform Picker Click Listener
        View btnSelectPlatform = v.findViewById(R.id.btnSelectPlatform);
        if (btnSelectPlatform != null) {
            btnSelectPlatform.setOnClickListener(view -> {
                hideKeyboard();
                List<String> platforms = new ArrayList<>(Arrays.asList(DEFAULT_PLATFORMS));
                String curPlat = tvSelectedPlatform.getText().toString().trim();
                if (!curPlat.isEmpty() && !platforms.contains(curPlat)) {
                    platforms.add(curPlat);
                }
                String[] platArray = platforms.toArray(new String[0]);
                showSingleChoiceDialog("Select Platform / Mode", platArray, curPlat, selected -> {
                    tvSelectedPlatform.setText(selected);
                });
            });
        }

        // Bank Account Picker Click Listener
        View btnSelectBankAccount = v.findViewById(R.id.btnSelectBankAccount);
        if (btnSelectBankAccount != null) {
            btnSelectBankAccount.setOnClickListener(view -> {
                hideKeyboard();
                List<String> banks = new ArrayList<>(Arrays.asList(DEFAULT_BANKS));
                FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
                if (db != null && db.accounts != null) {
                    for (Account a : db.accounts) {
                        if (a.name != null && !a.name.trim().isEmpty() && !banks.contains(a.name.trim())) {
                            banks.add(a.name.trim());
                        }
                    }
                }
                String curBank = tvSelectedBankAccount.getText().toString().trim();
                if (!curBank.isEmpty() && !banks.contains(curBank)) {
                    banks.add(curBank);
                }
                String[] bankArray = banks.toArray(new String[0]);
                showSingleChoiceDialog("Select Linked Bank Account", bankArray, curBank, selected -> {
                    tvSelectedBankAccount.setText(selected);
                });
            });
        }

        // Status Toggles
        btnToggleCurrentNotPaid.setOnClickListener(view -> {
            hideKeyboard();
            setCurrentStatus("Not Paid");
        });
        btnToggleCurrentPaid.setOnClickListener(view -> {
            hideKeyboard();
            setCurrentStatus("Paid");
        });

        btnToggleNextNotPaid.setOnClickListener(view -> {
            hideKeyboard();
            setNextStatus("Not Paid");
        });
        btnToggleNextPaid.setOnClickListener(view -> {
            hideKeyboard();
            setNextStatus("Paid");
        });

        // Setup dismiss keyboard when clicking anywhere outside of EditTexts
        setupKeyboardDismissOnTouch(v);

        populateData();

        btnSave.setOnClickListener(view -> saveSip());

        return v;
    }

    private void setupKeyboardDismissOnTouch(View root) {
        View[] nonEditableViews = new View[] {
                root.findViewById(R.id.scrollEditSipRoot),
                root.findViewById(R.id.layoutEditSipContainer),
                root.findViewById(R.id.cardEditSipCurrent),
                root.findViewById(R.id.cardEditSipNext),
                root.findViewById(R.id.cardEditSipConfig),
                tvFundName,
                tvRole
        };

        for (View v : nonEditableViews) {
            if (v != null) {
                v.setOnTouchListener((view, motionEvent) -> {
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                        hideKeyboard();
                    }
                    return false;
                });
            }
        }
    }

    private void hideKeyboard() {
        if (getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View view = getDialog() != null ? getDialog().getCurrentFocus() : null;
            if (view == null && getView() != null) {
                view = getView().findFocus();
            }
            if (view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                view.clearFocus();
            }
        }
    }

    private interface OnChoiceSelectedListener {
        void onSelected(String selected);
    }

    private void showSingleChoiceDialog(String title, String[] items, String currentSelection, OnChoiceSelectedListener onSelected) {
        int checkedItem = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(currentSelection.trim())) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext(), R.style.CustomDarkDialog)
                .setTitle(title)
                .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                    if (which >= 0 && which < items.length) {
                        onSelected.onSelected(items[which]);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setCurrentStatus(String status) {
        this.currentStatus = status;
        if ("Paid".equalsIgnoreCase(status)) {
            btnToggleCurrentPaid.setBackgroundResource(R.drawable.bg_chip_green);
            btnToggleCurrentPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));

            btnToggleCurrentNotPaid.setBackgroundResource(R.drawable.bg_pill_dark);
            btnToggleCurrentNotPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        } else {
            btnToggleCurrentNotPaid.setBackgroundResource(R.drawable.bg_pill_dark);
            btnToggleCurrentNotPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));

            btnToggleCurrentPaid.setBackgroundResource(R.drawable.bg_pill_dark);
            btnToggleCurrentPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        }
    }

    private void setNextStatus(String status) {
        this.nextStatus = status;
        if ("Paid".equalsIgnoreCase(status)) {
            btnToggleNextPaid.setBackgroundResource(R.drawable.bg_chip_green);
            btnToggleNextPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));

            btnToggleNextNotPaid.setBackgroundResource(R.drawable.bg_pill_dark);
            btnToggleNextNotPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        } else {
            btnToggleNextNotPaid.setBackgroundResource(R.drawable.bg_pill_dark);
            btnToggleNextNotPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));

            btnToggleNextPaid.setBackgroundResource(R.drawable.bg_pill_dark);
            btnToggleNextPaid.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
        }
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

        // Months
        String cMonth = (originalSip.currentMonth != null && !originalSip.currentMonth.trim().isEmpty()) ? originalSip.currentMonth.trim() : "Nov";
        String nMonth = (originalSip.nextMonth != null && !originalSip.nextMonth.trim().isEmpty()) ? originalSip.nextMonth.trim() : "Dec";
        tvCurrentMonth.setText(cMonth);
        tvNextMonth.setText(nMonth);

        // Statuses
        setCurrentStatus(originalSip.currentStatus != null && !originalSip.currentStatus.trim().isEmpty() ? originalSip.currentStatus.trim() : "Not Paid");
        setNextStatus(originalSip.nextStatus != null && !originalSip.nextStatus.trim().isEmpty() ? originalSip.nextStatus.trim() : "Not Paid");

        // Platform & Bank
        tvSelectedPlatform.setText(originalSip.platform != null && !originalSip.platform.trim().isEmpty() ? originalSip.platform.trim() : "amc/upiauto");
        tvSelectedBankAccount.setText(originalSip.bankAccount != null && !originalSip.bankAccount.trim().isEmpty() ? originalSip.bankAccount.trim() : "Slice");
    }

    private void saveSip() {
        hideKeyboard();

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
        updated.currentMonth = tvCurrentMonth.getText().toString().trim();
        updated.nextMonth = tvNextMonth.getText().toString().trim();
        updated.currentStatus = currentStatus;
        updated.nextStatus = nextStatus;
        updated.platform = tvSelectedPlatform.getText().toString().trim();
        updated.bankAccount = tvSelectedBankAccount.getText().toString().trim();

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
