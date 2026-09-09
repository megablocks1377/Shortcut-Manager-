package shortcutmanager.ui;

import shortcutmanager.model.Shortcut;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public class ShortcutKeyCell extends TableCell<Shortcut, String> {
    private TextField textField;

    @Override
    public void startEdit() {
        if (!isEmpty()) {
            super.startEdit();
            createTextField();
            setText(null);
            setGraphic(textField);
            textField.selectAll();
            textField.requestFocus();
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setGraphic(buildKeyDisplay(getItem()));
        setText(null);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (isEditing()) {
                if (textField != null) textField.setText(item);
                setText(null);
                setGraphic(textField);
            } else {
                setText(null);
                setGraphic(buildKeyDisplay(item));
            }
        }
    }

    private HBox buildKeyDisplay(String keys) {
        HBox box = new HBox(4);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(2, 0, 2, 0));

        if (keys == null || keys.isEmpty()) return box;

        String[] parts = keys.split("\\+");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            Text keyText = new Text(part);
            javafx.scene.layout.StackPane badge = new javafx.scene.layout.StackPane(keyText);
            badge.getStyleClass().add("key-badge");
            badge.setPadding(new Insets(2, 8, 2, 8));
            box.getChildren().add(badge);

            if (i < parts.length - 1 && !parts[i + 1].trim().isEmpty()) {
                Text plus = new Text("+");
                plus.getStyleClass().add("key-plus");
                box.getChildren().add(plus);
            }
        }
        return box;
    }

    private void createTextField() {
        textField = new TextField(getItem());
        textField.setMinWidth(getWidth() - getGraphicTextGap() * 2);
        textField.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                commitEdit(textField.getText());
            } else if (e.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
            }
        });
        textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) commitEdit(textField.getText());
        });
    }
}
