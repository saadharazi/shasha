package io.github.saadharazi.shasha;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.IOException;

/** إضافة اشتراك: حساب Xtream (سيرفر واسم وكلمة مرور) أو رابط M3U. نتحقق منه قبل ما نحفظه. */
public class SourceActivity extends Activity {
    private boolean xtream;
    private TextView kindX, kindM, status, save;
    private LinearLayout xBox, mBox;
    private EditText name, server, user, pass, url;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Net.init(this);
        Store.init(this);
        xtream = getIntent().getBooleanExtra("xtream", true);
        boolean wide = Ui.widthDp(this) >= 640;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout outer = new LinearLayout(this);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(20), Ui.dp(wide ? 28 : 20), Ui.dp(20), Ui.dp(36));
        outer.addView(col, new LinearLayout.LayoutParams(Math.min(Ui.dp(600), getResources().getDisplayMetrics().widthPixels), Ui.WRAP));
        scroll.addView(outer);

        TextView t = Ui.text(this, Ui.bold, wide ? 30 : 26, Ui.INK);
        t.setText("أضف اشتراك");
        Ui.markRoom(t, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        col.addView(t, tp);
        TextView sub = Ui.text(this, Ui.regular, 16, Ui.INK2);
        sub.setText("البيانات تنحفظ في جهازك بس، وما تروح لأي مكان غير سيرفر اشتراكك.");
        sub.setLineSpacing(Ui.dp(4), 1f);
        Ui.markRoom(sub, false);
        col.addView(sub);

        LinearLayout seg = new LinearLayout(this);
        seg.setBackground(Ui.round(Ui.SURFACE, 26));
        seg.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        kindX = segment("حساب Xtream", true);
        kindM = segment("رابط M3U", false);
        seg.addView(kindX, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        seg.addView(kindM, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        sp.topMargin = Ui.dp(18);
        col.addView(seg, sp);

        xBox = new LinearLayout(this);
        xBox.setOrientation(LinearLayout.VERTICAL);
        server = field(xBox, "رابط السيرفر", "http://example.com:8080", InputType.TYPE_TEXT_VARIATION_URI);
        user = field(xBox, "اسم المستخدم", null, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pass = field(xBox, "كلمة المرور", null, InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(xBox);
        mBox = new LinearLayout(this);
        mBox.setOrientation(LinearLayout.VERTICAL);
        url = field(mBox, "رابط القائمة", "http://example.com/playlist.m3u", InputType.TYPE_TEXT_VARIATION_URI);
        TextView hint = Ui.text(this, Ui.regular, 14, Ui.INK3);
        hint.setText("لو الرابط من نوع get.php وفيه username وpassword، نحوله لحساب Xtream عشان تطلع الأفلام والمسلسلات مرتبة.");
        hint.setLineSpacing(Ui.dp(3), 1f);
        hint.setPadding(Ui.dp(4), Ui.dp(8), Ui.dp(4), 0);
        mBox.addView(hint);
        col.addView(mBox);
        LinearLayout nameBox = new LinearLayout(this);
        nameBox.setOrientation(LinearLayout.VERTICAL);
        name = field(nameBox, "اسم يميّزه (اختياري)", "مثلًا: اشتراك البيت", InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        col.addView(nameBox);

        status = Ui.text(this, Ui.medium, 15, Ui.OFF);
        status.setLineSpacing(Ui.dp(3), 1f);
        status.setPadding(Ui.dp(4), Ui.dp(14), Ui.dp(4), 0);
        status.setVisibility(View.GONE);
        col.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, Ui.dp(22), 0, 0);
        save = Ui.button(this, "احفظ وتحقّق", R.drawable.ic_check, true);
        save.setOnClickListener(v -> check());
        TextView cancel = Ui.button(this, "إلغاء", 0, false);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        bp.setMarginEnd(Ui.dp(10));
        row.addView(save, bp);
        row.addView(cancel);
        col.addView(row);

        setContentView(scroll);
        if (Ui.isTv(this)) phoneCard(col);
        pick(xtream);
        (xtream ? server : url).requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        phone.stop();
    }

    private final PhoneForm phone = new PhoneForm();

    /** على التلفزيون: عنوان تفتحه من الجوال وتعبّي البيانات منه بدل الريموت. */
    private void phoneCard(LinearLayout col) {
        String addr = phone.start(form -> {
            String[] err = new String[1];
            Source s = build(!"m3u".equals(form.get("kind")), form.get("server"), form.get("user"), form.get("pass"), form.get("url"), form.get("name"), err);
            if (s == null) return err[0];
            try {
                final Source ok = verified(s, "m3u".equals(form.get("kind")), form.get("url"));
                Net.main.post(() -> saved(ok));
                return null;
            } catch (Exception e) {
                return Net.describe(e);
            }
        });
        if (addr == null) return;
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.ring(0x0FFFFFFF, 0x1FFFFFFF, 1, 18));
        card.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(20), Ui.dp(16));
        ImageView code = new ImageView(this);
        Bitmap qr = qr(addr, Ui.dp(150));
        code.setImageBitmap(qr);
        code.setScaleType(ImageView.ScaleType.CENTER);
        code.setBackground(Ui.round(0xFFFFFFFF, 12));
        code.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        card.addView(code, new LinearLayout.LayoutParams(qr.getWidth() + Ui.dp(12), qr.getHeight() + Ui.dp(12)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, Ui.medium, 16, Ui.INK2);
        t.setText("أسهل لك: امسح الرمز بكاميرا جوالك وعبّي البيانات منه. أو افتح هذا العنوان، وجوالك على نفس الواي فاي:");
        t.setLineSpacing(Ui.dp(3), 1f);
        Ui.markRoom(t, false);
        words.addView(t);
        TextView a = Ui.text(this, Ui.bold, 26, Ui.INK);
        a.setText(addr.replace("http://", ""));
        a.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        ap.topMargin = Ui.dp(6);
        words.addView(a, ap);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        wp.setMarginStart(Ui.dp(20));
        card.addView(words, wp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        cp.topMargin = Ui.dp(18);
        col.addView(card, 2, cp);
    }

    /** الرمز أسود على أبيض بمربعات كاملة البكسل (بدون تنعيم) وهامش ثلاث مربعات، بعرض قريب من size. */
    private static Bitmap qr(String text, int size) {
        boolean[][] m = QR.encode(text);
        int n = m.length + 6, s = Math.max(3, size / n), w = n * s;
        int[] px = new int[w * w];
        for (int y = 0; y < w; y++)
            for (int x = 0; x < w; x++) {
                int mx = x / s - 3, my = y / s - 3;
                px[y * w + x] = mx >= 0 && my >= 0 && mx < m.length && my < m.length && m[my][mx] ? 0xFF000000 : 0xFFFFFFFF;
            }
        return Bitmap.createBitmap(px, w, w, Bitmap.Config.ARGB_8888);
    }

    private TextView segment(String label, final boolean x) {
        TextView s = Ui.text(this, Ui.medium, 16, 0);
        s.setText(label);
        s.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        s.setGravity(Gravity.CENTER);
        s.setTextColor(Ui.inks(Ui.INK2, Ui.BG, Ui.INK));
        s.setBackground(Ui.states(Ui.round(0, 22), Ui.round(Ui.INK, 22), Ui.round(Ui.RAISED, 22)));
        s.setPadding(Ui.dp(10), Ui.dp(12), Ui.dp(10), Ui.dp(12));
        s.setFocusable(true);
        s.setClickable(true);
        s.setOnClickListener(v -> pick(x));
        return s;
    }

    private void pick(boolean x) {
        xtream = x;
        kindX.setActivated(x);
        kindM.setActivated(!x);
        xBox.setVisibility(x ? View.VISIBLE : View.GONE);
        mBox.setVisibility(x ? View.GONE : View.VISIBLE);
        status.setVisibility(View.GONE);
    }

    private EditText field(LinearLayout box, String label, String hint, int variation) {
        TextView l = Ui.text(this, Ui.medium, 15, Ui.INK2);
        l.setText(label);
        l.setPadding(Ui.dp(4), Ui.dp(14), Ui.dp(4), Ui.dp(7));
        box.addView(l);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTypeface(Ui.medium);
        e.setTextSize(17);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(Ui.INK3);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | variation);
        e.setTypeface(Ui.medium);   // أنواع الإدخال مثل كلمة المرور ترجّع الخط monospace
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        e.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        e.setBackground(Ui.states(Ui.ring(Ui.SURFACE, Ui.LINE, 1, 16), Ui.ring(Ui.SURFACE, Ui.INK, 2, 16), null));
        e.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
        box.addView(e, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        return e;
    }

    private void say(String msg, boolean ok) {
        status.setText(msg);
        status.setTextColor(ok ? Ui.OK : Ui.OFF);
        status.setVisibility(View.VISIBLE);
    }

    private void check() {
        if (busy) return;
        final String[] err = new String[1];
        final Source s = build(xtream, server.getText().toString(), user.getText().toString(), pass.getText().toString(),
                url.getText().toString(), name.getText().toString(), err);
        if (s == null) {
            say(err[0], false);
            return;
        }
        busy = true;
        save.setText("نتحقّق…");
        say("نتصل بالسيرفر…", true);
        final String linkText = url.getText().toString();
        final boolean fromLink = !xtream;
        Net.io.execute(() -> {
            String error = null;
            Source result = null;
            try {
                result = verified(s, fromLink, linkText);
            } catch (Exception e) {
                error = Net.describe(e);
            }
            final String fe = error;
            final Source fr = result;
            Net.main.post(() -> {
                busy = false;
                save.setText("احفظ وتحقّق");
                if (fe != null) say(fe, false);
                else saved(fr);
            });
        });
    }

    private void saved(Source s) {
        if (isFinishing()) return;
        Store.add(s);
        finish();
    }

    /** الاشتراك من المدخلات (من الشاشة أو من الجوال). null لو ناقص، والسبب في err[0]. */
    static Source build(boolean xtream, String server, String user, String pass, String link, String name, String[] err) {
        Source s;
        if (xtream) {
            if (server == null || server.trim().isEmpty() || user == null || user.trim().isEmpty() || pass == null || pass.trim().isEmpty()) {
                err[0] = "اكتب رابط السيرفر واسم المستخدم وكلمة المرور.";
                return null;
            }
            s = Source.xtream(server, user, pass);
        } else {
            if (link == null || link.trim().isEmpty()) {
                err[0] = "اكتب رابط القائمة.";
                return null;
            }
            Source x = Source.fromXtreamLink(link);
            s = x != null ? x : Source.m3u(link);
        }
        String n = name == null ? "" : name.trim();
        s.name = n.isEmpty() ? null : n;
        return s;
    }

    /** يتحقق من خيط خلفي. رابط get.php لو ما ضبط كـ Xtream نجربه قائمة M3U عادية. */
    static Source verified(Source s, boolean fromLink, String linkText) throws Exception {
        try {
            verify(s);
            return s;
        } catch (Exception e) {
            if (!fromLink || !s.xtream()) throw e;
            Source m = Source.m3u(linkText);
            m.name = s.name;
            try {
                verify(m);
                return m;
            } catch (Exception ignored) {
                throw e;
            }
        }
    }

    private static void verify(Source s) throws Exception {
        if (s.xtream()) {
            JSONObject o;
            try {
                o = new JSONObject(Net.text(s.api(null)));
            } catch (org.json.JSONException e) {
                throw new IOException("الرابط ما رد مثل سيرفر Xtream. تأكد من الرابط والمنفذ.");
            }
            JSONObject u = o.optJSONObject("user_info");
            if (u == null) throw new IOException("الرابط ما رد مثل سيرفر Xtream. تأكد من الرابط والمنفذ.");
            if (u.optInt("auth", 1) == 0) throw new IOException("اسم المستخدم أو كلمة المرور غلط.");
            String st = u.optString("status", "Active");
            if (!st.equalsIgnoreCase("Active")) throw new IOException("الاشتراك موقوف أو منتهي (" + st + ").");
        } else {
            java.net.HttpURLConnection c = Net.open(s.url);
            try (java.io.InputStream in = c.getInputStream()) {
                byte[] head = new byte[4096];
                int n = 0;
                for (int r; n < head.length && (r = in.read(head, n, head.length - n)) > 0; ) n += r;
                String text = new String(head, 0, n, "UTF-8");
                if (!text.contains("#EXTM3U") && !text.contains("#EXTINF")) throw new IOException("الرابط ما فيه قائمة M3U.");
            } finally {
                c.disconnect();
            }
        }
    }
}
