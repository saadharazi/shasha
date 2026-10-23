package io.github.saadharazi.shasha;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * خانات الشبكة: صف عريض للقنوات، وبوستر للأفلام والمسلسلات.
 * التركيز نرسمه بأنفسنا (activated + تكبير) لأن مؤشر GridView ما يكبر مع الخانة.
 */
final class Cards extends BaseAdapter {
    static final int ROW = 0, POSTER = 1;
    private static final float LIFT = 1.06f;

    private final Context c;
    private List<Item> list = new ArrayList<>();
    private int mode, cellW;

    Cards(Context c) {
        this.c = c;
    }

    void set(List<Item> l, int mode, int cellW) {
        this.list = l;
        this.mode = mode;
        this.cellW = cellW;
        notifyDataSetChanged();
    }

    List<Item> items() {
        return list;
    }

    int mode() {
        return mode;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Item getItem(int i) {
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int i) {
        return mode;
    }

    private static final class Holder {
        ImageView image;
        TextView letter, name, sub, rate;
        ImageView star;
        View bar;
    }

    @Override
    public View getView(int pos, View v, ViewGroup parent) {
        if (v == null) v = mode == ROW ? row() : poster();
        Holder h = (Holder) v.getTag();
        Item it = list.get(pos);
        h.name.setText(it.name);
        h.letter.setText(it.initial());
        h.star.setVisibility(Store.isFav(it) ? View.VISIBLE : View.GONE);
        if (mode == ROW) {
            h.sub.setText(it.num > 0 ? String.valueOf(it.num) : null);
            Net.image(h.image, it.logo, Ui.dp(96), h.letter);
        } else {
            h.sub.setText(it.kind == Item.EPISODE ? it.sub : it.year);
            h.rate.setText(it.rating);
            h.rate.setVisibility(it.rating == null ? View.GONE : View.VISIBLE);
            Net.image(h.image, it.logo, cellW, h.letter);
            boolean watching = it.dur > 0 && it.pos > 0;
            h.bar.setVisibility(watching ? View.VISIBLE : View.GONE);
            if (watching) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bar.getLayoutParams();
                lp.width = (int) (cellW * Math.min(1f, it.pos / (float) it.dur));
                h.bar.setLayoutParams(lp);
            }
        }
        AdapterView<?> av = (AdapterView<?>) parent;
        boolean on = av.hasFocus() && av.getSelectedItemPosition() == pos;
        light(v, on, false);
        return v;
    }

