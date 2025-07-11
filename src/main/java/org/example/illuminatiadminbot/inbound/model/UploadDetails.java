package org.example.illuminatiadminbot.inbound.model;

import lombok.Data;

@Data
public class UploadDetails {

    private String fileType;
    private String fileName;
    private String filePath;

    public void clearUploadDetails() {
        this.fileType = "";
        this.fileName = "";
        this.filePath = "";
    }

    public void clearFileType() {
        this.fileType = "";
    }
}
