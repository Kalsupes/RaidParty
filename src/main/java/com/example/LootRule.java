package com.example;

public enum LootRule {
    UNSPECIFIED("Unspecified"),
    FFA("FFA"),
    SPLIT("Split");

    private final String title;

    LootRule(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
