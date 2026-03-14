package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final Comment comment = new Comment();

        assertThat(comment).isNotNull();
        assertThat(comment.getId()).isNull();
        assertThat(comment.getPostId()).isNull();
        assertThat(comment.getUserId()).isNull();
        assertThat(comment.getUsername()).isNull();
        assertThat(comment.getContent()).isNull();
        assertThat(comment.getCreatedAt()).isNull();
    }

    // ─── Setters / Getters ────────────────────────────────────────────────────

    @Test
    void settersAndGetters_updateFields() throws Exception {
        final Comment comment = new Comment();
        final LocalDateTime now = LocalDateTime.now();

        comment.setPostId(10L);
        comment.setUserId(20L);
        comment.setUsername("johndoe");
        comment.setContent("Great post!");
        comment.setCreatedAt(now);

        assertThat(comment.getPostId()).isEqualTo(10L);
        assertThat(comment.getUserId()).isEqualTo(20L);
        assertThat(comment.getUsername()).isEqualTo("johndoe");
        assertThat(comment.getContent()).isEqualTo("Great post!");
        assertThat(comment.getCreatedAt()).isEqualTo(now);
    }
}
