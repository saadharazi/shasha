package io.github.saadharazi.shasha;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * واجهة التلفزيون مثل نتفلكس: فوق معلومات العنصر المركّز (أو الواجهة المميزة بأزرارها)، وتحت صفوف تمشي يمين ويسار.
 * التنقل نحسبه بأنفسنا: صف، وخانة في الصف، وكل صف يتذكر وين وقفت (بحث أندرويد عن الأقرب يضيع مع الصفوف المتحركة).
 * الخانات تنبني بس للصفوف القريبة، والمعلومات والخلفية تتبدّل لما يهدأ التنقل، والحركة كلها نقل وشفافية.
 */
final class Rows extends FrameLayout {
    interface Host {
        void open(Item it, List<Item> row, int index);

        void play(Item it);

        void details(Item it);

        void more(Row r);

        void options(Item it);

        void backdrop(Item it);

        void toRail();
    }

    static final class Row {
        final String title, group;
        final int type, kind;
        final List<Item> items;

        Row(String title, int type, int kind, List<Item> items, String group) {
            this.title = title;
            this.type = type;
            this.kind = kind;
            this.items = items;
            this.group = group;
        }
    }

    // بالـ dp على شاشة 960×540: المعلومات فوق يمين، والصف المركّز يثبت تحتها
    private static final int START = 84, INFO_TOP = 56, BILL_Y = 362, ROW_Y = 292, KEEP = 2;

    private final Host host;
    private final Handler h = new Handler();
    private final LinearLayout info, meta, cont, buttons, dots, list;
    private final TextView title, plot, contText, contLeft, message;
    private final FrameLayout contBar;
    private final ProgressBar spin;
    private final TextView playBtn, moreBtn, favBtn;
    private List<Row> rows = new ArrayList<>();
    private List<Item> featured = new ArrayList<>();
    private int[] col = new int[0];
    private int row = -1, feat, btn;
    private Item shown;

