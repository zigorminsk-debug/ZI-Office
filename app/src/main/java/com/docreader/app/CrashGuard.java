package com.docreader.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Перехватывает необработанные ошибки: пишет отчёт в файл приложения,
 * чтобы пользователь мог отправить его разработчику (кнопка «Отправить»
 * на экране «Отчёт об ошибке»). Приложение при этом закрывается как обычно —
 * ошибка не «проглатывается».
 *
 * Туда же пишутся некритичные сбои (например, файл из мессенджера не удалось
 * прочитать) — они помогают найти причину, не мешая работе.
 */
final class CrashGuard {
    private static final String CRASH_FILE = "crash-last.txt";
    private static final String ERRORS_FILE = "errors-log.txt";
    private static final long MAX_ERRORS = 64 * 1024;

    private static volatile boolean installed;

    private CrashGuard() { }

    /** Вызвать один раз при старте приложения. */
    static void install(final Application app) {
        if (installed) return;
        installed = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { writeFile(app, CRASH_FILE, report(app, thread, error)); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Некритичная ошибка: сохраняем текст, приложение продолжает работать. */
    static void log(Context ctx, String where, Throwable error) {
        try {
            String entry = "\n[" + stamp() + "] " + where + "\n" + stack(error) + "\n";
            File f = new File(ctx.getFilesDir(), ERRORS_FILE);
            if (f.length() > MAX_ERRORS) { // файл не растёт бесконечно
                writeFile(ctx, ERRORS_FILE, entry);
            } else {
                try (FileOutputStream out = new FileOutputStream(f, true)) {
                    out.write(entry.getBytes("UTF-8"));
                }
            }
        } catch (Throwable ignored) { }
    }

    /** Есть ли отчёт о падении (то есть приложение в прошлый раз закрылось с ошибкой). */
    static boolean hasCrashReport(Context ctx) { return crashFile(ctx).length() > 0; }

    /** Полный текст отчёта: падение + последние некритичные ошибки. */
    static String readReport(Context ctx) {
        StringBuilder sb = new StringBuilder();
        String crash = read(crashFile(ctx));
        if (!crash.isEmpty()) sb.append(crash);
        String errors = read(new File(ctx.getFilesDir(), ERRORS_FILE));
        if (!errors.isEmpty()) sb.append("\n----- Последние ошибки -----\n").append(errors);
        if (sb.length() == 0) sb.append("Отчётов нет.");
        return sb.toString();
    }

    /** Убрать отчёт (пользователь его отправил или закрыл). */
    static void clear(Context ctx) {
        try { crashFile(ctx).delete(); } catch (Throwable ignored) { }
        try { new File(ctx.getFilesDir(), ERRORS_FILE).delete(); } catch (Throwable ignored) { }
    }

    private static File crashFile(Context ctx) { return new File(ctx.getFilesDir(), CRASH_FILE); }

    private static String report(Application app, Thread thread, Throwable error) {
        return "ZI Office · отчёт об ошибке\n"
                + "Время: " + stamp() + "\n"
                + "Версия: " + BuildConfig.VERSION_NAME + " (сборка " + BuildConfig.VERSION_CODE + ")\n"
                + "Телефон: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Поток: " + (thread == null ? "?" : thread.getName()) + "\n\n"
                + stack(error);
    }

    private static String stack(Throwable error) {
        if (error == null) return "(нет данных)";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String stamp() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(new Date());
    }

    private static void writeFile(Context ctx, String name, String text) {
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), name), false)) {
            out.write(text.getBytes("UTF-8"));
        } catch (Throwable ignored) { }
    }

    private static String read(File f) {
        if (f == null || !f.exists()) return "";
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 512 * 1024)];
            int n = in.read(b);
            return n <= 0 ? "" : new String(b, 0, n, "UTF-8");
        } catch (Throwable e) { return ""; }
    }
}
