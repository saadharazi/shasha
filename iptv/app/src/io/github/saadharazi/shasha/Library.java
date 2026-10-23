package io.github.saadharazi.shasha;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** القنوات والأفلام والمسلسلات للاشتراك الحالي: تتحمّل مرة وتنحفظ في الجهاز 12 ساعة. */
final class Library {
    interface Done {
        void done(String error);
    }

    static final class Group {
        final String id, name;
        int count;

        Group(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static final String ALL = "*";
    private static final long FRESH = 12 * 3600 * 1000L;
    private static final String[] CATS = {"get_live_categories", "get_vod_categories", "get_series_categories"};
    private static final String[] LISTS = {"get_live_streams", "get_vod_streams", "get_series"};

    static Source source;
    @SuppressWarnings("unchecked")
    static List<Item>[] items = new List[3];
    @SuppressWarnings("unchecked")
    static List<Group>[] groups = new List[3];
    static String account, autoExt = "ts";
    private static final boolean[] busy = new boolean[3];
    @SuppressWarnings("unchecked")
    private static final List<Done>[] waiting = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};

    private Library() {}

    @SuppressWarnings("unchecked")
    static void use(Source s) {
        source = s;
        items = new List[3];
        groups = new List[3];
        account = null;
        autoExt = "ts";
    }

    /** يمسح القوائم المحفوظة عشان تنزل من جديد. */
    static void refresh(Context c) {
        File[] fs = new File(c.getCacheDir(), "lists").listFiles();
        if (fs != null) for (File f : fs) if (f.getName().startsWith(source.id)) f.delete();
        use(source);
    }

    static long updatedAt(Context c) {
        File[] fs = new File(c.getCacheDir(), "lists").listFiles();
        long t = 0;
        if (fs != null) for (File f : fs) if (f.getName().startsWith(source.id)) t = Math.max(t, f.lastModified());
        return t;
    }

    private static final class Result {
        @SuppressWarnings("unchecked")
        final List<Item>[] items = new List[3];
        @SuppressWarnings("unchecked")
        final List<Group>[] groups = new List[3];
        String error, account, ext;
    }

    static void load(Context c, final int kind, Done d) {
        if (items[kind] != null) {
            d.done(null);
            return;
        }
        final Source s = source;
        final int slot = s.xtream() ? kind : 0;   // قائمة M3U ملف واحد فيه كل شي
        waiting[slot].add(d);
        if (busy[slot]) return;
        busy[slot] = true;
        final File dir = new File(c.getCacheDir(), "lists");
        Net.io.execute(() -> {
            Result r = new Result();
            long t0 = android.os.SystemClock.uptimeMillis();
            try {
                if (s.xtream()) xtream(s, kind, dir, r);
                else m3u(s, dir, r);
                android.util.Log.i("Shasha", "list " + kind + " ready in " + (android.os.SystemClock.uptimeMillis() - t0) + " ms");
            } catch (Exception e) {
                r.error = Net.describe(e);
            } catch (OutOfMemoryError e) {
                r.error = "القائمة أكبر من ذاكرة الجهاز.";
            }
            Net.main.post(() -> {
                busy[slot] = false;
                List<Done> w = new ArrayList<>(waiting[slot]);
                waiting[slot].clear();
                if (s != source) return;
                for (int k = 0; k < 3; k++)
                    if (r.items[k] != null) {
                        items[k] = r.items[k];
                        groups[k] = r.groups[k];
                    }
                if (r.account != null) account = r.account;
                if (r.ext != null) autoExt = r.ext;
                for (Done x : w) x.done(r.error);
            });
        });
    }

    /** الملف المحفوظ لو جديد، وإلا ننزله. لو النت مقطوع نرضى بالقديم. */
    private static File cached(String url, File f) throws IOException {
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < FRESH) return f;
        try {
            Net.save(url, f);
        } catch (IOException e) {
            if (!f.exists()) throw e;
        }
        return f;
    }

    // ---- Xtream Codes

