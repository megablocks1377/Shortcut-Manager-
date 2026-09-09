module shortcutmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires java.xml;

    opens shortcutmanager to javafx.fxml;
    opens shortcutmanager.ui to javafx.fxml;
    opens shortcutmanager.model to javafx.fxml;
    exports shortcutmanager;
    exports shortcutmanager.ui;
    exports shortcutmanager.model;
    exports shortcutmanager.data;
    exports shortcutmanager.data.config;
    exports shortcutmanager.detection;
}
