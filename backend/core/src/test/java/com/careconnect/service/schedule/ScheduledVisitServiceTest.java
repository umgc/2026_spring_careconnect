package com.careconnect.service.schedule;

import com.careconnect.dto.schedule.ScheduledVisitRequest;
import com.careconnect.dto.schedule.ScheduledVisitResponse;
import com.careconnect.dto.schedule.ScheduledVisitSummary;
import com.careconnect.model.Patient;
import com.careconnect.model.schedule.ScheduledVisit;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.schedule.ScheduledVisitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledVisitServiceTest {

    @Mock
    private ScheduledVisitRepository scheduledVisitRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private ScheduledVisitService scheduledVisitService;

    private ScheduledVisit buildVisit(Long id, Long caregiverId, Long patientId) {
        final ScheduledVisit visit = new ScheduledVisit();
        visit.setId(id);
        visit.setCaregiverId(caregiverId);
        visit.setPatientId(patientId);
        visit.setServiceType("HOME_HEALTH");
        visit.setScheduledDate(LocalDate.of(2025, 6, 15));
        visit.setScheduledTime(LocalTime.of(9, 0));
        visit.setDurationMinutes(60);
        visit.setPriority("Normal");
        visit.setNotes("Test notes");
        visit.setStatus("Scheduled");
        return visit;
    }

    private ScheduledVisitRequest buildRequest() throws Exception {
        final ScheduledVisitRequest req = new ScheduledVisitRequest();
        req.setPatientId(10L);
        req.setServiceType("HOME_HEALTH");
        req.setScheduledDate(LocalDate.of(2025, 6, 15));
        req.setScheduledTime(LocalTime.of(9, 0));
        req.setDurationMinutes(60);
        req.setPriority("Normal");
        req.setNotes("Notes");
        return req;
    }

    private Patient buildPatient(String firstName, String lastName) {
        final Patient patient = mock(Patient.class);
        when(patient.getFirstName()).thenReturn(firstName);
        when(patient.getLastName()).thenReturn(lastName);
        return patient;
    }

    // ----- createScheduledVisit -----

    @Test
    void createScheduledVisit_savesAndReturnsResponse() throws Exception {
        final ScheduledVisitRequest request = buildRequest();
        final ScheduledVisit savedVisit = buildVisit(1L, 5L, 10L);
        final Patient johnDoe = buildPatient("John", "Doe");
        when(scheduledVisitRepository.save(any(ScheduledVisit.class))).thenReturn(savedVisit);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(johnDoe));

        final ScheduledVisitResponse response = scheduledVisitService.createScheduledVisit(5L, request);

        assertThat(response).isNotNull();
        assertThat(response.getCaregiverId()).isEqualTo(5L);
        assertThat(response.getPatientName()).isEqualTo("John Doe");
        assertThat(response.getStatus()).isEqualTo("Scheduled");
    }

    @Test
    void createScheduledVisit_patientNotFound_returnsUnknownPatient() throws Exception {
        final ScheduledVisitRequest request = buildRequest();
        final ScheduledVisit savedVisit = buildVisit(1L, 5L, 10L);
        when(scheduledVisitRepository.save(any(ScheduledVisit.class))).thenReturn(savedVisit);
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());

        final ScheduledVisitResponse response = scheduledVisitService.createScheduledVisit(5L, request);

        assertThat(response.getPatientName()).isEqualTo("Unknown Patient");
    }

    // ----- getScheduledVisits -----

    @Test
    void getScheduledVisits_returnsList() throws Exception {
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        final Patient janeSmith = buildPatient("Jane", "Smith");
        when(scheduledVisitRepository.findByCaregiverId(5L)).thenReturn(List.of(visit));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(janeSmith));

        final List<ScheduledVisitResponse> result = scheduledVisitService.getScheduledVisits(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPatientName()).isEqualTo("Jane Smith");
    }

    @Test
    void getScheduledVisits_emptyList() throws Exception {
        when(scheduledVisitRepository.findByCaregiverId(5L)).thenReturn(List.of());

        final List<ScheduledVisitResponse> result = scheduledVisitService.getScheduledVisits(5L);

        assertThat(result).isEmpty();
    }

    // ----- getScheduledVisitsByDate -----

    @Test
    void getScheduledVisitsByDate_returnsList() throws Exception {
        final LocalDate date = LocalDate.of(2025, 6, 15);
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        when(scheduledVisitRepository.findByCaregiverIdAndScheduledDate(5L, date)).thenReturn(List.of(visit));
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());

        final List<ScheduledVisitResponse> result = scheduledVisitService.getScheduledVisitsByDate(5L, date);

        assertThat(result).hasSize(1);
    }

    // ----- getScheduledVisitsBetweenDates -----

    @Test
    void getScheduledVisitsBetweenDates_returnsList() throws Exception {
        final LocalDate start = LocalDate.of(2025, 6, 1);
        final LocalDate end = LocalDate.of(2025, 6, 30);
        when(scheduledVisitRepository.findByCaregiverIdAndScheduledDateBetween(5L, start, end))
                .thenReturn(List.of());

        final List<ScheduledVisitResponse> result =
                scheduledVisitService.getScheduledVisitsBetweenDates(5L, start, end);

        assertThat(result).isEmpty();
    }

    // ----- getVisitSummary -----

    @Test
    void getVisitSummary_returnsCorrectCounts() throws Exception {
        when(scheduledVisitRepository.countOverdueVisits(eq(5L), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(2L);
        when(scheduledVisitRepository.countReadyVisits(eq(5L), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(1L);
        when(scheduledVisitRepository.countUpcomingVisits(eq(5L), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(3L);
        when(scheduledVisitRepository.countTodayVisits(eq(5L), any(LocalDate.class)))
                .thenReturn(6L);

        final ScheduledVisitSummary summary = scheduledVisitService.getVisitSummary(5L);

        assertThat(summary.getOverdue()).isEqualTo(2L);
        assertThat(summary.getReady()).isEqualTo(1L);
        assertThat(summary.getUpcoming()).isEqualTo(3L);
        assertThat(summary.getTotalToday()).isEqualTo(6L);
    }

    // ----- getOverdueVisits -----

    @Test
    void getOverdueVisits_returnsList() throws Exception {
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        when(scheduledVisitRepository.findOverdueVisits(eq(5L), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(visit));
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());

        final List<ScheduledVisitResponse> result = scheduledVisitService.getOverdueVisits(5L);

        assertThat(result).hasSize(1);
    }

    // ----- getReadyVisits -----

    @Test
    void getReadyVisits_emptyList() throws Exception {
        when(scheduledVisitRepository.findReadyVisits(eq(5L), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of());

        final List<ScheduledVisitResponse> result = scheduledVisitService.getReadyVisits(5L);

        assertThat(result).isEmpty();
    }

    // ----- getUpcomingVisits -----

    @Test
    void getUpcomingVisits_emptyList() throws Exception {
        when(scheduledVisitRepository.findUpcomingVisits(eq(5L), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of());

        final List<ScheduledVisitResponse> result = scheduledVisitService.getUpcomingVisits(5L);

        assertThat(result).isEmpty();
    }

    // ----- getScheduledVisit -----

    @Test
    void getScheduledVisit_found_returnsResponse() throws Exception {
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        final Patient maryJones = buildPatient("Mary", "Jones");
        when(scheduledVisitRepository.findById(1L)).thenReturn(Optional.of(visit));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(maryJones));

        final ScheduledVisitResponse response = scheduledVisitService.getScheduledVisit(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getPatientName()).isEqualTo("Mary Jones");
    }

    @Test
    void getScheduledVisit_notFound_throwsRuntime() throws Exception {
        when(scheduledVisitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledVisitService.getScheduledVisit(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Scheduled visit not found with id: 99");
    }

    // ----- updateScheduledVisit -----

    @Test
    void updateScheduledVisit_found_updatesAndReturns() throws Exception {
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        when(scheduledVisitRepository.findById(1L)).thenReturn(Optional.of(visit));
        when(scheduledVisitRepository.save(visit)).thenReturn(visit);
        when(patientRepository.findById(anyLong())).thenReturn(Optional.empty());

        final ScheduledVisitResponse response = scheduledVisitService.updateScheduledVisit(1L, buildRequest());

        assertThat(response).isNotNull();
        verify(scheduledVisitRepository).save(visit);
    }

    @Test
    void updateScheduledVisit_notFound_throws() throws Exception {
        when(scheduledVisitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledVisitService.updateScheduledVisit(99L, buildRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ----- cancelScheduledVisit -----

    @Test
    void cancelScheduledVisit_found_marksCancelled() throws Exception {
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        when(scheduledVisitRepository.findById(1L)).thenReturn(Optional.of(visit));
        when(scheduledVisitRepository.save(visit)).thenReturn(visit);

        scheduledVisitService.cancelScheduledVisit(1L);

        assertThat(visit.getStatus()).isEqualTo("Cancelled");
        verify(scheduledVisitRepository).save(visit);
    }

    @Test
    void cancelScheduledVisit_notFound_throws() throws Exception {
        when(scheduledVisitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledVisitService.cancelScheduledVisit(99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ----- updateVisitStatus -----

    @Test
    void updateVisitStatus_found_setsStatusAndReturns() throws Exception {
        final ScheduledVisit visit = buildVisit(1L, 5L, 10L);
        when(scheduledVisitRepository.findById(1L)).thenReturn(Optional.of(visit));
        when(scheduledVisitRepository.save(visit)).thenReturn(visit);
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());

        final ScheduledVisitResponse response = scheduledVisitService.updateVisitStatus(1L, "In Progress");

        assertThat(visit.getStatus()).isEqualTo("In Progress");
        assertThat(response).isNotNull();
    }

    @Test
    void updateVisitStatus_notFound_throws() throws Exception {
        when(scheduledVisitRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledVisitService.updateVisitStatus(99L, "In Progress"))
                .isInstanceOf(RuntimeException.class);
    }

    // ----- deleteScheduledVisit -----

    @Test
    void deleteScheduledVisit_callsDeleteById() throws Exception {
        scheduledVisitService.deleteScheduledVisit(1L);

        verify(scheduledVisitRepository).deleteById(1L);
    }
}
