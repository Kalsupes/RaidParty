package com.github.kalsupes.raidparty;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Custom RaidParty payload for broadcasting live player state to the lobby.
 * Uses optimized short SerializedName keys and nullable wrappers to support delta sync diffing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RaidPartyPlayerSync extends PartyMemberMessage {
    @SerializedName(value = "hp", alternate = {"hp", "hitpoints"})
    private Integer hp;
    
    @SerializedName(value = "mhp", alternate = {"maxHp", "maxHitpoints"})
    private Integer maxHp;
    
    @SerializedName(value = "pr", alternate = {"prayer"})
    private Integer prayer;
    
    @SerializedName(value = "mpr", alternate = {"maxPrayer"})
    private Integer maxPrayer;
    
    @SerializedName(value = "sp", alternate = {"spec", "specialAttack"})
    private Integer spec;
    
    @SerializedName(value = "r", alternate = {"run", "runEnergy"})
    private Integer run;

    // Parallel arrays for Inventory and Equipment
    @SerializedName(value = "ii", alternate = {"invIds"})
    private int[] invIds;
    
    @SerializedName(value = "iq", alternate = {"invQtys"})
    private int[] invQtys;

    @SerializedName(value = "ei", alternate = {"eqpIds"})
    private int[] eqpIds;
    
    @SerializedName(value = "eq", alternate = {"eqpQtys"})
    private int[] eqpQtys;

    // Extended fields
    @SerializedName(value = "w", alternate = {"world"})
    private Integer world;
    
    @SerializedName(value = "cl", alternate = {"combatLevel"})
    private Integer combatLevel;
    
    @SerializedName(value = "u", alternate = {"username"})
    private String username;

    // Ready Check: 0=unchecked, 1=ready, 2=not ready
    @SerializedName(value = "rs", alternate = {"readyState"})
    private Integer readyState;

    // Active prayer IDs (ordinals packed into bits)
    @SerializedName(value = "ap", alternate = {"activePrayers"})
    private Long activePrayers;

    // Skill levels: [boosted, real] for each Skill ordinal
    @SerializedName(value = "sl", alternate = {"skillLevels"})
    private int[] skillLevels;
    
    // Skill experiences for each Skill ordinal
    @SerializedName(value = "sx", alternate = {"skillXps"})
    private int[] skillXps;

    // --- Phase 6: Loot Distribution ---
    @SerializedName(value = "lr", alternate = {"lootRule"})
    private LootRule lootRule;

    // --- Phase 7: Feature Parity ---
    @SerializedName(value = "st", alternate = {"stamina"})
    private Integer stamina;
    
    @SerializedName(value = "po", alternate = {"poison"})
    private Integer poison;
    
    @SerializedName(value = "di", alternate = {"disease"})
    private Integer disease;
    
    @SerializedName(value = "tl", alternate = {"totalLevel"})
    private Integer totalLevel;
    
    @SerializedName(value = "va", alternate = {"vengeanceActive"})
    private Boolean vengeanceActive;

    // Rune Pouch contents (parallel arrays)
    @SerializedName(value = "ri", alternate = {"runePouchIds"})
    private int[] runePouchIds;
    
    @SerializedName(value = "rq", alternate = {"runePouchQtys"})
    private int[] runePouchQtys;

    // Dizana's Quiver
    @SerializedName(value = "qi", alternate = {"quiverAmmoId"})
    private Integer quiverAmmoId;
    
    @SerializedName(value = "qq", alternate = {"quiverAmmoQty"})
    private Integer quiverAmmoQty;

    // Prayer states (ordinals packed into bits)
    @SerializedName(value = "avp", alternate = {"availablePrayers"})
    private Long availablePrayers;
    
    @SerializedName(value = "up", alternate = {"unlockedPrayers"})
    private Long unlockedPrayers;

    // Spellbook: 0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus
    @SerializedName(value = "sb", alternate = {"spellbook"})
    private Integer spellbook;

    // --- Null-safe primitive getters for UI and safe null-checking ---
    public int getHp() { return hp != null ? hp : 0; }
    public int getMaxHp() { return maxHp != null ? maxHp : 0; }
    public int getPrayer() { return prayer != null ? prayer : 0; }
    public int getMaxPrayer() { return maxPrayer != null ? maxPrayer : 0; }
    public int getSpec() { return spec != null ? spec : 0; }
    public int getRun() { return run != null ? run : 0; }
    public int getWorld() { return world != null ? world : 0; }
    public int getCombatLevel() { return combatLevel != null ? combatLevel : 0; }
    public int getReadyState() { return readyState != null ? readyState : 0; }
    public long getActivePrayers() { return activePrayers != null ? activePrayers : 0L; }
    public int getStamina() { return stamina != null ? stamina : 0; }
    public int getPoison() { return poison != null ? poison : 0; }
    public int getDisease() { return disease != null ? disease : 0; }
    public int getTotalLevel() { return totalLevel != null ? totalLevel : 0; }
    public boolean getVengeanceActive() { return vengeanceActive != null && vengeanceActive; }
    public boolean isVengeanceActive() { return vengeanceActive != null && vengeanceActive; }
    public int getQuiverAmmoId() { return quiverAmmoId != null ? quiverAmmoId : -1; }
    public int getQuiverAmmoQty() { return quiverAmmoQty != null ? quiverAmmoQty : 0; }
    public long getAvailablePrayers() { return availablePrayers != null ? availablePrayers : 0L; }
    public long getUnlockedPrayers() { return unlockedPrayers != null ? unlockedPrayers : 0L; }
    public int getSpellbook() { return spellbook != null ? spellbook : 0; }

    // --- Raw nullable getters for delta diff checks ---
    public Integer getRawHp() { return hp; }
    public Integer getRawMaxHp() { return maxHp; }
    public Integer getRawPrayer() { return prayer; }
    public Integer getRawMaxPrayer() { return maxPrayer; }
    public Integer getRawSpec() { return spec; }
    public Integer getRawRun() { return run; }
    public Integer getRawWorld() { return world; }
    public Integer getRawCombatLevel() { return combatLevel; }
    public Integer getRawReadyState() { return readyState; }
    public Long getRawActivePrayers() { return activePrayers; }
    public Integer getRawStamina() { return stamina; }
    public Integer getRawPoison() { return poison; }
    public Integer getRawDisease() { return disease; }
    public Integer getRawTotalLevel() { return totalLevel; }
    public Boolean getRawVengeanceActive() { return vengeanceActive; }
    public Integer getRawQuiverAmmoId() { return quiverAmmoId; }
    public Integer getRawQuiverAmmoQty() { return quiverAmmoQty; }
    public Long getRawAvailablePrayers() { return availablePrayers; }
    public Long getRawUnlockedPrayers() { return unlockedPrayers; }
    public Integer getRawSpellbook() { return spellbook; }
}
