package org.example.illuminatiadminbot.inbound.handler;

import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.AdminTelegramBot;
import org.example.illuminatiadminbot.inbound.ConversationContext;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.EventType;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

import static org.example.illuminatiadminbot.inbound.AdminTelegramBot.*;

@RequiredArgsConstructor
public class NickOrPhoneHandler implements Handler {
    private final MenuBuilder menuBuilder;

    @Override
    public boolean supports(MenuState menuState, EventType eventType) {
        return menuState == MenuState.NICK_OR_PHONE_MENU && eventType == EventType.CALLBACK;
    }

    @Override
    public HandlerResult handle(Update update, ConversationContext conversationContext) {

        String data = update.getCallbackQuery().getData();
        if ("BACK-TO-DURATION".equals(data)) {
            String text = "Подписка: " + conversationContext.getSubscriptionDetails().get(0) + "\nВыберите срок подписки:";
            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getDuration(update)
            );

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.DURATION_MENU)
                    .contextUpdater(ctx -> ctx.getSubscriptionDetails().set(2, ""))
                    .build();
        }

        List<String> nickOrPhoneChoice = List.of("BY-PHONE", "BY-NICK");
        if (nickOrPhoneChoice.contains(data)) {
            String text = "BY-PHONE".equals(data) ? "Введите телефон подписчика" : "Введите никнейм подписчика";
            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getSubscriberId(update)
            );

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.SUBSCRIBER_ID_MENU)
                    .contextUpdater(ctx -> ctx.getSubscriptionDetails().set(2, data))
                    .build();
        }

        return HandlerResult.builder()
                .actions(List.of())
                .nextMenuState(MenuState.NICK_OR_PHONE_MENU)
                .contextUpdater(_ -> {
                })
                .build();
    }
}

