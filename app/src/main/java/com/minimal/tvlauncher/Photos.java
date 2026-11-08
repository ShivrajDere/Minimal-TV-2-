package com.minimal.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Photo storage (app-private, no permissions needed) + small settings helper. */
final class Photos {
    private Photos() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File[] list(Context c) {
        File[] fs = dir(c).listFiles((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
        });
        if (fs == null) return new File[0];
        Arrays.sort(fs, Comparator.comparingLong(File::lastModified).reversed());
        return fs;
    }

    /** Decode a downscaled bitmap so large photos never exhaust TV memory. */
    static Bitmap decode(File f, int reqW, int reqH) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- settings ----
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("minimal_tv", Context.MODE_PRIVATE);
    }

    static int theme(Context c) { return sp(c).getInt("theme", 0); }
    static int mode(Context c) { return sp(c).getInt("mode", 0); }
    static void setTheme(Context c, int v) { sp(c).edit().putInt("theme", v).apply(); }
    static void setMode(Context c, int v) { sp(c).edit().putInt("mode", v).apply(); }

    /** Saved home-screen order of apps (package names, first = leftmost). */
    static List<String> appOrder(Context c) {
        List<String> out = new ArrayList<>();
        String s = sp(c).getString("app_order", "");
        if (s != null && !s.isEmpty()) out.addAll(Arrays.asList(s.split(",")));
        return out;
    }

    static void setAppOrder(Context c, List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) { if (sb.length() > 0) sb.append(','); sb.append(p); }
        sp(c).edit().putString("app_order", sb.toString()).apply();
    }
}
