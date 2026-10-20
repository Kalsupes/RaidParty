package com.github.kalsupes.raidparty;

/** Coalesces peer baseline requests without extending their deadline on each request. */
final class FullSyncGate
{
    static final long COOLDOWN = 3000;
    private long lastSent = Long.MIN_VALUE;
    private String pending;
    void request(String reason) { if (pending == null) pending = reason; }
    boolean due(long now) { return pending != null && (lastSent == Long.MIN_VALUE || now - lastSent >= COOLDOWN); }
    String reason() { return pending == null ? "local" : pending; }
    void sent(long now) { lastSent = now; pending = null; }
    void reset() { lastSent = Long.MIN_VALUE; pending = null; }
}
