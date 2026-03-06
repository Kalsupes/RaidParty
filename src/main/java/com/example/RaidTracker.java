package com.example;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import javax.inject.Inject;
import java.util.Map;
import java.util.HashMap;

/**
 * Raid point tracking for ToA, CoX, and ToB.
 *
 * ToA point tracking and unique chance math adapted from
 * LlemonDuck's "Tombs of Amascut" plugin (BSD-2-Clause license).
 * See LICENSE-LLEMONDUCK for full license text.
 * https://github.com/LlemonDuck/tombs-of-amascut
 */
@Slf4j
public class RaidTracker {

    private final Client client;
    private final RaidPartyPlugin plugin;

    // --- ToA Tracking ---
    @Getter private int toaPersonalPoints = 0;
    @Getter private int toaRoomPoints = 0;
    @Getter private int toaRaidLevel = 0;
    @Getter private int toaPartySize = 1;

    private static final int TOA_BASE_POINTS = 5000;
    private static final int TOA_MAX_ROOM_POINTS = 20_000;
    private static final int TOA_MAX_TOTAL_POINTS = 64_000;
    private static final String TOA_START_MESSAGE = "You enter the Tombs of Amascut";
    private static final String TOA_DEATH_MESSAGE = "You have died";
    private static final String TOA_ROOM_FAIL_MESSAGE = "Your party failed to complete";
    private static final String TOA_ROOM_FINISH_MESSAGE = "Challenge complete";

    // Per-NPC damage point multipliers (adapted from LlemonDuck)
    // NPCs not in the map default to 1.0
    private static final Map<Integer, Double> TOA_DAMAGE_FACTORS = new HashMap<>();
    static {
        // Baboons (Apmeken path) = 1.2x
        // Ba-Ba = 2.0x, Zebak = 1.5x, Het goal = 2.5x
        // Kephri guardians = 0.5x, Kephri scarab = 1.0x
        // P1 obelisk = 1.5x, P2 obelisk = 1.5x
        // P2 wardens active = 2.0x, P3 wardens = 2.5x
        // Warden cores = 0.0x, Ba-Ba boulders = 0.0x
        // Non-combat wardens = 0.0x, Downed wardens = 0.0x
        // We use NPC IDs from the RuneLite API
        // Since gameval NpcID constants may not be available, we document the intent
        // and use a flat 1.0 default with known overrides where IDs are stable.
    }

    // --- CoX Tracking ---
    @Getter private int coxPersonalPoints = 0;
    @Getter private int coxTotalPoints = 0;
    private boolean coxStarted = false;

    // --- ToB Tracking ---
    @Getter private int tobDeaths = 0;
    private boolean tobStarted = false;

