package com.github.kalsupes.raidparty;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.ModifierlessKeybind;

@ConfigGroup("raidparty")
public interface RaidPartyConfig extends Config {
    // ================= SECTIONS =================
    @ConfigSection(name = "General", description = "General plugin settings", position = 0, closedByDefault = false)
    String generalSection = "generalSection";

    @ConfigSection(name = "Evidence & Screenshots", description = "Settings for RuneWatch auto-evidence", position = 1, closedByDefault = false)
    String evidenceSection = "evidenceSection";

    @ConfigSection(name = "Color Blindness", description = "Accessibility modes for color blindness", position = 2, closedByDefault = false)
    String colorblindSection = "colorblindSection";

    @ConfigSection(name = "Ping System", description = "Configure the ALT/SHIFT+Right-Click Ping System", position = 3, closedByDefault = false)
    String pingSection = "pingSection";

    @ConfigSection(name = "Team UI", description = "Configure 3D team tracking and visual warnings", position = 4, closedByDefault = false)
    String teamSection = "teamSection";

    @ConfigSection(name = "Status Overlay", description = "Configure the 3D floating text numbers for party members", position = 5, closedByDefault = false)
    String statusOverlaySection = "statusOverlaySection";

    @ConfigSection(name = "Minimap Tracking", description = "Draw party members on the game Minimap", position = 6, closedByDefault = false)
    String minimapSection = "minimapSection";

    // ================= COLOR BLINDNESS =================
    @ConfigItem(keyName = "colorblindMode", name = "Colorblind Mode", description = "Automatically adjusts ping and outline colors for colorblindness", position = 1, section = colorblindSection)
    default ColorblindMode colorblindMode() {
        return ColorblindMode.NONE;
    }

    // ================= GENERAL =================

    @ConfigItem(keyName = "announceMegarares", name = "Mega/rare Chat Alerts", description = "Broadcast party-wide megarare drops to the chatbox", position = 4, section = generalSection)
    default boolean announceMegarares() {
        return true;
    }

    // ================= PING SYSTEM =================
    @ConfigItem(keyName = "disableAllPings", name = "Disable All Incoming Pings", description = "Ignore all pings from your party", position = 0, section = pingSection)
    default boolean disableAllPings() {
        return false;
    }

    @net.runelite.client.config.Range(min = 0, max = 100)
    @net.runelite.client.config.Units(net.runelite.client.config.Units.PERCENT)
    @ConfigItem(keyName = "pingVolume", name = "Ping Volume", description = "Adjust the volume of incoming pings", position = 1, section = pingSection)
    default int pingVolume() {
        return 100;
    }

    @ConfigItem(keyName = "mutedPingUsers", name = "Muted Ping Users (Comma Separated)", description = "", position = 99, hidden = true)
    default String mutedPingUsers() {
        return "";
    }

    // --- Safe ---
    @ConfigItem(keyName = "safePingHotkey", name = "Safe Ping Hotkey", description = "Hotkey to drop a Safe (Green) Ping", position = 1, section = pingSection)
    default ModifierlessKeybind safePingHotkey() {
        return new ModifierlessKeybind(java.awt.event.KeyEvent.VK_UNDEFINED, 0);
    }

    @ConfigItem(keyName = "safePingColor", name = "Safe Ping Color", description = "Color of the Safe Ping ground marker", position = 2, section = pingSection)
    default java.awt.Color safePingColor() {
        return java.awt.Color.decode("#00FF00");
    }

    @ConfigItem(keyName = "safePingSound", name = "Safe Ping Sound ID", description = "Sound Effect ID for Safe pings (Default: 2266)", position = 3, section = pingSection)
    default int safePingSound() {
        return 2266;
    }

    // --- Caution ---
    @ConfigItem(keyName = "cautionPingHotkey", name = "Caution Ping Hotkey", description = "Hotkey to drop a Caution (Yellow) Ping", position = 4, section = pingSection)
    default ModifierlessKeybind cautionPingHotkey() {
        return new ModifierlessKeybind(java.awt.event.KeyEvent.VK_UNDEFINED, 0);
    }

    @ConfigItem(keyName = "cautionPingColor", name = "Caution Ping Color", description = "Color of the Caution Ping ground marker", position = 5, section = pingSection)
    default java.awt.Color cautionPingColor() {
        return java.awt.Color.decode("#FFFF00");
    }

    @ConfigItem(keyName = "cautionPingSound", name = "Caution Ping Sound ID", description = "Sound Effect ID for Caution pings (Default: 2269)", position = 6, section = pingSection)
    default int cautionPingSound() {
        return 2269;
    }

