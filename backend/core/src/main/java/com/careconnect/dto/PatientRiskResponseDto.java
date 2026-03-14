package com.careconnect.dto;

import com.careconnect.model.PatientRisk;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PatientRiskResponseDto {
    private Long id;
    private Long riskTypeId;
    private String riskTypeName;
    private Long flaggedByUserId;
    private Instant flaggedAt;

    public static PatientRiskResponseDto from(PatientRisk pr) {
        return PatientRiskResponseDto.builder()
                .id(pr.getId())
                .riskTypeId(pr.getRiskType().getId())
                .riskTypeName(pr.getRiskType().getName())
                .flaggedByUserId(pr.getFlaggedBy().getId())
                .flaggedAt(pr.getFlaggedAt())
                .build();
    }
}
