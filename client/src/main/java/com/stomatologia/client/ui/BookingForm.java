package com.stomatologia.client.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.api.Session;
import com.stomatologia.client.model.AppointmentModels.AppointmentDto;
import com.stomatologia.client.model.AppointmentModels.AppointmentRequest;
import com.stomatologia.client.model.AppointmentModels.SlotDto;
import com.stomatologia.client.model.DoctorModels.DoctorDto;
import com.stomatologia.client.model.DoctorModels.SpecialtyDto;
import com.stomatologia.client.model.PatientModels.PatientDto;
import com.stomatologia.client.model.Role;
import com.stomatologia.client.model.ServiceModels.ServiceDto;
import com.stomatologia.client.model.SettingsModels.HolidayDto;
import com.stomatologia.client.model.SettingsModels.SettingsDto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Форма записи на приём: пациент, специальность, врач, услуга, дата и свободное время.
 * Свободные окна рассчитывает сервер с учётом графика врача и занятости врача и кабинета.
 */
public class BookingForm extends VBox {

    private static final SpecialtyDto ALL_SPECIALTIES = new SpecialtyDto(null, "Все специальности");

    private final boolean patientMode = Session.hasRole(Role.PATIENT);

    private final ObservableList<PatientDto> patients = FXCollections.observableArrayList();
    private final FilteredList<PatientDto> filteredPatients = new FilteredList<>(patients);
    private final ObservableList<DoctorDto> doctors = FXCollections.observableArrayList();
    private final FilteredList<DoctorDto> filteredDoctors = new FilteredList<>(doctors);

    private final TextField patientSearch = new TextField();
    private final ComboBox<PatientDto> patientCombo = new ComboBox<>(filteredPatients);
    private final ComboBox<SpecialtyDto> specialtyCombo = new ComboBox<>();
    private final ComboBox<DoctorDto> doctorCombo = new ComboBox<>(filteredDoctors);
    private final ComboBox<ServiceDto> serviceCombo = new ComboBox<>();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final FlowPane slotsPane = new FlowPane(8, 8);
    private final Label slotsInfo = new Label();
    private final Label selectionLabel = new Label();
    private final TextArea notes = new TextArea();
    private final ToggleGroup slotGroup = new ToggleGroup();

    private final Map<LocalDate, String> holidays = new HashMap<>();
    private int horizonDays = 60;

    private boolean ready;
    private Runnable onReady;
    private LocalDateTime pendingSelection;
    private int slotRequest;

