package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.SipItem;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.util.KeyboardHelper;
import com.samiy.financemanager.util.SipHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddSipBottomSheet extends BaseBottomSheetDialogFragment {

    public interface OnSipAddedListener {
        void onSipAdded(SipItem newSip);
    }

    private OnSipAddedListener listener;

    private AutoCompleteTextView actvFundName;
    private EditText etRole, etCurrentAmount, etNextAmount;
    private TextView tvCurrentMonth, tvNextMonth;
    private TextView btnToggleCurrentNotPaid, btnToggleCurrentPaid;
    private TextView btnToggleNextNotPaid, btnToggleNextPaid;
    private TextView tvSelectedPlatform, tvSelectedBankAccount;
    private Button btnSubmit, btnCancel;

    private String currentStatus = "Not Paid";
    private String nextStatus = "Not Paid";

    public static AddSipBottomSheet newInstance() {
        return new AddSipBottomSheet();
    }

    public void setListener(OnSipAddedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_add_sip, container, false);

        actvFundName = v.findViewById(R.id.actvAddSipFundName);
        etRole = v.findViewById(R.id.etAddSipRole);
        etCurrentAmount = v.findViewById(R.id.etAddSipCurrentAmount);
        etNextAmount = v.findViewById(R.id.etAddSipNextAmount);

        tvCurrentMonth = v.findViewById(R.id.tvAddCurrentMonth);
        tvNextMonth = v.findViewById(R.id.tvAddNextMonth);

        btnToggleCurrentNotPaid = v.findViewById(R.id.btnToggleAddCurrentNotPaid);
        btnToggleCurrentPaid = v.findViewById(R.id.btnToggleAddCurrentPaid);
        btnToggleNextNotPaid = v.findViewById(R.id.btnToggleAddNextNotPaid);
        btnToggleNextPaid = v.findViewById(R.id.btnToggleAddNextPaid);

        tvSelectedPlatform = v.findViewById(R.id.tvAddSelectedPlatform);
        tvSelectedBankAccount = v.findViewById(R.id.tvAddSelectedBankAccount);

        btnSubmit = v.findViewById(R.id.btnAddSipSubmit);
        btnCancel = v.findViewById(R.id.btnAddSipCancel);
        View btnClose = v.findViewById(R.id.btnAddSipClose);

        if (btnClose != null) btnClose.setOnClickListener(view -> dismiss());
        if (btnCancel != null) btnCancel.setOnClickListener(view -> dismiss());

        // Setup Done action across inputs
        KeyboardHelper.setupDoneAction(etCurrentAmount, etNextAmount, etRole);

        // Prepopulate current & next months
        int curMonthIndex = Calendar.getInstance().get(Calendar.MONTH);
        String defaultCurMonth = SipHelper.SHORT_MONTHS[curMonthIndex % 12];
        String defaultNextMonth = SipHelper.getNextMonthName(defaultCurMonth);
        tvCurrentMonth.setText(defaultCurMonth);
        tvNextMonth.setText(defaultNextMonth);

        // Month Picker Click Listeners
        View btnSelectCurrentMonth = v.findViewById(R.id.btnSelectAddCurrentMonth);
        if (btnSelectCurrentMonth != null) {
            btnSelectCurrentMonth.setOnClickListener(view -> {
                hideKeyboard();
                FinMngrDialog.showSingleChoice(requireContext(), "Select Current Month", SipHelper.SHORT_MONTHS, tvCurrentMonth.getText().toString(), selected -> {
                    tvCurrentMonth.setText(selected);
                    tvNextMonth.setText(SipHelper.getNextMonthName(selected));
                });
            });
        }

        View btnSelectNextMonth = v.findViewById(R.id.btnSelectAddNextMonth);
        if (btnSelectNextMonth != null) {
            btnSelectNextMonth.setOnClickListener(view -> {
                hideKeyboard();
                FinMngrDialog.showSingleChoice(requireContext(), "Select Next Month", SipHelper.SHORT_MONTHS, tvNextMonth.getText().toString(), selected -> {
                    tvNextMonth.setText(selected);
                });
            });
        }

        // Platform Picker Click Listener
        View btnSelectPlatform = v.findViewById(R.id.btnSelectAddPlatform);
        if (btnSelectPlatform != null) {
            btnSelectPlatform.setOnClickListener(view -> {
                hideKeyboard();
                List<String> platforms = new ArrayList<>(Arrays.asList(EditSipBottomSheet.DEFAULT_PLATFORMS));
                String curPlat = tvSelectedPlatform.getText().toString().trim();
                if (!curPlat.isEmpty() && !platforms.contains(curPlat)) {
                    platforms.add(curPlat);
                }
                String[] platArray = platforms.toArray(new String[0]);
                FinMngrDialog.showSingleChoice(requireContext(), "Select Platform / Mode", platArray, curPlat, selected -> {
                    tvSelectedPlatform.setText(selected);
                });
            });
        }

        // Bank Account Picker Click Listener
        View btnSelectBankAccount = v.findViewById(R.id.btnSelectAddBankAccount);
        if (btnSelectBankAccount != null) {
            btnSelectBankAccount.setOnClickListener(view -> {
                hideKeyboard();
                List<String> banks = new ArrayList<>(Arrays.asList(EditSipBottomSheet.DEFAULT_BANKS));
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
                FinMngrDialog.showSingleChoice(requireContext(), "Select Linked Bank Account", bankArray, curBank, selected -> {
                    tvSelectedBankAccount.setText(selected);
                });
            });
        }

        // Status Toggles with Confirmation Dialogs
        btnToggleCurrentNotPaid.setOnClickListener(view -> {
            hideKeyboard();
            if ("Paid".equalsIgnoreCase(currentStatus)) {
                FinMngrDialog.showConfirmation(requireContext(),
                        "Mark Current Month as Not Paid?",
                        "Are you sure you want to mark " + tvCurrentMonth.getText() + " SIP as Not Paid?",
                        "Confirm",
                        () -> setCurrentStatus("Not Paid"));
            } else {
                setCurrentStatus("Not Paid");
            }
        });

        btnToggleCurrentPaid.setOnClickListener(view -> {
            hideKeyboard();
            String cMonth = tvCurrentMonth.getText().toString().trim();
            String nMonth = tvNextMonth.getText().toString().trim();
            String afterNextMonth = SipHelper.getNextMonthName(nMonth);
            String nextAmtStr = etNextAmount.getText().toString().trim();

            FinMngrDialog.showConfirmation(requireContext(),
                    "Mark SIP as Paid?",
                    "Marking current month as Paid will advance the cycle:\n\n• Current Month becomes " + nMonth + " (Not Paid)\n• Next Month becomes " + afterNextMonth + " (Not Paid)",
                    "Confirm & Advance",
                    () -> {
                        tvCurrentMonth.setText(nMonth);
                        if (!nextAmtStr.isEmpty()) {
                            etCurrentAmount.setText(nextAmtStr);
                        }
                        setCurrentStatus("Not Paid");

                        tvNextMonth.setText(afterNextMonth);
                        setNextStatus("Not Paid");
                    });
        });

        btnToggleNextNotPaid.setOnClickListener(view -> {
            hideKeyboard();
            if ("Paid".equalsIgnoreCase(nextStatus)) {
                FinMngrDialog.showConfirmation(requireContext(),
                        "Mark Next Month as Not Paid?",
                        "Are you sure you want to mark " + tvNextMonth.getText() + " SIP as Not Paid?",
                        "Confirm",
                        () -> setNextStatus("Not Paid"));
            } else {
                setNextStatus("Not Paid");
            }
        });

        btnToggleNextPaid.setOnClickListener(view -> {
            hideKeyboard();
            FinMngrDialog.showConfirmation(requireContext(),
                    "Mark Next Month as Paid?",
                    "Are you sure you want to mark " + tvNextMonth.getText() + " SIP as Paid?",
                    "Confirm",
                    () -> setNextStatus("Paid"));
        });

        // Auto-sync next amount when typing current amount
        etCurrentAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (etNextAmount != null && !etNextAmount.isFocused()) {
                    etNextAmount.setText(s != null ? s.toString() : "");
                }
            }
        });

        // Setup Autocomplete suggestions from Mutual Fund holdings
        setupFundSuggestions(v);

        // Setup outside touch dismiss
        setupKeyboardDismissOnTouch(v);

        // Submit button
        btnSubmit.setOnClickListener(view -> submitSip());

        return v;
    }

    private void setupFundSuggestions(View v) {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null) return;

        Set<String> schemeNames = new HashSet<>();
        if (db.holdings != null) {
            for (Holding h : db.holdings) {
                if (h.schemeName != null && !h.schemeName.trim().isEmpty()) {
                    schemeNames.add(h.schemeName.trim());
                }
            }
        }

        List<String> suggestions = new ArrayList<>(schemeNames);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                R.layout.item_autocomplete_dropdown, suggestions);
        actvFundName.setAdapter(adapter);

        View btnFundNameDropdown = v != null ? v.findViewById(R.id.btnFundNameDropdown) : null;
        KeyboardHelper.setupAutoComplete(actvFundName, btnFundNameDropdown, suggestions, (parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            actvFundName.setText(selected);
            actvFundName.setSelection(selected.length());
            hideKeyboard();
        });
    }

    private void setupKeyboardDismissOnTouch(View root) {
        View[] nonEditableViews = new View[] {
                root.findViewById(R.id.scrollAddSipRoot),
                root.findViewById(R.id.layoutAddSipContainer),
                root.findViewById(R.id.cardAddSipFundDetails),
                root.findViewById(R.id.cardAddSipCurrent),
                root.findViewById(R.id.cardAddSipNext),
                root.findViewById(R.id.cardAddSipConfig)
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

    private void submitSip() {
        hideKeyboard();

        String fundName = actvFundName.getText().toString().trim();
        if (fundName.isEmpty()) {
            actvFundName.setError("Fund Name is mandatory");
            actvFundName.requestFocus();
            FinMngrToast.show(getContext(), "Please enter or select a Fund Name", Toast.LENGTH_SHORT);
            return;
        }

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db != null && db.sips != null) {
            for (SipItem s : db.sips) {
                if (s != null && s.fundName != null && s.fundName.trim().equalsIgnoreCase(fundName)) {
                    actvFundName.setError("A SIP for this fund already exists");
                    actvFundName.requestFocus();
                    FinMngrToast.show(getContext(), "A SIP for '" + fundName + "' already exists", Toast.LENGTH_SHORT);
                    return;
                }
            }
        }

        String curAmtStr = etCurrentAmount.getText().toString().trim();
        double curAmt = 0.0;
        try {
            if (!curAmtStr.isEmpty()) curAmt = Double.parseDouble(curAmtStr);
        } catch (NumberFormatException e) {
            etCurrentAmount.setError("Invalid amount");
            etCurrentAmount.requestFocus();
            return;
        }

        if (curAmt <= 0) {
            etCurrentAmount.setError("SIP Amount must be greater than 0");
            etCurrentAmount.requestFocus();
            FinMngrToast.show(getContext(), "SIP Amount must be greater than 0", Toast.LENGTH_SHORT);
            return;
        }

        String nextAmtStr = etNextAmount.getText().toString().trim();
        double nextAmt = curAmt;
        if (!nextAmtStr.isEmpty()) {
            try {
                nextAmt = Double.parseDouble(nextAmtStr);
            } catch (NumberFormatException e) {
                etNextAmount.setError("Invalid amount");
                etNextAmount.requestFocus();
                return;
            }
        }

        SipItem newSip = new SipItem();
        newSip.fundName = fundName;
        newSip.role = etRole.getText().toString().trim();
        newSip.currentSip = curAmt;
        newSip.currentMonth = tvCurrentMonth.getText().toString().trim();
        newSip.currentStatus = currentStatus;
        newSip.nextSip = nextAmt;
        newSip.nextMonth = tvNextMonth.getText().toString().trim();
        newSip.nextStatus = nextStatus;
        newSip.platform = tvSelectedPlatform.getText().toString().trim();
        newSip.bankAccount = tvSelectedBankAccount.getText().toString().trim();

        btnSubmit.setEnabled(false);
        btnSubmit.setText("⏳ Adding to Google Sheet...");
        if (btnCancel != null) btnCancel.setEnabled(false);

        FinanceRepository.getInstance(requireContext()).addSip(newSip, new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase db) {
                if (isAdded()) {
                    FinMngrToast.showSuccess(getContext(), "SIP added successfully");
                    if (listener != null) {
                        listener.onSipAdded(newSip);
                    }
                    dismiss();
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Add to Google Sheet");
                    FinMngrToast.show(getContext(), "Failed to add SIP: " + error, Toast.LENGTH_LONG);
                }
            }
        });
    }
}
