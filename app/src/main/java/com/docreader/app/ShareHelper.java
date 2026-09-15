package com.docreader.app;
import android.content.Context; import android.content.Intent; import android.net.Uri; import androidx.core.content.FileProvider; import java.io.File;
final class ShareHelper {
    static void share(Context ctx, File file, String mime, String name) {
        if (file == null || !file.exists()) return;
        Uri uri = FileProvider.getUriForFile(ctx, "com.docreader.app.files", file);
        Intent i = new Intent(Intent.ACTION_SEND); i.setType(mime == null ? "*/*" : mime); i.putExtra(Intent.EXTRA_STREAM, uri); i.putExtra(Intent.EXTRA_SUBJECT, name == null ? "document" : name); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(i, "Отправить"));
    }
    static void print(android.app.Activity act, File file, String name) {
        android.print.PrintManager pm = (android.print.PrintManager) act.getSystemService(Context.PRINT_SERVICE); if (pm == null) return;
        pm.print(name == null ? "document" : name, new android.print.PrintDocumentAdapter() {
            @Override public void onLayout(android.print.PrintAttributes oldA, android.print.PrintAttributes newA, android.os.CancellationSignal c, LayoutResultCallback cb, android.os.Bundle extras) {
                cb.onLayoutFinished(new android.print.PrintDocumentInfo.Builder(name == null ? "document" : name).setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true);
            }
            @Override public void onWrite(android.print.PageRange[] pages, android.os.ParcelFileDescriptor dest, android.os.CancellationSignal c, WriteResultCallback cb) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(file); java.io.FileOutputStream out = new java.io.FileOutputStream(dest.getFileDescriptor())) {
                    byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n); cb.onWriteFinished(new android.print.PageRange[]{android.print.PageRange.ALL_PAGES});
                } catch (Exception e) { cb.onWriteFailed(e.getMessage()); }
            }
        }, null);
    }
}
