package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Lamp's dimming, set as a card's foreground: every card but the focused one sinks into the warm dark of the
 * room, and white tiles further, like paper in shadow; the focused one gets a thin amber ring instead, fading in
 * as the dim fades out. One multiplied rectangle, clipped by the card's rounded outline: no layer and no blur.
 */
final class LampDim extends Drawable {
    /** A warm grey at about 58% brightness, and about 40% for white tiles: the mock-up's filters as the TV shows them. */
    static final int DIM = 0xFF968A76, DIM_LIGHT = 0xFF6A6052;
    /** The lamp's amber, for the ring and the glow (LampGlow). */
    static final int GLOW = 0xFFE0A060;
    private static final int FADE_MS = 400;

    private final Paint paint = new Paint();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();
    private final float density;
    private boolean light;
    /** 0 is the card as it is, 1 fully dimmed. */
    private float level = 1f;
    private ValueAnimator fade;

    private LampDim(float density) {
        this.density = density;
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        ring.setStyle(Paint.Style.STROKE);
        // Twice the mock-up's 2 px: the outer half falls outside the card's outline, which clips it.
        ring.setStrokeWidth(4 * density);
    }

    /** Dims the card, or lifts the dim off it, adding the foreground the first time. */
    static void apply(View card, boolean light, boolean dim, boolean animate) {
        LampDim d = card.getForeground() instanceof LampDim ? (LampDim) card.getForeground() : null;
        if (d == null) {
            d = new LampDim(card.getResources().getDisplayMetrics().density);
            card.setForeground(d);
        }
        d.light = light;
        d.fadeTo(dim ? 1f : 0f, animate);
    }

    /** Lifts or restores the dim on a card that already has one; light stays as it was set. */
    static void set(View card, boolean dim, boolean animate) {
        if (card.getForeground() instanceof LampDim) ((LampDim) card.getForeground()).fadeTo(dim ? 1f : 0f, animate);
    }

    private void fadeTo(float target, boolean animate) {
        if (fade != null) fade.cancel();
        if (!animate || level == target) {
            level = target;
            invalidateSelf();
            return;
        }
        fade = ValueAnimator.ofFloat(level, target).setDuration(FADE_MS);
        fade.addUpdateListener(a -> {
            level = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        fade.start();
    }

    @Override
    public void draw(Canvas canvas) {
        if (level > 0f) {
            paint.setColor(Sky.lerpColor(0xFFFFFFFF, light ? DIM_LIGHT : DIM, level));
            canvas.drawRect(getBounds(), paint);
        }
        if (level < 1f) {
            ring.setColor(Math.round((1f - level) * 0.85f * 255) << 24 | GLOW & 0x00FFFFFF);
            ringRect.set(getBounds());
            float r = AppAdapter.cornerRadius(getBounds().width(), density);
            canvas.drawRoundRect(ringRect, r, r, ring);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        // The level carries the strength.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Nothing to filter: one flat colour.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
