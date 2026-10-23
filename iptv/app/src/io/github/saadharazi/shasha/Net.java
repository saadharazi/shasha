package io.github.saadharazi.shasha;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** الاتصال بالسيرفر وتحميل الصور (مع ذاكرة مؤقتة في الجهاز). */
final class Net {
    static String UA = "Shasha (Android)", version = "";
    static final ExecutorService io = Executors.newFixedThreadPool(3);
    static final Handler main = new Handler(Looper.getMainLooper());
    // آخر صورة طُلبت تتحمّل أول: لما تنزل بسرعة في القائمة ما ننتظر الصور اللي فاتت
    private static final ThreadPoolExecutor img = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>() {
        @Override
        public boolean offer(Runnable r) {
            return offerFirst(r);
        }
    });
    private static LruCache<String, Bitmap> mem;
    private static File imgDir;

    private Net() {}

    static void init(Context c) {
        if (mem != null) return;
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            UA = "Shasha/" + version + " (Android)";
        } catch (Exception ignored) {
        }
        mem = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
            @Override
            protected int sizeOf(String k, Bitmap b) {
                return b.getByteCount() / 1024;
            }
        };
        imgDir = new File(c.getCacheDir(), "img");
        imgDir.mkdirs();
        io.execute(Net::trim);
    }

    static HttpURLConnection open(String url) throws IOException {
        for (int hop = 0; ; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            // HttpURLConnection ما يتبع التحويل من http إلى https لحاله
            if (code >= 300 && code < 400 && loc != null && hop < 5) {
                c.disconnect();
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code >= 400) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
    }

    static String text(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** ينزّل في ملف مؤقت وبعدين يبدّله، عشان ما يبقى ملف ناقص لو انقطع النت. */
    static void save(String url, File f) throws IOException {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".part");
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out);
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("ما قدرنا نحفظ الملف");
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[16384];
        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
    }

    /** سبب الخطأ بكلام مفهوم. */
    static String describe(Exception e) {
        if (e instanceof UnknownHostException) return "ما لقينا السيرفر. تأكد من الرابط ومن اتصال الإنترنت.";
        if (e instanceof SocketTimeoutException) return "السيرفر ما رد في وقته. جرّب بعد شوي.";
        if (e instanceof ConnectException) return "السيرفر رفض الاتصال. تأكد من الرابط والمنفذ (البورت).";
        String m = e.getMessage();
        if (m != null && m.startsWith("HTTP ")) {
            String code = m.substring(5);
            if (code.equals("401") || code.equals("403")) return "السيرفر رفض الدخول (" + code + "). تأكد من بيانات الاشتراك.";
            if (code.equals("404")) return "الرابط ما له صفحة على السيرفر (404).";
            return "السيرفر رجّع خطأ " + code + ".";
        }
        return m != null && !m.isEmpty() ? m : "صار خطأ في الاتصال.";
    }

    /**
     * يحط الصورة في ImageView لما تتحمّل، ويخفي fallback (الحرف الأول من الاسم).
     * الخانة نفسها تتكرر في القائمة، فنتأكد إن الرابط ما تغيّر قبل ما نحط الصورة.
     */
    static void image(final ImageView v, final String url, final int px, final View fallback) {
        v.setTag(url);
        if (url == null || url.isEmpty() || !url.startsWith("http")) {
            v.setImageDrawable(null);
            if (fallback != null) fallback.setVisibility(View.VISIBLE);
            return;
        }
        Bitmap b = mem.get(url);
        if (b != null) {
            v.setImageBitmap(b);
            v.setAlpha(1f);
            if (fallback != null) fallback.setVisibility(View.INVISIBLE);
            return;
        }
        v.setImageDrawable(null);
        if (fallback != null) fallback.setVisibility(View.VISIBLE);
        img.execute(() -> {
            if (!url.equals(v.getTag())) return;
            final Bitmap bm = load(url, px);
            if (bm == null) return;
            mem.put(url, bm);
            main.post(() -> {
                if (!url.equals(v.getTag())) return;
                v.setImageBitmap(bm);
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(180).start();
                if (fallback != null) fallback.setVisibility(View.INVISIBLE);
            });
        });
    }

    private static Bitmap load(String url, int px) {
        // shortcut: اسم الملف من hashCode وطول الرابط، التصادم نادر جدا؛ لو صار مشكلة نبدّله بـ SHA-1
        File f = new File(imgDir, Integer.toHexString(url.hashCode()) + "-" + url.length());
        try {
            if (!f.exists()) save(url, f);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) {
                f.delete();
                return null;
            }
            o.inSampleSize = 1;
            while (Math.max(o.outWidth, o.outHeight) / (o.inSampleSize * 2) >= px) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** نخلي صور الذاكرة المؤقتة تحت 80 ميقا: نحذف الأقدم. */
    private static void trim() {
        File[] fs = imgDir.listFiles();
        if (fs == null) return;
        long total = 0;
        for (File f : fs) total += f.length();
        if (total < 80L << 20) return;
        Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : fs) {
            if (total < 40L << 20) break;
            total -= f.length();
            f.delete();
        }
    }
}
