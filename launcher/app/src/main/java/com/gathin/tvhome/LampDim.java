package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Lamp's dimming, set as a card's foreground: every card but the focused one sinks into the warm dark of the
 * room, and white tiles further, like paper in shadow. One multiplied rectangle, clipped by the card's rounded
 * outline: no layer and no blur, so it costs a draw per card.
 */
final class LampDim extends Drawable {
    /** Sepia at about half brightness, and about a third for white tiles (the mock-up's filters). */
    static final int DIM = 0xFF7A6450, DIM_LIGHT = 0xFF4E4034;
    private static final int FADE_MS = 400;

    private final Paint paint = new Paint();
    private boolean light;
    /** 0 is the card as it is, 1 fully dimmed. */
    private float level = 1f;
    private ValueAnimator fade;

    private LampDim() {
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    /** Dims the card, or lifts the dim off it, adding the foreground the first time. */
    static void apply(View card, boolean light, boolean dim, boolean animate) {
        LampDim d = card.getForeground() instanceof LampDim ? (LampDim) card.getForeground() : null;
        if (d == null) {
            d = new LampDim();
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
        if (level <= 0f) return;
        paint.setColor(Sky.lerpColor(0xFFFFFFFF, light ? DIM_LIGHT : DIM, level));
        canvas.drawRect(getBounds(), paint);
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
