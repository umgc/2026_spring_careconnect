package com.careconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class ActivityLogDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateActivityLogRequest {
        private Long clientId;
        private Long activityId;
        private String activityName;
        private Integer competencyScore;
        private Integer satisfactionRating;
        private String notes;
        // caregiverId must come from session; ignored if provided by client.
        private Long caregiverId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityLogResponse {
        private Long id;
        private Long clientId;
        private Long activityId;
        private String activityName;
        private Integer competencyScore;
        private Integer satisfactionRating;
        private String notes;
        private java.time.LocalDateTime createdAt;
    }
}

