package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * Centre Line's backdrop: a soft slate by day that sinks to near black after sunset, with stars that come out one
 * by one as it gets dark, all of them once it is fully dark. Drawn on the canvas (a gradient and about 140 dots)
 * and redrawn once a minute at most; never animated.
 */
final class NightSkyView extends View {
    private static final int STARS = 140;
    private static final int DAY_CENTRE = 0xFF3A3D48, DAY_EDGE = 0xFF262831;
    private static final int NIGHT_CENTRE = 0xFF16161C, NIGHT_EDGE = 0xFF0E0E12;

    /** Each star: x and y as a fraction of the screen, radius in dp, the darkness it appears at, and its alpha. */
    private final float[] x = new float[STARS], y = new float[STARS], r = new float[STARS], at = new float[STARS];
    private final int[] alpha = new int[STARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private final float density;
    private float darkness = -1f;

    NightSkyView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        Random rnd = new Random(17);   // the same sky every time
        for (int i = 0; i < STARS; i++) {
            x[i] = rnd.nextFloat();
            y[i] = (float) Math.pow(rnd.nextFloat(), 1.5);   // more of them high up
            r[i] = 0.4f + rnd.nextFloat() * 0.75f;
            at[i] = 0.15f + rnd.nextFloat() * 0.8f;
            alpha[i] = 90 + rnd.nextInt(140);
        }
        paint.setColor(0xFFFFFFFF);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** Picks up the time of day; cheap to call every minute, redraws only when the sky changed. */
    void update() {
        Calendar now = Calendar.getInstance();
        float d = darkness(now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f);
        if (Math.abs(d - darkness) < 0.005f) return;
        darkness = d;
        fill.setShader(null);
        invalidate();
    }

    /**
     * 0 for full day (8:00 to 16:00) to 1 for full night (21:00 to 5:00), easing through sunset (16 to 21) and
     * dawn (5 to 8).
     */
    static float darkness(float hour) {
        if (hour >= 8f && hour < 16f) return 0f;
        if (hour >= 16f && hour < 21f) return ease((hour - 16f) / 5f);
        if (hour >= 5f && hour < 8f) return ease(1f - (hour - 5f) / 3f);
        return 1f;
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        fill.setShader(null);
        if (darkness < 0f) update();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float d = Math.max(0f, darkness);
        if (fill.getShader() == null) {
            fill.setShader(new RadialGradient(w / 2f, h * 0.4f, w * 0.75f,
                    new int[]{Sky.lerpColor(DAY_CENTRE, NIGHT_CENTRE, d), Sky.lerpColor(DAY_EDGE, NIGHT_EDGE, d)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        }
        canvas.drawRect(0, 0, w, h, fill);
        for (int i = 0; i < STARS; i++) {
            if (d < at[i]) continue;
            paint.setAlpha(alpha[i]);
            canvas.drawCircle(x[i] * w, y[i] * h, r[i] * density, paint);
        }
    }
}
