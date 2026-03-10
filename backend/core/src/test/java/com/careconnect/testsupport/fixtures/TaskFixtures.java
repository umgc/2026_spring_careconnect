package com.careconnect.testsupport.fixtures;

import java.time.LocalDate;

import com.careconnect.dto.v2.TaskDtoV2;
import com.careconnect.model.Task;

/**
 * Shared task fixture builders for backend unit tests.
 *
 * <p>
 * These fixtures intentionally keep data local and deterministic so unit tests
 * can validate service/controller logic without live DB or external calls.
 * </p>
 */
public final class TaskFixtures {

    private TaskFixtures() {
        // Utility class
    }

    /**
     * Returns a baseline persisted-like task for read/update test scenarios.
     *
     * <p>
     * Use this when the test needs a realistic task entity with stable defaults.
     * </p>
     */
    public static Task basicTask() {
        return Task.builder()
                .id(1L)
                .name("Check Vitals")
                .description("Daily vitals check")
                .date(LocalDate.of(2025, 1, 1).toString())
                .timeOfDay("08:00")
                .isCompleted(false)
                .taskType("Health")
                .frequency("daily")
                .taskInterval(1)
                .doCount(1)
                .build();
    }

    /**
     * Returns the baseline task with an explicit ID override.
     *
     * <p>
     * Use when tests need multiple distinct task IDs while keeping the same
     * semantic payload.
     * </p>
     */
    public static Task taskWithId(Long id) {
        Task task = basicTask();
        task.setId(id);
        return task;
    }

    /**
     * Returns a baseline task DTO for read/list controller responses.
     *
     * <p>
     * Use in controller tests when JSON serialization behavior is under test.
     * </p>
     */
    public static TaskDtoV2 taskDtoBasic() {
        return TaskDtoV2.builder()
                .id(1L)
                .name("Check Blood Pressure")
                .description("Daily vitals check")
                .date(LocalDate.of(2025, 1, 1).toString())
                .timeOfDay("08:00")
                .isCompleted(false)
                .taskType("Health")
                .frequency("daily")
                .interval(1)
                .count(1)
                .build();
    }

    /**
     * Returns a create-request style DTO (no ID) for service creation paths.
     *
     * <p>
     * Use in tests that exercise mapping from inbound request DTOs to entities.
     * </p>
     */
    public static TaskDtoV2 taskDtoForCreate() {
        return TaskDtoV2.builder()
                .name("Daily Check")
                .description("Measure blood pressure")
                .date(LocalDate.of(2025, 1, 1).toString())
                .timeOfDay("08:00")
                .frequency("daily")
                .interval(1)
                .count(1)
                .taskType("Health")
                .isCompleted(false)
                .build();
    }
}
