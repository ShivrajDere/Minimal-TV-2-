package com.minimal.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Shared screen base: animated wallpaper behind everything + our volume overlay on top.
 * Volume keys are handled here with flags = 0, so Android's own volume panel is not shown
 * while one of the launcher's screens is in front.
 */
public abstract class BaseActivity extends Activity {
    protected FrameLayout root;
    protected BackgroundView bg;
    private VolumeOverlay vol;
    private AudioManager am;
    private long lastKeyShow;

    /**
     * Fallback: some TVs (Sony/Google TV) handle the remote's volume keys in the system and never
     * pass them to the app. The system still broadcasts the change, so we animate from that too.
     */
    private final BroadcastReceiver volReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (vol == null || am == null) return;
            int type = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
            if (type != AudioManager.STREAM_MUSIC) return;
            if (SystemClock.uptimeMillis() - lastKeyShow < 250) return; // key handler already showed it
            showVolume();
        }
    };

    private void showVolume() {
        vol.show(am.getStreamVolume(AudioManager.STREAM_MUSIC),
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                am.isStreamMute(AudioManager.STREAM_MUSIC));
    }

    protected int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        root = new FrameLayout(this);
        bg = new BackgroundView(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Call after adding your own content so the volume pill sits on top. */
    protected void attachOverlay() {
        vol = new VolumeOverlay(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(380), dp(64),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(44);
        root.addView(vol, lp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bg.reload();
        bg.setRunning(true);
        IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        f.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(volReceiver, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(volReceiver, f);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        bg.setRunning(false);
        try { unregisterReceiver(volReceiver); } catch (Exception ignored) { }
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && vol != null) {
                int dir = k == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                        : k == KeyEvent.KEYCODE_VOLUME_DOWN ? AudioManager.ADJUST_LOWER
                        : AudioManager.ADJUST_TOGGLE_MUTE;
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0); // 0 = no system UI
                } catch (SecurityException ignored) { }
                lastKeyShow = SystemClock.uptimeMillis();
                showVolume();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    protected static ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(-1, -1);
    }
}
