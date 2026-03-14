package com.careconnect.repository;

import com.careconnect.model.IncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
    List<IncidentReport> findByClientIdOrderByOccurredAtDesc(Long clientId);
}

