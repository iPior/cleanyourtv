package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * netflix_posters: the poster images Netflix puts in Watch Next, fetched over https from Netflix's own image server
 * (nflxso.net) and kept on the TV, so each one downloads once. No other address and no other app's pictures are
 * fetched; no retries, no polling, one image at a time on the poster thread. This is the launcher's only network use,
 * and it is off unless a theme or config.xml turns it on.
 */
final class NetflixPosters {
    private static final String TAG = "TvHomeNetflix";
    private static final int TIMEOUT_MS = 5000;
    /** Larger than any poster Netflix serves; anything bigger is not a poster and is dropped. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private NetflixPosters() {}

    /** Whether the launcher may fetch this address: https on nflxso.net or one of its subdomains, nothing else. */
    static boolean allowed(String uri) {
        if (uri == null) return false;
        try {
            java.net.URI u = new java.net.URI(uri);
            String host = u.getHost();
            // No user info or port tricks: the plain host only.
            if (!"https".equalsIgnoreCase(u.getScheme()) || host == null || u.getRawUserInfo() != null || u.getPort() != -1) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("nflxso.net") || host.endsWith(".nflxso.net");
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    /** The poster decoded at about widthPx wide: from the TV's copy, else fetched once and kept. Null on any failure. */
    static Bitmap poster(Context context, String uri, int widthPx) {
        if (!allowed(uri)) return null;
        File file = new File(new File(context.getCacheDir(), "netflix"), key(uri) + ".img");
        if (!file.exists() && !fetch(uri, file)) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                file.delete();   // not an image: fetch again next time rather than keep a bad file
                return null;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = WatchNext.sampleSize(bounds.outWidth, widthPx);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "poster too large: " + e);
            return null;
        }
    }

    /** One https GET to the allowed host, no redirects followed, into file. */
    private static boolean fetch(String uri, File file) {
        HttpURLConnection c = null;
        File part = new File(file.getPath() + ".part");
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return false;
            c = (HttpURLConnection) new URL(uri).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(false);   // a redirect could lead off nflxso.net
            c.setUseCaches(false);
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) return false;
            int total = 0;
            byte[] buf = new byte[16 * 1024];
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_BYTES) return false;
                    out.write(buf, 0, n);
                }
            }
            return part.renameTo(file);
        } catch (Exception e) {
            Log.w(TAG, "fetch failed: " + e.getClass().getSimpleName());
            return false;
        } finally {
            if (c != null) c.disconnect();
            if (part.exists()) part.delete();
        }
    }

    /** A file name for the address: its SHA-1, so nothing of the address itself lands on disk. */
    private static String key(String uri) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(uri.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(uri.hashCode());
        }
    }
}
