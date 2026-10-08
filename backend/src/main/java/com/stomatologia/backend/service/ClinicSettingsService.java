package com.stomatologia.backend.service;

import com.stomatologia.backend.common.ApiException;
import com.stomatologia.backend.domain.AppointmentStatus;
import com.stomatologia.backend.domain.ClinicSettings;
import com.stomatologia.backend.domain.Holiday;
import com.stomatologia.backend.dto.SettingsDtos.HolidayDto;
import com.stomatologia.backend.dto.SettingsDtos.HolidayRequest;
import com.stomatologia.backend.dto.SettingsDtos.SettingsDto;
import com.stomatologia.backend.dto.SettingsDtos.SettingsRequest;
import com.stomatologia.backend.repository.AppointmentRepository;
import com.stomatologia.backend.repository.ClinicSettingsRepository;
import com.stomatologia.backend.repository.HolidayRepository;
import com.stomatologia.backend.repository.UserRepository;
import com.stomatologia.backend.security.CurrentUser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Настройки клиники и нерабочие дни. Значения читаются из БД при каждом обращении,
 * поэтому изменения администратора применяются сразу, без перезапуска сервера.
 */
@Service
public class ClinicSettingsService {

    private static final Logger log = LogManager.getLogger(ClinicSettingsService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final Set<Integer> SLOT_STEPS = Set.of(10, 15, 20, 30);

    private final ClinicSettingsRepository settings;
    private final HolidayRepository holidays;
    private final AppointmentRepository appointments;
    private final UserRepository users;

    public ClinicSettingsService(ClinicSettingsRepository settings, HolidayRepository holidays,
                                 AppointmentRepository appointments, UserRepository users) {
        this.settings = settings;
        this.holidays = holidays;
        this.appointments = appointments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ClinicSettings current() {
        return settings.findById(ClinicSettings.ID)
                .orElseThrow(() -> new IllegalStateException("В БД нет строки настроек клиники (миграция V4)"));
    }

    @Transactional(readOnly = true)
    public SettingsDto get() {
        return SettingsDto.from(current());
    }

    @Transactional
    public SettingsDto update(SettingsRequest r) {
        if (!SLOT_STEPS.contains(r.slotStepMinutes())) {
            throw ApiException.badRequest("Шаг записи может быть 10, 15, 20 или 30 минут");
        }
        ClinicSettings s = current();
        List<String> changes = new ArrayList<>();
        s.setName(changed(changes, "название", s.getName(), r.name().trim()));
        s.setAddress(changed(changes, "адрес", s.getAddress(), r.address().trim()));
        s.setPhone(changed(changes, "телефон", s.getPhone(), r.phone().trim()));
        s.setEmail(changed(changes, "email", s.getEmail(), trimToNull(r.email())));
        s.setBin(changed(changes, "БИН", s.getBin(), trimToNull(r.bin())));
        s.setBankName(changed(changes, "банк", s.getBankName(), trimToNull(r.bankName())));
        s.setIik(changed(changes, "ИИК", s.getIik(), trimToNull(r.iik())));
        s.setBik(changed(changes, "БИК", s.getBik(), trimToNull(r.bik())));
        s.setSlotStepMinutes(changed(changes, "шаг записи, мин", s.getSlotStepMinutes(), r.slotStepMinutes()));
        s.setBookingHorizonDays(changed(changes, "запись вперёд, дней", s.getBookingHorizonDays(),
                r.bookingHorizonDays()));
        s.setMinLeadHours(changed(changes, "минимум до приёма, ч", s.getMinLeadHours(), r.minLeadHours()));
        s.setPatientCancelHours(changed(changes, "отмена пациентом, ч", s.getPatientCancelHours(),
                r.patientCancelHours()));
        s.setInvoicePrefix(changed(changes, "префикс счёта", s.getInvoicePrefix(), r.invoicePrefix().trim()));
        s.setVatEnabled(changed(changes, "НДС в счёте", s.isVatEnabled(), r.vatEnabled()));
        s.setVatRate(changed(changes, "ставка НДС, %", s.getVatRate(), r.vatRate()));
        s.setPrepaymentThreshold(changed(changes, "порог предоплаты", s.getPrepaymentThreshold(),
                r.prepaymentThreshold()));

        if (!changes.isEmpty()) {
            s.setUpdatedAt(LocalDateTime.now());
            s.setUpdatedBy(users.getReferenceById(CurrentUser.get().id()));
            settings.saveAndFlush(s);
            log.info("Настройки клиники изменены ({}): {}", CurrentUser.get().username(), String.join("; ", changes));
        }
        return SettingsDto.from(s);
    }

    @Transactional(readOnly = true)
    public Optional<Holiday> holiday(LocalDate day) {
        return holidays.findByDay(day);
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDates(LocalDate from, LocalDate to) {
        return holidays.findByDayBetweenOrderByDay(from, to).stream().map(Holiday::getDay).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> holidays(LocalDate from, LocalDate to) {
        List<Holiday> list = holidays.findByDayBetweenOrderByDay(from, to);
        Map<LocalDate, Long> scheduled = scheduledByDay(from, to);
        return list.stream()
                .map(h -> HolidayDto.from(h, scheduled.getOrDefault(h.getDay(), 0L).intValue()))
                .toList();
    }

    @Transactional
    public HolidayDto addHoliday(HolidayRequest r) {
        holidays.findByDay(r.day()).ifPresent(h -> {
            throw ApiException.conflict(DATE.format(h.getDay()) + " уже отмечен как нерабочий день («"
                    + h.getName() + "»)");
        });
        Holiday h = new Holiday();
        h.setDay(r.day());
        h.setName(r.name().trim());
        holidays.saveAndFlush(h);
        int scheduled = scheduledByDay(r.day(), r.day()).getOrDefault(r.day(), 0L).intValue();
        log.info("Добавлен нерабочий день {} «{}» ({}), запланированных приёмов на эту дату: {}",
                DATE.format(h.getDay()), h.getName(), CurrentUser.get().username(), scheduled);
        return HolidayDto.from(h, scheduled);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        Holiday h = holidays.findById(id).orElseThrow(() -> ApiException.notFound("Нерабочий день не найден"));
        holidays.delete(h);
        log.info("Удалён нерабочий день {} «{}» ({})", DATE.format(h.getDay()), h.getName(),
                CurrentUser.get().username());
    }

    private Map<LocalDate, Long> scheduledByDay(LocalDate from, LocalDate to) {
        return appointments.findActiveBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED)
                .collect(Collectors.groupingBy(a -> a.getStartAt().toLocalDate(), Collectors.counting()));
    }

    private static <T> T changed(List<String> changes, String label, T oldValue, T newValue) {
        boolean same = oldValue instanceof BigDecimal o && newValue instanceof BigDecimal n
                ? o.compareTo(n) == 0 : Objects.equals(oldValue, newValue);
        if (!same) {
            changes.add(label + ": " + display(oldValue) + " -> " + display(newValue));
        }
        return newValue;
    }

    private static String display(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof Boolean b) {
            return b ? "да" : "нет";
        }
        return value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : value.toString();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
