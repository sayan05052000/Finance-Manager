package com.samiy.financemanager.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Loan;
import com.samiy.financemanager.data.model.Transaction;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddTransactionBottomSheet extends BaseBottomSheetDialogFragment {
    public interface OnTransactionAddedListener {
        void onTransactionAdded();
    }

    public static class SourceItem {
        public final String name;
        public final double balance;
        public final boolean isLoan;
        public double dueAmount = 0;
        public double paymentBalance = 0;
        public boolean isCreditCard = false;

        public SourceItem(String name, double balance, boolean isLoan) {
            this.name = name;
            this.balance = balance;
            this.isLoan = isLoan;
        }

        public SourceItem(String name, double balance, boolean isLoan, double dueAmount, double paymentBalance) {
            this.name = name;
            this.balance = balance;
            this.isLoan = isLoan;
            this.dueAmount = dueAmount;
            this.paymentBalance = paymentBalance;
            this.isCreditCard = true;
        }

        public String getFormattedBalance() {
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
            if (isCreditCard) {
                String dueStr = nf.format(dueAmount).replace("INR", "₹").replace(".00", "");
                String payStr = nf.format(paymentBalance).replace("INR", "₹").replace(".00", "");
                return "Due: " + dueStr + " • Pay: " + payStr;
            }
            return nf.format(balance).replace("INR", "₹").replace(".00", "");
        }

        @Override
        public String toString() {
            return name + " (" + getFormattedBalance() + ")";
        }
    }

    public static class SourceSpinnerAdapter extends ArrayAdapter<SourceItem> {
        public SourceSpinnerAdapter(Context context, List<SourceItem> items) {
            super(context, 0, items);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_spinner_selected, parent, false);
            }
            TextView tv = convertView.findViewById(android.R.id.text1);
            SourceItem item = getItem(position);
            if (item != null && tv != null) {
                tv.setText(item.name + "  •  " + item.getFormattedBalance());
            }
            return convertView;
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_spinner_account_dropdown, parent, false);
            }
            TextView tvName = convertView.findViewById(R.id.tvSourceName);
            TextView tvBalance = convertView.findViewById(R.id.tvSourceBalance);
            SourceItem item = getItem(position);
            if (item != null) {
                if (tvName != null) tvName.setText(item.name);
                if (tvBalance != null) {
                    tvBalance.setText(item.getFormattedBalance());
                    if (item.isCreditCard) {
                        tvBalance.setTextColor(ContextCompat.getColor(getContext(), R.color.amber_gold));
                    } else if (item.isLoan) {
                        tvBalance.setTextColor(ContextCompat.getColor(getContext(), item.balance > 0 ? R.color.red_rose : R.color.text_secondary));
                    } else {
                        tvBalance.setTextColor(ContextCompat.getColor(getContext(), item.balance < 0 ? R.color.red_rose : (item.balance == 0 ? R.color.text_secondary : R.color.green_mint)));
                    }
                }
            }
            return convertView;
        }
    }

    public static final String ARG_LOCKED_TYPE = "arg_locked_type";
    public static final String ARG_TARGET_NAME = "arg_target_name";

    public static AddTransactionBottomSheet newInstanceForAccount(String accountName) {
        AddTransactionBottomSheet sheet = new AddTransactionBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_LOCKED_TYPE, "account");
        b.putString(ARG_TARGET_NAME, accountName);
        sheet.setArguments(b);
        return sheet;
    }

    public static AddTransactionBottomSheet newInstanceForLoan(String loanName) {
        AddTransactionBottomSheet sheet = new AddTransactionBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_LOCKED_TYPE, "loan");
        b.putString(ARG_TARGET_NAME, loanName);
        sheet.setArguments(b);
        return sheet;
    }

    public static AddTransactionBottomSheet newInstanceForCreditCard(String cardName) {
        AddTransactionBottomSheet sheet = new AddTransactionBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_LOCKED_TYPE, "credit_card");
        b.putString(ARG_TARGET_NAME, cardName);
        sheet.setArguments(b);
        return sheet;
    }

    private OnTransactionAddedListener listener;

    public void setListener(OnTransactionAddedListener listener) {
        this.listener = listener;
    }

    public void setOnTransactionAddedListener(OnTransactionAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!com.samiy.financemanager.auth.GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            com.samiy.financemanager.util.FinMngrToast.show(requireContext(), "You are not authorized", android.widget.Toast.LENGTH_SHORT);
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_add_transaction, container, false);

        RadioGroup rgType = v.findViewById(R.id.rgType);
        RadioButton rbExpense = v.findViewById(R.id.rbExpense);
        RadioButton rbIncome = v.findViewById(R.id.rbIncome);
        RadioButton rbDoNotConsider = v.findViewById(R.id.rbDoNotConsider);

        LinearLayout layoutDebitField = v.findViewById(R.id.layoutDebitField);
        LinearLayout layoutCreditField = v.findViewById(R.id.layoutCreditField);
        EditText etDebit = v.findViewById(R.id.etDebit);
        EditText etCredit = v.findViewById(R.id.etCredit);

        AutoCompleteTextView actvSubCategory = v.findViewById(R.id.actvSubCategory);
        View btnSearchSubCategory = v.findViewById(R.id.btnSearchSubCategory);
        View btnSubCategoryDropdown = v.findViewById(R.id.btnSubCategoryDropdown);

        RadioGroup rgTagType = v.findViewById(R.id.rgTagType);
        RadioButton rbTagLoan = v.findViewById(R.id.rbTagLoan);
        RadioButton rbTagAccount = v.findViewById(R.id.rbTagAccount);
        Spinner spSourceSelector = v.findViewById(R.id.spSourceSelector);
        LinearLayout layoutCreditCardTagSection = v.findViewById(R.id.layoutCreditCardTagSection);
        Spinner spCreditCardSelector = v.findViewById(R.id.spCreditCardSelector);

        EditText etDate = v.findViewById(R.id.etDate);
        Button btnSubmit = v.findViewById(R.id.btnSubmitTransaction);

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        // Helper to close keyboard
        Runnable hideKeyboard = () -> {
            View currentFocus = getDialog() != null ? getDialog().getCurrentFocus() : null;
            if (currentFocus != null && imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                currentFocus.clearFocus();
            }
        };

        EditText[] allInputs = {etDebit, etCredit, etDate, actvSubCategory};
        for (EditText et : allInputs) {
            et.setOnClickListener(view -> {
                if (imm != null) {
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            et.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus && imm != null) {
                    view.post(() -> imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT));
                }
            });
        }

        com.samiy.financemanager.util.KeyboardHelper.setupDoneAction(allInputs);
        // Recursive keyboard auto-dismissal when touching anywhere outside inputs
        setupTouchToDismissKeyboard(v, imm, allInputs);

        // Pre-fill today's date & wire calendar picker
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        etDate.setText(sdf.format(new Date()));
        com.samiy.financemanager.util.DateTimeHelper.setupDatePicker(etDate);

        // Populate Sub-Categories Dropdown and Search
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        List<String> subCatList = db != null ? db.getAllSubCategories() : new ArrayList<>();
        if (db != null && db.transactions != null) {
            for (Transaction t : db.transactions) {
                if (t.subCategory != null && !t.subCategory.trim().isEmpty() && !subCatList.contains(t.subCategory.trim())) {
                    subCatList.add(t.subCategory.trim());
                }
            }
        }
        if (subCatList.isEmpty()) {
            subCatList.add("Mutual Fund");
            subCatList.add("Credit Mutual Fund");
            subCatList.add("Food");
            subCatList.add("Electricity");
            subCatList.add("Shopping");
        }
        java.util.Collections.sort(subCatList);

        ArrayAdapter<String> subCatAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_autocomplete_dropdown, subCatList);
        actvSubCategory.setAdapter(subCatAdapter);
        if (!subCatList.isEmpty()) {
            actvSubCategory.setText(subCatList.get(0));
        }

        // Configure AutoCompleteTextView: close keyboard on selecting an option, handle down arrow click, and wrap content height
        com.samiy.financemanager.util.KeyboardHelper.setupAutoComplete(
                actvSubCategory, btnSubCategoryDropdown, subCatList, (parent, view, position, id) -> {
            actvSubCategory.setError(null);
        });

        // Real-time TextWatcher on Sub-Category: alert user if query does not match any available option
        actvSubCategory.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = (s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "");
                if (q.isEmpty()) {
                    actvSubCategory.setError(null);
                    return;
                }
                boolean hasMatch = false;
                for (String cat : subCatList) {
                    if (cat.toLowerCase(Locale.ROOT).contains(q)) {
                        hasMatch = true;
                        break;
                    }
                }
                if (!hasMatch) {
                    actvSubCategory.setError("No matching option found");
                } else {
                    actvSubCategory.setError(null);
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Searchable Sub-Category Modal Picker
        if (btnSearchSubCategory != null) {
            btnSearchSubCategory.setOnClickListener(vSearch -> {
                hideKeyboard.run();
                View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_subcategory, null);
                EditText etSearch = dialogView.findViewById(R.id.etSearchSubCategoryQuery);
                View btnClearSearch = dialogView.findViewById(R.id.btnClearSearchSubCategory);
                LinearLayout layoutItems = dialogView.findViewById(R.id.layoutSubCategoryItems);
                View scrollSubCategories = dialogView.findViewById(R.id.scrollSubCategories);

                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                com.samiy.financemanager.util.KeyboardHelper.setupOutsideTouchDismiss(dialog);
                com.samiy.financemanager.util.KeyboardHelper.setupAutoDismissKeyboard(dialogView);
                com.samiy.financemanager.util.KeyboardHelper.setupScrollToDismissKeyboard(dialogView);
                com.samiy.financemanager.util.KeyboardHelper.setupDoneAction(etSearch);

                if (btnClearSearch != null) {
                    btnClearSearch.setOnClickListener(vClear -> {
                        etSearch.setText("");
                        com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(etSearch);
                    });
                }

                Button btnCancel = dialogView.findViewById(R.id.btnSubCategoryDialogCancel);
                if (btnCancel != null) {
                    btnCancel.setOnClickListener(vCancel -> {
                        com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(etSearch);
                        dialog.dismiss();
                    });
                }

                java.util.function.Consumer<String> renderList = (query) -> {
                    layoutItems.removeAllViews();
                    String q = (query != null ? query.trim().toLowerCase(Locale.ROOT) : "");
                    int matchedCount = 0;
                    for (String cat : subCatList) {
                        if (q.isEmpty() || cat.toLowerCase(Locale.ROOT).contains(q)) {
                            matchedCount++;
                            View itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_folio_suggestion, layoutItems, false);
                            TextView tvVal = itemView.findViewById(R.id.tvFolioValue);
                            if (tvVal != null) tvVal.setText(cat);
                            itemView.setOnClickListener(vItem -> {
                                actvSubCategory.setText(cat);
                                actvSubCategory.setError(null);
                                com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(etSearch);
                                com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(actvSubCategory);
                                actvSubCategory.clearFocus();
                                dialog.dismiss();
                            });
                            layoutItems.addView(itemView);
                        }
                    }
                    if (matchedCount == 0) {
                        TextView tvEmpty = new TextView(getContext());
                        tvEmpty.setText("⚠️ No matching sub-categories found. Please choose an existing option.");
                        tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
                        tvEmpty.setTextSize(12);
                        tvEmpty.setPadding(0, 24, 0, 24);
                        layoutItems.addView(tvEmpty);
                    }
                    if (scrollSubCategories != null) {
                        scrollSubCategories.post(() -> {
                            int maxAllowed = (int) (requireContext().getResources().getDisplayMetrics().heightPixels * 0.45);
                            android.view.ViewGroup.LayoutParams lp = scrollSubCategories.getLayoutParams();
                            if (layoutItems.getHeight() > maxAllowed) {
                                lp.height = maxAllowed;
                            } else {
                                lp.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                            }
                            scrollSubCategories.setLayoutParams(lp);
                        });
                    }
                };

                renderList.accept("");

                etSearch.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (btnClearSearch != null) {
                            btnClearSearch.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                        }
                        renderList.accept(s != null ? s.toString() : "");
                    }
                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });

                dialog.show();
            });
        }

        // Sources lists for Loan vs Account with real balances
        double ccDue = db != null ? db.getCreditCardDueAmount() : 13801.47;
        double ccPay = db != null ? db.getCreditCardPaymentBalance() : 13801.47;

        List<SourceItem> loanList = new ArrayList<>();
        List<SourceItem> accountList = new ArrayList<>();
        if (db != null) {
            if (db.loans != null) {
                for (Loan l : db.loans) {
                    if (l != null && l.name != null && !l.name.isEmpty()) {
                        if ("CREDIT CARD".equalsIgnoreCase(l.name.trim())) {
                            loanList.add(new SourceItem(l.name, l.outstanding, true, ccDue, ccPay));
                        } else {
                            loanList.add(new SourceItem(l.name, l.outstanding, true));
                        }
                    }
                }
            }
            if (db.accounts != null) {
                for (Account a : db.accounts) {
                    if (a != null && a.name != null && !a.name.isEmpty()) {
                        accountList.add(new SourceItem(a.name, a.currentBalance, false));
                    }
                }
            }
        }
        if (loanList.isEmpty()) {
            loanList.add(new SourceItem("SLICE CREDIT", 0, true));
            loanList.add(new SourceItem("SBI CREDIT", 0, true));
            loanList.add(new SourceItem("CREDIT CARD", 0, true, 13801.47, 13801.47));
        }
        if (accountList.isEmpty()) {
            accountList.add(new SourceItem("PNB", 0, false));
            accountList.add(new SourceItem("HDFC SAVINGS", 0, false));
            accountList.add(new SourceItem("Slice", 0, false));
        }

        // Populate Credit Card Options
        List<String> creditCardNames = new ArrayList<>();
        if (db != null && db.creditCards != null) {
            for (com.samiy.financemanager.data.model.CreditCard cc : db.creditCards) {
                if (cc != null && cc.cardName != null && !cc.cardName.trim().isEmpty()) {
                    creditCardNames.add(cc.cardName.trim());
                }
            }
        }
        if (creditCardNames.isEmpty()) {
            creditCardNames.add("Primary Card");
        }
        ArrayAdapter<String> ccAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, creditCardNames);
        ccAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        if (spCreditCardSelector != null) {
            spCreditCardSelector.setAdapter(ccAdapter);
        }

        View btnAddCreditCardInline = v.findViewById(R.id.btnAddCreditCardInline);
        if (btnAddCreditCardInline != null) {
            btnAddCreditCardInline.setOnClickListener(view -> {
                hideKeyboard.run();
                com.samiy.financemanager.ui.dialog.AddCreditCardBottomSheet addCardSheet =
                        com.samiy.financemanager.ui.dialog.AddCreditCardBottomSheet.newInstance();
                addCardSheet.setOnCreditCardAddedListener(newCard -> {
                    if (newCard != null && newCard.cardName != null) {
                        String newName = newCard.cardName.trim();
                        if (!creditCardNames.contains(newName)) {
                            creditCardNames.add(newName);
                            ccAdapter.notifyDataSetChanged();
                        }
                        int pos = creditCardNames.indexOf(newName);
                        if (pos >= 0 && spCreditCardSelector != null) {
                            spCreditCardSelector.setSelection(pos);
                        }
                    }
                });
                addCardSheet.show(getChildFragmentManager(), "add_card_inline");
            });
        }

        Runnable updateCreditCardVisibility = () -> {
            if (layoutCreditCardTagSection == null) return;
            boolean isLoan = rbTagLoan.isChecked();
            Object selectedObj = spSourceSelector.getSelectedItem();
            String name = "";
            if (selectedObj instanceof SourceItem) {
                name = ((SourceItem) selectedObj).name;
            } else if (selectedObj != null) {
                name = selectedObj.toString();
            }
            if (isLoan && "CREDIT CARD".equalsIgnoreCase(name.trim())) {
                layoutCreditCardTagSection.setVisibility(View.VISIBLE);
            } else {
                layoutCreditCardTagSection.setVisibility(View.GONE);
            }
        };

        // Toggle tag sources
        Runnable updateSourceSpinner = () -> {
            boolean isLoan = rbTagLoan.isChecked();
            List<SourceItem> currentList = isLoan ? loanList : accountList;
            SourceSpinnerAdapter sourceAdapter = new SourceSpinnerAdapter(requireContext(), currentList);
            spSourceSelector.setAdapter(sourceAdapter);
            updateCreditCardVisibility.run();
        };
        updateSourceSpinner.run();

        TextView tvSourceLockHint = v.findViewById(R.id.tvSourceLockHint);
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_LOCKED_TYPE)) {
            String lockedType = args.getString(ARG_LOCKED_TYPE, "");
            String targetName = args.getString(ARG_TARGET_NAME, "");

            if ("account".equalsIgnoreCase(lockedType)) {
                rbTagAccount.setChecked(true);
                rbTagAccount.setEnabled(false);
                rbTagLoan.setEnabled(false);
                rgTagType.setEnabled(false);
                updateSourceSpinner.run();

                for (int i = 0; i < accountList.size(); i++) {
                    if (accountList.get(i).name.equalsIgnoreCase(targetName)) {
                        spSourceSelector.setSelection(i);
                        break;
                    }
                }
                spSourceSelector.setEnabled(false);
                spSourceSelector.setOnTouchListener((view, event) -> true);

                if (tvSourceLockHint != null) {
                    tvSourceLockHint.setVisibility(View.VISIBLE);
                    tvSourceLockHint.setText("🔒 Auto-selected Account: " + targetName + " (Cannot be changed)");
                }
            } else if ("loan".equalsIgnoreCase(lockedType)) {
                rbTagLoan.setChecked(true);
                rbTagAccount.setEnabled(false);
                rbTagLoan.setEnabled(false);
                rgTagType.setEnabled(false);
                updateSourceSpinner.run();

                for (int i = 0; i < loanList.size(); i++) {
                    if (loanList.get(i).name.equalsIgnoreCase(targetName)) {
                        spSourceSelector.setSelection(i);
                        break;
                    }
                }
                spSourceSelector.setEnabled(false);
                spSourceSelector.setOnTouchListener((view, event) -> true);

                if (tvSourceLockHint != null) {
                    tvSourceLockHint.setVisibility(View.VISIBLE);
                    tvSourceLockHint.setText("🔒 Auto-selected Loan: " + targetName + " (Cannot be changed)");
                }
            } else if ("credit_card".equalsIgnoreCase(lockedType)) {
                rbTagLoan.setChecked(true);
                rbTagAccount.setEnabled(false);
                rbTagLoan.setEnabled(false);
                rgTagType.setEnabled(false);
                updateSourceSpinner.run();

                for (int i = 0; i < loanList.size(); i++) {
                    if ("CREDIT CARD".equalsIgnoreCase(loanList.get(i).name.trim())) {
                        spSourceSelector.setSelection(i);
                        break;
                    }
                }
                spSourceSelector.setEnabled(false);
                spSourceSelector.setOnTouchListener((view, event) -> true);

                if (layoutCreditCardTagSection != null) {
                    layoutCreditCardTagSection.setVisibility(View.VISIBLE);
                }
                if (spCreditCardSelector != null) {
                    for (int i = 0; i < creditCardNames.size(); i++) {
                        if (creditCardNames.get(i).equalsIgnoreCase(targetName)) {
                            spCreditCardSelector.setSelection(i);
                            break;
                        }
                    }
                    spCreditCardSelector.setEnabled(false);
                    spCreditCardSelector.setOnTouchListener((view, event) -> true);
                }
                if (btnAddCreditCardInline != null) {
                    btnAddCreditCardInline.setVisibility(View.GONE);
                }

                if (tvSourceLockHint != null) {
                    tvSourceLockHint.setVisibility(View.VISIBLE);
                    tvSourceLockHint.setText("🔒 Auto-selected Loan: CREDIT CARD • Card: " + targetName + " (Cannot be changed)");
                }
            }
        }

        spSourceSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(parent);
                updateCreditCardVisibility.run();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                updateCreditCardVisibility.run();
            }
        });

        if (spCreditCardSelector != null) {
            spCreditCardSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    com.samiy.financemanager.util.KeyboardHelper.hideKeyboard(parent);
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        rgTagType.setOnCheckedChangeListener((group, checkedId) -> {
            hideKeyboard.run();
            updateSourceSpinner.run();
        });

        // Toggle visibility based on Type:
        // Expense: show ONLY debit
        // Income: show ONLY credit
        // Do not Consider: show BOTH debit and credit
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            hideKeyboard.run();
            if (checkedId == R.id.rbExpense) {
                layoutDebitField.setVisibility(View.VISIBLE);
                layoutCreditField.setVisibility(View.GONE);
                etDebit.requestFocus();
            } else if (checkedId == R.id.rbIncome) {
                layoutDebitField.setVisibility(View.GONE);
                layoutCreditField.setVisibility(View.VISIBLE);
                etCredit.requestFocus();
            } else if (checkedId == R.id.rbDoNotConsider) {
                layoutDebitField.setVisibility(View.VISIBLE);
                layoutCreditField.setVisibility(View.VISIBLE);
            }
        });

        btnSubmit.setOnClickListener(view -> {
            hideKeyboard.run();

            String selectedType = "Expense";
            if (rbIncome.isChecked()) selectedType = "Income";
            else if (rbDoNotConsider.isChecked()) selectedType = "Do not Consider";

            double debitVal = 0.0;
            double creditVal = 0.0;

            String debitStr = etDebit.getText().toString().trim();
            String creditStr = etCredit.getText().toString().trim();

            if ("Expense".equals(selectedType)) {
                if (debitStr.isEmpty()) {
                    FinMngrToast.show(getContext(), "Please enter debit amount", Toast.LENGTH_SHORT);
                    return;
                }
                try {
                    debitVal = Double.parseDouble(debitStr);
                } catch (Exception e) {
                    FinMngrToast.show(getContext(), "Invalid debit amount", Toast.LENGTH_SHORT);
                    return;
                }
            } else if ("Income".equals(selectedType)) {
                if (creditStr.isEmpty()) {
                    FinMngrToast.show(getContext(), "Please enter credit amount", Toast.LENGTH_SHORT);
                    return;
                }
                try {
                    creditVal = Double.parseDouble(creditStr);
                } catch (Exception e) {
                    FinMngrToast.show(getContext(), "Invalid credit amount", Toast.LENGTH_SHORT);
                    return;
                }
            } else { // Do not Consider
                if (debitStr.isEmpty() && creditStr.isEmpty()) {
                    FinMngrToast.show(getContext(), "Please enter at least debit or credit amount", Toast.LENGTH_SHORT);
                    return;
                }
                if (!debitStr.isEmpty()) {
                    try { debitVal = Double.parseDouble(debitStr); } catch (Exception ignored) {}
                }
                if (!creditStr.isEmpty()) {
                    try { creditVal = Double.parseDouble(creditStr); } catch (Exception ignored) {}
                }
            }

            String rawSubCategory = actvSubCategory.getText().toString().trim();
            if (rawSubCategory.isEmpty()) {
                actvSubCategory.setError("Sub-Category is mandatory");
                actvSubCategory.requestFocus();
                FinMngrToast.showError(getContext(), "Sub-Category is mandatory. Please select an option from the list.");
                return;
            }

            // Strict mandatory select validation: verify typed/selected value exists in available options
            String matchedSubCategory = null;
            for (String cat : subCatList) {
                if (cat.trim().equalsIgnoreCase(rawSubCategory)) {
                    matchedSubCategory = cat.trim();
                    break;
                }
            }

            if (matchedSubCategory == null) {
                actvSubCategory.setError("No matching option found. Please select from the list.");
                actvSubCategory.requestFocus();
                FinMngrToast.showError(getContext(), "No matching select option for '" + rawSubCategory + "'. You must select a valid option to post.");
                return; // DO NOT LET USER POST!
            }

            String subCategory = matchedSubCategory;

            String date = etDate.getText().toString().trim();

            Object selectedObj = spSourceSelector.getSelectedItem();
            String selectedSource = "";
            if (selectedObj instanceof SourceItem) {
                selectedSource = ((SourceItem) selectedObj).name;
            } else if (selectedObj != null) {
                selectedSource = selectedObj.toString();
            }

            if (selectedSource == null || selectedSource.trim().isEmpty() || "Select Source".equalsIgnoreCase(selectedSource.trim())) {
                FinMngrToast.showError(getContext(), "Please select an Account or Loan source.");
                return;
            }

            boolean isLoan = rbTagLoan.isChecked();
            if (isLoan && "CREDIT CARD".equalsIgnoreCase(selectedSource.trim())) {
                String card = (spCreditCardSelector != null && spCreditCardSelector.getSelectedItem() != null)
                        ? spCreditCardSelector.getSelectedItem().toString().trim() : "";
                if (card.isEmpty() || "No cards available".equalsIgnoreCase(card)) {
                    FinMngrToast.showError(getContext(), "Please select a Credit Card.");
                    return;
                }
            }

            Transaction tx = new Transaction();
            tx.date = date;
            tx.type = selectedType;
            tx.subCategory = subCategory;
            tx.category = ""; // Category will be automatically decided inside google sheet

            // User rule: debit value always goes into Debit with out Sign with exact value provided by user
            tx.debitWithoutSign = debitVal;
            tx.credit = creditVal;

            // DEBIT WITH SIGN:
            // Non-zero exact debit with sign (negative for expenses/debits, so Debit column is NEVER 0)
            tx.debit = -tx.debitWithoutSign;

            tx.net = (creditVal > 0 ? creditVal : 0) + (tx.debit != 0 ? tx.debit : 0);
            tx.remainder = 0;

            if (isLoan) {
                tx.loans = selectedSource;
                tx.account = "";
                if ("CREDIT CARD".equalsIgnoreCase(selectedSource.trim()) &&
                        layoutCreditCardTagSection != null &&
                        layoutCreditCardTagSection.getVisibility() == View.VISIBLE &&
                        spCreditCardSelector != null &&
                        spCreditCardSelector.getSelectedItem() != null) {
                    tx.taggedCreditCard = spCreditCardSelector.getSelectedItem().toString().trim();
                }
            } else {
                tx.account = selectedSource;
                tx.loans = "";
            }

            String[] parts = date.split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim());
                    int y = Integer.parseInt(parts[2].trim());
                    if (d < 6) {
                        if (m == 1) {
                            m = 12;
                            y = y - 1;
                        } else {
                            m = m - 1;
                        }
                    }
                    tx.month = m;
                    tx.year = y;
                } catch (Exception ignored) {}
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Syncing to Google Sheet...");

            FinanceRepository.getInstance(requireContext()).addTransaction(tx, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), "Transaction added to Google Sheet!", Toast.LENGTH_LONG);
                        if (listener != null) listener.onTransactionAdded();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), error, Toast.LENGTH_LONG);
                        if (listener != null) listener.onTransactionAdded();
                        dismiss();
                    }
                }
            });
        });

        return v;
    }

    private void setupTouchToDismissKeyboard(View view, InputMethodManager imm, EditText... inputs) {
        if (view == null) return;
        boolean isInput = false;
        for (EditText in : inputs) {
            if (view == in) {
                isInput = true;
                break;
            }
        }
        if (!isInput && !(view instanceof EditText)) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    View currentFocus = getDialog() != null ? getDialog().getCurrentFocus() : null;
                    if (currentFocus != null && imm != null) {
                        imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                        currentFocus.clearFocus();
                    }
                }
                return false;
            });
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setupTouchToDismissKeyboard(vg.getChildAt(i), imm, inputs);
            }
        }
    }
}
