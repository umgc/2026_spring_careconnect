package com.careconnect.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "feature_telemetry_event")
public class FeatureTelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 128)
    private String eventName;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "span_id", length = 32)
    private String spanId;

    @Convert(disableConversion = true)
    @Column(name = "device_info", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> deviceInfo;

    @Convert(disableConversion = true)
    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    @PrePersist
    void onCreate() {
        if (eventTime == null) {
            eventTime = OffsetDateTime.now();
        }
    }
}
