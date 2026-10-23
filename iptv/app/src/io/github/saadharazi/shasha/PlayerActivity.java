package io.github.saadharazi.shasha;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * المشغّل (ExoPlayer). عشان ما يعلّق:
 *  - تحميل مسبق من 30 ثانية لدقيقتين (وحد أعلى 48 ميقا عشان ذاكرة التلفزيون)،
 *  - كل طلب شبكة يعيد المحاولة لحاله، ولو البث انقطع نعيد الاتصال من نفس المكان،
 *  - مراقب: لو التحميل طوّل أو الصورة وقفت، نعيد الاتصال بدون ما تضغط شي،
 *  - المباشر لو صيغته ما اشتغلت نجرب الثانية (TS ↔ HLS).
 * المباشر: فوق/تحت يقلب، OK قائمة القنوات، والأرقام تروح لرقم القناة.
 * الأفلام والحلقات: OK يوقف ويكمل، يمين/يسار يقدّم ويرجّع، والحلقة اللي بعدها تشتغل لحالها.
 * ضغطة مطوّلة على OK (أو زر القائمة): الصوت والترجمة ومقاس الصورة.
 */
public class PlayerActivity extends Activity {
    private static List<Item> queue = new ArrayList<>();
    private static int startAt;
    private static boolean startFromBeginning;

    private static final int RETRIES = 3, STALLS = 3, FIRST_LOAD_MS = 30000;

    private Item item;
    private int index, retries, stuckFor, frozenFor, aspect, stalls;
    private boolean live, tv, triedAlt, fromStart, longOk, wasPlaying, everReady;
    private long lastPos = -1, seekTarget = -1, playingSince, openedAt;
    private String url, digits = "";

    private ExoPlayer player;
    private Fit fit;
    private SurfaceView surface;
    private View overlay, controls, panel;
    private TextView title, sub, nowLine, nextLine, timeNow, timeEnd, badge, quality, clock, subtitle, note, number;
    private ImageView logo, playIcon;
    private LinearLayout errorBox;
    private TextView errorTitle, errorText;
    private ProgressBar spin, programBar;
    private SeekBar bar;
    private ListView channels;
    private final Handler h = new Handler();

    static void start(Activity a, List<Item> list, int i) {
        start(a, list, i, false);
    }

