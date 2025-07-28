package org.example.illuminatiadminbot.mapper;

import it.tdlight.jni.TdApi;
import org.example.illuminatiadminbot.inbound.model.TelegramUserStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatMemberStatusMapper {

    public TelegramUserStatus simplifyStatus(TdApi.ChatMemberStatus telegramStatus) {
        if (telegramStatus instanceof TdApi.ChatMemberStatusCreator) {
            return TelegramUserStatus.CREATOR;
        }
        if (telegramStatus instanceof TdApi.ChatMemberStatusAdministrator) {
            return TelegramUserStatus.ADMINISTRATOR;
        }

        if (telegramStatus instanceof TdApi.ChatMemberStatusMember) {
            return TelegramUserStatus.MEMBER;
        }

        if (telegramStatus instanceof TdApi.ChatMemberStatusRestricted) {
            return TelegramUserStatus.RESTRICTED;
        }

        if (telegramStatus instanceof TdApi.ChatMemberStatusBanned) {
            return TelegramUserStatus.BANNED;
        }

        if (telegramStatus instanceof TdApi.ChatMemberStatusLeft) {
            return TelegramUserStatus.LEFT;
        }

        return TelegramUserStatus.UNKNOWN;
    }

}
