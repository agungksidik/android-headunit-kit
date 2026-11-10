package com.agung.headhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Three accent bars that bounce like an equalizer. They only move while playing and stop where they are. */
public class EqualizerView extends View {

    private static final float BAR_W_DP = 4f;
    private static final float GAP_DP = 3f;
    private static final float HEIGHT_DP = 18f;
    private static final long PERIOD_MS = 1000;
    private static final long[] OFFSET_MS = {0, 300, 600};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean playing;
    private long phaseMs;
    private long lastFrame;

    public EqualizerView(Context context) {
        this(context, null);
    }

    public EqualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        paint.setColor(context.getColor(R.color.accent));
    }

    public void setPlaying(boolean playing) {
        if (this.playing == playing) {
            return;
        }
        this.playing = playing;
        lastFrame = 0;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) ((3 * BAR_W_DP + 2 * GAP_DP) * density);
        setMeasuredDimension(w, (int) (HEIGHT_DP * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean animate = playing && ValueAnimator.areAnimatorsEnabled();
        long now = SystemClock.uptimeMillis();
        if (animate) {
            if (lastFrame != 0) {
                phaseMs += now - lastFrame;
            }
            lastFrame = now;
        } else {
            lastFrame = 0;
        }
        float barW = BAR_W_DP * density;
        float gap = GAP_DP * density;
        float h = HEIGHT_DP * density;
        for (int i = 0; i < 3; i++) {
            double u = ((phaseMs + OFFSET_MS[i]) % PERIOD_MS) / (double) PERIOD_MS;
            float scale = 0.35f + 0.65f * (float) ((1 - Math.cos(2 * Math.PI * u)) / 2);
            float left = i * (barW + gap);
            canvas.drawRect(left, h * (1 - scale), left + barW, h, paint);
        }
        if (animate) {
            postInvalidateOnAnimation();
        }
    }
}
