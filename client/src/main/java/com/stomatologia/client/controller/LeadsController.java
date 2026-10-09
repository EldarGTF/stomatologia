package com.stomatologia.client.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.dialog.LeadDialogs;
import com.stomatologia.client.model.LeadModels.ChatChannel;
import com.stomatologia.client.model.LeadModels.ChatMessageDto;
import com.stomatologia.client.model.LeadModels.ConversationMode;
import com.stomatologia.client.model.LeadModels.LeadDto;
import com.stomatologia.client.model.LeadModels.LeadSource;
import com.stomatologia.client.model.LeadModels.LeadStatus;
import com.stomatologia.client.model.LeadModels.MessageRole;
import com.stomatologia.client.model.LeadModels.OperatorMessageRequest;
import com.stomatologia.client.ui.Dialogs;
import com.stomatologia.client.ui.Downloads;
import com.stomatologia.client.ui.Formats;
import com.stomatologia.client.ui.Fx;
import com.stomatologia.client.ui.Screen;
import com.stomatologia.client.ui.Tables;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Заявки из мессенджеров, с сайта и по телефону. Список обновляется раз в 30 секунд, чтобы новые
 * обращения от бота появлялись без ручного обновления.
 */
public class LeadsController {

    /** Пункт фильтра статуса: null в status и open = true — все открытые заявки. */
    private record StatusOption(String title, LeadStatus status, boolean open) {
    }

    private static final StatusOption OPEN = new StatusOption("Ждут обработки", null, true);
    private static final StatusOption ALL = new StatusOption("Все заявки", null, false);

    @FXML
    private Label countLabel;
    @FXML
    private Label updatedLabel;
    @FXML
    private ComboBox<StatusOption> statusFilter;
    @FXML
    private ComboBox<LeadSource> sourceFilter;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<LeadDto> table;
    @FXML
    private VBox detailBox;

