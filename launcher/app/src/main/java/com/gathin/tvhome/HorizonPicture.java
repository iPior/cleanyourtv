package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizon's picture in the sky: the focused show's poster, faded into the sky at the top right behind the clock, at
 * 65% and softened towards the left and the bottom (the mock-up's 1240 by 440 px). A tall (2:3) poster stands as a
 * side panel at the right instead, fading in from its own left. Pictures cross-fade over 0.5 s.
 */
final class HorizonPicture extends View {
    private static final float WIDTH_SHARE = 1240f / 1920f, HEIGHT_SHARE = 440f / 1080f, SIDE_SHARE = 700f / 1240f;
    private static final float STRENGTH = 0.65f;
    private static final int FADE_MS = 500;

    /** One picture and how far it has faded in; the last is the one shown, the others are fading out. */
    private static final class Layer {
        final Bitmap bitmap;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF area = new RectF();
        float alpha;
        ValueAnimator fade;

        Layer(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }

    private final List<Layer> layers = new ArrayList<>();
    private boolean animate = true;

    HorizonPicture(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setAnimate(boolean animate) {
        this.animate = animate;
    }

    /** Shows this poster, cross-fading from the one before; null hides the picture. */
    void show(Bitmap bitmap) {
        Layer top = layers.isEmpty() ? null : layers.get(layers.size() - 1);
        if (top != null && top.bitmap == bitmap && top.fade == null && top.alpha == 1f) return;
        if (top != null && top.bitmap == bitmap) {
            fade(top, 1f);
            return;
        }
        for (Layer l : new ArrayList<>(layers)) fade(l, 0f);
        if (bitmap == null) return;
        Layer l = new Layer(bitmap);
        shade(l, getWidth(), getHeight());
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
        ValueAnimator a = ValueAnimator.ofFloat(l.alpha, target).setDuration(FADE_MS);
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
        for (Layer l : layers) shade(l, w, h);
    }

    /** The poster cropped to cover its area, times the masks that fade it into the sky. */
    private static void shade(Layer l, int w, int h) {
        if (w == 0 || h == 0) return;
        float rw = w * WIDTH_SHARE, rh = h * HEIGHT_SHARE, left = w - rw;
        boolean tall = l.bitmap.getHeight() > l.bitmap.getWidth();
        l.area.set(tall ? w - rw * SIDE_SHARE : left, 0, w, rh);

        BitmapShader picture = new BitmapShader(l.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float bw = l.bitmap.getWidth(), bh = l.bitmap.getHeight();
        float scale = Math.max(l.area.width() / bw, l.area.height() / bh);
        m.setScale(scale, scale);
        // A tall poster from its top, the scene, as its card shows it; a wide one from its middle.
        m.postTranslate(l.area.centerX() - bw * scale / 2f, tall ? l.area.top : l.area.centerY() - bh * scale / 2f);
        picture.setLocalMatrix(m);

        // Clear at the area's left, solid from 45% across; clear at its bottom, solid from 45% up.
        Shader across = new LinearGradient(left, 0, left + rw * 0.45f, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        Shader down = new LinearGradient(0, rh, 0, rh * 0.55f, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
        Shader mask = new ComposeShader(across, down, PorterDuff.Mode.DST_IN);
        if (tall) {
            // The side panel fades in from its own left too, solid from 55% across.
            Shader side = new LinearGradient(l.area.left, 0, l.area.left + l.area.width() * 0.55f, 0,
                    0x00000000, 0xFF000000, Shader.TileMode.CLAMP);
            mask = new ComposeShader(mask, side, PorterDuff.Mode.DST_IN);
        }
        l.paint.setShader(new ComposeShader(picture, mask, PorterDuff.Mode.DST_IN));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (Layer l : layers) {
            if (l.alpha <= 0f || l.paint.getShader() == null) continue;
            l.paint.setAlpha(Math.round(l.alpha * STRENGTH * 255));
            canvas.drawRect(l.area, l.paint);
        }
    }
}
