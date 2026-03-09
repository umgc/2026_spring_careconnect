package com.careconnect.repository;

import com.careconnect.model.CallSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {
    Optional<CallSummary> findTopByCallIdOrderByGeneratedAtDesc(String callId);
}
