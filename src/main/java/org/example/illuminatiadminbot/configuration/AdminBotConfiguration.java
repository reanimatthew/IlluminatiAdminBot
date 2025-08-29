package org.example.illuminatiadminbot.configuration;

import io.minio.MinioClient;
import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.example.illuminatiadminbot.inbound.DataParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties({MinioProperties.class, MtprotoProperties.class, TopicProperties.class})
public class AdminBotConfiguration {

    private final MinioProperties minioProperties;
    private final MtprotoProperties mtprotoProperties;

    public AdminBotConfiguration(MinioProperties minioProperties, MtprotoProperties mtprotoProperties) {
        this.minioProperties = minioProperties;
        this.mtprotoProperties = mtprotoProperties;
    }

    @Bean
    TelegramClient telegramClient(@Value("${telegram.bot.token}") String token) {
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
        SimpleTelegramClient client = builder.build(AuthenticationSupplier.consoleLogin());

        CountDownLatch latch = new CountDownLatch(1);
        client.addUpdateHandler(
                TdApi.UpdateAuthorizationState.class,
                updateAuthorizationState -> {
                    TdApi.AuthorizationState state = updateAuthorizationState.authorizationState;
                    if (state.getConstructor() == TdApi.AuthorizationStateReady.CONSTRUCTOR) {
                        latch.countDown();
                    }
                }
        );

        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout waiting for TDLib authorization");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return client;
    }

//    @Bean
//    public DataParser dataParser() {
//        return new DataParser();
//    }

}
