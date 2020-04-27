package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for extracting the checkstyle.xml used by other plugins
 */
public abstract class CheckstyleUtil {
    public static String getCheckstyleConfig(String resourcePath) throws IOException {
        InputStream in = CheckstyleUtil.class.getResourceAsStream(resourcePath);

        StringBuilder sb = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
        }
        return sb.toString();
    }
}
