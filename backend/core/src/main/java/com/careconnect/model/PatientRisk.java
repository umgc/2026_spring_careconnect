package com.careconnect.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "patient_risks")
public class PatientRisk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "risk_type_id", nullable = false)
    private RiskType riskType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flagged_by", nullable = false)
    private User flaggedBy;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt;
}
