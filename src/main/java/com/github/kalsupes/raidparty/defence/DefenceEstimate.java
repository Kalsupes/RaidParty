package com.github.kalsupes.raidparty.defence;

/** Integer stat arithmetic. Values are estimates from observed hits, not server stat reads. */
public final class DefenceEstimate
{
    private final int base;
    private final int baseMagic;
    private final int baseAttack;
    private final int baseStrength;
    private final int floor;
    private final boolean demon;
    private final String name;
    private int defence;
    private int magic;
    private int attack;
    private int strength;
    private int ranged;
    private int magicDefence;
    private final boolean magicDefenceKnown;
    private boolean uncertain;
    private boolean shadowApplied;

    public DefenceEstimate(DefenceProfile p, int partySize, boolean challengeMode, int highestHp)
    {
        name = DefenceProfile.normalize(p.name);
        demon = p.demon;
        int scale = p.cox ? coxPercent(partySize) : 100;
        int hpScale = p.cox ? Math.max(55, Math.min(99, 55 + 44 * highestHp / 99)) : 99;
        int cmScale = p.cox && challengeMode ? (name.startsWith("tekton") ? (partySize < 4 ? 120 : 135) : 150) : 100;
        base = p.defence * hpScale / 99 * scale / 100 * cmScale / 100;
        boolean olmHand = p.variant != null && p.variant.toLowerCase(java.util.Locale.ROOT).contains("claw");
        boolean rightHand = olmHand && p.variant.toLowerCase(java.util.Locale.ROOT).contains("right");
        boolean defensiveMagic = olmHand || name.startsWith("tekton")
            || name.equals("abyssal portal") || name.equals("deathly ranger") || name.equals("vespula");
        int magicScale = !p.cox ? 100 : defensiveMagic ? scale
            : 100 + 7 * (int) Math.sqrt(Math.max(0, partySize - 1)) + Math.max(0, partySize - 1);
        int rawMagic = rightHand ? p.defence : p.magic;
        baseMagic = rawMagic * hpScale / 99 * magicScale / 100
            * (p.cox && challengeMode ? (defensiveMagic ? cmScale : 150) : 100) / 100 / (rightHand ? 2 : 1);
        floor = Math.min(base, p.floor());
        defence = base;
        magic = baseMagic;
        int offensiveScale = p.cox ? 100 + 7 * (int) Math.sqrt(Math.max(0, partySize - 1)) + Math.max(0, partySize - 1) : 100;
        int offensiveCm = p.cox && challengeMode ? 150 : 100;
        baseAttack = p.attack * hpScale / 99 * offensiveScale / 100 * offensiveCm / 100;
        baseStrength = p.strength * hpScale / 99 * offensiveScale / 100 * offensiveCm / 100;
        attack = baseAttack;
        strength = baseStrength;
        ranged = p.ranged * hpScale / 99 * offensiveScale / 100 * offensiveCm / 100;
        magicDefence = p.magicDefence;
        magicDefenceKnown = p.stableMagicDefence();
        uncertain = p.dynamicDefence();
    }

    static int coxPercent(int size)
    {
        int extra = Math.max(0, Math.min(100, size) - 1);
        return 100 + (int) Math.sqrt(extra) + extra * 7 / 10;
    }

    public void apply(DefenceAttack effect, int hit, boolean bloatDown)
    {
        if (hit < 0) return;
        if (floor == base) return; // Includes awakened DT2: all stat drains are absorbed.
        switch (effect)
        {
            case DRAGON_WARHAMMER:
            case ELDER_MAUL:
                int percent = hit > 0 ? (effect == DefenceAttack.ELDER_MAUL ? 35 : 30)
                    : name.startsWith("tekton") ? 5 : 0;
                defence -= defence * percent / 100;
                break;
            case BANDOS_GODSWORD:
                int drain = hit == 0 && name.startsWith("tekton") ? 10 : hit;
                if (name.equals("corporeal beast") || (name.equals("pestilent bloat") && !bloatDown)) drain *= 2;
                int remaining = Math.max(0, drain - defence);
                defence -= drain;
                if (floor == 0 && remaining > 0)
                {
                    int used = Math.min(strength, remaining); strength -= used; remaining -= used;
                    used = Math.min(attack, remaining); attack -= used; remaining -= used;
                    used = Math.min(magic, remaining); magic -= used; remaining -= used;
                    ranged = Math.max(0, ranged - remaining);
                }
                break;
            case EMBERLIGHT:
            case ARCLIGHT:
            case DARKLIGHT:
                if (hit > 0)
                {
                    int rate = demon ? (effect == DefenceAttack.EMBERLIGHT ? 15 : 10) : 5;
                    defence -= base * rate / 100 + 1;
                    if (!name.equals("yama"))
                    {
                        attack = Math.max(0, attack - (baseAttack * rate / 100 + 1));
                        strength = Math.max(0, strength - (baseStrength * rate / 100 + 1));
                    }
                }
                break;
            case BARRELCHEST_ANCHOR:
                if (defence <= hit / 10) uncertain = true; // unobserved offensive overflow can affect later Ralos hits
                defence -= hit / 10;
                break;
            case BONE_DAGGER:
            case DORGESHUUN_CROSSBOW:
                if (defence == base) defence -= hit;
                break;
            case ACCURSED_SCEPTRE:
                if (hit > 0)
                {
                    defence = Math.min(defence, base * 85 / 100);
                    magic = Math.min(magic, baseMagic * 85 / 100);
                }
                break;
            case TONALZTICS_OF_RALOS:
                defence -= Math.min(hit, 2) * (magic / 8);
                break;
            case SEERCULL: magic = Math.max(0, magic - hit); break;
            case EYE_OF_AYAK: magicDefence = Math.max(0, magicDefence - hit); break;
            case VULNERABILITY:
                // Reapplying the curse cannot repeatedly multiply the current defence.
                defence = Math.min(defence, base * (hit == 15 ? 85 : 90) / 100);
                break;
            case CURSE: defence = Math.min(defence, base * 95 / 100); break;
            case ENFEEBLE:
            case WEAKEN:
                break; // Offensive curses do not reduce Defence.
            case SHADOW_SCEPTRE:
                if (!shadowApplied)
                {
                    defence -= defence * 165 / 1000;
                    shadowApplied = true;
                }
                break;
        }
        defence = Math.max(floor, Math.min(base, defence));
    }

    public int getBase() { return base; }
    public int getDefence() { return defence; }
    public int getFloor() { return floor; }
    public int getMagic() { return magic; }
    public int getMagicDefence() { return magicDefence; }
    public boolean isMagicDefenceKnown() { return magicDefenceKnown; }
    public boolean isUncertain() { return uncertain; }
    public void markUncertain() { uncertain = true; }
}
