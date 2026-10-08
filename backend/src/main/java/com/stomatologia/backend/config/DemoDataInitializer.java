package com.stomatologia.backend.config;

import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Role;
import com.stomatologia.backend.domain.User;
import com.stomatologia.backend.repository.DoctorRepository;
import com.stomatologia.backend.repository.PatientRepository;
import com.stomatologia.backend.repository.RoomRepository;
import com.stomatologia.backend.repository.SpecialtyRepository;
import com.stomatologia.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Заполняет пустую базу учебными (тестовыми) данными при первом запуске.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final UserRepository users;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final SpecialtyRepository specialties;
    private final RoomRepository rooms;
    private final PasswordEncoder encoder;

    public DemoDataInitializer(UserRepository users, DoctorRepository doctors, PatientRepository patients,
                               SpecialtyRepository specialties, RoomRepository rooms, PasswordEncoder encoder) {
        this.users = users;
        this.doctors = doctors;
        this.patients = patients;
        this.specialties = specialties;
        this.rooms = rooms;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            seedPeople();
            log.info("Созданы демонстрационные пользователи, врачи и пациенты");
        }
    }

    private void seedPeople() {
        user("admin", "admin123", "Администратор системы", Role.ADMIN);
        user("registrar", "reg123", "Козлова Марина Сергеевна", Role.REGISTRAR);

        doctor("ivanova", "Иванова Елена Петровна", "Стоматолог-терапевт", "101", "+7 900 111-22-01");
        doctor("petrov", "Петров Андрей Викторович", "Стоматолог-хирург", "102", "+7 900 111-22-02");
        doctor("sidorova", "Сидорова Ольга Николаевна", "Ортодонт", "103", "+7 900 111-22-03");
        doctor("smirnov", "Смирнов Дмитрий Алексеевич", "Детский стоматолог", "104", "+7 900 111-22-04");

        Patient first = patient("Алексеев", "Игорь", "Владимирович", LocalDate.of(1985, 3, 14), "+7 901 000-00-01");
        first.setUser(user("patient", "pat123", first.getFullName(), Role.PATIENT));
        patient("Белова", "Анна", "Сергеевна", LocalDate.of(1992, 7, 2), "+7 901 000-00-02");
        patient("Волков", "Николай", "Иванович", LocalDate.of(1978, 11, 23), "+7 901 000-00-03");
        patient("Григорьева", "Светлана", "Олеговна", LocalDate.of(2001, 1, 9), "+7 901 000-00-04");
        patient("Дмитриев", "Максим", "Андреевич", LocalDate.of(2015, 5, 30), "+7 901 000-00-05");
        patient("Егорова", "Татьяна", "Павловна", LocalDate.of(1969, 9, 17), "+7 901 000-00-06");
        patient("Жуков", "Артём", null, LocalDate.of(1999, 12, 5), "+7 901 000-00-07");
        patient("Зайцева", "Полина", "Дмитриевна", LocalDate.of(1988, 4, 21), "+7 901 000-00-08");
    }

    private User user(String username, String password, String fullName, Role role) {
        return users.save(new User(username, encoder.encode(password), fullName, role));
    }

    private void doctor(String username, String fullName, String specialty, String room, String phone) {
        Doctor d = new Doctor();
        d.setFullName(fullName);
        d.setSpecialty(specialties.findByName(specialty).orElseThrow());
        d.setRoom(rooms.findByNumber(room).orElseThrow());
        d.setPhone(phone);
        d.setEmail(username + "@stomatologia.local");
        d.setUser(user(username, "doc123", fullName, Role.DOCTOR));
        doctors.save(d);
    }

    private Patient patient(String last, String first, String middle, LocalDate birth, String phone) {
        Patient p = new Patient();
        p.setLastName(last);
        p.setFirstName(first);
        p.setMiddleName(middle);
        p.setBirthDate(birth);
        p.setPhone(phone);
        return patients.save(p);
    }
}
