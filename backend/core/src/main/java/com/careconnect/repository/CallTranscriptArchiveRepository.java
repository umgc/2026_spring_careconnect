package com.careconnect.repository;

import com.careconnect.model.CallTranscriptArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CallTranscriptArchiveRepository extends JpaRepository<CallTranscriptArchive, Long> {
    Optional<CallTranscriptArchive> findTopByCallIdOrderByArchivedAtDesc(String callId);

    boolean existsByCallId(String callId);

    java.util.List<CallTranscriptArchive> findByCallIdOrderByArchivedAtDesc(String callId);

    long deleteByCallId(String callId);
}
