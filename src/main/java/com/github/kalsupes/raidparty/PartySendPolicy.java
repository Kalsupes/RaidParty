package com.github.kalsupes.raidparty;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyMessage;
import net.runelite.client.party.messages.UserSync;

/** Policies around the existing PartyService, not a transport or an outbound queue. */
@Singleton
public final class PartySendPolicy
{
    private final java.util.function.BooleanSupplier inParty, memberPresent;
    private final java.util.function.Consumer<PartyMessage> sender;
    private final java.util.function.LongSupplier clock;
    private final java.util.function.IntSupplier jitter;
    private final NetworkStats stats;
    private final RecoveryBudget recovery = new RecoveryBudget();
    private boolean failed;

    public PartySendPolicy(PartyService party) { this(party, NetworkStats.disabled()); }
    @Inject public PartySendPolicy(PartyService party, NetworkStats stats)
    {
        this(party::isInParty, () -> party.getLocalMember() != null, party::send,
            System::currentTimeMillis, () -> java.util.concurrent.ThreadLocalRandom.current().nextInt(), stats);
    }
    PartySendPolicy(java.util.function.BooleanSupplier inParty, java.util.function.BooleanSupplier memberPresent,
        java.util.function.Consumer<PartyMessage> sender, java.util.function.LongSupplier clock,
        java.util.function.IntSupplier jitter, NetworkStats stats)
    {
        this.inParty = inParty; this.memberPresent = memberPresent; this.sender = sender;
        this.clock = clock; this.jitter = jitter; this.stats = stats;
    }

    public boolean ready()
    {
        return !failed && inParty.getAsBoolean() && memberPresent.getAsBoolean();
    }

    public boolean prepare()
    {
        if (!inParty.getAsBoolean()) return false; // never join a left/expired party
        if (ready()) return true;
        if (recovery.probe(clock.getAsLong(), jitter.getAsInt()))
        {
            stats.count("recovery.probe");
            try {
                UserSync request = new UserSync(); sender.accept(request); stats.sent(request);
                if (memberPresent.getAsBoolean()) failed = false;
            }
            catch (RuntimeException ignored) { /* next bounded probe uses core transport */ }
        }
        return false;
    }

    public void joined() { recovery.joined(); failed = false; }

    public boolean send(PartyMessage message)
    {
        if (!ready()) { stats.count("suppressed.notJoined"); return false; }
        try
        {
            sender.accept(message);
            stats.sent(message);
            // Core clears membership when this send discovers a failed connection.
            // A void send is not a delivery acknowledgement; recovery needs a baseline.
            return ready();
        }
        catch (RuntimeException ignored) { failed = true; stats.count("send.failure"); return false; }
    }
}
