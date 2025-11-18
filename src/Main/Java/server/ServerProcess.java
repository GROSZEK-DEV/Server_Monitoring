package Main.Java.server;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;

public class ServerProcess {
    private Process process;
    private final File serverDir;

    public ServerProcess(File serverDir){
        this.serverDir = serverDir;
    }

    public void start() throws Exception{
        ProcessBuilder pb = new ProcessBuilder("cmd.exe","/c","run.bat");
        pb.directory(serverDir);
        pb.redirectErrorStream(true);
        process = pb.start();
    }

    public void sendCommand(String cmd) throws Exception{
        if (process != null){
            OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream());
            writer.write(cmd + System.lineSeparator());
            writer.flush();
        }
    }

    public InputStream getConsoleStream(){
        return process != null ? process.getInputStream() : null;
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }
}
