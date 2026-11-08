package com.minimal.tvlauncher;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class UploadActivity extends BaseActivity {
    private UploadServer server;
    private TextView status, count;
    private int received;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        col.addView(text("Send photos from your phone", 30, 0xFFFFFFFF));
        col.addView(text("Connect your phone to the same Wi-Fi, then open this address:", 18, 0xBBFFFFFF), pad(10));
        status = text("", 44, 0xFFFFFFFF);
        col.addView(status, pad(18));
        col.addView(text("Enter this PIN on the page", 18, 0xBBFFFFFF), pad(26));
        TextView pinView = text("", 56, 0xFF9DB4FF);
        pinView.setLetterSpacing(0.3f);
        col.addView(pinView, pad(6));
        count = text("", 18, 0xFF5BE3A8);
        col.addView(count, pad(26));
        col.addView(text("Press Back when you are done", 15, 0x88FFFFFF), pad(18));

        server = new UploadServer(this, name -> {
            received++;
            count.setText(received + (received == 1 ? " photo received" : " photos received"));
        });
        boolean ok = server.start();
        String ip = UploadServer.localIp();
        if (!ok) {
            status.setText("Could not start the upload page");
        } else if (ip == null) {
            status.setText("TV is not connected to Wi-Fi / network");
            status.setTextSize(26);
        } else {
            status.setText("http://" + ip + ":" + server.port);
            pinView.setText(server.pin);
        }
        attachOverlay();
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout.LayoutParams pad(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    /** Leaving the screen (Home, another app) closes it, which also stops the server. */
    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }
}
