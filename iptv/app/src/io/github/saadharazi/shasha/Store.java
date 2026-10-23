package io.github.saadharazi.shasha;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** الاشتراكات والمفضلة وتابع المشاهدة. كل شي في الجهاز، والمفضلة لكل اشتراك لحاله. */
final class Store {
    private static SharedPreferences p;
    private static String favFor;
    private static final Set<String> favKeys = new HashSet<>();

    private Store() {}

    static void init(Context c) {
        if (p == null) p = c.getApplicationContext().getSharedPreferences("shasha", Context.MODE_PRIVATE);
    }

    // ---- الاشتراكات

    static List<Source> sources() {
        List<Source> out = new ArrayList<>();
        JSONArray a = array("sources");
        for (int i = 0; i < a.length(); i++) out.add(Source.from(a.optJSONObject(i)));
        return out;
    }

    static Source active() {
        String id = p.getString("active", null);
        List<Source> all = sources();
        for (Source s : all) if (s.id.equals(id)) return s;
        return all.isEmpty() ? null : all.get(0);
    }

    static void add(Source s) {
        JSONArray a = array("sources");
        a.put(s.json());
        p.edit().putString("sources", a.toString()).putString("active", s.id).apply();
    }

    static void use(Source s) {
        p.edit().putString("active", s.id).apply();
    }

    static void remove(Source s) {
        JSONArray a = array("sources"), keep = new JSONArray();
        for (int i = 0; i < a.length(); i++) if (!s.id.equals(a.optJSONObject(i).optString("id"))) keep.put(a.optJSONObject(i));
        p.edit().putString("sources", keep.toString()).remove("fav_" + s.id).remove("recent_" + s.id).apply();
    }

    // ---- الإعدادات

    /** صيغة البث المباشر: auto أو m3u8 أو ts. */
    static String liveFormat() {
        return p.getString("live_format", "auto");
    }

    static void setLiveFormat(String f) {
        p.edit().putString("live_format", f).apply();
    }

    /** آخر إصدار لقيناه على GitHub ومتى شيّكنا (مرة باليوم). */
    static String latest() {
        return p.getString("latest", null);
    }

    static boolean updateCheckDue() {
        return System.currentTimeMillis() - p.getLong("latest_at", 0) > 20 * 3600 * 1000L;
    }

    static void setLatest(String v) {
        p.edit().putString("latest", v).putLong("latest_at", System.currentTimeMillis()).apply();
    }

    /** مقاس الصورة في المشغّل: 0 على قد الشاشة، 1 تكبير، 2 تمديد. */
    static int aspect() {
        return p.getInt("aspect", 0);
    }

    static void setAspect(int a) {
        p.edit().putInt("aspect", a).apply();
    }

    /** آخر قناة اتفرجت عليها (نبدأ عندها في قائمة المباشر). */
    static void setLastLive(Item i) {
        if (Library.source != null) p.edit().putString("last_live_" + Library.source.id, i.id).apply();
    }

    static String lastLive() {
        return Library.source == null ? null : p.getString("last_live_" + Library.source.id, null);
    }

    // ---- المفضلة (العنصر كامل، عشان تطلع بدون ما نحمّل القسم كله)

    static boolean isFav(Item i) {
        loadFavKeys();
        return favKeys.contains(i.key());
    }

    /** يرجّع true لو صار في المفضلة. */
    static boolean toggleFav(Item i) {
        String k = "fav_" + Library.source.id;
        JSONArray a = array(k), out = new JSONArray();
        boolean was = false;
        for (int j = 0; j < a.length(); j++) {
            Item x = Item.from(a.optJSONObject(j));
            if (x.key().equals(i.key())) was = true;
            else out.put(a.optJSONObject(j));
        }
        if (!was) {
            Item c = Item.from(i.json());
            c.pos = c.dur = 0;
            out.put(c.json());
        }
        p.edit().putString(k, out.toString()).apply();
        favFor = null;
        return !was;
    }

    static List<Item> favs(int kind) {
        List<Item> out = new ArrayList<>();
        JSONArray a = array("fav_" + Library.source.id);
        for (int j = 0; j < a.length(); j++) {
            Item x = Item.from(a.optJSONObject(j));
            if (x.kind == kind) out.add(x);
        }
        return out;
    }

    private static void loadFavKeys() {
        if (Library.source == null || Library.source.id.equals(favFor)) return;
        favFor = Library.source.id;
        favKeys.clear();
        JSONArray a = array("fav_" + favFor);
        for (int j = 0; j < a.length(); j++) favKeys.add(Item.from(a.optJSONObject(j)).key());
    }

    // ---- تابع المشاهدة

    static long resumeAt(Item i) {
        for (Item r : recents()) if (r.key().equals(i.key())) return r.pos;
        return 0;
    }

    /** نحفظ وين وقف. لو خلّص الفيديو تقريبا، يطلع من القائمة. */
    static void watched(Item i, long pos, long dur) {
        if (i.kind == Item.LIVE || Library.source == null) return;
        String k = "recent_" + Library.source.id;
        JSONArray a = array(k), out = new JSONArray();
        boolean done = dur > 0 && pos > dur * 0.95;
        if (!done && pos > 15000) {
            Item c = Item.from(i.json());
            c.pos = pos;
            c.dur = dur;
            out.put(c.json());
        }
        for (int j = 0; j < a.length() && out.length() < 40; j++)
            if (!Item.from(a.optJSONObject(j)).key().equals(i.key())) out.put(a.optJSONObject(j));
        p.edit().putString(k, out.toString()).apply();
    }

    static List<Item> recents() {
        List<Item> out = new ArrayList<>();
        if (Library.source == null) return out;
        JSONArray a = array("recent_" + Library.source.id);
        for (int j = 0; j < a.length(); j++) out.add(Item.from(a.optJSONObject(j)));
        return out;
    }

    static void forget(Item i) {
        watched(i, 0, 0);
    }

    private static JSONArray array(String k) {
        try {
            return new JSONArray(p.getString(k, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
