package com.docreader.app;
import android.graphics.Bitmap; import android.graphics.Matrix;
final class ImagePrep {
    /** Уменьшает снимок до разумного размера страницы (макс. 1600 px по стороне). */
    static Bitmap prepare(Bitmap raw) {
        if (raw == null) return null;
        int max = 1600;
        float scale = Math.min(1f, max / (float) Math.max(raw.getWidth(), raw.getHeight()));
        if (scale >= 0.99f) return raw;
        Matrix m = new Matrix(); m.setScale(scale, scale);
        return Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
    }
}
