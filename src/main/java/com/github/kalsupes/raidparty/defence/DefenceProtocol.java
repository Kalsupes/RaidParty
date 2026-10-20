package com.github.kalsupes.raidparty.defence;

/** Validate before resolving NPCs or allocating hit history for an incoming party message. */
public final class DefenceProtocol
{
    private DefenceProtocol() { }

    public static boolean valid(RaidPartyDefenceMessage message, int world, long now)
    {
        return valid(message, world, now, 15000);
    }

    public static boolean valid(RaidPartyDefenceMessage message, int world, long now, long maxAge)
    {
        if (message == null || message.getVersion() != 1 || message.getWorld() != world
            || message.getEventId() == null || message.getEventId().length() > 80
            || message.getEventId().isEmpty() || message.getAttack() == null
            || message.getAttack().length() > 32 || message.getTime() <= 0
            || message.getEpoch() != null && !DefenceHistory.validStream(message.getEpoch(), message.getSequence())
            || message.getNpcIndex() < 0 || message.getNpcIndex() > 65535
            || message.getNpcId() < 0 || message.getNpcId() > 100000
            || message.getPlane() < 0 || message.getPlane() > 3
            || message.getX() < 0 || message.getX() > 65535 || message.getY() < 0 || message.getY() > 65535
            || message.getAmount() < 0 || message.getAmount() > 2000
            || message.getTime() < now - maxAge || message.getTime() > now + 5000)
        {
            return false;
        }
        try
        {
            DefenceAttack attack = DefenceAttack.valueOf(message.getAttack());
            return attack != DefenceAttack.TONALZTICS_OF_RALOS || message.getAmount() <= 2;
        }
        catch (IllegalArgumentException ex)
        {
            return false;
        }
    }
}
