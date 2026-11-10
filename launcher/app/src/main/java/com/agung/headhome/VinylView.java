package com.agung.headhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/**
 * The record: dark grooves, a center label (album art as a circle, or an accent gradient with initials) and a
 * spindle hole. The static record is drawn once into a bitmap and shown in a child view on a hardware layer, so
 * spinning only rotates a texture. A soft highlight sits on top and does not rotate, like real light. The disc
 * turns once per 6 s while spinning and keeps its angle when stopped.
 */
public class VinylView extends FrameLayout {

    private static final long SPIN_MS = 6000;
    private static final float LABEL_DP = 96f;
    private static final float HOLE_DP = 10f;

    private final float density;
    private final DiscView disc;
    private final LightView light;

    private Bitmap art;

    private ValueAnimator spin;
    private boolean wantSpin;

    public VinylView(Context context) {
        this(context, null);
    }

    public VinylView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        disc = new DiscView(context);
        light = new LightView(context);
        addView(disc, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(light, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        disc.setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Sets the label: the cover (cropped to a circle) or, when null, the default label image. */
    public void setLabel(Bitmap cover) {
        if (cover == art) {
            return;
        }
        art = cover;
        disc.rebuild();
    }

    public void setSpinning(boolean spinning) {
        wantSpin = spinning;
        applySpin();
    }

    private void applySpin() {
        boolean run = wantSpin && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled();
        if (run) {
            if (spin == null) {
                spin = ValueAnimator.ofFloat(0f, 360f);
                spin.setDuration(SPIN_MS);
                spin.setInterpolator(new LinearInterpolator());
                spin.setRepeatCount(ValueAnimator.INFINITE);
                spin.addUpdateListener(a -> disc.setRotation((float) a.getAnimatedValue()));
            }
            if (!spin.isStarted()) {
                spin.start();
            } else if (spin.isPaused()) {
                spin.resume();
            }
        } else if (spin != null && spin.isStarted() && !spin.isPaused()) {
            spin.pause();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applySpin();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (spin != null && spin.isStarted() && !spin.isPaused()) {
            spin.pause();
        }
    }

    /** The rotating part: shows the cached record bitmap. */
    private final class DiscView extends View {
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private Bitmap cache;

        DiscView(Context context) {
            super(context);
        }

        void rebuild() {
            // Not recycled: the previous frame's display list may still reference it.
            cache = null;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            rebuild();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (cache == null && getWidth() > 0 && getHeight() > 0) {
                cache = render(getWidth(), getHeight());
            }
            if (cache != null) {
                canvas.drawBitmap(cache, 0, 0, bitmapPaint);
            }
        }

        /** The default label vector, rendered to a square bitmap of this size. */
        private Bitmap defaultLabel(int size) {
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Drawable d = getContext().getDrawable(R.drawable.vinyl_label_default);
            d.setBounds(0, 0, size, size);
            d.draw(new Canvas(b));
            return b;
        }

        private Bitmap render(int w, int h) {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            float cx = w / 2f;
            float cy = h / 2f;
            float radius = Math.min(w, h) / 2f;
            float labelR = LABEL_DP * density / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF0D0E10);
            c.drawCircle(cx, cy, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(0xFF1A1C21);
            for (float r = 3 * density; r < radius - 2 * density; r += 4 * density) {
                if (r > labelR) {
                    c.drawCircle(cx, cy, r, paint);
                }
            }
            paint.setStrokeWidth(2 * density);
            paint.setColor(0xFF23262C);
            c.drawCircle(cx, cy, radius - density, paint);

            paint.setStyle(Paint.Style.FILL);
            Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            Bitmap label = art != null ? art : defaultLabel(Math.round(labelR * 2));
            BitmapShader shader = new BitmapShader(label, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float side = Math.min(label.getWidth(), label.getHeight());
            float scale = (labelR * 2) / side;
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate(cx - label.getWidth() * scale / 2f, cy - label.getHeight() * scale / 2f);
            shader.setLocalMatrix(m);
            labelPaint.setShader(shader);
            c.drawCircle(cx, cy, labelR, labelPaint);
            paint.setColor(0xFF0B0D10);
            c.drawCircle(cx, cy, HOLE_DP * density / 2f, paint);
            return bmp;
        }
    }

    /** The static highlight on top of the record. */
    private static final class LightView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        LightView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float cx = w / 2f;
            float cy = h / 2f;
            SweepGradient sweep = new SweepGradient(cx, cy,
                    new int[]{0x00FFFFFF, 0x17FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x12FFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 30f / 360f, 60f / 360f, 180f / 360f, 210f / 360f, 240f / 360f, 1f});
            Matrix m = new Matrix();
            // CSS conic-gradient(from 20deg) starts at 12 o'clock; a sweep gradient starts at 3 o'clock.
            m.setRotate(-70f, cx, cy);
            sweep.setLocalMatrix(m);
            paint.setShader(sweep);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawCircle(cx, cy, Math.min(getWidth(), getHeight()) / 2f, paint);
        }
    }
}
