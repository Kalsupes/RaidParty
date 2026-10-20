package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.RaidPartyConfig;
import com.github.kalsupes.raidparty.RaidPartyPlayerSync;
import com.github.kalsupes.raidparty.MessageBudget;
import com.github.kalsupes.raidparty.PartySendPolicy;
import com.github.kalsupes.raidparty.InboundBudget;
import com.github.kalsupes.raidparty.PeerDataValidation;
import com.github.kalsupes.raidparty.NetworkStats;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import net.runelite.client.util.Text;

@Singleton
@Slf4j
public class RaidPartyDefenceTracker
{
    private final Client client;
    private final ClientThread clientThread;
    private final PartyService party;
    private final RaidPartyConfig config;
    private final PartySendPolicy sendPolicy;
    @Inject private NetworkStats networkStats = NetworkStats.disabled();
    private final Map<NPC, Target> targets = new IdentityHashMap<>();
    private final Map<NPC, Long> births = new IdentityHashMap<>();
    private final Map<Integer, Long> lifetimeFloors = new LinkedHashMap<>();
    private final Map<String, Boolean> seen = new LinkedHashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Map<NPC, Integer> vulnerabilityTomes = new IdentityHashMap<>();
    private NPC selected;
    private int energy = -1;
    private int hpExperience = -1;
    private int hpCycle = -1;
    private int generation;
    private boolean running;
    private volatile long localMemberId;
    private final Map<Long, Integer> defencePeers = new LinkedHashMap<>();
    private final Map<Long, Long> peerSeen = new LinkedHashMap<>();
    private final MessageBudget requestBudget = new MessageBudget(1, 1000);
    private final MessageBudget replyBudget = new MessageBudget(4, 1000);
    private final java.util.Random syncJitter = new java.util.Random();
    private final InboundBudget inboundBudget = new InboundBudget();

    public RaidPartyDefenceTracker(Client client, ClientThread clientThread, PartyService party, RaidPartyConfig config)
    {
        this(client, clientThread, party, config, new PartySendPolicy(party));
    }

