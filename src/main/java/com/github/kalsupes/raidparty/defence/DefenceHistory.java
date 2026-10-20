package com.github.kalsupes.raidparty.defence;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-local-NPC lifetime reconciliation. Never infer receipt from a successful send. */
public final class DefenceHistory
{
    public static final int LIMIT = 128;
    private final String epoch = UUID.randomUUID().toString();
    private final List<RaidPartyDefenceMessage> own = new ArrayList<>();
    private final Map<Long, Peer> peers = new LinkedHashMap<>();
    private int sequence;

    public List<RaidPartyDefenceMessage> ownHits() { return own; }
    public String epoch() { return epoch; }
    public int size() { return own.size(); }
    public boolean truncated() { return sequence > own.size(); }

    public void record(RaidPartyDefenceMessage hit)
    {
        hit.setEpoch(epoch);
        hit.setSequence(++sequence);
        if (own.size() < LIMIT) own.add(hit);
    }

    public RaidPartyDefenceMessage[] missing(String knownEpoch, long low, long high)
    {
        List<RaidPartyDefenceMessage> result = new ArrayList<>();
        for (int i = 0; i < own.size(); i++)
        {
            long mask = i < 64 ? low : high;
            if (!epoch.equals(knownEpoch) || (mask & (1L << (i % 64))) == 0) result.add(own.get(i));
        }
        return result.toArray(new RaidPartyDefenceMessage[0]);
    }

    public Peer peer(long member)
    {
        Peer peer = peers.get(member);
        if (peer == null && peers.size() < 64)
        {
            peer = new Peer();
            peers.put(member, peer);
        }
        return peer;
    }

    public void forget(long member) { peers.remove(member); }

    public static boolean validStream(String epoch, int sequence)
    {
        return epoch != null && epoch.length() == 36 && sequence > 0;
    }

    public static final class Peer
    {
        private String epoch;
        private final BitSet received = new BitSet(LIMIT);
        private final String[] eventIds = new String[LIMIT];
        private String request;
        private long requestedAt;
        private long nextRequest;
        private final List<String> retired = new ArrayList<>();

        public String epoch() { return epoch; }
        public long low() { return bits(0); }
        public long high() { return bits(64); }
        private long bits(int from)
        {
            long bits = 0;
            for (int i = 0; i < 64; i++) if (received.get(from + i)) bits |= 1L << i;
            return bits;
        }
        public boolean due(long now) { return now >= nextRequest; }
        public String request(long now, int jitterMillis)
        {
            request = UUID.randomUUID().toString();
            requestedAt = now;
            // Until acknowledged, retry a lost request/reply after 15-20 seconds.
            nextRequest = now + 15000 + Math.floorMod(jitterMillis, 5001);
            return request;
        }
        public boolean matches(String id, long now)
        {
            return request != null && request.equals(id) && now >= requestedAt && now - requestedAt <= 15000;
        }
        public boolean bind(String value)
        {
            if (value == null || value.length() != 36 || retired.contains(value)) return false;
            if (!value.equals(epoch))
            {
                if (epoch != null)
                {
                    if (retired.size() >= 8) return false; // bounded; reset on new local encounter
                    retired.add(epoch);
                }
                epoch = value;
                received.clear();
                java.util.Arrays.fill(eventIds, null);
            }
            return true;
        }
        public boolean observe(RaidPartyDefenceMessage hit, long now)
        {
            if (!validStream(hit.getEpoch(), hit.getSequence()) || !bind(hit.getEpoch())) return false;
            int sequence = hit.getSequence();
            if (received.nextClearBit(0) < Math.min(sequence - 1, LIMIT)) nextRequest = Math.min(nextRequest, now);
            if (sequence <= LIMIT)
            {
                if (received.get(sequence - 1) && !java.util.Objects.equals(eventIds[sequence - 1], hit.getEventId())) return false;
                boolean gap = received.nextClearBit(0) < sequence - 1;
                received.set(sequence - 1);
                eventIds[sequence - 1] = hit.getEventId();
                if (gap) nextRequest = Math.min(nextRequest, now);
            }
            return true;
        }
        public void acknowledged(long now, int jitterMillis)
        {
            request = null;
            nextRequest = now + 90000 + Math.floorMod(jitterMillis, 30001);
        }
        public void recover() { nextRequest = 0; request = null; }
    }
}
