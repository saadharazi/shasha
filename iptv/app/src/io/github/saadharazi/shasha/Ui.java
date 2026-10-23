package io.github.saadharazi.shasha;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/** ألوان وخطوط ومقاسات مشتركة. المقاسات بالـ dp (شاشة التلفزيون 960×540 dp). */
final class Ui {
    // أسود وأبيض وألوان هادية، نفس ألوان أداة الكمبيوتر
    static final int BG = 0xFF0F1012, SURFACE = 0xFF17191C, RAISED = 0xFF22252A, LINE = 0x1FFFFFFF;
    static final int INK = 0xFFF3F3F0, INK2 = 0xA6F3F3F0, INK3 = 0x70F3F3F0;
    static final int ACCENT = 0xFF8EA8CC, OK = 0xFF6FA388, WARN = 0xFFC9A872, OFF = 0xFFD08F88;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT, MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    static Typeface regular, medium, bold;
    static float density = 1f;

    private Ui() {}

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        if (regular != null) return;
        regular = font(c, "Regular", Typeface.NORMAL);
        medium = font(c, "Medium", Typeface.NORMAL);
        bold = font(c, "Bold", Typeface.BOLD);
    }

    private static Typeface font(Context c, String weight, int fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "fonts/Tajawal-" + weight + ".ttf");
        } catch (Throwable t) {
            return Typeface.create(Typeface.DEFAULT, fallback);
        }
    }

    static boolean isTv(Context c) {
        UiModeManager m = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        return m != null && m.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    static int widthDp(Context c) {
        return c.getResources().getConfiguration().screenWidthDp;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static TextView text(Context c, Typeface face, float sp, int color) {
        TextView t = new TextView(c);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        // الأسماء اللاتينية بعد تبدأ من اليمين مثل العربي، عشان العمود يبقى مرتب
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    /**
     * مكان للهمزة: في Tajawal السطر 0.64em وهمزة «أ» توصل 0.88em، وTextView يقص على حدوده لما هامش الخط مطفي.
     * نزيد مكان فوق (وتحت لو bottomToo) ونرجّع كم زدنا عشان ينرد بهامش سالب.
     */
    static int markRoom(TextView t, boolean bottomToo) {
        int p = (int) Math.ceil(t.getTextSize() * 0.25f);
        t.setPaddingRelative(t.getPaddingStart(), t.getPaddingTop() + p, t.getPaddingEnd(), t.getPaddingBottom() + (bottomToo ? p : 0));
        return p;
    }

    static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    static GradientDrawable ring(int fill, int line, float w, float r) {
        GradientDrawable g = round(fill, r);
        g.setStroke(dp(w), line);
        return g;
    }

    /** خلفية للأزرار والصفوف: المركّز أبيض، والمختار (activated) أفتح شوي. */
    static StateListDrawable states(Drawable normal, Drawable focused, Drawable chosen) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_pressed}, focused);
        if (chosen != null) s.addState(new int[]{android.R.attr.state_activated}, chosen);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** مؤشر ListView: إطار أبيض وهي مركّزة، ولا شي وهي مو مركّزة (الشكل الثابت ما يختفي لحاله). */
    static StateListDrawable selector(int fill, float r) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, ring(fill, INK, 2, r));
        s.addState(new int[]{android.R.attr.state_pressed}, round(RAISED, r));
        s.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(0));
        return s;
    }

    static ColorStateList inks(int normal, int focused, int chosen) {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {android.R.attr.state_activated}, {}},
                new int[]{focused, focused, chosen, normal});
    }

    static Drawable icon(Context c, int res, float size, int color) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        d.setBounds(0, 0, dp(size), dp(size));
        return d;
    }

    /** يكبر شوي لما يتركز (التلفزيون). */
    static void lift(View v, final float s) {
        v.setOnFocusChangeListener((x, f) -> {
            // اللي حواليه ما يقص الزر وهو مكبّر
            // (ثلاث طبقات بس، ونوقف عند اللي يتمرر عشان محتواه ما يطلع برا حدوده)
            android.view.ViewParent p = x.getParent();
            for (int i = 0; i < 3 && p instanceof ViewGroup && !(p instanceof android.widget.FrameLayout)
                    && !(p instanceof android.widget.AdapterView); i++, p = p.getParent()) {
                ((ViewGroup) p).setClipChildren(false);
                ((ViewGroup) p).setClipToPadding(false);
            }
            x.animate().scaleX(f ? s : 1f).scaleY(f ? s : 1f).setDuration(150).start();
        });
    }

    /** زر بحبة: هادي وهو واقف، وأبيض بكلام أسود لما يتركز. */
    static TextView button(Context c, String label, int icon, boolean primary) {
        TextView b = text(c, medium, 16, 0);
        b.setText(label);
        b.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.setGravity(Gravity.CENTER);
        ColorStateList ink = inks(primary ? BG : INK, BG, INK);
        b.setTextColor(ink);
        b.setBackground(states(round(primary ? ACCENT : RAISED, 26), round(INK, 26), null));
        b.setPadding(dp(24), dp(13), dp(24), dp(13));
        b.setFocusable(true);
        b.setClickable(true);
        if (icon != 0) {
            Drawable d = c.getDrawable(icon).mutate();
            d.setTintList(ink);
            d.setBounds(0, 0, dp(19), dp(19));
            b.setCompoundDrawablesRelative(d, null, null, null);
            b.setCompoundDrawablePadding(dp(9));
        }
        lift(b, 1.05f);
        return b;
    }

    /** تنبيه صغير تحت الشاشة يختفي لحاله. */
    static void note(Activity a, String msg) {
        FrameLayout root = a.findViewById(android.R.id.content);
        TextView t = root.findViewWithTag("note");
        if (t == null) {
            t = text(a, medium, 16, INK);
            t.setTag("note");
            t.setBackground(ring(0xF5212428, LINE, 1, 24));
            t.setPadding(dp(22), dp(13), dp(22), dp(13));
            t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            t.setElevation(dp(8));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = dp(36);
            lp.leftMargin = lp.rightMargin = dp(24);
            root.addView(t, lp);
        }
        final TextView n = t;
        n.setText(msg);
        n.bringToFront();
        n.animate().cancel();
        n.setAlpha(0);
        n.setTranslationY(dp(10));
        n.animate().alpha(1).translationY(0).setStartDelay(0).setDuration(180)
                .withEndAction(() -> n.animate().alpha(0).setStartDelay(2600).setDuration(320).start()).start();
    }

    /** عدد بالعربي: قناة وحدة، قناتين، 3 قنوات، 11 قناة. الصفر ما ينكتب. */
    static String count(int n, String one, String two, String few, String many) {
        if (n <= 0) return "";
        if (n == 1) return one;
        if (n == 2) return two;
        return String.format(java.util.Locale.US, "%,d", n) + " " + (n <= 10 ? few : many);
    }

    /** وقت المشاهدة: 1:02:03 أو 12:34. */
    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        long hh = s / 3600, mm = s / 60 % 60, ss = s % 60;
        return hh > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", hh, mm, ss)
                : String.format(java.util.Locale.US, "%d:%02d", mm, ss);
    }
}
