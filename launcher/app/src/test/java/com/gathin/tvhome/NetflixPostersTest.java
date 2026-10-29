package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetflixPostersTest {
    @Test
    public void onlyHttpsOnNetflixsImageServerIsAllowed() {
        assertTrue(NetflixPosters.allowed("https://occ-0-1234-999.1.nflxso.net/dnm/api/v6/abc/AAAA.jpg?r=1"));
        assertTrue(NetflixPosters.allowed("https://nflxso.net/a.jpg"));
    }

    @Test
    public void everythingElseIsRefused() {
        assertFalse(NetflixPosters.allowed(null));
        assertFalse(NetflixPosters.allowed("http://occ-0-1.1.nflxso.net/a.jpg"));          // not https
        assertFalse(NetflixPosters.allowed("https://example.com/a.jpg"));                 // another host
        assertFalse(NetflixPosters.allowed("https://nflxso.net.example.com/a.jpg"));      // looks alike
        assertFalse(NetflixPosters.allowed("https://evilnflxso.net/a.jpg"));              // no dot before it
        assertFalse(NetflixPosters.allowed("https://nflxso.net@example.com/a.jpg"));      // user info trick
        assertFalse(NetflixPosters.allowed("https://x.nflxso.net:8443/a.jpg"));           // another port
        assertFalse(NetflixPosters.allowed("content://com.netflix/poster/1"));            // local, not this path
        assertFalse(NetflixPosters.allowed("not a url"));
    }
}
