package com.careconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "client_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

  @Column(name = "client_id", nullable = false)
    private Long clientId; // patient.id

  @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId; // caregiver.id

  @Column(name = "created_by", nullable = false)
    private Long createdBy; // user.id that created this record (audit; always from session)

  @Column(name = "activity_id", nullable = false)
    private Long activityId;

  @Column(name = "tapped_at", nullable = false)
    private LocalDateTime tappedAt;

  @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

  @PrePersist
    public void onCreate() {
    if (createdAt == null) createdAt = LocalDateTime.now();
    if (tappedAt == null) tappedAt = createdAt;
  }

  @PreUpdate
  @PreRemove
    private void preventUpdateOrDelete() {
    throw new UnsupportedOperationException("ClientEvent records are immutable; updates and deletes are not allowed.");
  }
}

