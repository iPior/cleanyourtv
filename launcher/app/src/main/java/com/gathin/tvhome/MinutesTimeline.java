package com.gathin.tvhome;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minutes' timeline, in place of the Continue watching row: up to five programs, shortest time left first, each a row
 * with its picture, title, "App · S1 E6 · 18 min left" and a bar from NOW to where it would end, on an axis of the
 * next two hours. The same programs as the row (WatchNext), less the "next episode" ones, which have no time left.
 * Up and Down move through the rows; the host takes focus beyond them (MainActivity.applyMinutes).
 */
final class MinutesTimeline extends FrameLayout {

    interface Host {
        /** A row took focus. */
        void onTimelineFocused(View row, WatchNext.Program program);

        /** Up from the first row, or Left from any: to the buttons in the clock column. */
        void onTimelineLeave();

        /** Down from the last row: to the dock. */
        void onTimelineDown();
    }

    private static final int MAX_ROWS = 5;
    /** The axis: two hours from now, marked every half hour. */
    private static final int SPAN_MIN = 120, STEP_MIN = 30;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();

    /** Minutes' colours (MainActivity): chalk, muted lilac grey, mint, lilac, the faint lines, the thumbnail's night. */
    private static final int TEXT = 0xFFECE8F4, MUTE = 0xFF8A84A0, MINT = 0xFF7BE0B5, LILAC = 0xFFA898FF,
            LINE = 0x17ECE8F4, DEEP = 0xFF1E1B2E;

    private final Activity activity;
    private final Host host;
    private final float density;
    private final boolean animations;
    private final Typeface titleFace, metaFace, axisFace;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<TextView> axisLabels = new ArrayList<>();
    private final List<View> axisLines = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private List<WatchNext.Program> programs = Collections.emptyList();
    private boolean netflixPosters;
    private int generation;
    private int lastIndex = -1;
    /** The rows' left and right edges on screen, set by place(). */
    private int left, right;
    private boolean wanted = true;

    MinutesTimeline(Activity activity, Host host, boolean animations, Typeface titleFace, Typeface metaFace,
                    Typeface axisFace) {
        super(activity);
        this.activity = activity;
        this.host = host;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.animations = animations;
        this.titleFace = titleFace;
        this.metaFace = metaFace;
        this.axisFace = axisFace;
        setClipChildren(false);
        setClipToPadding(false);
        setVisibility(GONE);
        for (int k = 0; k <= SPAN_MIN / STEP_MIN; k++) {
            boolean now = k == 0;
            View line = new View(activity);
            line.setBackgroundColor(now ? MINT : LINE);
            if (now) line.setAlpha(0.8f);
            addView(line);
            axisLines.add(line);
            TextView label = new TextView(activity);
            label.setTypeface(axisFace);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            label.setTextColor(now ? MINT : MUTE);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setSingleLine(true);
            label.setIncludeFontPadding(false);
            addView(label);
            axisLabels.add(label);
        }
        axisLabels.get(0).setText(R.string.minutes_now);
    }

    void setNetflixPosters(boolean on) {
        netflixPosters = on;
    }

    // ---- data ------------------------------------------------------------------------------------

    /** Reads Watch Next again (onResume), asking once for the permission, as the row does. */
    void refresh() {
        if (activity.checkSelfPermission(WatchNext.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{WatchNext.PERMISSION}, 0);
            apply(Collections.emptyList(), ++generation);
            return;
        }
        int gen = ++generation;
        Context app = activity.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        LOADER.execute(() -> {
            List<WatchNext.Program> all = WatchNext.query(app);
            List<WatchNext.Program> shown = WatchNext.byTimeLeft(WatchNext.select(all, System.currentTimeMillis(),
                    WatchNext.MAX_ROWS,
                    pkg -> pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null),
                    MAX_ROWS);
            android.util.Log.i(WatchNext.TAG, all.size() + " Watch Next rows visible, " + shown.size() + " on the timeline");
            main.post(() -> apply(shown, gen));
        });
    }

