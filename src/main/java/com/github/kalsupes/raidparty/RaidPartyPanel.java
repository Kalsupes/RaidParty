package com.github.kalsupes.raidparty;

import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

public class RaidPartyPanel extends PluginPanel {
    private final RaidPartyPlugin plugin;

    private static final Color ACCENT_GOLD = Color.decode("#FFD700");
    private final Map<String, RaidPartyPlayerCard> memberCards = new HashMap<>();

    private final JPanel rosterContainer = new JPanel();

    // UI Containers
    private final JPanel northActionsContainer = new JPanel(new BorderLayout());
    private final JPanel disconnectedPanel = new JPanel();
    private final JPanel connectedPanel = new JPanel();
    private final JPanel centerContainer = new JPanel(new BorderLayout());
    private final JLabel emptyStateLabel = new JLabel(
            "<html><center>Not in a party.<br><font color='#777777'>Create/Join a party to begin.</font></center></html>");

    private final JTextField passphraseInput = new JTextField();
    private final JButton leaveButton = new FlatButton("Leave Party", new Color(160, 40, 40), new Color(180, 50, 50));
    private final JButton togglePassphraseBtn = new JButton("👁");
    private boolean passphraseMasked = false;
    private String currentPassphrase = "";

    public RaidPartyPanel(RaidPartyPlugin plugin) {
        super();
        this.plugin = plugin;
        setLayout(new BorderLayout());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);

