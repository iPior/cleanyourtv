package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LampRoomTest {
    @Test
    public void dayIsPlainAndTheFullEveningRunsFromNineToFive() {
        assertEquals(0f, LampRoom.evening(12f), 0f);
        assertEquals(0f, LampRoom.evening(8f), 0f);
        assertEquals(0f, LampRoom.evening(16.9f), 0f);
        assertEquals(1f, LampRoom.evening(21f), 0f);
        assertEquals(1f, LampRoom.evening(23.5f), 0f);
        assertEquals(1f, LampRoom.evening(3f), 0f);
    }

    @Test
    public void itWarmsThroughTheEveningAndCoolsByMorning() {
        float last = 0f;
        for (float h = 17f; h <= 21f; h += 0.25f) {
            float k = LampRoom.evening(h);
            assertTrue("evening at " + h, k >= last);
            last = k;
        }
        last = 1f;
        for (float h = 5f; h <= 8f; h += 0.25f) {
            float k = LampRoom.evening(h);
            assertTrue("morning at " + h, k <= last);
            last = k;
        }
    }
}
