/*
 * Copyright 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/**
 * Verifies that the given "remote project ref" properties exist in {@code gradle.properties}.
 *
 * Usage (Java 11+ single-file source execution):
 *   java .teamcity/scripts/CheckRemoteProjectRef.java <propertyKey1> <propertyKey2> ...
 *
 * The check currently enforces:
 * - Each requested key exists in {@code gradle.properties}
 * - Its value is non-empty
 */
public class CheckRemoteProjectRef {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java CheckRemoteProjectRef.java <propertyKey1> <propertyKey2> ...");
            System.exit(2);
        }

        Path propsPath = Paths.get("gradle.properties");
        if (!Files.exists(propsPath)) {
            System.err.println("gradle.properties not found at: " + propsPath.toAbsolutePath());
            System.exit(2);
        }

        Map<String, String> props = readProperties(propsPath);

        List<String> missing = new ArrayList<>();
        List<String> empty = new ArrayList<>();

        for (String rawKey : args) {
            String key = rawKey.trim();
            if (key.isEmpty()) continue;
            if (!props.containsKey(key)) {
                missing.add(key);
                continue;
            }
            String value = props.get(key);
            if (value == null || value.trim().isEmpty()) {
                empty.add(key);
            }
        }

        if (!missing.isEmpty() || !empty.isEmpty()) {
            if (!missing.isEmpty()) {
                System.err.println("Missing required properties in gradle.properties:");
                for (String k : missing) {
                    System.err.println("  - " + k);
                }
            }
            if (!empty.isEmpty()) {
                System.err.println("Properties with empty values in gradle.properties:");
                for (String k : empty) {
                    System.err.println("  - " + k);
                }
            }
            System.exit(1);
        }
    }

    private static Map<String, String> readProperties(Path file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            int idx = trimmed.indexOf('=');
            if (idx < 0) {
                continue;
            }
            String key = trimmed.substring(0, idx).trim();
            String value = trimmed.substring(idx + 1).trim();
            if (!key.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }
}

