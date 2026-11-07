package com.samiy.financemanager.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CycleChartView extends View {

    public static class CycleBarItem implements Serializable {
        public String label;         // e.g. "Oct", "Nov"
        public String fullCycle;     // e.g. "November 2026"
        public double budget;        // Allocated budget
        public double expense;       // Spent
        public double investment;    // Investments
        public int year;
        public int monthNumber;
        public boolean isActiveCycle;

        public CycleBarItem(String label, String fullCycle, double budget, double expense, double investment, int year, int monthNumber, boolean isActiveCycle) {
            this.label = label;
            this.fullCycle = fullCycle;
            this.budget = budget;
            this.expense = expense;
            this.investment = investment;
            this.year = year;
            this.monthNumber = monthNumber;
            this.isActiveCycle = isActiveCycle;
        }
    }

    public interface OnCycleSelectedListener {
        void onCycleSelected(CycleBarItem item);
    }

    private final List<CycleBarItem> items = new ArrayList<>();
    private OnCycleSelectedListener selectedListener;

    private int selectedIndex = -1;
    private boolean showBudget = true;
    private boolean showExpense = true;
    private boolean showInvestment = true;

    // Paints
    private Paint paintBudget;
    private Paint paintExpense;
    private Paint paintInvestment;
    private Paint paintGrid;
    private Paint paintGridLine;
    private Paint paintAxisText;
    private Paint paintSelectedText;
    private Paint paintHighlightBg;
    private Paint paintHighlightBorder;
    private Paint paintActivePip;

    private float density = 1f;
    private final RectF rectTemp = new RectF();

    private final NumberFormat compactCurrencyFormat = NumberFormat.getNumberInstance(Locale.US);

    public CycleChartView(Context context) {
        super(context);
        init();
    }

    public CycleChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CycleChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;

        paintBudget = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBudget.setColor(Color.parseColor("#38BDF8")); // Sky Blue
        paintBudget.setStyle(Paint.Style.FILL);

        paintExpense = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintExpense.setColor(Color.parseColor("#F43F5E")); // Rose Red
        paintExpense.setStyle(Paint.Style.FILL);

        paintInvestment = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintInvestment.setColor(Color.parseColor("#F59E0B")); // Amber Gold
        paintInvestment.setStyle(Paint.Style.FILL);

        paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGrid.setColor(Color.parseColor("#64748B"));
        paintGrid.setTextSize(9f * density);
        paintGrid.setTextAlign(Paint.Align.RIGHT);

        paintGridLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGridLine.setColor(Color.parseColor("#1E293B"));
        paintGridLine.setStyle(Paint.Style.STROKE);
        paintGridLine.setStrokeWidth(1f * density);
        paintGridLine.setPathEffect(new DashPathEffect(new float[]{4f * density, 4f * density}, 0));

        paintAxisText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAxisText.setColor(Color.parseColor("#94A3B8"));
        paintAxisText.setTextSize(10f * density);
        paintAxisText.setTextAlign(Paint.Align.CENTER);

        paintSelectedText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSelectedText.setColor(Color.parseColor("#FFFFFF"));
        paintSelectedText.setTextSize(10.5f * density);
        paintSelectedText.setFakeBoldText(true);
        paintSelectedText.setTextAlign(Paint.Align.CENTER);

        paintHighlightBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHighlightBg.setColor(Color.parseColor("#1E293B"));
        paintHighlightBg.setStyle(Paint.Style.FILL);

        paintHighlightBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintHighlightBorder.setColor(Color.parseColor("#38BDF8"));
        paintHighlightBorder.setStyle(Paint.Style.STROKE);
        paintHighlightBorder.setStrokeWidth(1.2f * density);

        paintActivePip = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintActivePip.setColor(Color.parseColor("#10B981")); // Emerald Pip
        paintActivePip.setStyle(Paint.Style.FILL);
    }

    public void setData(List<CycleBarItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        if (!items.isEmpty()) {
            // Select the last/active item by default
            selectedIndex = items.size() - 1;
            if (selectedListener != null) {
                selectedListener.onCycleSelected(items.get(selectedIndex));
            }
        } else {
            selectedIndex = -1;
        }
        invalidate();
    }

    public void setSeriesVisibility(boolean budget, boolean expense, boolean investment) {
        this.showBudget = budget;
        this.showExpense = expense;
        this.showInvestment = investment;
        invalidate();
    }

    public void setOnCycleSelectedListener(OnCycleSelectedListener listener) {
        this.selectedListener = listener;
        if (selectedListener != null && selectedIndex >= 0 && selectedIndex < items.size()) {
            selectedListener.onCycleSelected(items.get(selectedIndex));
        }
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < items.size()) {
            this.selectedIndex = index;
            if (selectedListener != null) {
                selectedListener.onCycleSelected(items.get(selectedIndex));
            }
            invalidate();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (items.isEmpty()) return super.onTouchEvent(event);

        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float paddingLeft = 40f * density;
            float paddingRight = 12f * density;
            float availableWidth = getWidth() - paddingLeft - paddingRight;

            float x = event.getX() - paddingLeft;
            if (x >= 0 && x <= availableWidth) {
                float slotWidth = availableWidth / items.size();
                int touchedIndex = (int) (x / slotWidth);
                touchedIndex = Math.max(0, Math.min(items.size() - 1, touchedIndex));

                if (touchedIndex != selectedIndex) {
                    selectedIndex = touchedIndex;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (selectedListener != null) {
                        selectedListener.onCycleSelected(items.get(selectedIndex));
                    }
                    invalidate();
                }
                return true;
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (items.isEmpty()) return;

        float paddingLeft = 40f * density;
        float paddingRight = 12f * density;
        float paddingTop = 18f * density;
        float paddingBottom = 26f * density;

        float width = getWidth();
        float height = getHeight();

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;
        float baselineY = height - paddingBottom;

        // Calculate maximum value across active series
        double maxVal = 0.0;
        for (CycleBarItem item : items) {
            if (showBudget && item.budget > maxVal) maxVal = item.budget;
            if (showExpense && item.expense > maxVal) maxVal = item.expense;
            if (showInvestment && item.investment > maxVal) maxVal = item.investment;
        }

        if (maxVal <= 0) maxVal = 50000.0;
        maxVal = maxVal * 1.15; // 15% headroom for clean aesthetics

        // 1. Draw horizontal gridlines & Y-axis labels
        int gridSteps = 3;
        for (int i = 0; i <= gridSteps; i++) {
            float fraction = (float) i / gridSteps;
            float lineY = baselineY - (fraction * plotHeight);
            double val = fraction * maxVal;

            if (i > 0) {
                canvas.drawLine(paddingLeft, lineY, width - paddingRight, lineY, paintGridLine);
            } else {
                // Baseline solid line
                canvas.drawLine(paddingLeft, lineY, width - paddingRight, lineY, paintHighlightBorder);
            }

            // Format Y-axis label (e.g. ₹0, ₹25k, ₹50k, ₹75k)
            String label;
            if (val >= 1000) {
                label = "₹" + Math.round(val / 1000.0) + "k";
            } else {
                label = "₹" + Math.round(val);
            }
            canvas.drawText(label, paddingLeft - (6f * density), lineY + (3.5f * density), paintGrid);
        }

        // 2. Determine active series count per item
        int activeSeriesCount = 0;
        if (showBudget) activeSeriesCount++;
        if (showExpense) activeSeriesCount++;
        if (showInvestment) activeSeriesCount++;

        if (activeSeriesCount == 0) return;

        float slotWidth = plotWidth / items.size();
        float barGap = 2f * density;
        float barWidth = Math.max(3f * density, Math.min(10f * density, (slotWidth * 0.75f - ((activeSeriesCount - 1) * barGap)) / activeSeriesCount));
        float totalGroupWidth = (barWidth * activeSeriesCount) + ((activeSeriesCount - 1) * barGap);

        // 3. Render items
        for (int i = 0; i < items.size(); i++) {
            CycleBarItem item = items.get(i);
            float slotCenterX = paddingLeft + (i * slotWidth) + (slotWidth / 2f);

            // Selection Highlight Pillar
            if (i == selectedIndex) {
                rectTemp.set(slotCenterX - (slotWidth * 0.46f), paddingTop, slotCenterX + (slotWidth * 0.46f), baselineY);
                canvas.drawRoundRect(rectTemp, 8f * density, 8f * density, paintHighlightBg);
                canvas.drawRoundRect(rectTemp, 8f * density, 8f * density, paintHighlightBorder);

                // Indicator pip on top
                if (item.isActiveCycle) {
                    canvas.drawCircle(slotCenterX, paddingTop + 6f * density, 3f * density, paintActivePip);
                }
            }

            // Draw bars side by side inside slot
            float startBarX = slotCenterX - (totalGroupWidth / 2f);
            float currentBarX = startBarX;

            // Budget bar
            if (showBudget) {
                float barHeight = (float) ((item.budget / maxVal) * plotHeight);
                float topY = baselineY - barHeight;
                rectTemp.set(currentBarX, topY, currentBarX + barWidth, baselineY);
                canvas.drawRoundRect(rectTemp, 4f * density, 4f * density, paintBudget);
                currentBarX += barWidth + barGap;
            }

            // Expense bar
            if (showExpense) {
                float barHeight = (float) ((item.expense / maxVal) * plotHeight);
                float topY = baselineY - barHeight;
                rectTemp.set(currentBarX, topY, currentBarX + barWidth, baselineY);
                canvas.drawRoundRect(rectTemp, 4f * density, 4f * density, paintExpense);
                currentBarX += barWidth + barGap;
            }

            // Investment bar
            if (showInvestment) {
                float barHeight = (float) ((item.investment / maxVal) * plotHeight);
                float topY = baselineY - barHeight;
                rectTemp.set(currentBarX, topY, currentBarX + barWidth, baselineY);
                canvas.drawRoundRect(rectTemp, 4f * density, 4f * density, paintInvestment);
                currentBarX += barWidth + barGap;
            }

            // X-axis cycle label
            Paint textPaintToUse = (i == selectedIndex) ? paintSelectedText : paintAxisText;
            canvas.drawText(item.label, slotCenterX, baselineY + (16f * density), textPaintToUse);
        }
    }
}
