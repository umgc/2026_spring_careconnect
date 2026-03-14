package com.careconnect.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatientContextRetrievalServiceTest {

    private PatientContextRetrievalService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PatientContextRetrievalService();
    }

    // ── Constructor ──

    @Test
    @DisplayName("constructor_createsInstance_serviceIsNotNull")
    void constructor_createsInstance_serviceIsNotNull() throws Exception {
        final PatientContextRetrievalService svc = new PatientContextRetrievalService();
        assertNotNull(svc);
    }

    // ── indexPatientContext ──

    @Test
    @DisplayName("indexPatientContext_withMultilineContext_indexesNonEmptySegments")
    void indexPatientContext_withMultilineContext_indexesNonEmptySegments() throws Exception {
        service.indexPatientContext(1L, "Diabetes\nHypertension\nAsthma");

        final List<String> results = service.retrieveRelevantContext("Diabetes", 10);
        assertEquals(1, results.size());
        assertEquals("Diabetes", results.get(0));
    }

    @Test
    @DisplayName("indexPatientContext_withEmptyLines_skipsEmptySegments")
    void indexPatientContext_withEmptyLines_skipsEmptySegments() throws Exception {
        service.indexPatientContext(1L, "Diabetes\n\n  \nHypertension");

        // Only non-empty segments should be indexed
        final List<String> allDiabetes = service.retrieveRelevantContext("Diabetes", 10);
        final List<String> allHyper = service.retrieveRelevantContext("Hypertension", 10);
        assertEquals(1, allDiabetes.size());
        assertEquals(1, allHyper.size());
    }

    @Test
    @DisplayName("indexPatientContext_calledTwice_clearsOldSegments")
    void indexPatientContext_calledTwice_clearsOldSegments() throws Exception {
        service.indexPatientContext(1L, "Diabetes\nHypertension");
        service.indexPatientContext(2L, "Asthma");

        final List<String> diabetesResults = service.retrieveRelevantContext("Diabetes", 10);
        assertTrue(diabetesResults.isEmpty(), "Old segments should be cleared");

        final List<String> asthmaResults = service.retrieveRelevantContext("Asthma", 10);
        assertEquals(1, asthmaResults.size());
    }

    @Test
    @DisplayName("indexPatientContext_withWhitespaceOnlyContent_indexesNoSegments")
    void indexPatientContext_withWhitespaceOnlyContent_indexesNoSegments() throws Exception {
        service.indexPatientContext(1L, "   \n  \n   ");

        final List<String> results = service.retrieveRelevantContext("anything", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("indexPatientContext_trimsSegments_storedWithoutLeadingTrailingSpaces")
    void indexPatientContext_trimsSegments_storedWithoutLeadingTrailingSpaces() throws Exception {
        service.indexPatientContext(1L, "  Diabetes  \n  Hypertension  ");

        final List<String> results = service.retrieveRelevantContext("Diabetes", 10);
        assertEquals(1, results.size());
        assertEquals("Diabetes", results.get(0));
    }

    // ── retrieveRelevantContext ──

    @Test
    @DisplayName("retrieveRelevantContext_noMatchingSegments_returnsEmptyList")
    void retrieveRelevantContext_noMatchingSegments_returnsEmptyList() throws Exception {
        service.indexPatientContext(1L, "Diabetes\nHypertension");

        final List<String> results = service.retrieveRelevantContext("Cancer", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("retrieveRelevantContext_caseInsensitiveMatch_returnsMatchingSegments")
    void retrieveRelevantContext_caseInsensitiveMatch_returnsMatchingSegments() throws Exception {
        service.indexPatientContext(1L, "Diabetes Type 2\ndiabetes management");

        final List<String> results = service.retrieveRelevantContext("DIABETES", 10);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("retrieveRelevantContext_topKLimitsResults_returnsOnlyTopK")
    void retrieveRelevantContext_topKLimitsResults_returnsOnlyTopK() throws Exception {
        service.indexPatientContext(1L, "med A\nmed B\nmed C\nmed D");

        final List<String> results = service.retrieveRelevantContext("med", 2);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("retrieveRelevantContext_topKGreaterThanMatches_returnsAllMatches")
    void retrieveRelevantContext_topKGreaterThanMatches_returnsAllMatches() throws Exception {
        service.indexPatientContext(1L, "med A\nmed B");

        final List<String> results = service.retrieveRelevantContext("med", 100);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("retrieveRelevantContext_emptyContextSegments_returnsEmptyList")
    void retrieveRelevantContext_emptyContextSegments_returnsEmptyList() throws Exception {
        // No indexing called, contextSegments should be empty
        final List<String> results = service.retrieveRelevantContext("anything", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("retrieveRelevantContext_emptyQuery_returnsAllSegments")
    void retrieveRelevantContext_emptyQuery_returnsAllSegments() throws Exception {
        service.indexPatientContext(1L, "Diabetes\nHypertension\nAsthma");

        // An empty string is contained in every string
        final List<String> results = service.retrieveRelevantContext("", 10);
        assertEquals(3, results.size());
    }

    @Test
    @DisplayName("retrieveRelevantContext_topKZero_returnsEmptyList")
    void retrieveRelevantContext_topKZero_returnsEmptyList() throws Exception {
        service.indexPatientContext(1L, "Diabetes\nHypertension");

        final List<String> results = service.retrieveRelevantContext("Diabetes", 0);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("indexPatientContext_singleLineContext_indexesOneSegment")
    void indexPatientContext_singleLineContext_indexesOneSegment() throws Exception {
        service.indexPatientContext(1L, "Diabetes Type 2");

        final List<String> results = service.retrieveRelevantContext("Diabetes", 10);
        assertEquals(1, results.size());
        assertEquals("Diabetes Type 2", results.get(0));
    }
}
