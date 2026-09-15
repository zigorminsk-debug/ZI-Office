package com.docreader.app;
import android.content.Context; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.graphics.Matrix;
import android.net.Uri; import android.media.ExifInterface;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;
final class ImagePrep {
    static Bitmap prepare(Bitmap raw) {
        if (raw == null) return null;
        int max = 1600;
        float scale = Math.min(1f, max / (float) Math.max(raw.getWidth(), raw.getHeight()));
        if (scale >= 0.99f) return raw;
        Matrix m = new Matrix(); m.setScale(scale, scale);
        return Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), m, true);
    }
    static File toScanJpeg(Context ctx, Uri uri, File out, int maxSide, boolean bw, boolean deskew) {
        Bitmap bmp = decode(ctx, uri, maxSide * 2); if (bmp == null) return null;
        try (InputStream in = open(ctx, uri)) {
            if (in != null) {
                ExifInterface ex = new ExifInterface(in);
                int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                int deg = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
                if (deg != 0) { Matrix m = new Matrix(); m.postRotate(deg); Bitmap r = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true); if (r != bmp) bmp.recycle(); bmp = r; }
            }
        } catch (Exception ignored) {}
        float scale = Math.min(1f, maxSide / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (scale < 0.99f) { Matrix m = new Matrix(); m.setScale(scale, scale); Bitmap scaled = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true); if (scaled != bmp) bmp.recycle(); bmp = scaled; }
        try (FileOutputStream fo = new FileOutputStream(out)) { bmp.compress(Bitmap.CompressFormat.JPEG, 82, fo); } catch (Exception e) { bmp.recycle(); return null; }
        bmp.recycle(); return out;
    }
    private static Bitmap decode(Context ctx, Uri uri, int maxSide) {
        try (InputStream boundsIn = open(ctx, uri)) {
            if (boundsIn == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true; BitmapFactory.decodeStream(boundsIn, null, o);
            int sample = 1, w = Math.max(1, o.outWidth), h = Math.max(1, o.outHeight);
            while (Math.max(w / sample, h / sample) > maxSide) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options(); o2.inSampleSize = sample;
            try (InputStream in = open(ctx, uri)) { return in == null ? null : BitmapFactory.decodeStream(in, null, o2); }
        } catch (Exception e) { return null; }
    }
    private static InputStream open(Context ctx, Uri uri) throws Exception {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new java.io.FileInputStream(uri.getPath());
        return ctx.getContentResolver().openInputStream(uri);
    }
}
