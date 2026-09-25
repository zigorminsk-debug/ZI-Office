package com.docreader.app;
import android.content.Intent; import android.net.Uri; import android.os.Bundle;
import android.view.Gravity; import android.view.View; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
public class MainActivity extends AppCompatActivity {
    private LinearLayout recentList; private View emptyBox; private ActivityResultLauncher<Intent> openDoc;
    private ActivityResultLauncher<String> askNotifications;
    /** Файлы больше этого размера не копируем — открываем по исходной ссылке. */
    private static final long MAX_LOCAL_COPY = 64L * 1024 * 1024;
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
        new Thread(this::cleanupInbox).start();   // старые копии не нужны
    }

    /** Файл могут прислать по-разному: «Открыть с помощью» (VIEW) или «Поделиться» (SEND). */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        // в журнал — что именно прислал мессенджер: по отчёту видно причину
        try {
            CrashGuard.log(this, "получен файл: action=" + action + " | тип=" + intent.getType()
                    + " | ссылка=" + intent.getData()
                    + " | вложений=" + (intent.getClipData() == null ? 0 : intent.getClipData().getItemCount()));
        } catch (Throwable ignored) { }
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
    /** Результат подготовки файла: либо готовый экран просмотра, либо причина отказа. */
    private static final class OpenResult {
        final Intent intent; final Uri uri; final String name; final String kind;
        final String title; final String details;
        OpenResult(Intent intent, Uri uri, String name, String kind, String title, String details) {
            this.intent = intent; this.uri = uri; this.name = name; this.kind = kind;
            this.title = title; this.details = details;
        }
        static OpenResult problem(String title, String details) {
            return new OpenResult(null, null, null, null, title, details);
        }
    }

    /**
     * Открытие файла, пришедшего из другого приложения (мессенджер, файловый
     * менеджер, «Поделиться»).
     *
     * Доступ к такому файлу даётся временно и может пропасть, а ссылку file://
     * нельзя передавать между окнами (Android 11+ закрывает за это приложение).
     * Поэтому сразу делаем свою копию в папке приложения и дальше работаем
     * только с ней — файл открывается всегда, в том числе из «недавних».
     *
     * Копируем в фоновом потоке: провайдер мессенджера может отдавать файл
     * медленно (расшифровка, загрузка из сети), и в главном потоке это
     * выглядело бы как «приложение не отвечает».
     */
    private void openUri(final Uri uri) {
        if (uri == null) return;
        String scheme;
        try { scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT); }
        catch (Throwable t) { scheme = ""; }
        final boolean local = "file".equals(scheme);
        if (!local && !"content".equals(scheme) && !"android.resource".equals(scheme)) {
            openProblem("Файл не открывается", "Ссылка вида «" + scheme + "» не поддерживается.\n\nОткройте файл кнопкой «Открыть».");
            return;
        }
        if (!local) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        }

        final android.app.ProgressDialog wait = new android.app.ProgressDialog(this);
        wait.setMessage("Открываю файл…");
        wait.setCancelable(false);
        try { wait.show(); } catch (Throwable ignored) { }

        new Thread(() -> {
            OpenResult r;
            try {
                r = prepareOpen(uri);
            } catch (Throwable t) {
                CrashGuard.log(this, "подготовка файла " + uri, t);
                r = OpenResult.problem("Не удалось открыть файл", "Файл: " + uri + "\n\nОшибка: " + t);
            }
            final OpenResult res = r;
            runOnUiThread(() -> {
                try { if (wait.isShowing()) wait.dismiss(); } catch (Throwable ignored) { }
                if (isFinishing() || isDestroyed()) return;
                if (res.intent == null) { openProblem(res.title, res.details); return; }
                try { RecentStore.add(this, res.uri, res.name, res.kind); } catch (Throwable ignored) { }
                CrashGuard.log(this, "открытие: " + res.name + " | тип=" + res.kind + " | ссылка=" + res.uri);
                try {
                    startActivity(res.intent);
                } catch (Throwable t) {
                    CrashGuard.log(this, "запуск просмотра " + res.name, t);
                    openProblem("Не удалось открыть документ", "Файл: " + res.name + "\n\nОшибка: " + t);
                }
            });
        }, "open-file").start();
    }

    /** Разбор файла и копирование — выполняется в фоновом потоке. */
    private OpenResult prepareOpen(Uri uri) {
        String name; String mime;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            name = lastSegment(uri);
            mime = null;
        } else {
            name = FileKind.name(this, uri);
            mime = FileKind.mime(this, uri);
        }
        String kind = FileKind.fromNameAndMime(name, mime);

        java.io.File copy = copyToInbox(uri, name);   // своя копия: см. openUri()
        if (copy == null && "file".equalsIgnoreCase(uri.getScheme())) {
            return OpenResult.problem("Нет доступа к файлу",
                    "Файл: " + name + "\n\nAndroid не даёт читать файлы по прямой ссылке file://. "
                            + "Откройте его кнопкой «Открыть» — так доступ сохранится.");
        }
        if (copy != null && FileKind.UNKNOWN.equals(kind)) kind = FileKind.sniff(copy);

        if (FileKind.UNKNOWN.equals(kind)) {
            if (copy != null) { try { copy.delete(); } catch (Exception ignored) { } }
            return OpenResult.problem("Формат не поддерживается",
                    "Файл: " + name + "\nТип: " + (mime == null || mime.isEmpty() ? "неизвестен" : mime)
                            + "\n\nПоддерживаются PDF, Word, Excel, PowerPoint, RTF, TXT и CSV. "
                            + "Файлы Numbers/WPS сохраните как .xlsx или .docx, с файлов с паролем снимите пароль.");
        }
        if (FileKind.ext(name).isEmpty() && !"txt".equals(kind)) name = name + "." + kind;

        Uri pass = uri;
        if (copy != null) {
            try { pass = androidx.core.content.FileProvider.getUriForFile(this, "com.docreader.app.files", copy); }
            catch (Throwable t) { CrashGuard.log(this, "FileProvider для " + copy, t); }
        }
        Intent i = new Intent(this, ViewerActivity.class);
        i.setData(pass); i.putExtra("name", name); i.putExtra("kind", kind);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        return new OpenResult(i, pass, name, kind, null, null);
    }

    /** Последний сегмент ссылки как имя файла. */
    private static String lastSegment(Uri uri) {
        String seg = uri.getLastPathSegment();
        return seg == null || seg.isEmpty() ? "file" : seg;
    }

    /** Копирует файл в свою папку (до 64 МБ). Возвращает файл или null. */
    private java.io.File copyToInbox(Uri uri, String name) {
        java.io.File dst = null;
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "inbox");
            if (!dir.exists() && !dir.mkdirs()) return null;
            dst = new java.io.File(dir, System.currentTimeMillis() + "-" + FileKind.safeFileName(name));
            java.io.InputStream in = openStream(uri);
            if (in == null) return null;
            try (java.io.InputStream input = in; java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
                byte[] b = new byte[65536]; long total = 0; int n;
                while ((n = input.read(b)) > 0) {
                    total += n;
                    if (total > MAX_LOCAL_COPY) { dst.delete(); return null; } // очень большой — открываем по ссылке
                    out.write(b, 0, n);
                }
            }
            if (dst.length() == 0) { dst.delete(); return null; }
            return dst;
        } catch (Throwable t) {
            CrashGuard.log(this, "копирование файла " + name + " (" + uri + ")", t);
            if (dst != null) { try { dst.delete(); } catch (Exception ignored) { } }
            return null;
        }
    }

    private java.io.InputStream openStream(Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path == null) return null;
            java.io.File f = new java.io.File(path);
            if (!f.exists() || !f.isFile() || !f.canRead()) return null;
            return new java.io.FileInputStream(f);
        }
        return getContentResolver().openInputStream(uri);
    }

    /** Копии в «inbox» не должны копиться бесконечно: держим 25 последних. */
    private void cleanupInbox() {
        try {
            java.io.File[] files = new java.io.File(getFilesDir(), "inbox").listFiles();
            if (files == null || files.length <= 25) return;
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 25; i < files.length; i++) files[i].delete();
        } catch (Throwable ignored) { }
    }

    /** Понятное объяснение вместо «молча не открылось» + возможность прислать отчёт. */
    private void openProblem(String title, String details) {
        CrashGuard.log(this, title + ": " + details);
        try {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(details)
                    .setPositiveButton("Отправить отчёт", (d, w) -> startActivity(new Intent(this, CrashActivity.class)))
                    .setNegativeButton("OK", null)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, title, Toast.LENGTH_LONG).show();
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
