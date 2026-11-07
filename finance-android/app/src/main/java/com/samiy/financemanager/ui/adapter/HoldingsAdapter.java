package com.samiy.financemanager.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.model.Holding;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HoldingsAdapter extends RecyclerView.Adapter<HoldingsAdapter.ViewHolder> {
    public interface OnHoldingClickListener {
        void onHoldingClick(Holding holding);
    }

    private List<Holding> items = new ArrayList<>();
    private OnHoldingClickListener listener;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public void setOnHoldingClickListener(OnHoldingClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Holding> items) {
        this.items = items != null ? items : new ArrayList<>();
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
        holder.tvSchemeName.setText(h.schemeName);
        holder.tvSavingsType.setText(h.savingsType != null && !h.savingsType.isEmpty() ? h.savingsType : "Mutual Fund");

        String subCatText = (h.category != null ? h.category : "Equity") + " • " +
                (h.subCategory != null ? h.subCategory : "") + " • " +
                (h.source != null ? h.source : "Groww");
        holder.tvSubCategory.setText(subCatText);

        holder.tvCurrentValue.setText(formatCurrency(h.currentValue));
        holder.tvInvested.setText("Inv: " + formatCurrency(h.investedValue));

        String changeSign = h.oneDayChange >= 0 ? "+" : "";
        holder.tv1DChange.setText(changeSign + formatCurrency(h.oneDayChange));

        String returnsSign = h.returns >= 0 ? "+" : "";
        String returnsText = "Tot: " + returnsSign + formatCurrency(h.returns) + " (" + String.format(Locale.US, "%.2f", h.absReturn) + "%)";
        holder.tvReturns.setText(returnsText);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHoldingClick(h);
            }
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
        TextView tvSchemeName, tvSavingsType, tvSubCategory;
        TextView tvCurrentValue, tvInvested, tv1DChange, tvReturns;

        ViewHolder(View itemView) {
            super(itemView);
            tvSchemeName = itemView.findViewById(R.id.tvHoldingSchemeName);
            tvSavingsType = itemView.findViewById(R.id.tvHoldingSavingsType);
            tvSubCategory = itemView.findViewById(R.id.tvHoldingSubCategory);
            tvCurrentValue = itemView.findViewById(R.id.tvHoldingCurrentValue);
            tvInvested = itemView.findViewById(R.id.tvHoldingInvested);
            tv1DChange = itemView.findViewById(R.id.tvHolding1DChange);
            tvReturns = itemView.findViewById(R.id.tvHoldingReturns);
        }
    }
}
