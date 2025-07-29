package org.example.illuminatiadminbot.inbound.menu;

public enum Subscription {
    BASIC,
    PREMIUM,
    GOLD,
    ADMIN,
    CREATOR,
    TEMP;

    public static boolean contains(String s) {
        try {
            Subscription.valueOf(s.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static Subscription fromString(String s) {
        try {
            return Subscription.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
