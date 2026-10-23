package io.github.saadharazi.shasha;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * القائمة الجانبية (التلفزيون): أيقونات على اليمين، ولما تروح لها تظهر أسماؤها وتغمق الشاشة وراها.
 * ما تغيّر مقاسات الصفحة: الأسماء والتغميق يظهرون بالشفافية بس.
 */
final class Rail extends FrameLayout {
    interface Pick {
        void pick(int id);
    }

    static final int LIVE = 0, MOVIES = 1, SERIES = 2, MINE = 3, HOME = 4, SEARCH = 5, SETTINGS = 6;
    static final int WIDTH = 64;

    private final LinearLayout col;
    private final View shade;
    private final LinearLayout[] items = new LinearLayout[7];
    boolean open;

    Rail(Context c, final Pick pick) {
        super(c);
        shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xFA0B0D12, 0xEB0B0D12, 0x000B0D12}));
        shade.setAlpha(0f);
        addView(shade, new LayoutParams(Ui.dp(380), Ui.MATCH, Gravity.START));

        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Ui.dp(26), 0, Ui.dp(24));
        ImageView mark = new ImageView(c);
        mark.setImageResource(R.drawable.mark);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(30), Ui.dp(30));
        mp.setMarginStart(Ui.dp(17));
        mp.bottomMargin = Ui.dp(34);
        col.addView(mark, mp);
        int[] order = {SEARCH, HOME, LIVE, MOVIES, SERIES, MINE};
        String[] names = {"بحث", "الرئيسية", "مباشر", "أفلام", "مسلسلات", "قائمتي"};
        int[] icons = {R.drawable.ic_search, R.drawable.ic_home, R.drawable.ic_tv, R.drawable.ic_film, R.drawable.ic_series, R.drawable.ic_bookmark};
        for (int i = 0; i < order.length; i++) col.addView(items[order[i]] = item(c, order[i], icons[i], names[i], pick));
        col.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1));
        col.addView(items[SETTINGS] = item(c, SETTINGS, R.drawable.ic_gear, "الإعدادات", pick));
        addView(col, new LayoutParams(Ui.dp(230), Ui.MATCH, Gravity.START));

        // ما يطلع التركيز من فوق ولا من تحت القائمة
        LinearLayout first = items[SEARCH], last = items[SETTINGS];
        first.setNextFocusUpId(first.getId());
        last.setNextFocusDownId(last.getId());
        for (LinearLayout it : items) it.setNextFocusRightId(it.getId());
    }

    private LinearLayout item(Context c, final int id, int icon, String name, final Pick pick) {
        LinearLayout r = new LinearLayout(c);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPaddingRelative(Ui.dp(11), 0, Ui.dp(16), 0);
        r.setBackground(Ui.states(new ColorDrawable(0), Ui.round(Ui.INK, 20), null));
        r.setFocusable(true);
        r.setClickable(true);
        ColorStateList ink = new ColorStateList(new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_activated}, {}},
                new int[]{Ui.BG, Ui.INK, Ui.INK3});
        ImageView im = new ImageView(c);
        Drawable d = c.getDrawable(icon).mutate();
        d.setTintList(ink);
        im.setImageDrawable(d);
        im.setDuplicateParentStateEnabled(true);
        r.addView(im, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        TextView t = Ui.text(c, Ui.medium, 17, 0);
        t.setTextColor(ink);
        t.setText(name);
        t.setDuplicateParentStateEnabled(true);
        t.setAlpha(0f);
        Ui.markRoom(t, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        tp.setMarginStart(Ui.dp(16));
        r.addView(t, tp);
        r.setOnClickListener(v -> pick.pick(id));
        r.setOnFocusChangeListener((v, f) -> post(() -> setOpen(hasFocus())));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(196), Ui.dp(42));
        lp.setMarginStart(Ui.dp(10));
        lp.bottomMargin = Ui.dp(6);
        r.setLayoutParams(lp);
        return r;
    }

    /** الصفحة الحالية (أيقونتها أفتح). */
    void active(int id) {
        for (int i = 0; i < items.length; i++) items[i].setActivated(i == id);
    }

    void focus(int id) {
        items[id].requestFocus();
    }

    void focusActive() {
        for (LinearLayout it : items) if (it.isActivated()) {
            it.requestFocus();
            return;
        }
        items[HOME].requestFocus();
    }

    private void setOpen(boolean on) {
        if (on == open) return;
        open = on;
        shade.animate().alpha(on ? 1f : 0f).setDuration(220).start();
        for (LinearLayout it : items) it.getChildAt(1).animate().alpha(on ? 1f : 0f).setStartDelay(on ? 60 : 0).setDuration(180).start();
    }
}
