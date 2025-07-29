package org.example.illuminatiadminbot.inbound.menu;

public enum ThemeState {
    STRENGTH("Strength"),
    PERCEPTION("Perception"),
    ENDURANCE("Endurance"),
    CHARISMA("Charisma");

    private String displayName;

    ThemeState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static boolean contains(String name) {
        for (ThemeState ts : values()) {
            if (ts.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
