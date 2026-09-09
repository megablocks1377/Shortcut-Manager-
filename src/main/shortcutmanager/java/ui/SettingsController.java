package shortcutmanager.ui;

import shortcutmanager.data.ShortcutDataStore;
import shortcutmanager.model.AppSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class SettingsController {
    private final ShortcutDataStore dataStore;
    private final MainController mainController;

    public SettingsController(ShortcutDataStore dataStore, MainController mainController) {
        this.dataStore = dataStore;
        this.mainController = mainController;
    }

    public void show() {
        AppSettings settings = dataStore.getSettings();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Settings");
        stage.setMinWidth(520);
        stage.setMinHeight(480);
        stage.setResizable(false);

        VBox root = new VBox(0);
        root.getStyleClass().add("settings-root");

        HBox header = new HBox();
        header.getStyleClass().add("settings-header");
        header.setPadding(new Insets(20, 24, 20, 24));
        Text title = new Text("Settings");
        title.getStyleClass().add("settings-title");
        header.getChildren().add(title);

        VBox content = new VBox(0);
        content.getStyleClass().add("settings-content");

        content.getChildren().addAll(
                buildSection("Appearance",
                        buildThemeRow(settings),
                        buildFontSizeRow(settings),
                        buildAccentColorRow(settings),
                        buildCompactModeRow(settings)
                ),
                buildSection("Display",
                        buildShowCategoriesRow(settings)
                )
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        HBox footer = new HBox(8);
        footer.getStyleClass().add("settings-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 24, 16, 24));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> stage.close());

        Button saveBtn = new Button("Save Settings");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            dataStore.saveSettings();
            mainController.applyTheme(settings.getTheme());
            stage.close();
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);

        root.getChildren().addAll(header, scroll, footer);

        Scene scene = new Scene(root, 520, 480);
        String cssFile = settings.getTheme().equals("Light")
                ? "/com/shortcutmanager/light.css"
                : "/com/shortcutmanager/dark.css";
        scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    private VBox buildSection(String title, HBox... rows) {
        VBox section = new VBox(0);
        section.getStyleClass().add("settings-section");

        Label sectionTitle = new Label(title.toUpperCase());
        sectionTitle.getStyleClass().add("settings-section-label");
        sectionTitle.setPadding(new Insets(16, 24, 8, 24));

        section.getChildren().add(sectionTitle);
        for (HBox row : rows) {
            section.getChildren().add(row);
        }
        return section;
    }

    private HBox buildThemeRow(AppSettings settings) {
        HBox row = createRow("Theme", "Choose between dark and light modes");
        ToggleGroup tg = new ToggleGroup();
        RadioButton dark = new RadioButton("Dark");
        RadioButton light = new RadioButton("Light");
        dark.setToggleGroup(tg);
        light.setToggleGroup(tg);
        dark.getStyleClass().add("settings-radio");
        light.getStyleClass().add("settings-radio");
        if ("Light".equals(settings.getTheme())) light.setSelected(true);
        else dark.setSelected(true);
        tg.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == dark) settings.setTheme("Dark");
            else settings.setTheme("Light");
        });
        HBox controls = (HBox) row.getChildren().get(1);
        controls.getChildren().addAll(dark, light);
        return row;
    }

    private HBox buildFontSizeRow(AppSettings settings) {
        HBox row = createRow("Font Size", "Adjust the text size in shortcut lists");
        Slider slider = new Slider(10, 20, settings.getFontSize());
        slider.setMajorTickUnit(2);
        slider.setMinorTickCount(1);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setPrefWidth(180);
        slider.getStyleClass().add("settings-slider");
        Label sizeLabel = new Label((int) settings.getFontSize() + "px");
        sizeLabel.getStyleClass().add("settings-value-label");
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double rounded = Math.round(newVal.doubleValue());
            settings.setFontSize(rounded);
            sizeLabel.setText((int) rounded + "px");
        });
        HBox controls = (HBox) row.getChildren().get(1);
        controls.getChildren().addAll(slider, sizeLabel);
        return row;
    }

    private HBox buildAccentColorRow(AppSettings settings) {
        HBox row = createRow("Accent Color", "Pick the highlight color for the app");
        ColorPicker picker = new ColorPicker(Color.web(settings.getAccentColor()));
        picker.getStyleClass().add("settings-color-picker");
        picker.setOnAction(e -> {
            Color c = picker.getValue();
            settings.setAccentColor(String.format("#%02X%02X%02X",
                    (int) (c.getRed() * 255),
                    (int) (c.getGreen() * 255),
                    (int) (c.getBlue() * 255)));
        });
        HBox controls = (HBox) row.getChildren().get(1);
        controls.getChildren().add(picker);
        return row;
    }

    private HBox buildCompactModeRow(AppSettings settings) {
        HBox row = createRow("Compact Mode", "Show more shortcuts with less spacing");
        ToggleSwitch toggle = new ToggleSwitch(settings.isCompactMode());
        toggle.setOnToggle(settings::setCompactMode);
        HBox controls = (HBox) row.getChildren().get(1);
        controls.getChildren().add(toggle);
        return row;
    }

    private HBox buildShowCategoriesRow(AppSettings settings) {
        HBox row = createRow("Show Category Tabs", "Display shortcuts grouped by category tabs");
        ToggleSwitch toggle = new ToggleSwitch(settings.isShowCategories());
        toggle.setOnToggle(settings::setShowCategories);
        HBox controls = (HBox) row.getChildren().get(1);
        controls.getChildren().add(toggle);
        return row;
    }

    private HBox createRow(String title, String subtitle) {
        HBox row = new HBox();
        row.getStyleClass().add("settings-row");
        row.setPadding(new Insets(14, 24, 14, 24));
        row.setAlignment(Pos.CENTER_LEFT);

        VBox labelBox = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-row-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("settings-row-subtitle");
        labelBox.getChildren().addAll(titleLabel, subtitleLabel);
        HBox.setHgrow(labelBox, Priority.ALWAYS);

        HBox controlsBox = new HBox(8);
        controlsBox.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(labelBox, controlsBox);
        return row;
    }
}
