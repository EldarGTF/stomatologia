package com.stomatologia.backend.report;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.ClinicSettings;
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
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public byte[] ticket(Appointment a, ClinicSettings clinic, String issuedBy) {
        try (XWPFDocument doc = new XWPFDocument()) {
            header(doc, clinic);
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
            paragraph(doc, "Пожалуйста, приходите за 10 минут до начала приёма и возьмите с собой удостоверение "
                    + "личности. Отменить или перенести запись в личном кабинете можно не позднее чем за "
                    + clinic.getPatientCancelHours() + " ч до приёма, позже — по телефону " + clinic.getPhone() + ".",
                    11, false, ParagraphAlignment.LEFT);
            footer(doc, issuedBy);
            return bytes(doc);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] invoice(Invoice invoice, ClinicSettings clinic, String issuedBy) {
        Appointment a = invoice.getAppointment();
        try (XWPFDocument doc = new XWPFDocument()) {
            header(doc, clinic);
            paragraph(doc, "СЧЁТ № " + invoice.getNumber(), 16, true, ParagraphAlignment.CENTER);
            paragraph(doc, "от " + DATE.format(invoice.getIssuedAt()), 12, false, ParagraphAlignment.CENTER);
            paragraph(doc, "", 6, false, ParagraphAlignment.LEFT);
            String requisites = requisites(clinic);
            if (requisites != null) {
                labelValue(doc, "Получатель: ", requisites);
            }
            labelValue(doc, "Плательщик: ", a.getPatient().getFullName()
                    + (a.getPatient().getPhone() != null ? ", тел. " + a.getPatient().getPhone() : ""));
            labelValue(doc, "Приём: ", DATE_TIME.format(a.getStartAt()) + ", врач " + a.getDoctor().getFullName()
                    + " (" + a.getDoctor().getSpecialty().getName() + "), каб. " + a.getRoom().getNumber());

            XWPFTable items = doc.createTable(clinic.isVatEnabled() ? 4 : 3, 5);
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
            if (clinic.isVatEnabled()) {
                XWPFTableRow vat = items.getRow(3);
                cell(vat.getCell(3), "в т.ч. НДС " + percent(clinic.getVatRate()) + ":", false);
                cell(vat.getCell(4), money(vatIncluded(invoice.getAmount(), clinic.getVatRate())), false);
            }

            paragraph(doc, "", 6, false, ParagraphAlignment.LEFT);
            labelValue(doc, "Сумма прописью: ", MoneyInWords.tenge(invoice.getAmount()));
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

    private static void header(XWPFDocument doc, ClinicSettings clinic) {
        paragraph(doc, clinic.getName(), 14, true, ParagraphAlignment.CENTER);
        paragraph(doc, clinic.getAddress() + " · тел. " + clinic.getPhone()
                + (clinic.getEmail() != null ? " · " + clinic.getEmail() : ""), 10, false, ParagraphAlignment.CENTER);
        if (clinic.getBin() != null) {
            paragraph(doc, "БИН " + clinic.getBin(), 10, false, ParagraphAlignment.CENTER);
        }
        paragraph(doc, "", 8, false, ParagraphAlignment.LEFT);
    }

    /** Банковские реквизиты получателя; null, если ИИК не заполнен. */
    static String requisites(ClinicSettings clinic) {
        if (clinic.getIik() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(clinic.getName());
        if (clinic.getBin() != null) {
            sb.append(", БИН ").append(clinic.getBin());
        }
        sb.append(", ИИК ").append(clinic.getIik());
        if (clinic.getBankName() != null) {
            sb.append(" в ").append(clinic.getBankName());
        }
        if (clinic.getBik() != null) {
            sb.append(", БИК ").append(clinic.getBik());
        }
        return sb.toString();
    }

    /** НДС, уже включённый в сумму: amount × rate / (100 + rate). */
    static BigDecimal vatIncluded(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(rate.add(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
    }

    private static String percent(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
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
        return new DecimalFormat("#,##0.00", symbols).format(amount) + " ₸";
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
