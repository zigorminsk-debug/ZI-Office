package com.docreader.app;
import android.content.Intent; import android.database.Cursor; import android.net.Uri; import android.os.Bundle;
import android.provider.DocumentsContract; import android.view.Gravity; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList; import java.util.List;
public class CloudBrowseActivity extends AppCompatActivity {
    private String cloud; private Uri tree; private String currentId; private String currentName = "Корень";
    private LinearLayout fileList; private TextView pathView;
    private final List<String[]> navStack = new ArrayList<>(); // {id, name}
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_cloud_browse);
        cloud = getIntent().getStringExtra("cloud"); if (cloud == null) cloud = CloudTrees.GOOGLE;
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb); tb.setNavigationOnClickListener(v -> finish());
        tb.setTitle("google".equals(cloud) ? "Google Drive" : "Яндекс Диск");
        fileList = findViewById(R.id.fileList); pathView = findViewById(R.id.pathView);
        ActivityResultLauncher<Intent> pickTree = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getData() == null || res.getData().getData() == null) return;
            Uri uri = res.getData().getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
            CloudTrees.save(this, cloud, uri); tree = uri; currentId = null; currentName = "Корень"; navStack.clear(); load();
        });
        findViewById(R.id.btnSaf).setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); pickTree.launch(i); });
        findViewById(R.id.btnUp).setOnClickListener(v -> { if (!navStack.isEmpty()) { String[] p = navStack.remove(navStack.size()-1); currentId = p[0]; currentName = p[1]; load(); } else finish(); });
        tree = CloudTrees.get(this, cloud); if (tree == null) Toast.makeText(this, "Сначала подключите папку диска", Toast.LENGTH_LONG).show(); load();
    }
    private void load() {
        fileList.removeAllViews(); if (tree == null) { pathView.setText("Папка не выбрана"); return; }
        try {
            if (currentId == null) {
                currentId = DocumentsContract.getTreeDocumentId(tree);
                try (Cursor c = getContentResolver().query(tree, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst() && c.getString(0) != null) currentName = c.getString(0);
                } catch (Exception ignored) {}
            }
            pathView.setText("📁 " + (currentName == null ? currentId : currentName));
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, currentId);
            float d = getResources().getDisplayMetrics().density; boolean any = false;
            try (Cursor c = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (c != null) while (c.moveToNext()) {
                    any = true; String id = c.getString(0), name = c.getString(1), mime = c.getString(2); boolean dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                    TextView tv = new TextView(this); tv.setText((dir ? "📁 " : "📄 ") + (name == null ? "файл" : name)); tv.setTextSize(16); tv.setTextColor(getColor(R.color.ink));
                    tv.setPadding((int)(14*d),(int)(12*d),(int)(14*d),(int)(12*d)); tv.setGravity(Gravity.CENTER_VERTICAL);
                    tv.setOnClickListener(v -> {
                        if (dir) { navStack.add(new String[]{currentId, currentName}); currentId = id; currentName = name == null ? "папка" : name; load(); }
                        else {
                            Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                            String kind = FileKind.fromNameAndMime(name, mime);
                            if (FileKind.UNKNOWN.equals(kind)) { Toast.makeText(this, getString(R.string.unsupported), Toast.LENGTH_SHORT).show(); return; }
                            RecentStore.add(this, uri, name, kind);
                            Intent i = new Intent(this, ViewerActivity.class); i.setData(uri); i.putExtra("name", name); i.putExtra("kind", kind); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
                        }
                    });
                    fileList.addView(tv);
                }
            }
            if (!any) { TextView t = new TextView(this); t.setText("Папка пуста"); t.setPadding(32,32,32,32); fileList.addView(t); }
        } catch (Exception e) { Toast.makeText(this, "Нет доступа к папке", Toast.LENGTH_LONG).show(); }
    }
}
