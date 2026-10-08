package com.stomatologia.backend.report;

import com.stomatologia.backend.domain.Appointment;
import com.stomatologia.backend.domain.Payment;
import com.stomatologia.backend.domain.PaymentMethod;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Отчёты Excel: загрузка врачей и выручка за период.
 */
@Component
public class ExcelReports {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    /** autoSizeColumn не учитывает результаты формул (итоги), поэтому колонки не уже 16 символов. */
    private static final int MIN_COLUMN_WIDTH = 16 * 256;

    /**
     * Загрузка врача за период. {@code dailyPercent} — загрузка по дням (нет ключа — выходной по графику).
     */
    public record DoctorPeriodLoad(String doctor, String specialty, String room, int workDays, int workMinutes,
                                   int bookedMinutes, int appointments, int completed, int noShow, int cancelled,
                                   Map<LocalDate, Integer> dailyPercent) {
    }

    public byte[] doctorLoad(LocalDate from, LocalDate to, List<DoctorPeriodLoad> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            Sheet sheet = wb.createSheet("Загрузка врачей");
            int r = title(sheet, st, "Отчёт по загрузке врачей", from, to);
            String[] head = {"Врач", "Специальность", "Кабинет", "Рабочих дней", "Часов по графику", "Занято, ч",
                    "Загрузка", "Приёмов", "Завершено", "Неявки", "Отменено"};
            header(sheet.createRow(r++), head, st);
            int firstData = r;
            for (DoctorPeriodLoad l : rows) {
                Row row = sheet.createRow(r++);
                text(row, 0, l.doctor(), st.text);
                text(row, 1, l.specialty(), st.text);
                text(row, 2, l.room(), st.text);
                number(row, 3, l.workDays(), st.integer);
                number(row, 4, l.workMinutes() / 60.0, st.decimal);
                number(row, 5, l.bookedMinutes() / 60.0, st.decimal);
                number(row, 6, l.workMinutes() == 0 ? 0 : (double) l.bookedMinutes() / l.workMinutes(), st.percent);
                number(row, 7, l.appointments(), st.integer);
                number(row, 8, l.completed(), st.integer);
                number(row, 9, l.noShow(), st.integer);
                number(row, 10, l.cancelled(), st.integer);
            }
            int lastData = r - 1;
            Row total = sheet.createRow(r);
            text(total, 0, "Итого", st.totalText);
            for (int c = 1; c <= 2; c++) {
                text(total, c, "", st.totalText);
            }
            for (int c : new int[]{3, 4, 5, 7, 8, 9, 10}) {
                sum(total, c, firstData, lastData, c == 4 || c == 5 ? st.totalDecimal : st.totalInteger);
            }
            Cell totalLoad = total.createCell(6);
            totalLoad.setCellFormula("IF(" + cellRef(r, 4) + "=0,0," + cellRef(r, 5) + "/" + cellRef(r, 4) + ")");
            totalLoad.setCellStyle(st.totalPercent);
            autosize(sheet, head.length, firstData);

            Sheet daily = wb.createSheet("По дням");
            int d = title(daily, st, "Загрузка врачей по дням", from, to);
            int dailyFirst = d + 1;
            Row dh = daily.createRow(d++);
            text(dh, 0, "Дата", st.header);
            for (int i = 0; i < rows.size(); i++) {
                text(dh, i + 1, rows.get(i).doctor(), st.header);
            }
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                Row row = daily.createRow(d++);
                text(row, 0, DATE.format(day), st.text);
                for (int i = 0; i < rows.size(); i++) {
                    Integer percent = rows.get(i).dailyPercent().get(day);
                    if (percent == null) {
                        text(row, i + 1, "выходной", st.muted);
                    } else {
                        number(row, i + 1, percent / 100.0, st.percent);
                    }
                }
            }
            autosize(daily, rows.size() + 1, dailyFirst);
            return bytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] revenue(LocalDate from, LocalDate to, List<Payment> payments, BigDecimal invoiced,
                          BigDecimal outstanding) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            Sheet byDay = wb.createSheet("По дням");
            int r = title(byDay, st, "Отчёт по выручке", from, to);
            Row s1 = byDay.createRow(r++);
            text(s1, 0, "Выставлено счетов за период", st.text);
            number(s1, 1, invoiced.doubleValue(), st.money);
            Row s2 = byDay.createRow(r++);
            text(s2, 0, "Текущая задолженность по счетам", st.text);
            number(s2, 1, outstanding.doubleValue(), st.money);
            r++;
            String[] head = {"Дата", "Наличные", "Карта", "Перевод", "Итого", "Платежей"};
            header(byDay.createRow(r++), head, st);
            Map<LocalDate, List<Payment>> grouped = payments.stream()
                    .collect(Collectors.groupingBy(p -> p.getPaidAt().toLocalDate(), TreeMap::new, Collectors.toList()));
            int firstData = r;
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                List<Payment> list = grouped.getOrDefault(day, List.of());
                Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
                for (Payment p : list) {
                    byMethod.merge(p.getMethod(), p.getAmount(), BigDecimal::add);
                }
                Row row = byDay.createRow(r);
                text(row, 0, DATE.format(day), st.text);
                number(row, 1, byMethod.getOrDefault(PaymentMethod.CASH, BigDecimal.ZERO).doubleValue(), st.money);
                number(row, 2, byMethod.getOrDefault(PaymentMethod.CARD, BigDecimal.ZERO).doubleValue(), st.money);
                number(row, 3, byMethod.getOrDefault(PaymentMethod.TRANSFER, BigDecimal.ZERO).doubleValue(), st.money);
                Cell totalCell = row.createCell(4);
                totalCell.setCellFormula("SUM(" + cellRef(r, 1) + ":" + cellRef(r, 3) + ")");
                totalCell.setCellStyle(st.money);
                number(row, 5, list.size(), st.integer);
                r++;
            }
            totalRow(byDay.createRow(r), st, firstData, r - 1, new int[]{1, 2, 3, 4}, new int[]{5});
            autosize(byDay, head.length, firstData);

            groupSheet(wb, st, "По врачам", "Врач", from, to, payments,
                    p -> p.getInvoice().getAppointment().getDoctor().getFullName());
            groupSheet(wb, st, "По услугам", "Услуга", from, to, payments,
                    p -> p.getInvoice().getAppointment().getService().getName());

            Sheet list = wb.createSheet("Платежи");
            int l = title(list, st, "Платежи за период", from, to);
            String[] lh = {"Дата и время", "Счёт", "Пациент", "Врач", "Услуга", "Способ", "Сумма", "Принял"};
            header(list.createRow(l++), lh, st);
            int firstPayment = l;
            for (Payment p : payments) {
                Appointment a = p.getInvoice().getAppointment();
                Row row = list.createRow(l++);
                text(row, 0, DATE_TIME.format(p.getPaidAt()), st.text);
                text(row, 1, p.getInvoice().getNumber(), st.text);
                text(row, 2, a.getPatient().getFullName(), st.text);
                text(row, 3, a.getDoctor().getFullName(), st.text);
                text(row, 4, a.getService().getName(), st.text);
                text(row, 5, p.getMethod().title(), st.text);
                number(row, 6, p.getAmount().doubleValue(), st.money);
                text(row, 7, p.getReceivedBy() != null ? p.getReceivedBy().getFullName() : "", st.text);
            }
            totalRow(list.createRow(l), st, firstPayment, l - 1, new int[]{6}, new int[]{});
            autosize(list, lh.length, firstPayment);
            return bytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void groupSheet(XSSFWorkbook wb, Styles st, String name, String keyTitle, LocalDate from,
                                   LocalDate to, List<Payment> payments, Function<Payment, String> key) {
        Sheet sheet = wb.createSheet(name);
        int r = title(sheet, st, "Выручка: " + name.toLowerCase(), from, to);
        header(sheet.createRow(r++), new String[]{keyTitle, "Платежей", "Сумма", "Доля"}, st);
        Map<String, List<Payment>> grouped = payments.stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        List<Map.Entry<String, BigDecimal>> sums = grouped.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().stream().map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .toList();
        int firstData = r;
        int lastData = r + sums.size() - 1;
        for (Map.Entry<String, BigDecimal> e : sums) {
            Row row = sheet.createRow(r);
            text(row, 0, e.getKey(), st.text);
            number(row, 1, grouped.get(e.getKey()).size(), st.integer);
            number(row, 2, e.getValue().doubleValue(), st.money);
            Cell share = row.createCell(3);
            share.setCellFormula(cellRef(r, 2) + "/SUM(" + cellRef(firstData, 2) + ":" + cellRef(lastData, 2) + ")");
            share.setCellStyle(st.percent);
            r++;
        }
        totalRow(sheet.createRow(r), st, firstData, r - 1, new int[]{2}, new int[]{1});
        autosize(sheet, 4, firstData);
    }

    private static int title(Sheet sheet, Styles st, String title, LocalDate from, LocalDate to) {
        text(sheet.createRow(0), 0, title, st.title);
        text(sheet.createRow(1), 0, "Период: " + DATE.format(from) + " — " + DATE.format(to), st.muted);
        text(sheet.createRow(2), 0, "Сформирован: " + DATE_TIME.format(LocalDateTime.now()), st.muted);
        return 4;
    }

    private static void header(Row row, String[] titles, Styles st) {
        for (int i = 0; i < titles.length; i++) {
            text(row, i, titles[i], st.header);
        }
    }

    private static void totalRow(Row row, Styles st, int firstData, int lastData, int[] moneyColumns,
                                 int[] countColumns) {
        text(row, 0, "Итого", st.totalText);
        for (int c : moneyColumns) {
            sum(row, c, firstData, lastData, st.totalMoney);
        }
        for (int c : countColumns) {
            sum(row, c, firstData, lastData, st.totalInteger);
        }
    }

    private static void sum(Row row, int column, int firstData, int lastData, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellFormula(lastData < firstData ? "0"
                : "SUM(" + cellRef(firstData, column) + ":" + cellRef(lastData, column) + ")");
        cell.setCellStyle(style);
    }

    private static String cellRef(int row, int column) {
        return new CellReference(row, column).formatAsString();
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Подбирает ширину колонок и закрепляет строки до первой строки данных (шапка таблицы остаётся видна). */
    private static void autosize(Sheet sheet, int columns, int firstDataRow) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.max(sheet.getColumnWidth(i) + 512, MIN_COLUMN_WIDTH);
            sheet.setColumnWidth(i, Math.min(width, 60 * 256));
        }
        sheet.createFreezePane(0, firstDataRow);
    }

    private static byte[] bytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    /** Общие стили ячеек книги. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle muted;
        final CellStyle header;
        final CellStyle text;
        final CellStyle integer;
        final CellStyle decimal;
        final CellStyle money;
        final CellStyle percent;
        final CellStyle totalText;
        final CellStyle totalInteger;
        final CellStyle totalDecimal;
        final CellStyle totalMoney;
        final CellStyle totalPercent;

        Styles(XSSFWorkbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(titleFont);

            Font mutedFont = wb.createFont();
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            muted = wb.createCellStyle();
            muted.setFont(mutedFont);

            Font bold = wb.createFont();
            bold.setBold(true);
            Font white = wb.createFont();
            white.setBold(true);
            white.setColor(IndexedColors.WHITE.getIndex());
            header = bordered(wb);
            header.setFont(white);
            header.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);

            short fmtInteger = wb.createDataFormat().getFormat("0");
            short fmtDecimal = wb.createDataFormat().getFormat("0.0");
            short fmtMoney = wb.createDataFormat().getFormat("#,##0.00 \"₸\"");
            short fmtPercent = wb.createDataFormat().getFormat("0%");

            text = bordered(wb);
            integer = numeric(wb, fmtInteger, null);
            decimal = numeric(wb, fmtDecimal, null);
            money = numeric(wb, fmtMoney, null);
            percent = numeric(wb, fmtPercent, null);
            totalText = bordered(wb);
            totalText.setFont(bold);
            totalText.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            totalText.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalInteger = numeric(wb, fmtInteger, bold);
            totalDecimal = numeric(wb, fmtDecimal, bold);
            totalMoney = numeric(wb, fmtMoney, bold);
            totalPercent = numeric(wb, fmtPercent, bold);
        }

        private static CellStyle bordered(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            return s;
        }

        private static CellStyle numeric(XSSFWorkbook wb, short format, Font font) {
            CellStyle s = bordered(wb);
            s.setDataFormat(format);
            if (font != null) {
                s.setFont(font);
                s.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return s;
        }
    }
}
