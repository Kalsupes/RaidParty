package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.RaidPartyConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/** An advisory only: neither tracker nor its display is disabled automatically. */
@Singleton
public final class DefenceTrackingNotice
{
    static final String MESSAGE = "RaidParty: Built-in defence tracking is enabled alongside Party Defence Tracker. "
        + "Their displays can overlap or show different estimates. We recommend disabling Party Defence Tracker "
        + "and using RaidParty's defence tracking.";

    private final Client client;
    private final RaidPartyConfig config;
    private final DefenceDisplayCompatibility compatibility;
    private volatile boolean notified;

    @Inject
    public DefenceTrackingNotice(Client client, RaidPartyConfig config, DefenceDisplayCompatibility compatibility)
    {
        this.client = client;
        this.config = config;
        this.compatibility = compatibility;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // GameTick runs on the client thread after startup, covers either enable
        // order, and defers the notice until there is an in-game chat.
        if (client.getGameState() != GameState.LOGGED_IN) return;
        boolean overlap = config.defenceTracking() && compatibility.isExternalTrackerActive();
        if (!overlap)
        {
            notified = false;
        }
        else if (!notified)
        {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", MESSAGE, "");
            notified = true;
        }
    }

    public void reset()
    {
        notified = false;
    }
}
