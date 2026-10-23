package io.github.saadharazi.shasha;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/** قائمة خيارات فوق الشاشة: عنوان وصفوف، وكل صف له قيمة على الطرف الثاني. */
final class Sheet {
    interface Pick {
        void pick(int i);
    }

    private Sheet() {}

    /** checked = الخيار الحالي (عليه علامة صح)، أو -1. values ممكن تكون null. */
    static Dialog menu(Activity a, String title, final String[] labels, final String[] values, final int checked, final Pick pick) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setBackground(Ui.ring(Ui.SURFACE, Ui.LINE, 1, 24));
        panel.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(12));

        TextView t = Ui.text(a, Ui.bold, 20, Ui.INK);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(12));
        Ui.markRoom(t, false);
        panel.addView(t);

        final ListView list = new ListView(a);
        list.setDivider(null);
        list.setSelector(Ui.selector(Ui.RAISED, 16));
        list.setDrawSelectorOnTop(false);
        list.setVerticalFadingEdgeEnabled(true);
        list.setFadingEdgeLength(Ui.dp(24));
        list.setAdapter(new BaseAdapter() {
            public int getCount() {
                return labels.length;
            }

            public Object getItem(int i) {
                return labels[i];
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View v, ViewGroup parent) {
                if (v == null) {
                    LinearLayout row = new LinearLayout(parent.getContext());
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(Ui.dp(54));
                    row.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
                    TextView l = Ui.text(parent.getContext(), Ui.medium, 17, Ui.INK);
                    l.setSingleLine(true);
                    l.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    Ui.markRoom(l, true);
                    row.addView(l, new LinearLayout.LayoutParams(0, Ui.WRAP, 1));
                    TextView val = Ui.text(parent.getContext(), Ui.regular, 15, Ui.INK2);
                    val.setSingleLine(true);
                    val.setPaddingRelative(Ui.dp(14), 0, 0, 0);
                    Ui.markRoom(val, true);
                    row.addView(val);
                    ImageView ok = new ImageView(parent.getContext());
                    ok.setImageDrawable(Ui.icon(parent.getContext(), R.drawable.ic_check, 20, Ui.ACCENT));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20));
                    lp.setMarginStart(Ui.dp(12));
                    row.addView(ok, lp);
                    v = row;
                }
                LinearLayout row = (LinearLayout) v;
                ((TextView) row.getChildAt(0)).setText(labels[i]);
                TextView val = (TextView) row.getChildAt(1);
                String value = values == null ? null : values[i];
                val.setText(value);
                val.setVisibility(value == null || value.isEmpty() ? View.GONE : View.VISIBLE);
                row.getChildAt(2).setVisibility(i == checked ? View.VISIBLE : View.GONE);
                return row;
            }
        });
        list.setOnItemClickListener((p, v, i, id) -> {
            d.dismiss();
            pick.pick(i);
        });
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.72f);
        int rows = Math.min(labels.length * Ui.dp(54), maxH - Ui.dp(70));
        panel.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, rows));

        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            int width = Math.min(Ui.dp(460), a.getResources().getDisplayMetrics().widthPixels - Ui.dp(32));
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.62f);
        }
        d.show();
        if (checked > 0) list.setSelection(checked);
        list.requestFocus();
        return d;
    }
}
