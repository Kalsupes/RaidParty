package com.github.kalsupes.raidparty;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullSyncGateTest
{
    @Test public void initialDiscoveryIsImmediateAndJoinsCoalesce()
    {
        FullSyncGate gate = new FullSyncGate();
        gate.request("member-join"); gate.request("user-sync"); gate.request("member-join");
        assertTrue(gate.due(0)); assertEquals("member-join", gate.reason());
        gate.sent(0); assertFalse(gate.due(100));
        gate.request("user-sync"); assertFalse(gate.due(2999)); assertTrue(gate.due(3000));
    }
    @Test public void repeatedRequestsHaveBoundedWorkWithoutStarvingRecovery()
    {
        FullSyncGate gate = new FullSyncGate(); int baselines = 0;
        for (int now = 0; now < 3600000; now += 600)
        {
            gate.request("user-sync");
            if (gate.due(now)) { gate.sent(now); baselines++; }
        }
        assertEquals(1200, baselines);
        assertTrue(gate.due(3600000)); gate.sent(3600000);
        assertFalse(gate.due(7200000)); // no continuing response loop after traffic stops
    }
    @Test public void newPartyAndReconnectKeepDiscovery()
    {
        FullSyncGate gate = new FullSyncGate(); gate.request("initial"); gate.sent(100);
        gate.request("reconnect"); assertTrue(gate.due(3100)); gate.sent(3100);
        gate.reset(); gate.request("new-party"); assertTrue(gate.due(3101));
    }
}
