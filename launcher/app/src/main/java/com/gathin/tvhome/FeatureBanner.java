package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature's banner behind everything on Home: the focused show's picture at the top right, running into the room at
 * its left and bottom, with an amber line, the title in large poster capitals and the film strip with the time left
 * (the mock-up's 1320 by 780 px picture and 980 px of text). The text fades out and back for each new show; pictures cross-fade over 0.5 s.
 */
final class FeatureBanner extends FrameLayout {
    private static final float WIDTH_SHARE = 1320f / 1920f, HEIGHT_SHARE = 780f / 1080f;
    /** The mock-up's upright poster: 260 by 390 px, 150 px down, 160 px in from the right. */
    private static final float POSTER_W = 260f / 1920f, POSTER_H = 390f / 1080f, POSTER_TOP = 150f / 1080f, POSTER_RIGHT = 160f / 1920f;
    private static final int SLATE = 0xFF161B26;
    private static final int PICTURE_FADE_MS = 500, TEXT_FADE_MS = 160;
    /** The title's size, and the mock-up's smaller one for a title that would not fit on one line at it. */
    private static final float TITLE_SP = 70f, LONG_TITLE_SP = 52f;

    private final float density;
    private final boolean animate;
    private final int room, bone;
    private final Picture picture;
    private final TextView eyebrow, title, left;
    private final LinearLayout copy, progress;
    private final FeatureStrip strip;
    private String shownKey;

    FeatureBanner(Context context, boolean animate, int room, int amber, int bone, int mute, Typeface eyebrowFont,
                  Typeface titleFont, Typeface leftFont) {
        super(context);
        this.animate = animate;
        this.room = room;
        this.bone = bone;
        density = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        picture = new Picture(context);
        addView(picture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        // Held at its bottom, just over the row's label: a two-line title grows up towards the clock, not into the row.
        copy.setGravity(android.view.Gravity.BOTTOM);
        LayoutParams clp = new LayoutParams(Math.round(490 * density), Math.round(173 * density));
        clp.leftMargin = Math.round(48 * density);
        clp.topMargin = Math.round(75 * density);
        addView(copy, clp);

        eyebrow = new TextView(context);
        eyebrow.setSingleLine(true);
        eyebrow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        eyebrow.setTextColor(amber);
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        eyebrow.setLetterSpacing(0.22f);
        eyebrow.setTypeface(eyebrowFont);
        copy.addView(eyebrow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        title = new TextView(context);
        title.setMaxLines(2);
        title.setAllCaps(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(bone);
        title.setTypeface(titleFont);
        title.setIncludeFontPadding(false);
        title.setLineSpacing(0f, 0.86f);
        title.setLetterSpacing(0.005f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(10 * density);
        copy.addView(title, tlp);

        // The strip with "18 min left · ends at 9:40 PM" beside it.
        progress = new LinearLayout(context);
        progress.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        plp.topMargin = Math.round(-4 * density);   // up into the title's font box, which is taller than its capitals
        copy.addView(progress, plp);
        strip = new FeatureStrip(context, animate, amber, bone, room);
        progress.addView(strip, new LinearLayout.LayoutParams(strip.widthPx(), strip.heightPx()));
        left = new TextView(context);
        left.setSingleLine(true);
        left.setTextColor(mute);
        left.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        left.setTypeface(leftFont);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.leftMargin = Math.round(14 * density);
        progress.addView(left, llp);
    }

    /**
     * Fades to this show's text; key names the show, so focusing it again changes nothing. share is how far through it
     * is (0..1) with the running time in minutes and the time left; below 0 leaves the strip out.
     */
    void showText(String key, String lead, String rest, String name, float share, int minutes, String timeLeft, String ends) {
        fadeIn();
        if (key.equals(shownKey)) return;
        shownKey = key;
        Runnable fill = () -> {
            fill(lead, rest, name);
            progress.setVisibility(share < 0f ? GONE : VISIBLE);
            if (share >= 0f) {
                strip.set(share, minutes);
                SpannableString time = new SpannableString(timeLeft + " \u00B7 " + ends);
                time.setSpan(new ForegroundColorSpan(bone), 0, timeLeft.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                time.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, timeLeft.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                left.setText(time);
            }
            if (animate) copy.animate().alpha(1f).setDuration(TEXT_FADE_MS).start();
            else copy.setAlpha(1f);
        };
        copy.animate().cancel();
        if (animate) copy.animate().alpha(0f).setDuration(TEXT_FADE_MS).withEndAction(fill).start();
        else fill.run();
    }

    private void fill(String lead, String rest, String name) {
        // The caps transformation drops spans, so the capitals are written out here.
        String text = rest == null ? lead : lead + "  ·  " + rest;
        SpannableString line = new SpannableString(text.toUpperCase(java.util.Locale.ROOT));
        if (rest != null) line.setSpan(new ForegroundColorSpan(bone & 0x00FFFFFF | 0xBF000000),
                Math.min(lead.length(), line.length()), line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        eyebrow.setText(line);
        title.setText(name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP);
        if (title.getPaint().measureText(name.toUpperCase(java.util.Locale.ROOT)) > copy.getLayoutParams().width) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, LONG_TITLE_SP);
        }
    }

    /** The width in px to decode a picture at: a tall poster needs only its card on screen. */
    int pictureWidth(boolean tall) {
        return Math.round(getWidth() * (tall ? POSTER_W : WIDTH_SHARE));
    }

    /** Cross-fades to this picture; null or Jellyfin's see-through placeholder gives the glow in the app's colour. */
    void showPicture(Bitmap bitmap, int appColor) {
        picture.show(bitmap == null || MinutesTimeline.blank(bitmap) ? null : bitmap, appColor);
    }

    private void fadeIn() {
        animate().cancel();
        if (animate) animate().alpha(1f).setDuration(TEXT_FADE_MS).start();
        else setAlpha(1f);
    }

    /** Focus went down into the extra apps: the banner gives the screen to them. */
    void hide() {
        animate().cancel();
        if (animate) animate().alpha(0f).setDuration(TEXT_FADE_MS).start();
        else setAlpha(0f);
    }

    /** The picture and the two washes of the room over it. */
    private final class Picture extends View {
        /** A picture, or none, with what stands behind it and how far it has faded in. */
        private final class Layer {
            final Bitmap bitmap;
            final int color;
            final boolean tall;
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            final Paint glow = new Paint(), base = new Paint();
            float alpha;
            ValueAnimator fade;

            Layer(Bitmap bitmap, int color) {
                this.bitmap = bitmap;
                this.color = color;
                tall = bitmap != null && bitmap.getHeight() > bitmap.getWidth();
                base.setColor(bitmap == null ? SLATE : room);
            }
        }

        private final List<Layer> layers = new ArrayList<>();
        private final RectF area = new RectF();
        private final RectF poster = new RectF();
        private final Paint fromLeft = new Paint(), fromBottom = new Paint(), grain = new Paint();
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);

        Picture(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            grain.setShader(MinutesRoom.grainShader());
            shadow.setShadowLayer(35f * density / 2f, 0f, 17f * density, 0xA6000000);
        }

        void show(Bitmap bitmap, int appColor) {
            Layer top = layers.isEmpty() ? null : layers.get(layers.size() - 1);
            if (top != null && top.bitmap == bitmap && (bitmap != null || top.color == appColor)) {
                fade(top, 1f);
                return;
            }
            for (Layer l : new ArrayList<>(layers)) fade(l, 0f);
            int color = bitmap == null ? appColor
                    : AppAdapter.vividColor(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap));
            Layer l = new Layer(bitmap, color);
            shade(l);
            layers.add(l);
            fade(l, 1f);
        }

        private void fade(Layer l, float target) {
            if (l.fade != null) l.fade.cancel();
            l.fade = null;
            if (!animate) {
                l.alpha = target;
                if (target == 0f) layers.remove(l);
                invalidate();
                return;
            }
            ValueAnimator a = ValueAnimator.ofFloat(l.alpha, target).setDuration(PICTURE_FADE_MS);
            a.addUpdateListener(v -> {
                l.alpha = (float) v.getAnimatedValue();
                invalidate();
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (l.fade != a) return;
                    l.fade = null;
                    if (target == 0f) {
                        layers.remove(l);
                        invalidate();
                    }
                }
            });
            l.fade = a;
            a.start();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            float rw = w * WIDTH_SHARE, rh = h * HEIGHT_SHARE, left = w - rw;
            area.set(left, 0, w, rh);
            int clear = room & 0x00FFFFFF;
            fromLeft.setShader(new LinearGradient(left, 0, left + rw * 0.62f, 0,
                    new int[]{room, clear | 0xDB000000, clear}, new float[]{0f, 0.387f, 1f}, Shader.TileMode.CLAMP));
            fromBottom.setShader(new LinearGradient(0, rh * 0.96f, 0, rh * 0.52f, room, clear, Shader.TileMode.CLAMP));
            poster.set(w - w * (POSTER_RIGHT + POSTER_W), h * POSTER_TOP, w - w * POSTER_RIGHT, h * (POSTER_TOP + POSTER_H));
            for (Layer l : layers) shade(l);
        }

        /** The picture cropped to cover the area: a tall poster from its top, the scene; a wide one from its middle. */
        private void shade(Layer l) {
            if (area.isEmpty()) return;
            if (l.bitmap == null) {
                glow(l, 0.72f, 0.40f, 820f, 560f, 0x40);
            } else if (l.tall) {
                glow(l, 0.76f, 0.36f, 1000f, 720f, 0x66);
                BitmapShader shader = new BitmapShader(l.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float scale = Math.max(poster.width() / l.bitmap.getWidth(), poster.height() / l.bitmap.getHeight());
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate(poster.left, poster.top);
                shader.setLocalMatrix(m);
                l.paint.setShader(shader);
            } else {
                BitmapShader shader = new BitmapShader(l.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float bw = l.bitmap.getWidth(), bh = l.bitmap.getHeight();
                float scale = Math.max(area.width() / bw, area.height() / bh);
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate(area.centerX() - bw * scale / 2f, area.centerY() - bh * scale / 2f);
                shader.setLocalMatrix(m);
                l.paint.setShader(shader);
            }
        }

        /** The mock-up's oval glow: centred at x, y of the area, its radii in the mock-up's px, strength of 255. */
        private void glow(Layer l, float x, float y, float rx, float ry, int strength) {
            float unit = getWidth() / 1920f, cx = area.left + area.width() * x, cy = area.height() * y;
            int clear = l.color & 0x00FFFFFF;
            android.graphics.RadialGradient oval = new android.graphics.RadialGradient(0f, 0f, 1f,
                    clear | strength << 24, clear, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(rx * unit * 0.7f, ry * unit * 0.7f);
            m.postTranslate(cx, cy);
            oval.setLocalMatrix(m);
            l.glow.setShader(oval);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (layers.isEmpty()) return;
            for (Layer l : layers) {
                int alpha = Math.round(l.alpha * 255);
                if (alpha <= 0) continue;
                if (l.bitmap != null && !l.tall) {
                    l.paint.setAlpha(alpha);
                    canvas.drawRect(area, l.paint);
                } else {
                    l.base.setAlpha(alpha);
                    canvas.drawRect(area, l.base);
                    l.glow.setAlpha(alpha);
                    canvas.drawRect(area, l.glow);
                    grain.setAlpha(alpha);
                    canvas.drawRect(area, grain);
                }
            }
            canvas.drawRect(area, fromLeft);
            canvas.drawRect(area, fromBottom);
            float r = 6f * density;
            for (Layer l : layers) {
                if (!l.tall || l.alpha <= 0f) continue;
                int alpha = Math.round(l.alpha * 255);
                shadow.setAlpha(alpha);
                canvas.drawRoundRect(poster, r, r, shadow);
                l.paint.setAlpha(alpha);
                canvas.drawRoundRect(poster, r, r, l.paint);
            }
        }
    }
}
