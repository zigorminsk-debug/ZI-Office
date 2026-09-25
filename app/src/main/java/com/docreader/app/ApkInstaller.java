package com.docreader.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * Запуск установки скачанного APK.
 *
 * Android не позволяет обычным приложениям ставить APK без подтверждения —
 * единственное, что можно сделать, это открыть системный установщик сразу с
 * нужным файлом. Здесь же проверяется разрешение «установка из этого
 * источника» и при необходимости открываются настройки.
 */
final class ApkInstaller {

    /** Разрешено ли приложению устанавливать APK (Android 8+ спрашивает один раз). */
    static boolean allowed(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try { return ctx.getPackageManager().canRequestPackageInstalls(); }
        catch (Throwable t) { return true; }
    }

    /** Экран «Установка неизвестных приложений» для нашего пакета. */
    static void openSettings(Activity act) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + act.getPackageName()));
            act.startActivity(i);
            return;
        } catch (Throwable ignored) { }
        try { act.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); } catch (Throwable ignored) { }
    }

    /** Открыть системный установщик для файла. true — установщик запущен. */
    static boolean install(Context ctx, File apk) {
        if (apk == null || !apk.exists()) return false;
        try {
            Uri uri = FileProvider.getUriForFile(ctx, "com.docreader.app.files", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Throwable t) {
            CrashGuard.log(ctx, "запуск установщика APK", t);
            return false;
        }
    }
}
