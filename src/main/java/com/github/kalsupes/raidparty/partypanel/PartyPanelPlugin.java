package com.github.kalsupes.raidparty.partypanel;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemVariationMapping;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;

public class PartyPanelPlugin {
    public static final int[] RUNEPOUCH_ITEM_IDS = new int[]{
        12791, // Rune pouch
        24483, // Divine rune pouch
        27281  // Rune pouch (l)
    };

    public static final ImmutableSet<Integer> DIZANAS_QUIVER_IDS = ImmutableSet.<Integer>builder()
        .addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.DIZANAS_QUIVER_CHARGED)))
        .addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.DIZANAS_QUIVER_INFINITE)))
        .addAll(ItemVariationMapping.getVariations(ItemVariationMapping.map(ItemID.SKILLCAPE_MAX_DIZANAS)))
        .build();

    public static List<Item> getRunePouchContents(Client client) {
        return Collections.emptyList(); // Stubbed for plugin compatibility
    }
}
