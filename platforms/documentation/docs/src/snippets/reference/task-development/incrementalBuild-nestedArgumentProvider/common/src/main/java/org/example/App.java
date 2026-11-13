package org.example;

import java.nio.file.Files;
import java.nio.file.Path;

public class App {
    public static void main(String[] args) throws Exception {
        System.out.println(Files.readString(Path.of(System.getProperty("app.config"))).trim());
    }
}
