package com.github.kalsupes.raidparty.defence;

import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceMinimumTest {
    private DefenceEncounter encounter(int id, String name, boolean awakened) {
        return new DefenceEncounter(DefenceProfile.find(id, name, awakened, false), 1, false, 99);
    }
    @Test public void differentWeaponsAndMembersReachOneSharedYamaFloor() {
        DefenceEncounter e = encounter(14176, "Yama", false);
        e.add("member1-maul", 1000, DefenceAttack.ELDER_MAUL, 1, false);
        assertEquals(147, e.getEstimate().getDefence()); assertFalse(e.atMinimum(1001));
        e.add("member2-bgs", 1002, DefenceAttack.BANDOS_GODSWORD, 2, false);
        assertEquals(145, e.getEstimate().getDefence()); assertTrue(e.atMinimum(1003));
        assertFalse(e.add("member2-bgs", 1002, DefenceAttack.BANDOS_GODSWORD, 2, false));
        e.warn("Missed phase reset"); assertFalse(e.atMinimum(1004));
    }
    @Test public void hitCountsAreNotAUniversalThreshold() {
        DefenceEncounter e = encounter(14176, "Yama", false);
        e.add("one", 1000, DefenceAttack.EMBERLIGHT, 1, false);
        e.add("two", 1001, DefenceAttack.EMBERLIGHT, 1, false);
        assertFalse(e.atMinimum(1002));
        e.add("three", 1002, DefenceAttack.EMBERLIGHT, 1, false);
        assertTrue(e.atMinimum(1003)); assertFalse(e.atMinimum(61000));
    }
    @Test public void immuneUnknownDynamicAndFastRecoveryNeverProduceFalseGreen() {
        int[] ids = {12191, 12204, 8374, 12223, 11789, 99999};
        for (int id : ids) {
            DefenceEncounter e = encounter(id, "", true);
            e.add("hit", 1000, DefenceAttack.BANDOS_GODSWORD, 2000, false);
            assertFalse("npc " + id, e.atMinimum(1001));
        }
    }
    @Test public void normalDukeCanReachZeroButAwakenedCannotBeDrained() {
        DefenceEncounter normal = encounter(12191, "Duke Sucellus", false);
        normal.add("bgs", 1000, DefenceAttack.BANDOS_GODSWORD, 275, false);
        assertTrue(normal.atMinimum(1001));
        DefenceEncounter awakened = encounter(12191, "Duke Sucellus", true);
        awakened.add("bgs", 1000, DefenceAttack.BANDOS_GODSWORD, 1000, false);
        assertEquals(316, awakened.getEstimate().getDefence()); assertFalse(awakened.atMinimum(1001));
    }
    @Test public void freshEncounterAndUnverifiedBossStayNeutral() {
        DefenceProfile unknown = new DefenceProfile(); unknown.name = "New boss"; unknown.defence = 200;
        DefenceEncounter e = new DefenceEncounter(unknown, 1, false, 99);
        e.add("bgs", 1000, DefenceAttack.BANDOS_GODSWORD, 500, false);
        assertFalse(e.atMinimum(1001));
        assertFalse(encounter(14176, "Yama", false).atMinimum(1001));
    }
}
