package com.example;

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
}
