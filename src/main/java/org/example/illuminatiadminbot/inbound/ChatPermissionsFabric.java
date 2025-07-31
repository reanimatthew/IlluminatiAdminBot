package org.example.illuminatiadminbot.inbound;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.*;

@Component
public class ChatPermissionsFabric {

    public ChatPermissions getStandard() {
        ChatPermissions chatPermissions = ChatPermissions.builder()
                .canSendMessages(true)
                .canSendDocuments(true)
                .canSendPhotos(true)
                .canSendVideos(true)
                .canSendAudios(true)
                .canAddWebPagePreviews(true)
                .build();
        return chatPermissions;
    }
}
