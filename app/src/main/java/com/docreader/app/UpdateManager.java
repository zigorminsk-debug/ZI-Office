package com.docreader.app;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Автообновление через GitHub.
 *
 * Проверка и загрузка работают без открытого приложения: периодическое задание
 * ({@link UpdateJobService}, раз в 6 часов) смотрит последний релиз в публичном
 * репозитории {@link #RELEASE_REPO}, скачивает APK и показывает уведомление
 * «Обновление готово». Пользователю остаётся подтвердить установку — Android
 * не разрешает обычным приложениям ставить APK полностью без подтверждения.
 *
 * Когда приложение открыто, всё происходит сразу: проверка → загрузка →
 * предложение установить (см. {@link #onForeground(Activity)}).
 *
 * Сборка и публикация релизов — в .github/workflows/release.yml,
 * инструкция — в АВТООБНОВЛЕНИЕ.md.
 */
final class UpdateManager {
    /** Публичный репозиторий, куда GitHub Actions публикует APK-релизы. */
    static final String RELEASE_REPO = BuildConfig.RELEASE_REPO;
    private static final String LATEST_URL = "https://api.github.com/repos/" + RELEASE_REPO + "/releases/latest";
    private static final String PREFS = "update";
    private static final long CHECK_INTERVAL_MS = 6L * 3600 * 1000L; // раз в 6 часов
    static final int JOB_ID = 33714;
    private static final String CHANNEL_ID = "updates";
    private static final int NOTIFY_READY_ID = 3371;
    private static final int NOTIFY_PROGRESS_ID = 3370;
    private static final long MAX_APK_BYTES = 200L * 1024 * 1024;

    private static volatile Handler mainHandler;
    private static final AtomicBoolean busy = new AtomicBoolean(false);

    private static Handler main() {
        Handler h = mainHandler;
        if (h == null) { h = new Handler(Looper.getMainLooper()); mainHandler = h; }
        return h;
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, 0); }

    // ---------------------------------------------------------------- настройки

    /** Автообновление включено? По умолчанию — да. */
    static boolean isAutoEnabled(Context ctx) { return prefs(ctx).getBoolean("auto", true); }

    /** Включить/выключить автообновление (фоновая проверка и авто-загрузка). */
    static void setAutoEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("auto", enabled).apply();
        if (enabled) schedule(ctx); else cancelSchedule(ctx);
    }

    // ------------------------------------------------------- фоновая проверка

    /** Поставить периодическую проверку обновлений (раз в 6 часов, без открытия приложения). */
    static void schedule(Context ctx) {
        if (!isAutoEnabled(ctx)) { cancelSchedule(ctx); return; }
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, UpdateJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)                       // задание переживёт перезагрузку
                    .setPeriodic(CHECK_INTERVAL_MS, 15 * 60 * 1000L)
                    .build();
            js.schedule(job);
        } catch (Throwable t) {
            CrashGuard.log(ctx, "планирование проверки обновлений", t);
        }
    }

    static void cancelSchedule(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_ID);
        } catch (Throwable ignored) { }
    }

    /**
     * Проверка и загрузка «молча» — вызывается из JobService, приложение может
     * быть закрыто. Результат: уведомление «обновление готово».
     */
    static void backgroundCheck(Context ctx) {
        if (!isAutoEnabled(ctx)) return;
        Result r = fetch(ctx);
        if (r.version == null || r.apkUrl == null || !isSafeHost(r.apkUrl)) return;
        if (!isNewer(r.version, BuildConfig.VERSION_NAME)) return;
        if (isReady(ctx, r.version)) { notifyReady(ctx, r.version); return; } // уже скачано раньше
        File apk = downloadQuiet(ctx, r.version, r.apkUrl);
        if (apk == null) return;
        prefs(ctx).edit().putString("ready_version", r.version)
                .putString("ready_path", apk.getAbsolutePath()).apply();
        notifyReady(ctx, r.version);
    }

    /** Скачивание без диалогов, с уведомлением о прогрессе. Возвращает файл или null. */
    private static File downloadQuiet(Context ctx, String ver, String url) {
        File dir = new File(ctx.getFilesDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File apk = new File(dir, "ZIOffice-" + ver + ".apk");
        HttpURLConnection c = null;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock lock = null;
            if (pm != null) {
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zi:update");
                lock.setReferenceCounted(false);
                try { lock.acquire(10 * 60 * 1000L); } catch (Throwable ignored) { }
            }
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestProperty("User-Agent", "ZI-Office-Update");
                if (c.getResponseCode() != 200) return null;
                long total = c.getContentLengthLong();
                if (total > MAX_APK_BYTES) return null;
                int lastPct = -1;
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(apk)) {
                    byte[] b = new byte[65536]; long done = 0; int n;
                    while ((n = in.read(b)) > 0) {
                        fo.write(b, 0, n); done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != lastPct && pct % 10 == 0) { lastPct = pct; notifyProgress(ctx, ver, pct); }
                        }
                    }
                }
            } finally {
                if (lock != null && lock.isHeld()) { try { lock.release(); } catch (Throwable ignored) { } }
            }
            cancelProgress(ctx);
            return apk.length() > 1024 * 1024L ? apk : null; // слишком маленький файл — не APK
        } catch (Throwable t) {
            CrashGuard.log(ctx, "загрузка обновления " + ver, t);
            cancelProgress(ctx);
            try { apk.delete(); } catch (Throwable ignored) { }
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Обновление уже скачано и готово к установке? */
    static boolean isReady(Context ctx, String version) {
        File f = downloadedFile(ctx);
        if (f == null) return false;
        String ver = prefs(ctx).getString("ready_version", null);
        if (ver == null || !ver.equals(version)) return false;
        return isNewer(ver, BuildConfig.VERSION_NAME);
    }

    /** Скачанный APK обновления (или null). */
    static File downloadedFile(Context ctx) {
        String path = prefs(ctx).getString("ready_path", null);
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists() || f.length() < 1024 * 1024L) return null;
        return f;
    }

    static String downloadedVersion(Context ctx) { return prefs(ctx).getString("ready_version", null); }

    /** Забыть про скачанное обновление (например, установили вручную). */
    static void clearDownloaded(Context ctx) {
        File f = downloadedFile(ctx);
        if (f != null) { try { f.delete(); } catch (Throwable ignored) { } }
        prefs(ctx).edit().remove("ready_version").remove("ready_path").apply();
        cancelNotifications(ctx);
    }

    // --------------------------------------------------------- главное окно

    /**
     * Вызывается, когда приложение вышло на передний план: если обновление
     * уже скачано — предлагаем установить; иначе проверяем не чаще раза в 6 ч.
     */
    static void onForeground(final Activity act) {
        if (downloadedFile(act) != null && isReady(act, downloadedVersion(act))) {
            promptInstall(act);
            return;
        }
        checkIfDue(act);
    }

    /** «Обновление готово — установить?» */
    static void promptInstall(final Activity act) {
        final String ver = downloadedVersion(act);
        if (ver == null || downloadedFile(act) == null) return;
        if (act.isFinishing() || act.isDestroyed()) return;
        new AlertDialog.Builder(act)
                .setTitle("Обновление ZI Office " + ver)
                .setMessage("Новая версия уже скачана. Установить сейчас? Данные и настройки сохранятся.")
                .setPositiveButton("Установить", (d, w) -> installDownloaded(act))
                .setNegativeButton("Позже", null)
                .show();
    }

    /** Запустить установку скачанного обновления (или объяснить, чего не хватает). */
    static void installDownloaded(final Activity act) {
        File apk = downloadedFile(act);
        if (apk == null) { Toast.makeText(act, "Файл обновления не найден", Toast.LENGTH_SHORT).show(); return; }
        if (!ApkInstaller.allowed(act)) {
            new AlertDialog.Builder(act)
                    .setTitle("Разрешите установку обновлений")
                    .setMessage("Android требует один раз разрешить ZI Office устанавливать приложения. "
                            + "В открывшемся окне включите «Разрешить установку из этого источника», затем вернитесь и нажмите «Установить».")
                    .setPositiveButton("Открыть настройки", (d, w) -> ApkInstaller.openSettings(act))
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }
        if (!ApkInstaller.install(act, apk)) {
            Toast.makeText(act, "Не удалось запустить установщик", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ проверка

    /** Автопроверка при запуске (не чаще раза в 6 ч). */
    static void checkIfDue(final Activity act) {
        long last = prefs(act).getLong("last_check", 0L);
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return;
        check(act, false, null);
    }

    /** Проверка по кнопке — с показом результата. */
    static void check(final Activity act) { check(act, true, null); }

    /** Проверка с отчётом на экран (кнопка «Обновить» в «О программе»). */
    static void check(final Activity act, final CheckListener listener) { check(act, true, listener); }

    /** Оповещение о ходе и результате проверки — для экрана «О программе». */
    interface CheckListener {
        void onStart();
        /**
         * @param message текст для пользователя
         * @param version номер доступной версии или null, если обновления нет
         * @param apkUrl  ссылка на APK обновления или null
         */
        void onResult(String message, String version, String apkUrl);
    }

    private static void check(final Activity act, final boolean manual, final CheckListener listener) {
        if (!busy.compareAndSet(false, true)) {
            if (manual && listener == null) Toast.makeText(act, "Проверка уже идёт…", Toast.LENGTH_SHORT).show();
            if (listener != null) main().post(() -> listener.onResult("Проверка уже идёт…", null, null));
            return;
        }
        if (listener != null) main().post(listener::onStart);
        new Thread(() -> {
            final Result r = fetch(act);
            busy.set(false);
            final String local = BuildConfig.VERSION_NAME;

            if (r.version == null || r.apkUrl == null || !isSafeHost(r.apkUrl)) {
                if (listener != null) main().post(() -> listener.onResult(r.message, null, null));
                else if (manual) toast(act, r.message);
                return;
            }
            if (!isNewer(r.version, local)) {
                final String message = "Установлена последняя версия " + local;
                if (listener != null) main().post(() -> listener.onResult(message, null, null));
                else if (manual) toast(act, message);
                return;
            }
            final String found = "Доступна версия " + r.version + " (установлена " + local + ")";
            if (listener != null) main().post(() -> listener.onResult(found, r.version, r.apkUrl));

            if (isReady(act, r.version)) { // уже скачано — просто предлагаем установить
                main().post(() -> promptInstall(act));
                return;
            }
            final String version = r.version, url = r.apkUrl;
            main().post(() -> {
                if (act.isFinishing() || act.isDestroyed()) return;
                if (isAutoEnabled(act)) {
                    // автообновление включено — качаем сразу, пользователь только подтверждает установку
                    downloadWithDialog(act, url, version);
                } else {
                    new AlertDialog.Builder(act)
                            .setTitle("Обновление ZI Office")
                            .setMessage("Доступна версия " + version + " (установлена " + local + ").\n\n"
                                    + "Нажмите «Скачать» — файл загрузится, после чего система предложит установить обновление.")
                            .setPositiveButton("Скачать", (d, w) -> downloadWithDialog(act, url, version))
                            .setNegativeButton("Позже", null)
                            .show();
                }
            });
        }).start();
    }

    /** Скачать APK обновления и запустить установщик (кнопка на экране «О программе»). */
    static void startDownload(final Activity act, final String urlStr, final String ver) {
        if (urlStr == null || !isSafeHost(urlStr)) {
            Toast.makeText(act, "Ссылка на обновление недоступна", Toast.LENGTH_LONG).show();
            return;
        }
        File ready = downloadedFile(act);
        if (ready != null && ver != null && ver.equals(downloadedVersion(act))) { promptInstall(act); return; }
        downloadWithDialog(act, urlStr, ver);
    }

    /** Результат обращения к GitHub. */
    private static final class Result {
        String version, apkUrl, message = "Не удалось проверить обновления";
    }

    /** Запрос последнего релиза (блокирующий, вызывать в фоновом потоке). */
    private static Result fetch(Context ctx) {
        Result r = new Result();
        boolean answered = false;
        int httpCode = 0;
        Throwable failure = null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(LATEST_URL).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "ZI-Office-Update");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            httpCode = c.getResponseCode();
            answered = true;
            if (httpCode == 200) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                }
                JSONObject o = new JSONObject(new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                String tag = o.optString("tag_name");
                if (tag != null && !tag.isEmpty()) r.version = tag.startsWith("v") ? tag.substring(1) : tag;
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.optJSONObject(i);
                        if (a == null) continue;
                        String fn = a.optString("name");
                        if (fn != null && fn.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                            r.apkUrl = a.optString("browser_download_url");
                            break;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            failure = e;
        } finally {
            if (c != null) c.disconnect();
        }
        // отметку времени ставим, когда сервер ответил: иначе при отсутствии
        // обновлений GitHub дёргался бы при каждом запуске
        if (answered && ctx != null) prefs(ctx).edit().putLong("last_check", System.currentTimeMillis()).apply();
        if (r.version == null || r.apkUrl == null) r.message = explain(httpCode, failure);
        return r;
    }

    /** Почему не удалось проверить обновления — человеческим языком. */
    private static String explain(int httpCode, Throwable failure) {
        if (httpCode == 404) return "Репозиторий обновлений не найден: " + RELEASE_REPO;
        if (httpCode == 403 || httpCode == 429) return "GitHub ограничил запросы, попробуйте позже";
        if (httpCode >= 500) return "Ошибка на стороне GitHub (" + httpCode + ")";
        if (failure != null) return "Не удалось проверить обновления. Проверьте интернет.";
        if (httpCode != 0) return "В репозитории обновлений ещё нет релизов";
        return "Не удалось проверить обновления";
    }

    private static void toast(final Activity act, final String msg) {
        main().post(() -> { if (!act.isFinishing() && !act.isDestroyed()) Toast.makeText(act, msg, Toast.LENGTH_LONG).show(); });
    }

    // ------------------------------------------------------------ скачивание

    /** Скачивание с диалогом прогресса; по завершении предлагаем установить. */
    private static void downloadWithDialog(final Activity act, final String urlStr, final String ver) {
        final File dir = new File(act.getFilesDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(act, "Нет места для обновления", Toast.LENGTH_LONG).show();
            return;
        }
        final File apk = new File(dir, "ZIOffice-" + ver + ".apk");
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
                .setTitle("Загрузка обновления " + ver)
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
                if (total > MAX_APK_BYTES) {
                    main().post(() -> finishDialog(act, dlg, apk, "Файл обновления слишком большой"));
                    return;
                }
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(apk)) {
                    byte[] b = new byte[65536]; long done = 0; int n, shownPct = -1;
                    while ((n = in.read(b)) > 0) {
                        fo.write(b, 0, n); done += n;
                        if (total > 0) {
                            final int p = (int) (done * 100 / total);
                            if (p != shownPct) {
                                shownPct = p;
                                main().post(() -> { if (dlg.isShowing()) { dlg.setMessage("Загрузка… " + p + "%"); bar.setProgress(p); } });
                            }
                        }
                    }
                }
                if (apk.length() < 1024 * 1024L) {
                    main().post(() -> finishDialog(act, dlg, apk, "Скачанный файл повреждён — повторите позже"));
                    return;
                }
                prefs(act).edit().putString("ready_version", ver).putString("ready_path", apk.getAbsolutePath()).apply();
                main().post(() -> {
                    try { if (dlg.isShowing()) dlg.dismiss(); } catch (Throwable ignored) { }
                    if (!act.isFinishing() && !act.isDestroyed()) promptInstall(act);
                });
            } catch (Throwable e) {
                CrashGuard.log(act, "загрузка обновления " + ver, e);
                main().post(() -> finishDialog(act, dlg, apk, "Не удалось скачать. Проверьте интернет."));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private static void finishDialog(Activity act, AlertDialog dlg, File apk, String msg) {
        try { if (apk != null) apk.delete(); } catch (Throwable ignored) { }
        try { if (dlg.isShowing()) dlg.dismiss(); } catch (Throwable ignored) { }
        if (!act.isFinishing() && !act.isDestroyed()) Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
    }

    // --------------------------------------------------------- уведомления

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Обновления ZI Office", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Уведомления о новых версиях и готовых обновлениях");
            nm.createNotificationChannel(ch);
        } catch (Throwable ignored) { }
    }

    /** «Обновление скачано» — нажатие открывает установку. */
    private static void notifyReady(Context ctx, String ver) {
        try {
            ensureChannel(ctx);
            Intent i = new Intent(ctx, UpdateInstallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(ctx, 1, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Обновление ZI Office " + ver + " готово")
                    .setContentText("Нажмите, чтобы установить (данные сохранятся)")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(
                            "Новая версия " + ver + " скачана. Нажмите, чтобы установить — данные и настройки сохранятся."))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setContentIntent(pi)
                    .addAction(0, "Установить", pi);
            NotificationManagerCompat.from(ctx).notify(NOTIFY_READY_ID, b.build());
        } catch (Throwable t) {
            CrashGuard.log(ctx, "уведомление об обновлении", t);
        }
    }

    private static void notifyProgress(Context ctx, String ver, int pct) {
        try {
            ensureChannel(ctx);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Скачиваем обновление " + ver)
                    .setContentText(pct + "%")
                    .setProgress(100, pct, false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setSilent(true);
            NotificationManagerCompat.from(ctx).notify(NOTIFY_PROGRESS_ID, b.build());
        } catch (Throwable ignored) { }
    }

    private static void cancelProgress(Context ctx) {
        try { NotificationManagerCompat.from(ctx).cancel(NOTIFY_PROGRESS_ID); } catch (Throwable ignored) { }
    }

    static void cancelNotifications(Context ctx) {
        try { NotificationManagerCompat.from(ctx).cancel(NOTIFY_READY_ID); } catch (Throwable ignored) { }
        cancelProgress(ctx);
    }

    /** Разрешены только официальные домены GitHub. */
    private static boolean isSafeHost(String urlStr) {
        try {
            String h = new URL(urlStr).getHost();
            return h != null && (h.equals("github.com") || h.endsWith(".github.com")
                    || h.equals("githubusercontent.com") || h.endsWith(".githubusercontent.com"));
        } catch (Exception e) { return false; }
    }

    // ------------------------------------------------------------- версии

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
