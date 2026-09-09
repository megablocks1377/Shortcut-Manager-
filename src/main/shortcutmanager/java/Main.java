package shortcutmanager;

import shortcutmanager.ui.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/shortcutmanager/main.fxml")
        );
        Scene scene = new Scene(loader.load(), 1050, 700);
        scene.getStylesheets().add(
                getClass().getResource("/com/shortcutmanager/dark.css").toExternalForm()
        );

        MainController controller = loader.getController();
        primaryStage.setTitle("Shortcut Manager");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(860);
        primaryStage.setMinHeight(600);
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> controller.shutdown());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
