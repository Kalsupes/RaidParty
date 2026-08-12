package com.github.kalsupes.raidparty;

import com.google.inject.Provides;
import java.util.List;
import java.util.Objects;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.*;
import net.runelite.client.game.ItemManager;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.task.Schedule;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.ui.DrawManager;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Image;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.format.DateTimeFormatter;
import net.runelite.client.plugins.party.messages.StatusUpdate;
import java.util.function.Supplier;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.config.Keybind;
import net.runelite.api.widgets.Widget;
import net.runelite.http.api.worlds.World;

@Slf4j
@PluginDescriptor(name = "RaidParty", description = "A comprehensive party plugin for tracking raids, pings, low-HP warnings, and communication.", tags = {
        "party", "hub", "raid", "toa", "cox", "tob", "ping", "overlay" })
/**
 * Party panel UI and player sync adapted from TheStonedTurtle's "Hub Party
 * Panel"
 * (BSD-2-Clause). See LICENSE-THESTONEDTURTLE.
 * https://github.com/TheStonedTurtle/party-panel
 */
public class RaidPartyPlugin extends Plugin {
    private static final String PRESS_ENTER_TO_CHAT = "Press Enter to Chat";

    @Inject
    private Client client;

    @Inject
    private net.runelite.client.callback.ClientThread clientThread;

    @Inject
    private RaidPartyConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private WSClient wsClient;

    @Inject
    private ConfigManager configManager;

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Inject
    private ItemManager itemManager;

    @Inject
    private net.runelite.client.game.SpriteManager spriteManager;

    @Inject
    private net.runelite.client.party.PartyService partyService;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DrawManager drawManager;

    @Inject
    private ImageCapture imageCapture;

    @Inject
    private net.runelite.client.game.SkillIconManager skillIconManager;

    @Inject
    private RaidPartyOverlay raidpartyOverlay;

    @Inject
    private RaidPartyStatusOverlay statusOverlay;

    @Inject
    private RaidPartyMinimapOverlay minimapOverlay;

    private boolean wasInRaid = false;
    private boolean officiallyInRaid = false;
    private int outOfRaidTicks = 0;
    private boolean localSyncPushed = false;
    private int reconcileTimer = 0;



    @Inject
    private net.runelite.client.eventbus.EventBus eventBus;

    @Inject
    private KeyManager keyManager;

    private RaidPartyPanel panel;
    private NavigationButton navButton;
    private boolean addedButton = false;
    private Instant lastLogout;

    // World hopping functionality removed as requested

    // Ping Tracking
    private final List<BossPing> activePings = new CopyOnWriteArrayList<>();

    public List<BossPing> getActivePings() {
        return activePings;
    }

    public net.runelite.client.party.PartyService getPartyService() {
        return partyService;
    }

    public net.runelite.client.callback.ClientThread getClientThread() {
        return clientThread;
    }

    public net.runelite.api.Client getClient() {
        return client;
    }

    public static class BossPing {
        private final WorldPoint point;
        private final int pingType; // 0=Safe, 1=Caution, 2=Danger
        private final int targetType; // 0=Tile, 1=NPC
        private final int targetIndex;
        private final long expiryTime;

        public BossPing(WorldPoint point, int pingType, int targetType, int targetIndex, long expiryTime) {
            this.point = point;
            this.pingType = pingType;
            this.targetType = targetType;
            this.targetIndex = targetIndex;
            this.expiryTime = expiryTime;
        }

        public WorldPoint getPoint() {
            return point;
        }

        public int getPingType() {
            return pingType;
        }

        public int getTargetType() {
            return targetType;
        }

        public int getTargetIndex() {
            return targetIndex;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
    }

    @Override
    protected void startUp() throws Exception {
        try {
            log.info("RaidParty started!");
            panel = new RaidPartyPanel(this);

            initHotkeys();

            BufferedImage icon = ImageUtil.loadImageResource(RaidPartyPlugin.class, "/icon.png");

            navButton = NavigationButton.builder()
                    .tooltip("RaidParty")
                    .icon(icon)
                    .priority(5)
                    .panel(panel)
                    .build();

            clientToolbar.addNavigation(navButton);
            addedButton = true;

            wsClient.registerMessage(RaidPartyPlayerSync.class);
            wsClient.registerMessage(RaidPartyPartyMessage.class);
            wsClient.registerMessage(BossPingMessage.class);
            overlayManager.add(raidpartyOverlay);
            overlayManager.add(statusOverlay);
            overlayManager.add(minimapOverlay);

            lastLogout = Instant.now();
        } catch (Exception e) {
            log.error("CRASH IN STARTUP", e);
            throw e;
        }
    }

    @Override
    protected void shutDown() throws Exception {
        // Clean disconnect: send cleared sync before leaving
        if (partyService.isInParty()) {
            try {
                RaidPartyPlayerSync cleanSync = new RaidPartyPlayerSync();
                cleanSync.setUsername(getLocalPlayerName() != null ? getLocalPlayerName() : "");
                cleanSync.setInvIds(new int[0]);
                cleanSync.setInvQtys(new int[0]);
                cleanSync.setEqpIds(new int[0]);
                cleanSync.setEqpQtys(new int[0]);
                partyService.send(cleanSync);
            } catch (Exception ignored) {
            }
        }

        clientToolbar.removeNavigation(navButton);
        addedButton = false;
        overlayManager.remove(raidpartyOverlay);
        overlayManager.remove(statusOverlay);
        overlayManager.remove(minimapOverlay);
        keyManager.unregisterKeyListener(safePingHotkey);
        keyManager.unregisterKeyListener(cautionPingHotkey);
        keyManager.unregisterKeyListener(dangerPingHotkey);
        keyManager.unregisterKeyListener(resourcePingHotkey);
        keyManager.unregisterKeyListener(objectPingHotkey);
        wsClient.unregisterMessage(RaidPartyPlayerSync.class);
        wsClient.unregisterMessage(RaidPartyPartyMessage.class);
        wsClient.unregisterMessage(BossPingMessage.class);
        activePings.clear();
        partyData.clear();
        lastLogout = null;
        log.info("RaidParty stopped!");
    }

    @Provides
    RaidPartyConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RaidPartyConfig.class);
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public net.runelite.client.game.SpriteManager getSpriteManager() {
        return spriteManager;
    }

    public net.runelite.client.game.SkillIconManager getSkillIconManager() {
        return skillIconManager;
    }

