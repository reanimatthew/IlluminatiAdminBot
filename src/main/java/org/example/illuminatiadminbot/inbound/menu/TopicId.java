package org.example.illuminatiadminbot.inbound.menu;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record TopicId(
        @Value("${topic.strength.id}")
        Integer strength,

        @Value("${topic.perception.id}")
        Integer perception,

        @Value("${topic.endurance.id}")
        Integer endurance,

        @Value("${topic.charisma.id}")
        Integer charisma
) {}
