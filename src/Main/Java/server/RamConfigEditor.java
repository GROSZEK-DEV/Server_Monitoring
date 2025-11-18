package Main.Java.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class RamConfigEditor {

    public static void UpdateRam(File runBat,String maxRam,String minRam) throws IOException {

        String content = Files.readString(runBat.toPath());

        content = content.replaceAll("-Xmx\\d+[MG]", "-Xmx" + maxRam)
                .replaceAll("-Xms\\d+[MG]", "-Xms" + minRam);

        Files.writeString(runBat.toPath(), content);
    }



}
