package com.github.kalsupes.raidparty;

/** Presence/capability metadata alone is not a changed player state. */
final class PlayerStateDelta
{
    static boolean hasChanges(RaidPartyPlayerSync s)
    {
        return s.getRawHp() != null || s.getRawMaxHp() != null || s.getRawPrayer() != null
            || s.getRawMaxPrayer() != null || s.getRawSpec() != null || s.getRawRun() != null
            || s.getRawWorld() != null || s.getRawCombatLevel() != null || s.getUsername() != null
            || s.getRawAccountType() != null || s.getRawReadyState() != null || s.getRawActivePrayers() != null
            || s.getSkillLevels() != null || s.getSkillXps() != null || s.getLootRule() != null
            || s.getRawStamina() != null || s.getRawPoison() != null || s.getRawDisease() != null
            || s.getRawTotalLevel() != null || s.getRawVengeanceActive() != null
            || s.getInvIds() != null || s.getInvQtys() != null || s.getEqpIds() != null || s.getEqpQtys() != null
            || s.getRunePouchIds() != null || s.getRunePouchQtys() != null
            || s.getRawQuiverAmmoId() != null || s.getRawQuiverAmmoQty() != null
            || s.getRawAvailablePrayers() != null || s.getRawUnlockedPrayers() != null
            || s.getRawSpellbook() != null || s.getRawInRaid() != null;
    }
}
