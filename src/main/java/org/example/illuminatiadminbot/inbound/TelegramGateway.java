package org.example.illuminatiadminbot.inbound;

import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;

@Slf4j
@Component
public class TelegramGateway {
    private final TelegramClient telegramClient;

    @Value("${supergroup.id}")
    private Long supergroupId;

    public TelegramGateway(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public <R extends Serializable> R safeExecute(BotApiMethod<R> apiMethod) {
        try {
            return telegramClient.execute(apiMethod);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

//    public Message safeExecute(SendMessage menuMessage) {
//        Message message;
//        try {
//            message = telegramClient.execute(menuMessage);
//        } catch (TelegramApiException e) {
//            throw new RuntimeException(e);
//        }
//        return message;
//    }
//
//    public void safeExecute(EditMessageText editMessage) {
//        try {
//            telegramClient.execute(editMessage);
//        } catch (TelegramApiException e) {
//            throw new RuntimeException(e);
//        }
//    }

    public void banUser(GroupUser groupUser) {
        BanChatMember banChatMember = BanChatMember.builder()
                .chatId("-100" + supergroupId)
                .userId(groupUser.getTelegramId())
                .build();
        safeExecute(banChatMember);
        UnbanChatMember unbanChatMember = UnbanChatMember.builder()
                .chatId("-100" + supergroupId)
                .userId(groupUser.getTelegramId())
                .build();
        // это нужно просто для выбрасывания из группы, а не длительной блокировки
        safeExecute(unbanChatMember);
    }

}
