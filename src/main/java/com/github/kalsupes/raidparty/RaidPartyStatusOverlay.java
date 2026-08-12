package com.github.kalsupes.raidparty;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ColorUtil;

@Singleton
public class RaidPartyStatusOverlay extends Overlay
{
    private static final Color COLOR_HEALTH_MAX = Color.green;
    private static final Color COLOR_HEALTH_MIN = Color.red;
    private static final Color COLOR_PRAYER = new Color(50, 200, 200);
    private static final Color COLOR_STAMINA = new Color(160, 124, 72);
    private static final Color COLOR_SPEC = new Color(225, 225, 0);
    private static final Font OVERLAY_FONT = FontManager.getRunescapeBoldFont().deriveFont(16f);

    private final Client client;
    private final SpriteManager spriteManager;
    private final RaidPartyConfig config;
    private final PartyService partyService;
    private final RaidPartyPlugin plugin;

    @Inject
    public RaidPartyStatusOverlay(
        Client client, SpriteManager spriteManager,
        RaidPartyConfig config, PartyService partyService, RaidPartyPlugin plugin
    )
    {
        this.client = client;
        this.spriteManager = spriteManager;
        this.partyService = partyService;
        this.plugin = plugin;
        this.config = config;

        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!partyService.isInParty())
        {
            return null;
        }

        for (Player player : client.getPlayers())
        {
            if (!config.statusOverlayRenderSelf() && player == client.getLocalPlayer())
            {
                continue;
            }

            RaidPartyPlayerSync partyData = findPartySync(player);
            if (partyData == null)
            {
                continue;
            }

            int renderIx = 0;
            graphics.setFont(OVERLAY_FONT);
            if (config.statusOverlayHealth())
            {
                double healthRatio = partyData.getMaxHp() > 0 ? Math.min(1.0, (double) partyData.getHp() / partyData.getMaxHp()) : 1.0;
                Color healthColor = ColorUtil.colorLerp(COLOR_HEALTH_MIN, COLOR_HEALTH_MAX, healthRatio);
                renderPlayerOverlay(graphics, player, String.valueOf(partyData.getHp()), healthColor, renderIx++);
            }
            if (config.statusOverlayPrayer())
            {
                renderPlayerOverlay(graphics, player, String.valueOf(partyData.getPrayer()), COLOR_PRAYER, renderIx++);
            }
            if (config.statusOverlayStamina())
            {
                renderPlayerOverlay(graphics, player, String.valueOf(partyData.getRun()), COLOR_STAMINA, renderIx++);
            }
            if (config.statusOverlaySpec())
            {
                renderPlayerOverlay(graphics, player, String.valueOf(partyData.getSpec()), COLOR_SPEC, renderIx);
            }
            if (config.statusOverlayVeng() && partyData.isVengeanceActive())
            {
                BufferedImage vengIcon = spriteManager.getSprite(SpriteID.LunarMagicOn.VENGEANCE_OTHER, 0);
                if (vengIcon != null)
                {
                    renderPlayerOverlay(graphics, player, vengIcon);
                }
            }
        }

        return null;
    }

    private RaidPartyPlayerSync findPartySync(Player p)
    {
        if (p == null || p.getName() == null)
        {
            return null;
        }

        String cleanPName = p.getName().replace('\u00A0', ' ');
        for (RaidPartyPlayerSync sync : plugin.getPartyData().values()) {
            if (sync != null && sync.getUsername() != null && sync.getUsername().replace('\u00A0', ' ').equalsIgnoreCase(cleanPName)) {
                return sync;
            }
        }

        if (partyService != null) {
            for (PartyMember pm : partyService.getMembers()) {
                if (pm != null && pm.getDisplayName() != null && pm.getDisplayName().replace('\u00A0', ' ').equalsIgnoreCase(cleanPName)) {
                    return plugin.getPartyData().get(pm.getMemberId());
                }
            }
        }
        return null;
    }

    private void renderPlayerOverlay(Graphics2D graphics, Player player, String text, Color color, int renderIx)
    {
        Point point = Perspective.localToCanvas(client, player.getLocalLocation(), client.getPlane(), player.getLogicalHeight());

        if (point != null)
        {
            FontMetrics fm = graphics.getFontMetrics();
            int size = fm.getHeight();
            int zOffset = size * renderIx;

            OverlayUtil.renderTextLocation(
                graphics,
                new Point(point.getX() + size + 5, point.getY() + zOffset),
                text,
                color
            );
        }
    }

    private void renderPlayerOverlay(Graphics2D graphics, Player player, BufferedImage image)
    {
        Point textLocation = player.getCanvasImageLocation(image, player.getLogicalHeight() / 2);
        if (textLocation != null)
        {
            OverlayUtil.renderImageLocation(graphics, textLocation, image);
        }
    }
}
