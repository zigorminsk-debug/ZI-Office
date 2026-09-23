package com.docreader.app;
import android.annotation.SuppressLint; import android.content.ClipData; import android.content.ClipboardManager;
import android.content.Context; import android.content.Intent;
import android.graphics.Bitmap; import android.graphics.pdf.PdfRenderer; import android.net.Uri;
import android.os.Bundle; import android.os.Handler; import android.os.Looper; import android.os.ParcelFileDescriptor;
import android.text.InputType; import android.util.Base64; import android.view.Menu; import android.view.MenuItem; import android.view.View;
import android.webkit.JavascriptInterface; import android.webkit.WebResourceRequest; import android.webkit.WebResourceResponse;
import android.webkit.WebSettings; import android.webkit.WebView; import android.webkit.WebViewClient;
import android.widget.EditText; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog; import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar; import com.google.android.material.button.MaterialButton;
import java.io.ByteArrayOutputStream; import java.io.File; import java.io.FileInputStream; import java.io.FileOutputStream;
import java.io.InputStream; import java.io.OutputStream; import java.nio.charset.StandardCharsets;
import java.util.ArrayList; import java.util.List; import java.util.concurrent.Executors;
public class ViewerActivity extends AppCompatActivity {
    private WebView web, toolWeb; private PdfZoomView pdfZoom; private ZoomSurface webZoom;
    private View readerBar; private MaterialButton btnPlay; private MaterialToolbar toolbar;
    private File cacheFile; private Uri source; private String displayName = "document", fileExt = "", viewerKind = "pdf";
    private boolean isNew, nativePdf, readerOn, ocrBusy, toolReload;
    private PdfRenderer pdfRenderer; private ParcelFileDescriptor pdfPfd; private final Object pdfLock = new Object();
    private final List<Bitmap> pdfBitmaps = new ArrayList<>(); private final List<String> ocrPages = new ArrayList<>();
    private String ocrText = "", toolCmd = "rotate", extractSpec = "1", saveMime, saveName, pendingCloud;
    private final ByteArrayOutputStream saveBuf = new ByteArrayOutputStream();
    private int readPx = 20, pdfCount; private TtsHelper tts; private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zi-worker"); t.setDaemon(true); return t;
    });
    private ActivityResultLauncher<Intent> createDoc;
    public static void openLocal(Context ctx, File file, String name, String kind) {
        Intent i = new Intent(ctx, ViewerActivity.class);
        i.setData(androidx.core.content.FileProvider.getUriForFile(ctx, "com.docreader.app.files", file));
        i.putExtra("name", name); i.putExtra("kind", kind);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_viewer);
        toolbar = findViewById(R.id.toolbar); setSupportActionBar(toolbar); toolbar.setNavigationOnClickListener(v -> finish());
        web = findViewById(R.id.web); toolWeb = findViewById(R.id.toolWeb); pdfZoom = findViewById(R.id.pdfZoom); webZoom = findViewById(R.id.webZoom);
        readerBar = findViewById(R.id.readerBar); btnPlay = findViewById(R.id.btnTtsPlay);
        Intent in = getIntent(); source = in.getData();
        if (source == null && in.getParcelableExtra(Intent.EXTRA_STREAM) instanceof Uri) source = in.getParcelableExtra(Intent.EXTRA_STREAM);
        displayName = in.getStringExtra("name"); if (displayName == null) displayName = source == null ? "document" : FileKind.name(this, source);
        String k = in.getStringExtra("kind"); if (k == null || k.isEmpty()) k = FileKind.fromNameAndMime(displayName, source == null ? null : FileKind.mime(this, source));
        fileExt = FileKind.ext(displayName); viewerKind = FileKind.viewerKind(k.isEmpty() ? fileExt : k);
        isNew = in.getBooleanExtra("isNew", false); toolbar.setTitle(displayName);
        tts = new TtsHelper(this);
        tts.setListener((speaking, index, total) -> runOnUiThread(() -> { if (btnPlay != null) btnPlay.setText(speaking ? "▶ …" : "▶ Вслух"); }));
        createDoc = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
            if (res.getData() == null || res.getData().getData() == null) return;
            Uri u = res.getData().getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
            writeTo(u);
        });
        findViewById(R.id.btnTtsPlay).setOnClickListener(v -> { if (tts != null && tts.isPlaying()) tts.resume(); else startTts(); });
        findViewById(R.id.btnTtsPause).setOnClickListener(v -> { if (tts != null) tts.pause(); });
        findViewById(R.id.btnTtsStop).setOnClickListener(v -> { if (tts != null) tts.stop(); });
        findViewById(R.id.btnSlow).setOnClickListener(v -> { if (tts != null) Toast.makeText(this, "Скорость " + String.format("%.0f%%", tts.slower() * 100), Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.btnFast).setOnClickListener(v -> { if (tts != null) Toast.makeText(this, "Скорость " + String.format("%.0f%%", tts.faster() * 100), Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.btnAminus).setOnClickListener(v -> zoom(-1));
        findViewById(R.id.btnAplus).setOnClickListener(v -> zoom(1));
        findViewById(R.id.btnCloseRead).setOnClickListener(v -> setReader(false));
        if (pdfZoom != null) pdfZoom.setListener(s -> toolbar.setSubtitle(pdfCount + " стр. · " + Math.round(s * 100) + "%"));
        if (webZoom != null) webZoom.setListener(s -> toolbar.setSubtitle(Math.round(s * 100) + "%"));
        setupWeb(web); setupWeb(toolWeb);
        if (!isNew) {
            if (source == null) { Toast.makeText(this, "Нет файла для открытия", Toast.LENGTH_LONG).show(); finish(); return; }
            cacheFile = new File(getCacheDir(), "open-" + System.currentTimeMillis());
            try (InputStream inStream = getContentResolver().openInputStream(source); FileOutputStream fo = new FileOutputStream(cacheFile)) {
                if (inStream == null) throw new Exception("null");
                byte[] b = new byte[8192]; int n; while ((n = inStream.read(b)) > 0) fo.write(b, 0, n);
            } catch (Exception e) {
                Toast.makeText(this, "Не удалось открыть файл. Доступ мог быть отозван — откройте его заново.", Toast.LENGTH_LONG).show();
                finish(); return;
            }
            if (cacheFile.length() == 0) { Toast.makeText(this, "Файл пуст", Toast.LENGTH_LONG).show(); finish(); return; }
            if (cacheFile != null && cacheFile.exists()) {
                String sniffed = FileKind.sniff(cacheFile);
                if (!FileKind.UNKNOWN.equals(sniffed)) {
                    boolean weak = fileExt.isEmpty() || "bin".equals(fileExt) || "zip".equals(fileExt) || "dat".equals(fileExt);
                    String sv = FileKind.viewerKind(sniffed);
                    if (weak || ("pdf".equals(viewerKind) && !"pdf".equals(sv))) {
                        fileExt = sniffed; viewerKind = sv;
                    }
                }
            }
        }
        if ("pdf".equals(viewerKind) && !isNew && openPdfRenderer()) {
            nativePdf = true; if (webZoom != null) webZoom.setVisibility(View.GONE); pdfZoom.setVisibility(View.VISIBLE); paintPdf();
        } else {
            nativePdf = false; pdfZoom.setVisibility(View.GONE); if (webZoom != null) webZoom.setVisibility(View.VISIBLE); web.loadUrl("http://app.local/viewer.html");
        }
    }
    private void setupWeb(WebView w) {
        WebSettings s = w.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true);
        s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        w.setNestedScrollingEnabled(false); w.addJavascriptInterface(new Bridge(), "Android");
        w.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final android.webkit.JsPromptResult result) {
                if (isFinishing() || isDestroyed()) { result.cancel(); return true; }
                final EditText et = new EditText(ViewerActivity.this);
                et.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(ViewerActivity.this)
                        .setTitle(message == null || message.isEmpty() ? "Ввод" : message)
                        .setView(et)
                        .setPositiveButton("OK", (d, w2) -> result.confirm(et.getText().toString()))
                        .setNegativeButton("Отмена", (d, w2) -> result.cancel())
                        .show();
                return true;
            }
        });
        w.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                main.postDelayed(() -> { if (webZoom == null || nativePdf) return; int h = Math.round(view.getContentHeight() * getResources().getDisplayMetrics().density); webZoom.setChildHeight(Math.max(webZoom.getHeight(), h)); }, 400);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl(); if (u == null || !"app.local".equals(u.getHost())) return super.shouldInterceptRequest(view, request);
                String path = u.getPath() == null ? "" : u.getPath();
                try {
                    if ("/document".equals(path) && cacheFile != null && cacheFile.exists()) return new WebResourceResponse("application/octet-stream", "utf-8", new FileInputStream(cacheFile));
                    if (path.startsWith("/js/")) return new WebResourceResponse("application/javascript", "utf-8", getAssets().open(path.substring(1)));
                    if (path.contains("pdftools")) return new WebResourceResponse("text/html", "utf-8", getAssets().open("pdftools.html"));
                    return new WebResourceResponse("text/html", "utf-8", getAssets().open("viewer.html"));
                } catch (Exception ignored) {}
                return super.shouldInterceptRequest(view, request);
            }
        });
    }
    private boolean openPdfRenderer() {
        synchronized (pdfLock) {
            closePdfLocked();
            try {
                if (cacheFile == null || !cacheFile.exists()) return false;
                pdfPfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                pdfRenderer = new PdfRenderer(pdfPfd); pdfCount = pdfRenderer.getPageCount(); return pdfCount > 0;
            } catch (Exception e) { Toast.makeText(this, "Не удалось прочитать PDF", Toast.LENGTH_LONG).show(); return false; }
        }
    }
    private void closePdfLocked() {
        try { if (pdfRenderer != null) pdfRenderer.close(); } catch (Exception ignored) {}
        try { if (pdfPfd != null) pdfPfd.close(); } catch (Exception ignored) {}
        pdfRenderer = null; pdfPfd = null;
    }
    private void paintPdf() {
        if (pdfRenderer == null && !openPdfRenderer()) return;
        int targetW = Math.max(200, getResources().getDisplayMetrics().widthPixels);
        worker.execute(() -> {
            try {
                List<Bitmap> bitmaps = new ArrayList<>();
                synchronized (pdfLock) {
                    if (pdfRenderer == null) return; pdfCount = pdfRenderer.getPageCount();
                    for (int i = 0; i < pdfCount; i++) {
                        PdfRenderer.Page page = pdfRenderer.openPage(i);
                        int w = page.getWidth(), h = page.getHeight(); float sc = targetW / (float) Math.max(1, w);
                        Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)), Bitmap.Config.ARGB_8888);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close(); bitmaps.add(bmp);
                    }
                }
                main.post(() -> {
                    for (Bitmap b : pdfBitmaps) try { b.recycle(); } catch (Exception ignored) {}
                    pdfBitmaps.clear(); pdfBitmaps.addAll(bitmaps); pdfZoom.setPages(pdfBitmaps);
                    toolbar.setSubtitle(pdfCount + " стр. · 100%");
                });
            } catch (Exception e) { main.post(() -> Toast.makeText(this, "Не удалось прочитать PDF", Toast.LENGTH_LONG).show()); }
        });
    }
    private void zoom(int dir) {
        if (nativePdf && pdfZoom != null) pdfZoom.zoomBy(dir > 0 ? 1.25f : 0.8f);
        else if (webZoom != null) { webZoom.zoomBy(dir > 0 ? 1.25f : 0.8f); readPx = dir > 0 ? Math.min(48, readPx + 2) : Math.max(14, readPx - 2); web.evaluateJavascript("setReadSize(" + readPx + ")", null); }
    }
    private void setReader(boolean on) { readerOn = on; readerBar.setVisibility(on ? View.VISIBLE : View.GONE); if (!nativePdf) web.evaluateJavascript("setReaderMode(" + (on ? "true" : "false") + ")", null); }
    private void startTts() {
        setReader(true);
        if (nativePdf) { if (ocrText == null || ocrText.isEmpty()) { runOcr(true, false); return; } if (tts != null) tts.play(ocrText); return; }
        web.evaluateJavascript("startReadAloud()", null);
    }
    private void jumpPage() {
        if (!nativePdf || pdfCount < 1) return;
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setHint("1–" + pdfCount);
        new AlertDialog.Builder(this).setTitle("Страница").setView(input)
                .setPositiveButton("Перейти", (d, w) -> { try { int p = Integer.parseInt(input.getText().toString().trim()); if (p >= 1 && p <= pdfCount) pdfZoom.jumpToPage(p - 1); } catch (Exception ignored) {} })
                .setNegativeButton("Отмена", null).show();
    }
    private void runTool(String cmd, String spec) {
        if (!nativePdf) { Toast.makeText(this, "Только для PDF", Toast.LENGTH_SHORT).show(); return; }
        toolCmd = cmd; extractSpec = spec == null ? "1" : spec; toolReload = true; saveBuf.reset(); toolWeb.loadUrl("http://app.local/pdftools.html");
    }
    private void askRange(String title, String hint, String cmd) {
        EditText input = new EditText(this); input.setHint(hint);
        new AlertDialog.Builder(this).setTitle(title).setView(input)
                .setPositiveButton("OK", (d, w) -> { String spec = input.getText() == null ? "" : input.getText().toString().trim(); runTool(cmd, spec.isEmpty() ? "1" : spec); })
                .setNegativeButton("Отмена", null).show();
    }
    private void runOcr(boolean thenPlay, boolean thenDialog) {
        if (!nativePdf) { web.evaluateJavascript("startReadAloud()", null); return; }
        if (ocrBusy) { Toast.makeText(this, "Уже распознаём…", Toast.LENGTH_SHORT).show(); return; }
        if (pdfRenderer == null && !openPdfRenderer()) return;
        ocrBusy = true; Toast.makeText(this, "Распознаём текст…", Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            StringBuilder sb = new StringBuilder(); List<String> pages = new ArrayList<>();
            try {
                synchronized (pdfLock) {
                    if (pdfRenderer == null) { main.post(() -> { ocrBusy = false; }); return; }
                    int n = pdfRenderer.getPageCount();
                    for (int i = 0; i < n; i++) {
                        PdfRenderer.Page page = pdfRenderer.openPage(i);
                        int w = page.getWidth(), h = page.getHeight(); float sc = 1400f / Math.max(1, Math.max(w, h));
                        Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(w * sc)), Math.max(1, Math.round(h * sc)), Bitmap.Config.ARGB_8888);
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close();
                        String t = OcrHelper.recognizeBitmap(ViewerActivity.this, bmp); bmp.recycle();
                        pages.add(t == null ? "" : t);
                        if (t != null && !t.isEmpty()) { if (sb.length() > 0) sb.append("\n\n— стр. ").append(i + 1).append(" —\n\n"); else sb.append("— стр. ").append(i + 1).append(" —\n\n"); sb.append(t); }
                    }
                }
            } catch (Exception ignored) {}
            String text = sb.toString().trim();
            main.post(() -> {
                ocrBusy = false; ocrText = text; ocrPages.clear(); ocrPages.addAll(pages);
                if (text.isEmpty()) { Toast.makeText(this, "Текст на страницах не найден", Toast.LENGTH_LONG).show(); return; }
                if (thenPlay && tts != null) { setReader(true); tts.play(text); }
                if (thenDialog) new AlertDialog.Builder(this).setTitle("Распознанный текст").setMessage(text.length() > 3500 ? text.substring(0, 3500) + "…" : text)
                        .setPositiveButton("Открыть как TXT", (d, w) -> { try { File txt = new File(getCacheDir(), "ocr.txt"); try (FileOutputStream fo = new FileOutputStream(txt)) { fo.write(text.getBytes(StandardCharsets.UTF_8)); } openLocal(this, txt, "ocr.txt", "txt"); } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show(); } })
                        .setNeutralButton("Копировать", (d, w) -> { ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ocr", text)); })
                        .setNegativeButton("Вслух", (d, w) -> { setReader(true); if (tts != null) tts.play(text); }).show();
            });
        });
    }
    private void searchNative() {
        if (ocrText == null || ocrText.isEmpty()) { runOcr(false, false); Toast.makeText(this, "Сначала распознаем текст — повторите поиск", Toast.LENGTH_LONG).show(); return; }
        EditText input = new EditText(this); input.setHint("Найти в PDF");
        new AlertDialog.Builder(this).setTitle("Поиск").setView(input)
                .setPositiveButton("Найти", (d, w) -> {
                    String q = input.getText() == null ? "" : input.getText().toString().trim().toLowerCase(); if (q.isEmpty()) return;
                    for (int i = 0; i < ocrPages.size(); i++) if (ocrPages.get(i).toLowerCase().contains(q)) { pdfZoom.jumpToPage(i); Toast.makeText(this, "Стр. " + (i + 1), Toast.LENGTH_SHORT).show(); return; }
                    Toast.makeText(this, "Не найдено", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Отмена", null).show();
    }
    private void askCloudSave() {
        List<String> opts = new ArrayList<>();
        if (CloudSave.has(this, CloudTrees.GOOGLE)) opts.add("Google Drive (подключённая папка)");
        if (CloudSave.has(this, CloudTrees.YANDEX)) opts.add("Яндекс Диск (подключённая папка)");
        opts.add("Другое место…");
        new AlertDialog.Builder(this).setTitle("Куда сохранить").setItems(opts.toArray(new String[0]), (d, which) -> {
            String sel = opts.get(which);
            if (sel.startsWith("Google")) { pendingCloud = CloudTrees.GOOGLE; exportThenCloud(); }
            else if (sel.startsWith("Яндекс")) { pendingCloud = CloudTrees.YANDEX; exportThenCloud(); }
            else createSaveAs();
        }).show();
    }
    private void exportThenCloud() { if (nativePdf || "pdf".equals(viewerKind) || "ppt".equals(viewerKind)) finishSave(); else web.evaluateJavascript("exportDocument()", null); }
    private void createSaveAs() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(saveMime != null ? saveMime : FileKind.mimeForExt(fileExt.isEmpty() ? viewerKind : fileExt));
        i.putExtra(Intent.EXTRA_TITLE, saveName != null ? saveName : displayName); createDoc.launch(i);
    }
    private byte[] saveBytes() {
        if (saveBuf.size() > 0) return saveBuf.toByteArray();
        if (cacheFile != null && cacheFile.exists()) {
            try (FileInputStream in = new FileInputStream(cacheFile); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); return bo.toByteArray();
            } catch (Exception ignored) {}
        }
        return new byte[0];
    }
    private void finishSave() {
        byte[] data = saveBytes(); if (data.length == 0) { Toast.makeText(this, "Не удалось сохранить", Toast.LENGTH_SHORT).show(); return; }
        if (toolReload) {
            toolReload = false;
            try { if (cacheFile == null) cacheFile = new File(getCacheDir(), "open-" + System.currentTimeMillis()); try (FileOutputStream fo = new FileOutputStream(cacheFile)) { fo.write(data); } ocrText = ""; ocrPages.clear(); if (openPdfRenderer()) paintPdf(); Toast.makeText(this, "Готово. Сохраните, если нужно записать файл.", Toast.LENGTH_LONG).show(); }
            catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
            return;
        }
        if (pendingCloud != null) {
            String mime = saveMime != null ? saveMime : FileKind.mimeForExt(fileExt.isEmpty() ? viewerKind : fileExt);
            String name = saveName != null ? saveName : displayName;
            Uri created = CloudSave.create(this, pendingCloud, mime, name); pendingCloud = null;
            if (created != null && CloudSave.write(this, created, data)) { Toast.makeText(this, "Сохранено в " + name, Toast.LENGTH_SHORT).show(); return; }
            createSaveAs(); return;
        }
        if (source != null && !isNew && writeBytes(source, data)) { Toast.makeText(this, "Сохранено в исходный файл", Toast.LENGTH_SHORT).show(); return; }
        createSaveAs();
    }
    private void writeTo(Uri uri) { if (writeBytes(uri, saveBytes())) Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show(); else Toast.makeText(this, "Не удалось сохранить", Toast.LENGTH_SHORT).show(); }
    private boolean writeBytes(Uri uri, byte[] data) {
        if (uri == null || data == null) return false;
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) { if (out == null) return false; out.write(data); return true; }
        catch (Exception e) { try (OutputStream out = getContentResolver().openOutputStream(uri)) { if (out == null) return false; out.write(data); return true; } catch (Exception e2) { return false; } }
    }
    private File fileForShare() {
        if (saveBuf.size() > 0) {
            String base = displayName == null ? "document" : displayName;
            int s = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
            if (s >= 0) base = base.substring(s + 1);
            if (base.isEmpty()) base = "document";
            File f = new File(getCacheDir(), base);
            try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(saveBuf.toByteArray()); return f; } catch (Exception ignored) {}
        }
        return cacheFile;
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.viewer, menu); return true; }
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_zoomin) { zoom(1); return true; } if (id == R.id.action_zoomout) { zoom(-1); return true; }
        if (id == R.id.action_page) { jumpPage(); return true; } if (id == R.id.action_rotate) { runTool("rotate", "1"); return true; }
        if (id == R.id.action_extract) { askRange("Извлечь страницы", "например 1-3,5", "extract"); return true; }
        if (id == R.id.action_delete) { askRange("Удалить страницы", "например 2,4-6", "delete"); return true; }
        if (id == R.id.action_blank) { askRange("Пустая страница после №", String.valueOf(pdfCount), "blank"); return true; }
        if (id == R.id.action_reverse) { runTool("reverse", "1"); return true; } if (id == R.id.action_numbers) { runTool("numbers", "1"); return true; }
        if (id == R.id.action_ocr) { runOcr(false, true); return true; }
        if (id == R.id.action_find) { if (nativePdf) searchNative(); else web.evaluateJavascript("(function(){var t=prompt('Найти'); if(t){document.getElementById('q').value=t; document.getElementById('findBtn').click();}})()", null); return true; }
        if (id == R.id.action_read) { setReader(!readerOn); return true; } if (id == R.id.action_tts) { startTts(); return true; }
        if (id == R.id.action_save) { pendingCloud = null; toolReload = false; if (nativePdf) finishSave(); else web.evaluateJavascript("exportDocument()", null); return true; }
        if (id == R.id.action_cloud) { askCloudSave(); return true; }
        if (id == R.id.action_share) { ShareHelper.share(this, fileForShare(), FileKind.mimeForExt(fileExt.isEmpty() ? viewerKind : fileExt), displayName); return true; }
        if (id == R.id.action_print) { ShareHelper.print(this, fileForShare(), displayName); return true; }
        if (id == R.id.action_night) { ThemePrefs.toggle(this); web.evaluateJavascript("toggleNight()", null); return true; }
        if (id == R.id.action_bookmark) { if (nativePdf) nativeBookmarks(); else web.evaluateJavascript("showBookmarks()", null); return true; }
        return super.onOptionsItemSelected(item);
    }
    private void nativeBookmarks() {
        List<Integer> b = Bookmarks.load(this, displayName);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.size(); i++) { if (i > 0) sb.append(", "); sb.append(b.get(i) + 1); }
        EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setHint("№ страницы для добавления");
        new AlertDialog.Builder(this).setTitle("Закладки").setMessage(sb.length() > 0 ? "Страницы: " + sb : "Пока нет закладок")
                .setView(input)
                .setPositiveButton("Добавить", (d, w) -> {
                    try {
                        int p = Integer.parseInt(input.getText().toString().trim());
                        if (p < 1 || p > pdfCount) { Toast.makeText(this, "Страницы нет: " + p, Toast.LENGTH_SHORT).show(); return; }
                        if (b.contains(p)) { Toast.makeText(this, "Уже есть", Toast.LENGTH_SHORT).show(); return; }
                        b.add(p); java.util.Collections.sort(b); Bookmarks.save(this, displayName, b);
                        Toast.makeText(this, "Добавлена стр. " + p, Toast.LENGTH_SHORT).show();
                        pdfZoom.jumpToPage(p - 1);
                    } catch (Exception e) { Toast.makeText(this, "Введите номер страницы", Toast.LENGTH_SHORT).show(); }
                })
                .setNeutralButton("Удалить все", (d, w) -> { Bookmarks.save(this, displayName, new ArrayList<>()); Toast.makeText(this, "Закладки удалены", Toast.LENGTH_SHORT).show(); })
                .setNegativeButton("Закрыть", null)
                .show();
    }
    @Override public void onBackPressed() { if (readerOn) { setReader(false); return; } super.onBackPressed(); }
    @Override protected void onPause() { super.onPause(); if (tts != null) tts.pause(); }
    @Override protected void onDestroy() { worker.shutdownNow(); if (tts != null) tts.shutdown(); synchronized (pdfLock) { closePdfLocked(); } super.onDestroy(); }
    public class Bridge {
        @JavascriptInterface public String kind() { return viewerKind; }
        @JavascriptInterface public String fileExt() { return fileExt; }
        @JavascriptInterface public String fileName() { return displayName; }
        @JavascriptInterface public boolean isNew() { return isNew; }
        @JavascriptInterface public boolean isNight() { return ThemePrefs.isNight(ViewerActivity.this); }
        @JavascriptInterface public String toolCmd() { return toolCmd; }
        @JavascriptInterface public String extractSpec() { return extractSpec; }
        @JavascriptInterface public String getBookmarks() {
            List<Integer> b = Bookmarks.load(ViewerActivity.this, displayName); StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < b.size(); i++) { if (i > 0) sb.append(','); sb.append(b.get(i)); } return sb.append(']').toString();
        }
        @JavascriptInterface public void setBookmarks(String json) {
            List<Integer> out = new ArrayList<>();
            if (json != null) for (String p : json.replace("[", "").replace("]", "").split(",")) try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignored) {}
            Bookmarks.save(ViewerActivity.this, displayName, out);
        }
        @JavascriptInterface public void beginSave(String mime, String name) { saveBuf.reset(); saveMime = mime; saveName = name; }
        @JavascriptInterface public void appendChunk(String chunk) { if (chunk != null && !chunk.isEmpty()) try { saveBuf.write(Base64.decode(chunk, Base64.DEFAULT)); } catch (Exception ignored) {} }
        @JavascriptInterface public void endSave() { runOnUiThread(ViewerActivity.this::finishSave); }
        @JavascriptInterface public void ready() { runOnUiThread(() -> main.postDelayed(() -> { if (webZoom == null || nativePdf) return; int h = Math.round(web.getContentHeight() * getResources().getDisplayMetrics().density); webZoom.setChildHeight(Math.max(webZoom.getHeight(), h)); }, 200)); }
        @JavascriptInterface public void fail(String m) { runOnUiThread(() -> Toast.makeText(ViewerActivity.this, m == null ? "Ошибка" : m, Toast.LENGTH_LONG).show()); }
        @JavascriptInterface public void onReadText(String t) { runOnUiThread(() -> { if (tts != null) tts.play(t); }); }
        @JavascriptInterface public void visualZoom(String factor) { try { float f = Float.parseFloat(factor); runOnUiThread(() -> { if (webZoom != null) webZoom.zoomBy(f); }); } catch (Exception ignored) {} }
    }
}
