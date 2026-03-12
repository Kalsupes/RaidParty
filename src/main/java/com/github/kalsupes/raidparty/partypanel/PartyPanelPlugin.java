package com.github.kalsupes.raidparty.partypanel;

import net.runelite.api.Client;
import net.runelite.api.Item;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;

public class PartyPanelPlugin {
    public static final int[] RUNEPOUCH_ITEM_IDS = new int[]{
        12791, // Rune pouch
        24483, // Divine rune pouch
        27281  // Rune pouch (l)
    };

    public static final ImmutableSet<Integer> DIZANAS_QUIVER_IDS = ImmutableSet.of(
        28984, // Dizana's quiver
        28986, // Dizana's quiver (a)
        28988  // Dizana's quiver (c)
    );

    public static List<Item> getRunePouchContents(Client client) {
        return Collections.emptyList(); // Stubbed for plugin compatibility
    }
}
