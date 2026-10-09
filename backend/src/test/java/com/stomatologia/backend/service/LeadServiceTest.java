package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentSource;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentDto;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.dto.LeadDtos.LeadBookRequest;
import com.stomatologia.backend.dto.LeadDtos.LeadStats;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.ChatMessageRepository;
import com.stomatologia.backend.repository.ClinicServiceRepository;
import com.stomatologia.backend.repository.ConversationRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadServiceTest {

    private static final LocalDateTime VISIT = LocalDateTime.now().plusDays(3).withHour(11).withMinute(30)
            .withSecond(0).withNano(0);

    @Mock
    private LeadRepository leads;
    @Mock
    private ConversationRepository conversations;
    @Mock
    private ChatMessageRepository messages;
    @Mock
    private PatientRepository patients;
    @Mock
    private ClinicServiceRepository services;
    @Mock
    private DoctorRepository doctors;
    @Mock
    private AppointmentRepository appointments;
    @Mock
    private UserRepository users;
    @Mock
    private AppointmentService appointmentService;

    @InjectMocks
    private LeadService service;

    private Lead lead;
    private User registrar;

    @BeforeEach
    void setUp() {
        AuthUser me = new AuthUser(2L, "registrar", "Козлова Марина Сергеевна", Role.REGISTRAR, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(me, null, List.of()));
        registrar = new User();
        registrar.setFullName("Козлова Марина Сергеевна");
        when(users.getReferenceById(2L)).thenReturn(registrar);
        when(conversations.findLeadIdsWithConversation(anyCollection())).thenReturn(Set.of());

        lead = new Lead();
        lead.setId(10L);
        lead.setSource(LeadSource.TELEGRAM);
        lead.setName("Айгерим");
        lead.setPhone("+77015551234");
        when(leads.findById(10L)).thenReturn(Optional.of(lead));

        when(patients.saveAndFlush(any())).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        when(appointmentService.create(any(), any())).thenAnswer(inv -> appointmentDto(inv.getArgument(0)));
        when(appointments.getReferenceById(77L)).thenReturn(new Appointment());
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static AppointmentDto appointmentDto(AppointmentRequest r) {
        return new AppointmentDto(77L, r.patientId(), "Пациент", null, r.doctorId(), "Иванова Елена Петровна",
                "Стоматолог-терапевт", 1L, "101", r.serviceId(), "Лечение кариеса", new BigDecimal("27500"),
                r.startAt(), r.startAt().plusHours(1), AppointmentStatus.SCHEDULED, r.notes(), LocalDateTime.now(),
                AppointmentSource.MESSENGER);
    }

    private static LeadBookRequest newPatient(String lastName, String firstName) {
        return new LeadBookRequest(null, lastName, firstName, "8 701 555 12 34", 1L, 5L, VISIT, "Болит зуб");
    }

    private static HttpStatus statusOf(Throwable ex) {
        return ((ApiException) ex).getStatus();
    }

    @Test
    void bookingCreatesNewPatientAndLinksAppointment() {
        var dto = service.book(10L, newPatient("Сарсенова", "Айгерим"));

        ArgumentCaptor<Patient> patient = ArgumentCaptor.forClass(Patient.class);
        verify(patients).saveAndFlush(patient.capture());
        assertThat(patient.getValue().getFullName()).isEqualTo("Сарсенова Айгерим");
        assertThat(patient.getValue().getPhone()).isEqualTo("+77015551234");

        ArgumentCaptor<AppointmentRequest> request = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(appointmentService).create(request.capture(), eq(AppointmentSource.MESSENGER));
        assertThat(request.getValue().patientId()).isEqualTo(500L);
        assertThat(request.getValue().startAt()).isEqualTo(VISIT);

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.BOOKED);
        assertThat(lead.getPatient()).isSameAs(patient.getValue());
        assertThat(lead.getAssignedTo()).isSameAs(registrar);
        assertThat(dto.status()).isEqualTo(LeadStatus.BOOKED);
    }

    @Test
    void websiteLeadIsBookedWithWebsiteSource() {
        lead.setSource(LeadSource.WEBSITE);

        service.book(10L, newPatient("Ковалёв", "Дмитрий"));

        verify(appointmentService).create(any(), eq(AppointmentSource.WEBSITE));
    }

    @Test
    void existingPatientWithoutPhoneGetsPhoneFromLead() {
        Patient existing = new Patient();
        existing.setId(42L);
        existing.setLastName("Сарсенова");
        existing.setFirstName("Айгерим");
        when(patients.findById(42L)).thenReturn(Optional.of(existing));

        service.book(10L, new LeadBookRequest(42L, null, null, "+7 (701) 555-12-34", 1L, 5L, VISIT, null));

        assertThat(existing.getPhone()).isEqualTo("+77015551234");
        verify(patients, never()).saveAndFlush(any());
        assertThat(lead.getPatient()).isSameAs(existing);
    }

    @Test
    void newPatientRequiresLastAndFirstName() {
        assertThatThrownBy(() -> service.book(10L, newPatient(" ", "Айгерим")))
                .hasMessageContaining("фамилию и имя")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(appointmentService, never()).create(any(), any());
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    void failedBookingLeavesLeadOpen() {
        doThrow(ApiException.conflict("Врач Иванова Елена Петровна уже занят с 11:00 до 12:00"))
                .when(appointmentService).create(any(), any());

        assertThatThrownBy(() -> service.book(10L, newPatient("Сарсенова", "Айгерим")))
                .hasMessageContaining("уже занят");
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.getAppointment()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = LeadStatus.class, names = {"BOOKED", "REJECTED"})
    void closedLeadCannotBeBookedOrRejected(LeadStatus status) {
        lead.setStatus(status);

        assertThatThrownBy(() -> service.book(10L, newPatient("Сарсенова", "Айгерим")))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.reject(10L, "Дубль"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void takeAssignsCurrentRegistrar() {
        lead.setStatus(LeadStatus.NEEDS_OPERATOR);

        service.take(10L);

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.IN_PROGRESS);
        assertThat(lead.getAssignedTo()).isSameAs(registrar);
    }

    @Test
    void rejectedLeadCanBeReopened() {
        service.reject(10L, "  Передумал  ");
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.REJECTED);
        assertThat(lead.getRejectReason()).isEqualTo("Передумал");

        service.reopen(10L);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.IN_PROGRESS);
        assertThat(lead.getRejectReason()).isNull();
    }

    @Test
    void onlyRejectedLeadCanBeReopened() {
        assertThatThrownBy(() -> service.reopen(10L))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void matchingPatientsSearchByLastTenDigits() {
        service.matchingPatients(10L);

        verify(patients).findByPhoneDigits("7015551234");
    }

    @Test
    void conversionIsShareOfBookedLeadsForThirtyDays() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        when(leads.countByCreatedAtGreaterThanEqual(any())).thenAnswer(inv ->
                inv.<LocalDateTime>getArgument(0).isBefore(todayStart) ? 20L : 3L);
        when(leads.countByCreatedAtGreaterThanEqualAndStatus(any(), eq(LeadStatus.BOOKED))).thenReturn(7L);
        when(leads.countByStatusIn(LeadStatus.OPEN)).thenReturn(4L);

        LeadStats stats = service.stats();

        assertThat(stats.conversionPercent()).isEqualTo(35);
        assertThat(stats.newToday()).isEqualTo(3);
        assertThat(stats.open()).isEqualTo(4);
    }
}
