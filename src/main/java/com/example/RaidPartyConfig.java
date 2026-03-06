package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup("raidparty")
public interface RaidPartyConfig extends Config
{
    // ================= SECTIONS =================
    @ConfigSection(
        name = "Ping System",
        description = "Configure the ALT/SHIFT+Right-Click Ping System",
        position = 1,
        closedByDefault = false
    )
    String pingSection = "pingSection";



    @ConfigSection(
        name = "Team UI",
        description = "Configure 3D team tracking and visual warnings",
        position = 3,
        closedByDefault = false
    )
    String teamSection = "teamSection";

    // ================= PING SYSTEM =================
    // --- Safe ---
    @ConfigItem(keyName = "safePingHotkey", name = "Safe Ping Hotkey", description = "Hotkey to drop a Safe (Green) Ping", position = 1, section = pingSection)
    default Keybind safePingHotkey() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "safePingColor", name = "Safe Ping Color", description = "Color of the Safe Ping ground marker", position = 2, section = pingSection)
    default java.awt.Color safePingColor() { return java.awt.Color.decode("#00FF00"); }

    @ConfigItem(keyName = "safePingSound", name = "Safe Ping Sound ID", description = "Sound Effect ID for Safe pings (Default: 2266)", position = 3, section = pingSection)
    default int safePingSound() { return 2266; }

    // --- Caution ---
    @ConfigItem(keyName = "cautionPingHotkey", name = "Caution Ping Hotkey", description = "Hotkey to drop a Caution (Yellow) Ping", position = 4, section = pingSection)
    default Keybind cautionPingHotkey() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "cautionPingColor", name = "Caution Ping Color", description = "Color of the Caution Ping ground marker", position = 5, section = pingSection)
    default java.awt.Color cautionPingColor() { return java.awt.Color.decode("#FFFF00"); }

    @ConfigItem(keyName = "cautionPingSound", name = "Caution Ping Sound ID", description = "Sound Effect ID for Caution pings (Default: 2269)", position = 6, section = pingSection)
    default int cautionPingSound() { return 2269; }

    // --- Danger ---
    @ConfigItem(keyName = "dangerPingHotkey", name = "Danger Ping Hotkey", description = "Hotkey to drop a Danger (Red) Ping", position = 7, section = pingSection)
    default Keybind dangerPingHotkey() { return Keybind.NOT_SET; }

    @ConfigItem(keyName = "dangerPingColor", name = "Danger Ping Color", description = "Color of the Danger Ping ground marker", position = 8, section = pingSection)
    default java.awt.Color dangerPingColor() { return java.awt.Color.decode("#FF0000"); }

    @ConfigItem(keyName = "dangerPingSound", name = "Danger Ping Sound ID", description = "Sound Effect ID for Danger pings (Default: 2268)", position = 9, section = pingSection)
    default int dangerPingSound() { return 2268; }

    // --- Entity/Object/Item ---
    @ConfigItem(keyName = "entityPingColor", name = "Entity Ping Color", description = "Color of the NPC highlight", position = 10, section = pingSection)
    default java.awt.Color entityPingColor() { return java.awt.Color.decode("#00FFFF"); }

    @ConfigItem(keyName = "objectPingColor", name = "Object Ping Color", description = "Color of Game Object Pings", position = 11, section = pingSection)
    default java.awt.Color objectPingColor() { return java.awt.Color.decode("#FFFFFF"); }

    @ConfigItem(keyName = "itemPingColor", name = "Ground Item Ping Color", description = "Color of Ground Item Pings", position = 12, section = pingSection)
    default java.awt.Color itemPingColor() { return java.awt.Color.decode("#FFFFFF"); }

    // --- Toggles ---
    @ConfigItem(keyName = "drawPingIcons", name = "Draw 3D Icons", description = "Toggle the hovering 3D Icons above ping markers", position = 13, section = pingSection)
    default boolean drawPingIcons() { return true; }

    @ConfigItem(keyName = "playPingSounds", name = "Play Audio Pings", description = "Toggle audio cues when pings are dropped", position = 14, section = pingSection)
    default boolean playPingSounds() { return true; }



    @ConfigSection(
        name = "General",
        description = "General plugin settings",
        position = 0,
        closedByDefault = false
    )
    String generalSection = "generalSection";

    @ConfigItem(keyName = "displayVirtualLevels", name = "Virtual Levels", description = "Display virtual skill levels above 99 in the party panel", position = 1, section = generalSection)
    default boolean displayVirtualLevels() { return true; }

    @ConfigItem(keyName = "lootPreference", name = "Loot Preference", description = "Broadcast your raid loot rules (FFA, Split, Unspecified) to your party", position = 2, section = generalSection)
    default LootRule lootPreference() { return LootRule.UNSPECIFIED; }

    // ================= TEAM UI =================
    @ConfigItem(keyName = "enableLowHpGlow", name = "Enable < 30% HP Glow", description = "Toggle the pulsing 3D outline when a teammate drops below 30% HP", position = 0, section = teamSection)
    default boolean enableLowHpGlow() { return true; }

    @ConfigItem(keyName = "lowHpColor", name = "< 30% HP Glow Color", description = "Color of the pulsing 3D outline when a teammate drops below 30% HP", position = 1, section = teamSection)
    default java.awt.Color lowHpColor() { return java.awt.Color.decode("#FFFF00"); }

    @ConfigItem(keyName = "enableCriticalHpGlow", name = "Enable < 10% HP Glow", description = "Toggle the pulsing 3D outline when a teammate drops below 10% HP", position = 2, section = teamSection)
    default boolean enableCriticalHpGlow() { return true; }

    @ConfigItem(keyName = "criticalHpColor", name = "< 10% HP Glow Color", description = "Color of the pulsing 3D outline when a teammate drops below 10% HP", position = 3, section = teamSection)
    default java.awt.Color criticalHpColor() { return java.awt.Color.decode("#FF0000"); }

}