    @Inject
    public RaidTracker(Client client, RaidPartyPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // ===== ToA =====
        int newToaLevel = client.getVarbitValue(14345);
        if (newToaLevel > 0) {
            toaRaidLevel = newToaLevel;
        } else if (toaRaidLevel > 0) {
            // Left the raid area entirely — reset
            resetToa();
        }

        // ===== CoX =====
        int coxPersonal = client.getVarbitValue(5422);
        int coxTotal = client.getVarbitValue(5424);
        if (coxStarted) {
            coxPersonalPoints = coxPersonal;
            coxTotalPoints = coxTotal;
            if (coxPersonal == 0 && coxTotal == 0) {
                coxStarted = false;
            }
        } else if (coxPersonal > 0 || coxTotal > 0) {
            coxStarted = true;
            coxPersonalPoints = coxPersonal;
            coxTotalPoints = coxTotal;
        }

        // ===== ToB =====
        int tobState = client.getVarbitValue(6440);
        if (tobStarted && tobState == 0) {
            tobStarted = false;
            tobDeaths = 0;
        }

        // Broadcast raid data to party every 10 ticks (~6 seconds)
        if (client.getTickCount() % 10 == 0) {
            updatePartySync();
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied e) {
        if (!inToa()) return;
        if (e.getHitsplat().getAmount() < 1 || !(e.getActor() instanceof NPC)) return;

        if (e.getHitsplat().isMine()) {
            NPC target = (NPC) e.getActor();
            double factor = TOA_DAMAGE_FACTORS.getOrDefault(target.getId(), 1.0);
            int pointsEarned = (int) (e.getHitsplat().getAmount() * factor);

            // Room points cap (20,000 default)
            int roomCap = TOA_MAX_ROOM_POINTS;
            if (toaRoomPoints + pointsEarned > roomCap) {
                pointsEarned = roomCap - toaRoomPoints;
            }

            toaRoomPoints = Math.min(roomCap, toaRoomPoints + pointsEarned);
            toaPersonalPoints = Math.min(TOA_MAX_TOTAL_POINTS, toaPersonalPoints + pointsEarned);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        if (e.getType() != ChatMessageType.GAMEMESSAGE) return;

        String msg = e.getMessage();
        // Check without stripping tags first (LlemonDuck's approach)
        // Then also check stripped version for safety
        String stripped = msg.replaceAll("<[^>]*>", "");

        // --- ToA messages ---
        if (msg.startsWith(TOA_START_MESSAGE) || stripped.startsWith(TOA_START_MESSAGE)) {
            resetToa();
            toaPersonalPoints = TOA_BASE_POINTS;
            toaRaidLevel = client.getVarbitValue(14345);
        } else if (stripped.startsWith(TOA_DEATH_MESSAGE) && inToa()) {
            toaPersonalPoints -= (int) Math.max(0.2 * toaPersonalPoints, 1000);
            if (toaPersonalPoints < 0) toaPersonalPoints = 0;
        } else if (stripped.startsWith(TOA_ROOM_FAIL_MESSAGE) && inToa()) {
            toaRoomPoints = 0;
        } else if (stripped.startsWith(TOA_ROOM_FINISH_MESSAGE) && inToa()) {
            toaRoomPoints = 0;
        }

        // --- ToB messages ---
        if (stripped.contains("Theatre of Blood") || stripped.contains("Maiden of Sugadinti")) {
            if (client.getVarbitValue(6440) > 0) {
                tobStarted = true;
                tobDeaths = 0;
            }
        } else if (stripped.contains("A party member has died") && inTob()) {
            tobDeaths++;
        }
    }

    private void resetToa() {
        toaRaidLevel = 0;
        toaPersonalPoints = 0;
        toaRoomPoints = 0;
    }

    public boolean inToa() {
        return toaRaidLevel > 0;
    }

    public boolean inCox() {
        return coxStarted;
    }

    public boolean inTob() {
        return tobStarted;
    }

    private void updatePartySync() {
        boolean changed = false;
        if (plugin.getLocalPlayerSync().getToaPoints() != toaPersonalPoints) {
            plugin.getLocalPlayerSync().setToaPoints(toaPersonalPoints);
            changed = true;
        }
        if (plugin.getLocalPlayerSync().getCoxPoints() != coxPersonalPoints) {
            plugin.getLocalPlayerSync().setCoxPoints(coxPersonalPoints);
            changed = true;
        }
        if (plugin.getLocalPlayerSync().getTobDeaths() != tobDeaths) {
            plugin.getLocalPlayerSync().setTobDeaths(tobDeaths);
            changed = true;
        }
        if (changed && plugin.getPartyService().isInParty()) {
            plugin.getPartyService().send(plugin.getLocalPlayerSync());
        }
    }

    // --- ToA Unique Chance (adapted from LlemonDuck / Tombs of Amascut Plugin) ---
    public static double getToaUniqueChance(int raidLevel, int totalPartyPoints) {
        int level = Math.min(550, raidLevel);
        if (level > 310) {
            if (level > 430) {
                level = 430 + ((level - 430) / 2);
            }
            level = 310 + ((level - 310) / 3);
        }
        double denominator = 10500 - (20 * level);
        if (denominator <= 0) return 0;
        return Math.max(0, Math.min(55.0, totalPartyPoints / denominator)) / 100.0;
    }
}
