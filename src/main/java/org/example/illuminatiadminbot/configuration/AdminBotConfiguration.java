package org.example.illuminatiadminbot.configuration;

import io.minio.MinioClient;
import it.tdlight.client.*;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import it.tdlight.*;

import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties({MinioProperties.class, MtprotoProperties.class})
public class AdminBotConfiguration {

    private final MinioProperties minioProperties;
    private final MtprotoProperties mtprotoProperties;

    public AdminBotConfiguration(MinioProperties minioProperties, MtprotoProperties mtprotoProperties) {
        this.minioProperties = minioProperties;
        this.mtprotoProperties = mtprotoProperties;
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

    @Bean(destroyMethod = "close")
    public SimpleTelegramClient simpleTelegramClient(MtprotoProperties mtprotoProperties) throws UnsupportedNativeLibraryException {
        Init.init();
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());

        APIToken apiToken = new APIToken(
                Integer.parseInt(mtprotoProperties.getApiId()),
                mtprotoProperties.getApiHash()
        );

        TDLibSettings tdLibSettings = TDLibSettings.create(apiToken);
        tdLibSettings.setDatabaseDirectoryPath(Paths.get(mtprotoProperties.getDbDir()));
        tdLibSettings.setDownloadedFilesDirectoryPath(Paths.get(mtprotoProperties.getDbDir(), "downloads"));
        tdLibSettings.setUseTestDatacenter(false);

        SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory();
        SimpleTelegramClientBuilder builder = factory.builder(tdLibSettings);
        return builder.build(AuthenticationSupplier.consoleLogin());
    }


}
