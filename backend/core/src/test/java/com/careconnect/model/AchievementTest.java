package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementTest {

    @Test
    void noArgsConstructor_createsInstanceWithNullFields() throws Exception {
        final Achievement achievement = new Achievement();
        assertThat(achievement.getId()).isNull();
        assertThat(achievement.getTitle()).isNull();
        assertThat(achievement.getDescription()).isNull();
        assertThat(achievement.getIcon()).isNull();
    }

    @Test
    void allArgsConstructor_setsAllFields() throws Exception {
        final Achievement achievement = new Achievement(1L, "First Steps", "Complete your first visit", "star-icon");
        assertThat(achievement.getId()).isEqualTo(1L);
        assertThat(achievement.getTitle()).isEqualTo("First Steps");
        assertThat(achievement.getDescription()).isEqualTo("Complete your first visit");
        assertThat(achievement.getIcon()).isEqualTo("star-icon");
    }

    @Test
    void setTitle_updatesTitle() throws Exception {
        final Achievement achievement = new Achievement();
        achievement.setTitle("Milestone");
        assertThat(achievement.getTitle()).isEqualTo("Milestone");
    }

    @Test
    void setDescription_updatesDescription() throws Exception {
        final Achievement achievement = new Achievement();
        achievement.setDescription("Reach 10 visits");
        assertThat(achievement.getDescription()).isEqualTo("Reach 10 visits");
    }

    @Test
    void setIcon_updatesIcon() throws Exception {
        final Achievement achievement = new Achievement();
        achievement.setIcon("trophy.png");
        assertThat(achievement.getIcon()).isEqualTo("trophy.png");
    }

    @Test
    void setTitle_null_setsNull() throws Exception {
        final Achievement achievement = new Achievement(1L, "Title", "Desc", "icon");
        achievement.setTitle(null);
        assertThat(achievement.getTitle()).isNull();
    }

    @Test
    void setDescription_null_setsNull() throws Exception {
        final Achievement achievement = new Achievement(1L, "Title", "Desc", "icon");
        achievement.setDescription(null);
        assertThat(achievement.getDescription()).isNull();
    }

    @Test
    void setIcon_null_setsNull() throws Exception {
        final Achievement achievement = new Achievement(1L, "Title", "Desc", "icon");
        achievement.setIcon(null);
        assertThat(achievement.getIcon()).isNull();
    }
}
