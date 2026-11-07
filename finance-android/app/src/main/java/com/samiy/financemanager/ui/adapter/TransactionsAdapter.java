package com.samiy.financemanager.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.model.Transaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionsAdapter extends RecyclerView.Adapter<TransactionsAdapter.ViewHolder> {
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

        // Value in debit does NOT mean expense.
        // It is an expense only if marked as "Expense".
        // A positive debit in an expense balances out (refund/cashback).
        if (tx.isBalancingOut()) {
            holder.tvAmount.setText("+" + formatCurrency(tx.debit));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_mint));
        } else if (tx.debit < 0) {
            holder.tvAmount.setText("-" + formatCurrency(Math.abs(tx.debit)));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.red_rose));
        } else if (tx.credit > 0) {
            holder.tvAmount.setText("+" + formatCurrency(tx.credit));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_mint));
        } else if (tx.debit > 0) {
            holder.tvAmount.setText("+" + formatCurrency(tx.debit));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_mint));
        } else if (tx.debitWithoutSign > 0) {
            holder.tvAmount.setText("-" + formatCurrency(tx.debitWithoutSign));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.red_rose));
        } else {
            holder.tvAmount.setText(formatCurrency(0));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_muted));
        }

        String catDesc = (tx.subCategory != null && !tx.subCategory.isEmpty() ? tx.subCategory : "General") +
                (tx.category != null && !tx.category.isEmpty() ? " • " + tx.category : "");
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
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSourceBadge, tvDate, tvAmount, tvCategoryDesc, tvRemainder;

        ViewHolder(View itemView) {
            super(itemView);
            tvSourceBadge = itemView.findViewById(R.id.tvTxSourceBadge);
            tvDate = itemView.findViewById(R.id.tvTxDate);
            tvAmount = itemView.findViewById(R.id.tvTxAmount);
            tvCategoryDesc = itemView.findViewById(R.id.tvTxCategoryDesc);
            tvRemainder = itemView.findViewById(R.id.tvTxRemainder);
        }
    }
}
