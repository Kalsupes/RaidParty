package com.github.kalsupes.raidparty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.party.messages.StatusUpdate;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartyPresenceIntegrationTest {
    @Test public void malformedCoreStatusCannotOverwriteValidPartyState() throws Exception {
        Fixture f = new Fixture(); f.plugin.onRaidPartyPlayerSync(full());
        int hp = f.plugin.getPartyData().get(2L).getHp();
        StatusUpdate invalid = new StatusUpdate(); invalid.setMemberId(2);
        invalid.setCharacterName("Remote"); invalid.setHealthCurrent(-100);
        f.plugin.onStatusUpdate(invalid);
        assertEquals(hp, f.plugin.getPartyData().get(2L).getHp());
        invalid.setCharacterName(String.join("", java.util.Collections.nCopies(100, "x")));
        invalid.setHealthCurrent(1); f.plugin.onStatusUpdate(invalid);
        assertEquals(hp, f.plugin.getPartyData().get(2L).getHp());
    }
    @Test public void nativeLogoutClearsCachedContainersAndRejectsLegacyDeltas() throws Exception {
        Fixture f = new Fixture();
        f.plugin.onRaidPartyPlayerSync(full());
        RaidPartyPlayerSync old = f.plugin.getPartyData().get(2L);
        RaidPartyPlayerSync logout = new RaidPartyPlayerSync();
        logout.setMemberId(2); logout.setOnline(false);
        f.plugin.onRaidPartyPlayerSync(logout);
        RaidPartyPlayerSync offline = f.plugin.getPartyData().get(2L);
        assertEquals("Remote", offline.getUsername());
        assertEquals("Offline", offline.getUnavailableReason());
        assertNull(offline.getInvIds()); assertNull(offline.getEqpIds());
        assertNull(offline.getRunePouchIds()); assertNull(offline.getSkillLevels());
        assertEquals(0, offline.getWorld());
        assertFalse(f.plugin.isCurrentMemberSnapshot(old));
        RaidPartyPlayerSync late = new RaidPartyPlayerSync();
        late.setMemberId(2); late.setInvIds(new int[]{999});
        f.plugin.onRaidPartyPlayerSync(late);
        assertFalse(f.plugin.getPartyData().get(2L).hasLiveData());
        assertNull(f.plugin.getPartyData().get(2L).getInvIds());
    }

    @Test public void nativePartyEmptyNameIsLogoutAndFullLoginRestoresFreshData() throws Exception {
        Fixture f = new Fixture();
        f.plugin.onRaidPartyPlayerSync(full());
        StatusUpdate logout = new StatusUpdate();
        logout.setMemberId(2); logout.setCharacterName("");
        f.plugin.onStatusUpdate(logout);
        assertEquals("Remote", f.plugin.getPartyData().get(2L).getUsername());
        assertFalse(f.plugin.getPartyData().get(2L).hasLiveData());
        RaidPartyPlayerSync fresh = full();
        fresh.setInvIds(new int[]{456}); fresh.setEqpIds(null);
        f.plugin.onRaidPartyPlayerSync(fresh);
        assertTrue(f.plugin.getPartyData().get(2L).hasLiveData());
        assertArrayEquals(new int[]{456}, f.plugin.getPartyData().get(2L).getInvIds());
        assertNull(f.plugin.getPartyData().get(2L).getEqpIds());
    }

    @Test public void expiredMembershipCannotBeRevivedByPackets() throws Exception {
        Fixture f = new Fixture();
        PartyPresence presence = (PartyPresence) get(f.plugin, "presence");
        presence.joined(2, Instant.now().minusSeconds(901));
        f.plugin.onRaidPartyPlayerSync(full());
        assertFalse(f.plugin.getPartyData().containsKey(2L));
        f.plugin.onRaidPartyPlayerSync(full());
        assertFalse(f.plugin.getPartyData().containsKey(2L));
        presence.joined(2, Instant.now());
        f.plugin.onRaidPartyPlayerSync(full());
        assertTrue(f.plugin.getPartyData().containsKey(2L));
    }

    @Test public void timerAndLoginAfterSleepBothLeaveTheParty() throws Exception {
        Fixture f = new Fixture();
        set(f.plugin, "lastLogout", Instant.now().minusSeconds(901));
        f.plugin.checkIdle();
        assertTrue(f.plugin.left);
        f.plugin.left = false;
        GameStateChanged login = new GameStateChanged();
        login.setGameState(GameState.LOGGED_IN);
        f.plugin.onGameStateChanged(login);
        assertTrue(f.plugin.left);
        assertNull(get(f.plugin, "lastLogout"));
    }

    private static RaidPartyPlayerSync full() {
        RaidPartyPlayerSync s = new RaidPartyPlayerSync();
        s.setMemberId(2); s.setUsername("Remote"); s.setOnline(true); s.setWorld(302);
        s.setInvIds(new int[]{123}); s.setInvQtys(new int[]{1});
        s.setEqpIds(new int[]{234}); s.setEqpQtys(new int[]{1});
        s.setRunePouchIds(new int[]{556}); s.setSkillLevels(new int[]{99,99});
        return s;
    }

    private static final class TestPlugin extends RaidPartyPlugin {
        boolean left;
        @Override public void leaveParty() { left = true; }
    }

    private static final class Fixture {
        final TestPlugin plugin = new TestPlugin();
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                    (p, m, a) -> m.getName().equals("getGameState") ? GameState.LOGGED_IN : null);
            Constructor<?> constructor = PartyService.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            PartyService party = (PartyService) constructor.newInstance(client, null, new EventBus(), null);
            Field id = PartyService.class.getDeclaredField("partyId"); id.setAccessible(true); id.setLong(party, 10);
            id = PartyService.class.getDeclaredField("memberId"); id.setAccessible(true); id.setLong(party, 1);
            Field members = PartyService.class.getDeclaredField("members"); members.setAccessible(true);
            List<PartyMember> list = (List<PartyMember>) members.get(party);
            PartyMember local = new PartyMember(1); local.setDisplayName("Local"); list.add(local);
            PartyMember remote = new PartyMember(2); remote.setDisplayName("Remote"); list.add(remote);
            set(plugin, "partyService", party); set(plugin, "client", client);
            set(plugin, "clientThread", new ClientThread() {
                @Override public void invokeLater(Runnable runnable) { runnable.run(); }
            });
            RaidPartyConfig config = (RaidPartyConfig) Proxy.newProxyInstance(RaidPartyConfig.class.getClassLoader(),
                    new Class<?>[]{RaidPartyConfig.class}, (p, m, a) -> m.getReturnType() == boolean.class ? false : null);
            set(plugin, "config", config);
        }
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = RaidPartyPlugin.class.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = RaidPartyPlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
