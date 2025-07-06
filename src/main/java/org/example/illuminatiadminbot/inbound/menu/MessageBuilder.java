package org.example.illuminatiadminbot.inbound.menu;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Service
public class MessageBuilder {

    public SendMessage createMessage(Update update, InlineKeyboardMarkup markup, String text) {

        String chatId = update.hasMessage()
                ? update.getMessage().getChatId().toString()
                : update.getCallbackQuery().getMessage().getChatId().toString();

        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markup)
                .build();
    }

    public EditMessageText editMessage (Update update, Integer messageId , InlineKeyboardMarkup markup, String text) {

        String chatId = update.hasMessage()
                ? update.getMessage().getChatId().toString()
                : update.getCallbackQuery().getMessage().getChatId().toString();

        return EditMessageText.builder()
                .chatId(chatId)
                .text(text)
                .messageId(messageId)
                .replyMarkup(markup)
                .build();
    }


}
