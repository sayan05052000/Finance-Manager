package com.samiy.financemanager.ui.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Transaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TransactionsAdapter extends RecyclerView.Adapter<TransactionsAdapter.ViewHolder> {
    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    private OnTransactionClickListener listener;

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    public interface OnTransactionDeleteClickListener {
        void onTransactionDelete(Transaction transaction);
    }

    private OnTransactionDeleteClickListener deleteClickListener;

    public void setOnTransactionDeleteClickListener(OnTransactionDeleteClickListener listener) {
        this.deleteClickListener = listener;
    }

    public interface OnTransactionLongClickListener {
        void onTransactionLongClick(Transaction transaction);
    }

    private OnTransactionLongClickListener longClickListener;

    public void setOnTransactionLongClickListener(OnTransactionLongClickListener listener) {
        this.longClickListener = listener;
    }

    private final Set<Long> deletingTxIds = new HashSet<>();

    public void markDeleting(long id) {
        deletingTxIds.add(id);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void unmarkDeleting(long id) {
        deletingTxIds.remove(id);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public boolean isDeleting(long id) {
        return deletingTxIds.contains(id);
    }

    private List<Transaction> items = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public void setItems(List<Transaction> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction tx = items.get(position);
        holder.tvSourceBadge.setText(tx.getDisplaySource().toUpperCase(Locale.ROOT));
        holder.tvDate.setText(tx.getFormattedDate());

        // Resolve tagged credit card with fallback if only 1 card exists
        String taggedCard = tx.taggedCreditCard != null ? tx.taggedCreditCard.trim() : "";
        if (taggedCard.isEmpty() && "CREDIT CARD".equalsIgnoreCase(tx.loans != null ? tx.loans.trim() : "")) {
            FinanceDatabase db = FinanceRepository.getInstance(holder.itemView.getContext()).getDatabase();
            if (db != null && db.creditCards != null && db.creditCards.size() == 1) {
                taggedCard = db.creditCards.get(0).cardName;
            }
        }

        if (holder.tvCardBadge != null) {
            if (!taggedCard.isEmpty()) {
                holder.tvCardBadge.setText("💳 " + taggedCard);
                holder.tvCardBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvCardBadge.setVisibility(View.GONE);
            }
        }

        // Value in debit does NOT mean expense.
        // It is an expense only if marked as "Expense".
        // A positive debit in an expense balances out (refund/cashback).
        double effectiveNet = tx.net;
        if (effectiveNet == 0 && !tx.isBalancingOut()) {
            effectiveNet = (tx.credit > 0 ? tx.credit : 0) +
                    (tx.debit != 0 ? tx.debit : (tx.debitWithoutSign > 0 ? -tx.debitWithoutSign : 0));
        }

        if (tx.isBalancingOut()) {
            holder.tvAmount.setText("+" + formatCurrency(tx.debit));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_mint));
        } else if (effectiveNet < 0) {
            holder.tvAmount.setText("-" + formatCurrency(Math.abs(effectiveNet)));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.red_rose));
        } else if (effectiveNet > 0) {
            holder.tvAmount.setText("+" + formatCurrency(effectiveNet));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_mint));
        } else {
            holder.tvAmount.setText(formatCurrency(0));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_muted));
        }

        String catDesc = (tx.subCategory != null && !tx.subCategory.isEmpty() ? tx.subCategory : "General") +
                (tx.category != null && !tx.category.isEmpty() ? " • " + tx.category : "");
        if (!taggedCard.isEmpty()) {
            catDesc += " • 💳 " + taggedCard;
        }
        if (tx.isBalancingOut()) {
            catDesc += " (Balancing Out)";
        }
        holder.tvCategoryDesc.setText(catDesc);

        if (tx.remainder != 0) {
            holder.tvRemainder.setText(formatCurrency(tx.remainder));
            holder.tvRemainder.setVisibility(View.VISIBLE);
        } else {
            holder.tvRemainder.setVisibility(View.GONE);
        }

        boolean isDeleting = deletingTxIds.contains(tx.id);
        if (isDeleting) {
            holder.itemView.setAlpha(0.35f);
            holder.tvAmount.setPaintFlags(holder.tvAmount.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            if (holder.btnDelete != null) {
                holder.btnDelete.setText("⏳ Deleting...");
                holder.btnDelete.setEnabled(false);
            }
            holder.itemView.setEnabled(false);
        } else {
            holder.itemView.setAlpha(1.0f);
            holder.tvAmount.setPaintFlags(holder.tvAmount.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            if (holder.btnDelete != null) {
                holder.btnDelete.setText("🗑️ Delete");
                holder.btnDelete.setEnabled(true);
            }
            holder.itemView.setEnabled(true);
        }

        // Clicking the card body opens transaction details modal
        holder.itemView.setOnClickListener(v -> {
            if (isDeleting) return;
            if (listener != null) {
                listener.onTransactionClick(tx);
            }
        });

        // Clicking the delete button directly triggers delete confirmation
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                if (isDeleting) return;
                if (deleteClickListener != null) {
                    deleteClickListener.onTransactionDelete(tx);
                } else if (listener != null) {
                    listener.onTransactionClick(tx);
                }
            });
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (isDeleting) return false;
            if (longClickListener != null) {
                longClickListener.onTransactionLongClick(tx);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSourceBadge, tvCardBadge, tvDate, tvAmount, tvCategoryDesc, tvRemainder, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            tvSourceBadge = itemView.findViewById(R.id.tvTxSourceBadge);
            tvCardBadge = itemView.findViewById(R.id.tvTxCardBadge);
            tvDate = itemView.findViewById(R.id.tvTxDate);
            tvAmount = itemView.findViewById(R.id.tvTxAmount);
            tvCategoryDesc = itemView.findViewById(R.id.tvTxCategoryDesc);
            tvRemainder = itemView.findViewById(R.id.tvTxRemainder);
            btnDelete = itemView.findViewById(R.id.btnDeleteTxItem);
        }
    }
}
