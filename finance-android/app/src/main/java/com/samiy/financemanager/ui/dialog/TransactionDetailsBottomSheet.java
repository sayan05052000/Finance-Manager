package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.CreditCard;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Transaction;

import java.text.NumberFormat;
import java.util.Locale;

public class TransactionDetailsBottomSheet extends BaseBottomSheetDialogFragment {
    private static final String ARG_TRANSACTION = "transaction";
    private Transaction transaction;

    public interface OnTransactionDeleteRequestedListener {
        void onDeleteRequested(Transaction tx);
    }

    private OnTransactionDeleteRequestedListener deleteListener;

    public static TransactionDetailsBottomSheet newInstance(Transaction transaction) {
        TransactionDetailsBottomSheet sheet = new TransactionDetailsBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TRANSACTION, transaction);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnTransactionDeleteRequestedListener(OnTransactionDeleteRequestedListener listener) {
        this.deleteListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            View bottomSheetInternal = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                bottomSheetInternal.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
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

        View v = inflater.inflate(R.layout.dialog_transaction_details, container, false);

        if (getArguments() != null) {
            transaction = (Transaction) getArguments().getSerializable(ARG_TRANSACTION);
        }

        if (transaction == null) {
            dismiss();
            return null;
        }

        TextView tvSubtitle = v.findViewById(R.id.tvTxDetailsSubtitle);
        View btnClose = v.findViewById(R.id.btnTxDetailsClose);
        TextView tvAmount = v.findViewById(R.id.tvTxDetailsAmount);
        TextView tvTypeBadge = v.findViewById(R.id.tvTxDetailsTypeBadge);
        TextView tvBalancingOutBadge = v.findViewById(R.id.tvTxDetailsBalancingOutBadge);
        TextView tvDate = v.findViewById(R.id.tvTxDetailsDate);
        TextView tvSource = v.findViewById(R.id.tvTxDetailsSource);
        View layoutTaggedCard = v.findViewById(R.id.layoutTxDetailsTaggedCard);
        TextView tvTaggedCard = v.findViewById(R.id.tvTxDetailsTaggedCard);
        TextView tvCategory = v.findViewById(R.id.tvTxDetailsCategory);
        TextView tvRemainder = v.findViewById(R.id.tvTxDetailsRemainder);
        View btnDelete = v.findViewById(R.id.btnDeleteTxInModal);

        if (tvSubtitle != null) {
            tvSubtitle.setText("Transactions Sheet • Row #" + transaction.id);
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dismiss());
        }

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        // Effective amount and color coding
        double effectiveNet = transaction.net;
        if (effectiveNet == 0 && !transaction.isBalancingOut()) {
            effectiveNet = (transaction.credit > 0 ? transaction.credit : 0) +
                    (transaction.debit != 0 ? transaction.debit : (transaction.debitWithoutSign > 0 ? -transaction.debitWithoutSign : 0));
        }

        if (transaction.isBalancingOut()) {
            tvAmount.setText("+" + formatCurrency(currencyFormat, transaction.debit));
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            tvBalancingOutBadge.setVisibility(View.VISIBLE);
        } else if (effectiveNet < 0) {
            tvAmount.setText("-" + formatCurrency(currencyFormat, Math.abs(effectiveNet)));
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
            tvBalancingOutBadge.setVisibility(View.GONE);
        } else if (effectiveNet > 0) {
            tvAmount.setText("+" + formatCurrency(currencyFormat, effectiveNet));
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            tvBalancingOutBadge.setVisibility(View.GONE);
        } else {
            tvAmount.setText(formatCurrency(currencyFormat, 0));
            tvAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
            tvBalancingOutBadge.setVisibility(View.GONE);
        }

        // Type badge
        String typeStr = transaction.type != null ? transaction.type : "Expense";
        tvTypeBadge.setText(typeStr);
        if ("Income".equalsIgnoreCase(typeStr)) {
            tvTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_mint));
            tvTypeBadge.setBackgroundResource(R.drawable.bg_chip_green);
        } else if ("Do not Consider".equalsIgnoreCase(typeStr)) {
            tvTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_sky));
            tvTypeBadge.setBackgroundResource(R.drawable.bg_chip_blue);
        } else {
            tvTypeBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.red_rose));
            tvTypeBadge.setBackgroundResource(R.drawable.bg_badge_red);
        }

        // Date & Cycle
        String formattedDate = transaction.getFormattedDate();
        String cycleLabel = transaction.getCycleLabel();
        tvDate.setText(formattedDate + (cycleLabel != null && !cycleLabel.isEmpty() ? " (" + cycleLabel + ")" : ""));

        // Source
        String sourceName = transaction.loans != null && !transaction.loans.trim().isEmpty() ? transaction.loans.trim() :
                (transaction.account != null && !transaction.account.trim().isEmpty() ? transaction.account.trim() : "General");
        tvSource.setText(sourceName.toUpperCase(Locale.ROOT));

        // Tagged Credit Card resolution
        String taggedCard = transaction.taggedCreditCard != null ? transaction.taggedCreditCard.trim() : "";
        if (taggedCard.isEmpty() && "CREDIT CARD".equalsIgnoreCase(transaction.loans != null ? transaction.loans.trim() : "")) {
            FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
            if (db != null && db.creditCards != null && db.creditCards.size() == 1) {
                taggedCard = db.creditCards.get(0).cardName;
            }
        }

        if (!taggedCard.isEmpty()) {
            layoutTaggedCard.setVisibility(View.VISIBLE);
            tvTaggedCard.setText("💳 " + taggedCard);
        } else {
            layoutTaggedCard.setVisibility(View.GONE);
        }

        // Category & Sub-Category
        String cat = (transaction.subCategory != null && !transaction.subCategory.trim().isEmpty() ? transaction.subCategory.trim() : "General") +
                (transaction.category != null && !transaction.category.trim().isEmpty() ? " • " + transaction.category.trim() : "");
        tvCategory.setText(cat);

        // Remainder
        if (transaction.remainder != 0) {
            tvRemainder.setText(formatCurrency(currencyFormat, transaction.remainder));
        } else {
            tvRemainder.setText("₹0.00");
        }

        // Delete button
        if (btnDelete != null) {
            btnDelete.setOnClickListener(view -> {
                dismiss();
                if (deleteListener != null) {
                    deleteListener.onDeleteRequested(transaction);
                }
            });
        }

        return v;
    }

    private String formatCurrency(NumberFormat format, double amount) {
        return format.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
