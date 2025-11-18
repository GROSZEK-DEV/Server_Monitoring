package Main.Java.ui;

import Main.Java.server.RamConfigEditor;
import Main.Java.server.ServerProcess;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import javafx.util.Duration;

import java.io.*;

public class DashboardController {

    @FXML
    private TextArea consoleOutput;

    @FXML
    private TextField serverPathField;

    @FXML
    private TextField commandField;

    @FXML
    private Button startBtn;

    @FXML
    private Button stopBtn;

    @FXML
    private Label ramUsedLabel;

    @FXML
    private Label ramTotalLabel;

    @FXML
    private ProgressBar ramUsageBar;

    @FXML
    private TextField minRamField;

    @FXML
    private TextField maxRamField;

    // Player list table in center
    @FXML
    private TableView<PlayerViewModel> playerTable;

    @FXML
    private TableColumn<PlayerViewModel, PlayerViewModel> playerNameColumn;

    @FXML
    private TableColumn<PlayerViewModel, Number> playerPingColumn;

    @FXML
    private TableColumn<PlayerViewModel, Void> playerKickColumn;

    @FXML
    private TableColumn<PlayerViewModel, Void> playerBanColumn;

    @FXML
    private TableColumn<PlayerViewModel, Boolean> playerOpColumn;

    @FXML
    private StackPane rootPane; // add fx:id on root or a top-level StackPane in FXML

    @FXML
    private StackPane splashOverlay;

    private ServerProcess serverProcess;
    private Thread consoleReaderThread;
    private Thread ramMonitorThread;
    private volatile boolean serverStarted = false;

    @FXML
    private void initialize() {
        stopBtn.setDisable(true);

        // Intro splash fade-out on app startup
        if (splashOverlay != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(800), splashOverlay);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setDelay(Duration.millis(1000));
            fadeOut.setOnFinished(e -> rootPane.getChildren().remove(splashOverlay));
            fadeOut.play();
        }

        // Send command on Enter key
        commandField.setOnAction(event -> onSendCommand());

        // Configure player table
        setupPlayerTable();

