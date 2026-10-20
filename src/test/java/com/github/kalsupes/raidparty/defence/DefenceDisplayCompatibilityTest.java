package com.github.kalsupes.raidparty.defence;

import com.github.kalsupes.raidparty.RaidPartyConfig;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import static org.junit.Assert.*;

public class DefenceDisplayCompatibilityTest
{
    @PluginDescriptor(name = "Party Defence Tracker")
    public static class ExternalTracker extends Plugin { }

    @PluginDescriptor(name = "Special Attack Counter")
    public static class SpecialCounter extends Plugin { }

    @Test public void activeExternalTrackerSuppressesDuplicateDisplay()
    {
        Plugin external = new ExternalTracker();
        assertTrue(DefenceDisplayCompatibility.shouldHide(true, Collections.singletonList(external), plugin -> true));
    }

    @Test public void installedButInactiveTrackerDoesNotHideDisplay()
    {
        assertFalse(DefenceDisplayCompatibility.shouldHide(true, Collections.singletonList(new ExternalTracker()), plugin -> false));
    }

    @Test public void displayReturnsWhenExternalTrackerStops()
    {
        Plugin external = new ExternalTracker();
        boolean[] active = {true};
        assertTrue(DefenceDisplayCompatibility.shouldHide(true, Collections.singletonList(external), plugin -> active[0]));
        active[0] = false;
        assertFalse(DefenceDisplayCompatibility.shouldHide(true, Collections.singletonList(external), plugin -> active[0]));
    }

    @Test public void specialCounterAndOtherPluginsDoNotSuppressDisplay()
    {
        assertFalse(DefenceDisplayCompatibility.shouldHide(true, Collections.singletonList(new SpecialCounter()), plugin -> true));
        assertFalse(DefenceDisplayCompatibility.shouldHide(true, Collections.emptyList(), plugin -> true));
        Plugin external = new ExternalTracker();
        assertFalse(DefenceDisplayCompatibility.shouldHide(true, Arrays.asList(external, new SpecialCounter()), plugin -> plugin != external));
    }

    @Test public void userCanExplicitlyChooseBothDisplays()
    {
        assertFalse(DefenceDisplayCompatibility.shouldHide(false, Collections.singletonList(new ExternalTracker()), plugin -> true));
    }

    @Test public void overlayDefaultsToCooperationAndDoesNotNeedToReadTrackerWhenHidden()
    {
        RaidPartyConfig config = new RaidPartyConfig() { };
        assertTrue(config.defencePreferExternalDisplay());
        DefenceDisplayCompatibility compatibility = new DefenceDisplayCompatibility(null)
        {
            @Override public boolean hideRaidPartyDisplay(boolean preferExternal) { return preferExternal; }
        };
        RaidPartyDefenceOverlay overlay = new RaidPartyDefenceOverlay(null, config, null);
        assertNull(overlay.render(null));
    }
}
