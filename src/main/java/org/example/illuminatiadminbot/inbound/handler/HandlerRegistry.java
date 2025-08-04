package org.example.illuminatiadminbot.inbound.handler;

import org.example.illuminatiadminbot.inbound.menu.MenuState;
import org.example.illuminatiadminbot.inbound.model.EventType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class HandlerRegistry {

    private final List<Handler> handlers = new ArrayList<>();

    public void register(Handler handler) {
        handlers.add(handler);
    }

    public Optional<Handler> resolve(MenuState menuState, EventType eventType) {
        return handlers.stream()
                .filter(handler -> handler.supports(menuState, eventType))
                .findFirst();
    }
}
