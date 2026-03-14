package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MailPieceTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final MailPiece piece = new MailPiece();

        assertThat(piece).isNotNull();
        assertThat(piece.getId()).isNull();
        assertThat(piece.getSender()).isNull();
        assertThat(piece.getSubject()).isNull();
        assertThat(piece.getThumbnailUrl()).isNull();
        assertThat(piece.getReceivedAt()).isNull();
        assertThat(piece.getActionLinks()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final ActionLinks links = ActionLinks.builder().build();

        final MailPiece piece = new MailPiece("MP-001", "USPS", "Your bill", "http://thumb.jpg", now, links);

        assertThat(piece.getId()).isEqualTo("MP-001");
        assertThat(piece.getSender()).isEqualTo("USPS");
        assertThat(piece.getSubject()).isEqualTo("Your bill");
        assertThat(piece.getThumbnailUrl()).isEqualTo("http://thumb.jpg");
        assertThat(piece.getReceivedAt()).isEqualTo(now);
        assertThat(piece.getActionLinks()).isSameAs(links);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        final MailPiece piece = MailPiece.builder()
                .id("MP-002")
                .sender("FedEx")
                .subject("Package Notice")
                .thumbnailUrl("http://img.example.com/thumb.png")
                .receivedAt(now)
                .build();

        assertThat(piece.getId()).isEqualTo("MP-002");
        assertThat(piece.getSender()).isEqualTo("FedEx");
        assertThat(piece.getSubject()).isEqualTo("Package Notice");
        assertThat(piece.getThumbnailUrl()).isEqualTo("http://img.example.com/thumb.png");
        assertThat(piece.getReceivedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final MailPiece piece = new MailPiece();
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final ActionLinks links = new ActionLinks();

        piece.setId("MP-003");
        piece.setSender("UPS");
        piece.setSubject("Delivery Attempt");
        piece.setThumbnailUrl("http://example.com/img.png");
        piece.setReceivedAt(now);
        piece.setActionLinks(links);

        assertThat(piece.getId()).isEqualTo("MP-003");
        assertThat(piece.getSender()).isEqualTo("UPS");
        assertThat(piece.getSubject()).isEqualTo("Delivery Attempt");
        assertThat(piece.getThumbnailUrl()).isEqualTo("http://example.com/img.png");
        assertThat(piece.getReceivedAt()).isEqualTo(now);
        assertThat(piece.getActionLinks()).isSameAs(links);
    }
}
