package com.stomatologia.client.dialog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.model.AppointmentModels.AppointmentRequest;
import com.stomatologia.client.model.DoctorModels.DoctorDto;
import com.stomatologia.client.model.LeadModels.LeadBookRequest;
import com.stomatologia.client.model.LeadModels.LeadDto;
import com.stomatologia.client.model.LeadModels.LeadRequest;
import com.stomatologia.client.model.LeadModels.LeadSource;
import com.stomatologia.client.model.LeadModels.RejectRequest;
import com.stomatologia.client.model.PatientModels.PatientDto;
import com.stomatologia.client.model.ServiceModels.ServiceDto;
import com.stomatologia.client.ui.BookingForm;
import com.stomatologia.client.ui.FormDialog;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Диалоги раздела «Заявки»: ручное создание (звонок), правка, отказ и запись на приём.
 */
public final class LeadDialogs {

    private LeadDialogs() {
    }

    public static LeadDto create() {
        return form(null);
    }

    public static boolean edit(LeadDto lead) {
        return form(lead) != null;
    }

    private static LeadDto form(LeadDto lead) {
        FormDialog dialog = new FormDialog(lead == null ? "Новая заявка" : "Заявка №" + lead.id(),
                lead == null ? "Создать" : "Сохранить");
        if (lead == null) {
            dialog.dialog().setHeaderText("Новая заявка — например, после звонка в клинику.\n"
                    + "Заявка сразу попадёт к вам в работу.");
        }
        ComboBox<LeadSource> source = dialog.add("Источник *", new ComboBox<>());
        source.getItems().setAll(LeadSource.values());
        source.setValue(lead == null ? LeadSource.PHONE : lead.source());
        source.setDisable(lead != null);
        TextField name = dialog.add("Имя *", new TextField(lead == null ? null : lead.name()));
        name.setPromptText("Как обращаться к клиенту");
        TextField phone = dialog.add("Телефон", new TextField(lead == null ? null : lead.phone()));
        phone.setPromptText("+7 700 123 45 67");
        ComboBox<ServiceDto> service = dialog.add("Услуга", optional(new ComboBox<>(), "Не выбрана"));
        ComboBox<DoctorDto> doctor = dialog.add("Врач", optional(new ComboBox<>(), "Любой"));
        TextField preferred = dialog.add("Желаемое время",
                new TextField(lead == null ? null : lead.preferredText()));
        preferred.setPromptText("Например: в субботу утром");
        TextArea summary = dialog.add("Суть обращения", new TextArea(lead == null ? null : lead.summary()));
        summary.setPrefRowCount(4);
        summary.setWrapText(true);
        summary.setPromptText("Что беспокоит, о чём договорились");

        Fx.async(() -> List.of(
                ApiClient.get().get("/api/services?active=true", new TypeReference<List<ServiceDto>>() {
                }),
                ApiClient.get().get("/api/doctors", new TypeReference<List<DoctorDto>>() {
                })), lists -> {
            @SuppressWarnings("unchecked")
            List<ServiceDto> services = (List<ServiceDto>) lists.get(0);
            @SuppressWarnings("unchecked")
            List<DoctorDto> doctors = ((List<DoctorDto>) lists.get(1)).stream().filter(DoctorDto::active).toList();
            service.getItems().add(null);
            service.getItems().addAll(services);
            doctor.getItems().add(null);
            doctor.getItems().addAll(doctors);
            if (lead != null) {
                services.stream().filter(s -> s.id().equals(lead.serviceId())).findFirst().ifPresent(service::setValue);
                doctors.stream().filter(d -> d.id().equals(lead.doctorId())).findFirst().ifPresent(doctor::setValue);
            }
        });

        AtomicReference<LeadDto> saved = new AtomicReference<>();
        boolean ok = dialog.showAndSave(() -> {
            LeadRequest request = new LeadRequest(source.getValue(), Formats.blankToNull(name.getText()),
                    Formats.blankToNull(phone.getText()),
                    service.getValue() == null ? null : service.getValue().id(),
                    doctor.getValue() == null ? null : doctor.getValue().id(),
                    lead == null ? null : lead.preferredStart(),
                    Formats.blankToNull(preferred.getText()), Formats.blankToNull(summary.getText()));
            saved.set(lead == null
                    ? ApiClient.get().post("/api/leads", request, LeadDto.class)
                    : ApiClient.get().put("/api/leads/" + lead.id(), request, LeadDto.class));
        });
        return ok ? saved.get() : null;
    }

    public static boolean reject(LeadDto lead) {
        FormDialog dialog = new FormDialog("Отказ по заявке", "Закрыть заявку");
        dialog.dialog().setHeaderText("Закрыть заявку «" + lead.name() + "» без записи?\n"
                + "Её можно будет вернуть в работу.");
        TextArea reason = dialog.add("Причина *", new TextArea());
        reason.setPromptText("Например: выбрал другую клинику");
        reason.setPrefRowCount(3);
        reason.setWrapText(true);
        return dialog.showAndSave(() -> ApiClient.get().post("/api/leads/" + lead.id() + "/reject",
                new RejectRequest(Formats.blankToNull(reason.getText())), LeadDto.class));
    }

