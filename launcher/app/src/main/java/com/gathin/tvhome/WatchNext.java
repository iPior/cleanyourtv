package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Continue watching (config.xml continue_watching): the programs other apps publish to the TV provider's Watch Next
 * table, read with READ_TV_LISTINGS (docs/watch-next.md). Reading and choosing live here, with no UI code.
 *
 * Without ACCESS_ALL_EPG_DATA the provider refuses a selection, so the whole table is read and filtered in code.
 * Every failure (no permission, no provider, a bad row) gives fewer rows, never a crash: the row then stays hidden.
 */
final class WatchNext {

    static final String TAG = "TvHomeWatchNext";
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";

    // TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_*: continue, next, new; watchlist is not "continue watching".
    static final int TYPE_CONTINUE = 0, TYPE_WATCHLIST = 3;
    /** Rows linger for months (docs/watch-next.md): older than this they are left out. */
    static final long MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000;
    /** A "continue" row this far through is finished, whatever the app left behind. */
    static final float DONE = 0.95f;
    /** Rows read per refresh; the provider holds a few dozen on a lived-in TV. */
    static final int MAX_ROWS = 200;

    static final class Program {
        final String pkg, title, subtitle, poster, intentUri;
        final int type;
        final long engaged, position, duration;

        Program(String pkg, String title, String subtitle, String poster, String intentUri, int type,
                long engaged, long position, long duration) {
            this.pkg = pkg;
            this.title = title;
            this.subtitle = subtitle;
            this.poster = poster;
            this.intentUri = intentUri;
            this.type = type;
            this.engaged = engaged;
            this.position = position;
            this.duration = duration;
        }

        /** 0..1 through the program, or -1 when the app gave no position (a "next episode" row). */
        float progress() {
            if (position <= 0 || duration <= 0) return -1f;
            return Math.min(1f, position / (float) duration);
        }

        /** Same title from the same app: an app that writes a row per episode shows once, its latest. */
        String key() {
            return pkg + '\u0000' + title;
        }

        boolean sameAs(Program o) {
            return o != null && key().equals(o.key()) && eq(subtitle, o.subtitle) && eq(poster, o.poster)
                    && eq(intentUri, o.intentUri) && position == o.position && duration == o.duration;
        }

        private static boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    /** A subtitle's short part for a line of its own, "S4 E3" of "S4 E3 · Richmond's Got Talent"; null when it has none. */
    static String episode(String subtitle) {
        if (subtitle == null) return null;
        int dot = subtitle.indexOf(" \u00B7 ");
        String part = (dot < 0 ? subtitle : subtitle.substring(0, dot)).trim();
        return part.isEmpty() ? null : part;
    }

    interface Installed {
        boolean has(String pkg);
    }

    private WatchNext() {
    }