    private void apply(List<WatchNext.Program> shown, int gen) {
        if (gen != generation || activity.isDestroyed()) return;
        if (WatchNext.same(shown, programs)) return;
        boolean hadFocus = hasFocus();
        programs = shown;
        for (Row r : rows) removeView(r.view);
        rows.clear();
        for (int i = 0; i < programs.size(); i++) rows.add(new Row(programs.get(i), i));
        if (lastIndex >= rows.size()) lastIndex = rows.size() - 1;
        layoutAll();
        updateShown();
        if (hadFocus && !focus()) host.onTimelineDown();
    }

    // ---- placement -------------------------------------------------------------------------------

    /** The rows' left and right edges on screen (root px); the rest follows the mock-up's measures. */
    void place(int left, int right) {
        if (left == this.left && right == this.right) return;
        this.left = left;
        this.right = right;
        layoutAll();
    }

    private int px(float dp) {
        return Math.round(dp * density);
    }

    /** Where NOW is: under the rows' bars, which start 88 dp into the row. */
    private int nowX() {
        return left + px(88);
    }

    /** Pixels per minute: two hours from NOW to 20 dp short of the rows' right edge. */
    private float ppm() {
        return (right - px(20) - nowX()) / (float) SPAN_MIN;
    }

    private void layoutAll() {
        if (right <= left) return;
        int labelW = px(80);
        for (int k = 0; k < axisLabels.size(); k++) {
            int x = Math.round(nowX() + k * STEP_MIN * ppm());
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(labelW, ViewGroup.LayoutParams.WRAP_CONTENT);
            ll.leftMargin = x - labelW / 2;
            ll.topMargin = px(56);
            axisLabels.get(k).setLayoutParams(ll);
            int w = k == 0 ? px(1) : Math.max(1, Math.round(density / 2));
            FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(k == 0 ? Math.max(2, px(1)) : w, px(287));
            gl.leftMargin = x - gl.width / 2;
            gl.topMargin = px(75);
            axisLines.get(k).setLayoutParams(gl);
        }
        for (Row r : rows) r.layout();
        tick();
    }

    /** The axis's clock times, each minute. */
    void tick() {
        java.text.DateFormat format = android.text.format.DateFormat.getTimeFormat(activity);
        long now = System.currentTimeMillis();
        for (int k = 1; k < axisLabels.size(); k++) {
            axisLabels.get(k).setText(format.format(new java.util.Date(now + k * STEP_MIN * 60000L)));
        }
    }

    /** Shown while the dock or the clock column is in view, hidden while the grid below is (MainActivity). */
    void setWanted(boolean on) {
        wanted = on;
        updateShown();
    }

    private void updateShown() {
        boolean show = wanted && !rows.isEmpty() && right > left;
        if (show == (getVisibility() == VISIBLE) && (!show || getAlpha() == 1f)) return;
        animate().cancel();
        if (show) {
            setVisibility(VISIBLE);
            if (animations) animate().alpha(1f).setDuration(220).start(); else setAlpha(1f);
        } else if (animations) {
            animate().alpha(0f).setDuration(140).withEndAction(() -> {
                if (!wanted || rows.isEmpty()) setVisibility(GONE);
            }).start();
        } else {
            setAlpha(0f);
            setVisibility(GONE);
        }
    }

    // ---- focus -----------------------------------------------------------------------------------

    /** The row last focused, else the bottom one. False when there is nothing to focus. */
    boolean focus() {
        if (getVisibility() != VISIBLE || rows.isEmpty()) return false;
        int i = lastIndex >= 0 ? lastIndex : rows.size() - 1;
        return rows.get(Math.min(i, rows.size() - 1)).view.requestFocus();
    }

    boolean owns(View v) {
        for (Row r : rows) if (r.view == v) return true;
        return false;
    }

    // ---- one row ---------------------------------------------------------------------------------

