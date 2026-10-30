package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Minutes' room: the violet night behind everything, a little lighter towards the top right where the timeline
 * sits, and a glow in the colour of what is focused (a show's picture, an app's own colour, lilac on the buttons)
 * that fades from one colour to the next over 0.8 s, as in the mock-up.
 */
final class MinutesRoom extends View {
    /** The mock-up's lighter violet at the gradient's centre; the night itself is the theme's background_color. */
    private static final int LIGHT = 0xFF211C38;

    /** The theme's lilac: the glow's colour on the buttons, and for a colour too grey to tint the room. */
    static final int LILAC = 0xFFA898FF;
    private static final int FADE_MS = 800;

    /** Side of the noise tile, in pixels. */
    private static final int GRAIN = 64;

    private final Paint paint = new Paint();
    private final Paint high = new Paint(), low = new Paint();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    /** The glow's colour now. */
    private int shown = LILAC;
    private ValueAnimator fade;
    /**
     * A faint grain over the gradient: it steps only about 13 levels across the screen, which the TV draws as wide
     * rings, and the GPU ignores the dither flag. A tile of white specks at 0 to 3 of 255 breaks the steps up.
     */
    private final Paint grain = new Paint();

    MinutesRoom(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int[] specks = new int[GRAIN * GRAIN];
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < specks.length; i++) specks[i] = random.nextInt(4) << 24 | 0x00FFFFFF;
        Bitmap tile = Bitmap.createBitmap(specks, GRAIN, GRAIN, Bitmap.Config.ARGB_8888);
        grain.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Fades the glow to this colour; one too grey to tint the room (a white banner's) gives the lilac. */
    void setColor(int color, boolean animate) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        int target = (hsv[1] < 0.2f || hsv[2] < 0.15f ? LILAC : color) | 0xFF000000;
        if (target == shown && fade == null) return;
        if (fade != null) fade.cancel();
        fade = null;
        if (!animate) {
            show(target);
            return;
        }
        fade = ValueAnimator.ofObject(argb, shown, target).setDuration(FADE_MS);
        fade.addUpdateListener(a -> show((int) a.getAnimatedValue()));
        fade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (fade == a) fade = null;
            }
        });
        fade.start();
    }

    private void show(int color) {
        shown = color;
        shadeGlow(getWidth(), getHeight());
        invalidate();
    }

    /** The mock-up's 1300 by 760 px ellipse at 78% across and 18% down, fading out at 70% of its radius. */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        float s = w / 1920f;
        paint.setShader(oval(w * 0.78f, h * 0.18f, 1300 * s, 760 * s, LIGHT, 1f));
        shadeGlow(w, h);
    }

    /**
     * The mock-up's .amb: 1300 by 820 px at 74% across and 30% down, at 30% of the colour; and 1400 by 500 px at
     * 30% across, 112% down, at 16%; both fading out at 70% of their radius.
     */
    private void shadeGlow(int w, int h) {
        if (w == 0 || h == 0) return;
        float s = w / 1920f;
        high.setShader(oval(w * 0.74f, h * 0.30f, 1300 * s, 820 * s, shown, 0.30f));
        low.setShader(oval(w * 0.30f, h * 1.12f, 1400 * s, 500 * s, shown, 0.16f));
    }

    private static Shader oval(float cx, float cy, float rx, float ry, int color, float strength) {
        int rgb = color & 0x00FFFFFF;
        RadialGradient g = new RadialGradient(cx, cy, rx, new int[]{Math.round(255 * strength) << 24 | rgb, rgb},
                new float[]{0f, 0.7f}, Shader.TileMode.CLAMP);
        Matrix squeeze = new Matrix();
        squeeze.setScale(1f, ry / rx, cx, cy);
        g.setLocalMatrix(squeeze);
        return g;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (paint.getShader() == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        canvas.drawRect(0, 0, getWidth(), getHeight(), high);
        canvas.drawRect(0, 0, getWidth(), getHeight(), low);
        canvas.drawRect(0, 0, getWidth(), getHeight(), grain);
    }
}