    @Inject
    public RaidPartyDefenceTracker(Client client, ClientThread clientThread, PartyService party, RaidPartyConfig config,
        PartySendPolicy sendPolicy)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.party = party;
        this.config = config;
        this.sendPolicy = sendPolicy;
    }

    public void start()
    {
        defencePeers.clear(); peerSeen.clear();
        reset();
        running = true;
        clientThread.invoke(this::initializePlayer);
    }

    public void stop()
    {
        defencePeers.clear(); peerSeen.clear();
        running = false;
        reset();
    }

    public void reset()
    {
        targets.clear();
        births.clear();
        lifetimeFloors.clear();
        seen.clear();
        pending.clear();
        vulnerabilityTomes.clear();
        selected = null;
        localMemberId = 0;
        inboundBudget.clear();
        energy = -1;
        hpExperience = -1;
        hpCycle = -1;
        generation++;
    }

    private boolean enabled()
    {
        return running && config.defenceTracking() && client.getGameState() == GameState.LOGGED_IN;
    }

    private void initializePlayer()
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN) return;
        energy = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
        hpExperience = client.getSkillExperience(Skill.HITPOINTS);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (event.getGroup().equals("raidparty") && (event.getKey().equals("defenceTracking")
            || event.getKey().equals("defenceAwakened") || event.getKey().equals("defenceYamaContract")
            || event.getKey().equals("defenceCoxHighestHp")))
        {
            clientThread.invoke(this::reset);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        // Loading changes the scene/instance and invalidates NPC indices. Never retain
        // an estimate across a hop, reconnect, instance entry, or a logout.
        if (event.getGameState() != GameState.LOGGED_IN) reset();
        else initializePlayer();
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (enabled() && births.size() < 2048) births.put(event.getNpc(), System.currentTimeMillis());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        remove(event.getNpc());
    }

    private void remove(NPC npc)
    {
        if (lifetimeFloors.size() < 2048 || lifetimeFloors.containsKey(npc.getIndex()))
            lifetimeFloors.put(npc.getIndex(), System.currentTimeMillis());
        targets.remove(npc);
        births.remove(npc);
        vulnerabilityTomes.remove(npc);
        pending.removeIf(p -> p.npc == npc);
        if (selected == npc) selected = null;
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (event.getActor() instanceof NPC) remove((NPC) event.getActor());
    }

    @Subscribe
    public void onUserPart(UserPart event)
    {
        clientThread.invoke(() -> {
            defencePeers.remove(event.getMemberId());
            peerSeen.remove(event.getMemberId());
            inboundBudget.forget(event.getMemberId());
            for (Target target : targets.values())
            {
                target.history.forget(event.getMemberId());
                target.encounter.warn("Party coverage changed");
            }
        });
    }

    @Subscribe public void onUserJoin(UserJoin event)
    {
        clientThread.invoke(() -> {
            if (event.getPartyId() != party.getPartyId()) return;
            for (Target target : targets.values())
            {
                DefenceHistory.Peer peer = target.history.peer(event.getMemberId());
                if (peer != null) peer.recover();
            }
        });
    }

    @Subscribe public void onRaidPartyPlayerSync(RaidPartyPlayerSync event)
    {
        if (!PeerDataValidation.nativeState(event) || !inboundBudget.take(event.getMemberId(), System.currentTimeMillis())) return;
        clientThread.invoke(() -> {
            if (!party.isInParty() || party.getMemberById(event.getMemberId()) == null) return;
            if (defencePeers.size() < 64 || defencePeers.containsKey(event.getMemberId()))
            {
                defencePeers.put(event.getMemberId(), event.getDefenceVersion() == null ? 1 : event.getDefenceVersion());
                peerSeen.put(event.getMemberId(), System.currentTimeMillis());
            }
        });
    }

    @Subscribe public void onPartyChanged(PartyChanged event)
    {
        clientThread.invoke(() -> { defencePeers.clear(); peerSeen.clear(); reset(); });
    }

    private Target target(NPC npc)
    {
        Target existing = targets.get(npc);
        if (existing != null) return existing;
        if (targets.size() >= 128 || npc.getWorldView() != client.getTopLevelWorldView()) return null;
        DefenceProfile profile = DefenceProfile.find(npc.getId(), npc.getName(), config.defenceAwakened(), false);
        int size = profile != null && profile.isCox()
            ? Math.max(1, client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED)) : 1;
        boolean cm = profile != null && profile.isCox() && client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) != 0;
        Target state = new Target(npc, profile, size, cm, config.defenceCoxHighestHp());
        state.resetTime = lifetimeFloors.getOrDefault(npc.getIndex(), 0L);
        state.bloatDown = DefenceProfile.normalize(npc.getName()).equals("pestilent bloat") && npc.getAnimation() == 8082;
        if (!births.containsKey(npc) || (npc.getHealthRatio() > 0 && npc.getHealthRatio() < npc.getHealthScale()))
        {
            state.encounter.warn("Earlier hits may be missing");
        }
        if (profile != null && DefenceProfile.normalize(profile.getName()).equals("yama")
            && (config.defenceYamaContract() || npc.getCombatLevel() != 1238))
            state.encounter.warn("Yama contract or variant: normal-mode floor cannot be confirmed");
        targets.put(npc, state);
        return state;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (!enabled() || event.getVarpId() != VarPlayer.SPECIAL_ATTACK_PERCENT) return;
        int previous = energy;
        energy = event.getValue();
        if (previous < 0 || energy >= previous) return;
        final int tick = client.getTickCount();
        final int cycle = client.getGameCycle();
        final int token = generation;
        // Equipment and interacting actor update later in the same client cycle.
        clientThread.invokeLater(() -> {
            if (!enabled() || token != generation) return;
            Player local = client.getLocalPlayer();
            if (local == null || local.getPlayerComposition() == null || !(local.getInteracting() instanceof NPC)) return;
            int itemId = local.getPlayerComposition().getEquipmentId(KitType.WEAPON);
            for (SpecialWeapon weapon : SpecialWeapon.values())
            {
                DefenceAttack effect = DefenceAttack.fromWeapon(weapon);
                if (effect == null) continue;
                for (int id : weapon.getItemID())
                {
                    if (id != itemId) continue;
                    NPC npc = (NPC) local.getInteracting();
                    Target state = target(npc);
                    if (state == null) return;
                    selected = npc;
                    int distance = npc.getWorldArea().distanceTo(local.getWorldLocation());
                    Pending attack = new Pending(npc, effect, tick + weapon.getHitDelay(distance));
                    // RuneLite resolves Elder Maul using the immediate HP XP drop: its
                    // defence drain happens before its delayed damage hitsplat.
                    if (effect == DefenceAttack.ELDER_MAUL)
                    {
                        attack.tick = client.getTickCount();
                        attack.hits.add(hpCycle >= cycle ? 1 : 0);
                    }
                    for (Pending earlier : pending)
                    {
                        if (earlier.npc == npc && earlier.tick == attack.tick
                            && earlier.effect != DefenceAttack.ELDER_MAUL && effect != DefenceAttack.ELDER_MAUL)
                        {
                            earlier.ambiguous = true;
                            attack.ambiguous = true;
                        }
                    }
                    if (pending.size() < 8) pending.add(attack);
                    return;
                }
            }
        });
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (event.getSkill() == Skill.HITPOINTS)
        {
            if (hpExperience >= 0 && event.getXp() > hpExperience) hpCycle = client.getGameCycle();
            hpExperience = event.getXp();
        }
    }

    @Subscribe
    public void onFakeXpDrop(FakeXpDrop event)
    {
        if (event.getSkill() == Skill.HITPOINTS) hpCycle = client.getGameCycle();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!enabled() || !event.getHitsplat().isMine()) return;
        for (Pending attack : pending)
        {
            if (attack.effect != DefenceAttack.ELDER_MAUL && event.getActor() == attack.npc
                && client.getTickCount() == attack.tick)
            {
                if (attack.hits.size() < 16) attack.hits.add(event.getHitsplat().getAmount());
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!enabled()) return;
        localMemberId = party.getLocalMember() == null ? 0 : party.getLocalMember().getMemberId();
        int tick = client.getTickCount();
        if (energy < 0) initializePlayer();
        Iterator<Pending> iterator = pending.iterator();
        while (iterator.hasNext())
        {
            Pending attack = iterator.next();
            if (tick < attack.tick) continue;
            int count = attack.hits.size();
            // Ayak and thralls can arrive in either order. Ownership alone cannot
            // identify the weapon hit; neither the last nor largest value is proof.
            if (attack.ambiguous || attack.effect == DefenceAttack.EYE_OF_AYAK && count > 1)
            {
                Target state = targets.get(attack.npc);
                if (state != null) state.encounter.warn("Special attack hitsplats are ambiguous");
                iterator.remove();
                continue;
            }
            if (count > 0 && (attack.effect != DefenceAttack.TONALZTICS_OF_RALOS || count >= 2))
            {
                int amount = attack.hits.get(count - 1);
                if (attack.effect == DefenceAttack.TONALZTICS_OF_RALOS)
                    amount = Math.min(1, amount) + Math.min(1, attack.hits.get(count - 2));
                publish(attack.npc, attack.effect, amount);
                iterator.remove();
            }
            else if (tick > attack.tick)
            {
                Target state = targets.get(attack.npc);
                if (state != null) state.encounter.warn("Special attack result not observed");
                iterator.remove();
            }
        }
        Player local = client.getLocalPlayer();
        if (local != null && local.getInteracting() instanceof NPC)
        {
            NPC npc = (NPC) local.getInteracting();
            if (!npc.isDead() && npc.getCombatLevel() > 0)
            {
                Target current = target(npc);
                if (current != null)
                {
                    selected = npc;
                    current.lastActive = tick;
                }
            }
        }
        long now = System.currentTimeMillis();
        defencePeers.entrySet().removeIf(e -> now - peerSeen.getOrDefault(e.getKey(), 0L) > 45000);
        peerSeen.keySet().retainAll(defencePeers.keySet());
        for (Target state : targets.values())
        {
            if (state.encounter.getFirstTime() > 0 && now - state.encounter.getFirstTime() > 60000)
                state.encounter.warn("Recovery may have changed this estimate");
        }
        vulnerabilityTomes.entrySet().removeIf(e -> tick - Math.abs(e.getValue()) > 10);
        Target current = getSelected();
        if (current != null && config.defencePartySync() && sendPolicy.ready())
        {
            Long requestedPeer = null;
            for (Map.Entry<Long, Integer> entry : defencePeers.entrySet())
            {
                if (!visibleMember(entry.getKey()) || entry.getValue() < 1) continue;
                if (entry.getValue() >= 2)
                {
                    DefenceHistory.Peer peer = current.history.peer(entry.getKey());
                    if (peer != null && peer.due(now) && requestBudget.take(now))
                    {
                        RaidPartyDefenceSync request = sync(current, peer.request(now, syncJitter.nextInt()), null);
                        request.setVersion(2);
                        request.setRecipient(entry.getKey());
                        request.setEpoch(peer.epoch());
                        request.setKnownLow(peer.low()); request.setKnownHigh(peer.high());
                        sendSync(request);
                        requestedPeer = entry.getKey();
                        break;
                    }
                }
                else if (tick - current.lastSyncTick >= 300 && requestBudget.take(now))
                {
                    // Legacy peers cannot express a cursor. Keep a slower compatibility
                    // recovery path; never send this broadcast in a modern-only party.
                    current.lastSyncTick = tick;
                    current.syncRequestId = UUID.randomUUID().toString();
                    current.syncRequestTime = now;
                    sendSync(sync(current, current.syncRequestId, null));
                    requestedPeer = entry.getKey();
                    break;
                }
            }
            // Rotate after iteration so repeated loss for early peers cannot starve
            // later members under the global one-request-per-second budget.
            if (requestedPeer != null)
            {
                Integer version = defencePeers.remove(requestedPeer);
                defencePeers.put(requestedPeer, version);
            }
        }
    }

    private void publish(NPC npc, DefenceAttack attack, int amount)
    {
        if (npc == null || npc.isDead() || !targets.containsKey(npc)) return;
        RaidPartyDefenceMessage message = new RaidPartyDefenceMessage();
        message.setEventId(UUID.randomUUID().toString());
        message.setTime(System.currentTimeMillis());
        message.setWorld(client.getWorld());
        message.setNpcIndex(npc.getIndex());
        message.setNpcId(npc.getId());
        WorldPoint point = WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
        message.setX(point.getX());
        message.setY(point.getY());
        message.setPlane(point.getPlane());
        message.setAttack(attack.name());
        message.setAmount(amount);
        message.setBloatDown(targets.get(npc).bloatDown);
        Target state = targets.get(npc);
        state.history.record(message);
        if (message.getSequence() > DefenceHistory.LIMIT) state.encounter.warn("Party catch-up history limit reached");
        apply(npc, message);
        if (party.isInParty() && config.defencePartySync())
        {
            try {
                if (!sendPolicy.send(message)) state.encounter.warn("Party update could not be sent; catch-up pending");
            }
            catch (RuntimeException ex)
            {
                targets.get(npc).encounter.warn("Party update could not be sent");
                log.debug("Could not send defence hit", ex);
            }
        }
    }

    @Subscribe
    public void onRaidPartyDefenceMessage(RaidPartyDefenceMessage message)
    {
        networkStats.received(message);
        if (!PeerDataValidation.text(message.getEventId(), 80) || !PeerDataValidation.text(message.getAttack(), 32)
            || !PeerDataValidation.text(message.getEpoch(), 36)
            || !inboundBudget.take(message.getMemberId(), System.currentTimeMillis())) return;
        // Party messages arrive off the game thread. NPC resolution belongs here.
        clientThread.invoke(() -> {
            if (!enabled() || !config.defencePartySync() || !party.isInParty()
                || !DefenceProtocol.valid(message, client.getWorld(), System.currentTimeMillis())) return;
            // Matching world/index alone can identify a different boss in another instance.
            if (!visibleMember(message.getMemberId())) return;
            NPC npc = client.getTopLevelWorldView().npcs().byIndex(message.getNpcIndex());
            if (npc == null || npc.isDead() || npc.getId() != message.getNpcId()) return;
            WorldPoint point = WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
            if (point.getPlane() != message.getPlane()
                || point.distanceTo(new WorldPoint(message.getX(), message.getY(), message.getPlane())) > 16) return;
            Long birth = births.get(npc);
            if (birth != null && message.getTime() < birth - 2000) return;
            Target state = target(npc);
            if (state == null) return;
            if (message.getEpoch() != null)
            {
                DefenceHistory.Peer peer = state.history.peer(message.getMemberId());
                if (peer == null || !peer.observe(message, System.currentTimeMillis())) return;
                if (defencePeers.size() < 64 || defencePeers.containsKey(message.getMemberId()))
                {
                    defencePeers.put(message.getMemberId(), 2);
                    peerSeen.put(message.getMemberId(), System.currentTimeMillis());
                }
            }
            apply(npc, message);
        });
    }

    private boolean visibleMember(long memberId)
    {
        PartyMember member = party.getMemberById(memberId);
        PartyMember local = party.getLocalMember();
        if (member == null || local != null && memberId == local.getMemberId() || member.getDisplayName() == null) return false;
        for (Player player : client.getPlayers())
        {
            if (player.getName() != null && player.getWorldView() == client.getTopLevelWorldView()
                && Text.standardize(player.getName()).equals(Text.standardize(member.getDisplayName()))) return true;
        }
        return false;
    }

    private RaidPartyDefenceSync sync(Target state, String requestId, RaidPartyDefenceMessage[] hits)
    {
        RaidPartyDefenceSync sync = new RaidPartyDefenceSync();
        sync.setRequestId(requestId);
        sync.setTime(System.currentTimeMillis());
        sync.setWorld(client.getWorld());
        sync.setNpcId(state.npc.getId());
        sync.setNpcIndex(state.npc.getIndex());
        WorldPoint point = WorldPoint.fromLocalInstance(client, state.npc.getLocalLocation());
        sync.setX(point.getX()); sync.setY(point.getY()); sync.setPlane(point.getPlane());
        sync.setHits(hits);
        return sync;
    }

    private void sendSync(RaidPartyDefenceSync sync)
    {
        try { sendPolicy.send(sync); }
        catch (RuntimeException ex) { log.debug("Could not synchronize defence history", ex); }
    }

    @Subscribe
    public void onRaidPartyDefenceSync(RaidPartyDefenceSync sync)
    {
        networkStats.received(sync);
        if (sync.getVersion() == 2 && sync.getRecipient() != null && localMemberId != 0
            && sync.getRecipient() != localMemberId) {
            networkStats.count("suppressed.otherDefenceRecipient"); return;
        }
        if (sync.getHits() != null && sync.getHits().length > DefenceHistory.LIMIT
            || !PeerDataValidation.text(sync.getRequestId(), 80) || !PeerDataValidation.text(sync.getEpoch(), 36)
            || !inboundBudget.take(sync.getMemberId(), System.currentTimeMillis())) return;
        clientThread.invoke(() -> {
            long now = System.currentTimeMillis();
            if (!enabled() || !config.defencePartySync() || !party.isInParty()
                || (sync.getVersion() != 1 && sync.getVersion() != 2)
                || sync.getWorld() != client.getWorld() || sync.getRequestId() == null || sync.getRequestId().length() > 80
                || sync.getRequestId().isEmpty() || sync.getX() < 0 || sync.getX() > 65535 || sync.getY() < 0 || sync.getY() > 65535
                || sync.getPlane() < 0 || sync.getPlane() > 3
                || sync.getTime() < now - 15000 || sync.getTime() > now + 5000
                || sync.getNpcIndex() < 0 || sync.getNpcIndex() > 65535 || !visibleMember(sync.getMemberId())) return;
            if (sync.getVersion() == 2 && (party.getLocalMember() == null || sync.getRecipient() == null
                || sync.getRecipient() != party.getLocalMember().getMemberId()
                || sync.getEpoch() != null && sync.getEpoch().length() != 36)) return;
            NPC npc = client.getTopLevelWorldView().npcs().byIndex(sync.getNpcIndex());
            if (npc == null || npc.isDead() || npc.getId() != sync.getNpcId()) return;
            WorldPoint point = WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
            if (point.getPlane() != sync.getPlane()
                || point.distanceTo(new WorldPoint(sync.getX(), sync.getY(), sync.getPlane())) > 16) return;
            Target state = targets.get(npc);
            if (state == null) return;
            if (sync.getHits() == null)
            {
                Long lastReply = state.syncReplies.get(sync.getMemberId());
                if (lastReply != null && now - lastReply < 5000) return;
                if (state.syncReplies.size() >= 64 && lastReply == null) return;
                if (!replyBudget.take(now)) return;
                state.syncReplies.put(sync.getMemberId(), now);
                RaidPartyDefenceMessage[] hits = sync.getVersion() == 2
                    ? state.history.missing(sync.getEpoch(), sync.getKnownLow(), sync.getKnownHigh())
                    : state.ownHits.toArray(new RaidPartyDefenceMessage[0]);
                RaidPartyDefenceSync reply = sync(state, sync.getRequestId(), hits);
                reply.setVersion(sync.getVersion());
                if (sync.getVersion() == 2)
                {
                    reply.setRecipient(sync.getMemberId());
                    reply.setEpoch(state.history.epoch());
                    if (state.history.truncated()) reply.setTruncated(true);
                }
                sendSync(reply);
            }
            else
            {
                DefenceHistory.Peer peer = sync.getVersion() == 2 ? state.history.peer(sync.getMemberId()) : null;
                if (sync.getVersion() == 2)
                {
                    if (peer == null || !peer.matches(sync.getRequestId(), now)
                        || sync.getEpoch() == null || sync.getEpoch().length() != 36) return;
                }
                else if (!sync.getRequestId().equals(state.syncRequestId) || now - state.syncRequestTime > 15000) return;
                // Check the entire snapshot before advancing receipt markers. The fresh
                // challenge binds it to this local NPC lifetime. Historical positions may
                // differ for moving NPCs; the outer position must match the current NPC.
                java.util.BitSet sequences = new java.util.BitSet(DefenceHistory.LIMIT);
                for (RaidPartyDefenceMessage hit : sync.getHits())
                {
                    if (!DefenceProtocol.valid(hit, client.getWorld(), now, sync.getVersion() == 2 ? Long.MAX_VALUE : 900000)
                        || hit.getNpcIndex() != npc.getIndex() || hit.getNpcId() != npc.getId()
                        || hit.getPlane() != sync.getPlane()
                        || peer != null && (!sync.getEpoch().equals(hit.getEpoch())
                        || hit.getSequence() < 1 || hit.getSequence() > DefenceHistory.LIMIT)) return;
                    if (peer != null)
                    {
                        if (sequences.get(hit.getSequence() - 1)) return;
                        sequences.set(hit.getSequence() - 1);
                    }
                }
                if (peer != null && !peer.bind(sync.getEpoch())) return;
                for (RaidPartyDefenceMessage hit : sync.getHits())
                {
                    if (peer != null && !peer.observe(hit, now)) continue;
                    if (hit.getTime() >= state.resetTime) apply(npc, hit);
                }
                if (peer != null) peer.acknowledged(now, syncJitter.nextInt());
                if (Boolean.TRUE.equals(sync.getTruncated())) state.encounter.warn("Peer catch-up history limit reached");
            }
        });
    }

    private void apply(NPC npc, RaidPartyDefenceMessage message)
    {
        if (seen.putIfAbsent(message.getEventId(), Boolean.TRUE) != null) {
            networkStats.count("defence.deduplicated"); return;
        }
        if (seen.size() > 4096) seen.remove(seen.keySet().iterator().next());
        Target state = target(npc);
        if (state == null || message.getTime() < state.resetTime) return;
        state.encounter.add(message.getEventId(), message.getTime(), DefenceAttack.valueOf(message.getAttack()),
            message.getAmount(), message.isBloatDown());
        selected = npc;
        state.lastActive = client.getTickCount();
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event)
    {
        if (!enabled() || !(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();
        // Impact graphics are visible to all nearby clients. Apply them locally once;
        // rebroadcasting an observed graphic would count one spell once per observer.
        if (!npc.hasSpotAnim(SpotanimID.VULNERABILITY_IMPACT) && !npc.hasSpotAnim(SpotanimID.CURSE_IMPACT)) return;
        Target state = target(npc);
        if (state == null) return;
        DefenceAttack attack = npc.hasSpotAnim(SpotanimID.VULNERABILITY_IMPACT) ? DefenceAttack.VULNERABILITY : DefenceAttack.CURSE;
        Integer tomeTick = vulnerabilityTomes.remove(npc);
        int amount = tomeTick != null && client.getTickCount() - Math.abs(tomeTick) <= 10 && tomeTick > 0 ? 15 : 10;
        state.encounter.add("graphic:" + attack + ":" + client.getTickCount(), System.currentTimeMillis(), attack, amount, state.bloatDown);
        if (attack == DefenceAttack.VULNERABILITY)
        {
            state.vulnerabilityUntil = client.getTickCount();
            if (tomeTick == null) state.encounter.warn("Vulnerability: caster's tome bonus unknown");
        }
        selected = npc;
        state.lastActive = client.getTickCount();
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event)
    {
        if (!enabled()) return;
        Projectile projectile = event.getProjectile();
        if (projectile.getId() != SpotanimID.VULNERABILITY_TRAVEL || !(projectile.getSourceActor() instanceof Player)
            || !(projectile.getTargetActor() instanceof NPC)) return;
        Player caster = (Player) projectile.getSourceActor();
        if (caster.getPlayerComposition() == null) return;
        NPC npc = (NPC) projectile.getTargetActor();
        if (vulnerabilityTomes.size() >= 128 && !vulnerabilityTomes.containsKey(npc)) return;
        vulnerabilityTomes.putIfAbsent(npc, (caster.getPlayerComposition().getEquipmentId(KitType.SHIELD)
            == net.runelite.api.gameval.ItemID.TOME_OF_WATER ? 1 : -1) * Math.max(1, client.getTickCount()));
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (enabled() && event.getScriptId() == ScriptID.TOB_HUD_SOTETSEG_FADE)
        {
            for (Target state : targets.values())
                if (DefenceProfile.normalize(state.npc.getName()).equals("sotetseg")) resetTarget(state, false, null);
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        Actor actor = event.getActor();
        if (!enabled()) return;
        if (actor instanceof NPC)
        {
            Target state = targets.get((NPC) actor);
            if (state == null) return;
            if (DefenceProfile.normalize(actor.getName()).equals("pestilent bloat")) state.bloatDown = actor.getAnimation() == 8082;
            if (actor.getAnimation() == 9685 && DefenceProfile.normalize(actor.getName()).contains("warden"))
                resetTarget(state, true, "Warden phase changed");
            if (DefenceProfile.normalize(actor.getName()).startsWith("tekton") && actor.getAnimation() == 7475)
                state.encounter.warn("Tekton may restore defence at the anvil");
        }
        else if (actor == client.getLocalPlayer() && actor.getAnimation() == 1816)
        {
            for (Target state : targets.values())
                if (DefenceProfile.normalize(state.npc.getName()).equals("sotetseg")) resetTarget(state, false, null);
        }
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event)
    {
        int id = event.getNpc().getId();
        if (id == net.runelite.api.gameval.NpcID.DUKE_SUCELLUS_DEAD
            || id == net.runelite.api.gameval.NpcID.DUKE_SUCELLUS_DEAD_QUEST)
        {
            remove(event.getNpc());
            return;
        }
        if (id == net.runelite.api.gameval.NpcID.DUKE_SUCELLUS_AWAKE
            || id == net.runelite.api.gameval.NpcID.DUKE_SUCELLUS_AWAKE_QUEST)
        {
            // Duke reuses the same NPC object across kills rather than despawning.
            remove(event.getNpc());
            births.put(event.getNpc(), System.currentTimeMillis());
            return;
        }
        Target state = targets.get(event.getNpc());
        if (state == null) return;
        DefenceProfile next = DefenceProfile.find(event.getNpc().getId(), event.getNpc().getName(), config.defenceAwakened(), state.enraged);
        if (next == null || state.profile == null || next.getDefence() != state.profile.getDefence()
            || next.getMagic() != state.profile.getMagic()
            || !DefenceProfile.normalize(next.getName()).equals(DefenceProfile.normalize(state.profile.getName())))
            resetTarget(state, state.enraged, "NPC phase changed; estimate restarted");
        else if (DefenceProfile.normalize(event.getNpc().getName()).equals("nex"))
            resetTarget(state, false, "Nex phase changed; estimate restarted");
    }

    private void resetTarget(Target state, boolean enraged, String warning)
    {
        state.enraged = enraged;
        state.profile = DefenceProfile.find(state.npc.getId(), state.npc.getName(), config.defenceAwakened(), enraged);
        state.encounter = new DefenceEncounter(state.profile, state.partySize, state.challenge, config.defenceCoxHighestHp());
        state.resetTime = System.currentTimeMillis();
        state.vulnerabilityUntil = 0;
        state.history = new DefenceHistory();
        state.ownHits = state.history.ownHits();
        state.syncRequestId = null;
        state.syncReplies.clear();
        if (warning != null) state.encounter.warn(warning);
        pending.removeIf(p -> p.npc == state.npc);
    }

    @Subscribe
    public void onOverlayMenuClicked(OverlayMenuClicked event)
    {
        if (event.getOverlay() instanceof RaidPartyDefenceOverlay && event.getEntry().getOption().equals("Reset defence estimate"))
        {
            clientThread.invoke(() -> {
                Target state = targets.get(selected);
                if (state != null) resetTarget(state, state.enraged, "Manually reset to base defence");
            });
        }
    }

    public Target getSelected()
    {
        if (!enabled()) return null;
        Target state = targets.get(selected);
        if (state == null || state.npc.isDead()) return null;
        Player player = client.getLocalPlayer();
        if (player != null && player.getInteracting() == state.npc) return state;
        return client.getTickCount() - state.lastActive <= config.defenceDisplayTicks() ? state : null;
    }

    public static final class Target
    {
        final NPC npc;
        DefenceProfile profile;
        DefenceEncounter encounter;
        final int partySize;
        final boolean challenge;
        boolean bloatDown;
        boolean enraged;
        long resetTime;
        int vulnerabilityUntil;
        int lastActive;
        int lastSyncTick = -300;
        String syncRequestId;
        long syncRequestTime;
        DefenceHistory history = new DefenceHistory();
        List<RaidPartyDefenceMessage> ownHits = history.ownHits();
        final Map<Long, Long> syncReplies = new LinkedHashMap<>();
        Target(NPC npc, DefenceProfile profile, int partySize, boolean challenge, int highestHp)
        {
            this.npc = npc;
            this.profile = profile;
            this.partySize = partySize;
            this.challenge = challenge;
            encounter = new DefenceEncounter(profile, partySize, challenge, highestHp);
        }
    }

    private static final class Pending
    {
        final NPC npc;
        final DefenceAttack effect;
        int tick;
        boolean ambiguous;
        final List<Integer> hits = new ArrayList<>();
        Pending(NPC npc, DefenceAttack effect, int tick)
        {
            this.npc = npc;
            this.effect = effect;
            this.tick = tick;
        }
    }
}
