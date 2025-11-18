package Main.Java;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.awt.*;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass()
                .getResource("/Main/resources/layout.fxml"));

        Parent root = loader.load(); // This loads the FXML

        Scene scene = new Scene(root);

        // Load CSS styling
        scene.getStylesheets().add(getClass()
                .getResource("/Main/resources/style.css").toExternalForm());

        stage.setTitle("Minecraft Server Dashboard");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
