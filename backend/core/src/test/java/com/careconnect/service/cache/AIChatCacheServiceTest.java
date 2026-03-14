package com.careconnect.service.cache;

import com.careconnect.model.ChatConversation;
import com.careconnect.model.Patient;
import com.careconnect.model.UserAIConfig;
import com.careconnect.repository.ChatConversationRepository;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.UserAIConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AIChatCacheService}.
 */
class AIChatCacheServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserAIConfigRepository userAIConfigRepository;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    private AIChatCacheService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new AIChatCacheService();
        ReflectionTestUtils.setField(service, "patientRepository", patientRepository);
        ReflectionTestUtils.setField(service, "userAIConfigRepository", userAIConfigRepository);
        ReflectionTestUtils.setField(service, "chatConversationRepository", chatConversationRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helper: inject an expired CacheEntry into a cache map
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private <T> ConcurrentHashMap<String, Object> getCacheMap(String fieldName) {
        return (ConcurrentHashMap<String, Object>) ReflectionTestUtils.getField(service, fieldName);
    }

    /**
     * Creates a CacheEntry via reflection and sets its timestamp to the past so it appears expired.
     */
    private <T> Object createExpiredCacheEntry(T value) throws Exception {
        final Class<?> cacheEntryClass = Class.forName("com.careconnect.service.cache.AIChatCacheService$CacheEntry");
        final Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(Object.class);
        ctor.setAccessible(true);
        final Object entry = ctor.newInstance(value);

        // Set timestamp to 20 minutes ago so isExpired() returns true
        final Field timestampField = cacheEntryClass.getDeclaredField("timestamp");
        timestampField.setAccessible(true);
        timestampField.set(entry, LocalDateTime.now().minusMinutes(20));

        return entry;
    }

    /**
     * Creates a fresh (non-expired) CacheEntry via reflection.
     */
    private <T> Object createFreshCacheEntry(T value) throws Exception {
        final Class<?> cacheEntryClass = Class.forName("com.careconnect.service.cache.AIChatCacheService$CacheEntry");
        final Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(Object.class);
        ctor.setAccessible(true);
        return ctor.newInstance(value);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findPatient
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findPatient")
    class FindPatient {

        @Test
        @DisplayName("findPatient_cacheMiss_dbReturnsPatient_cachesAndReturns")
        void findPatient_cacheMiss_dbReturnsPatient_cachesAndReturns() throws Exception {
            final Patient patient = new Patient();
            when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));

            final Optional<Patient> result = service.findPatient(1L);

            assertThat(result).isPresent().contains(patient);
            verify(patientRepository).findById(1L);

            // Second call should hit cache
            final Optional<Patient> cached = service.findPatient(1L);
            assertThat(cached).isPresent().contains(patient);
            verifyNoMoreInteractions(patientRepository);
        }

        @Test
        @DisplayName("findPatient_cacheMiss_dbReturnsEmpty_returnsEmptyAndDoesNotCache")
        void findPatient_cacheMiss_dbReturnsEmpty_returnsEmptyAndDoesNotCache() throws Exception {
            when(patientRepository.findById(99L)).thenReturn(Optional.empty());

            final Optional<Patient> result = service.findPatient(99L);

            assertThat(result).isEmpty();
            verify(patientRepository).findById(99L);

            // Second call should still miss cache (empty result was not cached)
            service.findPatient(99L);
            verify(patientRepository, times(2)).findById(99L);
        }

        @Test
        @DisplayName("findPatient_cacheHit_returnsFromCacheWithoutDbQuery")
        void findPatient_cacheHit_returnsFromCacheWithoutDbQuery() throws Exception {
            final Patient patient = new Patient();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("patientCache");
            cache.put("patient_1", createFreshCacheEntry(patient));

            final Optional<Patient> result = service.findPatient(1L);

            assertThat(result).isPresent().contains(patient);
            verifyNoInteractions(patientRepository);
        }

        @Test
        @DisplayName("findPatient_cacheExpired_queriesDbAgain")
        void findPatient_cacheExpired_queriesDbAgain() throws Exception {
            final Patient oldPatient = new Patient();
            final Patient newPatient = new Patient();

            final ConcurrentHashMap<String, Object> cache = getCacheMap("patientCache");
            cache.put("patient_1", createExpiredCacheEntry(oldPatient));

            when(patientRepository.findById(1L)).thenReturn(Optional.of(newPatient));

            final Optional<Patient> result = service.findPatient(1L);

            assertThat(result).isPresent().contains(newPatient);
            verify(patientRepository).findById(1L);
        }

        @Test
        @DisplayName("findPatient_cacheExpired_dbReturnsEmpty_returnsEmpty")
        void findPatient_cacheExpired_dbReturnsEmpty_returnsEmpty() throws Exception {
            final Patient oldPatient = new Patient();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("patientCache");
            cache.put("patient_1", createExpiredCacheEntry(oldPatient));

            when(patientRepository.findById(1L)).thenReturn(Optional.empty());

            final Optional<Patient> result = service.findPatient(1L);

            assertThat(result).isEmpty();
            verify(patientRepository).findById(1L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findUserAIConfig
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findUserAIConfig")
    class FindUserAIConfig {

        @Test
        @DisplayName("findUserAIConfig_cacheMiss_dbReturnsConfig_cachesAndReturns")
        void findUserAIConfig_cacheMiss_dbReturnsConfig_cachesAndReturns() throws Exception {
            final UserAIConfig config = new UserAIConfig();
            when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L))
                    .thenReturn(Optional.of(config));

            final Optional<UserAIConfig> result = service.findUserAIConfig(10L, 20L);

            assertThat(result).isPresent().contains(config);
            verify(userAIConfigRepository).findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L);

            // Second call should hit cache
            final Optional<UserAIConfig> cached = service.findUserAIConfig(10L, 20L);
            assertThat(cached).isPresent().contains(config);
            verifyNoMoreInteractions(userAIConfigRepository);
        }

        @Test
        @DisplayName("findUserAIConfig_cacheMiss_dbReturnsEmpty_returnsEmptyAndDoesNotCache")
        void findUserAIConfig_cacheMiss_dbReturnsEmpty_returnsEmptyAndDoesNotCache() throws Exception {
            when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L))
                    .thenReturn(Optional.empty());

            final Optional<UserAIConfig> result = service.findUserAIConfig(10L, 20L);

            assertThat(result).isEmpty();
            verify(userAIConfigRepository).findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L);

            // Second call should still miss
            service.findUserAIConfig(10L, 20L);
            verify(userAIConfigRepository, times(2)).findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L);
        }

        @Test
        @DisplayName("findUserAIConfig_cacheHit_returnsFromCacheWithoutDbQuery")
        void findUserAIConfig_cacheHit_returnsFromCacheWithoutDbQuery() throws Exception {
            final UserAIConfig config = new UserAIConfig();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("configCache");
            cache.put("config_10_20", createFreshCacheEntry(config));

            final Optional<UserAIConfig> result = service.findUserAIConfig(10L, 20L);

            assertThat(result).isPresent().contains(config);
            verifyNoInteractions(userAIConfigRepository);
        }

        @Test
        @DisplayName("findUserAIConfig_cacheExpired_queriesDbAgain")
        void findUserAIConfig_cacheExpired_queriesDbAgain() throws Exception {
            final UserAIConfig oldConfig = new UserAIConfig();
            final UserAIConfig newConfig = new UserAIConfig();

            final ConcurrentHashMap<String, Object> cache = getCacheMap("configCache");
            cache.put("config_10_20", createExpiredCacheEntry(oldConfig));

            when(userAIConfigRepository.findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L))
                    .thenReturn(Optional.of(newConfig));

            final Optional<UserAIConfig> result = service.findUserAIConfig(10L, 20L);

            assertThat(result).isPresent().contains(newConfig);
            verify(userAIConfigRepository).findByUserIdAndPatientIdAndIsActiveTrue(10L, 20L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // saveUserAIConfig
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveUserAIConfig")
    class SaveUserAIConfig {

        @Test
        @DisplayName("saveUserAIConfig_savesToDbAndUpdatesCache")
        void saveUserAIConfig_savesToDbAndUpdatesCache() throws Exception {
            final UserAIConfig config = new UserAIConfig();
            config.setUserId(10L);
            config.setPatientId(20L);

            final UserAIConfig saved = new UserAIConfig();
            saved.setUserId(10L);
            saved.setPatientId(20L);

            when(userAIConfigRepository.save(config)).thenReturn(saved);

            final UserAIConfig result = service.saveUserAIConfig(config);

            assertThat(result).isEqualTo(saved);
            verify(userAIConfigRepository).save(config);

            // Verify cache was updated — next find should hit cache
            final Optional<UserAIConfig> cached = service.findUserAIConfig(10L, 20L);
            assertThat(cached).isPresent().contains(saved);
            verifyNoMoreInteractions(userAIConfigRepository);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // findConversation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findConversation")
    class FindConversation {

        @Test
        @DisplayName("findConversation_cacheMiss_dbReturnsConversation_cachesAndReturns")
        void findConversation_cacheMiss_dbReturnsConversation_cachesAndReturns() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue("conv-1"))
                    .thenReturn(Optional.of(conversation));

            final Optional<ChatConversation> result = service.findConversation("conv-1");

            assertThat(result).isPresent().contains(conversation);
            verify(chatConversationRepository).findByConversationIdAndIsActiveTrue("conv-1");

            // Second call should hit cache
            final Optional<ChatConversation> cached = service.findConversation("conv-1");
            assertThat(cached).isPresent().contains(conversation);
            verifyNoMoreInteractions(chatConversationRepository);
        }

        @Test
        @DisplayName("findConversation_cacheMiss_dbReturnsEmpty_returnsEmptyAndDoesNotCache")
        void findConversation_cacheMiss_dbReturnsEmpty_returnsEmptyAndDoesNotCache() throws Exception {
            when(chatConversationRepository.findByConversationIdAndIsActiveTrue("conv-99"))
                    .thenReturn(Optional.empty());

            final Optional<ChatConversation> result = service.findConversation("conv-99");

            assertThat(result).isEmpty();
            verify(chatConversationRepository).findByConversationIdAndIsActiveTrue("conv-99");

            // Second call should still miss
            service.findConversation("conv-99");
            verify(chatConversationRepository, times(2)).findByConversationIdAndIsActiveTrue("conv-99");
        }

        @Test
        @DisplayName("findConversation_cacheHit_returnsFromCacheWithoutDbQuery")
        void findConversation_cacheHit_returnsFromCacheWithoutDbQuery() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("conversationCache");
            cache.put("conversation_conv-1", createFreshCacheEntry(conversation));

            final Optional<ChatConversation> result = service.findConversation("conv-1");

            assertThat(result).isPresent().contains(conversation);
            verifyNoInteractions(chatConversationRepository);
        }

        @Test
        @DisplayName("findConversation_cacheExpired_queriesDbAgain")
        void findConversation_cacheExpired_queriesDbAgain() throws Exception {
            final ChatConversation oldConv = new ChatConversation();
            final ChatConversation newConv = new ChatConversation();

            final ConcurrentHashMap<String, Object> cache = getCacheMap("conversationCache");
            cache.put("conversation_conv-1", createExpiredCacheEntry(oldConv));

            when(chatConversationRepository.findByConversationIdAndIsActiveTrue("conv-1"))
                    .thenReturn(Optional.of(newConv));

            final Optional<ChatConversation> result = service.findConversation("conv-1");

            assertThat(result).isPresent().contains(newConv);
            verify(chatConversationRepository).findByConversationIdAndIsActiveTrue("conv-1");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // saveConversation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("saveConversation")
    class SaveConversation {

        @Test
        @DisplayName("saveConversation_savesToDbAndUpdatesCache")
        void saveConversation_savesToDbAndUpdatesCache() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            final ChatConversation saved = new ChatConversation();
            saved.setConversationId("conv-1");

            when(chatConversationRepository.save(conversation)).thenReturn(saved);

            final ChatConversation result = service.saveConversation(conversation);

            assertThat(result).isEqualTo(saved);
            verify(chatConversationRepository).save(conversation);

            // Verify cache was updated — next find should hit cache
            final Optional<ChatConversation> cached = service.findConversation("conv-1");
            assertThat(cached).isPresent().contains(saved);
            verifyNoMoreInteractions(chatConversationRepository);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // evictPatient
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evictPatient")
    class EvictPatient {

        @Test
        @DisplayName("evictPatient_removesEntryFromCache")
        void evictPatient_removesEntryFromCache() throws Exception {
            final Patient patient = new Patient();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("patientCache");
            cache.put("patient_1", createFreshCacheEntry(patient));
            assertThat(cache).containsKey("patient_1");

            service.evictPatient(1L);

            assertThat(cache).doesNotContainKey("patient_1");
        }

        @Test
        @DisplayName("evictPatient_noEntryInCache_doesNotThrow")
        void evictPatient_noEntryInCache_doesNotThrow() throws Exception {
            service.evictPatient(999L);
            // Should not throw
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // evictUserAIConfig
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evictUserAIConfig")
    class EvictUserAIConfig {

        @Test
        @DisplayName("evictUserAIConfig_removesEntryFromCache")
        void evictUserAIConfig_removesEntryFromCache() throws Exception {
            final UserAIConfig config = new UserAIConfig();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("configCache");
            cache.put("config_10_20", createFreshCacheEntry(config));
            assertThat(cache).containsKey("config_10_20");

            service.evictUserAIConfig(10L, 20L);

            assertThat(cache).doesNotContainKey("config_10_20");
        }

        @Test
        @DisplayName("evictUserAIConfig_noEntryInCache_doesNotThrow")
        void evictUserAIConfig_noEntryInCache_doesNotThrow() throws Exception {
            service.evictUserAIConfig(999L, 888L);
            // Should not throw
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // evictConversation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("evictConversation")
    class EvictConversation {

        @Test
        @DisplayName("evictConversation_removesEntryFromCache")
        void evictConversation_removesEntryFromCache() throws Exception {
            final ChatConversation conversation = new ChatConversation();
            final ConcurrentHashMap<String, Object> cache = getCacheMap("conversationCache");
            cache.put("conversation_conv-1", createFreshCacheEntry(conversation));
            assertThat(cache).containsKey("conversation_conv-1");

            service.evictConversation("conv-1");

            assertThat(cache).doesNotContainKey("conversation_conv-1");
        }

        @Test
        @DisplayName("evictConversation_noEntryInCache_doesNotThrow")
        void evictConversation_noEntryInCache_doesNotThrow() throws Exception {
            service.evictConversation("nonexistent");
            // Should not throw
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // cleanupExpiredEntries
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cleanupExpiredEntries")
    class CleanupExpiredEntries {

        @Test
        @DisplayName("cleanupExpiredEntries_removesExpiredEntriesFromAllCaches")
        void cleanupExpiredEntries_removesExpiredEntriesFromAllCaches() throws Exception {
            final Patient patient = new Patient();
            final UserAIConfig config = new UserAIConfig();
            final ChatConversation conversation = new ChatConversation();

            getCacheMap("patientCache").put("patient_1", createExpiredCacheEntry(patient));
            getCacheMap("configCache").put("config_10_20", createExpiredCacheEntry(config));
            getCacheMap("conversationCache").put("conversation_conv-1", createExpiredCacheEntry(conversation));

            service.cleanupExpiredEntries();

            assertThat(getCacheMap("patientCache")).isEmpty();
            assertThat(getCacheMap("configCache")).isEmpty();
            assertThat(getCacheMap("conversationCache")).isEmpty();
        }

        @Test
        @DisplayName("cleanupExpiredEntries_keepsNonExpiredEntries")
        void cleanupExpiredEntries_keepsNonExpiredEntries() throws Exception {
            final Patient freshPatient = new Patient();
            final Patient expiredPatient = new Patient();

            getCacheMap("patientCache").put("patient_1", createFreshCacheEntry(freshPatient));
            getCacheMap("patientCache").put("patient_2", createExpiredCacheEntry(expiredPatient));

            service.cleanupExpiredEntries();

            final ConcurrentHashMap<String, Object> cache = getCacheMap("patientCache");
            assertThat(cache).hasSize(1);
            assertThat(cache).containsKey("patient_1");
            assertThat(cache).doesNotContainKey("patient_2");
        }

        @Test
        @DisplayName("cleanupExpiredEntries_emptyCaches_doesNotThrow")
        void cleanupExpiredEntries_emptyCaches_doesNotThrow() throws Exception {
            service.cleanupExpiredEntries();
            // Should not throw on empty caches
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // clearAllCaches
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("clearAllCaches")
    class ClearAllCaches {

        @Test
        @DisplayName("clearAllCaches_clearsAllThreeCaches")
        void clearAllCaches_clearsAllThreeCaches() throws Exception {
            final Patient patient = new Patient();
            final UserAIConfig config = new UserAIConfig();
            final ChatConversation conversation = new ChatConversation();

            getCacheMap("patientCache").put("patient_1", createFreshCacheEntry(patient));
            getCacheMap("configCache").put("config_10_20", createFreshCacheEntry(config));
            getCacheMap("conversationCache").put("conversation_conv-1", createFreshCacheEntry(conversation));

            service.clearAllCaches();

            assertThat(getCacheMap("patientCache")).isEmpty();
            assertThat(getCacheMap("configCache")).isEmpty();
            assertThat(getCacheMap("conversationCache")).isEmpty();
        }

        @Test
        @DisplayName("clearAllCaches_emptyCaches_doesNotThrow")
        void clearAllCaches_emptyCaches_doesNotThrow() throws Exception {
            service.clearAllCaches();
            // Should not throw on empty caches
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CacheEntry inner class
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CacheEntry")
    class CacheEntryTests {

        @Test
        @DisplayName("cacheEntry_getValue_returnsStoredValue")
        void cacheEntry_getValue_returnsStoredValue() throws Exception {
            final String testValue = "test-value";
            final Class<?> cacheEntryClass = Class.forName("com.careconnect.service.cache.AIChatCacheService$CacheEntry");
            final Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            final Object entry = ctor.newInstance(testValue);

            java.lang.reflect.Method getValueMethod = cacheEntryClass.getDeclaredMethod("getValue");
            getValueMethod.setAccessible(true);
            final Object result = getValueMethod.invoke(entry);

            assertThat(result).isEqualTo("test-value");
        }

        @Test
        @DisplayName("cacheEntry_isExpired_freshEntry_returnsFalse")
        void cacheEntry_isExpired_freshEntry_returnsFalse() throws Exception {
            final Class<?> cacheEntryClass = Class.forName("com.careconnect.service.cache.AIChatCacheService$CacheEntry");
            final Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            final Object entry = ctor.newInstance("value");

            java.lang.reflect.Method isExpiredMethod = cacheEntryClass.getDeclaredMethod("isExpired");
            isExpiredMethod.setAccessible(true);
            final boolean expired = (boolean) isExpiredMethod.invoke(entry);

            assertThat(expired).isFalse();
        }

        @Test
        @DisplayName("cacheEntry_isExpired_oldEntry_returnsTrue")
        void cacheEntry_isExpired_oldEntry_returnsTrue() throws Exception {
            final Class<?> cacheEntryClass = Class.forName("com.careconnect.service.cache.AIChatCacheService$CacheEntry");
            final Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            final Object entry = ctor.newInstance("value");

            // Set timestamp to 20 minutes ago
            final Field timestampField = cacheEntryClass.getDeclaredField("timestamp");
            timestampField.setAccessible(true);
            timestampField.set(entry, LocalDateTime.now().minusMinutes(20));

            java.lang.reflect.Method isExpiredMethod = cacheEntryClass.getDeclaredMethod("isExpired");
            isExpiredMethod.setAccessible(true);
            final boolean expired = (boolean) isExpiredMethod.invoke(entry);

            assertThat(expired).isTrue();
        }

        @Test
        @DisplayName("cacheEntry_getValue_nullValue_returnsNull")
        void cacheEntry_getValue_nullValue_returnsNull() throws Exception {
            final Class<?> cacheEntryClass = Class.forName("com.careconnect.service.cache.AIChatCacheService$CacheEntry");
            final Constructor<?> ctor = cacheEntryClass.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            final Object entry = ctor.newInstance((Object) null);

            java.lang.reflect.Method getValueMethod = cacheEntryClass.getDeclaredMethod("getValue");
            getValueMethod.setAccessible(true);
            final Object result = getValueMethod.invoke(entry);

            assertThat(result).isNull();
        }
    }
}
