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
import com.samiy.financemanager.ui.dialog.FinMngrDialog;
import com.samiy.financemanager.util.SipHelper;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SipAdapter extends RecyclerView.Adapter<SipAdapter.ViewHolder> {
    public interface OnSipActionListener {
        void onStatusChange(SipItem item, String targetMonth, String newStatus);
        void onAdvanceCycle(SipItem originalItem, SipItem advancedItem);
        void onEditSip(SipItem item);
        void onDeleteSip(SipItem item);
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

        // Next Month
        String nMonth = (sip.nextMonth != null && !sip.nextMonth.trim().isEmpty()) ? sip.nextMonth.trim() : "Dec";
        holder.tvNextLabel.setText(nMonth + " SIP: " + formatCurrency(sip.nextSip));

        String nStatus = sip.nextStatus != null && !sip.nextStatus.isEmpty() ? sip.nextStatus : "Not Paid";
        holder.btnNextStatus.setText(nStatus);
        bindStatusStyle(holder.btnNextStatus, nStatus);

        // Current Month Status Click with Confirmation & Cycle Advancement
        holder.btnCurrentStatus.setOnClickListener(v -> {
            if ("Not Paid".equalsIgnoreCase(cStatus)) {
                String afterNext = SipHelper.getNextMonthName(nMonth);
                FinMngrDialog.showConfirmation(v.getContext(),
                        "Mark SIP as Paid?",
                        "Mark " + sip.fundName + " as Paid for " + cMonth + " (" + formatCurrency(sip.currentSip) + ")?\n\nThis will advance the cycle:\n• Current Month becomes " + nMonth + " (" + formatCurrency(sip.nextSip) + ", Not Paid)\n• Next Month becomes " + afterNext + " (" + formatCurrency(sip.nextSip) + ", Not Paid)",
                        "Confirm & Advance",
                        () -> {
                            SipItem advanced = SipHelper.advanceSipCycle(sip);
                            if (listener != null) {
                                listener.onAdvanceCycle(sip, advanced);
                            }
                        });
            } else {
                FinMngrDialog.showConfirmation(v.getContext(),
                        "Mark SIP as Not Paid?",
                        "Are you sure you want to mark " + sip.fundName + " as Not Paid for " + cMonth + "?",
                        "Confirm",
                        () -> {
                            if (listener != null) {
                                listener.onStatusChange(sip, "current", "Not Paid");
                            }
                        });
            }
        });

        // Next Month Status Click with Confirmation
        holder.btnNextStatus.setOnClickListener(v -> {
            if ("Not Paid".equalsIgnoreCase(nStatus)) {
                FinMngrDialog.showConfirmation(v.getContext(),
                        "Mark Next Month as Paid?",
                        "Are you sure you want to mark " + sip.fundName + " as Paid for " + nMonth + " (" + formatCurrency(sip.nextSip) + ")?",
                        "Confirm",
                        () -> {
                            if (listener != null) {
                                listener.onStatusChange(sip, "next", "Paid");
                            }
                        });
            } else {
                FinMngrDialog.showConfirmation(v.getContext(),
                        "Mark Next Month as Not Paid?",
                        "Are you sure you want to mark " + sip.fundName + " as Not Paid for " + nMonth + "?",
                        "Confirm",
                        () -> {
                            if (listener != null) {
                                listener.onStatusChange(sip, "next", "Not Paid");
                            }
                        });
            }
        });

        // Edit button
        if (holder.btnEditSip != null) {
            holder.btnEditSip.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditSip(sip);
                }
            });
        }

        // Delete button
        if (holder.btnDeleteSipItem != null) {
            holder.btnDeleteSipItem.setOnClickListener(v -> {
                FinMngrDialog.showDestructiveConfirmation(v.getContext(),
                        "Delete SIP",
                        "Are you sure you want to delete '" + sip.fundName + "' from SIP Tracer? This will permanently remove it from your Google Sheet.",
                        "Delete",
                        () -> {
                            if (listener != null) {
                                listener.onDeleteSip(sip);
                            }
                        });
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
        return items != null ? items.size() : 0;
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFundName, tvRoleBadge, tvAccountPlatform, btnEditSip, btnDeleteSipItem;
        TextView tvCurrentLabel, btnCurrentStatus;
        TextView tvNextLabel, btnNextStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvFundName = itemView.findViewById(R.id.tvSipFundName);
            tvRoleBadge = itemView.findViewById(R.id.tvSipRoleBadge);
            btnEditSip = itemView.findViewById(R.id.btnEditSip);
            btnDeleteSipItem = itemView.findViewById(R.id.btnDeleteSipItem);
            tvAccountPlatform = itemView.findViewById(R.id.tvSipAccountPlatform);
            tvCurrentLabel = itemView.findViewById(R.id.tvSipCurrentLabel);
            btnCurrentStatus = itemView.findViewById(R.id.btnSipCurrentStatus);
            tvNextLabel = itemView.findViewById(R.id.tvSipNextLabel);
            btnNextStatus = itemView.findViewById(R.id.btnSipNextStatus);
        }
    }
}
