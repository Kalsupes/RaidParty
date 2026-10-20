package com.github.kalsupes.raidparty.defence;

import net.runelite.client.plugins.specialcounter.SpecialWeapon;

/** The effect is separate from the item: only confirmed special attacks reach the calculator. */
public enum DefenceAttack
{
    DRAGON_WARHAMMER, ELDER_MAUL, BANDOS_GODSWORD, ARCLIGHT, DARKLIGHT, EMBERLIGHT,
    BARRELCHEST_ANCHOR, BONE_DAGGER, DORGESHUUN_CROSSBOW, ACCURSED_SCEPTRE,
    TONALZTICS_OF_RALOS, SEERCULL, EYE_OF_AYAK, VULNERABILITY, ENFEEBLE, WEAKEN, CURSE,
    SHADOW_SCEPTRE;

    public static DefenceAttack fromWeapon(SpecialWeapon weapon)
    {
        try
        {
            return valueOf(weapon.name());
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }

    public String displayName()
    {
        return name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    }
}
