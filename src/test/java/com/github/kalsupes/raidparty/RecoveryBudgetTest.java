package com.github.kalsupes.raidparty;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryBudgetTest
{
    @Test public void outagesBoundWorkAtThirtySecondsFiveMinutesAndOneHour()
    {
        for (int duration : new int[]{30000, 300000, 3600000})
        {
            RecoveryBudget gate = new RecoveryBudget(); int probes = 0;
            for (int ms = 0; ms < duration; ms += 600) if (gate.probe(ms, 0)) probes++;
            assertTrue(probes <= (duration == 30000 ? 3 : duration == 300000 ? 8 : 63));
            gate.joined(); assertTrue(gate.probe(duration, 0));
        }
    }
    @Test public void repeatedWorkDoesNotExtendOrGrowTheQueue()
    {
        RecoveryBudget gate = new RecoveryBudget();
        assertTrue(gate.probe(0, 1000));
        for (int i = 0; i < 6000; i++) assertFalse(gate.probe(1, 0));
        assertTrue(gate.probe(6000, 0));
    }
}
