package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.RaidPartyConfig;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceTrackingNoticeTest
{
    @Test public void bothEnableOrdersWarnOnceOnTheNextTick()
    {
        for (boolean alreadyActive : new boolean[]{false, true})
        {
            Fixture f = new Fixture(); f.external = alreadyActive;
            f.tick();
            assertEquals(alreadyActive ? 1 : 0, f.messages.size());
            f.external = true; f.tick(); f.tick();
            assertEquals(1, f.messages.size());
            assertEquals(DefenceTrackingNotice.MESSAGE, f.messages.get(0));
        }
    }

    @Test public void disabledRaidPartyTrackingDoesNotWarn()
    {
        Fixture f = new Fixture(); f.tracking = false; f.external = true;
        f.tick(); assertTrue(f.messages.isEmpty());
        f.tracking = true; f.tick(); assertEquals(1, f.messages.size());
    }

    @Test public void waitsForLoginAndDoesNotRepeatOnHopsOrRelog()
    {
        Fixture f = new Fixture(); f.external = true; f.state = GameState.LOGIN_SCREEN;
        f.tick(); assertTrue(f.messages.isEmpty());
        f.state = GameState.LOGGED_IN; f.tick();
        for (GameState state : new GameState[]{GameState.HOPPING, GameState.LOGIN_SCREEN})
        {
            f.state = state; f.tick(); f.state = GameState.LOGGED_IN; f.tick();
        }
        assertEquals(1, f.messages.size());
    }

    @Test public void resolvingThenReintroducingOverlapAllowsAnotherNotice()
    {
        Fixture f = new Fixture(); f.external = true; f.tick();
        f.external = false; f.tick();
        f.external = true; f.tick(); f.tick();
        assertEquals(2, f.messages.size());
    }

    @Test public void unregisteringStopsNoticesAndRestartResetsThem()
    {
        Fixture f = new Fixture(); EventBus bus = new EventBus();
        bus.register(f.notice); f.external = true; bus.post(new GameTick());
        assertEquals(1, f.messages.size());
        bus.unregister(f.notice); f.notice.reset(); bus.post(new GameTick());
        assertEquals(1, f.messages.size());
        bus.register(f.notice); bus.post(new GameTick());
        assertEquals(2, f.messages.size());
    }

    private static final class Fixture
    {
        boolean external;
        boolean tracking = true;
        GameState state = GameState.LOGGED_IN;
        final List<String> messages = new ArrayList<>();
        final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (object, method, args) -> {
                if (method.getName().equals("getGameState")) return state;
                if (method.getName().equals("addChatMessage")) { messages.add((String) args[2]); return null; }
                throw new AssertionError("Unexpected client call: " + method.getName());
            });
        final DefenceTrackingNotice notice = new DefenceTrackingNotice(client,
            new RaidPartyConfig() { @Override public boolean defenceTracking() { return tracking; } },
            new DefenceDisplayCompatibility(null) { @Override public boolean isExternalTrackerActive() { return external; } });
        void tick() { notice.onGameTick(new GameTick()); }
    }
}
