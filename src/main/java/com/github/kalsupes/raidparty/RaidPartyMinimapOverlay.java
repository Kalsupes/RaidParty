package com.github.kalsupes.raidparty;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

@Singleton
public class RaidPartyMinimapOverlay extends Overlay {
    private final Client client;
    private final PartyService partyService;
    private final RaidPartyPlugin plugin;
    private final RaidPartyConfig config;

    @Inject
    public RaidPartyMinimapOverlay(Client client, PartyService partyService, RaidPartyPlugin plugin, RaidPartyConfig config) {
        this.client = client;
        this.partyService = partyService;
        this.plugin = plugin;
        this.config = config;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.drawMinimap() || partyService == null || !partyService.isInParty()) {
            return null;
        }

        for (Player player : client.getPlayers()) {
            if (player == null || player == client.getLocalPlayer() || player.getName() == null) {
                continue;
            }

            String cleanPName = player.getName().replace('\u00A0', ' ');
            boolean isPartyMember = false;
            RaidPartyPlayerSync sync = null;

            // Check partyData map first (most authoritative for RaidParty sync)
            for (RaidPartyPlayerSync s : plugin.getPartyData().values()) {
                if (s.getUsername() != null && s.getUsername().replace('\u00A0', ' ').equalsIgnoreCase(cleanPName)) {
                    isPartyMember = true;
                    sync = s;
                    break;
                }
            }

            // Fallback to core partyService
            if (!isPartyMember && partyService != null) {
                for (PartyMember pm : partyService.getMembers()) {
                    if (pm.getDisplayName() != null && pm.getDisplayName().replace('\u00A0', ' ').equalsIgnoreCase(cleanPName)) {
                        isPartyMember = true;
                        sync = plugin.getPartyData().get(pm.getMemberId());
                        break;
                    }
                }
            }

            if (isPartyMember) {
                Point minimapLocation = player.getMinimapLocation();
                if (minimapLocation != null) {
                    Color color = Color.WHITE; // Default white
                    if (sync != null && sync.getLootRule() != null) {
                        if (sync.getLootRule() == LootRule.FFA) {
                            color = new Color(175, 0, 175); // FFA purple
                        } else if (sync.getLootRule() == LootRule.SPLIT) {
                            color = new Color(0, 191, 255); // Split cyan
                        }
                    }

                    if (config.drawMinimapDots()) {
                        OverlayUtil.renderMinimapLocation(graphics, minimapLocation, color);
                    }

                    if (config.drawMinimapNames()) {
                        OverlayUtil.renderTextLocation(graphics, minimapLocation, cleanPName, color);
                    }
                }
            }
        }
        return null;
    }
}
