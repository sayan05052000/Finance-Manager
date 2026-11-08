package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.samiy.financemanager.MainActivity;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.CreditCard;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Transaction;
import com.samiy.financemanager.ui.adapter.TransactionsAdapter;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.auth.GoogleAuthManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AccountTransactionsBottomSheet extends BottomSheetDialogFragment {

    public static final String TYPE_ACCOUNT = "ACCOUNT";
    public static final String TYPE_LOAN = "LOAN";
    public static final String TYPE_CREDIT_CARD = "CREDIT_CARD";

    private String entityType;
    private String entityName;
    private String entitySubtitle;

    private RecyclerView rvTransactions;
    private TransactionsAdapter adapter;
    private TextView tvEmpty;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView tvCount;
    private TextView tvInflow;
    private TextView tvOutflow;
    private MaterialButton btnAddTx;
    private TextView btnOpenInLedger;

    private Runnable onDismissCallback;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public static AccountTransactionsBottomSheet newInstanceForAccount(String accountName) {
        return newInstanceForAccount(accountName, "");
    }

    public static AccountTransactionsBottomSheet newInstanceForAccount(String accountName, String balanceText) {
        AccountTransactionsBottomSheet sheet = new AccountTransactionsBottomSheet();
        Bundle args = new Bundle();
        args.putString("entity_type", TYPE_ACCOUNT);
        args.putString("entity_name", accountName);
        args.putString("entity_subtitle", balanceText.isEmpty() ? "Bank Account" : "Bank Account • Balance: " + balanceText);
        sheet.setArguments(args);
        return sheet;
    }

    public static AccountTransactionsBottomSheet newInstanceForLoan(String loanName) {
        return newInstanceForLoan(loanName, "");
    }

    public static AccountTransactionsBottomSheet newInstanceForLoan(String loanName, String balanceText) {
        AccountTransactionsBottomSheet sheet = new AccountTransactionsBottomSheet();
        Bundle args = new Bundle();
        args.putString("entity_type", TYPE_LOAN);
        args.putString("entity_name", loanName);
        args.putString("entity_subtitle", balanceText.isEmpty() ? "Loan Account" : "Loan Account • Outstanding: " + balanceText);
        sheet.setArguments(args);
        return sheet;
    }

    public static AccountTransactionsBottomSheet newInstanceForCreditCard(String cardName) {
        return newInstanceForCreditCard(cardName, "");
    }

    public static AccountTransactionsBottomSheet newInstanceForCreditCard(String cardName, String cardDetails) {
        AccountTransactionsBottomSheet sheet = new AccountTransactionsBottomSheet();
        Bundle args = new Bundle();
        args.putString("entity_type", TYPE_CREDIT_CARD);
        args.putString("entity_name", cardName);
        args.putString("entity_subtitle", cardDetails.isEmpty() ? "Credit Card" : "Credit Card • " + cardDetails);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnDismissCallback(Runnable callback) {
        this.onDismissCallback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.FinMngrBottomSheetDialogTheme);
        if (getArguments() != null) {
            entityType = getArguments().getString("entity_type", TYPE_ACCOUNT);
            entityName = getArguments().getString("entity_name", "");
            entitySubtitle = getArguments().getString("entity_subtitle", "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_account_transactions, container, false);

        tvTitle = v.findViewById(R.id.tvAccountSheetTitle);
        tvSubtitle = v.findViewById(R.id.tvAccountSheetSubtitle);
        tvCount = v.findViewById(R.id.tvSheetTxCount);
        tvInflow = v.findViewById(R.id.tvSheetTotalInflow);
        tvOutflow = v.findViewById(R.id.tvSheetTotalOutflow);
        btnAddTx = v.findViewById(R.id.btnSheetAddTransaction);
        btnOpenInLedger = v.findViewById(R.id.btnOpenInLedger);
        rvTransactions = v.findViewById(R.id.rvAccountTransactions);
        tvEmpty = v.findViewById(R.id.tvEmptyAccountTransactions);
        ImageView btnClose = v.findViewById(R.id.btnCloseAccountSheet);

        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dismiss());
        }

        tvTitle.setText(entityName + " Transactions");
        tvSubtitle.setText(entitySubtitle);

        if (TYPE_CREDIT_CARD.equals(entityType)) {
            btnAddTx.setText("＋ Add Transaction for " + entityName);
        } else if (TYPE_LOAN.equals(entityType)) {
            btnAddTx.setText("＋ Add Transaction for " + entityName);
        } else {
            btnAddTx.setText("＋ Add Transaction for " + entityName);
        }

        btnAddTx.setOnClickListener(view -> openLockedAddTransaction());

        btnOpenInLedger.setOnClickListener(view -> {
            dismiss();
            if (getActivity() instanceof MainActivity) {
                MainActivity ma = (MainActivity) getActivity();
                if (TYPE_LOAN.equals(entityType) || TYPE_CREDIT_CARD.equals(entityType)) {
                    ma.openLedgerWithFilter(null, "CREDIT CARD".equalsIgnoreCase(entityName) || TYPE_CREDIT_CARD.equals(entityType) ? "CREDIT CARD" : entityName);
                } else {
                    ma.openLedgerWithFilter(entityName, null);
                }
            }
        });

        rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TransactionsAdapter();
        adapter.setOnTransactionClickListener(this::showTransactionDetailsModal);
        adapter.setOnTransactionDeleteClickListener(this::confirmDeleteTransaction);
        rvTransactions.setAdapter(adapter);

        loadTransactions();

        return v;
    }

    private void openLockedAddTransaction() {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.showError(getContext(), "You are not authorized");
            return;
        }

        AddTransactionBottomSheet sheet;
        if (TYPE_CREDIT_CARD.equals(entityType)) {
            sheet = AddTransactionBottomSheet.newInstanceForCreditCard(entityName);
        } else if (TYPE_LOAN.equals(entityType)) {
            sheet = AddTransactionBottomSheet.newInstanceForLoan(entityName);
        } else {
            sheet = AddTransactionBottomSheet.newInstanceForAccount(entityName);
        }

        sheet.setOnTransactionAddedListener(() -> {
            loadTransactions();
            if (onDismissCallback != null) {
                onDismissCallback.run();
            }
        });
        sheet.show(getChildFragmentManager(), "add_tx_locked");
    }

    private void loadTransactions() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null || db.transactions == null) {
            showEmpty("No database loaded.");
            return;
        }

        List<Transaction> matching = new ArrayList<>();
        double totalInflow = 0.0;
        double totalOutflow = 0.0;

        boolean onlyOneCreditCard = db.creditCards != null && db.creditCards.size() == 1;

        for (Transaction t : db.transactions) {
            boolean matches = false;
            if (TYPE_CREDIT_CARD.equals(entityType)) {
                if ("CREDIT CARD".equalsIgnoreCase(t.loans != null ? t.loans.trim() : "")) {
                    if (t.taggedCreditCard != null && t.taggedCreditCard.trim().equalsIgnoreCase(entityName)) {
                        matches = true;
                    } else if ((t.taggedCreditCard == null || t.taggedCreditCard.trim().isEmpty()) && onlyOneCreditCard) {
                        matches = true;
                    }
                }
            } else if (TYPE_LOAN.equals(entityType)) {
                if (t.loans != null && t.loans.trim().equalsIgnoreCase(entityName)) {
                    matches = true;
                }
            } else {
                if (t.account != null && t.account.trim().equalsIgnoreCase(entityName)) {
                    matches = true;
                }
            }

            if (matches) {
                matching.add(t);
                double debitAmount = t.debit != 0 ? t.debit : (t.debitWithoutSign != 0 ? -t.debitWithoutSign : 0);
                if (debitAmount < 0) {
                    totalOutflow += Math.abs(debitAmount);
                }
                totalInflow += t.credit;
            }
        }

        tvCount.setText(matching.size() + " txns");
        tvInflow.setText("+" + formatCurrency(totalInflow));
        tvOutflow.setText("-" + formatCurrency(totalOutflow));

        if (matching.isEmpty()) {
            rvTransactions.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("No transactions recorded for " + entityName + ".");
        } else {
            rvTransactions.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            adapter.setItems(matching);
        }
    }

    private void showEmpty(String msg) {
        if (rvTransactions != null) rvTransactions.setVisibility(View.GONE);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(msg);
        }
    }

    private void showTransactionDetailsModal(Transaction tx) {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.showError(getContext(), "You are not authorized");
            return;
        }
        TransactionDetailsBottomSheet dialog = TransactionDetailsBottomSheet.newInstance(tx);
        dialog.setOnTransactionDeleteRequestedListener(this::confirmDeleteTransaction);
        dialog.show(getChildFragmentManager(), "TxDetailsModal");
    }

    private void confirmDeleteTransaction(Transaction tx) {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.showError(getContext(), "You are not authorized");
            return;
        }

        String taggedCard = tx.taggedCreditCard != null ? tx.taggedCreditCard.trim() : "";
        if (taggedCard.isEmpty() && "CREDIT CARD".equalsIgnoreCase(tx.loans != null ? tx.loans.trim() : "")) {
            FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
            if (db != null && db.creditCards != null && db.creditCards.size() == 1) {
                taggedCard = db.creditCards.get(0).cardName;
            }
        }

        String details = (tx.getDisplaySource() != null ? tx.getDisplaySource() : "") +
                (tx.date != null ? " • " + tx.date : "") +
                (tx.subCategory != null ? "\n" + tx.subCategory : "");

        String message = "Are you sure you want to permanently delete this transaction from your Google Sheet?\n\n" + details;
        if (!taggedCard.isEmpty()) {
            double delta = (tx.debitWithoutSign > 0) ? (tx.debitWithoutSign - tx.credit) : -tx.credit;
            if (delta == 0 && tx.net != 0) delta = Math.abs(tx.net);
            message += "\n\n💳 " + taggedCard + " unbilled spend (" + formatCurrency(delta) + ") will be reversed.";
        }

        FinMngrDialog.showDestructiveConfirmation(requireContext(),
                "Delete Transaction",
                message,
                "Delete",
                () -> {
                    if (adapter != null) {
                        adapter.markDeleting(tx.id);
                    }

                    FinanceRepository.getInstance(requireContext()).deleteTransaction(tx.id, new FinanceRepository.DataCallback() {
                        @Override
                        public void onLoaded(FinanceDatabase updatedDb) {
                            if (isAdded()) {
                                if (adapter != null) {
                                    adapter.unmarkDeleting(tx.id);
                                }
                                loadTransactions();
                                if (onDismissCallback != null) {
                                    onDismissCallback.run();
                                }
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).updateHeaderInfo();
                                }
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (isAdded()) {
                                if (adapter != null) {
                                    adapter.unmarkDeleting(tx.id);
                                }
                            }
                        }
                    });
                });
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
