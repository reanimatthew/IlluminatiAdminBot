package org.example.illuminatiadminbot.inbound.handler;

import org.example.illuminatiadminbot.inbound.ConversationContext;
import org.example.illuminatiadminbot.inbound.TelegramGateway;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.Collections;

public interface OutboundAction {
    void execute(TelegramGateway telegramGateway, Update update, ConversationContext conversationContext);

    static OutboundAction sendFinalMessage(String text) {
        return (telegramGateway, update, conversationContext) -> {
            long chatId = update.hasMessage()
                    ? update.getMessage().getChatId()
                    : update.getCallbackQuery().getMessage().getChatId();

            EditMessageText editMessageText = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(update.getMessage().getMessageId())
                    .text(text)
                    .replyMarkup(new InlineKeyboardMarkup(Collections.emptyList()))
                    .build();

            telegramGateway.safeExecute(editMessageText);
        };
    }

    static OutboundAction editMessage(String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        return (telegramGateway, update, conversationContext) -> {
            long chatId = update.hasMessage()
                    ? update.getMessage().getChatId()
                    : update.getCallbackQuery().getMessage().getChatId();
            int messageId = conversationContext.getLastMessageId();

            EditMessageText editMessageText = EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(inlineKeyboardMarkup)
                    .build();

            telegramGateway.safeExecute(editMessageText);
        };
    }
}
