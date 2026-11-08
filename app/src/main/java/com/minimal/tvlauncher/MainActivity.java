package com.minimal.tvlauncher;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends BaseActivity {
    private TextView clock, date, moveHint;
    private LinearLayout row, topBar;
    private HorizontalScrollView scroller;
    private Ui.Icon netIcon;
    private ConnectivityManager cm;
    private boolean netRegistered;
    private View moving; // the app tile being moved (null = not in move mode)

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Date now = new Date();
            clock.setText(DateFormat.getTimeFormat(MainActivity.this).format(now));
            date.setText(DateFormat.format("EEEE, d MMMM", now));
            clock.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(48), dp(64), dp(40));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        clock = new TextView(this);
        clock.setTextColor(0xFFFFFFFF);
        clock.setTextSize(72);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        col.addView(clock);

        date = new TextView(this);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(20);
        col.addView(date);

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        moveHint = new TextView(this);
        moveHint.setText("◀  ▶   move the app        OK   done");
        moveHint.setTextColor(0xFF9DB4FF);
        moveHint.setTextSize(16);
        moveHint.setPadding(dp(8), 0, 0, 0);
        moveHint.setVisibility(View.INVISIBLE);
        col.addView(moveHint);

        scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setSmoothScrollingEnabled(true);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(dp(8), dp(24), dp(8), dp(24));
        scroller.addView(row);
        col.addView(scroller, new LinearLayout.LayoutParams(-1, -2));

        buildTopBar();
        attachOverlay();
        buildRow();
    }

    // ---------------- top-right bar: Wi-Fi status, Look, Settings ----------------
    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClipChildren(false);

        netIcon = new Ui.Icon(this, Ui.I_WIFI);
        addTopIcon(netIcon, "Wi-Fi", v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        addTopIcon(new Ui.Icon(this, Ui.I_LOOK), "Look",
                v -> startActivity(new Intent(this, LookActivity.class)));
        addTopIcon(new Ui.Icon(this, Ui.I_SETTINGS), "Settings",
                v -> openSettings(Settings.ACTION_SETTINGS));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.topMargin = dp(40);
        lp.rightMargin = dp(54);
        root.addView(topBar, lp);
    }

    private void addTopIcon(View icon, String desc, View.OnClickListener l) {
        icon.setContentDescription(desc);
        Ui.focusFx(icon, 26);
        icon.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
        lp.leftMargin = dp(10);
        topBar.addView(icon, lp);
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "Settings not available on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final ConnectivityManager.NetworkCallback netCb = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { postNet(); }
        @Override public void onLost(Network n) { postNet(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { postNet(); }
    };

    private void postNet() {
        runOnUiThread(() -> {
            refreshNet();
            // after a disconnect the system can still report the old network for a moment
            netIcon.postDelayed(this::refreshNet, 700);
        });
    }

    private void refreshNet() {
        int kind = Ui.I_WIFI, level = 3;
        boolean off = true;
        try {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    off = false;
                    level = wifiLevel();
                } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    off = false;
                    kind = Ui.I_ETH;
                } else if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    off = false;
                    kind = Ui.I_ETH;
                }
            }
        } catch (Exception ignored) { }
        netIcon.set(kind, level, off);
        netIcon.setContentDescription(off ? "Wi-Fi: not connected"
                : kind == Ui.I_ETH ? "Connected (wired)" : "Wi-Fi: connected");
    }

    @SuppressWarnings("deprecation")
    private int wifiLevel() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            int rssi = wi.getRssi();
            if (rssi <= -127) return 3;
            return rssi >= -60 ? 3 : rssi >= -72 ? 2 : 1;
        } catch (Exception e) {
            return 3;
        }
    }

    // ---------------- home row ----------------
    private void buildRow() {
        moving = null;
        moveHint.setVisibility(View.INVISIBLE);
        row.removeAllViews();
        addShortcut("Photos", Ui.PHOTOS, v -> startActivity(new Intent(this, GalleryActivity.class)));

        new Thread(() -> {
            final List<AppItem> apps = loadApps();
            runOnUiThread(() -> {
                for (AppItem a : apps) addApp(a);
                View first = row.getChildAt(0);
                if (first != null && getCurrentFocus() == null) first.requestFocus();
            });
        }).start();
    }

    private static class AppItem {
        String label; String pkg; ComponentName cn; String cat;
        android.graphics.drawable.Drawable icon;
    }

    private List<AppItem> loadApps() {
        PackageManager pm = getPackageManager();
        List<AppItem> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                AppItem a = new AppItem();
                a.pkg = pkg;
                a.cat = cat;
                a.cn = new ComponentName(pkg, ri.activityInfo.name);
                a.label = String.valueOf(ri.loadLabel(pm));
                a.icon = ri.loadIcon(pm);
                out.add(a);
            }
        }
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));

        // then apply the user's saved order (new apps that are not saved yet stay A-Z at the end)
        final Map<String, Integer> pos = new HashMap<>();
        List<String> saved = Photos.appOrder(this);
        for (int i = 0; i < saved.size(); i++) pos.put(saved.get(i), i);
        Collections.sort(out, (x, y) -> {
            Integer a = pos.get(x.pkg), b = pos.get(y.pkg);
            if (a != null && b != null) return a - b;
            if (a != null) return -1;
            if (b != null) return 1;
            return 0; // stable sort keeps A-Z
        });
        return out;
    }

    private View tile(View iconView, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(14), dp(14), dp(14), dp(12));
        t.addView(iconView, new LinearLayout.LayoutParams(dp(88), dp(88)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(15);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxWidth(dp(110));
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        t.addView(tv, lp);
        Ui.focusFx(t, 18);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.rightMargin = dp(10);
        row.addView(t, tl);
        return t;
    }

    private void addShortcut(String label, int kind, View.OnClickListener l) {
        View t = tile(new Ui.Glyph(this, kind), label);
        t.setOnClickListener(l);
    }

    private void addApp(final AppItem a) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final View t = tile(iv, a.label);
        t.setTag(a.pkg);
        t.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(a.cat).setComponent(a.cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try { startActivity(i); } catch (Exception ignored) { }
        });
        // press and hold OK = menu (Move / App info / Reset order)
        t.setOnLongClickListener(v -> {
            showAppMenu(t, a);
            return true;
        });
    }

    private void showAppMenu(final View t, final AppItem a) {
        String[] items = {"Move", "App info / Uninstall", "Reset order (A-Z)"};
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        startMove(t);
                    } else if (which == 1) {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + a.pkg)));
                        } catch (Exception ignored) { }
                    } else {
                        Photos.setAppOrder(this, new ArrayList<>());
                        buildRow();
                    }
                })
                .show();
    }

    // ---------------- move apps ----------------
    private void startMove(View t) {
        moving = t;
        t.requestFocus();
        t.animate().scaleX(1.25f).scaleY(1.25f).setDuration(140).start();
        moveHint.setAlpha(0f);
        moveHint.setVisibility(View.VISIBLE);
        moveHint.animate().alpha(1f).setDuration(200).start();
    }

    private void endMove() {
        if (moving == null) return;
        View t = moving;
        moving = null;
        t.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140).start();
        moveHint.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> { if (moving == null) moveHint.setVisibility(View.INVISIBLE); }).start();
        List<String> order = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            Object tag = row.getChildAt(i).getTag();
            if (tag instanceof String) order.add((String) tag);
        }
        Photos.setAppOrder(this, order);
    }

    private void moveTile(int dir) {
        int i = row.indexOfChild(moving);
        int j = i + dir;
        if (i < 0 || j < 1 || j >= row.getChildCount()) return; // index 0 = Photos shortcut, stays first
        final View other = row.getChildAt(j);
        int oldA = moving.getLeft(), oldB = other.getLeft();
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        row.removeViewAt(i);
        row.addView(moving, j, lp);
        slideFrom(moving, oldA);
        slideFrom(other, oldB);
        moving.requestFocus();
        moving.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).start();
    }

    /** Smoothly slide a view from where it used to be to its new place in the row. */
    private void slideFrom(final View v, final int oldLeft) {
        v.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                v.getViewTreeObserver().removeOnPreDrawListener(this);
                float dx = oldLeft - v.getLeft();
                if (dx != 0f) {
                    v.setTranslationX(dx);
                    v.animate().translationX(0f).setDuration(190).start();
                }
                return true;
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving != null) {
            int k = e.getKeyCode();
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { if (down) moveTile(-1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { if (down) moveTile(1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                if (e.getAction() == KeyEvent.ACTION_UP) endMove();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------- lifecycle ----------------
    private boolean appsChanged;
    private final android.content.BroadcastReceiver pkgReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, Intent i) { appsChanged = true; }
    };

    @Override
    protected void onStart() {
        super.onStart();
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        refreshNet();
        try {
            cm.registerDefaultNetworkCallback(netCb);
            netRegistered = true;
        } catch (Exception ignored) { }
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        if (netRegistered) {
            try { cm.unregisterNetworkCallback(netCb); } catch (Exception ignored) { }
            netRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appsChanged) { appsChanged = false; buildRow(); }
        clock.removeCallbacks(tick);
        tick.run();
    }

    @Override
    protected void onPause() {
        endMove();
        clock.removeCallbacks(tick);
        super.onPause();
    }

    /** Home button pressed while we are already open: jump back to the start of the row. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        endMove();
        scroller.smoothScrollTo(0, 0);
        View first = row.getChildAt(0);
        if (first != null) first.requestFocus();
    }

    /** A launcher should never "go back" out of itself. */
    @Override
    public void onBackPressed() { }
}