    private final ObservableList<LeadDto> leads = FXCollections.observableArrayList();
    private final FilteredList<LeadDto> filtered = new FilteredList<>(leads);
    private final Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(30),
            e -> load(false, selectedId())));
    private int detailRequest;
    private final Timeline chatRefresh = new Timeline(new KeyFrame(Duration.seconds(8),
            e -> refreshChat(detailRequest)));
    private final Map<Long, String> drafts = new HashMap<>();
    private boolean reloading;
    private LeadDto shown;
    private VBox chatBox;
    private Long chatLeadId;
    private int chatSize;

    @FXML
    private void initialize() {
        statusFilter.getItems().add(OPEN);
        for (LeadStatus s : LeadStatus.values()) {
            statusFilter.getItems().add(new StatusOption(s.title(), s, false));
        }
        statusFilter.getItems().add(ALL);
        statusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(StatusOption o) {
                return o == null ? "" : o.title();
            }

            @Override
            public StatusOption fromString(String s) {
                return null;
            }
        });
        statusFilter.setValue(OPEN);
        sourceFilter.getItems().add(null);
        sourceFilter.getItems().addAll(LeadSource.values());
        sourceFilter.setPromptText("Все источники");
        sourceFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(LeadSource item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "Все источники" : item.title());
            }
        });
        sourceFilter.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(LeadSource item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? "Все источники" : item.title());
            }
        });

        Tables.sorted(table, "Поступила", LeadDto::createdAt, LeadsController::received, 110).setMinWidth(100);
        Tables.badge(table, "Источник", l -> l.source().title(), l -> l.source().styleClass(), 95).setMinWidth(90);
        Tables.composite(table, "Клиент", l -> l.name() + (l.phone() != null ? "\n" + l.phone() : ""),
                Comparator.comparing(LeadDto::name), 180).setMinWidth(150);
        Tables.text(table, "Услуга", LeadDto::serviceName, 170);
        Tables.text(table, "Желаемое время", LeadsController::preferred, 140);
        Tables.badge(table, "Статус", LeadDto::statusTitle, LeadDto::statusStyle, 140).setMinWidth(135);
        Tables.init(table, "Заявок нет");
        SortedList<LeadDto> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        Tables.onDoubleClick(table, this::book);

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (!reloading && !Objects.equals(n, shown)) {
                showDetail(n);
            }
        });
        statusFilter.valueProperty().addListener((obs, o, n) -> load(true, selectedId()));
        sourceFilter.valueProperty().addListener((obs, o, n) -> load(true, selectedId()));
        searchField.textProperty().addListener((obs, o, n) -> applySearch());

        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
        chatRefresh.setCycleCount(Timeline.INDEFINITE);
        chatRefresh.play();
        table.sceneProperty().addListener((obs, o, n) -> {
            if (n == null) {
                autoRefresh.stop();
                chatRefresh.stop();
            }
        });
        showDetail(null);
        load(true, null);
    }

    private void load(boolean manual, Long selectId) {
        StatusOption option = statusFilter.getValue();
        LeadSource source = sourceFilter.getValue();
        String path = ApiClient.query("/api/leads", "open", option.open() ? "true" : null,
                "status", option.status() == null ? null : option.status().name(),
                "source", source == null ? null : source.name());
        Fx.async(() -> ApiClient.get().get(path, new TypeReference<List<LeadDto>>() {
        }), list -> {
            reloading = true;
            try {
                leads.setAll(list);
                applySearch();
                select(selectId);
            } finally {
                reloading = false;
            }
            updatedLabel.setText("обновлено в " + Formats.TIME.format(LocalTime.now()));
        }, ex -> {
            if (manual) {
                Dialogs.error(ex);
            } else {
                updatedLabel.setText("не удалось обновить: " + ex.getMessage());
            }
        });
    }

    private void applySearch() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String digits = q.replaceAll("\\D", "");
        filtered.setPredicate(l -> q.isEmpty() || l.name().toLowerCase().contains(q)
                || (digits.length() >= 3 && l.phone() != null && l.phone().replaceAll("\\D", "").contains(digits)));
        long open = leads.stream().filter(LeadDto::needsAttention).count();
        countLabel.setText("Показано: " + filtered.size() + " из " + leads.size()
                + (statusFilter.getValue() == OPEN ? "" : " · ждут обработки: " + open));
    }

    private Long selectedId() {
        LeadDto selected = table.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.id();
    }

    /** Карточка перестраивается, только если заявка изменилась: иначе сбросился бы набираемый ответ. */
    private void select(Long id) {
        if (id == null) {
            if (shown != null) {
                showDetail(null);
            }
            return;
        }
        table.getItems().stream().filter(l -> l.id().equals(id)).findFirst().ifPresentOrElse(l -> {
            table.getSelectionModel().select(l);
            if (!l.equals(shown)) {
                showDetail(l);
            }
        }, () -> showDetail(null));
    }

    private void reloadSelecting(LeadDto updated) {
        load(true, updated.id());
    }

    // ---------- Карточка заявки ----------

    private void showDetail(LeadDto l) {
        detailBox.getChildren().clear();
        int request = ++detailRequest;
        shown = l;
        chatBox = null;
        chatLeadId = null;
        if (l == null) {
            Label hint = new Label("Выберите заявку в списке, чтобы увидеть подробности и переписку");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);
            detailBox.getChildren().add(hint);
            return;
        }
        Label name = new Label(l.name());
        name.getStyleClass().add("section-title");
        Label number = new Label("Заявка №" + l.id());
        number.getStyleClass().add("muted");
        HBox badges = new HBox(6, badge(l.source().title(), l.source().styleClass()),
                badge(l.statusTitle(), l.statusStyle()));
        badges.setAlignment(Pos.CENTER_LEFT);
        detailBox.getChildren().addAll(new VBox(2, name, number), badges);

        GridPane info = new GridPane();
        info.setHgap(12);
        info.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(120);
        info.getColumnConstraints().addAll(labels, new ColumnConstraints());
        int row = 0;
        row = infoRow(info, row, "Телефон", l.phone());
        row = infoRow(info, row, "Услуга", l.serviceName());
        row = infoRow(info, row, "Врач", l.doctorName());
        row = infoRow(info, row, "Желаемое время", preferred(l));
        row = infoRow(info, row, "Поступила", Formats.dateTime(l.createdAt()));
        row = infoRow(info, row, "Согласие на ПД", l.consentAt() == null ? null : Formats.dateTime(l.consentAt()));
        row = infoRow(info, row, "Ответственный", l.assignedTo());
        if (l.status() == LeadStatus.BOOKED) {
            row = infoRow(info, row, "Пациент", l.patientName());
            row = infoRow(info, row, "Приём", Formats.dateTime(l.appointmentStart()));
            row = infoRow(info, row, "Подтверждена", l.awaitsConfirmation()
                    ? "нет — позвоните клиенту и подтвердите запись" : Formats.dateTime(l.confirmedAt()));
        }
        if (l.status() == LeadStatus.REJECTED) {
            infoRow(info, row, "Причина отказа", l.rejectReason());
        }
        detailBox.getChildren().add(info);

        if (l.summary() != null) {
            Label caption = new Label("Суть обращения");
            caption.getStyleClass().add("field-label");
            Label summary = new Label(l.summary());
            summary.setWrapText(true);
            summary.getStyleClass().add("lead-summary");
            summary.setMaxWidth(Double.MAX_VALUE);
            detailBox.getChildren().add(new VBox(4, caption, summary));
        }

        detailBox.getChildren().add(actions(l));

        if (l.hasConversation()) {
            Label caption = new Label("Переписка");
            caption.getStyleClass().add("section-title");
            HBox header = new HBox(8, caption);
            header.setAlignment(Pos.CENTER_LEFT);
            if (l.conversationChannel() != null) {
                header.getChildren().add(badge(l.conversationChannel().title(), "badge-muted"));
            }
            if (l.conversationMode() != null) {
                header.getChildren().add(badge(l.conversationMode().title(), l.conversationMode().styleClass()));
            }
            VBox chat = new VBox(8);
            chat.getStyleClass().add("chat");
            Label loading = new Label("Загрузка…");
            loading.getStyleClass().add("muted");
            chat.getChildren().add(loading);
            detailBox.getChildren().addAll(header, chat);
            chatBox = chat;
            chatLeadId = l.id();
            chatSize = -1;
            refreshChat(request);
            if (l.conversationMode() != ConversationMode.CLOSED) {
                detailBox.getChildren().add(replyBox(l));
            }
        }
    }

    /** Переписка выбранной заявки: при открытии карточки и раз в несколько секунд, пока она открыта. */
    private void refreshChat(int request) {
        Long leadId = chatLeadId;
        if (chatBox == null || leadId == null) {
            return;
        }
        Fx.async(() -> ApiClient.get().get("/api/leads/" + leadId + "/messages",
                new TypeReference<List<ChatMessageDto>>() {
                }), list -> showMessages(request, list), ex -> {
        });
    }

    private void showMessages(int request, List<ChatMessageDto> list) {
        if (request != detailRequest || chatBox == null || list.size() == chatSize) {
            return;
        }
        chatSize = list.size();
        chatBox.getChildren().setAll(list.stream().map(LeadsController::bubble).toList());
    }

    /** Ответ клиенту от имени клиники. Черновик переживает автообновление списка. */
    private Node replyBox(LeadDto l) {
        TextArea text = new TextArea(drafts.getOrDefault(l.id(), ""));
        text.setPromptText("Ответ клиенту…");
        text.setWrapText(true);
        text.setPrefRowCount(3);
        text.textProperty().addListener((obs, o, n) -> {
            if (n == null || n.isBlank()) {
                drafts.remove(l.id());
            } else {
                drafts.put(l.id(), n);
            }
        });

        Button send = new Button("Отправить");
        send.getStyleClass().add("primary");
        send.disableProperty().bind(text.textProperty().isEmpty());
        Runnable doSend = () -> {
            String message = text.getText() == null ? "" : text.getText().trim();
            if (message.isEmpty()) {
                return;
            }
            send.disableProperty().unbind();
            send.setDisable(true);
            int request = detailRequest;
            Fx.async(() -> ApiClient.get().post("/api/leads/" + l.id() + "/messages",
                    new OperatorMessageRequest(message), new TypeReference<List<ChatMessageDto>>() {
                    }), list -> {
                drafts.remove(l.id());
                text.clear();
                showMessages(request, list);
                load(false, l.id());
            }, ex -> {
                send.disableProperty().bind(text.textProperty().isEmpty());
                Dialogs.error(ex);
                refreshChat(request);
            });
        };
        send.setOnAction(e -> doSend.run());
        text.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) {
                e.consume();
                doSend.run();
            }
        });

        HBox buttons = new HBox(8, send);
        buttons.setAlignment(Pos.CENTER_LEFT);
        if (l.conversationMode() == ConversationMode.OPERATOR) {
            buttons.getChildren().add(button("Вернуть ИИ-менеджеру", () -> Fx.async(() -> ApiClient.get()
                    .post("/api/leads/" + l.id() + "/conversation/assistant", null, LeadDto.class),
                    this::reloadSelecting)));
        }
        Label hint = new Label(replyHint(l));
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        return new VBox(6, text, buttons, hint);
    }

    static String replyHint(LeadDto l) {
        String where = l.conversationChannel() == ChatChannel.WEB_CHAT
                ? "Клиент увидит ответ в чате на сайте."
                : "Сообщение уйдёт клиенту в " + (l.conversationChannel() == null ? "мессенджер"
                : l.conversationChannel().title()) + ".";
        String mode = l.conversationMode() == ConversationMode.AI
                ? " После ответа разговор перейдёт к вам — ИИ-менеджер перестанет отвечать."
                : " Ctrl+Enter — отправить.";
        return where + mode;
    }

    private Node actions(LeadDto l) {
        List<Button> buttons = new ArrayList<>();
        if (l.status().open()) {
            Button book = new Button("Записать на приём");
            book.getStyleClass().add("primary");
            book.setOnAction(e -> book(l));
            buttons.add(book);
            if (l.status() != LeadStatus.IN_PROGRESS) {
                buttons.add(button("Взять в работу", () -> Fx.async(() -> ApiClient.get()
                        .post("/api/leads/" + l.id() + "/take", null, LeadDto.class), this::reloadSelecting)));
            }
            buttons.add(button("Изменить", () -> {
                if (LeadDialogs.edit(l)) {
                    reloadSelecting(l);
                }
            }));
            Button reject = button("Отказ", () -> {
                if (LeadDialogs.reject(l)) {
                    reloadSelecting(l);
                }
            });
            reject.getStyleClass().add("danger");
            buttons.add(reject);
        } else if (l.status() == LeadStatus.REJECTED) {
            buttons.add(button("Вернуть в работу", () -> Fx.async(() -> ApiClient.get()
                    .post("/api/leads/" + l.id() + "/reopen", null, LeadDto.class), this::reloadSelecting)));
        } else if (l.status() == LeadStatus.BOOKED) {
            if (l.awaitsConfirmation()) {
                Button confirm = button("Подтвердить запись", () -> Fx.async(() -> ApiClient.get()
                        .post("/api/leads/" + l.id() + "/confirm", null, LeadDto.class), this::reloadSelecting));
                confirm.getStyleClass().add("primary");
                buttons.add(confirm);
            }
            buttons.add(button("Открыть приёмы", () -> MainController.navigate(Screen.APPOINTMENTS)));
        }
        FlowPane pane = new FlowPane(8, 8);
        pane.getChildren().addAll(buttons);
        return pane;
    }

    private void book(LeadDto l) {
        if (l == null || !l.status().open()) {
            return;
        }
        LeadDto booked = LeadDialogs.book(l);
        if (booked == null) {
            return;
        }
        reloadSelecting(booked);
        boolean print = Dialogs.offer("Запись создана", booked.patientName() + " записан(а) на "
                + Formats.dateTime(booked.appointmentStart()) + "\nВрач: " + booked.doctorName()
                + "\nУслуга: " + booked.serviceName(), "Талон (Word)");
        if (print) {
            Downloads.word("/api/reports/appointments/" + booked.appointmentId() + "/ticket",
                    AppointmentsController.ticketFileName(booked.appointmentStart(), booked.patientName()));
        }
    }

    @FXML
    private void onAdd() {
        LeadDto created = LeadDialogs.create();
        if (created != null) {
            if (statusFilter.getValue() != OPEN && statusFilter.getValue() != ALL) {
                statusFilter.setValue(OPEN);
            }
            reloadSelecting(created);
        }
    }

    @FXML
    private void onRefresh() {
        load(true, selectedId());
    }

    private static VBox bubble(ChatMessageDto m) {
        boolean client = m.role() == MessageRole.USER;
        String who = switch (m.role()) {
            case USER -> "Клиент";
            case ASSISTANT -> "ИИ-менеджер";
            case OPERATOR -> "Оператор" + (m.author() != null ? " · " + m.author() : "");
        };
        Label meta = new Label(who + " · " + Formats.TIME.format(m.sentAt()));
        meta.getStyleClass().add("chat-meta");
        Label text = new Label(m.text());
        text.setWrapText(true);
        text.setMaxWidth(340);
        text.getStyleClass().addAll("chat-bubble", client ? "chat-client"
                : m.role() == MessageRole.OPERATOR ? "chat-operator" : "chat-clinic");
        VBox box = new VBox(2, meta, text);
        box.setAlignment(client ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        box.setFillWidth(false);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static int infoRow(GridPane grid, int row, String label, String value) {
        if (value == null || value.isBlank()) {
            return row;
        }
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        Label v = new Label(value);
        v.setWrapText(true);
        grid.addRow(row, l, v);
        return row + 1;
    }

    private static Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", styleClass);
        return label;
    }

    private static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

    static String preferred(LeadDto l) {
        if (l.preferredStart() != null) {
            return Formats.dateTime(l.preferredStart());
        }
        return Objects.requireNonNullElse(l.preferredText(), "");
    }

    /** «14:05» для сегодняшних заявок, иначе дата и время. */
    static String received(LocalDateTime at) {
        return at.toLocalDate().equals(LocalDate.now()) ? "сегодня " + Formats.TIME.format(at) : Formats.dateTime(at);
    }
}