    private final class Row {
        final WatchNext.Program program;
        final int index;
        final FrameLayout view;
        final FrameLayout thumb;
        final TextView title, meta;
        final View marker, bar;
        final GradientDrawable fill, barShape;

        Row(WatchNext.Program p, int index) {
            this.program = p;
            this.index = index;
            Context c = activity;
            view = new FrameLayout(c);
            view.setFocusable(true);
            view.setClickable(true);
            view.setContentDescription(p.subtitle == null ? p.title : p.title + ", " + p.subtitle);
            fill = new GradientDrawable();
            fill.setCornerRadius(px(8));
            fill.setColor(LILAC & 0x00FFFFFF | 0x1A000000);
            fill.setAlpha(0);
            view.setBackground(fill);

            marker = new View(c);
            GradientDrawable m = new GradientDrawable();
            m.setCornerRadius(px(1));
            m.setColor(MINT);
            marker.setBackground(m);
            marker.setAlpha(0f);
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(2), px(32), Gravity.CENTER_VERTICAL);
            view.addView(marker, ml);

            thumb = new FrameLayout(c);
            thumb.setClipToOutline(true);
            thumb.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(4));
                }
            });
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(px(68), px(38));
            tl.leftMargin = px(9);
            tl.topMargin = px(6);
            view.addView(thumb, tl);
            showPicture(WatchRow.cardPoster(p));
            if (WatchRow.cardPoster(p) == null && p.poster != null) {
                WatchRow.poster(c.getApplicationContext(), p.poster, px(140), netflixPosters, b -> {
                    if (!activity.isDestroyed()) showPicture(b);
                });
            }

            title = text(titleFace, 15f, TEXT);
            title.setText(p.title);
            FrameLayout.LayoutParams til = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            til.leftMargin = px(97);
            til.rightMargin = px(16);
            til.topMargin = px(5);
            view.addView(title, til);

            meta = text(metaFace, 10f, MUTE);
            meta.setText(metaLine(p));
            FrameLayout.LayoutParams mel = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mel.leftMargin = px(97);
            mel.rightMargin = px(16);
            mel.topMargin = px(25);
            view.addView(meta, mel);

            bar = new View(c);
            barShape = new GradientDrawable();
            barShape.setCornerRadius(px(2.5f));
            barShape.setColor(LILAC);
            bar.setBackground(barShape);
            bar.setAlpha(0.55f);
            view.addView(bar, new FrameLayout.LayoutParams(1, px(5)));

            view.setOnFocusChangeListener((v, has) -> {
                focusLook(has);
                if (has) {
                    lastIndex = index;
                    host.onTimelineFocused(v, program);
                }
            });
            view.setOnClickListener(v -> WatchRow.open(activity, program));
            view.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (index > 0) rows.get(index - 1).view.requestFocus(); else host.onTimelineLeave();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (index < rows.size() - 1) rows.get(index + 1).view.requestFocus(); else host.onTimelineDown();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        host.onTimelineLeave();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;
                    default:
                        return false;
                }
            });
            addView(view);
        }

        private TextView text(Typeface face, float sp, int color) {
            TextView t = new TextView(activity);
            t.setTypeface(face);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            t.setTextColor(color);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(false);
            return t;
        }

        /** The row on screen: 50 dp high, 56 dp apart from 80 dp down, and its bar NOW to the program's end. */
        void layout() {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(right - left, px(50));
            lp.leftMargin = left;
            lp.topMargin = px(80) + index * px(56);
            view.setLayoutParams(lp);
            int minutes = Math.min(SPAN_MIN, Math.max(1, WatchNext.minutesLeft(program)));
            FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) bar.getLayoutParams();
            bl.width = Math.max(px(5), Math.round(minutes * ppm()));
            bl.leftMargin = nowX() - left;
            bl.topMargin = px(41);
            bar.setLayoutParams(bl);
        }

        /** Focused: the soft lilac fill, the mint marker at the left and a mint bar, as the mock-up's .item.on. */
        void focusLook(boolean on) {
            int ms = animations ? 200 : 0;
            ObjectAnimator.ofInt(fill, "alpha", fill.getAlpha(), on ? 255 : 0).setDuration(ms).start();
            marker.animate().alpha(on ? 1f : 0f).setDuration(ms).start();
            barShape.setColor(on ? MINT : LILAC);
            bar.animate().alpha(on ? 1f : 0.55f).setDuration(ms).start();
        }

        /**
         * The thumbnail, by the Feature rules: a wide picture fills it; a tall (2:3) one stands upright in the middle
         * over a glow in its own colour; with none, the app's banner small over a glow in the app's colour.
         */
        void showPicture(Bitmap poster) {
            if (poster != null && blank(poster)) poster = null;
            thumb.removeAllViews();
            ImageView art = new ImageView(activity);
            if (poster != null && poster.getWidth() >= poster.getHeight()) {
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageBitmap(poster);
                thumb.setBackground(new ColorDrawable(DEEP));
                thumb.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                return;
            }
            Drawable picture;
            if (poster != null) {
                picture = new android.graphics.drawable.BitmapDrawable(getResources(), poster);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                picture = banner(program.pkg);
                art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            int colour = picture == null ? LILAC : AppAdapter.vividColor(picture);
            thumb.setBackground(glow(colour == 0 ? LILAC : colour));
            if (picture == null) return;
            art.setImageDrawable(picture);
            FrameLayout.LayoutParams al = poster != null
                    ? new FrameLayout.LayoutParams(Math.round(px(38) * 2f / 3f), px(38), Gravity.CENTER)
                    : new FrameLayout.LayoutParams(px(46), px(26), Gravity.CENTER);
            if (poster != null) art.setElevation(px(3));
            thumb.addView(art, al);
        }

        private Drawable banner(String pkg) {
            PackageManager pm = activity.getPackageManager();
            try {
                Drawable d = pm.getApplicationBanner(pkg);
                return d != null ? d : pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }

        /** The night of the thumbnail with a soft glow of colour at its middle. */
        private Drawable glow(int colour) {
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(px(40));
            g.setColors(new int[]{colour & 0x00FFFFFF | 0x66000000, colour & 0x00FFFFFF});
            return new LayerDrawable(new Drawable[]{new ColorDrawable(DEEP), g});
        }
    }

    /**
     * Whether a poster is mostly see-through: Jellyfin's placeholder for an item with no art is its small logo on a
     * transparent picture, which reads as no picture at all, so it gets the no-picture look.
     */
    private static boolean blank(Bitmap b) {
        if (!b.hasAlpha()) return false;
        int clear = 0, n = 8;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if ((b.getPixel((2 * x + 1) * b.getWidth() / (2 * n), (2 * y + 1) * b.getHeight() / (2 * n)) >>> 24) < 16) clear++;
            }
        }
        return clear > n * n * 6 / 10;
    }

    /** "Jellyfin · S1 E6 · 18 min left": the app, the season and episode when the app gave them, the time left. */
    private String metaLine(WatchNext.Program p) {
        StringBuilder sb = new StringBuilder();
        try {
            PackageManager pm = activity.getPackageManager();
            sb.append(pm.getApplicationLabel(pm.getApplicationInfo(p.pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            sb.append(p.pkg);
        }
        String episode = p.subtitle == null ? null : p.subtitle.split(" · ", 2)[0];
        if (episode != null && episode.matches("[SE]\\d.*")) sb.append(" · ").append(episode);
        int minutes = WatchNext.minutesLeft(p);
        if (minutes > 0) {
            sb.append(" · ").append(minutes < 60
                    ? activity.getString(R.string.time_left_min, minutes)
                    : activity.getString(R.string.time_left_h_min, minutes / 60, minutes % 60));
        }
        return sb.toString();
    }
}
