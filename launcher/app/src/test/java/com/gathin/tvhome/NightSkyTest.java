package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NightSkyTest {
    @Test
    public void dayIsLightAndNightIsFullyDark() {
        assertEquals(0f, NightSkyView.darkness(12f), 0f);
        assertEquals(0f, NightSkyView.darkness(8f), 0f);
        assertEquals(1f, NightSkyView.darkness(23f), 0f);
        assertEquals(1f, NightSkyView.darkness(2f), 0f);
        assertEquals(1f, NightSkyView.darkness(21f), 0f);
    }

    @Test
    public void sunsetDarkensStepByStepAndDawnLightensBack() {
        float last = 0f;
        for (float h = 16f; h <= 21f; h += 0.25f) {
            float d = NightSkyView.darkness(h);
            assertTrue("sunset at " + h, d >= last);
            last = d;
        }
        last = 1f;
        for (float h = 5f; h <= 8f; h += 0.25f) {
            float d = NightSkyView.darkness(h);
            assertTrue("dawn at " + h, d <= last);
            last = d;
        }
    }
}
