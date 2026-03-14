package com.careconnect.dto.schedule;

import com.careconnect.model.schedule.ScheduledVisit;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CalendarViewDto {
    private LocalDate date;
    private List<ScheduledVisitResponse> visits;
    private Integer totalDuration; // minutes
    private Integer visitCount;
    private List<String> conflict_warnings;
}