    /** الخانة اللي عليها التركيز (أو null لما يطلع التركيز من الشبكة). نمر على كل الخانات عشان ما يبقى نور قديم. */
    void focus(ViewGroup grid, View v) {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            if (c.isActivated() != (c == v)) light(c, c == v, true);
        }
    }

    private static void light(View v, boolean on, boolean animate) {
        v.setActivated(on);
        float s = on ? LIFT : 1f;
        if (animate) v.animate().scaleX(s).scaleY(s).setDuration(140).start();
        else {
            v.animate().cancel();
            v.setScaleX(s);
            v.setScaleY(s);
        }
    }

    private View row() {
        Holder h = new Holder();
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        activatedLikeFocus(r);
        r.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, Ui.dp(68)));

        FrameLayout logo = new FrameLayout(c);
        logo.setBackground(Ui.round(0xFF1E2125, 11));
        logo.setClipToOutline(true);
        h.letter = Ui.text(c, Ui.bold, 19, Ui.INK3);
        h.letter.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        h.letter.setGravity(Gravity.CENTER);
        logo.addView(h.letter, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        h.image = new ImageView(c);
        h.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.image.setPadding(Ui.dp(6), Ui.dp(5), Ui.dp(6), Ui.dp(5));
        logo.addView(h.image, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        r.addView(logo, new LinearLayout.LayoutParams(Ui.dp(78), Ui.dp(50)));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPaddingRelative(Ui.dp(14), 0, Ui.dp(4), 0);
        h.name = Ui.text(c, Ui.medium, 16, Ui.INK);
        h.name.setSingleLine(true);
        h.name.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(h.name, false);
        col.addView(h.name);
        LinearLayout meta = new LinearLayout(c);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        h.sub = Ui.text(c, Ui.regular, 13, Ui.INK3);
        meta.addView(h.sub);
        h.star = star();
        meta.addView(h.star);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        mp.topMargin = Ui.dp(3);
        col.addView(meta, mp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        r.addView(col, cp);
        r.setTag(h);
        return r;
    }

    private View poster() {
        Holder h = new Holder();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int ph = Math.round(cellW * 1.5f);
        col.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, ph + Ui.dp(50)));

        FrameLayout art = new FrameLayout(c);
        art.setBackground(Ui.round(Ui.SURFACE, 14));
        art.setClipToOutline(true);
        h.letter = Ui.text(c, Ui.bold, 34, Ui.INK3);
        h.letter.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        h.letter.setGravity(Gravity.CENTER);
        art.addView(h.letter, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        h.image = new ImageView(c);
        h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.addView(h.image, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        h.bar = new View(c);
        h.bar.setBackgroundColor(Ui.ACCENT);
        art.addView(h.bar, new FrameLayout.LayoutParams(0, Ui.dp(4), Gravity.BOTTOM | Gravity.START));
        h.star = star();
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Ui.dp(26), Ui.dp(26), Gravity.TOP | Gravity.START);
        sp.setMargins(Ui.dp(7), Ui.dp(7), Ui.dp(7), Ui.dp(7));
        h.star.setBackground(Ui.round(0xB30F1012, 13));
        h.star.setPadding(Ui.dp(5), Ui.dp(5), Ui.dp(5), Ui.dp(5));
        art.addView(h.star, sp);
        View ring = new View(c);
        ring.setBackground(activeOnly(Ui.ring(0, Ui.INK, 2.5f, 14)));
        art.addView(ring, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        col.addView(art, new LinearLayout.LayoutParams(Ui.MATCH, ph));

        h.name = Ui.text(c, Ui.medium, 14, Ui.INK);
        h.name.setTextColor(Ui.inks(Ui.INK2, Ui.INK, Ui.INK));
        h.name.setSingleLine(true);
        h.name.setEllipsize(TextUtils.TruncateAt.END);
        h.name.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), 0);
        Ui.markRoom(h.name, false);
        col.addView(h.name, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        LinearLayout meta = new LinearLayout(c);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(Ui.dp(2), Ui.dp(2), Ui.dp(2), 0);
        h.sub = Ui.text(c, Ui.regular, 12, Ui.INK3);
        h.sub.setSingleLine(true);
        meta.addView(h.sub, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        h.rate = Ui.text(c, Ui.regular, 12, Ui.INK2);
        h.rate.setCompoundDrawablesRelative(Ui.icon(c, R.drawable.ic_star_fill, 11, Ui.WARN), null, null, null);
        h.rate.setCompoundDrawablePadding(Ui.dp(4));
        meta.addView(h.rate);
        col.addView(meta, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        col.setTag(h);
        return col;
    }

    private ImageView star() {
        ImageView s = new ImageView(c);
        s.setImageDrawable(Ui.icon(c, R.drawable.ic_star_fill, 13, Ui.WARN));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(13), Ui.dp(13));
        lp.setMarginStart(Ui.dp(8));
        s.setLayoutParams(lp);
        return s;
    }

    /** خلفية الصف: المركّز (activated) نفس شكل التركيز. */
    private static void activatedLikeFocus(View r) {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_activated}, Ui.ring(Ui.RAISED, Ui.INK, 2, 16));
        s.addState(new int[]{android.R.attr.state_pressed}, Ui.round(Ui.RAISED, 16));
        s.addState(new int[]{}, Ui.round(Ui.SURFACE, 16));
        r.setBackground(s);
    }

    private static android.graphics.drawable.Drawable activeOnly(android.graphics.drawable.Drawable d) {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_activated}, d);
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }
}
