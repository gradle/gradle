package org.gradle.sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sample {

    public void functionality() {
        try {
            Path marker = Paths.get("marker.file");
            if (!Files.exists(marker)) {
                Files.write(marker, "mark".getBytes());
                throw new RuntimeException("fail me!");
            }
            Files.write(marker, "again".getBytes());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public void otherFunctionality() {
        System.out.println("I'm doing things");
    }
}
