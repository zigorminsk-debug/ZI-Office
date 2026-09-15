package com.docreader.app;
import android.content.Context; import android.net.Uri;
import org.json.JSONArray; import org.json.JSONObject;
import java.util.ArrayList; import java.util.List;
final class RecentStore {
    static final class Item { final String uri, name, kind; Item(String u, String n, String k) { uri=u; name=n; kind=k; } }
    static List<Item> load(Context ctx) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(ctx.getSharedPreferences("recent", 0).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.optString("uri"), o.optString("name"), o.optString("kind")));
            }
        } catch (Exception ignored) {}
        return out;
    }
    static void add(Context ctx, Uri uri, String name, String kind) {
        if (uri == null) return;
        List<Item> cur = load(ctx); String u = uri.toString();
        List<Item> next = new ArrayList<>();
        next.add(new Item(u, name == null ? "document" : name, kind == null ? "" : kind));
        for (Item it : cur) if (!u.equals(it.uri)) next.add(it);
        if (next.size() > 30) next = next.subList(0, 30);
        JSONArray a = new JSONArray();
        try { for (Item it : next) { JSONObject o = new JSONObject(); o.put("uri", it.uri); o.put("name", it.name); o.put("kind", it.kind); a.put(o); } } catch (Exception ignored) {}
        ctx.getSharedPreferences("recent", 0).edit().putString("items", a.toString()).apply();
    }
}
