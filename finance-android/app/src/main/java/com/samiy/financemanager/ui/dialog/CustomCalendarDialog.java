package com.samiy.financemanager.ui.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.samiy.financemanager.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class CustomCalendarDialog extends Dialog {

    public interface OnDateSelectedListener {
        void onDateSelected(int day, int month, int year, String formattedDate);
    }

    private final OnDateSelectedListener listener;
    private final String initialDateStr;

    private int selectedYear;
    private int selectedMonth; // 0-based
    private int selectedDay;

    private int displayYear;
    private int displayMonth; // 0-based

    private int todayYear;
    private int todayMonth;
    private int todayDay;

    private TextView tvMonthYear;
    private TextView tvSelectedDatePreview;
    private RecyclerView rvCalendarGrid;
    private CalendarDayAdapter adapter;

    public CustomCalendarDialog(@NonNull Context context, String initialDateStr, OnDateSelectedListener listener) {
        super(context);
        this.initialDateStr = initialDateStr;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_custom_calendar);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92);
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        initDates();
        initViews();
        updateCalendarGrid();
    }

    private void initDates() {
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        todayYear = today.get(Calendar.YEAR);
        todayMonth = today.get(Calendar.MONTH);
        todayDay = today.get(Calendar.DAY_OF_MONTH);

        selectedYear = todayYear;
        selectedMonth = todayMonth;
        selectedDay = todayDay;

        if (initialDateStr != null && initialDateStr.contains("/")) {
            String[] parts = initialDateStr.trim().split("/");
            if (parts.length == 3) {
                try {
                    int d = Integer.parseInt(parts[0].trim());
                    int m = Integer.parseInt(parts[1].trim()) - 1;
                    int y = Integer.parseInt(parts[2].trim());
                    if (d >= 1 && d <= 31 && m >= 0 && m <= 11 && y >= 2000) {
                        selectedDay = d;
                        selectedMonth = m;
                        selectedYear = y;
                    }
                } catch (Exception ignored) {}
            }
        }

        displayYear = selectedYear;
        displayMonth = selectedMonth;
    }

    private void initViews() {
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDatePreview = findViewById(R.id.tvSelectedDatePreview);
        rvCalendarGrid = findViewById(R.id.rvCalendarGrid);

        View tvClose = findViewById(R.id.tvCalendarClose);
        View btnPrev = findViewById(R.id.btnPrevMonth);
        View btnNext = findViewById(R.id.btnNextMonth);
        View btnCancel = findViewById(R.id.btnCalendarCancel);
        View btnConfirm = findViewById(R.id.btnCalendarConfirm);

        // Presets
        View btnToday = findViewById(R.id.btnPresetToday);
        View btnYesterday = findViewById(R.id.btnPresetYesterday);
        View btnCycleStart = findViewById(R.id.btnPresetCycleStart);
        View btnMonthStart = findViewById(R.id.btnPresetMonthStart);

        if (tvClose != null) tvClose.setOnClickListener(v -> dismiss());
        if (btnCancel != null) btnCancel.setOnClickListener(v -> dismiss());

        if (btnPrev != null) btnPrev.setOnClickListener(v -> navigateMonth(-1));
        if (btnNext != null) btnNext.setOnClickListener(v -> navigateMonth(1));

        if (tvMonthYear != null) {
            tvMonthYear.setOnClickListener(v -> showYearPickerDialog());
        }

        if (btnToday != null) {
            btnToday.setOnClickListener(v -> {
                selectedYear = todayYear;
                selectedMonth = todayMonth;
                selectedDay = todayDay;
                displayYear = todayYear;
                displayMonth = todayMonth;
                updateCalendarGrid();
            });
        }

        if (btnYesterday != null) {
            btnYesterday.setOnClickListener(v -> {
                Calendar yCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
                yCal.add(Calendar.DAY_OF_YEAR, -1);
                selectedYear = yCal.get(Calendar.YEAR);
                selectedMonth = yCal.get(Calendar.MONTH);
                selectedDay = yCal.get(Calendar.DAY_OF_MONTH);
                displayYear = selectedYear;
                displayMonth = selectedMonth;
                updateCalendarGrid();
            });
        }

        if (btnCycleStart != null) {
            btnCycleStart.setOnClickListener(v -> {
                // Billing cycle starts on the 6th
                selectedDay = 6;
                selectedMonth = displayMonth;
                selectedYear = displayYear;
                updateCalendarGrid();
            });
        }

        if (btnMonthStart != null) {
            btnMonthStart.setOnClickListener(v -> {
                selectedDay = 1;
                selectedMonth = displayMonth;
                selectedYear = displayYear;
                updateCalendarGrid();
            });
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String formatted = String.format(Locale.US, "%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear);
                if (listener != null) {
                    listener.onDateSelected(selectedDay, selectedMonth + 1, selectedYear, formatted);
                }
                dismiss();
            });
        }

        rvCalendarGrid.setLayoutManager(new GridLayoutManager(getContext(), 7));
        adapter = new CalendarDayAdapter();
        rvCalendarGrid.setAdapter(adapter);
    }

    private void navigateMonth(int delta) {
        displayMonth += delta;
        if (displayMonth < 0) {
            displayMonth = 11;
            displayYear--;
        } else if (displayMonth > 11) {
            displayMonth = 0;
            displayYear++;
        }
        updateCalendarGrid();
    }

    private void showYearPickerDialog() {
        String[] years = new String[16];
        int startYear = 2020;
        int defaultIndex = 0;
        for (int i = 0; i < years.length; i++) {
            years[i] = String.valueOf(startYear + i);
            if (startYear + i == displayYear) {
                defaultIndex = i;
            }
        }

        new AlertDialog.Builder(getContext(), R.style.CustomDarkDialog)
                .setTitle("Select Year")
                .setSingleChoiceItems(years, defaultIndex, (dialog, which) -> {
                    displayYear = startYear + which;
                    updateCalendarGrid();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCalendarGrid() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, displayYear);
        cal.set(Calendar.MONTH, displayMonth);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        SimpleDateFormat titleFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        tvMonthYear.setText(titleFormat.format(cal.getTime()));

        updatePreview();

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updatePreview() {
        Calendar sel = Calendar.getInstance();
        sel.set(selectedYear, selectedMonth, selectedDay);
        SimpleDateFormat previewFormat = new SimpleDateFormat("EEE, dd MMM yyyy", Locale.US);
        tvSelectedDatePreview.setText("Selected: " + previewFormat.format(sel.getTime()));
    }

    private class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.DayViewHolder> {

        @NonNull
        @Override
        public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
            return new DayViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, displayYear);
            cal.set(Calendar.MONTH, displayMonth);
            cal.set(Calendar.DAY_OF_MONTH, 1);

            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // Sunday = 1, Monday = 2...
            int offset = firstDayOfWeek - 1; // Sunday = 0
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            if (position < offset || position >= offset + daysInMonth) {
                holder.tvDayNumber.setText("");
                holder.tvDayNumber.setBackground(null);
                holder.layoutDayCell.setClickable(false);
                holder.layoutDayCell.setOnClickListener(null);
                return;
            }

            int day = position - offset + 1;
            holder.tvDayNumber.setText(String.valueOf(day));
            holder.layoutDayCell.setClickable(true);

            boolean isSelected = (day == selectedDay && displayMonth == selectedMonth && displayYear == selectedYear);
            boolean isToday = (day == todayDay && displayMonth == todayMonth && displayYear == todayYear);

            if (isSelected) {
                holder.tvDayNumber.setBackgroundResource(R.drawable.bg_calendar_day_selected);
                holder.tvDayNumber.setTextColor(Color.WHITE);
            } else if (isToday) {
                holder.tvDayNumber.setBackgroundResource(R.drawable.bg_calendar_day_today);
                holder.tvDayNumber.setTextColor(ContextCompat.getColor(getContext(), R.color.green_mint));
            } else {
                holder.tvDayNumber.setBackground(null);
                holder.tvDayNumber.setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
            }

            holder.layoutDayCell.setOnClickListener(v -> {
                selectedDay = day;
                selectedMonth = displayMonth;
                selectedYear = displayYear;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                updatePreview();
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, displayYear);
            cal.set(Calendar.MONTH, displayMonth);
            cal.set(Calendar.DAY_OF_MONTH, 1);

            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            int offset = firstDayOfWeek - 1;
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            return (offset + daysInMonth <= 35) ? 35 : 42;
        }

        class DayViewHolder extends RecyclerView.ViewHolder {
            FrameLayout layoutDayCell;
            TextView tvDayNumber;

            DayViewHolder(@NonNull View itemView) {
                super(itemView);
                layoutDayCell = itemView.findViewById(R.id.layoutDayCell);
                tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            }
        }
    }
}
