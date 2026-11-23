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

public class HoldingsGrowthChartView extends View {

    public static class GrowthDataPoint implements Serializable {
        public long timestamp;
        public String dateLabel;     // e.g. "Jan '26"
        public String fullDate;      // e.g. "15/01/2026"
        public double invested;      // Cumulative capital invested
        public double valuation;     // Portfolio market valuation
        public double returns;       // Returns / PnL (valuation - invested)
        public double returnsPct;    // Return ROI %

        public GrowthDataPoint(long timestamp, String dateLabel, String fullDate, double invested, double valuation) {
            this.timestamp = timestamp;
            this.dateLabel = dateLabel;
            this.fullDate = fullDate;
            this.invested = invested;
            this.valuation = valuation;
            this.returns = valuation - invested;
            this.returnsPct = invested > 0 ? (this.returns / invested) * 100.0 : 0.0;
        }
    }

    public interface OnGrowthPointSelectedListener {
        void onPointSelected(GrowthDataPoint point);
    }

    private final List<GrowthDataPoint> points = new ArrayList<>();
    private OnGrowthPointSelectedListener selectedListener;

    private int selectedIndex = -1;

    // Paints
    private Paint paintValuationStroke;
    private Paint paintValuationFill;
    private Paint paintInvestedStroke;
    private Paint paintGridLine;
    private Paint paintGridText;
    private Paint paintAxisText;
    private Paint paintScrubberLine;
    private Paint paintScrubberPointVal;
    private Paint paintScrubberPointInv;
    private Paint paintTooltipBg;
    private Paint paintTooltipText;
    private Paint paintTooltipSubtext;

    private float density = 1f;
    private final RectF rectTemp = new RectF();
    private final Path valCurvePath = new Path();
    private final Path valFillPath = new Path();
    private final Path invCurvePath = new Path();

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public HoldingsGrowthChartView(Context context) {
        super(context);
        init();
    }

    public HoldingsGrowthChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HoldingsGrowthChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        density = dm.density;

        Context ctx = getContext();

        paintValuationStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintValuationStroke.setColor(ContextCompat.getColor(ctx, R.color.green_mint));
        paintValuationStroke.setStyle(Paint.Style.STROKE);
        paintValuationStroke.setStrokeWidth(2.8f * density);
        paintValuationStroke.setStrokeCap(Paint.Cap.ROUND);
        paintValuationStroke.setStrokeJoin(Paint.Join.ROUND);

        paintValuationFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintValuationFill.setStyle(Paint.Style.FILL);

        paintInvestedStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintInvestedStroke.setColor(ContextCompat.getColor(ctx, R.color.blue_sky));
        paintInvestedStroke.setStyle(Paint.Style.STROKE);
        paintInvestedStroke.setStrokeWidth(1.8f * density);
        paintInvestedStroke.setPathEffect(new DashPathEffect(new float[]{4f * density, 4f * density}, 0));

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
        paintScrubberLine.setColor(ContextCompat.getColor(ctx, R.color.green_mint));
        paintScrubberLine.setStyle(Paint.Style.STROKE);
        paintScrubberLine.setStrokeWidth(1.5f * density);

        paintScrubberPointVal = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintScrubberPointVal.setColor(ContextCompat.getColor(ctx, R.color.green_mint));
        paintScrubberPointVal.setStyle(Paint.Style.FILL);

        paintScrubberPointInv = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintScrubberPointInv.setColor(ContextCompat.getColor(ctx, R.color.blue_sky));
        paintScrubberPointInv.setStyle(Paint.Style.FILL);

        paintTooltipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTooltipBg.setColor(ContextCompat.getColor(ctx, R.color.bg_card_secondary));
        paintTooltipBg.setStyle(Paint.Style.FILL);

        paintTooltipText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTooltipText.setColor(ContextCompat.getColor(ctx, R.color.text_primary));
        paintTooltipText.setTextSize(10f * density);
        paintTooltipText.setFakeBoldText(true);
        paintTooltipText.setTextAlign(Paint.Align.CENTER);

