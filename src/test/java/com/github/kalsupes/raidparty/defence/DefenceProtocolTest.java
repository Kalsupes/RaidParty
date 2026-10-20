package com.github.kalsupes.raidparty.defence;

import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceProtocolTest
{
    private RaidPartyDefenceMessage message()
    {
        RaidPartyDefenceMessage m = new RaidPartyDefenceMessage();
        m.setEventId("unique-observation"); m.setWorld(302); m.setTime(100000);
        m.setNpcIndex(1); m.setNpcId(14176); m.setX(100); m.setY(200);
        m.setAttack("EMBERLIGHT"); m.setAmount(1);
        return m;
    }

    @Test public void acceptsValidHitButRejectsWrongWorldAndStalePacket()
    {
        assertTrue(DefenceProtocol.valid(message(), 302, 100000));
        assertFalse(DefenceProtocol.valid(message(), 303, 100000));
        assertFalse(DefenceProtocol.valid(message(), 302, 116000));
        assertFalse(DefenceProtocol.valid(message(), 302, 90000));
        assertTrue(DefenceProtocol.valid(message(), 302, 116000, 900000));
    }

    @Test public void rejectsMalformedAndUnboundedData()
    {
        assertFalse(DefenceProtocol.valid(null, 302, 100000));
        RaidPartyDefenceMessage m = message(); m.setAttack("NOT_A_WEAPON");
        assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setAmount(-1); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setAmount(Integer.MAX_VALUE); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setEventId(null); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setEventId("x".repeat(81)); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setNpcIndex(-1); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setPlane(4); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setVersion(2); assertFalse(DefenceProtocol.valid(m, 302, 100000));
        m = message(); m.setAttack("TONALZTICS_OF_RALOS"); m.setAmount(3);
        assertFalse(DefenceProtocol.valid(m, 302, 100000));
    }

    @Test public void jsonRoundTripPreservesHitContextAndSyncPayload()
    {
        RaidPartyDefenceMessage m = message(); m.setBloatDown(true); m.setMemberId(42);
        RaidPartyDefenceMessage decoded = RuneLiteAPI.GSON.fromJson(RuneLiteAPI.GSON.toJson(m), RaidPartyDefenceMessage.class);
        assertEquals(m.getEventId(), decoded.getEventId());
        assertEquals(m.getAttack(), decoded.getAttack());
        assertEquals(m.getAmount(), decoded.getAmount());
        assertEquals(m.getNpcId(), decoded.getNpcId());
        assertTrue(decoded.isBloatDown());
        assertEquals(0, decoded.getMemberId()); // transport supplies the authenticated sender ID
        RaidPartyDefenceSync sync = new RaidPartyDefenceSync();
        sync.setRequestId("request"); sync.setHits(new RaidPartyDefenceMessage[]{m});
        RaidPartyDefenceSync reply = RuneLiteAPI.GSON.fromJson(RuneLiteAPI.GSON.toJson(sync), RaidPartyDefenceSync.class);
        assertEquals("request", reply.getRequestId()); assertEquals(m.getEventId(), reply.getHits()[0].getEventId());
    }

    @Test public void duplicateAndEchoedHitIsAppliedOnlyOnce()
    {
        DefenceEncounter e = encounter();
        assertTrue(e.add("same", 100, DefenceAttack.BANDOS_GODSWORD, 50, false));
        assertFalse(e.add("same", 101, DefenceAttack.BANDOS_GODSWORD, 50, false));
        assertEquals(150, e.getEstimate().getDefence());
        assertEquals(1, e.getHitCount());
    }

    @Test public void lateMessagesReplayInOriginalOrder()
    {
        DefenceEncounter first = encounter(); DefenceEncounter second = encounter();
        first.add("bgs", 100, DefenceAttack.BANDOS_GODSWORD, 50, false);
        first.add("dwh", 200, DefenceAttack.DRAGON_WARHAMMER, 1, false);
        second.add("dwh", 200, DefenceAttack.DRAGON_WARHAMMER, 1, false);
        second.add("bgs", 100, DefenceAttack.BANDOS_GODSWORD, 50, false);
        assertEquals(105, first.getEstimate().getDefence());
        assertEquals(first.getEstimate().getDefence(), second.getEstimate().getDefence());
    }

    @Test public void independentNpcsDoNotShareDrainsAndHistoryIsBounded()
    {
        DefenceEncounter first = encounter(); DefenceEncounter second = encounter();
        first.add("hit", 100, DefenceAttack.BANDOS_GODSWORD, 50, false);
        assertEquals(200, second.getEstimate().getDefence());
        for (int i = 0; i < 600; i++) first.add("hit" + i, 200 + i, DefenceAttack.DRAGON_WARHAMMER, 0, false);
        assertEquals(512, first.getHitCount());
        assertTrue(first.getEstimate().isUncertain());
    }

    private DefenceEncounter encounter()
    {
        DefenceProfile p = new DefenceProfile(); p.name = "Test"; p.defence = 200; p.magic = 100;
        return new DefenceEncounter(p, 1, false, 99);
    }
}
