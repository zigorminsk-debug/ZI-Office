package com.docreader.app;
import android.content.Intent; import android.net.Uri; import android.os.Bundle; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity; import com.google.android.material.appbar.MaterialToolbar;
public class CloudActivity extends AppCompatActivity {
    private String pending; private ActivityResultLauncher<Intent> tree;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_cloud);
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb); tb.setNavigationOnClickListener(v -> finish());
        tree = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getData() == null || res.getData().getData() == null || pending == null) return;
            Uri uri = res.getData().getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
            CloudTrees.save(this, pending, uri); pending = null; refresh(); Toast.makeText(this, "Папка подключена", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnGooglePick).setOnClickListener(v -> pick(CloudTrees.GOOGLE));
        findViewById(R.id.btnYandexPick).setOnClickListener(v -> pick(CloudTrees.YANDEX));
        findViewById(R.id.btnGoogleWeb).setOnClickListener(v -> startActivity(new Intent(this, CloudBrowserActivity.class).putExtra("cloud", CloudTrees.GOOGLE)));
        findViewById(R.id.btnYandexWeb).setOnClickListener(v -> startActivity(new Intent(this, CloudBrowserActivity.class).putExtra("cloud", CloudTrees.YANDEX)));
        findViewById(R.id.btnGoogleOut).setOnClickListener(v -> { CloudTrees.clear(this, CloudTrees.GOOGLE); CloudSession.logoutGoogle(this); refresh(); });
        findViewById(R.id.btnYandexOut).setOnClickListener(v -> { CloudTrees.clear(this, CloudTrees.YANDEX); CloudSession.logoutYandex(this); refresh(); });
        findViewById(R.id.btnConnect).setOnClickListener(v -> startActivity(new Intent(this, CloudBrowseActivity.class).putExtra("cloud", CloudTrees.GOOGLE)));
        findViewById(R.id.btnChange).setOnClickListener(v -> startActivity(new Intent(this, CloudBrowseActivity.class).putExtra("cloud", CloudTrees.YANDEX)));
        refresh();
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    private void pick(String cloud) {
        pending = cloud; Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        tree.launch(i);
    }
    private void refresh() {
        ((TextView) findViewById(R.id.googleStatus)).setText(CloudTrees.get(this, CloudTrees.GOOGLE) != null ? "Google Drive: папка подключена" : "Google Drive: не подключено");
        ((TextView) findViewById(R.id.yandexStatus)).setText(CloudTrees.get(this, CloudTrees.YANDEX) != null ? "Яндекс Диск: папка подключена" : "Яндекс Диск: не подключено");
    }
}
