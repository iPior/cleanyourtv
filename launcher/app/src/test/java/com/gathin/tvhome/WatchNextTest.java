package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WatchNextTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static WatchNext.Program p(String pkg, String title, int type, long engagedDaysAgo, long pos, long dur) {
        return new WatchNext.Program(pkg, title, null, null, "intent:#Intent;package=" + pkg + ";end", type,
                NOW - engagedDaysAgo * DAY, pos, dur);
    }

    private static List<WatchNext.Program> select(WatchNext.Program... rows) {
        return WatchNext.select(Arrays.asList(rows), NOW, 5, pkg -> true);
    }

    @Test public void newestFirst() {
        List<WatchNext.Program> out = select(p("a", "Old", 0, 5, 10, 100), p("b", "New", 0, 1, 10, 100));
        assertEquals("New", out.get(0).title);
        assertEquals("Old", out.get(1).title);
    }

    @Test public void theWatchlistIsNotContinueWatching() {
        assertTrue(select(p("a", "Later", WatchNext.TYPE_WATCHLIST, 1, 0, 0)).isEmpty());
    }

    @Test public void rowsOlderThanTheMaxAgeAreLeftOut() {
        assertTrue(select(p("a", "Six months", 0, 180, 10, 100)).isEmpty());
        assertEquals(1, select(p("a", "Last month", 0, 30, 10, 100)).size());
    }

    @Test public void aFinishedContinueRowIsLeftOut() {
        assertTrue(select(p("a", "Done", 0, 1, 99, 100)).isEmpty());
    }

    @Test public void aNextEpisodeWithoutPositionStays() {
        List<WatchNext.Program> out = select(p("a", "Next", 1, 1, 0, 0));
        assertEquals(1, out.size());
        assertEquals(-1f, out.get(0).progress(), 0f);
    }

    @Test public void oneCardPerTitleTheNewest() {
        List<WatchNext.Program> out = select(p("a", "Show", 0, 3, 10, 100), p("a", "Show", 0, 1, 50, 100));
        assertEquals(1, out.size());
        assertEquals(0.5f, out.get(0).progress(), 0.001f);
    }

    @Test public void theSameTitleFromTwoAppsShowsTwice() {
        assertEquals(2, select(p("a", "Show", 0, 1, 10, 100), p("b", "Show", 0, 2, 10, 100)).size());
    }

    @Test public void atMostTheLimit() {
        WatchNext.Program[] rows = new WatchNext.Program[8];
        for (int i = 0; i < rows.length; i++) rows[i] = p("a", "T" + i, 0, i, 10, 100);
        assertEquals(5, select(rows).size());
    }

    @Test public void appsNoLongerInstalledAreLeftOut() {
        List<WatchNext.Program> out = WatchNext.select(Arrays.asList(p("gone", "X", 0, 1, 10, 100),
                p("here", "Y", 0, 2, 10, 100)), NOW, 5, pkg -> pkg.equals("here"));
        assertEquals(1, out.size());
        assertEquals("Y", out.get(0).title);
    }

    @Test public void rowsWithoutTitleOrIntentAreLeftOut() {
        WatchNext.Program noIntent = new WatchNext.Program("a", "T", null, null, null, 0, NOW, 10, 100);
        assertTrue(select(p("a", "", 0, 1, 10, 100), noIntent).isEmpty());
    }

    @Test public void sameComparesWhatTheCardDraws() {
        List<WatchNext.Program> a = Collections.singletonList(p("a", "T", 0, 1, 10, 100));
        assertTrue(WatchNext.same(a, Collections.singletonList(p("a", "T", 0, 1, 10, 100))));
        assertFalse(WatchNext.same(a, Collections.singletonList(p("a", "T", 0, 1, 20, 100))));
        assertFalse(WatchNext.same(a, Collections.emptyList()));
    }

    @Test public void subtitleFromTheFilledParts() {
        assertEquals("S2 E5 · Pilot", WatchNext.subtitle("2", "5", "Pilot"));
        assertEquals("E5", WatchNext.subtitle(null, "5", ""));
        assertNull(WatchNext.subtitle(null, null, null));
    }

    @Test public void onlyLocalPostersLoadWithoutNetwork() {
        assertTrue(WatchNext.localPoster("content://org.jellyfin.androidtv.images/1"));
        assertTrue(WatchNext.localPoster("android.resource://com.spotify.tv.android/123"));
        assertFalse(WatchNext.localPoster("https://image.example/poster.jpg"));
        assertFalse(WatchNext.localPoster(null));
    }

    @Test public void sampleSizeKeepsAtLeastTheTargetWidth() {
        assertEquals(1, WatchNext.sampleSize(400, 300));
        assertEquals(4, WatchNext.sampleSize(1920, 300));
        assertEquals(1, WatchNext.sampleSize(1920, 0));
    }
}
