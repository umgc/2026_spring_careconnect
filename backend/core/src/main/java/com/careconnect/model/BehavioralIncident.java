package com.careconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "behavioral_incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralIncident {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @Column(name = "client_id", nullable = false)
    private Long clientId; // patient.id

  @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId; // caregiver.id (not user id)

  @Column(name = "observed_behavior", nullable = false, columnDefinition = "TEXT")
    private String observedBehavior;

  @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

  @Column(name = "trigger_notes", columnDefinition = "TEXT")
    private String triggerNotes;

  @Column(name = "created_by", nullable = false)
    private Long createdBy; // user.id that created this record

  @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

  @PrePersist
    public void onCreate() {
    if (createdAt == null) createdAt = LocalDateTime.now();
    if (occurredAt == null) occurredAt = createdAt;
  }

  @PreUpdate
  @PreRemove
    private void preventUpdateOrDelete() {
    throw new UnsupportedOperationException("BehavioralIncident records are immutable; updates and deletes are not allowed.");
  }
}

