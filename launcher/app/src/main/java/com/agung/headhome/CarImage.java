package com.agung.headhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** The optional custom car picture: filesDir/car_custom.png, shown instead of the built-in drawing. */
final class CarImage {

    static final int MAX_SIDE_PX = 800;
    private static final String FILE_NAME = "car_custom.png";

    private CarImage() {
    }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    static boolean hasCustom(Context ctx) {
        return file(ctx).isFile();
    }

    /** Last modified time of the custom file, 0 when there is none. */
    static long modified(Context ctx) {
        File f = file(ctx);
        return f.isFile() ? f.lastModified() : 0;
    }

    /** The custom bitmap (longest side at most 800 px, alpha kept), or null without a usable file. */
    static Bitmap load(Context ctx) {
        File f = file(ctx);
        if (!f.isFile()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), bounds);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    static boolean remove(Context ctx) {
        File f = file(ctx);
        return !f.exists() || f.delete();
    }

    /** Smallest power of two that brings the longest side to at most 800 px. */
    static int sampleSize(int width, int height) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / sample > MAX_SIDE_PX) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * Copies the picked image into filesDir/car_custom.png as a PNG, scaled down so the longest side is at most
     * 800 px. Blocking: call off the UI thread. Returns false when the file cannot be decoded.
     */
    static boolean importFrom(Context ctx, Uri uri) {
        File tmp = new File(ctx.getFilesDir(), FILE_NAME + ".tmp");
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return false;
                }
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return false;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
            Bitmap bmp;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return false;
                }
                bmp = BitmapFactory.decodeStream(in, null, o);
            }
            if (bmp == null) {
                return false;
            }
            int longest = Math.max(bmp.getWidth(), bmp.getHeight());
            if (longest > MAX_SIDE_PX) {
                float s = (float) MAX_SIDE_PX / longest;
                bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * s)),
                        Math.max(1, Math.round(bmp.getHeight() * s)), true);
            }
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return false;
                }
            }
            File target = file(ctx);
            if (target.exists() && !target.delete()) {
                return false;
            }
            return tmp.renameTo(target);
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }
}
