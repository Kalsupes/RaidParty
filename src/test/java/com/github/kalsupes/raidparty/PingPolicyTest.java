package com.github.kalsupes.raidparty;
import org.junit.Test;
import static org.junit.Assert.*;
public class PingPolicyTest
{
    @Test public void allowsIntentionalConsecutivePingsAndBoundsKeyRepeat()
    {
        PingPolicy p = new PingPolicy();
        assertTrue(p.outgoing(0)); assertTrue(p.outgoing(0)); assertTrue(p.outgoing(0));
        assertFalse(p.outgoing(0)); assertTrue(p.outgoing(500)); assertFalse(p.outgoing(500));
    }
    @Test public void modernPingsAndQueuedLegacyPingsExpire()
    {
        assertTrue(PingPolicy.fresh(10000L, 10000, 11000));
        for (long delay : new long[]{30000,300000,3600000})
        {
            assertFalse(PingPolicy.fresh(10000L, 10000 + delay, 10000 + delay));
            assertFalse(PingPolicy.fresh(null, 10000, 10000 + delay));
        }
        assertTrue(PingPolicy.fresh(null, 10000, 10001)); // old peers have no sender timestamp
        assertFalse(PingPolicy.fresh(50000L, 10000, 10000));
    }
    @Test public void perSenderBudgetDoesNotSuppressOtherMembers()
    {
        PingPolicy p = new PingPolicy(); for (int i = 0; i < 4; i++) assertTrue(p.incoming(1,0));
        assertFalse(p.incoming(1,0)); assertTrue(p.incoming(2,0));
    }
}