    static void start(Activity a, List<Item> list, int i, boolean fromStart) {
        queue = new ArrayList<>(list);
        startAt = i;
        startFromBeginning = fromStart;
        a.startActivity(new Intent(a, PlayerActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Net.init(this);
        Store.init(this);
        if (Library.source == null || queue.isEmpty()) {
            finish();
            return;
        }
        tv = Ui.isTv(this);
        aspect = Store.aspect();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(build());
        player = makePlayer(this);
        player.setVideoSurfaceView(surface);
        player.addListener(listener);
        index = b != null ? b.getInt("index", startAt) : startAt;
        fromStart = startFromBeginning;
        play(index);
    }

    /** المشغّل وإعداداته: التحميل المسبق وإعادة المحاولة وقراءة بث TS حق IPTV. */
    static ExoPlayer makePlayer(Context c) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Net.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(20000);
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                // بث IPTV كثير منه ما يبدأ بإطار كامل، وبعضه بدون علامات وحدات: نقبله بدل ما نعلّق
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                        | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
                .setConstantBitrateSeekingEnabled(true);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(http, extractors)
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(8));
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(30000, 120000, 2000, 4000)
                .setTargetBufferBytes(48 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(c).setEnableDecoderFallback(true);
        ExoPlayer p = new ExoPlayer.Builder(c, renderers)
                .setMediaSourceFactory(sources)
                .setLoadControl(load)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        // الترجمة العربية أول لو موجودة
        p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon().setPreferredTextLanguages("ar", "ara").build());
        return p;
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("index", index);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player == null) return;
        h.post(tick);
        if (wasPlaying) {
            if (live) reconnect();
            else player.play();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player == null) return;
        remember();
        h.removeCallbacksAndMessages(null);
        wasPlaying = player.getPlayWhenReady();
        // المباشر نوقفه كامل عشان ما يسحب نت وهو مو ظاهر
        if (live) player.stop();
        else player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }

    // ---- الشكل

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        fit = new Fit(this);
        surface = new SurfaceView(this);
        fit.addView(surface, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(fit, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));

        subtitle = Ui.text(this, Ui.medium, tv ? 26 : 19, 0xFFFFFFFF);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        subtitle.setShadowLayer(Ui.dp(4), 0, Ui.dp(1), 0xE6000000);
        subtitle.setLineSpacing(Ui.dp(4), 1f);
        subtitle.setPadding(Ui.dp(40), 0, Ui.dp(40), 0);
        Ui.markRoom(subtitle, true);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        sp.bottomMargin = Ui.dp(tv ? 56 : 36);
        root.addView(subtitle, sp);

        spin = new ProgressBar(this);
        spin.setIndeterminateTintList(ColorStateList.valueOf(0xE6FFFFFF));
        root.addView(spin, new FrameLayout.LayoutParams(Ui.dp(54), Ui.dp(54), Gravity.CENTER));
        note = Ui.text(this, Ui.medium, 16, 0xE6FFFFFF);
        note.setShadowLayer(Ui.dp(3), 0, 0, 0xCC000000);
        note.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER);
        np.topMargin = Ui.dp(64);
        root.addView(note, np);

        root.addView(errorPanel(), new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        root.addView(overlay = osd(), new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));

        clock = Ui.text(this, Ui.medium, tv ? 22 : 17, 0xF2FFFFFF);
        clock.setShadowLayer(Ui.dp(4), 0, 0, 0xCC000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.LEFT);
        cp.setMargins(Ui.dp(tv ? 48 : 20), Ui.dp(tv ? 34 : 18), 0, 0);
        root.addView(clock, cp);
        number = Ui.text(this, Ui.bold, 54, 0xFFFFFFFF);
        number.setShadowLayer(Ui.dp(6), 0, 0, 0xCC000000);
        number.setVisibility(View.GONE);
        FrameLayout.LayoutParams nup = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.RIGHT);
        nup.setMargins(0, Ui.dp(30), Ui.dp(48), 0);
        root.addView(number, nup);

        root.addView(panel = channelPanel(), new FrameLayout.LayoutParams(Ui.dp(tv ? 430 : 320), Ui.MATCH, Gravity.RIGHT));
        panel.setVisibility(View.GONE);

        // أزرار للمس (الجوال). التلفزيون يستخدم أزرار الريموت
        LinearLayout c = new LinearLayout(this);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        c.setGravity(Gravity.CENTER);
        c.addView(touchButton(R.drawable.ic_rewind, v -> stepBack()));
        playIcon = touchButton(R.drawable.ic_pause, v -> toggle());
        c.addView(playIcon);
        c.addView(touchButton(R.drawable.ic_forward, v -> stepForward()));
        controls = c;
        controls.setVisibility(View.GONE);
        root.addView(c, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        root.setOnClickListener(v -> {
            if (panel.getVisibility() == View.VISIBLE) closePanel();
            else if (overlay.getAlpha() > 0.5f) hideNow();
            else show();
        });
        return root;
    }

    /** الطبقة اللي تحت: الشعار والاسم والبرنامج الحالي، أو شريط الوقت للأفلام. */
    private View osd() {
        LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF5000000, 0xB3000000, 0x00000000}));
        int pad = tv ? 56 : 20;
        o.setPadding(Ui.dp(pad), Ui.dp(110), Ui.dp(pad), Ui.dp(tv ? 40 : 20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout box = new FrameLayout(this);
        box.setBackground(Ui.ring(0x26FFFFFF, 0x26FFFFFF, 1, 14));
        box.setClipToOutline(true);
        logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        box.addView(logo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(tv ? 104 : 76), Ui.dp(tv ? 64 : 48));
        bp.setMarginEnd(Ui.dp(18));
        top.addView(box, bp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        title = Ui.text(this, Ui.bold, tv ? 32 : 22, Ui.INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(title, true);
        row.addView(title, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        badge = pill(Ui.OFF);
        row.addView(badge);
        quality = pill(0);
        row.addView(quality);
        if (!tv) {   // الجوال: زر للصوت والترجمة ومقاس الصورة (التلفزيون: ضغطة مطوّلة على OK)
            ImageView opt = new ImageView(this);
            opt.setImageDrawable(Ui.icon(this, R.drawable.ic_gear, 24, Ui.INK));
            opt.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
            opt.setContentDescription("الخيارات");
            opt.setOnClickListener(v -> options());
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44));
            op.setMarginStart(Ui.dp(6));
            row.addView(opt, op);
        }
        col.addView(row);
        sub = Ui.text(this, Ui.regular, tv ? 18 : 15, Ui.INK2);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(sub, false);
        col.addView(sub);
        top.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        o.addView(top);

        // البرنامج الحالي: اسمه ووقته وكم باقي منه
        nowLine = Ui.text(this, Ui.medium, tv ? 19 : 15, Ui.INK);
        nowLine.setSingleLine(true);
        nowLine.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(nowLine, false);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        nl.topMargin = Ui.dp(16);
        o.addView(nowLine, nl);
        programBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        programBar.setMax(1000);
        programBar.setProgressTintList(ColorStateList.valueOf(Ui.INK));
        programBar.setProgressBackgroundTintList(ColorStateList.valueOf(0x40FFFFFF));
        programBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(Ui.MATCH, Ui.dp(4));
        pb.topMargin = Ui.dp(8);
        o.addView(programBar, pb);
        nextLine = Ui.text(this, Ui.regular, tv ? 16 : 14, Ui.INK2);
        nextLine.setSingleLine(true);
        nextLine.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(nextLine, false);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        xl.topMargin = Ui.dp(8);
        o.addView(nextLine, xl);

        // شريط الوقت يمشي من اليسار لليمين مثل أي مشغّل، والرمادي الفاتح = اللي تحمّل مسبقا
        LinearLayout time = new LinearLayout(this);
        time.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        time.setGravity(Gravity.CENTER_VERTICAL);
        timeNow = Ui.text(this, Ui.medium, 16, Ui.INK);
        timeEnd = Ui.text(this, Ui.medium, 16, Ui.INK2);
        bar = new SeekBar(this);
        bar.setFocusable(false);
        bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.INK));
        bar.setSecondaryProgressTintList(ColorStateList.valueOf(0x80FFFFFF));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.INK));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && player.getDuration() > 0) {
                    seekTarget = player.getDuration() * p / 1000;
                    timeNow.setText(Ui.clock(seekTarget));
                }
            }

            public void onStartTrackingTouch(SeekBar s) {
                h.removeCallbacks(hide);
            }

            public void onStopTrackingTouch(SeekBar s) {
                if (seekTarget >= 0) player.seekTo(seekTarget);
                seekTarget = -1;
                show();
            }
        });
        time.addView(timeNow);
        time.addView(bar, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        time.addView(timeEnd);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        tl.topMargin = Ui.dp(16);
        o.addView(time, tl);
        return o;
    }

    private TextView pill(int dot) {
        TextView p = Ui.text(this, Ui.medium, 14, Ui.INK);
        p.setBackground(Ui.round(0x2EFFFFFF, 12));
        p.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
        if (dot != 0) {
            GradientDrawable d = Ui.round(dot, 4);
            d.setSize(Ui.dp(8), Ui.dp(8));
            p.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
            p.setCompoundDrawablePadding(Ui.dp(7));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.setMarginStart(Ui.dp(10));
        p.setLayoutParams(lp);
        p.setVisibility(View.GONE);
        return p;
    }

    private View errorPanel() {
        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER_HORIZONTAL);
        errorBox.setBackground(Ui.glass(24));
        errorBox.setPadding(Ui.dp(34), Ui.dp(26), Ui.dp(34), Ui.dp(26));
        errorTitle = Ui.text(this, Ui.bold, 22, Ui.INK);
        errorTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        Ui.markRoom(errorTitle, false);
        errorBox.addView(errorTitle);
        errorText = Ui.text(this, Ui.regular, 17, Ui.INK2);
        errorText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        errorText.setLineSpacing(Ui.dp(5), 1f);
        errorText.setMaxWidth(Ui.dp(520));
        Ui.markRoom(errorText, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.topMargin = Ui.dp(8);
        errorBox.addView(errorText, lp);
        errorBox.setVisibility(View.GONE);
        return errorBox;
    }

    /** قائمة القنوات فوق البث (OK في المباشر): تتنقل وتختار بدون ما تطلع من المشغّل. */
    private View channelPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, new int[]{0xF70F1012, 0xF20F1012, 0xD90F1012, 0x000F1012}));
        p.setPadding(Ui.dp(22), Ui.dp(30), Ui.dp(40), Ui.dp(20));
        TextView t = Ui.text(this, Ui.bold, 22, Ui.INK);
        t.setText("القنوات");
        t.setPadding(Ui.dp(12), 0, Ui.dp(12), Ui.dp(12));
        Ui.markRoom(t, false);
        p.addView(t);
        channels = new ListView(this);
        channels.setDivider(null);
        channels.setVerticalScrollBarEnabled(false);
        channels.setSelector(Ui.selector(0x33FFFFFF, 14));
        channels.setVerticalFadingEdgeEnabled(true);
        channels.setFadingEdgeLength(Ui.dp(40));
        channels.setAdapter(new BaseAdapter() {
            public int getCount() {
                return queue.size();
            }

            public Object getItem(int i) {
                return queue.get(i);
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) v = channelRow();
                Item it = queue.get(i);
                LinearLayout r = (LinearLayout) v;
                ((TextView) r.getChildAt(0)).setText(it.num > 0 ? String.valueOf(it.num) : "");
                FrameLayout lb = (FrameLayout) r.getChildAt(1);
                TextView letter = (TextView) lb.getChildAt(0);
                letter.setText(it.initial());
                Net.image((ImageView) lb.getChildAt(1), it.logo, Ui.dp(96), letter);
                TextView n = (TextView) r.getChildAt(2);
                n.setText(it.name);
                n.setTextColor(i == index ? Ui.ACCENT : Ui.INK);
                return r;
            }
        });
        channels.setOnItemClickListener((a, v, i, id) -> {
            closePanel();
            if (i != index) play(i);
        });
        p.addView(channels, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1));
        return p;
    }

    private View channelRow() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(12), Ui.dp(9));
        TextView num = Ui.text(this, Ui.medium, 15, Ui.INK3);
        num.setMinWidth(Ui.dp(38));
        r.addView(num);
        FrameLayout box = new FrameLayout(this);
        box.setBackground(Ui.round(0x1AFFFFFF, 9));
        box.setClipToOutline(true);
        TextView letter = Ui.text(this, Ui.bold, 15, Ui.INK3);
        letter.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        letter.setGravity(Gravity.CENTER);
        box.addView(letter, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ImageView im = new ImageView(this);
        im.setScaleType(ImageView.ScaleType.FIT_CENTER);
        im.setPadding(Ui.dp(4), Ui.dp(3), Ui.dp(4), Ui.dp(3));
        box.addView(im, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(60), Ui.dp(38));
        lp.setMarginEnd(Ui.dp(14));
        r.addView(box, lp);
        TextView n = Ui.text(this, Ui.medium, 17, Ui.INK);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(n, true);
        r.addView(n, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        return r;
    }

    private ImageView touchButton(int icon, View.OnClickListener l) {
        ImageView b = new ImageView(this);
        b.setImageDrawable(Ui.icon(this, icon, 30, Ui.INK));
        b.setPadding(Ui.dp(18), Ui.dp(18), Ui.dp(18), Ui.dp(18));
        b.setBackground(Ui.round(0x80000000, 34));
        b.setFocusable(false);
        b.setOnClickListener(v -> {
            l.onClick(v);
            show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(68), Ui.dp(68));
        lp.setMargins(Ui.dp(18), 0, Ui.dp(18), 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---- التشغيل

    private void play(int i) {
        remember();
        index = Math.max(0, Math.min(i, queue.size() - 1));
        item = queue.get(index);
        live = item.kind == Item.LIVE;
        triedAlt = false;
        retries = stalls = 0;
        if (live) Store.setLastLive(item);
        title.setText(item.name);
        badge.setText(live ? "مباشر" : null);
        badge.setVisibility(live ? View.VISIBLE : View.GONE);
        quality.setVisibility(View.GONE);
        String g = live ? groupName() : null;
        String s = item.kind == Item.EPISODE ? item.sub : live ? (item.num > 0 ? "القناة " + item.num + (g != null ? "، " + g : "") : g) : item.year;
        sub.setText(s);
        sub.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        ((View) logo.getParent()).setVisibility(live && item.logo != null ? View.VISIBLE : View.GONE);
        if (live) Net.image(logo, item.logo, Ui.dp(160), null);
        nowLine.setVisibility(View.GONE);
        programBar.setVisibility(View.GONE);
        programBar.setTag(null);
        nextLine.setVisibility(View.GONE);
        ((View) bar.getParent()).setVisibility(live ? View.GONE : View.VISIBLE);
        timeNow.setText("0:00");
        timeEnd.setText("");
        bar.setProgress(0);
        bar.setSecondaryProgress(0);
        subtitle.setText(null);
        if (live) loadEpg(item);
        open(Library.streamUrl(item), live || fromStart ? C.TIME_UNSET : Store.resumeAt(item));
        fromStart = false;
        show();
    }

    private void open(String u, long at) {
        url = u;
        errorBox.setVisibility(View.GONE);
        spin.setVisibility(View.VISIBLE);
        stuckFor = frozenFor = 0;
        lastPos = -1;
        everReady = false;
        openedAt = android.os.SystemClock.uptimeMillis();
        MediaItem.Builder m = new MediaItem.Builder().setUri(Uri.parse(u));
        String path = Uri.parse(u).getPath();
        if (path != null && path.toLowerCase(Locale.US).endsWith(".m3u8")) m.setMimeType(MimeTypes.APPLICATION_M3U8);
        if (at > 0 && at != C.TIME_UNSET) player.setMediaItem(m.build(), at);
        else player.setMediaItem(m.build());
        player.prepare();
        player.play();
    }

    /** نفس البث من جديد: المباشر من آخره، والفيلم من نفس الدقيقة. */
    private void reconnect() {
        if (url == null) return;
        long at = live ? C.TIME_UNSET : Math.max(0, player.getCurrentPosition() - 2000);
        note.setText("نعيد الاتصال…");
        open(url, at);
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY) {
                // الصيغة البديلة اشتغلت: نبدأ فيها القنوات الجاية بدل ما نجرب الأولى كل مرة
                if (live && triedAlt && "auto".equals(Store.liveFormat()) && url != null)
                    Library.autoExt = url.endsWith(".m3u8") ? "m3u8" : "ts";
                spin.setVisibility(View.GONE);
                note.setText(null);
                errorBox.setVisibility(View.GONE);
                everReady = true;
                playingSince = System.currentTimeMillis();
            } else if (state == Player.STATE_BUFFERING) {
                spin.setVisibility(View.VISIBLE);
            } else if (state == Player.STATE_ENDED) {
                if (live) {
                    reconnect();
                    return;
                }
                Store.watched(item, player.getDuration(), player.getDuration());
                if (index + 1 < queue.size()) {
                    Ui.note(PlayerActivity.this, "الحلقة اللي بعدها");
                    play(index + 1);
                } else finish();
            }
            updatePlayIcon();
        }

        @Override
        public void onIsPlayingChanged(boolean playing) {
            updatePlayIcon();
            if (!playing && !live && player.getPlaybackState() == Player.STATE_READY) show();
        }

        @Override
        public void onPlayerError(PlaybackException e) {
            onError(e);
        }

        @Override
        public void onVideoSizeChanged(VideoSize v) {
            if (v.width == 0 || v.height == 0) return;
            fit.setRatio(v.width * v.pixelWidthHeightRatio / v.height, aspect);
            int p = Math.min(v.width, v.height);
            String q = p >= 2000 ? "4K" : p >= 1000 ? "1080p" : p >= 700 ? "720p" : p >= 560 ? "576p" : p + "p";
            quality.setText(q);
            quality.setVisibility(View.VISIBLE);
        }

        @Override
        public void onCues(CueGroup g) {
            StringBuilder b = new StringBuilder();
            for (Cue c : g.cues) if (c.text != null) b.append(b.length() > 0 ? "\n" : "").append(c.text);
            subtitle.setText(b.length() == 0 ? null : b);
        }
    };

    private void onError(PlaybackException e) {
        // المباشر تأخر عن نافذة البث: نرجع لآخره ونكمل
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition();
            player.prepare();
            return;
        }
        boolean network = e.errorCode >= 2000 && e.errorCode < 3000;
        // الصيغة الثانية للمباشر (TS ↔ HLS) لو هذي ما انقرت أو ما ردت
        String alt = Library.altUrl(item);
        if (live && !triedAlt && alt != null && (!network || retries >= 1)) {
            triedAlt = true;
            open(alt, C.TIME_UNSET);
            return;
        }
        if (retries < RETRIES && (network || live)) {
            retries++;
            spin.setVisibility(View.VISIBLE);
            note.setText("نعيد الاتصال…");
            h.postDelayed(this::reconnect, 1000L << (retries - 1));
            return;
        }
        fail(reason(e));
    }

    /** نوقف ونقول السبب بوضوح، بدل ما نعلّق المشاهد على «نجهّز البث» للأبد. */
    private void fail(String why) {
        h.removeCallbacksAndMessages(null);
        h.post(tick);
        player.stop();
        spin.setVisibility(View.GONE);
        note.setText(null);
        errorTitle.setText(live ? "القناة ما اشتغلت" : "الفيديو ما اشتغل");
        errorText.setText(why + "\n" + (live ? "اضغط OK تعيد المحاولة، أو فوق وتحت لقناة ثانية." : "اضغط OK تعيد المحاولة."));
        errorBox.setVisibility(View.VISIBLE);
        show();
    }

    private static String reason(PlaybackException e) {
        int c = e.errorCode;
        if (c == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return "السيرفر رفض الطلب. يمكن الاشتراك منتهي أو مستخدم من جهاز ثاني.";
        if (c == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) return "الرابط ما له بث على السيرفر.";
        if (c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            return "ما قدرنا نوصل للسيرفر. تأكد من اتصال الإنترنت.";
        if (c >= 3000 && c < 4000) return "صيغة البث ما انقرت.";
        if (c >= 4000 && c < 5000) return "الجهاز ما يقدر يشغّل ترميز هذا الفيديو.";
        return "صار خطأ في التشغيل (" + c + ").";
    }

    private String groupName() {
        List<Library.Group> gs = Library.groups[Item.LIVE];
        if (gs != null) for (Library.Group g : gs) if (g.id.equals(item.group) && !Library.ALL.equals(g.id)) return g.name;
        return null;
    }

    /** نحفظ وين وقف (الأفلام والحلقات). */
    private void remember() {
        if (item == null || live || player == null) return;
        long d = player.getDuration();
        if (d > 0 && d != C.TIME_UNSET) Store.watched(item, player.getCurrentPosition(), d);
    }

    // ---- البرنامج الحالي (Xtream)

    private void loadEpg(final Item ch) {
        Library.epg(ch, list -> {
            if (ch != item || list.isEmpty()) return;
            Library.Program now = list.get(0);
            SimpleDateFormat hm = new SimpleDateFormat("h:mm", Locale.US);
            nowLine.setText("الحين: " + now.title + "   " + hm.format(new Date(now.start)) + " – " + hm.format(new Date(now.end)));
            nowLine.setVisibility(View.VISIBLE);
            programBar.setTag(now);
            programBar.setVisibility(View.VISIBLE);
            if (list.size() > 1) {
                Library.Program next = list.get(1);
                nextLine.setText("بعده: " + next.title + "   " + hm.format(new Date(next.start)));
                nextLine.setVisibility(View.VISIBLE);
            }
            refreshTime();
        });
    }

    // ---- الطبقة اللي فوق الفيديو

    private final Runnable hide = this::hideNow;

    private void show() {
        overlay.animate().cancel();
        overlay.animate().alpha(1f).setDuration(180).start();
        clock.animate().alpha(1f).setDuration(180).start();
        if (!tv) controls.setVisibility(View.VISIBLE);
        h.removeCallbacks(hide);
        if (errorBox.getVisibility() != View.VISIBLE) h.postDelayed(hide, live ? 5000 : 4000);
        refreshTime();
    }

    private void hideNow() {
        if (!live && player != null && player.getPlaybackState() == Player.STATE_READY && !player.getPlayWhenReady()) return;   // وهو موقف تبقى الطبقة
        overlay.animate().alpha(0f).setDuration(300).start();
        clock.animate().alpha(0f).setDuration(300).start();
        controls.setVisibility(View.GONE);
    }

    private void openPanel() {
        if (!live || queue.size() < 2) return;
        hideNow();
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationX(Ui.dp(60));
        panel.animate().alpha(1f).translationX(0).setDuration(220).start();
        ((BaseAdapter) channels.getAdapter()).notifyDataSetChanged();
        channels.setSelectionFromTop(index, Ui.dp(200));   // القناة الحالية في النص، واللي قبلها ظاهرة
        channels.requestFocus();
        h.removeCallbacks(panelIdle);
        h.postDelayed(panelIdle, 12000);
    }

    private void closePanel() {
        h.removeCallbacks(panelIdle);
        panel.animate().alpha(0f).translationX(Ui.dp(60)).setDuration(180).withEndAction(() -> panel.setVisibility(View.GONE)).start();
    }

    private final Runnable panelIdle = this::closePanel;

    /** كل ثانية: الوقت، والمراقب اللي يرجّع البث لو علّق. */
    private final Runnable tick = new Runnable() {
        public void run() {
            clock.setText(new SimpleDateFormat("h:mm", Locale.US).format(new Date()));
            refreshTime();
            watchdog();
            if (!live && player.isPlaying() && player.getCurrentPosition() / 1000 % 10 == 0) remember();
            if (player.isPlaying() && System.currentTimeMillis() - playingSince > 15000) retries = stalls = 0;   // اشتغل زين: نصفّر المحاولات
            h.postDelayed(this, 1000);
        }
    };

    private void watchdog() {
        if (!player.getPlayWhenReady() || errorBox.getVisibility() == View.VISIBLE) {
            stuckFor = frozenFor = 0;
            return;
        }
        int st = player.getPlaybackState();
        if (st == Player.STATE_BUFFERING || st == Player.STATE_IDLE) {
            // أول تحميل: نعطي المشغّل (وإعادة محاولاته) 30 ثانية، وبعدها نقول السبب
            if (!everReady) {
                long waited = android.os.SystemClock.uptimeMillis() - openedAt;
                if (waited > 5000) note.setText("نجهّز البث…");
                if (waited > FIRST_LOAD_MS) fail("السيرفر ما رد في وقته. يمكن النت بطيء أو البث واقف الحين.");
                return;
            }
            stuckFor++;
            if (stuckFor == 5) note.setText("نجهّز البث…");
            if (stuckFor >= (live ? 12 : 20)) restart();
        } else if (st == Player.STATE_READY) {
            stuckFor = 0;
            long p = player.getCurrentPosition();
            frozenFor = p == lastPos ? frozenFor + 1 : 0;
            lastPos = p;
            if (frozenFor >= 8) restart();   // الصورة واقفة والمشغّل يحسب إنه شغّال
        }
    }

    /** تعليق وسط المشاهدة: نعيد الاتصال لين 3 مرات، وبعدها نوضّح. */
    private void restart() {
        if (++stalls > STALLS) fail("البث يتقطّع من السيرفر. جرّب بعد شوي، أو غيّر صيغة البث من الإعدادات.");
        else reconnect();
    }

    private void refreshTime() {
        Object tag = programBar.getTag();
        if (tag instanceof Library.Program) {
            Library.Program pr = (Library.Program) tag;
            long now = System.currentTimeMillis();
            programBar.setProgress((int) Math.max(0, Math.min(1000, 1000 * (now - pr.start) / Math.max(1, pr.end - pr.start))));
        }
        if (live || player == null || seekTarget >= 0) return;
        long d = player.getDuration(), p = player.getCurrentPosition(), buf = player.getBufferedPosition();
        if (d <= 0 || d == C.TIME_UNSET) return;
        timeNow.setText(Ui.clock(p));
        timeEnd.setText("-" + Ui.clock(d - p));
        bar.setProgress((int) (1000 * p / d));
        bar.setSecondaryProgress((int) (1000 * buf / d));
    }

    private void updatePlayIcon() {
        int icon = live ? R.drawable.ic_layers : player.getPlayWhenReady() ? R.drawable.ic_pause : R.drawable.ic_play_fill;
        playIcon.setImageDrawable(Ui.icon(this, icon, 30, Ui.INK));
    }

    // ---- التحكم

    private void toggle() {
        if (live) {   // في المباشر الزر الأوسط (اللمس) يفتح قائمة القنوات
            if (!tv) openPanel();
            return;
        }
        if (player.getPlayWhenReady()) player.pause();
        else player.play();
        show();
    }

    private void stepBack() {
        if (live) play(index - 1);
        else seekBy(-10000);
    }

    private void stepForward() {
        if (live) play(index + 1);
        else seekBy(10000);
    }

    /** الضغطات المتتالية تتجمع، والقفزة تصير لما توقف. */
    private void seekBy(long ms) {
        long d = player.getDuration();
        if (d <= 0 || d == C.TIME_UNSET) return;
        long base = seekTarget >= 0 ? seekTarget : player.getCurrentPosition();
        seekTarget = Math.max(0, Math.min(d - 1000, base + ms));
        timeNow.setText(Ui.clock(seekTarget));
        bar.setProgress((int) (1000 * seekTarget / d));
        show();
        h.removeCallbacks(doSeek);
        h.postDelayed(doSeek, 600);
    }

    private final Runnable doSeek = () -> {
        if (seekTarget >= 0) player.seekTo(seekTarget);
        seekTarget = -1;
    };

    /** الأرقام تروح لرقم القناة: نجمعها ثانية ونص. */
    private void digit(int d) {
        if (!live) return;
        digits = digits.length() >= 4 ? String.valueOf(d) : digits + d;
        number.setText(digits);
        number.setVisibility(View.VISIBLE);
        h.removeCallbacks(jump);
        h.postDelayed(jump, 1500);
    }

    private final Runnable jump = () -> {
        number.setVisibility(View.GONE);
        int want;
        try {
            want = Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return;
        } finally {
            digits = "";
        }
        for (int i = 0; i < queue.size(); i++) if (queue.get(i).num == want) {
            play(i);
            return;
        }
        List<Item> all = Library.items[Item.LIVE];
        if (all != null) for (int i = 0; i < all.size(); i++) if (all.get(i).num == want) {
            queue = new ArrayList<>(all);
            play(i);
            return;
        }
        Ui.note(this, "ما فيه قناة رقمها " + want);
    };

    /** الصوت والترجمة ومقاس الصورة. */
    private void options() {
        final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        Tracks tracks = player.getCurrentTracks();
        int audio = 0, text = 0;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO) audio += g.length;
            if (g.getType() == C.TRACK_TYPE_TEXT) text += g.length;
        }
        if (audio > 1) {
            labels.add("الصوت");
            values.add(selected(tracks, C.TRACK_TYPE_AUDIO));
            acts.add(() -> pickTrack(C.TRACK_TYPE_AUDIO, "الصوت"));
        }
        if (text > 0) {
            labels.add("الترجمة");
            values.add(player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ? "مطفية" : selected(tracks, C.TRACK_TYPE_TEXT));
            acts.add(() -> pickTrack(C.TRACK_TYPE_TEXT, "الترجمة"));
        }
        labels.add("مقاس الصورة");
        values.add(ASPECTS[aspect]);
        acts.add(() -> Sheet.menu(this, "مقاس الصورة", ASPECTS, null, aspect, i -> {
            aspect = i;
            Store.setAspect(i);
            fit.setRatio(fit.ratio, aspect);
        }));
        if (live) {
            labels.add("أعد الاتصال");
            values.add("");
            acts.add(this::reconnect);
        }
        Sheet.menu(this, item.name, labels.toArray(new String[0]), values.toArray(new String[0]), -1, i -> acts.get(i).run());
    }

    private static final String[] ASPECTS = {"على قد الشاشة", "تكبير يعبّي الشاشة", "تمديد"};

    private static String selected(Tracks t, int type) {
        for (Tracks.Group g : t.getGroups())
            if (g.getType() == type) for (int i = 0; i < g.length; i++) if (g.isTrackSelected(i)) return trackName(g.getTrackFormat(i), i);
        return "";
    }

    private static String trackName(Format f, int i) {
        String lang = f.language == null || f.language.equals("und") ? null : new Locale(f.language).getDisplayLanguage(new Locale("ar"));
        String label = f.label != null ? f.label : lang;
        return label != null ? label : "مسار " + (i + 1);
    }

    private void pickTrack(final int type, String title) {
        final List<Tracks.Group> groups = new ArrayList<>();
        final List<Integer> idx = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int cur = -1;
        if (type == C.TRACK_TYPE_TEXT) {
            names.add("بدون ترجمة");
            groups.add(null);
            idx.add(-1);
            if (player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) cur = 0;
        }
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                if (g.isTrackSelected(i) && cur < 0) cur = names.size();
                names.add(trackName(g.getTrackFormat(i), i));
                groups.add(g);
                idx.add(i);
            }
        }
        Sheet.menu(this, title, names.toArray(new String[0]), null, cur, k -> {
            if (groups.get(k) == null) {
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
                subtitle.setText(null);
                return;
            }
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(new TrackSelectionOverride(groups.get(k).getMediaTrackGroup(), idx.get(k)))
                    .build());
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (player == null) return super.dispatchKeyEvent(e);   // انقفل قبل ما يجهز (ما فيه قائمة تشغيل)
        int code = e.getKeyCode();
        if (panel.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                h.removeCallbacks(panelIdle);
                h.postDelayed(panelIdle, 12000);
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    closePanel();
                    return true;
                }
            }
            return super.dispatchKeyEvent(e);
        }
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        // ضغطة مطوّلة على OK = الخيارات (أغلب الريموتات ما فيها زر قائمة)
        if (ok && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0) {
            if (!longOk) {
                longOk = true;
                options();
            }
            return true;
        }
        if (ok && e.getAction() == KeyEvent.ACTION_UP) {
            if (longOk) longOk = false;
            else if (errorBox.getVisibility() == View.VISIBLE) play(index);
            else if (live) openPanel();
            else if (overlay.getAlpha() < 0.5f) show();
            else toggle();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return ok || super.dispatchKeyEvent(e);
        if (ok) return true;
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digit(code - KeyEvent.KEYCODE_0);
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (live) play(index - 1);
                else show();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (live) play(index + 1);
                else show();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (live) openPanel();
                else seekBy(e.getRepeatCount() > 2 ? -30000 : -10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (live) show();
                else seekBy(e.getRepeatCount() > 2 ? 30000 : 10000);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                toggle();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                options();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                play(index + 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                play(index - 1);
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    /** إطار يحافظ على نسبة الفيديو: على قد الشاشة، أو تكبير يعبّيها، أو تمديد. */
    static final class Fit extends FrameLayout {
        float ratio;
        int mode;

        Fit(Context c) {
            super(c);
        }

        void setRatio(float r, int m) {
            ratio = r;
            mode = m;
            requestLayout();
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), hh = MeasureSpec.getSize(hs);
            if (ratio > 0 && mode != 2 && w > 0 && hh > 0) {
                float view = (float) w / hh;
                if (mode == 0 ? ratio > view : ratio < view) hh = Math.round(w / ratio);
                else w = Math.round(hh * ratio);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hh, MeasureSpec.EXACTLY));
        }
    }
}
