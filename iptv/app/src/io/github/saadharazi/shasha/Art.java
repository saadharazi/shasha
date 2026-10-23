package io.github.saadharazi.shasha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * خانات الواجهة الجديدة: بوستر، وخانة عريضة (كمّل المشاهدة)، وقناة، وأفضل 10 برقم كبير جنب البوستر، و«عرض الكل».
 * لما ما فيه صورة يطلع بوستر من الخط على لونين بدل الحرف الأول.
 * التركيز: الإطار يكبر شوي وتظهر حلقة بيضاء؛ ما فيه شي يرسم الشاشة كاملة.
 */
final class Art {
    static final int POSTER = 0, WIDE = 1, TOP = 2, CHANNEL = 3;
    static final int GAP = 6, PAD = 4, POSTER_W = 104, POSTER_H = 156, WIDE_W = 196, WIDE_H = 110, NUM_W = 70, NUM_OVER = 16;
    // غامق، أغمق، وفاتح للكلام
    private static final int[][] PAL = {
            {0xFF1D5A7A, 0xFF071621, 0xFF8FD3F0}, {0xFF8A5A26, 0xFF1D1108, 0xFFF3C98D}, {0xFF8A3550, 0xFF1D0A12, 0xFFF6B0C3},
            {0xFF583080, 0xFF130A1F, 0xFFCFB0F5}, {0xFF8F3418, 0xFF1D0905, 0xFFFFAB7A}, {0xFF24325E, 0xFF070A16, 0xFFB0C0F5},
            {0xFF22604A, 0xFF061510, 0xFFA3E6C6}, {0xFF3A4A5C, 0xFF0B0F15, 0xFFC7D3E0}};

    private Art() {}

    static final class Holder {
        Item item;
        int type, index;
        View frame, ring;
        TextView num;
    }

    static int[] palette(String s) {
        return PAL[((s == null ? 0 : s.hashCode()) & 0x7fffffff) % PAL.length];
    }

    /** عرض الخانة بالبكسل مع المسافة (التمرير ينحسب بدل ما ننتظر الرسم). */
    static int step(int type) {
        int w = type == TOP ? NUM_W + POSTER_W - NUM_OVER : type == POSTER ? POSTER_W : WIDE_W;
        return Ui.dp(w + 2 * PAD + GAP);
    }

    static int height(int type) {
        return Ui.dp((type == POSTER || type == TOP ? POSTER_H : WIDE_H) + 2 * PAD) + (type == WIDE ? Ui.dp(11) : 0);
    }

    static void focus(View card, boolean on) {
        Holder h = (Holder) card.getTag();
        float s = on ? 1.08f : 1f;
        h.frame.animate().scaleX(s).scaleY(s).setDuration(150).start();
        h.ring.animate().alpha(on ? 1f : 0f).setDuration(120).start();
        card.setTranslationZ(on ? Ui.dp(2) : 0);
        if (h.num != null) h.num.setTextColor(on ? 0xF2F3F3F0 : 0x73F3F3F0);
    }

    static View poster(Context c, Item it) {
        View fb = typo(c, it.name, it.year, false);
        ImageView im = image(c);
        LinearLayout card = card(c, it, POSTER);
        card.addView(frame(c, card, POSTER_W, POSTER_H, Ui.SURFACE, fb, im));
        Net.image(im, it.logo, Ui.dp(POSTER_H), fb);
        return card;
    }

