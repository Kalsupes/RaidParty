package com.github.kalsupes.raidparty;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.party.messages.PartyMessage;
import net.runelite.client.party.messages.UserSync;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartySendPolicyTest
{
    @Test public void outageRetainsNoMessagesAndRecoverySendsOnlyCurrentSnapshot()
    {
        for (int duration : new int[]{30000, 300000, 3600000})
        {
            boolean[] joined = {false}; long[] now = {0}; List<PartyMessage> sent = new ArrayList<>();
            PartySendPolicy policy = new PartySendPolicy(() -> true, () -> joined[0], sent::add,
                () -> now[0], () -> 0, NetworkStats.disabled());
            RaidPartyPlayerSync latest = null;
            for (; now[0] < duration; now[0] += 600)
            {
                latest = new RaidPartyPlayerSync(); latest.setRun((int) (now[0] % 10000));
                if (policy.prepare()) policy.send(latest);
                assertFalse(policy.send(new BossPingMessage(3200,3200,0,0,0,0)));
            }
            assertTrue(sent.size() <= (duration == 30000 ? 3 : duration == 300000 ? 8 : 63));
            for (PartyMessage message : sent) assertTrue(message instanceof UserSync);
            sent.clear(); joined[0] = true; policy.joined();
            assertTrue(policy.prepare()); assertTrue(policy.send(latest));
            assertEquals(1, sent.size()); assertSame(latest, sent.get(0));
        }
    }
    @Test public void intentionallyLeftPartyNeverProbesOrSends()
    {
        List<PartyMessage> sent = new ArrayList<>();
        PartySendPolicy policy = new PartySendPolicy(() -> false, () -> false, sent::add,
            () -> 3600000, () -> 0, NetworkStats.disabled());
        assertFalse(policy.prepare()); assertFalse(policy.send(new UserSync())); assertTrue(sent.isEmpty());
    }
    @Test public void coreMembershipLossDuringSendRequiresRecovery()
    {
        boolean[] joined = {true};
        PartySendPolicy policy = new PartySendPolicy(() -> true, () -> joined[0], message -> joined[0] = false,
            () -> 0, () -> 0, NetworkStats.disabled());
        assertFalse(policy.send(new RaidPartyPlayerSync())); assertFalse(policy.ready());
    }
    @Test public void exceptionsAreBoundedEvenIfCoreMembershipStillLooksPresent()
    {
        long[] now = {0}; int[] attempts = {0};
        PartySendPolicy policy = new PartySendPolicy(() -> true, () -> true,
            message -> { attempts[0]++; throw new IllegalStateException("simulated outage"); },
            () -> now[0], () -> 0, NetworkStats.disabled());
        for (; now[0] < 3600000; now[0] += 600)
            if (policy.prepare()) policy.send(new RaidPartyPlayerSync());
        assertTrue(attempts[0] <= 64);
    }
}
