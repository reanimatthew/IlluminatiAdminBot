package org.example.illuminatiadminbot.outbound.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@Builder
@Data
public class MinioFileNameDetail {
    String fileName;
    String minioFileName;
}
