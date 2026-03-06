package com.example.partypanel;

import com.example.RaidPartyConfig;

public class PartyPanelConfig {
    private final RaidPartyConfig pluginConfig;

    public PartyPanelConfig(RaidPartyConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public boolean autoExpandMembers() { return false; }
    public boolean displayPlayerWorlds() { return true; }
    public boolean displayVirtualLevels() { return true; }
}
