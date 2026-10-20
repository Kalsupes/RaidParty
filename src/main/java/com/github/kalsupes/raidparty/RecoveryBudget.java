package com.github.kalsupes.raidparty;

/** Bounds plugin work when core no longer reports local membership. No message queue. */
final class RecoveryBudget
{
    private long next;
    private long delay = 5000;
    boolean probe(long now, int jitter)
    {
        if (now < next) return false;
        next = now + delay + Math.floorMod(jitter, 1001);
        delay = Math.min(60000, delay * 2);
        return true;
    }
    void joined() { next = 0; delay = 5000; }
}