        paintTooltipSubtext = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTooltipSubtext.setColor(ContextCompat.getColor(ctx, R.color.green_mint));
        paintTooltipSubtext.setTextSize(9f * density);
        paintTooltipSubtext.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<GrowthDataPoint> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }

        selectedIndex = points.isEmpty() ? -1 : (points.size() - 1);
        if (selectedListener != null && selectedIndex >= 0) {
            selectedListener.onPointSelected(points.get(selectedIndex));
        }
        invalidate();
    }

    public void setOnGrowthPointSelectedListener(OnGrowthPointSelectedListener listener) {
        this.selectedListener = listener;
        if (selectedListener != null && selectedIndex >= 0 && selectedIndex < points.size()) {
            selectedListener.onPointSelected(points.get(selectedIndex));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) return super.onTouchEvent(event);

        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float paddingLeft = 46f * density;
            float paddingRight = 14f * density;
            float availableWidth = getWidth() - paddingLeft - paddingRight;

            float x = event.getX() - paddingLeft;
            if (x >= 0 && x <= availableWidth) {
                float slotWidth = availableWidth / (float) Math.max(1, points.size() - 1);
                int touched = Math.round(x / slotWidth);
                touched = Math.max(0, Math.min(points.size() - 1, touched));

                if (touched != selectedIndex) {
                    selectedIndex = touched;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (selectedListener != null) {
                        selectedListener.onPointSelected(points.get(selectedIndex));
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

        float paddingLeft = 48f * density;
        float paddingRight = 16f * density;
        float paddingTop = 24f * density;
        float paddingBottom = 28f * density;

        float width = getWidth();
        float height = getHeight();

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;
        float baselineY = height - paddingBottom;

        double maxVal = 1000;
        double minVal = Double.MAX_VALUE;
        for (GrowthDataPoint p : points) {
            if (p.valuation > maxVal) maxVal = p.valuation;
            if (p.invested > maxVal) maxVal = p.invested;
            if (p.valuation < minVal) minVal = p.valuation;
            if (p.invested < minVal) minVal = p.invested;
        }
        if (minVal == Double.MAX_VALUE) minVal = 0;
        minVal = Math.max(0, minVal * 0.85); // 15% bottom baseline headroom
        maxVal = maxVal * 1.08;              // 8% top headroom
        double valueSpan = Math.max(100.0, maxVal - minVal);

        // Draw 3 Horizontal Grid lines
        for (int i = 0; i <= 3; i++) {
            float y = baselineY - (plotHeight * i / 3f);
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, paintGridLine);

            double gridAmount = minVal + (valueSpan * i / 3.0);
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
        float stepX = plotWidth / (float) Math.max(1, count - 1);

        // 1. Draw Invested Line (Dashed Blue Sky)
        invCurvePath.reset();
        for (int i = 0; i < count; i++) {
            GrowthDataPoint pt = points.get(i);
            float cx = paddingLeft + i * stepX;
            float cy = (float) (baselineY - ((pt.invested - minVal) / valueSpan) * plotHeight);
            if (i == 0) {
                invCurvePath.moveTo(cx, cy);
            } else {
                invCurvePath.lineTo(cx, cy);
            }
        }
        canvas.drawPath(invCurvePath, paintInvestedStroke);

        // 2. Draw Valuation Curve (Solid Mint Green with glowing gradient fill)
        valCurvePath.reset();
        valFillPath.reset();

        float firstX = paddingLeft;
        float firstY = (float) (baselineY - ((points.get(0).valuation - minVal) / valueSpan) * plotHeight);

        valCurvePath.moveTo(firstX, firstY);
        valFillPath.moveTo(firstX, baselineY);
        valFillPath.lineTo(firstX, firstY);

        float prevX = firstX;
        float prevY = firstY;

        for (int i = 1; i < count; i++) {
            GrowthDataPoint pt = points.get(i);
            float cx = paddingLeft + i * stepX;
            float cy = (float) (baselineY - ((pt.valuation - minVal) / valueSpan) * plotHeight);

            float midX = (prevX + cx) / 2f;
            valCurvePath.cubicTo(midX, prevY, midX, cy, cx, cy);
            valFillPath.cubicTo(midX, prevY, midX, cy, cx, cy);

            prevX = cx;
            prevY = cy;
        }

        valFillPath.lineTo(prevX, baselineY);
        valFillPath.close();

        // Shading for acrylic glowing fill
        int startFill = Color.argb(60, 45, 212, 191);
        int endFill = Color.argb(4, 45, 212, 191);
        paintValuationFill.setShader(new LinearGradient(0, paddingTop, 0, baselineY, startFill, endFill, Shader.TileMode.CLAMP));

        canvas.drawPath(valFillPath, paintValuationFill);
        canvas.drawPath(valCurvePath, paintValuationStroke);

        // 3. Draw X-axis milestone labels
        int labelInterval = Math.max(1, count / 5);
        for (int i = 0; i < count; i++) {
            boolean shouldLabel = (i == 0) || (i == count - 1) || (i % labelInterval == 0);
            if (shouldLabel) {
                GrowthDataPoint pt = points.get(i);
                float cx = paddingLeft + i * stepX;
                canvas.drawText(pt.dateLabel, cx, baselineY + 16f * density, paintAxisText);
            }
        }

        // 4. Draw Interactive Scrubber Cursor & Floating Tooltip
        if (selectedIndex >= 0 && selectedIndex < count) {
            GrowthDataPoint sel = points.get(selectedIndex);
            float selX = paddingLeft + selectedIndex * stepX;

            // Vertical cursor line
            canvas.drawLine(selX, paddingTop, selX, baselineY, paintScrubberLine);

            // Points on curves
            float selYVal = (float) (baselineY - ((sel.valuation - minVal) / valueSpan) * plotHeight);
            float selYInv = (float) (baselineY - ((sel.invested - minVal) / valueSpan) * plotHeight);

            canvas.drawCircle(selX, selYInv, 4f * density, paintScrubberPointInv);
            canvas.drawCircle(selX, selYVal, 5.5f * density, paintScrubberPointVal);

            // Floating Tooltip
            String line1 = sel.fullDate + " • " + formatCompact(sel.valuation);
            String sign = sel.returns >= 0 ? "+" : "-";
            String line2 = "Inv: " + formatCompact(sel.invested) + " • " + sign + formatCompact(Math.abs(sel.returns)) + " (" + String.format(Locale.US, "%.1f%%", sel.returnsPct) + ")";

            float tw = Math.max(paintTooltipText.measureText(line1), paintTooltipSubtext.measureText(line2)) + 18f * density;
            float th = 27f * density;

            float ttLeft = Math.max(paddingLeft, Math.min(width - paddingRight - tw, selX - tw * 0.5f));
            float ttTop = Math.max(2f * density, Math.min(selYVal, selYInv) - th - 10f * density);

            rectTemp.set(ttLeft, ttTop, ttLeft + tw, ttTop + th);
            canvas.drawRoundRect(rectTemp, 8f * density, 8f * density, paintTooltipBg);
            canvas.drawRoundRect(rectTemp, 8f * density, 8f * density, paintScrubberLine);

            paintTooltipSubtext.setColor(ContextCompat.getColor(getContext(), sel.returns >= 0 ? R.color.green_mint : R.color.red_rose));

            canvas.drawText(line1, rectTemp.centerX(), ttTop + 12f * density, paintTooltipText);
            canvas.drawText(line2, rectTemp.centerX(), ttTop + 22f * density, paintTooltipSubtext);
        }
    }

    private String formatCompact(double amount) {
        if (amount == 0) return "₹0";
        if (amount >= 100000) return String.format(Locale.US, "₹%.2fL", amount / 100000.0);
        return "₹" + currencyFormat.format(amount).replace("INR", "").trim();
    }
}
