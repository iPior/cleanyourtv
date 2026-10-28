package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
 * The Continue watching row above the dock's top shelf (config.xml continue_watching): one card per Watch Next
 * program, its poster (or the app's banner when the poster is on the web, which the launcher cannot reach) and a
 * progress bar. Shown only while the dock is focused at rest and there is something to show; OK opens the program.
 *
 * Cards sit over the dock's columns, one per column at most, so Up and Down move straight between the two rows.
 * MainActivity owns the glass highlight and the top shelf's text; this class only reports focus to it.
 */
final class WatchRow {

    interface Host {
        /** A card took focus: put the glass on it and the program's title on the top shelf. */
        void onWatchFocused(View card, WatchNext.Program program);

        /** Down from a card: to the dock tile in the same column (-1: the dock tile focus came from). */
        void onWatchDown(int column);
    }

    /** Reads the provider. Posters load on their own thread: an app's image provider can take seconds per poster
     *  (Jellyfin's fetches from its server), and the cards must not wait for it. */
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor();
    private static final ExecutorService POSTERS = Executors.newSingleThreadExecutor();
    /** Decoded posters by URI, shared by every Home instance; a few dozen KB each at tile size. */
    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<>(12);
    /** URIs being decoded, so a resume during a slow load does not queue the same poster again (main thread). */
    private static final java.util.Set<String> PENDING = new java.util.HashSet<>();
    private static boolean permissionAsked;

    private final Activity activity;
    private final Host host;
    private final float density;
    private final boolean animations;
    private final int maxCards;
    private final FrameLayout container;
    private final TextView label;
    private final List<View> cards = new ArrayList<>();
    /** Per card: its picture, and the title written over the app banner until the poster arrives. */
    private final List<ImageView> arts = new ArrayList<>();
    private final List<TextView> titles = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    private List<WatchNext.Program> programs = Collections.emptyList();
    /** Bumped by every refresh; a load that finishes after a newer one started is dropped. */
    private int generation;
    private boolean wanted;
    private int lastIndex;
    /** The last placement asked for, kept so a refresh that changes the cards can lay them out again. */
    private float[] centers;
    private float bottomY, topLimitY;
    private int cardW, cardH;

    WatchRow(Activity activity, FrameLayout root, View above, Host host, int maxCards, boolean animations) {
        this.activity = activity;
        this.host = host;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.animations = animations;
        this.maxCards = Math.max(1, maxCards);
        container = new FrameLayout(activity);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        // Room under the cards for the focused one's zoom: the screen clips the row at its own bounds, which cut off
        // the bottom of Lamp's ring. The row is placed by its top, so this moves nothing.
        container.setPadding(0, 0, 0, Math.round(12 * density));
        container.setAlpha(0f);
        container.setVisibility(View.GONE);
        label = new TextView(activity);
        label.setText(R.string.continue_watching);
        label.setSingleLine(true);   // before setAllCaps: it replaces the transformation that capitalises
        label.setAllCaps(true);
        label.setLetterSpacing(0.22f);
        label.setTextColor(0xB3FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
        container.addView(label, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        // Under the glass, which is drawn last so it can sit over the focused tile.
        root.addView(container, root.indexOfChild(above), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---- data ------------------------------------------------------------------------------------

    /** Reads Watch Next again (onResume). Asks once per process for the permission the row needs. */
    void refresh() {
        if (activity.checkSelfPermission(WatchNext.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionAsked) {
                permissionAsked = true;
                activity.requestPermissions(new String[]{WatchNext.PERMISSION}, 0);
            }
            apply(Collections.emptyList(), ++generation);
            return;
        }
        int gen = ++generation;
        Context app = activity.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        LOADER.execute(() -> {
            List<WatchNext.Program> all = WatchNext.query(app);
            List<WatchNext.Program> rows = WatchNext.select(all, System.currentTimeMillis(), maxCards,
                    pkg -> pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null);
            // `logcat -s TvHomeWatchNext`: whether apps publish anything, and how much of it the row keeps.
            android.util.Log.i(WatchNext.TAG, all.size() + " Watch Next rows visible, " + rows.size() + " shown");
            main.post(() -> apply(rows, gen));
        });
    }

    private void apply(List<WatchNext.Program> rows, int gen) {
        if (gen != generation || activity.isDestroyed()) return;
        if (!WatchNext.same(rows, programs)) {
            boolean hadFocus = hasFocus();
            programs = rows;
            buildCards();
            if (programs.isEmpty()) {
                setShown(false);
                if (hadFocus) host.onWatchDown(-1);
            } else if (wanted && centers != null) {
                layout();
                setShown(fits());
                if (hadFocus && !focus()) host.onWatchDown(-1);
            }
        }
        loadPosters();
    }

    /** Decodes the posters not cached yet, one at a time, each put on its cards as soon as it is ready. */
    private void loadPosters() {
        Context app = activity.getApplicationContext();
        int width = cardW > 0 ? cardW : Math.round(200 * density);
        for (WatchNext.Program p : programs) {
            String uri = p.poster;
            if (!WatchNext.localPoster(uri) || CACHE.get(uri) != null || !PENDING.add(uri)) continue;
            POSTERS.execute(() -> {
                Bitmap b = WatchNext.poster(app, uri, width);
                main.post(() -> {
                    PENDING.remove(uri);
                    if (b == null) return;
                    CACHE.put(uri, b);
                    if (!activity.isDestroyed()) showPoster(uri, b);
                });
            });
        }
    }

    private void showPoster(String uri, Bitmap b) {
        for (int i = 0; i < programs.size() && i < arts.size(); i++) {
            if (!uri.equals(programs.get(i).poster)) continue;
            arts.get(i).setImageBitmap(b);
            arts.get(i).setImageAlpha(255);
            titles.get(i).setVisibility(View.GONE);
        }
    }

    // ---- cards -----------------------------------------------------------------------------------

    private void buildCards() {
        for (View c : cards) container.removeView(c);
        cards.clear();
        arts.clear();
        titles.clear();
        PackageManager pm = activity.getPackageManager();
        for (int i = 0; i < programs.size(); i++) {
            WatchNext.Program p = programs.get(i);
            cards.add(card(p, p.poster == null ? null : CACHE.get(p.poster), pm, i));
        }
        if (lastIndex >= cards.size()) lastIndex = Math.max(0, cards.size() - 1);
    }

    private View card(WatchNext.Program p, Bitmap poster, PackageManager pm, int index) {
        FrameLayout card = new FrameLayout(activity);
        card.setFocusable(true);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AppAdapter.cornerRadius(v.getWidth(), density));
            }
        });
        card.setBackgroundColor(0xFF1C1C1E);
        card.setContentDescription(p.subtitle == null ? p.title : p.title + ", " + p.subtitle);

        ImageView art = new ImageView(activity);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (poster != null) {
            art.setImageBitmap(poster);
        } else {
            // No poster yet (it is loading, or it is on the web): the app's banner, with the title written over it.
            Drawable banner = null;
            try {
                banner = pm.getApplicationBanner(p.pkg);
                if (banner == null) banner = pm.getApplicationIcon(p.pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // uninstalled since the query: the card shows only its title
            }
            if (banner != null) {
                art.setImageDrawable(banner);
                art.setImageAlpha(60);
            }
        }
        card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView t = new TextView(activity);
        {
            // Darker towards the bottom, so the title reads over any banner.
            t.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000, 0xB3000000}));
            t.setText(p.subtitle == null ? p.title : p.title + "\n" + p.subtitle);
            t.setMaxLines(3);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTypeface(activity.getResources().getFont(R.font.outfit_medium));
            t.setShadowLayer(4 * density, 0, 0, 0xCC000000);
            int pad = Math.round(10 * density);
            t.setPadding(pad, pad, pad, Math.round(16 * density));
            card.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
            t.setVisibility(poster == null ? View.VISIBLE : View.GONE);
        }
        arts.add(art);
        titles.add(t);

        float progress = p.progress();
        if (progress >= 0f) {
            int inset = Math.round(10 * density);
            int barH = Math.max(2, Math.round(4 * density));
            View track = new View(activity);
            track.setBackground(pill(0x59FFFFFF));
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH, Gravity.BOTTOM);
            tl.setMargins(inset, 0, inset, inset);
            card.addView(track, tl);
            View fill = new View(activity);
            fill.setBackground(pill(0xFFFFFFFF));
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(0, barH, Gravity.BOTTOM | Gravity.START);
            fl.setMargins(inset, 0, 0, inset);
            card.addView(fill, fl);
            fill.setTag(progress);
        }

        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? AppAdapter.FOCUS_SCALE : 1f;
            if (animations) v.animate().scaleX(s).scaleY(s).setDuration(200).start();
            else {
                v.setScaleX(s);
                v.setScaleY(s);
            }
            if (has) {
                lastIndex = index;
                host.onWatchFocused(v, p);
            }
        });
        card.setOnClickListener(v -> open(p));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.onWatchDown(index);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (index > 0) cards.get(index - 1).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (index < cards.size() - 1) cards.get(index + 1).requestFocus();
                    return true;
                default:
                    return false;   // Up: the focus finder takes it to the top bar right above
            }
        });
        container.addView(card);
        if (dimCards) LampDim.apply(card, false, true, false);
        return card;
    }

    private static GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(100f);
        return d;
    }

    private void open(WatchNext.Program p) {
        Intent i = WatchNext.intent(p);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                activity.startActivity(i);
                return;
            } catch (RuntimeException e) {
                // ActivityNotFoundException, or a SecurityException for an intent the app keeps to itself
            }
        }
        // The program's own intent did not open: the app, at least.
        PackageManager pm = activity.getPackageManager();
        Intent app = pm.getLeanbackLaunchIntentForPackage(p.pkg);
        if (app == null) app = pm.getLaunchIntentForPackage(p.pkg);
        if (app == null) {
            android.widget.Toast.makeText(activity, R.string.not_available, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activity.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(activity, R.string.not_available, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ---- placement and focus ---------------------------------------------------------------------

    /**
     * The dock is focused at rest: show the row with its cards centred on the dock's columns (root x), its bottom at
     * bottomY, sized like the dock's tiles but never reaching above topLimitY.
     */
    void showAbove(float[] columnCenters, int tileW, int tileH, float bottomY, float topLimitY) {
        wanted = true;
        centers = columnCenters;
        this.bottomY = bottomY;
        this.topLimitY = topLimitY;
        boolean resized = tileW != cardW || tileH != cardH;
        cardW = tileW;
        cardH = tileH;
        if (programs.isEmpty()) return;
        if (resized || container.getVisibility() != View.VISIBLE) layout();
        else placeContainer();
        setShown(fits());
    }

    /** Focus left the dock for the grid or the library, or Home is going away. */
    void hide() {
        wanted = false;
        setShown(false);
    }

    boolean isShown() {
        return container.getVisibility() == View.VISIBLE && wanted && !cards.isEmpty();
    }

    /** A Continue watching card: the glass stays shown when focus moves onto one. */
    boolean owns(View v) {
        return cards.contains(v);
    }

    /** Lamp dims every card but the focused one (LampDim); off in every other look. */
    private boolean dimCards;

    void setDimCards(boolean dim) {
        dimCards = dim;
        for (View card : cards) LampDim.apply(card, false, dim && !card.isFocused(), false);
    }

    boolean hasFocus() {
        return container.hasFocus();
    }

    /** Down from the top bar: the card last focused in the row. False when there is no row to go to. */
    boolean focus() {
        return focus(lastIndex);
    }

    /** Up from the dock tile in this column: the card above it, or the last card when the row is shorter. */
    boolean focus(int column) {
        if (!isShown()) return false;
        return cards.get(Math.max(0, Math.min(column, cards.size() - 1))).requestFocus();
    }

    /** Room for the label above the cards, from the label's top to the cards' top. */
    static final float LABEL_ROOM_DP = 30f;

    private int labelH() {
        return Math.round(LABEL_ROOM_DP * density);
    }

    /** A look's own lettering for the row's label: capitals or not, size in sp, colour and letter spacing. */
    void setLabelStyle(boolean caps, float sp, int color, float spacing, Typeface typeface) {
        label.setTypeface(typeface);
        label.setAllCaps(caps);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        label.setTextColor(color);
        label.setLetterSpacing(spacing);
    }

    private float rowTop() {
        return bottomY - labelH() - cardH;
    }

    private boolean fits() {
        return cardH > 0 && rowTop() >= topLimitY;
    }

    private void layout() {
        if (centers == null || cardW <= 0 || cardH <= 0) return;
        placeContainer();
        int n = Math.min(cards.size(), centers.length);
        for (int i = 0; i < cards.size(); i++) {
            View c = cards.get(i);
            if (i >= n) {
                c.setVisibility(View.GONE);
                continue;
            }
            c.setVisibility(View.VISIBLE);
            // Margins, not translations: the glass finds the card by its layout position.
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW, cardH);
            lp.topMargin = labelH();
            lp.leftMargin = Math.round(centers[i] - cardW / 2f);
            c.setLayoutParams(lp);
            c.setPivotX(cardW / 2f);
            c.setPivotY(cardH / 2f);
            // The progress fill's width depends on the card's.
            ViewGroup g = (ViewGroup) c;
            for (int j = 0; j < g.getChildCount(); j++) {
                View f = g.getChildAt(j);
                if (f.getTag() instanceof Float) {
                    int inset = Math.round(10 * density);
                    f.getLayoutParams().width = Math.max(1, Math.round((cardW - 2 * inset) * (Float) f.getTag()));
                    f.requestLayout();
                }
            }
        }
        FrameLayout.LayoutParams ll = (FrameLayout.LayoutParams) label.getLayoutParams();
        ll.leftMargin = Math.round(centers[0] - cardW / 2f);
        label.setLayoutParams(ll);
    }

    private void placeContainer() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) container.getLayoutParams();
        int top = Math.round(rowTop());
        if (lp.topMargin == top) return;
        lp.topMargin = top;
        container.setLayoutParams(lp);
    }

    private void setShown(boolean show) {
        boolean visible = container.getVisibility() == View.VISIBLE;
        if (show == visible && (!show || container.getAlpha() == 1f)) return;
        container.animate().cancel();
        if (show) {
            container.setVisibility(View.VISIBLE);
            if (animations) container.animate().alpha(1f).setDuration(220).start();
            else container.setAlpha(1f);
        } else if (animations) {
            container.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                if (!wanted || programs.isEmpty()) container.setVisibility(View.GONE);
            }).start();
        } else {
            container.setAlpha(0f);
            container.setVisibility(View.GONE);
        }
    }
}
