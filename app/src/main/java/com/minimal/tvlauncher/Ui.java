package com.minimal.tvlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Small UI helpers: focus animation + simple vector-style glyphs (no image assets needed). */
final class Ui {
    private Ui() {}

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Rounded highlight + smooth scale when the D-pad focuses a view. */
    static void focusFx(View v, int radiusDp) {
        final GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(v.getContext(), radiusDp));
        g.setColor(0x00FFFFFF);
        v.setBackground(g);
        v.setFocusable(true);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f)
                    .setDuration(160).start();
            g.setColor(has ? 0x30FFFFFF : 0x00FFFFFF);
            g.setStroke(has ? dp(view.getContext(), 2) : 0, 0x88FFFFFF);
        });
    }


    /** Flat white line icons for the top-right bar (no image assets needed). */
    static final int I_WIFI = 10, I_ETH = 11, I_SETTINGS = 12, I_LOOK = 13, I_UPLOAD = 14;

    static class Icon extends View {
        private int kind, level = 3;
        private boolean off;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        Icon(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        /** level = lit Wi-Fi arcs (1..3); off = draw dimmed with a slash. */
        void set(int kind, int level, boolean off) {
            if (this.kind == kind && this.level == level && this.off == off) return;
            this.kind = kind; this.level = level; this.off = off;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * .06f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xFFFFFFFF);
            p.setAlpha(255);
            if (kind == I_WIFI) {
                float oy = cy + s * .17f;
                for (int i = 1; i <= 3; i++) {
                    float rad = s * (0.04f + 0.12f * i);
                    p.setAlpha(!off && i <= level ? 255 : 70);
                    r.set(cx - rad, oy - rad, cx + rad, oy + rad);
                    cv.drawArc(r, -135, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(off ? 70 : 255);
                cv.drawCircle(cx, oy, s * .045f, p);
                if (off) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setAlpha(255);
                    cv.drawLine(cx - s * .26f, cy - s * .24f, cx + s * .26f, cy + s * .26f, p);
                }
            } else if (kind == I_ETH) {
                r.set(cx - s * .22f, cy - s * .2f, cx + s * .22f, cy + s * .06f);
                cv.drawRoundRect(r, s * .04f, s * .04f, p);
                for (int i = -1; i <= 1; i++)
                    cv.drawLine(cx + i * s * .1f, cy - s * .12f, cx + i * s * .1f, cy - s * .04f, p);
                cv.drawLine(cx, cy + s * .06f, cx, cy + s * .18f, p);
                cv.drawLine(cx - s * .2f, cy + s * .18f, cx + s * .2f, cy + s * .18f, p);
                cv.drawLine(cx - s * .2f, cy + s * .18f, cx - s * .2f, cy + s * .26f, p);
                cv.drawLine(cx + s * .2f, cy + s * .18f, cx + s * .2f, cy + s * .26f, p);
            } else if (kind == I_SETTINGS) {
                cv.drawCircle(cx, cy, s * .19f, p);
                cv.drawCircle(cx, cy, s * .07f, p);
                p.setStrokeWidth(s * .08f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    cv.drawLine(cx + cs * s * .23f, cy + sn * s * .23f,
                            cx + cs * s * .30f, cy + sn * s * .30f, p);
                }
            } else if (kind == I_LOOK) {
                cv.drawCircle(cx, cy, s * .25f, p);
                path.reset();
                r.set(cx - s * .25f, cy - s * .25f, cx + s * .25f, cy + s * .25f);
                path.addArc(r, 90, 180);
                path.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(path, p);
            } else { // I_UPLOAD
                cv.drawLine(cx, cy + s * .14f, cx, cy - s * .2f, p);
                path.reset();
                path.moveTo(cx - s * .14f, cy - s * .06f);
                path.lineTo(cx, cy - s * .2f);
                path.lineTo(cx + s * .14f, cy - s * .06f);
                cv.drawPath(path, p);
                cv.drawLine(cx - s * .24f, cy + s * .24f, cx + s * .24f, cy + s * .24f, p);
            }
        }
    }

    /** Glyph kinds for the launcher's own shortcuts. */
    static final int PHOTOS = 0, UPLOAD = 1, LOOK = 2;

    static class Glyph extends View {
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Path path = new Path();

        Glyph(Context c, int kind) {
            super(c);
            this.kind = kind;
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            // tile
            p.setStyle(Paint.Style.FILL);
            p.setColor(kind == PHOTOS ? 0xFF3D5AFE : kind == UPLOAD ? 0xFF00A884 : 0xFF8E44AD);
            r.set(0, 0, w, h);
            cv.drawRoundRect(r, s * .22f, s * .22f, p);
            p.setColor(0xFFFFFFFF);
            p.setStrokeWidth(s * .06f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            float cx = w / 2f, cy = h / 2f;
            if (kind == PHOTOS) {
                p.setStyle(Paint.Style.STROKE);
                r.set(cx - s * .26f, cy - s * .2f, cx + s * .26f, cy + s * .2f);
                cv.drawRoundRect(r, s * .04f, s * .04f, p);
                path.reset();
                path.moveTo(cx - s * .26f, cy + s * .14f);
                path.lineTo(cx - s * .08f, cy - s * .02f);
                path.lineTo(cx + s * .04f, cy + s * .08f);
                path.lineTo(cx + s * .12f, cy);
                path.lineTo(cx + s * .26f, cy + s * .14f);
                cv.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawCircle(cx + s * .12f, cy - s * .09f, s * .035f, p);
            } else if (kind == UPLOAD) {
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx, cy + s * .14f, cx, cy - s * .18f, p);
                path.reset();
                path.moveTo(cx - s * .13f, cy - s * .05f);
                path.lineTo(cx, cy - s * .18f);
                path.lineTo(cx + s * .13f, cy - s * .05f);
                cv.drawPath(path, p);
                cv.drawLine(cx - s * .22f, cy + s * .22f, cx + s * .22f, cy + s * .22f, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                cv.drawCircle(cx, cy, s * .2f, p);
                path.reset();
                r.set(cx - s * .2f, cy - s * .2f, cx + s * .2f, cy + s * .2f);
                path.addArc(r, 90, 180);
                path.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(path, p);
            }
        }
    }
}
