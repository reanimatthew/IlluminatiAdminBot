package org.example.illuminatiadminbot.outbound.repository;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    public String uploadFileToMinio(String uploadDetails, String fileName, InputStream uploadFile) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        String base = FilenameUtils.getBaseName(fileName);
        String ext  = FilenameUtils.getExtension(fileName);
        String suffix = ext.isEmpty() ? "" : "." + ext;
        String objectName = uploadDetails.toLowerCase(Locale.ROOT) + "/" + base + "_" + timestamp + suffix;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(objectName)
                            .stream(uploadFile, -1, 10 * 1024 * 1024)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return objectName;
    }
}
