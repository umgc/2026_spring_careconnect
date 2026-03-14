package com.careconnect.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_summaries", indexes = {
        @Index(name = "idx_call_summary_call_id", columnList = "call_id"),
        @Index(name = "idx_call_summary_generated_at", columnList = "generated_at")
})
public class CallSummary extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 120)
    private String callId;

    @Column(name = "summary_json", nullable = false, columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "transcript_segment_count", nullable = false)
    private Integer transcriptSegmentCount;

    @Column(name = "generated_by_user_id")
    private Long generatedByUserId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTranscriptSegmentCount() {
        return transcriptSegmentCount;
    }

    public void setTranscriptSegmentCount(Integer transcriptSegmentCount) {
        this.transcriptSegmentCount = transcriptSegmentCount;
    }

    public Long getGeneratedByUserId() {
        return generatedByUserId;
    }

    public void setGeneratedByUserId(Long generatedByUserId) {
        this.generatedByUserId = generatedByUserId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
