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
@Table(name = "call_transcript_archives", indexes = {
        @Index(name = "idx_call_transcript_archive_call_id", columnList = "call_id"),
        @Index(name = "idx_call_transcript_archive_archived_at", columnList = "archived_at")
})
public class CallTranscriptArchive extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false, length = 120)
    private String callId;

    @Column(name = "storage_provider", nullable = false, length = 24)
    private String storageProvider;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "segment_count", nullable = false)
    private Integer segmentCount;

    @Column(name = "transcript_chars", nullable = false)
    private Integer transcriptChars;

    @Column(name = "participant_user_ids", length = 512)
    private String participantUserIds;

    @Column(name = "sha256_checksum", length = 128)
    private String sha256Checksum;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

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

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public Integer getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(Integer segmentCount) {
        this.segmentCount = segmentCount;
    }

    public Integer getTranscriptChars() {
        return transcriptChars;
    }

    public void setTranscriptChars(Integer transcriptChars) {
        this.transcriptChars = transcriptChars;
    }

    public String getParticipantUserIds() {
        return participantUserIds;
    }

    public void setParticipantUserIds(String participantUserIds) {
        this.participantUserIds = participantUserIds;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
