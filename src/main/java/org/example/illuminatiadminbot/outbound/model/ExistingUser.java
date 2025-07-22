package org.example.illuminatiadminbot.outbound.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ExistingUser {
    private Long telegramId;
    private String nickname;
}
