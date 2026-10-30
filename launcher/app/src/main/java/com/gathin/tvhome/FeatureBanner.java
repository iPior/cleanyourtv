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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature's banner behind everything on Home: the focused show's picture at the top right, running into the room at
 * its left and bottom, with an amber line and the title in large poster capitals (the mock-up's 1320 by 780 px picture
 * and 980 px of text). The text fades out and back for each new show; pictures cross-fade over 0.5 s.
 */
final class FeatureBanner extends FrameLayout {
    private static final float WIDTH_SHARE = 1320f / 1920f, HEIGHT_SHARE = 780f / 1080f;
    private static final int PICTURE_FADE_MS = 500, TEXT_FADE_MS = 160;
    /** A longer title takes the mock-up's smaller size. */
    private static final int LONG_TITLE = 20;

    private final float density;
    private final boolean animate;
    private final int room, bone;
    private final Picture picture;
    private final TextView eyebrow, title;
    private final FrameLayout copy;
    private String shownKey;

    FeatureBanner(Context context, boolean animate, int room, int amber, int bone, Typeface eyebrowFont, Typeface titleFont) {
        super(context);
        this.animate = animate;
        this.room = room;
        this.bone = bone;
        density = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        picture = new Picture(context);
        addView(picture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        copy = new FrameLayout(context);
        LayoutParams clp = new LayoutParams(Math.round(490 * density), LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Math.round(48 * density);
        clp.topMargin = Math.round(113 * density);
        addView(copy, clp);

        eyebrow = new TextView(context);
        eyebrow.setSingleLine(true);
        eyebrow.setEllipsize(android.text.TextUtils.TruncateAt.END);
        eyebrow.setTextColor(amber);
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        eyebrow.setLetterSpacing(0.22f);
        eyebrow.setTypeface(eyebrowFont);
        copy.addView(eyebrow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        title = new TextView(context);
        title.setMaxLines(2);
        title.setAllCaps(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(bone);
        title.setTypeface(titleFont);
        title.setIncludeFontPadding(false);
        title.setLineSpacing(0f, 0.86f);
        title.setLetterSpacing(0.005f);
        LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Math.round(29 * density);
        copy.addView(title, tlp);
    }

    /** Fades to this show's text; key names the show, so focusing it again changes nothing. */
    void showText(String key, String lead, String rest, String name) {
        fadeIn();
        if (key.equals(shownKey)) return;
        shownKey = key;
        Runnable fill = () -> {
            fill(lead, rest, name);
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
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, name.length() > LONG_TITLE ? 52f : 70f);
    }

    /** Cross-fades to this picture; null leaves the room clear. */
    void showPicture(Bitmap bitmap) {
        picture.show(bitmap);
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
        private final class Layer {
            final Bitmap bitmap;
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            float alpha;
            ValueAnimator fade;

            Layer(Bitmap bitmap) {
                this.bitmap = bitmap;
            }
        }

        private final List<Layer> layers = new ArrayList<>();
        private final RectF area = new RectF();
        private final Paint fromLeft = new Paint(), fromBottom = new Paint();

        Picture(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void show(Bitmap bitmap) {
            Layer top = layers.isEmpty() ? null : layers.get(layers.size() - 1);
            if (top != null && top.bitmap == bitmap) {
                fade(top, 1f);
                return;
            }
            for (Layer l : new ArrayList<>(layers)) fade(l, 0f);
            if (bitmap == null) return;
            Layer l = new Layer(bitmap);
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
            for (Layer l : layers) shade(l);
        }

        /** The picture cropped to cover the area: a tall poster from its top, the scene; a wide one from its middle. */
        private void shade(Layer l) {
            if (area.isEmpty()) return;
            BitmapShader shader = new BitmapShader(l.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float bw = l.bitmap.getWidth(), bh = l.bitmap.getHeight();
            float scale = Math.max(area.width() / bw, area.height() / bh);
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            boolean tall = bh > bw;
            m.postTranslate(area.centerX() - bw * scale / 2f, tall ? 0f : area.centerY() - bh * scale / 2f);
            shader.setLocalMatrix(m);
            l.paint.setShader(shader);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (layers.isEmpty()) return;
            for (Layer l : layers) {
                if (l.alpha <= 0f || l.paint.getShader() == null) continue;
                l.paint.setAlpha(Math.round(l.alpha * 255));
                canvas.drawRect(area, l.paint);
            }
            canvas.drawRect(area, fromLeft);
            canvas.drawRect(area, fromBottom);
        }
    }
}
