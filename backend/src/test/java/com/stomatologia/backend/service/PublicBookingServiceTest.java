package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentSource;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Lead;
import com.stomatologia.backend.domain.LeadSource;
import com.stomatologia.backend.domain.LeadStatus;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Room;
import com.stomatologia.backend.domain.Specialty;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.dto.AppointmentDtos.SlotDto;
import com.stomatologia.backend.dto.PublicDtos.BookingInfo;
import com.stomatologia.backend.dto.PublicDtos.BookingRequest;
import com.stomatologia.backend.report.WordDocuments;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.LeadRepository;
import com.stomatologia.backend.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicBookingServiceTest {

    private static final LocalDateTime VISIT = LocalDateTime.now().plusDays(3).withHour(11).withMinute(30)
            .withSecond(0).withNano(0);

    @Mock
    private AppointmentService appointmentService;
    @Mock
    private SlotService slots;
    @Mock
    private ClinicSettingsService clinic;
    @Mock
    private PatientRepository patients;
    @Mock
    private AppointmentRepository appointments;
    @Mock
    private LeadRepository leads;
    @Mock
    private WordDocuments word;

    @InjectMocks
    private PublicBookingService service;

    private ClinicSettings settings;
    private Doctor doctor;
    private ClinicService treatment;

    @BeforeEach
    void setUp() {
        settings = new ClinicSettings();
        settings.setPhone("+7 (7182) 00-00-00");
        settings.setPatientCancelHours(24);
        when(clinic.current()).thenReturn(settings);

        Specialty therapist = new Specialty();
        therapist.setName("Стоматолог-терапевт");
        Room room = new Room();
        room.setNumber("101");
        doctor = new Doctor();
        doctor.setId(2L);
        doctor.setFullName("Иванова Елена Петровна");
        doctor.setSpecialty(therapist);
        doctor.setRoom(room);
        treatment = new ClinicService();
        treatment.setId(5L);
        treatment.setName("Лечение кариеса");
        treatment.setPrice(new BigDecimal("27500"));
        treatment.setDurationMinutes(60);

        when(patients.findByPhoneDigits(anyString())).thenReturn(List.of());
        when(patients.saveAndFlush(any())).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });
        when(appointmentService.createOnline(any(), any()))
                .thenAnswer(inv -> appointment(inv.getArgument(0), inv.getArgument(1)));
        when(leads.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Appointment appointment(Patient patient, AppointmentRequest r) {
        Appointment a = new Appointment();
        a.setId(77L);
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setRoom(doctor.getRoom());
        a.setService(treatment);
        a.setStartAt(r.startAt());
        a.setEndAt(r.startAt().plusMinutes(60));
        a.setNotes(r.notes());
        a.setSource(AppointmentSource.WEBSITE);
        return a;
    }

    private static BookingRequest request(Long doctorId, String phone, boolean consent, String honeypot) {
        return new BookingRequest(5L, doctorId, VISIT, " Сарсенова ", "Айгерим", phone, "Болит зуб", consent, honeypot);
    }

    private static BookingRequest request() {
        return request(2L, "8 (701) 555-12-34", true, null);
    }

    private static Patient patient(long id, String lastName, String firstName) {
        Patient p = new Patient();
        p.setId(id);
        p.setLastName(lastName);
        p.setFirstName(firstName);
        p.setPhone("+77015551234");
        return p;
    }

    private Lead savedLead() {
        ArgumentCaptor<Lead> lead = ArgumentCaptor.forClass(Lead.class);
        verify(leads).saveAndFlush(lead.capture());
        return lead.getValue();
    }

    private static HttpStatus statusOf(Throwable ex) {
        return ((ApiException) ex).getStatus();
    }

    @Test
    void newClientGetsCardAppointmentAndUnconfirmedLead() {
        BookingInfo info = service.book(request());

        ArgumentCaptor<Patient> patient = ArgumentCaptor.forClass(Patient.class);
        verify(patients).saveAndFlush(patient.capture());
        assertThat(patient.getValue().getFullName()).isEqualTo("Сарсенова Айгерим");
        assertThat(patient.getValue().getPhone()).isEqualTo("+77015551234");

        Lead lead = savedLead();
        assertThat(lead.getSource()).isEqualTo(LeadSource.WEBSITE);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.BOOKED);
        assertThat(lead.awaitsConfirmation()).isTrue();
        assertThat(lead.getName()).isEqualTo("Айгерим Сарсенова");
        assertThat(lead.getConsentAt()).isNotNull();
        assertThat(lead.getSummary()).isEqualTo("Болит зуб");
        assertThat(lead.getPublicToken()).hasSize(32);

        assertThat(info.token()).isEqualTo(lead.getPublicToken());
        assertThat(info.startAt()).isEqualTo(VISIT);
        assertThat(info.doctorName()).isEqualTo("Иванова Елена Петровна");
        assertThat(info.confirmed()).isFalse();
        assertThat(info.cancellable()).isTrue();
        assertThat(info.cancelDeadline()).isEqualTo(VISIT.minusHours(24));
    }

    @Test
    void knownPatientWithSamePhoneAndNameIsReused() {
        Patient known = patient(42L, "САРСЕНОВА", "айгерим");
        when(patients.findByPhoneDigits("7015551234")).thenReturn(List.of(known));

        service.book(request());

        verify(patients, never()).saveAndFlush(any());
        verify(appointmentService).createOnline(eq(known), any());
    }

    @Test
    void samePhoneButOtherNameGetsSeparateCard() {
        when(patients.findByPhoneDigits("7015551234")).thenReturn(List.of(patient(42L, "Сарсенов", "Ерлан")));

        service.book(request());

        verify(patients).saveAndFlush(any());
    }

    @Test
    void filledHoneypotLooksLikeBot() {
        assertThatThrownBy(() -> service.book(request(2L, "+77015551234", true, "http://spam")))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(appointmentService, never()).createOnline(any(), any());
    }

    @Test
    void consentIsRequired() {
        assertThatThrownBy(() -> service.book(request(2L, "+77015551234", false, null)))
                .hasMessageContaining("согласие");
        verify(appointmentService, never()).createOnline(any(), any());
    }

    @Test
    void incompletePhoneIsRejected() {
        assertThatThrownBy(() -> service.book(request(2L, "555-12-34", true, null)))
                .hasMessageContaining("номер телефона")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void notMoreThanTwoUpcomingOnlineBookingsPerPhone() {
        when(patients.findByPhoneDigits("7015551234")).thenReturn(List.of(patient(42L, "Сарсенова", "Айгерим")));
        when(appointments.countUpcoming(anyCollection(), eq(AppointmentSource.WEBSITE), any())).thenReturn(2L);

        assertThatThrownBy(() -> service.book(request()))
                .hasMessageContaining("уже есть 2")
                .hasMessageContaining(settings.getPhone())
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        verify(appointmentService, never()).createOnline(any(), any());
    }

    @Test
    void anyDoctorTakesTheOneFreeAtChosenTime() {
        when(slots.freeSlots(null, 5L, VISIT.toLocalDate(), true)).thenReturn(List.of(
                new SlotDto(3L, "Петров Андрей Викторович", "102", VISIT.minusMinutes(30), VISIT.plusMinutes(30)),
                new SlotDto(2L, "Иванова Елена Петровна", "101", VISIT, VISIT.plusHours(1))));

        service.book(request(null, "+77015551234", true, null));

        ArgumentCaptor<AppointmentRequest> r = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(appointmentService).createOnline(any(), r.capture());
        assertThat(r.getValue().doctorId()).isEqualTo(2L);
    }

    @Test
    void anyDoctorWhenTimeIsAlreadyTaken() {
        when(slots.freeSlots(null, 5L, VISIT.toLocalDate(), true)).thenReturn(List.of());

        assertThatThrownBy(() -> service.book(request(null, "+77015551234", true, null)))
                .hasMessageContaining("уже заняли")
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancelByLinkClosesLead() {
        Lead lead = bookedLead(VISIT);

        service.cancel("token");

        verify(appointmentService).cancelOnline(77L, PublicBookingService.CANCEL_REASON);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.REJECTED);
        assertThat(lead.getRejectReason()).isEqualTo(PublicBookingService.CANCEL_REASON);
    }

    @Test
    void cancellationDeadlinePassedShortlyBeforeVisit() {
        bookedLead(LocalDateTime.now().plusHours(5));

        BookingInfo info = service.get("token");

        assertThat(info.cancellable()).isFalse();
    }

    @Test
    void unknownLinkIsNotFound() {
        when(leads.findByPublicToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void noTicketForCancelledAppointment() {
        bookedLead(VISIT).getAppointment().setStatus(AppointmentStatus.CANCELLED);

        assertThatThrownBy(() -> service.ticket("token"))
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        verify(word, never()).ticket(any(), any(), any());
    }

    @Test
    void tokensAreUnpredictable() {
        assertThat(PublicBookingService.newToken()).hasSize(32).isNotEqualTo(PublicBookingService.newToken());
    }

    private Lead bookedLead(LocalDateTime start) {
        Lead lead = new Lead();
        lead.setId(9L);
        lead.setSource(LeadSource.WEBSITE);
        lead.setStatus(LeadStatus.BOOKED);
        lead.setPublicToken("token");
        lead.setAppointment(appointment(patient(42L, "Сарсенова", "Айгерим"),
                new AppointmentRequest(42L, 2L, 5L, start, null)));
        when(leads.findByPublicToken("token")).thenReturn(Optional.of(lead));
        return lead;
    }
}
