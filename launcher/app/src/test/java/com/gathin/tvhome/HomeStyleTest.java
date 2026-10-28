package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class HomeStyleTest {
    @Test
    public void everyStyleIsFoundByItsOwnId() {
        for (HomeStyle s : HomeStyle.values()) assertEquals(s, HomeStyle.of(s.id));
    }

    @Test
    public void noneOrAnUnknownIdIsNoStyle() {
        assertNull(HomeStyle.of(null));
        assertNull(HomeStyle.of(""));
        assertNull(HomeStyle.of("aurora"));   // a pushed theme's name is not a built-in style
    }

    @Test
    public void idsAreUniqueSoAPickNeverOpensTheWrongLook() {
        Set<String> ids = new HashSet<>();
        for (HomeStyle s : HomeStyle.values()) assertEquals(true, ids.add(s.id));
    }
}
