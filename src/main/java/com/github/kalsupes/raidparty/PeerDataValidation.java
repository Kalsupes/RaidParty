package com.github.kalsupes.raidparty;

import net.runelite.api.Skill;
import net.runelite.client.util.Text;

public final class PeerDataValidation
{
    private PeerDataValidation() { }
    public static boolean text(String s, int max) { return s == null || s.length() <= max; }
    public static String clean(String s)
    {
        return s == null ? "" : Text.JAGEX_PRINTABLE_CHAR_MATCHER.retainFrom(Text.removeTags(s));
    }
    private static boolean range(Integer v, int min, int max) { return v == null || v >= min && v <= max; }
    private static boolean array(int[] a, int max, int minValue, int maxValue)
    {
        if (a == null) return true;
        if (a.length > max) return false;
        for (int v : a) if (v < minValue || v > maxValue) return false;
        return true;
    }
    private static boolean pairs(int[] ids, int[] amounts, int max)
    {
        // Older native peers can send the two nullable columns independently.
        return (ids == null || amounts == null || ids.length == amounts.length)
            && array(ids, max, -1, 1000000) && array(amounts, max, 0, Integer.MAX_VALUE);
    }
    public static boolean nativeState(RaidPartyPlayerSync s)
    {
        return s != null && text(s.getUsername(), 32)
            && pairs(s.getInvIds(), s.getInvQtys(), 28) && pairs(s.getEqpIds(), s.getEqpQtys(), 16)
            && pairs(s.getRunePouchIds(), s.getRunePouchQtys(), 4)
            && array(s.getSkillLevels(), Skill.values().length * 2, 0, 1000)
            && (s.getSkillLevels() == null || s.getSkillLevels().length % 2 == 0)
            && array(s.getSkillXps(), Skill.values().length, 0, 200000000)
            && range(s.getRawHp(), 0, 1000) && range(s.getRawMaxHp(), 0, 1000)
            && range(s.getRawPrayer(), 0, 1000) && range(s.getRawMaxPrayer(), 0, 1000)
            && range(s.getRawSpec(), 0, 1000) && range(s.getRawRun(), 0, 10000)
            && range(s.getRawWorld(), 0, 65535) && range(s.getRawCombatLevel(), 0, 1000)
            && range(s.getRawReadyState(), 0, 2) && range(s.getRawSpellbook(), 0, 3)
            && range(s.getRawAccountType(), 0, 64) && range(s.getRawTotalLevel(), 0, 10000)
            && range(s.getRawStamina(), 0, 1000000) && range(s.getRawPoison(), -1000000, 1000000)
            && range(s.getRawDisease(), 0, 1000000) && range(s.getRawQuiverAmmoId(), -1, 1000000)
            && range(s.getRawQuiverAmmoQty(), 0, Integer.MAX_VALUE);
    }
    public static boolean ping(BossPingMessage p)
    {
        return p != null && p.getX() >= 0 && p.getX() <= 65535 && p.getY() >= 0 && p.getY() <= 65535
            && p.getPlane() >= 0 && p.getPlane() <= 3 && p.getPingType() >= 0 && p.getPingType() <= 3
            && p.getTargetType() >= 0 && p.getTargetType() <= 3 && p.getTargetIndex() >= -1
            && p.getTargetIndex() <= 1000000;
    }
}
