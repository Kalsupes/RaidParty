package com.github.kalsupes.raidparty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BossPingMessage extends PartyMemberMessage {
    private int x;
    private int y;
    private int plane;
    private int pingType;
    private int targetType; // 0=Tile, 1=NPC, 2=Object, 3=GroundItem
    private int targetIndex;
    private Long time;

    public BossPingMessage(int x, int y, int plane, int pingType, int targetType, int targetIndex) {
        this.x = x; this.y = y; this.plane = plane; this.pingType = pingType;
        this.targetType = targetType; this.targetIndex = targetIndex;
    }
}
