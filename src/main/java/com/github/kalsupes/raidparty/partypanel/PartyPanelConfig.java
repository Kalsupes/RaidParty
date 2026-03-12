package com.github.kalsupes.raidparty.partypanel;

import com.github.kalsupes.raidparty.RaidPartyConfig;

public class PartyPanelConfig {
    private final RaidPartyConfig pluginConfig;

    public PartyPanelConfig(RaidPartyConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public boolean autoExpandMembers() { return false; }
    public boolean displayPlayerWorlds() { return true; }
    public boolean displayVirtualLevels() { return true; }
}
