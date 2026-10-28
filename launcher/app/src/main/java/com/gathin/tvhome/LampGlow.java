package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

/**
 * Lamp's light: a wide soft amber oval behind the cards, centred on the focused one. It rides on the glass, which
 * Lamp keeps hidden: the glass already springs to every focus, through scrolls and jumps, so the lamp only reads
 * where it is before each frame and moves there. A focused top button is lit instead ({@link #light}); the lamp
 * eases between the two over the mock-up's 0.6 s. One gradient oval, no layer.
 */
final class LampGlow extends View {
    /** A 1400 by 960 px oval on a 1920 px wide screen: the mock-up's 1100 by 760 looked small and dark on the TV. */
    private static final float WIDTH_SHARE = 1400f / 1920f, HEIGHT_SHARE = 960f / 1920f;

    private View glass;
    /** A top button the lamp lights instead of the glass, or null. */
    private View button;
    private final Rect buttonRect = new Rect();
    /** The move between the glass and a button, 0 to 1 eased; null when the lamp sits on its source. */
    private ValueAnimator move;
    private float fromX, fromY, fromAlpha;
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

    /** Lights a focused top button, or (null) goes back to the glass, easing over. */
    void light(View button) {
        if (this.button == button) return;
        this.button = button;
        fromX = getTranslationX();
        fromY = getTranslationY();
        fromAlpha = getAlpha();
        if (move != null) move.cancel();
        move = ValueAnimator.ofFloat(0f, 1f).setDuration(600);
        move.setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f));
        move.addUpdateListener(a -> invalidate());
        move.start();
    }

    /** Sizes the oval to the screen; call with the root's width. */
    void fitTo(int screenW) {
        int w = Math.round(screenW * WIDTH_SHARE), h = Math.round(screenW * HEIGHT_SHARE);
        if (getLayoutParams() != null && getLayoutParams().width == w) return;
        getLayoutParams().width = w;
        getLayoutParams().height = h;
        requestLayout();
    }

    /** The lamp's colour (the evening warms it); the gradient's strength stays as set here. */
    void setColor(int color) {
        if (this.color == color) return;
        this.color = color;
        shaderW = 0;
        invalidate();
    }

    private void follow() {
        if (glass == null || glass.getWidth() == 0 || getWidth() == 0) return;
        float cx, cy, a;
        if (button != null && button.isAttachedToWindow()) {
            buttonRect.set(0, 0, button.getWidth(), button.getHeight());
            ((ViewGroup) getParent()).offsetDescendantRectToMyCoords(button, buttonRect);
            cx = buttonRect.exactCenterX();
            cy = buttonRect.exactCenterY();
            a = 1f;
        } else {
            cx = glass.getTranslationX() + glass.getLeft() + glass.getWidth() / 2f;
            cy = glass.getTranslationY() + glass.getTop() + glass.getHeight() / 2f;
            // The glass fades in and out with focus on the tiles; the lamp goes with it.
            a = glass.getAlpha();
        }
        float x = cx - getWidth() / 2f, y = cy - getHeight() / 2f;
        if (move != null) {
            float f = (float) move.getAnimatedValue();
            x = fromX + (x - fromX) * f;
            y = fromY + (y - fromY) * f;
            a = fromAlpha + (a - fromAlpha) * f;
            if (!move.isRunning()) move = null;
        }
        if (x != getTranslationX()) setTranslationX(x);
        if (y != getTranslationY()) setTranslationY(y);
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
                    new int[]{withAlpha(color, 0.32f), withAlpha(color, 0.12f), color & 0x00FFFFFF},
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
