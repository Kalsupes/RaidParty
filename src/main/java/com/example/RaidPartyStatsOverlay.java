package com.example;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class RaidPartyStatsOverlay extends Overlay {

    private final RaidPartyPlugin plugin;
    private final RaidTracker raidTracker;
    private final PanelComponent panelComponent = new PanelComponent();

    private static final NumberFormat POINTS_FORMAT = NumberFormat.getInstance();
    private static final NumberFormat PERCENT_FORMAT = new DecimalFormat("#.##%");

    @Inject
    public RaidPartyStatsOverlay(RaidPartyPlugin plugin, RaidTracker raidTracker) {
        super(plugin);
        this.plugin = plugin;
        this.raidTracker = raidTracker;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(Graphics2D graphics) {

        // Determine if we are in ToA, CoX or ToB to display the smart overlay
        boolean inToa = raidTracker.inToa();
        boolean inCox = raidTracker.inCox();
        
        if (!inToa && !inCox) {
            // Either waiting in lobby or not raiding
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("RaidParty Stats")
                .color(Color.ORANGE)
                .build());

        if (inToa) {
            // Calculate total party points
            int totalPartyPoints = 0;
            for (RaidPartyPlayerSync sync : plugin.getPartyData().values()) {
                totalPartyPoints += sync.getToaPoints();
            }
            // Include local player
            totalPartyPoints += raidTracker.getToaPersonalPoints();

            int raidLevel = raidTracker.getToaRaidLevel();
            double uniqueChance = RaidTracker.getToaUniqueChance(raidLevel, totalPartyPoints);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Raid Level:")
                    .right(String.valueOf(raidLevel))
                    .build());
                    
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Personal Points:")
                    .right(POINTS_FORMAT.format(raidTracker.getToaPersonalPoints()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Party Points:")
                    .right(POINTS_FORMAT.format(totalPartyPoints))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Purple Chance:")
                    .rightColor(Color.MAGENTA)
                    .right(PERCENT_FORMAT.format(uniqueChance))
                    .build());

        } else if (inCox) {
            int totalPartyPoints = raidTracker.getCoxTotalPoints(); // often managed by varbits
            if (totalPartyPoints == 0) {
                // Manually sum if total points varbit is 0
                for (RaidPartyPlayerSync sync : plugin.getPartyData().values()) {
                    totalPartyPoints += sync.getCoxPoints();
                }
                totalPartyPoints += raidTracker.getCoxPersonalPoints();
            }
            
            // CoX purple chance = points / 8675
            double uniqueChance = Math.min(65.7, totalPartyPoints / 8675.0) / 100.0;

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Personal Points:")
                    .right(POINTS_FORMAT.format(raidTracker.getCoxPersonalPoints()))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Party Points:")
                    .right(POINTS_FORMAT.format(totalPartyPoints))
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Purple Chance:")
                    .rightColor(Color.MAGENTA)
                    .right(PERCENT_FORMAT.format(uniqueChance))
                    .build());
        }

        return panelComponent.render(graphics);
    }
}
