package com.codingchili.ethereumingest.views;

import com.codingchili.core.context.StartupListener;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ResourceBundle;

import static com.codingchili.core.files.Configurations.launcher;
import static com.codingchili.ethereumingest.views.Settings.SETTINGS_FXML;

/**
 * This view is shown when the application is loading.
 */
public class Splash implements ApplicationScene {
    public static final String SPLASH_XML = "/splash.fxml";
    @FXML
    Label version;
    @FXML
    Label title;
    @FXML
    Hyperlink author;
    @FXML
    ProgressIndicator loading;

    @FXML
    private void openGithubLink(Event event) {
        try {
            Desktop.getDesktop().browse(URI.create("https://github.com/codingchili"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Form.centerLabelText(title);
        Form.centerLabelText(version);

        version.setText(launcher().getVersion());
        title.setText(launcher().getApplication());
        author.setText("An app by " + launcher().getAuthor());

        StartupListener.subscibe(core -> {
            Async.onExecutor(() -> {
                Platform.runLater(() -> {
                    loading.setVisible(false);
                });
            });
            Async.timer(2350, () -> {
                Async.setScene(SETTINGS_FXML);
            });
        });
    }
}
