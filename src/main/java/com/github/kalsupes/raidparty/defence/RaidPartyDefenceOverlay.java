package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.RaidPartyConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

/** No panel or new infoboxes: decorate existing weapon squares, then restore their icons. */
@Singleton
public class RaidPartyDefenceOverlay extends Overlay
{
    private final RaidPartyDefenceTracker tracker;
    private final RaidPartyConfig config;
    private final InfoBoxManager boxes;
    private final Map<InfoBox, Images> decorated = new IdentityHashMap<>();

    @Inject
    public RaidPartyDefenceOverlay(RaidPartyDefenceTracker tracker, RaidPartyConfig config, InfoBoxManager boxes)
    {
        this.tracker = tracker; this.config = config; this.boxes = boxes;
    }

    @Override public Dimension render(Graphics2D graphics) { return null; }

    @Subscribe public void onGameTick(GameTick event) { refresh(); }
    @Subscribe public void onPartyChanged(PartyChanged event) { clear(); }
    @Subscribe public void onUserPart(UserPart event) { clear(); }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN) clear();
    }
    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (event.getGroup().equals("raidparty")) clear();
    }

    public synchronized void clear()
    {
        decorated.forEach((box, images) -> restore(box, images));
        decorated.clear();
    }

    private void restore(InfoBox box, Images images)
    {
        if (box.getImage() == images.green) { box.setImage(images.original); boxes.updateInfoBoxImage(box); }
    }

    void refresh()
    {
        RaidPartyDefenceTracker.Target target = tracker.getSelected();
        boolean minimum = config.defenceOverlay() && target != null
            && target.encounter.atMinimum(System.currentTimeMillis());
        updateSquares(boxes.getInfoBoxes(), minimum);
    }

    synchronized void updateSquares(java.util.List<InfoBox> active, boolean minimum)
    {
        if (!minimum) { clear(); return; }
        decorated.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey()) && isDefenceCounter(entry.getKey())) return false;
            restore(entry.getKey(), entry.getValue()); return true;
        });
        for (InfoBox box : active)
        {
            if (!isDefenceCounter(box)) continue;
            Images images = decorated.get(box);
            if (images == null || box.getImage() != images.green)
            {
                images = new Images(box.getImage());
                decorated.put(box, images); box.setImage(images.green); boxes.updateInfoBoxImage(box);
            }
        }
    }

    static boolean isDefenceCounter(InfoBox box)
    {
        // Public metadata only: no reflection into other plugins and no ownership transfer.
        if (!box.getClass().getName().equals("net.runelite.client.plugins.specialcounter.SpecialCounter")) return false;
        String tooltip = box.getTooltip();
        if (tooltip == null) return false;
        for (SpecialWeapon weapon : SpecialWeapon.values())
            if (DefenceAttack.fromWeapon(weapon) != null && tooltip.startsWith(weapon.getName() + " special")) return true;
        return false;
    }

    private static final class Images
    {
        final BufferedImage original;
        final BufferedImage green;
        Images(BufferedImage original)
        {
            this.original = original;
            green = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = green.createGraphics();
            g.setColor(new Color(20, 115, 40, 180)); g.fillRect(0, 0, 32, 32);
            g.setColor(Color.GREEN); g.drawRect(0, 0, 31, 31);
            if (original != null) g.drawImage(original, (32 - original.getWidth()) / 2, (32 - original.getHeight()) / 2, null);
            g.dispose();
        }
    }
}
