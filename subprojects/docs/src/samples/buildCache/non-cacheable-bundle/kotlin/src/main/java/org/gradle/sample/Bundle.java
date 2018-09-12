package org.gradle.sample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Bundle {
    public static void main(String[] args) throws IOException {
        File scriptsDir = new File(args[0]);
        File bundleFile = new File(args[1]);

        try (OutputStream out = new FileOutputStream(bundleFile)) {
            List<File> sortedFiles = Stream.of(scriptsDir.listFiles()).sorted().collect(Collectors.toList());
            for (File jsFile : sortedFiles) {
                Files.copy(jsFile.toPath(), out);
            }
        }
    }
}
