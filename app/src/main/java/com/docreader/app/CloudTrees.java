package com.docreader.app;
import android.content.Context; import android.net.Uri;
final class CloudTrees {
    static final String GOOGLE = "google"; static final String YANDEX = "yandex";
    static Uri get(Context ctx, String cloud) { String s = ctx.getSharedPreferences("cloud_trees", 0).getString(cloud, null); return s == null ? null : Uri.parse(s); }
    static void save(Context ctx, String cloud, Uri uri) { ctx.getSharedPreferences("cloud_trees", 0).edit().putString(cloud, uri == null ? null : uri.toString()).apply(); }
    static void clear(Context ctx, String cloud) { ctx.getSharedPreferences("cloud_trees", 0).edit().remove(cloud).apply(); }
}
