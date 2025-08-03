package org.example.illuminatiadminbot.inbound;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.example.illuminatiadminbot.outbound.model.GroupUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.groupadministration.BanChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.RestrictChatMember;
import org.telegram.telegrambots.meta.api.methods.groupadministration.UnbanChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramGateway {
    private final TelegramClient telegramClient;
    private final ChatPermissionsFabric chatPermissionsFabric;

    @Value("${supergroup.id}")
    private Long supergroupId;

    @Value("${chat.id}")
    private Long superGroupChatId;

    public <R extends Serializable> R safeExecute(BotApiMethod<R> apiMethod) {
        try {
            return telegramClient.execute(apiMethod);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void banUser(GroupUser groupUser) {
        BanChatMember banChatMember = BanChatMember.builder()
                .chatId(superGroupChatId)
                .userId(groupUser.getTelegramId())
                .build();
        safeExecute(banChatMember);
        UnbanChatMember unbanChatMember = UnbanChatMember.builder()
                .chatId(superGroupChatId)
                .userId(groupUser.getTelegramId())
                .build();
        // это нужно просто для выбрасывания из группы, а не длительной блокировки
        safeExecute(unbanChatMember);
    }

    public void clearBannedStatus(GroupUser groupUser) {
        UnbanChatMember unbanChatMember = UnbanChatMember.builder()
                .chatId(superGroupChatId)
                .userId(groupUser.getTelegramId())
                .build();
        safeExecute(unbanChatMember);
    }

    public void restoreRestrictedUser(GroupUser groupUser) {
        RestrictChatMember restrictChatMember = RestrictChatMember.builder()
                .chatId(superGroupChatId)
                .userId(groupUser.getTelegramId())
                .permissions(chatPermissionsFabric.getStandard())
                .build();
        safeExecute(restrictChatMember);
    }
}

