package io.github.saadharazi.shasha;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/** صفحة الفيلم أو المسلسل: البوستر والقصة وزر التشغيل، وللمسلسل المواسم والحلقات. */
public class DetailsActivity extends Activity {
    private Item item;
    private boolean wide, series;
    private ImageView backdrop;
    private TextView plot, playBtn, startBtn, favBtn;
    private LinearLayout chips, buttons, seasonRow;
    private ProgressBar spin;
    private ListView episodes;
    private final TreeMap<Integer, List<Item>> seasons = new TreeMap<>();
    private final TreeMap<Integer, List<String[]>> episodeText = new TreeMap<>();   // عنوان الحلقة ومدتها
    private int season = -1;
    private final EpisodeAdapter adapter = new EpisodeAdapter();

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
        adapter.notifyDataSetChanged();
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Ui.BG);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        blurBehind(item.logo);
        // الكلام على اليمين: نغمّق من اليمين ومن تحت
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xF20F1012, 0xB30F1012, 0x590F1012}));
        root.addView(scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xFF0F1012, 0x000F1012}));
        root.addView(bottom, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(200), Gravity.BOTTOM));

        int pad = wide ? 48 : 18;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(wide && series ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(pad), Ui.dp(wide ? 40 : 22), Ui.dp(pad), Ui.dp(wide ? 28 : 14));
        root.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        boolean bigPoster = wide && !series;
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        if (bigPoster) info.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(bigPoster ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM);
        FrameLayout poster = new FrameLayout(this);
        poster.setBackground(Ui.round(Ui.SURFACE, 16));
        poster.setClipToOutline(true);
        poster.setElevation(Ui.dp(10));
        TextView letter = Ui.text(this, Ui.bold, 40, Ui.INK3);
        letter.setText(item.initial());
        letter.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        letter.setGravity(Gravity.CENTER);
        poster.addView(letter, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(art, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        int pw = bigPoster ? 220 : wide ? 124 : 108;
        top.addView(poster, new LinearLayout.LayoutParams(Ui.dp(pw), Ui.dp(pw * 1.5f)));
        Net.image(art, item.logo, Ui.dp(pw * 2), letter);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPaddingRelative(Ui.dp(bigPoster ? 36 : 18), 0, 0, 0);
        TextView title = Ui.text(this, Ui.bold, bigPoster ? 36 : wide ? 28 : 24, Ui.INK);
        title.setText(item.name);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(Ui.dp(2), 1f);
        Ui.markRoom(title, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        head.addView(title, tl);
        chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        cl.topMargin = Ui.dp(12);
        head.addView(chips, cl);
        top.addView(head, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        info.addView(top);

        plot = Ui.text(this, Ui.regular, wide ? 17 : 15, Ui.INK2);
        plot.setLineSpacing(Ui.dp(5), 1f);
        plot.setMaxLines(bigPoster ? 6 : wide ? 5 : 3);
        plot.setEllipsize(TextUtils.TruncateAt.END);
        plot.setMaxWidth(Ui.dp(620));
        Ui.markRoom(plot, false);
        spin = new ProgressBar(this);
        spin.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK3));
        buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout target = bigPoster ? head : info;
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        pl.topMargin = Ui.dp(bigPoster ? 18 : 16);
        target.addView(plot, pl);
        target.addView(spin, new LinearLayout.LayoutParams(Ui.dp(26), Ui.dp(26)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        bl.topMargin = Ui.dp(22);
        target.addView(buttons, bl);
        playBtn = Ui.button(this, "شغّل", R.drawable.ic_play_fill, true);
        startBtn = Ui.button(this, "من البداية", R.drawable.ic_rotate, false);
        favBtn = Ui.button(this, "", R.drawable.ic_star, false);
        playBtn.setOnClickListener(v -> play(false));
        startBtn.setOnClickListener(v -> play(true));
        favBtn.setOnClickListener(v -> {
            Ui.note(this, Store.toggleFav(item) ? "صار في المفضلة" : "طلع من المفضلة");
            updateButtons();
        });
        for (TextView bt : new TextView[]{playBtn, startBtn, favBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMarginEnd(Ui.dp(10));
            buttons.addView(bt, lp);
        }

        if (series) {
            LinearLayout eps = new LinearLayout(this);
            eps.setOrientation(LinearLayout.VERTICAL);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            hs.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
            seasonRow = new LinearLayout(this);
            hs.addView(seasonRow);
            eps.addView(hs);
            episodes = new ListView(this);
            episodes.setDivider(null);
            episodes.setSelector(Ui.selector(Ui.RAISED, 16));
            episodes.setClipToPadding(false);
            episodes.setPadding(0, Ui.dp(8), 0, Ui.dp(20));
            episodes.setVerticalFadingEdgeEnabled(true);
            episodes.setFadingEdgeLength(Ui.dp(28));
            episodes.setAdapter(adapter);
            episodes.setOnItemClickListener((p, v, i, id) -> PlayerActivity.start(this, seasons.get(season), i, false));
            eps.addView(episodes, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
            if (wide) {
                content.addView(info, new LinearLayout.LayoutParams(Ui.dp(400), Ui.MATCH));
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, Ui.MATCH, 1);
                ep.setMarginStart(Ui.dp(36));
                content.addView(eps, ep);
            } else {
                content.addView(info);
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(Ui.MATCH, 0, 1);
                ep.topMargin = Ui.dp(14);
                content.addView(eps, ep);
            }
        } else content.addView(info, new LinearLayout.LayoutParams(Ui.MATCH, bigPoster ? Ui.MATCH : Ui.WRAP));

        if (item.year != null) chip(item.year, 0);
        if (item.rating != null) chip(item.rating, R.drawable.ic_star_fill);
        updateButtons();
        (series ? favBtn : playBtn).requestFocus();
        return root;
    }

    /** البوستر مغبّش ورا الصفحة (لين تجي الخلفية الحقيقية لو فيه). */
    private void blurBehind(String url) {
        Net.blur(url, bm -> {
            if (backdrop.getTag() != null) return;   // الخلفية الحقيقية وصلت قبل
            backdrop.setImageAlpha(215);
            backdrop.setImageBitmap(bm);
            backdrop.setAlpha(0f);
            backdrop.animate().alpha(1f).setDuration(400).start();
        });
    }

    private void chip(String text, int icon) {
        TextView c = Ui.text(this, Ui.medium, 14, Ui.INK2);
        c.setText(text);
        c.setBackground(Ui.round(0x1AF3F3F0, 14));
        c.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        if (icon != 0) {
            c.setCompoundDrawablesRelative(Ui.icon(this, icon, 13, Ui.WARN), null, null, null);
            c.setCompoundDrawablePadding(Ui.dp(5));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginEnd(Ui.dp(8));
        chips.addView(c, lp);
    }

    /** ردّ السيرفر: القصة والمدة والصورة الخلفية، والحلقات للمسلسل. */
    private void filled(JSONObject o) {
        spin.setVisibility(View.GONE);
        JSONObject in = o == null ? null : o.optJSONObject("info");
        if (in != null) {
            String p = in.optString("plot", in.optString("description", "")).trim();
            plot.setText(p.isEmpty() ? null : p);
            String genre = in.optString("genre", "").trim();
            int mins = in.optInt("duration_secs", 0) / 60;
            if (mins == 0) mins = minutes(in.optString("duration", in.optString("episode_run_time", "")));
            if (mins > 0) chip(mins >= 60 ? (mins / 60) + "س " + (mins % 60) + "د" : minutesText(mins), 0);
            if (!genre.isEmpty()) chip(firstTwo(genre), 0);
            String back = first(in.opt("backdrop_path"));
            if (back != null) {
                backdrop.setImageAlpha(125);
                Net.image(backdrop, back, getResources().getDisplayMetrics().widthPixels, null);
            } else {
                String cover = in.optString("movie_image", in.optString("cover", null));
                if (cover != null && !cover.equals(item.logo)) blurBehind(cover);
            }
        }
        if (plot.getText().length() == 0) plot.setVisibility(View.GONE);
        if (series) episodes(o);
    }

    private void episodes(JSONObject o) {
        Object eps = o == null ? null : o.opt("episodes");
        List<JSONObject> all = new ArrayList<>();
        if (eps instanceof JSONObject) {
            for (Iterator<String> k = ((JSONObject) eps).keys(); k.hasNext(); ) collect(((JSONObject) eps).optJSONArray(k.next()), all);
        } else if (eps instanceof JSONArray) {
            JSONArray a = (JSONArray) eps;
            for (int i = 0; i < a.length(); i++) {
                if (a.optJSONArray(i) != null) collect(a.optJSONArray(i), all);
                else if (a.optJSONObject(i) != null) all.add(a.optJSONObject(i));
            }
        }
        for (JSONObject e : all) {
            int s = e.optInt("season", 1), n = e.optInt("episode_num", 0);
            JSONObject in = e.optJSONObject("info");
            Item ep = new Item();
            ep.kind = Item.EPISODE;
            ep.id = e.optString("id");
            ep.ext = e.optString("container_extension", "mp4");
            ep.name = item.name;
            ep.group = item.id;
            ep.sub = "الموسم " + s + "، الحلقة " + n;
            String img = in == null ? null : in.optString("movie_image", null);
            ep.logo = img != null && img.startsWith("http") ? img : item.logo;
            if (!seasons.containsKey(s)) {
                seasons.put(s, new ArrayList<>());
                episodeText.put(s, new ArrayList<>());
            }
            seasons.get(s).add(ep);
            String t = e.optString("title", "").trim();
            int mins = in == null ? 0 : in.optInt("duration_secs", 0) / 60;
            if (mins == 0 && in != null) mins = minutes(in.optString("duration", ""));
            episodeText.get(s).add(new String[]{t.isEmpty() ? "الحلقة " + n : t, "الحلقة " + n, minutesText(mins)});
        }
        if (seasons.isEmpty()) {
            plot.setVisibility(View.VISIBLE);
            plot.setText(o == null ? "ما قدرنا نجيب الحلقات من السيرفر. ارجع وجرّب مرة ثانية." : "المسلسل ما فيه حلقات للحين.");
            return;
        }
        for (final int s : seasons.keySet()) {
            TextView b = Ui.text(this, Ui.medium, 16, 0);
            b.setText("الموسم " + s);
            b.setTextColor(Ui.inks(Ui.INK2, Ui.BG, Ui.INK));
            b.setBackground(Ui.states(Ui.round(Ui.SURFACE, 22), Ui.round(Ui.INK, 22), Ui.round(Ui.RAISED, 22)));
            b.setPadding(Ui.dp(18), Ui.dp(10), Ui.dp(18), Ui.dp(10));
            b.setFocusable(true);
            b.setClickable(true);
            b.setTag(s);
            b.setOnClickListener(v -> showSeason(s));
            Ui.lift(b, 1.05f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMarginEnd(Ui.dp(8));
            seasonRow.addView(b, lp);
        }
        Item last = lastWatched();
        showSeason(last != null ? seasonOf(last) : seasons.firstKey());
        updateButtons();
    }

    private static void collect(JSONArray a, List<JSONObject> out) {
        if (a != null) for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.add(a.optJSONObject(i));
    }

    private void showSeason(int s) {
        season = s;
        for (int i = 0; i < seasonRow.getChildCount(); i++) seasonRow.getChildAt(i).setActivated(seasonRow.getChildAt(i).getTag().equals(s));
        adapter.notifyDataSetChanged();
        episodes.setSelection(0);
    }

    private int seasonOf(Item ep) {
        for (Integer s : seasons.keySet()) for (Item e : seasons.get(s)) if (e.id.equals(ep.id)) return s;
        return seasons.firstKey();
    }

    /** آخر حلقة شافها من هذا المسلسل (من تابع المشاهدة). */
    private Item lastWatched() {
        for (Item r : Store.recents()) if (r.kind == Item.EPISODE && item.id.equals(r.group)) return r;
        return null;
    }

    private void updateButtons() {
        boolean fav = Store.isFav(item);
        favBtn.setText(fav ? "شيل من المفضلة" : "أضف للمفضلة");
        android.graphics.drawable.Drawable d = getDrawable(fav ? R.drawable.ic_star_fill : R.drawable.ic_star).mutate();
        d.setTintList(favBtn.getTextColors());
        d.setBounds(0, 0, Ui.dp(19), Ui.dp(19));
        favBtn.setCompoundDrawablesRelative(d, null, null, null);
        if (series) {
            Item last = lastWatched();
            playBtn.setVisibility(last != null && !seasons.isEmpty() ? View.VISIBLE : View.GONE);
            if (last != null) playBtn.setText("كمّل " + last.sub.replace("الموسم ", "م").replace("، الحلقة ", " ح"));
            startBtn.setVisibility(View.GONE);
        } else {
            long at = Store.resumeAt(item);
            playBtn.setText(at > 0 ? "كمّل من " + Ui.clock(at) : "شغّل");
            startBtn.setVisibility(at > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void play(boolean fromStart) {
        if (!series) {
            PlayerActivity.start(this, Collections.singletonList(item), 0, fromStart);
            return;
        }
        Item last = lastWatched();
        if (last == null) return;
        int s = seasonOf(last);
        List<Item> list = seasons.get(s);
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(last.id)) PlayerActivity.start(this, list, i, false);
    }

    private static String minutesText(int m) {
        return Ui.count(m, "دقيقة", "دقيقتين", "دقايق", "دقيقة");
    }

    private static String first(Object o) {
        if (o instanceof JSONArray) return ((JSONArray) o).optString(0, null);
        if (o instanceof String && ((String) o).startsWith("http")) return (String) o;
        return null;
    }

    private static String firstTwo(String genre) {
        String[] g = genre.split("\\s*[,/،|]\\s*");
        return g.length > 1 ? g[0] + "، " + g[1] : g[0];
    }

    /** "01:45:00" أو "45 min" إلى دقائق. */
    private static int minutes(String d) {
        if (d == null || d.isEmpty()) return 0;
        try {
            if (d.contains(":")) {
                String[] p = d.split(":");
                return p.length == 3 ? Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim()) : Integer.parseInt(p[0].trim());
            }
            return Integer.parseInt(d.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private final class EpisodeAdapter extends BaseAdapter {
        public int getCount() {
            return season < 0 ? 0 : seasons.get(season).size();
        }

        public Object getItem(int i) {
            return seasons.get(season).get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = row();
            Item ep = seasons.get(season).get(i);
            String[] t = episodeText.get(season).get(i);
            LinearLayout r = (LinearLayout) v;
            FrameLayout thumb = (FrameLayout) r.getChildAt(0);
            Net.image((ImageView) thumb.getChildAt(0), ep.logo, Ui.dp(240), null);
            long pos = Store.resumeAt(ep);
            View bar = thumb.getChildAt(1);
            bar.setVisibility(pos > 0 ? View.VISIBLE : View.GONE);
            LinearLayout col = (LinearLayout) r.getChildAt(1);
            ((TextView) col.getChildAt(0)).setText(t[0]);
            LinearLayout meta = (LinearLayout) col.getChildAt(1);
            ((TextView) meta.getChildAt(0)).setText(t[1]);
            ((TextView) meta.getChildAt(1)).setText(pos > 0 ? "وقفت عند " + Ui.clock(pos) : t[2]);
            return r;
        }

        private View row() {
            LinearLayout r = new LinearLayout(DetailsActivity.this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
            FrameLayout thumb = new FrameLayout(DetailsActivity.this);
            thumb.setBackground(Ui.round(Ui.SURFACE, 10));
            thumb.setClipToOutline(true);
            ImageView im = new ImageView(DetailsActivity.this);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.addView(im, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            View bar = new View(DetailsActivity.this);
            bar.setBackgroundColor(Ui.ACCENT);
            thumb.addView(bar, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(3), Gravity.BOTTOM));
            r.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(wide ? 128 : 112), Ui.dp(wide ? 72 : 63)));
            LinearLayout col = new LinearLayout(DetailsActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPaddingRelative(Ui.dp(16), 0, Ui.dp(6), 0);
            TextView name = Ui.text(DetailsActivity.this, Ui.medium, 16, Ui.INK);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            Ui.markRoom(name, false);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
            col.addView(name, np);
            LinearLayout meta = new LinearLayout(DetailsActivity.this);
            TextView n = Ui.text(DetailsActivity.this, Ui.regular, 13, Ui.INK2);
            TextView d = Ui.text(DetailsActivity.this, Ui.regular, 13, Ui.INK3);
            d.setPaddingRelative(Ui.dp(12), 0, 0, 0);
            meta.addView(n);
            meta.addView(d);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            mp.topMargin = Ui.dp(6);
            col.addView(meta, mp);
            r.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
            return r;
        }
    }
}
