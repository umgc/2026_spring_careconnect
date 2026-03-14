package com.careconnect.repository;

import com.careconnect.model.CallTranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallTranscriptSegmentRepository extends JpaRepository<CallTranscriptSegment, Long> {
    List<CallTranscriptSegment> findByCallIdOrderByStartMsAscOccurredAtAsc(String callId);

    long countByCallId(String callId);

    boolean existsByCallIdAndActorUserId(String callId, Long actorUserId);

    boolean existsByCallId(String callId);

    long deleteByCallId(String callId);
}
