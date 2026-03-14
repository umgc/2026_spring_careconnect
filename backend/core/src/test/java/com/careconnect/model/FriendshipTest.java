package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Friendship friendship = new Friendship();

        assertThat(friendship).isNotNull();
        assertThat(friendship.getId()).isNull();
        assertThat(friendship.getUser1()).isNull();
        assertThat(friendship.getUser2()).isNull();
        assertThat(friendship.getStatus()).isNull();
    }

    // ─── All-arg constructor ──────────────────────────────────────────────────

    @Test
    void allArgConstructor_setsAllFields() throws Exception {
        final User user1 = User.builder().id(1L).name("Alice").build();
        final User user2 = User.builder().id(2L).name("Bob").build();

        final Friendship friendship = new Friendship(10L, user1, user2, "CONFIRMED");

        assertThat(friendship.getId()).isEqualTo(10L);
        assertThat(friendship.getUser1()).isSameAs(user1);
        assertThat(friendship.getUser2()).isSameAs(user2);
        assertThat(friendship.getStatus()).isEqualTo("CONFIRMED");
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_setsFields() throws Exception {
        final User user1 = User.builder().id(3L).build();
        final User user2 = User.builder().id(4L).build();

        final Friendship friendship = Friendship.builder()
                .id(5L)
                .user1(user1)
                .user2(user2)
                .status("PENDING")
                .build();

        assertThat(friendship.getId()).isEqualTo(5L);
        assertThat(friendship.getUser1()).isSameAs(user1);
        assertThat(friendship.getUser2()).isSameAs(user2);
        assertThat(friendship.getStatus()).isEqualTo("PENDING");
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final Friendship friendship = new Friendship();
        final User user1 = new User();
        final User user2 = new User();

        friendship.setId(20L);
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setStatus("CONFIRMED");

        assertThat(friendship.getId()).isEqualTo(20L);
        assertThat(friendship.getUser1()).isSameAs(user1);
        assertThat(friendship.getUser2()).isSameAs(user2);
        assertThat(friendship.getStatus()).isEqualTo("CONFIRMED");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Friendship f1 = Friendship.builder().id(1L).status("CONFIRMED").build();
        final Friendship f2 = Friendship.builder().id(1L).status("CONFIRMED").build();

        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final Friendship f1 = Friendship.builder().id(1L).build();
        final Friendship f2 = Friendship.builder().id(2L).build();

        assertThat(f1).isNotEqualTo(f2);
    }
}
