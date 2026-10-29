package com.gathin.tvhome;

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
 * sits. One gradient drawn once per size, over the theme's background colour.
 */
final class MinutesRoom extends View {
    /** The mock-up's lighter violet at the gradient's centre; the night itself is the theme's background_color. */
    private static final int LIGHT = 0xFF211C38;

    /** Side of the noise tile, in pixels. */
    private static final int GRAIN = 64;

    private final Paint paint = new Paint();
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

    /** The mock-up's 1300 by 760 px ellipse at 78% across and 18% down, fading out at 70% of its radius. */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w == 0 || h == 0) return;
        float s = w / 1920f, cx = w * 0.78f, cy = h * 0.18f, rx = 1300 * s, ry = 760 * s;
        RadialGradient g = new RadialGradient(cx, cy, rx, new int[]{LIGHT, LIGHT & 0x00FFFFFF},
                new float[]{0f, 0.7f}, Shader.TileMode.CLAMP);
        Matrix squeeze = new Matrix();
        squeeze.setScale(1f, ry / rx, cx, cy);
        g.setLocalMatrix(squeeze);
        paint.setShader(g);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (paint.getShader() == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        canvas.drawRect(0, 0, getWidth(), getHeight(), grain);
    }
}
