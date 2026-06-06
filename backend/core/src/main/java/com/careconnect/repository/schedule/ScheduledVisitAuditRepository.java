package com.careconnect.repository.schedule;

import com.careconnect.model.schedule.ScheduledVisitAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledVisitAuditRepository extends JpaRepository<ScheduledVisitAudit, Long> {

  List<ScheduledVisitAudit> findByVisitIdOrderByChangedAtDesc(Long visitId);

  List<ScheduledVisitAudit> findByVisitIdAndChangedAtBeforeOrderByChangedAtDesc(
            Long visitId, LocalDateTime changedAt);
}
