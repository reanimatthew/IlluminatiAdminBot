package org.example.illuminatiadminbot.outbound.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Date;

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
    String role;

    @Column
    String subscriptionType;

    @Column
    Integer subscriptionDuration;

    @Column
    LocalDate subscriptionExpiration;
}
