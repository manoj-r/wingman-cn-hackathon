package io.conferencewingman;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OperationsController {
    private final HealthEndpoint health;
    private final String version;

    OperationsController(HealthEndpoint health, @Value("${wingman.version}") String version) {
        this.health = health;
        this.version = version;
    }

    @GetMapping("/healthz")
    ResponseEntity<String> liveness() {
        return status("liveness");
    }

    @GetMapping("/readyz")
    ResponseEntity<String> readiness() {
        return status("readiness");
    }

    private ResponseEntity<String> status(String group) {
        var result = health.healthForPath(group);
        boolean ready = result != null && Status.UP.equals(result.getStatus());
        return ResponseEntity.status(ready ? 200 : 503).body(ready ? "ok" : "unavailable");
    }

    @GetMapping("/api/v1/version")
    Map<String, String> version() {
        return Map.of("version", version, "service", "conference-wingman-backend");
    }
}
