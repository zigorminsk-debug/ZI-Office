package com.docreader.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Автообновление через GitHub: приложение смотрит самый свежий релиз в
 * ПУБЛИЧНОМ репозитории {@link #RELEASE_REPO} (в нём лежат готовые APK),
 * сравнивает номер версии со своей и предлагает скачать/установить.
 *
 * Сборка и публикация релизов — в .github/workflows/release.yml
 * (срабатывает по тегу вида v2.24). Инструкция — в АВТООБНОВЛЕНИЕ.md.
 */
final class UpdateManager {
    /** Публичный репозиторий, куда GitHub Actions публикует APK-релизы.
     *  Значение приходит из app/build.gradle (buildConfigField RELEASE_REPO,
     *  можно переопределить сборкой: -PreleaseRepo=владелец/репозиторий). */
    static final String RELEASE_REPO = BuildConfig.RELEASE_REPO;
    private static final String LATEST_URL = "https://api.github.com/repos/" + RELEASE_REPO + "/releases/latest";
    private static final String PREFS = "update";
    private static final long CHECK_INTERVAL_MS = 6L * 3600 * 1000L; // раз в 6 часов
    private static volatile Handler mainHandler;
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    /** Лениво: класс можно инициализировать и без Android-окружения (юнит-тесты). */
    private static Handler main() {
        Handler h = mainHandler;
        if (h == null) { h = new Handler(Looper.getMainLooper()); mainHandler = h; }
        return h;
    }

    /** Автопроверка при запуске/возврате в главное окно (не чаще раза в 6 ч). */
    static void checkIfDue(final Activity act) {
        long last = act.getSharedPreferences(PREFS, 0).getLong("last_check", 0L);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return;
        check(act);
    }

    /** Проверить наличие новой версии и при необходимости показать диалог. */
    static void check(final Activity act) {
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            String tag = null, notes = null, apkUrl = null;
            boolean answered = false;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(LATEST_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "ZI-Office-Update");
                c.setRequestProperty("Accept", "application/vnd.github+json");
                int code = c.getResponseCode();
                answered = true; // сервер ответил — проверку можно считать состоявшейся
                if (code == 200) {
                    try (InputStream in = c.getInputStream()) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] b = new byte[8192]; int n;
                        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                        JSONObject o = new JSONObject(new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                        tag = o.optString("tag_name");
                        notes = o.optString("body");
                        JSONArray assets = o.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject a = assets.optJSONObject(i);
                                if (a == null) continue;
                                String fn = a.optString("name");
                                if (fn != null && fn.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                                    apkUrl = a.optString("browser_download_url");
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            busy.set(false);
            // Отметку времени ставим всегда, когда сервер ответил: иначе при
            // отсутствии новых версий GitHub дёргался бы при каждом запуске,
            // хотя обещан интервал раз в 6 часов.
            if (answered) act.getSharedPreferences(PREFS, 0).edit().putLong("last_check", System.currentTimeMillis()).apply();
            if (tag == null || apkUrl == null || !isSafeHost(apkUrl)) return;
            String remote = tag.startsWith("v") ? tag.substring(1) : tag;
            String local = BuildConfig.VERSION_NAME;
            if (!isNewer(remote, local)) return;
            final String ver = remote;
            final String notesFinal = notes;
            final String urlFinal = apkUrl;
            main().post(() -> {
                if (act.isFinishing() || act.isDestroyed()) return;
                String msg = "Доступна версия " + ver + " (установлена " + local + ").\n\n"
                        + "Нажмите «Скачать» — файл загрузится, после чего система сама "
                        + "предложит установить обновление.";
                if (notesFinal != null) {
                    String clean = notesFinal.replaceAll("(?s)<!--.*?-->", "").trim();
                    if (clean.length() > 500) clean = clean.substring(0, 500) + "…";
                    if (!clean.isEmpty()) msg += "\n\n" + clean;
                }
                new AlertDialog.Builder(act)
                        .setTitle("Обновление ZI Office")
                        .setMessage(msg)
                        .setPositiveButton("Скачать", (d, w) -> download(act, urlFinal, ver))
                        .setNegativeButton("Позже", null)
                        .show();
            });
        }).start();
    }

    private static void download(final Activity act, final String urlStr, final String ver) {
        final File apk = new File(act.getCacheDir(), "ZIOffice-" + ver + ".apk");
        final FrameLayout box = new FrameLayout(act);
        float d = act.getResources().getDisplayMetrics().density;
        final ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = blp.rightMargin = (int) (24 * d);
        bar.setLayoutParams(blp);
        box.addView(bar);
        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("Скачивание " + ver)
                .setMessage("Загрузка… 0%")
                .setView(box)
                .setCancelable(false)
                .show();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(urlStr).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "ZI-Office-Update");
                int code = c.getResponseCode();
                if (code != 200) {
                    main().post(() -> finishDialog(act, dlg, apk, "Не удалось скачать: HTTP " + code));
                    return;
                }
                long total = c.getContentLengthLong();
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(apk)) {
                    byte[] b = new byte[65536]; long done = 0; int n, shownPct = -1;
                    while ((n = in.read(b)) > 0) {
                        fo.write(b, 0, n); done += n;
                        if (total > 0) {
                            final int p = (int) (done * 100 / total);
                            // обновляем текст не чаще, чем меняется процент
                            if (p != shownPct) {
                                shownPct = p;
                                main().post(() -> { if (dlg.isShowing()) dlg.setMessage("Загрузка… " + p + "%"); });
                            }
                        }
                    }
                }
                main().post(() -> startInstall(act, dlg, apk, ver));
            } catch (Exception e) {
                main().post(() -> finishDialog(act, dlg, apk, "Не удалось скачать. Проверьте интернет."));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private static void finishDialog(Activity act, AlertDialog dlg, File apk, String msg) {
        try { if (apk != null) apk.delete(); } catch (Exception ignored) {}
        try { if (dlg.isShowing()) dlg.dismiss(); } catch (Exception ignored) {}
        if (!act.isFinishing() && !act.isDestroyed()) Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
    }

    private static void startInstall(Activity act, AlertDialog dlg, File apk, String ver) {
        try { if (dlg.isShowing()) dlg.dismiss(); } catch (Exception ignored) {}
        if (act.isFinishing() || act.isDestroyed()) return;
        if (apk.length() < 100 * 1024L) {
            Toast.makeText(act, "Скачанный файл слишком мал — повторите позже", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(act, "com.docreader.app.files", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(Intent.createChooser(i, "Установить ZI Office " + ver));
        } catch (Exception e) {
            Toast.makeText(act, "Не удалось открыть установщик. Файл: " + apk.getPath(), Toast.LENGTH_LONG).show();
        }
    }

    /** Разрешены только официальные домены GitHub. */
    private static boolean isSafeHost(String urlStr) {
        try {
            String h = new URL(urlStr).getHost();
            return h != null && (h.equals("github.com") || h.endsWith(".github.com")
                    || h.equals("githubusercontent.com") || h.endsWith(".githubusercontent.com"));
        } catch (Exception e) { return false; }
    }

    /** Сравнение номеров версий вида 2.24 / 2.10.1 по частям.
     *  Пустые/несуффиксные части игнорируются: "v2.24" == 2.24, "2.24-beta" → 2.24. */
    static boolean isNewer(String remote, String local) {
        String[] a = parts(remote);
        String[] b = parts(local);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? part(a[i]) : 0;
            int y = i < b.length ? part(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static String[] parts(String s) {
        if (s == null) s = "";
        String[] raw = s.split("[.\\-+v ]");
        String[] out = new String[raw.length];
        int n = 0;
        for (String p : raw) if (!p.isEmpty()) out[n++] = p;
        return java.util.Arrays.copyOf(out, n);
    }

    private static int part(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
