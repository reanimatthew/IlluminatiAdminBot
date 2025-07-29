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
            Subscription.valueOf(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
