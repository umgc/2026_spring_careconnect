package com.careconnect.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditDiffResponse {
  private ScheduledVisitResponse before;
  private ScheduledVisitResponse after;
  private String changedField;
  private String action;
  private String changedBy;
  private LocalDateTime changedAt;
}
