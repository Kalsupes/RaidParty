package com.github.kalsupes.raidparty.defence;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PartyChanged;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.specialcounter.*;
import net.runelite.client.ui.overlay.infobox.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceSquareTest {
    private InfoBox counter(SpecialWeapon weapon, BufferedImage image) throws Exception {
        Constructor<?> c = Class.forName("net.runelite.client.plugins.specialcounter.SpecialCounter").getDeclaredConstructors()[0];
        c.setAccessible(true);
        return (InfoBox)c.newInstance(image, new SpecialCounterPlugin(), new SpecialCounterConfig() {}, 71, weapon);
    }
    private RaidPartyDefenceOverlay display() throws Exception {
        Constructor<?> c = InfoBoxManager.class.getDeclaredConstructors()[0]; c.setAccessible(true);
        InfoBoxManager manager = (InfoBoxManager)c.newInstance(new RuneLiteConfig() {}, null, null, new EventBus(), null, null);
        return new RaidPartyDefenceOverlay(null, null, manager);
    }
    @Test public void existingWeaponsTurnGreenTogetherWithoutChangingCountsOrOtherSquares() throws Exception {
        BufferedImage image = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        InfoBox bgs = counter(SpecialWeapon.BANDOS_GODSWORD, image);
        InfoBox hammer = counter(SpecialWeapon.DRAGON_WARHAMMER, image);
        InfoBox other = counter(SpecialWeapon.BULWARK, image);
        RaidPartyDefenceOverlay display = display();
        display.updateSquares(Arrays.asList(bgs, hammer, other), true);
        assertNotSame(image, bgs.getImage()); assertNotSame(image, hammer.getImage());
        assertSame(image, other.getImage()); assertEquals("71", bgs.getText());
        assertEquals(Color.GREEN.getRGB(), bgs.getImage().getRGB(0,0));
        assertNull(display.render(null));
        display.updateSquares(Arrays.asList(bgs, hammer, other), false);
        assertSame(image, bgs.getImage()); assertSame(image, hammer.getImage());
    }
    @Test public void stoppingDoesNotOverwriteAnotherPluginsNewImage() throws Exception {
        BufferedImage image = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        InfoBox bgs = counter(SpecialWeapon.BANDOS_GODSWORD, image);
        RaidPartyDefenceOverlay display = display();
        display.updateSquares(Arrays.asList(bgs), true);
        BufferedImage updated = new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);
        bgs.setImage(updated); display.clear(); assertSame(updated, bgs.getImage());
    }
    @Test public void leavingPartyRestoresIconsWithoutWaitingForAnotherGameTick() throws Exception {
        BufferedImage image = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        InfoBox bgs = counter(SpecialWeapon.BANDOS_GODSWORD, image);
        RaidPartyDefenceOverlay display = display();
        display.updateSquares(Arrays.asList(bgs), true);
        display.onPartyChanged(new PartyChanged(null, null));
        assertSame(image, bgs.getImage());
        assertEquals("71", bgs.getText());
    }
    @Test public void logoutAndHopRestoreIconsWithoutWaitingForAnotherGameTick() throws Exception {
        for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.HOPPING, GameState.CONNECTION_LOST}) {
            BufferedImage image = new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
            InfoBox bgs = counter(SpecialWeapon.BANDOS_GODSWORD, image);
            RaidPartyDefenceOverlay display = display();
            display.updateSquares(Arrays.asList(bgs), true);
            GameStateChanged event = new GameStateChanged(); event.setGameState(state);
            display.onGameStateChanged(event);
            assertSame(image, bgs.getImage());
        }
    }
}
