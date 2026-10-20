package com.github.kalsupes.raidparty;

import com.github.kalsupes.raidparty.defence.RaidPartyDefenceMessage;
import com.github.kalsupes.raidparty.defence.RaidPartyDefenceSync;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.party.messages.PartyMessage;
import net.runelite.http.api.RuneLiteAPI;

/** Opt-in local aggregate counters. Never stores names, identifiers or payloads. */
@Singleton
public final class NetworkStats
{
    private final BooleanSupplier enabled;
    private final Map<String, Long> counters = new LinkedHashMap<>();
    @Inject public NetworkStats(RaidPartyConfig config) { this(config::networkDiagnostics); }
    NetworkStats(BooleanSupplier enabled) { this.enabled = enabled; }
    public static NetworkStats disabled() { return new NetworkStats(() -> false); }
    public synchronized void count(String key) { add(key, 1); }
    public synchronized void add(String key, long amount)
    {
        if (enabled.getAsBoolean() && (counters.size() < 96 || counters.containsKey(key)))
            counters.put(key, counters.getOrDefault(key, 0L) + amount);
    }
    public void received(PartyMessage message) { count("received." + message.getClass().getSimpleName()); }
    public void sent(PartyMessage message)
    {
        if (!enabled.getAsBoolean()) return;
        String type = message.getClass().getSimpleName();
        count("sent." + type);
        // Body, JSON discriminator and approximate protobuf envelope. No body retained.
        int body = RuneLiteAPI.GSON.toJson(message).getBytes(StandardCharsets.UTF_8).length;
        add("bytes." + type, body + 2 * type.length() + 20);
        if (message instanceof RaidPartyDefenceMessage) count("defence.live");
        if (message instanceof RaidPartyDefenceSync)
        {
            RaidPartyDefenceSync sync = (RaidPartyDefenceSync) message;
            if (sync.getHits() == null) count("defence.request");
            else
            {
                count("defence.response");
                add("defence.catchupEvents", sync.getHits().length);
                if (sync.getHits().length == 0) count("defence.unchanged");
            }
        }
    }
    public synchronized Map<String, Long> snapshot() { return new LinkedHashMap<>(counters); }
    public synchronized void clear() { counters.clear(); }
}