    Rows(Context c, Host host) {
        super(c);
        this.host = host;
        setClipChildren(false);

        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        addView(list, new LayoutParams(Ui.MATCH, Ui.WRAP));

        info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        LayoutParams ip = new LayoutParams(Ui.dp(470), Ui.WRAP, Gravity.TOP | Gravity.START);
        ip.setMarginStart(Ui.dp(START));
        ip.topMargin = Ui.dp(INFO_TOP);
        addView(info, ip);

        title = Ui.text(c, Ui.bold, 40, Ui.INK);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(Ui.dp(10), 0, Ui.dp(2), 0x66000000);
        Ui.markRoom(title, true);
        info.addView(title, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        meta = new LinearLayout(c);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(meta, margins(Ui.WRAP, 8));

        cont = new LinearLayout(c);
        cont.setGravity(Gravity.CENTER_VERTICAL);
        contText = Ui.text(c, Ui.medium, 14, Ui.INK2);
        contText.setSingleLine(true);
        cont.addView(contText);
        contBar = new FrameLayout(c);
        LinearLayout.LayoutParams cb = new LinearLayout.LayoutParams(Ui.dp(120), Ui.dp(3));
        cb.setMarginStart(Ui.dp(12));
        cb.setMarginEnd(Ui.dp(12));
        cont.addView(contBar, cb);
        contLeft = Ui.text(c, Ui.medium, 14, Ui.INK2);
        cont.addView(contLeft);
        info.addView(cont, margins(Ui.WRAP, 10));

        plot = Ui.text(c, Ui.regular, 15, Ui.INK2);
        plot.setMaxLines(3);
        plot.setEllipsize(TextUtils.TruncateAt.END);
        plot.setLineSpacing(Ui.dp(4), 1f);
        Ui.markRoom(plot, false);
        info.addView(plot, margins(Ui.MATCH, 8));

        buttons = new LinearLayout(c);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setClipChildren(false);
        playBtn = button(c, R.drawable.ic_play_fill, "شغّل");
        moreBtn = button(c, R.drawable.ic_info, "معلومات أكثر");
        favBtn = button(c, R.drawable.ic_plus, null);
        for (TextView b : new TextView[]{playBtn, moreBtn, favBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(b == favBtn ? Ui.dp(40) : Ui.WRAP, Ui.dp(40));
            lp.setMarginEnd(Ui.dp(8));
            buttons.addView(b, lp);
        }
        playBtn.setOnClickListener(v -> host.play(featured.get(feat)));
        moreBtn.setOnClickListener(v -> host.details(featured.get(feat)));
        favBtn.setOnClickListener(v -> {
            Item it = featured.get(feat);
            Ui.note((android.app.Activity) getContext(), Store.toggleFav(it) ? "صار في قائمتي" : "طلع من قائمتي");
            favIcon(it);
        });
        info.addView(buttons, margins(Ui.WRAP, 18));
        dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(dots, margins(Ui.WRAP, 16));

        LinearLayout state = new LinearLayout(c);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER);
        spin = new ProgressBar(c);
        spin.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK2));
        state.addView(spin, new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(34)));
        message = Ui.text(c, Ui.regular, 17, Ui.INK2);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        message.setMaxWidth(Ui.dp(460));
        message.setLineSpacing(Ui.dp(4), 1f);
        Ui.markRoom(message, false);
        state.addView(message, margins(Ui.WRAP, 14));
        addView(state, new LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
    }

    // ---- الصفحة

    /** يرسم الصفحة. لو keep، يرجع لنفس الصف والخانة (مثلا بعد ما ترجع من المشغّل). */
    void set(List<Row> rows, List<Item> featured, boolean keep) {
        String was = keep && row >= 0 && row < this.rows.size() ? this.rows.get(row).title : null;
        int wasCol = was != null ? col[row] : 0;
        h.removeCallbacksAndMessages(null);
        setFocusable(false);
        this.rows = rows;
        this.featured = featured;
        col = new int[rows.size()];
        feat = Math.min(feat, Math.max(0, featured.size() - 1));
        list.removeAllViews();
        for (Row r : rows) list.addView(rowView(r));
        ((View) message.getParent()).setVisibility(rows.isEmpty() && featured.isEmpty() ? View.VISIBLE : View.GONE);
        info.setVisibility(View.VISIBLE);
        int target = -1;
        for (int i = 0; was != null && i < rows.size(); i++) if (rows.get(i).title.equals(was)) target = i;
        if (target >= 0) {
            col[target] = Math.min(wasCol, count(target) - 1);
            row = target;
            populate();
            list.setTranslationY(Ui.dp(ROW_Y) - rowTop(target));
            fadeAbove(false);
            final int back = target;
            post(() -> go(back, col[back]));
        } else if (!featured.isEmpty()) {
            row = -1;
            populate();
            list.setTranslationY(Ui.dp(BILL_Y));
            toBill();
        } else {
            row = -1;
            populate();
            list.setTranslationY(Ui.dp(ROW_Y));
            if (!rows.isEmpty()) post(() -> go(0, 0));
            else info.setVisibility(View.GONE);
        }
        h.postDelayed(rotate, 9000);
    }

    void loading(String msg, boolean spinner) {
        rows = new ArrayList<>();
        featured = new ArrayList<>();
        list.removeAllViews();
        info.setVisibility(View.GONE);
        spin.setVisibility(spinner ? View.VISIBLE : View.GONE);
        message.setText(msg);
        ((View) message.getParent()).setVisibility(View.VISIBLE);
        // التركيز يبقى هنا لين تجهز الصفوف (بدل ما يروح للقائمة الجانبية وتنفتح)
        setFocusable(true);
        if (getVisibility() == View.VISIBLE) requestFocus();
    }

    void empty(String msg) {
        loading(msg, false);
    }

    /** يرجّع التركيز لمكانه (بعد القائمة الجانبية). */
    void restore() {
        if (row >= 0) go(row, col[row]);
        else if (!featured.isEmpty()) buttons.getChildAt(btn).requestFocus();
        else if (!rows.isEmpty()) go(0, 0);
    }

    boolean inRows() {
        return row >= 0 && !featured.isEmpty();
    }

    // ---- الريموت

    /** يرجّع false لو المفتاح مو لنا. */
    boolean key(int code) {
        if (rows.isEmpty() && featured.isEmpty()) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row + 1 < rows.size()) go(row + 1, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) go(row - 1, -1);
                else if (row == 0 && !featured.isEmpty()) toBill();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return step(1);   // من اليمين لليسار: اللي بعده
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return step(-1);
        }
        return false;
    }

    private boolean step(int d) {
        if (row < 0) {
            int n = btn + d;
            if (n < 0) host.toRail();
            else if (n < buttons.getChildCount()) buttons.getChildAt(btn = n).requestFocus();
            return true;
        }
        int n = col[row] + d;
        if (n < 0) host.toRail();
        else if (n < count(row)) go(row, n);
        return true;
    }

    void toBill() {
        if (featured.isEmpty()) return;
        row = -1;
        placeRows();
        showFeat();
        buttons.getChildAt(btn = 0).requestFocus();
    }

    private void go(int r, int c) {
        if (r < 0 || r >= rows.size()) return;
        boolean moved = r != row;
        row = r;
        if (c >= 0) col[r] = c;
        col[r] = Math.max(0, Math.min(col[r], count(r) - 1));
        if (moved) {
            populate();
            placeRows();
        }
        scrollTo(r);
        View card = track(r).getChildAt(col[r]);
        if (card != null) card.requestFocus();
    }

    private int count(int r) {
        Row x = rows.get(r);
        return x.items.size() + (x.group != null ? 1 : 0);
    }

    // ---- الحركة

    private void placeRows() {
        float y = row < 0 ? Ui.dp(BILL_Y) : Ui.dp(ROW_Y) - rowTop(row);
        list.animate().translationY(y).setDuration(380).setInterpolator(new DecelerateInterpolator(2f)).start();
        fadeAbove(true);
        boolean bill = row < 0;
        buttons.setVisibility(bill ? View.VISIBLE : View.GONE);
        dots.setVisibility(bill && featured.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void fadeAbove(boolean animate) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            float a = row >= 0 && i < row ? 0f : 1f;
            if (v.getAlpha() == a) continue;
            if (animate) v.animate().alpha(a).setDuration(260).start();
            else v.setAlpha(a);
        }
    }

    private int rowTop(int r) {
        int y = 0;
        for (int i = 0; i < r; i++) y += rowHeight(rows.get(i));
        return y;
    }

    private static int rowHeight(Row r) {
        return Ui.dp(38) + Art.height(r.type) + Ui.dp(18);
    }

    /** الخانة المركّزة تبان كاملة: نحرّك الصف بالحساب (العرض ثابت لكل نوع). */
    private void scrollTo(int r) {
        View track = track(r);
        int stepPx = Art.step(rows.get(r).type), w = getWidth(), start = Ui.dp(START) - Ui.dp(Art.PAD);
        float right = w - start - col[r] * stepPx, left = right - (stepPx - Ui.dp(Art.GAP));
        float tx = track.getTranslationX(), m = Ui.dp(40);
        if (left + tx < m) tx = m - left;
        if (right + tx > w - start) tx = w - start - right;
        tx = Math.max(0, tx);
        if (tx != track.getTranslationX()) track.animate().translationX(tx).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    // ---- الصفوف

    /** صف: عنوانه وتحته الخانات (تنبني لما يقرب التركيز). */
    private View rowView(Row r) {
        LinearLayout v = new LinearLayout(getContext()) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;   // الشفافية على كل خانة لحالها، بدون طبقة كاملة للصف
            }
        };
        v.setOrientation(LinearLayout.VERTICAL);
        v.setClipChildren(false);
        TextView t = Ui.text(getContext(), Ui.bold, 16, Ui.INK);
        t.setText(r.title);
        t.setSingleLine(true);
        Ui.markRoom(t, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(38));
        tp.setMarginStart(Ui.dp(START));
        v.addView(t, tp);
        FrameLayout vp = new FrameLayout(getContext());
        vp.setClipChildren(false);
        LinearLayout track = new LinearLayout(getContext());
        track.setClipChildren(false);
        track.setClipToPadding(false);
        track.setPaddingRelative(Ui.dp(START) - Ui.dp(Art.PAD), 0, 0, 0);
        vp.addView(track, new LayoutParams(Ui.MATCH, Ui.WRAP));
        v.addView(vp, new LinearLayout.LayoutParams(Ui.MATCH, Art.height(r.type) + Ui.dp(18)));
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.MATCH, rowHeight(r)));
        return v;
    }

    private LinearLayout track(int r) {
        return (LinearLayout) ((FrameLayout) ((LinearLayout) list.getChildAt(r)).getChildAt(1)).getChildAt(0);
    }

    /** نبني خانات الصفوف القريبة من التركيز، ونفضّي البعيدة (ذاكرة التلفزيون محدودة). */
    private void populate() {
        int at = Math.max(row, 0);
        for (int i = 0; i < rows.size(); i++) {
            LinearLayout t = track(i);
            boolean near = i >= at - 1 && i <= at + KEEP;
            if (near && t.getChildCount() == 0) fill(i, t);
            else if (!near && Math.abs(i - at) > KEEP + 2 && t.getChildCount() > 0) t.removeAllViews();
        }
    }

    private void fill(final int r, LinearLayout track) {
        final Row x = rows.get(r);
        for (int i = 0; i < x.items.size(); i++) {
            Item it = x.items.get(i);
            View card = x.type == Art.TOP ? Art.top(getContext(), it, i + 1) : x.type == Art.WIDE ? Art.wide(getContext(), it)
                    : x.type == Art.CHANNEL ? Art.channel(getContext(), it) : Art.poster(getContext(), it);
            hook(card, r, i);
            track.addView(card, cardParams());
        }
        if (x.group != null) {
            View more = Art.more(getContext());
            hook(more, r, x.items.size());
            track.addView(more, cardParams());
        }
    }

    private static LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginEnd(Ui.dp(Art.GAP));
        return lp;
    }

    private void hook(View card, final int r, final int i) {
        final Art.Holder hd = (Art.Holder) card.getTag();
        card.setOnFocusChangeListener((v, f) -> {
            Art.focus(v, f);
            if (!f || row != r) return;
            col[r] = i;
            h.removeCallbacks(calm);
            calmItem = hd.item;
            calmRank = rows.get(r).type == Art.TOP ? i + 1 : 0;
            h.postDelayed(calm, 180);
        });
        card.setOnClickListener(v -> {
            if (hd.item == null) host.more(rows.get(r));
            else host.open(hd.item, rows.get(r).items, i);
        });
        card.setOnLongClickListener(v -> {
            if (hd.item != null) host.options(hd.item);
            return true;
        });
    }

    // ---- المعلومات فوق

    private Item calmItem;
    private int calmRank;
    // لما يوقف التنقل: المعلومات، وبعدها بشوي الخلفية والقصة من السيرفر
    private final Runnable calm = new Runnable() {
        public void run() {
            if (calmItem == null) {
                info.setVisibility(View.INVISIBLE);
                return;
            }
            info.setVisibility(View.VISIBLE);
            show(calmItem, calmRank);
            h.removeCallbacks(later);
            h.postDelayed(later, 260);
        }
    };
    private final Runnable later = new Runnable() {
        public void run() {
            final Item it = shown;
            if (it == null) return;
            host.backdrop(it);
            if (it.plot == null && (it.kind == Item.MOVIE || it.kind == Item.SERIES) && Library.source != null && Library.source.xtream())
                Library.info(it, o -> {
                    Library.fill(it, o);
                    if (it == shown) show(it, calmRank);
                    if (it == shown && it.back != null) host.backdrop(it);
                });
        }
    };

    private void show(Item it, int rank) {
        boolean same = it == shown;
        shown = it;
        title.setText(it.name);
        meta.removeAllViews();
        if (rank > 0) meta.addView(pill("رقم " + rank, 0xFFFFFFFF, Ui.BG));
        if (it.kind == Item.LIVE) {
            meta.addView(pill("مباشر", 0xFFFF4D5E, 0xFFFFFFFF));
            if (it.num > 0) meta.addView(word("القناة " + it.num));
        }
        if (it.rating != null) {
            TextView s = word(it.rating);
            s.setTextColor(Ui.WARN);
            s.setCompoundDrawablesRelative(Ui.icon(getContext(), R.drawable.ic_star_fill, 14, Ui.WARN), null, null, null);
            s.setCompoundDrawablePadding(Ui.dp(5));
            meta.addView(s);
        }
        if (it.year != null) meta.addView(word(it.year));
        if (it.mins > 0) meta.addView(word(Ui.count(it.mins, "دقيقة", "دقيقتين", "دقايق", "دقيقة")));
        if (it.kind == Item.MOVIE || it.kind == Item.SERIES)
            for (String g : genres(it.sub)) meta.addView(pill(g, 0x14FFFFFF, Ui.INK));
        meta.setVisibility(meta.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        boolean watching = it.dur > 0 && it.pos > 0;
        cont.setVisibility(watching ? View.VISIBLE : View.GONE);
        if (watching) {
            contText.setText(it.kind == Item.EPISODE ? it.sub : null);
            contText.setVisibility(it.kind == Item.EPISODE ? View.VISIBLE : View.GONE);
            contBar.removeAllViews();
            contBar.addView(Art.bar(getContext(), it.pos / (float) it.dur, 120), new LayoutParams(Ui.MATCH, Ui.MATCH));
            int left = (int) Math.max(1, (it.dur - it.pos) / 60000);
            contLeft.setText("باقي " + Ui.count(left, "دقيقة", "دقيقتين", "دقايق", "دقيقة"));
        }
        plot.setText(it.plot);
        plot.setVisibility(it.plot == null || it.plot.isEmpty() ? View.GONE : View.VISIBLE);
        if (!same) {
            info.setAlpha(0.3f);
            info.animate().alpha(1f).setDuration(180).start();
        }
    }

    /** الواجهة المميزة: نفس المعلومات مع أزرار، وتتبدّل كل 9 ثواني لين تتحرك. */
    private void showFeat() {
        if (featured.isEmpty()) return;
        Item it = featured.get(feat);
        h.removeCallbacks(calm);
        info.setVisibility(View.VISIBLE);
        show(it, 0);
        h.removeCallbacks(later);
        h.postDelayed(later, 60);
        long at = it.kind == Item.MOVIE ? Store.resumeAt(it) : 0;
        playBtn.setText(it.kind == Item.SERIES ? "الحلقات" : at > 0 ? "كمّل" : "شغّل");
        favIcon(it);
        dots.removeAllViews();
        for (int i = 0; i < featured.size(); i++) {
            View d = new View(getContext());
            d.setBackground(Ui.round(i == feat ? Ui.INK : 0x4DFFFFFF, 3));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(i == feat ? 18 : 6), Ui.dp(4));
            lp.setMarginEnd(Ui.dp(5));
            dots.addView(d, lp);
        }
    }

    private final Runnable rotate = new Runnable() {
        public void run() {
            if (row < 0 && featured.size() > 1 && buttons.hasFocus()) {
                feat = (feat + 1) % featured.size();
                showFeat();
            }
            h.postDelayed(this, 9000);
        }
    };

    private void favIcon(Item it) {
        boolean on = Store.isFav(it);
        Drawable d = getContext().getDrawable(on ? R.drawable.ic_check : R.drawable.ic_plus).mutate();
        d.setTintList(favBtn.getTextColors());
        d.setBounds(0, 0, Ui.dp(20), Ui.dp(20));
        favBtn.setCompoundDrawablesRelative(d, null, null, null);
        favBtn.setContentDescription(on ? "شيل من قائمتي" : "أضف لقائمتي");
    }

    private static List<String> genres(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String g : s.split("\\s*[,/،|]\\s*")) if (!g.isEmpty() && out.size() < 2) out.add(g);
        return out;
    }

    private TextView word(String s) {
        TextView t = Ui.text(getContext(), Ui.medium, 15, Ui.INK2);
        t.setText(s);
        t.setSingleLine(true);
        Ui.markRoom(t, true);
        t.setLayoutParams(gap());
        return t;
    }

    private TextView pill(String s, int fill, int ink) {
        TextView t = Ui.text(getContext(), Ui.medium, 13, ink);
        t.setText(s);
        t.setSingleLine(true);
        t.setBackground(fill == 0x14FFFFFF ? Ui.ring(fill, 0x29FFFFFF, 1, 12) : Ui.round(fill, 12));
        t.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        t.setLayoutParams(gap());
        return t;
    }

    private static LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginEnd(Ui.dp(12));
        return lp;
    }

    private static LinearLayout.LayoutParams margins(int w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, Ui.WRAP);
        lp.topMargin = Ui.dp(top);
        return lp;
    }

    /** صف خانات لحاله (صفحة التفاصيل): يمين ويسار بين خاناته، وفوق يرجع للي فوقه، والتمرير مثل صفوف الواجهة. */
    static final class Strip extends FrameLayout {
        private final LinearLayout track;
        private final int type, start;
        private Runnable up;
        int at;

        Strip(Context c, int type, int startDp) {
            super(c);
            this.type = type;
            start = Ui.dp(startDp) - Ui.dp(Art.PAD);
            setClipChildren(false);
            track = new LinearLayout(c);
            track.setClipChildren(false);
            track.setClipToPadding(false);
            track.setPaddingRelative(start, 0, 0, 0);
            addView(track, new LayoutParams(Ui.MATCH, Ui.WRAP));
        }

        void onUp(Runnable r) {
            up = r;
        }

        void clear() {
            track.removeAllViews();
            track.setTranslationX(0);
            at = 0;
        }

        void add(View card) {
            final int i = track.getChildCount();
            View.OnFocusChangeListener old = card.getOnFocusChangeListener();
            card.setOnFocusChangeListener((v, f) -> {
                Art.focus(v, f);
                if (old != null) old.onFocusChange(v, f);
                if (f) {
                    at = i;
                    scroll();
                }
            });
            card.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (i + 1 < track.getChildCount()) track.getChildAt(i + 1).requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i > 0) {
                    track.getChildAt(i - 1).requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP && up != null) {
                    up.run();
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_DOWN;
            });
            track.addView(card, cardParams());
        }

        void focus(int i) {
            View v = track.getChildAt(Math.max(0, Math.min(i, track.getChildCount() - 1)));
            if (v != null) v.requestFocus();
        }

        int size() {
            return track.getChildCount();
        }

        /** يورّي الخانة i بدون ما ياخذ التركيز (مثلا آخر حلقة شفتها). */
        void reveal(int i) {
            at = i;
            post(this::scroll);
        }

        private void scroll() {
            int stepPx = Art.step(type), w = getWidth();
            float right = w - start - at * stepPx, left = right - (stepPx - Ui.dp(Art.GAP));
            float tx = track.getTranslationX(), m = Ui.dp(40);
            if (left + tx < m) tx = m - left;
            if (right + tx > w - start) tx = w - start - right;
            tx = Math.max(0, tx);
            if (tx != track.getTranslationX()) track.animate().translationX(tx).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    /** زر الواجهة: شفاف وهو واقف، وأبيض بكلام غامق لما يتركز. */
    static TextView button(Context c, int icon, String label) {
        TextView b = Ui.text(c, Ui.bold, 15, 0);
        ColorStateList ink = Ui.inks(Ui.INK, Ui.BG, Ui.INK);
        b.setTextColor(ink);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.states(Ui.round(0x29FFFFFF, 20), Ui.round(Ui.INK, 20), null));
        b.setPadding(Ui.dp(label == null ? 10 : 18), 0, Ui.dp(label == null ? 10 : 20), 0);
        b.setFocusable(true);
        b.setClickable(true);
        Drawable d = c.getDrawable(icon).mutate();
        d.setTintList(ink);
        d.setBounds(0, 0, Ui.dp(20), Ui.dp(20));
        b.setCompoundDrawablesRelative(d, null, null, null);
        b.setCompoundDrawablePadding(Ui.dp(label == null ? 0 : 8));
        b.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(140).start());
        return b;
    }
}
