package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.SettingsModels.HolidayDto;
import com.stomatologia.client.model.SettingsModels.HolidayRequest;
import com.stomatologia.client.model.SettingsModels.SettingsDto;
import com.stomatologia.client.model.SettingsModels.SettingsRequest;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Screen;
import com.stomatologia.client.ui.Tables;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Настройки клиники (только администратор): реквизиты, правила записи, счета и нерабочие дни.
 * Реквизиты и правила сохраняются одной кнопкой, нерабочие дни — сразу при добавлении и удалении.
 */
public class SettingsController {

    private static final BigDecimal SAMPLE_AMOUNT = new BigDecimal("27500");
    private static final DateTimeFormatter NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    @FXML
    private Label updatedLabel;
    @FXML
    private Label dirtyLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button revertButton;

    @FXML
    private TextField nameField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField binField;
    @FXML
    private TextField bankField;
    @FXML
    private TextField iikField;
    @FXML
    private TextField bikField;

    @FXML
    private ComboBox<Integer> stepCombo;
    @FXML
    private Spinner<Integer> horizonSpinner;
    @FXML
    private Label horizonHint;
    @FXML
    private Spinner<Integer> leadSpinner;
    @FXML
    private Spinner<Integer> cancelSpinner;

    @FXML
    private TextField prefixField;
    @FXML
    private Label prefixHint;
    @FXML
    private CheckBox vatCheck;
    @FXML
    private TextField vatField;
    @FXML
    private Label vatHint;
    @FXML
    private CheckBox prepayCheck;
    @FXML
    private TextField prepayField;
    @FXML
    private Label prepayHint;

    @FXML
    private TableView<HolidayDto> holidaysTable;
    @FXML
    private Label holidaysCount;
    @FXML
    private Button deleteHolidayButton;

    private SettingsDto loaded;
    private boolean filling;

    @FXML
    private void initialize() {
        stepCombo.getItems().setAll(10, 15, 20, 30);
        stepCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer minutes) {
                return minutes == null ? "" : minutes + " мин";
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        horizonSpinner.setValueFactory(new IntegerSpinnerValueFactory(7, 365, 60, 7));
        leadSpinner.setValueFactory(new IntegerSpinnerValueFactory(0, 72, 1));
        cancelSpinner.setValueFactory(new IntegerSpinnerValueFactory(0, 168, 24));
        vatField.disableProperty().bind(vatCheck.selectedProperty().not());
        prepayField.disableProperty().bind(prepayCheck.selectedProperty().not());

        List<ObservableValue<?>> inputs = List.of(nameField.textProperty(), addressField.textProperty(),
                phoneField.textProperty(), emailField.textProperty(), binField.textProperty(),
                bankField.textProperty(), iikField.textProperty(), bikField.textProperty(),
                stepCombo.valueProperty(), horizonSpinner.valueProperty(), leadSpinner.valueProperty(),
                cancelSpinner.valueProperty(), prefixField.textProperty(), vatCheck.selectedProperty(),
                vatField.textProperty(), prepayCheck.selectedProperty(), prepayField.textProperty());
        for (ObservableValue<?> input : inputs) {
            input.addListener((obs, o, n) -> {
                if (!filling) {
                    setDirty(true);
                }
                updateHints();
            });
        }

        Tables.sorted(holidaysTable, "Дата", HolidayDto::day, Formats::date, 110);
        Tables.text(holidaysTable, "День недели", h -> Formats.dayFull(h.day().getDayOfWeek().getValue()), 130);
        Tables.text(holidaysTable, "Название", HolidayDto::name, 300);
        Tables.badge(holidaysTable, "Записи на дату", SettingsController::scheduledText,
                h -> h.scheduledAppointments() > 0 ? "badge-warning" : "badge-muted", 160);
        Tables.init(holidaysTable, "Нерабочие дни не заданы");
        deleteHolidayButton.disableProperty().bind(holidaysTable.getSelectionModel().selectedItemProperty().isNull());

        setDirty(false);
        load();
    }

    private void load() {
        Fx.async(() -> ApiClient.get().get("/api/settings", SettingsDto.class), this::fill);
        loadHolidays();
    }

