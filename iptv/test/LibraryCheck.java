package io.github.saadharazi.shasha;

/**
 * فحص سريع للدوال اللي ما تحتاج أندرويد (قراءة M3U والبحث والتقييم). يشتغل على الكمبيوتر:
 *   bash test/check.sh
 */
public class LibraryCheck {
    static int fails;

    static void eq(Object got, Object want, String what) {
        if (want == null ? got != null : !want.equals(got)) {
            fails++;
            System.out.println("FAIL " + what + ": got [" + got + "] want [" + want + "]");
        }
    }

    public static void main(String[] a) {
        String line = "#EXTINF:-1 tvg-id=\"x\" tvg-name=\"News, HD\" tvg-logo=\"http://l/x.png\" group-title=\"أخبار\",قناة الأخبار, HD";
        eq(Library.title(line), "قناة الأخبار, HD", "title after first unquoted comma");
        eq(Library.attr(line, "tvg-logo"), "http://l/x.png", "attr logo");
        eq(Library.attr(line, "group-title"), "أخبار", "attr group");
        eq(Library.attr(line, "tvg-chno"), null, "missing attr");
        eq(Library.title("#EXTINF:-1,Simple"), "Simple", "simple title");

        eq(Library.isVod("http://s/movie/u/p/12.mkv"), true, "movie path");
        eq(Library.isVod("http://s/clip.MP4?token=1"), true, "mp4 with query");
        eq(Library.isVod("http://s/live/u/p/12.ts"), false, "live ts");
        eq(Library.isVod("http://s/hls/index.m3u8"), false, "hls");

        eq(Library.norm("الإخبارية"), Library.norm("الاخباريه"), "alef and ta marbuta");
        eq(Library.norm("مُسَلْسَل"), "مسلسل", "diacritics");
        eq(Library.norm("BBC Arabic"), "bbc arabic", "latin lowercase");

        eq(Library.rating("7.25"), "7.3", "rating one decimal");
        eq(Library.rating("85"), "8.5", "rating out of 100");
        eq(Library.rating("0"), null, "zero rating hidden");
        eq(Library.rating("n/a"), null, "bad rating");
        eq(Library.year(null, "2019-05-01"), "2019", "year from date");
        eq(Library.year("2021", null), "2021", "year field");
        eq(Library.year("", ""), null, "no year");

        eq(Ui.count(1, "قناة وحدة", "قناتين", "قنوات", "قناة"), "قناة وحدة", "count 1");
        eq(Ui.count(2, "قناة وحدة", "قناتين", "قنوات", "قناة"), "قناتين", "count 2");
        eq(Ui.count(7, "قناة وحدة", "قناتين", "قنوات", "قناة"), "7 قنوات", "count 7");
        eq(Ui.count(1500, "قناة وحدة", "قناتين", "قنوات", "قناة"), "1,500 قناة", "count 1500");
        eq(Ui.count(0, "a", "b", "c", "d"), "", "count 0 hidden");
        eq(Ui.clock(3723000), "1:02:03", "clock h:mm:ss");
        eq(Ui.clock(95000), "1:35", "clock m:ss");

        java.util.Map<String, String> f = PhoneForm.parse("kind=xtream&server=http%3A%2F%2Fa.b%3A8080&user=u+1&pass=p%26q&bad");
        eq(f.get("server"), "http://a.b:8080", "form url decode");
        eq(f.get("user"), "u 1", "form plus is space");
        eq(f.get("pass"), "p&q", "form ampersand");
        eq(f.containsKey("bad"), false, "form skips broken pair");
        eq(PhoneForm.esc("<b>\"x\"&"), "&lt;b&gt;&quot;x&quot;&amp;", "html escape");

        eq(Ui.isNewer("1.10", "1.9"), true, "version 1.10 > 1.9");
        eq(Ui.isNewer("1.1", "1.1"), false, "same version");
        eq(Ui.isNewer("1.0.9", "1.1"), false, "older patch");
        eq(Ui.isNewer(null, "1.1"), false, "no latest");

        try {
            java.io.File tmp = java.io.File.createTempFile("list", ".json");
            String json = "\uFEFF[ {\"name\":\"\u0642\u0646\u0627\u0629 \\\"1\\\" \\u0627\", \"stream_id\":5, \"num\":\"7\", "
                    + "\"x\":{\"a\":[1,{\"b\":\"]}\"}]}, \"backdrop_path\":[\"http://a/b.jpg\",\"c\"], \"category_id\":null, \"rating\":7.5},"
                    + "{}, {\"name\":\"B\\/C\",\"skip\":\"x\\\\\"} ]";
            java.nio.file.Files.write(tmp.toPath(), json.getBytes("UTF-8"));
            final java.util.List<java.util.Map<String, String>> rows = new java.util.ArrayList<>();
            java.util.Set<String> want = new java.util.HashSet<>(java.util.Arrays.asList("name", "stream_id", "num", "backdrop_path", "category_id", "rating"));
            Library.each(tmp, want, o -> rows.add(new java.util.HashMap<>(o)));
            eq(rows.size(), 3, "json rows");
            eq(rows.get(0).get("name"), "\u0642\u0646\u0627\u0629 \"1\" \u0627", "json escapes and unicode");
            eq(rows.get(0).get("stream_id"), "5", "json number");
            eq(rows.get(0).get("backdrop_path"), "http://a/b.jpg", "json first of array");
            eq(rows.get(0).containsKey("category_id"), false, "json null dropped");
            eq(rows.get(0).get("rating"), "7.5", "json decimal");
            eq(rows.get(2).get("name"), "B/C", "json slash escape");
            java.nio.file.Files.write(tmp.toPath(), "{\"user_info\":{}}".getBytes("UTF-8"));
            rows.clear();
            Library.each(tmp, want, o -> rows.add(o));
            eq(rows.size(), 0, "json object top-level is empty");
            java.nio.file.Files.write(tmp.toPath(), "[{\"name\":\"a".getBytes("UTF-8"));
            boolean threw = false;
            try {
                Library.each(tmp, want, o -> {});
            } catch (java.io.IOException e) {
                threw = true;
            }
            eq(threw, true, "json truncated throws");
            tmp.delete();
        } catch (Exception e) {
            fails++;
            System.out.println("FAIL json: " + e);
        }

        System.out.println(fails == 0 ? "ALL OK" : fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
