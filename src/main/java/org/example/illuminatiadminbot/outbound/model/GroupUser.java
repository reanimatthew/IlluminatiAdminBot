package org.example.illuminatiadminbot.outbound.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.inbound.model.UserStatus;

import java.time.LocalDate;

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
    @Enumerated(EnumType.STRING)
    UserStatus status;

    @Column
    Long chatId;

    public String toStringSupergroup() {
        return "telegramId=" + telegramId +
                ", \nnickname='" + nickname + '\'' +
                ", \ntelegramUserStatus='" + telegramUserStatus + '\'' +
                "\n";
    }

    public String toStringSql() {
        return "Telegram ID: " + telegramId +
                "\nник: " + nickname +
                "\nстатус: " + telegramUserStatus +
                "\nтип подписки: " + subscriptionType +
                "\nдлительность подписки, мес.: " + subscriptionDuration +
                "\nподписка истекает: " + subscriptionExpiration +
                "\n\n";
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public static GroupUser createNewMember(long telegramId, String nickname) {
        return GroupUser.builder()
                .telegramId(telegramId)
                .nickname(nickname)
                .telegramUserStatus(TelegramUserStatus.MEMBER)
                .subscriptionType(Subscription.TEMP)
                .subscriptionDuration(1)
                .subscriptionExpiration(LocalDate.now().plusMonths(1))
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static GroupUser createNewAdministrator(long telegramId, String nickname) {
        return GroupUser.builder()
                .telegramId(telegramId)
                .nickname(nickname)
                .telegramUserStatus(TelegramUserStatus.ADMINISTRATOR)
                .subscriptionType(Subscription.ADMIN)
                .subscriptionDuration(12 * 5)
                .subscriptionExpiration(LocalDate.now().plusYears(5))
                .status(UserStatus.ACTIVE)
                .build();
    }

    public static GroupUser createNewCreator(long telegramId, String nickname) {
        return GroupUser.builder()
                .telegramId(telegramId)
                .nickname(nickname)
                .telegramUserStatus(TelegramUserStatus.CREATOR)
                .subscriptionType(Subscription.CREATOR)
                .subscriptionDuration(12 * 20)
                .subscriptionExpiration(LocalDate.now().plusYears(20))
                .status(UserStatus.ACTIVE)
                .build();
    }
}
