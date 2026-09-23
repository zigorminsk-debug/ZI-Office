package com.docreader.app;
import android.content.Intent; import android.graphics.Bitmap; import android.graphics.pdf.PdfRenderer; import android.net.Uri; import android.os.Bundle; import android.os.ParcelFileDescriptor; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; import com.google.android.material.appbar.MaterialToolbar;
import java.io.File; import java.io.FileOutputStream; import java.util.ArrayList;
public class MergeActivity extends AppCompatActivity {
    private final ArrayList<Uri> files = new ArrayList<>(); private LinearLayout list;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_merge);
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb); tb.setNavigationOnClickListener(v -> finish());
        list = findViewById(R.id.fileList);
        ActivityResultLauncher<Intent> pick = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getData() == null) return;
            if (res.getData().getClipData() != null) { for (int i = 0; i < res.getData().getClipData().getItemCount(); i++) add(res.getData().getClipData().getItemAt(i).getUri()); }
            else if (res.getData().getData() != null) add(res.getData().getData());
        });
        findViewById(R.id.btnAdd).setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); pick.launch(i); });
        findViewById(R.id.btnMerge).setOnClickListener(v -> { if (files.size() < 2) { Toast.makeText(this, "Добавьте минимум 2 PDF", Toast.LENGTH_SHORT).show(); return; } new Thread(this::merge).start(); });
    }
    private void add(Uri uri) { if (uri == null) return; files.add(uri); TextView t = new TextView(this); t.setText(FileKind.name(this, uri)); t.setPadding(24, 16, 24, 16); t.setTextColor(getColor(R.color.ink)); list.addView(t); }
    private void merge() {
        File dir = getExternalFilesDir(null); if (dir == null) dir = getFilesDir();
        File out = new File(dir, "merge-" + System.currentTimeMillis() + ".pdf");
        android.graphics.pdf.PdfDocument dest = null;
        try {
            dest = new android.graphics.pdf.PdfDocument();
            boolean any = false;
            for (Uri u : files) {
                ParcelFileDescriptor pfd = null; PdfRenderer r = null;
                try {
                    pfd = getContentResolver().openFileDescriptor(u, "r");
                    if (pfd == null) continue;
                    r = new PdfRenderer(pfd);
                    for (int i = 0; i < r.getPageCount(); i++) {
                        PdfRenderer.Page p = r.openPage(i);
                        int pw = p.getWidth(), ph = p.getHeight();
                        float sc = Math.min(1f, 1400f / Math.max(1, Math.max(pw, ph)));
                        int tw = Math.max(1, Math.round(pw * sc)), th = Math.max(1, Math.round(ph * sc));
                        Bitmap bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
                        p.render(bmp, new android.graphics.RectF(0, 0, tw, th), null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close();
                        android.graphics.pdf.PdfDocument.PageInfo info = new android.graphics.pdf.PdfDocument.PageInfo.Builder(tw, th, dest.getPages().size() + 1).create();
                        android.graphics.pdf.PdfDocument.Page page = dest.startPage(info); page.getCanvas().drawBitmap(bmp, 0, 0, null); dest.finishPage(page);
                        bmp.recycle(); any = true;
                    }
                } finally {
                    try { if (r != null) r.close(); } catch (Exception ignored) {}
                    try { if (pfd != null) pfd.close(); } catch (Exception ignored) {}
                }
            }
            if (!any) { runOnUiThread(() -> Toast.makeText(this, "Нет страниц для объединения", Toast.LENGTH_LONG).show()); return; }
            try (FileOutputStream fo = new FileOutputStream(out)) { dest.writeTo(fo); }
            runOnUiThread(() -> ViewerActivity.openLocal(this, out, out.getName(), "pdf"));
        } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Ошибка объединения", Toast.LENGTH_LONG).show()); }
        finally { try { if (dest != null) dest.close(); } catch (Exception ignored) {} }
    }
}
