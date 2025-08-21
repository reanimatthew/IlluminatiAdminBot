package org.example.illuminatiadminbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // НОВЫЙ ИМПОРТ
import io.minio.MinioClient;
import it.tdlight.client.SimpleTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@SpringBootTest
class IlluminatiAdminBotApplicationTests {

    @MockitoBean SimpleTelegramClient simpleTelegramClient;
    @MockitoBean TelegramClient telegramClient;
    @MockitoBean MinioClient minioClient;

    @Test
    void contextLoads() {}
}