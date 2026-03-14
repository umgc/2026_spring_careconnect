package com.careconnect.service;

import com.careconnect.model.Achievement;
import com.careconnect.model.UserAchievement;
import com.careconnect.model.XPProgress;
import com.careconnect.repository.AchievementRepository;
import com.careconnect.repository.UserAchievementRepository;
import com.careconnect.repository.XPProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GamificationService}.
 */
class GamificationServiceTest {

    @Mock
    private XPProgressRepository xpProgressRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @InjectMocks
    private GamificationService gamificationService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    // ──────────────────────────────────────────────
    //  awardXp
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("awardXp_existingProgress_updatesXpAndLevel")
    void awardXp_existingProgress_updatesXpAndLevel() throws Exception {
        final Long userId = 1L;
        final int existingXp = 40;
        final int amount = 20;

        final XPProgress existing = new XPProgress();
        existing.setUserId(userId);
        existing.setXp(existingXp);
        existing.setLevel(1);

        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(xpProgressRepository.save(any(XPProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        final XPProgress result = gamificationService.awardXp(userId, amount);

        assertThat(result.getXp()).isEqualTo(60);
        // calculateLevel(60) = 60/50 + 1 = 2
        assertThat(result.getLevel()).isEqualTo(2);
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(xpProgressRepository).save(result);
    }

    @Test
    @DisplayName("awardXp_noExistingProgress_createsNewProgressAndAwardsXp")
    void awardXp_noExistingProgress_createsNewProgressAndAwardsXp() throws Exception {
        final Long userId = 2L;
        final int amount = 10;

        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(xpProgressRepository.save(any(XPProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        final XPProgress result = gamificationService.awardXp(userId, amount);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getXp()).isEqualTo(10);
        // calculateLevel(10) = 10/50 + 1 = 1
        assertThat(result.getLevel()).isEqualTo(1);
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(xpProgressRepository).save(result);
    }

    @Test
    @DisplayName("awardXp_zeroAmount_xpUnchangedLevelStays")
    void awardXp_zeroAmount_xpUnchangedLevelStays() throws Exception {
        final Long userId = 3L;

        final XPProgress existing = new XPProgress();
        existing.setUserId(userId);
        existing.setXp(100);
        existing.setLevel(3);

        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(xpProgressRepository.save(any(XPProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        final XPProgress result = gamificationService.awardXp(userId, 0);

        assertThat(result.getXp()).isEqualTo(100);
        // calculateLevel(100) = 100/50 + 1 = 3
        assertThat(result.getLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("awardXp_levelBoundary_levelIncreasesCorrectly")
    void awardXp_levelBoundary_levelIncreasesCorrectly() throws Exception {
        final Long userId = 4L;

        final XPProgress existing = new XPProgress();
        existing.setUserId(userId);
        existing.setXp(49);
        existing.setLevel(1);

        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(xpProgressRepository.save(any(XPProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        final XPProgress result = gamificationService.awardXp(userId, 1);

        assertThat(result.getXp()).isEqualTo(50);
        // calculateLevel(50) = 50/50 + 1 = 2
        assertThat(result.getLevel()).isEqualTo(2);
    }

    // ──────────────────────────────────────────────
    //  grantAchievement
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("grantAchievement_achievementNotFound_throwsRuntimeException")
    void grantAchievement_achievementNotFound_throwsRuntimeException() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 99L;

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gamificationService.grantAchievement(userId, achievementId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Achievement not found");
    }

    @Test
    @DisplayName("grantAchievement_userAlreadyHasAchievement_doesNotSaveDuplicate")
    void grantAchievement_userAlreadyHasAchievement_doesNotSaveDuplicate() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 10L;

        final Achievement achievement = new Achievement();
        achievement.setId(achievementId);
        achievement.setTitle("First Check-In");

        final UserAchievement existingUa = new UserAchievement();
        existingUa.setUserId(userId);
        existingUa.setAchievement(achievement);

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(existingUa));

        gamificationService.grantAchievement(userId, achievementId);

        verify(userAchievementRepository, never()).save(any(UserAchievement.class));
    }

    @Test
    @DisplayName("grantAchievement_userDoesNotHaveAchievement_savesNewUserAchievement")
    void grantAchievement_userDoesNotHaveAchievement_savesNewUserAchievement() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 10L;

        final Achievement achievement = new Achievement();
        achievement.setId(achievementId);
        achievement.setTitle("First Check-In");

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        gamificationService.grantAchievement(userId, achievementId);

        final ArgumentCaptor<UserAchievement> captor = ArgumentCaptor.forClass(UserAchievement.class);
        verify(userAchievementRepository).save(captor.capture());

        final UserAchievement saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getAchievement()).isEqualTo(achievement);
        assertThat(saved.getEarnedAt()).isNotNull();
    }

    @Test
    @DisplayName("grantAchievement_userHasDifferentAchievement_savesNewOne")
    void grantAchievement_userHasDifferentAchievement_savesNewOne() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 10L;
        final Long otherAchievementId = 20L;

        final Achievement achievement = new Achievement();
        achievement.setId(achievementId);
        achievement.setTitle("First Check-In");

        final Achievement otherAchievement = new Achievement();
        otherAchievement.setId(otherAchievementId);
        otherAchievement.setTitle("Other Achievement");

        final UserAchievement existingUa = new UserAchievement();
        existingUa.setUserId(userId);
        existingUa.setAchievement(otherAchievement);

        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(existingUa));

        gamificationService.grantAchievement(userId, achievementId);

        verify(userAchievementRepository).save(any(UserAchievement.class));
    }

    // ──────────────────────────────────────────────
    //  getAllAchievements
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getAllAchievements_achievementsExist_returnsAll")
    void getAllAchievements_achievementsExist_returnsAll() throws Exception {
        final Achievement a1 = new Achievement();
        a1.setTitle("First Check-In");
        final Achievement a2 = new Achievement();
        a2.setTitle("Streak Master");

        when(achievementRepository.findAll()).thenReturn(List.of(a1, a2));

        final List<Achievement> result = gamificationService.getAllAchievements();

        assertThat(result).hasSize(2);
        verify(achievementRepository).findAll();
    }

    @Test
    @DisplayName("getAllAchievements_noAchievements_returnsEmptyList")
    void getAllAchievements_noAchievements_returnsEmptyList() throws Exception {
        when(achievementRepository.findAll()).thenReturn(Collections.emptyList());

        final List<Achievement> result = gamificationService.getAllAchievements();

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  getUserAchievements
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getUserAchievements_userHasAchievements_returnsList")
    void getUserAchievements_userHasAchievements_returnsList() throws Exception {
        final Long userId = 1L;

        final UserAchievement ua = new UserAchievement();
        ua.setUserId(userId);

        when(userAchievementRepository.findByUserId(userId)).thenReturn(List.of(ua));

        final List<UserAchievement> result = gamificationService.getUserAchievements(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getUserAchievements_userHasNone_returnsEmptyList")
    void getUserAchievements_userHasNone_returnsEmptyList() throws Exception {
        final Long userId = 99L;

        when(userAchievementRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        final List<UserAchievement> result = gamificationService.getUserAchievements(userId);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  getXpProgress
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("getXpProgress_progressExists_returnsOptionalWithValue")
    void getXpProgress_progressExists_returnsOptionalWithValue() throws Exception {
        final Long userId = 1L;
        final XPProgress progress = new XPProgress();
        progress.setUserId(userId);
        progress.setXp(150);

        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.of(progress));

        final Optional<XPProgress> result = gamificationService.getXpProgress(userId);

        assertThat(result).isPresent();
        assertThat(result.get().getXp()).isEqualTo(150);
    }

    @Test
    @DisplayName("getXpProgress_noProgress_returnsEmptyOptional")
    void getXpProgress_noProgress_returnsEmptyOptional() throws Exception {
        final Long userId = 99L;

        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.empty());

        final Optional<XPProgress> result = gamificationService.getXpProgress(userId);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────
    //  unlockAchievement
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("unlockAchievement_achievementNotFoundByTitle_returnsEarlyWithoutSaving")
    void unlockAchievement_achievementNotFoundByTitle_returnsEarlyWithoutSaving() throws Exception {
        final Long userId = 1L;
        final String title = "Nonexistent";

        when(achievementRepository.findByTitle(title)).thenReturn(Optional.empty());

        gamificationService.unlockAchievement(userId, title, 25);

        verify(userAchievementRepository, never()).existsByUserIdAndAchievementId(any(), any());
        verify(userAchievementRepository, never()).save(any());
        verify(xpProgressRepository, never()).save(any());
    }

    @Test
    @DisplayName("unlockAchievement_alreadyUnlocked_returnsEarlyWithoutSaving")
    void unlockAchievement_alreadyUnlocked_returnsEarlyWithoutSaving() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 5L;
        final String title = "Streak Master";

        final Achievement achievement = new Achievement();
        achievement.setId(achievementId);
        achievement.setTitle(title);

        when(achievementRepository.findByTitle(title)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.existsByUserIdAndAchievementId(userId, achievementId)).thenReturn(true);

        gamificationService.unlockAchievement(userId, title, 25);

        verify(userAchievementRepository, never()).save(any());
        verify(xpProgressRepository, never()).save(any());
    }

    @Test
    @DisplayName("unlockAchievement_newUnlock_awardsXpAndSavesUserAchievement")
    void unlockAchievement_newUnlock_awardsXpAndSavesUserAchievement() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 5L;
        final String title = "Streak Master";
        final int xpAward = 25;

        final Achievement achievement = new Achievement();
        achievement.setId(achievementId);
        achievement.setTitle(title);

        when(achievementRepository.findByTitle(title)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.existsByUserIdAndAchievementId(userId, achievementId)).thenReturn(false);

        // awardXp will call xpProgressRepository.findByUserId -- return empty so new XPProgress is created
        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(xpProgressRepository.save(any(XPProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        gamificationService.unlockAchievement(userId, title, xpAward);

        // Verify XP was awarded (xpProgressRepository.save called via awardXp)
        final ArgumentCaptor<XPProgress> xpCaptor = ArgumentCaptor.forClass(XPProgress.class);
        verify(xpProgressRepository).save(xpCaptor.capture());
        final XPProgress savedXp = xpCaptor.getValue();
        assertThat(savedXp.getXp()).isEqualTo(xpAward);
        assertThat(savedXp.getUserId()).isEqualTo(userId);

        // Verify user achievement was saved
        final ArgumentCaptor<UserAchievement> uaCaptor = ArgumentCaptor.forClass(UserAchievement.class);
        verify(userAchievementRepository).save(uaCaptor.capture());
        final UserAchievement savedUa = uaCaptor.getValue();
        assertThat(savedUa.getUserId()).isEqualTo(userId);
        assertThat(savedUa.getAchievement()).isEqualTo(achievement);
        assertThat(savedUa.getEarnedAt()).isNotNull();
    }

    @Test
    @DisplayName("unlockAchievement_existingXpProgress_addsXpToExisting")
    void unlockAchievement_existingXpProgress_addsXpToExisting() throws Exception {
        final Long userId = 1L;
        final Long achievementId = 7L;
        final String title = "Health Hero";
        final int xpAward = 50;

        final Achievement achievement = new Achievement();
        achievement.setId(achievementId);
        achievement.setTitle(title);

        final XPProgress existingProgress = new XPProgress();
        existingProgress.setUserId(userId);
        existingProgress.setXp(100);
        existingProgress.setLevel(3);

        when(achievementRepository.findByTitle(title)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.existsByUserIdAndAchievementId(userId, achievementId)).thenReturn(false);
        when(xpProgressRepository.findByUserId(userId)).thenReturn(Optional.of(existingProgress));
        when(xpProgressRepository.save(any(XPProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        gamificationService.unlockAchievement(userId, title, xpAward);

        final ArgumentCaptor<XPProgress> xpCaptor = ArgumentCaptor.forClass(XPProgress.class);
        verify(xpProgressRepository).save(xpCaptor.capture());
        final XPProgress savedXp = xpCaptor.getValue();
        assertThat(savedXp.getXp()).isEqualTo(150);
        // calculateLevel(150) = 150/50 + 1 = 4
        assertThat(savedXp.getLevel()).isEqualTo(4);

        verify(userAchievementRepository).save(any(UserAchievement.class));
    }
}
