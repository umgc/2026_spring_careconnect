package com.careconnect.controller.dev;

import com.careconnect.model.FeatureTelemetryEvent;
import com.careconnect.service.FeatureTelemetryService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/dev/telemetry")
@Profile("dev")
public class DevTelemetryController {

    private final FeatureTelemetryService telemetry;

    public DevTelemetryController(FeatureTelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @PostMapping
    public ResponseEntity<FeatureTelemetryEvent> emit(@RequestBody Map<String, Object> body) {
        FeatureTelemetryEvent e = new FeatureTelemetryEvent();
        e.setEventName(asString(body.getOrDefault("eventName", "dev_emit")));
        e.setEventTime(OffsetDateTime.now(java.time.Clock.systemUTC()));

        e.setTraceId(asString(body.get("traceId")));
        e.setSpanId(asString(body.get("spanId")));
        e.setActorUserId(asLong(body.get("actorUserId")));
        e.setPatientId(asLong(body.get("patientId")));

        e.setDetails(asMap(body.get("details")));
        e.setDeviceInfo(asMap(body.get("deviceInfo")));

        return ResponseEntity.ok(telemetry.record(e));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> recent(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(telemetry.recent(limit));
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception ex) { return null; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o == null) return null;
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }
}