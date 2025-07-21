package org.example.illuminatiadminbot.configuration;

import lombok.Data;
import org.example.illuminatiadminbot.inbound.model.SupergroupTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "topic")
@Data
public class TopicProperties {
    private Map<String, SupergroupTopic> map = new LinkedHashMap<>();
}
