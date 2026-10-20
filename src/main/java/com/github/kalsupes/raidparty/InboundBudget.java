package com.github.kalsupes.raidparty;

import java.util.HashMap;
import java.util.Map;

/** Bounds client-thread jobs before enqueueing. Does not replace relay byte limits. */
public final class InboundBudget
{
    private final Map<Long, MessageBudget> senders = new HashMap<>();
    public synchronized boolean take(long sender, long now)
    {
        MessageBudget budget = senders.get(sender);
        if (budget == null)
        {
            if (senders.size() >= 256) return false;
            budget = new MessageBudget(32, 50);
            senders.put(sender, budget);
        }
        return budget.take(now);
    }
    public synchronized void clear() { senders.clear(); }
    public synchronized void forget(long member) { senders.remove(member); }
}
