package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Feature's signature: progress as a strip of film, the mock-up's 560 by 46 px, with sprocket holes along both edges,
 * the watched part lit amber and a frame line every 10 minutes of the running time.
 */
final class FeatureStrip extends View {
    /** The mock-up's sizes, in its pixels; drawn at half that in dp. */
    private static final float WIDTH = 560f, HEIGHT = 46f, HOLE = 12f, HOLE_STEP = 26f, HOLE_HEIGHT = 8f, EDGE = 5f,
            FIRST_HOLE = 4f, LINE_TOP = 15f, LINE_WIDTH = 2f, RADIUS = 3f;
    private static final int FILM = 0xFF05070A, AMBER_DARK = 0xFF9C6A16, GROW_MS = 500;

    private final float unit;
    private final boolean animate;
    private final Paint film = new Paint(Paint.ANTI_ALIAS_FLAG), lit = new Paint(), hole = new Paint(), line = new Paint();
    private final RectF box = new RectF();
    private final Matrix litScale = new Matrix();
    private float progress;
    private int totalMinutes;
    private ValueAnimator grow;

    FeatureStrip(Context context, boolean animate, int amber, int bone, int room) {
        super(context);
        this.animate = animate;
        unit = context.getResources().getDisplayMetrics().density / 2f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        film.setColor(FILM);
        lit.setShader(new LinearGradient(0, 0, WIDTH * unit, 0, AMBER_DARK, amber, Shader.TileMode.CLAMP));
        hole.setColor(bone & 0x00FFFFFF | 0x47000000);
        line.setColor(room & 0x00FFFFFF | 0x80000000);
    }

    int widthPx() {
        return Math.round(WIDTH * unit);
    }

    int heightPx() {
        return Math.round(HEIGHT * unit);
    }

    /** Lights the strip to this share of the show (0..1), its frame lines from the running time in minutes. */
    void set(float share, int minutes) {
        totalMinutes = minutes;
        if (grow != null) grow.cancel();
        grow = null;
        if (!animate) {
            progress = share;
            invalidate();
            return;
        }
        grow = ValueAnimator.ofFloat(progress, share).setDuration(GROW_MS);
        grow.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
        grow.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        grow.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        box.set(0, 0, w, h);
        float r = RADIUS * unit;
        canvas.drawRoundRect(box, r, r, film);
        // The amber runs dark to bright across the watched part, however long it is.
        litScale.setScale(w * progress / (WIDTH * unit), 1f);
        lit.getShader().setLocalMatrix(litScale);
        canvas.drawRect(0, 0, w * progress, h, lit);
        for (int m = 10; m < totalMinutes; m += 10) {
            float x = w * m / totalMinutes;
            canvas.drawRect(x, LINE_TOP * unit, x + LINE_WIDTH * unit, h - LINE_TOP * unit, line);
        }
        float hw = HOLE * unit, hh = HOLE_HEIGHT * unit, edge = EDGE * unit;
        for (float x = FIRST_HOLE * unit; x < w; x += HOLE_STEP * unit) {
            canvas.drawRect(x, edge, x + hw, edge + hh, hole);
            canvas.drawRect(x, h - edge - hh, x + hw, h - edge, hole);
        }
    }
}
