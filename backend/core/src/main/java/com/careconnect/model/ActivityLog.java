package com.careconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @Column(name = "client_id", nullable = false)
    private Long clientId; // patient.id

  @Column(name = "activity_id", nullable = false)
    private Long activityId;

  @Column(name = "activity_name", length = 255)
    private String activityName;

  @Column(name = "caregiver_user_id", nullable = false)
    private Long caregiverUserId;

  @Column(name = "competency_score", nullable = false)
    private Integer competencyScore;

  @Column(name = "satisfaction_rating")
    private Integer satisfactionRating;

  @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

  @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

  @PrePersist
    public void onCreate() {
    if (createdAt == null) createdAt = LocalDateTime.now();
  }

  @PreUpdate
  @PreRemove
    private void preventUpdateOrDelete() {
    throw new UnsupportedOperationException("ActivityLog records are immutable; updates and deletes are not allowed.");
  }
}

