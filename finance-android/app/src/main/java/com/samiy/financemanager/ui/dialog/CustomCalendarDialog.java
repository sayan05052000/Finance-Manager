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
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    private Calendar minDate;
    private Calendar maxDate;

    public void setMinDate(Calendar min) {
        if (min != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(min.getTime());
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            this.minDate = cal;
            clampDates();
        } else {
            this.minDate = null;
        }
    }

    public void setMaxDate(Calendar max) {
        if (max != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(max.getTime());
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
            this.maxDate = cal;
            clampDates();
        } else {
            this.maxDate = null;
        }
    }

    private void clampDates() {
        Calendar checkCal = Calendar.getInstance();
        checkCal.set(selectedYear, selectedMonth, selectedDay, 12, 0, 0);
        if (minDate != null && checkCal.before(minDate)) {
            selectedYear = minDate.get(Calendar.YEAR);
            selectedMonth = minDate.get(Calendar.MONTH);
            selectedDay = minDate.get(Calendar.DAY_OF_MONTH);
            displayYear = selectedYear;
            displayMonth = selectedMonth;
        }
        if (maxDate != null && checkCal.after(maxDate)) {
            selectedYear = maxDate.get(Calendar.YEAR);
            selectedMonth = maxDate.get(Calendar.MONTH);
            selectedDay = maxDate.get(Calendar.DAY_OF_MONTH);
            displayYear = selectedYear;
            displayMonth = selectedMonth;
        }
        if (rvCalendarGrid != null) {
            updateCalendarGrid();
        }
    }

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
                    if (d >= 1 && d <= 31 && m >= 0 && m <= 11 && y >= 1900 && y <= 2100) {
                        selectedDay = d;
                        selectedMonth = m;
                        selectedYear = y;
                    }
                } catch (Exception ignored) {}
            }
        }

        displayYear = selectedYear;
        displayMonth = selectedMonth;
        clampDates();
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

        View btnMonthYearContainer = findViewById(R.id.btnMonthYearContainer);
        if (btnMonthYearContainer != null) {
            btnMonthYearContainer.setOnClickListener(v -> showMonthYearPickerDialog());
        }
        if (tvMonthYear != null) {
            tvMonthYear.setOnClickListener(v -> showMonthYearPickerDialog());
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
                Calendar checkCal = Calendar.getInstance();
                checkCal.set(selectedYear, selectedMonth, selectedDay, 12, 0, 0);

                if (maxDate != null && checkCal.after(maxDate)) {
                    android.widget.Toast.makeText(getContext(), "Selected date cannot be in the future", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                if (minDate != null && checkCal.before(minDate)) {
                    android.widget.Toast.makeText(getContext(), "Selected date cannot be in the past", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

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

    private void showMonthYearPickerDialog() {
        Dialog pickerDialog = new Dialog(getContext());
        pickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        pickerDialog.setContentView(R.layout.dialog_select_month_year);

        Window window = pickerDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.92);
            window.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        final int[] tempMonth = {displayMonth};
        final int[] tempYear = {displayYear};

        // Month buttons
        TextView[] monthViews = new TextView[12];
        int[] monthIds = {
                R.id.btnMonth0, R.id.btnMonth1, R.id.btnMonth2, R.id.btnMonth3,
                R.id.btnMonth4, R.id.btnMonth5, R.id.btnMonth6, R.id.btnMonth7,
                R.id.btnMonth8, R.id.btnMonth9, R.id.btnMonth10, R.id.btnMonth11
        };

        Runnable updateMonthUI = () -> {
            for (int i = 0; i < 12; i++) {
                if (monthViews[i] != null) {
                    if (i == tempMonth[0]) {
                        monthViews[i].setBackgroundResource(R.drawable.bg_pill_emerald);
                        monthViews[i].setTextColor(Color.parseColor("#0B0F19"));
                    } else {
                        monthViews[i].setBackgroundResource(R.drawable.bg_pill_dark);
                        monthViews[i].setTextColor(ContextCompat.getColor(getContext(), R.color.text_primary));
                    }
                }
            }
        };

        for (int i = 0; i < 12; i++) {
            final int mIndex = i;
            monthViews[i] = pickerDialog.findViewById(monthIds[i]);
            if (monthViews[i] != null) {
                monthViews[i].setOnClickListener(v -> {
                    tempMonth[0] = mIndex;
                    updateMonthUI.run();
                });
            }
        }
        updateMonthUI.run();

        // Year Grid & Badge
        TextView tvBadge = pickerDialog.findViewById(R.id.tvCurrentYearSelectedBadge);
        if (tvBadge != null) tvBadge.setText(String.valueOf(tempYear[0]));

        RecyclerView rvYearGrid = pickerDialog.findViewById(R.id.rvYearGrid);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 4);
        rvYearGrid.setLayoutManager(gridLayoutManager);

        final int START_YEAR = 1940;
        final int END_YEAR = 2070;
        final int YEAR_COUNT = END_YEAR - START_YEAR + 1; // 131 years

        final YearAdapter[] yearAdapterRef = new YearAdapter[1];

        // Decade Quick Jump (2 Rows x 4 Chips = 8 decades: 1990s, 2000s, 2010s, 2020s, 2025s, 2030s, 2040s, 2050s)
        LinearLayout layoutDecadeChips = pickerDialog.findViewById(R.id.layoutDecadeChips);
        int[][] decadeRows = {
                {1990, 2000, 2010, 2020},
                {2025, 2030, 2040, 2050}
        };
        if (layoutDecadeChips != null) {
            layoutDecadeChips.removeAllViews();
            for (int r = 0; r < decadeRows.length; r++) {
                LinearLayout rowLayout = new LinearLayout(getContext());
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (r > 0) rowLp.topMargin = 12;
                rowLayout.setLayoutParams(rowLp);

                for (int dec : decadeRows[r]) {
                    TextView chip = new TextView(getContext());
                    chip.setText(dec + "s");
                    chip.setTextSize(11f);
                    chip.setTypeface(null, android.graphics.Typeface.BOLD);
                    chip.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                    chip.setBackgroundResource(R.drawable.bg_pill_dark);
                    chip.setGravity(android.view.Gravity.CENTER);
                    chip.setPadding(12, 14, 12, 14);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                    lp.setMargins(6, 0, 6, 0);
                    chip.setLayoutParams(lp);

                    chip.setOnClickListener(v -> {
                        int targetPos = Math.max(0, dec - START_YEAR);
                        gridLayoutManager.scrollToPositionWithOffset(targetPos, 0);
                    });
                    rowLayout.addView(chip);
                }
                layoutDecadeChips.addView(rowLayout);
            }
        }

        yearAdapterRef[0] = new YearAdapter(START_YEAR, YEAR_COUNT, tempYear, (selectedY) -> {
            tempYear[0] = selectedY;
            if (tvBadge != null) tvBadge.setText(String.valueOf(selectedY));
            if (yearAdapterRef[0] != null) yearAdapterRef[0].notifyDataSetChanged();
        });
        rvYearGrid.setAdapter(yearAdapterRef[0]);

        // Auto-scroll to currently selected year
        int initialPos = Math.max(0, tempYear[0] - START_YEAR - 4);
        rvYearGrid.post(() -> gridLayoutManager.scrollToPositionWithOffset(initialPos, 0));

        // Buttons
        View tvClose = pickerDialog.findViewById(R.id.tvCloseMonthYear);
        View btnCancel = pickerDialog.findViewById(R.id.btnCancelMonthYear);
        View btnApply = pickerDialog.findViewById(R.id.btnApplyMonthYear);

        if (tvClose != null) tvClose.setOnClickListener(v -> pickerDialog.dismiss());
        if (btnCancel != null) btnCancel.setOnClickListener(v -> pickerDialog.dismiss());

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                displayMonth = tempMonth[0];
                displayYear = tempYear[0];
                updateCalendarGrid();
                pickerDialog.dismiss();
            });
        }

        pickerDialog.show();
    }

    private class YearAdapter extends RecyclerView.Adapter<YearAdapter.YearViewHolder> {
        private final int startYear;
        private final int count;
        private final int[] selectedYearRef;
        private final OnYearClickListener onYearClickListener;

        interface OnYearClickListener {
            void onYearClick(int year);
        }

        YearAdapter(int startYear, int count, int[] selectedYearRef, OnYearClickListener listener) {
            this.startYear = startYear;
            this.count = count;
            this.selectedYearRef = selectedYearRef;
            this.onYearClickListener = listener;
        }

        @NonNull
        @Override
        public YearViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_year, parent, false);
            return new YearViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull YearViewHolder holder, int position) {
            int year = startYear + position;
            holder.tvYear.setText(String.valueOf(year));

            if (year == selectedYearRef[0]) {
                holder.tvYear.setBackgroundResource(R.drawable.bg_pill_emerald);
                holder.tvYear.setTextColor(Color.parseColor("#0B0F19"));
            } else {
                holder.tvYear.setBackgroundResource(R.drawable.bg_pill_dark);
                holder.tvYear.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
            }

            holder.itemView.setOnClickListener(v -> {
                if (onYearClickListener != null) {
                    onYearClickListener.onYearClick(year);
                }
            });
        }

        @Override
        public int getItemCount() {
            return count;
        }

        class YearViewHolder extends RecyclerView.ViewHolder {
            TextView tvYear;

            YearViewHolder(@NonNull View itemView) {
                super(itemView);
                tvYear = itemView.findViewById(R.id.tvYearItem);
            }
        }
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

            boolean isBeforeMin = false;
            if (minDate != null) {
                Calendar cellCal = Calendar.getInstance();
                cellCal.set(displayYear, displayMonth, day, 23, 59, 59);
                if (cellCal.before(minDate)) {
                    isBeforeMin = true;
                }
            }

            boolean isAfterMax = false;
            if (maxDate != null) {
                Calendar cellCal = Calendar.getInstance();
                cellCal.set(displayYear, displayMonth, day, 0, 0, 0);
                if (cellCal.after(maxDate)) {
                    isAfterMax = true;
                }
            }

            boolean isDisabled = isBeforeMin || isAfterMax;

            if (isDisabled) {
                holder.tvDayNumber.setBackground(null);
                holder.tvDayNumber.setTextColor(Color.parseColor("#475569"));
                final boolean fAfterMax = isAfterMax;
                holder.layoutDayCell.setOnClickListener(v -> {
                    if (fAfterMax) {
                        android.widget.Toast.makeText(getContext(), "Cannot select future date", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        android.widget.Toast.makeText(getContext(), "Cannot select past date for Due Date", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
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