    public BookingForm() {
        setSpacing(14);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(110);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setMinWidth(340);
        grid.getColumnConstraints().addAll(labels, fields);

        int row = 0;
        if (!patientMode) {
            patientSearch.setPromptText("Поиск по ФИО или телефону");
            patientSearch.textProperty().addListener((obs, o, n) -> filterPatients(n));
            patientCombo.setPromptText("Выберите пациента");
            patientCombo.setMaxWidth(Double.MAX_VALUE);
            grid.addRow(row++, label("Пациент *"), stretch(new VBox(6, patientSearch, patientCombo)));
        }
        specialtyCombo.setPromptText("Все специальности");
        doctorCombo.setPromptText("Выберите врача");
        serviceCombo.setPromptText("Выберите услугу");
        datePicker.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("holiday-cell");
                setTooltip(null);
                if (empty || item == null) {
                    return;
                }
                String holiday = holidays.get(item);
                setDisable(!bookable(item));
                if (holiday != null) {
                    getStyleClass().add("holiday-cell");
                    setTooltip(new Tooltip("Нерабочий день: " + holiday));
                }
            }
        });
        grid.addRow(row++, label("Специальность"), stretch(specialtyCombo));
        grid.addRow(row++, label("Врач *"), stretch(doctorCombo));
        grid.addRow(row++, label("Услуга *"), stretch(serviceCombo));

        Button prevDay = new Button("‹");
        Button nextDay = new Button("›");
        prevDay.setOnAction(e -> shiftDate(-1));
        nextDay.setOnAction(e -> shiftDate(1));
        Button nearest = new Button("Найти ближайшее окно");
        nearest.getStyleClass().add("primary");
        nearest.setOnAction(e -> findNearest());
        HBox dateRow = new HBox(8, prevDay, datePicker, nextDay, nearest);
        grid.addRow(row++, label("Дата *"), dateRow);

        slotsInfo.getStyleClass().add("muted");
        slotsPane.setPrefWrapLength(520);
        grid.addRow(row++, label("Время *"), new VBox(8, slotsInfo, slotsPane));

        notes.setPromptText("Жалобы, пожелания (необязательно)");
        notes.setPrefRowCount(2);
        notes.setWrapText(true);
        grid.addRow(row, label("Комментарий"), stretch(notes));

        selectionLabel.getStyleClass().add("section-title");
        selectionLabel.setWrapText(true);
        getChildren().addAll(grid, selectionLabel);

        specialtyCombo.valueProperty().addListener((obs, o, n) -> filterDoctors());
        doctorCombo.valueProperty().addListener((obs, o, n) -> loadSlots());
        serviceCombo.valueProperty().addListener((obs, o, n) -> loadSlots());
        datePicker.valueProperty().addListener((obs, o, n) -> loadSlots());
        slotGroup.selectedToggleProperty().addListener((obs, o, n) -> updateSelection());

        showSlotsHint("Выберите врача и услугу, чтобы увидеть свободное время");
        loadReferences();
    }

    /** Выполняет действие, когда справочники загружены (сразу, если уже загружены). */
    public void whenReady(Runnable action) {
        if (ready) {
            action.run();
        } else {
            onReady = action;
        }
    }

    /** Заполняет форму данными существующего приёма — для переноса. Пациент при переносе не меняется. */
    public void preset(AppointmentDto a) {
        whenReady(() -> {
            if (!patientMode) {
                patients.stream().filter(p -> p.id().equals(a.patientId())).findFirst()
                        .ifPresent(patientCombo::setValue);
                patientSearch.setDisable(true);
                patientCombo.setDisable(true);
            }
            specialtyCombo.setValue(ALL_SPECIALTIES);
            pendingSelection = a.startAt();
            doctors.stream().filter(d -> d.id().equals(a.doctorId())).findFirst().ifPresent(doctorCombo::setValue);
            serviceCombo.getItems().stream().filter(s -> s.id().equals(a.serviceId())).findFirst()
                    .ifPresent(serviceCombo::setValue);
            datePicker.setValue(a.startAt().toLocalDate().isBefore(LocalDate.now())
                    ? LocalDate.now() : a.startAt().toLocalDate());
            notes.setText(a.notes());
            loadSlots();
        });
    }

    public void presetPatient(Long patientId) {
        whenReady(() -> patients.stream().filter(p -> p.id().equals(patientId)).findFirst()
                .ifPresent(patientCombo::setValue));
    }

    /** Собирает запрос; при незаполненных полях бросает исключение с понятным сообщением. */
    public AppointmentRequest request() {
        PatientDto patient = patientCombo.getValue();
        if (!patientMode && patient == null) {
            throw new IllegalArgumentException("Выберите пациента");
        }
        if (doctorCombo.getValue() == null) {
            throw new IllegalArgumentException("Выберите врача");
        }
        if (serviceCombo.getValue() == null) {
            throw new IllegalArgumentException("Выберите услугу");
        }
        SlotDto slot = selectedSlot();
        if (slot == null) {
            throw new IllegalArgumentException("Выберите свободное время");
        }
        Long patientId = patientMode ? Session.user().patientId() : patient.id();
        return new AppointmentRequest(patientId, doctorCombo.getValue().id(), serviceCombo.getValue().id(),
                slot.start(), Formats.blankToNull(notes.getText()));
    }

    /** Сбрасывает выбор времени и комментарий после успешной записи и обновляет свободные окна. */
    public void resetAfterBooking() {
        notes.clear();
        loadSlots();
    }

    private void loadReferences() {
        Fx.async(() -> {
            ApiClient api = ApiClient.get();
            List<DoctorDto> d = api.get("/api/doctors", new TypeReference<List<DoctorDto>>() {
            });
            List<SpecialtyDto> s = api.get("/api/specialties", new TypeReference<List<SpecialtyDto>>() {
            });
            List<ServiceDto> sv = api.get("/api/services?active=true", new TypeReference<List<ServiceDto>>() {
            });
            List<PatientDto> p = patientMode ? List.of()
                    : api.get("/api/patients", new TypeReference<List<PatientDto>>() {
                    });
            SettingsDto settings = api.get("/api/settings", SettingsDto.class);
            List<HolidayDto> h = api.get(ApiClient.query("/api/holidays", "from", LocalDate.now(),
                    "to", LocalDate.now().plusDays(settings.bookingHorizonDays())),
                    new TypeReference<List<HolidayDto>>() {
                    });
            return new References(d, s, sv, p, settings, h);
        }, refs -> {
            horizonDays = refs.settings().bookingHorizonDays();
            holidays.clear();
            refs.holidays().forEach(h -> holidays.put(h.day(), h.name()));
            doctors.setAll(refs.doctors().stream().filter(DoctorDto::active).toList());
            specialtyCombo.getItems().setAll(ALL_SPECIALTIES);
            specialtyCombo.getItems().addAll(refs.specialties());
            specialtyCombo.setValue(ALL_SPECIALTIES);
            serviceCombo.getItems().setAll(refs.services());
            patients.setAll(refs.patients());
            ready = true;
            if (onReady != null) {
                onReady.run();
                onReady = null;
            }
        });
    }

    private record References(List<DoctorDto> doctors, List<SpecialtyDto> specialties, List<ServiceDto> services,
                              List<PatientDto> patients, SettingsDto settings, List<HolidayDto> holidays) {
    }

    private LocalDate lastBookableDay() {
        return LocalDate.now().plusDays(horizonDays);
    }

    private boolean bookable(LocalDate day) {
        return !day.isBefore(LocalDate.now()) && !day.isAfter(lastBookableDay()) && !holidays.containsKey(day);
    }

    private void filterPatients(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        PatientDto selected = patientCombo.getValue();
        String digits = q.replaceAll("\\D", "");
        filteredPatients.setPredicate(p -> q.isEmpty() || p.fullName().toLowerCase().contains(q)
                || (!digits.isEmpty() && p.phone() != null && p.phone().replaceAll("\\D", "").contains(digits)));
        if (filteredPatients.size() == 1) {
            patientCombo.setValue(filteredPatients.get(0));
        } else if (selected != null && !filteredPatients.contains(selected)) {
            patientCombo.setValue(null);
        }
        if (!q.isEmpty() && filteredPatients.size() > 1) {
            patientCombo.show();
        }
    }

    private void filterDoctors() {
        SpecialtyDto s = specialtyCombo.getValue();
        DoctorDto selected = doctorCombo.getValue();
        filteredDoctors.setPredicate(d -> s == null || s.id() == null || Objects.equals(d.specialtyId(), s.id()));
        if (selected != null && !filteredDoctors.contains(selected)) {
            doctorCombo.setValue(null);
        }
    }

    private void shiftDate(int days) {
        LocalDate next = datePicker.getValue().plusDays(days);
        while (holidays.containsKey(next)) {
            next = next.plusDays(days);
        }
        if (bookable(next)) {
            datePicker.setValue(next);
        }
    }

    private void loadSlots() {
        DoctorDto doctor = doctorCombo.getValue();
        ServiceDto service = serviceCombo.getValue();
        LocalDate date = datePicker.getValue();
        clearSlots();
        if (doctor == null || service == null || date == null) {
            showSlotsHint("Выберите врача и услугу, чтобы увидеть свободное время");
            return;
        }
        int request = ++slotRequest;
        slotsInfo.setText("Загрузка свободного времени…");
        String path = ApiClient.query("/api/slots", "doctorId", doctor.id(), "serviceId", service.id(), "date", date);
        Fx.async(() -> ApiClient.get().get(path, new TypeReference<List<SlotDto>>() {
        }), slots -> {
            if (request == slotRequest) {
                showSlots(slots);
            }
        });
    }

    private void clearSlots() {
        slotGroup.selectToggle(null);
        slotGroup.getToggles().clear();
        slotsPane.getChildren().clear();
        updateSelection();
    }

    private void showSlots(List<SlotDto> slots) {
        clearSlots();
        if (slots.isEmpty()) {
            LocalDate date = datePicker.getValue();
            if (holidays.containsKey(date)) {
                showSlotsHint(Formats.date(date) + " — нерабочий день клиники («" + holidays.get(date)
                        + "»). Выберите другую дату или нажмите «Найти ближайшее окно».");
            } else if (date.isAfter(lastBookableDay())) {
                showSlotsHint("Запись открыта до " + Formats.date(lastBookableDay())
                        + ". Выберите более раннюю дату.");
            } else {
                showSlotsHint("Нет свободного времени на " + Formats.date(date)
                        + ". Выберите другую дату или нажмите «Найти ближайшее окно».");
            }
            return;
        }
        slotsInfo.setText("Свободно окон: " + slots.size());
        for (SlotDto slot : slots) {
            ToggleButton b = new ToggleButton(Formats.TIME.format(slot.start()));
            b.getStyleClass().add("slot-button");
            b.setUserData(slot);
            b.setToggleGroup(slotGroup);
            slotsPane.getChildren().add(b);
            if (slot.start().equals(pendingSelection)) {
                b.setSelected(true);
            }
        }
        pendingSelection = null;
    }

    private void showSlotsHint(String text) {
        slotsInfo.setText(text);
    }

    private void findNearest() {
        ServiceDto service = serviceCombo.getValue();
        if (service == null) {
            Dialogs.error("Сначала выберите услугу");
            return;
        }
        DoctorDto doctor = doctorCombo.getValue();
        String path = ApiClient.query("/api/slots/nearest", "serviceId", service.id(),
                "doctorId", doctor == null ? null : doctor.id());
        Fx.async(() -> ApiClient.get().get(path, SlotDto.class), slot -> {
            pendingSelection = slot.start();
            if (doctor == null || !doctor.id().equals(slot.doctorId())) {
                specialtyCombo.setValue(ALL_SPECIALTIES);
                doctors.stream().filter(d -> d.id().equals(slot.doctorId())).findFirst()
                        .ifPresent(doctorCombo::setValue);
            }
            if (slot.start().toLocalDate().equals(datePicker.getValue())) {
                loadSlots();
            } else {
                datePicker.setValue(slot.start().toLocalDate());
            }
        });
    }

    private SlotDto selectedSlot() {
        return slotGroup.getSelectedToggle() == null ? null : (SlotDto) slotGroup.getSelectedToggle().getUserData();
    }

    private void updateSelection() {
        SlotDto slot = selectedSlot();
        ServiceDto service = serviceCombo.getValue();
        if (slot == null || service == null) {
            selectionLabel.setText("");
            return;
        }
        selectionLabel.setText(Formats.dayFull(slot.start().getDayOfWeek().getValue()) + ", "
                + Formats.date(slot.start().toLocalDate()) + ", " + Formats.TIME.format(slot.start()) + "–"
                + Formats.TIME.format(slot.end()) + " · " + slot.doctorName() + " · каб. " + slot.roomNumber()
                + " · " + service.name() + " · " + Formats.money(service.price()));
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static <T extends javafx.scene.layout.Region> T stretch(T region) {
        region.setMaxWidth(Double.MAX_VALUE);
        return region;
    }
}
