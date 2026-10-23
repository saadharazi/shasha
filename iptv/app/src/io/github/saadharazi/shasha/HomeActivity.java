package io.github.saadharazi.shasha;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * الواجهة. التلفزيون: قائمة جانبية على اليمين، والرئيسية والأفلام والمسلسلات صفوف مثل نتفلكس (Rows)،
 * والمباشر و«عرض الكل» والبحث شبكة بالتصنيفات. الجوال: أقسام فوق والشبكة.
 */
public class HomeActivity extends Activity implements Rows.Host {
    private static final String[] TABS = {"مباشر", "أفلام", "مسلسلات", "المفضلة"};
    private static final String[] LOADING = {"نجهّز القنوات", "نجهّز الأفلام", "نجهّز المسلسلات", ""};
    private static final int FAV = 3, SIDE_W = 232, SPACE = 14;
    static final String CONTACT = "0583630471", CONTACT_WA = "https://wa.me/966583630471";
    private static boolean splashed;

    private boolean wide, tv, firstFocus = true;
    private int section = -1, contentW, restoreSection;
    private String group = Library.ALL, query = "", sourceId, restoreGroup;
    private List<Library.Group> groupList = new ArrayList<>();
    private final TextView[] tabs = new TextView[4];
    private LinearLayout tabRow, state;
    private View head, searchBox, actions;
    private ListView side;
    private GridView grid;
    private Cards cards;
    private final GroupAdapter groupAdapter = new GroupAdapter();
    private TextView title, count, clock;
    private EditText search;
    private final Handler h = new Handler();
    private Runnable pendingGroup, pendingSearch, backTask;
    private final ImageView[] back = new ImageView[2];
    private int backOn;
    private String backUrl, headName, headCount;

    // التلفزيون
    private Rail rail;
    private Rows rowsView;
    private View gridView, scrimGrid, scrimRows;
    private int page = Rail.HOME, gridFrom = -1;
    private final Set<Integer> failed = new HashSet<>();
    private String lastError;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Net.init(this);
        Store.init(this);
        tv = Ui.isTv(this);
        wide = Ui.widthDp(this) >= 640;
        if (b != null) {
            restoreSection = b.getInt("section");
            restoreGroup = b.getString("group");
        }
        setContentView(withBackdrop(build(), !splashed));
        splashed = true;
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("section", Math.max(section, 0));
        b.putString("group", group);
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.post(tick);
        sync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    /** يتأكد إن اللي على الشاشة للاشتراك الحالي. */
    private void sync() {
        Net.checkUpdate(() -> Ui.note(this, "فيه إصدار جديد " + Store.latest() + ". حمّله من saadharazi.github.io/shasha"));
        Source s = Store.active();
        if (s == null) {
            sourceId = null;
            Library.source = null;
            welcome();
            return;
        }
        if (Library.source == null || !Library.source.id.equals(s.id)) Library.use(s);
        if (!s.id.equals(sourceId)) {
            sourceId = s.id;
            firstFocus = true;
            ready();
            if (tv) showPage(page);
            else show(restoreSection);
            restoreSection = 0;
        } else if (tv && rowsView.getVisibility() == View.VISIBLE) fillPage(true);
        else if (section == FAV) favChanged();
        else cards.notifyDataSetChanged();
    }

    // ---- البناء