    private static void xtream(Source s, int kind, File dir, Result r) throws Exception {
        if (kind == Item.LIVE) account(s, r);
        final LinkedHashMap<String, Group> cats = new LinkedHashMap<>();
        each(cached(s.api(CATS[kind]), new File(dir, s.id + "-cat" + kind + ".json")), CAT_FIELDS, o -> {
            String id = o.get("category_id");
            if (id != null) cats.put(id, new Group(id, o.get("category_name") == null ? "" : o.get("category_name").trim()));
        });
        final List<Item> list = new ArrayList<>();
        each(cached(s.api(LISTS[kind]), new File(dir, s.id + "-list" + kind + ".json")), LIST_FIELDS, o -> {
            Item i = new Item();
            i.kind = kind;
            i.name = o.get("name") == null ? "" : o.get("name").trim();
            i.group = o.get("category_id");
            if (kind == Item.LIVE) {
                i.id = o.get("stream_id");
                i.logo = o.get("stream_icon");
                i.num = toInt(o.get("num"));
            } else if (kind == Item.MOVIE) {
                i.id = o.get("stream_id");
                i.logo = o.get("stream_icon");
                i.ext = o.get("container_extension");
                i.rating = rating(o.get("rating"));
                i.year = year(o.get("year"), o.get("releasedate"));
            } else {
                i.id = o.get("series_id");
                i.logo = o.get("cover");
                i.rating = rating(o.get("rating"));
                i.year = year(o.get("year"), o.get("releaseDate") != null ? o.get("releaseDate") : o.get("release_date"));
                i.sub = o.get("genre");
            }
            if (i.id != null) list.add(i);
        });
        r.items[kind] = list;
        r.groups[kind] = group(list, cats);
    }

    /** حالة الاشتراك وتاريخ انتهائه والصيغ اللي يسمح فيها السيرفر. */
    private static void account(Source s, Result r) throws Exception {
        JSONObject o;
        try {
            o = new JSONObject(Net.text(s.api(null)));
        } catch (org.json.JSONException e) {
            throw new IOException("السيرفر رد بشي غير Xtream. تأكد من رابط السيرفر.");
        }
        JSONObject u = o.optJSONObject("user_info");
        if (u == null || u.optInt("auth", 1) == 0) throw new IOException("اسم المستخدم أو كلمة المرور غلط.");
        String status = u.optString("status", "");
        long exp = u.optLong("exp_date", 0);
        String when = exp > 0 ? new SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn")).format(new Date(exp * 1000)) : null;
        if (!status.isEmpty() && !status.equalsIgnoreCase("Active")) r.account = "موقوف أو منتهي (" + status + ")";
        else r.account = when == null ? "فعّال، بدون تاريخ انتهاء" : "فعّال، ينتهي " + when;
        JSONArray f = u.optJSONArray("allowed_output_formats");
        if (f != null) {
            String fs = f.toString();
            r.ext = fs.contains("\"ts\"") || !fs.contains("m3u8") ? "ts" : "m3u8";
        }
    }

    private static List<Group> group(List<Item> list, LinkedHashMap<String, Group> cats) {
        Group other = new Group("?", "بدون قسم");
        for (Item i : list) {
            Group g = i.group == null ? null : cats.get(i.group);
            if (g == null) {
                i.group = other.id;
                other.count++;
            } else g.count++;
        }
        List<Group> out = new ArrayList<>();
        Group all = new Group(ALL, "الكل");
        all.count = list.size();
        out.add(all);
        for (Group g : cats.values()) if (g.count > 0) out.add(g);
        if (other.count > 0) out.add(other);
        return out;
    }

    // ---- M3U

