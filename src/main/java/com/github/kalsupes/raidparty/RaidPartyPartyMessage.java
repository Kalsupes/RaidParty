package com.github.kalsupes.raidparty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Party message for custom hotkey chat messages.
 * Displayed as "[RaidParty] PlayerName: Message" in game chat.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RaidPartyPartyMessage extends PartyMemberMessage {
    private String senderName;
    private String message;
}
