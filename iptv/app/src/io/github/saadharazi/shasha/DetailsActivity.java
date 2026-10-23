package io.github.saadharazi.shasha;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/**
 * صفحة الفيلم أو المسلسل: الصورة كاملة ورا، والاسم والقصة والأزرار فوق يمين، وتحت المواسم وحلقاتها بصورها
 * (أو «ممكن يعجبك» للفيلم). على الجوال نفس الشي تحت بعض.
 */
public class DetailsActivity extends Activity {
    private static final int START = 56;

    private Item item;
    private boolean wide, series;
    private ImageView backdrop;
    private TextView plot, playBtn, startBtn, favBtn;
    private LinearLayout meta, seasonRow, epList;
    private ProgressBar spin;
    private Rows.Strip strip;
    private TreeMap<Integer, List<Item>> seasons = new TreeMap<>();
    private int season = -1;

    static void start(Activity a, Item it) {
        a.startActivity(new Intent(a, DetailsActivity.class).putExtra("item", it.json().toString()));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Net.init(this);
        Store.init(this);
        if (Library.source == null) {
            finish();
            return;
        }
        try {
            item = Item.from(new JSONObject(getIntent().getStringExtra("item")));
        } catch (Exception e) {
            finish();
            return;
        }
        // القصة والصورة العريضة لو جت من القائمة (المسلسلات)
        List<Item> all = Library.items[item.kind == Item.SERIES ? Item.SERIES : Item.MOVIE];
        if (all != null) for (Item x : all) if (x.id.equals(item.id)) {
            item.plot = x.plot;
            item.back = x.back;
            break;
        }
        series = item.kind == Item.SERIES;
        wide = Ui.widthDp(this) >= 640;
        setContentView(build());
        Library.info(item, this::filled);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (item == null) return;
        updateButtons();
        if (season >= 0) showSeason(season);
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Ui.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        blurBehind(item.logo);
        // الكلام على اليمين والحلقات تحت: نغمّق من اليمين ومن تحت
        View side = new View(this);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xF70F1012, 0xD60F1012, 0x330F1012, 0x730F1012}));
        root.addView(side, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View low = new View(this);
        low.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xFF0F1012, 0xD90F1012, 0x000F1012}));
        root.addView(low, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(wide ? 300 : 260), Gravity.BOTTOM));

        LinearLayout info = info();
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        if (series) {
            LinearLayout box = new LinearLayout(this);
            box.setBackground(Ui.ring(0x99141619, 0x1FFFFFFF, 1, 22));
            box.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
            seasonRow = box;
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            sp.setMarginStart(Ui.dp(wide ? START : 18));
            sp.bottomMargin = Ui.dp(12);
            bottom.addView(box, sp);
        } else {
            TextView t = Ui.text(this, Ui.bold, 16, Ui.INK);
            t.setText("ممكن يعجبك");
            Ui.markRoom(t, true);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            tp.setMarginStart(Ui.dp(wide ? START : 18));
            tp.bottomMargin = Ui.dp(8);
            bottom.addView(t, tp);
        }
        if (wide) {
            strip = new Rows.Strip(this, series ? Art.WIDE : Art.POSTER, START);
            bottom.addView(strip, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
            strip.onUp(() -> {
                View t = series && seasonRow.getChildCount() > 0 ? activeSeason() : firstButton();
                t.requestFocus();
            });
        } else {
            epList = new LinearLayout(this);
            epList.setOrientation(LinearLayout.VERTICAL);
            epList.setPaddingRelative(Ui.dp(14), 0, Ui.dp(14), 0);
            bottom.addView(epList);
        }

        if (wide) {
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(480), Ui.WRAP, Gravity.TOP | Gravity.START);
            ip.setMarginStart(Ui.dp(START));
            ip.topMargin = Ui.dp(series ? 34 : 50);
            root.addView(info, ip);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
            bp.bottomMargin = Ui.dp(series ? 14 : 22);
            root.addView(bottom, bp);
        } else {
            ScrollView sv = new ScrollView(this);
            sv.setFillViewport(true);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(Ui.dp(18), Ui.dp(26), Ui.dp(18), Ui.dp(24));
            col.addView(info);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
            bp.topMargin = Ui.dp(26);
            col.addView(bottom, bp);
            sv.addView(col);
            root.addView(sv, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
        if (!series) similar();
        updateButtons();
        playBtn.requestFocus();
        return root;
    }

    /** الاسم والتقييم والسنة والقصة والأزرار. */
    private LinearLayout info() {
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, Ui.bold, wide ? 38 : 28, Ui.INK);
        title.setText(item.name);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(Ui.dp(10), 0, Ui.dp(2), 0x66000000);
        Ui.markRoom(title, true);
        info.addView(title, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        mp.topMargin = Ui.dp(8);
        info.addView(meta, mp);
        fillMeta();
        plot = Ui.text(this, Ui.regular, 15, Ui.INK2);
        plot.setLineSpacing(Ui.dp(4), 1f);
        plot.setMaxLines(wide ? (series ? 2 : 4) : 5);
        plot.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(plot, false);
        plot.setText(item.plot);
        plot.setVisibility(item.plot == null || item.plot.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        pl.topMargin = Ui.dp(10);
        info.addView(plot, pl);
        spin = new ProgressBar(this);
        spin.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK3));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22));
        sl.topMargin = Ui.dp(10);
        info.addView(spin, sl);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setClipChildren(false);
        playBtn = Rows.button(this, R.drawable.ic_play_fill, "شغّل");
        startBtn = Rows.button(this, R.drawable.ic_rotate, series ? "من أول حلقة" : "من البداية");
        favBtn = Rows.button(this, R.drawable.ic_plus, null);
        playBtn.setOnClickListener(v -> play(false));
        startBtn.setOnClickListener(v -> play(true));
        favBtn.setOnClickListener(v -> {
            Ui.note(this, Store.toggleFav(item) ? "صار في قائمتي" : "طلع من قائمتي");
            updateButtons();
        });
        for (TextView bt : new TextView[]{playBtn, startBtn, favBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(bt == favBtn ? Ui.dp(40) : Ui.WRAP, Ui.dp(40));
            lp.setMarginEnd(Ui.dp(8));
            buttons.addView(bt, lp);
            bt.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                return down();
            });
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        bl.topMargin = Ui.dp(18);
        info.addView(buttons, bl);
        return info;
    }

    /** تحت من الأزرار: للمواسم، أو للصف تحت. */
    private boolean down() {
        if (series && seasonRow.getChildCount() > 0) activeSeason().requestFocus();
        else if (strip != null && strip.size() > 0) strip.focus(strip.at);
        else return false;
        return true;
    }

    private View activeSeason() {
        for (int i = 0; i < seasonRow.getChildCount(); i++) if (seasonRow.getChildAt(i).isActivated()) return seasonRow.getChildAt(i);
        return seasonRow.getChildAt(0);
    }

    private View firstButton() {
        return playBtn.getVisibility() == View.VISIBLE ? playBtn : favBtn;
    }

    private void fillMeta() {
        meta.removeAllViews();
        if (item.rating != null) {
            TextView s = word(item.rating);
            s.setTextColor(Ui.WARN);
            s.setCompoundDrawablesRelative(Ui.icon(this, R.drawable.ic_star_fill, 14, Ui.WARN), null, null, null);
            s.setCompoundDrawablePadding(Ui.dp(5));
            meta.addView(s);
        }
        if (item.year != null) meta.addView(word(item.year));
        if (series && !seasons.isEmpty()) meta.addView(word(Ui.count(seasons.size(), "موسم واحد", "موسمين", "مواسم", "موسم")));
        if (!series && item.mins > 0)
            meta.addView(word(item.mins >= 60 ? (item.mins / 60) + " س " + (item.mins % 60) + " د" : Ui.count(item.mins, "دقيقة", "دقيقتين", "دقايق", "دقيقة")));
        if (item.sub != null) {
            String[] g = item.sub.split("\\s*[,/،|]\\s*");
            for (int i = 0; i < Math.min(2, g.length); i++) if (!g[i].isEmpty()) meta.addView(pill(g[i]));
        }
    }

    private TextView word(String s) {
        TextView t = Ui.text(this, Ui.medium, 15, Ui.INK2);
        t.setText(s);
        t.setSingleLine(true);
        Ui.markRoom(t, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginEnd(Ui.dp(12));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView pill(String s) {
        TextView t = word(s);
        t.setTextSize(13);
        t.setTextColor(Ui.INK);
        t.setBackground(Ui.ring(0x14FFFFFF, 0x29FFFFFF, 1, 12));
        t.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        return t;
    }

    /** البوستر مغبّش ورا الصفحة لين تجي الصورة العريضة (لو فيه). */
    private void blurBehind(String url) {
        Net.blur(url, bm -> {
            if (backdrop.getTag() != null) return;
            backdrop.setImageBitmap(bm);
            backdrop.setAlpha(0f);
            backdrop.animate().alpha(0.85f).setDuration(400).start();
        });
        if (item.back != null) sharp();
    }

    private void sharp() {
        Net.bitmap(item.back, getResources().getDisplayMetrics().widthPixels / 2, bm -> {
            backdrop.setTag("sharp");
            backdrop.setImageBitmap(bm);
            backdrop.setAlpha(0.4f);
            backdrop.animate().alpha(1f).setDuration(450).start();
        });
    }

    /** ردّ السيرفر: القصة والمدة والنوع والصورة العريضة، والحلقات للمسلسل. */
    private void filled(JSONObject o) {
        spin.setVisibility(View.GONE);
        boolean hadBack = item.back != null;
        Library.fill(item, o);
        plot.setText(item.plot);
        plot.setVisibility(item.plot == null || item.plot.isEmpty() ? View.GONE : View.VISIBLE);
        if (!hadBack && item.back != null) sharp();
        if (series) {
            seasons = Library.episodes(item, o);
            if (seasons.isEmpty()) {
                plot.setVisibility(View.VISIBLE);
                plot.setText(o == null ? "ما قدرنا نجيب الحلقات من السيرفر. ارجع وجرّب مرة ثانية." : "المسلسل ما فيه حلقات للحين.");
            } else seasonChips();
        }
        fillMeta();
        updateButtons();
        if (series && !seasons.isEmpty() && favBtn.isFocused()) playBtn.requestFocus();   // الحلقات وصلت: «كمّل» أول شي
    }

    private void seasonChips() {
        seasonRow.removeAllViews();
        for (final int s : seasons.keySet()) {
            TextView b = Ui.text(this, Ui.medium, 15, 0);
            b.setText("الموسم " + s);
            b.setTextColor(Ui.inks(Ui.INK2, Ui.BG, Ui.INK));
            b.setBackground(Ui.states(new ColorDrawable(0), Ui.round(Ui.INK, 18), Ui.round(0x29FFFFFF, 18)));
            b.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
            b.setFocusable(true);
            b.setClickable(true);
            b.setTag(s);
            b.setOnClickListener(v -> showSeason(s));
            b.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && strip != null && strip.size() > 0) {
                    strip.focus(strip.at);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    firstButton().requestFocus();
                    return true;
                }
                return false;
            });
            seasonRow.addView(b);
        }
        Item last = lastWatched();
        showSeason(last != null ? seasonOf(last) : seasons.firstKey());
    }

    private void showSeason(int s) {
        if (!seasons.containsKey(s)) return;
        season = s;
        for (int i = 0; i < seasonRow.getChildCount(); i++) seasonRow.getChildAt(i).setActivated(seasonRow.getChildAt(i).getTag().equals(s));
        final List<Item> eps = seasons.get(s);
        Item last = lastWatched();
        int lastSeason = last != null ? seasonOf(last) : -1, lastNum = -1, focusAt = 0;
        if (last != null) for (Item e : seasons.get(lastSeason)) if (e.id.equals(last.id)) lastNum = e.num;
        boolean had = strip != null && strip.hasFocus();
        if (strip != null) strip.clear();
        if (epList != null) epList.removeAllViews();
        for (int i = 0; i < eps.size(); i++) {
            final Item ep = eps.get(i);
            final int at = i;
            float seen = 0;
            for (Item r : Store.recents()) if (r.key().equals(ep.key()) && r.dur > 0) seen = r.pos / (float) r.dur;
            boolean done = s < lastSeason || s == lastSeason && ep.num < lastNum;
            if (s == lastSeason && ep.num == lastNum) focusAt = i;
            View card = Art.episode(this, ep, seen, done);
            card.setOnClickListener(v -> PlayerActivity.start(this, eps, at, false));
            if (strip != null) strip.add(card);
            else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
                lp.bottomMargin = Ui.dp(14);
                epList.addView(card, lp);
            }
        }
        if (strip != null) {
            strip.reveal(focusAt);
            if (had) strip.focus(focusAt);
        }
    }

    /** للفيلم: من نفس التصنيف، الأحدث أول. */
    private void similar() {
        List<Item> out = new ArrayList<>();
        for (Item i : Library.newest(Item.MOVIE))
            if (out.size() < 12 && !i.id.equals(item.id) && i.group != null && i.group.equals(item.group)) out.add(i);
        for (final Item it : out) {
            View card = Art.poster(this, it);
            card.setOnClickListener(v -> start(this, it));
            if (strip != null) strip.add(card);
            else epList.addView(card);
        }
        if (out.isEmpty()) ((View) (strip != null ? strip : epList).getParent()).setVisibility(View.GONE);
    }

    private int seasonOf(Item ep) {
        for (Integer s : seasons.keySet()) for (Item e : seasons.get(s)) if (e.id.equals(ep.id)) return s;
        return seasons.firstKey();
    }

    /** آخر حلقة شافها من هذا المسلسل (من كمّل المشاهدة). */
    private Item lastWatched() {
        for (Item r : Store.recents()) if (r.kind == Item.EPISODE && item.id.equals(r.group)) return r;
        return null;
    }

    private void updateButtons() {
        boolean fav = Store.isFav(item);
        Drawable d = getDrawable(fav ? R.drawable.ic_check : R.drawable.ic_plus).mutate();
        d.setTintList(favBtn.getTextColors());
        d.setBounds(0, 0, Ui.dp(20), Ui.dp(20));
        favBtn.setCompoundDrawablesRelative(d, null, null, null);
        favBtn.setContentDescription(fav ? "شيل من قائمتي" : "أضف لقائمتي");
        if (series) {
            Item last = lastWatched();
            boolean ready = !seasons.isEmpty();
            playBtn.setVisibility(ready ? View.VISIBLE : View.GONE);
            playBtn.setText(last != null ? "كمّل " + last.sub.replace("الموسم ", "م").replace("، الحلقة ", " ح") : "شغّل الحلقة 1");
            startBtn.setVisibility(ready && last != null ? View.VISIBLE : View.GONE);
        } else {
            long at = Store.resumeAt(item);
            playBtn.setText(at > 0 ? "كمّل من " + Ui.clock(at) : "شغّل");
            startBtn.setVisibility(at > 0 ? View.VISIBLE : View.GONE);
        }
        if (getCurrentFocus() == null || getCurrentFocus().getVisibility() != View.VISIBLE) firstButton().requestFocus();
    }

    private void play(boolean fromStart) {
        if (!series) {
            PlayerActivity.start(this, Collections.singletonList(item), 0, fromStart);
            return;
        }
        if (seasons.isEmpty()) return;
        Item last = fromStart ? null : lastWatched();
        int s = last != null ? seasonOf(last) : seasons.firstKey();
        List<Item> list = seasons.get(s);
        int at = 0;
        for (int i = 0; last != null && i < list.size(); i++) if (list.get(i).id.equals(last.id)) at = i;
        PlayerActivity.start(this, list, at, fromStart);
    }
}
