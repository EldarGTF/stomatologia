package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.AuditAction;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Holiday;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.Room;
import com.stomatologia.backend.domain.Schedule;
import com.stomatologia.backend.domain.Specialty;
import com.stomatologia.backend.dto.AppointmentDtos.AppointmentRequest;
import com.stomatologia.backend.repository.AppointmentAuditRepository;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.ScheduleRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentServiceTest {

    private static final Long DOCTOR_ID = 10L;
    private static final Long PATIENT_ID = 100L;

    @Mock
    private AppointmentRepository appointments;
    @Mock
    private AppointmentAuditRepository audit;
    @Mock
    private DoctorRepository doctors;
    @Mock
    private PatientRepository patients;
    @Mock
    private ScheduleRepository schedules;
    @Mock
    private UserRepository users;
    @Mock
    private ServiceCatalogService catalog;
    @Mock
    private InvoiceService invoices;
    @Mock
    private ClinicSettingsService clinic;

    @InjectMocks
    private AppointmentService service;

    private Doctor doctor;
    private Patient patient;
    private ClinicService treatment;
    private ClinicSettings settings;
    private LocalDateTime nextWeek;
    private LocalDateTime tomorrow;

    @BeforeEach
    void setUp() {
        settings = new ClinicSettings();
        settings.setPhone("+7 (7182) 00-00-00");
        when(clinic.current()).thenReturn(settings);
        when(clinic.holiday(any())).thenReturn(Optional.empty());
        loginAs(Role.REGISTRAR, null, null);

        Room room = new Room();
        room.setId(1L);
        room.setNumber("101");

        doctor = new Doctor();
        doctor.setId(DOCTOR_ID);
        doctor.setFullName("Иванова Елена Петровна");
        doctor.setRoom(room);
        Specialty therapist = new Specialty();
        therapist.setName("Стоматолог-терапевт");
        doctor.setSpecialty(therapist);

        patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setLastName("Алексеев");
        patient.setFirstName("Игорь");

        treatment = new ClinicService();
        treatment.setId(5L);
        treatment.setName("Лечение кариеса");
        treatment.setDurationMinutes(60);
        treatment.setPrice(new BigDecimal("27500"));

        Schedule schedule = new Schedule();
        schedule.setDoctor(doctor);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(18, 0));
        when(schedules.findByDoctorIdAndDayOfWeek(eq(DOCTOR_ID), anyInt())).thenReturn(Optional.of(schedule));

        nextWeek = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);
        tomorrow = LocalDate.now().plusDays(1).atTime(10, 0);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Appointment appointment(LocalDateTime start) {
        Appointment a = new Appointment();
        a.setId(1L);
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setRoom(doctor.getRoom());
        a.setService(treatment);
        a.setStartAt(start);
        a.setEndAt(start.plusMinutes(treatment.getDurationMinutes()));
        return a;
    }

    private static void loginAs(Role role, Long doctorId, Long patientId) {
        AuthUser user = new AuthUser(1L, role.name().toLowerCase(), "Тестовый пользователь", role, doctorId, patientId);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static HttpStatus statusOf(Throwable ex) {
        return ((ApiException) ex).getStatus();
    }

    @Nested
    @DisplayName("Проверка свободного времени перед записью")
    class Bookable {

        @Test
        void freeSlotInsideScheduleIsAccepted() {
            assertThatCode(() -> service.ensureBookable(appointment(nextWeek), null)).doesNotThrowAnyException();
        }

        @Test
        void pastTimeIsRejected() {
            assertThatThrownBy(() -> service.ensureBookable(appointment(LocalDateTime.now().minusHours(2)), null))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("прошедшее время")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        void appointmentEndingAfterWorkdayIsRejected() {
            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek.withHour(17).withMinute(30)), null))
                    .hasMessageContaining("вне графика")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        void doctorDayOffIsRejected() {
            when(schedules.findByDoctorIdAndDayOfWeek(eq(DOCTOR_ID), anyInt())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek), null))
                    .hasMessageContaining("выходной");
        }

        @Test
        void doubleBookingOfDoctorIsRejected() {
            when(appointments.findDoctorOverlaps(eq(DOCTOR_ID), any(), any(), isNull()))
                    .thenReturn(List.of(appointment(nextWeek.minusMinutes(30))));

            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek), null))
                    .hasMessageContaining("уже занят")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        void doubleBookingOfRoomIsRejected() {
            when(appointments.findRoomOverlaps(eq(1L), any(), any(), isNull()))
                    .thenReturn(List.of(appointment(nextWeek)));

            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek), null))
                    .hasMessageContaining("Кабинет № 101 занят")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        void patientCannotBeInTwoPlacesAtOnce() {
            when(appointments.findPatientOverlaps(eq(PATIENT_ID), any(), any(), isNull()))
                    .thenReturn(List.of(appointment(nextWeek)));

            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek), null))
                    .hasMessageContaining("У пациента уже есть запись");
        }

        @Test
        void rescheduleIgnoresTheAppointmentItself() {
            Appointment a = appointment(nextWeek);

            service.ensureBookable(a, 42L);

            verify(appointments).findDoctorOverlaps(DOCTOR_ID, a.getStartAt(), a.getEndAt(), 42L);
            verify(appointments).findRoomOverlaps(1L, a.getStartAt(), a.getEndAt(), 42L);
        }
    }

    @Nested
    @DisplayName("Правила из настроек клиники")
    class ClinicRules {

        @Test
        void holidayIsRejected() {
            Holiday holiday = new Holiday();
            holiday.setDay(nextWeek.toLocalDate());
            holiday.setName("День Республики");
            when(clinic.holiday(nextWeek.toLocalDate())).thenReturn(Optional.of(holiday));

            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek), null))
                    .hasMessageContaining("нерабочий день")
                    .hasMessageContaining("День Республики")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        void dateBeyondBookingHorizonIsRejected() {
            settings.setBookingHorizonDays(5);

            assertThatThrownBy(() -> service.ensureBookable(appointment(nextWeek), null))
                    .hasMessageContaining("Запись открыта на 5 дн. вперёд")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        void patientCannotBookLaterThanMinimumLeadTime() {
            loginAs(Role.PATIENT, null, PATIENT_ID);
            settings.setMinLeadHours(48);

            assertThatThrownBy(() -> service.ensureBookable(appointment(tomorrow), null))
                    .hasMessageContaining("не позднее чем за 48 ч")
                    .hasMessageContaining(settings.getPhone());
        }

        @Test
        void registrarIsNotLimitedByLeadTime() {
            settings.setMinLeadHours(48);

            assertThatCode(() -> service.ensureBookable(appointment(tomorrow), null)).doesNotThrowAnyException();
        }

        @Test
        void patientCannotCancelShortlyBeforeVisit() {
            loginAs(Role.PATIENT, null, PATIENT_ID);
            settings.setPatientCancelHours(24);
            Appointment a = appointment(LocalDateTime.now().plusHours(3));
            when(appointments.findById(1L)).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> service.cancel(1L, null))
                    .hasMessageContaining("Отменить запись онлайн можно не позднее чем за 24 ч")
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
            assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        }

        @Test
        void patientCanCancelInAdvance() {
            loginAs(Role.PATIENT, null, PATIENT_ID);
            settings.setPatientCancelHours(24);
            Appointment a = appointment(nextWeek);
            when(appointments.findById(1L)).thenReturn(Optional.of(a));

            service.cancel(1L, null);

            assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        }

        @Test
        void expensiveServiceGetsInvoiceAtBooking() {
            settings.setPrepaymentThreshold(new BigDecimal("20000"));
            stubBookingLookups();

            service.create(new AppointmentRequest(PATIENT_ID, DOCTOR_ID, 5L, nextWeek, null));

            verify(invoices).onBookedWithPrepayment(any());
        }

        @Test
        void cheapServiceIsBookedWithoutInvoice() {
            settings.setPrepaymentThreshold(new BigDecimal("50000"));
            stubBookingLookups();

            service.create(new AppointmentRequest(PATIENT_ID, DOCTOR_ID, 5L, nextWeek, null));

            verify(invoices, never()).onBookedWithPrepayment(any());
        }

        private void stubBookingLookups() {
            when(patients.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
            when(doctors.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
            when(catalog.find(5L)).thenReturn(treatment);
        }
    }

    @Nested
    @DisplayName("Права и смена статуса")
    class Workflow {

        @Test
        void patientCannotBookForAnotherPatient() {
            loginAs(Role.PATIENT, null, PATIENT_ID);

            assertThatThrownBy(() -> service.create(new AppointmentRequest(999L, DOCTOR_ID, 5L, nextWeek, null)))
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.FORBIDDEN));
            verify(appointments, never()).saveAndFlush(any());
        }

        @Test
        void cancellationFreesSlotWritesAuditAndClosesInvoice() {
            loginAs(Role.REGISTRAR, null, null);
            Appointment a = appointment(nextWeek);
            when(appointments.findById(1L)).thenReturn(Optional.of(a));

            service.cancel(1L, "Пациент заболел");

            assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
            assertThat(a.isActive()).isFalse();
            verify(invoices).onClosedWithoutVisit(a);
            verify(audit).save(argThat(e -> e.getAction() == AuditAction.CANCEL
                    && e.getNewValue().contains("Пациент заболел")));
        }

        @Test
        void doctorCannotCancelAppointments() {
            loginAs(Role.DOCTOR, DOCTOR_ID, null);
            when(appointments.findById(1L)).thenReturn(Optional.of(appointment(nextWeek)));

            assertThatThrownBy(() -> service.cancel(1L, null))
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        void cancelledAppointmentCannotBeCancelledAgain() {
            loginAs(Role.REGISTRAR, null, null);
            Appointment a = appointment(nextWeek);
            a.setStatus(AppointmentStatus.CANCELLED);
            when(appointments.findById(1L)).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> service.cancel(1L, null))
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        void futureAppointmentCannotBeMarkedCompleted() {
            loginAs(Role.DOCTOR, DOCTOR_ID, null);
            when(appointments.findById(1L)).thenReturn(Optional.of(appointment(nextWeek)));

            assertThatThrownBy(() -> service.changeStatus(1L, AppointmentStatus.COMPLETED))
                    .hasMessageContaining("ещё не начался");
        }

        @Test
        void completedAppointmentGetsInvoice() {
            loginAs(Role.DOCTOR, DOCTOR_ID, null);
            Appointment a = appointment(LocalDateTime.now().minusHours(1));
            when(appointments.findById(1L)).thenReturn(Optional.of(a));

            service.changeStatus(1L, AppointmentStatus.COMPLETED);

            assertThat(a.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
            verify(invoices).onCompleted(a);
            verify(audit).save(argThat(e -> e.getAction() == AuditAction.STATUS));
        }

        @Test
        void doctorCannotMarkAnotherDoctorsAppointment() {
            loginAs(Role.DOCTOR, 77L, null);
            when(appointments.findById(1L)).thenReturn(Optional.of(appointment(LocalDateTime.now().minusHours(1))));

            assertThatThrownBy(() -> service.changeStatus(1L, AppointmentStatus.NO_SHOW))
                    .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }
}
