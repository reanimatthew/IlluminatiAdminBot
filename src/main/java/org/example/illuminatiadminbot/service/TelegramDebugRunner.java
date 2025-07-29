package org.example.illuminatiadminbot.service;

import jakarta.annotation.PostConstruct;
import org.drinkless.tdlight.ClientManager;
import org.drinkless.tdlight.SimpleTelegramClient;
import org.drinkless.tdlight.TdApi;
import org.drinkless.tdlight.TdApi.Exception as TdApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class TelegramDebugRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramDebugRunner.class);

    /**
     * ID пользователя, которого точно нет в вашей супергруппе.
     * Замените на реальный ID.
     */
    private final long userIdNotInChat = 123456789L;

    private final long chatId;
    private final SimpleTelegramClient simpleTelegramClient;

    public TelegramDebugRunner(
            @Value("${telegram.chat-id}") long chatId,
            SimpleTelegramClient simpleTelegramClient) {
        this.chatId = chatId;
        this.simpleTelegramClient = simpleTelegramClient;
    }

    @PostConstruct
    public void debugGetChatMember() {
        log.info("DEBUG: пытаемся получить ChatMember для userId={} в чате {}", userIdNotInChat, chatId);

        try {
            // Прямой вызов TdLib
            TdApi.ChatMember chatMember = simpleTelegramClient
                    .send(new TdApi.GetChatMember(
                            chatId,
                            new TdApi.MessageSenderUser(userIdNotInChat)))
                    .get();

            log.info("GetChatMember вернул: {}", chatMember);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while calling GetChatMember", ie);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();

            // TdLib бросает обёртку TdApiException, внутри которой лежит TdApi.Error
            if (cause instanceof TdApiException tdEx) {
                TdApi.Error err = tdEx.error;  // Получаем тело ошибки
                log.warn("TdApi.Error: code={} message='{}'", err.code, err.message);

            } else {
                log.error("ExecutionException при GetChatMember", ee);
            }
        }
    }
}

