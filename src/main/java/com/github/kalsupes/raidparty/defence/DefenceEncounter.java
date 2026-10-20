package com.github.kalsupes.raidparty.defence;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Replays a bounded hit history so delayed party packets have a stable calculation order. */
public final class DefenceEncounter
{
    private final DefenceProfile profile;
    private final int partySize;
    private final boolean challenge;
    private final int highestHp;
    private final Map<String, Hit> hits = new HashMap<>();
    private DefenceEstimate estimate;
    private String warning;
    private String lastEffect = "No reductions observed";
    private long lastTime;
    private long firstTime;

    public DefenceEncounter(DefenceProfile profile, int partySize, boolean challenge, int highestHp)
    {
        this.profile = profile;
        this.partySize = partySize;
        this.challenge = challenge;
        this.highestHp = highestHp;
        rebuild();
    }

    public boolean add(String id, long time, DefenceAttack attack, int amount, boolean bloatDown)
    {
        if (id == null || hits.containsKey(id)) return false;
        if (hits.size() >= 512)
        {
            warn("Hit history limit reached");
            return false;
        }
        hits.put(id, new Hit(id, time, attack, amount, bloatDown));
        firstTime = firstTime == 0 ? time : Math.min(firstTime, time);
        rebuild();
        return true;
    }

    private void rebuild()
    {
        estimate = profile == null || profile.dynamicDefence() ? null
            : new DefenceEstimate(profile, partySize, challenge, highestHp);
        hits.values().stream().sorted(Comparator.comparingLong((Hit h) -> h.time).thenComparing(h -> h.id))
            .forEach(hit -> {
                if (estimate != null) estimate.apply(hit.attack, hit.amount, hit.bloatDown);
                lastEffect = hit.attack.displayName() + (hit.amount == 0 ? " (miss)" : "");
                lastTime = hit.time;
            });
        if (estimate != null && warning != null) estimate.markUncertain();
    }

    public void warn(String message)
    {
        warning = message;
        if (estimate != null) estimate.markUncertain();
    }

    public DefenceEstimate getEstimate() { return estimate; }
    public String getWarning() { return warning; }
    public String getLastEffect() { return lastEffect; }
    public long getLastTime() { return lastTime; }
    public long getFirstTime() { return firstTime; }
    public int getHitCount() { return hits.size(); }

    public boolean atMinimum(long now)
    {
        return profile != null && profile.verifiedFloor() && !profile.fastRecovery()
            && estimate != null && !estimate.isUncertain() && estimate.getBase() > estimate.getFloor()
            && estimate.getDefence() == estimate.getFloor() && firstTime > 0
            && now - firstTime < 60000;
    }


    private static final class Hit
    {
        final String id;
        final long time;
        final DefenceAttack attack;
        final int amount;
        final boolean bloatDown;
        Hit(String id, long time, DefenceAttack attack, int amount, boolean bloatDown)
        {
            this.id = id;
            this.time = time;
            this.attack = attack;
            this.amount = amount;
            this.bloatDown = bloatDown;
        }
    }
}