    private View build() {
        int pad = wide ? 40 : 16;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(pad), Ui.dp(wide ? 20 : 12), Ui.dp(pad), Ui.dp(wide ? 12 : 6));
        top.addView(wordmark());
        tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 4; i++) tabRow.addView(tabs[i] = tab(i));
        if (wide) {
            // كبسولة زجاجية للأقسام
            tabRow.setBackground(Ui.ring(0x0FFFFFFF, 0x1AFFFFFF, 1, 28));
            tabRow.setPadding(Ui.dp(5), Ui.dp(5), Ui.dp(1), Ui.dp(5));
        }
        if (wide) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMarginStart(Ui.dp(30));
            top.addView(tabRow, lp);
        }
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout act = new LinearLayout(this);
        act.setGravity(Gravity.CENTER_VERTICAL);
        act.addView(roundButton(R.drawable.ic_search, "ابحث", v -> openSearch()));
        act.addView(roundButton(R.drawable.ic_gear, "الإعدادات", v -> settings()));
        if (tv) {
            clock = Ui.text(this, Ui.medium, 18, Ui.INK2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMarginStart(Ui.dp(20));
            act.addView(clock, lp);
        }
        actions = act;
        top.addView(act);
        root.addView(top);
        if (tv) top.setVisibility(View.GONE);
        if (!wide) {
            tabRow.setPaddingRelative(Ui.dp(pad - 4), 0, Ui.dp(pad), Ui.dp(8));
            root.addView(tabRow);
        }

        LinearLayout body = new LinearLayout(this);
        int bodyPad = wide ? pad - 12 : 4;
        body.setPadding(Ui.dp(bodyPad), 0, Ui.dp(bodyPad), 0);
        if (tv) body.setPaddingRelative(Ui.dp(Rail.WIDTH + 6), Ui.dp(26), Ui.dp(bodyPad), 0);
        if (wide) {
            side = new ListView(this);
            side.setDivider(null);
            side.setSelector(Ui.selector(0, 14));
            side.setDrawSelectorOnTop(true);
            side.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            side.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(24));
            side.setClipToPadding(false);
            side.setVerticalScrollBarEnabled(false);
            side.setVerticalFadingEdgeEnabled(true);
            side.setFadingEdgeLength(Ui.dp(28));
            side.setAdapter(groupAdapter);
            side.setOnItemClickListener((p, v, i, id) -> {
                chooseGroup(i);
                if (!cards.isEmpty()) grid.requestFocus();
            });
            side.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                    if (!side.hasFocus()) return;
                    final int at = i;
                    h.removeCallbacks(pendingGroup);
                    pendingGroup = () -> chooseGroup(at);
                    h.postDelayed(pendingGroup, 260);
                }

                public void onNothingSelected(AdapterView<?> p) {
                }
            });
            // لما ندخل القائمة من الشبكة يختار أندرويد الصف اللي بجنب التركيز؛ نرجّعه للقسم الحالي
            side.setOnFocusChangeListener((v, f) -> {
                if (f && indexOf(group) >= 0) side.setSelection(indexOf(group));
            });
            body.addView(side, new LinearLayout.LayoutParams(Ui.dp(SIDE_W), Ui.MATCH));
        }

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.addView(head = header());
        main.addView(searchBox = searchBar());
        searchBox.setVisibility(View.GONE);
        FrameLayout stage = new FrameLayout(this);
        grid = new GridView(this);
        grid.setSelector(new ColorDrawable(0));
        grid.setClipToPadding(false);
        grid.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(32));
        grid.setHorizontalSpacing(Ui.dp(SPACE));
        grid.setVerticalSpacing(Ui.dp(SPACE));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(!tv);
        grid.setFastScrollEnabled(!tv);
        grid.setAdapter(cards = new Cards(this));
        grid.setOnItemClickListener((p, v, i, id) -> open(cards.getItem(i), i));
        grid.setOnItemLongClickListener((p, v, i, id) -> {
            options(cards.getItem(i), i);
            return true;
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (grid.hasFocus()) {
                    cards.focus(grid, v);
                    spotlight(i);
                }
            }

            public void onNothingSelected(AdapterView<?> p) {
                cards.focus(grid, null);
            }
        });
        grid.setOnFocusChangeListener((v, f) -> {
            cards.focus(grid, f ? grid.getSelectedView() : null);
            if (f) spotlight(grid.getSelectedItemPosition());
            else unspot();
        });
        // تحت من الأقسام يروح للمحتوى مباشرة (لو ما في محتوى، أندرويد يدوّر لحاله)
        grid.setId(View.generateViewId());
        for (TextView t : tabs) t.setNextFocusDownId(grid.getId());
        search.setId(View.generateViewId());
        stage.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        state = new LinearLayout(this);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER);
        state.setPadding(Ui.dp(24), 0, Ui.dp(24), Ui.dp(40));
        stage.addView(state, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        main.addView(stage, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        body.addView(main, new LinearLayout.LayoutParams(0, Ui.MATCH, 1));
        root.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));

        contentW = Ui.widthDp(this) - 2 * bodyPad - (wide ? SIDE_W : 0) - 24 - (tv ? Rail.WIDTH + 6 - bodyPad : 0);
        return root;
    }

    private View wordmark() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        ImageView m = new ImageView(this);
        m.setImageResource(R.drawable.mark);
        r.addView(m, new LinearLayout.LayoutParams(Ui.dp(wide ? 32 : 28), Ui.dp(wide ? 32 : 28)));
        TextView t = Ui.text(this, Ui.bold, wide ? 25 : 22, Ui.INK);
        t.setText(R.string.app_name);
        Ui.markRoom(t, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginStart(Ui.dp(10));
        r.addView(t, lp);
        return r;
    }

    private TextView tab(final int i) {
        TextView t = Ui.text(this, Ui.medium, wide ? 17 : 16, 0);
        t.setText(TABS[i]);
        t.setTextColor(Ui.inks(Ui.INK2, Ui.BG, Ui.INK));
        t.setBackground(Ui.states(new ColorDrawable(0), Ui.round(Ui.INK, 22), Ui.round(0x26FFFFFF, 22)));
        t.setPadding(Ui.dp(wide ? 18 : 13), Ui.dp(10), Ui.dp(wide ? 18 : 13), Ui.dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setId(View.generateViewId());
        t.setOnClickListener(v -> {
            if (section != i) show(i);
            else if (!cards.isEmpty()) grid.requestFocus();
        });
        Ui.lift(t, 1.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginEnd(Ui.dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private View roundButton(int icon, String label, View.OnClickListener l) {
        ImageView b = new ImageView(this);
        Drawable d = getDrawable(icon).mutate();
        d.setTintList(Ui.inks(Ui.INK2, Ui.BG, Ui.INK));
        b.setImageDrawable(d);
        b.setPadding(Ui.dp(11), Ui.dp(11), Ui.dp(11), Ui.dp(11));
        b.setBackground(Ui.states(Ui.round(Ui.SURFACE, 22), Ui.round(Ui.INK, 22), null));
        b.setContentDescription(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(l);
        Ui.lift(b, 1.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
        lp.setMarginStart(Ui.dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private View header() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(50));
        r.setPadding(Ui.dp(14), Ui.dp(2), Ui.dp(14), 0);
        title = Ui.text(this, Ui.bold, wide ? 23 : 20, Ui.INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxWidth(Ui.dp(contentWidthGuess() * 0.7f));
        Ui.markRoom(title, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        r.addView(title, tp);
        count = Ui.text(this, Ui.regular, 15, Ui.INK3);
        Ui.markRoom(count, true);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        cp.setMarginStart(Ui.dp(12));
        r.addView(count, cp);
        r.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (!wide) {
            TextView pick = Ui.button(this, "الأقسام", R.drawable.ic_layers, false);
            pick.setTextSize(15);
            pick.setPadding(Ui.dp(16), Ui.dp(9), Ui.dp(16), Ui.dp(9));
            pick.setOnClickListener(v -> pickGroup());
            r.addView(pick);
        }
        return r;
    }

    private int contentWidthGuess() {
        return Ui.widthDp(this) - (wide ? SIDE_W + 80 : 32);
    }

    private View searchBar() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(12), Ui.dp(2), Ui.dp(12), Ui.dp(4));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTypeface(Ui.medium);
        search.setTextSize(17);
        search.setTextColor(Ui.INK);
        search.setHintTextColor(Ui.INK3);
        search.setBackground(Ui.states(Ui.ring(Ui.SURFACE, Ui.LINE, 1, 24), Ui.ring(Ui.SURFACE, Ui.INK, 2, 24), null));
        search.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
        search.setCompoundDrawablesRelative(Ui.icon(this, R.drawable.ic_search, 20, Ui.INK2), null, null, null);
        search.setCompoundDrawablePadding(Ui.dp(10));
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        search.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(final Editable s) {
                h.removeCallbacks(pendingSearch);
                pendingSearch = () -> {
                    String q = s.toString().trim();
                    if (q.equals(query)) return;
                    query = q;
                    refill(true);
                };
                h.postDelayed(pendingSearch, 300);
            }
        });
        search.setOnEditorActionListener((v, a, e) -> {
            keyboard(false);
            if (!cards.isEmpty()) grid.requestFocus();
            return true;
        });
        r.addView(search, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        TextView close = Ui.button(this, "إلغاء", 0, false);
        close.setPadding(Ui.dp(18), Ui.dp(11), Ui.dp(18), Ui.dp(11));
        close.setOnClickListener(v -> closeSearch());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginStart(Ui.dp(10));
        r.addView(close, lp);
        return r;
    }

    // ---- الحالات

    /** أول تشغيل: ما فيه اشتراك. */
    private void welcome() {
        section = -1;
        tabRow.setVisibility(View.GONE);
        actions.setVisibility(View.GONE);
        if (side != null) side.setVisibility(View.GONE);
        head.setVisibility(View.GONE);
        searchBox.setVisibility(View.GONE);
        cards.set(new ArrayList<>(), Cards.ROW, 0);
        TextView x = Ui.button(this, "أضف حساب Xtream", R.drawable.ic_server, true);
        x.setOnClickListener(v -> addSource(true));
        TextView m = Ui.button(this, "أضف رابط M3U", R.drawable.ic_link, false);
        m.setOnClickListener(v -> addSource(false));
        message(true, "أهلًا في شاشة",
                "شاشة يشغّل اشتراك IPTV حقك، وما فيه قنوات جاهزة. أضف بيانات اشتراكك وتطلع لك القنوات والأفلام والمسلسلات.",
                false, x, m);
        TextView foot = Ui.text(this, Ui.regular, 14, Ui.INK3);
        foot.setText("© 2026 شاشة     للتواصل " + CONTACT);
        foot.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        Ui.markRoom(foot, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        fp.topMargin = Ui.dp(40);
        state.addView(foot, fp);
        x.requestFocus();
        if (tv) {
            grid(true);
            rail.setVisibility(View.GONE);
        }
    }

    private void ready() {
        if (tv) rail.setVisibility(View.VISIBLE);
        tabRow.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);
        if (side != null) side.setVisibility(View.VISIBLE);
        head.setVisibility(View.VISIBLE);
    }

    private void loading(int s) {
        title.setText(TABS[s]);
        count.setText(null);
        groupList = new ArrayList<>();
        groupAdapter.notifyDataSetChanged();
        cards.set(new ArrayList<>(), Cards.ROW, 0);
        boolean first = Library.updatedAt(this) == 0;
        message(false, null, LOADING[s] + "…" + (first ? "\nأول مرة ياخذ وقت، وبعدها يفتح على طول." : ""), true);
    }

    private void failed(String why) {
        TextView retry = Ui.button(this, "حاول مرة ثانية", R.drawable.ic_rotate, true);
        retry.setOnClickListener(v -> show(section));
        TextView set = Ui.button(this, "الإعدادات", R.drawable.ic_gear, false);
        set.setOnClickListener(v -> settings());
        message(false, "ما قدرنا نجيب القائمة", why, false, retry, set);
        retry.requestFocus();
    }

    private void empty() {
        String head, body;
        if (!query.isEmpty()) {
            head = "ما لقينا شي باسم «" + Ui.iso(query) + "»";
            body = "جرّب كلمة أقصر، أو اكتب الاسم بطريقة ثانية.";
        } else if (section == FAV) {
            head = "المفضلة فاضية";
            body = (tv ? "اضغط مطوّلًا على OK" : "اضغط مطوّلًا") + " على أي قناة أو فيلم أو مسلسل، واختر «أضف للمفضلة».";
        } else {
            head = "ما فيه " + TABS[section] + " في اشتراكك";
            if (section == Item.LIVE) head = "ما فيه قنوات مباشرة في اشتراكك";
            body = Library.source.xtream() ? "السيرفر رجّع القسم فاضي. جرّب «حدّث القوائم» من الإعدادات."
                    : "بعض قوائم M3U فيها قنوات بس. لو عندك حساب Xtream لنفس الاشتراك، أضفه من الإعدادات.";
        }
        message(false, head, body, false);
    }

    private void message(boolean logo, String head, String body, boolean spinner, TextView... buttons) {
        state.removeAllViews();
        state.setVisibility(View.VISIBLE);
        grid.setVisibility(View.INVISIBLE);
        if (logo) {
            ImageView m = new ImageView(this);
            m.setImageResource(R.drawable.mark);
            state.addView(m, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
        }
        if (spinner) {
            ProgressBar p = new ProgressBar(this);
            p.setIndeterminateTintList(ColorStateList.valueOf(Ui.INK2));
            state.addView(p, new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(38)));
        }
        if (head != null) {
            TextView t = Ui.text(this, Ui.bold, 24, Ui.INK);
            t.setText(head);
            t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            Ui.markRoom(t, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.topMargin = Ui.dp(logo || spinner ? 14 : 0);
            state.addView(t, lp);
        }
        if (body != null) {
            TextView t = Ui.text(this, Ui.regular, 17, Ui.INK2);
            t.setText(body);
            t.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            t.setMaxWidth(Ui.dp(500));
            t.setLineSpacing(Ui.dp(5), 1f);
            Ui.markRoom(t, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.topMargin = Ui.dp(head != null ? 6 : 16);
            state.addView(t, lp);
        }
        if (buttons.length > 0) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            if (!wide) row.setOrientation(LinearLayout.VERTICAL);
            for (TextView b : buttons) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wide ? Ui.WRAP : Ui.MATCH, Ui.WRAP);
                lp.setMargins(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
                row.addView(b, lp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wide ? Ui.WRAP : Ui.MATCH, Ui.WRAP);
            lp.topMargin = Ui.dp(22);
            state.addView(row, lp);
        }
    }

    // ---- الأقسام والتصنيفات

    private void show(int s) {
        section = s;
        backdrop(null, 0);
        for (int i = 0; i < 4; i++) tabs[i].setActivated(i == s);
        upTo(tabs[s]);
        if (searchBox.getVisibility() == View.VISIBLE) {
            searchBox.setVisibility(View.GONE);
            head.setVisibility(View.VISIBLE);
            search.setText("");
            h.removeCallbacks(pendingSearch);
        }
        query = "";
        group = Library.ALL;
        if (s == FAV) {
            groupList = favGroups();
            applyGroups(true);
            return;
        }
        if (Library.items[s] == null) loading(s);
        final int want = s;
        Library.load(this, s, err -> {
            if (section != want || isFinishing()) return;
            if (err != null) {
                failed(err);
                return;
            }
            groupList = Library.groups[want];
            applyGroups(true);
            // نجهّز الأقسام الثانية بالخلفية عشان تفتح على طول لما تنتقل لها
            for (int k = 0; k < 3; k++) if (k != want) Library.load(this, k, e -> {});
        });
    }

    private void applyGroups(boolean reset) {
        if (restoreGroup != null && indexOf(restoreGroup) >= 0) group = restoreGroup;
        restoreGroup = null;
        if (indexOf(group) < 0) group = groupList.isEmpty() ? Library.ALL : groupList.get(0).id;
        if (side != null) {
            groupAdapter.notifyDataSetChanged();
            int at = indexOf(group);
            if (at >= 0) {
                side.setItemChecked(at, true);
                if (!side.hasFocus()) side.setSelection(at);
            }
        }
        refill(reset);
        if (firstFocus && tv && !cards.isEmpty()) {
            firstFocus = false;
            grid.requestFocus();
        }
    }

    // ---- الخلفية الحية: صورة العنصر المركّز مغبّشة ورا الواجهة، وتتبدّل بنعومة

    private View withBackdrop(View content, boolean intro) {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Ui.BG);
        for (int i = 0; i < 2; i++) {
            back[i] = new ImageView(this);
            back[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            back[i].setAlpha(0f);
            f.addView(back[i], new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
        View scrim = new View(this);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C0F1012, 0xCC0F1012, 0xF20F1012}));
        f.addView(scrimGrid = scrim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (tv) {
            // الصفوف: الصورة واضحة على اليسار، وتغمق من اليمين (ورا الكلام) ومن تحت (ورا الصفوف)
            FrameLayout sr = new FrameLayout(this);
            View side = new View(this);
            side.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.RIGHT_LEFT,
                    new int[]{0xF70F1012, 0xD60F1012, 0x330F1012, 0x730F1012}));
            sr.addView(side, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            View low = new View(this);
            low.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xFF0F1012, 0xE60F1012, 0x000F1012, 0x000F1012}));
            sr.addView(low, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            f.addView(scrimRows = sr, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        }
        f.addView(gridView = content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        if (tv) {
            rowsView = new Rows(this, this);
            f.addView(rowsView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            rail = new Rail(this, this::pick);
            f.addView(rail, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            clock = Ui.text(this, Ui.medium, 16, Ui.INK2);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.LEFT);
            cp.setMargins(Ui.dp(32), Ui.dp(22), 0, 0);
            f.addView(clock, cp);
            grid(false);
        }
        if (intro) splash(f);
        return f;
    }

    private void backdrop(String url, float strength) {
        backdrop(url, strength, false);
    }

    private void backdrop(String url, float strength, boolean sharp) {
        if (url == null ? backUrl == null : url.equals(backUrl)) return;
        backUrl = url;
        if (url == null) {
            for (ImageView v : back) v.animate().alpha(0f).setDuration(400).start();
            return;
        }
        java.util.function.Consumer<android.graphics.Bitmap> put = bm -> {
            if (!url.equals(backUrl)) return;
            ImageView next = back[1 - backOn], cur = back[backOn];
            next.setImageBitmap(bm);
            next.animate().alpha(strength).setDuration(450).start();
            cur.animate().alpha(0f).setDuration(450).start();
            backOn = 1 - backOn;
        };
        if (sharp) Net.bitmap(url, getResources().getDisplayMetrics().widthPixels / 2, put);
        else Net.blur(url, put);
    }

    /** العنصر المركّز: خلفيته، وللبوسترات اسمه وسنته وتقييمه بدل عنوان القسم. */
    private void spotlight(int i) {
        h.removeCallbacks(backTask);
        if (i < 0 || i >= cards.getCount()) return;
        final Item it = cards.getItem(i);
        // الخلفية للبوسترات بس، وبعد ما يهدأ التنقل (تبديلها يرسم الشاشة كاملة)
        backTask = () -> backdrop(cards.mode() == Cards.POSTER ? it.logo : null, 0.8f);
        h.postDelayed(backTask, 320);
        if (cards.mode() != Cards.POSTER || !query.isEmpty()) return;
        title.setText(it.name);
        StringBuilder m = new StringBuilder();
        if (it.year != null) m.append(it.year);
        if (it.rating != null) m.append(m.length() > 0 ? "     " : "").append("★ ").append(it.rating);
        if (it.kind == Item.SERIES && it.sub != null) m.append(m.length() > 0 ? "     " : "").append(it.sub);
        count.setText(m);
    }

    private void unspot() {
        if (headName != null) title.setText(headName);
        count.setText(headCount);
    }

    /** أول ما يفتح التطبيق: إطار الشاشة يرسم نفسه، وزر التشغيل يطلع، والاسم يدخل وعليه لمعة، وبعدين تطلع الواجهة. */
    private void splash(FrameLayout f) {
        final FrameLayout s = new FrameLayout(this);
        s.setBackgroundColor(0xFF07080D);
        s.setClickable(true);
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Mark m = new Mark(this);
        c.addView(m, new LinearLayout.LayoutParams(Ui.dp(wide ? 84 : 64), Ui.dp(wide ? 84 : 64)));
        FrameLayout wordBox = new FrameLayout(this);
        TextView w = Ui.text(this, Ui.bold, wide ? 64 : 48, Ui.INK);
        w.setText(R.string.app_name);
        Ui.markRoom(w, true);
        wordBox.addView(w, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        final View sweep = new View(this);
        sweep.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x000F1012, 0x9907080D, 0x000F1012}));
        sweep.setVisibility(View.INVISIBLE);
        wordBox.addView(sweep, new FrameLayout.LayoutParams(Ui.dp(70), Ui.MATCH));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        wp.setMarginStart(Ui.dp(18));
        c.addView(wordBox, wp);
        s.addView(c, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        f.addView(s, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        android.view.animation.DecelerateInterpolator ease = new android.view.animation.DecelerateInterpolator(2f);
        m.start();
        w.setAlpha(0f);
        w.setTranslationX(-Ui.dp(24));
        w.animate().alpha(1f).translationX(0).setStartDelay(700).setDuration(600).setInterpolator(ease).withEndAction(() -> {
            // لمعة غامقة تمر على الاسم (الحرف الأبيض يلمع حولها)
            sweep.setVisibility(View.VISIBLE);
            sweep.setTranslationX(w.getWidth());
            sweep.animate().translationX(-Ui.dp(70)).setDuration(650).setInterpolator(ease).start();
        }).start();
        s.animate().alpha(0f).setStartDelay(2050).setDuration(450).withEndAction(() -> f.removeView(s)).start();
        if (rowsView != null) {
            rowsView.setAlpha(0f);
            rowsView.setTranslationY(Ui.dp(26));
            rowsView.animate().alpha(1f).translationY(0).setStartDelay(2200).setDuration(650).setInterpolator(ease).start();
        }
    }

    /** علامة شاشة ترسم نفسها: الإطار، وبعده الرجل، وزر التشغيل يكبر بنطّة. */
    private static final class Mark extends View {
        private final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG),
                fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path frame = new android.graphics.Path(), part = new android.graphics.Path(), tri = new android.graphics.Path();
        private android.graphics.PathMeasure pm;
        private float t;

        Mark(android.content.Context c) {
            super(c);
            line.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            line.setColor(0xFFF3F3F0);
            fill.setColor(0xFFC9D3FF);
        }

        void start() {
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(1500);
            a.setStartDelay(150);
            a.addUpdateListener(v -> {
                t = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float u = w / 64f;
            line.setStrokeWidth(4.5f * u);
            frame.reset();
            frame.addRoundRect(new android.graphics.RectF(6 * u, 11 * u, 58 * u, 47 * u), 11 * u, 11 * u, android.graphics.Path.Direction.CW);
            pm = new android.graphics.PathMeasure(frame, true);
            tri.reset();
            tri.moveTo(28 * u, 22.5f * u);
            tri.lineTo(28 * u, 36 * u);
            tri.lineTo(39.5f * u, 29.25f * u);
            tri.close();
        }

        private static float seg(float t, float a, float b) {
            return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            if (pm == null) return;
            float u = getWidth() / 64f, d = seg(t, 0f, 0.55f);
            float ease = 1f - (1f - d) * (1f - d) * (1f - d);
            part.reset();
            pm.getSegment(0, pm.getLength() * ease, part, true);
            c.drawPath(part, line);
            float st = seg(t, 0.4f, 0.65f);
            if (st > 0) c.drawLine(32 * u - 10 * u * st, 55 * u, 32 * u + 10 * u * st, 55 * u, line);
            float p = seg(t, 0.5f, 0.85f);
            if (p > 0) {
                float k = p < 1 ? (float) (1 + 2.2 * Math.pow(p - 1, 3) + 1.2 * Math.pow(p - 1, 2)) : 1f;   // تكبير مع نطّة خفيفة
                c.save();
                c.scale(k, k, 32 * u, 29.25f * u);
                c.drawPath(tri, fill);
                c.restore();
            }
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < groupList.size(); i++) if (groupList.get(i).id.equals(id)) return i;
        return -1;
    }

    private void chooseGroup(int i) {
        if (i < 0 || i >= groupList.size()) return;
        String id = groupList.get(i).id;
        if (side != null) side.setItemChecked(i, true);
        if (id.equals(group)) return;
        group = id;
        refill(true);
    }

    private void pickGroup() {
        if (groupList.isEmpty()) return;
        String[] names = new String[groupList.size()], counts = new String[groupList.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = groupList.get(i).name;
            counts[i] = String.valueOf(groupList.get(i).count);
        }
        Sheet.menu(this, "الأقسام", names, counts, indexOf(group), this::chooseGroup);
    }

    private List<Library.Group> favGroups() {
        List<Library.Group> out = new ArrayList<>();
        addGroup(out, "recent", "تابع المشاهدة", Store.recents().size());
        addGroup(out, "fav0", "القنوات", Store.favs(Item.LIVE).size());
        addGroup(out, "fav1", "الأفلام", Store.favs(Item.MOVIE).size());
        addGroup(out, "fav2", "المسلسلات", Store.favs(Item.SERIES).size());
        return out;
    }

    private static void addGroup(List<Library.Group> out, String id, String name, int n) {
        if (n == 0) return;
        Library.Group g = new Library.Group(id, name);
        g.count = n;
        out.add(g);
    }

    private void favChanged() {
        if (section != FAV) {
            cards.notifyDataSetChanged();
            return;
        }
        groupList = favGroups();
        applyGroups(false);
    }

    private void refill(boolean reset) {
        if (section < 0) return;
        List<Item> list;
        if (section == FAV) {
            List<Item> src = group.equals("recent") ? Store.recents()
                    : group.startsWith("fav") ? Store.favs(group.charAt(3) - '0') : new ArrayList<>();
            String q = Library.norm(query);
            list = new ArrayList<>();
            for (Item i : src) if (q.isEmpty() || Library.norm(i.name).contains(q)) list.add(i);
        } else list = Library.filter(section, group, query);
        int mode = section == Item.LIVE || group.equals("fav0") ? Cards.ROW : Cards.POSTER;
        int cols = mode == Cards.ROW ? (contentW >= 560 ? 2 : 1) : Math.max(2, (contentW + SPACE) / ((wide ? 92 : 100) + SPACE));
        int cellW = Ui.dp((contentW - SPACE * (cols - 1)) / (float) cols);
        int sel = grid.getSelectedItemPosition();
        grid.setNumColumns(cols);
        cards.set(list, mode, cellW);
        if (reset || sel >= list.size()) grid.setSelection(0);
        else if (sel >= 0) grid.setSelection(sel);
        // المباشر يرجع لآخر قناة اتفرجت عليها
        if (reset && section == Item.LIVE && query.isEmpty()) {
            String last = Store.lastLive();
            for (int k = 0; last != null && k < list.size(); k++)
                if (last.equals(list.get(k).id)) {
                    grid.setSelection(k);
                    break;
                }
        }
        String name = section == FAV && groupList.isEmpty() ? TABS[FAV] : groupName(group);
        headName = query.isEmpty() ? name : "نتائج البحث";
        headCount = countText(list.size(), mode);
        title.setText(headName);
        count.setText(headCount);
        if (list.isEmpty()) empty();
        else {
            state.setVisibility(View.GONE);
            grid.setVisibility(View.VISIBLE);
        }
    }

    private String groupName(String id) {
        int i = indexOf(id);
        if (i < 0) return TABS[section];
        return Library.ALL.equals(id) ? (section == Item.LIVE ? "كل القنوات" : section == Item.MOVIE ? "كل الأفلام" : "كل المسلسلات")
                : groupList.get(i).name;
    }

    private String countText(int n, int mode) {
        if (section == FAV && group.equals("recent")) return Ui.count(n, "فيديو واحد", "فيديوين", "فيديوهات", "فيديو");
        int kind = section == FAV ? (group.startsWith("fav") ? group.charAt(3) - '0' : Item.MOVIE) : section;
        if (kind == Item.LIVE) return Ui.count(n, "قناة وحدة", "قناتين", "قنوات", "قناة");
        if (kind == Item.MOVIE) return Ui.count(n, "فيلم واحد", "فيلمين", "أفلام", "فيلم");
        return Ui.count(n, "مسلسل واحد", "مسلسلين", "مسلسلات", "مسلسل");
    }

    // ---- البحث

    private void openSearch() {
        if (section < 0) return;
        head.setVisibility(View.GONE);
        searchBox.setVisibility(View.VISIBLE);
        search.setHint(section == FAV ? "ابحث في المفضلة" : section == Item.LIVE ? "ابحث في القنوات" : "ابحث في ال" + TABS[section]);
        search.requestFocus();
        keyboard(true);
        upTo(search);
    }

    /** فوق من أول صف (والقائمة الجانبية) يروح للقسم الحالي أو لخانة البحث، مو لأقرب شي بالمسافة. */
    private void upTo(View v) {
        grid.setNextFocusUpId(v.getId());
        if (side != null) side.setNextFocusUpId(v.getId());
    }

    private void closeSearch() {
        keyboard(false);
        h.removeCallbacks(pendingSearch);
        search.setText("");
        searchBox.setVisibility(View.GONE);
        head.setVisibility(View.VISIBLE);
        upTo(tabs[Math.max(section, 0)]);
        if (!query.isEmpty()) {
            query = "";
            refill(true);
        }
        tabs[Math.max(section, 0)].requestFocus();
    }

    private void keyboard(boolean show) {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im == null) return;
        if (show) search.post(() -> im.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT));
        else im.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (searchBox.getVisibility() == View.VISIBLE && tv && gridFrom >= 0) backToRows();
        else if (searchBox.getVisibility() == View.VISIBLE) closeSearch();
        else if (!tv || rail == null || rail.getVisibility() != View.VISIBLE || rail.hasFocus()) super.onBackPressed();
        else if (gridFrom >= 0) backToRows();
        else if (rowsView.getVisibility() == View.VISIBLE && rowsView.inRows()) rowsView.toBill();
        else rail.focusActive();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (tv && rail != null && rail.getVisibility() == View.VISIBLE && e.getAction() == KeyEvent.ACTION_DOWN) {
            int code = e.getKeyCode();
            if (rail.hasFocus()) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    leaveRail();
                    return true;
                }
            } else if (rowsView.getVisibility() == View.VISIBLE && rowsView.key(code)) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU && grid.hasFocus() && grid.getSelectedItemPosition() >= 0) {
            options(cards.getItem(grid.getSelectedItemPosition()), grid.getSelectedItemPosition());
            return true;
        }
        if (code == KeyEvent.KEYCODE_SEARCH) {
            openSearch();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    // ---- التلفزيون: الصفحات من القائمة الجانبية

    private void pick(int id) {
        if (id == Rail.SETTINGS) {
            settings();
            return;
        }
        if (id == Rail.SEARCH) {
            int from = gridFrom >= 0 ? gridFrom : page;
            gridFrom = from;
            grid(true);
            if (section != Item.MOVIE && section != Item.SERIES && section != Item.LIVE) show(Item.MOVIE);
            openSearch();
            return;
        }
        showPage(id);
    }

    private void showPage(int p) {
        page = p;
        gridFrom = -1;
        failed.clear();
        rail.active(p);
        if (p == Rail.LIVE) {
            grid(true);
            show(Item.LIVE);
            if (!rail.hasFocus() && !cards.isEmpty()) grid.requestFocus();
            return;
        }
        grid(false);
        fillPage(false);
    }

    /** الشبكة (المباشر، عرض الكل، البحث) أو الصفوف. */
    private void grid(boolean on) {
        gridView.setVisibility(on ? View.VISIBLE : View.GONE);
        rowsView.setVisibility(on ? View.GONE : View.VISIBLE);
        scrimGrid.setVisibility(on ? View.VISIBLE : View.GONE);
        scrimRows.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) backdrop(null, 0);
    }

    private void backToRows() {
        if (searchBox.getVisibility() == View.VISIBLE) closeSearch();
        page = gridFrom;
        gridFrom = -1;
        rail.active(page);
        if (page == Rail.LIVE) {
            showPage(Rail.LIVE);
            return;
        }
        grid(false);
        rowsView.restore();
    }

    private void leaveRail() {
        if (rowsView.getVisibility() == View.VISIBLE) rowsView.restore();
        else if (!cards.isEmpty()) grid.requestFocus();
        else if (side != null) side.requestFocus();
    }

    /** صفوف الصفحة. ننتظر القوائم اللي تحتاجها؛ اللي ما نزلت نكمل بدونها. */
    private void fillPage(final boolean keep) {
        final int p = page;
        int[] need = p == Rail.HOME ? new int[]{Item.MOVIE, Item.SERIES, Item.LIVE} : p == Rail.MINE ? new int[0] : new int[]{p};
        for (final int k : need)
            if (Library.items[k] == null && !failed.contains(k)) {
                if (!keep) {
                    boolean first = Library.updatedAt(this) == 0;
                    rowsView.loading(LOADING[k] + "…" + (first ? "\nأول مرة ياخذ وقت، وبعدها يفتح على طول." : ""), true);
                }
                Library.load(this, k, err -> {
                    if (err != null) {
                        failed.add(k);
                        lastError = err;
                    }
                    if (page == p && !isFinishing() && rowsView.getVisibility() == View.VISIBLE) fillPage(keep);
                });
                return;
            }
        List<Rows.Row> rows = pageRows(p);
        if (rows.isEmpty()) {
            rowsView.empty(!failed.isEmpty() ? "ما قدرنا نجيب القائمة.\n" + lastError
                    : p == Rail.MINE ? "قائمتك فاضية.\nاضغط مطوّلًا على OK فوق أي فيلم أو مسلسل أو قناة، واختر «أضف لقائمتي»."
                    : "ما فيه شي في هذا القسم من اشتراكك.");
            return;
        }
        rowsView.set(rows, featured(p), keep);
        if (!keep && !rail.hasFocus()) rowsView.restore();
    }

    private List<Rows.Row> pageRows(int p) {
        List<Rows.Row> out = new ArrayList<>();
        if (p == Rail.HOME || p == Rail.MINE) addRow(out, "كمّل المشاهدة", Art.WIDE, -1, Store.recents(), null);
        if (p == Rail.HOME) {
            addRow(out, "أفضل 10 في شاشة", Art.TOP, -1, Library.top(10, Item.MOVIE, Item.SERIES), null);
            addRow(out, "أفلام جديدة", Art.POSTER, Item.MOVIE, Library.head(Library.newest(Item.MOVIE), 24), Library.ALL);
            addRow(out, "مسلسلات جديدة", Art.POSTER, Item.SERIES, Library.head(Library.newest(Item.SERIES), 24), Library.ALL);
            List<Item> fav = Store.favs(Item.LIVE);
            List<Item> ch = !fav.isEmpty() ? fav : Library.items[Item.LIVE] == null ? new ArrayList<>() : Library.head(Library.items[Item.LIVE], 20);
            addRow(out, fav.isEmpty() ? "القنوات" : "قنواتك", Art.CHANNEL, Item.LIVE, ch, Library.ALL);
            List<Item> mine = Store.favs(Item.MOVIE);
            mine.addAll(Store.favs(Item.SERIES));
            addRow(out, "قائمتي", Art.POSTER, -1, mine, null);
        } else if (p == Rail.MINE) {
            addRow(out, "قنواتك", Art.CHANNEL, Item.LIVE, Store.favs(Item.LIVE), null);
            addRow(out, "أفلام", Art.POSTER, Item.MOVIE, Store.favs(Item.MOVIE), null);
            addRow(out, "مسلسلات", Art.POSTER, Item.SERIES, Store.favs(Item.SERIES), null);
        } else {
            addRow(out, p == Item.MOVIE ? "أفضل 10 أفلام" : "أفضل 10 مسلسلات", Art.TOP, p, Library.top(10, p), null);
            addRow(out, "أضيفت جديد", Art.POSTER, p, Library.head(Library.newest(p), 24), Library.ALL);
            for (Map.Entry<Library.Group, List<Item>> e : Library.byGroup(p, 24).entrySet())
                addRow(out, e.getKey().name, Art.POSTER, p, e.getValue(), e.getKey().id);
        }
        return out;
    }

    private static void addRow(List<Rows.Row> out, String title, int type, int kind, List<Item> items, String group) {
        if (!items.isEmpty()) out.add(new Rows.Row(title, type, kind, items, group));
    }

    /** الواجهة المميزة: الأعلى تقييم اللي لها صورة (خمسة). */
    private List<Item> featured(int p) {
        List<Item> out = new ArrayList<>();
        if (p == Rail.MINE) return out;
        for (Item i : p == Rail.HOME ? Library.top(10, Item.MOVIE, Item.SERIES) : Library.top(10, p))
            if (i.logo != null && out.size() < 5) out.add(i);
        return out;
    }

    // Rows.Host

    @Override
    public void open(Item it, List<Item> row, int index) {
        if (it.kind == Item.LIVE) PlayerActivity.start(this, row, index);
        else if (it.kind == Item.EPISODE || it.pos > 0 && it.dur > 0) PlayerActivity.start(this, Collections.singletonList(it), 0);
        else DetailsActivity.start(this, it);
    }

    @Override
    public void play(Item it) {
        if (it.kind == Item.MOVIE) PlayerActivity.start(this, Collections.singletonList(it), 0);
        else DetailsActivity.start(this, it);
    }

    @Override
    public void details(Item it) {
        DetailsActivity.start(this, it);
    }

    @Override
    public void more(Rows.Row r) {
        if (r.kind == Item.LIVE) {
            showPage(Rail.LIVE);
            return;
        }
        gridFrom = page;
        grid(true);
        restoreGroup = r.group;
        show(r.kind);
        if (!cards.isEmpty()) grid.requestFocus();
    }

    @Override
    public void options(final Item it) {
        final boolean recent = it.pos > 0 && it.dur > 0;
        final List<String> l = new ArrayList<>();
        if (it.kind != Item.EPISODE) l.add(Store.isFav(it) ? "شيل من قائمتي" : "أضف لقائمتي");
        if (recent) l.add("شيل من كمّل المشاهدة");
        if (l.isEmpty()) return;
        Sheet.menu(this, it.name, l.toArray(new String[0]), null, -1, i -> {
            if (l.get(i).contains("كمّل")) Store.forget(it);
            else Ui.note(this, Store.toggleFav(it) ? "صار في قائمتي" : "طلع من قائمتي");
            fillPage(true);
        });
    }

    @Override
    public void backdrop(Item it) {
        if (it.kind == Item.LIVE) backdrop(null, 0);
        else if (it.back != null) backdrop(it.back, 1f, true);
        else backdrop(it.logo, 0.7f);
    }

    @Override
    public void toRail() {
        rail.focusActive();
    }

    // ---- التشغيل والخيارات

    private void open(Item it, int pos) {
        boolean recent = section == FAV && group.equals("recent");
        if (it.kind == Item.LIVE) PlayerActivity.start(this, cards.items(), pos);
        else if (it.kind == Item.EPISODE || recent) PlayerActivity.start(this, Collections.singletonList(it), 0);
        else DetailsActivity.start(this, it);
    }

    private void options(final Item it, final int pos) {
        final boolean recent = section == FAV && group.equals("recent");
        final List<String> l = new ArrayList<>();
        l.add(it.kind == Item.LIVE || recent ? "شغّل" : "افتح");
        if (it.kind != Item.EPISODE) l.add(Store.isFav(it) ? "شيل من المفضلة" : "أضف للمفضلة");
        if (recent) l.add("شيل من تابع المشاهدة");
        Sheet.menu(this, it.name, l.toArray(new String[0]), null, -1, i -> {
            String c = l.get(i);
            if (c.equals("شيل من تابع المشاهدة")) {
                Store.forget(it);
                favChanged();
            } else if (c.contains("المفضلة")) {
                Ui.note(this, Store.toggleFav(it) ? "صار في المفضلة" : "طلع من المفضلة");
                favChanged();
            } else open(it, pos);
        });
    }

    private void addSource(boolean xtream) {
        startActivity(new Intent(this, SourceActivity.class).putExtra("xtream", xtream));
    }

    // ---- الإعدادات

    private void settings() {
        final Source s = Library.source;
        if (s == null) return;
        final List<String> l = new ArrayList<>(), v = new ArrayList<>();
        final List<Runnable> act = new ArrayList<>();
        l.add("الاشتراك");
        v.add(s.title());
        act.add(this::sources);
        if (s.xtream() && Library.account != null) {
            l.add("حالة الاشتراك");
            v.add(Library.account);
            act.add(() -> {});
        }
        if (s.xtream()) {
            l.add("صيغة البث المباشر");
            v.add(formatName(Store.liveFormat()));
            act.add(this::formats);
        }
        long at = Library.updatedAt(this);
        l.add("حدّث القوائم");
        v.add(at > 0 ? "آخر تحديث " + ago(at) : "");
        act.add(() -> {
            Library.refresh(this);
            show(section < 0 ? 0 : section);
            Ui.note(this, "نحدّث القوائم من السيرفر");
        });
        if (Ui.isNewer(Store.latest(), Net.version)) {
            l.add("فيه تحديث");
            v.add("الإصدار " + Store.latest() + " على saadharazi.github.io/shasha");
            act.add(() -> {});
        }
        l.add("عن شاشة");
        v.add("الإصدار " + Net.version);
        act.add(this::about);
        Sheet.menu(this, "الإعدادات", l.toArray(new String[0]), v.toArray(new String[0]), -1, i -> act.get(i).run());
    }

    private void sources() {
        final List<Source> all = Store.sources();
        String[] l = new String[all.size() + 1], v = new String[all.size() + 1];
        int checked = -1;
        for (int i = 0; i < all.size(); i++) {
            l[i] = all.get(i).title();
            v[i] = all.get(i).xtream() ? "Xtream" : "M3U";
            if (all.get(i).id.equals(sourceId)) checked = i;
        }
        l[all.size()] = "أضف اشتراك ثاني";
        Sheet.menu(this, "الاشتراكات", l, v, checked, i -> {
            if (i == all.size()) {
                Sheet.menu(this, "نوع الاشتراك", new String[]{"حساب Xtream", "رابط M3U"},
                        new String[]{"سيرفر واسم مستخدم وكلمة مرور", "رابط قائمة القنوات"}, -1, k -> addSource(k == 0));
                return;
            }
            final Source s = all.get(i);
            final boolean current = s.id.equals(sourceId);
            final String[] opts = current ? new String[]{"احذفه"} : new String[]{"استخدمه", "احذفه"};
            Sheet.menu(this, s.title(), opts, null, -1, k -> {
                if (opts[k].equals("استخدمه")) {
                    Store.use(s);
                    sync();
                } else Sheet.menu(this, "تحذف «" + Ui.iso(s.title()) + "»؟", new String[]{"احذفه", "خلّه"},
                        new String[]{"تنمسح مفضلته وتابع المشاهدة", ""}, -1, c -> {
                            if (c != 0) return;
                            Store.remove(s);
                            if (current) sourceId = null;
                            sync();
                            Ui.note(this, "انحذف الاشتراك");
                        });
            });
        });
    }

    private static String formatName(String f) {
        return f.equals("m3u8") ? "HLS" : f.equals("ts") ? "TS" : "تلقائي";
    }

    private void formats() {
        final String[] keys = {"auto", "m3u8", "ts"};
        int at = java.util.Arrays.asList(keys).indexOf(Store.liveFormat());
        Sheet.menu(this, "صيغة البث المباشر", new String[]{"تلقائي", "HLS (m3u8)", "TS"},
                new String[]{"حسب السيرفر، ولو ما اشتغلت نجرب الثانية", "لو TS يقطّع عندك", "بث متصل، الأثبت غالبا"}, at, i -> {
                    Store.setLiveFormat(keys[i]);
                    Ui.note(this, "الصيغة تنطبق من القناة الجاية");
                });
    }

    private void about() {
        Sheet.menu(this, "عن شاشة", new String[]{"الإصدار", "الحقوق", "للتواصل", "الموقع", "الرخصة", "المحتوى"},
                new String[]{Net.version, "© 2026 شاشة", CONTACT + " (واتساب)", "saadharazi.github.io/shasha", "MIT، مفتوح المصدر",
                        "التطبيق مشغّل بس، ما فيه قنوات"}, -1, i -> {
                    if (i != 2) return;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(CONTACT_WA)));
                    } catch (Exception e) {
                        Ui.note(this, "تواصل معنا واتساب على " + CONTACT);
                    }
                });
    }

    private static String ago(long t) {
        long m = (System.currentTimeMillis() - t) / 60000;
        if (m < 1) return "الحين";
        if (m < 60) return "قبل " + Ui.count((int) m, "دقيقة", "دقيقتين", "دقايق", "دقيقة");
        long hrs = m / 60;
        if (hrs < 24) return "قبل " + Ui.count((int) hrs, "ساعة", "ساعتين", "ساعات", "ساعة");
        return "قبل " + Ui.count((int) (hrs / 24), "يوم", "يومين", "أيام", "يوم");
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (clock == null) return;
            Calendar c = Calendar.getInstance();
            boolean h24 = DateFormat.is24HourFormat(HomeActivity.this);
            int hr = c.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
            if (!h24 && hr == 0) hr = 12;
            clock.setText(String.format(java.util.Locale.US, h24 ? "%02d:%02d" : "%d:%02d", hr, c.get(Calendar.MINUTE))
                    + (h24 ? "" : c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م"));
            h.postDelayed(this, 15000);
        }
    };

    /** قائمة التصنيفات على اليمين. */
    private final class GroupAdapter extends BaseAdapter {
        public int getCount() {
            return groupList.size();
        }

        public Object getItem(int i) {
            return groupList.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) {
                LinearLayout r = new LinearLayout(HomeActivity.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setMinimumHeight(Ui.dp(48));
                r.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
                android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
                bg.addState(new int[]{android.R.attr.state_activated}, Ui.round(Ui.RAISED, 14));
                bg.addState(new int[]{}, new ColorDrawable(0));
                r.setBackground(bg);
                ColorStateList ink = new ColorStateList(new int[][]{{android.R.attr.state_selected}, {android.R.attr.state_activated}, {}},
                        new int[]{Ui.INK, Ui.INK, Ui.INK2});
                TextView n = Ui.text(HomeActivity.this, Ui.medium, 16, 0);
                n.setTextColor(ink);
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                Ui.markRoom(n, true);
                r.addView(n, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                TextView c = Ui.text(HomeActivity.this, Ui.regular, 13, Ui.INK3);
                c.setPaddingRelative(Ui.dp(10), 0, 0, 0);
                r.addView(c);
                v = r;
            }
            LinearLayout r = (LinearLayout) v;
            Library.Group g = groupList.get(i);
            ((TextView) r.getChildAt(0)).setText(Library.ALL.equals(g.id) ? "الكل" : g.name);
            ((TextView) r.getChildAt(1)).setText(String.format(java.util.Locale.US, "%,d", g.count));
            return r;
        }
    }
}
