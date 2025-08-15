package org.example.illuminatiadminbot.inbound;

import lombok.Data;
import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.UploadDetails;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Data
public class ConversationContext {
    private final ArrayList<String> subscriptionDetails = new ArrayList<>(List.of("", "", ""));
    private UploadDetails uploadDetails = new UploadDetails();
    private MenuState menuState = MenuState.MAIN_MENU;
    private Integer lastMessageId = null;
    private Long chatId = null;

    public void clearContext() {
        subscriptionDetails.set(0, "");
        subscriptionDetails.set(1, "");
        subscriptionDetails.set(2, "");
        uploadDetails.clearUploadDetails();
        menuState = MenuState.MAIN_MENU;
        lastMessageId = null;
        chatId = null;
    }
}
