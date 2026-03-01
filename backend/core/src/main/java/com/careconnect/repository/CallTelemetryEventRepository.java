package com.careconnect.repository;

import com.careconnect.model.CallTelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallTelemetryEventRepository extends JpaRepository<CallTelemetryEvent, Long> {
    List<CallTelemetryEvent> findByCallIdOrderByOccurredAtDesc(String callId);

    List<CallTelemetryEvent> findTop500ByActorUserIdOrTargetUserIdOrderByOccurredAtDesc(Long actorUserId, Long targetUserId);
}
