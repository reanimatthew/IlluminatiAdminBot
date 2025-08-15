package org.example.illuminatiadminbot.outbound.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;

import java.time.LocalDate;
import java.time.ZoneId;

@Entity

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor
@Builder
@Data
public class GroupUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    Long telegramId;

    @Column
    String nickname;

    @Column
    @Enumerated(EnumType.STRING)
    TelegramUserStatus telegramUserStatus;

    @Column
    @Enumerated(EnumType.STRING)
    Subscription subscriptionType;

    @Column
    int subscriptionDuration;

    @Column
    LocalDate subscriptionExpiration;

    @Column
    Long chatId;

    public String toStringSupergroup() {
        return "telegramId=" + telegramId +
                " \nnickname=" + nickname +
                " \ntelegramUserStatus=" + telegramUserStatus +
                "\n";
    }

    public String toStringSql() {
        return "Telegram ID: " + telegramId +
                "\nник: " + nickname +
                "\nстатус: " + telegramUserStatus +
                "\nтип подписки: " + subscriptionType +
                "\nдлительность подписки, мес.: " + subscriptionDuration +
                "\nподписка истекает: " + subscriptionExpiration +
                "\n";
    }

    public boolean isActive() {
        return telegramUserStatus == TelegramUserStatus.CREATOR
                || telegramUserStatus == TelegramUserStatus.ADMINISTRATOR
                || telegramUserStatus == TelegramUserStatus.MEMBER;
    }

    public boolean isMember() {
        return telegramUserStatus == TelegramUserStatus.MEMBER;
    }

    public boolean isAdminOrCreator() {
        return telegramUserStatus == TelegramUserStatus.CREATOR || telegramUserStatus == TelegramUserStatus.ADMINISTRATOR;
    }

    public static GroupUser createNewUser(TelegramUserStatus telegramUserStatus, long telegramId, String nickname) {
        return switch (telegramUserStatus) {
            case CREATOR -> builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.CREATOR)
                    .subscriptionType(Subscription.CREATOR)
                    .subscriptionDuration(12 * 20)
                    .subscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(20))
                    .build();

            case ADMINISTRATOR -> builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.ADMINISTRATOR)
                    .subscriptionType(Subscription.ADMIN)
                    .subscriptionDuration(12 * 5)
                    .subscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusYears(5))
                    .build();

            case MEMBER -> builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.MEMBER)
                    .subscriptionType(Subscription.TEMP)
                    .subscriptionDuration(1)
                    .subscriptionExpiration(LocalDate.now(ZoneId.of("Europe/Moscow")).plusMonths(1))
                    .build();

            case BANNED -> builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.BANNED)
                    .build();

            case RESTRICTED -> builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.RESTRICTED)
                    .build();

            case LEFT -> builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.LEFT)
                    .build();

            default -> GroupUser.builder()
                    .telegramId(telegramId)
                    .nickname(nickname)
                    .telegramUserStatus(TelegramUserStatus.UNKNOWN)
                    .build();
        };
    }
}
