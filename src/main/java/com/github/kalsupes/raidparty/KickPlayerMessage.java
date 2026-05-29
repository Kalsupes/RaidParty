package com.github.kalsupes.raidparty;

import net.runelite.client.party.messages.PartyMemberMessage;

public class KickPlayerMessage extends PartyMemberMessage {
    private final long targetMemberId;
    private final String targetName;
    private final String kickerName;

    public KickPlayerMessage(long targetMemberId, String targetName, String kickerName) {
        this.targetMemberId = targetMemberId;
        this.targetName = targetName;
        this.kickerName = kickerName;
    }

    public long getTargetMemberId() {
        return targetMemberId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getKickerName() {
        return kickerName;
    }
}
