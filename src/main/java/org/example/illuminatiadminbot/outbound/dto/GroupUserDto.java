package org.example.illuminatiadminbot.outbound.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor
@Builder
@Data
public class GroupUserDto {
    private Long id;
    Long telegramId;
    String nickname;
    String telegramUserStatus;
    String subscriptionType;
    int subscriptionDuration;
    LocalDate subscriptionExpiration;
    String status;
}
