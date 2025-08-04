package org.example.illuminatiadminbot.inbound.handler;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.AdminTelegramBot;
import org.example.illuminatiadminbot.inbound.menu.MenuState;

import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Data
@Builder
public class HandlerResult {
    private final List<OutboundAction> actions;
    private final MenuState nextMenuState;
    private final Consumer<AdminTelegramBot.ConversationContext> contextUpdater;

    public void updateContext(AdminTelegramBot.ConversationContext conversationContext) {
        contextUpdater.accept(conversationContext);
    }
}
