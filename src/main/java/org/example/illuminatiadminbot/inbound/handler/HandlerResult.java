package org.example.illuminatiadminbot.inbound.handler;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.AdminTelegramBot;
import org.example.illuminatiadminbot.inbound.menu.MenuState;

import java.util.function.Consumer;

@RequiredArgsConstructor
@Data
public class HandlerResult {
    private final List<OutboundAction> actions;
    private final MenuState nextMenuState;
    private final Consumer<AdminTelegramBot.ConversationContext> contextUpdater;
}
