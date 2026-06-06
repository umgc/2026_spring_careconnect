package com.careconnect.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledVisitAuditResponse {
  private Long id;
  private Long visitId;
  private String action;
  private String changedField;
  private String oldValue;
  private String newValue;
  private LocalDateTime changedAt;
  private String changedBy;
}
