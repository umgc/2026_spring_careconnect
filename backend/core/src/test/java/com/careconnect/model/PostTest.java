package com.careconnect.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    // ─── Default constructor ──────────────────────────────────────────────────

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final Post post = new Post();

        assertThat(post).isNotNull();
        assertThat(post.getId()).isNull();
        assertThat(post.getUserId()).isNull();
        assertThat(post.getContent()).isNull();
        assertThat(post.getImageUrl()).isNull();
        assertThat(post.getCreatedAt()).isNotNull(); // defaults to now
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final LocalDateTime now = LocalDateTime.now();
        final Post post = new Post(1L, 10L, "Hello World", "http://img.example.com/pic.jpg", now);

        assertThat(post.getId()).isEqualTo(1L);
        assertThat(post.getUserId()).isEqualTo(10L);
        assertThat(post.getContent()).isEqualTo("Hello World");
        assertThat(post.getImageUrl()).isEqualTo("http://img.example.com/pic.jpg");
        assertThat(post.getCreatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Post post = new Post();
        final LocalDateTime now = LocalDateTime.now();

        post.setUserId(55L);
        post.setContent("Updated content");
        post.setImageUrl("http://new.url/img.png");
        post.setCreatedAt(now);

        assertThat(post.getUserId()).isEqualTo(55L);
        assertThat(post.getContent()).isEqualTo("Updated content");
        assertThat(post.getImageUrl()).isEqualTo("http://new.url/img.png");
        assertThat(post.getCreatedAt()).isEqualTo(now);
    }
}
