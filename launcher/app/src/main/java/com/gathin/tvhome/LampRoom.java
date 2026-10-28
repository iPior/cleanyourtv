package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Lamp's room behind everything: warm brown by day, sinking into a deep ember orange in the evening, darker at the
 * edges. {@link #evening} is shared with the dim, the lamp, its ring and the clock, which warm with it.
 */
final class LampRoom extends View {
    private static final int DAY = 0xFF120E0B, EVENING_CENTRE = 0xFF3A1806, EVENING_EDGE = 0xFF140804;

    private final Paint fill = new Paint();
    private final Matrix squeeze = new Matrix();
    private float evening = -1f;

    LampRoom(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** The evening now, or at the preview hour (HomeStyle.hourNow), from 0 (day) to 1 (full evening). */
    static float eveningNow() {
        return evening(HomeStyle.hourNow());
    }

    /** Takes the evening; cheap to call every minute, redraws only when it changed. Returns whether it did. */
    boolean update(float k) {
        if (Math.abs(k - evening) < 0.005f) return false;
        evening = k;
        shade(getWidth(), getHeight());
        invalidate();
        return true;
    }

    /**
     * 0 for the day (8:00 to 17:00) to 1 for the full evening (21:00 to 5:00): it warms from 17 to 21 and cools from
     * 5 to 8, eased (the mock-up's curve).
     */
    static float evening(float hour) {
        if (hour >= 8f && hour < 17f) return 0f;
        if (hour >= 17f && hour < 21f) return ease((hour - 17f) / 4f);
        if (hour >= 5f && hour < 8f) return ease(1f - (hour - 5f) / 3f);
        return 1f;
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        shade(w, h);
    }

    /** The mock-up's 1500 by 1000 ellipse at the middle, 45% down, reaching the edge colour at 75%. */
    private void shade(int w, int h) {
        if (w == 0 || h == 0) return;
        float k = Math.max(0f, evening);
        float cx = w / 2f, cy = h * 0.45f, r = w * 1500f / 1920f;
        RadialGradient g = new RadialGradient(cx, cy, r,
                new int[]{Sky.lerpColor(DAY, EVENING_CENTRE, k), Sky.lerpColor(DAY, EVENING_EDGE, k)},
                new float[]{0f, 0.75f}, Shader.TileMode.CLAMP);
        squeeze.setScale(1f, 1000f / 1500f, cx, cy);
        g.setLocalMatrix(squeeze);
        fill.setShader(g);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (fill.getShader() != null) canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
    }
}
