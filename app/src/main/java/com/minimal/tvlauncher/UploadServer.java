package com.minimal.tvlauncher;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP server for uploading photos from a phone on the same Wi-Fi.
 * Local network only. Started when the Upload screen opens, stopped when it closes.
 * A random 4-digit PIN (shown on the TV) is required for every upload.
 */
final class UploadServer {
    interface Listener { void onPhotoSaved(String name); }

    private static final long MAX_BYTES = 40L * 1024 * 1024;

    private final Context ctx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private ServerSocket server;
    private volatile boolean stopped;
    private int fails;

    final String pin = String.format("%04d", new SecureRandom().nextInt(10000));
    int port;

    UploadServer(Context c, Listener l) {
        ctx = c.getApplicationContext();
        listener = l;
    }

    boolean start() {
        for (int p = 8080; p < 8090; p++) {
            try {
                server = new ServerSocket(p);
                port = p;
                break;
            } catch (IOException ignored) { }
        }
        if (server == null) return false;
        Thread t = new Thread(() -> {
            while (!stopped) {
                try {
                    final Socket s = server.accept();
                    pool.execute(() -> handle(s));
                } catch (IOException e) {
                    break;
                }
            }
        }, "upload-server");
        t.setDaemon(true);
        t.start();
        return true;
    }

    void stop() {
        stopped = true;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }

    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> en = ni.getInetAddresses();
                while (en.hasMoreElements()) {
                    InetAddress a = en.nextElement();
                    if (a instanceof java.net.Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------------- request handling ----------------
    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(20000);
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();

            String reqLine = readLine(in);
            if (reqLine == null) return;
            String[] parts = reqLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], target = parts[1];

            long len = 0;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int c = line.indexOf(':');
                if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase("content-length")) {
                    try { len = Long.parseLong(line.substring(c + 1).trim()); } catch (NumberFormatException ignored) { }
                }
            }

            String path = target, query = "";
            int q = target.indexOf('?');
            if (q >= 0) { path = target.substring(0, q); query = target.substring(q + 1); }

            if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                send(out, 200, "text/html; charset=utf-8", readAsset("upload.html"));
            } else if (method.equals("POST") && path.equals("/upload")) {
                upload(in, out, query, len);
            } else {
                send(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    private void upload(InputStream in, OutputStream out, String query, long len) throws IOException {
        String name = "photo.jpg", givenPin = "";
        for (String kv : query.split("&")) {
            int e = kv.indexOf('=');
            if (e < 0) continue;
            String k = kv.substring(0, e), v = URLDecoder.decode(kv.substring(e + 1), "UTF-8");
            if (k.equals("name")) name = v;
            else if (k.equals("pin")) givenPin = v;
        }
        synchronized (this) {
            if (fails >= 10) { send(out, 429, "text/plain", "Too many wrong PINs".getBytes()); return; }
            if (!givenPin.equals(pin)) {
                fails++;
                send(out, 403, "text/plain", "Wrong PIN".getBytes());
                return;
            }
        }
        String lower = name.toLowerCase();
        if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))) {
            send(out, 415, "text/plain", "Only JPG, PNG or WEBP".getBytes());
            return;
        }
        if (len <= 0 || len > MAX_BYTES) {
            send(out, 413, "text/plain", "File too large (max 40 MB)".getBytes());
            return;
        }
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 60) safe = safe.substring(safe.length() - 60);
        final File dest = new File(Photos.dir(ctx), System.currentTimeMillis() + "_" + safe);
        long remaining = len;
        byte[] buf = new byte[16384];
        try (FileOutputStream fo = new FileOutputStream(dest)) {
            while (remaining > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) break;
                fo.write(buf, 0, n);
                remaining -= n;
            }
        }
        if (remaining > 0) { dest.delete(); send(out, 400, "text/plain", "Upload interrupted".getBytes()); return; }
        main.post(() -> listener.onPhotoSaved(dest.getName()));
        send(out, 200, "text/plain", "OK".getBytes());
    }

    private byte[] readAsset(String n) throws IOException {
        try (InputStream is = ctx.getAssets().open(n)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int r;
            while ((r = is.read(b)) > 0) bo.write(b, 0, r);
            return bo.toByteArray();
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        return (c == -1 && sb.length() == 0) ? null : sb.toString();
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + code + " X\r\nContent-Type: " + type
                + "\r\nContent-Length: " + body.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
