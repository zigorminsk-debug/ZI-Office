package com.docreader.app;
import android.content.Context; import androidx.appcompat.app.AppCompatDelegate;
final class ThemePrefs {
    static boolean isNight(Context ctx) { return ctx.getSharedPreferences("theme", 0).getBoolean("night", false); }
    static void apply(Context ctx) { AppCompatDelegate.setDefaultNightMode(isNight(ctx) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO); }
    static void toggle(Context ctx) { ctx.getSharedPreferences("theme", 0).edit().putBoolean("night", !isNight(ctx)).apply(); apply(ctx); }
}
