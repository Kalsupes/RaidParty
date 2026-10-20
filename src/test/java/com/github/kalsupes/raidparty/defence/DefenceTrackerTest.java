package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.RaidPartyConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PartyChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.PartyMember;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import org.junit.Test;
import static org.junit.Assert.*;

/** Client-event tests use interface proxies; no RuneLite login or network connection. */
public class DefenceTrackerTest
{
    @Test public void modernCatchupReplyUsesActualTrackerAndDoesNotReplayKnownHistory() throws Exception
    {
        for (int count : new int[]{0,10,128})
        {
            Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick());
            RaidPartyDefenceTracker.Target target = f.tracker.getSelected();
            for (int i = 0; i < count; i++) { RaidPartyDefenceMessage hit = f.message(); hit.setEventId("own-" + i); target.history.record(hit); }
            RaidPartyDefenceSync request = f.reply(f.message()); request.setHits(null);
            request.setVersion(2); request.setRecipient(1L); request.setRequestId("first-request");
            f.tracker.onRaidPartyDefenceSync(request);
            RaidPartyDefenceSync first = (RaidPartyDefenceSync) f.sent.get(f.sent.size()-1);
            assertEquals(count, first.getHits().length); assertEquals(Long.valueOf(2), first.getRecipient());
            request.setEpoch(first.getEpoch());
            DefenceHistory.Peer known = new DefenceHistory().peer(1); known.bind(first.getEpoch());
            for (RaidPartyDefenceMessage hit : first.getHits()) known.observe(hit, System.currentTimeMillis());
            request.setKnownLow(known.low()); request.setKnownHigh(known.high());
            for (int interval = 0; interval < 5; interval++)
            {
                target.syncReplies.put(2L, 0L); // simulate elapsed reply cooldown without sleeping
                request.setRequestId("next-request-" + interval);
                // A bounded reply budget intentionally permits only its initial burst here.
                if (interval >= 3) break;
                f.tracker.onRaidPartyDefenceSync(request);
                RaidPartyDefenceSync next = (RaidPartyDefenceSync) f.sent.get(f.sent.size()-1);
                assertEquals(request.getRequestId(), next.getRequestId()); assertEquals(0, next.getHits().length);
            }
        }
    }

    @Test public void modernChallengeHandlesMovingNpcOldHitsAndRejectsReusedLifetime() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick());
        RaidPartyDefenceTracker.Target target = f.tracker.getSelected();
        RaidPartyDefenceMessage old = f.message(); old.setTime(System.currentTimeMillis()-3600000);
        old.setX(3100); old.setEpoch(java.util.UUID.randomUUID().toString()); old.setSequence(1);
        RaidPartyDefenceSync reply = f.reply(old); reply.setVersion(2); reply.setRecipient(1L); reply.setEpoch(old.getEpoch());
        reply.setRequestId(target.history.peer(2).request(System.currentTimeMillis(),0));
        f.tracker.onRaidPartyDefenceSync(reply);
        assertEquals(1,target.encounter.getHitCount());
        f.tracker.onRaidPartyDefenceSync(reply); assertEquals(1,target.encounter.getHitCount());
        f.tracker.onNpcDespawned(new NpcDespawned(f.npc)); f.npc = f.newNpc();
        f.tracker.onNpcSpawned(new NpcSpawned(f.npc)); f.tracker.onGameTick(new GameTick());
        f.tracker.onRaidPartyDefenceSync(reply); assertEquals(0,f.tracker.getSelected().encounter.getHitCount());
        // Even a fresh challenge after observed index reuse cannot apply old-lifetime hits.
        reply.setRequestId(f.tracker.getSelected().history.peer(2).request(System.currentTimeMillis(),0));
        f.tracker.onRaidPartyDefenceSync(reply); assertEquals(0,f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void modernSnapshotsRequireRecipientWorldInstanceAndValidNestedRecords() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick());
        RaidPartyDefenceMessage hit = f.message(); hit.setEpoch(java.util.UUID.randomUUID().toString()); hit.setSequence(1);
        RaidPartyDefenceSync reply = f.reply(hit); reply.setVersion(2); reply.setEpoch(hit.getEpoch());
        reply.setRequestId(f.tracker.getSelected().history.peer(2).request(System.currentTimeMillis(),0));
        reply.setRecipient(99L); f.tracker.onRaidPartyDefenceSync(reply);
        reply.setRecipient(1L); reply.setWorld(303); f.tracker.onRaidPartyDefenceSync(reply);
        reply.setWorld(302); reply.setX(4000); f.tracker.onRaidPartyDefenceSync(reply);
        reply.setX(3205); hit.setSequence(129); f.tracker.onRaidPartyDefenceSync(reply);
        assertEquals(0,f.tracker.getSelected().encounter.getHitCount());
        hit.setSequence(1); f.tracker.onRaidPartyDefenceSync(reply); assertEquals(1,f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void modernDiscoveryIsSuppressedWithoutAdvertisedVisiblePeerAndLiveHitIsImmediate() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick()); assertTrue(f.sent.isEmpty());
        com.github.kalsupes.raidparty.RaidPartyPlayerSync state = new com.github.kalsupes.raidparty.RaidPartyPlayerSync();
        state.setMemberId(2); state.setDefenceVersion(2); f.tracker.onRaidPartyPlayerSync(state);
        f.remoteVisible = false; f.tracker.onGameTick(new GameTick()); assertTrue(f.sent.isEmpty());
        f.remoteVisible = true; f.tracker.onGameTick(new GameTick());
        assertEquals(2,((RaidPartyDefenceSync)f.sent.get(0)).getVersion());
        f.beginSpec(SpecialWeapon.BANDOS_GODSWORD); f.hit(f.npc,25,true); f.tracker.onGameTick(new GameTick());
        assertTrue(f.sent.get(f.sent.size()-1) instanceof RaidPartyDefenceMessage);
    }
    @Test public void specialCounterPartyEventsCannotDoubleCountRaidPartyHits() throws Exception
    {
        Fixture f = new Fixture();
        EventBus bus = new EventBus();
        bus.register(f.tracker);
        f.beginSpec(SpecialWeapon.BANDOS_GODSWORD);
        f.hit(f.npc, 25, true);
        f.tracker.onGameTick(new GameTick());
        net.runelite.client.plugins.specialcounter.SpecialCounterUpdate external =
            new net.runelite.client.plugins.specialcounter.SpecialCounterUpdate(7, SpecialWeapon.BANDOS_GODSWORD, 25, 302, 1);
        bus.post(external);
        bus.post(external);
        f.tracker.onGameTick(new GameTick());
        assertEquals(200, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertEquals(1, f.tracker.getSelected().encounter.getHitCount());
        bus.unregister(f.tracker);
    }

    @Test public void soloFirstSpecTracksOnlyOwnHitsOnTheCapturedNpc() throws Exception
    {
        Fixture f = new Fixture();
        f.beginSpec(SpecialWeapon.BANDOS_GODSWORD);
        f.hit(f.npc, 5, true); // earlier same-tick thrall hitsplat
        f.hit(f.other, 100, true); // different NPC
        f.hit(f.npc, 100, false); // another player
        f.hit(f.npc, 25, true); // weapon hitsplat is last
        f.tracker.onGameTick(new GameTick());
        assertEquals(200, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertEquals(1, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void missedSpecialDoesNotLowerDefence() throws Exception
    {
        Fixture f = new Fixture(); f.beginSpec(SpecialWeapon.DRAGON_WARHAMMER);
        f.hit(f.npc, 0, true); f.tracker.onGameTick(new GameTick());
        assertEquals(225, f.tracker.getSelected().encounter.getEstimate().getDefence());
    }

    @Test public void tonalzticsCountsSuccessfulHitsInsteadOfDamage() throws Exception
    {
        Fixture f = new Fixture(); f.beginSpec(SpecialWeapon.TONALZTICS_OF_RALOS);
        f.tick = 12;
        f.hit(f.npc, 20, true); f.hit(f.npc, 0, true); f.tracker.onGameTick(new GameTick());
        assertEquals(194, f.tracker.getSelected().encounter.getEstimate().getDefence());
    }

    @Test public void missingHitsplatMarksEstimateUncertain() throws Exception
    {
        Fixture f = new Fixture(); f.beginSpec(SpecialWeapon.BANDOS_GODSWORD);
        f.tick = 12; f.tracker.onGameTick(new GameTick());
        assertEquals(225, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertEquals("Special attack result not observed", f.tracker.getSelected().encounter.getWarning());
    }

    @Test public void despawnAndReusedNpcIndexCannotInheritDrains() throws Exception
    {
        Fixture f = new Fixture(); f.beginSpec(SpecialWeapon.BANDOS_GODSWORD);
        f.hit(f.npc, 50, true); f.tracker.onGameTick(new GameTick());
        assertEquals(175, f.tracker.getSelected().encounter.getEstimate().getDefence());
        f.tracker.onNpcDespawned(new NpcDespawned(f.npc));
        assertNull(f.tracker.getSelected());
        f.npc = f.newNpc(); f.tracker.onNpcSpawned(new NpcSpawned(f.npc));
        f.tracker.onGameTick(new GameTick());
        assertEquals(225, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void hopInvalidatesDeferredAttackAndStopClearsOverlay() throws Exception
    {
        Fixture f = new Fixture();
        f.dropEnergy();
        GameStateChanged event = new GameStateChanged(); event.setGameState(GameState.HOPPING);
        f.tracker.onGameStateChanged(event);
        f.flushDeferred();
        assertNull(f.tracker.getSelected());
        f.tracker.onGameTick(new GameTick());
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
        f.tracker.stop(); assertNull(f.tracker.getSelected());
    }

    @Test public void deathClearsPendingHit() throws Exception
    {
        Fixture f = new Fixture(); f.beginSpec(SpecialWeapon.BANDOS_GODSWORD);
        f.tracker.onActorDeath(new ActorDeath(f.npc));
        assertNull(f.tracker.getSelected());
    }

    @Test public void remoteHitIsAppliedOnceAndLocalEchoIsIgnored() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick());
        RaidPartyDefenceMessage hit = f.message();
        f.tracker.onRaidPartyDefenceMessage(hit); f.tracker.onRaidPartyDefenceMessage(hit);
        assertEquals(191, f.tracker.getSelected().encounter.getEstimate().getDefence());
        hit.setEventId("echo"); hit.setMemberId(1); f.tracker.onRaidPartyDefenceMessage(hit);
        assertEquals(1, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void remoteHitMustMatchPartyWorldNpcAndVisibleScene() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick());
        RaidPartyDefenceMessage hit = f.message(); hit.setWorld(303); f.tracker.onRaidPartyDefenceMessage(hit);
        hit = f.message(); hit.setMemberId(99); f.tracker.onRaidPartyDefenceMessage(hit);
        hit = f.message(); hit.setNpcId(11278); f.tracker.onRaidPartyDefenceMessage(hit);
        hit = f.message(); hit.setX(4000); f.tracker.onRaidPartyDefenceMessage(hit);
        f.remoteVisible = false; f.tracker.onRaidPartyDefenceMessage(f.message());
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void requestedHistoryRecoversOldHitsButRejectsUnsolicitedSnapshots() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty();
        com.github.kalsupes.raidparty.RaidPartyPlayerSync capability = new com.github.kalsupes.raidparty.RaidPartyPlayerSync();
        capability.setMemberId(2); // old RaidParty peer: legacy recovery fallback
        f.tracker.onRaidPartyPlayerSync(capability);
        f.tracker.onGameTick(new GameTick());
        RaidPartyDefenceMessage old = f.message(); old.setTime(System.currentTimeMillis() - 20000);
        f.tracker.onRaidPartyDefenceMessage(old);
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
        RaidPartyDefenceSync reply = f.reply(old);
        reply.setRequestId("unsolicited"); f.tracker.onRaidPartyDefenceSync(reply);
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
        reply.setRequestId(f.tracker.getSelected().syncRequestId); f.tracker.onRaidPartyDefenceSync(reply);
        assertEquals(191, f.tracker.getSelected().encounter.getEstimate().getDefence());
        f.tracker.onRaidPartyDefenceSync(reply);
        assertEquals(1, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void staleHitFromPreviousSpawnIsRejected() throws Exception
    {
        Fixture f = new Fixture(); f.joinParty(); f.tracker.onGameTick(new GameTick());
        RaidPartyDefenceMessage hit = f.message(); hit.setTime(System.currentTimeMillis() - 5000);
        f.tracker.onRaidPartyDefenceMessage(hit);
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void localBgsAndPartyEmberlightsReachOneFloor() throws Exception
    {
        Fixture f = new Fixture();
        f.beginSpec(SpecialWeapon.BANDOS_GODSWORD); f.hit(f.npc, 40, true);
        f.tracker.onGameTick(new GameTick()); f.joinParty();
        RaidPartyDefenceMessage first = f.message();
        f.tracker.onRaidPartyDefenceMessage(first);
        assertFalse(f.tracker.getSelected().encounter.atMinimum(System.currentTimeMillis()));
        RaidPartyDefenceMessage second = f.message(); second.setEventId("second-party-emberlight");
        f.tracker.onRaidPartyDefenceMessage(second);
        assertEquals(145, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertTrue(f.tracker.getSelected().encounter.atMinimum(System.currentTimeMillis()));
        f.tracker.onRaidPartyDefenceMessage(second);
        assertEquals(3, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void dukeDeathAndAwakeningStartANewFightOnTheSameNpc() throws Exception
    {
        Fixture f = new Fixture(); f.npcId = 12191; f.npcName = "Duke Sucellus";
        f.beginSpec(SpecialWeapon.BANDOS_GODSWORD); f.hit(f.npc, 275, true);
        f.tracker.onGameTick(new GameTick());
        assertTrue(f.tracker.getSelected().encounter.atMinimum(System.currentTimeMillis()));
        f.npcId = 12192; f.tracker.onNpcChanged(new NpcChanged(f.npc, null));
        assertNull(f.tracker.getSelected());
        f.npcId = 12191; f.tracker.onNpcChanged(new NpcChanged(f.npc, null));
        f.tracker.onGameTick(new GameTick());
        assertEquals(275, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertNull(f.tracker.getSelected().encounter.getWarning());
        assertFalse(f.tracker.getSelected().encounter.atMinimum(System.currentTimeMillis()));
    }

    @Test public void ayakWithThrallHitsIsUncertainRegardlessOfHitsplatOrder() throws Exception
    {
        for (int[] hits : new int[][]{{43, 3}, {3, 43}, {0, 3}, {3, 0}, {11, 3}})
        {
            Fixture f = new Fixture(); f.npcId = 7552; f.npcName = "Great Olm";
            f.beginSpec(SpecialWeapon.EYE_OF_AYAK); f.tick = 15;
            for (int hit : hits) f.hit(f.npc, hit, true);
            f.tracker.onGameTick(new GameTick());
            assertEquals(200, f.tracker.getSelected().encounter.getEstimate().getMagicDefence());
            assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
            assertTrue(f.tracker.getSelected().encounter.getEstimate().isUncertain());
            assertTrue(f.tracker.getSelected().ownHits.isEmpty());
        }
    }

    @Test public void consecutiveAyakSpecialsAccumulateInsteadOfReplacingTheFirst() throws Exception
    {
        Fixture f = new Fixture(); f.npcId = 7552; f.npcName = "Great Olm";
        f.beginSpec(SpecialWeapon.EYE_OF_AYAK); f.tick = 15;
        f.hit(f.npc, 42, true);
        f.dropEnergy(0); f.flushDeferred(); // second cast before the first is finalized
        f.tracker.onGameTick(new GameTick());
        assertEquals(158, f.tracker.getSelected().encounter.getEstimate().getMagicDefence());
        f.tick = 20; f.hit(f.npc, 45, true); f.tracker.onGameTick(new GameTick());
        assertEquals(113, f.tracker.getSelected().encounter.getEstimate().getMagicDefence());
        assertEquals(2, f.tracker.getSelected().encounter.getHitCount());
        assertNotEquals(f.tracker.getSelected().ownHits.get(0).getEventId(),
            f.tracker.getSelected().ownHits.get(1).getEventId());
        f.tracker.onGameTick(new GameTick());
        assertEquals(2, f.tracker.getSelected().encounter.getHitCount());
    }

    @Test public void overlappingImpactWindowsCannotCountOneHitsplatTwice() throws Exception
    {
        Fixture f = new Fixture(); f.itemId = SpecialWeapon.BANDOS_GODSWORD.getItemID()[0];
        f.dropEnergy(500); f.dropEnergy(0); f.tick = 11; f.flushDeferred();
        f.hit(f.npc, 50, true); f.tracker.onGameTick(new GameTick());
        assertEquals(225, f.tracker.getSelected().encounter.getEstimate().getDefence());
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
        assertTrue(f.tracker.getSelected().encounter.getEstimate().isUncertain());
    }

    @Test public void leavingPartyClearsHitsCatchupAndDeferredSpecials() throws Exception
    {
        Fixture f = new Fixture(); f.beginSpec(SpecialWeapon.BANDOS_GODSWORD);
        f.hit(f.npc, 50, true); f.tracker.onGameTick(new GameTick());
        f.dropEnergy(0);
        f.tracker.onPartyChanged(new PartyChanged(null, null));
        f.flushDeferred();
        assertNull(f.tracker.getSelected());
        f.tracker.onGameTick(new GameTick());
        assertEquals(0, f.tracker.getSelected().encounter.getHitCount());
        assertTrue(f.tracker.getSelected().ownHits.isEmpty());
        assertEquals(225, f.tracker.getSelected().encounter.getEstimate().getDefence());
    }

    @Test public void actualChallengeVarbitDeterminesOlmModeNotTheRaidLayout() throws Exception
    {
        for (boolean cm : new boolean[]{false, true})
        {
            Fixture f = new Fixture(); f.npcId = 7555; f.npcName = "Great Olm (Left claw)";
            f.coxSize = 1; f.challenge = cm;
            f.beginSpec(SpecialWeapon.DRAGON_WARHAMMER);
            f.hit(f.npc, 1, true); f.tracker.onGameTick(new GameTick());
            assertEquals(cm ? 262 : 175, f.tracker.getSelected().encounter.getEstimate().getBase());
            assertEquals(cm ? 184 : 123, f.tracker.getSelected().encounter.getEstimate().getDefence());
        }
    }

    private static final class Fixture
    {
        int tick = 10;
        int npcId = 14176;
        String npcName = "Yama";
        int itemId = SpecialWeapon.BANDOS_GODSWORD.getItemID()[0];
        boolean sync;
        int coxSize = 1;
        boolean challenge;
        boolean remoteVisible = true;
        final List<Runnable> deferred = new ArrayList<>();
        final WorldView view = proxy(WorldView.class, method -> {
            switch (method.getName())
            {
                case "getBaseX": case "getBaseY": return 3200;
                case "getId": return -1;
                case "isTopLevel": return true;
                case "npcs": return proxy(IndexedObjectSet.class, m -> m.getName().equals("byIndex") ? this.npc : null);
                default: return null;
            }
        });
        NPC npc = newNpc();
        NPC other = newNpc();
        final PlayerComposition composition = proxy(PlayerComposition.class, method -> method.getName().equals("getEquipmentId") ? itemId : null);
        final Player local = proxy(Player.class, method -> {
            switch (method.getName())
            {
                case "getInteracting": return npc;
                case "getPlayerComposition": return composition;
                case "getWorldLocation": return new WorldPoint(3205, 3205, 0);
                case "getWorldView": return view;
                case "getName": return "Local";
                default: return null;
            }
        });
        final Player remote = proxy(Player.class, method -> {
            if (method.getName().equals("getName")) return "Remote";
            if (method.getName().equals("getWorldView")) return view;
            return null;
        });
        final Client client = proxy(Client.class, (method, args) -> {
            switch (method.getName())
            {
                case "getGameState": return GameState.LOGGED_IN;
                case "getLocalPlayer": return local;
                case "getTickCount": return tick;
                case "getGameCycle": return tick * 30;
                case "getTopLevelWorldView": case "getWorldView": return view;
                case "getWorld": return 302;
                case "getVarpValue": return 1000;
                case "getVarbitValue":
                    return (int) args[0] == VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED ? coxSize
                        : (int) args[0] == VarbitID.RAIDS_CHALLENGE_MODE && challenge ? 1 : 0;
                case "getSkillExperience": return 100000;
                case "getPlayers": return remoteVisible ? java.util.Arrays.asList(local, remote) : java.util.Collections.singletonList(local);
                default: return null;
            }
        });
        final RaidPartyDefenceTracker tracker;
        final PartyService party;
        final List<net.runelite.client.party.messages.PartyMessage> sent = new ArrayList<>();

        Fixture() throws Exception
        {
            RaidPartyConfig config = proxy(RaidPartyConfig.class, method -> {
                switch (method.getName())
                {
                    case "defenceTracking": return true;
                    case "defencePartySync": return sync;
                    case "defenceCoxHighestHp": return 99;
                    case "defenceDisplayTicks": return 50;
                    default: return null;
                }
            });
            ClientThread thread = new ClientThread()
            {
                @Override public void invoke(Runnable runnable) { runnable.run(); }
                @Override public void invokeLater(Runnable runnable) { deferred.add(runnable); }
            };
            Constructor<?> constructor = PartyService.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            party = (PartyService) constructor.newInstance(client, null, new EventBus(), null);
            tracker = new RaidPartyDefenceTracker(client, thread, party, config,
                com.github.kalsupes.raidparty.TestPartySendPolicy.recording(party, sent));
            tracker.start(); tracker.onNpcSpawned(new NpcSpawned(npc));
        }

        @SuppressWarnings("unchecked")
        void joinParty() throws Exception
        {
            sync = true;
            Field partyId = PartyService.class.getDeclaredField("partyId"); partyId.setAccessible(true); partyId.setLong(party, 10);
            Field memberId = PartyService.class.getDeclaredField("memberId"); memberId.setAccessible(true); memberId.setLong(party, 1);
            PartyMember one = new PartyMember(1); one.setDisplayName("Local");
            PartyMember two = new PartyMember(2); two.setDisplayName("Remote");
            Field members = PartyService.class.getDeclaredField("members"); members.setAccessible(true);
            List<PartyMember> list = (List<PartyMember>) members.get(party); list.add(one); list.add(two);
        }

        RaidPartyDefenceMessage message()
        {
            RaidPartyDefenceMessage hit = new RaidPartyDefenceMessage();
            hit.setEventId("remote-hit"); hit.setMemberId(2); hit.setWorld(302); hit.setTime(System.currentTimeMillis());
            hit.setNpcId(14176); hit.setNpcIndex(7); hit.setX(3205); hit.setY(3205);
            hit.setAttack("EMBERLIGHT"); hit.setAmount(1);
            return hit;
        }

        RaidPartyDefenceSync reply(RaidPartyDefenceMessage hit)
        {
            RaidPartyDefenceSync reply = new RaidPartyDefenceSync();
            reply.setMemberId(2); reply.setTime(System.currentTimeMillis()); reply.setWorld(302);
            reply.setNpcId(14176); reply.setNpcIndex(7); reply.setX(3205); reply.setY(3205);
            reply.setHits(new RaidPartyDefenceMessage[]{hit});
            return reply;
        }

        NPC newNpc()
        {
            return proxy(NPC.class, method -> {
                switch (method.getName())
                {
                    case "getWorldView": return view;
                    case "getId": return npcId;
                    case "getIndex": return 7;
                    case "getName": return npcName;
                    case "getCombatLevel": return 1238;
                    case "getHealthRatio": case "getHealthScale": return 30;
                    case "getLocalLocation": return new LocalPoint(640, 640);
                    case "getWorldLocation": return new WorldPoint(3205, 3205, 0);
                    case "getWorldArea": return new WorldArea(3205, 3205, 5, 5, 0);
                    default: return null;
                }
            });
        }

        void dropEnergy()
        {
            dropEnergy(500);
        }

        void dropEnergy(int value)
        {
            VarbitChanged event = new VarbitChanged(); event.setVarpId(VarPlayer.SPECIAL_ATTACK_PERCENT); event.setValue(value);
            tracker.onVarbitChanged(event);
        }

        void beginSpec(SpecialWeapon weapon)
        {
            itemId = weapon.getItemID()[0]; dropEnergy(); tick = 11; flushDeferred();
        }

        void flushDeferred()
        {
            List<Runnable> tasks = new ArrayList<>(deferred); deferred.clear(); tasks.forEach(Runnable::run);
        }

        void hit(NPC target, int amount, boolean mine)
        {
            Hitsplat splat = proxy(Hitsplat.class, method -> {
                if (method.getName().equals("getAmount")) return amount;
                if (method.getName().equals("isMine")) return mine;
                return null;
            });
            HitsplatApplied event = new HitsplatApplied(); event.setActor(target); event.setHitsplat(splat);
            tracker.onHitsplatApplied(event);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Function<Method, Object> handler)
    {
        return proxy(type, (method, args) -> handler.apply(method));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<Method, Object[], Object> handler)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(object);
            if (method.getName().equals("equals")) return object == args[0];
            if (method.getName().equals("toString")) return type.getSimpleName();
            Object result = handler.apply(method, args);
            if (result != null) return result;
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == long.class) return 0L;
            return null;
        });
    }
}