    private static void m3u(Source s, File dir, Result r) throws IOException {
        File f = cached(s.url, new File(dir, s.id + "-playlist.m3u"));
        List<Item> live = new ArrayList<>(), vod = new ArrayList<>();
        LinkedHashMap<String, Group> liveCats = new LinkedHashMap<>(), vodCats = new LinkedHashMap<>();
        boolean any = false;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            Item next = null;
            String grp = null;
            for (String line; (line = in.readLine()) != null; ) {
                line = line.trim();
                if (line.startsWith("#EXTM3U")) any = true;
                else if (line.startsWith("#EXTINF")) {
                    any = true;
                    next = new Item();
                    next.name = title(line);
                    if (next.name.isEmpty()) next.name = attr(line, "tvg-name");
                    next.logo = attr(line, "tvg-logo");
                    next.group = attr(line, "group-title");
                } else if (line.startsWith("#EXTGRP:")) grp = line.substring(8).trim();
                else if (!line.isEmpty() && !line.startsWith("#")) {
                    Item i = next != null ? next : new Item();
                    i.url = i.id = line;
                    if (i.name == null || i.name.isEmpty()) i.name = Uri.parse(line).getLastPathSegment();
                    if (i.name == null) i.name = line;
                    if (i.group == null || i.group.isEmpty()) i.group = grp != null ? grp : "";
                    boolean isVod = isVod(line);
                    i.kind = isVod ? Item.MOVIE : Item.LIVE;
                    if (i.kind == Item.LIVE) i.num = live.size() + 1;
                    (isVod ? vod : live).add(i);
                    LinkedHashMap<String, Group> cats = isVod ? vodCats : liveCats;
                    if (!i.group.isEmpty() && !cats.containsKey(i.group)) cats.put(i.group, new Group(i.group, i.group));
                    next = null;
                    grp = null;
                }
            }
        }
        if (!any) throw new IOException("الرابط ما فيه قائمة M3U.");
        r.items[Item.LIVE] = live;
        r.groups[Item.LIVE] = group(live, liveCats);
        r.items[Item.MOVIE] = vod;
        r.groups[Item.MOVIE] = group(vod, vodCats);
        r.items[Item.SERIES] = new ArrayList<>();
        r.groups[Item.SERIES] = group(r.items[Item.SERIES], new LinkedHashMap<>());
    }

    /** الاسم بعد أول فاصلة برا علامات التنصيص: #EXTINF:-1 tvg-name="a, b",الاسم */
    static String title(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) return line.substring(i + 1).trim();
        }
        return "";
    }

    static String attr(String line, String key) {
        int i = line.indexOf(key + "=\"");
        if (i < 0) return null;
        i += key.length() + 2;
        int j = line.indexOf('"', i);
        String v = j < 0 ? line.substring(i) : line.substring(i, j);
        return v.trim().isEmpty() ? null : v.trim();
    }

    static boolean isVod(String url) {
        String u = url.toLowerCase(Locale.US);
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        return u.contains("/movie/") || u.contains("/series/") || u.endsWith(".mp4") || u.endsWith(".mkv")
                || u.endsWith(".avi") || u.endsWith(".mov") || u.endsWith(".m4v");
    }

    // ---- القراءة

    interface Row {
        void row(Map<String, String> o);
    }

    /** يقرأ مصفوفة JSON كبيرة كائن كائن بدون ما يحمّلها كلها في الذاكرة. القيم كلها نصوص. */
    // الحقول اللي نحتاجها بس؛ الباقي نتجاوزه بدون ما نسوي منه نص (القوائم الكبيرة تنقرا أسرع)
    static final java.util.Set<String> CAT_FIELDS = new java.util.HashSet<>(java.util.Arrays.asList("category_id", "category_name"));
    static final java.util.Set<String> LIST_FIELDS = new java.util.HashSet<>(java.util.Arrays.asList("name", "stream_id", "stream_icon",
            "category_id", "num", "container_extension", "rating", "year", "releasedate", "series_id", "cover", "releaseDate", "release_date", "genre"));

    /**
     * يقرأ مصفوفة كائنات JSON (قوائم Xtream) بسرعة: الملف كبايتات، والحقول اللي ما نبيها نتجاوزها بدون ما نسوي منها نص.
     * قارئ أندرويد يمشي حرف حرف وكان ياخذ ثواني على قائمة 20 ألف فيلم. القيم كلها ترجع نصوص.
     */
    static void each(File f, java.util.Set<String> want, Row row) throws IOException {
        byte[] b = readAll(f);
        try {
            int i = ws(b, 0);
            if (i >= b.length || b[i] != '[') return;   // بعض السيرفرات ترجع {} لما القسم فاضي
            i++;
            HashMap<String, String> o = new HashMap<>();
            int[] at = new int[1];
            while (true) {
                i = ws(b, i);
                if (b[i] == ']') return;
                if (b[i] == ',') {
                    i++;
                    continue;
                }
                if (b[i] != '{') {
                    i = skip(b, i);
                    continue;
                }
                i++;
                o.clear();
                while (true) {
                    i = ws(b, i);
                    if (b[i] == '}') {
                        i++;
                        break;
                    }
                    if (b[i] == ',') {
                        i++;
                        continue;
                    }
                    if (b[i] != '"') throw new IOException();
                    String k = str(b, i, at);
                    i = ws(b, at[0]);
                    if (b[i] != ':') throw new IOException();
                    i = ws(b, i + 1);
                    if (!want.contains(k)) {
                        i = skip(b, i);
                        continue;
                    }
                    String v = null;
                    if (b[i] == '"') {
                        v = str(b, i, at);
                        i = at[0];
                    } else if (b[i] == '[') {   // مثل backdrop_path: ناخذ أول نص
                        int e = skip(b, i);
                        int j = ws(b, i + 1);
                        if (b[j] == '"') v = str(b, j, at);
                        i = e;
                    } else if (b[i] == '{') i = skip(b, i);
                    else {
                        int e = skip(b, i);
                        String lit = new String(b, i, e - i, "US-ASCII").trim();
                        if (!lit.equals("null")) v = lit;
                        i = e;
                    }
                    if (v != null) o.put(k, v);
                }
                row.row(o);
            }
        } catch (ArrayIndexOutOfBoundsException | IOException e) {
            throw new IOException("رد السيرفر ناقص أو مو مفهوم.");
        }
    }

    private static byte[] readAll(File f) throws IOException {
        long n = f.length();
        if (n > 200L << 20) throw new IOException("القائمة أكبر من اللازم.");
        byte[] b = new byte[(int) n];
        try (FileInputStream in = new FileInputStream(f)) {
            int got = 0;
            while (got < b.length) {
                int r = in.read(b, got, b.length - got);
                if (r < 0) break;
                got += r;
            }
            if (got < b.length) b = Arrays.copyOf(b, got);
        }
        return b;
    }

    private static int ws(byte[] b, int i) {
        while (b[i] == ' ' || b[i] == '\n' || b[i] == '\r' || b[i] == '\t' || b[i] == (byte) 0xEF || b[i] == (byte) 0xBB || b[i] == (byte) 0xBF) i++;
        return i;
    }

    /** نهاية القيمة اللي تبدأ عند i (نص أو رقم أو كائن أو مصفوفة). */
    private static int skip(byte[] b, int i) {
        if (b[i] == '"') {
            i++;
            while (b[i] != '"') i += b[i] == '\\' ? 2 : 1;
            return i + 1;
        }
        if (b[i] == '{' || b[i] == '[') {
            int depth = 0;
            do {
                byte c = b[i];
                if (c == '"') {
                    i = skip(b, i);
                    continue;
                }
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                i++;
            } while (depth > 0);
            return i;
        }
        while (b[i] != ',' && b[i] != '}' && b[i] != ']') i++;
        return i;
    }

    /** نص JSON يبدأ بعلامة " عند i، ونفك رموزه (حروف يونيكود ومحارف خاصة). at[0] = بعد النهاية. */
    private static String str(byte[] b, int i, int[] at) throws IOException {
        int s = ++i;
        boolean esc = false;
        while (b[i] != '"') {
            if (b[i] == '\\') {
                esc = true;
                i++;
            }
            i++;
        }
        at[0] = i + 1;
        String raw = new String(b, s, i - s, "UTF-8");
        if (!esc) return raw;
        StringBuilder o = new StringBuilder(raw.length());
        for (int k = 0; k < raw.length(); k++) {
            char c = raw.charAt(k);
            if (c != '\\' || k + 1 >= raw.length()) {
                o.append(c);
                continue;
            }
            char n = raw.charAt(++k);
            switch (n) {
                case 'n': o.append('\n'); break;
                case 't': o.append('\t'); break;
                case 'r': o.append('\r'); break;
                case 'b': o.append('\b'); break;
                case 'f': o.append('\f'); break;
                case 'u':
                    if (k + 4 < raw.length()) {
                        try {
                            o.append((char) Integer.parseInt(raw.substring(k + 1, k + 5), 16));
                        } catch (NumberFormatException e) {
                            throw new IOException();
                        }
                        k += 4;
                    }
                    break;
                default: o.append(n);   // \" \\ \/
            }
        }
        return o.toString();
    }

    private static int toInt(String s) {
        try {
            return s == null ? 0 : (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String rating(String s) {
        try {
            double v = Double.parseDouble(s);
            return v <= 0 ? null : String.format(Locale.US, "%.1f", v > 10 ? v / 10 : v);
        } catch (Exception e) {
            return null;
        }
    }

    static String year(String y, String date) {
        // بدون regex: تتنادى لكل فيلم، وتجهيز النمط كل مرة كان ياخذ ثواني في القوائم الكبيرة
        String v = startsWithYear(y) ? y : date;
        return startsWithYear(v) ? v.substring(0, 4) : null;
    }

    private static boolean startsWithYear(String s) {
        if (s == null || s.length() < 4) return false;
        for (int i = 0; i < 4; i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        return true;
    }

    // ---- البحث والتصفية

    static List<Item> filter(int kind, String group, String query) {
        List<Item> src = items[kind], out = new ArrayList<>();
        if (src == null) return out;
        String q = norm(query);
        for (Item i : src) {
            if (!q.isEmpty()) {
                if (i.search == null) i.search = norm(i.name);
                if (!i.search.contains(q)) continue;
            } else if (!ALL.equals(group) && !group.equals(i.group)) continue;
            out.add(i);
        }
        return out;
    }

    /** يبسّط الاسم للبحث: بدون تشكيل، والألف والتاء المربوطة والياء بشكل واحد. */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (ch >= 'ً' && ch <= 'ٟ' || ch == 'ـ') continue;
            if (ch == 'أ' || ch == 'إ' || ch == 'آ') ch = 'ا';
            else if (ch == 'ة') ch = 'ه';
            else if (ch == 'ى') ch = 'ي';
            b.append(ch);
        }
        return b.toString().trim();
    }

    // ---- الروابط

    static String streamUrl(Item i) {
        if (i.url != null) return i.url;
        Source s = source;
        String base = s.server + "/", cred = Uri.encode(s.user) + "/" + Uri.encode(s.pass) + "/";
        switch (i.kind) {
            case Item.LIVE:
                String f = Store.liveFormat();
                return base + "live/" + cred + i.id + "." + ("auto".equals(f) ? autoExt : f);
            case Item.MOVIE:
                return base + "movie/" + cred + i.id + "." + (i.ext == null ? "mp4" : i.ext);
            default:
                return base + "series/" + cred + i.id + "." + (i.ext == null ? "mp4" : i.ext);
        }
    }

    /** رابط بديل لو البث ما اشتغل: نجرب الصيغة الثانية (HLS ↔ TS). */
    static String altUrl(Item i) {
        if (i.kind != Item.LIVE || i.url != null || !source.xtream()) return null;
        String u = streamUrl(i);
        return u.endsWith(".m3u8") ? u.substring(0, u.length() - 5) + ".ts" : u.substring(0, u.lastIndexOf('.')) + ".m3u8";
    }

    // ---- التفاصيل (Xtream بس)

    static void info(final Item i, final Consumer<JSONObject> d) {
        final Source s = source;
        if (!s.xtream()) {
            d.accept(null);
            return;
        }
        final String url = i.kind == Item.SERIES ? s.api("get_series_info") + "&series_id=" + i.id : s.api("get_vod_info") + "&vod_id=" + i.id;
        Net.io.execute(() -> {
            JSONObject o = null;
            try {
                o = new JSONObject(Net.text(url));
            } catch (Exception ignored) {
            }
            final JSONObject res = o;
            Net.main.post(() -> d.accept(res));
        });
    }

    static final class Program {
        final String title;
        final long start, end;

        Program(String title, long start, long end) {
            this.title = title;
            this.start = start;
            this.end = end;
        }
    }

    /** البرنامج الحالي واللي بعده للقناة (Xtream). */
    static void epg(final Item ch, final Consumer<List<Program>> d) {
        final Source s = source;
        if (s == null || !s.xtream() || ch.url != null) return;
        Net.io.execute(() -> {
            final List<Program> out = new ArrayList<>();
            try {
                JSONArray a = new JSONObject(Net.text(s.api("get_short_epg") + "&stream_id=" + ch.id + "&limit=3")).optJSONArray("epg_listings");
                long now = System.currentTimeMillis();
                for (int k = 0; a != null && k < a.length() && out.size() < 2; k++) {
                    JSONObject e = a.getJSONObject(k);
                    long start = e.optLong("start_timestamp") * 1000, end = e.optLong("stop_timestamp") * 1000;
                    if (end > 0 && end < now) continue;   // بعض السيرفرات ترجع برنامج خلص
                    String t = new String(Base64.decode(e.optString("title"), Base64.DEFAULT), "UTF-8").trim();
                    if (!t.isEmpty()) out.add(new Program(t, start, end));
                }
            } catch (Exception ignored) {
            }
            Net.main.post(() -> {
                if (s == source) d.accept(out);
            });
        });
    }
}
