package com.github.kalsupes.raidparty.defence;

import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceEstimateTest
{
    private DefenceProfile profile(String name, int defence, int magic, boolean demon)
    {
        DefenceProfile p = new DefenceProfile();
        p.name = name; p.defence = defence; p.magic = magic; p.demon = demon;
        p.attack = 100; p.strength = 100; p.ranged = 100;
        return p;
    }

    private DefenceEstimate estimate(DefenceProfile p)
    {
        return new DefenceEstimate(p, 1, false, 99);
    }

    @Test public void bgsSubtractsDamageAndClampsAtZero()
    {
        DefenceEstimate e = estimate(profile("Test", 200, 100, false));
        e.apply(DefenceAttack.BANDOS_GODSWORD, 50, false);
        assertEquals(150, e.getDefence());
        e.apply(DefenceAttack.BANDOS_GODSWORD, 500, false);
        assertEquals(0, e.getDefence());
    }

    @Test public void hammersUseIntegerDrainNotRoundedRemainingLevel()
    {
        DefenceEstimate e = estimate(profile("Test", 205, 100, false));
        e.apply(DefenceAttack.DRAGON_WARHAMMER, 1, false);
        assertEquals(144, e.getDefence());
        e.apply(DefenceAttack.DRAGON_WARHAMMER, 1, false);
        assertEquals(101, e.getDefence());
        e.apply(DefenceAttack.DRAGON_WARHAMMER, 0, false);
        assertEquals(101, e.getDefence());
    }

    @Test public void threeEmberlightsReachYamaFloor()
    {
        DefenceEstimate e = estimate(DefenceProfile.find(14176, "Yama", false, false));
        e.apply(DefenceAttack.EMBERLIGHT, 1, false); assertEquals(191, e.getDefence());
        e.apply(DefenceAttack.EMBERLIGHT, 1, false); assertEquals(157, e.getDefence());
        e.apply(DefenceAttack.EMBERLIGHT, 1, false); assertEquals(145, e.getDefence());
        e.apply(DefenceAttack.BANDOS_GODSWORD, 100, false); assertEquals(145, e.getDefence());
    }

    @Test public void yamaMaulLeavesTwoLevelsAboveFloor()
    {
        DefenceEstimate e = estimate(DefenceProfile.find(14176, "Yama", false, false));
        e.apply(DefenceAttack.ELDER_MAUL, 1, false); assertEquals(147, e.getDefence());
        e.apply(DefenceAttack.BANDOS_GODSWORD, 10, false); assertEquals(145, e.getDefence());
    }

    @Test public void nexCannotBeDrainedToZero()
    {
        DefenceEstimate e = estimate(DefenceProfile.find(11281, "Nex", false, false));
        e.apply(DefenceAttack.DRAGON_WARHAMMER, 1, false);
        assertEquals(250, e.getDefence());
        assertEquals(250, e.getFloor());
    }

    @Test public void toaFloorsAreBossSpecific()
    {
        int[] ids = {11789, 11719, 11778, 11730, 11761};
        int[] floors = {70, 60, 60, 50, 120};
        for (int i = 0; i < ids.length; i++)
        {
            DefenceEstimate e = estimate(DefenceProfile.find(ids[i], "", false, false));
            e.apply(DefenceAttack.BANDOS_GODSWORD, 500, false);
            assertEquals(floors[i], e.getDefence());
        }
    }

    @Test public void tektonMissesStillDrain()
    {
        DefenceEstimate e = estimate(DefenceProfile.find(7540, "Tekton", false, false));
        e.apply(DefenceAttack.DRAGON_WARHAMMER, 0, false); assertEquals(195, e.getDefence());
        e.apply(DefenceAttack.ELDER_MAUL, 0, false); assertEquals(186, e.getDefence());
        e.apply(DefenceAttack.BANDOS_GODSWORD, 0, false); assertEquals(176, e.getDefence());
    }

    @Test public void corpAndWalkingBloatUseUnhalvedBgsDamage()
    {
        DefenceEstimate corp = estimate(profile("Corporeal Beast", 310, 350, false));
        corp.apply(DefenceAttack.BANDOS_GODSWORD, 50, false); assertEquals(210, corp.getDefence());
        DefenceEstimate bloat = estimate(profile("Pestilent Bloat", 100, 150, false));
        bloat.apply(DefenceAttack.BANDOS_GODSWORD, 20, true); assertEquals(80, bloat.getDefence());
        bloat.apply(DefenceAttack.BANDOS_GODSWORD, 20, false); assertEquals(40, bloat.getDefence());
    }

    @Test public void accursedDoesNotStackOrIncreaseAlreadyDrainedStats()
    {
        DefenceEstimate e = estimate(profile("Test", 200, 100, false));
        e.apply(DefenceAttack.ACCURSED_SCEPTRE, 1, false); assertEquals(170, e.getDefence());
        e.apply(DefenceAttack.ACCURSED_SCEPTRE, 1, false); assertEquals(170, e.getDefence());
        e.apply(DefenceAttack.BANDOS_GODSWORD, 100, false);
        e.apply(DefenceAttack.ACCURSED_SCEPTRE, 1, false); assertEquals(70, e.getDefence());
        assertEquals(85, e.getMagic());
    }

    @Test public void ralosUsesCurrentMagicForEachSuccessfulHit()
    {
        DefenceEstimate e = estimate(profile("Test", 200, 200, false));
        e.apply(DefenceAttack.ACCURSED_SCEPTRE, 1, false);
        e.apply(DefenceAttack.TONALZTICS_OF_RALOS, 2, false); assertEquals(128, e.getDefence());
        e.apply(DefenceAttack.TONALZTICS_OF_RALOS, 0, false); assertEquals(128, e.getDefence());
        e.apply(DefenceAttack.SEERCULL, 100, false);
        e.apply(DefenceAttack.TONALZTICS_OF_RALOS, 1, false); assertEquals(120, e.getDefence());
    }

    @Test public void boneWeaponsOnlyDrainAnUndrainedTarget()
    {
        DefenceEstimate e = estimate(profile("Test", 200, 100, false));
        e.apply(DefenceAttack.BONE_DAGGER, 30, false);
        e.apply(DefenceAttack.DORGESHUUN_CROSSBOW, 50, false);
        assertEquals(170, e.getDefence());
    }

    @Test public void cursesAreNonStackingAndRespectFloors()
    {
        DefenceEstimate e = estimate(profile("Test", 200, 100, false));
        e.apply(DefenceAttack.VULNERABILITY, 10, false); assertEquals(180, e.getDefence());
        e.apply(DefenceAttack.VULNERABILITY, 10, false); assertEquals(180, e.getDefence());
        e.apply(DefenceAttack.VULNERABILITY, 15, false); assertEquals(170, e.getDefence());
        e.apply(DefenceAttack.CURSE, 10, false); assertEquals(170, e.getDefence());
    }

    @Test public void immuneBossStaysAtBase()
    {
        DefenceEstimate e = estimate(DefenceProfile.find(8374, "Verzik Vitur", false, false));
        e.apply(DefenceAttack.BANDOS_GODSWORD, 500, false);
        assertEquals(e.getBase(), e.getDefence());
    }

    @Test public void coxScalingIncludesModeAndHighestHitpoints()
    {
        DefenceProfile p = DefenceProfile.find(7544, "Tekton", false, false);
        assertEquals(246, new DefenceEstimate(p, 1, true, 99).getBase());
        assertEquals(284, new DefenceEstimate(p, 4, true, 99).getBase());
        assertTrue(new DefenceEstimate(p, 4, false, 80).getBase() < new DefenceEstimate(p, 4, false, 99).getBase());
        DefenceProfile claw = DefenceProfile.find(7553, "Great Olm (Right claw)", false, false);
        assertEquals(136, new DefenceEstimate(claw, 5, true, 99).getMagic());
    }

    @Test public void profileLookupSeparatesVariantsAndRejectsUnknowns()
    {
        assertEquals(250, DefenceProfile.find(12204, "The Whisperer", false, false).getDefence());
        assertEquals(300, DefenceProfile.find(12204, "The Whisperer", true, false).getDefence());
        assertEquals(150, DefenceProfile.find(11761, "", false, false).getDefence());
        assertEquals(180, DefenceProfile.find(11761, "", false, true).getDefence());
        assertNull(DefenceProfile.find(99999, "Unknown boss", false, false));
        assertNull(DefenceProfile.find(99999, "Goblin", false, false));
    }

    @Test public void olmMeleeHandUsesDefensiveMagicScalingInNormalAndChallengeModes()
    {
        for (int id : new int[]{7552, 7555})
        {
            DefenceProfile p = DefenceProfile.find(id, "Great Olm", false, false);
            for (int size = 1; size <= 100; size++)
            {
                DefenceEstimate normal = new DefenceEstimate(p, size, false, 99);
                DefenceEstimate cm = new DefenceEstimate(p, size, true, 99);
                assertEquals(normal.getBase(), normal.getMagic());
                assertEquals(cm.getBase(), cm.getMagic());
            }
            DefenceEstimate solo = new DefenceEstimate(p, 1, false, 99);
            assertEquals(175, solo.getBase());
            solo.apply(DefenceAttack.DRAGON_WARHAMMER, 1, false);
            assertEquals(123, solo.getDefence());
            DefenceEstimate normal = new DefenceEstimate(p, 100, false, 99);
            DefenceEstimate cm = new DefenceEstimate(p, 100, true, 99);
            assertEquals(311, normal.getMagic());
            assertEquals(466, cm.getMagic());
            // July 2026 increased Ralos from 10% to 12.5% per successful hit.
            normal.apply(DefenceAttack.TONALZTICS_OF_RALOS, 1, false);
            cm.apply(DefenceAttack.TONALZTICS_OF_RALOS, 1, false);
            assertEquals(273, normal.getDefence());
            assertEquals(408, cm.getDefence());
        }
    }

    @Test public void bgsOverflowUpdatesMagicAndRalosUsesTheRemainingLevel()
    {
        DefenceEstimate xarpus = estimate(DefenceProfile.find(8340, "Xarpus", false, false));
        xarpus.apply(DefenceAttack.BANDOS_GODSWORD, 300, false);
        assertEquals(0, xarpus.getDefence());
        assertEquals(172, xarpus.getMagic()); // 250 Defence, 1 Strength, 1 Attack, then Magic
        xarpus.apply(DefenceAttack.TONALZTICS_OF_RALOS, 2, false);
        assertEquals(0, xarpus.getDefence());
        assertEquals(172, xarpus.getMagic());

        DefenceEstimate mixed = estimate(profile("Test", 200, 200, false));
        mixed.apply(DefenceAttack.SEERCULL, 100, false);
        mixed.apply(DefenceAttack.TONALZTICS_OF_RALOS, 2, false);
        assertEquals(176, mixed.getDefence());
    }

    @Test public void dynamicDefenceIsUnknownInsteadOfZero()
    {
        DefenceEncounter e = new DefenceEncounter(DefenceProfile.find(12223, "Vardorvis", false, false), 1, false, 99);
        assertNull(e.getEstimate());
        assertTrue(e.add("hit", 100, DefenceAttack.BANDOS_GODSWORD, 50, false));
        assertEquals(1, e.getHitCount());
    }
}
