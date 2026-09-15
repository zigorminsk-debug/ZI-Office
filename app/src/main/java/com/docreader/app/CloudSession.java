package com.docreader.app;
import android.content.Context; import android.content.SharedPreferences; import android.webkit.CookieManager;
final class CloudSession {
    static void persist(Context ctx, String key) {
        CookieManager cm = CookieManager.getInstance(); try { cm.flush(); } catch (Exception ignored) {}
        String cookies = "";
        try { cookies = cm.getCookie("google".equals(key) ? "https://drive.google.com" : "https://disk.yandex.ru"); if (cookies == null) cookies = ""; } catch (Exception ignored) {}
        prefs(ctx).edit().putString(key, cookies).apply();
    }
    static void restore(Context ctx) {
        CookieManager cm = CookieManager.getInstance(); cm.setAcceptCookie(true);
        restoreOne(cm, "https://drive.google.com", prefs(ctx).getString("google", ""));
        restoreOne(cm, "https://accounts.google.com", prefs(ctx).getString("google", ""));
        restoreOne(cm, "https://disk.yandex.ru", prefs(ctx).getString("yandex", ""));
        restoreOne(cm, "https://passport.yandex.ru", prefs(ctx).getString("yandex", ""));
        try { cm.flush(); } catch (Exception ignored) {}
    }
    static void logoutGoogle(Context ctx) { prefs(ctx).edit().remove("google").apply(); }
    static void logoutYandex(Context ctx) { prefs(ctx).edit().remove("yandex").apply(); }
    private static void restoreOne(CookieManager cm, String url, String cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        for (String c : cookies.split(";")) { String t = c.trim(); if (!t.isEmpty()) cm.setCookie(url, t); }
    }
    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences("cloud_sessions", 0); }
}
