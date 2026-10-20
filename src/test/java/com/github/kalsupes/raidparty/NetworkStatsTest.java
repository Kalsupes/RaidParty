package com.github.kalsupes.raidparty;
import com.github.kalsupes.raidparty.defence.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkStatsTest
{
    @Test public void disabledInstrumentationDoesNoAccounting()
    {
        NetworkStats stats = NetworkStats.disabled(); stats.sent(new RaidPartyPlayerSync()); assertTrue(stats.snapshot().isEmpty());
    }
    @Test public void countsTypesAndHistoryWithoutRetainingPersonalPayloads()
    {
        NetworkStats stats = new NetworkStats(() -> true);
        RaidPartyPlayerSync state = new RaidPartyPlayerSync(); state.setUsername("PrivateName");
        state.setInvIds(new int[]{4151}); state.setInvQtys(new int[]{1});
        stats.sent(state); stats.received(state);
        RaidPartyDefenceSync reply = new RaidPartyDefenceSync(); reply.setRequestId("secret-id");
        reply.setHits(new RaidPartyDefenceMessage[0]); stats.sent(reply);
        assertEquals(Long.valueOf(1), stats.snapshot().get("sent.RaidPartyPlayerSync"));
        assertEquals(Long.valueOf(1), stats.snapshot().get("received.RaidPartyPlayerSync"));
        assertEquals(Long.valueOf(1), stats.snapshot().get("defence.unchanged"));
        assertTrue(stats.snapshot().get("bytes.RaidPartyPlayerSync") > 0);
        assertFalse(stats.snapshot().toString().contains("PrivateName")); assertFalse(stats.snapshot().toString().contains("secret-id"));
    }
}
