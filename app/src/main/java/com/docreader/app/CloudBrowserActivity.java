package com.docreader.app;
import android.annotation.SuppressLint; import android.net.Uri; import android.os.Bundle;
import android.webkit.CookieManager; import android.webkit.URLUtil; import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest; import android.webkit.WebSettings; import android.webkit.WebView; import android.webkit.WebViewClient; import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity; import com.google.android.material.appbar.MaterialToolbar;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream; import java.net.HttpURLConnection; import java.net.URL;
public class CloudBrowserActivity extends AppCompatActivity {
    private String cloud; private WebView web;
    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_cloud_browser);
        cloud = getIntent().getStringExtra("cloud"); if (cloud == null) cloud = CloudTrees.GOOGLE;
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb); tb.setNavigationOnClickListener(v -> finish());
        tb.setTitle("google".equals(cloud) ? "Google Drive" : "Яндекс Диск"); CloudSession.restore(this);
        web = findViewById(R.id.web); WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl(); if (u == null) return false; String last = u.getLastPathSegment();
                if (last != null && FileKind.office(FileKind.ext(last))) { download(u.toString(), last); return true; } return false;
            }
            @Override public void onPageFinished(WebView view, String url) { CloudSession.persist(CloudBrowserActivity.this, cloud); }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> download(url, URLUtil.guessFileName(url, cd, mime)));
        web.loadUrl("google".equals(cloud) ? "https://drive.google.com" : "https://disk.yandex.ru/client/disk");
    }
    private void download(String url, String name) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        final String fileName = FileKind.safeFileName(name);
        Toast.makeText(this, "Скачиваем…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); c.setInstanceFollowRedirects(true);
                String cookies = CookieManager.getInstance().getCookie(url); if (cookies != null) c.setRequestProperty("Cookie", cookies); c.connect();
                File out = new File(getCacheDir(), fileName);
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) { byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) fo.write(buf, 0, n); }
                String kind = FileKind.fromNameAndMime(out.getName(), null);
                runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; if (FileKind.UNKNOWN.equals(kind)) Toast.makeText(this, getString(R.string.unsupported), Toast.LENGTH_LONG).show(); else ViewerActivity.openLocal(this, out, out.getName(), kind); });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "Откройте файл через «Папка диска»", Toast.LENGTH_LONG).show()); }
        }).start();
    }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); CloudSession.persist(this, cloud); }
    @Override protected void onDestroy() {
        try { if (web != null) { web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(null); web.destroy(); web = null; } } catch (Exception ignored) {}
        super.onDestroy();
    }
}
