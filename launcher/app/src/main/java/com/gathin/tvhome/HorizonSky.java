package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Horizon's sky behind everything: a dark vertical gradient with a lighter horizon glowing along the bottom, in the
 * colours of dawn, day, dusk or night by the clock (no setting). Each phase fades in over its first hour.
 */
final class HorizonSky extends View {
    /** Top, bottom and horizon colours per phase, from the mock-up: always dark enough for a TV at night. */
    static final int[] DAWN = {0xFF0D0B1A, 0xFF2A2140, 0xFF7A5068};
    static final int[] DAY = {0xFF08121C, 0xFF12283C, 0xFF3A6484};
    static final int[] DUSK = {0xFF0C0812, 0xFF2A1428, 0xFF8A4048};
    static final int[] NIGHT = {0xFF060914, 0xFF0E1834, 0xFF24346A};

    private final Paint sky = new Paint(), horizon = new Paint();
    private final Matrix squeeze = new Matrix();
    private int[] colours;

    HorizonSky(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Picks up the hour (HomeStyle.hourNow); cheap to call every minute, redraws only when the sky changed. */
    void update() {
        int[] c = colours(HomeStyle.hourNow());
        if (colours != null && java.util.Arrays.equals(c, colours)) return;
        colours = c;
        shade(getWidth(), getHeight());
        invalidate();
    }

    /**
     * The sky's three colours at this hour: dawn 5 to 8, day 8 to 17, dusk 17 to 21, night 21 to 5, each easing in
     * from the one before over its first hour.
     */
    static int[] colours(float hour) {
        hour = (hour % 24f + 24f) % 24f;
        int[] from, to;
        float start;
        if (hour >= 5f && hour < 8f) { from = NIGHT; to = DAWN; start = 5f; }
        else if (hour >= 8f && hour < 17f) { from = DAWN; to = DAY; start = 8f; }
        else if (hour >= 17f && hour < 21f) { from = DAY; to = DUSK; start = 17f; }
        else { from = DUSK; to = NIGHT; start = 21f; }
        float t = Math.min(1f, ((hour - start) % 24f + 24f) % 24f);
        t = t * t * (3f - 2f * t);
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) out[i] = Sky.lerpColor(from[i], to[i], t);
        return out;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        shade(w, h);
    }

    /** The gradient top to bottom, and the mock-up's 1800 by 640 horizon glow centred just below the screen. */
    private void shade(int w, int h) {
        if (w == 0 || h == 0 || colours == null) return;
        sky.setShader(new LinearGradient(0, 0, 0, h, colours[0], colours[1], Shader.TileMode.CLAMP));
        float cx = w / 2f, cy = h * 1.12f, r = w * 1800f / 1920f;
        RadialGradient g = new RadialGradient(cx, cy, r, new int[]{colours[2], colours[2] & 0x00FFFFFF},
                new float[]{0f, 0.72f}, Shader.TileMode.CLAMP);
        squeeze.setScale(1f, 640f / 1800f, cx, cy);
        g.setLocalMatrix(squeeze);
        horizon.setShader(g);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sky.getShader() == null) return;
        canvas.drawRect(0, 0, getWidth(), getHeight(), sky);
        canvas.drawRect(0, 0, getWidth(), getHeight(), horizon);
    }
}
