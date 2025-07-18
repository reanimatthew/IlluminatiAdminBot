package org.example.illuminatiadminbot.outbound.repository;

import io.minio.*;
import io.minio.errors.*;
import it.tdlight.jni.TdApi;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.example.illuminatiadminbot.outbound.model.MinioFileDetail;
import org.example.illuminatiadminbot.outbound.model.MinioFileNameDetail;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

    public MinioFileNameDetail uploadFileToMinio(String uploadDetails, String fileName, InputStream uploadFile) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        String base = FilenameUtils.getBaseName(fileName);
        String ext = FilenameUtils.getExtension(fileName);
        String suffix = ext.isEmpty() ? "" : "." + ext;
        String newFileName = base + "_" + timestamp + suffix;
        String objectName = uploadDetails.toLowerCase(Locale.ROOT) + "/" + newFileName;
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

        return new MinioFileNameDetail(objectName, newFileName);
    }

    public InputFile getInputFileFromMinio(MinioFileNameDetail detail) {

        InputFile inputFile;
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(detail.getMinioFileName())
                        .build())
        ) {
            inputFile = new InputFile(inputStream, detail.getFileName());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return inputFile;
    }
}