    // --- Danger ---
    @ConfigItem(keyName = "dangerPingHotkey", name = "Danger Ping Hotkey", description = "Hotkey to drop a Danger (Red) Ping", position = 7, section = pingSection)
    default ModifierlessKeybind dangerPingHotkey() {
        return new ModifierlessKeybind(java.awt.event.KeyEvent.VK_UNDEFINED, 0);
    }

    @ConfigItem(keyName = "dangerPingColor", name = "Danger Ping Color", description = "Color of the Danger Ping ground marker", position = 8, section = pingSection)
    default java.awt.Color dangerPingColor() {
        return java.awt.Color.decode("#FF0000");
    }

    @ConfigItem(keyName = "dangerPingSound", name = "Danger Ping Sound ID", description = "Sound Effect ID for Danger pings (Default: 2268)", position = 9, section = pingSection)
    default int dangerPingSound() {
        return 2268;
    }

    // --- Resource ---
    @ConfigItem(keyName = "resourcePingHotkey", name = "Resource Ping Hotkey", description = "Hotkey to drop a Resource (Blue) Ping", position = 10, section = pingSection)
    default ModifierlessKeybind resourcePingHotkey() {
        return new ModifierlessKeybind(java.awt.event.KeyEvent.VK_UNDEFINED, 0);
    }

    @ConfigItem(keyName = "resourcePingColor", name = "Resource Ping Color", description = "Color of the Resource Ping ground marker", position = 11, section = pingSection)
    default java.awt.Color resourcePingColor() {
        return java.awt.Color.decode("#0000FF");
    }

    @ConfigItem(keyName = "resourcePingSound", name = "Resource Ping Sound ID", description = "Sound Effect ID for Resource pings (Default: 2267)", position = 12, section = pingSection)
    default int resourcePingSound() {
        return 2267; // Assuming 2267 is a safe default, can be changed by user
    }

    // --- Entity/Object/Item ---
    @ConfigItem(keyName = "objectPingHotkey", name = "Object/Entity Hotkey", description = "Hotkey to ping Objects, NPCs, and Items", position = 13, section = pingSection)
    default ModifierlessKeybind objectPingHotkey() {
        return new ModifierlessKeybind(java.awt.event.KeyEvent.VK_UNDEFINED, 0);
    }

    @ConfigItem(keyName = "entityPingColor", name = "Entity Ping Color", description = "Color of the NPC highlight", position = 14, section = pingSection)
    default java.awt.Color entityPingColor() {
        return java.awt.Color.decode("#00FFFF");
    }

    @ConfigItem(keyName = "objectPingColor", name = "Object Ping Color", description = "Color of Game Object Pings", position = 15, section = pingSection)
    default java.awt.Color objectPingColor() {
        return java.awt.Color.decode("#FFFFFF");
    }

    @ConfigItem(keyName = "itemPingColor", name = "Ground Item Ping Color", description = "Color of Ground Item Pings", position = 16, section = pingSection)
    default java.awt.Color itemPingColor() {
        return java.awt.Color.decode("#FFFFFF");
    }

    // --- Toggles ---
    @ConfigItem(keyName = "drawPingIcons", name = "Draw 3D Icons", description = "Toggle the hovering 3D Icons above ping markers", position = 17, section = pingSection)
    default boolean drawPingIcons() {
        return true;
    }

    @ConfigItem(keyName = "playPingSounds", name = "Play Audio Pings", description = "Toggle audio cues when pings are dropped", position = 18, section = pingSection)
    default boolean playPingSounds() {
        return false;
    }

    @ConfigItem(keyName = "displayVirtualLevels", name = "Virtual Levels", description = "Display virtual skill levels above 99 in the party panel", position = 1, section = generalSection)
    default boolean displayVirtualLevels() {
        return true;
    }

    @ConfigItem(keyName = "chatReadyToggle", name = "Ready State Chat Alerts", description = "Show chat messages when party members change their Ready state", position = 2, section = generalSection)
    default boolean chatReadyToggle() {
        return true;
    }

    @ConfigItem(keyName = "chatLootToggle", name = "Loot Rule Chat Alerts", description = "Show chat messages when party members change their Loot rule", position = 3, section = generalSection)
    default boolean chatLootToggle() {
        return true;
    }

    // ================= EVIDENCE & SCREENSHOTS =================
    @ConfigItem(keyName = "printRaidStartRules", name = "Print Raid Start Rules", description = "Print party members' agreed Loot Rules into chat when a raid starts", position = 1, section = evidenceSection)
    default boolean printRaidStartRules() {
        return true;
    }

    @ConfigItem(keyName = "takeRaidStartScreenshot", name = "Auto-Screenshot Raid Start", description = "Take a screenshot of the chatbox when entering a raid", position = 2, section = evidenceSection)
    default boolean takeRaidStartScreenshot() {
        return true;
    }

    @ConfigItem(keyName = "takeDropScreenshot", name = "Auto-Screenshot Drops", description = "Take a screenshot when a Megarare drops", position = 3, section = evidenceSection)
    default boolean takeDropScreenshot() {
        return true;
    }

