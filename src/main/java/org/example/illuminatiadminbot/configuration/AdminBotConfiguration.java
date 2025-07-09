package org.example.illuminatiadminbot.configuration;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@EnableConfigurationProperties({MinioProperties.class})
public class AdminBotConfiguration {

    private final MinioProperties minioProperties;

    public AdminBotConfiguration(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Bean
    TelegramClient telegramClient(@Value("${telegram.bot.token}")  String token) {
        return new OkHttpTelegramClient(token);
    }

    @Bean
    MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getUser(), minioProperties.getPassword())
                .build();
    }

}
