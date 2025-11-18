package Main.Java.server;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RconConnection implements Closeable {

    private final String host;
    private final int port;
    private final String password;

    private Socket socket;

    public RconConnection(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    public synchronized void connect() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        // TODO: implement RCON handshake / auth packet using 'password'
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    // TODO: add sendCommand(String cmd) that sends RCON packets and returns the response
}
