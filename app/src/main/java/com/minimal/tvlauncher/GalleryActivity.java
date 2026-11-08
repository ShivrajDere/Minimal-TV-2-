package com.minimal.tvlauncher;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryActivity extends BaseActivity {
    private File[] files = new File[0];
    private GridView grid;
    private TextView title, empty;
    private LinearLayout uploadBtn;
    private boolean focusSet;
    private FrameLayout viewer;
    private ImageView big;
    private int viewIndex;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final LruCache<String, Bitmap> cache = new LruCache<>(60);
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(40), dp(56), dp(24));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        title = new TextView(this);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        titles.addView(title);

        TextView hint = new TextView(this);
        hint.setText("OK to open  \u2022  hold OK to delete  \u2022  Back to return");
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(14);
        titles.addView(hint);

        // Upload lives here now (top right): send photos from your phone over Wi-Fi
        uploadBtn = new LinearLayout(this);
        uploadBtn.setOrientation(LinearLayout.HORIZONTAL);
        uploadBtn.setGravity(Gravity.CENTER_VERTICAL);
        uploadBtn.setPadding(dp(18), dp(8), dp(22), dp(8));
        uploadBtn.addView(new Ui.Icon(this, Ui.I_UPLOAD), new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView ut = new TextView(this);
        ut.setText("Upload");
        ut.setTextColor(0xFFFFFFFF);
        ut.setTextSize(18);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-2, -2);
        ulp.leftMargin = dp(8);
        uploadBtn.addView(ut, ulp);
        uploadBtn.setContentDescription("Upload photos from your phone");
        Ui.focusFx(uploadBtn, 28);
        uploadBtn.setOnClickListener(v -> startActivity(new Intent(this, UploadActivity.class)));
        header.addView(uploadBtn, new LinearLayout.LayoutParams(-2, -2));

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setHorizontalSpacing(dp(16));
        grid.setVerticalSpacing(dp(16));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setClipToPadding(false);
        grid.setPadding(dp(6), dp(24), dp(6), dp(24));
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x00000000);
        sel.setStroke(dp(3), 0xFFFFFFFF);
        sel.setCornerRadius(dp(12));
        grid.setSelector(sel);
        grid.setDrawSelectorOnTop(true);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> openViewer(pos));
        grid.setOnItemLongClickListener((p, v, pos, id) -> { confirmDelete(pos); return true; });
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        empty = new TextView(this);
        empty.setText("No photos yet.\nChoose “Upload” (top right) and send some from your phone.");
        empty.setTextColor(0xCCFFFFFF);
        empty.setTextSize(20);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new FrameLayout.LayoutParams(-1, -1));

        viewer = new FrameLayout(this);
        viewer.setBackgroundColor(0xFF000000);
        viewer.setVisibility(View.GONE);
        big = new ImageView(this);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewer.addView(big, new FrameLayout.LayoutParams(-1, -1));
        root.addView(viewer, new FrameLayout.LayoutParams(-1, -1));

        attachOverlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        files = Photos.list(this);
        title.setText("Photos  (" + files.length + ")");
        empty.setVisibility(files.length == 0 ? View.VISIBLE : View.GONE);
        grid.setVisibility(files.length == 0 ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
        if (!focusSet) {
            focusSet = true;
            (files.length == 0 ? uploadBtn : grid).requestFocus();
        }
    }

    // ---------- grid ----------
    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return files.length; }
        @Override public Object getItem(int i) { return files[i]; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            ImageView iv = (ImageView) convert;
            if (iv == null) {
                iv = new ImageView(GalleryActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundColor(0x33FFFFFF);
                iv.setLayoutParams(new AbsListView.LayoutParams(-1, dp(150)));
                iv.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
                    }
                });
                iv.setClipToOutline(true);
            }
            final File f = files[pos];
            final String key = f.getAbsolutePath();
            iv.setTag(key);
            Bitmap c = cache.get(key);
            if (c != null) {
                iv.setImageBitmap(c);
            } else {
                iv.setImageDrawable(null);
                final ImageView target = iv;
                io.execute(() -> {
                    final Bitmap bm = Photos.decode(f, 480, 270);
                    if (bm == null) return;
                    cache.put(key, bm);
                    target.post(() -> { if (key.equals(target.getTag())) target.setImageBitmap(bm); });
                });
            }
            return iv;
        }
    }

    // ---------- viewer ----------
    private void openViewer(int pos) {
        viewIndex = pos;
        showCurrent(false);
        viewer.setAlpha(0f);
        viewer.setVisibility(View.VISIBLE);
        viewer.animate().alpha(1f).setDuration(220).start();
    }

    private void closeViewer() {
        viewer.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            viewer.setVisibility(View.GONE);
            grid.setSelection(viewIndex);
        }).start();
    }

    private void step(int d) {
        if (files.length == 0) return;
        viewIndex = (viewIndex + d + files.length) % files.length;
        showCurrent(true);
    }

    private void showCurrent(final boolean animate) {
        final File f = files[viewIndex];
        final int idx = viewIndex;
        io.execute(() -> {
            final Bitmap bm = Photos.decode(f, 1920, 1080);
            big.post(() -> {
                if (idx != viewIndex || bm == null) return;
                big.setImageBitmap(bm);
                if (animate) {
                    big.setAlpha(0f);
                    big.animate().alpha(1f).setDuration(220).start();
                }
            });
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (viewer.getVisibility() == View.VISIBLE) {
            int k = e.getKeyCode();
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            if (k == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) closeViewer();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { if (down) step(1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { if (down) step(-1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                    || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void confirmDelete(final int pos) {
        final File f = files[pos];
        new AlertDialog.Builder(this)
                .setTitle("Delete this photo?")
                .setPositiveButton("Delete", (d, w) -> {
                    cache.remove(f.getAbsolutePath());
                    f.delete();
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
