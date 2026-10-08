package com.stomatologia.backend.report;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.ClinicService;
import com.stomatologia.backend.domain.Doctor;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.PaymentMethod;
import com.stomatologia.backend.domain.Room;
import com.stomatologia.backend.domain.Specialty;
import com.stomatologia.backend.report.ExcelReports.DoctorPeriodLoad;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGeneratorsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    private final WordDocuments word = new WordDocuments("Клиника «Улыбка»", "ул. Центральная, 1", "+7 900 000-00-00");
    private final ExcelReports excel = new ExcelReports();

    private static Appointment appointment() {
        Specialty specialty = new Specialty();
        specialty.setName("Стоматолог-терапевт");
        Room room = new Room();
        room.setNumber("101");
        room.setName("Терапия");
        Doctor doctor = new Doctor();
        doctor.setFullName("Иванова Елена Петровна");
        doctor.setSpecialty(specialty);
        doctor.setRoom(room);
        Patient patient = new Patient();
        patient.setLastName("Алексеев");
        patient.setFirstName("Игорь");
        patient.setPhone("+7 901 000-00-01");
        ClinicService service = new ClinicService();
        service.setName("Лечение кариеса");
        service.setPrice(new BigDecimal("5500.00"));
        service.setDurationMinutes(60);

        Appointment a = new Appointment();
        a.setId(42L);
        a.setPatient(patient);
        a.setDoctor(doctor);
        a.setRoom(room);
        a.setService(service);
        a.setStartAt(DAY.atTime(10, 0));
        a.setEndAt(DAY.atTime(11, 0));
        return a;
    }

    private static Invoice paidInvoice(Appointment a) {
        Invoice invoice = new Invoice();
        invoice.setNumber("СЧ-20261005-000042");
        invoice.setAppointment(a);
        invoice.setAmount(new BigDecimal("5500.00"));
        invoice.setIssuedAt(DAY.atTime(11, 0));
        Payment p = new Payment();
        p.setInvoice(invoice);
        p.setAmount(new BigDecimal("5500.00"));
        p.setMethod(PaymentMethod.CARD);
        p.setPaidAt(DAY.atTime(11, 10));
        invoice.getPayments().add(p);
        invoice.refreshStatus();
        return invoice;
    }

    private static String text(byte[] docx) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    @Test
    void ticketContainsAppointmentDetails() throws IOException {
        String text = text(word.ticket(appointment(), "Козлова Марина"));

        assertThat(text).contains("ТАЛОН НА ПРИЁМ № 000042", "Алексеев Игорь", "Иванова Елена Петровна",
                "05.10.2026 10:00–11:00", "№ 101", "Лечение кариеса", "5 500,00 ₽", "Козлова Марина");
    }

    @Test
    void invoiceContainsAmountInWordsAndPayments() throws IOException {
        String text = text(word.invoice(paidInvoice(appointment()), "Козлова Марина"));

        assertThat(text).contains("СЧЁТ № СЧ-20261005-000042", "Пять тысяч пятьсот рублей 00 копеек",
                "Оплачен", "Банковская карта");
    }

    @Test
    void revenueWorkbookHasSheetsAndTotals() throws IOException {
        Invoice invoice = paidInvoice(appointment());
        byte[] xlsx = excel.revenue(DAY, DAY.plusDays(1), invoice.getPayments(), invoice.getAmount(), BigDecimal.ZERO);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetName(0)).isEqualTo("По дням");
            assertThat(wb.getSheet("По врачам")).isNotNull();
            assertThat(wb.getSheet("По услугам")).isNotNull();
            Sheet list = wb.getSheet("Платежи");
            Row first = list.getRow(5);
            assertThat(first.getCell(1).getStringCellValue()).isEqualTo("СЧ-20261005-000042");
            assertThat(first.getCell(6).getNumericCellValue()).isEqualTo(5500.0);
            assertThat(list.getRow(6).getCell(6).getCellFormula()).isEqualTo("SUM(G6:G6)");
        }
    }

    @Test
    void doctorLoadWorkbookShowsPercentAndDaysOff() throws IOException {
        DoctorPeriodLoad load = new DoctorPeriodLoad("Иванова Елена Петровна", "Стоматолог-терапевт", "101",
                1, 540, 270, 4, 3, 1, 0, Map.of(DAY, 50));
        byte[] xlsx = excel.doctorLoad(DAY, DAY.plusDays(1), List.of(load));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row row = wb.getSheet("Загрузка врачей").getRow(5);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Иванова Елена Петровна");
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(0.5);
            Sheet daily = wb.getSheet("По дням");
            assertThat(daily.getRow(5).getCell(1).getNumericCellValue()).isEqualTo(0.5);
            assertThat(daily.getRow(6).getCell(1).getStringCellValue()).isEqualTo("выходной");
        }
    }
}
