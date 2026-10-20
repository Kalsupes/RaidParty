package com.github.kalsupes.raidparty.defence;

import java.util.Collection;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/** Cooperates with the external tracker without importing its classes or changing its state. */
@Singleton
public class DefenceDisplayCompatibility
{
    private final PluginManager pluginManager;

    @Inject
    public DefenceDisplayCompatibility(PluginManager pluginManager)
    {
        this.pluginManager = pluginManager;
    }

    public boolean hideRaidPartyDisplay(boolean preferExternal)
    {
        return shouldHide(preferExternal, pluginManager.getPlugins(), pluginManager::isPluginActive);
    }

    public boolean isExternalTrackerActive()
    {
        return shouldHide(true, pluginManager.getPlugins(), pluginManager::isPluginActive);
    }

    static boolean shouldHide(boolean preferExternal, Collection<Plugin> plugins, Predicate<Plugin> active)
    {
        if (!preferExternal) return false;
        for (Plugin plugin : plugins)
        {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if ((plugin.getClass().getName().equals("com.partydefencetracker.DefenceTrackerPlugin")
                || descriptor != null && descriptor.name().equalsIgnoreCase("Party Defence Tracker"))
                && active.test(plugin))
            {
                return true;
            }
        }
        return false;
    }
}
