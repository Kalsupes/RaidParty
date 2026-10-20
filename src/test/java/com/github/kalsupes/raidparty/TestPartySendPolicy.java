package com.github.kalsupes.raidparty;

import java.util.List;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyMessage;

/** Local recording adapter for the existing client-event fixtures. */
public final class TestPartySendPolicy
{
    public static PartySendPolicy recording(PartyService party, List<PartyMessage> messages)
    {
        return new PartySendPolicy(party::isInParty, () -> party.getLocalMember() != null,
            messages::add, System::currentTimeMillis, () -> 0, NetworkStats.disabled());
    }
}
