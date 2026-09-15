package com.docreader.app;
import android.content.Context; import android.graphics.Bitmap;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;
final class OcrHelper {
    static synchronized String recognizeBitmap(Context ctx, Bitmap bmp) {
        if (bmp == null) return "";
        File tessDir = new File(ctx.getFilesDir(), "tessdata");
        if (!tessDir.exists() && !tessDir.mkdirs()) return "";
        File rus = new File(tessDir, "rus.traineddata");
        if (!rus.exists() || rus.length() < 1000) {
            try (InputStream in = ctx.getAssets().open("tessdata/rus.traineddata"); FileOutputStream out = new FileOutputStream(rus)) {
                byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            } catch (Exception e) { return ""; }
        }
        TessBaseAPI api = new TessBaseAPI();
        try {
            if (!api.init(ctx.getFilesDir().getAbsolutePath(), "rus")) return "";
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            api.setImage(bmp);
            String t = api.getUTF8Text();
            return t == null ? "" : t.trim();
        } catch (Exception e) { return ""; }
        finally { try { api.recycle(); } catch (Exception ignored) {} }
    }
}
