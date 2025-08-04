package org.example.illuminatiadminbot.inbound.handler;

import org.example.illuminatiadminbot.inbound.AdminTelegramBot;
import org.example.illuminatiadminbot.inbound.TelegramGateway;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface OutboundAction {
    void execute(TelegramGateway telegramGateway, Update update, AdminTelegramBot.ConversationContext conversationContext);

    static OutboundAction sendMessage(String text, InlineKeyboardMarkup inlineKeyboardMarkup) {
        return (telegramGateway, update, conversationContext) -> {
            long chatId = update.hasMessage()
                    ? update.getMessage().getChatId()
                    : update.getCallbackQuery().getMessage().getChatId();

            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(inlineKeyboardMarkup)
                    .build();

            telegramGateway.safeExecute(sendMessage);
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