    // --- PARTY MANAGEMENT ---
    public void joinParty(String passphrase) {
        if (passphrase == null || passphrase.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(panel,
                    "Please enter a passphrase.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE));
            return;
        }

        String sanitized = passphrase.trim().replace(" ", "-");
        configManager.setConfiguration("raidparty", "previousParty", sanitized);
        partyService.changeParty(sanitized);
        
        SwingUtilities.invokeLater(() -> panel.updateConnectionState(true, sanitized));

        clientThread.invokeLater(() -> {
            String localName = getLocalPlayerName();
            if (localName != null) {
                SwingUtilities.invokeLater(() -> panel.addMember(localName, true));
            }
        });
    }

    public void leaveParty() {
        partyService.changeParty(null);
        panel.updateConnectionState(false, "");
        partyData.clear();
        activePings.clear();
        if (panel != null) {
            panel.removeAllMembers();
        }
    }

    @Subscribe
    public void onPartyChanged(net.runelite.client.events.PartyChanged event) {
        forceFullSync = true;
        localSyncPushed = false;
        lastSentUsername = null;
        partyData.clear();
        activePings.clear();
        if (panel != null) {
            panel.removeAllMembers();
        }
        
        // Ensure native party service has our local player's display name populated for the native panel
        if (partyService != null && partyService.getLocalMember() != null) {
            String name = getLocalPlayerName();
            if (name != null && !name.isEmpty()) {
                partyService.getLocalMember().setDisplayName(name);
            }
        }
    }

    @Subscribe
    public void onUserJoin(UserJoin event) {
        // We do not have usernames at user join until they send a sync broadcast.
        // However, we MUST dispatch a fresh full payload to them so their
        // client receives a baseline cache of our data rather than hollow deltas.
        forceFullSync = true;
        lastSentInvHash = -1;
        lastSentEqpHash = -1;
        lastSentSkillsHash = -1;
        lastSentRunePouchHash = -1;
        lastSentXpsHash = -1;
        needsPartySync = true;
        // Delay sync by 5 ticks (~3 seconds) to ensure the new member's client 
        // has fully connected to the party websocket and can receive messages
        partySyncTimer = Math.max(partySyncTimer, 5);
    }

    @Subscribe
    public void onUserPart(UserPart event) {
        RaidPartyPlayerSync sync = partyData.remove(event.getMemberId());
        if (panel != null) {
            panel.removeMemberById(event.getMemberId());
            if (sync != null && sync.getUsername() != null) {
                panel.removeMember(sync.getUsername());
            }
        }
    }

    @Subscribe
    public void onStatusUpdate(StatusUpdate event) {
        if (partyService == null || partyService.getLocalMember() == null) return;
        if (event.getMemberId() == partyService.getLocalMember().getMemberId()) return;

        RaidPartyPlayerSync existing = partyData.get(event.getMemberId());
        if (existing == null) {
            existing = new RaidPartyPlayerSync();
            existing.setMemberId(event.getMemberId());
            partyData.put(event.getMemberId(), existing);
        }

        if (event.getCharacterName() != null) {
            existing.setUsername(event.getCharacterName());
        }
        if (event.getHealthCurrent() != null) {
            existing.setHp(event.getHealthCurrent());
        }
        if (event.getHealthMax() != null) {
            existing.setMaxHp(event.getHealthMax());
        }
        if (event.getPrayerCurrent() != null) {
            existing.setPrayer(event.getPrayerCurrent());
        }
        if (event.getPrayerMax() != null) {
            existing.setMaxPrayer(event.getPrayerMax());
        }
        if (event.getRunEnergy() != null) {
            existing.setRun(event.getRunEnergy());
        }
        if (event.getSpecEnergy() != null) {
            existing.setSpec(event.getSpecEnergy());
        }
        if (event.getVengeanceActive() != null) {
            existing.setVengeanceActive(event.getVengeanceActive());
        }
        
        final RaidPartyPlayerSync finalExisting = existing;
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (panel != null) panel.onPlayerSync(finalExisting);
        });
    }

    public String getLocalPlayerName() {
        if (client != null && client.getLocalPlayer() != null) {
            return client.getLocalPlayer().getName();
        }
        return null;
    }

    // --- PING SYSTEM HOOKS ---
    private abstract class PingHotkeyListener extends HotkeyListener {
        private PingHotkeyListener(Supplier<Keybind> keybind) {
            super(keybind);
        }

        @Override
        public void keyPressed(KeyEvent event) {
            if (isTextInputActive(event)) {
                return;
            }

            super.keyPressed(event);
        }
    }

    private boolean isTextInputActive(KeyEvent event) {
        int code = event.getKeyCode();
        if ((code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F24) || code == KeyEvent.VK_TAB || code == KeyEvent.VK_ESCAPE
                || code == KeyEvent.VK_INSERT || code == KeyEvent.VK_DELETE || code == KeyEvent.VK_HOME || code == KeyEvent.VK_END
                || code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN || code == KeyEvent.VK_CONTROL
                || code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_ALT) {
            return false;
        }

        if (client.getFocusedInputFieldWidget() != null) {
            return true;
        }

        Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (chatboxInput != null) {
            String inputText = chatboxInput.getText();
            if (inputText != null && inputText.contains(PRESS_ENTER_TO_CHAT)) {
                return false;
            }
        }

        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0) {
            return true;
        }

        String chatboxText = client.getVarcStrValue(VarClientID.CHATINPUT);
        return chatboxText != null && !chatboxText.isEmpty();
    }

    private final HotkeyListener safePingHotkey = new PingHotkeyListener(() -> config.safePingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(0, false);
        }
    };

    private final HotkeyListener cautionPingHotkey = new PingHotkeyListener(() -> config.cautionPingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(1, false);
        }
    };

    private final HotkeyListener dangerPingHotkey = new PingHotkeyListener(() -> config.dangerPingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(2, false);
        }
    };

    private final HotkeyListener resourcePingHotkey = new PingHotkeyListener(() -> config.resourcePingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(3, false);
        }
    };

    private final HotkeyListener objectPingHotkey = new PingHotkeyListener(() -> config.objectPingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(0, true);
        }
    };

    // --- HOTKEY MESSAGE HOOKS (disabled — muted players can't be detected) ---

    private void initHotkeys() {
        keyManager.registerKeyListener(safePingHotkey);
        keyManager.registerKeyListener(cautionPingHotkey);
        keyManager.registerKeyListener(dangerPingHotkey);
        keyManager.registerKeyListener(resourcePingHotkey);
        keyManager.registerKeyListener(objectPingHotkey);
    }



    private void executePing(int pingType, boolean forceEntity) {
        clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() != null) {
                // Block if dead
                if (client.getLocalPlayer().getHealthRatio() == 0) {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                            "<col=aa77ff>[RaidParty]</col> <col=ff5555>You cannot ping while you are dead.</col>", "");
                    return;
                }

                // Block if spectating in ToB (elevated planes, Dead Spectating varbit, or Dead ToB Orb)
                int region = client.getLocalPlayer().getWorldLocation().getRegionID();
                int plane = client.getLocalPlayer().getWorldLocation().getPlane();
                
                String localName = client.getLocalPlayer().getName();
                boolean isTobDead = false;
                if (localName != null && region >= 12611 && region <= 13379) {
                    try {
                        if (localName.equals(client.getVarcStrValue(330)) && client.getVarbitValue(6442) == 30) isTobDead = true;
                        else if (localName.equals(client.getVarcStrValue(331)) && client.getVarbitValue(6443) == 30) isTobDead = true;
                        else if (localName.equals(client.getVarcStrValue(332)) && client.getVarbitValue(6444) == 30) isTobDead = true;
                        else if (localName.equals(client.getVarcStrValue(333)) && client.getVarbitValue(6445) == 30) isTobDead = true;
                        else if (localName.equals(client.getVarcStrValue(334)) && client.getVarbitValue(6446) == 30) isTobDead = true;
                    } catch (Exception e) {
                        // Fallback
                    }
                }

                if ((plane > 0 && (region == 12613 || region == 13125 || region == 13122 || region == 13123 || region == 12612 || region == 12611 || region == 13379)) 
                        || client.getVarbitValue(6440) == 3 || isTobDead) {
                    client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                            "<col=aa77ff>[RaidParty]</col> <col=ff5555>You cannot ping while spectating.</col>", "");
                    return;
                }
            }

            net.runelite.api.Tile targetTile = client.getSelectedSceneTile();
            if (targetTile == null)
                return;

            WorldPoint wp = targetTile.getWorldLocation();
            if (wp == null)
                return;

            int tType = 0;
            int tIndex = -1;

            if (forceEntity) {
                MenuEntry[] entries = client.getMenuEntries();
                for (int i = entries.length - 1; i >= 0; i--) {
                    MenuEntry entry = entries[i];
                    if (entry.getType() == MenuAction.NPC_FIRST_OPTION || entry.getType() == MenuAction.NPC_SECOND_OPTION ||
                            entry.getType() == MenuAction.NPC_THIRD_OPTION
                            || entry.getType() == MenuAction.NPC_FOURTH_OPTION ||
                            entry.getType() == MenuAction.NPC_FIFTH_OPTION || entry.getType() == MenuAction.EXAMINE_NPC) {
                        tType = 1;
                        tIndex = entry.getIdentifier();
                        NPC targetNpc = null;
                        for (NPC n : client.getNpcs()) {
                            if (n.getIndex() == tIndex) {
                                targetNpc = n;
                                break;
                            }
                        }
                        if (targetNpc != null)
                            wp = targetNpc.getWorldLocation();
                        break;
                    } else if (entry.getType() == MenuAction.GAME_OBJECT_FIRST_OPTION
                            || entry.getType() == MenuAction.GAME_OBJECT_SECOND_OPTION ||
                            entry.getType() == MenuAction.GAME_OBJECT_THIRD_OPTION
                            || entry.getType() == MenuAction.GAME_OBJECT_FOURTH_OPTION ||
                            entry.getType() == MenuAction.GAME_OBJECT_FIFTH_OPTION
                            || entry.getType() == MenuAction.EXAMINE_OBJECT) {
                        tType = 2;
                        tIndex = entry.getIdentifier(); // Object ID
                        // Resolve the object's actual tile from scene coordinates
                        int sceneX = entry.getParam0();
                        int sceneY = entry.getParam1();
                        if (sceneX >= 0 && sceneY >= 0) {
                            wp = WorldPoint.fromScene(client, sceneX, sceneY, client.getPlane());
                        }
                        break;
                    } else if (entry.getType() == MenuAction.GROUND_ITEM_FIRST_OPTION
                            || entry.getType() == MenuAction.GROUND_ITEM_SECOND_OPTION ||
                            entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION
                            || entry.getType() == MenuAction.GROUND_ITEM_FOURTH_OPTION ||
                            entry.getType() == MenuAction.GROUND_ITEM_FIFTH_OPTION
                            || entry.getType() == MenuAction.EXAMINE_ITEM_GROUND) {
                        tType = 3;
                        tIndex = entry.getIdentifier(); // Item ID
                        // Resolve the ground item's actual tile from scene coordinates
                        int gSceneX = entry.getParam0();
                        int gSceneY = entry.getParam1();
                        if (gSceneX >= 0 && gSceneY >= 0) {
                            wp = WorldPoint.fromScene(client, gSceneX, gSceneY, client.getPlane());
                        }
                        break;
                    }
                }
                
                // If it's an object ping but we found no object, do nothing (or fallback to tile?)
                // Let's fallback to nothing to prevent accidental safe pings.
                if (tType == 0) return;
            }

            activePings.add(new BossPing(wp, pingType, tType, tIndex, System.currentTimeMillis() + 2000));
            playPingSound(pingType, tType);

            if (partyService != null && partyService.isInParty()) {
                partyService.send(new BossPingMessage(wp.getX(), wp.getY(), wp.getPlane(), pingType, tType, tIndex));
            }
        });
    }

    private void playPingSound(int pingType, int targetType) {
        if (!config.playPingSounds())
            return;
        if (targetType != 0)
            return; // Only play sounds for raw Tile pings!

        int soundId = -1;
        switch (pingType) {
            case 0:
                soundId = config.safePingSound();
                break; // Safe
            case 1:
                soundId = config.cautionPingSound();
                break; // Caution
            case 2:
                soundId = config.dangerPingSound();
                break; // Danger
            case 3:
                soundId = config.resourcePingSound();
                break; // Resource
        }

        if (soundId != -1) {
            final int sid = soundId;
            clientThread.invokeLater(() -> {
                net.runelite.api.Preferences preferences = client.getPreferences();
                int baseVolume = preferences.getSoundEffectVolume();
                if (baseVolume <= 0) return; // Muted in game settings

                int targetVol = (int) (baseVolume * (config.pingVolume() / 100.0f));
                if (targetVol > 127) targetVol = 127;
                
                client.playSoundEffect(sid, targetVol);
            });
        }
    }

    @Subscribe
    public void onBossPingMessage(BossPingMessage event) {
        if (config.disableAllPings()) return;

        if (partyService != null && partyService.getLocalMember() != null
                && partyService.getLocalMember().getMemberId() == event.getMemberId()) {
            return;
        }

        if (partyService != null) {
            net.runelite.client.party.PartyMember sender = partyService.getMemberById(event.getMemberId());
            if (sender != null) {
                String senderName = sender.getDisplayName();
                if (senderName != null) {
                    String mutedStr = config.mutedPingUsers();
                    if (mutedStr != null && !mutedStr.isEmpty()) {
                        java.util.List<String> mutedList = java.util.Arrays.asList(mutedStr.split(","));
                        if (mutedList.contains(senderName)) {
                            return; // Sender is muted
                        }
                    }
                }
            }
        }

        clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() == null || client.getLocalPlayer().getWorldLocation() == null) {
                return;
            }

            // 1. Check if the sender is with us on our world / in our raid instance
            RaidPartyPlayerSync senderSync = partyData.get(event.getMemberId());
            if (senderSync != null && !isPartyMemberWithLocalPlayer(senderSync)) {
                return; // Ignore pings from players who are away / on another world / not in our raid
            }

            WorldPoint wp = new WorldPoint(event.getX(), event.getY(), event.getPlane());

            // 2. Check distance from our player's location (max 50 tiles and same plane)
            if (wp.getPlane() != client.getPlane() || wp.distanceTo(client.getLocalPlayer().getWorldLocation()) > 50) {
                return; // Ignore pings that are too far away or on a different height/plane
            }

            activePings.add(new BossPing(wp, event.getPingType(), event.getTargetType(), event.getTargetIndex(),
                    System.currentTimeMillis() + 2000));
            playPingSound(event.getPingType(), event.getTargetType());
        });
    }

    @Subscribe
    public void onRaidPartyPartyMessage(RaidPartyPartyMessage event) {
        if (partyService == null || partyService.getLocalMember() == null)
            return;
        if (event.getMemberId() == partyService.getLocalMember().getMemberId())
            return;
        clientThread.invokeLater(() -> {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "<col=aa77ff>[RaidParty]</col> <col=55aaff>" + event.getSenderName() + ":</col> <col=ffffff>"
                            + event.getMessage() + "</col>",
                    "");

            for (net.runelite.api.Player p : client.getPlayers()) {
                if (p != null && p.getName() != null && p.getName().equals(event.getSenderName())) {
                    p.setOverheadText(event.getMessage());
                    p.setOverheadCycle(150); // Set timer if available
                    break;
                }
            }
        });
    }

    @Subscribe
    public void onRaidPartyPlayerSync(RaidPartyPlayerSync event) {
        if (partyService == null || partyService.getLocalMember() == null)
            return;

        // Merge incoming delta arrays with our existing cached data
        RaidPartyPlayerSync existing = partyData.get(event.getMemberId());
        if (existing != null) {
            mergeDeltaSync(existing, event);
        }

        // Determine if this is US
        boolean isLocal = event.getMemberId() == partyService.getLocalMember().getMemberId();

        // Chat notifications for remote party members' ready/loot changes
        if (!isLocal && event.getUsername() != null && !event.getUsername().isEmpty()) {
            // Ready state change
            if (existing == null || existing.getReadyState() != event.getReadyState()) {
                if (config.chatReadyToggle()) {
                    if (event.getReadyState() == 1) {
                        postPartyChat("<col=55aaff>" + event.getUsername() + "</col> is now <col=00ff00>Ready</col>");
                    } else if (event.getReadyState() == 2) {
                        postPartyChat("<col=55aaff>" + event.getUsername() + "</col> is now <col=ff5555>Not Ready</col>");
                    }
                }
            }
            // Loot rule change
            if (existing == null || existing.getLootRule() != event.getLootRule()) {
                if (event.getLootRule() != null && event.getLootRule() != LootRule.UNSPECIFIED) {
                    if (config.chatLootToggle()) {
                        String color = event.getLootRule() == LootRule.FFA ? "af00af" : "00bfff";
                        postPartyChat("<col=55aaff>" + event.getUsername() + "</col> Loot Confirmed: <col=" + color + ">" + event.getLootRule() + "</col>");
                    }
                }
            }
        }

        partyData.put(event.getMemberId(), event);

        javax.swing.SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.onPlayerSync(event);
            }
        });
    }

    private void postPartyChat(String message) {
        final String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        final String chatMsg = "<col=aa77ff>[RaidParty]</col> <col=909090>[" + timeStr + "]</col> " + message;
        clientThread.invokeLater(
                () -> client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", chatMsg, ""));
    }

    // --- LIVE PARTY SYNC LOGIC ---
    private int localHp, localMaxHp, localPrayer, localMaxPrayer, localSpec, localRun;
    private int localCombatLevel = -1;
    private long localActivePrayers = -1L;
    private int localPouchHash = -1;
    private int keepaliveTicks = 0;
    private boolean needsPartySync = false;
    private int partySyncTimer = 0;
    private int evidenceScreenshotTicks = -1;

    private final Map<Long, RaidPartyPlayerSync> partyData = new HashMap<>();

    public Map<Long, RaidPartyPlayerSync> getPartyData() {
        return partyData;
    }

    private volatile RaidPartyPlayerSync cachedLocalSync = new RaidPartyPlayerSync();

    public RaidPartyPlayerSync getLocalPlayerSync() {
        return cachedLocalSync;
    }

    // Persistent ready state (survives sync rebuilds)
    private int localReadyState = 0; // 0=None, 1=Ready, 2=Not Ready
    private LootRule localLootRule = LootRule.UNSPECIFIED;
    private long lastChatBroadcastTime = 0;

    public LootRule getLocalLootRule() {
        return localLootRule;
    }

    private void updateCachedLocalSync() {
        if (client.getLocalPlayer() == null)
            return;

        RaidPartyPlayerSync sync = new RaidPartyPlayerSync();
        sync.setHp(client.getBoostedSkillLevel(Skill.HITPOINTS));
        sync.setMaxHp(client.getRealSkillLevel(Skill.HITPOINTS));
        sync.setPrayer(client.getBoostedSkillLevel(Skill.PRAYER));
        sync.setMaxPrayer(client.getRealSkillLevel(Skill.PRAYER));
        sync.setSpec(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10);
        sync.setRun(client.getEnergy() / 100);
        if (client.getAccountType() != null) {
            sync.setAccountType(client.getAccountType().ordinal());
        }

        // Inventory
        net.runelite.api.ItemContainer invContainer = client.getItemContainer(InventoryID.INVENTORY);
        int[] invIds = new int[28];
        int[] invQtys = new int[28];
        if (invContainer != null) {
            Item[] items = invContainer.getItems();
            for (int i = 0; i < items.length && i < 28; i++) {
                invIds[i] = items[i].getId();
                invQtys[i] = items[i].getQuantity();
            }
        }
        sync.setInvIds(invIds);
        sync.setInvQtys(invQtys);

        // Equipment
        net.runelite.api.ItemContainer eqpContainer = client.getItemContainer(InventoryID.EQUIPMENT);
        int[] eqpIds = new int[14];
        int[] eqpQtys = new int[14];
        if (eqpContainer != null) {
            Item[] eqp = eqpContainer.getItems();
            for (int i = 0; i < eqp.length && i < 14; i++) {
                eqpIds[i] = eqp[i].getId();
                eqpQtys[i] = eqp[i].getQuantity();
            }
        }
        sync.setEqpIds(eqpIds);
        sync.setEqpQtys(eqpQtys);

        String name = getLocalPlayerName();
        sync.setWorld(client.getWorld());
        sync.setCombatLevel(client.getLocalPlayer().getCombatLevel());
        sync.setUsername(name != null ? name : "");
        sync.setActivePrayers(gatherActivePrayers());
        sync.setSkillLevels(gatherSkillLevels());
        sync.setSkillXps(gatherSkillXps());
        sync.setLootRule(localLootRule);

        sync.setStamina(client.getVarbitValue(Varbits.STAMINA_EFFECT));
        sync.setPoison(client.getVarpValue(VarPlayer.POISON));
        sync.setDisease(client.getVarpValue(VarPlayer.DISEASE_VALUE));
        sync.setTotalLevel(client.getTotalLevel());
        sync.setVengeanceActive(client.getVarbitValue(Varbits.VENGEANCE_ACTIVE) == 1);

        // Rune Pouch
        gatherRunePouchContents(sync);

        // Dizana's Quiver
        sync.setQuiverAmmoId(client.getVarpValue(VarPlayer.DIZANAS_QUIVER_ITEM_ID));
        sync.setQuiverAmmoQty(client.getVarpValue(VarPlayer.DIZANAS_QUIVER_ITEM_COUNT));

        // Prayer availability and unlocked status
        sync.setAvailablePrayers(gatherAvailablePrayers());
        sync.setUnlockedPrayers(gatherUnlockedPrayers());

        // Spellbook: varbit 4070 (0=Standard, 1=Ancient, 2=Lunar, 3=Arceuus)
        sync.setSpellbook(client.getVarbitValue(4070));

        // Carry forward persistent ready state
        sync.setReadyState(localReadyState);
        
        // Add Raid Status
        sync.setInRaid(cachedInRaid);

        cachedLocalSync = sync;

        // Instantly push to UI locally if a stat changed
        if (panel != null && name != null && partyService.isInParty()) {
            if (needsPartySync || !localSyncPushed) {
                panel.onPlayerSync(cachedLocalSync);
                localSyncPushed = true;
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!partyService.isInParty())
            return;
        Skill s = event.getSkill();
        if (s == Skill.HITPOINTS || s == Skill.PRAYER) {
            localHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
            localMaxHp = client.getRealSkillLevel(Skill.HITPOINTS);
            localPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
            localMaxPrayer = client.getRealSkillLevel(Skill.PRAYER);
            needsPartySync = true;
            partySyncTimer = 0;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (!partyService.isInParty())
            return;
        int id = event.getContainerId();
        if (id == InventoryID.INVENTORY.getId() || id == InventoryID.EQUIPMENT.getId()) {
            needsPartySync = true;
            partySyncTimer = 0;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (partyService == null || !partyService.isInParty() || client.getLocalPlayer() == null)
            return;
        long activePrays = gatherActivePrayers();
        if (activePrays != localActivePrayers) {
            localActivePrayers = activePrays;
            needsPartySync = true;
            partySyncTimer = 0;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {

        if (evidenceScreenshotTicks > 0) {
            evidenceScreenshotTicks--;
            if (evidenceScreenshotTicks == 0) {
                evidenceScreenshotTicks = -1;
                takeEvidenceScreenshot("RaidStart");
            }
        }

        boolean inRaidNow = isPlayerInRaid();
        if (inRaidNow) {
            outOfRaidTicks = 0;
            if (!officiallyInRaid && partyService != null && partyService.isInParty()) {
                resetReadyState();
                officiallyInRaid = true;
                if (config.printRaidStartRules() || config.takeRaidStartScreenshot()) {
                    triggerRaidStartEvidence();
                }
                needsPartySync = true;
                forceFullSync = true;
                partySyncTimer = 0;
                updateCachedLocalSync();
                if (panel != null) {
                    SwingUtilities.invokeLater(() -> panel.refreshRosterContainer());
                }
            }
        } else {
            if (officiallyInRaid) {
                outOfRaidTicks++;
                if (outOfRaidTicks > 30) { // ~18 seconds out of raid to officially drop the lock
                    officiallyInRaid = false;
                    needsPartySync = true;
                    forceFullSync = true;
                    partySyncTimer = 0;
                    updateCachedLocalSync();
                    if (panel != null) {
                        SwingUtilities.invokeLater(() -> panel.refreshRosterContainer());
                    }
                }
            }
        }
        
        cachedInRaid = officiallyInRaid;
        reconcileTimer++;
        if (reconcileTimer >= 10 && partyService != null && partyService.isInParty()) {
            reconcileTimer = 0;
            java.util.Iterator<java.util.Map.Entry<Long, RaidPartyPlayerSync>> it = partyData.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Long, RaidPartyPlayerSync> entry = it.next();
                Long memberId = entry.getKey();
                if (partyService.getMemberById(memberId) == null && (partyService.getLocalMember() == null || partyService.getLocalMember().getMemberId() != memberId)) {
                    RaidPartyPlayerSync ghostSync = entry.getValue();
                    if (panel != null) {
                        panel.removeMemberById(memberId);
                        if (ghostSync != null && ghostSync.getUsername() != null) {
                            panel.removeMember(ghostSync.getUsername());
                        }
                    }
                    it.remove();
                }
            }
            java.util.Set<String> validUsernames = new java.util.HashSet<>();
            String locName = getLocalPlayerName();
            if (locName != null) {
                String cleanLoc = locName.trim();
                if (!cleanLoc.isEmpty() && !cleanLoc.equalsIgnoreCase("<unknown>")) {
                    validUsernames.add(cleanLoc.toLowerCase().replace("\u00A0", " "));
                }
            }
            for (RaidPartyPlayerSync s : partyData.values()) {
                if (s.getUsername() != null) {
                    String cleanName = s.getUsername().trim();
                    if (!cleanName.isEmpty() && !cleanName.equalsIgnoreCase("<unknown>")) {
                        validUsernames.add(cleanName.toLowerCase().replace("\u00A0", " "));
                    }
                }
            }
            
            // BACKWARDS COMPATIBILITY: Make sure any active party members with a name are valid,
            // even if they haven't sent a RaidParty payload yet.
            for (net.runelite.client.party.PartyMember pm : partyService.getMembers()) {
                if (pm.getDisplayName() != null && !pm.getDisplayName().isEmpty()) {
                    String cleanName = pm.getDisplayName().trim();
                    if (!cleanName.isEmpty() && !cleanName.equalsIgnoreCase("<unknown>")) {
                        validUsernames.add(cleanName.toLowerCase().replace("\u00A0", " "));
                        if (!partyData.containsKey(pm.getMemberId())) {
                            RaidPartyPlayerSync dummy = new RaidPartyPlayerSync();
                            dummy.setMemberId(pm.getMemberId());
                            dummy.setUsername(pm.getDisplayName());
                            partyData.put(pm.getMemberId(), dummy);
                            if (panel != null) {
                                SwingUtilities.invokeLater(() -> panel.onPlayerSync(dummy));
                            }
                        }
                    }
                }
            }
            if (panel != null) panel.reconcileMembers(validUsernames);
        }

        updateCachedLocalSync();

        if (partyService != null && partyService.isInParty()) {
            // Ensure nav button is showing
            if (!addedButton) {
                clientToolbar.addNavigation(navButton);
                addedButton = true;
            }

            if (localMaxHp == 0 && client.getLocalPlayer() != null) {
                localHp = client.getBoostedSkillLevel(Skill.HITPOINTS);
                localMaxHp = client.getRealSkillLevel(Skill.HITPOINTS);
                localPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
                localMaxPrayer = client.getRealSkillLevel(Skill.PRAYER);
                needsPartySync = true;
            }

            int run = client.getEnergy() / 100;
            int spec = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
            int combat = client.getLocalPlayer().getCombatLevel();
            int pouchHash = Arrays.hashCode(cachedLocalSync.getRunePouchIds()) * 31 + Arrays.hashCode(cachedLocalSync.getRunePouchQtys());

            if (partySyncTimer > 0)
                partySyncTimer--;

            keepaliveTicks++;
            if (keepaliveTicks >= 25) { // Force heartbeat keepalive broadcast every 25 ticks (15 seconds)
                keepaliveTicks = 0;
                needsPartySync = true;
            }

            long activePrays = cachedLocalSync != null ? cachedLocalSync.getActivePrayers() : gatherActivePrayers();
            boolean urgent = (activePrays != localActivePrayers);
            if (urgent) {
                localActivePrayers = activePrays;
                needsPartySync = true;
                partySyncTimer = 0;
            }

            if (spec != localSpec) {
                localSpec = spec;
                needsPartySync = true;
                partySyncTimer = 0; // Urgent: immediate broadcast when special attack changes
            }

            if (run != localRun || combat != localCombatLevel || pouchHash != localPouchHash) {
                localRun = run;
                localCombatLevel = combat;
                localPouchHash = pouchHash;
                needsPartySync = true; // Non-urgent: queue sync without forcing instant 600ms packet spam
            }

            if (needsPartySync && partySyncTimer == 0) {
                sendPartySyncMessage();
                needsPartySync = false;
                partySyncTimer = 1;
            }
        } else {
            partyData.clear();
            forceFullSync = true;
            lastSentUsername = null;
        }
    }

    private Integer lastSentHp;
    private Integer lastSentMaxHp;
    private Integer lastSentPrayer;
    private Integer lastSentMaxPrayer;
    private Integer lastSentSpec;
    private Integer lastSentRun;
    private Integer lastSentWorld;
    private Integer lastSentCombatLevel;
    private Integer lastSentAccountType;
    private String lastSentUsername;
    private Integer lastSentReadyState;
    private Long lastSentActivePrayers;
    private LootRule lastSentLootRule;
    private Integer lastSentStamina;
    private Integer lastSentPoison;
    private Integer lastSentDisease;
    private Integer lastSentTotalLevel;
    private Boolean lastSentVengeanceActive;
    private Integer lastSentQuiverAmmoId;
    private Integer lastSentQuiverAmmoQty;
    private Long lastSentAvailablePrayers;
    private Long lastSentUnlockedPrayers;
    private Integer lastSentSpellbook;
    private Boolean lastSentInRaid;

    private int lastSentInvHash = -1;
    private int lastSentEqpHash = -1;
    private int lastSentSkillsHash = -1;
    private int lastSentRunePouchHash = -1;
    private int lastSentXpsHash = -1;
    private int forceSyncCounter = 0;
    private boolean forceFullSync = true;

    private void sendPartySyncMessage() {
        RaidPartyPlayerSync local = cachedLocalSync;
        if (local == null || partyService == null || !partyService.isInParty())
            return;
            
        String rawName = local.getUsername();
        if (rawName == null || rawName.trim().isEmpty() || rawName.trim().equalsIgnoreCase("<unknown>")) {
            return;
        }

        forceSyncCounter++;
        boolean fullSync = (forceFullSync || lastSentUsername == null || forceSyncCounter >= 150);
        if (fullSync) {
            forceSyncCounter = 0;
            forceFullSync = false;
        }

        RaidPartyPlayerSync syncCopy = new RaidPartyPlayerSync();

        if (fullSync || !Objects.equals(local.getRawHp(), lastSentHp)) {
            syncCopy.setHp(local.getRawHp());
            lastSentHp = local.getRawHp();
        }
        if (fullSync || !Objects.equals(local.getRawMaxHp(), lastSentMaxHp)) {
            syncCopy.setMaxHp(local.getRawMaxHp());
            lastSentMaxHp = local.getRawMaxHp();
        }
        if (fullSync || !Objects.equals(local.getRawPrayer(), lastSentPrayer)) {
            syncCopy.setPrayer(local.getRawPrayer());
            lastSentPrayer = local.getRawPrayer();
        }
        if (fullSync || !Objects.equals(local.getRawMaxPrayer(), lastSentMaxPrayer)) {
            syncCopy.setMaxPrayer(local.getRawMaxPrayer());
            lastSentMaxPrayer = local.getRawMaxPrayer();
        }
        if (fullSync || !Objects.equals(local.getRawSpec(), lastSentSpec)) {
            syncCopy.setSpec(local.getRawSpec());
            lastSentSpec = local.getRawSpec();
        }
        if (fullSync || !Objects.equals(local.getRawRun(), lastSentRun)) {
            syncCopy.setRun(local.getRawRun());
            lastSentRun = local.getRawRun();
        }
        if (fullSync || !Objects.equals(local.getRawWorld(), lastSentWorld)) {
            syncCopy.setWorld(local.getRawWorld());
            lastSentWorld = local.getRawWorld();
        }
        if (fullSync || !Objects.equals(local.getRawCombatLevel(), lastSentCombatLevel)) {
            syncCopy.setCombatLevel(local.getRawCombatLevel());
            lastSentCombatLevel = local.getRawCombatLevel();
        }
        if (fullSync || !Objects.equals(local.getAccountType(), lastSentAccountType)) {
            syncCopy.setAccountType(local.getAccountType());
            lastSentAccountType = local.getAccountType();
        }
        if (fullSync || !Objects.equals(local.getUsername(), lastSentUsername)) {
            syncCopy.setUsername(local.getUsername());
            lastSentUsername = local.getUsername();
        }
        if (fullSync || !Objects.equals(local.getRawActivePrayers(), lastSentActivePrayers)) {
            syncCopy.setActivePrayers(local.getRawActivePrayers());
            lastSentActivePrayers = local.getRawActivePrayers();
        }
        if (fullSync || !Objects.equals(local.getLootRule(), lastSentLootRule)) {
            syncCopy.setLootRule(local.getLootRule());
            lastSentLootRule = local.getLootRule();
        }
        if (fullSync || !Objects.equals(local.getRawStamina(), lastSentStamina)) {
            syncCopy.setStamina(local.getRawStamina());
            lastSentStamina = local.getRawStamina();
        }
        if (fullSync || !Objects.equals(local.getRawPoison(), lastSentPoison)) {
            syncCopy.setPoison(local.getRawPoison());
            lastSentPoison = local.getRawPoison();
        }
        if (fullSync || !Objects.equals(local.getRawDisease(), lastSentDisease)) {
            syncCopy.setDisease(local.getRawDisease());
            lastSentDisease = local.getRawDisease();
        }
        if (fullSync || !Objects.equals(local.getRawTotalLevel(), lastSentTotalLevel)) {
            syncCopy.setTotalLevel(local.getRawTotalLevel());
            lastSentTotalLevel = local.getRawTotalLevel();
        }
        if (fullSync || !Objects.equals(local.getRawVengeanceActive(), lastSentVengeanceActive)) {
            syncCopy.setVengeanceActive(local.getRawVengeanceActive());
            lastSentVengeanceActive = local.getRawVengeanceActive();
        }
        if (fullSync || !Objects.equals(local.getRawQuiverAmmoId(), lastSentQuiverAmmoId)) {
            syncCopy.setQuiverAmmoId(local.getRawQuiverAmmoId());
            lastSentQuiverAmmoId = local.getRawQuiverAmmoId();
        }
        if (fullSync || !Objects.equals(local.getRawQuiverAmmoQty(), lastSentQuiverAmmoQty)) {
            syncCopy.setQuiverAmmoQty(local.getRawQuiverAmmoQty());
            lastSentQuiverAmmoQty = local.getRawQuiverAmmoQty();
        }
        if (fullSync || !Objects.equals(local.getRawAvailablePrayers(), lastSentAvailablePrayers)) {
            syncCopy.setAvailablePrayers(local.getRawAvailablePrayers());
            lastSentAvailablePrayers = local.getRawAvailablePrayers();
        }
        if (fullSync || !Objects.equals(local.getRawUnlockedPrayers(), lastSentUnlockedPrayers)) {
            syncCopy.setUnlockedPrayers(local.getRawUnlockedPrayers());
            lastSentUnlockedPrayers = local.getRawUnlockedPrayers();
        }
        if (fullSync || !Objects.equals(local.getRawSpellbook(), lastSentSpellbook)) {
            syncCopy.setSpellbook(local.getRawSpellbook());
            lastSentSpellbook = local.getRawSpellbook();
        }
        if (fullSync || !Objects.equals(local.getRawReadyState(), lastSentReadyState)) {
            syncCopy.setReadyState(local.getRawReadyState());
            lastSentReadyState = local.getRawReadyState();
        }
        if (fullSync || !Objects.equals(local.getRawInRaid(), lastSentInRaid)) {
            syncCopy.setInRaid(local.getRawInRaid());
            lastSentInRaid = local.getRawInRaid();
        }

        // Delta Array Logic: Only attach arrays if their hash changed or fullSync
        int invHash = Arrays.hashCode(local.getInvIds()) * 31 + Arrays.hashCode(local.getInvQtys());
        if (fullSync || invHash != lastSentInvHash) {
            syncCopy.setInvIds(local.getInvIds());
            syncCopy.setInvQtys(local.getInvQtys());
            lastSentInvHash = invHash;
        }

        int eqpHash = Arrays.hashCode(local.getEqpIds()) * 31 + Arrays.hashCode(local.getEqpQtys());
        if (fullSync || eqpHash != lastSentEqpHash) {
            syncCopy.setEqpIds(local.getEqpIds());
            syncCopy.setEqpQtys(local.getEqpQtys());
            lastSentEqpHash = eqpHash;
        }

        int skillsHash = Arrays.hashCode(local.getSkillLevels());
        if (fullSync || skillsHash != lastSentSkillsHash) {
            syncCopy.setSkillLevels(local.getSkillLevels());
            lastSentSkillsHash = skillsHash;
        }

        int xpsHash = Arrays.hashCode(local.getSkillXps());
        if (fullSync || xpsHash != lastSentXpsHash) {
            syncCopy.setSkillXps(local.getSkillXps());
            lastSentXpsHash = xpsHash;
        }

        int pouchHash = Arrays.hashCode(local.getRunePouchIds()) * 31 + Arrays.hashCode(local.getRunePouchQtys());
        if (fullSync || pouchHash != lastSentRunePouchHash) {
            syncCopy.setRunePouchIds(local.getRunePouchIds());
            syncCopy.setRunePouchQtys(local.getRunePouchQtys());
            lastSentRunePouchHash = pouchHash;
        }

        if (partyService.getLocalMember() != null) {
            local.setMemberId(partyService.getLocalMember().getMemberId());
            syncCopy.setMemberId(partyService.getLocalMember().getMemberId());
            partyData.put(local.getMemberId(), local);
        }

        partyService.send(syncCopy);
    }

    private void mergeDeltaSync(RaidPartyPlayerSync oldSync, RaidPartyPlayerSync newSync) {
        if (oldSync == null || newSync == null)
            return;

        if (newSync.getRawHp() == null) newSync.setHp(oldSync.getRawHp());
        if (newSync.getRawMaxHp() == null) newSync.setMaxHp(oldSync.getRawMaxHp());
        if (newSync.getRawPrayer() == null) newSync.setPrayer(oldSync.getRawPrayer());
        if (newSync.getRawMaxPrayer() == null) newSync.setMaxPrayer(oldSync.getRawMaxPrayer());
        if (newSync.getRawSpec() == null) newSync.setSpec(oldSync.getRawSpec());
        if (newSync.getRawRun() == null) newSync.setRun(oldSync.getRawRun());
        if (newSync.getRawWorld() == null) newSync.setWorld(oldSync.getRawWorld());
        if (newSync.getRawCombatLevel() == null) newSync.setCombatLevel(oldSync.getRawCombatLevel());
        if (newSync.getRawAccountType() == null) newSync.setAccountType(oldSync.getAccountType());
        if (newSync.getUsername() == null) newSync.setUsername(oldSync.getUsername());
        if (newSync.getRawReadyState() == null) newSync.setReadyState(oldSync.getRawReadyState());
        if (newSync.getRawActivePrayers() == null) newSync.setActivePrayers(oldSync.getRawActivePrayers());
        if (newSync.getLootRule() == null) newSync.setLootRule(oldSync.getLootRule());
        if (newSync.getRawStamina() == null) newSync.setStamina(oldSync.getRawStamina());
        if (newSync.getRawPoison() == null) newSync.setPoison(oldSync.getRawPoison());
        if (newSync.getRawDisease() == null) newSync.setDisease(oldSync.getRawDisease());
        if (newSync.getRawTotalLevel() == null) newSync.setTotalLevel(oldSync.getRawTotalLevel());
        if (newSync.getRawVengeanceActive() == null) newSync.setVengeanceActive(oldSync.getRawVengeanceActive());
        if (newSync.getRawQuiverAmmoId() == null) newSync.setQuiverAmmoId(oldSync.getRawQuiverAmmoId());
        if (newSync.getRawQuiverAmmoQty() == null) newSync.setQuiverAmmoQty(oldSync.getRawQuiverAmmoQty());
        if (newSync.getRawAvailablePrayers() == null) newSync.setAvailablePrayers(oldSync.getRawAvailablePrayers());
        if (newSync.getRawUnlockedPrayers() == null) newSync.setUnlockedPrayers(oldSync.getRawUnlockedPrayers());
        if (newSync.getRawSpellbook() == null) newSync.setSpellbook(oldSync.getRawSpellbook());
        if (newSync.getRawInRaid() == null) newSync.setInRaid(oldSync.getRawInRaid());

        if (newSync.getInvIds() == null) {
            newSync.setInvIds(oldSync.getInvIds());
            newSync.setInvQtys(oldSync.getInvQtys());
        }
        if (newSync.getEqpIds() == null) {
            newSync.setEqpIds(oldSync.getEqpIds());
            newSync.setEqpQtys(oldSync.getEqpQtys());
        }
        if (newSync.getSkillLevels() == null) {
            newSync.setSkillLevels(oldSync.getSkillLevels());
        }
        if (newSync.getSkillXps() == null) {
            newSync.setSkillXps(oldSync.getSkillXps());
        }
        if (newSync.getRunePouchIds() == null) {
            newSync.setRunePouchIds(oldSync.getRunePouchIds());
            newSync.setRunePouchQtys(oldSync.getRunePouchQtys());
        }
    }

    private long gatherActivePrayers() {
        long packed = 0L;
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                if (client.isPrayerActive(p)) {
                    packed |= (1L << p.ordinal());
                }
            } catch (Exception ignored) {
            }
        }

        boolean rangeActive = false;
        try { rangeActive = client.isPrayerActive(net.runelite.api.Prayer.EAGLE_EYE) || client.isPrayerActive(net.runelite.api.Prayer.DEADEYE); } catch (Exception ignored) {}
        boolean hasRangeScroll = isDeadeyeUnlocked();

        if (rangeActive) {
            if (hasRangeScroll) {
                packed |= (1L << net.runelite.api.Prayer.EAGLE_EYE.ordinal());
                packed &= ~(1L << net.runelite.api.Prayer.DEADEYE.ordinal());
            } else {
                packed |= (1L << net.runelite.api.Prayer.DEADEYE.ordinal());
                packed &= ~(1L << net.runelite.api.Prayer.EAGLE_EYE.ordinal());
            }
        } else {
            packed &= ~(1L << net.runelite.api.Prayer.EAGLE_EYE.ordinal());
            packed &= ~(1L << net.runelite.api.Prayer.DEADEYE.ordinal());
        }

        boolean magicActive = false;
        try { magicActive = client.isPrayerActive(net.runelite.api.Prayer.MYSTIC_MIGHT) || client.isPrayerActive(net.runelite.api.Prayer.MYSTIC_VIGOUR); } catch (Exception ignored) {}
        boolean hasMagicScroll = isMysticVigourUnlocked();

        if (magicActive) {
            if (hasMagicScroll) {
                packed |= (1L << net.runelite.api.Prayer.MYSTIC_MIGHT.ordinal());
                packed &= ~(1L << net.runelite.api.Prayer.MYSTIC_VIGOUR.ordinal());
            } else {
                packed |= (1L << net.runelite.api.Prayer.MYSTIC_VIGOUR.ordinal());
                packed &= ~(1L << net.runelite.api.Prayer.MYSTIC_MIGHT.ordinal());
            }
        } else {
            packed &= ~(1L << net.runelite.api.Prayer.MYSTIC_MIGHT.ordinal());
            packed &= ~(1L << net.runelite.api.Prayer.MYSTIC_VIGOUR.ordinal());
        }

        return packed;
    }

    private boolean checkWidgetTreeContainsSprite(net.runelite.api.widgets.Widget widget, int s1, int s2) {
        if (widget == null) return false;
        try {
            int sid = widget.getSpriteId();
            if (sid == s1 || sid == s2) return true;
        } catch (Exception ignored) {}
        try {
            net.runelite.api.widgets.Widget[] children = widget.getChildren();
            if (children != null) {
                for (net.runelite.api.widgets.Widget child : children) {
                    if (checkWidgetTreeContainsSprite(child, s1, s2)) return true;
                }
            }
        } catch (Exception ignored) {}
        try {
            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null) {
                for (net.runelite.api.widgets.Widget child : staticChildren) {
                    if (checkWidgetTreeContainsSprite(child, s1, s2)) return true;
                }
            }
        } catch (Exception ignored) {}
        try {
            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null) {
                for (net.runelite.api.widgets.Widget child : dynamicChildren) {
                    if (checkWidgetTreeContainsSprite(child, s1, s2)) return true;
                }
            }
        } catch (Exception ignored) {}
        try {
            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null) {
                for (net.runelite.api.widgets.Widget child : nestedChildren) {
                    if (checkWidgetTreeContainsSprite(child, s1, s2)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isDeadeyeUnlocked() {
        try {
            if (client.getVarbitValue(Varbits.PRAYER_DEADEYE_UNLOCKED) != 0 || client.getVarbitValue(16090) != 0) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            for (int i = 0; i < 50; i++) {
                if (checkWidgetTreeContainsSprite(client.getWidget(541, i), 1422, 1426)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isMysticVigourUnlocked() {
        try {
            if (client.getVarbitValue(Varbits.PRAYER_MYSTIC_VIGOUR_UNLOCKED) != 0 || client.getVarbitValue(16091) != 0) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            for (int i = 0; i < 50; i++) {
                if (checkWidgetTreeContainsSprite(client.getWidget(541, i), 1423, 1427)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private long gatherAvailablePrayers() {
        long packed = 0L;
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                // A prayer is "available" if the player has the required prayer level
                if (client.getRealSkillLevel(Skill.PRAYER) >= getPrayerLevelRequirement(p)) {
                    packed |= (1L << p.ordinal());
                }
            } catch (Exception ignored) {
            }
        }
        return packed;
    }

    private long gatherUnlockedPrayers() {
        long packed = 0L;
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                boolean unlocked = true;
                switch (p) {
                    case PIETY:
                    case CHIVALRY:
                        unlocked = client.getVarbitValue(3909) == 8; // Knight Waves completion varbit
                        break;
                    case RIGOUR:
                        unlocked = client.getVarbitValue(5451) == 1; // Rigour unlock varbit
                        break;
                    case AUGURY:
                        unlocked = client.getVarbitValue(5452) == 1; // Augury unlock varbit
                        break;
                    case PRESERVE:
                        unlocked = client.getVarbitValue(5453) == 1; // Preserve unlock varbit
                        break;
                    case DEADEYE:
                    case MYSTIC_VIGOUR:
                    case EAGLE_EYE:
                    case MYSTIC_MIGHT:
                        unlocked = true;
                        break;
                    default:
                        unlocked = true;
                        break;
                }
                if (unlocked) {
                    packed |= (1L << p.ordinal());
                }
            } catch (Exception ignored) {
            }
        }
        return packed;
    }

    private int getPrayerLevelRequirement(net.runelite.api.Prayer prayer) {
        // Standard prayer level requirements
        switch (prayer) {
            case THICK_SKIN:
                return 1;
            case BURST_OF_STRENGTH:
                return 4;
            case CLARITY_OF_THOUGHT:
                return 7;
            case SHARP_EYE:
                return 8;
            case MYSTIC_WILL:
                return 9;
            case ROCK_SKIN:
                return 10;
            case SUPERHUMAN_STRENGTH:
                return 13;
            case IMPROVED_REFLEXES:
                return 16;
            case RAPID_RESTORE:
                return 19;
            case RAPID_HEAL:
                return 22;
            case PROTECT_ITEM:
                return 25;
            case HAWK_EYE:
                return 26;
            case MYSTIC_LORE:
                return 27;
            case STEEL_SKIN:
                return 28;
            case ULTIMATE_STRENGTH:
                return 31;
            case INCREDIBLE_REFLEXES:
                return 34;
            case PROTECT_FROM_MAGIC:
                return 37;
            case PROTECT_FROM_MISSILES:
                return 40;
            case PROTECT_FROM_MELEE:
                return 43;
            case EAGLE_EYE:
                return 44;
            case MYSTIC_MIGHT:
                return 45;
            case RETRIBUTION:
                return 46;
            case REDEMPTION:
                return 49;
            case SMITE:
                return 52;
            case PRESERVE:
                return 55;
            case CHIVALRY:
                return 60;
            case PIETY:
                return 70;
            case RIGOUR:
                return 74;
            case AUGURY:
                return 77;
            default:
                return 1;
        }
    }

    private void gatherRunePouchContents(RaidPartyPlayerSync sync) {
        try {
            final int[] RUNE_POUCH_AMOUNT_VARBITS = {
                    Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3,
                    Varbits.RUNE_POUCH_AMOUNT4
            };
            final int[] RUNE_POUCH_RUNE_VARBITS = {
                    Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3,
                    Varbits.RUNE_POUCH_RUNE4
            };

            List<Integer> ids = new ArrayList<>();
            List<Integer> qtys = new ArrayList<>();

            EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
            for (int i = 0; i < RUNE_POUCH_AMOUNT_VARBITS.length; i++) {
                int amount = client.getVarbitValue(RUNE_POUCH_AMOUNT_VARBITS[i]);
                if (amount <= 0)
                    continue;
                int runeId = client.getVarbitValue(RUNE_POUCH_RUNE_VARBITS[i]);
                if (runeId == 0)
                    continue;
                int itemId = runepouchEnum.getIntValue(runeId);
                ids.add(itemId);
                qtys.add(amount);
            }

            sync.setRunePouchIds(ids.stream().mapToInt(Integer::intValue).toArray());
            sync.setRunePouchQtys(qtys.stream().mapToInt(Integer::intValue).toArray());
        } catch (Exception e) {
            sync.setRunePouchIds(new int[0]);
            sync.setRunePouchQtys(new int[0]);
        }
    }

    private int[] gatherSkillLevels() {
        Skill[] skills = Skill.values();
        int[] levels = new int[skills.length * 2];
        for (int i = 0; i < skills.length; i++) {
            try {
                levels[i * 2] = client.getBoostedSkillLevel(skills[i]);
                levels[i * 2 + 1] = client.getRealSkillLevel(skills[i]);
            } catch (Exception ignored) {
            }
        }
        return levels;
    }

    private int[] gatherSkillXps() {
        Skill[] skills = Skill.values();
        int[] xps = new int[skills.length];
        for (int i = 0; i < skills.length; i++) {
            try {
                xps[i] = client.getSkillExperience(skills[i]);
            } catch (Exception ignored) {
            }
        }
        return xps;
    }

    // --- AUTO-LEAVE ON IDLE ---
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            lastLogout = Instant.now();
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            // Force a full party sync once we enter the game so our newly populated arrays broadcast
            forceFullSync = true;
            lastSentUsername = null;
            lastSentInvHash = -1;
            lastSentEqpHash = -1;
            lastSentSkillsHash = -1;
            lastSentRunePouchHash = -1;
            lastSentXpsHash = -1;
            needsPartySync = true;
            
            // Ensure native party service is updated upon login
            if (partyService != null && partyService.getLocalMember() != null) {
                String name = getLocalPlayerName();
                if (name != null && !name.isEmpty()) {
                    partyService.getLocalMember().setDisplayName(name);
                }
            }
        }
    }

    @Subscribe
    public void onChatMessage(net.runelite.api.events.ChatMessage event) {
        if (!config.announceMegarares() || partyService == null || !partyService.isInParty()) {
            return;
        }

        if (event.getType() != net.runelite.api.ChatMessageType.GAMEMESSAGE &&
            event.getType() != net.runelite.api.ChatMessageType.SPAM &&
            event.getType() != net.runelite.api.ChatMessageType.BROADCAST) {
            return;
        }

        // Prevent player-spoofed chat messages
        if (event.getName() != null && !event.getName().isEmpty()) {
            return;
        }

        String rawMessage = net.runelite.client.util.Text.removeTags(event.getMessage());
        String message = rawMessage.toLowerCase();

        // Check if it's a drop message
        if (!message.contains("special loot:") && !message.contains("found some loot:") && !message.contains("received a drop:") && !message.contains("found some special loot:") && !message.contains("found something special:") && !message.contains("valuable drop:")) {
            return;
        }

        java.util.Map<String, Integer> megarares = new HashMap<>();
        megarares.put("twisted bow", 20997);
        megarares.put("kodai insignia", 21043);
        megarares.put("elder maul", 21003);
        megarares.put("dragon hunter crossbow", 21012);
        megarares.put("dinh's bulwark", 21015);
        megarares.put("ancestral hat", 21018);
        megarares.put("ancestral robe top", 21021);
        megarares.put("ancestral robe bottom", 21024);
        megarares.put("dragon claws", 13652);
        megarares.put("twisted buckler", 21000);
        megarares.put("scythe of vitur", 22486); // Uncharged
        megarares.put("ghrazi rapier", 22324);
        megarares.put("sanguinesti staff", 22323); // Uncharged
        megarares.put("justiciar faceguard", 22326);
        megarares.put("justiciar chestguard", 22327);
        megarares.put("justiciar legguards", 22328);
        megarares.put("avernic defender hilt", 22477);
        megarares.put("tumeken's shadow", 27277); // Uncharged
        megarares.put("elidinis' ward", 25985);
        megarares.put("osmumten's fang", 26219);
        megarares.put("lightbearer", 25975);
        megarares.put("masori mask", 27226);
        megarares.put("masori body", 27229);
        megarares.put("masori chaps", 27232);

        String matchedItem = null;
        int matchedId = -1;
        for (Map.Entry<String, Integer> entry : megarares.entrySet()) {
            if (message.contains(entry.getKey())) {
                matchedItem = entry.getKey();
                matchedId = entry.getValue();
                break;
            }
        }

        boolean isHighValueCoinMessage = false;
        long parsedCoins = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([\\d,]+) coins\\)").matcher(rawMessage);
        if (m.find()) {
            try {
                parsedCoins = Long.parseLong(m.group(1).replace(",", ""));
                if (parsedCoins >= 10_000_000) {
                    isHighValueCoinMessage = true;
                }
            } catch (Exception e) {}
        }

        if (matchedItem != null || isHighValueCoinMessage) {
            String playerName = "Someone";
            if (message.contains(" - ")) { // CoX style: "Special loot: Patrolman - Twisted bow"
                String[] parts = rawMessage.split(" - ");
                if (parts.length >= 2) {
                    playerName = parts[0].replace("Special loot:", "").trim();
                }
            } else if (message.contains("received a drop:")) {
                playerName = rawMessage.split("received a drop:")[0].trim();
            } else if (message.contains("found some special loot:")) {
                playerName = rawMessage.split("found some special loot:")[0].trim();
            } else if (message.contains("found something special:")) {
                playerName = rawMessage.split("found something special:")[0].trim();
            } else if (message.contains("found some loot:")) {
                playerName = rawMessage.split("found some loot:")[0].trim();
            } else if (message.contains("valuable drop:")) {
                playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "You";
            }
            
            // Format name properly
            if (playerName.length() > 0) {
                playerName = playerName.substring(0, 1).toUpperCase() + playerName.substring(1);
            }
            
            String finalItemName = "";
            int price = 0;

            if (matchedItem != null) {
                // Title case the item name
                for (String word : matchedItem.split(" ")) {
                    if (!finalItemName.isEmpty()) finalItemName += " ";
                    finalItemName += word.substring(0, 1).toUpperCase() + word.substring(1);
                }
                price = itemManager.getItemPrice(matchedId);
            } else {
                // Extract the item name from the message before the '('
                java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile(":\\s*(?:\\d+\\s*x\\s*)?([^(]+)\\s*\\(").matcher(rawMessage);
                if (nameMatcher.find()) {
                    finalItemName = nameMatcher.group(1).trim();
                } else {
                    finalItemName = "Valuable Item";
                }
                price = (int) parsedCoins;
            }

            String priceStr = price > 0 ? java.text.NumberFormat.getInstance().format(price) : "Unknown";
            
            String date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dropMessage = playerName + " Received: <col=ef20ff>" + finalItemName + "</col> [<col=00ff00>" + priceStr + "</col> gp] [" + date + "]";
            postPartyChat(dropMessage);

            if (config.takeDropScreenshot()) {
                clientThread.invokeLater(() -> {
                    takeEvidenceScreenshot("Drops");
                });
            }
        }
    }

    @Schedule(period = 10, unit = ChronoUnit.SECONDS)
    public void checkIdle() {
        if (client.getGameState() != GameState.LOGIN_SCREEN)
            return;
        if (lastLogout != null && lastLogout.isBefore(Instant.now().minus(30, ChronoUnit.MINUTES))
                && partyService.isInParty()) {
            log.info("Leaving party due to 30 minute idle timeout");
            partyService.changeParty(null);
            SwingUtilities.invokeLater(() -> panel.updateConnectionState(false, ""));
        }
    }

    public RaidPartyConfig getConfig() {
        return config;
    }

    // --- Ready Check ---
    public int getLocalReadyState() {
        return localReadyState;
    }

    public void setReadyState(int state) {
        localReadyState = state;
        cachedLocalSync.setReadyState(state);

        if (state > 0) {
            if (config.chatReadyToggle() && System.currentTimeMillis() - lastChatBroadcastTime > 1500) {
                String name = getLocalPlayerName();
                if (name == null) name = "Unknown";
                String statusText = state == 1 ? "is now <col=00ff00>Ready</col>"
                        : "is now <col=ff0000>Not Ready</col>";
                postPartyChat(name + " " + statusText);
                lastChatBroadcastTime = System.currentTimeMillis();
            }
        }

        if (partyService.isInParty()) {
            needsPartySync = true;
        }
    }

    public void resetReadyState() {
        boolean changed = false;
        if (localReadyState != 0) {
            localReadyState = 0;
            if (cachedLocalSync != null) {
                cachedLocalSync.setReadyState(0);
            }
            needsPartySync = true;
            partySyncTimer = 0; // Force immediate broadcast to party
            changed = true;
        }
        for (RaidPartyPlayerSync s : partyData.values()) {
            if (s != null && s.getReadyState() != 0) {
                s.setReadyState(0);
                changed = true;
            }
        }
        if (changed && panel != null) {
            javax.swing.SwingUtilities.invokeLater(() -> panel.repaint());
        }
    }

    // --- Loot Rule ---
    public void setLootRule(LootRule rule) {
        if (isLootLocked()) {
            return; // Block changing loot rule while actively inside a raid
        }
        localLootRule = rule;
        cachedLocalSync.setLootRule(rule);

        // Broadcast to party chat locally
        if (rule != LootRule.UNSPECIFIED) {
            if (config.chatLootToggle() && System.currentTimeMillis() - lastChatBroadcastTime > 1500) {
                String name = getLocalPlayerName();
                if (name == null) name = "Unknown";
                String color = rule == LootRule.FFA ? "af00af" : "00bfff";
                postPartyChat(name + " Loot Confirmed: <col=" + color + ">" + rule + "</col>");
                lastChatBroadcastTime = System.currentTimeMillis();
            }
        }

        if (partyService.isInParty()) {
            needsPartySync = true;
        }
    }

    public boolean isPartyMemberWithLocalPlayer(RaidPartyPlayerSync syncData) {
        if (syncData == null || client.getLocalPlayer() == null) return false;
        if (partyService != null && partyService.getLocalMember() != null) {
            if (syncData.getMemberId() == partyService.getLocalMember().getMemberId()) {
                return true;
            }
        }
        // 1. Must be on exact same world
        if (syncData.getWorld() == 0 || syncData.getWorld() != client.getWorld()) {
            return false;
        }
        // 2. If we are in an instanced region (Raid rooms, boss rooms, instanced lobbies),
        // check if their character entity is loaded inside our exact instance!
        if (isPlayerInRaid() || (client.isInInstancedRegion() && client.getLocalPlayer().getLocalLocation() != null)) {
            String targetUsername = syncData.getUsername();
            if (targetUsername != null && !targetUsername.isEmpty()) {
                String cleanTarget = net.runelite.client.util.Text.removeTags(targetUsername).toLowerCase();
                if (client.getPlayers() != null) {
                    for (net.runelite.api.Player p : client.getPlayers()) {
                        if (p != null && p.getName() != null) {
                            String cleanPlayer = net.runelite.client.util.Text.removeTags(p.getName()).toLowerCase();
                            if (cleanPlayer.equals(cleanTarget)) {
                                return true;
                            }
                        }
                    }
                }
                return false; // On same world, but NOT inside our instanced raid/room!
            }
        }

        // 3. Fallback (not inside raid instance, no active party board registered): return true if on our exact same world
        return true;
    }

    public boolean isPlayerInRaid() {
        if (client.getLocalPlayer() == null) return false;

        // 1. CoX Varbit & Region check (Consider in-raid immediately upon entering lobby to lock loot)
        try {
            if (client.getVarbitValue(Varbits.IN_RAID) == 1 && client.isInInstancedRegion() && client.getVarbitValue(6386) != 0) {
                return true;
            }
        } catch (Exception ignored) {}

        // 2. ToB Varbit check (2=Inside/Spectator, 3=Dead Spectating)
        try {
            int tobState = client.getVarbitValue(Varbits.THEATRE_OF_BLOOD);
            if ((tobState == 2 || tobState == 3) && client.isInInstancedRegion()) return true;
        } catch (Exception ignored) {}

        // 3. (Removed ToA Varbit check because TOA_RAID_LEVEL persists outside the raid)

        // 4. Region ID checks (Supports both Instanced and Static regions)
        int instancedRegion = -1;
        if (client.isInInstancedRegion() && client.getLocalPlayer().getLocalLocation() != null) {
            net.runelite.api.coords.WorldPoint wp = net.runelite.api.coords.WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
            if (wp != null) {
                instancedRegion = wp.getRegionID();
            }
        }
        int worldRegion = client.getLocalPlayer().getWorldLocation().getRegionID();

        int[] regionsToCheck = {instancedRegion, worldRegion};
        for (int region : regionsToCheck) {
            if (region == -1) continue;

            // ToA Rooms (Lobby, Nexus, Kephri, Zebak, Akkha, Baba, Wardens, Loot)
            if (region == 14674 || region == 14160 || region == 14162 || region == 14163 || region == 14164 || 
                region == 14686 || region == 14687 || region == 14688 || 
                region == 15184 || region == 15186 || region == 15187 || region == 15188 || 
                region == 15696 || region == 15698 || region == 15699 || region == 15700) {
                return true;
            }
            // ToB Rooms (Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik, Loot)
            if (region == 12611 || region == 12612 || region == 12613 || region == 13122 || region == 13123 || region == 13125 || region == 12867) {
                return true;
            }
            // CoX Rooms (Olm room specifically if varbits delayed, excluding gearing lobby 13138)
            if (region == 12889) {
                return true;
            }
        }

        return false;
    }

    private boolean cachedInRaid = false;

    public boolean isLootLocked() {
        return cachedInRaid;
    }

    // --- Evidence ---
    private void triggerRaidStartEvidence() {
        if (partyService == null || !partyService.isInParty()) return;

        String date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        StringBuilder sb = new StringBuilder();
        sb.append("<col=ffffff>Raid Started [</col><col=909090>").append(date).append("</col><col=ffffff>]! Rules:</col> ");
        boolean first = true;
        for (net.runelite.client.party.PartyMember member : partyService.getMembers()) {
            if (member == null) continue;
            RaidPartyPlayerSync sync = partyData.get(member.getMemberId());
            if (config.filterRaidScreenshots() && sync != null && !isPartyMemberWithLocalPlayer(sync)) {
                continue;
            }
            if (!first) sb.append("<col=ffffff>, </col>");
            
            sb.append("<col=55aaff>").append(member.getDisplayName()).append("</col> ");
            
            if (sync != null && sync.getLootRule() != null && sync.getLootRule() != LootRule.UNSPECIFIED) {
                String color = sync.getLootRule() == LootRule.FFA ? "af00af" : "00bfff";
                sb.append("<col=").append(color).append(">(").append(sync.getLootRule().name()).append(")</col>");
            } else {
                sb.append("<col=ff5555>(Unspecified)</col>");
            }
            first = false;
        }
        
        if (first) {
            sb.append("<col=ff5555>None</col>");
        }

        if (config.printRaidStartRules()) {
            postPartyChat(sb.toString());
        }

        if (config.takeRaidStartScreenshot()) {
            // Delay screenshot by 6 game ticks (~3.6 seconds) to ensure black loading screen fades
            evidenceScreenshotTicks = 6;
        }
    }

    private void takeEvidenceScreenshot(String subDir) {
        java.util.function.Consumer<Image> imageCallback = (img) -> {
            Graphics2D graphics = (Graphics2D) img.getGraphics();

            String date = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String playerName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";
            String watermark = "RaidParty Verified - " + date + " " + time + " - " + playerName;

            // --- 1% Stealth Chatbox Seal ---
            net.runelite.api.widgets.Widget chatbox = client.getWidget(net.runelite.api.widgets.ComponentID.CHATBOX_FRAME);
            if (chatbox != null && !chatbox.isHidden()) {
                java.awt.Rectangle bounds = chatbox.getBounds();
                graphics.setClip(bounds);
                
                Color stealthText = new Color(255, 255, 255, 3); // 1% white
                Color stealthShadow = new Color(0, 0, 0, 3);     // 1% black
                
                // Smaller font for tighter stacking inside the chatbox
                graphics.setFont(new Font("Arial", Font.BOLD, 18));
                FontMetrics sm = graphics.getFontMetrics();
                int sWidth = sm.stringWidth(watermark) + 10;
                int sHeight = sm.getHeight() + 5;
                
                for (int y = bounds.y; y < bounds.y + bounds.height + sHeight; y += sHeight) {
                    for (int x = bounds.x; x < bounds.x + bounds.width; x += sWidth) {
                        graphics.setColor(stealthShadow);
                        graphics.drawString(watermark, x + 1, y + 1);
                        graphics.setColor(stealthText);
                        graphics.drawString(watermark, x, y);
                    }
                }
                graphics.setClip(null);
            }
            graphics.dispose();

            BufferedImage bufferedImage = (BufferedImage) img;
            imageCapture.takeScreenshot(bufferedImage, "RaidParty", subDir, false, ImageUploadStyle.NEITHER);
        };

        drawManager.requestNextFrameListener(imageCallback);
    }


}
