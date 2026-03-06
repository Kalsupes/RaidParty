package com.example;

import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import com.example.partypanel.data.GameItem;
import com.example.partypanel.data.PartyPlayer;
import com.example.partypanel.data.Prayers;
import com.example.partypanel.ui.equipment.PlayerEquipmentPanel;
import com.example.partypanel.ui.PlayerInventoryPanel;
import com.example.partypanel.ui.prayer.PlayerPrayerPanel;
import com.example.partypanel.ui.skills.PlayerSkillsPanel;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class RaidPartyPlayerCard extends JPanel {
    private static final Dimension IMAGE_SIZE = new Dimension(24, 24);
    private static final Color GOLD = new Color(225, 175, 50);
    private static final Color HP_GREEN = new Color(60, 200, 80);
    private static final Color HP_RED = new Color(220, 50, 50);
    private static final Color PRAYER_AQUA = new Color(50, 200, 220);
    private static final Color SPEC_YELLOW = new Color(220, 200, 50);
    private static final Color RUN_ORANGE = new Color(220, 160, 50);
    private static final Color CARD_BG = new Color(30, 32, 38, 220);
    private static final Color CHEVRON_COLOR = new Color(180, 180, 180);

    private static final BufferedImage SPELLBOOK_STANDARD = ImageUtil.loadImageResource(RaidPartyPlugin.class, "/com/example/spellbook_standard.png");
    private static final BufferedImage SPELLBOOK_ANCIENT = ImageUtil.loadImageResource(RaidPartyPlugin.class, "/com/example/spellbook_ancient.png");
    private static final BufferedImage SPELLBOOK_LUNAR = ImageUtil.loadImageResource(RaidPartyPlugin.class, "/com/example/spellbook_lunar.png");
    private static final BufferedImage SPELLBOOK_ARCEUUS = ImageUtil.loadImageResource(RaidPartyPlugin.class, "/com/example/spellbook_arceuus.png");

    private final RaidPartyPlugin plugin;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final String memberName;
    private final boolean isHost;

    private boolean expanded = false;
    private RaidPartyPlayerSync syncData;
    private PartyPlayer adapterPlayer;

    private JPanel bannerPanel;
    private JLabel chevronLabel;
    
    // Tab System
    private MaterialTabGroup tabGroup;
    private JPanel displayPanel;
    private final Map<Integer, Boolean> tabMap = new HashMap<>();

    // Container Panels
    private PlayerInventoryPanel inventoryPanel;
    private PlayerEquipmentPanel equipmentPanel;
    private PlayerPrayerPanel prayerPanel;
    private PlayerSkillsPanel skillsPanel;

    public RaidPartyPlayerCard(RaidPartyPlugin plugin, String memberName, boolean isHost, RaidPartyPlayerSync syncData) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
        this.spriteManager = plugin.getSpriteManager();
        this.memberName = memberName;
        this.isHost = isHost;
        this.syncData = syncData;

        setLayout(new DynamicGridLayout(0, 1));
        setOpaque(false);

        buildCard();
    }

    public void updateSyncData(RaidPartyPlayerSync newSync) {
        this.syncData = newSync;
        if (!expanded) {
            buildCard(); // Only rebuild banner if collapsed
            return;
        }

        // Full UI injection on ClientThread for ItemManager
        plugin.getClientThread().invokeLater(() -> {
            this.adapterPlayer = createAdapterPlayer(newSync);
            SwingUtilities.invokeLater(() -> {
                if (inventoryPanel != null) inventoryPanel.updateInventory(adapterPlayer.getInventory(), adapterPlayer.getRunesInPouch());
                if (equipmentPanel != null) equipmentPanel.updateEquipment(adapterPlayer.getEquipment());
                if (skillsPanel != null) skillsPanel.updateStats(adapterPlayer);
                if (prayerPanel != null) {
                    prayerPanel.updatePrayers(adapterPlayer.getPrayers());
                    prayerPanel.updatePrayerRemaining(adapterPlayer.getSkillBoostedLevel(net.runelite.api.Skill.PRAYER), adapterPlayer.getSkillRealLevel(net.runelite.api.Skill.PRAYER, false));
                }
                
                // Rebuild banner visually
                remove(0); 
                bannerPanel = createBanner();
                add(bannerPanel, 0);

                revalidate();
                repaint();
            });
        });
    }

    private PartyPlayer createAdapterPlayer(RaidPartyPlayerSync sync) {
        PartyPlayer p = new PartyPlayer(null);
        if (sync == null) return p;

        p.setUsername(memberName);
        p.setStamina(sync.getRun());
        p.setWorld(sync.getWorld());

        // Inventory
        GameItem[] inv = new GameItem[28];
        if (sync.getInvIds() != null) {
            for (int i = 0; i < sync.getInvIds().length && i < 28; i++) {
                if (sync.getInvIds()[i] > 0) inv[i] = new GameItem(sync.getInvIds()[i], sync.getInvQtys()[i], itemManager);
            }
        }
        p.setInventory(inv);

        // Equipment
        GameItem[] eqp = new GameItem[14];
        if (sync.getEqpIds() != null) {
            for (int i = 0; i < sync.getEqpIds().length && i < 14; i++) {
                if (sync.getEqpIds()[i] > 0) eqp[i] = new GameItem(sync.getEqpIds()[i], sync.getEqpQtys()[i], itemManager);
            }
        }
        p.setEquipment(eqp);
        
        // Stats
        com.example.partypanel.data.Stats s = new com.example.partypanel.data.Stats();
        if (sync.getSkillLevels() != null && sync.getSkillLevels().length >= 46) {
            for (net.runelite.api.Skill skill : net.runelite.api.Skill.values()) {
                int idx = skill.ordinal() * 2;
                if (idx + 1 < sync.getSkillLevels().length) {
                    s.getBaseLevels().put(skill, sync.getSkillLevels()[idx + 1]);
                    s.getBoostedLevels().put(skill, sync.getSkillLevels()[idx]);
                }
            }
        }
        s.setRunEnergy(sync.getRun());
        s.setCombatLevel(sync.getCombatLevel());
        p.setStats(s);

        // Prayers
        com.example.partypanel.data.Prayers prayers = new com.example.partypanel.data.Prayers();
        for (com.example.partypanel.data.PrayerData pd : prayers.getPrayerData().values()) {
            pd.setAvailable(true); // Don't artificially darken prayers
        }
        
        if (sync.getActivePrayerIds() != null) {
            for (int pId : sync.getActivePrayerIds()) {
                if (pId >= 0 && pId < net.runelite.api.Prayer.values().length) {
                    net.runelite.api.Prayer pr = net.runelite.api.Prayer.values()[pId];
                    com.example.partypanel.data.PrayerData pd = prayers.getPrayerData().get(pr);
                    if (pd != null) pd.setEnabled(true);
                }
            }
        }
        p.setPrayers(prayers);

        return p;
    }

    private void buildCard() {
        removeAll();
        
        bannerPanel = createBanner();
        add(bannerPanel);

        if (expanded) {
            setBorder(new CompoundBorder(
                new MatteBorder(2, 2, 2, 2, new Color(87, 80, 64)),
                new EmptyBorder(0, 0, 5, 0)
            ));

            plugin.getClientThread().invokeLater(() -> {
                this.adapterPlayer = createAdapterPlayer(syncData);

                SwingUtilities.invokeLater(() -> {
                    displayPanel = new JPanel();
                    displayPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
                    displayPanel.setOpaque(false);
                    
                    tabGroup = new MaterialTabGroup(displayPanel);
                    tabGroup.setBorder(new EmptyBorder(10, 0, 4, 0));

                    inventoryPanel = new PlayerInventoryPanel(adapterPlayer.getInventory(), adapterPlayer.getRunesInPouch(), itemManager);
                    equipmentPanel = new PlayerEquipmentPanel(adapterPlayer.getEquipment(), adapterPlayer.getQuiver(), spriteManager, itemManager);
                    skillsPanel = new PlayerSkillsPanel(adapterPlayer, true, spriteManager);
                    prayerPanel = new PlayerPrayerPanel(adapterPlayer, spriteManager);

                    tabMap.clear();
                    addTab(tabGroup, SpriteID.TAB_INVENTORY, inventoryPanel, "Inventory");
                    addTab(tabGroup, SpriteID.TAB_EQUIPMENT, equipmentPanel, "Equipment");
                    addTab(tabGroup, SpriteID.TAB_PRAYER, prayerPanel, "Prayers");
                    addTab(tabGroup, SpriteID.TAB_STATS, skillsPanel, "Skills");

                    add(tabGroup);
                    add(displayPanel);
                    
                    revalidate();
                    repaint();
                });
            });
        } else {
            setBorder(new MatteBorder(2, 2, 2, 2, new Color(87, 80, 64)));
        }

        revalidate();
        repaint();
    }

    private void addTab(final MaterialTabGroup tabGroup, final int spriteID, final JPanel panel, final String tooltip) {
        spriteManager.getSpriteAsync(spriteID, 0, img -> SwingUtilities.invokeLater(() -> {
            ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(img, IMAGE_SIZE.width, IMAGE_SIZE.height));
            final MaterialTab tab = new MaterialTab(icon, tabGroup, panel);
            tab.setToolTipText(tooltip);
            tabGroup.addTab(tab);
            tabGroup.revalidate();
            tabGroup.repaint();

            tabMap.put(spriteID, false);
            tab.setOnSelectEvent(() -> {
                tabMap.replaceAll((k, v) -> v = false);
                tabMap.put(spriteID, true);
                return true;
            });

            if (spriteID == SpriteID.TAB_INVENTORY) {
                tabGroup.select(tab);
                tabMap.put(spriteID, true);
            }
        }));
    }

    // ======================== BANNER ========================
    private JPanel createBanner() {
        JPanel banner = new JPanel(new BorderLayout(6, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isHost) g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40));
                else g2.setColor(new Color(255, 255, 255, 10));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
            }
        };
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(6, 8, 6, 8));
        banner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel leftInfo = new JPanel();
        leftInfo.setLayout(new BoxLayout(leftInfo, BoxLayout.Y_AXIS));
        leftInfo.setOpaque(false);

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        JLabel nameLbl = new JLabel(memberName);
        nameLbl.setFont(FontManager.getRunescapeBoldFont());
        nameLbl.setForeground(isHost ? GOLD : Color.WHITE);
        nameRow.add(nameLbl);
        if (isHost) {
            JLabel crown = new JLabel(" [H]");
            crown.setFont(FontManager.getRunescapeSmallFont());
            crown.setForeground(GOLD);
            nameRow.add(crown);
        }
        
        // Add discord avatar if possible
        BufferedImage avatar = getDiscordAvatarFromParty();
        if (avatar != null) {
            ImageIcon avatarIcon = new ImageIcon(ImageUtil.resizeImage(avatar, 16, 16));
            JLabel avatarLabel = new JLabel(avatarIcon);
            avatarLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
            nameRow.add(avatarLabel, 0); // Add before name
        }

        // Badges container (stacked vertically)
        JPanel badgesPanel = new JPanel();
        badgesPanel.setLayout(new BoxLayout(badgesPanel, BoxLayout.Y_AXIS));
        badgesPanel.setOpaque(false);
        badgesPanel.setBorder(new EmptyBorder(0, 4, 0, 0));

        boolean hasBadges = false;

        // Add Loot Rule Badge
        if (syncData != null && syncData.getLootRule() != null && syncData.getLootRule() != LootRule.UNSPECIFIED) {
            final boolean isFfa = syncData.getLootRule() == LootRule.FFA;
            final Color badgeBg = isFfa ? new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 180) 
                                        : new Color(50, 150, 220, 180); // Blue for Split
            
            JLabel lootBadge = new JLabel(isFfa ? "FFA" : "SPLIT", SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(badgeBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(255, 255, 255, 50)); // subtle highlight
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            lootBadge.setFont(FontManager.getRunescapeSmallFont());
            lootBadge.setForeground(Color.WHITE);
            lootBadge.setBorder(new EmptyBorder(1, 4, 1, 4));
            lootBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            badgesPanel.add(lootBadge);
            hasBadges = true;
        }

        // Add Ready Check Badge
        if (syncData != null && syncData.getReadyState() > 0) {
            final boolean isReady = syncData.getReadyState() == 1;
            final Color readyBg = isReady ? new Color(40, 160, 60, 200)   // Green
                                          : new Color(190, 40, 40, 200);  // Red
            final String readyText = isReady ? "READY" : "NOT READY";

            JLabel readyBadge = new JLabel(readyText, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(readyBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(255, 255, 255, 50));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            readyBadge.setFont(FontManager.getRunescapeSmallFont());
            readyBadge.setForeground(Color.WHITE);
            readyBadge.setBorder(new EmptyBorder(1, 4, 1, 4));
            readyBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

            if (hasBadges) badgesPanel.add(Box.createRigidArea(new Dimension(0, 2))); // gap between badges
            badgesPanel.add(readyBadge);
            hasBadges = true;
        }

        if (hasBadges) {
            nameRow.add(badgesPanel);
        }

        
        leftInfo.add(nameRow);

        if (syncData != null) {
            String sub = "";
            if (syncData.getCombatLevel() > 0) sub += "Lvl " + syncData.getCombatLevel();
            if (syncData.getWorld() > 0) {
                if (!sub.isEmpty()) sub += "  \u2022  ";
                sub += "W" + syncData.getWorld();
            }
            if (!sub.isEmpty()) {
                JPanel subRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                subRow.setOpaque(false);
                subRow.setBorder(new EmptyBorder(2, 0, 0, 0));
                JLabel subLbl = new JLabel(sub);
                subLbl.setFont(FontManager.getRunescapeSmallFont());
                subLbl.setForeground(Color.GRAY);
                subRow.add(subLbl);
                leftInfo.add(subRow);
            }
        }

        JPanel topHeader = new JPanel(new BorderLayout());
        topHeader.setOpaque(false);
        topHeader.add(leftInfo, BorderLayout.CENTER);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightControls.setOpaque(false);
        rightControls.setBorder(new EmptyBorder(2, 0, 0, 0)); // Align with name

        if (syncData != null) {
            int sb = syncData.getSpellbook();
            BufferedImage iconBuf = null;
            String tooltip = "";
            if (sb == 0) { iconBuf = SPELLBOOK_STANDARD; tooltip = "Standard Spellbook"; }
            else if (sb == 1) { iconBuf = SPELLBOOK_ANCIENT; tooltip = "Ancient Magicks"; }
            else if (sb == 2) { iconBuf = SPELLBOOK_LUNAR; tooltip = "Lunar Spellbook"; }
            else if (sb == 3) { iconBuf = SPELLBOOK_ARCEUUS; tooltip = "Arceuus Spellbook"; }
            
            if (iconBuf != null) {
                JLabel spellLbl = new JLabel(new ImageIcon(iconBuf));
                spellLbl.setToolTipText(tooltip);
                rightControls.add(spellLbl);
            }
        }

        chevronLabel = new JLabel(expanded ? "\u25B2" : "\u25BC");
        chevronLabel.setFont(FontManager.getRunescapeSmallFont());
        chevronLabel.setForeground(CHEVRON_COLOR);
        chevronLabel.setVerticalAlignment(SwingConstants.TOP);
        rightControls.add(chevronLabel);

        topHeader.add(rightControls, BorderLayout.EAST);
        
        banner.add(topHeader, BorderLayout.NORTH);

        if (syncData != null && syncData.getMaxHp() > 0) {
            JPanel statsRow = new JPanel(new GridLayout(1, 4, 4, 0));
            statsRow.setOpaque(false);
            statsRow.setBorder(new EmptyBorder(4, 0, 0, 0));

            int hp = syncData.getHp();
            int maxHp = syncData.getMaxHp();
            Color hpCol = hp < maxHp / 3 ? Color.RED : HP_RED; // Changed to red as requested by user

            statsRow.add(createStatCell("\u2764", hp, hpCol));
            statsRow.add(createStatCell("\u2728", syncData.getPrayer(), PRAYER_AQUA));
            statsRow.add(createStatCell("\u2694", syncData.getSpec() / 10, SPEC_YELLOW));
            statsRow.add(createStatCell("\uD83C\uDFC3", syncData.getRun() / 100, RUN_ORANGE));

            JPanel bannerWrap = new JPanel(new BorderLayout());
            bannerWrap.setOpaque(false);
            bannerWrap.add(banner, BorderLayout.NORTH);
            bannerWrap.add(statsRow, BorderLayout.CENTER);

            MouseAdapter toggleAdapter = createToggleAdapter();
            bannerWrap.addMouseListener(toggleAdapter);
            banner.addMouseListener(toggleAdapter);
            statsRow.addMouseListener(toggleAdapter);

            return bannerWrap;
        }

        banner.addMouseListener(createToggleAdapter());
        return banner;
    }

    private MouseAdapter createToggleAdapter() {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) return;
                expanded = !expanded;
                buildCard();
            }
        };
    }

    private JPanel createStatCell(String icon, int value, Color color) {
        JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        cell.setOpaque(false);
        JLabel iconLbl = new JLabel(icon);
        iconLbl.setFont(FontManager.getRunescapeSmallFont());
        iconLbl.setForeground(color);
        JLabel valLbl = new JLabel(String.valueOf(value));
        valLbl.setFont(FontManager.getRunescapeBoldFont());
        valLbl.setForeground(color);
        cell.add(iconLbl);
        cell.add(valLbl);
        return cell;
    }

    private BufferedImage getDiscordAvatarFromParty() {
        if (plugin.getPartyService() == null || !plugin.getPartyService().isInParty()) return null;
        for (net.runelite.client.party.PartyMember member : plugin.getPartyService().getMembers()) {
            if (member.getDisplayName().equals(memberName)) {
                return member.getAvatar(); 
            }
        }
        return null;
    }
}
