package org.example.illuminatiadminbot.inbound.handler;

import lombok.RequiredArgsConstructor;
import org.example.illuminatiadminbot.inbound.ConversationContext;
import org.example.illuminatiadminbot.inbound.DataParser;
import org.example.illuminatiadminbot.inbound.menu.MenuBuilder;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.EventType;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
public class DurationMenuHandler implements Handler {
    private final MenuBuilder menuBuilder;
    private final DataParser dataParser;

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

        Set<String> duration = Set.of("1M", "3M", "6M", "12M");
        LocalDate now = LocalDate.now();

        Optional<LocalDate> parsed =  dataParser.parse(data);
        boolean isDuration = duration.contains(data);
        boolean isDate = parsed.isPresent();

        String month;
        LocalDate date = null;
        String text;
        if (duration.contains(data) || dataParser.isDate(data)) {
            if (duration.contains(data)) {
                month = data;
            } else {
                date = dataParser.parse(data).orElse(LocalDate.now());
                month = ChronoUnit.MONTHS.between(date, LocalDate.now()) + "M";
            }

            text = "Подписка: " + conversationContext.getSubscriptionDetails().get(0) +
                    ", на срок: " + month + "." +
                    "\nВведите никнейм или телефон:";

            OutboundAction outboundAction = OutboundAction.editMessage(
                    text,
                    menuBuilder.getNickOrPhone(update)
            );

            String dateToString = date != null
                    ? date.toString()
                    : null;

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.NICK_OR_PHONE_MENU)
                    .contextUpdater(ctx -> {
                        ctx.getSubscriptionDetails().set(1, month);
                        ctx.getSubscriptionDetails().set(4, dateToString);
                    })
                    .nextMenuState(MenuState.NICK_OR_PHONE_MENU)
                    .build();
        }

        // если что-то другое прислали - не реагируем
        return HandlerResult.builder()
                .actions(List.of())
                .nextMenuState(MenuState.DURATION_MENU)
                .contextUpdater(context -> {
                })
                .build();
    }
}
