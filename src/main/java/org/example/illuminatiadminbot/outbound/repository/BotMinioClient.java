package org.example.illuminatiadminbot.outbound.repository;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.example.illuminatiadminbot.inbound.model.SupergroupTopic;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.example.illuminatiadminbot.outbound.model.MinioFileNameDetail;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.InputFile;

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

    public MinioFileNameDetail uploadFileToMinio(UploadDetails uploadDetails, InputStream uploadFile) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        String base = FilenameUtils.getBaseName(uploadDetails.getFileName());
        String ext = FilenameUtils.getExtension(uploadDetails.getFileName());
        String suffix = ext.isEmpty() ? "" : "." + ext;
        String objectName = uploadDetails.getFileType().toLowerCase(Locale.ROOT) + "/" + base + "_" + timestamp + suffix;
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

        return new MinioFileNameDetail(objectName, uploadDetails.getFileName());
    }

    public InputFile getInputFileFromMinio(MinioFileNameDetail detail) {

        log.info("MinioFilePath: {}", detail.getMinioFilePath());
        log.info("MinioFileName: {}", detail.getOriginalFileName());

        InputStream inputStream;
        try {
            inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(detail.getMinioFilePath())
                            .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new InputFile(inputStream, detail.getOriginalFileName());
    }
}
