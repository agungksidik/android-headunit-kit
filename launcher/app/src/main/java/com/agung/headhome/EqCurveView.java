package com.agung.headhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * The curve graph of Custom Sound. Drawn in the design's 760x236 coordinate space and scaled to fit:
 * -12 to +12 dB scale, zero line, level line, optional 32 bars with a selected band, the curve with an area fill
 * and, in the simple mode, four handles on the bass, clarity, vocal and treble bands.
 */
public class EqCurveView extends View {

    interface BandListener {
        void onBandPicked(int band);
    }

    private static final float VB_W = 760f;
    private static final float VB_H = 236f;
    private static final float X0 = 27f;
    private static final float X_SPAN = 706f;
    private static final int[] HANDLE_HZ = {72, 240, 1800, 10000};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve = new Path();
    private final Path area = new Path();

    private int[] gains = new int[SoundPreset.BANDS];
    private int level;
    private boolean advanced;
    private int selected;
    private BandListener listener;
    private final int accent;

    public EqCurveView(Context context) {
        this(context, null);
    }

    public EqCurveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = context.getColor(R.color.accent);
    }

    void setListener(BandListener l) {
        listener = l;
    }

    void set(int[] gains, int level, boolean advanced, int selected) {
        this.gains = gains;
        this.level = level;
        this.advanced = advanced;
        this.selected = selected;
        invalidate();
    }

    private static float y(float db) {
        return 112f - db * 7.5f;
    }

    private static float x(int i) {
        return X0 + i * (X_SPAN / 31f);
    }

    private float scale() {
        return Math.min(getWidth() / VB_W, getHeight() / VB_H);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = scale();
        if (s <= 0) {
            return;
        }
        canvas.save();
        canvas.translate((getWidth() - VB_W * s) / 2f, (getHeight() - VB_H * s) / 2f);
        canvas.scale(s, s);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setPathEffect(null);
        int[] marks = {12, 6, 0, -6, -12};
        paint.setTextSize(11f);
        for (int v : marks) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF1B1F26);
            canvas.drawLine(27f, y(v), 733f, y(v), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF5D636C);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText((v > 0 ? "+" : "") + v, 2f, y(v) + 4f, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF2A2F37);
        paint.setPathEffect(new DashPathEffect(new float[]{4f, 6f}, 0f));
        canvas.drawLine(27f, y(0), 733f, y(0), paint);
        paint.setPathEffect(null);

        paint.setColor((accent & 0x00FFFFFF) | 0x59000000);
        canvas.drawLine(20f, y(level), 740f, y(level), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("level " + (level > 0 ? "+" : "") + level, 731f, y(level) - 4f, paint);

        if (advanced) {
            for (int i = 0; i < gains.length; i++) {
                float yy = y(gains[i]);
                float top = Math.min(yy, y(0));
                float h = Math.max(2f, Math.abs(yy - y(0)));
                paint.setColor(i == selected ? accent : 0x2EE6E8EB);
                canvas.drawRoundRect(x(i) - 7f, top, x(i) + 7f, top + h, 4f, 4f, paint);
            }
        }

        curve.rewind();
        area.rewind();
        float minY = y(-12);
        for (int i = 0; i < gains.length; i++) {
            float yy = y(gains[i]);
            minY = Math.min(minY, yy);
            if (i == 0) {
                curve.moveTo(x(i), yy);
            } else {
                curve.lineTo(x(i), yy);
            }
        }
        area.addPath(curve);
        area.lineTo(733f, y(-12));
        area.lineTo(27f, y(-12));
        area.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0f, minY, 0f, y(-12),
                (accent & 0x00FFFFFF) | 0x66000000, (accent & 0x00FFFFFF), Shader.TileMode.CLAMP));
        paint.setAlpha(advanced ? 90 : 255);
        canvas.drawPath(area, paint);
        paint.setShader(null);
        paint.setAlpha(255);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(new LinearGradient(27f, 0f, 733f, 0f,
                new int[]{0xFF7A5BFF, accent, 0xFF3EC7A8}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(curve, paint);
        paint.setShader(null);

        if (!advanced) {
            for (int hz : HANDLE_HZ) {
                int i = SoundCurve.nearestBand(hz);
                float cx = x(i);
                float cy = y(gains[i]);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF0B0D10);
                canvas.drawCircle(cx, cy, 13f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(cx, cy, 13f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accent);
                canvas.drawCircle(cx, cy, 5f, paint);
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF5D636C);
        paint.setTextSize(12f);
        paint.setTextAlign(Paint.Align.CENTER);
        float[] ax = {27f, 175f, 391f, 595f, 733f};
        String[] at = {"20", "100", "1k", "5k", "20k Hz"};
        for (int i = 0; i < ax.length; i++) {
            canvas.drawText(at[i], ax[i], 232f, paint);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!advanced || listener == null) {
            return false;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float s = scale();
            float vx = (e.getX() - (getWidth() - VB_W * s) / 2f) / s;
            int band = Math.round((vx - X0) / (X_SPAN / 31f));
            listener.onBandPicked(Math.max(0, Math.min(31, band)));
            return true;
        }
        return true;
    }
}
