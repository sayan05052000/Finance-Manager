package com.samiy.financemanager.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.model.MutualFundTransaction;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MfTransactionsAdapter extends RecyclerView.Adapter<MfTransactionsAdapter.ViewHolder> {
    private List<MutualFundTransaction> items = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public void setItems(List<MutualFundTransaction> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mf_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MutualFundTransaction tx = items.get(position);

        holder.tvDate.setText(!tx.getFormattedDate().isEmpty() ? tx.getFormattedDate() : (tx.date != null && !tx.date.isEmpty() ? tx.date : "—"));
        holder.tvSchemeName.setText(tx.schemeName != null && !tx.schemeName.isEmpty() ? tx.schemeName : "Mutual Fund Scheme");

        // Meta string: e.g. "Equity • Small Cap • Groww"
        StringBuilder meta = new StringBuilder();
        if (tx.fundCategory != null && !tx.fundCategory.isEmpty()) meta.append(tx.fundCategory);
        if (tx.fundSubCategory != null && !tx.fundSubCategory.isEmpty()) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(tx.fundSubCategory);
        }
        if (tx.platform != null && !tx.platform.isEmpty()) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(tx.platform);
        }
        holder.tvMeta.setText(meta.toString());

        // Folio badge
        if (tx.folioNumber != null && !tx.folioNumber.trim().isEmpty()) {
            holder.tvFolio.setVisibility(View.VISIBLE);
            holder.tvFolio.setText("Folio: " + tx.folioNumber.trim());
        } else {
            holder.tvFolio.setVisibility(View.GONE);
        }

        // Type badge (Buy/SIP vs Redeem)
        if (tx.isRedeem()) {
            holder.tvTypeBadge.setText("Redeem");
            holder.tvTypeBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.red_rose));
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_pill_dark);
        } else {
            holder.tvTypeBadge.setText("Buy / SIP");
            holder.tvTypeBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.green_mint));
            holder.tvTypeBadge.setBackgroundResource(R.drawable.bg_chip_green);
        }

        // Amount, Units, NAV
        double displayAmt = tx.getDisplayAmount();
        holder.tvAmount.setText(formatCurrency(displayAmt));
        holder.tvUnits.setText(String.format(Locale.US, "%.3f", Math.abs(tx.units)));
        holder.tvNav.setText(formatCurrency(tx.nav));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvFolio, tvTypeBadge, tvSchemeName, tvMeta;
        TextView tvAmount, tvUnits, tvNav;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvMfTxDate);
            tvFolio = itemView.findViewById(R.id.tvMfTxFolio);
            tvTypeBadge = itemView.findViewById(R.id.tvMfTxTypeBadge);
            tvSchemeName = itemView.findViewById(R.id.tvMfTxSchemeName);
            tvMeta = itemView.findViewById(R.id.tvMfTxMeta);
            tvAmount = itemView.findViewById(R.id.tvMfTxAmount);
            tvUnits = itemView.findViewById(R.id.tvMfTxUnits);
            tvNav = itemView.findViewById(R.id.tvMfTxNav);
        }
    }
}
