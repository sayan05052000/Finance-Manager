package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.SipItem;
import com.samiy.financemanager.ui.adapter.SipAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SipTracerBottomSheet extends BottomSheetDialogFragment {
    private TextView tvSipSubtitle, tvEmptySips;
    private RecyclerView rvSipList;
    private SipAdapter adapter;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_sip_tracer, container, false);

        tvSipSubtitle = v.findViewById(R.id.tvSipSubtitle);
        tvEmptySips = v.findViewById(R.id.tvEmptySips);
        rvSipList = v.findViewById(R.id.rvSipList);

        rvSipList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SipAdapter();
        rvSipList.setAdapter(adapter);

        View btnClose = v.findViewById(R.id.btnSipClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dismiss());
        }

        adapter.setListener((item, targetMonth, newStatus) -> {
            FinanceRepository.getInstance(requireContext()).updateSipStatus(
                    item.fundName, targetMonth, newStatus, new FinanceRepository.DataCallback() {
                        @Override
                        public void onLoaded(FinanceDatabase db) {
                            if (isAdded()) {
                                Toast.makeText(getContext(), item.fundName + " updated to " + newStatus, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (isAdded()) {
                                Toast.makeText(getContext(), "Failed to update: " + error, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        });

        loadData();

        return v;
    }

    private void loadData() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        List<SipItem> sips = (db != null && db.sips != null) ? db.sips : new ArrayList<>();

        double totalMonthlySip = 0.0;
        for (SipItem s : sips) {
            totalMonthlySip += s.currentSip;
        }

        if (tvSipSubtitle != null) {
            tvSipSubtitle.setText(sips.size() + " Active SIPs • Total Monthly: " + formatCurrency(totalMonthlySip));
        }

        if (sips.isEmpty()) {
            tvEmptySips.setVisibility(View.VISIBLE);
            rvSipList.setVisibility(View.GONE);
        } else {
            tvEmptySips.setVisibility(View.GONE);
            rvSipList.setVisibility(View.VISIBLE);
            adapter.setItems(sips);
        }
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
