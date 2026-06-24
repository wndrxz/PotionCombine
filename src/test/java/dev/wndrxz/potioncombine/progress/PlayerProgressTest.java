package dev.wndrxz.potioncombine.progress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The progression bookkeeping in isolation — no server, just the rules that
 * decide what "discovered" and "first time" mean. These pin the contract the
 * journal and the discovery message lean on: discovery is one-shot and
 * monotonic, and the counters only ever count up.
 */
class PlayerProgressTest {

    @Test
    void firstDiscoveryIsReportedOnceThenNeverAgain() {
        PlayerProgress p = new PlayerProgress();
        assertTrue(p.discover("phoenix_elixir"), "first sight of a recipe is new");
        assertFalse(p.discover("phoenix_elixir"), "seeing it again is not");
        assertTrue(p.hasDiscovered("phoenix_elixir"));
    }

    @Test
    void brewCountsAndTotalsClimbTogether() {
        PlayerProgress p = new PlayerProgress();
        p.recordBrew("ashen_brew");
        p.recordBrew("ashen_brew");
        p.recordBrew("drowned_tear");
        assertEquals(2, p.brewCount("ashen_brew"));
        assertEquals(1, p.brewCount("drowned_tear"));
        assertEquals(3, p.totalBrews(), "every brew bumps the grand total too");
        assertEquals(0, p.brewCount("never_brewed"));
    }

    @Test
    void spoiledAndFailedAreSeparateTallies() {
        PlayerProgress p = new PlayerProgress();
        p.recordSpoiled();
        p.recordFailed();
        p.recordFailed();
        assertEquals(1, p.totalSpoiled());
        assertEquals(2, p.totalFailed());
        assertEquals(0, p.totalBrews(), "a spoil or a fail is not a collected brew");
    }

    @Test
    void restoreRebuildsStateWithoutFiringFirstTimeDiscovery() {
        PlayerProgress p = new PlayerProgress();
        // A blank, brand-new player record.
        assertTrue(p.isEmpty());

        p.restoreDiscovered("phoenix_crown");
        p.restoreBrewCount("phoenix_crown", 5);
        p.restoreTotals(9, 2, 1);

        assertFalse(p.isEmpty());
        assertTrue(p.hasDiscovered("phoenix_crown"));
        assertEquals(5, p.brewCount("phoenix_crown"));
        assertEquals(9, p.totalBrews());
        // A restored recipe must not look freshly discovered, or a reload
        // would spam the "new recipe!" line on every login.
        assertFalse(p.discover("phoenix_crown"));
    }
}
