package com.alkeari.modtrix.locator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NestedJarTest {
    @Test
    void closedLowerOpenUpperRange() {
        assertTrue(NestedJar.inRange("1.20.4", "[1.20.4,1.20.6)"));
        assertTrue(NestedJar.inRange("1.20.5", "[1.20.4,1.20.6)"));
        assertFalse(NestedJar.inRange("1.20.6", "[1.20.4,1.20.6)"));
        assertFalse(NestedJar.inRange("1.20.3", "[1.20.4,1.20.6)"));
    }

    @Test
    void unboundedUpperRange() {
        assertTrue(NestedJar.inRange("1.21", "[1.21,)"));
        assertTrue(NestedJar.inRange("1.21.11", "[1.21,)"));
        assertFalse(NestedJar.inRange("1.20.6", "[1.21,)"));
    }

    @Test
    void exactVersionRange() {
        assertTrue(NestedJar.inRange("1.21.1", "[1.21.1]"));
        assertTrue(NestedJar.inRange("1.21", "[1.21]"));
        assertFalse(NestedJar.inRange("1.21.10", "[1.21.1]"));
        assertFalse(NestedJar.inRange("1.21", "[1.21.1]"));
    }

    @Test
    void shortVersionComparesAsTrailingZero() {
        assertEquals(0, NestedJar.compare("1.21", "1.21.0"));
        assertTrue(NestedJar.compare("1.21.1", "1.21") > 0);
    }

    @Test
    void indexPicksTheJarWhoseRangeHoldsTheVersion() {
        assertEquals("neoforge-1.20.4.jar", NestedJar.jarFor("neoforge", "1.20.4"));
        assertEquals("neoforge-1.21.1.jar", NestedJar.jarFor("neoforge", "1.21.1"));
        assertNull(NestedJar.jarFor("neoforge", "1.19.2"));
        assertNull(NestedJar.jarFor("forge", "1.20.1"));
    }
}
