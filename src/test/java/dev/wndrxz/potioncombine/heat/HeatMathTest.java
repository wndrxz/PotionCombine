package dev.wndrxz.potioncombine.heat;

import dev.wndrxz.potioncombine.heat.HeatSourceManager.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure distance-falloff maths for area heat. No server needed — these only
 * touch doubles, so they run on the same Paper classpath as the matcher
 * tests without spinning anything up.
 */
class HeatMathTest {

    @Test
    void adjacentSourceKeepsFullStrength() {
        Source lava = new Source(0.70, 0.30);
        // "Right under" and "one block in any direction" are both full value;
        // fall-off only starts to bite from distance two.
        assertSame(lava, HeatSourceManager.scaleByDistance(lava, 0, 0.34));
        assertSame(lava, HeatSourceManager.scaleByDistance(lava, 1, 0.34));
    }

    @Test
    void distanceWeakensTheBonus() {
        Source lava = new Source(0.70, 0.30); // 0.30 speedup, 0.30 resist
        // factor = 1 - 0.34 * (2 - 1) = 0.66
        Source far = HeatSourceManager.scaleByDistance(lava, 2, 0.34);
        assertEquals(0.802, far.brewTimeMultiplier(), 1e-9);
        assertEquals(0.198, far.pollutionResist(), 1e-9);
    }

    @Test
    void fallenOffToNothingIsNone() {
        Source campfire = new Source(0.85, 0.0);
        // factor = 1 - 0.5 * (3 - 1) = 0 → the source no longer reaches.
        assertTrue(HeatSourceManager.scaleByDistance(campfire, 3, 0.5).isNone());
    }
}
