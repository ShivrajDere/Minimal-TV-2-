package com.minimal.tvlauncher;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Theme + animated wallpaper picker. The wallpaper behind this screen is a live preview. */
public class LookActivity extends BaseActivity {
    private final List<TextView> themeChips = new ArrayList<>();
    private final List<TextView> modeChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.BOTTOM);
        col.setPadding(dp(64), dp(40), dp(64), dp(64));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextView title = label("Look", 34, 0xFFFFFFFF);
        col.addView(title);

        col.addView(label("Theme colors", 16, 0xAAFFFFFF), top(26));
        col.addView(chipRow(BackgroundView.THEME_NAMES, themeChips, true), top(10));

        col.addView(label("Animated wallpaper", 16, 0xAAFFFFFF), top(26));
        col.addView(chipRow(BackgroundView.MODE_NAMES, modeChips, false), top(10));

        col.addView(label("Photos mode plays your uploaded pictures as a slow slideshow.", 14, 0x88FFFFFF), top(18));

        attachOverlay();
        updateChips();
    }

    private LinearLayout chipRow(String[] names, List<TextView> store, final boolean isTheme) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView c = label(names[i], 20, 0xFFFFFFFF);
            c.setPadding(dp(26), dp(14), dp(26), dp(14));
            Ui.focusFx(c, 28);
            c.setOnClickListener(v -> {
                if (isTheme) {
                    Photos.setTheme(this, idx);
                } else {
                    if (idx == BackgroundView.MODE_PHOTOS && Photos.list(this).length == 0)
                        Toast.makeText(this, "No photos yet - use Upload first", Toast.LENGTH_SHORT).show();
                    Photos.setMode(this, idx);
                }
                bg.reload();
                updateChips();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(12);
            r.addView(c, lp);
            store.add(c);
        }
        return r;
    }

    private void updateChips() {
        mark(themeChips, BackgroundView.THEME_NAMES, Photos.theme(this));
        mark(modeChips, BackgroundView.MODE_NAMES, Photos.mode(this));
    }

    private void mark(List<TextView> chips, String[] names, int selected) {
        for (int i = 0; i < chips.size(); i++) {
            boolean on = i == selected;
            chips.get(i).setText(on ? "●  " + names[i] : names[i]);
            chips.get(i).setTextColor(on ? 0xFF9DB4FF : 0xFFFFFFFF);
            chips.get(i).setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private TextView label(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams top(int dpTop) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(dpTop);
        return lp;
    }
}
