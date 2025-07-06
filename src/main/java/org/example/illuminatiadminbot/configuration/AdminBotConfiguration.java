package org.example.illuminatiadminbot.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class AdminBotConfiguration {

    @Bean
    TelegramClient telegramClient(@Value("${telegram.bot.token}")  String token) {
        return new OkHttpTelegramClient(token);
    }

}