        // Header
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);
        northPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Compact Top Padding
        northPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // empty state label formatting
        emptyStateLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyStateLabel.setVerticalAlignment(SwingConstants.TOP);
        emptyStateLabel.setFont(FontManager.getRunescapeFont());
        emptyStateLabel.setBorder(new EmptyBorder(20, 0, 0, 0));

        // Disconnected panel setup (top buttons)
        disconnectedPanel.setLayout(new GridLayout(2, 1, 5, 5));
        disconnectedPanel.setOpaque(false);

        JPanel topRowButtons = new JPanel(new GridLayout(1, 2, 5, 0));
        topRowButtons.setOpaque(false);

        JButton createButton = new FlatButton("Create party", new Color(35, 35, 35), new Color(45, 45, 45));
        JButton joinNewButton = new FlatButton("Join party", new Color(35, 35, 35), new Color(45, 45, 45));

        createButton.addActionListener(e -> {
            plugin.getClientThread().invokeLater(() -> {
                String pass = null;
                try {
                    pass = plugin.getPartyService().generatePassphrase();
                } catch (Exception ex) {
                    pass = "raidparty-" + java.util.UUID.randomUUID().toString().substring(0, 8);
                }
                final String finalPass = pass;
                SwingUtilities.invokeLater(() -> plugin.joinParty(finalPass));
            });
        });

        joinNewButton.addActionListener(e -> {
            String p = JOptionPane.showInputDialog(this, "Enter party passphrase:");
            if (p != null && !p.trim().isEmpty())
                plugin.joinParty(p.trim());
        });

        topRowButtons.add(createButton);
        topRowButtons.add(joinNewButton);

        JButton joinPreviousButton = new FlatButton("Join previous party", new Color(35, 35, 35),
                new Color(45, 45, 45));
        joinPreviousButton.addActionListener(e -> {
            String prev = plugin.getConfigManager().getConfiguration("raidparty", "previousParty");
            if (prev != null && !prev.isEmpty()) {
                plugin.joinParty(prev);
            } else {
                JOptionPane.showMessageDialog(this, "No previous party saved.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        disconnectedPanel.add(topRowButtons);
        disconnectedPanel.add(joinPreviousButton);

        // Connected panel setup (leave button on top, passphrase row below)
        connectedPanel.setLayout(new BoxLayout(connectedPanel, BoxLayout.Y_AXIS));
        connectedPanel.setOpaque(false);

        // Top: Leave button centered
        leaveButton.setPreferredSize(new Dimension(0, 30));
        leaveButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        leaveButton.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
        leaveButton.addActionListener(e -> plugin.leaveParty());
        connectedPanel.add(leaveButton);

        connectedPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Bottom row: [Copy icon] [passphrase field] [show/hide toggle]
        // Custom painted copy icon: two overlapping rectangles
        javax.swing.Icon copyIcon = new javax.swing.Icon() {
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.LIGHT_GRAY);
                g2.setStroke(new BasicStroke(1.3f));
                // Back rectangle
                g2.drawRect(x + 4, y + 1, 8, 10);
                // Front rectangle (overlapping, offset)
                g2.setColor(new Color(35, 35, 35));
                g2.fillRect(x + 1, y + 4, 8, 10);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawRect(x + 1, y + 4, 8, 10);
                g2.dispose();
            }

            public int getIconWidth() {
                return 14;
            }

            public int getIconHeight() {
                return 15;
            }
        };
        javax.swing.Icon checkIcon = new javax.swing.Icon() {
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(100, 200, 100));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(x + 2, y + 8, x + 5, y + 12);
                g2.drawLine(x + 5, y + 12, x + 12, y + 3);
                g2.dispose();
            }

            public int getIconWidth() {
                return 14;
            }

            public int getIconHeight() {
                return 15;
            }
        };

        JButton copyButton = new JButton(copyIcon);
        copyButton.setPreferredSize(new Dimension(28, 26));
        copyButton.setMinimumSize(new Dimension(28, 26));
        copyButton.setMaximumSize(new Dimension(28, 26));
        copyButton.setToolTipText("Copy passphrase");
        copyButton.setFocusPainted(false);
        copyButton.setBackground(new Color(35, 35, 35));
        copyButton.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        copyButton.addActionListener(e -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(currentPassphrase), null);
            copyButton.setIcon(checkIcon);
            copyButton.setText("");
            javax.swing.Timer timer = new javax.swing.Timer(1500, evt -> {
                copyButton.setIcon(copyIcon);
                copyButton.setText("");
            });
            timer.setRepeats(false);
            timer.start();
        });

        passphraseInput.setFont(FontManager.getRunescapeFont());
        passphraseInput.setHorizontalAlignment(JTextField.CENTER);
        passphraseInput.setPreferredSize(new Dimension(0, 26));
        passphraseInput.setEditable(false);

        togglePassphraseBtn.setPreferredSize(new Dimension(28, 26));
        togglePassphraseBtn.setMinimumSize(new Dimension(28, 26));
        togglePassphraseBtn.setMaximumSize(new Dimension(28, 26));
        togglePassphraseBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        togglePassphraseBtn.setToolTipText("Toggle passphrase visibility");
        togglePassphraseBtn.setFocusPainted(false);
        togglePassphraseBtn.setBackground(new Color(35, 35, 35));
        togglePassphraseBtn.setForeground(Color.LIGHT_GRAY);
        togglePassphraseBtn.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        togglePassphraseBtn.addActionListener(e -> {
            passphraseMasked = !passphraseMasked;
            applyPassphraseMask();
        });

        JPanel passphraseRow = new JPanel(new BorderLayout(3, 0));
        passphraseRow.setOpaque(false);
        passphraseRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        passphraseRow.add(copyButton, BorderLayout.WEST);
        passphraseRow.add(passphraseInput, BorderLayout.CENTER);
        passphraseRow.add(togglePassphraseBtn, BorderLayout.EAST);
        connectedPanel.add(passphraseRow);

        connectedPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Ready Check buttons row
        JPanel readyCheckRow = new JPanel(new GridLayout(1, 2, 6, 0));
        readyCheckRow.setOpaque(false);
        readyCheckRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JButton readyBtn = new JButton("Ready") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = plugin.getLocalReadyState() == 1;
                if (selected) {
                    g2.setColor(getModel().isPressed() ? new Color(30, 130, 50) : new Color(40, 160, 60));
                } else {
                    g2.setColor(getModel().isPressed() ? new Color(40, 40, 40) : new Color(60, 60, 60));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        readyBtn.setFont(FontManager.getRunescapeBoldFont());
        readyBtn.setForeground(Color.WHITE);
        readyBtn.setFocusPainted(false);
        readyBtn.setContentAreaFilled(false);
        readyBtn.setBorderPainted(false);
        readyBtn.setOpaque(false);
        readyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton notReadyBtn = new JButton("Not Ready") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = plugin.getLocalReadyState() == 2;
                if (selected) {
                    g2.setColor(getModel().isPressed() ? new Color(160, 30, 30) : new Color(190, 40, 40));
                } else {
                    g2.setColor(getModel().isPressed() ? new Color(40, 40, 40) : new Color(60, 60, 60));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        notReadyBtn.setFont(FontManager.getRunescapeBoldFont());
        notReadyBtn.setForeground(Color.WHITE);
        notReadyBtn.setFocusPainted(false);
        notReadyBtn.setContentAreaFilled(false);
        notReadyBtn.setBorderPainted(false);
        notReadyBtn.setOpaque(false);
        notReadyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        readyBtn.addActionListener(e -> {
            int current = plugin.getLocalReadyState();
            plugin.setReadyState(current == 1 ? 0 : 1);
            readyCheckRow.repaint();
        });

        notReadyBtn.addActionListener(e -> {
            int current = plugin.getLocalReadyState();
            plugin.setReadyState(current == 2 ? 0 : 2);
            readyCheckRow.repaint();
        });

        readyCheckRow.add(readyBtn);
        readyCheckRow.add(notReadyBtn);
        connectedPanel.add(readyCheckRow);

        connectedPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        // Loot Rule buttons row
        JPanel lootRuleRow = new JPanel(new GridLayout(1, 3, 6, 0));
        lootRuleRow.setOpaque(false);
        lootRuleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JButton ffaBtn = createLootBtn("FFA", LootRule.FFA, new Color(175, 0, 175), new Color(140, 0, 140),
                lootRuleRow);
        JButton noneBtn = createLootBtn("None", LootRule.UNSPECIFIED, new Color(120, 120, 120), new Color(90, 90, 90),
                lootRuleRow);
        JButton splitBtn = createLootBtn("SPLIT", LootRule.SPLIT, new Color(0, 191, 255), new Color(0, 150, 200),
                lootRuleRow);

        lootRuleRow.add(ffaBtn);
        lootRuleRow.add(noneBtn);
        lootRuleRow.add(splitBtn);
        connectedPanel.add(lootRuleRow);

        // north wrapper
        JPanel headerWrapper = new JPanel();
        headerWrapper.setLayout(new BoxLayout(headerWrapper, BoxLayout.Y_AXIS));
        headerWrapper.setOpaque(false);
        headerWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));

        northActionsContainer.setOpaque(false);
        northActionsContainer.add(disconnectedPanel, BorderLayout.CENTER); // default
        headerWrapper.add(northActionsContainer);

        wrapper.add(headerWrapper, BorderLayout.NORTH);

        // Center Wrapper
        rosterContainer.setLayout(new BoxLayout(rosterContainer, BoxLayout.Y_AXIS));
        rosterContainer.setOpaque(false);

        centerContainer.setOpaque(false);
        centerContainer.add(emptyStateLabel, BorderLayout.NORTH); // default
        wrapper.add(centerContainer, BorderLayout.CENTER);

        add(wrapper, BorderLayout.CENTER);
    }

    public void updateConnectionState(boolean connected, String passphrase) {
        SwingUtilities.invokeLater(() -> {
            northActionsContainer.removeAll();
            centerContainer.removeAll();

            if (connected) {
                currentPassphrase = passphrase;
                // Check config for default mask state
                try {
                    passphraseMasked = true; // Default to masked; toggled via panel button
                } catch (Exception ignored) {
                }
                applyPassphraseMask();
                northActionsContainer.add(connectedPanel, BorderLayout.CENTER);
                centerContainer.add(rosterContainer, BorderLayout.NORTH);
            } else {
                currentPassphrase = "";
                northActionsContainer.add(disconnectedPanel, BorderLayout.CENTER);
                centerContainer.add(emptyStateLabel, BorderLayout.NORTH);
                rosterContainer.removeAll();
                memberCards.clear();
            }

            northActionsContainer.revalidate();
            northActionsContainer.repaint();
            centerContainer.revalidate();
            centerContainer.repaint();
        });
    }

    private void applyPassphraseMask() {
        if (passphraseMasked) {
            passphraseInput.setText("•".repeat(Math.max(8, currentPassphrase.length())));
            togglePassphraseBtn.setText("👁");
            togglePassphraseBtn.setToolTipText("Show passphrase");
        } else {
            passphraseInput.setText(currentPassphrase);
            togglePassphraseBtn.setText("🙈");
            togglePassphraseBtn.setToolTipText("Hide passphrase");
        }
    }

    public void addMember(String username, boolean isLocal) {
        SwingUtilities.invokeLater(() -> {
            String sanitized = username.toLowerCase().replace("\u00A0", " ");
            if (memberCards.containsKey(sanitized))
                return;

            // Dummy sync on creation
            RaidPartyPlayerSync dummySync = new RaidPartyPlayerSync();
            dummySync.setUsername(username);

            RaidPartyPlayerCard card = new RaidPartyPlayerCard(plugin, username, false, dummySync);
            card.setAlignmentX(Component.CENTER_ALIGNMENT);
            memberCards.put(sanitized, card);

            rosterContainer.add(card);
            rosterContainer.add(Box.createRigidArea(new Dimension(0, 4)));
            rosterContainer.revalidate();
            rosterContainer.repaint();
        });
    }

    public void removeMember(String username) {
        SwingUtilities.invokeLater(() -> {
            String sanitized = username.toLowerCase().replace("\u00A0", " ");
            RaidPartyPlayerCard card = memberCards.remove(sanitized);
            if (card != null) {
                rosterContainer.remove(card);
                rosterContainer.revalidate();
                rosterContainer.repaint();
            }
        });
    }

    public void onPlayerSync(RaidPartyPlayerSync sync) {
        if (sync == null || sync.getUsername() == null)
            return;
        SwingUtilities.invokeLater(() -> {
            String sanitized = sync.getUsername().toLowerCase().replace("\u00A0", " ");
            RaidPartyPlayerCard card = memberCards.get(sanitized);
            if (card != null) {
                card.updateSyncData(sync);
            } else {
                addMember(sync.getUsername(), false);
            }
        });
    }

    private JButton createLootBtn(String text, LootRule rule, Color selectedColor, Color pressedColor,
            JPanel parentRow) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = plugin.getLocalLootRule() == rule;
                if (selected) {
                    g2.setColor(getModel().isPressed() ? pressedColor : selectedColor);
                } else {
                    g2.setColor(getModel().isPressed() ? new Color(40, 40, 40) : new Color(60, 60, 60));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> {
            plugin.setLootRule(rule);
            parentRow.repaint();
        });
        return btn;
    }

    public static class FlatButton extends JButton {
        private final Color baseColor;
        private final Color hoverColor;
        private boolean isHovered = false;

        public FlatButton(String text, Color base, Color hover) {
            super(text);
            this.baseColor = base;
            this.hoverColor = hover;

            setFont(FontManager.getRunescapeBoldFont());
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setContentAreaFilled(false);

            addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    isHovered = true;
                    repaint();
                }

                public void mouseExited(java.awt.event.MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color bg = isHovered ? new Color(40, 45, 50, 220) : new Color(25, 27, 30, 180);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, 8, 8);

            Color borderColor = baseColor;
            if (isHovered)
                borderColor = borderColor.brighter();

            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = g2.getFontMetrics();
            int stringWidth = fm.stringWidth(getText());
            int stringX = (w - stringWidth) / 2;
            int stringY = (h - fm.getHeight()) / 2 + fm.getAscent();

            g2.setColor(Color.BLACK);
            g2.drawString(getText(), stringX + 1, stringY + 1);

            g2.setColor(getForeground());
            g2.drawString(getText(), stringX, stringY);

            g2.dispose();
        }
    }
}
