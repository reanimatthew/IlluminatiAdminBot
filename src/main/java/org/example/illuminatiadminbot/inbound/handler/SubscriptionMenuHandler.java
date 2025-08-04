package org.example.illuminatiadminbot.inbound.handler;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.AdminTelegramBot;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.menu.Subscription;
import org.example.illuminatiadminbot.inbound.model.EventType;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@RequiredArgsConstructor
public class SubscriptionMenuHandler implements Handler {
    private final MenuBuilder menuBuilder;

    @Override
    public boolean supports(MenuState menuState, EventType eventType) {
        return menuState == MenuState.SUBSCRIPTION_MENU && eventType == EventType.CALLBACK;
    }

    @Override
    public HandlerResult handle(Update update, AdminTelegramBot.ConversationContext conversationContext) {

        String data = update.getCallbackQuery().getData();
        if ("BACK-TO-MAIN".equals(data)) {
            String text = "Выберите действие:";
            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getMain(update)
            );

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.MAIN_MENU)
                    .contextUpdater(context -> {
                        conversationContext.getSubscriptionDetails().set(0, "");
                    })
                    .build();
        }


        if (Subscription.contains(data)) {
            String text = "Подписка: " + conversationContext.getSubscriptionDetails().get(0) + "\nВыберите срок подписки:";

            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getDuration(update)
            );

            return  HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.DURATION_MENU)
                    .contextUpdater(context -> {
                        conversationContext.getSubscriptionDetails().set(0, data);
                    })
                    .build();
        }

        return HandlerResult.builder()
                .actions(List.of())
                .nextMenuState(MenuState.SUBSCRIPTION_MENU)
                .contextUpdater(_ -> {})
                .build();
    }
}
