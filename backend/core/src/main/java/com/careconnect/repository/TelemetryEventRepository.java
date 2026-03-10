package com.careconnect.repository;

import com.careconnect.model.TelemetryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TelemetryEventRepository extends JpaRepository<TelemetryEvent, Long> {
    List<TelemetryEvent> findTop50ByOrderByEventTimeDesc();
}