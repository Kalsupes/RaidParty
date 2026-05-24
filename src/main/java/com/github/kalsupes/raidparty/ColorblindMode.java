package com.github.kalsupes.raidparty;

public enum ColorblindMode {
    NONE("None"),
    DEUTERANOPE("Deuteranope"),
    PROTANOPE("Protanope"),
    TRITANOPE("Tritanope");

    private final String name;

    ColorblindMode(String name) {
        this.name = name;
    }

    public java.awt.Color adjustColor(java.awt.Color original, int pingType) {
        if (this == NONE) return original;
        
        // pingType: 0=Safe(Green), 1=Caution(Yellow), 2=Danger/CriticalHP(Red), 3=Resource(Blue), 4=LowHP(Yellow)
        if (this == DEUTERANOPE || this == PROTANOPE) {
            if (pingType == 0) return java.awt.Color.decode("#00A2FF"); // Sky Blue
            if (pingType == 2) return java.awt.Color.decode("#D55E00"); // Vermilion
            if (pingType == 1 || pingType == 4) return java.awt.Color.decode("#FFC20A"); // Bright Gold
            if (pingType == 3) return java.awt.Color.decode("#CC00FF"); // Magenta
        } else if (this == TRITANOPE) {
            if (pingType == 1 || pingType == 4) return java.awt.Color.decode("#FFC0CB"); // Pink
            if (pingType == 3) return java.awt.Color.decode("#00FFFF"); // Cyan
        }
        
        return original;
    }

    @Override
    public String toString() {
        return name;
    }
}
