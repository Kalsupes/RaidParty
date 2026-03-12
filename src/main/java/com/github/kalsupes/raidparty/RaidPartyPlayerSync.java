package com.github.kalsupes.raidparty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Custom RaidParty payload for broadcasting live player state to the lobby.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RaidPartyPlayerSync extends PartyMemberMessage {
    private int hp;
    private int maxHp;
    private int prayer;
    private int maxPrayer;
    private int spec;
    private int run;

    // Parallel arrays for Inventory and Equipment
    private int[] invIds;
    private int[] invQtys;

    private int[] eqpIds;
    private int[] eqpQtys;

    // Extended fields
    private int world;
    private int combatLevel;
    private String username;

    // Ready Check: 0=unchecked, 1=ready, 2=not ready
    private int readyState;

    // Active prayer IDs (ordinals packed into bits)
    private int activePrayers;

    // Skill levels: [boosted, real] for each Skill ordinal
    // Index = Skill.ordinal() * 2 (boosted), Skill.ordinal() * 2 + 1 (real)
    private int[] skillLevels;

    // --- Phase 5: Raid Metrics ---
    private int toaPoints;
    private int coxPoints;
    private int tobDeaths;

    // --- Phase 6: Loot Distribution ---
    private LootRule lootRule;

    // --- Phase 7: Feature Parity ---
    private int stamina; // Varbits.STAMINA_EFFECT (0 or ticks remaining)
    private int poison; // VarPlayer.POISON
    private int disease; // VarPlayer.DISEASE_VALUE
    private int totalLevel; // client.getTotalLevel()

    // Rune Pouch contents (parallel arrays)
    private int[] runePouchIds;
    private int[] runePouchQtys;

    // Dizana's Quiver
    private int quiverAmmoId; // VarPlayer.DIZANAS_QUIVER_ITEM_ID
    private int quiverAmmoQty; // VarPlayer.DIZANAS_QUIVER_ITEM_COUNT

    // Prayer states (ordinals packed into bits)
    private int availablePrayers; // Prayers player has the level for
    private int unlockedPrayers; // Prayers unlocked via quests/rewards

    // Spellbook: 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus
    private int spellbook;
}
