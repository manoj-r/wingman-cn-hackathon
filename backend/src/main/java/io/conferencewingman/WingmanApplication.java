package io.conferencewingman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.conferencewingman.ai.EmbeddingProperties;
import io.conferencewingman.ai.LlmProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({EmbeddingProperties.class, LlmProperties.class})
public class WingmanApplication {
    public static void main(String[] args) {
        SpringApplication.run(WingmanApplication.class, args);
    }
}
