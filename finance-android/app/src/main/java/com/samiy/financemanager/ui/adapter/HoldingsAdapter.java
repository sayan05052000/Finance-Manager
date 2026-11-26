package com.samiy.financemanager.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.MutualFundTransaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HoldingsAdapter extends RecyclerView.Adapter<HoldingsAdapter.ViewHolder> {
    public interface OnHoldingClickListener {
        void onHoldingClick(Holding holding);
    }

    public interface OnHoldingActionListener {
        void onViewTransactions(Holding holding);
        void onAddTransaction(Holding holding);
    }

    private List<Holding> items = new ArrayList<>();
    private OnHoldingClickListener listener;
    private OnHoldingActionListener actionListener;
    private final Map<String, Integer> txCounts = new HashMap<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public void setOnHoldingClickListener(OnHoldingClickListener listener) {
        this.listener = listener;
    }

    public void setOnHoldingActionListener(OnHoldingActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void setItems(List<Holding> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setMutualFundTransactions(List<MutualFundTransaction> transactions) {
        txCounts.clear();
        if (transactions != null) {
            for (MutualFundTransaction tx : transactions) {
                if (tx != null && tx.schemeName != null) {
                    String key = tx.schemeName.trim().toLowerCase(Locale.US);
                    txCounts.put(key, txCounts.getOrDefault(key, 0) + 1);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_holding, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Holding h = items.get(position);
        Context context = holder.itemView.getContext();
        holder.tvSchemeName.setText(h.schemeName);
        holder.tvSavingsType.setText(h.savingsType != null && !h.savingsType.isEmpty() ? h.savingsType : "Mutual Fund");

        String subCatText = (h.category != null ? h.category : "Equity") + " • " +
                (h.subCategory != null ? h.subCategory : "") + " • " +
                (h.source != null ? h.source : "Groww");
        holder.tvSubCategory.setText(subCatText);

        holder.tvCurrentValue.setText(formatCurrency(h.currentValue));
        holder.tvInvested.setText("Inv: " + formatCurrency(h.investedValue));

        // 1-Day Change (Red if negative/loss, Green if positive/gain, Neutral if zero)
        if (h.oneDayChange < 0) {
            holder.tv1DChange.setText("-" + formatCurrency(Math.abs(h.oneDayChange)));
            holder.tv1DChange.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
            if (holder.tv1DChangeLabel != null) {
                holder.tv1DChangeLabel.setText("1-Day Loss");
            }
        } else if (h.oneDayChange > 0) {
            holder.tv1DChange.setText("+" + formatCurrency(h.oneDayChange));
            holder.tv1DChange.setTextColor(ContextCompat.getColor(context, R.color.green_mint));
            if (holder.tv1DChangeLabel != null) {
                holder.tv1DChangeLabel.setText("1-Day Gain");
            }
        } else {
            holder.tv1DChange.setText(formatCurrency(0));
            holder.tv1DChange.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            if (holder.tv1DChangeLabel != null) {
                holder.tv1DChangeLabel.setText("1-Day Change");
            }
        }

        // Total Returns & Current Value (Red if in loss, Green if in profit)
        boolean isLoss = h.returns < 0 || h.absReturn < 0 || (h.investedValue > 0 && h.currentValue < h.investedValue);
        if (isLoss) {
            String absReturnStr = String.format(Locale.US, "%.2f", Math.abs(h.absReturn));
            holder.tvReturns.setText("Tot: -" + formatCurrency(Math.abs(h.returns)) + " (-" + absReturnStr + "%)");
            holder.tvReturns.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
            holder.tvCurrentValue.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
        } else {
            String returnsSign = h.returns >= 0 ? "+" : "";
            String returnsText = "Tot: " + returnsSign + formatCurrency(h.returns) + " (" + String.format(Locale.US, "%.2f", h.absReturn) + "%)";
            holder.tvReturns.setText(returnsText);
            holder.tvReturns.setTextColor(ContextCompat.getColor(context, R.color.green_emerald));
            holder.tvCurrentValue.setTextColor(ContextCompat.getColor(context, R.color.green_mint));
        }

        // NAV and NAV Date
        String navStr = h.getFormattedNav();
        if (holder.tvNav != null) {
            if (!navStr.isEmpty()) {
                holder.tvNav.setText("NAV: " + navStr);
                holder.tvNav.setVisibility(View.VISIBLE);
            } else {
                holder.tvNav.setVisibility(View.GONE);
            }
        }

        String navDateStr = h.getFormattedNavDate();
        if (holder.tvNavDate != null) {
            if (!navDateStr.isEmpty()) {
                holder.tvNavDate.setText("NAV Date: " + navDateStr);
                holder.tvNavDate.setVisibility(View.VISIBLE);
            } else {
                holder.tvNavDate.setVisibility(View.GONE);
            }
        }

        // Transaction count and units badge
        String key = h.schemeName != null ? h.schemeName.trim().toLowerCase(Locale.US) : "";
        int count = txCounts.getOrDefault(key, 0);
        String unitsStr = h.units > 0 ? String.format(Locale.US, "%.3f units", h.units) : "";
        if (count > 0 && !unitsStr.isEmpty()) {
            holder.tvHoldingTxCount.setText(count + " txs • " + unitsStr);
        } else if (count > 0) {
            holder.tvHoldingTxCount.setText(count + " txs");
        } else if (!unitsStr.isEmpty()) {
            holder.tvHoldingTxCount.setText(unitsStr);
        } else {
            holder.tvHoldingTxCount.setText(h.savingsType != null && !h.savingsType.isEmpty() ? h.savingsType : "Mutual Fund");
        }

        // View Transactions Action
        View.OnClickListener viewTxClick = v -> {
            if (actionListener != null) {
                actionListener.onViewTransactions(h);
            } else if (listener != null) {
                listener.onHoldingClick(h);
            }
        };
        holder.itemView.setOnClickListener(viewTxClick);
        if (holder.btnViewTransactions != null) {
            holder.btnViewTransactions.setOnClickListener(viewTxClick);
        }

        // Add MF Transaction Action
        if (holder.btnAddTransaction != null) {
            holder.btnAddTransaction.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onAddTransaction(h);
                }
            });
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
        TextView tvSchemeName, tvSavingsType, tvSubCategory;
        TextView tvCurrentValue, tvInvested, tvNav, tv1DChangeLabel, tv1DChange, tvReturns, tvNavDate;
        TextView tvHoldingTxCount, btnViewTransactions, btnAddTransaction;

        ViewHolder(View itemView) {
            super(itemView);
            tvSchemeName = itemView.findViewById(R.id.tvHoldingSchemeName);
            tvSavingsType = itemView.findViewById(R.id.tvHoldingSavingsType);
            tvSubCategory = itemView.findViewById(R.id.tvHoldingSubCategory);
            tvCurrentValue = itemView.findViewById(R.id.tvHoldingCurrentValue);
            tvInvested = itemView.findViewById(R.id.tvHoldingInvested);
            tvNav = itemView.findViewById(R.id.tvHoldingNav);
            tv1DChangeLabel = itemView.findViewById(R.id.tvHolding1DChangeLabel);
            tv1DChange = itemView.findViewById(R.id.tvHolding1DChange);
            tvReturns = itemView.findViewById(R.id.tvHoldingReturns);
            tvNavDate = itemView.findViewById(R.id.tvHoldingNavDate);
            tvHoldingTxCount = itemView.findViewById(R.id.tvHoldingTxCount);
            btnViewTransactions = itemView.findViewById(R.id.btnViewHoldingTransactions);
            btnAddTransaction = itemView.findViewById(R.id.btnAddHoldingTransaction);
        }
    }
}