package com.careconnect.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "incident_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_report_id", nullable = false)
    @JsonIgnore
    private IncidentReport incidentReport;

    @Column(name = "action_taken", nullable = false, columnDefinition = "TEXT")
    private String actionTaken;

    @PreUpdate
    @PreRemove
    private void preventUpdateOrDelete() {
        throw new UnsupportedOperationException("IncidentAction records are immutable; updates and deletes are not allowed.");
    }
}

