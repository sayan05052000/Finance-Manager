package com.samiy.financemanager.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.samiy.financemanager.R;

import java.util.List;
import java.util.Locale;

public class AllocationSourceAdapter extends ArrayAdapter<AllocationSourceAdapter.SourceItem> {

    public static class SourceItem {
        public final String name;
        public final double availablePercent;
        public final double consumedPercent;

        public SourceItem(String name, double availablePercent, double consumedPercent) {
            this.name = name;
            this.availablePercent = Math.max(0.0, availablePercent);
            this.consumedPercent = consumedPercent;
        }

        public boolean isSelectable() {
            return availablePercent > 0.0001;
        }

        @NonNull
        @Override
        public String toString() {
            if (!isSelectable()) {
                return name + " • 0% available (Fully Allocated 🔒)";
            } else {
                return name + " • " + String.format(Locale.US, "%.0f%% available", availablePercent);
            }
        }
    }

    private final LayoutInflater inflater;

    public AllocationSourceAdapter(@NonNull Context context, @NonNull List<SourceItem> objects) {
        super(context, R.layout.item_spinner_selected, objects);
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public boolean isEnabled(int position) {
        SourceItem item = getItem(position);
        return item != null && item.isSelectable();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_spinner_selected, parent, false);
        }
        TextView tv = v.findViewById(android.R.id.text1);
        SourceItem item = getItem(position);
        if (item != null && tv != null) {
            if (item.isSelectable()) {
                tv.setText(item.name + " (" + String.format(Locale.US, "%.0f%% available", item.availablePercent) + ")");
                tv.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            } else {
                tv.setText(item.name + " (0% available - Fully Allocated 🔒)");
                tv.setTextColor(ContextCompat.getColor(getContext(), R.color.red_rose));
            }
        }
        return v;
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = inflater.inflate(R.layout.item_spinner_dropdown, parent, false);
        }
        TextView tv = v.findViewById(android.R.id.text1);
        SourceItem item = getItem(position);
        if (item != null && tv != null) {
            if (item.isSelectable()) {
                tv.setText(item.name + " • " + String.format(Locale.US, "%.0f%% available", item.availablePercent));
                tv.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                v.setEnabled(true);
                v.setAlpha(1.0f);
            } else {
                tv.setText(item.name + " • 0% available (Fully Allocated 🔒)");
                tv.setTextColor(ContextCompat.getColor(getContext(), R.color.text_muted));
                v.setEnabled(false);
                v.setAlpha(0.45f);
            }
        }
        return v;
    }
}
