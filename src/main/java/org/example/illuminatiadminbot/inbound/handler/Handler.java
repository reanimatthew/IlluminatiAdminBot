package org.example.illuminatiadminbot.inbound.handler;

import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.EventType;

public interface Handler {
    boolean supports(MenuState menuState, EventType eventType);
}