    /**
     * The rows worth showing, newest first: not on the watchlist, engaged with in the last MAX_AGE_MS, a "continue"
     * row not finished, one per title, from an app that is still installed and enabled, at most limit.
     */
    static List<Program> select(List<Program> all, long now, int limit, Installed installed) {
        List<Program> rows = new ArrayList<>();
        for (Program p : all) {
            if (p == null || p.title == null || p.title.isEmpty() || p.pkg == null || p.intentUri == null) continue;
            if (p.type == TYPE_WATCHLIST) continue;
            if (p.engaged > 0 && now - p.engaged > MAX_AGE_MS) continue;
            if (p.type == TYPE_CONTINUE && p.progress() >= DONE) continue;
            rows.add(p);
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.engaged, a.engaged));
        List<Program> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Program p : rows) {
            if (out.size() >= limit) break;
            if (!seen.add(p.key())) continue;
            if (installed != null && !installed.has(p.pkg)) continue;
            out.add(p);
        }
        return out;
    }

    /**
     * Minutes' timeline: the rows with a position and a duration, shortest time left first, at most limit. A "next
     * episode" row, with no position, has no time left to show and is left out.
     */
    static List<Program> byTimeLeft(List<Program> rows, int limit) {
        List<Program> out = new ArrayList<>();
        for (Program p : rows) if (p.progress() > 0f) out.add(p);
        Collections.sort(out, (a, b) -> Long.compare(a.duration - a.position, b.duration - b.position));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Whole minutes left in a program with a position, at least 1; -1 without one. */
    static int minutesLeft(Program p) {
        if (p.progress() <= 0f) return -1;
        return (int) Math.max(1L, Math.round((p.duration - p.position) / 60000.0));
    }

    /** True when both lists would draw the same row. */
    static boolean same(List<Program> a, List<Program> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!a.get(i).sameAs(b.get(i))) return false;
        return true;
    }

    /** "S2 E5 · Episode title", or whichever parts the app filled; null with none. */
    static String subtitle(String season, String episode, String episodeTitle) {
        StringBuilder sb = new StringBuilder();
        if (season != null && !season.isEmpty()) sb.append('S').append(season);
        if (episode != null && !episode.isEmpty()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append('E').append(episode);
        }
        if (episodeTitle != null && !episodeTitle.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(episodeTitle);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Every Watch Next row the launcher may see. Call off the main thread. */
    static List<Program> query(Context context) {
        List<Program> out = new ArrayList<>();
        if (context.checkSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return out;
        try (Cursor c = context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,
                null, null, null, null)) {
            if (c == null) return out;
            int pkg = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
            int title = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
            int season = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
            int episode = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
            int episodeTitle = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE);
            int poster = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
            int thumb = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI);
            int intent = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
            int type = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE);
            int engaged = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);
            int position = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
            int duration = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
            int browsable = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_BROWSABLE);
            while (c.moveToNext() && out.size() < MAX_ROWS) {
                try {
                    if (browsable >= 0 && !c.isNull(browsable) && c.getInt(browsable) == 0) continue;
                    String art = str(c, poster);
                    if (art == null) art = str(c, thumb);
                    out.add(new Program(str(c, pkg), str(c, title),
                            subtitle(str(c, season), str(c, episode), str(c, episodeTitle)),
                            art, str(c, intent), type >= 0 && !c.isNull(type) ? c.getInt(type) : TYPE_CONTINUE,
                            num(c, engaged), num(c, position), num(c, duration)));
                } catch (RuntimeException e) {
                    Log.w(TAG, "skipped a row: " + e);
                }
            }
        } catch (RuntimeException e) {
            // SecurityException without the permission, IllegalArgumentException on a TV without the provider.
            Log.w(TAG, "query failed: " + e);
        }
        return out;
    }

    private static String str(Cursor c, int i) {
        if (i < 0 || c.isNull(i)) return null;
        String s = c.getString(i);
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static long num(Cursor c, int i) {
        return i < 0 || c.isNull(i) ? 0L : c.getLong(i);
    }

    /** The program's own intent, or null when the app wrote one Android cannot read. */
    static Intent intent(Program p) {
        try {
            Intent i = Intent.parseUri(p.intentUri, Intent.URI_INTENT_SCHEME);
            i.setSelector(null);   // no intent picks a different target through its selector
            return i;
        } catch (URISyntaxException e) {
            Log.w(TAG, "bad intent for " + p.pkg + ": " + e.getMessage());
            return null;
        }
    }

    /** Whether the poster can be opened without the network the launcher does not have (docs/watch-next.md). */
    static boolean localPoster(String uri) {
        if (uri == null) return false;
        String s = uri.toLowerCase(java.util.Locale.ROOT);
        return s.startsWith(ContentResolver.SCHEME_CONTENT + ":")
                || s.startsWith(ContentResolver.SCHEME_ANDROID_RESOURCE + ":");
    }

    /** A content:// or android.resource:// poster decoded at about widthPx wide, or null. Call off the main thread. */
    static Bitmap poster(Context context, String uri, int widthPx) {
        if (!localPoster(uri)) return null;
        Uri u = Uri.parse(uri);
        ContentResolver cr = context.getContentResolver();
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(u)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, widthPx);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;   // 1 GB TVs: half the memory, posters have no alpha
            try (InputStream in = cr.openInputStream(u)) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "poster failed for " + uri + ": " + e);
            return null;
        }
    }

    /** The largest power of two that keeps the decoded width at or above the target. */
    static int sampleSize(int width, int targetWidth) {
        int s = 1;
        if (targetWidth <= 0) return s;
        while (width / (s * 2) >= targetWidth) s *= 2;
        return s;
    }
}
