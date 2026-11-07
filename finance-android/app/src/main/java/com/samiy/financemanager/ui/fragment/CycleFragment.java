package com.samiy.financemanager.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.CurrentCycle;
import com.samiy.financemanager.data.model.FinanceDatabase;

import java.text.NumberFormat;
import java.util.Locale;

public class CycleFragment extends Fragment {
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvCycleDetailTitle, tvCycleDetailPeriod;
    private TextView tvAllocatedBudget, tvTotalCycleSpent, tvCycleRemainderVal;
    private TextView tvDailyLimitValueCycle;
    private LinearLayout layoutCycleSubCats, layoutCycleReceivables;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_cycle, container, false);

        swipeRefresh = v.findViewById(R.id.swipeRefreshCycle);
        tvCycleDetailTitle = v.findViewById(R.id.tvCycleDetailTitle);
        tvCycleDetailPeriod = v.findViewById(R.id.tvCycleDetailPeriod);
        tvAllocatedBudget = v.findViewById(R.id.tvAllocatedBudget);
        tvTotalCycleSpent = v.findViewById(R.id.tvTotalCycleSpent);
        tvCycleRemainderVal = v.findViewById(R.id.tvCycleRemainderVal);
        tvDailyLimitValueCycle = v.findViewById(R.id.tvDailyLimitValueCycle);
        layoutCycleSubCats = v.findViewById(R.id.layoutCycleSubCats);
        layoutCycleReceivables = v.findViewById(R.id.layoutCycleReceivables);

        swipeRefresh.setOnRefreshListener(this::refreshData);

        populateUI();

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        populateUI();
    }

    public void populateUI() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db == null || db.currentCycle == null) return;

        CurrentCycle c = db.currentCycle;
        tvCycleDetailTitle.setText(c.cycleName);
        tvCycleDetailPeriod.setText(c.cycleStart + " – " + c.cycleEnd + " • " + c.daysLeft + " days left");

        tvAllocatedBudget.setText(formatCurrency(c.budget));
        tvTotalCycleSpent.setText(formatCurrency(c.totalSpend));
        tvCycleRemainderVal.setText(formatCurrency(c.remainder));
        tvDailyLimitValueCycle.setText(formatCurrency(c.canSpendPerDay) + " / day");

        // Subcategories
        layoutCycleSubCats.removeAllViews();
        if (c.subCategories != null) {
            for (CurrentCycle.SubCategorySpend sc : c.subCategories) {
                LinearLayout item = new LinearLayout(getContext());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackgroundResource(R.drawable.bg_card);
                item.setPadding(32, 24, 32, 24);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                item.setLayoutParams(lp);

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);

                TextView name = new TextView(getContext());
                name.setText(sc.name);
                name.setTextColor(getResources().getColor(R.color.text_primary));
                name.setTextSize(13);
                name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView amt = new TextView(getContext());
                amt.setText(formatCurrency(sc.amount) + " (" + String.format(Locale.US, "%.1f", sc.percentage) + "%)");
                amt.setTextColor(getResources().getColor(R.color.text_secondary));
                amt.setTextSize(13);

                row.addView(name);
                row.addView(amt);
                item.addView(row);
                layoutCycleSubCats.addView(item);
            }
        }

        // Receivables
        layoutCycleReceivables.removeAllViews();
        if (c.receivables != null) {
            for (CurrentCycle.ReceivableItem r : c.receivables) {
                LinearLayout item = new LinearLayout(getContext());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackgroundResource(R.drawable.bg_card);
                item.setPadding(32, 24, 32, 24);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                item.setLayoutParams(lp);

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);

                TextView src = new TextView(getContext());
                src.setText(r.source);
                src.setTextColor(getResources().getColor(R.color.text_primary));
                src.setTextSize(13);
                src.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView amt = new TextView(getContext());
                amt.setText(formatCurrency(r.amount));
                amt.setTextColor(getResources().getColor(R.color.blue_sky));
                amt.setTextSize(13);

                row.addView(src);
                row.addView(amt);
                item.addView(row);

                if (r.note != null && !r.note.isEmpty()) {
                    TextView note = new TextView(getContext());
                    note.setText(r.note);
                    note.setTextColor(getResources().getColor(R.color.text_muted));
                    note.setTextSize(11);
                    note.setPadding(0, 8, 0, 0);
                    item.addView(note);
                }

                layoutCycleReceivables.addView(item);
            }
        }
    }

    private void refreshData() {
        FinanceRepository.getInstance(requireContext()).syncWithGoogleSheets(new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase db) {
                if (isAdded()) {
                    swipeRefresh.setRefreshing(false);
                    populateUI();
                    Toast.makeText(getContext(), "Synced with Google Sheet", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(getContext(), "Sync failed: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
