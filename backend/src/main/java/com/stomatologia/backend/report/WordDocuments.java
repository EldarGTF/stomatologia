package com.stomatologia.backend.report;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.Invoice;
import com.stomatologia.backend.domain.Patient;
import com.stomatologia.backend.domain.Payment;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Документы Word: талон на приём и счёт на оплату.
 */
@Component
public class WordDocuments {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String FONT = "Times New Roman";

    private final String clinicName;
    private final String clinicAddress;
    private final String clinicPhone;

    public WordDocuments(@Value("${app.clinic-name}") String clinicName,
                         @Value("${app.clinic-address}") String clinicAddress,
                         @Value("${app.clinic-phone}") String clinicPhone) {
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
        this.clinicPhone = clinicPhone;
    }

    public byte[] ticket(Appointment a, String issuedBy) {
        try (XWPFDocument doc = new XWPFDocument()) {
            header(doc);
            paragraph(doc, "ТАЛОН НА ПРИЁМ № " + String.format("%06d", a.getId()), 16, true, ParagraphAlignment.CENTER);
            paragraph(doc, "", 6, false, ParagraphAlignment.LEFT);

            Patient p = a.getPatient();
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Пациент", p.getFullName());
            fields.put("Дата рождения", p.getBirthDate() != null ? DATE.format(p.getBirthDate()) : "—");
            fields.put("Телефон", orDash(p.getPhone()));
            fields.put("Дата и время приёма", DATE_TIME.format(a.getStartAt()) + "–" + TIME.format(a.getEndAt()));
            fields.put("Врач", a.getDoctor().getFullName());
            fields.put("Специальность", a.getDoctor().getSpecialty().getName());
            fields.put("Кабинет", "№ " + a.getRoom().getNumber()
                    + (a.getRoom().getName() != null ? " (" + a.getRoom().getName() + ")" : ""));
            fields.put("Услуга", a.getService().getName() + " (" + a.getService().getDurationMinutes() + " мин)");
            fields.put("Стоимость", money(a.getService().getPrice()));
            fields.put("Статус", a.getStatus().title());
            fields.put("Комментарий", orDash(a.getNotes()));

            XWPFTable table = doc.createTable(fields.size(), 2);
            table.setWidth("100%");
            int r = 0;
            for (Map.Entry<String, String> f : fields.entrySet()) {
                cell(table.getRow(r).getCell(0), f.getKey(), true);
                cell(table.getRow(r).getCell(1), f.getValue(), false);
                r++;
            }

            paragraph(doc, "", 6, false, ParagraphAlignment.LEFT);
            paragraph(doc, "Пожалуйста, приходите за 10 минут до начала приёма и возьмите с собой паспорт. "
                    + "Если вы не сможете прийти, сообщите об этом по телефону " + clinicPhone + ".",
                    11, false, ParagraphAlignment.LEFT);
            footer(doc, issuedBy);
            return bytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] invoice(Invoice invoice, String issuedBy) {
        Appointment a = invoice.getAppointment();
        try (XWPFDocument doc = new XWPFDocument()) {
            header(doc);
            paragraph(doc, "СЧЁТ № " + invoice.getNumber(), 16, true, ParagraphAlignment.CENTER);
            paragraph(doc, "от " + DATE.format(invoice.getIssuedAt()), 12, false, ParagraphAlignment.CENTER);
            paragraph(doc, "", 6, false, ParagraphAlignment.LEFT);
            labelValue(doc, "Плательщик: ", a.getPatient().getFullName()
                    + (a.getPatient().getPhone() != null ? ", тел. " + a.getPatient().getPhone() : ""));
            labelValue(doc, "Приём: ", DATE_TIME.format(a.getStartAt()) + ", врач " + a.getDoctor().getFullName()
                    + " (" + a.getDoctor().getSpecialty().getName() + "), каб. " + a.getRoom().getNumber());

            XWPFTable items = doc.createTable(3, 5);
            items.setWidth("100%");
            String[] head = {"№", "Наименование услуги", "Кол-во", "Цена", "Сумма"};
            for (int i = 0; i < head.length; i++) {
                cell(items.getRow(0).getCell(i), head[i], true);
            }
            XWPFTableRow line = items.getRow(1);
            cell(line.getCell(0), "1", false);
            cell(line.getCell(1), a.getService().getName(), false);
            cell(line.getCell(2), "1", false);
            cell(line.getCell(3), money(invoice.getAmount()), false);
            cell(line.getCell(4), money(invoice.getAmount()), false);
            XWPFTableRow total = items.getRow(2);
            cell(total.getCell(3), "Итого:", true);
            cell(total.getCell(4), money(invoice.getAmount()), true);

            paragraph(doc, "", 6, false, ParagraphAlignment.LEFT);
            labelValue(doc, "Сумма прописью: ", MoneyInWords.rubles(invoice.getAmount()));
            labelValue(doc, "Оплачено: ", money(invoice.paidAmount()));
            labelValue(doc, "К оплате: ", money(invoice.dueAmount()));
            labelValue(doc, "Статус счёта: ", invoice.getStatus().title());

            if (!invoice.getPayments().isEmpty()) {
                paragraph(doc, "Платежи по счёту", 12, true, ParagraphAlignment.LEFT);
                XWPFTable pays = doc.createTable(invoice.getPayments().size() + 1, 3);
                pays.setWidth("100%");
                cell(pays.getRow(0).getCell(0), "Дата", true);
                cell(pays.getRow(0).getCell(1), "Способ оплаты", true);
                cell(pays.getRow(0).getCell(2), "Сумма", true);
                int r = 1;
                for (Payment p : invoice.getPayments()) {
                    cell(pays.getRow(r).getCell(0), DATE_TIME.format(p.getPaidAt()), false);
                    cell(pays.getRow(r).getCell(1), p.getMethod().title(), false);
                    cell(pays.getRow(r).getCell(2), money(p.getAmount()), false);
                    r++;
                }
            }
            footer(doc, issuedBy);
            return bytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void header(XWPFDocument doc) {
        paragraph(doc, clinicName, 14, true, ParagraphAlignment.CENTER);
        paragraph(doc, clinicAddress + " · тел. " + clinicPhone, 10, false, ParagraphAlignment.CENTER);
        paragraph(doc, "", 8, false, ParagraphAlignment.LEFT);
    }

    private static void footer(XWPFDocument doc, String issuedBy) {
        paragraph(doc, "", 10, false, ParagraphAlignment.LEFT);
        paragraph(doc, "Документ сформирован: " + DATE_TIME.format(LocalDateTime.now()), 10, false,
                ParagraphAlignment.LEFT);
        paragraph(doc, "Выдал: ____________________ / " + issuedBy + " /", 11, false, ParagraphAlignment.LEFT);
    }

    private static void cell(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT);
        run.setFontSize(11);
    }

    private static void paragraph(XWPFDocument doc, String text, int size, boolean bold, ParagraphAlignment align) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        p.setSpacingAfter(80);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(FONT);
        run.setFontSize(size);
    }

    private static void labelValue(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(60);
        XWPFRun l = p.createRun();
        l.setText(label);
        l.setBold(true);
        l.setFontFamily(FONT);
        l.setFontSize(12);
        XWPFRun v = p.createRun();
        v.setText(value);
        v.setFontFamily(FONT);
        v.setFontSize(12);
    }

    static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", symbols).format(amount) + " ₽";
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static byte[] bytes(XWPFDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.write(out);
        return out.toByteArray();
    }
}
