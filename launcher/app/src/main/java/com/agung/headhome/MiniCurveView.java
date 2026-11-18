package com.agung.headhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/** The small curve on a preset card, stretched to the view (design viewBox 120x26, no aspect ratio kept). */
public class MiniCurveView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int[] gains = new int[SoundPreset.BANDS];

    public MiniCurveView(Context context) {
        this(context, null);
    }

    public MiniCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(int[] gains, int color) {
        this.gains = gains;
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.scale(getWidth() / 120f, getHeight() / 26f);
        path.rewind();
        for (int i = 0; i < gains.length; i++) {
            float x = 4f + i * (112f / 31f);
            float y = 13f - gains[i] * 0.8f;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
        canvas.restore();
    }
}
