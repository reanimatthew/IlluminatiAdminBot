package org.example.illuminatiadminbot.inbound.model;

import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum TelegramUserStatus {
    CREATOR("creator"),
    ADMINISTRATOR("administrator"),
    MEMBER("member"),
    RESTRICTED("restricted"),
    LEFT("left"),
    BANNED("banned"),
    UNKNOWN("unknown");

    private final String value;

    TelegramUserStatus(String value) {
       this.value = value;
    }

    public boolean matches(String other) {
        return value.equalsIgnoreCase(other);
    }

    public static boolean is(String other, TelegramUserStatus status) {
        return status.matches(other);
    }

    public static List<String> allValues() {
        return Stream.of(values())
                .map(TelegramUserStatus::getValue)
                .collect(Collectors.toList());
    }

}
