package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Lamp's light: a wide soft amber oval behind the cards, centred on the focused one. It rides on the glass, which
 * Lamp keeps hidden: the glass already springs to every focus, through scrolls and jumps, so the lamp only reads
 * where it is before each frame and moves there. One gradient oval, no layer.
 */
final class LampGlow extends View {
    /** The mock-up's 1100 by 760 px oval on a 1920 px wide screen. */
    private static final float WIDTH_SHARE = 1100f / 1920f, HEIGHT_SHARE = 760f / 1920f;

    private View glass;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = LampDim.GLOW;
    private int shaderW;
    private final ViewTreeObserver.OnPreDrawListener follow = () -> {
        follow();
        return true;
    };

    LampGlow(Context context) {
        super(context);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(follow);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(follow);
        super.onDetachedFromWindow();
    }

    /** The (hidden) glass to stay centred on. */
    void follow(View glass) {
        this.glass = glass;
        invalidate();
    }

    /** Sizes the oval to the screen; call with the root's width. */
    void fitTo(int screenW) {
        int w = Math.round(screenW * WIDTH_SHARE), h = Math.round(screenW * HEIGHT_SHARE);
        if (getLayoutParams() != null && getLayoutParams().width == w) return;
        getLayoutParams().width = w;
        getLayoutParams().height = h;
        requestLayout();
    }

    /** The lamp's colour (the evening warms it); the gradient's strength stays the mock-up's. */
    void setColor(int color) {
        if (this.color == color) return;
        this.color = color;
        shaderW = 0;
        invalidate();
    }

    private void follow() {
        if (glass == null || glass.getWidth() == 0 || getWidth() == 0) return;
        float x = glass.getTranslationX() + glass.getLeft() + glass.getWidth() / 2f - getWidth() / 2f;
        float y = glass.getTranslationY() + glass.getTop() + glass.getHeight() / 2f - getHeight() / 2f;
        if (x != getTranslationX()) setTranslationX(x);
        if (y != getTranslationY()) setTranslationY(y);
        // The glass fades in and out with focus on the tiles; the lamp goes with it.
        float a = glass.getAlpha();
        if (a != getAlpha()) setAlpha(a);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (shaderW != w) {
            // A circle's gradient, squeezed into the oval by scaling the canvas.
            float r = w / 2f;
            paint.setShader(new RadialGradient(r, r, r,
                    new int[]{withAlpha(color, 0.24f), withAlpha(color, 0.08f), color & 0x00FFFFFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            shaderW = w;
        }
        canvas.save();
        canvas.scale(1f, h / (float) w);
        canvas.drawCircle(w / 2f, w / 2f, w / 2f, paint);
        canvas.restore();
    }

    private static int withAlpha(int color, float alpha) {
        return Math.round(alpha * 255) << 24 | color & 0x00FFFFFF;
    }
}
