package org.example.illuminatiadminbot.inbound.handler;

import org.example.illuminatiadminbot.inbound.ConversationContext;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.EventType;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface Handler {
    boolean supports(MenuState menuState, EventType eventType);

    HandlerResult handle(Update update, ConversationContext conversationContext);
}
