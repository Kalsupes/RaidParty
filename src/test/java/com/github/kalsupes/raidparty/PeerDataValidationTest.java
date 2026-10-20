package com.github.kalsupes.raidparty;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PeerDataValidationTest
{
    @Test public void validMaximumInventoryAndAtomicClearsAreAccepted()
    {
        RaidPartyPlayerSync s = new RaidPartyPlayerSync(); s.setInvIds(new int[28]); s.setInvQtys(new int[28]);
        s.getInvQtys()[0] = Integer.MAX_VALUE;
        assertTrue(PeerDataValidation.nativeState(s));
        s.setInvIds(new int[0]); s.setInvQtys(new int[0]); assertTrue(PeerDataValidation.nativeState(s));
        s.setInvIds(new int[29]); s.setInvQtys(new int[29]); assertFalse(PeerDataValidation.nativeState(s));
    }
    @Test public void mismatchedNegativeAndOversizedNativeDataAreRejected()
    {
        RaidPartyPlayerSync s = new RaidPartyPlayerSync(); s.setInvIds(new int[1]); s.setInvQtys(new int[2]); assertFalse(PeerDataValidation.nativeState(s));
        s = new RaidPartyPlayerSync(); s.setHp(-1); assertFalse(PeerDataValidation.nativeState(s));
        s = new RaidPartyPlayerSync(); s.setUsername(String.join("", Collections.nCopies(33, "x"))); assertFalse(PeerDataValidation.nativeState(s));
        s = new RaidPartyPlayerSync(); s.setSpellbook(4); assertFalse(PeerDataValidation.nativeState(s));
    }
    @Test public void enumCoordinateAndMarkupChecksProtectPresentation()
    {
        assertTrue(PeerDataValidation.ping(new BossPingMessage(3200,3200,0,3,3,995)));
        assertFalse(PeerDataValidation.ping(new BossPingMessage(3200,3200,4,0,0,0)));
        assertFalse(PeerDataValidation.ping(new BossPingMessage(3200,3200,0,99,0,0)));
        assertEquals("Hello", PeerDataValidation.clean("<col=ff0000>Hello</col>"));
    }
    @Test public void incomingBurstIsBoundedAndMembershipCanBeReleased()
    {
        InboundBudget budget = new InboundBudget(); int accepted = 0;
        for (int i = 0; i < 10000; i++) if (budget.take(1, 0)) accepted++;
        assertEquals(32, accepted);
        budget.forget(1); assertTrue(budget.take(1, 0));
    }
}
