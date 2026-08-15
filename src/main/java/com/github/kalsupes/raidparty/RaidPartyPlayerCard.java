package com.github.kalsupes.raidparty;

import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

import com.github.kalsupes.raidparty.partypanel.data.GameItem;
import com.github.kalsupes.raidparty.partypanel.data.PartyPlayer;
import com.github.kalsupes.raidparty.partypanel.ui.equipment.PlayerEquipmentPanel;
import com.github.kalsupes.raidparty.partypanel.ui.PlayerInventoryPanel;
import com.github.kalsupes.raidparty.partypanel.ui.prayer.PlayerPrayerPanel;
import com.github.kalsupes.raidparty.partypanel.ui.skills.PlayerSkillsPanel;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RaidPartyPlayerCard extends JPanel {
    private static final Dimension IMAGE_SIZE = new Dimension(24, 24);
    private static final Color FFA_PURPLE = new Color(175, 0, 175);
    private static final Color GOLD = new Color(225, 175, 50);
    private static final Color HP_GREEN = new Color(60, 200, 80);
    private static final Color HP_RED = new Color(220, 50, 50);
    private static final Color PRAYER_AQUA = new Color(50, 200, 220);
    private static final Color SPEC_YELLOW = new Color(220, 200, 50);
    private static final Color RUN_ORANGE = new Color(220, 160, 50);
    private static final Color STAMINA_ORANGE = new Color(255, 140, 0);
    private static final Color CARD_BG = new Color(30, 32, 38, 220);
    private static final Color CHEVRON_COLOR = new Color(180, 180, 180);

    private static final BufferedImage SPELLBOOK_STANDARD = ImageUtil.loadImageResource(RaidPartyPlugin.class,
            "/com/github/kalsupes/raidparty/spellbook_standard.png");
    private static final BufferedImage SPELLBOOK_ANCIENT = ImageUtil.loadImageResource(RaidPartyPlugin.class,
            "/com/github/kalsupes/raidparty/spellbook_ancient.png");
    private static final BufferedImage SPELLBOOK_LUNAR = ImageUtil.loadImageResource(RaidPartyPlugin.class,
            "/com/github/kalsupes/raidparty/spellbook_lunar.png");
    private static final BufferedImage SPELLBOOK_ARCEUUS = ImageUtil.loadImageResource(RaidPartyPlugin.class,
            "/com/github/kalsupes/raidparty/spellbook_arceuus.png");

    private final RaidPartyPlugin plugin;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private final String memberName;
    private final boolean isHost;

    private static ImageIcon cachedHpIcon = null;
    private static ImageIcon cachedPrayIcon = null;
    private static ImageIcon cachedSpecIcon = null;
    private static ImageIcon cachedRunIcon = null;
    private static ImageIcon cachedStaminaIcon = null;

    private void loadStatIcons() {
        if (cachedHpIcon == null) {
            BufferedImage hpImg = plugin.getSkillIconManager().getSkillImage(net.runelite.api.Skill.HITPOINTS);
            if (hpImg != null) cachedHpIcon = new ImageIcon(ImageUtil.resizeImage(hpImg, 15, 15));
        }
        if (cachedPrayIcon == null) {
            BufferedImage prayImg = plugin.getSkillIconManager().getSkillImage(net.runelite.api.Skill.PRAYER);
            if (prayImg != null) cachedPrayIcon = new ImageIcon(ImageUtil.resizeImage(prayImg, 15, 15));
        }
        if (cachedSpecIcon == null) {
            plugin.getSpriteManager().getSpriteAsync(net.runelite.api.SpriteID.MINIMAP_ORB_SPECIAL_ICON, 0, img -> {
                if (img != null) cachedSpecIcon = new ImageIcon(ImageUtil.resizeImage(img, 15, 15));
            });
        }
        if (cachedRunIcon == null) {
            plugin.getSpriteManager().getSpriteAsync(net.runelite.api.SpriteID.MINIMAP_ORB_RUN_ICON, 0, img -> {
                if (img != null) cachedRunIcon = new ImageIcon(ImageUtil.resizeImage(img, 15, 15));
            });
        }
        if (cachedStaminaIcon == null) {
            plugin.getSpriteManager().getSpriteAsync(net.runelite.api.SpriteID.MINIMAP_ORB_RUN_ICON_SLOWED_DEPLETION, 0, img -> {
                if (img != null) cachedStaminaIcon = new ImageIcon(ImageUtil.resizeImage(img, 15, 15));
            });
        }
    }

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

    public RaidPartyPlayerCard(RaidPartyPlugin plugin, String memberName, boolean isHost,
            RaidPartyPlayerSync syncData) {
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

    public RaidPartyPlayerSync getSyncData() {
        return syncData;
    }

    public void updateSyncData(RaidPartyPlayerSync newSync) {
        this.syncData = newSync;

        // Fetch data on ClientThread if expanded, otherwise just update banner on Swing thread
        plugin.getClientThread().invokeLater(() -> {
            if (expanded) {
                this.adapterPlayer = createAdapterPlayer(newSync);
            }
            
            // Fetch client-dependent variables on the client thread before passing to Swing thread
            final boolean inRaid = newSync != null && newSync.isInRaid();
            final boolean onSameWorld = newSync != null && newSync.getWorld() != 0 && newSync.getWorld() == plugin.getClient().getWorld();
            
            SwingUtilities.invokeLater(() -> {
                if (expanded) {
                    if (inventoryPanel != null)
                        inventoryPanel.updateInventory(adapterPlayer.getInventory(), adapterPlayer.getRunesInPouch());
                    if (equipmentPanel != null)
                        equipmentPanel.updateEquipment(adapterPlayer.getEquipment());
                    if (skillsPanel != null)
                        skillsPanel.updateStats(adapterPlayer);
                    if (prayerPanel != null) {
                        prayerPanel.updatePrayers(adapterPlayer.getPrayers());
                        prayerPanel.updatePrayerRemaining(adapterPlayer.getSkillBoostedLevel(net.runelite.api.Skill.PRAYER),
                                adapterPlayer.getSkillRealLevel(net.runelite.api.Skill.PRAYER, false));
                    }
                }

                // Rebuild banner visually
                if (bannerPanel != null) {
                    remove(bannerPanel);
                }
                bannerPanel = createBanner();
                add(bannerPanel, 0);
                
                // Seamlessly update border color without flickering to default
                Color borderColor = new Color(87, 80, 64);
                if (inRaid) {
                    borderColor = new Color(40, 160, 60); // Green
                } else if (plugin.getConfig().indicateFarAwayMembers() && newSync != null && !onSameWorld) {
                    borderColor = new Color(110, 60, 130); // Darker, less vibrant purple for separate worlds
                }
                setBorder(new CompoundBorder(
                        new MatteBorder(2, 2, 2, 2, borderColor),
                        new EmptyBorder(0, 0, 5, 0)));

                revalidate();
                repaint();
            });
        });
    }

    private PartyPlayer createAdapterPlayer(RaidPartyPlayerSync sync) {
        PartyPlayer p = new PartyPlayer(null);
        if (sync == null)
            return p;

        p.setUsername(memberName);
        p.setStamina(sync.getRun());
        p.setWorld(sync.getWorld());

        // Inventory
        GameItem[] inv = new GameItem[28];
        if (sync.getInvIds() != null) {
            for (int i = 0; i < sync.getInvIds().length && i < 28; i++) {
                if (sync.getInvIds()[i] > 0)
                    inv[i] = new GameItem(sync.getInvIds()[i], sync.getInvQtys()[i], itemManager);
            }
        }
        p.setInventory(inv);

        // Rune Pouch
        GameItem[] pouch = new GameItem[4];
        if (sync.getRunePouchIds() != null) {
            for (int i = 0; i < sync.getRunePouchIds().length && i < 4; i++) {
                if (sync.getRunePouchIds()[i] > 0)
                    pouch[i] = new GameItem(sync.getRunePouchIds()[i], sync.getRunePouchQtys()[i], itemManager);
            }
        }
        p.setRunesInPouch(pouch);

        // Equipment
        GameItem[] eqp = new GameItem[14];
        if (sync.getEqpIds() != null) {
            for (int i = 0; i < sync.getEqpIds().length && i < 14; i++) {
                if (sync.getEqpIds()[i] > 0)
                    eqp[i] = new GameItem(sync.getEqpIds()[i], sync.getEqpQtys()[i], itemManager);
            }
        }
        p.setEquipment(eqp);

        // Stats
        com.github.kalsupes.raidparty.partypanel.data.Stats s = new com.github.kalsupes.raidparty.partypanel.data.Stats();
        if (sync.getSkillLevels() != null && sync.getSkillLevels().length >= 46) {
            for (net.runelite.api.Skill skill : net.runelite.api.Skill.values()) {
                int idx = skill.ordinal() * 2;
                if (idx + 1 < sync.getSkillLevels().length) {
                    s.getBaseLevels().put(skill, sync.getSkillLevels()[idx + 1]);
                    s.getBoostedLevels().put(skill, sync.getSkillLevels()[idx]);
                }
                if (sync.getSkillXps() != null && skill.ordinal() < sync.getSkillXps().length) {
                    s.getExperiences().put(skill, sync.getSkillXps()[skill.ordinal()]);
                }
            }
        }
        s.setRunEnergy(sync.getRun());
        s.setCombatLevel(sync.getCombatLevel());
        p.setStats(s);

        // Prayers
        com.github.kalsupes.raidparty.partypanel.data.Prayers prayers = new com.github.kalsupes.raidparty.partypanel.data.Prayers();
        for (net.runelite.api.Prayer pr : net.runelite.api.Prayer.values()) {
            com.github.kalsupes.raidparty.partypanel.data.PrayerData pd = prayers.getPrayerData().get(pr);
            if (pd != null) {
                long ordIdx = 1L << pr.ordinal();
                pd.setAvailable((sync.getAvailablePrayers() & ordIdx) != 0);
                pd.setEnabled((sync.getActivePrayers() & ordIdx) != 0);
                pd.setUnlocked((sync.getUnlockedPrayers() & ordIdx) != 0);
            }
        }
        p.setPrayers(prayers);

        return p;
    }

    private void buildCard() {
        removeAll();

        bannerPanel = createBanner();
        add(bannerPanel);

        // The border will be updated dynamically below
        setBorder(new CompoundBorder(
                new MatteBorder(2, 2, 2, 2, new Color(45, 45, 45)),
                new EmptyBorder(0, 0, 5, 0)));

        if (expanded) {


            displayPanel = new JPanel();
            displayPanel.setBorder(new EmptyBorder(5, 5, 0, 5));
            displayPanel.setOpaque(false);

            tabGroup = new MaterialTabGroup(displayPanel);
            tabGroup.setBorder(new EmptyBorder(10, 0, 4, 0));

            inventoryPanel = new PlayerInventoryPanel(adapterPlayer.getInventory(),
                    adapterPlayer.getRunesInPouch(), itemManager);
            equipmentPanel = new PlayerEquipmentPanel(adapterPlayer.getEquipment(), adapterPlayer.getQuiver(),
                    spriteManager, itemManager);
            skillsPanel = new PlayerSkillsPanel(adapterPlayer, true, spriteManager);
            prayerPanel = new PlayerPrayerPanel(adapterPlayer, spriteManager);

            tabMap.clear();
            addTab(tabGroup, SpriteID.TAB_INVENTORY, inventoryPanel, "Inventory");
            addTab(tabGroup, SpriteID.TAB_EQUIPMENT, equipmentPanel, "Equipment");
            addTab(tabGroup, SpriteID.TAB_PRAYER, prayerPanel, "Prayers");
            addTab(tabGroup, SpriteID.TAB_STATS, skillsPanel, "Skills");

            add(tabGroup);
            add(displayPanel);

        }

        // Fetch dynamic border color asynchronously without blocking UI build
        plugin.getClientThread().invokeLater(() -> {
            boolean inRaid = syncData != null && syncData.isInRaid();
            boolean onSameWorld = syncData != null && syncData.getWorld() != 0 && syncData.getWorld() == plugin.getClient().getWorld();
            
            SwingUtilities.invokeLater(() -> {
                Color borderColor = new Color(87, 80, 64);
                if (inRaid) {
                    borderColor = new Color(40, 160, 60); // Green
                } else if (plugin.getConfig().indicateFarAwayMembers() && syncData != null && !onSameWorld) {
                    borderColor = new Color(110, 60, 130); // Darker, less vibrant purple for separate worlds
                }
                setBorder(new CompoundBorder(
                        new MatteBorder(2, 2, 2, 2, borderColor),
                        new EmptyBorder(0, 0, 5, 0)));
                repaint();
            });
        });

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
            
            RaidPartyPlayerCard.this.revalidate();
            RaidPartyPlayerCard.this.repaint();
            java.awt.Container parent = RaidPartyPlayerCard.this.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }));
    }

    // ======================== BANNER ========================
    private JPanel createBanner() {
        JPanel banner = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                if (isHost)
                    g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40));
                else
                    g2.setColor(new Color(255, 255, 255, 10));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(6, 8, 6, 8));
        banner.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel leftInfo = new JPanel(new GridBagLayout());
        leftInfo.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        JLabel nameLbl = new JLabel(memberName) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (d.width > 110) d.width = 110;
                return d;
            }
        };
        nameLbl.setFont(FontManager.getRunescapeBoldFont());
        nameLbl.setForeground(Color.WHITE);
        nameRow.add(nameLbl);

        // Add discord avatar if possible
        BufferedImage avatar = getDiscordAvatarFromParty();
        if (avatar != null) {
            ImageIcon avatarIcon = new ImageIcon(ImageUtil.resizeImage(avatar, 16, 16));
            JLabel avatarLabel = new JLabel(avatarIcon);
            avatarLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
            nameRow.add(avatarLabel, 0); // Add before name
        }

        // Add ironman helm if applicable
        if (syncData != null && syncData.getAccountType() > 0) {
            BufferedImage helmIcon = getAccountTypeIcon(syncData.getAccountType());
            if (helmIcon != null) {
                JLabel helmLabel = new JLabel(new ImageIcon(helmIcon));
                helmLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
                // Add right before the name (after avatar and crown if they exist)
                nameRow.add(helmLabel, nameRow.getComponentCount() - 1); 
            }
        }

        // Badges container (stacked vertically)
        JPanel badgesPanel = new JPanel();
        badgesPanel.setLayout(new BoxLayout(badgesPanel, BoxLayout.Y_AXIS));
        badgesPanel.setOpaque(false);
        badgesPanel.setBorder(new EmptyBorder(0, 4, 0, 0));

        boolean hasBadges = false;

        // Add Ready Check Badge
        if (syncData != null && syncData.getReadyState() > 0) {
            final boolean isReady = syncData.getReadyState() == 1;
            final Color readyBg = isReady ? new Color(40, 160, 60, 200) // Green
                    : new Color(190, 40, 40, 200); // Red
            final String readyText = isReady ? "R" : "NR";

            JLabel readyBadge = new JLabel(readyText, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(readyBg);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(new Color(255, 255, 255, 50));
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            readyBadge.setFont(FontManager.getRunescapeSmallFont());
            readyBadge.setForeground(Color.WHITE);
            readyBadge.setBorder(new EmptyBorder(1, 4, 1, 4));
            readyBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

            badgesPanel.add(readyBadge);
            hasBadges = true;
        }

        // Add Loot Rule Badge
        if (syncData != null && syncData.getLootRule() != null && syncData.getLootRule() != LootRule.UNSPECIFIED) {
            final boolean isFfa = syncData.getLootRule() == LootRule.FFA;
            final Color badgeBg = isFfa ? new Color(FFA_PURPLE.getRed(), FFA_PURPLE.getGreen(), FFA_PURPLE.getBlue(), 180)
                    : new Color(0, 191, 255, 180); // Cyan-blue for Split

            JLabel lootBadge = new JLabel(isFfa ? "FFA" : "SPLIT", SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(badgeBg);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(new Color(255, 255, 255, 50)); // subtle highlight
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            lootBadge.setFont(FontManager.getRunescapeSmallFont());
            lootBadge.setForeground(Color.WHITE);
            lootBadge.setBorder(new EmptyBorder(1, 4, 1, 4));
            lootBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

            if (hasBadges)
                badgesPanel.add(Box.createRigidArea(new Dimension(0, 2))); // gap between badges
            badgesPanel.add(lootBadge);
            hasBadges = true;
        }

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        leftInfo.add(nameRow, gbc);

        if (syncData != null) {
            String sub = "";
            if (syncData.getCombatLevel() > 0)
                sub += "Lvl " + syncData.getCombatLevel();
            if (syncData.getWorld() > 0) {
                if (!sub.isEmpty())
                    sub += "  \u2022  ";
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
                gbc.gridy = 1;
                leftInfo.add(subRow, gbc);
            }
        }

        if (hasBadges) {
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.gridheight = 2;
            gbc.anchor = GridBagConstraints.WEST; // Center vertically next to the two rows
            gbc.insets = new Insets(0, 4, 0, 0);
            leftInfo.add(badgesPanel, gbc);
        }

        // Add a filler to absorb remaining horizontal space so it aligns left
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        leftInfo.add(Box.createHorizontalGlue(), gbc);

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
            if (sb == 0) {
                iconBuf = SPELLBOOK_STANDARD;
                tooltip = "Standard Spellbook";
            } else if (sb == 1) {
                iconBuf = SPELLBOOK_ANCIENT;
                tooltip = "Ancient Magicks";
            } else if (sb == 2) {
                iconBuf = SPELLBOOK_LUNAR;
                tooltip = "Lunar Spellbook";
            } else if (sb == 3) {
                iconBuf = SPELLBOOK_ARCEUUS;
                tooltip = "Arceuus Spellbook";
            }

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

            loadStatIcons();

            int hp = syncData.getHp();
            int maxHp = syncData.getMaxHp();
            Color hpCol = hp < maxHp / 3 ? Color.RED : HP_RED;

            if (cachedHpIcon != null) {
                statsRow.add(createStatCell(cachedHpIcon, hp, hpCol));
            } else {
                statsRow.add(createStatCell("\u2764", hp, hpCol));
            }

            if (cachedPrayIcon != null) {
                statsRow.add(createStatCell(cachedPrayIcon, syncData.getPrayer(), PRAYER_AQUA));
            } else {
                statsRow.add(createStatCell("\u2728", syncData.getPrayer(), PRAYER_AQUA));
            }

            JPanel specCell;
            if (cachedSpecIcon != null) {
                specCell = createStatCell(cachedSpecIcon, syncData.getSpec(), SPEC_YELLOW);
            } else {
                specCell = createStatCell("\u2694", syncData.getSpec(), SPEC_YELLOW);
            }
            statsRow.add(specCell);

            boolean hasStamina = syncData.getStamina() > 0;
            JPanel runCell;
            if (hasStamina && cachedStaminaIcon != null) {
                runCell = createStatCell(cachedStaminaIcon, syncData.getRun(), STAMINA_ORANGE);
            } else if (!hasStamina && cachedRunIcon != null) {
                runCell = createStatCell(cachedRunIcon, syncData.getRun(), RUN_ORANGE);
            } else {
                runCell = createStatCell("\uD83C\uDFC3", syncData.getRun(), hasStamina ? STAMINA_ORANGE : RUN_ORANGE);
            }
            statsRow.add(runCell);

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
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                    return;
                }
                expanded = !expanded;
                
                if (expanded) {
                    plugin.getClientThread().invokeLater(() -> {
                        adapterPlayer = createAdapterPlayer(syncData);
                        SwingUtilities.invokeLater(() -> buildCard());
                    });
                } else {
                    buildCard();
                }
            }

            private void showContextMenu(MouseEvent e) {
                JPopupMenu popup = new JPopupMenu();
                String mutedStr = plugin.getConfig().mutedPingUsers();
                if (mutedStr == null) mutedStr = "";
                List<String> mutedList = new ArrayList<>(Arrays.asList(mutedStr.split(",")));
                boolean isMuted = mutedList.contains(memberName);

                JMenuItem muteItem = new JMenuItem(isMuted ? "Unmute Pings" : "Mute Pings");
                muteItem.addActionListener(evt -> {
                    if (isMuted) {
                        mutedList.remove(memberName);
                    } else {
                        mutedList.add(memberName);
                    }
                    // Filter empty strings and join
                    mutedList.removeIf(String::isEmpty);
                    String newMuted = String.join(",", mutedList);
                    plugin.getConfigManager().setConfiguration("raidparty", "mutedPingUsers", newMuted);
                });

                popup.add(muteItem);

                popup.show(e.getComponent(), e.getX(), e.getY());
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

    private JPanel createStatCell(javax.swing.Icon icon, int value, Color color) {
        JPanel cell = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        cell.setOpaque(false);
        JLabel iconLbl = new JLabel(icon);
        JLabel valLbl = new JLabel(String.valueOf(value));
        valLbl.setFont(FontManager.getRunescapeBoldFont());
        valLbl.setForeground(color);
        cell.add(iconLbl);
        cell.add(valLbl);
        return cell;
    }

    private BufferedImage getDiscordAvatarFromParty() {
        if (plugin.getPartyService() == null || !plugin.getPartyService().isInParty() || memberName == null)
            return null;
            
        String cleanMemberName = net.runelite.client.util.Text.standardize(memberName);
        
        // Check local member first
        net.runelite.client.party.PartyMember localMember = plugin.getPartyService().getLocalMember();
        if (localMember != null && localMember.getDisplayName() != null && net.runelite.client.util.Text.standardize(localMember.getDisplayName()).equals(cleanMemberName)) {
            return localMember.getAvatar();
        }
        
        // Check remote members
        for (net.runelite.client.party.PartyMember member : plugin.getPartyService().getMembers()) {
            if (member.getDisplayName() != null && net.runelite.client.util.Text.standardize(member.getDisplayName()).equals(cleanMemberName)) {
                return member.getAvatar();
            }
        }
        return null;
    }

    private BufferedImage getAccountTypeIcon(int accountType) {
        String fileName = null;
        switch (accountType) {
            case 1: fileName = "ironman.png"; break;
            case 2: fileName = "ultimate_ironman.png"; break;
            case 3: fileName = "hardcore_ironman.png"; break;
            case 4: fileName = "group_ironman.png"; break;
            case 5: fileName = "hardcore_group_ironman.png"; break;
            case 6: fileName = "unranked_group_ironman.png"; break;
        }
        if (fileName != null) {
            try {
                return net.runelite.client.util.ImageUtil.loadImageResource(RaidPartyPlugin.class, fileName);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
