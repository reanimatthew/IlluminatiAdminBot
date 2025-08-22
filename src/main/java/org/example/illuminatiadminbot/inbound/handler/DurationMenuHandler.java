package org.example.illuminatiadminbot.inbound.handler;

import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.ConversationContext;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.EventType;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@RequiredArgsConstructor
public class DurationMenuHandler implements Handler {
    private final MenuBuilder menuBuilder;

    @Override
    public boolean supports(MenuState menuState, EventType eventType) {
        return menuState == MenuState.DURATION_MENU && eventType == EventType.CALLBACK;
    }

    @Override
    public HandlerResult handle(Update update, ConversationContext conversationContext) {
        String data = update.getCallbackQuery().getData();

        if ("BACK-TO-SUBSCRIPTION".equals(data)) {
            String text = "Выберите подписку";
            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getSubscription(update)
            );

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.SUBSCRIPTION_MENU)
                    .contextUpdater(ctx -> {
                        ctx.getSubscriptionDetails().set(1, "");
                        ctx.getSubscriptionDetails().set(2, "");
                    })
                    .build();
        }

        List<String> duration = List.of("1M", "3M", "6M", "12M");
        if (duration.contains(data)) {
            String text = "Подписка: " + conversationContext.getSubscriptionDetails().get(0) +
                    ", на срок: " + data + "." +
                    "\nВведите никнейм или телефон:";

            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getNickOrPhone(update)
            );

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.NICK_OR_PHONE_MENU)
                    .contextUpdater(ctx -> {
                        ctx.getSubscriptionDetails().set(1, data);
                    })
                    .nextMenuState(MenuState.NICK_OR_PHONE_MENU)
                    .build();
        }

        // если что-то другое прислали - не реагируем
        return HandlerResult.builder()
                .actions(List.of())
                .nextMenuState(MenuState.DURATION_MENU)
                .contextUpdater(context -> {})
                .build();
    }
}
