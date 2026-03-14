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
@Table(name = "call_recordings", indexes = {
        @Index(name = "idx_call_recordings_call_id", columnList = "call_id"),
        @Index(name = "idx_call_recordings_user_id", columnList = "initiated_by_user_id"),
        @Index(name = "idx_call_recordings_status", columnList = "status"),
        @Index(name = "idx_call_recordings_started_at", columnList = "started_at")
})
public class CallRecording extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 120)
    private String callId;

    /** AWS Chime Media Pipeline ID — used to stop the pipeline. */
    @Column(name = "pipeline_id", length = 255)
    private String pipelineId;

    /** AWS Chime Media Concatenation Pipeline ID — used to track stitched output readiness. */
    @Column(name = "concatenation_pipeline_id", length = 255)
    private String concatenationPipelineId;

    /** S3 bucket where recordings are written. */
    @Column(name = "s3_bucket", length = 255)
    private String s3Bucket;

    /** S3 key prefix for this recording (e.g. recordings/{callId}/{timestamp}/). */
    @Column(name = "s3_prefix", length = 500)
    private String s3Prefix;

    /** STARTED | STOPPED | FAILED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** NOT_REQUESTED | PROCESSING | READY | FAILED */
    @Column(name = "concatenation_status", length = 30)
    private String concatenationStatus;

    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getConcatenationPipelineId() { return concatenationPipelineId; }
    public void setConcatenationPipelineId(String concatenationPipelineId) { this.concatenationPipelineId = concatenationPipelineId; }

    public String getS3Bucket() { return s3Bucket; }
    public void setS3Bucket(String s3Bucket) { this.s3Bucket = s3Bucket; }

    public String getS3Prefix() { return s3Prefix; }
    public void setS3Prefix(String s3Prefix) { this.s3Prefix = s3Prefix; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getConcatenationStatus() { return concatenationStatus; }
    public void setConcatenationStatus(String concatenationStatus) { this.concatenationStatus = concatenationStatus; }

    public Long getInitiatedByUserId() { return initiatedByUserId; }
    public void setInitiatedByUserId(Long initiatedByUserId) { this.initiatedByUserId = initiatedByUserId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public Long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Long durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
