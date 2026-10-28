package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;

public class HorizonSkyTest {
    @Test
    public void eachPhaseHoldsItsOwnColoursOnceItHasFadedIn() {
        assertArrayEquals(HorizonSky.DAWN, HorizonSky.colours(7f));
        assertArrayEquals(HorizonSky.DAY, HorizonSky.colours(12f));
        assertArrayEquals(HorizonSky.DUSK, HorizonSky.colours(19f));
        assertArrayEquals(HorizonSky.NIGHT, HorizonSky.colours(23f));
        assertArrayEquals(HorizonSky.NIGHT, HorizonSky.colours(3f));
    }

    @Test
    public void aPhaseStartsWithTheOneBeforeAndFadesOverAnHour() {
        assertArrayEquals(HorizonSky.DAY, HorizonSky.colours(17f));
        assertArrayEquals(HorizonSky.DUSK, HorizonSky.colours(18f));
        int[] half = HorizonSky.colours(17.5f);
        assertFalse(Arrays.equals(half, HorizonSky.DAY) || Arrays.equals(half, HorizonSky.DUSK));
        // Midnight wraps: still night, as at 23:00.
        assertArrayEquals(HorizonSky.NIGHT, HorizonSky.colours(0f));
    }
}
