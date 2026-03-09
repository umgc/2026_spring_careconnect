package com.careconnect.repository;

import com.careconnect.model.CallTelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CallTelemetryEventRepository extends JpaRepository<CallTelemetryEvent, Long> {
    List<CallTelemetryEvent> findByCallIdOrderByOccurredAtDesc(String callId);

    List<CallTelemetryEvent> findByCallIdOrderByOccurredAtAsc(String callId);

    List<CallTelemetryEvent> findTop500ByActorUserIdOrTargetUserIdOrderByOccurredAtDesc(Long actorUserId, Long targetUserId);

    List<CallTelemetryEvent> findByActorUserIdOrTargetUserIdOrderByOccurredAtAsc(Long actorUserId, Long targetUserId);

    @Modifying
    @Transactional
    long deleteByCallId(String callId);
}
