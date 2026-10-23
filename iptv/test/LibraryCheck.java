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

        System.out.println(fails == 0 ? "ALL OK" : fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
