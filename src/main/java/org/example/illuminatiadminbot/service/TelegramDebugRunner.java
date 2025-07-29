package org.example.illuminatiadminbot.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TelegramError;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutionException;

// TODO этот класс использовался для теста - удалить

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
            @Value("${chat.id}") long chatId,
            SimpleTelegramClient simpleTelegramClient) {
        this.chatId = chatId;
        this.simpleTelegramClient = simpleTelegramClient;
    }

    @PostConstruct
    public void debugGetChatMember() {
        log.info("DEBUG: пытаемся получить ChatMember для userId={} в чате {}", userIdNotInChat, chatId);

        try {
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

            //  ✅ Правильный тип исключения
            if (cause instanceof TelegramError tgErr) {
                TdApi.Error err = tgErr.getError();     // сам объект TdApi.Error
                log.warn("TdApi.Error: code={} message='{}'",
                        err.code, err.message);        // :contentReference[oaicite:1]{index=1}
            } else {
                log.error("ExecutionException при GetChatMember", ee);
            }
        }
    }
}

