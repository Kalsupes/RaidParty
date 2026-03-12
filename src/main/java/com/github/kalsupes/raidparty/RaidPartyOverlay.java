package com.github.kalsupes.raidparty;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;

public class RaidPartyOverlay extends Overlay {
    private final Client client;
    private final RaidPartyPlugin plugin;

    private final RaidPartyConfig config;

    @Inject
    public RaidPartyOverlay(Client client, RaidPartyPlugin plugin, RaidPartyConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        long now = System.currentTimeMillis();

        // Collect expired then batch-remove (avoids O(n²) on CopyOnWriteArrayList)
        java.util.List<RaidPartyPlugin.BossPing> expired = new java.util.ArrayList<>();
        for (RaidPartyPlugin.BossPing p : plugin.getActivePings()) {
            if (now > p.getExpiryTime())
                expired.add(p);
        }
        if (!expired.isEmpty())
            plugin.getActivePings().removeAll(expired);

        for (RaidPartyPlugin.BossPing ping : plugin.getActivePings()) {
            if (ping.getPoint().getPlane() != client.getPlane())
                continue;

            long remaining = ping.getExpiryTime() - now;
            float lifespanPct = Math.max(0, remaining / 4000f); // 0 to 1 based on 4s total life

            Color baseColor;
            java.awt.Shape shapeToDraw = null;
            String floatingText = "";
            net.runelite.api.Point floatingPoint = null;

            if (ping.getTargetType() == 1) { // NPC Target
                net.runelite.api.NPC npc = null;
                for (net.runelite.api.NPC n : client.getNpcs()) {
                    if (n.getIndex() == ping.getTargetIndex()) {
                        npc = n;
                        break;
                    }
                }

                if (npc != null) {
                    baseColor = config.entityPingColor();
                    shapeToDraw = npc.getConvexHull();
                    floatingText = Text.removeTags(npc.getName());
                    if (floatingText != null) {
                        floatingPoint = npc.getLogicalHeight() > 0
                                ? npc.getCanvasTextLocation(graphics, floatingText, npc.getLogicalHeight() + 40)
                                : net.runelite.api.Perspective.localToCanvas(client, npc.getLocalLocation(),
                                        client.getPlane(), 200);
                    }
                } else {
                    continue; // Skip rendering if NPC died or despawned
                }
            } else if (ping.getTargetType() == 2) { // GameObject Target
                LocalPoint lp = LocalPoint.fromWorld(client, ping.getPoint());
                if (lp == null)
                    continue;

                net.runelite.api.Tile tile = client.getScene().getTiles()[client.getPlane()][lp.getSceneX()][lp
                        .getSceneY()];
                net.runelite.api.TileObject targetObj = null;
                if (tile != null) {
                    if (tile.getWallObject() != null && tile.getWallObject().getId() == ping.getTargetIndex()) {
                        targetObj = tile.getWallObject();
                    } else if (tile.getDecorativeObject() != null
                            && tile.getDecorativeObject().getId() == ping.getTargetIndex()) {
                        targetObj = tile.getDecorativeObject();
                    } else if (tile.getGroundObject() != null
                            && tile.getGroundObject().getId() == ping.getTargetIndex()) {
                        targetObj = tile.getGroundObject();
                    } else {
                        net.runelite.api.GameObject[] gameObjects = tile.getGameObjects();
                        if (gameObjects != null) {
                            for (net.runelite.api.GameObject obj : gameObjects) {
                                if (obj != null && obj.getId() == ping.getTargetIndex()) {
                                    targetObj = obj;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (targetObj != null) {
                    baseColor = config.objectPingColor();
                    if (targetObj instanceof net.runelite.api.GameObject) {
                        shapeToDraw = ((net.runelite.api.GameObject) targetObj).getConvexHull();
                    }
                    if (shapeToDraw == null)
                        shapeToDraw = targetObj.getClickbox();
                    if (shapeToDraw == null)
                        shapeToDraw = Perspective.getCanvasTilePoly(client, lp);

                    net.runelite.api.ObjectComposition def = client.getObjectDefinition(targetObj.getId());
                    if (def != null) {
                        if (def.getImpostorIds() != null) {
                            def = def.getImpostor();
                        }
                        if (def != null)
                            floatingText = Text.removeTags(def.getName());
                    }

                    int hoverHeight = 150 + (int) (Math.sin(System.currentTimeMillis() / 200.0) * 20); // Bobbing up and
                                                                                                       // down
                    floatingPoint = Perspective.localToCanvas(client, lp, client.getPlane(), hoverHeight);
                } else {
                    continue;
                }
            } else if (ping.getTargetType() == 3) { // GroundItem Target
                LocalPoint lp = LocalPoint.fromWorld(client, ping.getPoint());
                if (lp == null)
                    continue;

                net.runelite.api.Tile tile = client.getScene().getTiles()[client.getPlane()][lp.getSceneX()][lp
                        .getSceneY()];
                net.runelite.api.TileItem targetItem = null;
                if (tile != null) {
                    java.util.List<net.runelite.api.TileItem> groundItems = tile.getGroundItems();
                    if (groundItems != null) {
                        for (net.runelite.api.TileItem item : groundItems) {
                            if (item.getId() == ping.getTargetIndex()) {
                                targetItem = item;
                                break;
                            }
                        }
                    }
                }

                if (targetItem != null) {
                    baseColor = config.itemPingColor();
                    shapeToDraw = Perspective.getCanvasTilePoly(client, lp);

                    net.runelite.api.ItemComposition def = client.getItemDefinition(targetItem.getId());
                    if (def != null)
                        floatingText = Text.removeTags(def.getName());

                    int hoverHeight = 150 + (int) (Math.sin(System.currentTimeMillis() / 200.0) * 20); // Bobbing up and
                                                                                                       // down
                    floatingPoint = Perspective.localToCanvas(client, lp, client.getPlane(), hoverHeight);
                } else {
                    continue;
                }
            } else { // Tile Target
                LocalPoint lp = LocalPoint.fromWorld(client, ping.getPoint());
                if (lp == null)
                    continue;

                shapeToDraw = Perspective.getCanvasTilePoly(client, lp);

                if (ping.getPingType() == 2) {
                    baseColor = config.dangerPingColor();
                    floatingText = "X";
                } else if (ping.getPingType() == 1) {
                    baseColor = config.cautionPingColor();
                    floatingText = "!";
                } else {
                    baseColor = config.safePingColor();
                    floatingText = "v";
                }

                int hoverHeight = 150 + (int) (Math.sin(System.currentTimeMillis() / 200.0) * 20); // Bobbing up and
                                                                                                   // down
                floatingPoint = Perspective.localToCanvas(client, lp, client.getPlane(), hoverHeight);
            }

            if (shapeToDraw != null) {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw filled polygon (either tile or hull)
                graphics.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                        (int) (50 * lifespanPct)));
                graphics.fill(shapeToDraw);

                // Emulate a blurred glow by drawing multiple fading thick strokes
                for (int i = 0; i < 5; i++) {
                    float glowAlpha = 1f - (i / 5f);
                    graphics.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                            (int) (100 * glowAlpha * lifespanPct)));
                    graphics.setStroke(new BasicStroke(2f + (i * 2.5f)));
                    graphics.draw(shapeToDraw);
                }

                // Core solid bright line
                graphics.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                        (int) (255 * lifespanPct)));
                graphics.setStroke(new BasicStroke(2f));
                graphics.draw(shapeToDraw);
            }

            // Hovering 3D Icon / Name Renderer
            if (config.drawPingIcons() && floatingPoint != null && lifespanPct > 0.1f && floatingText != null
                    && !floatingText.isEmpty()) {
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setFont(new Font("Arial", Font.BOLD, ping.getTargetType() == 1 ? 16 : 36));

                FontMetrics metrics = graphics.getFontMetrics();
                int x = floatingPoint.getX() - metrics.stringWidth(floatingText) / 2;
                int y = floatingPoint.getY() + metrics.getAscent() / 4;

                // Draw black drop shadow for 3D effect
                graphics.setColor(Color.BLACK);
                graphics.drawString(floatingText, x + (ping.getTargetType() == 1 ? 1 : 2),
                        y + (ping.getTargetType() == 1 ? 1 : 2));

                // Draw actual text
                graphics.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(),
                        (int) (255 * lifespanPct)));
                graphics.drawString(floatingText, x, y);
            }
        }

        // --- Low HP Player Glow ---
        for (net.runelite.api.Player p : client.getPlayers()) {
            if (p == null || p.getName() == null)
                continue;

            boolean isLocal = (p == client.getLocalPlayer());
            boolean inParty = plugin.getPartyService() != null && plugin.getPartyService().isInParty();

            // Only render other players if we are in a party
            if (!isLocal && !inParty)
                continue;

            RaidPartyPlayerSync syncData = null;
            if (isLocal) {
                syncData = plugin.getLocalPlayerSync();
            } else {
                String pName = p.getName();
                for (RaidPartyPlayerSync s : plugin.getPartyData().values()) {
                    if (pName.compareTo("\u00A0") != 0
                            && pName.replace('\u00A0', ' ').equals(s.getUsername().replace('\u00A0', ' '))) {
                        syncData = s;
                        break;
                    }
                }
            }

            if (syncData != null && syncData.getMaxHp() > 0) {
                float hpPct = (float) syncData.getHp() / syncData.getMaxHp();
                Color glowColor = null;

                if (hpPct <= (config.criticalHpThreshold() / 100f) && config.enableCriticalHpGlow()) {
                    glowColor = config.criticalHpColor();
                } else if (hpPct <= (config.lowHpThreshold() / 100f) && config.enableLowHpGlow()) {
                    glowColor = config.lowHpColor();
                }

                if (glowColor != null) {
                    java.awt.Shape hull = p.getConvexHull();
                    if (hull != null) {
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        // High-speed pulsing multiplier
                        float pulse = 0.5f + (float) (Math.sin(System.currentTimeMillis() / 100.0) * 0.5f);

                        // Emulate a blurred glow by drawing multiple fading thick strokes
                        for (int i = 0; i < 4; i++) {
                            float glowAlpha = 1f - (i / 4f);
                            graphics.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(),
                                    (int) (120 * glowAlpha * pulse)));
                            graphics.setStroke(new BasicStroke(2f + (i * 3f)));
                            graphics.draw(hull);
                        }

                        // Core solid bright line
                        graphics.setColor(new Color(glowColor.getRed(), glowColor.getGreen(), glowColor.getBlue(),
                                (int) (255 * pulse)));
                        graphics.setStroke(new BasicStroke(1.5f));
                        graphics.draw(hull);
                    }
                }
            }
        }

        return null;
    }
}
