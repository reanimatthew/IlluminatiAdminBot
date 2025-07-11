package org.example.illuminatiadminbot.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mtproto")
@Data
public class MtprotoProperties {
    private String apiId;
    private String apiHash;
    private String dbDir;
    private String encKey;
}
