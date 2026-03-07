package com.careconnect.service;

import com.careconnect.model.TelemetryEvent;
import com.careconnect.repository.TelemetryEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryEventRepository repository;
    private final TelemetryToggleService toggle;

    public TelemetryEvent record(TelemetryEvent event) {
        if (!toggle.isEnabled()) return event; // no-op when disabled
        return repository.save(event);
    }

    public List<TelemetryEvent> recent(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));

        List<TelemetryEvent> results = repository.findTop50ByOrderByEventTimeDesc();

        if (results == null || results.isEmpty()) return Collections.emptyList();
        if (results.size() <= safe) return results;
        return results.subList(0, safe);
    }

    /**
     * Anonymous feature telemetry (no user identifiers).
     * Returns null when telemetry is disabled.
     */
    public TelemetryEvent recordAnonymous(
            String eventName,
            Map<String, Object> details,
            Map<String, Object> deviceInfo,
            String traceId,
            String spanId
    ) {
        if (!toggle.isEnabled()) return null;

        TelemetryEvent e = TelemetryEvent.builder()
                .eventName(eventName)
                .traceId(traceId)
                .spanId(spanId)
                .details(details)
                .deviceInfo(deviceInfo)
                .build();

        return repository.save(e);
    }
}