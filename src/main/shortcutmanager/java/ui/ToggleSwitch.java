package shortcutmanager.ui;

import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.function.Consumer;

public class ToggleSwitch extends StackPane {
    private boolean on;
    private Consumer<Boolean> onToggle;

    private final Rectangle track = new Rectangle(44, 22);
    private final Circle thumb = new Circle(9);
    private final TranslateTransition thumbAnim;

    public ToggleSwitch(boolean initialValue) {
        this.on = initialValue;

        track.setArcWidth(22);
        track.setArcHeight(22);
        track.getStyleClass().add("toggle-track");

        thumb.getStyleClass().add("toggle-thumb");

        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(track, thumb);
        getStyleClass().add("toggle-switch");

        thumbAnim = new TranslateTransition(Duration.millis(150), thumb);

        updateVisual(false);

        setOnMouseClicked(e -> {
            on = !on;
            updateVisual(true);
            if (onToggle != null) onToggle.accept(on);
        });

        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setPrefWidth(44);
        setPrefHeight(22);
    }

    private void updateVisual(boolean animate) {
        if (on) {
            track.getStyleClass().remove("toggle-track-off");
            if (!track.getStyleClass().contains("toggle-track-on"))
                track.getStyleClass().add("toggle-track-on");
        } else {
            track.getStyleClass().remove("toggle-track-on");
            if (!track.getStyleClass().contains("toggle-track-off"))
                track.getStyleClass().add("toggle-track-off");
        }

        double targetX = on ? 11 : -11;
        if (animate) {
            thumbAnim.stop();
            thumbAnim.setToX(targetX);
            thumbAnim.play();
        } else {
            thumb.setTranslateX(targetX);
        }
    }

    public void setOnToggle(Consumer<Boolean> handler) {
        this.onToggle = handler;
    }

    public boolean isOn() {
        return on;
    }
}
