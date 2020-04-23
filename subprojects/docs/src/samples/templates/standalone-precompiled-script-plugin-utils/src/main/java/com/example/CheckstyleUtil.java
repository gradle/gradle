package com.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class CheckstyleUtil {

    public static void copyFileFromJar(String jarPath, File target) throws IOException {
        Files.copy(CheckstyleUtil.class.getResourceAsStream(jarPath), target.toPath(), REPLACE_EXISTING);
    }
}
