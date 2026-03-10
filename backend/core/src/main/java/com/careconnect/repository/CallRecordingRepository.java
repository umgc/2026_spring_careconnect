package com.careconnect.repository;

import com.careconnect.model.CallRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallRecordingRepository extends JpaRepository<CallRecording, Long> {

    Optional<CallRecording> findTopByCallIdOrderByStartedAtDesc(String callId);

    List<CallRecording> findByCallIdOrderByStartedAtDesc(String callId);

    List<CallRecording> findByInitiatedByUserIdOrderByStartedAtDesc(Long userId);

    List<CallRecording> findByStatusOrderByStartedAtDesc(String status);

    List<CallRecording> findTop100ByStatusOrderByStartedAtDesc(String status);

    long deleteByCallId(String callId);
}