    /** كمّل المشاهدة: صورة عريضة وتحتها كم شفت. */
    static View wide(Context c, Item it) {
        View fb = typo(c, it.name, it.kind == Item.EPISODE ? it.sub : it.year, true);
        ImageView im = image(c);
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x99000000, 0x00000000}));
        LinearLayout card = card(c, it, WIDE);
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(frame(c, card, WIDE_W, WIDE_H, Ui.SURFACE, fb, im, shade));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(WIDE_W), Ui.dp(3));
        bp.setMargins(Ui.dp(PAD), Ui.dp(8), Ui.dp(PAD), 0);
        card.addView(bar(c, it.dur > 0 ? it.pos / (float) it.dur : 0, WIDE_W), bp);
        Net.image(im, it.logo, Ui.dp(WIDE_W), fb);
        return card;
    }

    /** قناة: شعارها على لونين، و«مباشر» فوق. */
    static View channel(Context c, Item it) {
        int[] p = palette(it.name);
        TextView name = Ui.text(c, Ui.bold, 17, 0xFFFFFFFF);
        name.setText(it.name);
        name.setGravity(Gravity.CENTER);
        name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        Ui.markRoom(name, true);
        ImageView im = new ImageView(c);
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        im.setPadding(Ui.dp(40), Ui.dp(22), Ui.dp(40), Ui.dp(22));
        TextView live = Ui.text(c, Ui.bold, 11, 0xFFFFFFFF);
        live.setText("مباشر");
        live.setBackground(Ui.round(0xA6000000, 10));
        live.setPadding(Ui.dp(8), Ui.dp(3), Ui.dp(8), Ui.dp(3));
        GradientDrawable dot = Ui.round(0xFFFF4D5E, 3);
        dot.setSize(Ui.dp(6), Ui.dp(6));
        live.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        live.setCompoundDrawablePadding(Ui.dp(5));
        FrameLayout corner = new FrameLayout(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.START);
        lp.setMargins(Ui.dp(8), Ui.dp(8), Ui.dp(8), 0);
        corner.addView(live, lp);
        LinearLayout card = card(c, it, CHANNEL);
        View f = frame(c, card, WIDE_W, WIDE_H, 0, name, im, corner);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{p[0], p[1]});
        bg.setCornerRadius(Ui.dp(10));
        ((FrameLayout) f).getChildAt(0).setBackground(bg);
        card.addView(f);
        Net.image(im, it.logo, Ui.dp(WIDE_W), name);
        return card;
    }

    /** أفضل 10: رقم كبير مفرّغ والبوستر راكب على طرفه. */
    static View top(Context c, Item it, int rank) {
        View fb = typo(c, it.name, it.year, false);
        ImageView im = image(c);
        LinearLayout card = card(c, it, TOP);
        card.setGravity(Gravity.BOTTOM);
        Outline num = new Outline(c);
        num.setTypeface(Ui.bold);
        num.setTextSize(rank >= 10 ? 64 : 118);   // «10» لازم يدخل في نفس العرض
        num.setSingleLine(true);
        num.setIncludeFontPadding(false);
        num.setText(String.valueOf(rank));
        num.setTextColor(0x73F3F3F0);
        num.setGravity(Gravity.BOTTOM | Gravity.END);
        num.setTextDirection(View.TEXT_DIRECTION_LTR);
        num.setLetterSpacing(-0.06f);
        card.addView(num, new LinearLayout.LayoutParams(Ui.dp(NUM_W), Ui.dp(POSTER_H + 2 * PAD)));
        View f = frame(c, card, POSTER_W, POSTER_H, Ui.SURFACE, fb, im);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        fp.setMarginStart(-Ui.dp(NUM_OVER));
        card.addView(f, fp);
        ((Holder) card.getTag()).num = num;
        Net.image(im, it.logo, Ui.dp(POSTER_H), fb);
        return card;
    }

    /** حلقة: صورتها ورقمها، و«شفتها» أو كم شفت منها، وتحتها اسمها ومدتها. */
    static View episode(Context c, Item ep, float seen, boolean done) {
        View fb = typo(c, ep.ep != null ? ep.ep : ep.name, ep.sub, true);
        ImageView im = image(c);
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x99000000, 0x00000000}));
        FrameLayout over = new FrameLayout(c);
        over.addView(badge(c, String.valueOf(ep.num)), corner(Gravity.TOP | Gravity.START));
        if (done) over.addView(badge(c, "شفتها"), corner(Gravity.TOP | Gravity.END));
        else if (seen > 0) {
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(3), Gravity.BOTTOM);
            bp.setMargins(Ui.dp(8), 0, Ui.dp(8), Ui.dp(8));
            over.addView(bar(c, seen, WIDE_W - 16), bp);
        }
        LinearLayout card = card(c, ep, WIDE);
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(frame(c, card, WIDE_W, WIDE_H, Ui.SURFACE, fb, im, shade, over));
        TextView t = Ui.text(c, Ui.bold, 14, Ui.INK);
        t.setText(ep.ep != null ? ep.ep : "الحلقة " + ep.num);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(t, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.dp(WIDE_W), Ui.WRAP);
        tp.setMargins(Ui.dp(PAD), Ui.dp(4), Ui.dp(PAD), 0);
        card.addView(t, tp);
        TextView m = Ui.text(c, Ui.regular, 12, Ui.INK3);
        m.setText("الحلقة " + ep.num + (ep.mins > 0 ? "     " + Ui.count(ep.mins, "دقيقة", "دقيقتين", "دقايق", "دقيقة") : ""));
        m.setSingleLine(true);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.dp(WIDE_W), Ui.WRAP);
        mp.setMargins(Ui.dp(PAD), Ui.dp(3), Ui.dp(PAD), 0);
        card.addView(m, mp);
        Net.image(im, ep.logo, Ui.dp(WIDE_W), fb);
        return card;
    }

    private static TextView badge(Context c, String s) {
        TextView b = Ui.text(c, Ui.bold, 12, Ui.INK);
        b.setText(s);
        b.setBackground(Ui.round(0xB3060810, 11));
        b.setPadding(Ui.dp(8), Ui.dp(3), Ui.dp(8), Ui.dp(3));
        return b;
    }

    private static FrameLayout.LayoutParams corner(int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, gravity);
        lp.setMargins(Ui.dp(8), Ui.dp(8), Ui.dp(8), 0);
        return lp;
    }

    /** آخر الصف: يفتح التصنيف كامل. */
    static View more(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ImageView arrow = new ImageView(c);
        arrow.setImageDrawable(Ui.icon(c, R.drawable.ic_list, 26, Ui.INK2));
        box.addView(arrow, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
        TextView t = Ui.text(c, Ui.medium, 15, Ui.INK);
        t.setText("عرض الكل");
        Ui.markRoom(t, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        tp.topMargin = Ui.dp(8);
        box.addView(t, tp);
        LinearLayout card = card(c, null, POSTER);
        card.addView(frame(c, card, POSTER_W, POSTER_H, Ui.RAISED, box));
        return card;
    }

    /** شريط كم شفت: أبيض على رمادي، يبدأ من اليمين. */
    static View bar(Context c, float p, int widthDp) {
        FrameLayout b = new FrameLayout(c);
        b.setBackground(Ui.round(0x38FFFFFF, 2));
        View fill = new View(c);
        fill.setBackground(Ui.round(Ui.INK, 2));
        b.addView(fill, new FrameLayout.LayoutParams(Math.round(Ui.dp(widthDp) * Math.max(0, Math.min(1, p))), Ui.MATCH, Gravity.START));
        return b;
    }

    /** بوستر من الخط: السنة فوق، وخط ملوّن، والاسم كبير تحت. */
    static View typo(Context c, String name, String top, boolean wide) {
        int[] p = palette(name);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientCenter(0.85f, 0f);
        bg.setGradientRadius(Ui.dp(wide ? 240 : 190));
        bg.setColors(new int[]{p[0], p[1]});
        FrameLayout f = new FrameLayout(c);
        f.setBackground(bg);
        if (top != null) {
            TextView y = Ui.text(c, Ui.medium, 11, p[2]);
            y.setText(top);
            y.setSingleLine(true);
            FrameLayout.LayoutParams yp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.START);
            yp.setMargins(Ui.dp(11), Ui.dp(11), Ui.dp(11), 0);
            f.addView(y, yp);
        }
        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        View line = new View(c);
        line.setBackgroundColor(p[2]);
        bottom.addView(line, new LinearLayout.LayoutParams(Ui.dp(18), Ui.dp(2)));
        TextView n = Ui.text(c, Ui.bold, wide ? 18 : 17, 0xFFFFFFFF);
        n.setText(name);
        n.setMaxLines(wide ? 2 : 4);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setLineSpacing(Ui.dp(1), 1f);
        Ui.markRoom(n, false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        np.topMargin = Ui.dp(4);
        bottom.addView(n, np);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        bp.setMargins(Ui.dp(11), 0, Ui.dp(11), Ui.dp(12));
        f.addView(bottom, bp);
        return f;
    }

    private static ImageView image(Context c) {
        ImageView im = new ImageView(c);
        im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return im;
    }

    private static LinearLayout card(Context c, Item it, int type) {
        LinearLayout card = new LinearLayout(c);
        card.setClipChildren(false);
        card.setClipToPadding(false);
        card.setFocusable(true);
        card.setClickable(true);
        Holder h = new Holder();
        h.item = it;
        h.type = type;
        card.setTag(h);
        return card;
    }

    /** الإطار: الصورة بزوايا مدوّرة، وحولها حلقة التركيز. */
    private static View frame(Context c, View card, int w, int hDp, int fill, View... inside) {
        FrameLayout f = new FrameLayout(c);
        f.setPadding(Ui.dp(PAD), Ui.dp(PAD), Ui.dp(PAD), Ui.dp(PAD));
        f.setClipToPadding(false);
        FrameLayout art = new FrameLayout(c);
        if (fill != 0) art.setBackground(Ui.round(fill, 10));
        art.setClipToOutline(true);
        if (fill == 0) art.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(10));
            }
        });
        for (View v : inside) art.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        f.addView(art, new FrameLayout.LayoutParams(Ui.dp(w), Ui.dp(hDp)));
        View ring = new View(c);
        ring.setBackground(Ui.ring(0, Ui.INK, 2.5f, 13));
        ring.setAlpha(0f);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(Ui.dp(w + 2 * PAD), Ui.dp(hDp + 2 * PAD));
        rp.setMargins(-Ui.dp(PAD), -Ui.dp(PAD), -Ui.dp(PAD), -Ui.dp(PAD));   // فوق الهامش، حول الصورة
        f.addView(ring, rp);
        Holder h = (Holder) card.getTag();
        h.frame = f;
        h.ring = ring;
        return f;
    }

    /** رقم مفرّغ (خط بدون تعبئة). */
    static final class Outline extends TextView {
        Outline(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Paint p = getPaint();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(2));
            p.setStrokeJoin(Paint.Join.ROUND);
            super.onDraw(cv);
        }
    }
}
