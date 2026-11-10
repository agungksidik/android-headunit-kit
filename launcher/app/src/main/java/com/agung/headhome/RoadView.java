package com.agung.headhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Background of the center column: two road edges converging to a vanishing point, a dashed center line
 * that scrolls toward the viewer in proportion to the speed, and a soft glow under the car.
 * Geometry follows a 600x560 dp box anchored to the bottom center of the view (above the bottom padding).
 */
public class RoadView extends View {

    private static final float BOX_W = 600f;
    private static final float BOX_H = 560f;
    private static final float EDGE_BOTTOM_DX = 150f;
    private static final float EDGE_TOP_DX = 30f;
    private static final float DASH = 34f;
    private static final float GAP = 26f;
    private static final float PERIOD = DASH + GAP;
    private static final float STROKE = 3f;
    private static final float MIN_PERIOD_SEC = 0.15f;
    private static final long SKEW_MS = 600;
    private static final float MAX_SKEW = 0.25f;

    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;

    private int speedKmh;
    private float phase;
    private long lastFrameNs;
    private float skew;
    private ValueAnimator skewAnimator;

    public RoadView(Context context) {
        this(context, null);
    }

    public RoadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);
        edgePaint.setColor(context.getColor(R.color.road_line));
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setColor(context.getColor(R.color.road_dash));
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void setSpeedKmh(int kmh) {
        int v = Math.max(0, kmh);
        if (v == speedKmh) {
            return;
        }
        speedKmh = v;
        lastFrameNs = 0;
        invalidate();
    }

    /** Skews the road around its bottom edge. +1 tilts the far end to the left, -1 to the right. */
    public void setTurn(TurnDetector.Turn turn, boolean animate) {
        float target = turn == TurnDetector.Turn.LEFT ? MAX_SKEW : turn == TurnDetector.Turn.RIGHT ? -MAX_SKEW : 0f;
        if (skewAnimator != null) {
            skewAnimator.cancel();
            skewAnimator = null;
        }
        if (!animate) {
            skew = target;
            invalidate();
            return;
        }
        skewAnimator = ValueAnimator.ofFloat(skew, target);
        skewAnimator.setDuration(SKEW_MS);
        skewAnimator.addUpdateListener(a -> {
            skew = (float) a.getAnimatedValue();
            invalidate();
        });
        skewAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (skewAnimator != null) {
            skewAnimator.cancel();
            skewAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int bottom = getHeight() - getPaddingBottom();
        int availH = bottom - getPaddingTop();
        if (w <= 0 || availH <= 0) {
            return;
        }
        float cx = w / 2f;
        // Box scale: 1 dp per unit, shrunk when the view is shorter than the box.
        float scale = Math.min(1f, availH / (BOX_H * density));
        float u = density * scale;
        float topY = bottom - BOX_H * u;

        // Glow under the car (not skewed).
        float glowR = 240f * u;
        float glowCy = bottom - 70f * u;
        glowPaint.setShader(new RadialGradient(cx, glowCy, glowR,
                new int[]{0xCC1C3557, 0x00000000}, null, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.scale(1f, 0.32f, cx, glowCy);
        canvas.drawCircle(cx, glowCy, glowR, glowPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(0, bottom);
        canvas.skew(skew, 0f);
        canvas.translate(0, -bottom);

        edgePaint.setStrokeWidth(STROKE * u);
        canvas.drawLine(cx - EDGE_BOTTOM_DX * u, bottom, cx - EDGE_TOP_DX * u, topY, edgePaint);
        canvas.drawLine(cx + EDGE_BOTTOM_DX * u, bottom, cx + EDGE_TOP_DX * u, topY, edgePaint);

        boolean scrolling = speedKmh > 0 && ValueAnimator.areAnimatorsEnabled() && isShown();
        long now = System.nanoTime();
        if (scrolling) {
            if (lastFrameNs != 0) {
                float dt = (now - lastFrameNs) / 1e9f;
                float periodSec = Math.max(MIN_PERIOD_SEC, 36f / speedKmh);
                phase = (phase + PERIOD * dt / periodSec) % PERIOD;
            }
            lastFrameNs = now;
        } else {
            lastFrameNs = 0;
        }

        dashPaint.setStrokeWidth(STROKE * u);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{DASH * u, GAP * u}, phase * u));
        // Path starts at the bottom so a growing phase moves the dashes toward the viewer.
        path.rewind();
        path.moveTo(cx, bottom);
        path.lineTo(cx, topY);
        canvas.drawPath(path, dashPaint);
        canvas.restore();

        if (scrolling) {
            postInvalidateOnAnimation();
        }
    }
}
