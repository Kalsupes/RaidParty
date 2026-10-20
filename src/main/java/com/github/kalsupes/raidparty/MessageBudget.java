package com.github.kalsupes.raidparty;

/** Small client-thread token bucket; supplied time makes outage tests deterministic. */
public final class MessageBudget
{
    private final int capacity;
    private final long refillMillis;
    private double tokens;
    private long last = -1;

    public MessageBudget(int capacity, long refillMillis)
    {
        this.capacity = capacity;
        this.refillMillis = refillMillis;
        tokens = capacity;
    }

    public boolean take(long now)
    {
        if (last >= 0) tokens = Math.min(capacity, tokens + Math.max(0, now - last) / (double) refillMillis);
        last = now;
        if (tokens < 1) return false;
        tokens--;
        return true;
    }
}
