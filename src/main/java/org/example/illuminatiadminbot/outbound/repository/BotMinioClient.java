package org.example.illuminatiadminbot.outbound.repository;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@Slf4j
public class BotMinioClient {

    private final MinioClient minioClient;
    private static final String BUCKET_NAME = "illuminati-admin-bot";


    public BotMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init() {
        try {
            Boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(BUCKET_NAME)
                            .build()

            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(BUCKET_NAME)
                                .build()
                );
                log.info("Успешно создан бакет: {}", BUCKET_NAME);
            } else {
                log.info("Бакет {} уже существует", BUCKET_NAME);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось проверить/создать бакет " + BUCKET_NAME, e);
        }
    }

    public String uploadText(ArrayList<String> uploadDetails) {
        return null;
    }

    public String uploadAudio(ArrayList<String> uploadDetails) {
        return null;
    }

    public String uploadVideo(ArrayList<String> uploadDetails) {
        return null;
    }

//    public Boolean uploadVideo() {
//        minioClient.putObject()
//    }
}