    // ================= TEAM UI =================
    @ConfigItem(keyName = "enableLowHpGlow", name = "Enable Low HP Glow", description = "Toggle the pulsing 3D outline when a teammate drops below the Low HP threshold", position = 0, section = teamSection)
    default boolean enableLowHpGlow() {
        return false;
    }

    @net.runelite.client.config.Range(min = 1, max = 99)
    @net.runelite.client.config.Units(net.runelite.client.config.Units.PERCENT)
    @ConfigItem(keyName = "lowHpThreshold", name = "Low HP Threshold", description = "Percentage at which the Low HP glow appears", position = 1, section = teamSection)
    default int lowHpThreshold() {
        return 30;
    }

    @ConfigItem(keyName = "lowHpColor", name = "Low HP Glow Color", description = "Color of the pulsing 3D outline for Low HP", position = 2, section = teamSection)
    default java.awt.Color lowHpColor() {
        return java.awt.Color.decode("#FFFF00");
    }

    @ConfigItem(keyName = "enableCriticalHpGlow", name = "Enable Critical HP Glow", description = "Toggle the pulsing 3D outline when a teammate drops below the Critical HP threshold", position = 3, section = teamSection)
    default boolean enableCriticalHpGlow() {
        return false;
    }

    @net.runelite.client.config.Range(min = 1, max = 99)
    @net.runelite.client.config.Units(net.runelite.client.config.Units.PERCENT)
    @ConfigItem(keyName = "criticalHpThreshold", name = "Critical HP Threshold", description = "Percentage at which the Critical HP glow appears", position = 4, section = teamSection)
    default int criticalHpThreshold() {
        return 10;
    }

    @ConfigItem(keyName = "criticalHpColor", name = "Critical HP Glow Color", description = "Color of the pulsing 3D outline for Critical HP", position = 5, section = teamSection)
    default java.awt.Color criticalHpColor() {
        return java.awt.Color.decode("#FF0000");
    }

    @ConfigItem(keyName = "drawOverheadNames", name = "Show Overhead Names", description = "Draw party member usernames floating above their character models in 3D", position = 6, section = teamSection)
    default boolean drawOverheadNames() {
        return true;
    }

    @ConfigItem(keyName = "suppressOverheadForFriends", name = "Ignore Friends & Clan", description = "Do not draw overhead names for players on your friends list or clan chat to prevent overlapping with Player Indicators", position = 7, section = teamSection)
    default boolean suppressOverheadForFriends() {
        return true;
    }

    // ================= STATUS OVERLAY =================
    @ConfigItem(keyName = "statusOverlayHealth", name = "Show Health", description = "Show health on the status overlay", position = 0, section = statusOverlaySection)
    default boolean statusOverlayHealth() {
        return false;
    }

    @ConfigItem(keyName = "statusOverlayPrayer", name = "Show Prayer", description = "Show prayer on the status overlay", position = 1, section = statusOverlaySection)
    default boolean statusOverlayPrayer() {
        return false;
    }

    @ConfigItem(keyName = "statusOverlayStamina", name = "Show Run Energy", description = "Show run energy on the status overlay", position = 2, section = statusOverlaySection)
    default boolean statusOverlayStamina() {
        return false;
    }

    @ConfigItem(keyName = "statusOverlaySpec", name = "Show Spec Energy", description = "Show spec energy on the status overlay", position = 3, section = statusOverlaySection)
    default boolean statusOverlaySpec() {
        return false;
    }

    @ConfigItem(keyName = "statusOverlayVeng", name = "Show Vengeance", description = "Show vengeance on the status overlay", position = 4, section = statusOverlaySection)
    default boolean statusOverlayVeng() {
        return false;
    }

    @ConfigItem(keyName = "statusOverlayRenderSelf", name = "Show on Self", description = "Show the status overlay on your own local player", position = 5, section = statusOverlaySection)
    default boolean statusOverlayRenderSelf() {
        return false;
    }

    // ================= MINIMAP TRACKING =================
    @ConfigItem(keyName = "drawMinimap", name = "Enable Minimap Tracking", description = "Draw party members on the game minimap", position = 0, section = minimapSection)
    default boolean drawMinimap() {
        return true;
    }

    @ConfigItem(keyName = "drawMinimapNames", name = "Show Minimap Names", description = "Draw usernames next to party member minimap dots", position = 1, section = minimapSection)
    default boolean drawMinimapNames() {
        return true;
    }

    @ConfigItem(keyName = "drawMinimapDots", name = "Highlight Minimap Dots", description = "Draw colored highlight dots over party members on the minimap", position = 2, section = minimapSection)
    default boolean drawMinimapDots() {
        return true;
    }
}
