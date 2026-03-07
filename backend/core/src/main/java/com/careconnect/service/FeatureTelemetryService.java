package com.careconnect.service;

import com.careconnect.model.FeatureTelemetryEvent;
import com.careconnect.repository.FeatureTelemetryEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeatureTelemetryService {

    private final FeatureTelemetryEventRepository repository;
    private final TelemetryToggleService toggle;

    public FeatureTelemetryEvent record(FeatureTelemetryEvent event) {
        if (!toggle.isEnabled()) return event; // no-op when disabled
        return repository.save(event);
    }

    public List<FeatureTelemetryEvent> recent(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));

        List<FeatureTelemetryEvent> results = repository.findTop50ByOrderByEventTimeDesc();

        if (results == null || results.isEmpty()) return Collections.emptyList();
        if (results.size() <= safe) return results;
        return results.subList(0, safe);
    }

    /**
     * Anonymous feature telemetry (no user identifiers).
     * Returns null when telemetry is disabled.
     */
    public FeatureTelemetryEvent recordAnonymous(
            String eventName,
            Map<String, Object> details,
            Map<String, Object> deviceInfo,
            String traceId,
            String spanId
    ) {
        if (!toggle.isEnabled()) return null;

        FeatureTelemetryEvent e = FeatureTelemetryEvent.builder()
                .eventName(eventName)
                .traceId(traceId)
                .spanId(spanId)
                .details(details)
                .deviceInfo(deviceInfo)
                .build();

        return repository.save(e);
    }
}