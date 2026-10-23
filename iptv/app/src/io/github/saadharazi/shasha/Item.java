package io.github.saadharazi.shasha;

import org.json.JSONException;
import org.json.JSONObject;

/** قناة أو فيلم أو مسلسل أو حلقة. */
final class Item {
    static final int LIVE = 0, MOVIE = 1, SERIES = 2, EPISODE = 3;

    int kind, num;
    String id, name, logo, group, url, ext, rating, year, sub;
    long pos, dur;   // وين وقف ومدة الفيديو (لتابع المشاهدة)
    String search;   // الاسم مبسّط للبحث، يتجهز أول مرة نبحث

    String key() {
        return kind + ":" + id;
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("k", kind).put("i", id).put("n", name).putOpt("l", logo).putOpt("g", group).putOpt("u", url)
                    .putOpt("e", ext).putOpt("r", rating).putOpt("y", year).putOpt("s", sub);
            if (num != 0) o.put("num", num);
            if (pos != 0) o.put("p", pos).put("d", dur);
        } catch (JSONException ignored) {
        }
        return o;
    }

    static Item from(JSONObject o) {
        Item i = new Item();
        i.kind = o.optInt("k");
        i.id = o.optString("i");
        i.name = o.optString("n");
        i.logo = o.optString("l", null);
        i.group = o.optString("g", null);
        i.url = o.optString("u", null);
        i.ext = o.optString("e", null);
        i.rating = o.optString("r", null);
        i.year = o.optString("y", null);
        i.sub = o.optString("s", null);
        i.num = o.optInt("num");
        i.pos = o.optLong("p");
        i.dur = o.optLong("d");
        return i;
    }

    /** الحرف الأول من الاسم (للخانة اللي ما لها صورة). */
    String initial() {
        if (name != null) for (int i = 0; i < name.length(); i++) {
            int cp = name.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) return new String(Character.toChars(Character.toUpperCase(cp)));
        }
        return "";
    }
}
