package com.docreader.app;
import android.Manifest; import android.content.pm.PackageManager; import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.net.Uri; import android.os.Bundle; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; import androidx.core.content.ContextCompat; import androidx.core.content.FileProvider;
import com.google.android.material.appbar.MaterialToolbar;
import java.io.File; import java.io.FileOutputStream; import java.util.ArrayList;
public class ScanActivity extends AppCompatActivity {
    private final ArrayList<File> pages = new ArrayList<>();
    private File pendingPhoto; private ActivityResultLauncher<Uri> takePic; private ActivityResultLauncher<String> askCam;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_scan);
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb); tb.setNavigationOnClickListener(v -> finish());
        takePic = registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> { if (ok && pendingPhoto != null && pendingPhoto.exists()) { pages.add(pendingPhoto); pendingPhoto = null; Toast.makeText(this, "Страниц: " + pages.size(), Toast.LENGTH_SHORT).show(); } });
        askCam = registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> { if (g) shoot(); else Toast.makeText(this, "Нужна камера", Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.btnShot).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) askCam.launch(Manifest.permission.CAMERA); else shoot();
        });
        findViewById(R.id.btnDone).setOnClickListener(v -> { if (pages.isEmpty()) { Toast.makeText(this, "Сначала сделайте снимок", Toast.LENGTH_SHORT).show(); return; } new Thread(this::buildPdf).start(); });
    }
    private void shoot() {
        try {
            File dir = new File(getCacheDir(), "scan"); dir.mkdirs(); pendingPhoto = new File(dir, "p" + System.currentTimeMillis() + ".jpg");
            Uri uri = FileProvider.getUriForFile(this, "com.docreader.app.files", pendingPhoto); takePic.launch(uri);
        } catch (Exception e) { Toast.makeText(this, "Камера недоступна", Toast.LENGTH_SHORT).show(); }
    }
    private void buildPdf() {
        File out = new File(getExternalFilesDir(null), "scan-" + System.currentTimeMillis() + ".pdf");
        try {
            android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
            for (File f : pages) {
                Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath()); if (raw == null) continue;
                Bitmap bmp = ImagePrep.prepare(raw);
                android.graphics.pdf.PdfDocument.PageInfo info = new android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.getWidth(), bmp.getHeight(), doc.getPages().size()+1).create();
                android.graphics.pdf.PdfDocument.Page page = doc.startPage(info); page.getCanvas().drawBitmap(bmp, 0, 0, null); doc.finishPage(page);
                if (bmp != raw) bmp.recycle(); raw.recycle();
            }
            try (FileOutputStream fo = new FileOutputStream(out)) { doc.writeTo(fo); } doc.close();
            runOnUiThread(() -> ViewerActivity.openLocal(this, out, out.getName(), "pdf"));
        } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Ошибка PDF", Toast.LENGTH_LONG).show()); }
    }
}
