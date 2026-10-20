package com.github.kalsupes.raidparty;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerStateDeltaTest
{
    @Test public void eventOnlyMetadataDoesNotCauseStateTraffic()
    {
        RaidPartyPlayerSync s = new RaidPartyPlayerSync(); s.setOnline(true); s.setDefenceVersion(2);
        assertFalse(PlayerStateDelta.hasChanges(s));
    }
    @Test public void zeroCombatValuesAndEmptyInventoryClearsAreRealChanges()
    {
        RaidPartyPlayerSync s = new RaidPartyPlayerSync(); s.setHp(0); assertTrue(PlayerStateDelta.hasChanges(s));
        s = new RaidPartyPlayerSync(); s.setPrayer(0); assertTrue(PlayerStateDelta.hasChanges(s));
        s = new RaidPartyPlayerSync(); s.setSpec(0); assertTrue(PlayerStateDelta.hasChanges(s));
        s = new RaidPartyPlayerSync(); s.setInvIds(new int[0]); assertTrue(PlayerStateDelta.hasChanges(s));
        s = new RaidPartyPlayerSync(); s.setSkillXps(new int[]{123}); assertTrue(PlayerStateDelta.hasChanges(s));
    }
    @Test public void consecutiveHpChangesAreNotDebounced()
    {
        for (int hp = 99; hp >= 0; hp--) { RaidPartyPlayerSync s = new RaidPartyPlayerSync(); s.setHp(hp); assertTrue(PlayerStateDelta.hasChanges(s)); }
    }
}
