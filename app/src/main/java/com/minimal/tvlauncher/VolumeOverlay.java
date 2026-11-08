package com.minimal.tvlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Our own volume pill: pops in with a bounce, the bar fills smoothly with a glowing knob,
 * the number counts up/down, the speaker waves pulse on every press, then it fades out.
 */
public class VolumeOverlay extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path icon = new Path();
    private final RectF r = new RectF();
    private final float d;

    private float shown;      // animated bar fraction 0..1
    private float target;
    private float pulse = 1f; // 0..1 wave pulse, restarted on every press
    private boolean muted;
    private boolean visible;
    private ValueAnimator barAnim, pulseAnim;

    private final Runnable hide = () -> {
        visible = false;
        animate().alpha(0f).translationY(14 * getResources().getDisplayMetrics().density).scaleX(0.96f).scaleY(0.96f).setDuration(240)
                .withEndAction(() -> { if (!visible) setVisibility(GONE); }).start();
    };

    public VolumeOverlay(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setVisibility(GONE);
        bg.setColor(0xE60E1218);
        fill.setColor(0xFFFFFFFF);
        glow.setColor(0x55FFFFFF);
        track.setColor(0x33FFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(2.5f * d);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(20 * d);
        text.setTextAlign(Paint.Align.RIGHT);
    }

    public void show(int level, int max, boolean isMuted) {
        muted = isMuted;
        target = (muted || max <= 0) ? 0f : level / (float) max;

        if (barAnim != null) barAnim.cancel();
        barAnim = ValueAnimator.ofFloat(shown, target);
        barAnim.setDuration(220);
        barAnim.setInterpolator(new DecelerateInterpolator());
        barAnim.addUpdateListener(a -> { shown = (float) a.getAnimatedValue(); invalidate(); });
        barAnim.start();

        if (pulseAnim != null) pulseAnim.cancel();
        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(420);
        pulseAnim.addUpdateListener(a -> { pulse = (float) a.getAnimatedValue(); invalidate(); });
        pulseAnim.start();

        removeCallbacks(hide);
        animate().cancel(); // must happen before we touch visibility (a fade-out may be running)
        if (!visible) {
            visible = true;
            setVisibility(VISIBLE);
            setAlpha(0f);
            setTranslationY(28 * d);
            setScaleX(0.88f);
            setScaleY(0.88f);
            animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                    .setDuration(300).setInterpolator(new OvershootInterpolator(1.3f)).start();
        } else {
            // already on screen: small "bump" so every key press visibly reacts
            setAlpha(1f);
            setTranslationY(0);
            setScaleX(1.045f);
            setScaleY(1.045f);
            animate().scaleX(1f).scaleY(1f).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        postDelayed(hide, 1800);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hide);
        if (barAnim != null) barAnim.cancel();
        if (pulseAnim != null) pulseAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, h / 2f, h / 2f, bg);

        // speaker icon
        float s = 26 * d, x = 22 * d, cy = h / 2f, y = cy - s / 2f;
        icon.reset();
        icon.moveTo(x, y + s * .35f);
        icon.lineTo(x + s * .28f, y + s * .35f);
        icon.lineTo(x + s * .55f, y + s * .12f);
        icon.lineTo(x + s * .55f, y + s * .88f);
        icon.lineTo(x + s * .28f, y + s * .65f);
        icon.lineTo(x, y + s * .65f);
        icon.close();
        cv.drawPath(icon, fill);
        if (muted || shown <= 0.001f) {
            cv.drawLine(x + s * .72f, cy - s * .18f, x + s * 1.0f, cy + s * .18f, stroke);
            cv.drawLine(x + s * .72f, cy + s * .18f, x + s * 1.0f, cy - s * .18f, stroke);
        } else {
            // waves swell outward and settle on each press
            float swell = 1f + 0.35f * (float) Math.sin(Math.PI * pulse);
            for (int i = 1; i <= 3; i++) {
                if (i == 2 && shown < 0.34f) break;
                if (i == 3 && shown < 0.67f) break;
                float rad = s * (0.16f + 0.15f * i) * swell;
                stroke.setAlpha(i == 1 ? 255 : 200);
                r.set(x + s * .55f - rad, cy - rad, x + s * .55f + rad, cy + rad);
                cv.drawArc(r, -45, 90, false, stroke);
            }
            stroke.setAlpha(255);
        }

        // bar
        float bx = x + s + 22 * d, ex = w - 84 * d, bh = 8 * d;
        r.set(bx, cy - bh / 2f, ex, cy + bh / 2f);
        cv.drawRoundRect(r, bh / 2f, bh / 2f, track);
        float fx = bx + Math.max(bh, (ex - bx) * shown);
        r.set(bx, cy - bh / 2f, fx, cy + bh / 2f);
        cv.drawRoundRect(r, bh / 2f, bh / 2f, fill);
        if (!muted && shown > 0.001f) {
            float kr = bh * (0.95f + 0.5f * (float) Math.sin(Math.PI * pulse));
            cv.drawCircle(fx - bh / 2f, cy, kr * 1.7f, glow);
            cv.drawCircle(fx - bh / 2f, cy, kr, fill);
        }

        int number = muted ? 0 : Math.round(100f * shown);
        cv.drawText(muted ? "MUTE" : String.valueOf(number), w - 20 * d, cy + 7 * d, text);
    }
}