    /**
     * Запись по заявке. Пациент выбирается из найденных по номеру телефона или создаётся новый.
     * Возвращает обновлённую заявку или null, если запись не создана.
     */
    public static LeadDto book(LeadDto lead) {
        FormDialog dialog = new FormDialog("Запись по заявке", "Записать");
        dialog.dialog().setHeaderText("Запись по заявке: " + lead.name()
                + (lead.phone() != null ? ", " + lead.phone() : "")
                + (lead.preferredText() != null ? "\nПожелание клиента: " + lead.preferredText() : ""));

        dialog.section("Пациент");
        ToggleGroup choice = new ToggleGroup();
        VBox options = new VBox(6);
        RadioButton newPatient = new RadioButton("Новый пациент");
        newPatient.setToggleGroup(choice);
        newPatient.setSelected(true);
        Label searching = new Label("Ищем пациентов с этим номером телефона…");
        searching.getStyleClass().add("muted");
        options.getChildren().addAll(searching, newPatient);
        dialog.addWide(options);

        String[] names = splitName(lead.name());
        TextField lastName = new TextField(names[0]);
        TextField firstName = new TextField(names[1]);
        TextField phone = new TextField(lead.phone());
        lastName.setPromptText("Фамилия *");
        firstName.setPromptText("Имя *");
        phone.setPromptText("Телефон");
        GridPane newFields = new GridPane();
        newFields.setHgap(8);
        newFields.addRow(0, lastName, firstName, phone);
        newFields.disableProperty().bind(newPatient.selectedProperty().not());
        dialog.addWide(newFields);

        dialog.section("Приём");
        BookingForm form = new BookingForm(false);
        form.presetChoice(lead.doctorId(), lead.serviceId(), lead.preferredStart(), "Заявка №" + lead.id()
                + (lead.preferredText() != null ? ". Пожелание: " + lead.preferredText() : ""));
        dialog.addWide(form);

        Fx.async(() -> ApiClient.get().get("/api/leads/" + lead.id() + "/patients",
                new TypeReference<List<PatientDto>>() {
                }), patients -> {
            options.getChildren().remove(searching);
            for (int i = 0; i < patients.size(); i++) {
                PatientDto p = patients.get(i);
                RadioButton option = new RadioButton(patientLabel(p));
                option.setUserData(p);
                option.setToggleGroup(choice);
                options.getChildren().add(i, option);
                if (i == 0 || p.id().equals(lead.patientId())) {
                    option.setSelected(true);
                }
            }
            if (patients.isEmpty()) {
                Label none = new Label("Пациентов с таким номером в базе нет — будет создана новая карта.");
                none.getStyleClass().add("muted");
                options.getChildren().add(0, none);
            }
        }, ex -> {
            searching.setText("Не удалось найти пациентов: " + ex.getMessage());
        });

        AtomicReference<LeadDto> booked = new AtomicReference<>();
        boolean ok = dialog.showAndSave(() -> {
            AppointmentRequest r = form.request();
            PatientDto existing = (PatientDto) choice.getSelectedToggle().getUserData();
            LeadBookRequest request = existing != null
                    ? new LeadBookRequest(existing.id(), null, null, null,
                    r.doctorId(), r.serviceId(), r.startAt(), r.notes())
                    : new LeadBookRequest(null, Formats.blankToNull(lastName.getText()),
                    Formats.blankToNull(firstName.getText()), Formats.blankToNull(phone.getText()),
                    r.doctorId(), r.serviceId(), r.startAt(), r.notes());
            booked.set(ApiClient.get().post("/api/leads/" + lead.id() + "/book", request, LeadDto.class));
        });
        return ok ? booked.get() : null;
    }

    private static String patientLabel(PatientDto p) {
        StringBuilder s = new StringBuilder(p.fullName());
        if (p.birthDate() != null) {
            s.append(", ").append(Formats.date(p.birthDate()));
        }
        if (p.phone() != null) {
            s.append(", ").append(p.phone());
        }
        return s.toString();
    }

    /**
     * Делит имя из заявки на фамилию и имя. Клиенты обычно пишут «Имя Фамилия», а три слова — это
     * «Фамилия Имя Отчество». Одно слово считается именем: фамилию регистратор уточнит сам.
     */
    static String[] splitName(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        return switch (parts.length) {
            case 0 -> new String[]{"", ""};
            case 1 -> new String[]{"", parts[0]};
            case 2 -> new String[]{parts[1], parts[0]};
            default -> new String[]{parts[0], parts[1]};
        };
    }

    private static <T> ComboBox<T> optional(ComboBox<T> combo, String emptyText) {
        combo.setPromptText(emptyText);
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? emptyText : item.toString());
            }
        });
        combo.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Objects.toString(item, emptyText));
            }
        });
        return combo;
    }
}