    private void loadHolidays() {
        Fx.async(() -> ApiClient.get().get("/api/holidays", new TypeReference<List<HolidayDto>>() {
        }), list -> {
            holidaysTable.getItems().setAll(list);
            long upcoming = list.stream().filter(h -> !h.day().isBefore(LocalDate.now())).count();
            holidaysCount.setText("Всего: " + list.size() + ", предстоящих: " + upcoming);
            if (upcoming > 0) {
                holidaysTable.scrollTo(list.size() - (int) upcoming);
            }
        });
    }

    private void fill(SettingsDto s) {
        loaded = s;
        filling = true;
        try {
            nameField.setText(s.name());
            addressField.setText(s.address());
            phoneField.setText(s.phone());
            emailField.setText(s.email());
            binField.setText(s.bin());
            bankField.setText(s.bankName());
            iikField.setText(s.iik());
            bikField.setText(s.bik());
            stepCombo.setValue(s.slotStepMinutes());
            horizonSpinner.getValueFactory().setValue(s.bookingHorizonDays());
            leadSpinner.getValueFactory().setValue(s.minLeadHours());
            cancelSpinner.getValueFactory().setValue(s.patientCancelHours());
            prefixField.setText(s.invoicePrefix());
            vatCheck.setSelected(s.vatEnabled());
            vatField.setText(plain(s.vatRate()));
            prepayCheck.setSelected(s.prepaymentThreshold() != null);
            prepayField.setText(plain(s.prepaymentThreshold()));
        } finally {
            filling = false;
        }
        updatedLabel.setText(s.updatedBy() == null ? "Значения по умолчанию"
                : "Изменено " + Formats.dateTime(s.updatedAt()) + ", " + s.updatedBy());
        setDirty(false);
        updateHints();
    }

    private void setDirty(boolean dirty) {
        dirtyLabel.setVisible(dirty);
        dirtyLabel.setManaged(dirty);
        revertButton.setDisable(!dirty);
        saveButton.setDisable(!dirty);
    }

    private void updateHints() {
        horizonHint.setText("Последний день для записи сегодня: "
                + Formats.date(LocalDate.now().plusDays(horizonSpinner.getValue())));
        String prefix = prefixField.getText() == null || prefixField.getText().isBlank()
                ? "…" : prefixField.getText().trim();
        prefixHint.setText("Пример номера: " + prefix + "-" + NUMBER_DATE.format(LocalDate.now()) + "-000042");

        BigDecimal rate = parseOrNull(vatField.getText());
        vatHint.setText(!vatCheck.isSelected() ? "В счёте нет строки НДС"
                : rate == null ? "Укажите ставку, например 12"
                : "В счёте на " + Formats.money(SAMPLE_AMOUNT) + " будет строка «в т.ч. НДС " + plain(rate) + "%: "
                + Formats.money(vatIncluded(SAMPLE_AMOUNT, rate)) + "»");

        BigDecimal threshold = parseOrNull(prepayField.getText());
        prepayHint.setText(!prepayCheck.isSelected() ? "Счёт выставляется после приёма"
                : threshold == null ? "Укажите сумму, от которой нужна предоплата"
                : "Запись на услугу от " + Formats.money(threshold) + " сразу получает счёт на оплату");
    }

