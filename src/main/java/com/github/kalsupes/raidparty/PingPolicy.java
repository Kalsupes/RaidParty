package com.github.kalsupes.raidparty;

import java.util.HashMap;
import java.util.Map;

final class PingPolicy
{
    private final MessageBudget outgoing = new MessageBudget(3, 500);
    private final Map<Long, MessageBudget> incoming = new HashMap<>();
    boolean outgoing(long now) { return outgoing.take(now); }
    boolean incoming(long member, long now)
    {
        MessageBudget budget = incoming.get(member);
        if (budget == null)
        {
            if (incoming.size() >= 256) return false;
            budget = new MessageBudget(4, 500); incoming.put(member, budget);
        }
        return budget.take(now);
    }
    void clear() { incoming.clear(); }
    static boolean fresh(Long sent, long queued, long now)
    {
        return now - queued <= 2000 && (sent == null || sent > 0 && sent >= now - 2000 && sent <= now + 5000);
    }
}
