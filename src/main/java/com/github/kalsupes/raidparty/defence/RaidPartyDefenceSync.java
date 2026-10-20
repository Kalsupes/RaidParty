package com.github.kalsupes.raidparty.defence;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Requests/replies carry only each sender's own observations for the visible encounter. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RaidPartyDefenceSync extends PartyMemberMessage
{
    private int version = 1;
    private String requestId;
    private long time;
    private int world;
    private int npcIndex;
    private int npcId;
    private int x;
    private int y;
    private int plane;
    // null = request; non-null = response (at most 128 observations).
    private RaidPartyDefenceMessage[] hits;
    // Version 2: one intended responder; other peers discard before game work.
    private Long recipient;
    private String epoch;
    private long knownLow;
    private long knownHigh;
    private Boolean truncated;
}