        // RAM monitor will show 0 GB until serverStarted is true
        startRamMonitor();
    }

    private void setupPlayerTable() {
        // Name column with head image + name label
        playerNameColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue()));
        playerNameColumn.setCellFactory(col -> new TableCell<PlayerViewModel, PlayerViewModel>() {
            private final ImageView imageView = new ImageView();
            private final Label nameLabel = new Label();
            private final HBox container = new HBox(6, imageView, nameLabel);

            {
                imageView.setFitWidth(20);
                imageView.setFitHeight(20);
                imageView.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(PlayerViewModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getName());
                    Image head = item.getSkinHead();
                    imageView.setImage(head);
                    setGraphic(container);
                }
            }
        });

        // Ping column
        playerPingColumn.setCellValueFactory(data -> data.getValue().pingProperty());

        // Operator checkbox column (no server integration yet)
        playerOpColumn.setCellValueFactory(data -> data.getValue().operatorProperty());
        playerOpColumn.setCellFactory(CheckBoxTableCell.forTableColumn(playerOpColumn));

        // Kick/Ban button columns (placeholders that log to console)
        addButtonToColumn(playerKickColumn, "Kick", name -> {
            appendToConsole("Kick " + name + " (not yet wired to server)\n");
        });
        addButtonToColumn(playerBanColumn, "Ban", name -> {
            appendToConsole("Ban " + name + " (not yet wired to server)\n");
        });
        }

    private interface PlayerAction {
        void perform(String playerName);
    }

    private void addButtonToColumn(TableColumn<PlayerViewModel, Void> column, String label, PlayerAction action) {
        Callback<TableColumn<PlayerViewModel, Void>, TableCell<PlayerViewModel, Void>> cellFactory = param -> new TableCell<>() {
            private final Button btn = new Button(label);

            {
                btn.setOnAction(event -> {
                    PlayerViewModel player = getTableView().getItems().get(getIndex());
                    action.perform(player.getName());
                });
                btn.getStyleClass().add("secondary-button");
                btn.setPrefWidth(60);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        };
        column.setCellFactory(cellFactory);
    }

    private Image loadDummyHead() {
        // Placeholder: you'd replace this with a real skin head URL or local resource
        // For now, this returns null which keeps the ImageView empty but functional
        return null;
    }

    @FXML
    private void onBrowseServer() {
        Window window = consoleOutput.getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Minecraft server folder");
        File folder = chooser.showDialog(window);
        if (folder != null) {
            serverPathField.setText(folder.getAbsolutePath());
        }
    }

    @FXML
    private void onStartServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            appendToConsole("Server is already running.\n");
            return;
        }
        String path = serverPathField.getText();
        if (path == null || path.isBlank()) {
            appendToConsole("Please choose a server folder first.\n");
            return;
        }
        File dir = new File(path);
        if (!dir.isDirectory()) {
            appendToConsole("Selected path is not a folder.\n");
            return;
        }
        serverProcess = new ServerProcess(dir);
        try {
            showStartupAnimation();
            appendToConsole("Starting server in: " + dir.getAbsolutePath() + "\n");
            serverProcess.start();
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
            startConsoleReader();
        } catch (Exception e) {
            appendToConsole("Failed to start server: " + e.getMessage() + "\n");
            e.printStackTrace();
            serverProcess = null;
        }
    }

    @FXML
    private void onStopServer() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            appendToConsole("Server is not running.\n");
            return;
        }
        try {
            serverProcess.sendCommand("stop");
            appendToConsole("Sent stop command to server.\n");
        } catch (Exception e) {
            appendToConsole("Failed to send stop command: " + e.getMessage() + "\n");
        }
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        serverStarted = false;
    }

    @FXML
    private void onRestartServer() {
        appendToConsole("Restarting server...\n");
        onStopServer();
        // Simple delay to let stop command process
        new Thread(() -> {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException ignored) {}
            Platform.runLater(this::onStartServer);
        }).start();
    }

    @FXML
    private void onSendCommand() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            appendToConsole("Server is not running.\n");
            return;
        }
        String cmd = commandField.getText();
        if (cmd == null || cmd.isBlank()) {
            return;
        }
        try {
            serverProcess.sendCommand(cmd);
            appendToConsole("> " + cmd + "\n");
            commandField.clear();
        } catch (Exception e) {
            appendToConsole("Failed to send command: " + e.getMessage() + "\n");
        }
    }

    @FXML
    private void onApplyRamSettings() {
        String path = serverPathField.getText();
        if (path == null || path.isBlank()) {
            appendToConsole("Please choose a server folder first.\n");
            return;
        }
        File dir = new File(path);
        File runBat = new File(dir, "run.bat");
        if (!runBat.exists()) {
            appendToConsole("Could not find run.bat in the selected folder.\n");
            return;
        }
        String min = minRamField.getText();
        String max = maxRamField.getText();
        if (min == null || min.isBlank() || max == null || max.isBlank()) {
            appendToConsole("Please enter both min and max RAM values (e.g. 1G, 4G).\n");
            return;
        }
        try {
            RamConfigEditor.UpdateRam(runBat, max, min);
            appendToConsole("Updated RAM settings in run.bat to Xms=" + min + ", Xmx=" + max + "\n");
        } catch (IOException e) {
            appendToConsole("Failed to update RAM settings: " + e.getMessage() + "\n");
        }
    }

    private void startConsoleReader() {
        try {
            InputStream stream = serverProcess.getConsoleStream();
            if (stream == null) {
                appendToConsole("No console stream available.\n");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            consoleReaderThread = new Thread(() -> {
                String line;
                boolean bootSection = true;
                try {
                    while ((line = reader.readLine()) != null) {
                        String finalLine = line;
                        // Filter boot noise until serverStarted is set
                        if (bootSection) {
                            if (finalLine.toLowerCase().contains("done") ||
                                finalLine.toLowerCase().contains("ready") ||
                                finalLine.toLowerCase().contains("server started")) {
                                serverStarted = true;
                                bootSection = false;
                                Platform.runLater(() -> appendToConsole(finalLine + "\n"));
                            }
                        } else {
                            Platform.runLater(() -> appendToConsole(finalLine + "\n"));
                        }
                    }
                } catch (IOException e) {
                    Platform.runLater(() -> appendToConsole("Console reader stopped: " + e.getMessage() + "\n"));
                }
            }, "server-console-reader");
            consoleReaderThread.setDaemon(true);
            consoleReaderThread.start();
        } catch (Exception e) {
            appendToConsole("Failed to start console reader: " + e.getMessage() + "\n");
        }
    }

    private void showStartupAnimation() {
        if (rootPane == null) {
            return;
        }
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(80, 80);
        StackPane overlay = new StackPane(indicator);
        overlay.setStyle("-fx-background-color: rgba(15,23,42,0.7);");
        rootPane.getChildren().add(overlay);

        FadeTransition ft = new FadeTransition(Duration.millis(600), overlay);
        ft.setFromValue(0.0);
        ft.setToValue(1.0);
        ft.play();

        // Remove overlay when server is marked started (in console reader)
        new Thread(() -> {
            while (!serverStarted) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
            Platform.runLater(() -> rootPane.getChildren().remove(overlay));
        }).start();
    }

    private void startRamMonitor() {
        ramMonitorThread = new Thread(() -> {
            Runtime rt = Runtime.getRuntime();
            while (true) {
                long total = rt.totalMemory();
                long used = total - rt.freeMemory();
                double usedGb = used / (1024.0 * 1024.0 * 1024.0);
                double totalGb = total / (1024.0 * 1024.0 * 1024.0);

                double displayUsed = serverStarted ? usedGb : 0.0;
                double displayTotal = serverStarted ? totalGb : 0.0;
                double progress = (serverStarted && totalGb > 0) ? usedGb / totalGb : 0.0;

                Platform.runLater(() -> {
                    if (ramUsedLabel != null) {
                        ramUsedLabel.setText(String.format("%.2f GB", displayUsed));
                    }
                    if (ramTotalLabel != null) {
                        ramTotalLabel.setText(String.format("%.2f GB", displayTotal));
                    }
                    if (ramUsageBar != null) {
                        ramUsageBar.setProgress(progress);
                    }
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "ram-monitor");
        ramMonitorThread.setDaemon(true);
        ramMonitorThread.start();
    }

    private void appendToConsole(String text) {
        if (consoleOutput == null) return;
        consoleOutput.appendText(text);
    }
}
