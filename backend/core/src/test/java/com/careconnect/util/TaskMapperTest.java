package com.careconnect.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TaskMapper}.
 *
 * <p>
 * These tests validate correct JSON serialization and deserialization
 * of the {@code daysOfWeek} list used in recurring task definitions.
 * </p>
 */
class TaskMapperTest {

    // --------------------------------------------------------------------------
    // parseDays() Tests
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("parseDays should correctly parse valid JSON string into List<Boolean>")
    void testParseDays_validJson() throws Exception {
        final String json = "[true,false,true,false,false,true,false]";

        final List<Boolean> result = TaskMapper.parseDays(json);

        assertNotNull(result);
        assertEquals(7, result.size());
        assertEquals(List.of(true, false, true, false, false, true, false), result);
    }

    @Test
    @DisplayName("parseDays should return null for null input")
    void testParseDays_nullInput() throws Exception {
        assertNull(TaskMapper.parseDays(null));
    }

    @Test
    @DisplayName("parseDays should return null for malformed JSON")
    void testParseDays_malformedJson() {
        String badJson = "[true, false, invalid, false]";
        assertNull(TaskMapper.parseDays(badJson));
    }

    @Test
    @DisplayName("parseDays should parse legacy CSV day names")
    void testParseDays_legacyCsvDayNames() {
        String csv = "MON,WED,FRI";

        List<Boolean> result = TaskMapper.parseDays(csv);

        assertNotNull(result);
        assertEquals(7, result.size());
        assertEquals(List.of(false, true, false, true, false, true, false), result);
    }

    @Test
    @DisplayName("parseDays should parse JSON array of day names")
    void testParseDays_jsonArrayDayNames() {
        String json = "[\"sunday\",\"Tuesday\",\"thursday\"]";

        List<Boolean> result = TaskMapper.parseDays(json);

        assertNotNull(result);
        assertEquals(7, result.size());
        assertEquals(List.of(true, false, true, false, true, false, false), result);
    }

    @Test
    @DisplayName("parseDays should parse double-encoded JSON boolean array")
    void testParseDays_doubleEncodedJson() {
        String json = "\"[true,false,true,false,false,true,false]\"";

        List<Boolean> result = TaskMapper.parseDays(json);

        assertNotNull(result);
        assertEquals(7, result.size());
        assertEquals(List.of(true, false, true, false, false, true, false), result);
    }

    // --------------------------------------------------------------------------
    // serializeDays() Tests
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("serializeDays should correctly convert List<Boolean> to JSON string")
    void testSerializeDays_validList() throws Exception {
        final List<Boolean> days = List.of(true, false, true, false, false, true, false);

        final String result = TaskMapper.serializeDays(days);

        assertEquals("[true,false,true,false,false,true,false]", result);
    }

    @Test
    @DisplayName("serializeDays should return null for null input")
    void testSerializeDays_nullInput() throws Exception {
        assertNull(TaskMapper.serializeDays(null));
    }

    @Test
    @DisplayName("serializeDays should throw RuntimeException when serialization fails")
    @SuppressWarnings("unchecked")
    void testSerializeDays_serializationFailure() throws Exception {
        final List<Boolean> badList = mock(List.class);
        when(badList.size()).thenReturn(1);
        when(badList.get(anyInt())).thenThrow(new RuntimeException("mock error"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> TaskMapper.serializeDays(badList));
        assertTrue(ex.getMessage().contains("Failed to serialize daysOfWeek JSON"));
    }

    // --------------------------------------------------------------------------
    // Round-Trip & Constructor Tests
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("serializeDays and parseDays should be inverses (round-trip test)")
    void testRoundTrip_serializeAndParse() throws Exception {
        final List<Boolean> original = List.of(true, false, true, false, true, false, false);

        final String json = TaskMapper.serializeDays(original);
        final List<Boolean> parsedBack = TaskMapper.parseDays(json);

        assertEquals(original, parsedBack);
    }

    @Test
    @DisplayName("TaskMapper default constructor should be instantiable")
    void testConstructor() throws Exception {
        final TaskMapper mapper = new TaskMapper();
        assertNotNull(mapper);
    }
}
