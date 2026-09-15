package com.docreader.app;
import android.content.Intent; import android.provider.Settings;
final class DefaultApps {
    static void prompt(android.app.Activity act) { try { act.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); } catch (Exception ignored) {} }
}
