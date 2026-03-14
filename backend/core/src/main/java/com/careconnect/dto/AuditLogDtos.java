package com.careconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class AuditLogDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditLogItem {
        private String type;
        private String summary;
        private String caregiverName;
        private LocalDateTime createdAt;
    }
}

