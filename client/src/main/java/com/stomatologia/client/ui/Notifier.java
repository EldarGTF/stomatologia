package com.stomatologia.client.ui;

import com.stomatologia.client.api.ApiClient;
import com.stomatologia.client.controller.LeadsController;
import com.stomatologia.client.model.NotificationModels.NotificationDto;
import com.stomatologia.client.model.NotificationModels.NotificationFeed;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Уведомления администратора и регистратора: раз в 15 секунд спрашивает сервер о новых заявках
 * и сообщениях клиентов и показывает карточки со звуком в правом нижнем углу окна.
 */
public final class Notifier {

    private static final Logger log = LogManager.getLogger(Notifier.class);

    static final int MAX_CARDS = 3;
    private static final Duration POLL = Duration.seconds(15);
    private static final Duration LIFETIME = Duration.seconds(15);
    private static final double WIDTH = 340;
    private static final double MARGIN = 20;

    private static Notifier current;

    private final Window owner;
    private final Timeline poll;
    private final Popup popup = new Popup();
    private final VBox cards = new VBox(8);
    private final ChangeListener<Number> follow = (obs, o, n) -> place();
    private Long lastLead;
    private Long lastMessage;
    private boolean polling;

    private Notifier(Window owner) {
        this.owner = owner;
        this.poll = new Timeline(new KeyFrame(POLL, e -> check()));
        poll.setCycleCount(Timeline.INDEFINITE);
        cards.setFillWidth(true);
        popup.getContent().add(cards);
        popup.setAutoFix(true);
        popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_TOP_LEFT);
        popup.setHideOnEscape(false);
        popup.getScene().getStylesheets().addAll(owner.getScene().getStylesheets());
        owner.xProperty().addListener(follow);
        owner.yProperty().addListener(follow);
        owner.widthProperty().addListener(follow);
        owner.heightProperty().addListener(follow);
    }

    public static void start(Window owner) {
        stop();
        current = new Notifier(owner);
        current.check();
        current.poll.play();
    }

    public static void stop() {
        if (current == null) {
            return;
        }
        Notifier n = current;
        current = null;
        n.poll.stop();
        n.popup.hide();
        n.owner.xProperty().removeListener(n.follow);
        n.owner.yProperty().removeListener(n.follow);
        n.owner.widthProperty().removeListener(n.follow);
        n.owner.heightProperty().removeListener(n.follow);
    }

    private void check() {
        if (polling) {
            return;
        }
        polling = true;
        String path = ApiClient.query("/api/notifications", "afterLead", lastLead, "afterMessage", lastMessage);
        Fx.async(() -> ApiClient.get().get(path, NotificationFeed.class), feed -> {
            polling = false;
            if (current != this) {
                return;
            }
            lastLead = feed.lastLeadId();
            lastMessage = feed.lastMessageId();
            if (!feed.items().isEmpty()) {
                collapse(feed.items()).forEach(n -> cards.getChildren().add(card(n)));
                while (cards.getChildren().size() > MAX_CARDS) {
                    cards.getChildren().remove(0);
                }
                place();
                Chime.play();
            }
        }, ex -> {
            polling = false;
            log.debug("Уведомления не получены: {}", ex.getMessage());
        });
    }

    /**
     * Не больше {@link #MAX_CARDS} карточек: если событий больше, первая — сводка «ещё N» без заявки.
     */
    static List<NotificationDto> collapse(List<NotificationDto> items) {
        if (items.size() <= MAX_CARDS) {
            return items;
        }
        int hidden = items.size() - (MAX_CARDS - 1);
        List<NotificationDto> result = new ArrayList<>();
        result.add(new NotificationDto(null, null, "Ещё " + hidden + " " + Formats.plural(hidden, "событие",
                "события", "событий"), "Новые заявки и сообщения клиентов", null));
        result.addAll(items.subList(items.size() - (MAX_CARDS - 1), items.size()));
        return result;
    }

    private Node card(NotificationDto n) {
        Label title = new Label(n.title());
        title.getStyleClass().add("notice-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕");
        close.getStyleClass().add("notice-close");
        HBox head = new HBox(8, title, spacer, close);
        head.setAlignment(Pos.CENTER_LEFT);

        Label text = new Label(n.text());
        text.setWrapText(true);
        text.getStyleClass().add("notice-text");
        Label hint = new Label(n.leadId() != null ? "Нажмите, чтобы открыть заявку" : "Нажмите, чтобы открыть «Заявки»");
        hint.getStyleClass().add("notice-hint");

        VBox box = new VBox(4, head, text, hint);
        box.getStyleClass().add("notice");
        box.setPrefWidth(WIDTH);
        box.setMaxWidth(WIDTH);

        PauseTransition life = new PauseTransition(LIFETIME);
        life.setOnFinished(e -> remove(box));
        box.hoverProperty().addListener((obs, was, now) -> {
            if (now) {
                life.pause();
            } else {
                life.playFromStart();
            }
        });
        close.setOnAction(e -> {
            life.stop();
            remove(box);
        });
        box.setOnMouseClicked(e -> {
            life.stop();
            remove(box);
            LeadsController.open(n.leadId());
        });
        life.play();
        return box;
    }

    private void remove(Node card) {
        cards.getChildren().remove(card);
        place();
    }

    private void place() {
        if (cards.getChildren().isEmpty()) {
            popup.hide();
            return;
        }
        if (!owner.isShowing()) {
            return;
        }
        cards.applyCss();
        cards.layout();
        double height = cards.prefHeight(WIDTH);
        Scene scene = owner.getScene();
        double x = owner.getX() + scene.getX() + scene.getWidth() - WIDTH - MARGIN;
        double y = owner.getY() + scene.getY() + scene.getHeight() - height - MARGIN;
        if (popup.isShowing()) {
            popup.setAnchorX(x);
            popup.setAnchorY(y);
        } else {
            popup.show(owner, x, y);
        }
    }
}
