package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.io.File;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight animated wallpaper (about 30 fps, paused when the screen is not visible).
 * Modes: 0 = Aurora (drifting glows), 1 = Bokeh (floating lights), 2 = Photos (slideshow with slow zoom).
 */
public class BackgroundView extends View {
    public static final int MODE_AURORA = 0, MODE_BOKEH = 1, MODE_PHOTOS = 2;
    public static final String[] MODE_NAMES = {"Aurora", "Bokeh", "Photos"};

    public static final String[] THEME_NAMES = {"Midnight", "Forest", "Sunset", "Mono", "Ocean"};
    // base, mid, accent
    static final int[][] THEMES = {
            {0xFF0B1020, 0xFF1B2A6B, 0xFF6C3BD1},
            {0xFF08171A, 0xFF0F5C4F, 0xFF3BD1A0},
            {0xFF1E0A10, 0xFF8A2C3B, 0xFFFF9A5A},
            {0xFF0B0B0B, 0xFF2A2A2A, 0xFF7A7A7A},
            {0xFF061826, 0xFF0B5C8A, 0xFF3BC4FF},
    };

    private static final long SLIDE_MS = 14000, FADE_MS = 2500;

    private int theme, mode;
    private boolean running;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private RadialGradient[] blobs;
    private float blobR;

    // bokeh
    private final float[][] dots = new float[22][5]; // x, y, r, speed, phase

    // photos
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private File[] files = new File[0];
    private int index;
    private Bitmap cur, next;
    private long slideStart;
    private boolean loading;

    public BackgroundView(Context c) {
        super(c);
        Random r = new Random(7);
        for (float[] d : dots) {
            d[0] = r.nextFloat(); d[1] = r.nextFloat();
            d[2] = 0.02f + r.nextFloat() * 0.05f;
            d[3] = 0.01f + r.nextFloat() * 0.02f;
            d[4] = r.nextFloat() * 6.28f;
        }
        reload();
    }

    /** Re-read theme / mode from settings. */
    public void reload() {
        theme = Math.min(Photos.theme(getContext()), THEMES.length - 1);
        mode = Photos.mode(getContext());
        files = Photos.list(getContext());
        index = 0;
        cur = next = null;
        slideStart = SystemClock.uptimeMillis();
        blobs = null;
        if (mode == MODE_PHOTOS && files.length > 0) requestNext(true);
        invalidate();
    }

    public void setRunning(boolean r) {
        running = r;
        if (r) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        blobs = null;
    }

    private void buildBlobs() {
        int[] c = THEMES[theme];
        blobR = Math.max(getWidth(), getHeight()) * 0.55f;
        blobs = new RadialGradient[3];
        int[] cols = {c[2], c[1], c[2]};
        for (int i = 0; i < 3; i++) {
            int col = cols[i];
            int a = Color.argb(i == 1 ? 200 : 150, Color.red(col), Color.green(col), Color.blue(col));
            int z = Color.argb(0, Color.red(col), Color.green(col), Color.blue(col));
            blobs[i] = new RadialGradient(0, 0, blobR, a, z, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;
        int[] c = THEMES[theme];
        cv.drawColor(c[0]);

        boolean photoDrawn = false;
        if (mode == MODE_PHOTOS) photoDrawn = drawPhotos(cv, now, w, h);
        if (!photoDrawn) {
            if (mode == MODE_BOKEH) drawBokeh(cv, t, w, h, c);
            else drawAurora(cv, t, w, h);
        }
        // gentle dim so text stays readable on any wallpaper
        cv.drawColor(photoDrawn ? 0x66000000 : 0x22000000);

        if (running) postInvalidateDelayed(33);
    }

    private void drawAurora(Canvas cv, float t, int w, int h) {
        if (blobs == null) buildBlobs();
        float[][] p = {
                {0.30f + 0.22f * (float) Math.sin(t * 0.17f), 0.35f + 0.20f * (float) Math.cos(t * 0.13f)},
                {0.70f + 0.20f * (float) Math.cos(t * 0.11f), 0.60f + 0.22f * (float) Math.sin(t * 0.15f)},
                {0.50f + 0.28f * (float) Math.sin(t * 0.09f + 2f), 0.80f + 0.12f * (float) Math.cos(t * 0.19f)},
        };
        for (int i = 0; i < 3; i++) {
            cv.save();
            cv.translate(p[i][0] * w, p[i][1] * h);
            paint.setShader(blobs[i]);
            cv.drawCircle(0, 0, blobR, paint);
            cv.restore();
        }
        paint.setShader(null);
    }

    private void drawBokeh(Canvas cv, float t, int w, int h, int[] c) {
        drawAurora(cv, t * 0.5f, w, h);
        paint.setStyle(Paint.Style.FILL);
        for (float[] d : dots) {
            float y = 1.1f - ((d[1] + t * d[3]) % 1.2f);
            float x = d[0] + 0.03f * (float) Math.sin(t * 0.4f + d[4]);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.8f + d[4]);
            int col = c[2];
            paint.setColor(Color.argb((int) (20 + 40 * tw), Color.red(col), Color.green(col), Color.blue(col)));
            cv.drawCircle(x * w, y * h, d[2] * w, paint);
        }
    }

    // ---------- photo slideshow ----------
    private boolean drawPhotos(Canvas cv, long now, int w, int h) {
        if (files.length == 0 || cur == null) return false;
        long el = now - slideStart;
        if (el >= SLIDE_MS && next != null) {
            cur = next; next = null; slideStart = now; el = 0;
            requestNext(false);
        }
        drawCover(cv, cur, Math.min(1f, el / (float) SLIDE_MS), 255, w, h);
        if (next != null && el > SLIDE_MS - FADE_MS) {
            float a = (el - (SLIDE_MS - FADE_MS)) / (float) FADE_MS;
            drawCover(cv, next, 0f, (int) (255 * Math.min(1f, a)), w, h);
        }
        return true;
    }

    private void drawCover(Canvas cv, Bitmap b, float p, int alpha, int w, int h) {
        float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight()) * (1f + 0.10f * p);
        matrix.setScale(s, s);
        matrix.postTranslate((w - b.getWidth() * s) / 2f - p * w * 0.02f, (h - b.getHeight() * s) / 2f);
        bmpPaint.setAlpha(alpha);
        cv.drawBitmap(b, matrix, bmpPaint);
    }

    private void requestNext(boolean first) {
        if (loading || files.length == 0) return;
        loading = true;
        final File f = files[index % files.length];
        index++;
        final int tw = Math.max(getWidth(), 1280), th = Math.max(getHeight(), 720);
        io.execute(() -> {
            final Bitmap b = Photos.decode(f, tw, th);
            post(() -> {
                loading = false;
                if (b == null) { if (files.length > 1) requestNext(first); return; }
                if (first || cur == null) { cur = b; slideStart = SystemClock.uptimeMillis(); requestNext(false); }
                else next = b;
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        io.shutdownNow();
    }
}
