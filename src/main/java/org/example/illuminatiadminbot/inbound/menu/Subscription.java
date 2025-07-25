package org.example.illuminatiadminbot.inbound.menu;

public enum Subscription {
    BASIC,
    PREMIUM,
    GOLD,
    ADMIN;

    public static boolean contains(String s) {
        try {
            Subscription.valueOf(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
