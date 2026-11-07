package com.samiy.financemanager.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.model.SipItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SipAdapter extends RecyclerView.Adapter<SipAdapter.ViewHolder> {
    public interface OnSipActionListener {
        void onStatusChange(SipItem item, String targetMonth, String newStatus);
        void onEditSip(SipItem item);
    }

    private List<SipItem> items = new ArrayList<>();
    private OnSipActionListener listener;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public void setItems(List<SipItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setListener(OnSipActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SipItem sip = items.get(position);

        holder.tvFundName.setText(sip.fundName != null && !sip.fundName.isEmpty() ? sip.fundName : "SIP Fund");

        // Role badge
        if (sip.role != null && !sip.role.trim().isEmpty()) {
            holder.tvRoleBadge.setVisibility(View.VISIBLE);
            holder.tvRoleBadge.setText(sip.role.trim());
        } else {
            holder.tvRoleBadge.setVisibility(View.GONE);
        }

        // Account & Platform
        String acct = sip.bankAccount != null && !sip.bankAccount.isEmpty() ? sip.bankAccount : "Bank";
        String plat = sip.platform != null && !sip.platform.isEmpty() ? sip.platform : "Auto";
        holder.tvAccountPlatform.setText(acct + " • " + plat);

        // Current Month
        String cMonth = (sip.currentMonth != null && !sip.currentMonth.trim().isEmpty()) ? sip.currentMonth.trim() : "Nov";
        holder.tvCurrentLabel.setText(cMonth + " SIP: " + formatCurrency(sip.currentSip));

        String cStatus = sip.currentStatus != null && !sip.currentStatus.isEmpty() ? sip.currentStatus : "Not Paid";
        holder.btnCurrentStatus.setText(cStatus);
        bindStatusStyle(holder.btnCurrentStatus, cStatus);

        holder.btnCurrentStatus.setOnClickListener(v -> {
            String newStatus = "Paid".equalsIgnoreCase(cStatus) ? "Not Paid" : "Paid";
            sip.currentStatus = newStatus;
            notifyItemChanged(position);
            if (listener != null) {
                listener.onStatusChange(sip, "current", newStatus);
            }
        });

        // Next Month
        String nMonth = (sip.nextMonth != null && !sip.nextMonth.trim().isEmpty()) ? sip.nextMonth.trim() : "Dec";
        holder.tvNextLabel.setText(nMonth + " SIP: " + formatCurrency(sip.nextSip));

        String nStatus = sip.nextStatus != null && !sip.nextStatus.isEmpty() ? sip.nextStatus : "Not Paid";
        holder.btnNextStatus.setText(nStatus);
        bindStatusStyle(holder.btnNextStatus, nStatus);

        holder.btnNextStatus.setOnClickListener(v -> {
            String newStatus = "Paid".equalsIgnoreCase(nStatus) ? "Not Paid" : "Paid";
            sip.nextStatus = newStatus;
            notifyItemChanged(position);
            if (listener != null) {
                listener.onStatusChange(sip, "next", newStatus);
            }
        });

        // Edit button & Item Click
        if (holder.btnEditSip != null) {
            holder.btnEditSip.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditSip(sip);
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditSip(sip);
            }
        });
    }

    private void bindStatusStyle(TextView btn, String status) {
        if ("Paid".equalsIgnoreCase(status)) {
            btn.setTextColor(ContextCompat.getColor(btn.getContext(), R.color.green_mint));
            btn.setBackgroundResource(R.drawable.bg_chip_green);
        } else {
            btn.setTextColor(ContextCompat.getColor(btn.getContext(), R.color.red_rose));
            btn.setBackgroundResource(R.drawable.bg_pill_dark);
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
        TextView tvFundName, tvRoleBadge, tvAccountPlatform, btnEditSip;
        TextView tvCurrentLabel, btnCurrentStatus;
        TextView tvNextLabel, btnNextStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvFundName = itemView.findViewById(R.id.tvSipFundName);
            tvRoleBadge = itemView.findViewById(R.id.tvSipRoleBadge);
            btnEditSip = itemView.findViewById(R.id.btnEditSip);
            tvAccountPlatform = itemView.findViewById(R.id.tvSipAccountPlatform);
            tvCurrentLabel = itemView.findViewById(R.id.tvSipCurrentLabel);
            btnCurrentStatus = itemView.findViewById(R.id.btnSipCurrentStatus);
            tvNextLabel = itemView.findViewById(R.id.tvSipNextLabel);
            btnNextStatus = itemView.findViewById(R.id.btnSipNextStatus);
        }
    }
}
