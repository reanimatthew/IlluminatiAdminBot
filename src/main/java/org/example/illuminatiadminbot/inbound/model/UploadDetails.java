package org.example.illuminatiadminbot.inbound.model;

import lombok.Data;
import org.telegram.telegrambots.meta.api.objects.InputFile;

@Data
public class UploadDetails {

    // в какой топик должен уйти файл
    private String theme;

    // тип файла - текст, аудио, видео
    private String fileType;

    // изначальное имя файла
    private String fileName;

    // путь к файлу в Minio (objectName)
    private String minioFilePath;

    // название файла, модифицированное для Minio (с Timestamp)
    private String minioFileName;

    // для передачи в телеграм-бот для выгрузки в топик
    private InputFile inputFile;

    public void clearUploadDetails() {
        this.theme = "";
        this.fileType = "";
        this.fileName = "";
        this.minioFilePath = "";
        this.minioFileName = "";
        this.inputFile = null;
    }

    public void clearTheme() {
        this.theme = "";
    }

    public void clearFileType() {
        this.fileType = "";
    }
}
