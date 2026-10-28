package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Horizon's glow in the colour of what is focused: a wide band rising from below the screen, and a fainter patch
 * at the top right, over the sky. The colour fades over 0.8 s, as in the mock-up; 0 fades it away.
 */
final class HorizonGlow extends View {
    private static final int FADE_MS = 800;

    private final Paint low = new Paint(), high = new Paint();
    private final Matrix squeeze = new Matrix();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    /** The colour drawn now, with its alpha carrying the fade (0: nothing). */
    private int shown;
    private ValueAnimator fade;

    HorizonGlow(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Fades to this colour (opaque), or away with 0. */
    void setColor(int color, boolean animate) {
        int target = color == 0 ? shown & 0x00FFFFFF : color | 0xFF000000;
        if (fade != null) fade.cancel();
        if (!animate) {
            show(target);
            return;
        }
        fade = ValueAnimator.ofObject(argb, shown, target).setDuration(FADE_MS);
        fade.addUpdateListener(a -> show((int) a.getAnimatedValue()));
        fade.start();
    }

    private void show(int color) {
        shown = color;
        shade(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        shade(w, h);
    }

    /**
     * The mock-up's two gradients: 1900 by 620 px at the middle, 115% down, at 42% of the colour; and 1100 by 600 px
     * at 80% across, 18% down, at 18%.
     */
    private void shade(int w, int h) {
        if (w == 0 || h == 0 || (shown >>> 24) == 0) return;
        float s = w / 1920f;
        low.setShader(oval(w * 0.5f, h * 1.15f, 1900 * s, 620 * s, 0.42f, 0.72f));
        high.setShader(oval(w * 0.8f, h * 0.18f, 1100 * s, 600 * s, 0.18f, 0.70f));
    }

    private Shader oval(float cx, float cy, float rx, float ry, float strength, float end) {
        int a = Math.round((shown >>> 24) * strength);
        RadialGradient g = new RadialGradient(cx, cy, rx, new int[]{a << 24 | shown & 0x00FFFFFF, shown & 0x00FFFFFF},
                new float[]{0f, end}, Shader.TileMode.CLAMP);
        squeeze.setScale(1f, ry / rx, cx, cy);
        g.setLocalMatrix(squeeze);
        return g;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if ((shown >>> 24) == 0 || low.getShader() == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), low);
        canvas.drawRect(0, 0, getWidth(), getHeight(), high);
    }
}
