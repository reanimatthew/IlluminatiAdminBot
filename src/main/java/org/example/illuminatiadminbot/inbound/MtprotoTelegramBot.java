package org.example.illuminatiadminbot.inbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class MtprotoTelegramBot {

    private String apiId;
    private String apiHash;
    private String dbDir;
    private String encKey;
}


