package Main.Java.utils;

import javafx.stage.DirectoryChooser;

import java.io.File;

public class FileHelper {
    public static File selectServerFolder(){
        DirectoryChooser directory = new DirectoryChooser();
        directory.setTitle("Choose Server Folder");
        return directory.showDialog(null);
    }

}
