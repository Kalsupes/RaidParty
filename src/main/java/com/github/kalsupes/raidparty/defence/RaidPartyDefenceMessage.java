package com.github.kalsupes.raidparty.defence;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Versioned, per-hit protocol. Never broadcasts an inferred total as an authoritative value. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RaidPartyDefenceMessage extends PartyMemberMessage
{
    private int version = 1;
    private String eventId;
    private long time;
    private int world;
    private int npcIndex;
    private int npcId;
    private int x;
    private int y;
    private int plane;
    private String attack;
    private int amount;
    private boolean bloatDown;
    // Optional on the v1 live message, so older clients still receive live hits.
    private String epoch;
    private int sequence;
}