    /** НДС, включённый в сумму: amount × rate / (100 + rate), как в счёте Word. */
    static BigDecimal vatIncluded(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(rate.add(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
    }

    @FXML
    private void onSave() {
        SettingsRequest request;
        try {
            request = request();
        } catch (IllegalArgumentException e) {
            Dialogs.error(e.getMessage());
            return;
        }
        saveButton.setDisable(true);
        Fx.async(() -> ApiClient.get().put("/api/settings", request, SettingsDto.class), s -> {
            fill(s);
            Dialogs.info("Настройки клиники сохранены. Новые правила уже действуют.");
        }, ex -> {
            saveButton.setDisable(false);
            Dialogs.error(ex);
        });
    }

    @FXML
    private void onRevert() {
        if (loaded != null) {
            fill(loaded);
        }
    }

    SettingsRequest request() {
        commit(horizonSpinner, "Запись вперёд");
        commit(leadSpinner, "Минимальное время до приёма");
        commit(cancelSpinner, "Срок отмены");
        if (stepCombo.getValue() == null) {
            throw new IllegalArgumentException("Выберите шаг записи");
        }
        BigDecimal vatRate = vatCheck.isSelected()
                ? required(vatField.getText(), "Укажите ставку НДС числом, например 12")
                : loaded != null && loaded.vatRate() != null ? loaded.vatRate() : new BigDecimal("12");
        BigDecimal threshold = prepayCheck.isSelected()
                ? required(prepayField.getText(), "Укажите порог предоплаты числом, например 50000") : null;
        return new SettingsRequest(text(nameField), text(addressField), text(phoneField),
                Formats.blankToNull(emailField.getText()), digits(binField), Formats.blankToNull(bankField.getText()),
                compactUpper(iikField), compactUpper(bikField), stepCombo.getValue(), horizonSpinner.getValue(),
                leadSpinner.getValue(), cancelSpinner.getValue(), text(prefixField), vatCheck.isSelected(),
                vatRate, threshold);
    }

    @FXML
    private void onAddHoliday() {
        FormDialog form = new FormDialog("Нерабочий день", "Добавить");
        DatePicker day = form.add("Дата *", new DatePicker(LocalDate.now().plusDays(1)));
        TextField name = form.add("Название *", new TextField());
        name.setPromptText("например, Санитарный день");
        HolidayDto[] created = new HolidayDto[1];
        boolean saved = form.showAndSave(() -> {
            if (day.getValue() == null) {
                throw new IllegalArgumentException("Укажите дату");
            }
            if (name.getText().isBlank()) {
                throw new IllegalArgumentException("Укажите название нерабочего дня");
            }
            created[0] = ApiClient.get().post("/api/holidays",
                    new HolidayRequest(day.getValue(), name.getText().trim()), HolidayDto.class);
        });
        if (!saved) {
            return;
        }
        loadHolidays();
        HolidayDto h = created[0];
        if (h != null && h.scheduledAppointments() > 0 && Dialogs.offer("Нерабочий день добавлен",
                "На " + Formats.date(h.day()) + " уже " + scheduledText(h) + ". Новая запись на эту дату закрыта, "
                        + "а существующие приёмы нужно перенести или отменить, предупредив пациентов.",
                "Открыть приёмы")) {
            MainController.navigate(Screen.APPOINTMENTS);
        }
    }

    @FXML
    private void onDeleteHoliday() {
        HolidayDto h = holidaysTable.getSelectionModel().getSelectedItem();
        if (h == null || !Dialogs.confirm("Удалить нерабочий день " + Formats.date(h.day()) + " «" + h.name()
                + "»? Запись на эту дату снова откроется.")) {
            return;
        }
        Fx.run(() -> ApiClient.get().delete("/api/holidays/" + h.id()), this::loadHolidays);
    }

    private static String scheduledText(HolidayDto h) {
        int n = h.scheduledAppointments();
        if (n == 0) {
            return "нет записей";
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        String word = mod100 >= 11 && mod100 <= 14 ? "приёмов"
                : mod10 == 1 ? "приём" : mod10 >= 2 && mod10 <= 4 ? "приёма" : "приёмов";
        return "записано " + n + " " + word;
    }

    /** Редактируемый Spinner принимает введённый текст только по Enter — фиксируем его перед сохранением. */
    private static void commit(Spinner<Integer> spinner, String title) {
        String text = spinner.getEditor().getText().trim();
        try {
            spinner.getValueFactory().setValue(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(title + ": введите целое число");
        }
        spinner.getEditor().setText(String.valueOf(spinner.getValue()));
    }

    private static BigDecimal required(String text, String error) {
        BigDecimal value = parseOrNull(text);
        if (value == null) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    private static BigDecimal parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.replaceAll("[\\s\\u00a0]", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static String digits(TextField field) {
        String s = Formats.blankToNull(field.getText());
        return s == null ? null : s.replaceAll("[\\s-]", "");
    }

    private static String compactUpper(TextField field) {
        String s = Formats.blankToNull(field.getText());
        return s == null ? null : s.replaceAll("\\s", "").toUpperCase();
    }
}
