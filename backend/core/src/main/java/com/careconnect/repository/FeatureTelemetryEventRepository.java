package com.careconnect.repository;

import com.careconnect.model.FeatureTelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeatureTelemetryEventRepository extends JpaRepository<FeatureTelemetryEvent, Long> {
    List<FeatureTelemetryEvent> findTop50ByOrderByEventTimeDesc();
}