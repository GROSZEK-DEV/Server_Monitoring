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
import java.lang.management.ManagementFactory;

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

    private volatile long serverPid = -1L;

    private volatile double configuredMaxRamGb = 0.0;

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
                nameLabel.getStyleClass().add("player-name-label");
                nameLabel.getStyleClass().add("minecraft-font");
            }

            @Override
            protected void updateItem(PlayerViewModel item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.getName());
                    // Load player head from Minotar API based on name
                    String url = "https://minotar.net/avatar/" + item.getName() + "/40.png";
                    Image head = new Image(url, true);
                    imageView.setImage(head);
                    setGraphic(container);
                }
            }
        });

        // Operator checkbox column (no server integration yet)
        playerOpColumn.setCellValueFactory(data -> data.getValue().operatorProperty());
        playerOpColumn.setCellFactory(col -> new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty) {
                    int index = getIndex();
                    if (index >= 0 && index < playerTable.getItems().size()) {
                        PlayerViewModel player = playerTable.getItems().get(index);
                        selectedProperty().addListener((obs, oldVal, newVal) -> {
                            if (newVal != null && !oldVal.equals(newVal)) {
                                onToggleOp(player.getName(), newVal);
                                player.operatorProperty().set(newVal);
                            }
                        });
                    }
                }
            }
        });

        // Kick/Ban button columns (placeholders that log to console)
        addButtonToColumn(playerKickColumn, "Kick", this::onKickPlayer);
        addButtonToColumn(playerBanColumn, "Ban", this::onBanPlayer);
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
            // capture PID if available
            Process p = serverProcess.getProcess();
            try {
                serverPid = p.pid();
            } catch (Throwable ignored) {
                serverPid = -1L;
            }
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
            configuredMaxRamGb = parseRamToGb(max);
            appendToConsole("Updated RAM settings in run.bat to Xms=" + min + ", Xmx=" + max + "\n");
        } catch (IOException e) {
            appendToConsole("Failed to update RAM settings: " + e.getMessage() + "\n");
        }
    }

    private double parseRamToGb(String value) {
        String v = value.trim().toUpperCase();
        try {
            if (v.endsWith("G")) {
                return Double.parseDouble(v.substring(0, v.length() - 1));
            } else if (v.endsWith("M")) {
                return Double.parseDouble(v.substring(0, v.length() - 1)) / 1024.0;
            } else {
                return Double.parseDouble(v); // assume GB
            }
        } catch (NumberFormatException e) {
            return 0.0;
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
                        // Player join/leave parsing
                        handlePlayerEvents(finalLine);
                        // Filter boot noise until serverStarted is set
                        if (bootSection) {
                            String lower = finalLine.toLowerCase();
                            if (lower.contains("done") ||
                                lower.contains("ready") ||
                                lower.contains("server started")) {
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

    private void handlePlayerEvents(String logLine) {
        // Basic patterns; adjust as needed for your log format
        // Example: "[19:32:07] [Server thread/INFO]: PlayerName joined the game"
        String lower = logLine.toLowerCase();
        if (lower.contains(" joined the game")) {
            String name = extractPlayerName(logLine, "joined the game");
            if (name != null && !name.isBlank()) {
                Platform.runLater(() -> addOrUpdatePlayer(name));
            }
        } else if (lower.contains(" left the game")) {
            String name = extractPlayerName(logLine, "left the game");
            if (name != null && !name.isBlank()) {
                Platform.runLater(() -> removePlayer(name));
            }
        }
    }

    private String extractPlayerName(String logLine, String marker) {
        int idx = logLine.indexOf(marker);
        if (idx <= 0) return null;
        String before = logLine.substring(0, idx).trim();
        // Take the last token before the marker as the name
        int lastSpace = before.lastIndexOf(' ');
        if (lastSpace >= 0 && lastSpace < before.length() - 1) {
            return before.substring(lastSpace + 1).trim();
        }
        return before;
    }

    private void addOrUpdatePlayer(String name) {
        if (playerTable == null) return;
        PlayerViewModel existing = playerTable.getItems().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            // Create view model; head image will be lazily loaded in the cell from Minotar
            playerTable.getItems().add(new PlayerViewModel(name, 0, false, null));
        }
    }

    private void removePlayer(String name) {
        if (playerTable == null) return;
        playerTable.getItems().removeIf(p -> p.getName().equalsIgnoreCase(name));
    }

    private void startRamMonitor() {
        ramMonitorThread = new Thread(() -> {
            while (true) {
                double usedGb = 0.0;
                // As a fallback, approximate using this JVM's heap; real server RSS would
                // require OS-specific calls. We still scale against configuredMaxRamGb.
                Runtime rt = Runtime.getRuntime();
                long total = rt.totalMemory();
                long used = total - rt.freeMemory();
                usedGb = used / (1024.0 * 1024.0 * 1024.0);

                double totalGb = configuredMaxRamGb;
                double displayUsed = serverStarted ? usedGb : 0.0;
                double displayTotal = serverStarted ? totalGb : 0.0;
                double progress = (serverStarted && displayTotal > 0) ? displayUsed / displayTotal : 0.0;
                double finalUsed = displayUsed;
                double finalTotal = displayTotal;

                Platform.runLater(() -> {
                    if (ramUsedLabel != null) {
                        ramUsedLabel.setText(String.format("%.2f GB", finalUsed));
                    }
                    if (ramTotalLabel != null) {
                        if (finalTotal > 0) {
                            ramTotalLabel.setText(String.format("%.2f GB", finalTotal));
                        } else {
                            ramTotalLabel.setText("-");
                        }
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


    private void appendToConsole(String text) {
        if (consoleOutput == null) return;
        consoleOutput.appendText(text);
    }

    private void onKickPlayer(String name) {
        sendServerCommand("kick " + name);
    }

    private void onBanPlayer(String name) {
        sendServerCommand("ban " + name);
    }

    private void onToggleOp(String name, boolean op) {
        sendServerCommand((op ? "op " : "deop ") + name);
    }

    private void sendServerCommand(String cmd) {
        if (serverProcess == null || !serverProcess.isAlive()) {
            appendToConsole("Server is not running.\n");
            return;
        }
        try {
            serverProcess.sendCommand(cmd);
            appendToConsole("> " + cmd + "\n");
        } catch (Exception e) {
            appendToConsole("Failed to send command: " + e.getMessage() + "\n");
        }
    }
}
