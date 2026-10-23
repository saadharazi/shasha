package io.github.saadharazi.shasha;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * المشغّل. المباشر: فوق/تحت يقلب القنوات وOK يطلع اسم القناة.
 * الأفلام والحلقات: OK يوقف ويكمل، ويمين/يسار يقدّم ويرجّع، والحلقة اللي بعدها تشتغل لحالها.
 */
public class PlayerActivity extends Activity {
    private static List<Item> queue = new ArrayList<>();
    private static int startAt;
    private static boolean startFromBeginning;

    private Item item;
    private int index;
    private boolean live, tv, triedAlt, fromStart;
    private VideoView video;
    private MediaPlayer mp;
    private View overlay, controls;
    private TextView title, sub, epg, timeNow, timeEnd, error, badge;
    private SeekBar bar;
    private ProgressBar spin;
    private ImageView playIcon;
    private final Handler h = new Handler();
    private long seekTarget = -1;
    private String url;

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
        Store.init(this);
        if (Library.source == null || queue.isEmpty()) {
            finish();
            return;
        }
        tv = Ui.isTv(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(build());
        index = b != null ? b.getInt("index", startAt) : startAt;
        fromStart = startFromBeginning;
        play(index);
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("index", index);
    }

    @Override
    protected void onPause() {
        super.onPause();
        remember();
        h.removeCallbacksAndMessages(null);
        if (video != null) video.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (video != null && mp != null && !video.isPlaying()) {
            if (live) play(index);
            else video.start();
        }
        h.post(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (video != null) video.stopPlayback();
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        video = new VideoView(this);
        root.addView(video, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));

        spin = new ProgressBar(this);
        spin.setIndeterminateTintList(ColorStateList.valueOf(0xCCFFFFFF));
        root.addView(spin, new FrameLayout.LayoutParams(Ui.dp(52), Ui.dp(52), Gravity.CENTER));

        error = Ui.text(this, Ui.medium, 18, Ui.INK);
        error.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        error.setLineSpacing(Ui.dp(5), 1f);
        error.setBackground(Ui.ring(0xE617191C, Ui.LINE, 1, 22));
        error.setPadding(Ui.dp(28), Ui.dp(20), Ui.dp(28), Ui.dp(20));
        error.setMaxWidth(Ui.dp(520));
        error.setVisibility(View.GONE);
        root.addView(error, new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setGravity(Gravity.BOTTOM);
        o.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF2000000, 0x99000000, 0x00000000}));
        int pad = tv ? 56 : 22;
        o.setPadding(Ui.dp(pad), Ui.dp(80), Ui.dp(pad), Ui.dp(tv ? 40 : 22));
        overlay = o;

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        badge = Ui.text(this, Ui.medium, 14, Ui.INK);
        badge.setBackground(Ui.round(0x33F3F3F0, 12));
        badge.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
        GradientDrawable dot = Ui.round(Ui.OFF, 4);
        dot.setSize(Ui.dp(8), Ui.dp(8));
        badge.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        badge.setCompoundDrawablePadding(Ui.dp(7));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        bp.setMarginEnd(Ui.dp(14));
        top.addView(badge, bp);
        title = Ui.text(this, Ui.bold, tv ? 30 : 22, Ui.INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(title, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1);
        top.addView(title, tp);
        if (!tv) {
            ImageView au = new ImageView(this);
            au.setImageDrawable(Ui.icon(this, R.drawable.ic_audio, 24, Ui.INK));
            au.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
            au.setContentDescription("الصوت");
            au.setOnClickListener(v -> audio());
            top.addView(au, new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
        }
        o.addView(top);
        sub = Ui.text(this, Ui.regular, tv ? 17 : 15, Ui.INK2);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        Ui.markRoom(sub, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        sp.topMargin = Ui.dp(8);
        o.addView(sub, sp);
        epg = Ui.text(this, Ui.regular, tv ? 16 : 14, Ui.INK2);
        epg.setLineSpacing(Ui.dp(4), 1f);
        epg.setMaxLines(2);
        Ui.markRoom(epg, false);
        epg.setVisibility(View.GONE);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        ep.topMargin = Ui.dp(10);
        o.addView(epg, ep);

        // شريط الوقت يمشي من اليسار لليمين مثل أي مشغّل، حتى والواجهة عربي
        LinearLayout time = new LinearLayout(this);
        time.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        time.setGravity(Gravity.CENTER_VERTICAL);
        timeNow = Ui.text(this, Ui.medium, 15, Ui.INK);
        timeEnd = Ui.text(this, Ui.medium, 15, Ui.INK2);
        bar = new SeekBar(this);
        bar.setFocusable(false);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.INK));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0x66F3F3F0));
        bar.setThumbTintList(ColorStateList.valueOf(Ui.INK));
        bar.setMax(1000);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && video.getDuration() > 0) {
                    long t = (long) video.getDuration() * p / 1000;
                    timeNow.setText(Ui.clock(t));
                    seekTarget = t;
                }
            }

            public void onStartTrackingTouch(SeekBar s) {
                h.removeCallbacks(hide);
            }

            public void onStopTrackingTouch(SeekBar s) {
                if (seekTarget >= 0) video.seekTo((int) seekTarget);
                seekTarget = -1;
                show();
            }
        });
        time.addView(timeNow);
        time.addView(bar, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
        time.addView(timeEnd);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        tl.topMargin = Ui.dp(14);
        o.addView(time, tl);
        root.addView(o, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM));

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
            if (overlay.getAlpha() > 0.5f) hideNow();
            else show();
        });

        video.setOnPreparedListener(p -> {
            h.removeCallbacks(stalled);
            mp = p;
            spin.setVisibility(View.GONE);
            error.setVisibility(View.GONE);
            p.setOnInfoListener((m, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) spin.setVisibility(View.VISIBLE);
                else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)
                    spin.setVisibility(View.GONE);
                return false;
            });
            if (!live && !fromStart) {
                long at = Store.resumeAt(item);
                if (at > 0 && (p.getDuration() <= 0 || at < p.getDuration() - 10000)) video.seekTo((int) at);
            }
            fromStart = false;
            video.start();
            updatePlayIcon();
        });
        video.setOnErrorListener((p, what, extra) -> {
            failed(reason(what, extra));
            return true;
        });
        video.setOnCompletionListener(p -> {
            if (live) {
                play(index);
                return;
            }
            Store.watched(item, video.getDuration(), video.getDuration());
            if (index + 1 < queue.size()) {
                Ui.note(this, "الحلقة اللي بعدها");
                play(index + 1);
            } else finish();
        });
        return root;
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

    /** البث ما اشتغل: للمباشر نجرب الصيغة الثانية أول، وبعدها نقول السبب. */
    private void failed(String why) {
        h.removeCallbacks(stalled);
        String alt = Library.altUrl(item);
        if (live && !triedAlt && alt != null) {
            triedAlt = true;
            open(alt);
            return;
        }
        video.stopPlayback();
        mp = null;
        spin.setVisibility(View.GONE);
        error.setText((live ? "القناة ما اشتغلت" : "الفيديو ما اشتغل") + "\n" + why
                + (live ? "\nجرّب قناة ثانية بالأسهم، أو اضغط OK تعيد المحاولة." : "\nاضغط OK تعيد المحاولة."));
        error.setVisibility(View.VISIBLE);
        show();
    }

    // لو البث ما جهز في 25 ثانية نعتبره خطأ، بدل ما الجهاز يعيد المحاولة نص دقيقة وأكثر
    private final Runnable stalled = () -> failed("السيرفر ما رد في وقته. النت بطيء أو الرابط ما يشتغل الحين.");

    private static String reason(int what, int extra) {
        if (extra == MediaPlayer.MEDIA_ERROR_IO || what == MediaPlayer.MEDIA_ERROR_IO || extra == -1005)
            return "السيرفر ما رد، أو الرابط ما يشتغل الحين.";
        if (extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED || extra == MediaPlayer.MEDIA_ERROR_MALFORMED)
            return "صيغة الفيديو ما يدعمها هذا الجهاز.";
        if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) return "انتهى وقت الانتظار. النت بطيء أو السيرفر زحمة.";
        return "رمز الخطأ " + what + "/" + extra + ".";
    }

    private void play(int i) {
        remember();
        index = Math.max(0, Math.min(i, queue.size() - 1));
        item = queue.get(index);
        live = item.kind == Item.LIVE;
        triedAlt = false;
        mp = null;
        title.setText(item.kind == Item.LIVE && item.num > 0 ? item.num + "  " + item.name : item.name);
        badge.setText(live ? "مباشر" : null);
        badge.setVisibility(live ? View.VISIBLE : View.GONE);
        String s = item.kind == Item.EPISODE ? item.sub : live ? groupName() : item.year;
        sub.setText(s);
        sub.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        epg.setVisibility(View.GONE);
        ((View) bar.getParent()).setVisibility(live ? View.GONE : View.VISIBLE);
        timeNow.setText("0:00");
        timeEnd.setText("");
        bar.setProgress(0);
        if (live) {
            final Item ch = item;
            Library.epg(ch, t -> {
                if (ch != item) return;
                epg.setText(t);
                epg.setVisibility(View.VISIBLE);
            });
        }
        error.setVisibility(View.GONE);
        open(Library.streamUrl(item));
        show();
    }

    private void open(String u) {
        url = u;
        spin.setVisibility(View.VISIBLE);
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", Net.UA);
        video.setVideoURI(Uri.parse(u), headers);
        h.removeCallbacks(stalled);
        h.postDelayed(stalled, 25000);
    }

    private String groupName() {
        List<Library.Group> gs = Library.groups[Item.LIVE];
        if (gs != null) for (Library.Group g : gs) if (g.id.equals(item.group) && !Library.ALL.equals(g.id)) return g.name;
        return null;
    }

    /** نحفظ وين وقف (الأفلام والحلقات). */
    private void remember() {
        if (item == null || live || mp == null) return;
        int d = video.getDuration();
        if (d > 0) Store.watched(item, video.getCurrentPosition(), d);
    }

    // ---- الطبقة اللي فوق الفيديو

    private final Runnable hide = this::hideNow;

    private void show() {
        overlay.animate().cancel();
        overlay.animate().alpha(1f).setDuration(160).start();
        if (!tv) controls.setVisibility(View.VISIBLE);
        h.removeCallbacks(hide);
        if (error.getVisibility() != View.VISIBLE) h.postDelayed(hide, live ? 4500 : 3500);
        refreshTime();
    }

    private void hideNow() {
        if (mp != null && !live && !video.isPlaying()) return;   // وهو موقف تبقى الطبقة ظاهرة
        overlay.animate().alpha(0f).setDuration(260).start();
        controls.setVisibility(View.GONE);
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            refreshTime();
            if (!live && mp != null && video.isPlaying() && video.getCurrentPosition() / 1000 % 10 == 0) remember();
            h.postDelayed(this, 1000);
        }
    };

    private void refreshTime() {
        if (live || mp == null || seekTarget >= 0) return;
        int d = video.getDuration(), p = video.getCurrentPosition();
        if (d <= 0) return;
        timeNow.setText(Ui.clock(p));
        timeEnd.setText(Ui.clock(d));
        bar.setProgress((int) (1000L * p / d));
    }

    private void updatePlayIcon() {
        playIcon.setImageDrawable(Ui.icon(this, video.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_fill, 30, Ui.INK));
    }

    // ---- التحكم

    private void toggle() {
        if (live || mp == null) return;
        if (video.isPlaying()) video.pause();
        else video.start();
        updatePlayIcon();
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

    /** الضغطات المتتالية تتجمع، والقفزة تصير لما توقف (ما نرهق السيرفر). */
    private void seekBy(long ms) {
        if (mp == null || video.getDuration() <= 0) return;
        long base = seekTarget >= 0 ? seekTarget : video.getCurrentPosition();
        seekTarget = Math.max(0, Math.min(video.getDuration() - 1000, base + ms));
        timeNow.setText(Ui.clock(seekTarget));
        bar.setProgress((int) (1000L * seekTarget / video.getDuration()));
        show();
        h.removeCallbacks(doSeek);
        h.postDelayed(doSeek, 650);
    }

    private final Runnable doSeek = () -> {
        if (seekTarget >= 0) video.seekTo((int) seekTarget);
        seekTarget = -1;
    };

    private void audio() {
        if (mp == null) return;
        final List<Integer> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            MediaPlayer.TrackInfo[] tracks = mp.getTrackInfo();
            for (int i = 0; i < tracks.length; i++)
                if (tracks[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                    ids.add(i);
                    String lang = tracks[i].getLanguage();
                    names.add("الصوت " + ids.size() + (lang == null || lang.equals("und") ? "" : " (" + lang + ")"));
                }
        } catch (Exception ignored) {
        }
        if (ids.size() < 2) {
            Ui.note(this, "هذا الفيديو فيه صوت واحد بس");
            return;
        }
        int cur = -1;
        try {
            cur = ids.indexOf(mp.getSelectedTrack(MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO));
        } catch (Exception ignored) {
        }
        Sheet.menu(this, "الصوت", names.toArray(new String[0]), null, cur, i -> {
            try {
                mp.selectTrack(ids.get(i));
            } catch (Exception e) {
                Ui.note(this, "الجهاز ما قدر يغيّر الصوت");
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
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
                if (!live) seekBy(e.getRepeatCount() > 2 ? -30000 : -10000);
                else show();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!live) seekBy(e.getRepeatCount() > 2 ? 30000 : 10000);
                else show();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (error.getVisibility() == View.VISIBLE) play(index);
                else if (live || overlay.getAlpha() < 0.5f) show();
                else toggle();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                toggle();
                return true;
            case KeyEvent.KEYCODE_MENU:
                audio();
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
}
