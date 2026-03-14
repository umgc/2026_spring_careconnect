package com.careconnect.controller.dev;

import com.careconnect.model.TelemetryEvent;
import com.careconnect.service.TelemetryService;
import com.careconnect.service.TelemetryToggleService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/dev/telemetry")
@Profile("dev")
public class DevTelemetryController {

    private final TelemetryService telemetry;
    private final TelemetryToggleService toggle;

    public DevTelemetryController(TelemetryService telemetry, TelemetryToggleService toggle) {
        this.telemetry = telemetry;
        this.toggle = toggle;
    }

    @PostMapping
    public ResponseEntity<?> emit(@RequestBody Map<String, Object> body) {
        // Telemetry OFF means: do not record anything
        if (!toggle.isEnabled()) {
            return ResponseEntity.noContent().build(); // 204
        }

        TelemetryEvent e = new TelemetryEvent();
        e.setEventName(asString(body.getOrDefault("eventName", "dev_emit")));
        e.setEventTime(OffsetDateTime.now(java.time.Clock.systemUTC()));

        e.setSessionId(asString(body.get("sessionId")));
        e.setTraceId(asString(body.get("traceId")));
        e.setSpanId(asString(body.get("spanId")));

        e.setDetails(asMap(body.get("details")));
        e.setDeviceInfo(asMap(body.get("deviceInfo")));

        return ResponseEntity.ok(telemetry.record(e));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> recent(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(telemetry.recent(limit));
    }

    @GetMapping("/enabled")
    public ResponseEntity<?> enabled() {
        return ResponseEntity.ok(Map.of("enabled", toggle.isEnabled()));
    }

    @PutMapping("/enabled")
    public ResponseEntity<?> setEnabled(@RequestParam boolean enabled) {
        return ResponseEntity.ok(Map.of("enabled", toggle.setEnabled(enabled)));
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o == null) return null;
        if (o instanceof Map) return (Map<String, Object>) o;
        return null;
    }
}