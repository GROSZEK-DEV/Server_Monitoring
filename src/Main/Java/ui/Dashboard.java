package Main.Java.ui;

import Main.Java.server.ServerProcess;

import java.io.File;

public class Dashboard {
    private ServerProcess server;

    public void OnStartClicked(File serverFolder){
        server = new ServerProcess(serverFolder);
        try {
            server.start();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
//jello

}
