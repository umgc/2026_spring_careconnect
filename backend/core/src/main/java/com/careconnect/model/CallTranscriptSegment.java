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
@Table(name = "call_transcript_segments", indexes = {
        @Index(name = "idx_call_transcript_call_id", columnList = "call_id"),
        @Index(name = "idx_call_transcript_actor", columnList = "actor_user_id"),
        @Index(name = "idx_call_transcript_start_ms", columnList = "start_ms")
})
public class CallTranscriptSegment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 120)
    private String callId;

    @Column(name = "speaker_label", length = 60)
    private String speakerLabel;

    @Column(name = "transcript_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "start_ms")
    private Long startMs;

    @Column(name = "end_ms")
    private Long endMs;

    @Column(name = "source", length = 80)
    private String source;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

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

    public String getSpeakerLabel() {
        return speakerLabel;
    }

    public void setSpeakerLabel(String speakerLabel) {
        this.speakerLabel = speakerLabel;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Long getStartMs() {
        return startMs;
    }

    public void setStartMs(Long startMs) {
        this.startMs = startMs;
    }

    public Long getEndMs() {
        return endMs;
    }

    public void setEndMs(Long endMs) {
        this.endMs = endMs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
