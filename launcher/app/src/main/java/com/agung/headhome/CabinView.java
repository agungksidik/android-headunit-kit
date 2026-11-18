package com.agung.headhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Top view of the cabin with the listening position as a pulsing point (design viewBox 200x250). */
public class CabinView extends View {

    private static final float VB_W = 200f;
    private static final float VB_H = 250f;
    private static final float[][] FOCUS = {{127, 95}, {100, 95}, {100, 135}, {100, 175}};
    private static final int[][] LIT = {{1}, {0, 1}, {0, 1, 2, 3}, {2, 3}};
    private static final float[][] SEATS = {{64, 78}, {102, 78}, {64, 158}, {102, 158}};
    private static final float[][] SPEAKERS = {{48, 90}, {152, 90}, {48, 190}, {152, 190}};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private int seat;
    private ValueAnimator pulse;
    private float phase;

    public CabinView(Context context) {
        this(context, null);
    }

    public CabinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.accent);
    }

    void setSeat(int seat) {
        this.seat = Math.max(0, Math.min(3, seat));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ValueAnimator.areAnimatorsEnabled()) {
            pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(1200);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            pulse.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth() / VB_W, getHeight() / VB_H);
        if (s <= 0) {
            return;
        }
        canvas.save();
        canvas.translate((getWidth() - VB_W * s) / 2f, (getHeight() - VB_H * s) / 2f);
        canvas.scale(s, s);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF14181E);
        canvas.drawRoundRect(45f, 10f, 155f, 240f, 40f, 40f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFF2A2F37);
        canvas.drawRoundRect(45f, 10f, 155f, 240f, 40f, 40f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1B2027);
        canvas.drawRoundRect(58f, 34f, 142f, 60f, 10f, 10f, paint);

        for (int i = 0; i < SEATS.length; i++) {
            boolean lit = false;
            for (int l : LIT[seat]) {
                lit |= l == i;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(lit ? 0xFF1D2A3D : 0xFF181C22);
            canvas.drawRoundRect(SEATS[i][0], SEATS[i][1], SEATS[i][0] + 34f, SEATS[i][1] + 38f, 9f, 9f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(lit ? accent : 0xFF2A2F37);
            canvas.drawRoundRect(SEATS[i][0], SEATS[i][1], SEATS[i][0] + 34f, SEATS[i][1] + 38f, 9f, 9f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF3A404A);
        for (float[] sp : SPEAKERS) {
            canvas.drawCircle(sp[0], sp[1], 6f, paint);
        }

        float fx = FOCUS[seat][0];
        float fy = FOCUS[seat][1];
        paint.setColor(accent);
        paint.setAlpha((int) (255 * (0.25f + 0.35f * phase)));
        canvas.drawCircle(fx, fy, 26f * (1f + 0.15f * phase), paint);
        paint.setAlpha(255);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(fx, fy, 6f, paint);
        canvas.restore();
    }
}
