package com.docreader.app;
import android.content.Context; import java.util.ArrayList; import java.util.List;
final class Bookmarks {
    static List<Integer> load(Context ctx, String name) {
        List<Integer> out = new ArrayList<>();
        String s = ctx.getSharedPreferences("bm", 0).getString(name == null ? "" : name, "");
        if (s == null || s.isEmpty()) return out;
        for (String p : s.split(",")) try { out.add(Integer.parseInt(p.trim())); } catch (Exception ignored) {}
        return out;
    }
    static void save(Context ctx, String name, List<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        if (pages != null) for (int i = 0; i < pages.size(); i++) { if (i > 0) sb.append(','); sb.append(pages.get(i)); }
        ctx.getSharedPreferences("bm", 0).edit().putString(name == null ? "" : name, sb.toString()).apply();
    }
}
