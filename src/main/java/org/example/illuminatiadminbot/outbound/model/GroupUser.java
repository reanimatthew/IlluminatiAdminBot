package org.example.illuminatiadminbot.outbound.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
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
    String subscriptionType;

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
}
