package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.Account;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Loan;
import com.samiy.financemanager.data.model.Transaction;
import com.samiy.financemanager.util.DateTimeHelper;
import com.samiy.financemanager.util.KeyboardHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class EditTransactionBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TRANSACTION = "transaction";
    private Transaction transaction;

    public interface OnTransactionUpdatedListener {
        void onTransactionUpdated();
    }

    private OnTransactionUpdatedListener listener;

    public static EditTransactionBottomSheet newInstance(Transaction transaction) {
        EditTransactionBottomSheet fragment = new EditTransactionBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TRANSACTION, transaction);
        fragment.setArguments(args);
        return fragment;
    }

    public void setListener(OnTransactionUpdatedListener listener) {
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
            Toast.makeText(requireContext(), "You are not authorized", Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_edit_transaction, container, false);

        if (getArguments() != null) {
            transaction = (Transaction) getArguments().getSerializable(ARG_TRANSACTION);
        }

        if (transaction == null) {
            dismiss();
            return null;
        }

        TextView tvSubtitle = v.findViewById(R.id.tvEditTxSubtitle);
        if (tvSubtitle != null) {
            tvSubtitle.setText("Updates row #" + transaction.id + " in your Google Sheet Transactions tab");
        }

        RadioGroup rgType = v.findViewById(R.id.rgEditType);
        RadioButton rbExpense = v.findViewById(R.id.rbEditExpense);
        RadioButton rbIncome = v.findViewById(R.id.rbEditIncome);
        RadioButton rbDoNotConsider = v.findViewById(R.id.rbEditDoNotConsider);

        LinearLayout layoutDebitField = v.findViewById(R.id.layoutEditDebitField);
        LinearLayout layoutCreditField = v.findViewById(R.id.layoutEditCreditField);
        EditText etDebit = v.findViewById(R.id.etEditDebit);
        EditText etCredit = v.findViewById(R.id.etEditCredit);

        AutoCompleteTextView actvSubCategory = v.findViewById(R.id.actvEditSubCategory);
        View btnSearchSubCategory = v.findViewById(R.id.btnEditSearchSubCategory);
        View btnSubCategoryDropdown = v.findViewById(R.id.btnEditSubCategoryDropdown);

        RadioGroup rgTagType = v.findViewById(R.id.rgEditTagType);
        RadioButton rbTagLoan = v.findViewById(R.id.rbEditTagLoan);
        RadioButton rbTagAccount = v.findViewById(R.id.rbEditTagAccount);
        Spinner spSourceSelector = v.findViewById(R.id.spEditSourceSelector);

        EditText etDate = v.findViewById(R.id.etEditDate);
        Button btnUpdate = v.findViewById(R.id.btnUpdateTransaction);
        Button btnDelete = v.findViewById(R.id.btnDeleteTransaction);

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        Runnable hideKeyboard = () -> {
            View currentFocus = getDialog() != null ? getDialog().getCurrentFocus() : null;
            if (currentFocus != null && imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                currentFocus.clearFocus();
            }
        };

        if (btnDelete != null) {
            btnDelete.setOnClickListener(view -> {
                hideKeyboard.run();
                new AlertDialog.Builder(requireContext(), R.style.CustomDarkDialog)
                        .setTitle("Delete Transaction")
                        .setMessage("Are you sure you want to permanently delete this transaction from your Google Sheet?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            btnDelete.setEnabled(false);
                            btnDelete.setText("Deleting...");
                            btnUpdate.setEnabled(false);

                            FinanceRepository.getInstance(requireContext()).deleteTransaction(transaction.id, new FinanceRepository.DataCallback() {
                                @Override
                                public void onLoaded(FinanceDatabase updatedDb) {
                                    if (isAdded()) {
                                        Toast.makeText(getContext(), "Transaction deleted successfully!", Toast.LENGTH_SHORT).show();
                                        if (listener != null) listener.onTransactionUpdated();
                                        dismiss();
                                    }
                                }

                                @Override
                                public void onError(String error) {
                                    if (isAdded()) {
                                        btnDelete.setEnabled(true);
                                        btnDelete.setText("Delete");
                                        btnUpdate.setEnabled(true);
                                        Toast.makeText(getContext(), "Failed to delete: " + error, Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        EditText[] allInputs = {etDebit, etCredit, etDate, actvSubCategory};
        KeyboardHelper.setupDoneAction(allInputs);

        // Pre-fill Date (cannot select future dates)
        etDate.setText(DateTimeHelper.formatDisplayDate(transaction.date));
        DateTimeHelper.setupPastDatePicker(etDate);



        // Pre-fill Type
        String currentType = transaction.type != null ? transaction.type.trim() : "Expense";
        if ("Income".equalsIgnoreCase(currentType)) {
            rbIncome.setChecked(true);
            layoutDebitField.setVisibility(View.GONE);
            layoutCreditField.setVisibility(View.VISIBLE);
        } else if ("Do not Consider".equalsIgnoreCase(currentType)) {
            rbDoNotConsider.setChecked(true);
            layoutDebitField.setVisibility(View.VISIBLE);
            layoutCreditField.setVisibility(View.VISIBLE);
        } else {
            rbExpense.setChecked(true);
            layoutDebitField.setVisibility(View.VISIBLE);
            layoutCreditField.setVisibility(View.GONE);
        }

        // Pre-fill Debit & Credit
        double dVal = transaction.debitWithoutSign > 0 ? transaction.debitWithoutSign : Math.abs(transaction.debit);
        if (dVal > 0) {
            String dStr = String.format(Locale.US, "%.2f", dVal);
            if (dStr.endsWith(".00")) dStr = dStr.substring(0, dStr.length() - 3);
            etDebit.setText(dStr);
        }
        if (transaction.credit > 0) {
            String cStr = String.format(Locale.US, "%.2f", transaction.credit);
            if (cStr.endsWith(".00")) cStr = cStr.substring(0, cStr.length() - 3);
            etCredit.setText(cStr);
        }

        // Subcategory list & Autocomplete
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
        Collections.sort(subCatList);

        ArrayAdapter<String> subCatAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown, subCatList);
        actvSubCategory.setAdapter(subCatAdapter);
        actvSubCategory.setText(transaction.subCategory != null ? transaction.subCategory : "");

        actvSubCategory.setOnClickListener(view -> actvSubCategory.showDropDown());
        if (btnSubCategoryDropdown != null) {
            btnSubCategoryDropdown.setOnClickListener(view -> actvSubCategory.showDropDown());
        }

        // Sub-Category Search Modal
        if (btnSearchSubCategory != null) {
            btnSearchSubCategory.setOnClickListener(vSearch -> {
                hideKeyboard.run();
                View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_subcategory, null);
                EditText etSearch = dialogView.findViewById(R.id.etSearchSubCategoryQuery);
                LinearLayout layoutItems = dialogView.findViewById(R.id.layoutSubCategoryItems);

                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                Button btnCancel = dialogView.findViewById(R.id.btnSubCategoryDialogCancel);
                if (btnCancel != null) btnCancel.setOnClickListener(vCancel -> dialog.dismiss());

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
                                dialog.dismiss();
                            });
                            layoutItems.addView(itemView);
                        }
                    }
                    if (matchedCount == 0) {
                        TextView tvEmpty = new TextView(getContext());
                        tvEmpty.setText("No matching sub-categories found.");
                        tvEmpty.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
                        tvEmpty.setTextSize(12);
                        tvEmpty.setPadding(0, 24, 0, 24);
                        layoutItems.addView(tvEmpty);
                    }
                };

                renderList.accept("");

                etSearch.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        renderList.accept(s != null ? s.toString() : "");
                    }
                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });

                dialog.show();
            });
        }

        // Sources lists for Loan vs Account
        double ccDue = db != null ? db.getCreditCardDueAmount() : 13801.47;
        double ccPay = db != null ? db.getCreditCardPaymentBalance() : 13801.47;

        List<AddTransactionBottomSheet.SourceItem> loanList = new ArrayList<>();
        List<AddTransactionBottomSheet.SourceItem> accountList = new ArrayList<>();
        if (db != null) {
            if (db.loans != null) {
                for (Loan l : db.loans) {
                    if (l != null && l.name != null && !l.name.isEmpty()) {
                        if ("CREDIT CARD".equalsIgnoreCase(l.name.trim())) {
                            loanList.add(new AddTransactionBottomSheet.SourceItem(l.name, l.outstanding, true, ccDue, ccPay));
                        } else {
                            loanList.add(new AddTransactionBottomSheet.SourceItem(l.name, l.outstanding, true));
                        }
                    }
                }
            }
            if (db.accounts != null) {
                for (Account a : db.accounts) {
                    if (a != null && a.name != null && !a.name.isEmpty()) {
                        accountList.add(new AddTransactionBottomSheet.SourceItem(a.name, a.currentBalance, false));
                    }
                }
            }
        }
        if (loanList.isEmpty()) {
            loanList.add(new AddTransactionBottomSheet.SourceItem("SLICE CREDIT", 0, true));
            loanList.add(new AddTransactionBottomSheet.SourceItem("SBI CREDIT", 0, true));
            loanList.add(new AddTransactionBottomSheet.SourceItem("CREDIT CARD", 0, true, 13801.47, 13801.47));
        }
        if (accountList.isEmpty()) {
            accountList.add(new AddTransactionBottomSheet.SourceItem("PNB", 0, false));
            accountList.add(new AddTransactionBottomSheet.SourceItem("HDFC SAVINGS", 0, false));
            accountList.add(new AddTransactionBottomSheet.SourceItem("Slice", 0, false));
        }

        boolean currentIsLoan = (transaction.loans != null && !transaction.loans.trim().isEmpty());
        if (currentIsLoan) {
            rbTagLoan.setChecked(true);
        } else {
            rbTagAccount.setChecked(true);
        }

        final String activeSourceName = currentIsLoan ? transaction.loans.trim() : (transaction.account != null ? transaction.account.trim() : "");

        Runnable updateSourceSpinner = () -> {
            boolean isLoan = rbTagLoan.isChecked();
            List<AddTransactionBottomSheet.SourceItem> currentList = isLoan ? loanList : accountList;
            AddTransactionBottomSheet.SourceSpinnerAdapter sourceAdapter = new AddTransactionBottomSheet.SourceSpinnerAdapter(requireContext(), currentList);
            spSourceSelector.setAdapter(sourceAdapter);

            int selectedPos = 0;
            for (int i = 0; i < currentList.size(); i++) {
                if (currentList.get(i).name.equalsIgnoreCase(activeSourceName)) {
                    selectedPos = i;
                    break;
                }
            }
            spSourceSelector.setSelection(selectedPos);
        };
        updateSourceSpinner.run();

        rgTagType.setOnCheckedChangeListener((group, checkedId) -> {
            hideKeyboard.run();
            updateSourceSpinner.run();
        });

        // Type visibility toggle
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            hideKeyboard.run();
            if (checkedId == R.id.rbEditExpense) {
                layoutDebitField.setVisibility(View.VISIBLE);
                layoutCreditField.setVisibility(View.GONE);
            } else if (checkedId == R.id.rbEditIncome) {
                layoutDebitField.setVisibility(View.GONE);
                layoutCreditField.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rbEditDoNotConsider) {
                layoutDebitField.setVisibility(View.VISIBLE);
                layoutCreditField.setVisibility(View.VISIBLE);
            }
        });

        btnUpdate.setOnClickListener(view -> {
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
                    Toast.makeText(getContext(), "Please enter debit amount", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    debitVal = Double.parseDouble(debitStr);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Invalid debit amount", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else if ("Income".equals(selectedType)) {
                if (creditStr.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter credit amount", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    creditVal = Double.parseDouble(creditStr);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Invalid credit amount", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else { // Do not Consider
                if (debitStr.isEmpty() && creditStr.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter at least debit or credit amount", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!debitStr.isEmpty()) {
                    try { debitVal = Double.parseDouble(debitStr); } catch (Exception ignored) {}
                }
                if (!creditStr.isEmpty()) {
                    try { creditVal = Double.parseDouble(creditStr); } catch (Exception ignored) {}
                }
            }

            String subCategory = actvSubCategory.getText().toString().trim();
            if (subCategory.isEmpty()) {
                subCategory = "General";
            }

            String date = etDate.getText().toString().trim();
            if (date.isEmpty()) {
                Toast.makeText(getContext(), "Please select date", Toast.LENGTH_SHORT).show();
                return;
            }

            Object selectedObj = spSourceSelector.getSelectedItem();
            String selectedSource = "";
            if (selectedObj instanceof AddTransactionBottomSheet.SourceItem) {
                selectedSource = ((AddTransactionBottomSheet.SourceItem) selectedObj).name;
            } else if (selectedObj != null) {
                selectedSource = selectedObj.toString();
            }
            boolean isLoan = rbTagLoan.isChecked();

            // Apply updates to transaction object
            transaction.date = date;
            transaction.type = selectedType;
            transaction.subCategory = subCategory;
            transaction.debitWithoutSign = debitVal;
            transaction.credit = creditVal;

            if (debitVal != 0) {
                transaction.debit = -Math.abs(debitVal);
            } else {
                transaction.debit = 0;
            }

            if (isLoan) {
                transaction.loans = selectedSource;
                transaction.account = "";
            } else {
                transaction.account = selectedSource;
                transaction.loans = "";
            }

            btnUpdate.setEnabled(false);
            btnUpdate.setText("Updating in Google Sheet...");

            FinanceRepository.getInstance(requireContext()).updateTransaction(transaction, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Transaction updated successfully!", Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onTransactionUpdated();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnUpdate.setEnabled(true);
                        btnUpdate.setText("Update Transaction");
                        Toast.makeText(getContext(), "Failed to update: " + error, Toast.LENGTH_LONG).show();
                    }
                }
            });
        });

        return v;
    }
}
