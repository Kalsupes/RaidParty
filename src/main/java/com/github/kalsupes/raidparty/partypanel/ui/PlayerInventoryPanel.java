/*
 * Copyright (c) 2020, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.kalsupes.raidparty.partypanel.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import org.apache.commons.lang3.ArrayUtils;
import com.github.kalsupes.raidparty.partypanel.PartyPanelPlugin;
import com.github.kalsupes.raidparty.partypanel.data.GameItem;

public class PlayerInventoryPanel extends JPanel
{
	private static final Dimension INVI_SLOT_SIZE = new Dimension(44, 38);
	private static final Color INVI_BACKGROUND = new Color(62, 53, 41);

	private final ItemManager itemManager;

	public PlayerInventoryPanel(final GameItem[] items, final GameItem[] runePouchContents, final ItemManager itemManager)
	{
		super();

		this.itemManager = itemManager;

		setLayout(new DynamicGridLayout(7, 4, 2, 2));
		setBackground(INVI_BACKGROUND);

		updateInventory(items, runePouchContents);
	}

	public void updateInventory(final GameItem[] items, final GameItem[] runePouchContents)
	{
		this.removeAll();

		for (final GameItem i : items)
		{
			final JLabel label = new JLabel() {
				@Override
				protected void paintComponent(java.awt.Graphics g) {
					super.paintComponent(g);
					java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
					g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

					if (i != null && isDefenseLoweringWeapon(i.getId())) {
						g2.setColor(new Color(255, 215, 0, 180)); // Golden glow
						g2.setStroke(new java.awt.BasicStroke(2f));
						g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 4, 4);
					}

					if (i != null && i.getDisplayName() != null && i.getDisplayName().toLowerCase().contains("rune pouch") && runePouchContents != null) {

						int count = 0;
						for (GameItem rune : runePouchContents) {
							if (rune == null || rune.getId() <= 0) continue;

							net.runelite.client.util.AsyncBufferedImage runeImg = itemManager.getImage(rune.getId(), rune.getQty(), false);
							if (runeImg != null) {
								int x = (count % 2 == 0) ? 2 : 22;
								int y = (count < 2) ? 2 : 20;

								g2.drawImage(runeImg, x, y, 14, 14, null);

								int maxRunes = 16000;
								int height = Math.min(14, (int) ((rune.getQty() / (float) maxRunes) * 14));
								g2.setColor(Color.GREEN);
								g2.fillRect(x + 15, y + (14 - height), 2, height);

								count++;
							}
						}
					}
				}
			};

			label.setMinimumSize(INVI_SLOT_SIZE);
			label.setPreferredSize(INVI_SLOT_SIZE);
			label.setVerticalAlignment(JLabel.CENTER);
			label.setHorizontalAlignment(JLabel.CENTER);

			if (i != null)
			{
				String tooltip;
				if (i.getDisplayName() != null && i.getDisplayName().toLowerCase().contains("rune pouch"))
				{
					tooltip = getRunePouchHoverText(i, runePouchContents);
					if (runePouchContents != null) {
						for (GameItem rune : runePouchContents) {
							if (rune != null && rune.getId() > 0) {
								itemManager.getImage(rune.getId(), rune.getQty(), false).onLoaded(label::repaint);
							}
						}
					}
				}
				else
				{
					tooltip = i.getDisplayName();
				}
				label.setToolTipText(tooltip);
				itemManager.getImage(i.getId(), i.getQty(), i.isStackable()).addTo(label);
			}

			add(label);
		}

		for (int i = getComponentCount(); i < 28; i++)
		{
			final JLabel label = new JLabel();
			label.setMinimumSize(INVI_SLOT_SIZE);
			label.setPreferredSize(INVI_SLOT_SIZE);
			label.setVerticalAlignment(JLabel.CENTER);
			label.setHorizontalAlignment(JLabel.CENTER);
			add(label);
		}

		revalidate();
		repaint();
	}

	public String getRunePouchHoverText(final GameItem runePouch, final GameItem[] contents)
	{
		final String contentNames = Arrays.stream(contents)
			.filter(Objects::nonNull)
			.map(item -> java.text.NumberFormat.getInstance().format(item.getQty()) + " x " + item.getDisplayName())
			.collect(Collectors.joining("<br>"));

		if (contentNames.isEmpty())
		{
			return runePouch.getDisplayName() + " (Empty)";
		}

		return "<html>"
			+ runePouch.getDisplayName()
			+ "<br>---<br>"
			+ contentNames
			+ "</html>";
	}

	private boolean isDefenseLoweringWeapon(int itemId) {
		switch (itemId) {
			case 13576: // Dragon Warhammer
			case 20785: // Dragon Warhammer (cr)
			case 28035: // Dragon Warhammer (cr)
			case 26710: // Dragon Warhammer (or)
			case 11804: // Bandos Godsword
			case 20370: // Bandos Godsword (or)
			case 20782: // Bandos Godsword (or 2)
			case 21060: // Bandos Godsword (or 3)
			case 21003: // Elder Maul
			case 21205: // Elder Maul (or)
			case 27100: // Elder Maul (or 2)
			case 22622: // Statius's warhammer
			case 23620: // Statius's warhammer (c)
			case 27908: // Statius's warhammer (bh)
			case 8872:  // Bone dagger
			case 8874:  // Bone dagger (p)
			case 8876:  // Bone dagger (p+)
			case 8878:  // Bone dagger (p++)
			case 8880:  // Dorgeshuun crossbow
			case 27665: // Accursed sceptre
			case 27662: // Accursed sceptre (u)
			case 27679: // Accursed sceptre (a)
			case 27676: // Accursed sceptre (au)
			case 25492: // Shadow ancient sceptre
			case 28476: // Shadow ancient sceptre (l)
			case 19675: // Arclight
			case 29553: // Emberlight
			case 6746:  // Darklight
			case 10887: // Barrelchest anchor
			case 27855: // Barrelchest anchor (bh)
			case 28922: // Tonalztics of ralos
			case 28919: // Tonalztics of ralos (uncharged)
				return true;
			default:
				return false;
		}
	}
}
