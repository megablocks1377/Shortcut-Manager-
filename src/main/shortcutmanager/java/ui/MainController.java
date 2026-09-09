package shortcutmanager.ui;

import shortcutmanager.data.ShortcutDataStore;
import shortcutmanager.data.config.ConfigReader;
import shortcutmanager.detection.AppDetector;
import shortcutmanager.detection.AppInfo;
import shortcutmanager.model.AppShortcuts;
import shortcutmanager.model.Shortcut;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    // ── FXML bindings ──────────────────────────────────────────────────────────
    @FXML private ComboBox<String> appDropdown;
    @FXML private TextField searchField;
    @FXML private Label appIconLabel;
    @FXML private Label appNameLabel;
    @FXML private Label shortcutCountLabel;
    @FXML private TabPane categoryTabs;
    @FXML private TableView<Shortcut> allShortcutsTable;
    @FXML private TableColumn<Shortcut, String> actionColumn;
    @FXML private TableColumn<Shortcut, String> keysColumn;
    @FXML private TableColumn<Shortcut, String> categoryColumn;
    @FXML private Button addShortcutBtn;
    @FXML private Button deleteShortcutBtn;
    @FXML private Button settingsBtn;
    @FXML private StackPane contentPane;
    @FXML private ToggleButton autoDetectToggle;
    @FXML private Label detectionStatusLabel;

    // ── State ──────────────────────────────────────────────────────────────────
    private ShortcutDataStore dataStore;
    private AppShortcuts currentApp;
    private SettingsController settingsController;
    private AppDetector appDetector;
    private ScheduledExecutorService detectionScheduler;

    private final ObservableList<Shortcut> shortcutList = FXCollections.observableArrayList();
    private FilteredList<Shortcut> filteredList;

    // Track the last focused app name to avoid redundant reloads
    private String lastFocusedApp = null;

    // Full list of app names for manual mode (restored when auto-detect is turned off)
    private List<String> allAppNames = new ArrayList<>();

    // ── Init ───────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dataStore = new ShortcutDataStore();
        settingsController = new SettingsController(dataStore, this);
        appDetector = new AppDetector();

        setupDropdown();
        setupTable();
        setupSearch();
        setupButtons();
        setupAutoDetect();

        if (!appDropdown.getItems().isEmpty()) {
            appDropdown.getSelectionModel().selectFirst();
        }
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private void setupDropdown() {
        // Populate with built-in apps by default; detected apps are added dynamically
        allAppNames = dataStore.getAllApps().stream()
                .map(AppShortcuts::getAppName)
                .collect(Collectors.toList());
        appDropdown.setItems(FXCollections.observableArrayList(allAppNames));
        appDropdown.setOnAction(e -> {
            String selected = appDropdown.getSelectionModel().getSelectedItem();
            if (selected != null) loadApp(selected);
        });
    }

    private void setupTable() {
        filteredList = new FilteredList<>(shortcutList, p -> true);

        actionColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAction()));
        keysColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getKeys()));
        categoryColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCategory()));

        actionColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        actionColumn.setOnEditCommit(e -> {
            e.getRowValue().setAction(e.getNewValue());
            dataStore.saveCustomShortcuts();
        });

        keysColumn.setCellFactory(col -> new ShortcutKeyCell());
        keysColumn.setOnEditCommit(e -> {
            e.getRowValue().setKeys(e.getNewValue());
            dataStore.saveCustomShortcuts();
            refreshCurrentApp();
        });

        categoryColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        categoryColumn.setOnEditCommit(e -> {
            e.getRowValue().setCategory(e.getNewValue());
            dataStore.saveCustomShortcuts();
            refreshCurrentApp();
        });

        allShortcutsTable.setItems(filteredList);
        allShortcutsTable.setEditable(true);
        allShortcutsTable.setPlaceholder(new Label("No shortcuts found"));

        allShortcutsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> deleteShortcutBtn.setDisable(n == null));

        setupContextMenu(allShortcutsTable, true);
    }

    private void setupContextMenu(TableView<Shortcut> tv, boolean includeDelete) {
        ContextMenu cm = new ContextMenu();
        MenuItem editItem = new MenuItem("Edit Shortcut");
        editItem.setOnAction(e -> {
            Shortcut sel = tv.getSelectionModel().getSelectedItem();
            if (sel != null) showEditDialog(sel);
        });
        MenuItem copyItem = new MenuItem("Copy Keys");
        copyItem.setOnAction(e -> {
            Shortcut sel = tv.getSelectionModel().getSelectedItem();
            if (sel != null) copyToClipboard(sel.getKeys());
        });
        cm.getItems().addAll(editItem, copyItem);
        if (includeDelete) {
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> deleteSelected());
            cm.getItems().addAll(new SeparatorMenuItem(), deleteItem);
        }
        tv.setContextMenu(cm);
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, old, val) -> {
            String lower = val.toLowerCase().trim();
            filteredList.setPredicate(s -> lower.isEmpty()
                    || s.getAction().toLowerCase().contains(lower)
                    || s.getKeys().toLowerCase().contains(lower)
                    || s.getCategory().toLowerCase().contains(lower));
            updateCount();
        });
    }

    private void setupButtons() {
        deleteShortcutBtn.setDisable(true);
        addShortcutBtn.setOnAction(e -> showAddDialog());
        deleteShortcutBtn.setOnAction(e -> deleteSelected());
        settingsBtn.setOnAction(e -> settingsController.show());
    }

    private void setupAutoDetect() {
        autoDetectToggle.setOnAction(e -> {
            if (autoDetectToggle.isSelected()) {
                startDetection();
            } else {
                stopDetection();
                detectionStatusLabel.setText("Manual mode");
            }
        });
        detectionStatusLabel.setText("Manual mode");
    }

    // ── Detection ──────────────────────────────────────────────────────────────

    private void startDetection() {
        // Clear the dropdown — auto-detect mode only shows running apps
        appDropdown.getItems().clear();
        appDropdown.setPromptText("Scanning for running apps...");
        detectionStatusLabel.setText("Scanning...");
        lastFocusedApp = null;
        detectionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "app-detector");
            t.setDaemon(true);
            return t;
        });
        detectionScheduler.scheduleAtFixedRate(this::runDetection, 0, 3, TimeUnit.SECONDS);
    }

    private void stopDetection() {
        if (detectionScheduler != null) {
            detectionScheduler.shutdownNow();
            detectionScheduler = null;
        }
        lastFocusedApp = null;
        // Restore full app list for manual browsing
        appDropdown.setPromptText("Select an application...");
        appDropdown.setItems(FXCollections.observableArrayList(allAppNames));
        if (!appDropdown.getItems().isEmpty()) {
            appDropdown.getSelectionModel().selectFirst();
        }
        detectionStatusLabel.setText("Manual mode");
    }

    private void runDetection() {
        try {
            List<AppInfo> runningApps = appDetector.detectRunningApps();
            String focusedApp = appDetector.detectFocusedApp();

            Platform.runLater(() -> {
                // Rebuild the dropdown to only show currently running known apps
                updateDropdownWithDetectedApps(runningApps);

                if (runningApps.isEmpty()) {
                    detectionStatusLabel.setText("No known apps detected");
                    return;
                }
                if (focusedApp != null && !focusedApp.equals(lastFocusedApp)) {
                    lastFocusedApp = focusedApp;
                    autoSelectApp(focusedApp);
                    detectionStatusLabel.setText("Active: " + focusedApp);
                } else if (focusedApp == null) {
                    detectionStatusLabel.setText(runningApps.size() + " app(s) running");
                }
            });
        } catch (Exception e) {
            Platform.runLater(() -> detectionStatusLabel.setText("Detection error"));
        }
    }

    /**
     * Rebuild the dropdown to exactly match the currently running apps.
     * Preserves the selected item if it is still running.
     */
    private void updateDropdownWithDetectedApps(List<AppInfo> runningApps) {
        String currentlySelected = appDropdown.getSelectionModel().getSelectedItem();

        List<String> runningNames = runningApps.stream()
                .map(AppInfo::getAppName)
                .collect(Collectors.toList());

        // Ensure data store has entries for all detected apps
        for (AppInfo info : runningApps) {
            dataStore.ensureAppExists(info.getAppName(), "🖥️");
        }

        // Only update if list changed (avoid flickering)
        if (!new ArrayList<>(appDropdown.getItems()).equals(runningNames)) {
            appDropdown.setItems(FXCollections.observableArrayList(runningNames));
        }

        // Re-select the same app if still running, otherwise pick first
        if (currentlySelected != null && runningNames.contains(currentlySelected)) {
            appDropdown.getSelectionModel().select(currentlySelected);
        } else if (!runningNames.isEmpty()) {
            appDropdown.getSelectionModel().selectFirst();
        }
    }

    /**
     * Switch the dropdown (and load) to the focused app if it's in our list.
     */
    private void autoSelectApp(String appName) {
        if (appDropdown.getItems().contains(appName)) {
            appDropdown.getSelectionModel().select(appName);
            loadApp(appName);
        }
    }

    // ── App Loading ────────────────────────────────────────────────────────────

    public void loadApp(String appName) {
        currentApp = dataStore.getApp(appName);
        if (currentApp == null) return;

        appIconLabel.setText(currentApp.getIcon());
        appNameLabel.setText(currentApp.getAppName());

        // Show config source badge
        ConfigReader reader = dataStore.getConfigReader(appName);
        if (reader != null && reader.isInstalled()) {
            appNameLabel.setText(currentApp.getAppName() + "  ✓ Config detected");
        }

        shortcutList.setAll(currentApp.getShortcuts());
        searchField.clear();
        updateCount();
        buildCategoryTabs();
    }

    private void buildCategoryTabs() {
        categoryTabs.getTabs().clear();

        Tab allTab = new Tab("All");
        allTab.setClosable(false);
        allTab.setContent(allShortcutsTable);
        categoryTabs.getTabs().add(allTab);

        if (currentApp == null) return;

        Map<String, List<Shortcut>> byCategory = currentApp.getShortcuts().stream()
                .collect(Collectors.groupingBy(Shortcut::getCategory, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Shortcut>> entry : byCategory.entrySet()) {
            Tab tab = new Tab(entry.getKey() + " (" + entry.getValue().size() + ")");
            tab.setClosable(false);
            tab.setContent(buildCategoryTable(entry.getValue()));
            categoryTabs.getTabs().add(tab);
        }
    }

    private TableView<Shortcut> buildCategoryTable(List<Shortcut> shortcuts) {
        TableView<Shortcut> tv = new TableView<>();
        tv.setEditable(true);
        tv.getStyleClass().add("shortcuts-table");

        TableColumn<Shortcut, String> ac = new TableColumn<>("Action");
        ac.setPrefWidth(320);
        ac.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getAction()));
        ac.setCellFactory(TextFieldTableCell.forTableColumn());
        ac.setOnEditCommit(e -> {
            e.getRowValue().setAction(e.getNewValue());
            dataStore.saveCustomShortcuts();
        });

        TableColumn<Shortcut, String> kc = new TableColumn<>("Shortcut Keys");
        kc.setPrefWidth(200);
        kc.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getKeys()));
        kc.setCellFactory(col -> new ShortcutKeyCell());
        kc.setOnEditCommit(e -> {
            e.getRowValue().setKeys(e.getNewValue());
            dataStore.saveCustomShortcuts();
        });

        tv.getColumns().addAll(ac, kc);
        tv.setItems(FXCollections.observableArrayList(shortcuts));
        tv.setPlaceholder(new Label("No shortcuts in this category"));

        setupContextMenu(tv, false);
        return tv;
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    private void showAddDialog() {
        if (currentApp == null) return;
        Dialog<Shortcut> dialog = new Dialog<>();
        dialog.setTitle("Add Shortcut");
        dialog.setHeaderText("Add a new shortcut for " + currentApp.getAppName());
        dialog.getDialogPane().getStylesheets().addAll(allShortcutsTable.getScene().getStylesheets());

        ButtonType saveBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = buildDialogGrid();
        TextField actionField = new TextField();
        actionField.setPromptText("e.g. Open New Tab");
        TextField keysField = new TextField();
        keysField.setPromptText("e.g. Ctrl+T");

        Set<String> existingCats = currentApp.getShortcuts().stream()
                .map(Shortcut::getCategory).collect(Collectors.toCollection(LinkedHashSet::new));
        ComboBox<String> catCombo = buildCategoryCombo(existingCats, null);

        grid.add(new Label("Action:"), 0, 0); grid.add(actionField, 1, 0);
        grid.add(new Label("Keys:"), 0, 1);   grid.add(keysField, 1, 1);
        grid.add(new Label("Category:"), 0, 2); grid.add(catCombo, 1, 2);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(actionField::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String cat = catCombo.getEditor().getText().trim();
                if (cat.isEmpty()) cat = "Custom";
                return new Shortcut(actionField.getText().trim(), keysField.getText().trim(), cat, true);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(s -> {
            if (!s.getAction().isEmpty() && !s.getKeys().isEmpty()) {
                currentApp.addShortcut(s);
                shortcutList.setAll(currentApp.getShortcuts());
                dataStore.saveCustomShortcuts();
                buildCategoryTabs();
                updateCount();
            }
        });
    }

    private void showEditDialog(Shortcut shortcut) {
        Dialog<Shortcut> dialog = new Dialog<>();
        dialog.setTitle("Edit Shortcut");
        dialog.setHeaderText("Edit: " + shortcut.getAction());
        dialog.getDialogPane().getStylesheets().addAll(allShortcutsTable.getScene().getStylesheets());

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = buildDialogGrid();
        TextField actionField = new TextField(shortcut.getAction());
        TextField keysField = new TextField(shortcut.getKeys());

        Set<String> existingCats = currentApp != null ? currentApp.getShortcuts().stream()
                .map(Shortcut::getCategory).collect(Collectors.toCollection(LinkedHashSet::new))
                : new LinkedHashSet<>();
        ComboBox<String> catCombo = buildCategoryCombo(existingCats, shortcut.getCategory());

        grid.add(new Label("Action:"), 0, 0); grid.add(actionField, 1, 0);
        grid.add(new Label("Keys:"), 0, 1);   grid.add(keysField, 1, 1);
        grid.add(new Label("Category:"), 0, 2); grid.add(catCombo, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String cat = catCombo.getEditor().getText().trim();
                shortcut.setAction(actionField.getText().trim());
                shortcut.setKeys(keysField.getText().trim());
                shortcut.setCategory(cat.isEmpty() ? shortcut.getCategory() : cat);
                return shortcut;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(s -> {
            shortcutList.setAll(currentApp.getShortcuts());
            dataStore.saveCustomShortcuts();
            buildCategoryTabs();
        });
    }

    private void deleteSelected() {
        Shortcut sel = allShortcutsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Shortcut");
        alert.setHeaderText("Delete \"" + sel.getAction() + "\"?");
        alert.setContentText("This action cannot be undone.");
        alert.getDialogPane().getStylesheets().addAll(allShortcutsTable.getScene().getStylesheets());
        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                currentApp.getShortcuts().remove(sel);
                shortcutList.setAll(currentApp.getShortcuts());
                dataStore.saveCustomShortcuts();
                buildCategoryTabs();
                updateCount();
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private GridPane buildDialogGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));
        return grid;
    }

    private ComboBox<String> buildCategoryCombo(Set<String> categories, String selected) {
        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(categories));
        combo.setEditable(true);
        combo.setPrefWidth(200);
        combo.setPromptText("Select or type category");
        if (selected != null) combo.setValue(selected);
        return combo;
    }

    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        cb.setContent(content);
    }

    private void refreshCurrentApp() {
        if (currentApp != null) {
            shortcutList.setAll(currentApp.getShortcuts());
            buildCategoryTabs();
            updateCount();
        }
    }

    private void updateCount() {
        int total = currentApp != null ? currentApp.getShortcuts().size() : 0;
        int showing = filteredList != null ? filteredList.size() : total;
        shortcutCountLabel.setText(showing == total
                ? total + " shortcuts"
                : showing + " of " + total + " shortcuts");
    }

    // ── Public API (called by SettingsController) ──────────────────────────────

    public ShortcutDataStore getDataStore() { return dataStore; }

    public void applyTheme(String theme) {
        if (allShortcutsTable.getScene() != null) {
            allShortcutsTable.getScene().getStylesheets().clear();
            String css = theme.equals("Light")
                    ? "/com/shortcutmanager/light.css"
                    : "/com/shortcutmanager/dark.css";
            allShortcutsTable.getScene().getStylesheets().add(
                    getClass().getResource(css).toExternalForm());
        }
    }

    /** Called when the window is closed — clean up background threads */
    public void shutdown() {
        stopDetection();
    }
}
