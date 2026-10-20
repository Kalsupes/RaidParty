package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.MessageBudget;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceHistoryTest
{
    private static RaidPartyDefenceMessage hit(int n)
    {
        RaidPartyDefenceMessage hit = new RaidPartyDefenceMessage();
        hit.setEventId("event-" + n);
        return hit;
    }

    @Test public void liveOutOfOrderAndLostEventsRecoverOnlyMissing()
    {
        DefenceHistory sender = new DefenceHistory(), receiver = new DefenceHistory();
        for (int i = 0; i < 10; i++) sender.record(hit(i));
        DefenceHistory.Peer peer = receiver.peer(7);
        for (int i : new int[]{9, 0, 2, 3, 4, 5, 6, 7, 8}) peer.observe(sender.ownHits().get(i), 1000);
        RaidPartyDefenceMessage[] missing = sender.missing(peer.epoch(), peer.low(), peer.high());
        assertEquals(1, missing.length);
        assertEquals(2, missing[0].getSequence());
        peer.observe(missing[0], 2000);
        assertEquals(0, sender.missing(peer.epoch(), peer.low(), peer.high()).length);
    }

    @Test public void lateJoinAndMaximumHistoryNeverReplayUnchanged()
    {
        for (int count : new int[]{0, 10, 128})
        {
            DefenceHistory sender = new DefenceHistory();
            for (int i = 0; i < count; i++) sender.record(hit(i));
            for (int member = 0; member < 4; member++)
            {
                DefenceHistory.Peer peer = new DefenceHistory().peer(member);
                assertEquals(count, sender.missing(null, 0, 0).length);
                peer.bind(sender.epoch());
                for (RaidPartyDefenceMessage hit : sender.missing(null, 0, 0)) peer.observe(hit, 1);
                for (int hour = 0; hour < 24; hour++)
                    assertEquals(0, sender.missing(peer.epoch(), peer.low(), peer.high()).length);
            }
        }
    }

    @Test public void lostResponseDoesNotAdvanceCursorAndCanBeRetried()
    {
        DefenceHistory sender = new DefenceHistory(); sender.record(hit(1));
        DefenceHistory.Peer peer = new DefenceHistory().peer(2);
        String first = peer.request(1000, 0);
        assertTrue(peer.matches(first, 15000));
        assertFalse(peer.matches(first, 17000));
        assertTrue(peer.due(17000));
        String retry = peer.request(17000, 0);
        assertFalse(peer.matches(first, 17000));
        assertTrue(peer.matches(retry, 17000));
        assertEquals(1, sender.missing(peer.epoch(), peer.low(), peer.high()).length);
        peer.observe(sender.ownHits().get(0), 17001);
        peer.acknowledged(17001, 0);
        assertFalse(peer.due(17002));
        assertEquals(0, sender.missing(peer.epoch(), peer.low(), peer.high()).length);
    }

    @Test public void resetNpcReuseAndSceneReplacementInvalidateOutstandingChallenges()
    {
        DefenceHistory before = new DefenceHistory();
        String oldRequest = before.peer(2).request(1000, 0);
        DefenceHistory after = new DefenceHistory();
        assertNotEquals(before.epoch(), after.epoch());
        assertFalse(after.peer(2).matches(oldRequest, 1001));
        assertEquals(0, after.size());
    }

    @Test public void senderResetAndReconnectCannotRollCursorBackToRetiredStream()
    {
        DefenceHistory old = new DefenceHistory(), next = new DefenceHistory();
        old.record(hit(1)); next.record(hit(2));
        DefenceHistory.Peer peer = new DefenceHistory().peer(2);
        assertTrue(peer.observe(old.ownHits().get(0), 1));
        assertTrue(peer.observe(next.ownHits().get(0), 2));
        assertFalse(peer.observe(old.ownHits().get(0), 3));
        peer.acknowledged(3, 0); peer.recover();
        assertTrue(peer.due(4));
        assertEquals(0, next.missing(peer.epoch(), peer.low(), peer.high()).length);
    }

    @Test public void longEncounterMemoryAndGlobalBudgetAreBounded()
    {
        DefenceHistory history = new DefenceHistory();
        for (int i = 0; i < 10000; i++) history.record(hit(i));
        assertEquals(128, history.size());
        assertTrue(history.truncated());
        for (int i = 0; i < 64; i++) assertNotNull(history.peer(i));
        assertNull(history.peer(64));
        MessageBudget budget = new MessageBudget(4, 1000);
        int sent = 0;
        for (long ms = 0; ms < 3600000; ms += 10) if (budget.take(ms)) sent++;
        assertTrue(sent <= 3604);
    }
}
