package com.careconnect.service;

import com.careconnect.model.Mood;
import com.careconnect.repository.MoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MoodServiceTest {

    @Mock
    private MoodRepository moodRepository;

    @InjectMocks
    private MoodService moodService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("saveMood - valid inputs - returns saved mood")
    void saveMood_validInputs_returnsSavedMood() throws Exception {
        final Long userId = 1L;
        final int score = 8;
        final String label = "Happy";

        final Mood savedMood = new Mood(userId, score, label);
        when(moodRepository.save(any(Mood.class))).thenReturn(savedMood);

        final Mood result = moodService.saveMood(userId, score, label);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(score, result.getScore());
        assertEquals(label, result.getLabel());
        verify(moodRepository, times(1)).save(any(Mood.class));
    }

    @Test
    @DisplayName("saveMood - zero score - returns saved mood with zero score")
    void saveMood_zeroScore_returnsSavedMoodWithZeroScore() throws Exception {
        final Long userId = 2L;
        final int score = 0;
        final String label = "Neutral";

        final Mood savedMood = new Mood(userId, score, label);
        when(moodRepository.save(any(Mood.class))).thenReturn(savedMood);

        final Mood result = moodService.saveMood(userId, score, label);

        assertNotNull(result);
        assertEquals(0, result.getScore());
        assertEquals("Neutral", result.getLabel());
        verify(moodRepository).save(any(Mood.class));
    }

    @Test
    @DisplayName("saveMood - negative score - returns saved mood with negative score")
    void saveMood_negativeScore_returnsSavedMoodWithNegativeScore() throws Exception {
        final Long userId = 3L;
        final int score = -1;
        final String label = "Sad";

        final Mood savedMood = new Mood(userId, score, label);
        when(moodRepository.save(any(Mood.class))).thenReturn(savedMood);

        final Mood result = moodService.saveMood(userId, score, label);

        assertNotNull(result);
        assertEquals(-1, result.getScore());
        verify(moodRepository).save(any(Mood.class));
    }

    @Test
    @DisplayName("saveMood - verifies mood object is constructed and saved")
    void saveMood_verifiesMoodConstructionAndSave_savesCorrectly() throws Exception {
        final Long userId = 5L;
        final int score = 10;
        final String label = "Excellent";

        final Mood savedMood = new Mood(userId, score, label);
        when(moodRepository.save(any(Mood.class))).thenReturn(savedMood);

        final Mood result = moodService.saveMood(userId, score, label);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(score, result.getScore());
        assertEquals(label, result.getLabel());
        assertNotNull(result.getCreatedAt());
        verify(moodRepository).save(any(Mood.class));
    }

    @Test
    @DisplayName("getMoods - moods exist for user - returns list of moods")
    void getMoods_moodsExistForUser_returnsListOfMoods() throws Exception {
        final Long userId = 1L;
        final Mood mood1 = new Mood(userId, 7, "Good");
        final Mood mood2 = new Mood(userId, 3, "Bad");

        when(moodRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Arrays.asList(mood1, mood2));

        final List<Mood> result = moodService.getMoods(userId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(7, result.get(0).getScore());
        assertEquals(3, result.get(1).getScore());
        verify(moodRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("getMoods - no moods for user - returns empty list")
    void getMoods_noMoodsForUser_returnsEmptyList() throws Exception {
        final Long userId = 99L;
        when(moodRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.emptyList());

        final List<Mood> result = moodService.getMoods(userId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(moodRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("getMoods - single mood for user - returns single element list")
    void getMoods_singleMoodForUser_returnsSingleElementList() throws Exception {
        final Long userId = 42L;
        final Mood mood = new Mood(userId, 5, "Okay");

        when(moodRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.singletonList(mood));

        final List<Mood> result = moodService.getMoods(userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Okay", result.get(0).getLabel());
        assertEquals(userId, result.get(0).getUserId());
    }
}
