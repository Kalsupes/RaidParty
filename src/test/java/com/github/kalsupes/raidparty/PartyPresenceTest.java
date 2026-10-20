package com.github.kalsupes.raidparty;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartyPresenceTest {
    private final Instant start = Instant.parse("2026-09-26T12:00:00Z");

    @Test public void silentMemberLosesLiveStatusAndExpiresAtFifteenMinutes() {
        PartyPresence p = new PartyPresence();
        assertEquals(PartyPresence.State.ONLINE, p.receive(1, true, start));
        assertEquals(PartyPresence.State.ONLINE, p.state(1, start.plusSeconds(59)));
        assertEquals(PartyPresence.State.STALE, p.state(1, start.plusSeconds(60)));
        assertEquals(PartyPresence.State.STALE, p.state(1, start.plusSeconds(899)));
        assertEquals(PartyPresence.State.EXPIRED, p.state(1, start.plusSeconds(900)));
    }

    @Test public void logoutDeltasCannotExtendDeadlineOrRestoreLiveData() {
        PartyPresence p = new PartyPresence();
        p.receive(1, true, start);
        p.receive(1, false, start.plusSeconds(10));
        assertEquals(PartyPresence.State.OFFLINE, p.receive(1, null, start.plusSeconds(30)));
        p.receive(1, false, start.plusSeconds(800));
        assertEquals(PartyPresence.State.EXPIRED, p.state(1, start.plusSeconds(910)));
    }

    @Test public void loginBeforeDeadlineResumesButLatePacketsRequireNewMembership() {
        PartyPresence p = new PartyPresence();
        p.receive(1, false, start);
        assertEquals(PartyPresence.State.ONLINE, p.receive(1, true, start.plusSeconds(899)));
        assertEquals(PartyPresence.State.EXPIRED, p.receive(1, true, start.plusSeconds(1799)));
        p.joined(1, start.plusSeconds(1800));
        assertEquals(PartyPresence.State.ONLINE, p.receive(1, true, start.plusSeconds(1801)));
    }

    @Test public void heartbeatKeepsAnUnchangingInventoryOnline() {
        PartyPresence p = new PartyPresence();
        for (int seconds = 0; seconds < 3600; seconds += 15) {
            assertEquals(PartyPresence.State.ONLINE, p.receive(1, true, start.plusSeconds(seconds)));
        }
    }

    @Test public void unknownMembersAreNeverAssumedLiveAndPartyChangeClearsExpiry() {
        PartyPresence p = new PartyPresence();
        assertEquals(PartyPresence.State.WAITING, p.state(1, start));
        assertEquals(PartyPresence.State.EXPIRED, p.state(1, start.plusSeconds(900)));
        p.clear();
        assertEquals(PartyPresence.State.ONLINE, p.receive(1, true, start.plusSeconds(901)));
    }

    @Test public void localTimeoutIncludesExactBoundaryAndSuspendedClients() {
        assertFalse(PartyPresence.expiredSince(null, start));
        assertFalse(PartyPresence.expiredSince(start, start.plusSeconds(899)));
        assertTrue(PartyPresence.expiredSince(start, start.plusSeconds(900)));
        assertTrue(PartyPresence.expiredSince(start, start.plusSeconds(7200)));
    }

    @Test public void legacyClientsDoNotPromiseHeartbeatsButStillReportLogout() {
        PartyPresence p = new PartyPresence();
        p.receive(1, true, start, false);
        assertEquals(PartyPresence.State.ONLINE, p.state(1, start.plusSeconds(3600)));
        p.receive(1, false, start.plusSeconds(3600), false);
        assertEquals(PartyPresence.State.OFFLINE, p.state(1, start.plusSeconds(4499)));
        assertEquals(PartyPresence.State.EXPIRED, p.state(1, start.plusSeconds(4500)));
    }
}
