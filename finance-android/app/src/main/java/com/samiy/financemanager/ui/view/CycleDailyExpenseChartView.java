package com.samiy.financemanager.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.samiy.financemanager.R;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CycleDailyExpenseChartView extends View {

    public static class DayExpensePoint implements Serializable {
        public int dayNumber;            // 1 to N
        public String dateStr;           // e.g. "06/11/2026"
        public String displayDate;       // e.g. "06 Nov"
        public double dailyExpense;      // Amount spent on this day
        public double cumulativeExpense; // Cumulative spend up to this day
        public double budgetBenchmark;   // Pro-rated budget benchmark
        public boolean isToday;
        public boolean isFuture;

        public DayExpensePoint(int dayNumber, String dateStr, String displayDate, double dailyExpense,
                               double cumulativeExpense, double budgetBenchmark, boolean isToday, boolean isFuture) {
            this.dayNumber = dayNumber;
            this.dateStr = dateStr;
            this.displayDate = displayDate;
            this.dailyExpense = dailyExpense;
            this.cumulativeExpense = cumulativeExpense;
            this.budgetBenchmark = budgetBenchmark;
            this.isToday = isToday;
            this.isFuture = isFuture;
        }
    }

    public interface OnDaySelectedListener {
        void onDaySelected(DayExpensePoint point);
    }

    private final List<DayExpensePoint> points = new ArrayList<>();
    private OnDaySelectedListener selectedListener;

    private int selectedIndex = -1;
    private boolean showDailyBars = true;
    private boolean showCumulativeCurve = true;
    private double totalCycleBudget = 0.0;

    // Paints
    private Paint paintDailyBar;
    private Paint paintDailyBarHigh;
    private Paint paintCumulativeStroke;
    private Paint paintCumulativeFill;
    private Paint paintBenchmarkLine;
    private Paint paintGridLine;
    private Paint paintGridText;
    private Paint paintAxisText;
    private Paint paintScrubberLine;
    private Paint paintScrubberPoint;
    private Paint paintTodayMarker;
    private Paint paintTooltipBg;
    private Paint paintTooltipText;
    private Paint paintTooltipSubtext;

    private float density = 1f;
    private final RectF rectTemp = new RectF();
    private final Path curvePath = new Path();
    private final Path fillPath = new Path();

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public CycleDailyExpenseChartView(Context context) {
        super(context);
        init();
    }

    public CycleDailyExpenseChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CycleDailyExpenseChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;

        Context ctx = getContext();

        paintDailyBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintDailyBar.setColor(ContextCompat.getColor(ctx, R.color.blue_sky));
        paintDailyBar.setStyle(Paint.Style.FILL);

        paintDailyBarHigh = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintDailyBarHigh.setColor(ContextCompat.getColor(ctx, R.color.red_rose));
        paintDailyBarHigh.setStyle(Paint.Style.FILL);

        paintCumulativeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCumulativeStroke.setColor(ContextCompat.getColor(ctx, R.color.colorPrimary));
        paintCumulativeStroke.setStyle(Paint.Style.STROKE);
        paintCumulativeStroke.setStrokeWidth(2.5f * density);
        paintCumulativeStroke.setStrokeCap(Paint.Cap.ROUND);
        paintCumulativeStroke.setStrokeJoin(Paint.Join.ROUND);

        paintCumulativeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCumulativeFill.setStyle(Paint.Style.FILL);

        paintBenchmarkLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBenchmarkLine.setColor(ContextCompat.getColor(ctx, R.color.amber_gold));
        paintBenchmarkLine.setStyle(Paint.Style.STROKE);
        paintBenchmarkLine.setStrokeWidth(1.2f * density);
        paintBenchmarkLine.setPathEffect(new DashPathEffect(new float[]{4f * density, 4f * density}, 0));

        paintGridLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGridLine.setColor(ContextCompat.getColor(ctx, R.color.border_dark));
        paintGridLine.setStyle(Paint.Style.STROKE);
        paintGridLine.setStrokeWidth(1f * density);
        paintGridLine.setPathEffect(new DashPathEffect(new float[]{3f * density, 3f * density}, 0));

        paintGridText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGridText.setColor(ContextCompat.getColor(ctx, R.color.text_muted));
        paintGridText.setTextSize(9f * density);
        paintGridText.setTextAlign(Paint.Align.RIGHT);

        paintAxisText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintAxisText.setColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        paintAxisText.setTextSize(9.5f * density);
        paintAxisText.setTextAlign(Paint.Align.CENTER);

        paintScrubberLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintScrubberLine.setColor(ContextCompat.getColor(ctx, R.color.colorPrimary));
        paintScrubberLine.setStyle(Paint.Style.STROKE);
        paintScrubberLine.setStrokeWidth(1.5f * density);

        paintScrubberPoint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintScrubberPoint.setColor(ContextCompat.getColor(ctx, R.color.colorPrimary));
        paintScrubberPoint.setStyle(Paint.Style.FILL);

        paintTodayMarker = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTodayMarker.setColor(ContextCompat.getColor(ctx, R.color.green_mint));
        paintTodayMarker.setStyle(Paint.Style.STROKE);
        paintTodayMarker.setStrokeWidth(1.2f * density);
        paintTodayMarker.setPathEffect(new DashPathEffect(new float[]{2f * density, 2f * density}, 0));

        paintTooltipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTooltipBg.setColor(ContextCompat.getColor(ctx, R.color.bg_card_secondary));
        paintTooltipBg.setStyle(Paint.Style.FILL);

        paintTooltipText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTooltipText.setColor(ContextCompat.getColor(ctx, R.color.text_primary));
        paintTooltipText.setTextSize(10f * density);
        paintTooltipText.setFakeBoldText(true);
        paintTooltipText.setTextAlign(Paint.Align.CENTER);

        paintTooltipSubtext = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTooltipSubtext.setColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        paintTooltipSubtext.setTextSize(8.5f * density);
        paintTooltipSubtext.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<DayExpensePoint> newPoints, double budget) {
        points.clear();
        this.totalCycleBudget = budget;
        if (newPoints != null) {
            points.addAll(newPoints);
        }

        // Set default selection to today or latest non-future point
        if (!points.isEmpty()) {
            int targetIdx = -1;
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).isToday) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx == -1) {
                for (int i = points.size() - 1; i >= 0; i--) {
                    if (!points.get(i).isFuture) {
                        targetIdx = i;
                        break;
                    }
                }
            }
            selectedIndex = (targetIdx != -1) ? targetIdx : Math.min(points.size() - 1, 0);
            if (selectedListener != null && selectedIndex >= 0) {
                selectedListener.onDaySelected(points.get(selectedIndex));
            }
        } else {
            selectedIndex = -1;
        }
        invalidate();
    }

    public void setDisplayModes(boolean showDaily, boolean showCumulative) {
        this.showDailyBars = showDaily;
        this.showCumulativeCurve = showCumulative;
        invalidate();
    }

    public void setOnDaySelectedListener(OnDaySelectedListener listener) {
        this.selectedListener = listener;
        if (selectedListener != null && selectedIndex >= 0 && selectedIndex < points.size()) {
            selectedListener.onDaySelected(points.get(selectedIndex));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) return super.onTouchEvent(event);

        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float paddingLeft = 44f * density;
            float paddingRight = 14f * density;
            float availableWidth = getWidth() - paddingLeft - paddingRight;

            float x = event.getX() - paddingLeft;
            if (x >= 0 && x <= availableWidth) {
                float slotWidth = availableWidth / (float) points.size();
                int touched = (int) (x / slotWidth);
                touched = Math.max(0, Math.min(points.size() - 1, touched));

                if (touched != selectedIndex) {
                    selectedIndex = touched;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (selectedListener != null) {
                        selectedListener.onDaySelected(points.get(selectedIndex));
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
        if (points.isEmpty()) return;

        float paddingLeft = 46f * density;
        float paddingRight = 14f * density;
        float paddingTop = 24f * density;
        float paddingBottom = 28f * density;

        float width = getWidth();
        float height = getHeight();

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;
        float baselineY = height - paddingBottom;

        // Compute max value for scaling
        double maxVal = totalCycleBudget > 0 ? totalCycleBudget : 10000;
        double maxDaily = 0.0;
        for (DayExpensePoint p : points) {
            if (p.cumulativeExpense > maxVal) maxVal = p.cumulativeExpense;
            if (p.dailyExpense > maxDaily) maxDaily = p.dailyExpense;
        }
        maxVal = Math.max(maxVal * 1.08, 1000); // 8% headroom

        // Draw 3 Horizontal Grid lines
        for (int i = 0; i <= 3; i++) {
            float y = baselineY - (plotHeight * i / 3f);
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paintGridLine);

            double gridAmount = (maxVal * i) / 3.0;
            String label;
            if (gridAmount >= 100000) {
                label = "₹" + String.format(Locale.US, "%.1fL", gridAmount / 100000.0);
            } else if (gridAmount >= 1000) {
                label = "₹" + String.format(Locale.US, "%.0fk", gridAmount / 1000.0);
            } else {
                label = "₹" + (int) gridAmount;
            }
            canvas.drawText(label, paddingLeft - 6f * density, y + 3f * density, paintGridText);
        }

        int count = points.size();
        float slotWidth = plotWidth / (float) count;

        // 1. Draw Daily Expense Bars
        if (showDailyBars) {
            float barMaxScale = (float) (maxDaily > 0 ? (plotHeight * 0.45f / maxDaily) : 1f);
            float barW = Math.max(2.5f * density, slotWidth * 0.5f);

            for (int i = 0; i < count; i++) {
                DayExpensePoint pt = points.get(i);
                if (pt.isFuture || pt.dailyExpense <= 0) continue;

                float cx = paddingLeft + i * slotWidth + slotWidth * 0.5f;
                float barH = (float) (pt.dailyExpense * barMaxScale);
                barH = Math.min(barH, plotHeight * 0.55f);

                rectTemp.set(cx - barW * 0.5f, baselineY - barH, cx + barW * 0.5f, baselineY);
                Paint barPaint = (pt.dailyExpense > (totalCycleBudget / count) * 1.5) ? paintDailyBarHigh : paintDailyBar;
                canvas.drawRoundRect(rectTemp, 3f * density, 3f * density, barPaint);
            }
        }

        // 2. Draw Cumulative Curve & Gradient Fill
        if (showCumulativeCurve) {
            curvePath.reset();
            fillPath.reset();

            int validCount = 0;
            for (DayExpensePoint pt : points) {
                if (!pt.isFuture) validCount++;
            }

            if (validCount > 0) {
                float firstX = paddingLeft + slotWidth * 0.5f;
                float firstY = (float) (baselineY - (points.get(0).cumulativeExpense / maxVal) * plotHeight);

                curvePath.moveTo(firstX, firstY);
                fillPath.moveTo(firstX, baselineY);
                fillPath.lineTo(firstX, firstY);

                float prevX = firstX;
                float prevY = firstY;

                for (int i = 1; i < validCount; i++) {
                    DayExpensePoint pt = points.get(i);
                    float curX = paddingLeft + i * slotWidth + slotWidth * 0.5f;
                    float curY = (float) (baselineY - (pt.cumulativeExpense / maxVal) * plotHeight);

                    // Smooth Bezier segment
                    float midX = (prevX + curX) / 2f;
                    curvePath.cubicTo(midX, prevY, midX, curY, curX, curY);
                    fillPath.cubicTo(midX, prevY, midX, curY, curX, curY);

                    prevX = curX;
                    prevY = curY;
                }

                fillPath.lineTo(prevX, baselineY);
                fillPath.close();

                // Shader for translucent glowing fill
                int startColor = Color.argb(65, 45, 212, 191); // Teal glowing alpha
                int endColor = Color.argb(5, 45, 212, 191);
                paintCumulativeFill.setShader(new LinearGradient(0, paddingTop, 0, baselineY, startColor, endColor, Shader.TileMode.CLAMP));

                canvas.drawPath(fillPath, paintCumulativeFill);
                canvas.drawPath(curvePath, paintCumulativeStroke);
            }
        }

        // 3. Draw Target Budget benchmark line (Pro-rated line across the cycle)
        if (totalCycleBudget > 0) {
            float bStartX = paddingLeft + slotWidth * 0.5f;
            float bStartY = baselineY;
            float bEndX = paddingLeft + (count - 1) * slotWidth + slotWidth * 0.5f;
            float bEndY = (float) (baselineY - (totalCycleBudget / maxVal) * plotHeight);
            canvas.drawLine(bStartX, bStartY, bEndX, bEndY, paintBenchmarkLine);
        }

        // 4. Draw X-axis date milestones (e.g. Day 1, 5, 10, 15, 20, 25, 30)
        for (int i = 0; i < count; i++) {
            DayExpensePoint pt = points.get(i);
            boolean shouldLabel = (i == 0) || (i == count - 1) || (i % 5 == 4);
            float cx = paddingLeft + i * slotWidth + slotWidth * 0.5f;

            if (pt.isToday) {
                // Today marker line
                canvas.drawLine(cx, paddingTop, cx, baselineY, paintTodayMarker);
            }

            if (shouldLabel) {
                String dayLabel = "D" + pt.dayNumber;
                canvas.drawText(dayLabel, cx, baselineY + 14f * density, paintAxisText);
                String dtPart = pt.displayDate.length() > 5 ? pt.displayDate.substring(0, 6) : pt.displayDate;
                canvas.drawText(dtPart, cx, baselineY + 23f * density, paintGridText);
            }
        }

        // 5. Draw Interactive Scrubber Cursor
        if (selectedIndex >= 0 && selectedIndex < count) {
            DayExpensePoint sel = points.get(selectedIndex);
            float selX = paddingLeft + selectedIndex * slotWidth + slotWidth * 0.5f;

            // Vertical cursor
            canvas.drawLine(selX, paddingTop, selX, baselineY, paintScrubberLine);

            // Point on curve
            float selY = (float) (baselineY - (sel.cumulativeExpense / maxVal) * plotHeight);
            canvas.drawCircle(selX, selY, 4.5f * density, paintScrubberPoint);

            // Floating Tooltip Badge
            String text = "Day " + sel.dayNumber + " (" + sel.displayDate + "): " + formatCompact(sel.dailyExpense);
            String subtext = "Cum: " + formatCompact(sel.cumulativeExpense);

            float tw = Math.max(paintTooltipText.measureText(text), paintTooltipSubtext.measureText(subtext)) + 16f * density;
            float th = 26f * density;

            float ttLeft = Math.max(paddingLeft, Math.min(width - paddingRight - tw, selX - tw * 0.5f));
            float ttTop = Math.max(2f * density, selY - th - 8f * density);

            rectTemp.set(ttLeft, ttTop, ttLeft + tw, ttTop + th);
            canvas.drawRoundRect(rectTemp, 8f * density, 8f * density, paintTooltipBg);
            canvas.drawRoundRect(rectTemp, 8f * density, 8f * density, paintScrubberLine);

            canvas.drawText(text, rectTemp.centerX(), ttTop + 11.5f * density, paintTooltipText);
            canvas.drawText(subtext, rectTemp.centerX(), ttTop + 21f * density, paintTooltipSubtext);
        }
    }

    private String formatCompact(double amount) {
        if (amount == 0) return "₹0";
        if (amount >= 100000) return String.format(Locale.US, "₹%.2fL", amount / 100000.0);
        return "₹" + currencyFormat.format(amount).replace("INR", "").trim();
    }
}
