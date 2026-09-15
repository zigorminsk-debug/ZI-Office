package com.docreader.app;
import android.content.Intent; import android.net.Uri; import android.os.Bundle;
import android.view.Gravity; import android.view.View; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
public class MainActivity extends AppCompatActivity {
    private LinearLayout recentList; private View emptyBox; private ActivityResultLauncher<Intent> openDoc;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        recentList = findViewById(R.id.recentList); emptyBox = findViewById(R.id.emptyBox);
        openDoc = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> { if (res.getData() != null && res.getData().getData() != null) openUri(res.getData().getData()); });
        findViewById(R.id.btnOpen).setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel.sheet.macroEnabled.12","application/vnd.ms-excel.sheet.binary.macroEnabled.12","application/vnd.oasis.opendocument.spreadsheet","application/zip","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","text/plain","text/csv","*/*"}); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); openDoc.launch(i); });
        findViewById(R.id.btnNewWord).setOnClickListener(v -> { Intent i = new Intent(this, ViewerActivity.class); i.putExtra("isNew", true); i.putExtra("name", "Документ.docx"); i.putExtra("kind", "docx"); startActivity(i); });
        findViewById(R.id.btnNewExcel).setOnClickListener(v -> { Intent i = new Intent(this, ViewerActivity.class); i.putExtra("isNew", true); i.putExtra("name", "Таблица.xlsx"); i.putExtra("kind", "xlsx"); startActivity(i); });
        findViewById(R.id.btnScan).setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.btnMerge).setOnClickListener(v -> startActivity(new Intent(this, MergeActivity.class)));
        findViewById(R.id.btnCloud).setOnClickListener(v -> startActivity(new Intent(this, CloudActivity.class)));
        findViewById(R.id.btnNight).setOnClickListener(v -> { ThemePrefs.toggle(this); recreate(); });
        findViewById(R.id.btnDefault).setOnClickListener(v -> DefaultApps.prompt(this));
        findViewById(R.id.btnCall).setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+375293371412"))); } catch (Exception e) { Toast.makeText(this, "+375293371412", Toast.LENGTH_LONG).show(); } });
        tb.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(getString(R.string.developer_name)+"\n"+getString(R.string.developer_phone_pretty)+"\n"+getString(R.string.version)).setPositiveButton("OK", null).show(); return true; });
        if (getIntent() != null && Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) openUri(getIntent().getData());
    }
    @Override protected void onResume() { super.onResume(); fillRecent(); }
    private void openUri(Uri uri) {
        if (uri == null) return;
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
        String name = FileKind.name(this, uri); String kind = FileKind.fromNameAndMime(name, FileKind.mime(this, uri));
        if (FileKind.UNKNOWN.equals(kind)) {
            java.io.File tmp = new java.io.File(getCacheDir(), "sniff-" + System.currentTimeMillis());
            try (java.io.InputStream in = getContentResolver().openInputStream(uri); java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp)) {
                if (in != null) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) fo.write(b, 0, n); }
            } catch (Exception ignored) {}
            kind = FileKind.sniff(tmp); try { tmp.delete(); } catch (Exception ignored) {}
        }
        if (FileKind.UNKNOWN.equals(kind)) { Toast.makeText(this, getString(R.string.unsupported), Toast.LENGTH_LONG).show(); return; }
        RecentStore.add(this, uri, name, kind);
        Intent i = new Intent(this, ViewerActivity.class); i.setData(uri); i.putExtra("name", name); i.putExtra("kind", kind); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); startActivity(i);
    }
    private void fillRecent() {
        recentList.removeAllViews();
        java.util.List<RecentStore.Item> items = RecentStore.load(this);
        emptyBox.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        int pdf = getColor(R.color.pdf), word = getColor(R.color.word), excel = getColor(R.color.excel), ppt = getColor(R.color.ppt), teal = getColor(R.color.teal_900);
        float d = getResources().getDisplayMetrics().density;
        for (RecentStore.Item it : items) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding((int)(12*d),(int)(10*d),(int)(12*d),(int)(10*d)); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView badge = new TextView(this); badge.setText(FileKind.displayExt(it.name, it.kind)); badge.setTextColor(0xFFFFFFFF); badge.setGravity(Gravity.CENTER); badge.setPadding((int)(8*d),(int)(4*d),(int)(8*d),(int)(4*d)); badge.setBackgroundColor(FileKind.color(it.kind, pdf, word, excel, ppt, teal));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams((int)(52*d), LinearLayout.LayoutParams.WRAP_CONTENT); bp.rightMargin = (int)(12*d); badge.setLayoutParams(bp);
            TextView name = new TextView(this); name.setText(it.name); name.setTextSize(16); name.setTextColor(getColor(R.color.ink)); name.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(badge); row.addView(name);
            row.setOnClickListener(v -> { try { openUri(Uri.parse(it.uri)); } catch (Exception e) { Toast.makeText(this, "Файл недоступен", Toast.LENGTH_SHORT).show(); } });
            recentList.addView(row);
        }
    }
}
