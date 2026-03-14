package com.github.kalsupes.raidparty;

import com.google.inject.Provides;
import java.util.List;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Varbits;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.game.ItemManager;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.task.Schedule;
import net.runelite.client.util.HotkeyListener;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Item;
import net.runelite.api.InventoryID;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
    private RaidPartyOverlay raidpartyOverlay;



    @Inject
    private net.runelite.client.eventbus.EventBus eventBus;

    @Inject
    private KeyManager keyManager;

    private RaidPartyPanel panel;
    private NavigationButton navButton;
    private boolean addedButton = false;
    private Instant lastLogout;

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
        keyManager.unregisterKeyListener(safePingHotkey);
        keyManager.unregisterKeyListener(cautionPingHotkey);
        keyManager.unregisterKeyListener(dangerPingHotkey);
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

    // --- PARTY MANAGEMENT ---
    public void joinParty(String passphrase) {
        if (passphrase == null || passphrase.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(panel,
                    "Please enter a passphrase.", "Error", javax.swing.JOptionPane.ERROR_MESSAGE));
            return;
        }

        configManager.setConfiguration("raidparty", "previousParty", passphrase);

        partyService.changeParty(passphrase);
        panel.updateConnectionState(true, passphrase);

        // Setup local player immediately
        String localName = getLocalPlayerName();
        if (localName != null) {
            panel.addMember(localName, true);
        }
    }

    public void leaveParty() {
        partyService.changeParty(null);
        panel.updateConnectionState(false, "");
        partyData.clear();
    }

    @Subscribe
    public void onUserJoin(UserJoin event) {
        // We do not have usernames at user join until they send a sync broadcast.
        // However, we MUST dispatch a fresh full payload to them immediately so their
        // client receives a baseline cache of our arrays rather than hollow deltas.
        lastSentInvHash = -1;
        lastSentEqpHash = -1;
        lastSentSkillsHash = -1;
        lastSentRunePouchHash = -1;
        needsPartySync = true;
    }

    @Subscribe
    public void onUserPart(UserPart event) {
        RaidPartyPlayerSync sync = partyData.remove(event.getMemberId());
        if (sync != null && sync.getUsername() != null) {
            panel.removeMember(sync.getUsername());
        }
    }

    public String getLocalPlayerName() {
        if (client != null && client.getLocalPlayer() != null) {
            return client.getLocalPlayer().getName();
        }
        return null;
    }

    // --- PING SYSTEM HOOKS ---
    private final HotkeyListener safePingHotkey = new HotkeyListener(() -> config.safePingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(0);
        }
    };

    private final HotkeyListener cautionPingHotkey = new HotkeyListener(() -> config.cautionPingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(1);
        }
    };

    private final HotkeyListener dangerPingHotkey = new HotkeyListener(() -> config.dangerPingHotkey()) {
        @Override
        public void hotkeyPressed() {
            executePing(2);
        }
    };

    // --- HOTKEY MESSAGE HOOKS (disabled — muted players can't be detected) ---
    // private final HotkeyListener msgHotkey1 = new HotkeyListener(() ->
    // config.hotkeyBind1()) {
    // @Override public void hotkeyPressed() {
    // sendHotkeyMessage(config.hotkeyMessage1()); }
    // };
    // private final HotkeyListener msgHotkey2 = new HotkeyListener(() ->
    // config.hotkeyBind2()) {
    // @Override public void hotkeyPressed() {
    // sendHotkeyMessage(config.hotkeyMessage2()); }
    // };
    // private final HotkeyListener msgHotkey3 = new HotkeyListener(() ->
    // config.hotkeyBind3()) {
    // @Override public void hotkeyPressed() {
    // sendHotkeyMessage(config.hotkeyMessage3()); }
    // };

    private void initHotkeys() {
        keyManager.registerKeyListener(safePingHotkey);
        keyManager.registerKeyListener(cautionPingHotkey);
        keyManager.registerKeyListener(dangerPingHotkey);
    }

    private void sendHotkeyMessage(String message) {
        if (message == null || message.isEmpty())
            return;

        String name = getLocalPlayerName();
        if (name == null)
            name = "Unknown";

        if (partyService != null && partyService.isInParty()) {
            RaidPartyPartyMessage msg = new RaidPartyPartyMessage(name, message);
            partyService.send(msg);
        }

        final String finalName = name;
        clientThread.invokeLater(() -> {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ffff>[RaidParty]</col> <col=ffffff>" + finalName + ":</col> " + message, "");

            if (client.getLocalPlayer() != null) {
                client.getLocalPlayer().setOverheadText(message);
                client.getLocalPlayer().setOverheadCycle(150); // Optional: if supported
            }
        });
    }

    private void executePing(int pingType) {
        clientThread.invokeLater(() -> {
            net.runelite.api.Tile targetTile = client.getSelectedSceneTile();
            if (targetTile == null)
                return;

            WorldPoint wp = targetTile.getWorldLocation();
            if (wp == null)
                return;

            int tType = 0;
            int tIndex = -1;

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

            activePings.add(new BossPing(wp, pingType, tType, tIndex, System.currentTimeMillis() + 4000));
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
        }

        if (soundId != -1) {
            final int sid = soundId;
            clientThread.invokeLater(() -> client.playSoundEffect(sid));
        }
    }

    @Subscribe
    public void onBossPingMessage(BossPingMessage event) {
        if (partyService != null && partyService.getLocalMember() != null
                && partyService.getLocalMember().getMemberId() == event.getMemberId()) {
            return;
        }
        WorldPoint wp = new WorldPoint(event.getX(), event.getY(), event.getPlane());
        activePings.add(new BossPing(wp, event.getPingType(), event.getTargetType(), event.getTargetIndex(),
                System.currentTimeMillis() + 4000));
        playPingSound(event.getPingType(), event.getTargetType());
    }

    @Subscribe
    public void onRaidPartyPartyMessage(RaidPartyPartyMessage event) {
        if (partyService == null || partyService.getLocalMember() == null)
            return;
        if (event.getMemberId() == partyService.getLocalMember().getMemberId())
            return;
        clientThread.invokeLater(() -> {
            client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ffff>[RaidParty]</col> <col=ffffff>" + event.getSenderName() + ":</col> "
                            + event.getMessage(),
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
                        postPartyChat(event.getUsername() + " is <col=00ff00>Ready</col>");
                    } else if (event.getReadyState() == 2) {
                        postPartyChat(event.getUsername() + " is <col=ff0000>Not Ready</col>");
                    }
                }
            }
            // Loot rule change
            if (existing == null || existing.getLootRule() != event.getLootRule()) {
                if (event.getLootRule() != null && event.getLootRule() != LootRule.UNSPECIFIED) {
                    if (config.chatLootToggle()) {
                        String color = event.getLootRule() == LootRule.FFA ? "af00af" : "00bfff";
                        postPartyChat(event.getUsername() + " set loot to <col=" + color + ">" + event.getLootRule() + "</col>");
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
        final String chatMsg = "<col=00ffff>[RaidParty]</col> " + message;
        clientThread.invokeLater(
                () -> client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", chatMsg, ""));
    }

    // --- LIVE PARTY SYNC LOGIC ---
    private int localHp, localMaxHp, localPrayer, localMaxPrayer, localSpec, localRun;
    private Item[] localEquipment = new Item[0];
    private Item[] localInventory = new Item[0];
    private boolean needsPartySync = false;
    private int partySyncTimer = 0;

    private final Map<Long, RaidPartyPlayerSync> partyData = new HashMap<>();

    public Map<Long, RaidPartyPlayerSync> getPartyData() {
        return partyData;
    }

    private volatile RaidPartyPlayerSync cachedLocalSync = new RaidPartyPlayerSync();

    public RaidPartyPlayerSync getLocalPlayerSync() {
        return cachedLocalSync;
    }

    // Persistent ready state (survives sync rebuilds)
    private int localReadyState = 0;

    // Persistent loot rule
    private LootRule localLootRule = LootRule.UNSPECIFIED;

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
        sync.setSpec(client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
        sync.setRun(client.getEnergy());

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
        sync.setLootRule(localLootRule);

        // New fields: stamina, poison, disease, total level
        sync.setStamina(client.getVarbitValue(Varbits.STAMINA_EFFECT));
        sync.setPoison(client.getVarpValue(VarPlayer.POISON));
        sync.setDisease(client.getVarpValue(VarPlayer.DISEASE_VALUE));
        sync.setTotalLevel(client.getTotalLevel());

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

        cachedLocalSync = sync;

        // Instantly push to UI locally
        if (panel != null && name != null && partyService.isInParty()) {
            panel.onPlayerSync(cachedLocalSync);
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!partyService.isInParty())
            return;
        Skill s = event.getSkill();
        if (s == Skill.HITPOINTS) {
            localHp = event.getLevel();
            localMaxHp = client.getRealSkillLevel(Skill.HITPOINTS);
            needsPartySync = true;
        } else if (s == Skill.PRAYER) {
            localPrayer = event.getLevel();
            localMaxPrayer = client.getRealSkillLevel(Skill.PRAYER);
            needsPartySync = true;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (!partyService.isInParty())
            return;
        int id = event.getContainerId();
        if (id == InventoryID.INVENTORY.getId()) {
            localInventory = event.getItemContainer().getItems();
            needsPartySync = true;
        } else if (id == InventoryID.EQUIPMENT.getId()) {
            localEquipment = event.getItemContainer().getItems();
            needsPartySync = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
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

            int run = client.getEnergy();
            int spec = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
            if (run != localRun || spec != localSpec) {
                localRun = run;
                localSpec = spec;
                needsPartySync = true;
            }

            // Dynamic tick frequency: scale with party size
            int syncFreq = Math.max(2, partyData.size() - 4);
            if (needsPartySync) {
                if (partySyncTimer > 0)
                    partySyncTimer--;
                else {
                    sendPartySyncMessage();
                    needsPartySync = false;
                    partySyncTimer = syncFreq;
                }
            }
        } else {
            partyData.clear();
        }
    }

    private int lastSentInvHash;
    private int lastSentEqpHash;
    private int lastSentSkillsHash;
    private int lastSentRunePouchHash;

    private void sendPartySyncMessage() {
        RaidPartyPlayerSync local = cachedLocalSync;
        if (local == null)
            return;

        // Create a distinct copy for the network dispatch so we don't corrupt the local
        // UI's data
        RaidPartyPlayerSync syncCopy = new RaidPartyPlayerSync();
        syncCopy.setHp(local.getHp());
        syncCopy.setMaxHp(local.getMaxHp());
        syncCopy.setPrayer(local.getPrayer());
        syncCopy.setMaxPrayer(local.getMaxPrayer());
        syncCopy.setSpec(local.getSpec());
        syncCopy.setRun(local.getRun());
        syncCopy.setWorld(local.getWorld());
        syncCopy.setCombatLevel(local.getCombatLevel());
        syncCopy.setUsername(local.getUsername());
        syncCopy.setActivePrayers(local.getActivePrayers());
        syncCopy.setLootRule(local.getLootRule());
        syncCopy.setStamina(local.getStamina());
        syncCopy.setPoison(local.getPoison());
        syncCopy.setDisease(local.getDisease());
        syncCopy.setTotalLevel(local.getTotalLevel());
        syncCopy.setQuiverAmmoId(local.getQuiverAmmoId());
        syncCopy.setQuiverAmmoQty(local.getQuiverAmmoQty());
        syncCopy.setAvailablePrayers(local.getAvailablePrayers());
        syncCopy.setUnlockedPrayers(local.getUnlockedPrayers());
        syncCopy.setSpellbook(local.getSpellbook());
        syncCopy.setReadyState(local.getReadyState());

        // Delta Array Logic: Only attach arrays if their hash changed
        int invHash = Arrays.hashCode(local.getInvIds()) * 31 + Arrays.hashCode(local.getInvQtys());
        if (invHash != lastSentInvHash) {
            syncCopy.setInvIds(local.getInvIds());
            syncCopy.setInvQtys(local.getInvQtys());
            lastSentInvHash = invHash;
        }

        int eqpHash = Arrays.hashCode(local.getEqpIds()) * 31 + Arrays.hashCode(local.getEqpQtys());
        if (eqpHash != lastSentEqpHash) {
            syncCopy.setEqpIds(local.getEqpIds());
            syncCopy.setEqpQtys(local.getEqpQtys());
            lastSentEqpHash = eqpHash;
        }

        int skillsHash = Arrays.hashCode(local.getSkillLevels());
        if (skillsHash != lastSentSkillsHash) {
            syncCopy.setSkillLevels(local.getSkillLevels());
            lastSentSkillsHash = skillsHash;
        }

        int pouchHash = Arrays.hashCode(local.getRunePouchIds()) * 31 + Arrays.hashCode(local.getRunePouchQtys());
        if (pouchHash != lastSentRunePouchHash) {
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
        if (oldSync == null)
            return;

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
        if (newSync.getRunePouchIds() == null) {
            newSync.setRunePouchIds(oldSync.getRunePouchIds());
            newSync.setRunePouchQtys(oldSync.getRunePouchQtys());
        }
    }

    private int gatherActivePrayers() {
        int packed = 0;
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                if (client.isPrayerActive(p)) {
                    packed |= (1 << p.ordinal());
                }
            } catch (Exception ignored) {
            }
        }
        return packed;
    }

    private int gatherAvailablePrayers() {
        int packed = 0;
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                // A prayer is "available" if the player has the required prayer level
                if (client.getRealSkillLevel(Skill.PRAYER) >= getPrayerLevelRequirement(p)) {
                    packed |= (1 << p.ordinal());
                }
            } catch (Exception ignored) {
            }
        }
        return packed;
    }

    private int gatherUnlockedPrayers() {
        // All non-quest-locked prayers are unlocked by default
        // Quest-locked prayers (Rigour, Augury, Preserve, etc.) check varbits
        int packed = 0;
        for (net.runelite.api.Prayer p : net.runelite.api.Prayer.values()) {
            try {
                // For simplicity, we treat all prayers the player has access to as unlocked
                // The client prayer interface itself shows the lock status
                packed |= (1 << p.ordinal());
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

    // --- AUTO-LEAVE ON IDLE ---
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            lastLogout = Instant.now();
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
            if (config.chatReadyToggle()) {
                String name = getLocalPlayerName();
                if (name == null) name = "Unknown";
                String statusText = state == 1 ? "is <col=00ff00>Ready</col>"
                        : "is <col=ff0000>Not Ready</col>";
                postPartyChat(name + " " + statusText);
            }
        }

        if (partyService.isInParty()) {
            sendPartySyncMessage();
        }
    }

    // --- Loot Rule ---
    public void setLootRule(LootRule rule) {
        localLootRule = rule;
        cachedLocalSync.setLootRule(rule);

        // Broadcast to party chat locally
        if (rule != LootRule.UNSPECIFIED) {
            if (config.chatLootToggle()) {
                String name = getLocalPlayerName();
                if (name == null) name = "Unknown";
                String color = rule == LootRule.FFA ? "af00af" : "00bfff";
                postPartyChat(name + " set loot to <col=" + color + ">" + rule + "</col>");
            }
        }

        if (partyService.isInParty()) {
            sendPartySyncMessage();
        }
    }
}
