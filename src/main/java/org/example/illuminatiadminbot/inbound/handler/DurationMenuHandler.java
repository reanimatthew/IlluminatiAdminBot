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
        return menuState == MenuState.DURATION_MENU && (eventType == EventType.CALLBACK || eventType == EventType.TEXT);
    }

    @Override
    public HandlerResult handle(Update update, ConversationContext conversationContext) {
        String data = update.hasCallbackQuery()
                ? update.getCallbackQuery().getData()
                : null;

        String text = update.hasMessage() && update.getMessage().hasText()
                ? update.getMessage().getText()
                : null;

        if ("BACK-TO-SUBSCRIPTION".equals(data)) {
            String message = "Выберите подписку";
            OutboundAction outboundAction = OutboundAction.editMessage(
                    message,
                    menuBuilder.getSubscription(update)
            );

            return HandlerResult.builder()
                    .actions(List.of(outboundAction))
                    .nextMenuState(MenuState.SUBSCRIPTION_MENU)
                    .contextUpdater(ctx -> {
                        ctx.getSubscriptionDetails().set(1, "");
                        ctx.getSubscriptionDetails().set(2, "");
                        ctx.getSubscriptionDetails().set(3, "");
                    })
                    .build();
        }

        Set<String> duration = Set.of("1M", "3M", "6M", "12M");
        LocalDate now = LocalDate.now();


        Optional<LocalDate> parsed = dataParser.parse(text);
        boolean isDuration = (data != null && duration.contains(data));
        boolean isDate = parsed.isPresent();

        if (!isDuration && !isDate) {
            return HandlerResult.builder()
                    .actions(List.of())
                    .nextMenuState(MenuState.DURATION_MENU)
                    .contextUpdater(context -> {
                    })
                    .build();
        }

        String monthString;
        LocalDate existedDate = null;

        if (isDuration) {
            monthString = data;
        } else {
            existedDate = parsed.get();

            if (existedDate.isBefore(now)) {

                OutboundAction errorAction = OutboundAction.editMessage(
                        "Введена дата раньше сегодняшнего дня, введите заново",
                        menuBuilder.getDuration(update)
                );

                return HandlerResult.builder()
                        .actions(List.of(errorAction))
                        .nextMenuState(MenuState.DURATION_MENU)
                        .contextUpdater(ctx -> {
                            ctx.getSubscriptionDetails().set(1, "");
                            ctx.getSubscriptionDetails().set(2, "");
                            ctx.getSubscriptionDetails().set(3, "");
                        })
                        .build();
            }

            long months = ChronoUnit.MONTHS.between(
                    now.withDayOfMonth(1),
                    existedDate.withDayOfMonth(1));
            monthString = months + "M";

        }

        String message = "Подписка: " + conversationContext.getSubscriptionDetails().get(0) +
                ", на срок: " + monthString + "." +
                "\nВведите никнейм или телефон:";

        OutboundAction outboundAction = OutboundAction.editMessage(
                message,
                menuBuilder.getNickOrPhone(update)
        );

        String dateToString = existedDate != null
                ? existedDate.toString()
                : "";

        return HandlerResult.builder()
                .actions(List.of(outboundAction))
                .contextUpdater(ctx -> {
                    ctx.getSubscriptionDetails().set(1, monthString);
                    ctx.getSubscriptionDetails().set(3, dateToString);
                })
                .nextMenuState(MenuState.NICK_OR_PHONE_MENU)
                .build();
    }
}
