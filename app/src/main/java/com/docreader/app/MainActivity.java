package com.docreader.app;
import android.content.Intent; import android.net.Uri; import android.os.Bundle;
import android.view.Gravity; import android.view.View; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
public class MainActivity extends AppCompatActivity {
    private LinearLayout recentList; private View emptyBox; private ActivityResultLauncher<Intent> openDoc;
    private ActivityResultLauncher<String> askNotifications;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        MaterialToolbar tb = findViewById(R.id.toolbar); setSupportActionBar(tb);
        recentList = findViewById(R.id.recentList); emptyBox = findViewById(R.id.emptyBox);
        openDoc = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> { if (res.getData() != null && res.getData().getData() != null) openUri(res.getData().getData()); });
        askNotifications = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { if (granted) UpdateManager.schedule(this); });
        findViewById(R.id.btnOpen).setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel.sheet.macroEnabled.12","application/vnd.ms-excel.sheet.binary.macroEnabled.12","application/vnd.oasis.opendocument.spreadsheet","application/zip","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","text/plain","text/csv","*/*"}); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); openDoc.launch(i); });
        findViewById(R.id.btnNewWord).setOnClickListener(v -> { Intent i = new Intent(this, ViewerActivity.class); i.putExtra("isNew", true); i.putExtra("name", "Документ.docx"); i.putExtra("kind", "docx"); startActivity(i); });
        findViewById(R.id.btnNewExcel).setOnClickListener(v -> { Intent i = new Intent(this, ViewerActivity.class); i.putExtra("isNew", true); i.putExtra("name", "Таблица.xlsx"); i.putExtra("kind", "xlsx"); startActivity(i); });
        findViewById(R.id.btnScan).setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)));
        findViewById(R.id.btnMerge).setOnClickListener(v -> startActivity(new Intent(this, MergeActivity.class)));
        findViewById(R.id.btnCloud).setOnClickListener(v -> startActivity(new Intent(this, CloudActivity.class)));
        findViewById(R.id.btnNight).setOnClickListener(v -> { ThemePrefs.toggle(this); recreate(); });
        findViewById(R.id.btnDefault).setOnClickListener(v -> DefaultApps.prompt(this));
        findViewById(R.id.btnAbout).setOnClickListener(v -> openAbout());
        findViewById(R.id.btnCall).setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:+375293371412"))); } catch (Exception e) { Toast.makeText(this, "+375293371412", Toast.LENGTH_LONG).show(); } });
        tb.setOnLongClickListener(v -> { openAbout(); return true; });
        handleIntent(getIntent());
        askAboutCrash();
        askNotifications();
    }

    /** Файл могут прислать по-разному: «Открыть с помощью» (VIEW) или «Поделиться» (SEND). */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
            if (uri == null) uri = firstUri(intent.getClipData());
        } else if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            uri = streamUri(intent);
            if (uri == null) uri = firstUri(intent.getClipData());
        }
        if (uri != null) openUri(uri);
    }

    /** Первая ссылка из набора (мессенджеры кладут файлы в ClipData). */
    private static Uri firstUri(android.content.ClipData clip) {
        try {
            if (clip == null || clip.getItemCount() == 0) return null;
            return clip.getItemAt(0).getUri();
        } catch (Throwable t) { return null; }
    }

    /**
     * Ссылка на файл из EXTRA_STREAM. Читаем через extras без приведения типов:
     * некоторые приложения кладут туда список (ArrayList&lt;Uri&gt;), и обычный
     * getParcelableExtra() падает с ClassCastException.
     */
    private static Uri streamUri(Intent intent) {
        try {
            android.os.Bundle extras = intent.getExtras();
            if (extras == null) return null;
            Object raw = extras.get(Intent.EXTRA_STREAM);
            if (raw instanceof Uri) return (Uri) raw;
            if (raw instanceof java.util.List) {
                for (Object o : (java.util.List<?>) raw) if (o instanceof Uri) return (Uri) o;
            }
            if (raw instanceof String) return Uri.parse((String) raw);
        } catch (Throwable ignored) { }
        return null;
    }
    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) { getMenuInflater().inflate(R.menu.main, menu); return true; }
    @Override public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_update) { UpdateManager.check(this); return true; }
        if (id == R.id.action_about) { openAbout(); return true; }
        return super.onOptionsItemSelected(item);
    }
    /** Экран «О программе»: версия, обновление, контакты и частые вопросы. */
    private void openAbout() { startActivity(new Intent(this, AboutActivity.class)); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }
    @Override protected void onResume() { super.onResume(); fillRecent(); UpdateManager.onForeground(this); }

    /**
     * Android 13+ требует разрешение на уведомления — без него приложение не
     * сможет сообщить, что обновление скачалось в фоне.
     */
    private void askNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;
        if (!UpdateManager.isAutoEnabled(this)) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        final android.content.SharedPreferences p = getSharedPreferences("update", 0);
        if (p.getBoolean("asked_notifications", false)) return;
        p.edit().putBoolean("asked_notifications", true).apply();
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Уведомления об обновлениях")
                .setMessage("Разрешить уведомления? Приложение сообщит, когда новая версия скачается в фоне — останется подтвердить установку.")
                .setPositiveButton("Разрешить", (d, w) -> { try { askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS); } catch (Throwable ignored) { } })
                .setNegativeButton("Не сейчас", null)
                .show();
    }
    private void openUri(Uri uri) {
        try {
            openUriChecked(uri);
        } catch (Throwable t) {
            CrashGuard.log(this, "открытие файла " + uri, t);
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_LONG).show();
        }
    }

    private void openUriChecked(Uri uri) {
        if (uri == null) return;
        // file:// нельзя передавать между окнами — Android 11+ роняет приложение
        // (FileUriExposedException). Копируем такой файл в свою папку и работаем
        // с безопасной ссылкой FileProvider.
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        if ("file".equals(scheme)) {
            Uri safe = copyLocalToCache(uri);
            if (safe == null) {
                Toast.makeText(this, "Нет доступа к файлу — выберите его кнопкой «Открыть»", Toast.LENGTH_LONG).show();
                return;
            }
            openUriChecked(safe);
            return;
        }
        if (!"content".equals(scheme) && !"android.resource".equals(scheme)) {
            Toast.makeText(this, getString(R.string.unsupported), Toast.LENGTH_LONG).show();
            return;
        }
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        String name = FileKind.name(this, uri);
        String kind = FileKind.fromNameAndMime(name, FileKind.mime(this, uri));
        java.io.File copy = null;
        if (FileKind.UNKNOWN.equals(kind)) {
            // незнакомый тип — определяем по содержимому (копия пригодится и просмотрщику)
            copy = copyToCache(uri, 40 * 1024 * 1024L);
            if (copy != null) {
                kind = FileKind.sniff(copy);
                if (FileKind.UNKNOWN.equals(kind)) { try { copy.delete(); } catch (Exception ignored) { } copy = null; }
            }
        }
        if (FileKind.UNKNOWN.equals(kind)) {
            Toast.makeText(this, getString(R.string.unsupported), Toast.LENGTH_LONG).show();
            return;
        }
        Uri pass = uri;
        if (copy != null) {
            try { pass = androidx.core.content.FileProvider.getUriForFile(this, "com.docreader.app.files", copy); }
            catch (Exception e) { copy = null; }
        }
        RecentStore.add(this, pass, name, kind);
        Intent i = new Intent(this, ViewerActivity.class);
        i.setData(pass); i.putExtra("name", name); i.putExtra("kind", kind);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivity(i);
    }

    /** Копирует содержимое в кэш приложения, возвращает безопасную ссылку. */
    private Uri copyLocalToCache(Uri fileUri) {
        try {
            String path = fileUri.getPath();
            if (path == null) return null;
            java.io.File src = new java.io.File(path);
            if (!src.exists() || !src.isFile() || !src.canRead()) return null;
            java.io.File dst = new java.io.File(getCacheDir(), "in-" + System.currentTimeMillis() + "-" + FileKind.safeFileName(src.getName()));
            try (java.io.InputStream in = new java.io.FileInputStream(src); java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                byte[] b = new byte[65536]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            return androidx.core.content.FileProvider.getUriForFile(this, "com.docreader.app.files", dst);
        } catch (Throwable t) {
            CrashGuard.log(this, "копирование file://-файла", t);
            return null;
        }
    }

    /** Копия файла из content:// в кэш (только если он не больше limit). */
    private java.io.File copyToCache(Uri uri, long limit) {
        java.io.File dst = new java.io.File(getCacheDir(), "in-" + System.currentTimeMillis());
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            if (in == null) { dst.delete(); return null; }
            byte[] b = new byte[65536]; long total = 0; int n;
            while ((n = in.read(b)) > 0) {
                total += n;
                if (total > limit) { dst.delete(); return null; } // слишком большой — не копируем
                out.write(b, 0, n);
            }
            return dst.length() > 0 ? dst : null;
        } catch (Throwable t) {
            CrashGuard.log(this, "чтение файла " + uri, t);
            try { dst.delete(); } catch (Exception ignored) { }
            return null;
        }
    }

    /** После сбоя предлагаем отправить отчёт разработчику. */
    private void askAboutCrash() {
        if (!CrashGuard.hasCrashReport(this)) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Приложение закрылось с ошибкой")
                .setMessage("В прошлый раз ZI Office завершился с ошибкой. Отправить отчёт разработчику?")
                .setPositiveButton("Отправить", (d, w) -> startActivity(new Intent(this, CrashActivity.class)))
                .setNegativeButton("Позже", null)
                .show();
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
